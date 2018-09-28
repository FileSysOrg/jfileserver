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
import java.nio.channels.SocketChannel;

import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.smb.server.SMBPacketPool;
import org.filesys.smb.server.Protocol;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.util.DataPacker;

/**
 * NetBIOS SMB Packet Handler Class
 *
 * @author gkspencer
 */
public class NetBIOSSMBChannelHandler extends ChannelPacketHandler {

    /**
     * Class constructor
     *
     * @param sockChannel SocketChannel
     * @param packetPool  CIFSPacketPool
     * @throws IOException If a network error occurs
     */
    public NetBIOSSMBChannelHandler(SocketChannel sockChannel, SMBPacketPool packetPool)
            throws IOException {
        super(sockChannel, Protocol.NetBIOS, "NetBIOS", "N", packetPool);
    }

    /**
     * Read a packet from the input stream
     *
     * @return SMBSrvPacket
     * @throws IOException If a network error occurs
     */
    public SMBSrvPacket readPacket()
            throws IOException {

        // Read the packet header
        int len = readBytes(m_headerBuf, 0, 4);

        // Check if the connection has been closed, read length equals -1
        if (len <= 0)
            return null;

        // Check if we received a valid NetBIOS header
        if (len < RFCNetBIOSProtocol.HEADER_LEN)
            throw new IOException("Invalid NetBIOS header, len=" + len);

        // Get the packet type from the header
        RFCNetBIOSProtocol.MsgType typ = RFCNetBIOSProtocol.MsgType.fromInt((int) (m_headerBuf[0] & 0xFF));
        int flags = (int) m_headerBuf[1];
        int dlen = (int) DataPacker.getShort(m_headerBuf, 2);

        if ((flags & 0x01) != 0)
            dlen += 0x10000;

        // Check for a session keep alive type message
        if (typ == RFCNetBIOSProtocol.MsgType.KEEPALIVE)
            return null;

        // Get a packet from the pool to hold the request data, allow for the NetBIOS header length
        // so that the CIFS request lines up with other implementations.
        SMBSrvPacket pkt = getPacketPool().allocatePacket(dlen + getEncryptionOverhead() + RFCNetBIOSProtocol.HEADER_LEN);

        // Read the data part of the packet into the users buffer, this may take several reads
        int offset = RFCNetBIOSProtocol.HEADER_LEN;
        int totlen = offset;

        try {

            while (dlen > 0) {

                // Read the data
                len = readBytes(pkt.getBuffer(), offset, dlen);

                // Check if the connection has been closed
                if (len == -1)
                    throw new IOException("Connection closed (request read)");

                // Update the received length and remaining data length
                totlen += len;
                dlen -= len;

                // Update the user buffer offset as more reads will be required to complete the data read
                offset += len;

            }
        }
        catch (Throwable ex) {

            // Release the packet back to the pool
            getPacketPool().releasePacket(pkt);

            // Rethrow the exception
            rethrowException(ex);
        }

        // Copy the NetBIOS header to the request buffer
        System.arraycopy(m_headerBuf, 0, pkt.getBuffer(), 0, 4);

        // Set the received request length
        pkt.setReceivedLength(totlen);

        // Return the received packet
        return pkt;
    }

    /**
     * Send a packet to the output stream
     *
     * @param pkt      SMBSrvPacket
     * @param len      int
     * @param writeRaw boolean
     * @throws IOException If a network error occurs
     */
    public void writePacket(SMBSrvPacket pkt, int len, boolean writeRaw)
            throws IOException {

        // Update the NetBIOS header, unless this is  write raw request
        byte[] buf = pkt.getBuffer();

        if (writeRaw == false) {

            // Fill in the NetBIOS message header, this is already allocated as part of the users buffer.
            buf[0] = (byte) RFCNetBIOSProtocol.MsgType.MESSAGE.intValue();
            buf[1] = (byte) 0;

            if (len > 0xFFFF) {

                // Set the >64K flag
                buf[1] = (byte) 0x01;

                // Set the low word of the data length
                DataPacker.putShort((short) (len & 0xFFFF), buf, 2);
            }
            else {

                // Set the data length
                DataPacker.putShort((short) len, buf, 2);
            }

            // Update the length to include the NetBIOS header
            len += RFCNetBIOSProtocol.HEADER_LEN;
        }

        // Output the data packet
        writeBytes(buf, 0, len);
    }
}
