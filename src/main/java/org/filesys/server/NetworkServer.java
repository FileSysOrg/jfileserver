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

package org.filesys.server;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.filesys.server.auth.acl.AccessControlManager;
import org.filesys.server.config.GlobalConfigSection;
import org.filesys.server.config.SecurityConfigSection;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.server.core.ShareMapper;
import org.filesys.server.core.ShareType;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.core.SharedDeviceList;

/**
 * Network Server Base Class
 *
 * <p>Base class for server implementations for different protocols.
 *
 * @author gkspencer
 */
public abstract class NetworkServer {

    //  Server shutdown thread timeout
    protected final static int SHUTDOWN_TIMEOUT = 60000;  // 1 minute

    //	Protocol name
    private String m_protoName;

    //	Server version
    private String m_version;

    //	Configuration sections used by all network servers
    private ServerConfiguration m_config;
    private GlobalConfigSection m_globalConfig;
    private SecurityConfigSection m_securityConfig;

    //	Debug enabled flag and debug flags
    private boolean m_debug;
    private int m_debugFlags;

    //	List of addresses that the server is bound to
    private InetAddress[] m_ipAddr;

    //	Server shutdown flag and server active flag
    private boolean m_shutdown = false;
    private boolean m_active = false;

    //  Server enabled flag
    private boolean m_enabled;

    //	Server error exception details
    private Exception m_exception;

    //	Server events listener
    private ServerListener m_listener;

    //	Session listener list
    private List<SessionListener> m_sessListeners;

    /**
     * Class constructor
     *
     * @param proto  String
     * @param config ServerConfiguration
     */
    public NetworkServer(String proto, ServerConfiguration config) {
        m_protoName = proto;
        m_config = config;

        // Get the configuration sections
        m_securityConfig = (SecurityConfigSection) m_config.getConfigSection(SecurityConfigSection.SectionName);
        m_globalConfig = (GlobalConfigSection) m_config.getConfigSection(GlobalConfigSection.SectionName);
    }

    /**
     * Return the server configuration.
     *
     * @return ServerConfiguration
     */
    public final ServerConfiguration getConfiguration() {
        return m_config;
    }

    /**
     * Determine if an access control manager is configured
     *
     * @return boolean
     */
    public final boolean hasAccessControlManager() {
        if (m_securityConfig != null)
            return m_securityConfig.getAccessControlManager() != null ? true : false;
        return false;
    }

    /**
     * Return the access control manager
     *
     * @return AccessControlManager
     */
    public final AccessControlManager getAccessControlManager() {
        if (m_securityConfig != null)
            return m_securityConfig.getAccessControlManager();
        return null;
    }

    /**
     * Return the list of IP addresses that the server is bound to.
     *
     * @return InetAddress[]
     */
    public final InetAddress[] getServerAddresses() {
        return m_ipAddr;
    }

    /**
     * Return the share mapper
     *
     * @return ShareMapper
     */
    public final ShareMapper getShareMapper() {
        if (m_securityConfig != null)
            return m_securityConfig.getShareMapper();
        return null;
    }

    /**
     * Return the available shared device list.
     *
     * @param host String
     * @param sess SrvSession
     * @return SharedDeviceList
     */
    public final SharedDeviceList getShareList(String host, SrvSession sess) {
        if (m_securityConfig != null)
            return m_securityConfig.getShareMapper().getShareList(host, sess, false);
        return null;
    }

    /**
     * Return the complete shared device list.
     *
     * @param host String
     * @param sess SrvSession
     * @return SharedDeviceList
     */
    public final SharedDeviceList getFullShareList(String host, SrvSession sess) {
        if (m_securityConfig != null)
            return m_securityConfig.getShareMapper().getShareList(host, sess, true);
        return null;
    }

    /**
     * Return the global configuration
     *
     * @return GlobalConfigSection
     */
    public final GlobalConfigSection getGlobalConfiguration() {
        if (m_globalConfig == null)
            m_globalConfig = (GlobalConfigSection) m_config.getConfigSection(GlobalConfigSection.SectionName);
        return m_globalConfig;
    }

    /**
     * Return the security configuration
     *
     * @return SecurityConfigSection
     */
    public final SecurityConfigSection getSecurityConfiguration() {
        if (m_securityConfig == null)
            m_securityConfig = (SecurityConfigSection) m_config.getConfigSection(SecurityConfigSection.SectionName);
        return m_securityConfig;
    }

    /**
     * Find the shared device with the specified name.
     *
     * @param host   Host name from the UNC path
     * @param name   Name of the shared device to find.
     * @param typ    Shared device type
     * @param sess   Session details
     * @param create Create share flag, false indicates lookup only
     * @return SharedDevice with the specified name and type, else null.
     * @exception Exception Error finding shared device
     */
    public final SharedDevice findShare(String host, String name, ShareType typ, SrvSession sess, boolean create)
            throws Exception {

        //  Search for the specified share
        if (m_securityConfig != null)
            return m_securityConfig.getShareMapper().findShare(host, name, typ, sess, create);
        return null;
    }

    /**
     * Determine if the server is active.
     *
     * @return boolean
     */
    public final boolean isActive() {
        return m_active;
    }

    /**
     * Determine if the server is enabled
     *
     * @return boolean
     */
    public final boolean isEnabled() {
        return m_enabled;
    }

    /**
     * Return the server version string, in 'n.n.n' format
     *
     * @return String
     */

    public final String isVersion() {
        return m_version;
    }

    /**
     * Check if there is a stored server exception
     *
     * @return boolean
     */
    public final boolean hasException() {
        return m_exception != null ? true : false;
    }

    /**
     * Return the stored exception
     *
     * @return Exception
     */
    public final Exception getException() {
        return m_exception;
    }

    /**
     * Clear the stored server exception
     */
    public final void clearException() {
        m_exception = null;
    }

    /**
     * Return the server protocol name
     *
     * @return String
     */
    public final String getProtocolName() {
        return m_protoName;
    }

    /**
     * Determine if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Determine if the specified debug flag is enabled
     *
     * @param flg int
     * @return boolean
     */
    public final boolean hasDebugFlag(int flg) {
        return (m_debugFlags & flg) != 0 ? true : false;
    }

    /**
     * Check if the shutdown flag is set
     *
     * @return boolean
     */
    public final boolean hasShutdown() {
        return m_shutdown;
    }

    /**
     * Set/clear the server active flag
     *
     * @param active boolean
     */
    protected void setActive(boolean active) {
        m_active = active;
    }

    /**
     * Set/clear the server enabled flag
     *
     * @param ena boolean
     */
    protected void setEnabled(boolean ena) {
        m_enabled = ena;
    }

    /**
     * Set the stored server exception
     *
     * @param ex Exception
     */
    protected final void setException(Exception ex) {
        m_exception = ex;
    }

    /**
     * Set the addresses that the server is bound to
     *
     * @param addrs InetAddress[]
     */
    protected final void setServerAddresses(InetAddress[] addrs) {
        m_ipAddr = addrs;
    }

    /**
     * Set the server version
     *
     * @param ver String
     */
    protected final void setVersion(String ver) {
        m_version = ver;
    }

    /**
     * Enable/disable debug output for the server
     *
     * @param dbg boolean
     */
    protected final void setDebug(boolean dbg) {
        m_debug = dbg;
    }

    /**
     * Set the debug flags
     *
     * @param flags int
     */
    protected final void setDebugFlags(int flags) {
        m_debugFlags = flags;
        setDebug(flags == 0 ? false : true);
    }

    /**
     * Set/clear the shutdown flag
     *
     * @param ena boolean
     */
    protected final void setShutdown(boolean ena) {
        m_shutdown = ena;
    }

    /**
     * Add a server listener to this server
     *
     * @param l ServerListener
     */
    public final void addServerListener(ServerListener l) {
        m_listener = l;
    }

    /**
     * Remove the server listener
     *
     * @param l ServerListener
     */
    public final void removeServerListener(ServerListener l) {
        if (m_listener == l)
            m_listener = null;
    }

    /**
     * Add a new session listener to the network server.
     *
     * @param l SessionListener
     */
    public final void addSessionListener(SessionListener l) {

        //  Check if the session listener list is allocated
        if (m_sessListeners == null)
            m_sessListeners = new ArrayList<SessionListener>();
        m_sessListeners.add(l);
    }

    /**
     * Remove a session listener from the network server.
     *
     * @param l SessionListener
     */
    public final void removeSessionListener(SessionListener l) {

        //  Check if the listener list is valid
        if (m_sessListeners == null)
            return;
        m_sessListeners.remove(l);
    }

    /**
     * Fire a server event to the registered listener
     *
     * @param event int
     */
    protected final void fireServerEvent(int event) {

        //	Check if there is a listener registered with this server
        if (m_listener != null) {
            try {
                m_listener.serverStatusEvent(this, event);
            }
            catch (Exception ex) {
            }
        }
    }

    /**
     * Start the network server
     */
    public abstract void startServer();

    /**
     * Shutdown the network server
     *
     * @param immediate boolean
     */
    public abstract void shutdownServer(boolean immediate);

    /**
     * Trigger a closed session event to all registered session listeners.
     *
     * @param sess SrvSession
     */
    protected final void fireSessionClosedEvent(SrvSession sess) {

        //  Check if there are any listeners
        if (m_sessListeners == null || m_sessListeners.size() == 0)
            return;

        //  Inform all registered listeners
        for (int i = 0; i < m_sessListeners.size(); i++) {

            //	Get the current session listener
            try {
                SessionListener sessListener = m_sessListeners.get(i);
                sessListener.sessionClosed(sess);
            }
            catch (Exception ex) {
// TODO:				debugPrintln("Session listener error [closed]: " + ex.toString());
            }
        }
    }

    /**
     * Trigger a new session event to all registered session listeners.
     *
     * @param sess SrvSession
     */
    protected final void fireSessionLoggedOnEvent(SrvSession sess) {

        //  Check if there are any listeners
        if (m_sessListeners == null || m_sessListeners.size() == 0)
            return;

        //  Inform all registered listeners
        for (int i = 0; i < m_sessListeners.size(); i++) {

            //	Get the current session listener
            try {
                SessionListener sessListener = m_sessListeners.get(i);
                sessListener.sessionLoggedOn(sess);
            }
            catch (Exception ex) {
// TODO:				debugPrintln("Session listener error [logon]: " + ex.toString());
            }
        }
    }

    /**
     * Trigger a new session event to all registered session listeners.
     *
     * @param sess SrvSession
     */
    protected final void fireSessionOpenEvent(SrvSession sess) {

        //  Check if there are any listeners
        if (m_sessListeners == null || m_sessListeners.size() == 0)
            return;

        //  Inform all registered listeners
        for (int i = 0; i < m_sessListeners.size(); i++) {

            //	Get the current session listener
            try {
                SessionListener sessListener = m_sessListeners.get(i);
                sessListener.sessionCreated(sess);
            }
            catch (Exception ex) {
// TODO:				debugPrintln("Session listener error [open]: " + ex.toString());
            }
        }
    }
}
