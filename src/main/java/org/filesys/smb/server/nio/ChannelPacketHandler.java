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

package org.filesys.smb.server.nio;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import org.filesys.debug.Debug;
import org.filesys.smb.server.SMBPacketPool;
import org.filesys.smb.server.PacketHandler;
import org.filesys.smb.server.Protocol;
import org.filesys.util.HexDump;

/**
 * Channel Packet Handler Class
 *
 * <p>
 * Provides the base class for Java SocketChannel based packet handler implementations.
 *
 * @author gkspencer
 */
public abstract class ChannelPacketHandler extends PacketHandler {

    // Socket channel that this session is using.
    private SocketChannel m_sockChannel;

    // Buffer to read the request header
    protected byte[] m_headerBuf = new byte[4];

    /**
     * Class constructor
     *
     * @param sockChannel SocketChannel
     * @param typ         Protocol
     * @param name        String
     * @param shortName   String
     * @param packetPool  SMBPacketPool
     * @throws IOException If a network error occurs
     */
    public ChannelPacketHandler(SocketChannel sockChannel, Protocol typ, String name, String shortName, SMBPacketPool packetPool)
            throws IOException {

        super(typ, name, shortName, packetPool);

        m_sockChannel = sockChannel;

        // Set socket options
        m_sockChannel.socket().setTcpNoDelay(true);
        m_sockChannel.setOption(StandardSocketOptions.SO_SNDBUF, 256000);

        // Set the remote address
        setRemoteAddress(m_sockChannel.socket().getInetAddress());
    }

    /**
     * Return the socket channel
     *
     * @return SocketChannel
     */
    public final SocketChannel getSocketChannel() {
        return m_sockChannel;
    }

    /**
     * Return the count of available bytes in the receive input stream
     *
     * @return int
     * @throws IOException If a network error occurs.
     */
    public int availableBytes()
            throws IOException {

        return 0;
    }

    /**
     * Read bytes from the socket channel
     *
     * @param pkt    byte[]
     * @param offset int
     * @param len    int
     * @return int
     * @throws IOException If a network error occurs.
     */
    protected int readBytes(byte[] pkt, int offset, int len)
            throws IOException {

        // Wrap the buffer and read into it
        ByteBuffer buf = ByteBuffer.wrap(pkt, offset, len);
        return m_sockChannel.read(buf);
    }

    /**
     * Write bytes to the output socket channel
     *
     * @param pkt byte[]
     * @param off int
     * @param len int
     * @throws IOException If a network error occurs.
     */
    protected void writeBytes(byte[] pkt, int off, int len)
            throws IOException {

        // Wrap the buffer and output to the socket channel
        ByteBuffer buf = ByteBuffer.wrap(pkt, off, len);

        while (buf.hasRemaining())
            m_sockChannel.write(buf);
    }

    /**
     * Flush the output socket
     *
     * @throws IOException If a network error occurs
     */
    public void flushPacket()
            throws IOException {
    }

    /**
     * Close the protocol handler
     */
    public void closeHandler() {

        // Close the socket channel
        if (m_sockChannel != null) {

            try {

                // Close the associated socket
                if (m_sockChannel.socket() != null)
                    m_sockChannel.socket().close();

                // Close the channel
                m_sockChannel.close();
            }
            catch (IOException ex) {
            }
        }
    }
}
