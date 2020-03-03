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

package org.filesys.oncrpc;

import org.filesys.server.core.NoPooledMemoryException;
import org.filesys.server.thread.ThreadRequestPool;

import java.io.IOException;
import java.net.Socket;

/**
 * Multi-Threaded Tcp Rpc Packet Handler Class
 *
 * <p>Adds multi-threaded processing of RPC requests to the standard TCP RPC handler.
 *
 * @author gkspencer
 */
public class MultiThreadedTcpRpcPacketHandler extends TcpRpcPacketHandler implements RpcPacketHandler {

    //	RPC packet pool
    private RpcPacketPool m_packetPool;

    //	Request handler thread pool
    private ThreadRequestPool m_threadPool;

    /**
     * Class constructor to create a TCP RPC handler for a server.
     *
     * @param handler    TcpRpcSessionHandler
     * @param sessId     int
     * @param server     RpcProcessor
     * @param socket     Socket
     * @param maxRpcSize int
     * @param pktPool    RpcPacketPool
     * @param threadPool ThreadRequestPool
     * @exception IOException Socket error
     */
    public MultiThreadedTcpRpcPacketHandler(TcpRpcSessionHandler handler, int sessId, RpcProcessor server, Socket socket,
                                            int maxRpcSize, RpcPacketPool pktPool, ThreadRequestPool threadPool)
            throws IOException {
        super(handler, sessId, server, socket, maxRpcSize);

        // Save the packet pool and thread pool
        m_packetPool = pktPool;
        m_threadPool = threadPool;
    }

    /**
     * Return the multi-threaded RPC session handler
     *
     * @return MultiThreadedTcpRpcSessionHandler
     */
    protected final MultiThreadedTcpRpcSessionHandler getSessionHandler() {
        return (MultiThreadedTcpRpcSessionHandler) getHandler();
    }

    /**
     * Allocate an RPC packet from the packet pool
     *
     * @param maxSize int
     * @return RpcPacket
     * @exception NoPooledMemoryException No pooled memory available
     */
    protected RpcPacket allocateRpcPacket(int maxSize)
        throws NoPooledMemoryException {

        //	Allocate the RPC packet
        return m_packetPool.allocatePacket(maxSize);
    }

    /**
     * Deallocate an RPC packet, return the packet to the pool.
     *
     * @param pkt RpcPacket
     */
    protected void deallocateRpcPacket(RpcPacket pkt) {

        // Return the packet to the pool
        if (pkt.isAllocatedFromPool())
            pkt.getOwnerPacketPool().releasePacket(pkt);
    }

    /**
     * Process an RPC request by passing the request to a pool of worker threads.
     *
     * @param rpc RpcPacket
     * @exception IOException Socket error
     */
    protected void processRpc(RpcPacket rpc)
            throws IOException {

        //	Link the RPC request to this handler
        rpc.setPacketHandler(this);

        //	Queue the RPC request to the session handlers thread pool for processing
        m_threadPool.queueRequest( new RpcThreadRequest( rpc, getRpcProcessor(), this));
    }

    /**
     * Send an RPC response using the TCP socket connection
     *
     * @param rpc RpcPacket
     * @exception IOException Socket error
     */
    public void sendRpcResponse(RpcPacket rpc)
            throws IOException {

        //	Send the RPC response
        sendRpc(rpc);
    }

    @Override
    public RpcPacket receiveRpc() throws IOException {

        // Not currently used
        return null;
    }
}
