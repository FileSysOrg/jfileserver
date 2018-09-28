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
    private RpcRequestThreadPool m_threadPool;

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
     * @exception IOException Socket error
     */
    public void initializeSessionHandler(NetworkServer server) throws IOException {

        //	If the packet pool has not been created, create a default packet pool
        if (m_packetPool == null)
            m_packetPool = new RpcPacketPool(DefaultSmallPacketSize, DefaultPacketPoolSize, getMaximumRpcSize(), DefaultPacketPoolSize);

        //	Create the RPC request handling thread pool, if not already created
        if (m_threadPool == null)
            m_threadPool = new RpcRequestThreadPool(getHandlerName(), getRpcProcessor());

        //	Call the base class initialization
        super.initializeSessionHandler(server);
    }

    /**
     * Allocate an RPC packet from the packet pool
     *
     * @param size int
     * @return RpcPacket
     */
    protected final RpcPacket allocateRpcPacket(int size) {

        //	Allocate an RPC packet from the packet pool
        return m_packetPool.allocatePacket(size);
    }

    /**
     * Queue an RPC request to the thread pool for processing
     *
     * @param rpc RpcPacket
     */
    protected final void queueRpcRequest(RpcPacket rpc) {

        //	DEBUG
//    Debug.println("MTRpcSessHandler Queue rpc=" + rpc.toString());

        //	Queue the RPC request to the thread pool for processing
        m_threadPool.queueRpcRequest(rpc);
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

        //	Create a multi-threaded packet handler to use the session handlers thread pool to
        //	process the RPC requests
        return new MultiThreadedTcpRpcPacketHandler(this, sessId, getRpcProcessor(), sock, getMaximumRpcSize());
    }

    /**
     * Set the packet pool size
     *
     * @param smallSize int
     * @param smallPool int
     * @param largeSize int
     * @param largePool int
     */
    public final void setPacketPool(int smallSize, int smallPool, int largeSize, int largePool) {

        //	Create the packet pool, if not already initialized
        if (m_packetPool == null) {

            //	Create the packet pool
            m_packetPool = new RpcPacketPool(smallSize, smallPool, largeSize, largePool);
        }
    }

    /**
     * Set the packet pool size
     *
     * @param poolSize int
     */
    public final void setPacketPool(int poolSize) {

        //	Create the packet pool, if not already initialized
        if (m_packetPool == null) {

            //	Create the packet pool
            m_packetPool = new RpcPacketPool(DefaultSmallPacketSize, poolSize, getMaximumRpcSize(), poolSize);
        }
    }

    /**
     * Set the packet pool
     *
     * @param pktPool RpcPacketPool
     */
    public final void setPacketPool(RpcPacketPool pktPool) {

        //	Set the packet pool, if not already initialized
        if (m_packetPool == null)
            m_packetPool = pktPool;
    }

    /**
     * Set the thread pool size
     *
     * @param numThreads int
     */
    public final void setThreadPool(int numThreads) {

        //	Create the thread pool, if not already initialized
        if (m_threadPool == null) {

            //	Create the thread pool
            m_threadPool = new RpcRequestThreadPool(getHandlerName(), numThreads, getRpcProcessor());
        }
    }

    /**
     * Set the thread pool
     *
     * @param threadPool RpcRequestThreadPool
     */
    public final void setThreadPool(RpcRequestThreadPool threadPool) {

        //	Set the thread pool, if not already initialized
        if (m_threadPool == null)
            m_threadPool = threadPool;
    }
}
