/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.smb.server;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

import org.filesys.debug.Debug;
import org.filesys.netbios.server.LANAMonitor;
import org.filesys.server.ServerListener;
import org.filesys.server.SrvSession;
import org.filesys.server.SrvSessionList;
import org.filesys.server.Version;
import org.filesys.server.auth.ISMBAuthenticator;
import org.filesys.server.config.ConfigId;
import org.filesys.server.config.ConfigurationListener;
import org.filesys.server.config.CoreServerConfigSection;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.server.core.InvalidDeviceInterfaceException;
import org.filesys.server.core.ShareType;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.filesys.DiskInterface;
import org.filesys.server.filesys.NetworkFileServer;
import org.filesys.server.thread.ThreadRequestPool;
import org.filesys.server.thread.TimedThreadRequest;
import org.filesys.smb.Dialect;
import org.filesys.smb.DialectSelector;
import org.filesys.smb.ServerType;
import org.filesys.smb.dcerpc.UUID;
import org.filesys.smb.server.nio.NIOSMBConnectionsHandler;
import org.filesys.util.PlatformType;

/**
 * SMB Server Class
 *
 * @author gkspencer
 */
public class SMBServer extends NetworkFileServer implements Runnable, ConfigurationListener {

    // Constants
    //
    // Server version
    private static final String ServerVersion = Version.SMBServerVersion;

    // SMB server custom server events
    public static final int SMBNetBIOSNamesAdded = ServerListener.ServerCustomEvent;

    // Disconnected session expiry time
    private static final long SMBDisconnectExpiryTime       = 5 * 60L * 1000L;  // 5 mins
    private static final long SMBDisconnectExpiryCheckSecs  = 30L;  // 30 secs

    // Configuration sections
    private SMBConfigSection m_smbConfig;
    private CoreServerConfigSection m_coreConfig;

    // Server thread
    private Thread m_srvThread;

    // Session connections handler
    private SMBConnectionsHandler m_connectionsHandler;

    // Active session list
    private SrvSessionList m_sessions;

    // List of disconnected persistent sessions
    private SrvSessionList m_disconnectedSessList;

    // Server type flags, used when announcing the host
    private int m_srvType = ServerType.WorkStation + ServerType.Server;

    // Server GUID
    private UUID m_serverGUID;

    // SMB packet pool
    private SMBPacketPool m_packetPool;

    // NetBIOS LANA monitor
    private LANAMonitor m_lanaMonitor;

    /**
     * SMB Disconnected Session Expiry Timed Thread Request Class
     */
    private class SMBDisconnectedSessionTimedRequest extends TimedThreadRequest {

        /**
         * Constructor
         */
        public SMBDisconnectedSessionTimedRequest() {
            super("SMBDisconnectedSessionExpiry", -SMBDisconnectExpiryCheckSecs, SMBDisconnectExpiryCheckSecs);
        }

        /**
         * Expiry checker method
         */
        protected void runTimedRequest() {

            // Check for expired leases
            checkForExpiredSessions();
        }
    }

    /**
     * Create an SMB server using the specified configuration.
     *
     * @param cfg ServerConfiguration
     * @exception Exception Failed to initialize the SMB server
     */
    public SMBServer(ServerConfiguration cfg) throws Exception {

        super("SMB", cfg);

        // Call the common constructor
        CommonConstructor();
    }

    /**
     * Add a new session to the server
     *
     * @param sess SMBSrvSession
     */
    public final void addSession(SMBSrvSession sess) {

        // Add the session to the session list
        m_sessions.addSession(sess);

        // Indicate this is not a disconnected session
        sess.setDisconnectedAt( 0L);

        // Propagate the debug settings to the new session
        if (Debug.EnableInfo && hasDebug()) {

            // Enable session debugging, output to the same stream as the server
            sess.setDebug(getSMBConfiguration().getSessionDebugFlags());
        }
    }

    /**
     * Check if the disk share is read-only.
     *
     * @param shr SharedDevice
     */
    protected final void checkReadOnly(SharedDevice shr) {

        // For disk devices check if the shared device is read-only, this should also check if the
        // shared device path actually exists.
        if (shr.getType() == ShareType.DISK) {

            // Check if the disk device is read-only
            try {

                // Get the device interface for the shared device
                DiskInterface disk = (DiskInterface) shr.getInterface();
                if (disk.isReadOnly(null, shr.getContext())) {

                    // The disk is read-only, mark the share as read-only
                    int attr = shr.getAttributes();
                    if ((attr & SharedDevice.ReadOnly) == 0)
                        attr += SharedDevice.ReadOnly;
                    shr.setAttributes(attr);

                    // Debug
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("[SMB] Add Share " + shr.toString() + " : isReadOnly");
                }
            }
            catch (InvalidDeviceInterfaceException ex) {

                // Shared device interface error
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[SMB] Add Share " + shr.toString() + " : " + ex.toString());
            }
            catch (FileNotFoundException ex) {

                // Shared disk device local path does not exist
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[SMB] Add Share " + shr.toString() + " : " + ex.toString());
            }
            catch (IOException ex) {

                // Shared disk device access error
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[SMB] Add Share " + shr.toString() + " : " + ex.toString());
            }
        }
    }

    /**
     * Common constructor code.
     *
     * @exception Exception Error initializing the SMB server
     */
    protected void CommonConstructor()
            throws Exception {

        // Get the SMB server configuration
        m_smbConfig = (SMBConfigSection) getConfiguration().getConfigSection(SMBConfigSection.SectionName);

        if (m_smbConfig != null) {

            // Add the SMB server as a configuration change listener of the server configuration
            getConfiguration().addListener(this);

            // Check if debug output is enabled
            if (getSMBConfiguration().getSessionDebugFlags().isEmpty() == false)
                setDebug(true);

            // Set the server version
            setVersion(ServerVersion);

            // Create the active session list
            m_sessions = new SrvSessionList();

            // Set the maximum virtual circuits per session
            SMBSrvSession.getFactory().setMaximumVirtualCircuits(m_smbConfig.getMaximumVirtualCircuits());

            // Get the core server configuration
            m_coreConfig = (CoreServerConfigSection) getConfiguration().getConfigSection(CoreServerConfigSection.SectionName);
            if (m_coreConfig != null) {

                // Create the SMB packet pool using the global memory pool
                m_packetPool = new SMBPacketPool(m_coreConfig.getMemoryPool(), m_coreConfig.getThreadPool());

                // Set the maximum oversized packet size
                m_packetPool.setMaximumOverSizedAllocation( m_coreConfig.getMaximumOversizedPacket());

                // Check if packet pool debugging is enabled
                if (m_smbConfig.getSessionDebugFlags().contains( SMBSrvSession.Dbg.PKTPOOL))
                    m_packetPool.setDebug(true);

                if (m_smbConfig.getSessionDebugFlags().contains( SMBSrvSession.Dbg.PKTALLOC))
                    m_packetPool.setAllocateDebug(true);
            }
        }
        else
            setEnabled(false);

    }

    /**
     * Delete temporary shares created by the share mapper for the specified session
     *
     * @param sess SMBSrvSession
     */
    public final void deleteTemporaryShares(SMBSrvSession sess) {

        // Delete temporary shares via the share mapper
        getShareMapper().deleteShares(sess);
    }

    /**
     * Return the SMB server configuration
     *
     * @return SMBConfigSection
     */
    public final SMBConfigSection getSMBConfiguration() {
        return m_smbConfig;
    }

    /**
     * Return the server comment.
     *
     * @return String
     */
    public final String getComment() {
        return getSMBConfiguration().getComment();
    }

    /**
     * Return the SMB server name
     *
     * @return String
     */
    public final String getServerName() {
        return getSMBConfiguration().getServerName();
    }

    /**
     * Return the server type flags.
     *
     * @return int
     */
    public final int getServerType() {
        return m_srvType;
    }

    /**
     * Return the per session debug flag settings.
     *
     * @return EnumSet&lt;SMBSrvSession.Dbg&gt;
     */
    public final EnumSet<SMBSrvSession.Dbg> getSessionDebug() {
        return getSMBConfiguration().getSessionDebugFlags();
    }

    /**
     * Return the list of SMB dialects that this server supports.
     *
     * @return DialectSelector
     */
    public final DialectSelector getSMBDialects() {
        return getSMBConfiguration().getEnabledDialects();
    }

    /**
     * Return the SMB authenticator
     *
     * @return ISMBAuthenticator
     */
    public final ISMBAuthenticator getSMBAuthenticator() {
        return getSMBConfiguration().getAuthenticator();
    }

    /**
     * Return the active session list
     *
     * @return SrvSessionList
     */
    public final SrvSessionList getSessions() {
        return m_sessions;
    }

    /**
     * Return the SMB packet pool
     *
     * @return SMBPacketPool
     */
    public final SMBPacketPool getPacketPool() {
        return m_packetPool;
    }

    /**
     * Return the thread pool
     *
     * @return ThreadRequestPool
     */
    public final ThreadRequestPool getThreadPool() {
        return m_coreConfig.getThreadPool();
    }

    /**
     * Return the NetBIOS LANA monitor
     *
     * @return LANAMonitor
     */
    public final LANAMonitor getLANAMonitor() { return m_lanaMonitor; }

    /**
     * Set the LANA monitor
     *
     * @param lanaMonitor LANAMonitor
     */
    public final void setLANAMonitor( LANAMonitor lanaMonitor) { m_lanaMonitor = lanaMonitor; }

    /**
     * Start the SMB server.
     */
    public void run() {

        // Fire a server startup event
        fireServerEvent(ServerListener.ServerStartup);

        // Indicate that the server is active
        setActive(true);

        // Check if we are running under Windows
        boolean isWindows = PlatformType.isWindowsNTOnwards();

        // Generate a GUID for the server based on the server name
        Random r = new Random();
        m_serverGUID = new UUID(r.nextLong(), r.nextLong());

        // Debug
        if (Debug.EnableInfo && hasDebug()) {

            // Dump the server name/version and Java runtime details
            Debug.println("[SMB] SMB Server " + getServerName() + " starting");
            Debug.print("[SMB] Version " + isVersion());
            Debug.print(", Java VM " + System.getProperty("java.vm.version"));
            Debug.println(", OS " + System.getProperty("os.name") + ", version " + System.getProperty("os.version"));

            // Check for server alias names
            if (getSMBConfiguration().hasAliasNames())
                Debug.println("[SMB] Server alias(es) : " + getSMBConfiguration().getAliasNames());

            // Output the authenticator details
            if (getSMBAuthenticator() != null)
                Debug.println("[SMB] Using authenticator " + getSMBAuthenticator().toString());

            // Display the timezone offset/name
            if (getGlobalConfiguration().getTimeZone() != null)
                Debug.println("[SMB] Server timezone " + getGlobalConfiguration().getTimeZone() + ", offset from UTC = "
                        + getGlobalConfiguration().getTimeZoneOffset() / 60 + "hrs");
            else
                Debug.println("[SMB] Server timezone offset = " + getGlobalConfiguration().getTimeZoneOffset() / 60 + "hrs");

            // Dump the available dialect list
            Debug.println("[SMB] Dialects enabled = " + getSMBDialects());

            // Dump the share list
            Debug.println("[SMB] Shares:");
            Enumeration<SharedDevice> enm = getFullShareList(getSMBConfiguration().getServerName(), null).enumerateShares();

            while (enm.hasMoreElements()) {
                SharedDevice share = enm.nextElement();
                Debug.println("[SMB]  " + share.toString() + " "
                        + (share.getContext() != null ? share.getContext().toString() : ""));
            }
        }

        // Create a server socket to listen for incoming session requests
        try {

            // Add the IPC$ named pipe shared device
            AdminSharedDevice admShare = new AdminSharedDevice();
            getFilesystemConfiguration().addShare(admShare);

            // Clear the server shutdown flag
            setShutdown(false);

            // Get the list of IP addresses the server is bound to
            getServerIPAddresses();

            // Check if the NT SMB dialect is enabled, if so then update the server flags to
            // indicate that this is an NT server
            if (getSMBConfiguration().getEnabledDialects().hasDialect(Dialect.NT) == true) {

                // Enable the NT server flag
                getSMBConfiguration().setServerType(getServerType() + ServerType.NTServer);

                // Debug
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[SMB] Added NTServer flag to host announcement");
            }

            // Create the SMB connections handler
            //
            // Note: The older thread per session/socket handler is used for Win32 NetBIOS connections
            if (getSMBConfiguration().hasDisableNIOCode() || getSMBConfiguration().hasWin32NetBIOS()) {

                // Use the older threaded connections handler (thread per session model)
                m_connectionsHandler = new ThreadedSMBConnectionsHandler();
            }
            else {

                // Check if the Java socket connections handler should be used
                if (getSMBConfiguration().hasTcpipSMB() || getSMBConfiguration().hasNetBIOSSMB()) {

                    // Use the NIO based native SMB/NetBIOS SMB connections handler
                    m_connectionsHandler = new NIOSMBConnectionsHandler();
                }
            }

            // Initialize the connections handler
            m_connectionsHandler.initializeHandler(this, getSMBConfiguration());
            m_connectionsHandler.startHandler();

            // Check if there are any session handlers installed, if not then close the server
            if (m_connectionsHandler.numberOfSessionHandlers() > 0 || getSMBConfiguration().hasWin32NetBIOS()) {

                // Fire a server active event
                fireServerEvent(ServerListener.ServerActive);

                // Wait for incoming connection requests
                while (hasShutdown() == false) {

                    // Sleep for a while
                    try {
                        Thread.sleep(3000L);
                    }
                    catch (InterruptedException ex) {
                    }
                }
            }
            else if (Debug.EnableError && hasDebug()) {

                // DEBUG
                Debug.println("[SMB] No valid session handlers, server closing");
            }
        }
        catch (Exception ex) {

            // Do not report an error if the server has shutdown, closing the server socket
            // causes an exception to be thrown.
            if (hasShutdown() == false) {
                Debug.println("[SMB] Server error : " + ex.toString(), Debug.Error);
                Debug.println(ex);

                // Store the error, fire a server error event
                setException(ex);
                fireServerEvent(ServerListener.ServerError);
            }
        }

        // Debug
        if (Debug.EnableInfo && hasDebug()) {
            Debug.println("[SMB] SMB Server shutting down ...");

            // Dump the session lists
            dumpSessionLists();
        }

        // Close the host announcer and session handlers
        m_connectionsHandler.stopHandler();

        // Shutdown the Win32 NetBIOS LANA monitor, if enabled
        if (isWindows && getLANAMonitor() != null) {

            // Shutdown the LANA monitor
            getLANAMonitor().shutdownRequest();
        }

        // Indicate that the server is not active
        setActive(false);
        fireServerEvent(ServerListener.ServerShutdown);

        // DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[SMB] Packet pool at shutdown: " + getPacketPool());
    }

    /**
     * Notify the server that a session has been closed.
     *
     * @param sess SMBSrvSession
     */
    protected final void sessionClosed(SMBSrvSession sess) {

        // Remove the session from the active session list
        m_sessions.removeSession(sess);

        // DEBUG
        if (hasDebug()) {
            Debug.println("[SMB] Closed session " + sess.getSessionId() + ", sessions=" + m_sessions.numberOfSessions());
            if (m_sessions.numberOfSessions() > 0 && m_sessions.numberOfSessions() <= 10) {
                Enumeration<SrvSession> sessions = m_sessions.enumerateSessions();
                Debug.print("      Active sessions [");
                while (sessions.hasMoreElements()) {
                    SMBSrvSession curSess = (SMBSrvSession) sessions.nextElement();
                    InetAddress addr = curSess.getRemoteAddress();
                    Debug.print("" + curSess.getSessionId() + "=" + (addr != null ? addr.getHostAddress() : "unknown") + ",");
                }
                Debug.println("]");
            }
        }

        // Notify session listeners that a session has been closed
        fireSessionClosedEvent(sess);
    }

    /**
     * Notify the server that a user has logged on.
     *
     * @param sess SMBSrvSession
     */
    protected final void sessionLoggedOn(SMBSrvSession sess) {

        // Notify session listeners that a user has logged on.
        fireSessionLoggedOnEvent(sess);
    }

    /**
     * Notify the server that a session has been closed.
     *
     * @param sess SMBSrvSession
     */
    protected final void sessionOpened(SMBSrvSession sess) {

        // Notify session listeners that a session has been closed
        fireSessionOpenEvent(sess);
    }

    /**
     * Shutdown the SMB server
     *
     * @param immediate boolean
     */
    public final void shutdownServer(boolean immediate) {

        // Indicate that the server is closing
        setShutdown(true);

        try {

            // Wakeup the main SMB server thread
            m_srvThread.interrupt();
        }
        catch (Exception ex) {
        }

        // Close the active sessions
        Enumeration<SrvSession> enm = m_sessions.enumerateSessions();

        while (enm.hasMoreElements()) {

            // Get the session id and associated session
            SMBSrvSession sess = (SMBSrvSession) enm.nextElement();

            // Inform listeners that the session has been closed
            fireSessionClosedEvent(sess);

            // Close the session
            sess.closeSession();
        }

        // Close and disconnected sessions
        if ( hasDisconnectedSessions()) {

            // DEBUG
            if ( hasDebug())
                Debug.println("[SMB] Disconnected sessions=" + m_disconnectedSessList.numberOfSessions());

            enm = m_disconnectedSessList.enumerateSessions();

            while ( enm.hasMoreElements()) {

                // Get the session id and associated session
                SMBSrvSession sess = (SMBSrvSession) enm.nextElement();

                // Inform listeners that the session has been closed
                fireSessionClosedEvent(sess);

                // Close the session
                sess.closeSession();
            }
        }

        // Wait for the main server thread to close
        if (m_srvThread != null) {

            try {
                m_srvThread.join(3000);
            }
            catch (Exception ex) {
            }
        }

        // Fire a shutdown notification event
        fireServerEvent(ServerListener.ServerShutdown);
    }

    /**
     * Start the SMB server in a seperate thread
     */
    public void startServer() {

        // Create a seperate thread to run the SMB server
        m_srvThread = new Thread(this);
        m_srvThread.setName("SMB Server");

        m_srvThread.start();
    }

    @Override
    public void dumpSessionLists() {

        // Dump the active sessions
        Debug.println("[SMB] Open sessions: " + m_sessions.numberOfSessions());

        if ( m_sessions.numberOfSessions() > 0) {
            Enumeration<SrvSession> sessEnum = m_sessions.enumerateSessions();

            while ( sessEnum.hasMoreElements()) {
                SrvSession curSess = sessEnum.nextElement();

                if ( curSess != null)
                    Debug.println("[SMB]  Open session: " + curSess.toString());
            }
        }

        // Dump the disconnected sessions
        if ( m_disconnectedSessList != null) {
            Debug.println("[SMB] Disconnected sessions: " + m_disconnectedSessList.numberOfSessions());

            if ( m_disconnectedSessList.numberOfSessions() > 0) {
                Enumeration<SrvSession> sessEnum = m_disconnectedSessList.enumerateSessions();

                while ( sessEnum.hasMoreElements()) {
                    SrvSession curSess = sessEnum.nextElement();

                    if ( curSess != null)
                        Debug.println("[SMB]  Disconnected session: " + curSess.toString());
                }
            }
        }
    }

    /**
     * Validate configuration changes that are relevant to the SMB server
     *
     * @param id     int
     * @param config ServerConfiguration
     * @param newVal Object
     * @return int
     * @throws InvalidConfigurationException Failed to change the configuration
     */
    public int configurationChanged(int id, ServerConfiguration config, Object newVal)
            throws InvalidConfigurationException {

        int sts = ConfigurationListener.StsIgnored;

        try {

            // Check if the configuration change affects the SMB server
            switch (id) {

                // Server enable/disable
                case ConfigId.ServerSMBEnable:

                    // Check if the server is active
                    Boolean enaSMB = (Boolean) newVal;

                    if (isActive() && enaSMB.booleanValue() == false) {

                        // Shutdown the server
                        shutdownServer(false);
                    }
                    else if (isActive() == false && enaSMB.booleanValue() == true) {

                        // Start the server
                        startServer();
                    }

                    // Indicate that the setting was accepted
                    sts = ConfigurationListener.StsAccepted;
                    break;

                // Changes that can be accepted without restart
                case ConfigId.SMBComment:
                case ConfigId.SMBDialects:
                case ConfigId.SMBTCPPort:
                case ConfigId.SMBMacExtEnable:
                case ConfigId.SMBDebugEnable:
                case ConfigId.ServerTimezone:
                case ConfigId.ServerTZOffset:
                case ConfigId.ShareList:
                case ConfigId.ShareMapper:
                case ConfigId.SecurityAuthenticator:
                case ConfigId.UsersList:
                case ConfigId.DebugDevice:
                    sts = ConfigurationListener.StsAccepted;
                    break;

                // Changes that affect new sessions only
                //
                // Enable/dsiable debug output
                case ConfigId.SMBSessionDebug:
                    sts = ConfigurationListener.StsNewSessionsOnly;
                    if (newVal instanceof Integer) {
                        Integer dbgVal = (Integer) newVal;
                        setDebug(dbgVal.intValue() != 0 ? true : false);
                    }
                    break;

                // Maximum virtual circuits per session
                case ConfigId.SMBMaxVirtualCircuit:
                    sts = ConfigurationListener.StsNewSessionsOnly;
                    if (newVal instanceof Integer) {
                        Integer maxVC = (Integer) newVal;
                        SMBSrvSession.getFactory().setMaximumVirtualCircuits(maxVC);
                    }
                    break;

                // Changes that require a restart
                case ConfigId.SMBHostName:
                case ConfigId.SMBAliasNames:
                case ConfigId.SMBDomain:
                case ConfigId.SMBBroadcastMask:
                case ConfigId.SMBAnnceEnable:
                case ConfigId.SMBAnnceInterval:
                case ConfigId.SMBAnnceDebug:
                case ConfigId.SMBTCPEnable:
                case ConfigId.SMBBindAddress:
                    sts = ConfigurationListener.StsRestartRequired;
                    break;
            }
        }
        catch (Exception ex) {
            throw new InvalidConfigurationException("SMB Server configuration error", ex);
        }

        // Return the status
        return sts;
    }

    /**
     * Get the list of local IP addresses
     */
    private final void getServerIPAddresses() {

        try {

            // Get the local IP address list
            Enumeration<NetworkInterface> enm = NetworkInterface.getNetworkInterfaces();
            List<InetAddress> addrList = new ArrayList<InetAddress>();

            while (enm.hasMoreElements()) {

                // Get the current network interface
                NetworkInterface ni = enm.nextElement();

                // Get the address list for the current interface
                Enumeration<InetAddress> addrs = ni.getInetAddresses();

                while (addrs.hasMoreElements())
                    addrList.add(addrs.nextElement());
            }

            // Convert the vector of addresses to an array
            if (addrList.size() > 0) {

                // Convert the address vector to an array
                InetAddress[] inetAddrs = new InetAddress[addrList.size()];

                // Copy the address details to the array
                for (int i = 0; i < addrList.size(); i++)
                    inetAddrs[i] = addrList.get(i);

                // Set the server IP address list
                setServerAddresses(inetAddrs);
            }
        }
        catch (Exception ex) {

            // DEBUG
            if (Debug.EnableError && hasDebug())
                Debug.println("[SMB] Error getting local IP addresses, " + ex.toString());
        }
    }

    /**
     * Return the server GUID
     *
     * @return UUID
     */
    public final UUID getServerGUID() {
        return m_serverGUID;
    }

    /**
     * Send a NetBIOS names added event to server listeners
     *
     * @param lana int
     */
    public final void fireNetBIOSNamesAddedEvent(int lana) {

        // Send the event to registered listeners, encode the LANA id in the top of the event id
        fireServerEvent(SMBNetBIOSNamesAdded + (lana << 16));
    }

    /**
     * Check if there are disconnected sessions
     *
     * @return boolean
     */
    public final boolean hasDisconnectedSessions() {
        if ( m_disconnectedSessList == null)
            return false;
        return m_disconnectedSessList.numberOfSessions() > 0 ? true : false;
    }

    /**
     * Check for a disconnected persistent session
     *
     * @param sessId int
     * @return SMBSrvSession
     */
    public final synchronized SMBSrvSession findDisconnectedSession(int sessId) {

        // Check if there are any disconnected sessions
        if ( m_disconnectedSessList == null)
            return null;

        // Search for the disconnected session
        return (SMBSrvSession) m_disconnectedSessList.removeSession( sessId);
    }

    /**
     * Check for a persistent session on the active session list
     *
     * @param sessId int
     * @return SMBSrvSession
     */
    public final synchronized SMBSrvSession findActiveSession(int sessId) {

        SMBSrvSession sess = (SMBSrvSession) m_sessions.findSession( sessId);
        if ( sess != null && sess.isPersistentSession()) {

            // Remove the session from the active session list
            m_sessions.removeSession( sessId);
        }
        else {
            sess = null;
        }

        // Return the session, or null
        return sess;
    }

    /**
     * Add a session to the disconnected session list
     *
     * @param sess SMBSrvSession
     */
    public final synchronized void addDisconnectedSession(SMBSrvSession sess) {

        // Check if the disconnected session list has been allocated
        if ( m_disconnectedSessList == null) {

            // Create the disconnected session list
            m_disconnectedSessList = new SrvSessionList();

            // Add a timer to check for expired disconnected sessions
            getThreadPool().queueTimedRequest( new SMBDisconnectedSessionTimedRequest());
        }

        // Set the system time that the session was disconnected
        sess.setDisconnectedAt( System.currentTimeMillis());

        // Add the disconnected session
        m_disconnectedSessList.addSession( sess);
    }

    /**
     * Check for expired disconnected sessions
     */
    private final void checkForExpiredSessions() {

        // Make sure the disconnected session list is valid
        if ( m_disconnectedSessList == null || m_disconnectedSessList.numberOfSessions() == 0)
            return;

        synchronized ( m_disconnectedSessList) {

            // Enumerate the disconnected session list for expired sessions
            Enumeration<SrvSession> enm = m_disconnectedSessList.enumerateSessions();
            long timeNow = System.currentTimeMillis();

            while( enm.hasMoreElements()) {

                // Check the current disconnected session
                SMBSrvSession curSess = (SMBSrvSession) enm.nextElement();

                if ( curSess != null && curSess.isDisconnectedSession()) {

                    // Check if the disconnected session has expired
                    if (( curSess.getDisconnectedAt() + SMBDisconnectExpiryTime) < timeNow) {

                        // Disconnected session has expired, remove it from the list and cleanup the session
                        m_disconnectedSessList.removeSession( curSess.getSessionId());

                        // DEBUG
                        if ( getSessionDebug().contains( SMBSrvSession.Dbg.SOCKET))
                            Debug.println("[SMB] Disconnected session expired, sess=" + curSess);

                        // Cleanup the disconnected session
                        curSess.setPersistentSession( false);
                        curSess.closeSession();
                    }
                }
                else {

                    // DEBUG
                    if ( getSessionDebug().contains( SMBSrvSession.Dbg.SOCKET))
                        Debug.println("[SMB] Ignored disconnected session=" + curSess);
                }
            }
        }
    }
}
