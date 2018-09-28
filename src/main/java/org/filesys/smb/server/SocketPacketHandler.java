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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Socket Packet Handler Class
 *
 * <p>Provides the base class for Java Socket based packet handler implementations.
 *
 * @author gkspencer
 */
public abstract class SocketPacketHandler extends PacketHandler {

    // Socket that this session is using.
    private Socket m_socket;

    // Input/output streams for receiving/sending SMB requests.
    private DataInputStream m_in;
    private DataOutputStream m_out;

    /**
     * Class constructor
     *
     * @param sock      Socket
     * @param typ       Protocol
     * @param name      String
     * @param shortName String
     * @param packetPool SMBPacketPool
     * @throws IOException If a network error occurs
     */
    public SocketPacketHandler(Socket sock, Protocol typ, String name, String shortName, SMBPacketPool packetPool)
            throws IOException {

        super(typ, name, shortName, packetPool);

        m_socket = sock;

        //  Set socket options
        sock.setTcpNoDelay(true);

        //  Open the input/output streams
        m_in = new DataInputStream(m_socket.getInputStream());
        m_out = new DataOutputStream(m_socket.getOutputStream());

        // Set the remote address
        setRemoteAddress(m_socket.getInetAddress());
    }

    /**
     * Return the count of available bytes in the receive input stream
     *
     * @return int
     * @throws IOException If a network error occurs.
     */
    public int availableBytes()
            throws IOException {
        if (m_in != null)
            return m_in.available();
        return 0;
    }

    /**
     * Read bytes from the socket input stream
     *
     * @param buf byte[]
     * @param offset int
     * @param len int
     * @return int
     * @throws IOException If a network error occurs.
     */
    protected int readBytes(byte[] buf, int offset, int len)
            throws IOException {

        //  Read a packet of data
        if (m_in != null)
            return m_in.read(buf, offset, len);
        return 0;
    }

    /**
     * Write bytes to the output socket stream
     *
     * @param pkt byte[]
     * @param off int
     * @param len int
     * @throws IOException If a network error occurs.
     */
    protected void writeBytes(byte[] pkt, int off, int len)
            throws IOException {

        //  Output the raw packet
        if (m_out != null)
            m_out.write(pkt, off, len);
    }

    /**
     * Flush the output socket
     *
     * @throws IOException If a network error occurs
     */
    public void flushPacket()
            throws IOException {
        if (m_out != null)
            m_out.flush();
    }

    /**
     * Close the protocol handler
     */
    public void closeHandler() {

        //  Close the input stream
        if (m_in != null) {
            try {
                m_in.close();
            }
            catch (Exception ex) {
            }
            m_in = null;
        }

        //  Close the output stream
        if (m_out != null) {
            try {
                m_out.close();
            }
            catch (Exception ex) {
            }
            m_out = null;
        }

        //  Close the socket
        if (m_socket != null) {
            try {
                m_socket.close();
            }
            catch (Exception ex) {
            }
            m_socket = null;
        }
    }
}
