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

package org.filesys.netbios;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.filesys.util.DataPacker;

/**
 * NetBIOS datagram class
 *
 * <p>Used for sending/receiving NetBIOS broadcast datagrams that are used for name lookups and registration.
 *
 * @author gkspencer
 */
public class NetBIOSDatagram {

    //	Datagram types
    public enum Type {
        DIRECT_UNIQUE   (0x10),
        DIRECT_GROUP    (0x11),
        BROADCAST       (0x12),
        DATAGRAM_ERROR  (0x13),
        DATAGRAM_QUERY  (0x14),
        POSITIVE_RESP   (0x15),
        NEGATIVE_RESP   (0x16),

        UNKNOWN         (-1);

        private final int dgType;

        /**
         * Enum constructor
         *
         * @param typ int
         */
        Type(int typ) { dgType = typ; }

        /**
         * Return the type as an int
         *
         * @return int
         */
        public final int intValue() { return dgType; }

        /**
         * Convert to a datagram type from an int
         *
         * @param iVal int
         * @return Type
         */
        public final static Type fromInt(int iVal) {
            Type typ = UNKNOWN;

            switch ( iVal) {
                case 0x10:
                    typ = DIRECT_UNIQUE;
                    break;
                case 0x11:
                    typ = DIRECT_GROUP;
                    break;
                case 0x12:
                    typ = BROADCAST;
                    break;
                case 0x13:
                    typ = DATAGRAM_ERROR;
                    break;
                case 0x14:
                    typ = DATAGRAM_QUERY;
                    break;
                case 0x15:
                    typ = POSITIVE_RESP;
                    break;
                case 0x16:
                    typ = NEGATIVE_RESP;
                    break;
            }

            return typ;
        }
    }

    //	Datagram flags
    public static final int FLG_MOREFRAGMENTS = 0x01;
    public static final int FLG_FIRSTPKT = 0x02;

    //	Default NetBIOS packet buffer size to allocate
    public static final int DEFBUFSIZE = 4096;

    //	NetBIOS datagram offsets
    public static final int NB_MSGTYPE      = 0;
    public static final int NB_FLAGS        = 1;
    public static final int NB_DATAGRAMID   = 2;
    public static final int NB_SOURCEIP     = 4;
    public static final int NB_SOURCEPORT   = 8;
    public static final int NB_DATAGRAMLEN  = 10;
    public static final int NB_PKTOFFSET    = 12;
    public static final int NB_FROMNAME     = 14;
    public static final int NB_TONAME       = 48;
    public static final int NB_USERDATA     = 82;

    public static final int NB_MINLENGTH    = 82;
    public static final int NB_MINSMBLEN    = 100;

    //	NetBIOS packet buffer
    protected byte[] m_buf;

    //	Next available datagram id
    private static int m_nextId;

    /**
     * NetBIOS Datagram constructor
     */
    public NetBIOSDatagram() {

        //  Allocate a NetBIOS packet buffer
        m_buf = new byte[DEFBUFSIZE];
    }

    /**
     * Create a new NetBIOS datagram using the specified packet buffer.
     *
     * @param pkt byte[]
     */
    public NetBIOSDatagram(byte[] pkt) {
        m_buf = pkt;
    }

    /**
     * Create a new NetBIOS datagram with the specified buffer size.
     *
     * @param bufSize int
     */
    public NetBIOSDatagram(int bufSize) {
        m_buf = new byte[bufSize];
    }

    /**
     * Return the next available datagram id.
     *
     * @return int
     */
    public final static synchronized int getNextDatagramId() {

        //  Update and return the next available datagram id
        return m_nextId++;
    }

    /**
     * Return the NetBIOS buffer.
     *
     * @return byte[]
     */
    public final byte[] getBuffer() {
        return m_buf;
    }

    /**
     * Get the datagram id.
     *
     * @return int
     */
    public final int getDatagramId() {
        return DataPacker.getIntelShort(m_buf, NB_DATAGRAMID);
    }

    /**
     * Get the datagram destination name.
     *
     * @return NetBIOSName
     */
    public final NetBIOSName getDestinationName() {

        //  Decode the NetBIOS name to a string
        String name = NetBIOSSession.DecodeName(m_buf, NB_TONAME + 1);
        if (name != null) {

            //  Convert the name string to a NetBIOS name
            NetBIOSName nbName = new NetBIOSName(name.substring(0, 14), name.charAt(15), false);
            if (getMessageType() == Type.DIRECT_GROUP)
                nbName.setGroup(true);
            return nbName;
        }
        return null;
    }

    /**
     * Return the datagram flags value.
     *
     * @return int
     */
    public final int getFlags() {
        return m_buf[NB_FLAGS] & 0xFF;
    }

    /**
     * Return the datagram length.
     *
     * @return int
     */
    public final int getLength() {
        return DataPacker.getShort(m_buf, NB_DATAGRAMLEN);
    }

    /**
     * Return the user data length
     *
     * @return int
     */
    public final int getDataLength() {
        return getLength() - NB_USERDATA;
    }

    /**
     * Get the NetBIOS datagram message type.
     *
     * @return typ NetBIOSDatagramType
     */
    public final Type getMessageType() {
        return Type.fromInt(m_buf[NB_MSGTYPE] & 0xFF);
    }

    /**
     * Return the datagram source IP address.
     *
     * @return byte[]
     */
    public final byte[] getSourceIPAddress() {

        //  Allocate a 4 byte array for the IP address
        byte[] ipaddr = new byte[4];

        //  Copy the IP address bytes from the datagram
        for (int i = 0; i < 4; i++)
            ipaddr[i] = m_buf[NB_SOURCEIP + i];

        //  Return the IP address bytes
        return ipaddr;
    }

    /**
     * Return the datagram source IP address, as a string
     *
     * @return String
     */
    public final String getSourceAddress() {

        //	Get the IP address
        byte[] addr = getSourceIPAddress();

        //	Build the IP address string
        StringBuffer addrStr = new StringBuffer();

        addrStr.append(addr[0]);
        addrStr.append(".");
        addrStr.append(addr[1]);
        addrStr.append(".");
        addrStr.append(addr[2]);
        addrStr.append(".");
        addrStr.append(addr[3]);

        return addrStr.toString();
    }

    /**
     * Get the source NetBIOS name.
     *
     * @return java.lang.String
     */
    public final NetBIOSName getSourceName() {

        //  Decode the NetBIOS name string
        String name = NetBIOSSession.DecodeName(m_buf, NB_FROMNAME + 1);

        //  Convert the name to a NetBIOS name
        if (name != null) {

            //  Convert the name string to a NetBIOS name
            NetBIOSName nbName = new NetBIOSName(name.substring(0, 14), name.charAt(15), false);
            return nbName;
        }
        return null;
    }

    /**
     * Get the source port/socket for the datagram.
     *
     * @return int
     */
    public final int getSourcePort() {
        return DataPacker.getIntelShort(m_buf, NB_SOURCEPORT);
    }

    /**
     * Check if the user data is an SMB packet
     *
     * @return boolean
     */
    public final boolean isSMBData() {
        if (m_buf[NB_USERDATA] == (byte) 0xFF &&
                m_buf[NB_USERDATA + 1] == (byte) 'S' &&
                m_buf[NB_USERDATA + 2] == (byte) 'M' &&
                m_buf[NB_USERDATA + 3] == (byte) 'B' &&
                getLength() >= NB_MINSMBLEN)
            return true;
        return false;
    }

    /**
     * Send a datagram to the specified NetBIOS name using the global NetBIOS datagram socket
     *
     * @param dgramTyp     Datagram type
     * @param fromName     From NetBIOS name
     * @param fromNameType From NetBIOS name type.
     * @param toName       To NetBIOS name
     * @param toNameType   To NetBIOS name type.
     * @param userData     User data buffer
     * @param userLen      User data length.
     * @param userOff      Offset of data within user buffer.
     * @param addr         Address to send to
     * @param port         Port to send to
     * @throws java.io.IOException  Error occurred sending datagram
     * @throws UnknownHostException Failed to generate the broadcast mask for the network
     */
    public final void SendDatagram(NetBIOSDatagram.Type dgramTyp, String fromName, char fromNameType, String toName, char toNameType,
                                   byte[] userData, int userLen, int userOff, InetAddress addr, int port)
            throws IOException, UnknownHostException {

        //  Set the datagram header values
        setMessageType(dgramTyp);
        setSourceName(fromName, fromNameType);
        setDestinationName(toName, toNameType);
        setSourcePort(RFCNetBIOSProtocol.DATAGRAM);
        setSourceIPAddress(InetAddress.getLocalHost().getAddress());
        setFlags(FLG_FIRSTPKT);

        if (m_nextId == 0)
            m_nextId = (int) (System.currentTimeMillis() & 0x7FFF);
        setDatagramId(m_nextId++);

        //  Set the user data and length
        setLength(userLen + NB_USERDATA);
        setUserData(userData, userLen, userOff);

        //	Use the global NetBIOS datagram socket to sent the broadcast datagram
        NetBIOSDatagramSocket nbSocket = NetBIOSDatagramSocket.getInstance();
        nbSocket.sendDatagram(this, addr, port);
    }

    /**
     * Send a datagram to the specified NetBIOS name using the global NetBIOS datagram socket
     *
     * @param dgramTyp     Datagram type
     * @param fromName     From NetBIOS name
     * @param fromNameType From NetBIOS name type.
     * @param toName       To NetBIOS name
     * @param toNameType   To NetBIOS name type.
     * @param userData     User data buffer
     * @param userLen      User data length.
     * @param userOff      Offset of data within user buffer.
     * @throws java.io.IOException  Error occurred sending datagram
     * @throws UnknownHostException Failed to generate the broadcast mask for the network
     */
    public final void SendDatagram(NetBIOSDatagram.Type dgramTyp, String fromName, char fromNameType, String toName, char toNameType,
                                   byte[] userData, int userLen, int userOff)
            throws IOException, UnknownHostException {

        //  Set the datagram header values
        setMessageType(dgramTyp);
        setSourceName(fromName, fromNameType);
        setDestinationName(toName, toNameType);
        setSourcePort(RFCNetBIOSProtocol.DATAGRAM);
        setSourceIPAddress(InetAddress.getLocalHost().getAddress());
        setFlags(FLG_FIRSTPKT);

        if (m_nextId == 0)
            m_nextId = (int) (System.currentTimeMillis() & 0x7FFF);
        setDatagramId(m_nextId++);

        //  Set the user data and length
        setLength(userLen + NB_USERDATA);
        setUserData(userData, userLen, userOff);

        //	Use the global NetBIOS datagram socket to sent the broadcast datagram
        NetBIOSDatagramSocket nbSocket = NetBIOSDatagramSocket.getInstance();
        nbSocket.sendBroadcastDatagram(this);
    }

    /**
     * Send a datagram to the specified NetBIOS name using the global NetBIOS datagram socket
     *
     * @param dgramTyp Datagram type
     * @param fromName From NetBIOS name
     * @param toName   To NetBIOS name
     * @param userData User data buffer
     * @param userLen  User data length.
     * @throws java.io.IOException  Error occurred sending datagram
     * @throws UnknownHostException Failed to generate the broadcast mask for the network
     */
    public final void SendDatagram(NetBIOSDatagram.Type dgramTyp, String fromName, String toName, byte[] userData, int userLen)
            throws IOException, UnknownHostException {

        //  Send the datagram from the standard port
        SendDatagram(dgramTyp, fromName, NetBIOSName.FileServer, toName, NetBIOSName.FileServer, userData, userLen, 0);
    }

    /**
     * Send a datagram to the specified NetBIOS name using the supplised datagram socket.
     *
     * @param dgramTyp     Datagram type
     * @param sock         Datagram socket to use to send the datagram packet.
     * @param fromName     From NetBIOS name
     * @param fromNameType From NetBIOS name type.
     * @param toName       To NetBIOS name
     * @param toNameType   To NetBIOS name type.
     * @param userData     User data buffer
     * @param userLen      User data length.
     * @param userOff      Offset of data within user buffer.
     * @throws java.io.IOException The exception description.
     */
    public final void SendDatagram(NetBIOSDatagram.Type dgramTyp, DatagramSocket sock, String fromName, char fromNameType, String toName,
                                   char toNameType, byte[] userData, int userLen, int userOff) throws IOException {

        //  Set the datagram header values
        setMessageType(dgramTyp);
        setSourceName(fromName, fromNameType);
        setDestinationName(toName, toNameType);
        setSourcePort(RFCNetBIOSProtocol.DATAGRAM);
        setSourceIPAddress(InetAddress.getLocalHost().getAddress());
        setFlags(FLG_FIRSTPKT);

        if (m_nextId == 0)
            m_nextId = (int) (System.currentTimeMillis() & 0x7FFF);
        setDatagramId(m_nextId++);

        //  Set the user data and length
        setLength(userLen + NB_USERDATA);
        setUserData(userData, userLen, userOff);

        //  Build a broadcast destination address
        InetAddress destAddr = InetAddress.getByName(NetworkSettings.GenerateBroadcastMask(null));
        DatagramPacket dgram = new DatagramPacket(m_buf, userLen + NB_USERDATA, destAddr, RFCNetBIOSProtocol.DATAGRAM);

        //  Debug
        //  HexDump.Dump( m_buf, userLen + NB_USERDATA, 0);

        //  Send the datagram
        sock.send(dgram);
    }

    /**
     * Send a datagram to the specified NetBIOS name using the supplied datagram socket.
     *
     * @param dgramTyp NetBIOSDatagram.Type
     * @param sock DatagramSocket
     * @param fromName String
     * @param toName   String
     * @param userData byte[]
     * @param userLen  int
     * @exception IOException Socket error
     */
    public final void SendDatagram(NetBIOSDatagram.Type dgramTyp, DatagramSocket sock, String fromName, String toName,
                                   byte[] userData, int userLen) throws IOException {

        //  Send the datagram from the standard port
        SendDatagram(dgramTyp, sock, fromName, NetBIOSName.FileServer, toName, NetBIOSName.FileServer, userData, userLen, 0);
    }

    /**
     * Set the datagram id.
     *
     * @param id int
     */
    public final void setDatagramId(int id) {
        DataPacker.putIntelShort(id, m_buf, NB_DATAGRAMID);
    }

    /**
     * Set the datagram destination name.
     *
     * @param name String
     */
    public final void setDestinationName(String name) {
        setDestinationName(name, NetBIOSName.FileServer);
    }

    /**
     * Set the datagram destination name.
     *
     * @param name String
     * @param typ char
     */
    public final void setDestinationName(String name, char typ) {

        //  Convert the name to  NetBIOS RFC encoded name
        NetBIOSSession.EncodeName(name, typ, m_buf, NB_TONAME);
    }

    /**
     * Set the datagram flags value.
     *
     * @param flg int
     */
    public final void setFlags(int flg) {
        m_buf[NB_FLAGS] = (byte) (flg & 0xFF);
    }

    /**
     * Set the datagram length.
     *
     * @param len int
     */
    public final void setLength(int len) {
        DataPacker.putShort((short) len, m_buf, NB_DATAGRAMLEN);
    }

    /**
     * Set the NetBIOS datagram message type.
     *
     * @param msg NetBIOSDatagram.Type
     */
    public final void setMessageType(NetBIOSDatagram.Type msg) {
        m_buf[NB_MSGTYPE] = (byte) (msg.intValue() & 0xFF);
    }

    /**
     * Set the source IP address for the datagram.
     *
     * @param ipaddr byte[]
     */
    public final void setSourceIPAddress(byte[] ipaddr) {

        //  Pack the IP address into the datagram buffer
        for (int i = 0; i < 4; i++)
            m_buf[NB_SOURCEIP + i] = ipaddr[i];
    }

    /**
     * Set the datagram source NetBIOS name.
     *
     * @param name String
     */
    public final void setSourceName(String name) {

        //  Convert the name to  NetBIOS RFC encoded name
        NetBIOSSession.EncodeName(name, NetBIOSName.FileServer, m_buf, NB_FROMNAME);
    }

    /**
     * Set the datagram source NetBIOS name.
     *
     * @param name String
     * @param typ char
     */
    public final void setSourceName(String name, char typ) {

        //  Convert the name to  NetBIOS RFC encoded name
        NetBIOSSession.EncodeName(name, typ, m_buf, NB_FROMNAME);
    }

    /**
     * Set the source port/socket for the datagram.
     *
     * @param port int
     */
    public final void setSourcePort(int port) {
        DataPacker.putShort((short) port, m_buf, NB_SOURCEPORT);
    }

    /**
     * Set the user data portion of the datagram.
     *
     * @param buf byte[]
     * @param len int
     */
    public final void setUserData(byte[] buf, int len) {

        //  Copy the user data
        System.arraycopy(buf, 0, m_buf, NB_USERDATA, len);
    }

    /**
     * Set the user data portion of the datagram.
     *
     * @param buf User data buffer
     * @param len Length of user data
     * @param off Offset to start of data within buffer.
     */
    public final void setUserData(byte[] buf, int len, int off) {

        //  Copy the user data
        System.arraycopy(buf, off, m_buf, NB_USERDATA, len);
    }

    /**
     * Common constructor initialization code.
     */
    protected final void CommonInit() {
    }
}
