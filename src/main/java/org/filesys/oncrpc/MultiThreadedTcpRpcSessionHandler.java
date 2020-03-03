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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import org.filesys.server.NetworkServer;
import org.filesys.server.core.NoPooledMemoryException;
import org.filesys.server.thread.ThreadRequestPool;


/**
 * Multi-threaded TCP RPC Session Handler Class
 *
 * <p>Extend the basic TCP RPC handler class to process RPC requests using a thread pool.
 *
 * @author gkspencer
 */
public class MultiThreadedTcpRpcSessionHandler extends TcpRpcSessionHandler {

    //	Constants
    //
    //	Default packet pool size
    public static final int DefaultPacketPoolSize   = 50;
    public static final int DefaultSmallPacketSize  = 512;

    //	RPC packet pool
    private RpcPacketPool m_packetPool;

    //	Request handler thread pool
    private ThreadRequestPool m_threadPool;

    /**
     * Class constructor
     *
     * @param name      String
     * @param protocol  String
     * @param rpcServer RpcProcessor
     * @param server    NetworkServer
     * @param addr      InetAddress
     * @param port      int
     * @param maxSize   int
     */
    public MultiThreadedTcpRpcSessionHandler(String name, String protocol, RpcProcessor rpcServer, NetworkServer server,
                                             InetAddress addr, int port, int maxSize) {
        super(name, protocol, rpcServer, server, addr, port, maxSize);
    }

    /**
     * Initialize the session socket handler
     *
     * @param server NetworkServer
     * @param pktPool RpcPacketPool
     * @param threadPool ThreadRequestPool
     * @exception IOException Socket error
     */
    public void initializeSessionHandler(NetworkServer server, RpcPacketPool pktPool, ThreadRequestPool threadPool)
        throws IOException {

        // Use the specified packet pool and thread pool
        m_packetPool = pktPool;
        m_threadPool = threadPool;

        //	Call the base class initialization
        super.initializeSessionHandler(server);
    }

    /**
     * Allocate an RPC packet from the packet pool
     *
     * @param size int
     * @return RpcPacket
     * @exception NoPooledMemoryException No pooled memory available
     */
    protected final RpcPacket allocateRpcPacket(int size)
        throws NoPooledMemoryException {

        //	Allocate an RPC packet from the packet pool
        return m_packetPool.allocatePacket(size);
    }

    /**
     * Create a multi-threaded packet handler for the new session
     *
     * @param sessId int
     * @param sock   Socket
     * @return TcpRpcPacketHandler
     * @exception IOException Socket error
     */
    protected TcpRpcPacketHandler createPacketHandler(int sessId, Socket sock)
            throws IOException {

        //	Create a multi-threaded packet handler to use the session handlers thread pool to process the RPC requests
        return new MultiThreadedTcpRpcPacketHandler(this, sessId, getRpcProcessor(), sock, getMaximumRpcSize(), m_packetPool, m_threadPool);
    }
}
