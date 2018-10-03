/*
 * Copyright (C) 2018 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.netbios.win32;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import org.filesys.netbios.NetBIOSName;
import org.filesys.util.DataBuffer;
import org.filesys.util.IPAddress;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

/**
 * Win32 NetBIOS Native Interface Class
 *
 * @author gkspencer
 */
public class Win32NetBIOS {

    // Constants
    //
    // FIND_NAME_BUFFER structure length
    protected final static int FindNameBufferLen = 33;

    /**
     * Add a NetBIOS name to the local name table
     *
     * @param lana int
     * @param name byte[]
     * @return int
     */
    public static int AddName( int lana, byte[] name) {

        // Setup the Netbios control block
        NCB ncb = new NCB();

        ncb.ncb_command = NetBIOS.NCBAddName;
        ncb.ncb_lana_num = (byte) (lana & 0xFF);

        for ( int i = 0; i < NetBIOS.NCBNameSize; i++)
            ncb.ncb_name[ i] = name[ i];

        // NetBIOS native call
        if ( NetbiosApi.INSTANCE.Netbios( ncb) == NetBIOS.NRC_GoodRet) {

            // Return the name number
            return ncb.ncb_num;
        }

        // Return the error status
        return -ncb.ncb_retcode;
    }

    /**
     * Add a NetBIOS group name to the local name table
     *
     * @param lana int
     * @param name NetBIOSName
     * @return int
     */
    public static int AddGroupName( int lana, NetBIOSName name) {

        // Setup the Netbios control block
        NCB ncb = new NCB();

        ncb.ncb_command = NetBIOS.NCBAddGrName;
        ncb.ncb_lana_num = (byte) (lana & 0xFF);

        byte[] nbName = name.getNetBIOSName();

        for ( int i = 0; i < NetBIOS.NCBNameSize; i++)
            ncb.ncb_name[ i] = nbName[ i];

        // NetBIOS native call
        if ( NetbiosApi.INSTANCE.Netbios( ncb) == NetBIOS.NRC_GoodRet) {

            // Return the name number
            return ncb.ncb_num;
        }

        // Return the error status
        return -ncb.ncb_retcode;
    }

    /**
     * Find a NetBIOS name
     *
     * @param lana   int
     * @param nbName NetBIOSName
     * @return int
     */
    public static int FindName(int lana, NetBIOSName nbName) {

        // Allocate a buffer to receive the name details
        byte[] nameBuf = new byte[nbName.isGroupName() ? 65535 : 4096];

        // Get the raw NetBIOS name data
        int sts = FindNameRaw(lana, nbName.getNetBIOSName(), nameBuf, nameBuf.length);

        if (sts != NetBIOS.NRC_GoodRet)
            return -sts;

        // Unpack the FIND_NAME_HEADER structure
        DataBuffer buf = new DataBuffer(nameBuf, 0, nameBuf.length);

        int nodeCount = buf.getShort();
        buf.skipBytes(1);

        boolean isGroupName = buf.getByte() == 0 ? false : true;
        nbName.setGroup(isGroupName);

        // Unpack the FIND_NAME_BUFFER structures
        int curPos = buf.getPosition();

        for (int i = 0; i < nodeCount; i++) {

            // FIND_NAME_BUFFER:
            // UCHAR length
            // UCHAR access_control
            // UCHAR frame_control
            // UCHAR destination_addr[6]
            // UCHAR source_addr[6]
            // UCHAR routing_info[18]

            // Skip to the source_addr field
            buf.skipBytes(9);

            // Source address field format should be 0.0.n.n.n.n for TCP/IP address
            if (buf.getByte() == 0 && buf.getByte() == 0) {

                // Looks like a TCP/IP format address, unpack it
                byte[] ipAddr = new byte[4];

                ipAddr[0] = (byte) buf.getByte();
                ipAddr[1] = (byte) buf.getByte();
                ipAddr[2] = (byte) buf.getByte();
                ipAddr[3] = (byte) buf.getByte();

                // Add the address to the list of TCP/IP addresses for the NetBIOS name
                nbName.addIPAddress(ipAddr);

                // Skip to the start of the next FIND_NAME_BUFFER structure
                curPos += FindNameBufferLen;
                buf.setPosition(curPos);
            }
        }

        // Return the node count
        return nodeCount;
    }

    /**
     * Find a NetBIOS name, return the name buffer
     *
     * @param lana    int
     * @param name    byte[]
     * @param nameBuf byte[]
     * @param bufLen  int
     * @return int
     */
    public static int FindNameRaw(int lana, byte[] name, byte[] nameBuf, int bufLen) {

        // Allocate a JNA buffer for the native call
        Pointer jnaNameBuf = new Memory( bufLen);

        // Setup the Netbios control block
        NCB ncb = new NCB();

        ncb.ncb_command = NetBIOS.NCBFindName;
        ncb.ncb_lana_num = (byte) (lana & 0xFF);
        ncb.ncb_buffer = jnaNameBuf;
        ncb.ncb_length = (short) bufLen;

        for ( int i = 0; i < NetBIOS.NCBNameSize; i++)
            ncb.ncb_name[ i] = name[ i];

        if ( NetbiosApi.INSTANCE.Netbios( ncb) == NetBIOS.NRC_GoodRet) {

            // Copy the name details to the callers buffer
            jnaNameBuf.read( 0, nameBuf, 0, ncb.ncb_length);
        }

        return ncb.ncb_retcode;
    }

    /**
     * Delete a NetBIOS name from the local name table
     *
     * @param lana int
     * @param name byte[]
     * @return int
     */
    public static int DeleteName(int lana, byte[] name) {
        return -1;
    }

    /**
     * Get a list of the available NetBIOS LANAs
     *
     * @return List of available LANAs
     */
    public static List<Integer> LanaEnum() {

        // Setup the Netbios control block
        NCB ncb = new NCB();
        Pointer lanaBuf = new Memory(NetBIOS.MaxLANA + 1);

        ncb.ncb_command = NetBIOS.NCBEnum;
        ncb.ncb_buffer = lanaBuf;
        ncb.ncb_length = NetBIOS.MaxLANA + 1;

        List<Integer> lanaList = null;

        if ( NetbiosApi.INSTANCE.Netbios( ncb) == NetBIOS.NRC_GoodRet) {

            int lanaCnt = (int) lanaBuf.getByte( 0);
            lanaList = new ArrayList<Integer>( lanaCnt);

            for ( int i = 1; i <= lanaCnt; lanaList.add( new Integer( lanaBuf.getByte( i++))));
        }

        return lanaList;
    }

    /**
     * Reset a NetBIOS LANA
     *
     * @param lana int
     * @return int
     */
    public static int Reset( int lana) {

        // Setup the Netbios control block
        NCB ncb = new NCB();

        ncb.ncb_command = NetBIOS.NCBReset;
        ncb.ncb_lana_num = (byte) (lana & 0xFF);

        ncb.ncb_callname[0] = 20;   // maximum number of sessions
        ncb.ncb_callname[2] = 30;   // maximum number of names

        // NetBIOS native call
        return NetbiosApi.INSTANCE.Netbios( ncb);
    }

    /**
     * Listen for an incoming session request
     *
     * @param lana       int
     * @param toName     byte[]
     * @param fromName   byte[]
     * @param callerName byte[]
     * @return int
     */
    public static int Listen(int lana, byte[] toName, byte[] fromName, byte[] callerName) {

        // Setup the Netbios control block
        NCB ncb = new NCB();

        ncb.ncb_command = NetBIOS.NCBListen;
        ncb.ncb_lana_num = (byte) (lana & 0xFF);

        ncb.ncb_rto = 0;    // no timeout
        ncb.ncb_sto = 0;    // no timeout

        // Setup the to/from names
        for ( int i = 0; i < NetBIOS.NCBNameSize; i++)
            ncb.ncb_name[ i] = toName[ i];

        for( int i = 0; i < NetBIOS.NCBNameSize; i++)
            ncb.ncb_callname[ i] = fromName[ i];

        // NetBIOS native call
        if ( NetbiosApi.INSTANCE.Netbios( ncb) == NetBIOS.NRC_GoodRet) {

            // Return the callers name, if available
            if ( ncb.ncb_callname[ 0] != '\0') {
                for ( int i = 0; i < NetBIOS.NCBNameSize; i++)
                    callerName[ i] = ncb.ncb_callname[ i];
            }

            // Return the session id
            return ncb.ncb_lsn;
        }

        // Return the error status
        return -ncb.ncb_retcode;
    }

    /**
     * Receive a data packet on a session
     *
     * @param lana   int
     * @param lsn    int
     * @param buf    byte[]
     * @param off    int
     * @param maxLen int
     * @return int
     */
    public static int Receive(int lana, int lsn, byte[] buf, int off, int maxLen) {

        // Allocate a JNA buffer for the native call
        short len = (short) (maxLen - off);
        Pointer jnaBuf = new Memory( len);

        // Setup the Netbios control block
        NCB ncb = new NCB();

        ncb.ncb_command = NetBIOS.NCBRecv;
        ncb.ncb_lana_num = (byte) (lana & 0xFF);
        ncb.ncb_lsn = (byte) (lsn & 0xFF);

        ncb.ncb_rto = 0;
        ncb.ncb_sto = 0;

        ncb.ncb_buffer = jnaBuf;
        ncb.ncb_length = (short) len;

        if ( NetbiosApi.INSTANCE.Netbios( ncb) == NetBIOS.NRC_GoodRet) {

            // Copy the data to the callers buffer
            jnaBuf.read( 0, buf, 0, ncb.ncb_length);

            // Return the received length
            return ncb.ncb_length;
        }

        // Return the NetBIOS status code
        int sts = ncb.ncb_retcode << 24;
        if ( ncb.ncb_retcode == NetBIOS.NRC_Incomp)
            sts += ncb.ncb_length;

        return sts;
    }

    /**
     * Send a data packet on a session
     *
     * @param lana int
     * @param lsn  int
     * @param buf  byte[]
     * @param off  int
     * @param len  int
     * @return int
     */
    public static int Send(int lana, int lsn, byte[] buf, int off, int len) {

        // Allocate a JNA buffer for the native call
        Pointer jnaBuf = new Memory( len);

        // Setup the Netbios control block
        NCB ncb = new NCB();

        ncb.ncb_command = NetBIOS.NCBSend;
        ncb.ncb_lana_num = (byte) (lana & 0xFF);
        ncb.ncb_lsn = (byte) (lsn & 0xFF);

        ncb.ncb_rto = 0;
        ncb.ncb_sto = 0;

        ncb.ncb_buffer = jnaBuf;
        ncb.ncb_length = (short) len;

        // Copy the data to the native buffer
        jnaBuf.write( 0, buf, off, len);

        if ( NetbiosApi.INSTANCE.Netbios( ncb) == NetBIOS.NRC_GoodRet) {

            // Return the sent length
            return ncb.ncb_length;
        }

        // Return the NetBIOS status code
        return -ncb.ncb_retcode;
    }

    /**
     * Send a datagram to a specified name
     *
     * @param lana     int
     * @param srcNum   int
     * @param destName byte[]
     * @param buf      byte[]
     * @param off      int
     * @param len      int
     * @return int
     */
    public static int SendDatagram(int lana, int srcNum, byte[] destName, byte[] buf, int off, int len) {

        // Allocate a JNA buffer for the native call
        Pointer jnaBuf = new Memory( len);

        // Setup the Netbios control block
        NCB ncb = new NCB();

        ncb.ncb_command = NetBIOS.NCBDGSend;
        ncb.ncb_lana_num = (byte) (lana & 0xFF);
        ncb.ncb_num = (byte) (srcNum & 0xFF);

        ncb.ncb_rto = 0;
        ncb.ncb_sto = 0;

        ncb.ncb_buffer = jnaBuf;
        ncb.ncb_length = (short) len;

        // Copy the data to the native buffer
        jnaBuf.write( 0, buf, off, len);

        if ( NetbiosApi.INSTANCE.Netbios( ncb) == NetBIOS.NRC_GoodRet) {

            // Return the sent length
            return ncb.ncb_length;
        }

        // Return the NetBIOS status code
        return -ncb.ncb_retcode;
    }

    /**
     * Send a broadcast datagram
     *
     * @param lana int
     * @param buf  byte[]
     * @param off  int
     * @param len  int
     * @return int
     */
    public static int SendBroadcastDatagram(int lana, byte[] buf, int off, int len) {

        // Allocate a JNA buffer for the native call
        Pointer jnaBuf = new Memory( len);

        // Setup the Netbios control block
        NCB ncb = new NCB();

        ncb.ncb_command = NetBIOS.NCBDGSendBc;
        ncb.ncb_lana_num = (byte) (lana & 0xFF);

        ncb.ncb_rto = 0;
        ncb.ncb_sto = 0;

        ncb.ncb_buffer = jnaBuf;
        ncb.ncb_length = (short) len;

        // Copy the data to the native buffer
        jnaBuf.write( 0, buf, off, len);

        if ( NetbiosApi.INSTANCE.Netbios( ncb) == NetBIOS.NRC_GoodRet) {

            // Return the sent length
            return ncb.ncb_length;
        }

        // Return the NetBIOS status code
        return -ncb.ncb_retcode;
    }

    /**
     * Receive a datagram on a specified name
     *
     * @param lana    int
     * @param nameNum int
     * @param buf     byte[]
     * @param off     int
     * @param maxLen  int
     * @return int
     */
    public static int ReceiveDatagram(int lana, int nameNum, byte[] buf, int off, int maxLen) {
        return -1;
    }

    /**
     * Receive a broadcast datagram
     *
     * @param lana    int
     * @param nameNum int
     * @param buf     byte[]
     * @param off     int
     * @param maxLen  int
     * @return int
     */
    public static int ReceiveBroadcastDatagram(int lana, int nameNum, byte[] buf, int off, int maxLen) {

        // Allocate a JNA buffer for the native call
        short len = (short) (maxLen - off);
        Pointer jnaBuf = new Memory( len);

        // Setup the Netbios control block
        NCB ncb = new NCB();

        ncb.ncb_command = NetBIOS.NCBDGRecvBc;
        ncb.ncb_lana_num = (byte) (lana & 0xFF);
        ncb.ncb_num = (byte) (nameNum & 0xFF);

        ncb.ncb_rto = 0;
        ncb.ncb_sto = 0;

        ncb.ncb_buffer = jnaBuf;
        ncb.ncb_length = (short) len;

        if ( NetbiosApi.INSTANCE.Netbios( ncb) == NetBIOS.NRC_GoodRet) {

            // Copy the data to the callers buffer
            jnaBuf.read( 0, buf, 0, ncb.ncb_length);

            // Return the received length
            return ncb.ncb_length;
        }

        // Return the NetBIOS status code
        return -ncb.ncb_retcode;
    }

    /**
     * Hangup a session
     *
     * @param lana int
     * @param lsn int
     * @return int
     */
    public static int Hangup(int lana, int lsn) {

        // Setup the Netbios control block
        NCB ncb = new NCB();

        ncb.ncb_command = NetBIOS.NCBHangup;
        ncb.ncb_lana_num = (byte) (lana & 0xFF);
        ncb.ncb_lsn      = (byte) (lsn & 0xFF);

        // NetBIOS native call
        return NetbiosApi.INSTANCE.Netbios( ncb);
    }

    /**
     * Enumerate the available LANAs
     *
     * @return List of available LANAs
     */
    public static List<Integer> LanaEnumerate() {

        // Make sure that there is an active network adapter as making calls to the LanaEnum native
        // call causes problems when there are no active network adapters.
        boolean adapterAvail = false;

        try {

            // Enumerate the available network adapters and check for an active adapter, not
            // including the loopback adapter
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();

            while (nis.hasMoreElements() && adapterAvail == false) {

                NetworkInterface ni = nis.nextElement();
                if (ni.getName().equals("lo") == false) {

                    // Make sure the adapter has a valid IP address
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    if (addrs.hasMoreElements())
                        adapterAvail = true;
                }
            }
        }
        catch (SocketException ex) {
        }

        // Check if there are network adapter(s) available
        if (adapterAvail == false)
            return null;

        // Call the native code to return the available LANA list
        return LanaEnum();
    }

    /**
     * Return the local computers NetBIOS name
     *
     * @return String
     */
    public static String GetLocalNetBIOSName() {
        return Kernel32Util.getComputerName();
    }

    /**
     * Return the local domain name
     *
     * @return String
     */
    public static String GetLocalDomainName() {
        return Netapi32Util.getDomainName( null);
    }

    /**
     * Return a comma delimeted list of WINS server TCP/IP addresses, or null if no WINS servers are
     * configured.
     *
     * @return String
     */
    public static String getWINSServerList() {

        // Open the NetBIOS over TCP/IP registry key
        WinReg.HKEYByReference pnbtKey = new WinReg.HKEYByReference();
        StringBuffer winsListStr = new StringBuffer();

        String ifaceRoot = "System\\CurrentControlSet\\Services\\NetBT\\Parameters\\Interfaces";

        if (Advapi32.INSTANCE.RegOpenKeyEx( WinReg.HKEY_LOCAL_MACHINE, ifaceRoot,
                0, WinNT.KEY_ENUMERATE_SUB_KEYS, pnbtKey) == W32Errors.ERROR_SUCCESS) {

            // Allocate a buffer for the subkey name
            char[] name = new char[256];
            IntByReference lpcchValueName = new IntByReference( 256);

            // Allocate a buffer for the WINS server list
            char[] winsBuf = new char[256];
            IntByReference lpcbData = new IntByReference();
            IntByReference lpType = new IntByReference();

            // Enumerate the interfaces
            int sts = W32Errors.ERROR_SUCCESS;
            int keyIndex = 0;

            while ( sts == W32Errors.ERROR_SUCCESS) {

                sts = Advapi32.INSTANCE.RegEnumKeyEx( pnbtKey.getValue(), keyIndex++, name, lpcchValueName, null, null, null, null);

                if ( sts != W32Errors.ERROR_SUCCESS)
                    continue;

                // Check if we found a TcpIP interface
                String ifaceName = Native.toString( name);

                if ( ifaceName.startsWith( "Tcpip_")) {

                    // Build the path to the specific interface key
                    String ifaceKey = ifaceRoot + "\\" + ifaceName;

                    // Check if there is a WINS server list for the current interface
                    try {
                        if (Advapi32Util.registryValueExists(WinReg.HKEY_LOCAL_MACHINE, ifaceKey, "NameServerList")) {

                            // Get the WINS name server list for the current interface
                            String[] winsList = Advapi32Util.registryGetStringArray(WinReg.HKEY_LOCAL_MACHINE, ifaceKey, "NameServerList");

                            if (winsList != null && winsList.length > 0) {
                                for (int i = 0; i < winsList.length; i++) {
                                    winsListStr.append(winsList[i]);
                                    winsListStr.append(",");
                                }
                            }
                        }
                    }
                    catch ( Exception ex) {

                    }
                }
            }

            // Close the interfaces key
            Advapi32.INSTANCE.RegCloseKey( pnbtKey.getValue());
        }

        return winsListStr.toString();
    }

    /**
     * Wait for a network address change
     */
    public static void waitForNetworkAddressChange() {

        // Wait for a network address change
        int sts = IpHlpAPI.INSTANCE.NotifyAddrChange( null, null);
    }

    /**
     * Cancel the wait for network address change request
     */
    public static void cancelWaitForNetworkAddressChange() {

        // Cancel a wait for network address change
        IpHlpAPI.INSTANCE.CancelIPChangeNotify( null);
    }

    /**
     * Find the TCP/IP address for a LANA
     *
     * @param lana int
     * @return String
     */
    public static final String getIPAddressForLANA(int lana) {

        // Get the local NetBIOS name
        String localName = GetLocalNetBIOSName();
        if (localName == null)
            return null;

        // Create a NetBIOS name for the local name
        NetBIOSName nbName = new NetBIOSName(localName, NetBIOSName.WorkStation, false);

        // Get the local NetBIOS name details
        int sts = FindName(lana, nbName);

        if (sts == -NetBIOS.NRC_EnvNotDef) {

            // Reset the LANA then try the name lookup again
            Reset(lana);
            sts = FindName(lana, nbName);
        }

        // Check if the name lookup was successful
        String ipAddr = null;

        if (sts >= 0) {

            // Get the first IP address from the list
            ipAddr = nbName.getIPAddressString(0);
        }

        // Return the TCP/IP address for the LANA
        return ipAddr;
    }

    /**
     * Find the adapter name for a LANA
     *
     * @param lana int
     * @return String
     */
    public static final String getAdapterNameForLANA(int lana) {

        // Get the TCP/IP address for a LANA
        String ipAddr = getIPAddressForLANA(lana);
        if (ipAddr == null)
            return null;

        // Get the list of available network adapters
        Hashtable<String, NetworkInterface> adapters = getNetworkAdapterList();
        String adapterName = null;

        if (adapters != null) {

            // Find the network adapter for the TCP/IP address
            NetworkInterface ni = adapters.get(ipAddr);
            if (ni != null)
                adapterName = ni.getDisplayName();
        }

        // Return the adapter name for the LANA
        return adapterName;
    }

    /**
     * Find the LANA for a TCP/IP address
     *
     * @param addr String
     * @return int
     */
    public static final int getLANAForIPAddress(String addr) {

        // Check if the address is a numeric TCP/IP address
        if (IPAddress.isNumericAddress(addr) == false)
            return -1;

        // Get a list of the available NetBIOS LANAs
        List<Integer> lanas = LanaEnum();
        if (lanas == null || lanas.size() == 0)
            return -1;

        // Search for the LANA with the matching TCP/IP address
        for (int i = 0; i < lanas.size(); i++) {

            // Get the current LANAs TCP/IP address
            String curAddr = getIPAddressForLANA(lanas.get( i));
            if (curAddr != null && curAddr.equals(addr))
                return lanas.get( i);
        }

        // Failed to find the LANA for the specified TCP/IP address
        return -1;
    }

    /**
     * Find the LANA for a network adapter
     *
     * @param name String
     * @return int
     */
    public static final int getLANAForAdapterName(String name) {

        // Get the list of available network adapters
        Hashtable<String, NetworkInterface> niList = getNetworkAdapterList();

        // Search for the address of the specified network adapter
        Enumeration<String> niEnum = niList.keys();

        while (niEnum.hasMoreElements()) {

            // Get the current TCP/IP address
            String ipAddr = niEnum.nextElement();
            NetworkInterface ni = (NetworkInterface) niList.get(ipAddr);

            if (ni.getName().equalsIgnoreCase(name)) {

                // Return the LANA for the network adapters TCP/IP address
                return getLANAForIPAddress(ipAddr);
            }
        }

        // Failed to find matching network adapter
        return -1;
    }

    /**
     * Return a hashtable of NetworkInterfaces indexed by TCP/IP address
     *
     * @return Hashtable
     */
    private static final Hashtable<String, NetworkInterface> getNetworkAdapterList() {

        // Get a list of the local network adapters
        Hashtable<String, NetworkInterface> niList = new Hashtable<String, NetworkInterface>();

        try {

            // Enumerate the available network adapters
            Enumeration<NetworkInterface> niEnum = NetworkInterface.getNetworkInterfaces();

            while (niEnum.hasMoreElements()) {

                // Get the current network interface details
                NetworkInterface ni = (NetworkInterface) niEnum.nextElement();
                Enumeration<InetAddress> addrEnum = ni.getInetAddresses();

                while (addrEnum.hasMoreElements()) {

                    // Get the address and add the adapter to the list indexed via the numeric IP
                    // address string
                    InetAddress addr = addrEnum.nextElement();
                    niList.put(addr.getHostAddress(), ni);
                }
            }
        }
        catch (Exception ex) {
        }

        // Return the network adapter list
        return niList;
    }
}
