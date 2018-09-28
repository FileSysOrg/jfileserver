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

import org.filesys.debug.Debug;
import org.filesys.netbios.NetBIOSName;
import org.filesys.netbios.NetBIOSNameList;
import org.filesys.netbios.NetBIOSSession;
import org.filesys.netbios.NetworkSession;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.server.auth.PasswordEncryptor;
import org.filesys.smb.*;
import org.filesys.smb.PacketTypeV1;
import org.filesys.util.IPAddress;
import org.filesys.util.StringList;

/**
 * <p>The AuthSessionFactory static class is used to create sessions to remote shared
 * resources using the SMB/CIFS protocol. A PCShare object is used to specify the
 * remote node and share details, as well as required access control details.
 *
 * @author gkspencer
 */
public final class AuthSessionFactory {

    //	Constants
    private static final int BROADCAST_LOOKUP_TIMEOUT = 4000;    // ms

    //	Default SMB dialect list
    private static DialectSelector m_defDialects;

    //  Session index, used to make each session request call id unique
    private static int m_sessIdx = 1;

    //	Local domain name, if known
    private static String m_localDomain = null;

    //	Local domains browse master, if known
    private static String m_localBrowseMaster = null;

    // 	Default session packet buffer size
    private static int m_defPktSize = 4096 + RFCNetBIOSProtocol.HEADER_LEN;

    //	List of local TCP/IP addresses
    private static InetAddress[] m_localAddrList;

    //	Password encryptor
    private static PasswordEncryptor m_encryptor = new PasswordEncryptor();

    //  If this is an evaluation version then only 5 sessions may be opened before an exception is
    //  thrown.
    //
    //  FULL VERSION = -1
    //  DEMO VERSION = 5
    private static int m_evalCount = -1;        //	full
//  private static int m_evalCount = 5;		//	demo

    static {

        //	Initialize the default dialect list
        m_defDialects = new DialectSelector();
        m_defDialects.AddDialect(Dialect.Core);
        m_defDialects.AddDialect(Dialect.CorePlus);
        m_defDialects.AddDialect(Dialect.DOSLanMan1);
        m_defDialects.AddDialect(Dialect.DOSLanMan2);
        m_defDialects.AddDialect(Dialect.LanMan1);
        m_defDialects.AddDialect(Dialect.LanMan2);
        m_defDialects.AddDialect(Dialect.LanMan2_1);
        m_defDialects.AddDialect(Dialect.NT);
    }

    //	Default user name, password and domain used by methods that create their own connections.
    private static String m_defUserName = "";
    private static String m_defPassword = "";
    private static String m_defDomain = "?";

    //	Primary and secondary protocols to connect with
    private static int m_primaryProto = Protocol.TCPNetBIOS;
    private static int m_secondaryProto = Protocol.NativeSMB;

    //	Session factory debug flag
    private static boolean m_debug = false;

    // NetBIOS port to connect to when setting up a new session. The default port is 139.
    private static int m_netbiosPort = RFCNetBIOSProtocol.SESSION;

    //	NetBIOS name scope
    private static String m_netBIOSScopeId = null;

    /**
     * Build an SMB negotiate dialect packet.
     *
     * @param pkt  SMBPacket to build the negotiate request
     * @param dlct SMB dialects to negotiate
     * @param pid  Process id to be used by this new session
     * @return StringList
     */
    private final static StringList BuildNegotiatePacket(SMBPacket pkt, DialectSelector dlct, int pid) {

        //  Initialize the SMB packet header fields
        pkt.setCommand(PacketTypeV1.Negotiate);
        pkt.setProcessId(pid);

        //	If the NT dialect is enabled set the Unicode flag in the request flags
        if (dlct.hasDialect(Dialect.NT))
            pkt.setFlags2(SMBPacket.FLG2_UNICODE);

        //  Build the SMB dialect list
        StringBuffer dia = new StringBuffer();
        StringList dStrs = new StringList();

        //  Loop through all SMB dialect types and add the appropriate dialect strings
        //  to the negotiate packet.
        int d = Dialect.Core;

        while (d < Dialect.Max) {

            //  Check if the current dialect is selected
            if (dlct.hasDialect(d)) {

                //  Search the SMB dialect type string list and add all strings for the
                //  current dialect
                for (int i = 0; i < Dialect.NumberOfDialects(); i++) {

                    //  Check if the current dialect string should be added to the list
                    if (Dialect.DialectType(i) == d) {

                        //	Get the current SMB dialect string
                        String curDialect = Dialect.DialectString(i);
                        dStrs.addString( curDialect);

                        //	Add the current SMB dialect type string to the negotiate packet
                        dia.append(DataType.Dialect);
                        dia.append(curDialect);
                        dia.append((char) 0x00);
                    }
                }
            }

            //  Update the current dialect type
            d++;
        }

        //  Copy the dialect strings to the SMB packet
        pkt.setBytes(dia.toString().getBytes());

        //	Return the dialect strings
        return dStrs;
    }

    /**
     * Return the default SMB packet size
     *
     * @return Default SMB packet size to allocate.
     */
    protected final static int DefaultPacketSize() {
        return m_defPktSize;
    }

    /**
     * Disable session factory debugging.
     */
    public final static void disableDebug() {
        m_debug = false;
    }

    /**
     * Enable session factory debug output.
     */
    public final static void enableDebug() {
        m_debug = true;
    }

    /**
     * Return the list of SMB dialects that will be negotiated when a new
     * session is created.
     *
     * @return DialectSelector    List of enabled SMB dialects.
     */
    public final static DialectSelector getDefaultDialects() {
        return m_defDialects;
    }

    /**
     * Return the default domain name
     *
     * @return String
     */
    public static String getDefaultDomain() {
        return m_defDomain;
    }

    /**
     * Return the default password.
     *
     * @return java.lang.String
     */
    public static String getDefaultPassword() {
        return m_defPassword;
    }

    /**
     * Return the default user name.
     *
     * @return java.lang.String
     */
    public static String getDefaultUserName() {
        return m_defUserName;
    }

    /**
     * Return the NetBIOS scope id, or null if not set
     *
     * @return String
     */
    public static String getNetBIOSNameScope() {
        return m_netBIOSScopeId;
    }

    /**
     * Return the NetBIOS socket number that new sessions are connected to.
     *
     * @return int NetBIOS session socket number.
     */
    public static int getNetBIOSPort() {
        return m_netbiosPort;
    }

    /**
     * Return the primary connection protocol (either Protocol.TCPNetBIOS or Protocol.NativeSMB)
     *
     * @return int
     */
    public static final int getPrimaryProtocol() {
        return m_primaryProto;
    }

    /**
     * Return the secondary connection protocol (Protocol.TCPNetBIOS, Protocol.NativeSMB or Protocol.None)
     *
     * @return int
     */
    public static final int getSecondaryProtocol() {
        return m_secondaryProto;
    }

    /**
     * Return the next session id
     *
     * @return int
     */
    private static synchronized int getSessionId() {
        int sessId = m_sessIdx++ + (NetBIOSSession.getJVMIndex() * 100);
        return sessId;
    }

    /**
     * Get the list of local TCP/IP addresses
     *
     * @return InetAddress[]
     */
    private static synchronized InetAddress[] getLocalTcpipAddresses() {

        //	Get the list of local TCP/IP addresses
        if (m_localAddrList == null) {
            try {
                m_localAddrList = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
            }
            catch (UnknownHostException ex) {
            }
        }

        //	Return the address list
        return m_localAddrList;
    }

    /**
     * Determine if session factory debugging is enabled.
     *
     * @return boolean
     */
    public final static boolean hasDebug() {
        return m_debug;
    }

    /**
     * Determine if the NetBIOS name scope is set
     *
     * @return boolean
     */
    public final static boolean hasNetBIOSNameScope() {
        return m_netBIOSScopeId != null ? true : false;
    }

    /**
     * Determine if SMB session debugging is enabled.
     *
     * @return true if SMB session debugging is enabled, else false.
     */
    public final static boolean hasSessionDebug() {
        return AuthenticateSession.hasDebug();
    }

    /**
     * Open a session to a remote server, negotiate an SMB dialect and get the returned challenge key. Returns an
     * AuthenticateSession which can then be used to provide passthru authentication.
     *
     * @param shr Remote server share and access control details.
     * @param tmo Timeout value in milliseconds
     * @return AuthenticateSession for the new session, else null.
     * @throws java.io.IOException           If an I/O error occurs.
     * @throws java.net.UnknownHostException Remote node is unknown.
     * @throws SMBException                  Failed to setup a new session.
     */
    public static AuthenticateSession OpenAuthenticateSession(PCShare shr, int tmo)
            throws java.io.IOException, java.net.UnknownHostException, SMBException {

        //	Open an authentication session
        return OpenAuthenticateSession(shr, tmo, null);
    }

    /**
     * Open a session to a remote server, negotiate an SMB dialect and get the returned challenge key. Returns an
     * AuthenticateSession which can then be used to provide passthru authentication.
     *
     * @param shr Remote server share and access control details.
     * @param tmo Timeout value in milliseconds
     * @param dia SMB dialects to negotiate for this session.
     * @return AuthenticateSession for the new session, else null.
     * @throws java.io.IOException           If an I/O error occurs.
     * @throws java.net.UnknownHostException Remote node is unknown.
     * @throws SMBException                  Failed to setup a new session.
     */
    public static AuthenticateSession OpenAuthenticateSession(PCShare shr, int tmo, DialectSelector dia)
            throws java.io.IOException, java.net.UnknownHostException, SMBException {

        //	Build a unique caller name
        int pid = getSessionId();

        StringBuffer nameBuf = new StringBuffer(InetAddress.getLocalHost().getHostName() + "_" + pid);
        String localName = nameBuf.toString();

        //  Debug
        if (Debug.EnableInfo && AuthenticateSession.hasDebug()) {
            Debug.println("** New auth session from " + localName + " to " + shr.toString());

            //  Display the Java system variables
            Debug.println("** os.arch = " + System.getProperty("os.arch") + ", java.version: " + System.getProperty("java.version"));
        }

        //	Connect to the requested server
        NetworkSession netSession = null;

        switch (getPrimaryProtocol()) {

            //	NetBIOS connection
            case Protocol.TCPNetBIOS:
                netSession = connectNetBIOSSession(shr.getNodeName(), localName, tmo);
                break;

            //	Native SMB connection
            case Protocol.NativeSMB:
                netSession = connectNativeSMBSession(shr.getNodeName(), localName, tmo);
                break;
        }

        //	If the connection was not made using the primary protocol try the secondary protocol, if configured
        if (netSession == null) {

            //	Try the secondary protocol
            switch (getSecondaryProtocol()) {

                //	NetBIOS connection
                case Protocol.TCPNetBIOS:
                    netSession = connectNetBIOSSession(shr.getNodeName(), localName, tmo);
                    break;

                //	Native SMB connection
                case Protocol.NativeSMB:
                    netSession = connectNativeSMBSession(shr.getNodeName(), localName, tmo);
                    break;
            }
        }

        //	Check if we connected to the remote host
        if (netSession == null)
            throw new IOException("Failed to connect to host, " + shr.getNodeName());

        //  Debug
        if (Debug.EnableInfo && AuthenticateSession.hasDebug())
            Debug.println("** Connected session, protocol : " + netSession.getProtocolName());

        //	Build a protocol negotiation SMB packet, and send it to the remote
        //	file server.
        SMBPacket pkt = new SMBPacket();
        DialectSelector selDialect = dia;

        if (selDialect == null) {

            //	Use the default SMB dialect list
            selDialect = new DialectSelector();
            selDialect.copyFrom(m_defDialects);
        }

        //	Build the negotiate SMB dialect packet and exchange with the remote server
        StringList diaList = BuildNegotiatePacket(pkt, selDialect, pid);
        pkt.ExchangeLowLevelSMB(netSession, pkt, true);

        //	Determine the selected SMB dialect
        String diaStr = diaList.getStringAt(pkt.getParameter(0));
        int dialectId = Dialect.DialectType(diaStr);

        //  DEBUG
        if (Debug.EnableInfo && AuthenticateSession.hasDebug())
            Debug.println("** SessionFactory: Negotiated SMB dialect " + diaStr);

        if (dialectId == Dialect.Unknown)
            throw new java.io.IOException("Unknown SMB dialect");

        //	Create the authenticate session
        AuthenticateSession authSess = new AuthenticateSession(shr, netSession, dialectId, pkt);
        return authSess;
    }

    /**
     * Set the default domain.
     *
     * @param domain String
     */
    public static void setDefaultDomain(String domain) {
        m_defDomain = domain;
    }

    /**
     * Set the default password.
     *
     * @param pwd java.lang.String
     */
    public static void setDefaultPassword(String pwd) {
        m_defPassword = pwd;
    }

    /**
     * Set the default user name.
     *
     * @param user java.lang.String
     */
    public static void setDefaultUserName(String user) {
        m_defUserName = user;
    }

    /**
     * Set the NetBIOS socket number to be used when setting up new sessions. The default socket is 139.
     *
     * @param port int
     */
    public static void setNetBIOSPort(int port) {
        m_netbiosPort = port;
    }

    /**
     * Set the NetBIOS scope id
     *
     * @param scope String
     */
    public static void setNetBIOSNameScope(String scope) {
        if (scope != null && scope.startsWith("."))
            m_netBIOSScopeId = scope.substring(1);
        else
            m_netBIOSScopeId = scope;
    }

    /**
     * Set the protocol connection order
     *
     * @param pri Primary connection protocol
     * @param sec Secondary connection protocol, or none
     * @return boolean
     */
    public static final boolean setProtocolOrder(int pri, int sec) {

        //	Primary protocol must be specified
        if (pri != Protocol.TCPNetBIOS && pri != Protocol.NativeSMB)
            return false;

        //	Primary and secondary must be different
        if (pri == sec)
            return false;

        //	Save the settings
        m_primaryProto = pri;
        m_secondaryProto = sec;

        return true;
    }

    /**
     * Enable/disable SMB session debugging.
     *
     * @param dbg true to enable SMB session debugging, else false.
     */
    public final static void setSessionDebug(boolean dbg) {
        if (dbg == true)
            AuthenticateSession.setDebug(AuthenticateSession.DBGPacketType);
        else
            AuthenticateSession.setDebug(0);
    }

    /**
     * Set the subnet mask string for network broadcast requests
     * <p>
     * If the subnet mask is not set a default broadcast mask for the TCP/IP address
     * class will be used.
     *
     * @param subnet Subnet mask string, in 'nnn.nnn.nnn.nnn' format.
     */
    public final static void setSubnetMask(String subnet) {
        NetBIOSSession.setDefaultSubnetMask(subnet);
    }

    /**
     * Setup the default SMB dialects to be negotiated when creating new sessions.
     */
    private static void SetupDefaultDialects() {

        //	Initialize the default dialect list
        if (m_defDialects == null)
            m_defDialects = new DialectSelector();
        else
            m_defDialects.ClearAll();

        //	Always enable core protocol
        m_defDialects.AddDialect(Dialect.Core);
        m_defDialects.AddDialect(Dialect.CorePlus);
        m_defDialects.AddDialect(Dialect.DOSLanMan1);
        m_defDialects.AddDialect(Dialect.DOSLanMan2);
        m_defDialects.AddDialect(Dialect.LanMan1);
        m_defDialects.AddDialect(Dialect.LanMan2);
        m_defDialects.AddDialect(Dialect.LanMan2_1);
        m_defDialects.AddDialect(Dialect.NT);
    }

    /**
     * Connect a NetBIOS network session
     *
     * @param toName   Host name/address to connect to
     * @param fromName Local host name/address
     * @param tmo      Timeout in seconds
     * @return NetworkSession
     * @throws IOException If a network error occurs
     */
    private static final NetworkSession connectNetBIOSSession(String toName, String fromName, int tmo)
            throws IOException {

        //	Connect to the requested server
        NetBIOSSession nbSession = new NetBIOSSession(tmo, getNetBIOSPort(), RFCNetBIOSProtocol.NAMING);

        //	Check if the remote host is specified as a TCP/IP address
        String toAddr = null;
        NetBIOSName nbName = null;

        if (IPAddress.isNumericAddress(toName)) {

            try {

                //	Get a list of NetBIOS names from the remote host
                toAddr = toName;
                NetBIOSNameList nameList = NetBIOSSession.FindNamesForAddress(toAddr);

                //	Find the server service
                nbName = nameList.findName(NetBIOSName.FileServer, false);
                if (nbName == null)
                    throw new IOException("Server service not running");

                //	Set the remote name
                toName = nbName.getName();
            }
            catch (UnknownHostException ex) {
                return null;
            }
        } else {

            //	Find the remote host and get a list of the network addresses it is using
            nbName = NetBIOSSession.FindName(toName, NetBIOSName.FileServer, 500);
        }

        //	Check if the NetBIOS name scope has been set, if so then update the names to add the scope id
        if (hasNetBIOSNameScope()) {

            //	Add the NetBIOS scope id to the to/from NetBIOS names
            toName = toName + "." + getNetBIOSNameScope();
            fromName = fromName + "." + getNetBIOSNameScope();
        }

        //	If the NetBIOS name has more than one TCP/IP address then find the best match for the client and
        //	try to connect on that address first, if that fails then we will have to try each address in turn.
        if (nbName.numberOfAddresses() > 1) {

            // Get the local TCP/IP address list and search for a best match address to connect to the server on
            InetAddress[] addrList = getLocalTcpipAddresses();
            int addrIdx = nbName.findBestMatchAddress(addrList);

            if (addrIdx != -1) {

                try {

                    //	Get the server IP address
                    String ipAddr = nbName.getIPAddressString(addrIdx);

                    //  DEBUG

                    if (Debug.EnableInfo && hasSessionDebug())
                        Debug.println("** Server is multi-homed, trying to connect to " + ipAddr);

                    //	Open the session to the remote host
                    nbSession.Open(toName, fromName, ipAddr);

                    //	Check if the session is connected
                    if (nbSession.isConnected() == false) {

                        //	Close the session
                        try {
                            nbSession.Close();
                        }
                        catch (Exception ex) {
                        }
                    } else if (Debug.EnableInfo && hasSessionDebug() && nbSession.isConnected())
                        Debug.println("** Connected to address " + ipAddr);
                }
                catch (IOException ex) {
                }
            }
        }

        //  DEBUG
        if (Debug.EnableInfo && hasSessionDebug() && nbSession.isConnected() == false && nbName.numberOfAddresses() > 1)
            Debug.println("** Server is multi-homed, trying all addresses");

        //	Loop through the available addresses for the remote file server until we get a successful
        //	connection, or all addresses have been used
        IOException lastException = null;
        int addrIdx = 0;

        while (nbSession.isConnected() == false && addrIdx < nbName.numberOfAddresses()) {

            try {

                //	Get the server IP address
                String ipAddr = nbName.getIPAddressString(addrIdx++);

                //  DEBUG
                if (Debug.EnableInfo && hasSessionDebug())
                    Debug.println("** Trying address " + ipAddr);

                //	Open the session to the remote host
                nbSession.Open(toName, fromName, ipAddr);

                //	Check if the session is connected
                if (nbSession.isConnected() == false) {

                    //	Close the session
                    try {
                        nbSession.Close();
                    }
                    catch (Exception ex) {
                    }
                } else if (Debug.EnableInfo && hasSessionDebug() && nbSession.isConnected())
                    Debug.println("** Connected to address " + ipAddr);
            }
            catch (IOException ex) {

                //	Save the last exception
                lastException = ex;
            }
        }

        //	Check if the session is connected
        if (nbSession.isConnected() == false) {

            //	If there is a saved exception rethrow it
            if (lastException != null)
                throw lastException;

            //	Indicate that the session was not connected
            return null;
        }

        //	Return the network session
        return nbSession;
    }

    /**
     * Connect a native SMB network session
     *
     * @param toName   Host name/address to connect to
     * @param fromName Local host name/address
     * @param tmo      Timeout in seconds
     * @return NetworkSession
     * @throws IOException If a network error occurs
     */
    private static final NetworkSession connectNativeSMBSession(String toName, String fromName, int tmo)
            throws IOException {

        //	Connect to the requested server
        TcpipSMBNetworkSession tcpSession = new TcpipSMBNetworkSession(tmo);

        try {

            //	Open the session
            tcpSession.Open(toName, fromName, null);

            //	Check if the session is connected
            if (tcpSession.isConnected() == false) {

                //	Close the session
                try {
                    tcpSession.Close();
                }
                catch (Exception ex) {
                }

                //	Return a null session
                return null;
            }
        }
        catch (Exception ex) {
            try {
                tcpSession.Close();
            }
            catch (Exception ex2) {
            }
            tcpSession = null;
        }

        //	Return the network session
        return tcpSession;
    }
}
