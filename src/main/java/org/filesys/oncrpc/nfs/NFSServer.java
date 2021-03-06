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

package org.filesys.oncrpc.nfs;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Enumeration;

import org.filesys.debug.Debug;
import org.filesys.oncrpc.*;
import org.filesys.oncrpc.nfs.nio.NFSConnectionsHandler;
import org.filesys.oncrpc.nfs.v3.NFS3;
import org.filesys.oncrpc.nfs.v3.NFS3RpcProcessor;
import org.filesys.server.ServerListener;
import org.filesys.server.SrvSession;
import org.filesys.server.Version;
import org.filesys.server.config.CoreServerConfigSection;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.server.core.InvalidDeviceInterfaceException;
import org.filesys.server.core.ShareType;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.core.SharedDeviceList;
import org.filesys.server.filesys.*;
import org.filesys.server.thread.ThreadRequestPool;
import org.filesys.util.HexDump;

/**
 * NFS Server Class
 *
 * <p>Contains the main NFS server.
 *
 * @author gkspencer
 */
public class NFSServer extends RpcNetworkServer implements RpcProcessor {

    //  Constants
    //
    //  Server version
    private static final String ServerVersion = Version.NFSServerVersion;

    //	Unix path seperator
    public static final String UNIX_SEPERATOR       = "/";
    public static final char UNIX_SEPERATOR_CHAR    = '/';
    public static final String DOS_SEPERATOR        = "\\";
    public static final char DOS_SEPERATOR_CHAR     = '\\';

    //	Maximum request size to accept
    public final static int MaxRequestSize = 0xFFFF;

    //  Configuration sections
    private CoreServerConfigSection m_coreConfig;
    private NFSConfigSection m_nfsConfig;

    //	Incoming datagram handler for UDP requests
    private MultiThreadedUdpRpcDatagramHandler m_udpHandler;

    //	Incoming session handler for TCP requests
    private MultiThreadedTcpRpcSessionHandler m_tcpHandler;

    // NIO handler for TCP connections
    private NFSConnectionsHandler m_nioTcpHandler;

    //	Share details hash
    private ShareDetailsHash m_shareDetails;

    //	Tree connection hash
    private TreeConnectionHash m_connections;

    //	Session tables for the various authentication types
    private NFSSessionTable m_sessAuthNull;
    private NFSSessionTable m_sessAuthUnix;

    //	Session id generator
    private int m_sessId = 1;

    //	Port to bind the NFS server to (UDP and TCP)
    private int m_port;

    //	Shared thread pool, used by TCP and UDP request handlers
    private ThreadRequestPool m_threadPool;

    //	Shared packet pool, usd by TCP and UDP request handlers
    private RpcPacketPool m_packetPool;

    //	RPC authenticator, from the main server configuration
    private RpcAuthenticator m_rpcAuthenticator;

    //	Write verifier, generated from the server start time
    private long m_writeVerifier;

    // Debug flags
    private EnumSet<NFSSrvSession.Dbg> m_debug;

    /**
     * Class constructor
     *
     * @param config ServerConfiguration
     */
    public NFSServer(ServerConfiguration config) {
        super("NFS", config);

        //	Set the server version
        setVersion(ServerVersion);

        //  Get the required configuration sections
        m_coreConfig = (CoreServerConfigSection) config.getConfigSection( CoreServerConfigSection.SectionName);
        m_nfsConfig = (NFSConfigSection) config.getConfigSection(NFSConfigSection.SectionName);

        if (m_nfsConfig != null) {

            //	Set the debug flags
            setDebugFlags(getNFSConfiguration().getNFSDebug());

            //	Set the port to bind the server to
            if (getNFSConfiguration().getNFSServerPort() != 0)
                setPort(getNFSConfiguration().getNFSServerPort());
            else
                setPort(NFS.DefaultPort);

            //	Set the RPC authenticator
            m_rpcAuthenticator = getNFSConfiguration().getRpcAuthenticator();

            //	Generate the write verifier
            m_writeVerifier = System.currentTimeMillis();

            // Set the port mapper port
            setPortMapper(getNFSConfiguration().getPortMapperPort());
        } else
            setEnabled(false);
    }

    /**
     * Return the port to bind to
     *
     * @return int
     */
    public final int getPort() {
        return m_port;
    }

    /**
     * Return the NFS configuration section
     *
     * @return NFSConfigSection
     */
    protected final NFSConfigSection getNFSConfiguration() {
        return m_nfsConfig;
    }

    /**
     * Get the associated thread pool
     *
     * @return ThreadRequestPool
     */
    public final ThreadRequestPool getThreadPool() { return m_threadPool; }

    /**
     * Get the associated memory pool
     *
     * @return RpcPacketPool
     */
    public final RpcPacketPool getPacketPool() { return m_packetPool; }

    /**
     * Set the port to use
     *
     * @param port int
     */
    public final void setPort(int port) {
        m_port = port;
    }

    /**
     * Check if the specified debug flag is enabled
     *
     * @param flg Enum&lt;NFSSrvSession.Dbg&gt;
     * @return boolean
     */
    public final boolean hasDebug( Enum<NFSSrvSession.Dbg> flg) {
        return m_debug.contains( flg);
    }

    /**
     * Set the debug flags
     *
     * @param dbg EnumSet&lt;NFSSrvSession.Dbg&gt;
     */
    public final void setDebugFlags(EnumSet<NFSSrvSession.Dbg> dbg) {
        m_debug = dbg;
    }

    /**
     * Start the NFS server
     */
    public void startServer() {

        try {

            // Add the NFS v3 RPC processor
            RpcProcessorFactory.addRpcProcessorClass(NFS3.ProgramId, NFS3.VersionId, NFS3RpcProcessor.class);

            //	Allocate the share detail hash list and tree connection list, and populate with the available share details
            m_shareDetails = new ShareDetailsHash();
            m_connections = new TreeConnectionHash();

            checkForNewShares();

            //	Create the share thread pool for RPC processing
            m_threadPool = m_coreConfig.getThreadPool();

            //	Create the shared packet pool
            m_packetPool = new RpcPacketPool( m_coreConfig.getMemoryPool(), m_coreConfig.getThreadPool());

            //	Create the UDP handler for accepting incoming requests
            m_udpHandler = new MultiThreadedUdpRpcDatagramHandler("Nfsd", "Nfs", this, this, null, getPort(), MaxRequestSize);
            m_udpHandler.initializeSessionHandler(this, m_packetPool, m_threadPool);

            //	Start the UDP request listener is a seperate thread
            Thread udpThread = new Thread(m_udpHandler);
            udpThread.setName("NFS_UDP");
            udpThread.start();

            //	Create the TCP handler for accepting incoming requests
            if ( m_nfsConfig.hasDisableNIOCode() == false) {

                // Use the NIO based TCP socket connections handler
                m_nioTcpHandler = new NFSConnectionsHandler();
                m_nioTcpHandler.initializeHandler( this, m_nfsConfig);

                m_port = m_nioTcpHandler.getPort();

                // Start the NIO connections handler
                m_nioTcpHandler.startHandler();
            }
            else {

                // DEBUG
                if ( hasDebug( NFSSrvSession.Dbg.SOCKET))
                    Debug.println("[NFS] Disabled NIO");

                // Use the older thread per socket TCP connections handler
                m_tcpHandler = new MultiThreadedTcpRpcSessionHandler("Nfsd", "Nfs", this, this, null, getPort(), MaxRequestSize);
                m_tcpHandler.initializeSessionHandler(this, m_packetPool, m_threadPool);

                m_port = m_tcpHandler.getPort();

                //	Start the TCP request listener is a seperate thread
                Thread tcpThread = new Thread(m_tcpHandler);
                tcpThread.setName("NFS_TCP");
                tcpThread.start();
            }

            //	Register the NFS server with the portmapper
            PortMapping[] mappings = new PortMapping[2];
            mappings[0] = new PortMapping(NFS3.ProgramId, NFS3.VersionId, Rpc.ProtocolId.UDP, getPort());
            mappings[1] = new PortMapping(NFS3.ProgramId, NFS3.VersionId, Rpc.ProtocolId.TCP, getPort());

            registerRPCServer(mappings);

            // Indicate the NFS server is running
            setActive(true);
        }
        catch (Exception ex) {

            // Save the exception
            setException(ex);

            Debug.println(ex);
        }
    }

    /**
     * Shutdown the NFS server
     *
     * @param immediate boolean
     */
    public void shutdownServer(boolean immediate) {

        //  Unregister the NFS server with the portmapper
        try {
            PortMapping[] mappings = new PortMapping[2];
            mappings[0] = new PortMapping(NFS3.ProgramId, NFS3.VersionId, Rpc.ProtocolId.UDP, getPort());
            mappings[1] = new PortMapping(NFS3.ProgramId, NFS3.VersionId, Rpc.ProtocolId.TCP, getPort());

            unregisterRPCServer(mappings);
        }
        catch (IOException ex) {

            //  DEBUG
            if (hasDebug(NFSSrvSession.Dbg.ERROR))
                Debug.println(ex);
        }

        //	Stop the RPC handlers
        if (m_udpHandler != null) {
            m_udpHandler.closeSessionHandler(this);
            m_udpHandler = null;
        }

        if (m_tcpHandler != null) {
            m_tcpHandler.closeSessionHandler(this);
            m_tcpHandler = null;
        }

        if ( m_nioTcpHandler != null) {
            m_nioTcpHandler.stopHandler();
            m_nioTcpHandler = null;
        }

        //	Fire a shutdown notification event
        fireServerEvent(ServerListener.ServerShutdown);

        // Indicate the server has been shutdown
        setActive(false);
    }


    /**
     * Return the next session id
     *
     * @return int
     */
    protected final synchronized int getNextSessionId() {
        return m_sessId++;
    }

    /**
     * Process an RPC request to the NFS or mount server
     *
     * @param rpc RpcPacket
     * @return RpcPacket
     * @exception IOException Socket error
     */
    public RpcPacket processRpc(RpcPacket rpc) throws IOException {

        //	Dump the request data
        if (Debug.EnableInfo && hasDebug(NFSSrvSession.Dbg.DUMPDATA))
            Debug.println("NFS Req=" + rpc.toString());

        //	Validate the request
        int progId = rpc.getProgramId();
        int version = rpc.getProgramVersion();

        if ( RpcProcessorFactory.supportsRpcProgram( progId) == false) {

            //	Request is not for us
            rpc.buildAcceptErrorResponse(Rpc.AcceptSts.ProgUnavail);
            return rpc;
        }
        else if ( RpcProcessorFactory.supportsRpcVersion( progId, version) == false) {

            // Get the supported version range for the NFS server
            int[] nfsVers = RpcProcessorFactory.getSupportedVersionRange( progId);

            //	Request is not for this version of NFS
            rpc.buildProgramMismatchResponse(nfsVers[0], nfsVers[1]);
            return rpc;
        }

        //	Find the associated session object for the request, or create a new
        // 	session
        NFSSrvSession nfsSess = null;

        try {

            //	Find the associated session, or create a new session
            nfsSess = findSessionForRequest(rpc);
        }
        catch (RpcAuthenticationException ex) {

            //	Failed to authenticate the RPC client
            rpc.buildAuthFailResponse(ex.getAuthenticationErrorCode());
            return rpc;
        }

        // Make sure there is a valid session to process the RPC
        RpcPacket response = null;

        if ( nfsSess != null) {

            // Check if the NFS session has an RPC processor
            if ( nfsSess.hasRpcProcessor() == false) {

                // Create an RPC processor for the session
                RpcSessionProcessor rpcProc = RpcProcessorFactory.getRpcSessionProcessor( progId, version);
                if ( rpcProc != null)
                    nfsSess.setRpcProcessor( rpcProc);
                else {

                    // Return an error, failed to create RPC processor
                    rpc.buildErrorResponse( NFS3.StatusCode.ServerFault.intValue());

                    return rpc;
                }
            }

            // Handoff to the RPC processor for the session
            response = nfsSess.getRpcProcessor().processRpc(rpc, nfsSess);
        }

        // Commit/rollback a transaction that the filesystem driver may have stored in the session
        if (nfsSess != null)
            nfsSess.endTransaction();

        //	Dump the response
        if (Debug.EnableInfo && hasDebug(NFSSrvSession.Dbg.DUMPDATA)) {
            Debug.println("NFS Resp=" + (rpc != null ? rpc.toString() : "<Null>"));
            HexDump.Dump(rpc.getBuffer(), rpc.getLength(), 0);
        }

        //	Return the RPC response
        return response;
    }

    /**
     * Find, or create, the session for the specified RPC request.
     *
     * @param rpc RpcPacket
     * @return NFSSrvSession
     * @exception RpcAuthenticationException Authentication error
     */
    private final NFSSrvSession findSessionForRequest(RpcPacket rpc)
            throws RpcAuthenticationException {

        //	Check the authentication type and search the appropriate session table
        //  for an existing session
        AuthType authType = rpc.getCredentialsType();

        boolean authFailed = true;
        NFSSrvSession sess = null;

        try {

            //	Authenticate the request
            Object sessKey = getRpcAuthenticator().authenticateRpcClient(authType, rpc);

            switch (authType) {

                //	Null authentication
                case Null:
                    sess = findAuthNullSession(rpc, sessKey);
                    break;

                //	Unix authentication
                case Unix:
                    sess = findAuthUnixSession(rpc, sessKey);
                    break;
            }

            // Setup the user context for this request
            if (sess != null) {
                getRpcAuthenticator().setCurrentUser(sess, sess.getClientInformation());
                authFailed = false;

                // DEBUG
                if (Debug.EnableDbg && hasDebug(NFSSrvSession.Dbg.SESSION))
                    Debug.println("[NFS] Found session " + sess + ", client=" + sess.getClientInformation());
            }
        }
        catch (Throwable ex) {
            // DEBUG
            if (Debug.EnableError && hasDebug(NFSSrvSession.Dbg.ERROR))
                Debug.println("[NFS] RPC Authencation Exception: " + ex.toString());
        }

        // Check if the session is valid
        if (authFailed) {

            // Check if the request is a Null request
            rpc.positionAtParameters();
            if (rpc.getProcedureId() != NFS3.ProcedureId.Null.intValue())
                throw new RpcAuthenticationException(Rpc.AuthSts.BadCred);
        }

        //	Return the server session
        return sess;
    }

    /**
     * Find, or create, a null authentication session for the specified request
     *
     * @param rpc     RpcPacket
     * @param sessKey Object
     * @return NFSSrvSession
     */
    private final NFSSrvSession findAuthNullSession(RpcPacket rpc, Object sessKey) {

        //	Check if the null authentication session table is valid
        NFSSrvSession sess = null;

        if (m_sessAuthNull != null) {

            //	Search for the required session using the client IP address
            sess = m_sessAuthNull.findSession(sessKey);
        } else {

            //	Allocate the null authentication session table
            m_sessAuthNull = new NFSSessionTable();
        }

        //	Check if we found the required session object
        if (sess == null) {

            //	Create a new session for the request
            sess = new NFSSrvSession(this, rpc.getClientAddress(), rpc.getClientPort(), rpc.getClientProtocol());
            sess.setAuthIdentifier(sessKey);

            //	Get the client information from the RPC
            sess.setClientInformation(getRpcAuthenticator().getRpcClientInformation(sessKey, rpc));

            //	Add the new session to the session table
            m_sessAuthNull.addSession(sess);

            //	Set the session id and debug output prefix
            sess.setUniqueId("" + sessKey.hashCode());
            sess.setDebugPrefix("[NFS_AN_" + getNextSessionId() + "] ");
            sess.setDebug(getNFSConfiguration().getNFSDebug());

            //	DEBUG
            if (Debug.EnableInfo && hasDebug(NFSSrvSession.Dbg.SESSION))
                Debug.println("[NFS] Added Null session " + sess.getUniqueId());
        }

        //	Return the session
        return sess;
    }

    /**
     * Find, or create, a Unix authentication session for the specified request
     *
     * @param rpc     RpcPacket
     * @param sessKey Object
     * @return NFSSrvSession
     */
    private final NFSSrvSession findAuthUnixSession(RpcPacket rpc, Object sessKey) {

        //	Check if the Unix authentication session table is valid
        NFSSrvSession sess = null;

        if (m_sessAuthUnix != null) {

            //	Search for the required session using the client IP address + gid + uid
            sess = m_sessAuthUnix.findSession(sessKey);
        } else {

            //	Allocate the Unix authentication session table
            m_sessAuthUnix = new NFSSessionTable();
        }

        //	Check if we found the required session object
        if (sess == null) {

            //	Create a new session for the request
            sess = new NFSSrvSession(this, rpc.getClientAddress(), rpc.getClientPort(), rpc.getClientProtocol());
            sess.setAuthIdentifier(sessKey);

            //	Set the session id and debug output prefix
            sess.setUniqueId("" + sessKey.hashCode());
            sess.setDebugPrefix("[NFS_AU_" + getNextSessionId() + "] ");
            sess.setDebug(getNFSConfiguration().getNFSDebug());

            //	Get the client information from the RPC
            sess.setNFSClientInformation(getRpcAuthenticator().getRpcClientInformation(sessKey, rpc));
            sess.setClientInformation(sess.getNFSClientInformation());

            //	Add the new session to the session table
            m_sessAuthUnix.addSession(sess);

            //	DEBUG
            if (Debug.EnableInfo && hasDebug(NFSSrvSession.Dbg.SESSION))
                Debug.println("[NFS] Added Unix session " + sess.getUniqueId());
        } else {

            // Set the thread local client information
            sess.setClientInformation(sess.getNFSClientInformation());
        }

        //	Return the session
        return sess;
    }

    /**
     * Find the share details for the specified shared filesystem using the share hash id
     *
     * @param shareId int
     * @return ShareDetails
     */
    public final ShareDetails findShareDetails(int shareId) {
        return m_shareDetails.findDetails( shareId);
    }

    /**
     * Find the share details for the specified shared filesystem using the share name
     *
     * @param shareName String
     * @return ShareDetails
     */
    public final ShareDetails findShareDetailsByName(String shareName) {
        return m_shareDetails.findDetails( shareName);
    }

    /**
     * Return the write verifier
     *
     * @return long
     */
    public final long getWriteVerifier() {
        return m_writeVerifier;
    }

    /**
     * Check for new shared devices and add them to the share and tree connection lists
     *
     * @return int
     */
    public final int checkForNewShares() {

        //  Scan the shared device list and check for new shared devices
        SharedDeviceList shareList = getShareMapper().getShareList(getConfiguration().getServerName(), null, false);
        Enumeration<SharedDevice> shares = shareList.enumerateShares();

        int newShares = 0;

        while (shares.hasMoreElements()) {

            //  Get the shared device
            SharedDevice share = shares.nextElement();

            //  Check if it is a disk type shared device, if so then add a connection
            //  to the tree connection hash
            if (share != null && share.getType() == ShareType.DISK) {

                //  Check if the filesystem driver has file id support
                boolean fileIdSupport = false;
                try {
                    if (share.getInterface() instanceof FileIdInterface)
                        fileIdSupport = true;
                }
                catch (InvalidDeviceInterfaceException ex) {
                }

                //  Check if the share is already in the share/tree connection lists
                if (m_shareDetails.findDetails(share.getName()) == null) {

                    // Add the new share details
                    m_shareDetails.addDetails(new ShareDetails(share.getName(), fileIdSupport));
                    m_connections.addConnection(new TreeConnection(share));

                    // Update the new share count
                    newShares++;
                }
            }
        }

        // Return the count of new shares added
        return newShares;
    }

    /**
     * Find the tree connection for the specified share hash
     *
     * @param shareHash int
     * @return TreeConnection
     */
    public final TreeConnection findConnection(int shareHash) {
        if (m_connections == null)
            return null;
        return m_connections.findConnection(shareHash);
    }

    /**
     * Return the configured RPC authenticator
     *
     * @return RpcAuthenticator
     */
    protected final RpcAuthenticator getRpcAuthenticator() {
        return m_rpcAuthenticator;
    }

    /**
     * Inform session listeners that a new session has been created
     *
     * @param sess SrvSession
     */
    protected final void fireSessionOpened(SrvSession sess) {
        fireSessionOpenEvent(sess);
    }

    /**
     * Inform session listeners that a session has been closed
     *
     * @param sess SrvSession
     */
    protected final void fireSessionClosed(SrvSession sess) {
        fireSessionClosedEvent(sess);
    }
}
