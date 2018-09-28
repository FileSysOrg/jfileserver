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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Java Socket Based Packet Handler Class
 *
 * @author gkspencer
 */
public abstract class SocketPacketHandler implements PacketHandlerInterface {

    //	Socket to read/write to/from
    private Socket m_socket;

    // Input/output streams for receiving/sending data
    private DataInputStream m_in;
    private DataOutputStream m_out;

    /**
     * Class constructor
     *
     * @param socket Socket
     * @exception IOException Socket error
     */
    protected SocketPacketHandler(Socket socket)
            throws IOException {
        m_socket = socket;

        //	Open the input/output streams
        m_in = new DataInputStream(m_socket.getInputStream());
        m_out = new DataOutputStream(m_socket.getOutputStream());
    }

    /**
     * Return the protocol name
     *
     * @return String
     */
    public abstract String getProtocolName();

    /**
     * Return the number of bytes available for reading without blocking
     *
     * @return int
     * @exception IOException Socket error
     */
    public int availableBytes()
            throws IOException {
        if (m_in != null)
            return m_in.available();
        return 0;
    }

    /**
     * Read a packet of data
     *
     * @param pkt    byte[]
     * @param offset int
     * @param maxLen int
     * @return int
     * @exception IOException Socket error
     */
    public int readPacket(byte[] pkt, int offset, int maxLen)
            throws IOException {

        //	Read a packet of data
        if (m_in != null)
            return m_in.read(pkt, offset, maxLen);
        return 0;
    }

    /**
     * Write a packet of data
     *
     * @param pkt    byte[]
     * @param offset int
     * @param len    int
     * @exception IOException Socket error
     */
    public void writePacket(byte[] pkt, int offset, int len)
            throws IOException {

        //	Output the raw packet
        if (m_out != null) {

            synchronized (m_out) {
                m_out.write(pkt, offset, len);
            }
        }
    }

    /**
     * Close the packet handler
     */
    public void closePacketHandler() {

        //	Close the socket
        if (m_socket != null) {
            try {
                m_socket.close();
            }
            catch (Exception ex) {
            }
            m_socket = null;
        }

        //	Close the input stream
        if (m_in != null) {
            try {
                m_in.close();
            }
            catch (Exception ex) {
            }
            m_in = null;
        }

        //	Close the output stream
        if (m_out != null) {
            try {
                m_out.close();
            }
            catch (Exception ex) {
            }
            m_out = null;
        }
    }

    /**
     * Return the socket
     *
     * @return Socket
     */
    protected final Socket getSocket() {
        return m_socket;
    }
}
