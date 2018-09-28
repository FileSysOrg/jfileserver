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

package org.filesys.server.auth.passthru;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.filesys.debug.Debug;
import org.filesys.netbios.NetBIOSName;
import org.filesys.netbios.NetBIOSNameList;
import org.filesys.netbios.NetBIOSSession;
import org.filesys.smb.PCShare;
import org.filesys.util.IPAddress;

/**
 * Passthru Servers Class
 *
 * <p>Contains a list of one or more servers that are used for passthru authentication. The status of the
 * servers is tracked so that offline servers are not used but periodically monitored so that they can be
 * returned to the online list of servers.
 *
 * <p>The server list may be initialized from a list of server names or addresses, or by specifying a domain
 * name in which case the primary and backup domain controllers will be used.
 *
 * @author GKSpencer
 */
public class PassthruServers {
    // Default timeout for setting up a new session
    private static final int DefaultConnectTimeout = 5000;  // 5 seconds

    // Default interval to check if offline servers
    private static final long DefaultOfflineCheckInterval = 5 * 60000;  // 5 minutes

    // List of online and offline authentication servers
    private List<PassthruServerDetails> m_onlineList;
    private List<PassthruServerDetails> m_offlineList;

    // Timeout value when opening a session to an authentication server, in milliseconds
    private int m_tmo = DefaultConnectTimeout;

    // Domain name, if using domain controllers
    private String m_domain;

    // Offline server check interval
    private long m_offlineCheckInterval = DefaultOfflineCheckInterval;

    // Offline server checker thread
    PassthruOfflineChecker m_offlineChecker;

    // Debug output enable
    private boolean m_debug;

    // Null domain uses any available server option
    private boolean m_nullDomainUseAnyServer;

    /**
     * Inner class used to periodically check offline servers to see if they are back online
     */
    class PassthruOfflineChecker extends Thread {

        // Thread shutdown request flag
        private boolean m_ishutdown;

        /**
         * Default constructor
         */
        PassthruOfflineChecker() {
            setDaemon(true);
            setName("PassthruOfflineChecker");
            start();
        }

        /**
         * Main thread code
         */
        public void run() {

            // Loop until shutdown
            m_ishutdown = false;

            while (m_ishutdown == false) {

                // Sleep for a while
                try {
                    sleep(m_offlineCheckInterval);
                }
                catch (InterruptedException ex) {
                }

                // Check if shutdown has been requested
                if (m_ishutdown == true)
                    continue;

                // Check if there are any offline servers to check
                if (getOfflineServerCount() > 0) {

                    // Enumerate the offline server list
                    int idx = 0;
                    PassthruServerDetails offlineServer = null;
                    PCShare authShare = new PCShare("", "IPC$", "", "");
                    AuthenticateSession authSess = null;

                    while (idx < getOfflineServerCount()) {

                        // Get an offline server from the list
                        offlineServer = (PassthruServerDetails) m_offlineList.get(idx);

                        if (offlineServer != null) {

                            try {
                                // Set the target host name
                                authShare.setNodeName(offlineServer.getAddress().getHostAddress());

                                // Try and connect to the authentication server
                                authSess = AuthSessionFactory.OpenAuthenticateSession(authShare, getConnectionTimeout());

                                // Close the session
                                try {
                                    authSess.CloseSession();
                                }
                                catch (Exception ex) {
                                }

                                // Authentication server is online, move it to the online list
                                serverOnline(offlineServer);
                            }
                            catch (Exception ex) {

                                // Debug
                                if (hasDebug())
                                    Debug.println("Passthru offline check failed for " + offlineServer.getName());
                            }

                            // Check if the server is now online
                            if (offlineServer.isOnline() == false)
                                idx++;
                        }
                    }
                }
            }

            // Debug
            if (hasDebug())
                Debug.println("Passthru offline checker thread closed");
        }

        /**
         * Shutdown the checker thread
         */
        public final void shutdownRequest() {
            m_ishutdown = true;
            this.interrupt();
        }

        /**
         * Wakeup the offline checker thread to process the offline server list
         */
        public final void processOfflineServers() {
            this.interrupt();
        }
    }

    /**
     * Default constructor
     */
    public PassthruServers() {
        commonInit();
    }

    /**
     * Class constructor
     *
     * @param checkInterval In seconds
     */
    public PassthruServers(int checkInterval) {

        // Set the offline checker wakeup interval
        m_offlineCheckInterval = ((long) checkInterval) * 1000L;

        // Common initialization
        commonInit();
    }

    /**
     * Common constructor
     */
    private void commonInit() {

        // Create the server lists
        m_onlineList = new ArrayList<PassthruServerDetails>();
        m_offlineList = new ArrayList<PassthruServerDetails>();

        // Create and start the offline server checker thread
        m_offlineChecker = new PassthruOfflineChecker();
    }

    /**
     * Return the count of online authentication servers
     *
     * @return int
     */
    public final int getOnlineServerCount() {
        return m_onlineList.size();
    }

    /**
     * Return the count of offline authentication servers
     *
     * @return int
     */
    public final int getOfflineServerCount() {
        return m_offlineList.size();
    }

    /**
     * Return the total count of online and offline servers
     *
     * @return int
     */
    public final int getTotalServerCount() {
        return m_onlineList.size() + m_offlineList.size();
    }

    /**
     * Determine if there are online servers
     *
     * @return boolean
     */
    public final boolean hasOnlineServers() {
        return m_onlineList.size() > 0 ? true : false;
    }

    /**
     * Return the connection timeout, in milliseconds
     *
     * @return int
     */
    public final int getConnectionTimeout() {
        return m_tmo;
    }

    /**
     * Determine if the authentication servers are domain controllers
     *
     * @return boolean
     */
    public final boolean isDomainAuthentication() {
        return m_domain != null ? true : false;
    }

    /**
     * Return the domain name
     *
     * @return String
     */
    public final String getDomain() {
        return m_domain;
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Check if a null domain should use any available passthru server
     *
     * @return boolean
     */
    public final boolean getNullDomainUseAnyServer() {
        return m_nullDomainUseAnyServer;
    }

    /**
     * Open a new session to an authentication server
     *
     * @return AuthenticateSession
     */
    public final AuthenticateSession openSession() {
        return openSession(false, null);
    }

    /**
     * Open a new session to an authentication server
     *
     * @param useExtSec    boolean
     * @param clientDomain String
     * @return AuthenticateSession
     */
    public final AuthenticateSession openSession(boolean useExtSec, String clientDomain) {
        // Get the details of an authentication server to connect to
        PassthruServerDetails passthruServer = null;

        if (clientDomain != null)
            passthruServer = getAuthenticationServer(clientDomain);
        else
            passthruServer = getAuthenticationServer();

        if (passthruServer == null)
            return null;

        // Debug
        if (hasDebug())
            Debug.println("Open authenticate session to " + passthruServer + (clientDomain != null ? " (routed for client domain " + clientDomain + ")" : ""));

        // Open a new authentication session to the server
        AuthenticateSession authSess = null;

        while (authSess == null && passthruServer != null && hasOnlineServers()) {

            PCShare authShare = new PCShare(passthruServer.getAddress().getHostAddress(), "IPC$", "", "");

            try {
                // Open a session to the current authentication server
                authSess = AuthSessionFactory.OpenAuthenticateSession(authShare, getConnectionTimeout());

                // Update the passthru statistics
                passthruServer.incrementAuthenticationCount();
            }
            catch (Exception ex) {
                // Debug
                if (hasDebug())
                    Debug.println("Failed to connect to " + passthruServer + " : " + ex.getMessage());

                // Failed to connect to the current authentication server, mark the server as offline
                serverOffline(passthruServer);
            }

            // Check if we have a valid session
            if (authSess == null) {
                // Try another authentication server
                passthruServer = getAuthenticationServer();

                // Debug
                if (hasDebug())
                    Debug.println("Trying authentication server " + passthruServer);
            }
        }

        // Return the authentication session
        return authSess;
    }

    /**
     * Return the details of an online server to use for authentication
     *
     * @return PassthruServerDetails
     */
    protected PassthruServerDetails getAuthenticationServer() {
        // Check if any available passthru server or a passthru server that does not have a domain name set
        // should be used
        PassthruServerDetails passthruServer = null;

        if (getNullDomainUseAnyServer()) {
            // Use the first available passthru server
            synchronized (m_onlineList) {
                // Rotate the head of the list and return the new head of list server details
                if (m_onlineList.size() > 1)
                    m_onlineList.add(m_onlineList.remove(0));
                if (m_onlineList.size() > 0)
                    passthruServer = (PassthruServerDetails) m_onlineList.get(0);
            }
        } else {
            // Search for an online passthru server that does not have a domain name set
            synchronized (m_onlineList) {
                int idx = 0;

                while (idx < m_onlineList.size() && passthruServer == null) {
                    // Get the current passthru server details
                    PassthruServerDetails curServer = m_onlineList.get(idx);

                    if (curServer.getDomain() == null || curServer.getDomain().length() == 0) {
                        // Use this passthru server
                        passthruServer = curServer;

                        // Move to the back of the list
                        m_onlineList.add(m_onlineList.remove(idx));
                    }

                    // Update the server index
                    idx++;
                }
            }
        }

        // Return the selected passthru server, or null if not available
        return passthruServer;
    }

    /**
     * Return the details of an online server to use for authentication of the specified client
     * domain
     *
     * @param clientDomain String
     * @return PassthruServerDetails
     */
    protected PassthruServerDetails getAuthenticationServer(String clientDomain) {
        // Rotate the head of the list and return the new head of list server details
        PassthruServerDetails passthruServer = null;

        synchronized (m_onlineList) {
            int idx = 0;

            while (idx < m_onlineList.size() && passthruServer == null) {
                // Get the current passthru server details
                PassthruServerDetails curServer = m_onlineList.get(idx);

                if (curServer.getDomain() != null && curServer.getDomain().equals(clientDomain)) {
                    // Use this passthru server
                    passthruServer = curServer;

                    // Move to the back of the list
                    m_onlineList.add(m_onlineList.remove(idx));
                }

                // Update the server index
                idx++;
            }
        }

        if (passthruServer == null) {
            Debug.println("No server found for domain " + clientDomain);
        }
        return passthruServer;
    }

    /**
     * Move a server from the list of online servers to the offline list
     *
     * @param server PassthruServerDetails
     */
    protected final void serverOffline(PassthruServerDetails server) {
        // Set the server status
        server.setOnline(false);

        // Remove the server from the online list
        synchronized (m_onlineList) {
            m_onlineList.remove(server);
        }

        // Add it to the offline list
        synchronized (m_offlineList) {
            m_offlineList.add(server);
        }

        // Debug
        if (hasDebug())
            Debug.println("Passthru server offline, " + server);
    }

    /**
     * Move a server from the list of offline servers to the online list
     *
     * @param server PassthruServerDetails
     */
    protected final void serverOnline(PassthruServerDetails server) {
        // Set the server status
        server.setOnline(true);

        // Remove the server from the offline list
        synchronized (m_offlineList) {
            m_offlineList.remove(server);
        }

        // Add it to the online list
        synchronized (m_onlineList) {
            m_onlineList.add(server);
        }

        // Debug
        if (hasDebug())
            Debug.println("Passthru server online, " + server);
    }

    /**
     * Set the session connect timeout value, in milliseconds
     *
     * @param tmo int
     */
    public final void setConnectionTimeout(int tmo) {
        m_tmo = tmo;
    }

    /**
     * Set the offline check interval, in seconds
     *
     * @param interval long
     */
    public final void setOfflineCheckInterval(long interval) {
        m_offlineCheckInterval = interval * 1000L;

        // Wakeup the offline checked thread to pickup the new interval
        m_offlineChecker.processOfflineServers();
    }

    /**
     * Set the list of servers to use for passthru authentication using a comma delimeted list
     * of server names/addresses
     *
     * @param servers String
     */
    public final void setServerList(String servers) {
        // Split the server list into seperate name/address tokens
        StringTokenizer tokens = new StringTokenizer(servers, ",");

        while (tokens.hasMoreTokens()) {
            // Get the current server name/address
            String srvName = tokens.nextToken().trim();

            // Check if the server address also contains a domain name
            String domain = null;
            int pos = srvName.indexOf('\\');

            if (pos != -1) {
                domain = srvName.substring(0, pos);
                srvName = srvName.substring(pos + 1);
            }

            // If a name a has been specified convert it to an address, if an address has been specified
            // then convert to a name.
            InetAddress srvAddr = null;

            if (IPAddress.isNumericAddress(srvName)) {
                // Get the server name
                try {
                    // Get the host address and name
                    srvAddr = InetAddress.getByName(srvName);
                    srvName = srvAddr.getHostName();
                }
                catch (UnknownHostException ex) {
                    // Debug
                    if (hasDebug())
                        Debug.println("Passthru failed to find name/address for " + srvName);
                }
            } else {
                // Get the server address
                try {
                    srvAddr = InetAddress.getByName(srvName);
                }
                catch (UnknownHostException ex) {
                    // Debug
                    if (hasDebug())
                        Debug.println("Passthru failed to find address for " + srvName);
                }
            }

            // Create the passthru server details and add to the list of offline servers
            if (srvName != null && srvAddr != null) {
                // Create the passthru server details
                PassthruServerDetails passthruServer = new PassthruServerDetails(srvName, domain, srvAddr, false);
                m_offlineList.add(passthruServer);

                // Debug
                if (hasDebug())
                    Debug.println("Added passthru server " + passthruServer);
            }
        }

        // Wakeup the server checker thread to check each of the servers just added and move servers that are
        // accessible to the online list
        m_offlineChecker.processOfflineServers();
    }

    /**
     * Set the domain to use for passthru authentication
     *
     * @param domain String
     * @exception IOException Failed to set the domain
     */
    public final void setDomain(String domain)
            throws IOException {

        // DEBUG
        if (hasDebug())
            Debug.println("Passthru finding domain controller for " + domain + " ...");

        // Find a domain controller or the browse master
        NetBIOSName nbName = null;

        try {
            // Find a domain controller
            nbName = NetBIOSSession.FindName(domain, NetBIOSName.DomainControllers, getConnectionTimeout());

            // DEBUG
            if (hasDebug())
                Debug.println("  Found " + nbName.numberOfAddresses() + " domain controller(s)");
        }
        catch (IOException ex) {
        }

        //  If we did not find a domain controller look for the browse master
        if (nbName == null) {

            try {
                // Try and find the browse master for the workgroup
                nbName = NetBIOSSession.FindName(domain, NetBIOSName.MasterBrowser, getConnectionTimeout());

                // DEBUG
                if (hasDebug())
                    Debug.println("  Found browse master at " + nbName.getIPAddressString(0));
            }
            catch (IOException ex) {
                throw new IOException("Failed to find domain controller or browse master for " + domain);
            }
        }

        // Add the passthru server(s)
        //
        // Try and convert the address to a name for each domain controller
        for (int i = 0; i < nbName.numberOfAddresses(); i++) {
            // Get the domain controller name
            InetAddress dcAddr = null;
            String dcName = null;

            try {
                // Get the current domain controller address
                dcAddr = InetAddress.getByName(nbName.getIPAddressString(i));

                // Get the list of NetBIOS names for the domain controller
                NetBIOSNameList nameList = NetBIOSSession.FindNamesForAddress(dcAddr.getHostAddress());
                NetBIOSName dcNBName = nameList.findName(NetBIOSName.FileServer, false);

                if (dcNBName != null)
                    dcName = dcNBName.getName();
            }
            catch (UnknownHostException ex) {
                // Debug
                if (hasDebug())
                    Debug.println("Invalid address for server " + nbName.getIPAddressString(i));
            }
            catch (Exception ex) {
                // Failed to get domain controller name, use the address
                dcName = dcAddr.getHostAddress();

                // Debug
                if (hasDebug())
                    Debug.println("Failed to get NetBIOS name for server " + dcAddr);
            }

            // Create a passthru server entry for the domain controller
            if (dcAddr != null) {
                // Create the passthru authentication server record
                PassthruServerDetails passthruServer = new PassthruServerDetails(dcName, domain, dcAddr, false);
                m_offlineList.add(passthruServer);

                // Debug
                if (hasDebug())
                    Debug.println("Added passthru server " + passthruServer);
            }
        }

        // Wakeup the server checker thread to check each of the servers just added and move servers that are
        // accessible to the online list
        m_offlineChecker.processOfflineServers();
    }

    /**
     * Set the null domain to use any available server option
     *
     * @param nullDomain boolean
     */
    public final void setNullDomainUseAnyServer(boolean nullDomain) {
        m_nullDomainUseAnyServer = nullDomain;
    }

    /**
     * Enable/disbale debug output
     *
     * @param dbg boolean
     */
    public final void setDebug(boolean dbg) {
        m_debug = dbg;
    }

    /**
     * Shutdown passthru authentication
     */
    public final void shutdown() {
        // Shutdown the offline server checker thread
        m_offlineChecker.shutdownRequest();

        // Clear the online and offline server lists
        m_onlineList.clear();
        m_offlineList.clear();
    }

    /**
     * Return the passthru server details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");

        if (isDomainAuthentication()) {
            str.append("Domain:");
            str.append(getDomain());
        } else
            str.append("Servers:");

        str.append(",Online=");
        str.append(getOnlineServerCount());
        str.append(",Offline=");
        str.append(getOfflineServerCount());

        str.append(",nullDomain=");
        str.append(getNullDomainUseAnyServer() ? "On" : "Off");

        str.append("]");

        return str.toString();
    }
}
