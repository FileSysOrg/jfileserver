/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.netbios;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.filesys.debug.Debug;
import org.filesys.util.DataPacker;
import org.filesys.util.HexDump;
import org.filesys.util.IPAddress;
import org.filesys.util.StringList;

/**
 * NetBIOS session class.
 *
 * <p>Holds the details of a TCP/IP NetBIOS session connection to a remote NetBIOS service.
 *
 * <p>The session is usually used to send higher level requests such as SMB/CIFS requests to a file server service.
 *
 * <p>Contains a number of static methods for performing name lookups of remote servers/services.
 *
 * @author gkspencer
 */
public final class NetBIOSSession extends NetworkSession {

    //	Constants
    //
    // Protocol name
    private static final String ProtocolName = "TCP/IP NetBIOS";

    //  Name lookup types
    public enum LookupType {
        DNS_ONLY,
        WINS_ONLY,
        WINS_AND_DNS;
    }

    //	Caller name template
    public static final int MaxCallerNameTemplateLength = 8;
    public static final char SessionIdChar          = '#';
    public static final char JVMIdChar              = '@';
    public static final String ValidTemplateChars   = "@#_";

    //	Default find name buffer size
    private static final int FindNameBufferSize = 2048;

    //  Per session overrides
    //
    //  Remote socket to connect to, default is 139, and name port
    private int m_remotePort = RFCNetBIOSProtocol.SESSION;
    private int m_namePort = RFCNetBIOSProtocol.NAMING;

    //  Subnet mask override
    private String m_subnetMask = _subnetMask;

    //  WINS server address override
    private InetAddress m_winsServer = _winsServer;

    //  Name lookup type and timeout overrides
    private LookupType m_lookupType = _lookupType;
    private int m_lookupTmo = _lookupTmo;

    //  Use wildcard server name in session connection override
    private boolean m_useWildcardServerName = _useWildcardFileServer;

    //  Socket used to connect and read/write to remote host
    private Socket m_nbSocket;

    //  Input and output data streams, from the socket network connection
    private DataInputStream m_nbIn;
    private DataOutputStream m_nbOut;

    // 	Local and remote name types
    private char m_locNameType = NetBIOSName.FileServer;
    private char m_remNameType = NetBIOSName.FileServer;

    //  Unique session identifier, used to generate a unique caller name when opening a new session
    private static int m_sessIdx = 0;

    //	Unique JVM id, used to generate a unique caller name when multiple JVMs may be running on the same
    //	host
    private static int m_jvmIdx = 0;

    //	Caller name template string. The template is used to create a unique caller name when opening a new session.
    //	The template is appended to the local host name, which may be truncated to allow room for the template to be
    //	appended and still be within the 16 character NetBIOS name limit.
    //
    //	The caller name generation replaces '#' characters with a zero padded session index as a hex value and '@'
    //	characters with a zero padded JVM index. Multiple '#' and/or '@' characters can be specified to indicate the
    //	field width. Any other characters in the template are passed through to the final caller name string.
    //
    //	The maximum template string length is 8 characters to allow for at least 8 characters from the host name.
    private static String m_callerTemplate = "_##";

    //	Truncated host name, caller name generation appends the caller template result to this string
    private static String m_localNamePart;

    //  Transaction identifier, used for datagrams
    private static short m_tranIdx = 1;

    //  RFC NetBIOS name service datagram socket
    private static DatagramSocket m_dgramSock = null;

    //  Debug enable flag
    private static boolean m_debug = false;

    //  Subnet mask, required for broadcast name lookup requests
    private static String _subnetMask = null;

    //	WINS server address
    private static InetAddress _winsServer;

    // 	Flag to control whether name lookups use WINS/NetBIOS lookup or DNS
    private static LookupType _lookupType = LookupType.WINS_AND_DNS;

    // 	NetBIOS name lookup timeout value.
    private static int _lookupTmo = 500;

    //	Flag to control use of the '*SMBSERVER' name when connecting to a file server
    private static boolean _useWildcardFileServer = true;

    /**
     * NetBIOS session class constructor.
     * <p>
     * Create a NetBIOS session with the default socket number and no current
     * network connection.
     */
    public NetBIOSSession() {
        super(ProtocolName);
    }

    /**
     * NetBIOS session class constructor
     *
     * @param tmo Send/receive timeout value in milliseconds
     */
    public NetBIOSSession(int tmo) {
        super(ProtocolName);
        setTimeout(tmo);
    }

    /**
     * NetBIOS session class constructor
     *
     * @param tmo      Send/receive timeout value in milliseconds
     * @param port     Remote port to connect to
     * @param namePort Name lookup port to use
     */
    public NetBIOSSession(int tmo, int port, int namePort) {
        super(ProtocolName);
        setTimeout(tmo);
        m_remotePort = port;
        m_namePort = namePort;
    }

    /**
     * Get the NetBIOS session port to connect to
     *
     * @return int
     */
    public final int getSessionPort() {
        return m_remotePort;
    }

    /**
     * Return the name port
     *
     * @return int
     */
    public final int getNamePort() {
        return m_namePort;
    }

    /**
     * Return the name lookup type(s) to use
     *
     * @return LookupType
     */
    public final LookupType getLookupType() {
        return m_lookupType;
    }

    /**
     * Return the name lookup timeout, in milliseconds
     *
     * @return int
     */
    public final int getLookupTimeout() {
        return m_lookupTmo;
    }

    /**
     * Check if WINS server is configured
     *
     * @return boolean
     */
    public final boolean hasWINSServer() {
        return m_winsServer != null ? true : false;
    }

    /**
     * Return the WINS server address
     *
     * @return InetAddress
     */
    public final InetAddress getWINSServer() {
        return m_winsServer;
    }

    /**
     * Return the subnet mask
     *
     * @return String
     */
    public final String getSubnetMask() {
        return m_subnetMask;
    }

    /**
     * Determine if the session is connected to a remote host
     *
     * @return boolean
     */
    public final boolean isConnected() {

        //	Check if the socket is valid
        if (m_nbSocket == null)
            return false;
        return true;
    }

    /**
     * Check if there is data available on this network session
     *
     * @return boolean
     * @exception IOException Socket error
     */
    public final boolean hasData()
            throws IOException {

        //	Check if the connection is active
        if (m_nbSocket == null || m_nbIn == null)
            return false;

        //	Check if there is data available
        return m_nbIn.available() > 0 ? true : false;
    }

    /**
     * Convert a host name string into RFC NetBIOS format.
     *
     * @param hostName Host name to be converted.
     * @return Converted host name string.
     */
    public static String ConvertName(String hostName) {
        return ConvertName(hostName, NetBIOSName.FileServer);
    }

    /**
     * Convert a host name string into RFC NetBIOS format.
     *
     * @param hostName Host name to be converted.
     * @param nameType NetBIOS name type, added as the 16th byte of the name
     *                 before conversion.
     * @return Converted host name string.
     */
    public static String ConvertName(String hostName, char nameType) {

        //  Build the name string with the name type, make sure that the host
        //  name is uppercase.
        StringBuffer hName = new StringBuffer(hostName.toUpperCase());

        if (hName.length() > 15)
            hName.setLength(15);

        //  Space pad the name then add the NetBIOS name type
        while (hName.length() < 15)
            hName.append(' ');
        hName.append(nameType);

        //  Convert the NetBIOS name string to the RFC NetBIOS name format
        String convstr = "ABCDEFGHIJKLMNOP";
        StringBuffer nameBuf = new StringBuffer(32);

        int idx = 0;

        while (idx < hName.length()) {

            //  Get the current character from the host name string
            char ch = hName.charAt(idx++);

            if (ch == ' ') {

                //  Append an encoded <SPACE> character
                nameBuf.append("CA");
            } else {

                //  Append octet for the current character
                nameBuf.append(convstr.charAt((int) ch / 16));
                nameBuf.append(convstr.charAt((int) ch % 16));
            }

        } // end while

        //  Return the encoded string
        return nameBuf.toString();
    }

    /**
     * Convert an encoded NetBIOS name to a normal name string
     *
     * @param buf Buffer that contains the NetBIOS encoded name
     * @param off Offset that the name starts within the buffer
     * @return Normal NetBIOS name string
     */
    public static String DecodeName(byte[] buf, int off) {

        //  Convert the RFC NetBIOS name string to a normal NetBIOS name string
        String convstr = "ABCDEFGHIJKLMNOP";
        StringBuffer nameBuf = new StringBuffer(16);

        int idx = 0;
        char ch1, ch2;

        while (idx < 32) {

            //  Get the current encoded character pair from the encoded name string
            ch1 = (char) buf[off + idx];
            ch2 = (char) buf[off + idx + 1];

            if (ch1 == 'C' && ch2 == 'A') {

                //  Append a <SPACE> character
                nameBuf.append(' ');
            } else {

                //  Convert back to a character code
                int val = convstr.indexOf(ch1) << 4;
                val += convstr.indexOf(ch2);

                //  Append the current character to the decoded name
                nameBuf.append((char) (val & 0xFF));
            }

            //  Update the encoded string index
            idx += 2;

        } // end while

        //  Return the decoded string
        return nameBuf.toString();
    }

    /**
     * Convert an encoded NetBIOS name to a normal name string
     *
     * @param encnam RFC NetBIOS encoded name
     * @return Normal NetBIOS name string
     */
    public static String DecodeName(String encnam) {

        //  Check if the encoded name string is valid, must be 32 characters
        if (encnam == null || encnam.length() != 32)
            return "";

        //  Convert the RFC NetBIOS name string to a normal NetBIOS name string
        String convstr = "ABCDEFGHIJKLMNOP";
        StringBuffer nameBuf = new StringBuffer(16);

        int idx = 0;
        char ch1, ch2;

        while (idx < 32) {

            //  Get the current encoded character pair from the encoded name string
            ch1 = encnam.charAt(idx);
            ch2 = encnam.charAt(idx + 1);

            if (ch1 == 'C' && ch2 == 'A') {

                //  Append a <SPACE> character
                nameBuf.append(' ');
            } else {

                //  Convert back to a character code
                int val = convstr.indexOf(ch1) << 4;
                val += convstr.indexOf(ch2);

                //  Append the current character to the decoded name
                nameBuf.append((char) (val & 0xFF));
            }

            //  Update the encoded string index
            idx += 2;

        } // end while

        //  Return the decoded string
        return nameBuf.toString();
    }

    /**
     * Convert a host name string into RFC NetBIOS format.
     *
     * @param hostName Host name to be converted.
     * @param nameType NetBIOS name type, added as the 16th byte of the name
     *                 before conversion.
     * @param buf      Buffer to write the encoded name into.
     * @param off      Offset within the buffer to start writing.
     * @return Buffer position
     */
    public static int EncodeName(String hostName, char nameType, byte[] buf, int off) {

        //  Build the name string with the name type, make sure that the host
        //  name is uppercase.
        StringBuffer hName = new StringBuffer(hostName.toUpperCase());

        if (hName.length() > 15)
            hName.setLength(15);

        //  Space pad the name then add the NetBIOS name type
        while (hName.length() < 15)
            hName.append(' ');
        hName.append(nameType);

        //  Convert the NetBIOS name string to the RFC NetBIOS name format
        String convstr = "ABCDEFGHIJKLMNOP";
        int idx = 0;
        int bufpos = off;

        //  Set the name length byte
        buf[bufpos++] = 0x20;

        //  Copy the encoded NetBIOS name to the buffer
        while (idx < hName.length()) {

            //  Get the current character from the host name string
            char ch = hName.charAt(idx++);

            if (ch == ' ') {

                //  Append an encoded <SPACE> character
                buf[bufpos++] = (byte) 'C';
                buf[bufpos++] = (byte) 'A';
            } else {

                //  Append octet for the current character
                buf[bufpos++] = (byte) convstr.charAt((int) ch / 16);
                buf[bufpos++] = (byte) convstr.charAt((int) ch % 16);
            }

        } // end while

        //  Null terminate the string
        buf[bufpos++] = 0;
        return bufpos;
    }

    /**
     * Find a NetBIOS name on the network
     *
     * @param nbName NetBIOS name to search for, not yet RFC encoded
     * @param nbType Name type, appended as the 16th byte of the name
     * @param tmo    Timeout value for receiving incoming datagrams
     * @param sess   NetBIOS session to use to override various settings
     * @return NetBIOS name details
     * @throws java.io.IOException If an I/O error occurs
     */
    public static NetBIOSName FindName(String nbName, char nbType, int tmo, NetBIOSSession sess)
            throws java.io.IOException {

        //  Call the main FindName method
        return FindName(new NetBIOSName(nbName, nbType, false), tmo, sess);
    }

    /**
     * Find a NetBIOS name on the network
     *
     * @param nbName NetBIOS name to search for, not yet RFC encoded
     * @param nbType Name type, appended as the 16th byte of the name
     * @param tmo    Timeout value for receiving incoming datagrams
     * @return NetBIOS name details
     * @throws java.io.IOException If an I/O error occurs
     */
    public static NetBIOSName FindName(String nbName, char nbType, int tmo)
            throws java.io.IOException {

        //	Call the main FindName method
        return FindName(new NetBIOSName(nbName, nbType, false), tmo, null);
    }

    /**
     * Find a NetBIOS name on the network
     *
     * @param nbName NetBIOS name to search for
     * @param tmo    Timeout value for receiving incoming datagrams
     * @return NetBIOS name details
     * @throws java.io.IOException If an I/O error occurs
     */
    public static NetBIOSName FindName(NetBIOSName nbName, int tmo)
            throws java.io.IOException {

        // Call the main FindName method
        return FindName(nbName, tmo, null);
    }

    /**
     * Find a NetBIOS name on the network
     *
     * @param nbName NetBIOS name to search for
     * @param tmo    Timeout value for receiving incoming datagrams
     * @param sess   NetBIOS session to use to override various settings
     * @return NetBIOS name details
     * @throws java.io.IOException If an I/O error occurs
     */
    public static NetBIOSName FindName(NetBIOSName nbName, int tmo, NetBIOSSession sess)
            throws java.io.IOException {

        //  Get the name lookup type(s) to use
        LookupType lookupTypes = getDefaultLookupType();

        if (sess != null)
            lookupTypes = sess.getLookupType();

        //  Check if only DNS lookups should be used
        if (lookupTypes == LookupType.DNS_ONLY) {

            // Convert the address to a name using a DNS lookup
            InetAddress inetAddr = InetAddress.getByName(nbName.getFullName());
            return new NetBIOSName(inetAddr.getHostName(), nbName.getType(), nbName.isGroupName(), inetAddr.getAddress());
        }

        //  Get the local address details
        InetAddress locAddr = InetAddress.getLocalHost();

        //  Create a datagram socket
        if (m_dgramSock == null) {

            //  Create a datagram socket
            m_dgramSock = new DatagramSocket();
        }

        //  Set the datagram socket timeout, in milliseconds
        m_dgramSock.setSoTimeout(tmo);

        //  Check if the lookup should use WINS or a broadcast
        boolean wins = false;
        if ((sess != null && sess.hasWINSServer()) || hasDefaultWINSServer())
            wins = true;

        //  Create a name lookup NetBIOS packet
        NetBIOSPacket nbpkt = new NetBIOSPacket();
        nbpkt.buildNameQueryRequest(nbName, m_tranIdx++, wins);

        //  Get the local host numeric address
        String locIP = locAddr.getHostAddress();
        int dotIdx = locIP.indexOf('.');
        if (dotIdx == -1)
            return null;

        //  Get the subnet mask addresses, using either the global settings or per session settings
        String subnetMask = getDefaultSubnetMask();
        if (sess != null)
            subnetMask = sess.getSubnetMask();

        //	If a WINS server has been configured the request is sent directly to the WINS server, if not then
        //	a broadcast is done on the local subnet.
        InetAddress destAddr = null;

        if (hasDefaultWINSServer() == false || (sess != null && sess.hasWINSServer() == false)) {

            //  Check if the subnet mask has been set, if not then generate a subnet mask
            if (subnetMask == null)
                subnetMask = GenerateSubnetMask(null);

            //  Build a broadcast destination address
            destAddr = InetAddress.getByName(subnetMask);
        } else {

            //	Use the WINS server address
            if (sess != null)
                destAddr = sess.getWINSServer();
            else
                destAddr = getDefaultWINSServer();
        }

        //	Build the name lookup request
        DatagramPacket dgram = new DatagramPacket(nbpkt.getBuffer(), nbpkt.getLength(),
                destAddr, RFCNetBIOSProtocol.NAMING);

        //  Allocate a receive datagram packet
        byte[] rxbuf = new byte[FindNameBufferSize];
        DatagramPacket rxdgram = new DatagramPacket(rxbuf, rxbuf.length);

        //  Create a NetBIOS packet using the receive buffer
        NetBIOSPacket rxpkt = new NetBIOSPacket(rxbuf);

        //  DEBUG
        if (m_debug)
            nbpkt.DumpPacket(false);

        //  Send the find name datagram
        m_dgramSock.send(dgram);

        //  Receive a reply datagram
        boolean rxOK = false;

        do {

            //  Receive a datagram packet
            m_dgramSock.receive(rxdgram);

            //  DEBUG
            if (Debug.EnableInfo && m_debug) {
                Debug.println("NetBIOS: Rx Datagram");
                rxpkt.DumpPacket(false);
            }

            //  Check if this is a valid response datagram
            if (rxpkt.isResponse() && rxpkt.getOpcode() == NetBIOSPacket.RESP_QUERY)
                rxOK = true;

        } while (!rxOK);

        //	Get the list of names from the response, should only be one name
        NetBIOSNameList nameList = rxpkt.getAnswerNameList();
        if (nameList != null && nameList.numberOfNames() > 0)
            return nameList.getName(0);
        return null;
    }

    /**
     * Find a NetBIOS name on the network
     *
     * @param nbName      NetBIOS name to search for
     * @param tmo         Timeout value for receiving incoming datagrams
     * @param lookupTypes Types of name lookup to use (DNS and/or NetBIOS)
     * @param sess        NetBIOS session to use to override various settings
     * @return NetBIOS name details
     * @throws java.io.IOException If an I/O error occurs
     */
    public static NetBIOSName FindName(NetBIOSName nbName, int tmo, LookupType lookupTypes, NetBIOSSession sess)
            throws IOException {

        //  Check if only DNS lookups should be used
        if (lookupTypes == LookupType.DNS_ONLY) {

            // Convert the address to a name using a DNS lookup
            InetAddress inetAddr = InetAddress.getByName(nbName.getFullName());
            return new NetBIOSName(inetAddr.getHostName(), nbName.getType(), nbName.isGroupName(), inetAddr.getAddress());
        }

        //  Get the local address details
        InetAddress locAddr = InetAddress.getLocalHost();

        //  Create a datagram socket
        if (m_dgramSock == null) {

            //  Create a datagram socket
            m_dgramSock = new DatagramSocket();
        }

        //  Set the datagram socket timeout, in milliseconds
        m_dgramSock.setSoTimeout(tmo);

        //  Check if the lookup should use WINS or a broadcast
        boolean wins = false;
        if ((sess != null && sess.hasWINSServer()) || hasDefaultWINSServer())
            wins = true;

        //  Create a name lookup NetBIOS packet
        NetBIOSPacket nbpkt = new NetBIOSPacket();
        nbpkt.buildNameQueryRequest(nbName, m_tranIdx++, wins);

        //  Get the local host numeric address
        String locIP = locAddr.getHostAddress();
        int dotIdx = locIP.indexOf('.');
        if (dotIdx == -1)
            return null;

        //  Get the subnet mask addresses, using either the global settings or per session settings
        String subnetMask = getDefaultSubnetMask();
        if (sess != null)
            subnetMask = sess.getSubnetMask();

        //	If a WINS server has been configured the request is sent directly to the WINS server, if not then
        //	a broadcast is done on the local subnet.
        InetAddress destAddr = null;

        if (hasDefaultWINSServer() == false || (sess != null && sess.hasWINSServer() == false)) {

            //  Check if the subnet mask has been set, if not then generate a subnet mask
            if (subnetMask == null)
                subnetMask = GenerateSubnetMask(null);

            //  Build a broadcast destination address
            destAddr = InetAddress.getByName(subnetMask);
        } else {

            //	Use the WINS server address
            if (sess != null)
                destAddr = sess.getWINSServer();
            else
                destAddr = getDefaultWINSServer();
        }

        //	Build the name lookup request
        DatagramPacket dgram = new DatagramPacket(nbpkt.getBuffer(), nbpkt.getLength(),
                destAddr, RFCNetBIOSProtocol.NAMING);

        //  Allocate a receive datagram packet
        byte[] rxbuf = new byte[FindNameBufferSize];
        DatagramPacket rxdgram = new DatagramPacket(rxbuf, rxbuf.length);

        //  Create a NetBIOS packet using the receive buffer
        NetBIOSPacket rxpkt = new NetBIOSPacket(rxbuf);

        //  DEBUG
        if (m_debug)
            nbpkt.DumpPacket(false);

        //  Send the find name datagram
        m_dgramSock.send(dgram);

        //  Receive a reply datagram
        boolean rxOK = false;

        do {

            //  Receive a datagram packet
            m_dgramSock.receive(rxdgram);

            //  DEBUG
            if (Debug.EnableInfo && m_debug) {
                Debug.println("NetBIOS: Rx Datagram");
                rxpkt.DumpPacket(false);
            }

            //  Check if this is a valid response datagram
            if (rxpkt.isResponse() && rxpkt.getOpcode() == NetBIOSPacket.RESP_QUERY)
                rxOK = true;

        } while (!rxOK);

        //	Get the list of names from the response, should only be one name
        NetBIOSNameList nameList = rxpkt.getAnswerNameList();
        if (nameList != null && nameList.numberOfNames() > 0)
            return nameList.getName(0);
        return null;
    }

    /**
     * Build a list of nodes that own the specified NetBIOS name.
     *
     * @param nbName NetBIOS name to search for, not yet RFC encoded
     * @param nbType Name type, appended as the 16th byte of the name
     * @param tmo    Timeout value for receiving incoming datagrams
     * @return List of host name strings
     * @throws java.io.IOException If an I/O error occurs
     */
    public static StringList FindNameList(String nbName, char nbType, int tmo)
            throws IOException {

        // Call the main find name list method
        return FindNameList(nbName, nbType, tmo, null);
    }

    /**
     * Build a list of nodes that own the specified NetBIOS name.
     *
     * @param nbName NetBIOS name to search for, not yet RFC encoded
     * @param nbType Name type, appended as the 16th byte of the name
     * @param tmo    Timeout value for receiving incoming datagrams
     * @param sess   NetBIOS session to use to override various settings
     * @return List of host name strings
     * @throws java.io.IOException If an I/O error occurs
     */
    public static StringList FindNameList(String nbName, char nbType, int tmo, NetBIOSSession sess)
            throws IOException {

        //  Get the local address details
        InetAddress locAddr = InetAddress.getLocalHost();

        //  Create a datagram socket
        if (m_dgramSock == null) {

            //  Create a datagram socket
            m_dgramSock = new DatagramSocket();
        }

        //  Set the datagram socket timeout, in milliseconds
        m_dgramSock.setSoTimeout(tmo);

        //  Create a name lookup NetBIOS packet
        NetBIOSPacket nbpkt = new NetBIOSPacket();

        nbpkt.setTransactionId(m_tranIdx++);
        nbpkt.setOpcode(NetBIOSPacket.NAME_QUERY);
        nbpkt.setFlags(NetBIOSPacket.FLG_BROADCAST);
        nbpkt.setQuestionCount(1);
        nbpkt.setQuestionName(nbName, nbType, NetBIOSPacket.NAME_TYPE_NB, NetBIOSPacket.NAME_CLASS_IN);

        //  Get the local host numeric address
        String locIP = locAddr.getHostAddress();
        int dotIdx = locIP.indexOf('.');
        if (dotIdx == -1)
            return null;

        //	If a WINS server has been configured the request is sent directly to the WINS server, if not then
        //	a broadcast is done on the local subnet.
        InetAddress destAddr = null;

        if (hasDefaultWINSServer() == false || (sess != null && sess.hasWINSServer() == false)) {

            //  Get the subnet mask addresses, using either the global settings or per session settings
            String subnetMask = getDefaultSubnetMask();
            if (sess != null)
                subnetMask = sess.getSubnetMask();

            //  Check if the subnet mask has been set, if not then generate a subnet mask
            if (subnetMask == null)
                subnetMask = GenerateSubnetMask(null);

            //  Build a broadcast destination address
            destAddr = InetAddress.getByName(subnetMask);
        } else {

            //  Use the WINS server address
            if (sess != null)
                destAddr = sess.getWINSServer();
            else
                destAddr = getDefaultWINSServer();
        }

        //  Build the request datagram
        DatagramPacket dgram = new DatagramPacket(nbpkt.getBuffer(), nbpkt.getLength(),
                destAddr, RFCNetBIOSProtocol.NAMING);

        //  Allocate a receive datagram packet
        byte[] rxbuf = new byte[FindNameBufferSize];
        DatagramPacket rxdgram = new DatagramPacket(rxbuf, rxbuf.length);

        //  Create a NetBIOS packet using the receive buffer
        NetBIOSPacket rxpkt = new NetBIOSPacket(rxbuf);

        //  DEBUG
        if (m_debug)
            nbpkt.DumpPacket(false);

        //  Create a vector to store the remote host addresses
        List<InetAddress> addrList = new ArrayList<InetAddress>();

        //  Calculate the end  time, to stop receiving datagrams
        long endTime = System.currentTimeMillis() + tmo;

        //  Send the find name datagram
        m_dgramSock.send(dgram);

        //  Receive reply datagrams
        do {

            //  Receive a datagram packet
            try {
                m_dgramSock.receive(rxdgram);

                //  DEBUG
                if (Debug.EnableInfo && m_debug) {
                    Debug.println("NetBIOS: Rx Datagram");
                    rxpkt.DumpPacket(false);
                }

                //  Check if this is a valid response datagram
                if (rxpkt.isResponse() && rxpkt.getOpcode() == NetBIOSPacket.RESP_QUERY) {

                    //  Get the address of the remote host for this datagram and add it to the list
                    //  of responders
                    addrList.add(rxdgram.getAddress());
                }
            }
            catch (java.io.IOException ex) {

                //  DEBUG
                if (Debug.EnableInfo && m_debug)
                    Debug.println(ex.toString());
            }

        } while (System.currentTimeMillis() < endTime);

        //  Check if we received any replies
        if (addrList.size() == 0)
            return null;

        //  Create a node name list
        StringList nameList = new StringList();

        //  Convert the reply addresses to node names
        for (int i = 0; i < addrList.size(); i++) {

            //  Get the current address from the list
            InetAddress addr = (InetAddress) addrList.get(i);

            //  Convert the address to a node name string
            String name = NetBIOSName(addr.getHostName());

            //  Check if the name is already in the name list
            if (!nameList.containsString(name))
                nameList.addString(name);
        }

        //  Return the node name list
        return nameList;
    }

    /**
     * Get the NetBIOS name list for the specified IP address
     *
     * @param ipAddr String
     * @return NetBIOSNameList
     * @exception UnknownHostException If the name cannot be converted using a DNS lookup
     * @exception SocketException      If there is an error searching for the NetBIOS name
     */
    public static NetBIOSNameList FindNamesForAddress(String ipAddr)
            throws UnknownHostException, SocketException {

        // Find the names using the standard name port
        return FindNamesForAddress(ipAddr, null);
    }

    /**
     * Get the NetBIOS name list for the specified IP address
     *
     * @param ipAddr String
     * @param sess   NetBIOSSession
     * @return NetBIOSNameList
     * @exception UnknownHostException If the name cannot be converted using a DNS lookup
     * @exception SocketException      If there is an error searching for the NetBIOS name
     */
    public static NetBIOSNameList FindNamesForAddress(String ipAddr, NetBIOSSession sess)
            throws UnknownHostException, SocketException {

        //  Create a datagram socket
        if (m_dgramSock == null) {

            //  Create a datagram socket
            m_dgramSock = new DatagramSocket();
        }

        //  Set the datagram socket timeout, in milliseconds
        m_dgramSock.setSoTimeout(2000);

        //  Create a name lookup NetBIOS packet
        NetBIOSPacket nbpkt = new NetBIOSPacket();

        nbpkt.setTransactionId(m_tranIdx++);
        nbpkt.setOpcode(NetBIOSPacket.NAME_QUERY);
        nbpkt.setFlags(NetBIOSPacket.FLG_BROADCAST);
        nbpkt.setQuestionCount(1);
        nbpkt.setQuestionName("*\0\0\0\0\0\0\0\0\0\0\0\0\0\0", NetBIOSName.WorkStation, NetBIOSPacket.NAME_TYPE_NBSTAT, NetBIOSPacket.NAME_CLASS_IN);

        //  Get the name port from the session settings, or use the default port

        int namePort = RFCNetBIOSProtocol.NAMING;
        if (sess != null)
            namePort = sess.getNamePort();

        //  Send the request to the specified address
        InetAddress destAddr = InetAddress.getByName(ipAddr);
        DatagramPacket dgram = new DatagramPacket(nbpkt.getBuffer(), nbpkt.getLength(),
                destAddr, namePort);

        //  Allocate a receive datagram packet
        byte[] rxbuf = new byte[FindNameBufferSize];
        DatagramPacket rxdgram = new DatagramPacket(rxbuf, rxbuf.length);

        //  Create a NetBIOS packet using the receive buffer
        NetBIOSPacket rxpkt = new NetBIOSPacket(rxbuf);

        //  DEBUG
        if (Debug.EnableInfo && m_debug)
            nbpkt.DumpPacket(false);

        //  Create a vector to store the remote hosts NetBIOS names
        NetBIOSNameList nameList = null;

        try {

            //  Send the name query datagram
            m_dgramSock.send(dgram);

            //  Receive a datagram packet
            m_dgramSock.receive(rxdgram);

            //  DEBUG
            if (Debug.EnableInfo && m_debug) {
                Debug.println("NetBIOS: Rx Datagram");
                rxpkt.DumpPacket(false);
            }

            //  Check if this is a valid response datagram
            if (rxpkt.isResponse() && rxpkt.getOpcode() == NetBIOSPacket.RESP_QUERY &&
                    rxpkt.getAnswerCount() >= 1) {

                //	Get the received name list
                nameList = rxpkt.getAdapterStatusNameList();

                // If the name list is valid set the TCP/IP address for each name in the list
                if (nameList != null) {

                    // Convert the TCP/IP address to bytes
                    byte[] ipByts = IPAddress.asBytes(ipAddr);

                    // Set the TCP/IP address for each name in the list
                    for (int i = 0; i < nameList.numberOfNames(); i++) {
                        NetBIOSName nbName = nameList.getName(i);
                        nbName.addIPAddress(ipByts);
                    }
                }
            }
        }
        catch (java.io.IOException ex) {

            //  DEBUG
            if (Debug.EnableInfo && m_debug)
                Debug.println(ex.toString());

            //	Unknown host
            throw new UnknownHostException(ipAddr);
        }

        //  Return the NetBIOS name list
        return nameList;
    }

    /**
     * Convert a TCP/IP address to a NetBIOS or host name
     *
     * @param addr    String
     * @param nbTyp   char
     * @param isGroup boolean
     * @return NetBIOSName
     * @exception UnknownHostException If the name cannot be converted using a DNS lookup
     * @exception SocketException      If there is an error searching for the NetBIOS name
     * @exception IOException          If a name conversion fails
     */
    public final static NetBIOSName ConvertAddressToName(String addr, char nbTyp, boolean isGroup)
            throws UnknownHostException, SocketException, IOException {

        // Call the main convert name method
        return ConvertAddressToName(addr, nbTyp, isGroup, null);
    }

    /**
     * Convert a TCP/IP address to a NetBIOS or host name
     *
     * @param addr    String
     * @param nbTyp   char
     * @param isGroup boolean
     * @param sess    NetBIOS session to use to override various settings
     * @return NetBIOSName
     * @throws UnknownHostException If the name cannot be converted using a DNS lookup
     * @throws SocketException      If there is an error searching for the NetBIOS name
     * @throws IOException          If a name conversion fails
     */
    public final static NetBIOSName ConvertAddressToName(String addr, char nbTyp, boolean isGroup, NetBIOSSession sess)
            throws UnknownHostException, SocketException, IOException {

        //  Get the name lookup type(s) to use
        LookupType lookupTypes = getDefaultLookupType();

        if (sess != null)
            lookupTypes = sess.getLookupType();

        //  Check if the remote host is specified as a TCP/IP address
        NetBIOSName nbName = null;

        if (IPAddress.isNumericAddress(addr)) {

            // Convert the address to a name, using either DNS or WINS/NetBIOS broadcast type lookups
            if (lookupTypes != LookupType.WINS_ONLY) {

                try {

                    // Convert the address to a name using a DNS lookup
                    InetAddress inetAddr = InetAddress.getByName(addr);
                    nbName = new NetBIOSName(addr, nbTyp, isGroup, inetAddr.getAddress());

                    // DEBUG
                    if (isDebug())
                        Debug.println("Converted " + addr + " to NetBIOS name " + nbName + " [DNS]");
                }
                catch (Exception ex) {
                }
            }

            // If the address has not been converted try a WINS/NetBIOS name lookup
            if (nbName == null && lookupTypes != LookupType.DNS_ONLY) {

                //  Get a list of NetBIOS names from the remote host
                NetBIOSNameList nameList = NetBIOSSession.FindNamesForAddress(addr);

                //  Find the required service type
                nbName = nameList.findName(nbTyp, isGroup);

                // DEBUG
                if (isDebug())
                    Debug.println("Converted " + addr + " to NetBIOS name " + nbName + " [WINS]");
            }
        }

        // Check if the address has been converted to a name
        if (nbName == null)
            throw new IOException("NetBIOS service not running");

        // Return the NetBIOS name
        return nbName;
    }

    /**
     * Determine the subnet mask from the local hosts TCP/IP address
     *
     * @param addr TCP/IP address to set the subnet mask for, in 'nnn.nnn.nnn.nnn'
     *             format.
     * @return String
     * @exception UnknownHostException If the name cannot be converted using a DNS lookup
     */
    public static String GenerateSubnetMask(String addr)
            throws UnknownHostException {

        //  Set the TCP/IP address string
        String localIP = addr;

        //  Get the local TCP/IP address, if a null string has been specified
        if (localIP == null)
            localIP = InetAddress.getLocalHost().getHostAddress();

        // Get the network interface for the address
        boolean fromNI = false;

        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();

                if (ni.isLoopback()) {
                    continue;
                }

                for (InterfaceAddress iAddr : ni.getInterfaceAddresses()) {
                    InetAddress broadcast = iAddr.getBroadcast();
                    if (broadcast != null) {
                        _subnetMask = broadcast.getHostAddress();
                        fromNI = true;
                    }
                }
            }
        }
        catch (SocketException ex) {
        }

        // Try to generate the subnet mask from the address
        if (_subnetMask == null) {

            //  Find the location of the first dot in the TCP/IP address
            int dotPos = localIP.indexOf('.');
            if (dotPos != -1) {

                //  Extract the leading IP address value
                String ipStr = localIP.substring(0, dotPos);
                int ipVal = Integer.valueOf(ipStr).intValue();

                //  Determine the subnet mask to use
                if (ipVal <= 127) {

                    //  Class A address
                    _subnetMask = "" + ipVal + ".255.255.255";
                } else if (ipVal <= 191) {

                    //  Class B adddress
                    dotPos++;
                    while (localIP.charAt(dotPos) != '.' && dotPos < localIP.length())
                        dotPos++;

                    if (dotPos < localIP.length())
                        _subnetMask = localIP.substring(0, dotPos) + ".255.255";
                } else if (ipVal <= 223) {

                    //  Class C address
                    dotPos++;
                    int dotCnt = 1;

                    while (dotCnt < 3 && dotPos < localIP.length()) {

                        //  Check if the current character is a dot
                        if (localIP.charAt(dotPos++) == '.')
                            dotCnt++;
                    }

                    if (dotPos < localIP.length())
                        _subnetMask = localIP.substring(0, dotPos - 1) + ".255";
                }
            }
        }

        //  Check if the subnet mask has been set, if not then use a general
        //  broadcast mask
        if (_subnetMask == null) {

            //  Invalid TCP/IP address string format, use a general broadcast mask
            //  for now.
            _subnetMask = "255.255.255.255";
        }

        //  DEBUG
        if (Debug.EnableInfo && m_debug)
            Debug.println("NetBIOS: Set subnet mask to " + _subnetMask + (fromNI ? " (Network Interface)" : " (Generated)"));

        //  Return the subnet mask string
        return _subnetMask;
    }

    /**
     * Get the WINS/NetBIOS name lookup timeout, in milliseconds.
     *
     * @return int
     */
    public static int getDefaultLookupTimeout() {
        return _lookupTmo;
    }

    /**
     * Return the name lookup type that is used when setting up new sessions, valid values
     * are DNSOnly, WINSOnly, WINSAndDNS. DNSOnly is the default type.
     *
     * @return LookupType
     */
    public static LookupType getDefaultLookupType() {
        return _lookupType;
    }

    /**
     * Return the subnet mask string
     *
     * @return Subnet mask string, in 'nnn.nnn.nnn.nnn' format
     */
    public static String getDefaultSubnetMask() {
        return _subnetMask;
    }

    /**
     * Determine if the WINS server address is configured
     *
     * @return boolean
     */
    public final static boolean hasDefaultWINSServer() {
        return _winsServer != null ? true : false;
    }

    /**
     * Return the WINS server address
     *
     * @return InetAddress
     */
    public final static InetAddress getDefaultWINSServer() {
        return _winsServer;
    }

    /**
     * Determine if SMB session debugging is enabled
     *
     * @return true if debugging is enabled, else false.
     */
    public static boolean isDebug() {
        return m_debug;
    }

    /**
     * Return the next session index
     *
     * @return int
     */
    private final static synchronized int getSessionId() {
        return m_sessIdx++;
    }

    /**
     * Return the JVM unique id, used when generating caller names
     *
     * @return int
     */
    public final static int getJVMIndex() {
        return m_jvmIdx;
    }

    /**
     * Convert the TCP/IP host name to a NetBIOS name string.
     *
     * @param hostName java.lang.String
     * @return java.lang.String
     */
    public static String NetBIOSName(String hostName) {

        //  Check if the host name contains a domain name
        String nbName = hostName.toUpperCase();
        int pos = nbName.indexOf(".");

        if (pos != -1) {

            //  Strip the domain name for the NetBIOS name
            nbName = nbName.substring(0, pos);
        }

        //  Return the NetBIOS name string
        return nbName;
    }

    /**
     * Override the NetBIOS session port to connect to
     *
     * @param port int
     */
    public final void setSessionPort(int port) {
        m_remotePort = port;
    }

    /**
     * Override the name lookup type(s) to use
     *
     * @param lookupTyp LookupType
     */
    public final void setLookupType(LookupType lookupTyp) {
        m_lookupType = lookupTyp;
    }

    /**
     * Override the name lookup timeout, in milliseconds
     *
     * @param tmo int
     */
    public final void setLookupTimeout(int tmo) {
        m_lookupTmo = tmo;
    }

    /**
     * Override the default WINS server address
     *
     * @param addr InetAddress
     */
    public final void setWINSServer(InetAddress addr) {
        m_winsServer = addr;
    }

    /**
     * Override the default subnet mask
     *
     * @param mask String
     */
    public final void setSubnetMask(String mask) {
        m_subnetMask = mask;
    }

    /**
     * Enable/disable NetBIOS session debugging
     *
     * @param dbg true to enable debugging, else false
     */
    public static void setDebug(boolean dbg) {
        m_debug = dbg;
    }

    /**
     * Set the WINS/NetBIOS name lookup timeout value, in milliseconds.
     *
     * @param tmo int
     */
    public static void setDefaultLookupTimeout(int tmo) {
        if (tmo >= 250)
            _lookupTmo = tmo;
    }

    /**
     * Set the name lookup type(s) to be used when opening new sessions, valid values
     * are DNSOnly, WINSOnly, WINSAndDNS. DNSOnly is the default type.
     *
     * @param typ LookupType
     */
    public static void setDefaultLookupType(LookupType typ) {
        _lookupType = typ;
    }

    /**
     * Set the subnet mask string
     *
     * @param subnet Subnet mask string, in 'nnn.nnn.nnn.nnn' format
     */
    public static void setDefaultSubnetMask(String subnet) {
        _subnetMask = subnet;
    }

    /**
     * Set the WINS server address
     *
     * @param addr InetAddress
     */
    public final static void setDefaultWINSServer(InetAddress addr) {
        _winsServer = addr;
    }

    /**
     * Connect to a remote host.
     *
     * @param remHost Remote host node name/NetBIOS name.
     * @param locName Local name/NetBIOS name.
     * @param remAddr Optional remote address, if null then lookup will be done to convert name to address
     * @throws java.io.IOException           I/O error occurred.
     * @throws java.net.UnknownHostException Remote host is unknown.
     */
    public void Open(String remHost, String locName, String remAddr)
            throws java.io.IOException, java.net.UnknownHostException {

        //  Debug mode
        if (Debug.EnableInfo && m_debug)
            Debug.println("NetBIOS: Call " + remHost);

        //  Convert the remote host name to an address
        boolean dnsLookup = false;
        InetAddress addr = null;

        //	Set the remote address is specified
        if (remAddr != null) {

            //	Use the specified remote address
            addr = InetAddress.getByName(remAddr);
        } else {

            //  Try a WINS/NetBIOS type name lookup, if enabled
            if (getLookupType() != LookupType.DNS_ONLY) {
                try {
                    NetBIOSName netName = FindName(remHost, NetBIOSName.FileServer, 500);
                    if (netName != null && netName.numberOfAddresses() > 0)
                        addr = InetAddress.getByName(netName.getIPAddressString(0));
                }
                catch (Exception ex) {
                }
            }

            //  Try a DNS type name lookup, if enabled
            if (addr == null && getLookupType() != LookupType.WINS_ONLY) {
                addr = InetAddress.getByName(remHost);
                dnsLookup = true;
            }
        }

        //  Check if we translated the remote host name to an address
        if (addr == null)
            throw new java.net.UnknownHostException(remHost);

        //  Debug mode
        if (Debug.EnableInfo && m_debug)
            Debug.println("NetBIOS: Remote node has address " + addr.getHostAddress() + " (" + (dnsLookup ? "DNS" : "WINS") + ")");

        //	Determine the remote name to call
        String remoteName = null;

        if (getRemoteNameType() == NetBIOSName.FileServer && useWildcardFileServerName() == true)
            remoteName = "*SMBSERVER";
        else
            remoteName = remHost;

        //	Open a session to the remote server
        RFCNetBIOSProtocol.MsgType resp = openSession(remoteName, addr);

        //	Check the server response
        if (resp == RFCNetBIOSProtocol.MsgType.ACK)
            return;
        else if (resp == RFCNetBIOSProtocol.MsgType.REJECT) {

            //  If the session was rejected (especially with the remotename "*SMBSERVER") we need to get a proper netbios name
            //  via WINS lookup, to be able to setup a NetBIOS session
            if (IPAddress.isNumericAddress(remHost)) {
                NetBIOSName nbName = null;

                //  Convert the TCP/IP address to a NetBIOS name using WINS/NetBIOS name lookup
                LookupType tmpLookupType = getLookupType();

                setLookupType(LookupType.WINS_ONLY);
                nbName = NetBIOSSession.ConvertAddressToName(remHost, NetBIOSName.FileServer, false, this);

                //  Set the lookuptype back to its intial state
                setLookupType(tmpLookupType);
                remHost = nbName.getFullName();
            }

            //	Try the connection again with the remote host name
            if (remoteName.equals(remHost) == false)
                resp = openSession(remHost, addr);

            //	Check if we got a valid response this time
            if (resp == RFCNetBIOSProtocol.MsgType.ACK)
                return;

            //	Server rejected the connection
            throw new java.io.IOException("NetBIOS session reject");
        } else if (resp == RFCNetBIOSProtocol.MsgType.RETARGET)
            throw new java.io.IOException("NetBIOS ReTarget");

        //  Invalid session response, hangup the session
        Close();
        throw new java.io.IOException("Invalid NetBIOS response, 0x" + Integer.toHexString(resp.intValue()));
    }

    /**
     * Open a NetBIOS session to a remote server
     *
     * @param remoteName String
     * @param addr       InetAddress
     * @return RFCNetBIOSProtocol.MsgType
     * @exception IOException Socket error
     */
    private final RFCNetBIOSProtocol.MsgType openSession(String remoteName, InetAddress addr)
            throws IOException {

        // Get starting timestamp, for timeout
        long start = System.currentTimeMillis();

        //  Create the socket
        m_nbSocket = new Socket();
        try {
            m_nbSocket.connect(new InetSocketAddress(addr, m_remotePort), getTimeout());
        }
        catch (IOException e) {
            m_nbSocket = null;
            throw e;
        }

        //  Enable the timeout on the socket, and disable the Nagle algorithm
        m_nbSocket.setSoTimeout(getTimeout());
        m_nbSocket.setTcpNoDelay(true);

        //  Attach input/output streams to the socket
        m_nbIn = new DataInputStream(m_nbSocket.getInputStream());
        m_nbOut = new DataOutputStream(m_nbSocket.getOutputStream());

        //	Allocate a buffer to receive the session response
        byte[] inpkt = new byte[RFCNetBIOSProtocol.SESSRESP_LEN];

        //	Create the from/to NetBIOS names
        NetBIOSName fromName = createUniqueCallerName();
        NetBIOSName toName = new NetBIOSName(remoteName, getRemoteNameType(), false);

        //	Debug
        if (Debug.EnableInfo && m_debug)
            Debug.println("NetBIOS: Call from " + fromName + " to " + toName);

        //	Build the session request packet
        NetBIOSPacket nbPkt = new NetBIOSPacket();
        nbPkt.buildSessionSetupRequest(fromName, toName);

        //  Send the session request packet
        m_nbOut.write(nbPkt.getBuffer(), 0, nbPkt.getLength());

        // Check for available bytes, so we don't hang in read()
        while (m_nbIn.available() < 1) {

            if ((getTimeout() > 0) && (System.currentTimeMillis() - start) > getTimeout()) {

                // Close socket and streams
                m_nbIn.close();
                m_nbIn = null;

                m_nbOut.close();
                m_nbOut = null;

                m_nbSocket.close();
                m_nbSocket = null;

                // Throw timeout exception
                throw new IOException("NetBIOS session response timeout");
            }
            try {
                Thread.sleep(100);
            }
            catch (Exception e) {
            }
        }

        //  Allocate a buffer for the session request response, and read the response
        RFCNetBIOSProtocol.MsgType resp = RFCNetBIOSProtocol.MsgType.INVALID;

        if (m_nbIn.read(inpkt, 0, RFCNetBIOSProtocol.SESSRESP_LEN) >= RFCNetBIOSProtocol.HEADER_LEN) {

            //  Check the session request response
            resp = RFCNetBIOSProtocol.MsgType.fromInt((int) (inpkt[0] & 0xFF));

            //  Debug mode
            if (Debug.EnableInfo && m_debug)
                Debug.println("NetBIOS: Rx " + resp.name());
        }

        //	Check for a positive response
        if (resp != RFCNetBIOSProtocol.MsgType.ACK) {

            //	Close the socket and streams
            m_nbIn.close();
            m_nbIn = null;

            m_nbOut.close();
            m_nbOut = null;

            m_nbSocket.close();
            m_nbSocket = null;
        }

        //	Return the response code
        return resp;
    }

    /**
     * Return the local NetBIOS name type.
     *
     * @return char
     */
    public char getLocalNameType() {
        return m_locNameType;
    }

    /**
     * Return the remote NetBIOS name type.
     *
     * @return char
     */
    public char getRemoteNameType() {
        return m_remNameType;
    }

    /**
     * Close the NetBIOS session.
     *
     * @exception IOException If an I/O error occurs
     */
    public void Close()
            throws IOException {

        //  Debug mode
        if (Debug.EnableInfo && m_debug)
            Debug.println("NetBIOS: HangUp");

        //  Close the session if active
        if (m_nbSocket != null) {
            m_nbSocket.close();
            m_nbSocket = null;
        }
    }

    /**
     * Receive a data packet from the remote host.
     *
     * @param buf Byte buffer to receive the data into.
     * @return Length of the received data.
     * @exception IOException I/O error occurred.
     */
    public int Receive(byte[] buf)
            throws java.io.IOException {

        //  Read a data packet, dump any session keep alive packets
        RFCNetBIOSProtocol.MsgType pkttyp;
        int rdlen;

        do {

            //  Read a packet header
            rdlen = m_nbIn.read(buf, 0, RFCNetBIOSProtocol.HEADER_LEN);

            //  Debug mode
            if (Debug.EnableInfo && m_debug)
                Debug.println("NetBIOS: Read " + rdlen + " bytes");

            //  Check if a header was received
            if (rdlen < RFCNetBIOSProtocol.HEADER_LEN)
                throw new java.io.IOException("NetBIOS Short Read");

            //  Get the packet type from the header
            pkttyp = RFCNetBIOSProtocol.MsgType.fromInt((int) (buf[0] & 0xFF));

        } while (pkttyp == RFCNetBIOSProtocol.MsgType.KEEPALIVE);

        //  Debug mode
        if (Debug.EnableInfo && m_debug) {
            Debug.print("NetBIOS: Rx pkt ");

            switch (pkttyp) {
                case MESSAGE:
                    Debug.println("Session_Message");
                    break;
                case RETARGET:
                    Debug.print("Session_Retarget - ");
                    int len = (int) DataPacker.getShort(buf, 2);
                    len = m_nbIn.read(buf, RFCNetBIOSProtocol.HEADER_LEN, len);
                    int addr = DataPacker.getInt(buf, 4);
                    Debug.println(Integer.toHexString(addr));
                    break;
                default:
                    Debug.println("NetBIOS: Rx Pkt Type = " + pkttyp + ", " + Integer.toHexString(pkttyp.intValue()));
                    break;
            }
        }

        //  Check that the packet is a session data packet
        if (pkttyp != RFCNetBIOSProtocol.MsgType.MESSAGE)
            throw new java.io.IOException("NetBIOS Unknown Packet Type, " + pkttyp);

        //  Extract the data size from the packet header
        int pktlen = (int) DataPacker.getShort(buf, 2);
        if (Debug.EnableInfo && m_debug)
            Debug.println("NetBIOS: Rx Data Len = " + pktlen);

        //  Check if the user buffer is long enough to contain the data
        if (buf.length < (pktlen + RFCNetBIOSProtocol.HEADER_LEN)) {

            //  Debug mode
            if (Debug.EnableInfo && m_debug) {
                Debug.println("NetBIOS: Rx Pkt Type = " + pkttyp + ", " + Integer.toHexString(pkttyp.intValue()));
                Debug.println("NetBIOS: Rx Buf Too Small pkt=" + pktlen + " buflen=" + buf.length);
                HexDump.Dump(buf, 16, 0);
            }

            throw new java.io.IOException("NetBIOS Recv Buffer Too Small (pkt=" + pktlen + "/buf=" + buf.length + ")");
        }

        //  Read the data part of the packet into the users buffer, this may take
        //  several reads
        int totlen = 0;
        int offset = RFCNetBIOSProtocol.HEADER_LEN;

        while (pktlen > 0) {

            //  Read the data
            rdlen = m_nbIn.read(buf, offset, pktlen);

            //  Update the received length and remaining data length
            totlen += rdlen;
            pktlen -= rdlen;

            //  Update the user buffer offset as more reads will be required
            //  to complete the data read
            offset += rdlen;

        } // end while reading data

        //  Return the received data length, not including the NetBIOS header
        return totlen;
    }

    /**
     * Send a data packet to the remote host.
     *
     * @param data Byte array containing the data to be sent.
     * @param siz  Length of the data to send.
     * @return true if the data was sent successfully, else false.
     * @exception IOException I/O error occurred.
     */
    public boolean Send(byte[] data, int siz)
            throws java.io.IOException {

        //  Check that the session is valid
        if (m_nbSocket == null)
            return false;

        //  Debug mode
        if (Debug.EnableInfo && m_debug)
            Debug.println("NetBIOS: Tx " + siz + " bytes");

        //  Fill in the NetBIOS message header, this is already allocated as part of the users buffer.
        data[0] = (byte) RFCNetBIOSProtocol.MsgType.MESSAGE.intValue();
        data[1] = (byte) 0;

        DataPacker.putShort((short) siz, data, 2);

        //  Output the data packet
        int bufSiz = siz + RFCNetBIOSProtocol.HEADER_LEN;
        m_nbOut.write(data, 0, bufSiz);
        return true;
    }

    /**
     * Set the socket timeout
     *
     * @param tmo int
     */
    public void setTimeout(int tmo) {

        // Call the base class to store the timeout
        super.setTimeout(tmo);

        // Set the socket timeout, if the socket is valid
        if (m_nbSocket != null) {
            try {
                m_nbSocket.setSoTimeout(getTimeout());
            }
            catch (SocketException ex) {
            }
        }
    }

    /**
     * Set the local NetBIOS name type for this session.
     *
     * @param nameType int
     */
    public void setLocalNameType(char nameType) {
        m_locNameType = nameType;
    }

    /**
     * Set the remote NetBIOS name type.
     *
     * @param nameType char
     */
    public void setRemoteNameType(char nameType) {
        m_remNameType = nameType;
    }

    /**
     * Set the caller session name template string that is appended to the local host name to create a unique caller
     * name.
     *
     * @param template String
     * @exception NameTemplateException Invalid name template
     */
    public final static void setCallerNameTemplate(String template)
            throws NameTemplateException {

        //	Check if the template string is valid, is not too long
        if (template == null || template.length() == 0 || template.length() > MaxCallerNameTemplateLength)
            throw new NameTemplateException("Invalid template string, " + template);

        //	Template must contain at least one session id template character
        if (template.indexOf(SessionIdChar) == -1)
            throw new NameTemplateException("No session id character in template");

        //	Check if the template contains any invalid characters
        for (int i = 0; i < template.length(); i++) {
            if (ValidTemplateChars.indexOf(template.charAt(i)) == -1)
                throw new NameTemplateException("Invalid character in template, '" + template.charAt(i) + "'");
        }

        //	Set the caller name template string
        m_callerTemplate = template;

        //	Clear the local name part string so that it will be regenerated to match the new template string
        m_localNamePart = null;
    }

    /**
     * Set the JVM index, used to generate unique caller names when multiple JVMs are run on the same host.
     *
     * @param jvmIdx int
     */
    public final static void setJVMIndex(int jvmIdx) {
        if (jvmIdx >= 0)
            m_jvmIdx = jvmIdx;
    }

    /**
     * Create a unique caller name for a new NetBIOS session. The unique name contains the local host name plus
     * an index that is unique for this JVM, plus an optional JVM index.
     *
     * @return NetBIOSName
     */
    private final NetBIOSName createUniqueCallerName() {

        //	Check if the local name part has been set
        if (m_localNamePart == null) {

            String localName = null;

            try {
                localName = InetAddress.getLocalHost().getHostName();
            }
            catch (Exception ex) {
            }

            //	Check if the name contains a domain
            int pos = localName.indexOf(".");

            if (pos != -1)
                localName = localName.substring(0, pos);

            //	Truncate the name if the host name plus the template is longer than 15 characters.
            int nameLen = 16 - m_callerTemplate.length();

            if (localName.length() > nameLen)
                localName = localName.substring(0, nameLen - 1);

            //	Set the local host name part
            m_localNamePart = localName.toUpperCase();
        }

        //	Get a unique session id and the unique JVM id
        int sessId = getSessionId();
        int jvmId = getJVMIndex();

        //	Build the NetBIOS name string
        StringBuffer nameBuf = new StringBuffer(16);

        nameBuf.append(m_localNamePart);

        //	Process the caller name template string
        int idx = 0;
        int len = -1;

        while (idx < m_callerTemplate.length()) {

            //	Get the current template character
            char ch = m_callerTemplate.charAt(idx++);

            switch (ch) {

                // Session id
                case SessionIdChar:
                    len = findRepeatLength(m_callerTemplate, idx, SessionIdChar);
                    appendZeroPaddedHexValue(sessId, len, nameBuf);
                    idx += len - 1;
                    break;

                //	JVM id
                case JVMIdChar:
                    len = findRepeatLength(m_callerTemplate, idx, JVMIdChar);
                    appendZeroPaddedHexValue(jvmId, len, nameBuf);
                    idx += len - 1;
                    break;

                //	Pass any other characters through to the name string
                default:
                    nameBuf.append(ch);
                    break;
            }
        }

        //	Create the NetBIOS name object
        return new NetBIOSName(nameBuf.toString(), getLocalNameType(), false);
    }

    /**
     * Find the length of the character block in the specified string
     *
     * @param str String
     * @param pos int
     * @param ch  char
     * @return int
     */
    private final int findRepeatLength(String str, int pos, char ch) {
        int len = 1;

        while (pos < str.length() && str.charAt(pos++) == ch)
            len++;
        return len;
    }

    /**
     * Append a zero filled hex string to the specified string
     *
     * @param val int
     * @param len int
     * @param str StringBuffer
     */
    private final void appendZeroPaddedHexValue(int val, int len, StringBuffer str) {

        //	Create the hex string of the value
        String hex = Integer.toHexString(val);

        //	Pad the final string as required
        for (int i = 0; i < len - hex.length(); i++)
            str.append("0");
        str.append(hex);
    }

    /**
     * Return the use wildcard file server name flag status. If true the target name when conencting
     * to a remote file server will be '*SMBSERVER', if false the remote name will be used.
     *
     * @return boolean
     */
    public final boolean useWildcardFileServerName() {
        return m_useWildcardServerName;
    }

    /**
     * Set the use wildcard file server name flag. If true the target name when conencting
     * to a remote file server will be '*SMBSERVER', if false the remote name will be used.
     *
     * @param useWildcard boolean
     */
    public final void setWildcardFileServerName(boolean useWildcard) {
        m_useWildcardServerName = useWildcard;
    }

    /**
     * Return the default setting for the use wildcard file server name setting
     *
     * @return boolean
     */
    public final static boolean getDefaultWildcardFileServerName() {
        return _useWildcardFileServer;
    }

    /**
     * Set the default use wildcard file server name flag. If true the target name when conencting
     * to a remote file server will be '*SMBSERVER', if false the remote name will be used.
     *
     * @param useWildcard boolean
     */
    public final static void setDefaultWildcardFileServerName(boolean useWildcard) {
        _useWildcardFileServer = useWildcard;
    }

    /**
     * Finalize the NetBIOS session object
     */
    protected void finalize() {

        //  Close the socket
        if (m_nbSocket != null) {
            try {
                m_nbSocket.close();
            }
            catch (java.io.IOException ex) {
            }
            m_nbSocket = null;
        }
    }
}
