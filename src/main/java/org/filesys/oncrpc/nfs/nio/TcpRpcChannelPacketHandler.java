/*
 * Copyright (C) 2020 GK Spencer
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

package org.filesys.oncrpc.nfs.nio;

import org.filesys.debug.Debug;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.oncrpc.Rpc;
import org.filesys.oncrpc.RpcPacket;
import org.filesys.oncrpc.RpcPacketPool;
import org.filesys.server.core.NoPooledMemoryException;
import org.filesys.server.thread.ThreadRequestPool;
import org.filesys.util.DataPacker;
import org.filesys.util.MemorySize;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Tcpip RPC Packet Handler Class
 *
 * @author gkspencer
 */
public class TcpRpcChannelPacketHandler extends RpcChannelPacketHandler {

    // Fragment header buffer
    private byte[] m_fragBuf = new byte[4];

    /**
     * Class constructor
     *
     * @param sockChannel SocketChannel
     * @param packetPool  RpcPacketPool
     * @param threadPool ThreadRequestPool
     * @throws IOException If a network error occurs
     */
    public TcpRpcChannelPacketHandler(SocketChannel sockChannel, RpcPacketPool packetPool, ThreadRequestPool threadPool)
        throws IOException {
        super(sockChannel, packetPool, threadPool);
    }

    /**
     * Read an RPC request/response
     *
     * @return RpcPacket
     * @exception IOException Socket error
     */
    public final RpcPacket receiveRpc()
        throws IOException {

        //	Fill the buffer until the last fragment is received
        int rxLen = 0;
        int totLen = 0;
        int rxOffset = RpcPacket.FragHeaderLen;
        int fragLen = 0;
        boolean lastFrag = false;
        RpcPacket rpcPkt = null;

        while (lastFrag == false) {

            //	Read in a header to get the fragment length
            rxLen = readBytes(m_fragBuf, 0, RpcPacket.FragHeaderLen);

            // Zero length read indicates no more data
            if ( rxLen == 0)
                return null;

            // -1 read length indicates a socket error
            if (rxLen == -1)
                throw new IOException("Socket closed by client (read header)");

            //	Check if we received the last fragment
            fragLen = DataPacker.getInt(m_fragBuf, 0);

            if ((fragLen & Rpc.LastFragment) != 0) {
                lastFrag = true;
                fragLen = fragLen & Rpc.LengthMask;
            }

            // Allocate the RPC packet, or re-allocate if we need more space
            if ( rpcPkt == null) {

                // Allocate the RPC packet buffer from the memory pool
                int allocLen = fragLen + RpcPacket.FragHeaderLen;
                if ( lastFrag == false)
                    allocLen *= 2;

                try {

                    // Allocate a buffer for the RPC
                    rpcPkt = getPacketPool().allocatePacket( allocLen);
                }
                catch ( NoPooledMemoryException ex) {

                    return null;
                }
            }
            else if ( fragLen > rpcPkt.getAvailableLength()) {

                // Need to reallocate the RPC with a larger buffer, unless we are already at the maximum buffer size
                int bufLen = rpcPkt.getBuffer().length;

                if ( bufLen < getPacketPool().getMaximumOverSizedAllocation()) {

                    // Double the current buffer size
                    bufLen *= 2;
                    if ( bufLen > getPacketPool().getMaximumOverSizedAllocation())
                        bufLen = getPacketPool().getMaximumOverSizedAllocation();

                    // Allocate a new RPC buffer, and copy the existing data to the new buffer
                    RpcPacket newPkt = getPacketPool().allocatePacket( bufLen);
                    System.arraycopy( rpcPkt.getBuffer(), 0, newPkt.getBuffer(), 0, rpcPkt.getLength());

                    // Switch to the new RPC buffer and release the original buffer
                    RpcPacket oldPkt = rpcPkt;
                    rpcPkt = newPkt;
                    getPacketPool().releasePacket( oldPkt);
                }
                else
                    throw new IOException( "RPC buffer size has reached maximum size (" +
                            MemorySize.asScaledString( getPacketPool().getMaximumOverSizedAllocation()) + ")");
            }

            //  Read the data part of the packet into the users buffer, this may take  several reads
            while (fragLen > 0) {

                //  Read the data
                rxLen = readBytes(rpcPkt.getBuffer(), rxOffset, fragLen);

                //	Check if the connection has been closed
                if (rxLen == -1)
                    throw new IOException("Socket closed by client (read fragment)");

                //  Update the received length and remaining data length
                totLen += rxLen;
                fragLen -= rxLen;

                //  Update the user buffer offset as more reads will be required to complete the data read
                rxOffset += rxLen;

            } // end while reading data

        } // end while fragments

        //	Return the RPC packet
        return rpcPkt;
    }

    /**
     * Send an RPC response
     *
     * @param rpc RpcPacket
     * @exception IOException Socket error
     */
    public void sendRpcResponse(RpcPacket rpc)
            throws IOException {

        // Wrap the buffer and output to the socket channel
        ByteBuffer buf = ByteBuffer.wrap(rpc.getBuffer(), 0, rpc.getTxLength());

        while (buf.hasRemaining())
            getChannel().write(buf);
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
        return getChannel().read(buf);
    }
}
