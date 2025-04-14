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

import java.net.InetAddress;
import java.util.EnumSet;
import java.util.List;

import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.server.auth.SMBAuthenticator;
import org.filesys.server.auth.ISMBAuthenticator;
import org.filesys.smb.Dialect;
import org.filesys.smb.DialectSelector;
import org.filesys.smb.ServerType;
import org.filesys.smb.TcpipSMB;
import org.filesys.util.StringList;
import org.springframework.extensions.config.ConfigElement;
import org.filesys.server.config.ConfigId;
import org.filesys.server.config.ConfigSection;
import org.filesys.server.config.ConfigurationListener;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;

/**
 * SMB Server Configuration Section Class
 *
 * @author gkspencer
 */
public class SMBConfigSection extends ConfigSection {

    // SMB server configuration section name
    public static final String SectionName = "SMB";

    // Default client socket timeout
    public static final int DefSessionTimeout = 15 * 60 * 1000;    // 15 minutes, milliseconds

    // Minimum/maximum packets per run
    public static final int MinPacketsPerRun        = 1;
    public static final int MaxPacketsPerRun        = 32;

    //  Server name
    private String m_name;

    //  Server alias name(s)
    private StringList m_aliasNames;

    //  Server type, used by the host announcer
    private int m_srvType = ServerType.WorkStation + ServerType.Server;

    //  Server comment
    private String m_comment;

    //  Server domain
    private String m_domain;

    //  Network broadcast mask string
    private String m_broadcast;

    //  Announce the server to network neighborhood, announcement interval in minutes and announcer port
    private boolean m_announce;
    private int m_announceInterval;

    private int m_announcePort;

    //  Default SMB dialects to enable
    private DialectSelector m_dialects;

    //  Authenticator, used to authenticate users and share connections.
    private ISMBAuthenticator m_authenticator;
    private ConfigElement m_authParams;

    // Is the authenticator instance owned by this object?
    private boolean m_localAuthenticator;

    //  NetBIOS name server and host announcer debug enable
    private boolean m_nbDebug = false;
    private boolean m_announceDebug = false;

    //  Default session debugging setting
    private EnumSet<SMBSrvSession.Dbg> m_sessDebug;

    //  Name server port
    private int m_namePort = RFCNetBIOSProtocol.NAMING;

    //  Session port
    private int m_sessPort = RFCNetBIOSProtocol.SESSION;

    //  Datagram port
    private int m_nbDatagramPort = RFCNetBIOSProtocol.DATAGRAM;

    //  TCP/IP SMB port
    private int m_tcpSMBPort = TcpipSMB.PORT;

    //  Flags to indicate if NetBIOS, native TCP/IP SMB and/or Win32 NetBIOS should be enabled
    private boolean m_netBIOSEnable = true;
    private boolean m_tcpSMBEnable = false;
    private boolean m_win32NBEnable = false;

    //  Address to bind the SMB server to, if null all local addresses are used
    private InetAddress m_smbBindAddress;

    //  Address to bind the NetBIOS name server to, if null all addresses are used
    private InetAddress m_nbBindAddress;

    //  WINS servers
    private InetAddress m_winsPrimary;
    private InetAddress m_winsSecondary;

    //  Enable/disable Macintosh extension SMBs
    private boolean m_macExtensions;

    // Disable NIO based code
    private boolean m_disableNIO;

    // Client session socket timeout, in milliseconds
    private int m_clientSocketTimeout = DefSessionTimeout;

    // Enable TCP socket keep-alive for client connections
    private boolean m_clientKeepAlive = true;

    // Per session virtual circuit limit
    private int m_virtualCircuitLimit = SMBV1VirtualCircuitList.DefMaxCircuits;

    // Use ArrayOpenFileMap instead of HashedOpenFileMap
    private boolean m_disableHashedOpenFileMap;

    //--------------------------------------------------------------------------------
    //  Win32 NetBIOS configuration
    //
    //  Server name to register under Win32 NetBIOS, if not set the main server name is used
    private String m_win32NBName;

    //  LANA to be used for Win32 NetBIOS, if not specified the first available is used
    private int m_win32NBLANA = -1;

    //  Accept connections from all hosts or a specific host only
    private String m_win32NBAccept = "*";

    //  Send out host announcements via the Win32 NetBIOS interface
    private boolean m_win32NBAnnounce = false;
    private int m_win32NBAnnounceInterval;

    // Use Winsock NetBIOS interface if true, else use the Netbios() API interface
    private boolean m_win32NBUseWinsock = true;

    // Disable use of native code on Windows, do not use any JNI calls
    private boolean m_disableNativeCode = false;

    // List of terminal server/load balancer addresses, need special session setup handling
    private List<String> m_terminalServerList;
    private List<String> m_loadBalancerList;

    //--------------------------------------------------------------------------------
    // Thread pool configuration
    //
    // Maximum number of packets to process per thread pool request for a socket before the socket
    // events are re-enabled and the thread request exits processing
    private int m_maxPacketsPerRun = 4;     // original default, based on SMB1

    // Values for the Local Security Authority
    private String m_dnsName;
    private String m_forestName;

    /**
     * Class constructor
     *
     * @param config ServerConfiguration
     */
    public SMBConfigSection(ServerConfiguration config) {
        super(SectionName, config);

        // Set the default dialect list
        m_dialects = new DialectSelector();
        m_dialects.enableUpTo(Dialect.UpToSMBv1);
    }

    /**
     * Sets the terminal server list.
     *
     * @param terminalServerList List of Strings
     * @return int
     * @exception InvalidConfigurationException Failed to set the terminal server list
     */
    public final int setTerminalServerList(List<String> terminalServerList) throws InvalidConfigurationException {
        // Inform listeners, validate the configuration change

        int sts = fireConfigurationChange(ConfigId.SMBTerminalServerList, terminalServerList);
        m_terminalServerList = terminalServerList;

        // Return the change status
        return sts;
    }

    /**
     * Gets the terminal server list address.
     *
     * @return the terminal server list address
     */
    public final List<String> getTerminalServerList() {
        return m_terminalServerList;
    }

    /**
     * Sets the load balancer list.
     *
     * @param loadBalancerList List of Strings
     * @return int
     * @exception InvalidConfigurationException Failed to set the load balancer list
     */
    public final int setLoadBalancerList(List<String> loadBalancerList) throws InvalidConfigurationException {
        // Inform listeners, validate the configuration change

        int sts = fireConfigurationChange(ConfigId.SMBLoadBalancerList, loadBalancerList);
        m_loadBalancerList = loadBalancerList;

        // Return the change status
        return sts;
    }

    /**
     * Gets the load balancer list address.
     *
     * @return the load balancer list address
     */
    public final List<String> getLoadBalancerList() {
        return m_loadBalancerList;
    }

    /**
     * Get the authenticator object that is used to provide user and share connection authentication.
     *
     * @return ISMBAuthenticator
     */
    public final ISMBAuthenticator getAuthenticator() {
        return m_authenticator;
    }

    /**
     * Return the authenticator initialization parameters
     *
     * @return ConfigElement
     */
    public final ConfigElement getAuthenticatorParameters() {
        return m_authParams;
    }

    /**
     * Return the local address that the SMB server should bind to.
     *
     * @return java.net.InetAddress
     */
    public final InetAddress getSMBBindAddress() {
        return m_smbBindAddress;
    }

    /**
     * Return the local address that the NetBIOS name server should bind to.
     *
     * @return java.net.InetAddress
     */
    public final InetAddress getNetBIOSBindAddress() {
        return m_nbBindAddress;
    }

    /**
     * Return the network broadcast mask to be used for broadcast datagrams.
     *
     * @return String
     */
    public final String getBroadcastMask() {
        return m_broadcast;
    }

    /**
     * Return the server comment.
     *
     * @return String
     */
    public final String getComment() {
        return m_comment != null ? m_comment : "";
    }

    /**
     * Return the domain name.
     *
     * @return String
     */
    public final String getDomainName() {
        return m_domain;
    }

    /**
     * Return the enabled SMB dialects that the server will use when negotiating sessions.
     *
     * @return DialectSelector
     */
    public final DialectSelector getEnabledDialects() {
        return m_dialects;
    }

    /**
     * Return the name server port to listen on.
     *
     * @return int
     */
    public final int getNameServerPort() {
        return m_namePort;
    }

    /**
     * Return the NetBIOS datagram port
     *
     * @return int
     */
    public final int getDatagramPort() {
        return m_nbDatagramPort;
    }

    /**
     * Return the server name.
     *
     * @return String
     */
    public final String getServerName() {
        return m_name;
    }

    /**
     * Check if the server has any alias names configured
     *
     * @return boolean
     */
    public final boolean hasAliasNames() {
        return m_aliasNames != null ? true : false;
    }

    /**
     * Return the number of alias names configured
     *
     * @return int
     */
    public final int getNumberOfAliasNames() {
        return m_aliasNames != null ? m_aliasNames.numberOfStrings() : 0;
    }

    /**
     * Return the list of alias names for the server
     *
     * @return StringList
     */
    public final StringList getAliasNames() {
        return m_aliasNames;
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
     * Return the server debug flags.
     *
     * @return EnumSet&lt;SMBSrvSession.Dbg&gt;
     */
    public final EnumSet<SMBSrvSession.Dbg> getSessionDebugFlags() {
        return m_sessDebug;
    }

    /**
     * Get the session port to listen on.
     *
     * @return int
     */
    public final int getSessionPort() {
        return m_sessPort;
    }

    /**
     * Return the Win32 NetBIOS server name, if null the default server name will be used
     *
     * @return String
     */
    public final String getWin32ServerName() {
        return m_win32NBName;
    }

    /**
     * Return the Win32 NetBIOS allowed client name, defaults to '*' to allow any client to connect
     *
     * @return String
     */
    public final String getWin32ClientAccept() {
        return m_win32NBAccept;
    }

    /**
     * Determine if the server should be announced via Win32 NetBIOS, so that it appears under Network Neighborhood.
     *
     * @return boolean
     */
    public final boolean hasWin32EnableAnnouncer() {
        return m_win32NBAnnounce;
    }

    /**
     * Return the Win32 NetBIOS host announcement interval, in minutes
     *
     * @return int
     */
    public final int getWin32HostAnnounceInterval() {
        return m_win32NBAnnounceInterval;
    }

    /**
     * Return the Win3 NetBIOS LANA number to use, or -1 for the first available
     *
     * @return int
     */
    public final int getWin32LANA() {
        return m_win32NBLANA;
    }

    /**
     * Determine if the Win32 Netbios() API or Winsock Netbios calls should be used
     *
     * @return boolean
     */
    public final boolean useWinsockNetBIOS() {
        return m_win32NBUseWinsock;
    }

    /**
     * Determine if NIO based code should be disabled
     *
     * @return boolean
     */
    public final boolean hasDisableNIOCode() {
        return m_disableNIO;
    }

    /**
     * Determine if the primary WINS server address has been set
     *
     * @return boolean
     */
    public final boolean hasPrimaryWINSServer() {
        return m_winsPrimary != null ? true : false;
    }

    /**
     * Return the primary WINS server address
     *
     * @return InetAddress
     */
    public final InetAddress getPrimaryWINSServer() {
        return m_winsPrimary;
    }

    /**
     * Determine if the secondary WINS server address has been set
     *
     * @return boolean
     */
    public final boolean hasSecondaryWINSServer() {
        return m_winsSecondary != null ? true : false;
    }

    /**
     * Return the secondary WINS server address
     *
     * @return InetAddress
     */
    public final InetAddress getSecondaryWINSServer() {
        return m_winsSecondary;
    }

    /**
     * Determine if the SMB server should bind to a particular local address
     *
     * @return boolean
     */
    public final boolean hasSMBBindAddress() {
        return m_smbBindAddress != null ? true : false;
    }

    /**
     * Determine if the NetBIOS name server should bind to a particular local address
     *
     * @return boolean
     */
    public final boolean hasNetBIOSBindAddress() {
        return m_nbBindAddress != null ? true : false;
    }

    /**
     * Determine if NetBIOS name server debugging is enabled
     *
     * @return boolean
     */
    public final boolean hasNetBIOSDebug() {
        return m_nbDebug;
    }

    /**
     * Determine if host announcement debugging is enabled
     *
     * @return boolean
     */
    public final boolean hasHostAnnounceDebug() {
        return m_announceDebug;
    }

    /**
     * Determine if the server should be announced so that it appears under Network Neighborhood.
     *
     * @return boolean
     */
    public final boolean hasEnableAnnouncer() {
        return m_announce;
    }

    /**
     * Return the host announcement interval, in minutes
     *
     * @return int
     */
    public final int getHostAnnounceInterval() {
        return m_announceInterval;
    }

    /**
     * Return the host announcer port to use, or zero for the default port
     *
     * @return int
     */
    public final int getHostAnnouncerPort() {
        return m_announcePort;
    }

    /**
     * Determine if Macintosh extension SMBs are enabled
     *
     * @return boolean
     */
    public final boolean hasMacintoshExtensions() {
        return m_macExtensions;
    }

    /**
     * Determine if NetBIOS SMB is enabled
     *
     * @return boolean
     */
    public final boolean hasNetBIOSSMB() {
        return m_netBIOSEnable;
    }

    /**
     * Determine if TCP/IP SMB is enabled
     *
     * @return boolean
     */
    public final boolean hasTcpipSMB() {
        return m_tcpSMBEnable;
    }

    /**
     * Determine if Win32 NetBIOS is enabled
     *
     * @return boolean
     */
    public final boolean hasWin32NetBIOS() {
        return m_win32NBEnable;
    }

    /**
     * Return the TCP/IP SMB port
     *
     * @return int
     */
    public final int getTcpipSMBPort() {
        return m_tcpSMBPort;
    }

    /**
     * Return the client socket timeout, in millisconds
     *
     * @return int
     */
    public final int getSocketTimeout() {
        return m_clientSocketTimeout;
    }

    /**
     * Check if socket keep-alives should be enabled for client socket connections
     *
     * @return boolean
     */
    public final boolean hasSocketKeepAlive() { return m_clientKeepAlive; }

    /**
     * Return the maximum virtual circuits per session
     *
     * @return int
     */
    public final int getMaximumVirtualCircuits() {
        return m_virtualCircuitLimit;
    }

    /**
     * Return the maximum packets that will be processed per thread pool request run
     *
     * @return int
     */
    public final int getMaximumPacketsPerThreadRun() { return m_maxPacketsPerRun; }

    /**
     * Return the DNS name of the server
     *
     * @return String
     */
    public final String getDNSName() { return m_dnsName; }

    /**
     * Return the forest name for the server
     *
     * @return String
     */
    public final String getForestName() { return m_forestName; }

    /**
     * Check if native code calls are disabled
     *
     * @return boolean
     */
    public final boolean isNativeCodeDisabled() {
        return m_disableNativeCode;
    }

    /**
     * Determine if the HashedOpenFileMap should be disabled
     *
     * @return boolean
     */
    public final boolean hasDisableHashedOpenFileMap() {
        return m_disableHashedOpenFileMap;
    }

    /**
     * Set the authenticator to be used to authenticate users and share connections.
     *
     * @param authClass  String
     * @param params     ConfigElement
     * @param accessMode AuthMode
     * @param allowGuest boolean
     * @return int
     * @throws InvalidConfigurationException Failed to set the authenticator class
     */
    public final int setAuthenticator(String authClass, ConfigElement params, ISMBAuthenticator.AuthMode accessMode, boolean allowGuest)
            throws InvalidConfigurationException {

        //  Validate the authenticator class
        int sts = ConfigurationListener.StsIgnored;
        SMBAuthenticator auth = null;

        try {

            //  Load the authenticator class
            Object authObj = Class.forName(authClass).newInstance();

            if (authObj instanceof SMBAuthenticator) {

                //  Set the server authenticator
                auth = (SMBAuthenticator) authObj;
                auth.setAccessMode(accessMode);
                auth.setAllowGuest(allowGuest);
            }
            else
                throw new InvalidConfigurationException("Authenticator is not derived from required base class");
        }
        catch (ClassNotFoundException ex) {
            throw new InvalidConfigurationException("Authenticator class " + authClass + " not found");
        }
        catch (Exception ex) {
            throw new InvalidConfigurationException("Authenticator class error");
        }

        //  Initialize the authenticator using the parameter values
        auth.initialize(getServerConfiguration(), params);

        //  Inform listeners, validate the configuration change
        sts = setAuthenticator(auth);

        // Remember that the authenticator instance will need destroying
        m_localAuthenticator = true;

        //  Set initialization parameters
        m_authParams = params;

        //  Return the change status
        return sts;
    }

    /**
     * Set the authenticator to be used to authenticate users and share connections.
     *
     * @param auth the authenticator
     * @return int
     * @throws InvalidConfigurationException Failed to set the authenticator class
     */
    public final int setAuthenticator(ISMBAuthenticator auth)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBAuthenticator, auth);

        //  Set the server authenticator
        m_authenticator = auth;
        m_localAuthenticator = false;

        //  Return the change status
        return sts;
    }


    /**
     * Set the local address that the SMB server should bind to.
     *
     * @param addr java.net.InetAddress
     * @return int
     * @throws InvalidConfigurationException Failed to set the bind address
     */
    public final int setSMBBindAddress(InetAddress addr)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBBindAddress, addr);
        m_smbBindAddress = addr;

        //  Return the change status
        return sts;
    }

    /**
     * Set the local address that the NetBIOS name server should bind to.
     *
     * @param addr java.net.InetAddress
     * @return int
     * @throws InvalidConfigurationException Failed to set the NetBIOS bind address
     */
    public final int setNetBIOSBindAddress(InetAddress addr)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NetBIOSBindAddress, addr);
        m_nbBindAddress = addr;

        //  Return the change status
        return sts;
    }

    /**
     * Set the broadcast mask to be used for broadcast datagrams.
     *
     * @param mask String
     * @return int
     * @throws InvalidConfigurationException Failed to set the broadcast mask
     */
    public final int setBroadcastMask(String mask)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBBroadcastMask, mask);
        m_broadcast = mask;

        //  Return the change status
        return sts;
    }

    /**
     * Set the server comment.
     *
     * @param comment String
     * @return int
     * @throws InvalidConfigurationException Failed to set the server comment
     */
    public final int setComment(String comment)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBComment, comment);
        m_comment = comment;

        //  Return the change status
        return sts;
    }

    /**
     * Set the domain that the server is to belong to.
     *
     * @param domain String
     * @return int
     * @throws InvalidConfigurationException Failed to set the server domain
     */
    public final int setDomainName(String domain)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBDomain, domain);
        m_domain = domain;

        //  Return the change status
        return sts;
    }

    /**
     * Set the SMB dialects that the server may use when negotiating a session with a client.
     *
     * @param dialects DialectSelector
     * @return int
     * @throws InvalidConfigurationException Failed to set the enabled SMB dialects
     */
    public final int setEnabledDialects(DialectSelector dialects)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBDialects, dialects);
        m_dialects = new DialectSelector();
        m_dialects.copyFrom(dialects);

        //  Return the change status
        return sts;
    }

    /**
     * Enable/disable the host announcer.
     *
     * @param b boolean
     * @return int
     * @throws InvalidConfigurationException Failed to enable the host announcer
     */
    public final int setHostAnnouncer(boolean b)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        int sts = ConfigurationListener.StsIgnored;

        if (m_announce != b) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.SMBAnnceEnable, new Boolean(b));
            m_announce = b;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Set the host announcement interval, in minutes
     *
     * @param ival int
     * @return int
     * @throws InvalidConfigurationException Failed to set the host announcement interval
     */
    public final int setHostAnnounceInterval(int ival)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBAnnceInterval, new Integer(ival));
        m_announceInterval = ival;

        //  Return the change status
        return sts;
    }

    /**
     * Set the host announcer port
     *
     * @param port int
     * @return int
     * @throws InvalidConfigurationException Failed to set the host announcer port
     */
    public final int setHostAnnouncerPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBAnncePort, new Integer(port));
        m_announcePort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Set the name server port to listen on.
     *
     * @param port int
     * @return int
     * @throws InvalidConfigurationException Failed to set the name server port
     */
    public final int setNameServerPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NetBIOSNamePort, new Integer(port));
        m_namePort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Set the name server datagram port
     *
     * @param port int
     * @return int
     * @throws InvalidConfigurationException Failed to set the name server datagram port
     */
    public final int setDatagramPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NetBIOSDatagramPort, new Integer(port));
        m_nbDatagramPort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Enable/disable NetBIOS name server debug output
     *
     * @param ena boolean
     * @return int
     * @throws InvalidConfigurationException Failed to set the NetBIOS debug flag
     */
    public final int setNetBIOSDebug(boolean ena)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        int sts = ConfigurationListener.StsIgnored;

        if (m_nbDebug != ena) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.NetBIOSDebugEnable, new Boolean(ena));
            m_nbDebug = ena;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Enable/disable host announcement debug output
     *
     * @param ena boolean
     * @return int
     * @throws InvalidConfigurationException Failed to set the host announcer debug flag
     */
    public final int setHostAnnounceDebug(boolean ena)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        int sts = ConfigurationListener.StsIgnored;

        if (m_announceDebug != ena) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.SMBAnnceDebug, new Boolean(ena));
            m_announceDebug = ena;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Enable/disbale Macintosh extension SMBs
     *
     * @param ena boolean
     * @return int
     * @throws InvalidConfigurationException Failed to set the Macintosh extensions flag
     */
    public final int setMacintoshExtensions(boolean ena)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        int sts = ConfigurationListener.StsIgnored;

        if (m_macExtensions != ena) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.SMBMacExtEnable, new Boolean(ena));
            m_macExtensions = ena;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Set the server name.
     *
     * @param name String
     * @return int
     * @throws InvalidConfigurationException Failed to set the server name
     */
    public final int setServerName(String name)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBHostName, name);
        m_name = name;

        //  Return the change status
        return sts;
    }

    /**
     * Add a server alias name
     *
     * @param alias String
     * @return int
     * @throws InvalidConfigurationException Failed to add a server alias name
     */
    public final int addAliasName(String alias)
            throws InvalidConfigurationException {

        //  Check if the alias name list has been allocated
        if (m_aliasNames == null)
            m_aliasNames = new StringList();

        //  Check if the name already exists in the list
        int sts = ConfigurationListener.StsIgnored;

        if (m_aliasNames.containsString(alias) == false) {

            //  Inform listeners of the change
            sts = fireConfigurationChange(ConfigId.SMBAliasNames, alias);
            m_aliasNames.addString(alias);
        }

        //  Return the change status
        return sts;
    }

    /**
     * Add server alias names
     *
     * @param names StringList
     * @return int
     * @throws InvalidConfigurationException Failed to add server alias names
     */
    public final int addAliasNames(StringList names)
            throws InvalidConfigurationException {

        //  Check if the alias name list has been allocated
        if (m_aliasNames == null)
            m_aliasNames = new StringList();

        //  Add the names to the alias list
        int sts = ConfigurationListener.StsIgnored;

        for (int i = 0; i < names.numberOfStrings(); i++) {

            //  Add the current alias
            String curAlias = names.getStringAt(i);
            int curSts = addAliasName(curAlias);

            //  Keep the highest status
            if (curSts > sts)
                sts = curSts;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Set the server type flags.
     *
     * @param typ int
     * @return int
     * @throws InvalidConfigurationException Failed to set the server type flags
     */
    public final int setServerType(int typ)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBServerType, new Integer(typ));
        m_srvType = typ;

        //  Return the change status
        return sts;
    }

    /**
     * Set the debug flags to be used by the server.
     *
     * @param flags EnumSet&lt;SMBSrvSession.Dbg&gt;
     * @return int
     * @throws InvalidConfigurationException Failed to set the session debug flags
     */
    public final int setSessionDebugFlags(EnumSet<SMBSrvSession.Dbg> flags)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBSessionDebug, flags);
        m_sessDebug = flags;

        //  Return the change status
        return sts;
    }

    /**
     * Set the session port to listen on for incoming session requests.
     *
     * @param port int
     * @return int
     * @throws InvalidConfigurationException Failed to set the server session port
     */
    public final int setSessionPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NetBIOSSessionPort, new Integer(port));
        m_sessPort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Enable/disable the NetBIOS SMB support
     *
     * @param ena boolean
     * @return int
     * @throws InvalidConfigurationException Failed to set the NetBIOS SMB flag
     */
    public final int setNetBIOSSMB(boolean ena)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        int sts = ConfigurationListener.StsIgnored;

        if (m_netBIOSEnable != ena) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.SMBNetBIOSEnable, new Boolean(ena));
            m_netBIOSEnable = ena;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Enable/disable the TCP/IP SMB support
     *
     * @param ena boolean
     * @return int
     * @throws InvalidConfigurationException Failed to set the TCPIP SMB flag
     */
    public final int setTcpipSMB(boolean ena)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        int sts = ConfigurationListener.StsIgnored;

        if (m_tcpSMBEnable != ena) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.SMBTCPEnable, new Boolean(ena));
            m_tcpSMBEnable = ena;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Enable/disable the Win32 NetBIOS SMB support
     *
     * @param ena boolean
     * @return int
     * @throws InvalidConfigurationException Failed to set the Win32 NetBIOS SMB flag
     */
    public final int setWin32NetBIOS(boolean ena)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        int sts = ConfigurationListener.StsIgnored;

        if (m_win32NBEnable != ena) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.SMBWin32NetBIOS, new Boolean(ena));
            m_win32NBEnable = ena;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Set the Win32 NetBIOS file server name
     *
     * @param name String
     * @return int
     * @throws InvalidConfigurationException Failed to set the Win32 NetBIOS server name
     */
    public final int setWin32NetBIOSName(String name)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBWin32NBName, name);
        m_win32NBName = name;

        //  Return the change status
        return sts;
    }

    /**
     * Set the Win32 NetBIOS accepted client name
     *
     * @param name String
     * @return int
     * @throws InvalidConfigurationException Failed to set the Win32 NetBIOS accepted client name
     */
    public final int setWin32NetBIOSClientAccept(String name)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBWin32NBAccept, name);
        m_win32NBAccept = name;

        //  Return the change status
        return sts;
    }

    /**
     * Enable/disable the Win32 NetBIOS host announcer.
     *
     * @param b boolean
     * @return int
     * @throws InvalidConfigurationException Failed to set the Win32 NetBIOS host announcer flag
     */
    public final int setWin32HostAnnouncer(boolean b)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        int sts = ConfigurationListener.StsIgnored;

        if (m_win32NBAnnounce != b) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.SMBWin32NBAnnounce, new Boolean(b));
            m_win32NBAnnounce = b;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Set the Win32 LANA to be used by the Win32 NetBIOS interface
     *
     * @param ival int
     * @return int
     * @throws InvalidConfigurationException Failed to set the Win32 LANA
     */
    public final int setWin32LANA(int ival)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBWin32NBLana, new Integer(ival));
        m_win32NBLANA = ival;

        //  Return the change status
        return sts;
    }

    /**
     * Set the Win32 NetBIOS host announcement interval, in minutes
     *
     * @param ival int
     * @return int
     * @throws InvalidConfigurationException Failed to set the Win32 NetBIOS host announcement interval
     */
    public final int setWin32HostAnnounceInterval(int ival)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBWin32NBAnnounce, new Integer(ival));
        m_win32NBAnnounceInterval = ival;

        //  Return the change status
        return sts;
    }

    /**
     * Set the Win32 NetBIOS interface to use either Winsock NetBIOS or the Netbios() API calls
     *
     * @param useWinsock boolean
     * @return int
     * @throws InvalidConfigurationException Failed to set the Winsock NetBIOS flag
     */
    public final int setWin32WinsockNetBIOS(boolean useWinsock)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBWin32NBWinsock, new Boolean(useWinsock));
        m_win32NBUseWinsock = useWinsock;

        //  Return the change status
        return sts;
    }

    /**
     * Set the TCP/IP SMB port
     *
     * @param port int
     * @return int
     * @throws InvalidConfigurationException Failed to set the TCPIP SMB port
     */
    public final int setTcpipSMBPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBTCPPort, new Integer(port));
        m_tcpSMBPort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Set the primary WINS server address
     *
     * @param addr InetAddress
     * @return int
     * @throws InvalidConfigurationException Failed to set the primary WINS server address
     */
    public final int setPrimaryWINSServer(InetAddress addr)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NetBIOSWINSPrimary, addr);
        m_winsPrimary = addr;

        //  Return the change status
        return sts;
    }

    /**
     * Set the secondary WINS server address
     *
     * @param addr InetAddress
     * @return int
     * @throws InvalidConfigurationException Failed to set the secondary WINS server address
     */
    public final int setSecondaryWINSServer(InetAddress addr)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.NetBIOSWINSSecondary, addr);
        m_winsSecondary = addr;

        //  Return the change status
        return sts;
    }

    /**
     * Set the disable NIO code flag
     *
     * @param disableNIO boolean
     * @return int
     * @throws InvalidConfigurationException Failed to set the disable NIO flag
     */
    public final int setDisableNIOCode(boolean disableNIO)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBDisableNIO, new Boolean(disableNIO));
        m_disableNIO = disableNIO;

        //  Return the change status
        return sts;
    }

    /**
     * Set the client socket timeout, in milliseconds
     *
     * @param tmo int
     * @return int
     * @throws InvalidConfigurationException Failed to set the client socket timeout
     */
    public final int setSocketTimeout(int tmo)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBSocketTimeout, new Integer(tmo));
        m_clientSocketTimeout = tmo;

        //  Return the change status
        return sts;
    }

    /**
     * Enable or disable use of TCP socket keep-alives on client socket connections
     *
     * @param ena boolean
     * @return int
     * @throws InvalidConfigurationException Failed to set the client socket keep-alive value
     */
    public final int setSocketKeepAlive(boolean ena)
        throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBSocketKeepAlive, new Boolean(ena));
        m_clientKeepAlive = ena;

        //  Return the change status
        return sts;
    }

    /**
     * Set the native code disabled flag, to prevent calls to the Win32NetBIOS DLL
     *
     * @param noNative boolean
     */
    public final void setNativeCodeDisabled(boolean noNative) {
        m_disableNativeCode = noNative;
    }

    /**
     * Set the maximum virtual circuits per session
     *
     * @param maxVC int
     * @return int
     * @throws InvalidConfigurationException Failed to set the maximum virtual circuits per session
     */
    public final int setMaximumVirtualCircuits(int maxVC)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBMaxVirtualCircuit, new Integer(maxVC));
        m_virtualCircuitLimit = maxVC;

        //  Return the change status
        return sts;
    }

    /**
     * Set the maximum packets that will be processed per thread pool request run
     *
     * @param maxPkts int
     * @return int
     * @throws InvalidConfigurationException Failed to set the maximum packets per thread run
     */
    public final int setMaximumPacketsPerThreadRun(int maxPkts)
        throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBPacketsPerThreadRun, new Integer(maxPkts));
        m_maxPacketsPerRun = maxPkts;

        //  Return the change status
        return sts;
    }

    /**
     * Set the DNS name of the server
     *
     * @param dnsName String
     */
    public final void setDNSName(String dnsName) { m_dnsName = dnsName; }

    /**
     * Set the forest name of the server
     *
     * @param forestName String
     */
    public final void setForestName(String forestName) { m_forestName = forestName; }

    /**
     * Set the disable HashedOpenFileMap flag
     *
     * @param disableHashedOFM boolean
     * @return int
     * @throws InvalidConfigurationException Failed to set the disable
     *                                       HashedOpenFileMap flag
     */
    public final int setDisableHashedOpenFileMap(boolean disableHashedOFM) throws InvalidConfigurationException {

        // Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.SMBDisableHashedOFM, new Boolean(disableHashedOFM));
        m_disableHashedOpenFileMap = disableHashedOFM;

        // Return the change status
        return sts;
    }

    /**
     * Close the configuration section
     */
    public final void closeConfig() {

        // Close the authenticator
        if (m_authenticator != null && m_localAuthenticator) {
            m_authenticator.closeAuthenticator();
            m_authenticator = null;
        }
    }
}
