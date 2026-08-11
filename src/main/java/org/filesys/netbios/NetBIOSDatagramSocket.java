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
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * NetBIOS Datagram Socket Class
 *
 * <p>Singleton class that allows multiple users of the socket.
 *
 * @author gkspencer
 */
public class NetBIOSDatagramSocket {

    //	Global NetBIOS datagram socket instance
    private static NetBIOSDatagramSocket m_nbSocket;

    //	Default port and bind address
    private static int m_defPort = RFCNetBIOSProtocol.DATAGRAM;
    private static InetAddress m_defBindAddr;

    //	Datagram socket
    private DatagramSocket m_socket;

    //	Broadcast address
    private InetAddress m_broadcastAddr;

    /**
     * Class constructor
     *
     * @exception SocketException Socket error
     * @exception UnknownHostException Host name lookup failed
     */
    private NetBIOSDatagramSocket()
            throws SocketException, UnknownHostException {

        //	Create the datagram socket
        if (m_defBindAddr == null)
            m_socket = new DatagramSocket(m_defPort);
        else
            m_socket = new DatagramSocket(m_defPort, m_defBindAddr);

        //	Generate the broadcast mask
        if (m_defBindAddr == null)
            m_broadcastAddr = InetAddress.getByName(NetworkSettings.GenerateBroadcastMask(null));
        else
            m_broadcastAddr = InetAddress.getByName(NetworkSettings.GenerateBroadcastMask(m_defBindAddr.getHostAddress()));
    }

    /**
     * Return the global NetBIOS datagram instance
     *
     * @return NetBIOSDatagramSocket
     * @exception SocketException Socket error
     * @exception UnknownHostException Host name lookup failed
     */
    public final static synchronized NetBIOSDatagramSocket getInstance()
            throws SocketException, UnknownHostException {

        //	Check if the datagram socket has been created
        if (m_nbSocket == null)
            m_nbSocket = new NetBIOSDatagramSocket();

        //	Return the global NetBIOS datagram socket instance
        return m_nbSocket;
    }

    /**
     * Set the default port to use
     *
     * @param port int
     */
    public final static void setDefaultPort(int port) {
        m_defPort = port;
    }

    /**
     * Set the address to bind the datagram socket to
     *
     * @param bindAddr InetAddress
     */
    public final static void setBindAddress(InetAddress bindAddr) {
        m_defBindAddr = bindAddr;
    }

    /**
     * Receive a NetBIOS datagram
     *
     * @param dgram NetBIOSDatagram
     * @return int
     * @exception IOException Socket error
     */
    public final int receiveDatagram(NetBIOSDatagram dgram)
            throws IOException {

        //	Create a datagram packet using the NetBIOS datagram buffer
        DatagramPacket pkt = new DatagramPacket(dgram.getBuffer(), dgram.getBuffer().length);

        //	Receive a datagram
        m_socket.receive(pkt);
        return pkt.getLength();
    }

    /**
     * Send a NetBIOS datagram
     *
     * @param dgram    NetBIOSDatagram
     * @param destAddr InetAddress
     * @param destPort int
     * @exception IOException Socket error
     */
    public final void sendDatagram(NetBIOSDatagram dgram, InetAddress destAddr, int destPort)
            throws IOException {

        //	Create a datagram packet using the NetBIOS datagram buffer
        DatagramPacket pkt = new DatagramPacket(dgram.getBuffer(), dgram.getLength(), destAddr, RFCNetBIOSProtocol.DATAGRAM);

        //	Send the NetBIOS datagram
        m_socket.send(pkt);
    }

    /**
     * Send a broadcast NetBIOS datagram
     *
     * @param dgram NetBIOSDatagram
     * @exception IOException Socket error
     */
    public final void sendBroadcastDatagram(NetBIOSDatagram dgram)
            throws IOException {

        //	Create a datagram packet using the NetBIOS datagram buffer
        DatagramPacket pkt = new DatagramPacket(dgram.getBuffer(), dgram.getLength(), m_broadcastAddr, RFCNetBIOSProtocol.DATAGRAM);

        //	Send the NetBIOS datagram
        m_socket.send(pkt);
    }
}
