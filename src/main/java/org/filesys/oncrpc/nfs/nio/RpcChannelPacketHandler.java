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

import org.filesys.oncrpc.RpcPacketHandler;
import org.filesys.oncrpc.RpcPacketPool;
import org.filesys.server.thread.ThreadRequestPool;

import java.nio.channels.SocketChannel;

/**
 * RPC Channel Packet Handler Base Class
 *
 * @author gkspencer
 */
public abstract class RpcChannelPacketHandler implements RpcPacketHandler {

    // Socket channel
    private SocketChannel m_channel;

    // Memory and thread pools
    private RpcPacketPool m_pktPool;
    private ThreadRequestPool m_threadPool;

    /**
     * Class constructor
     *
     * @param sockChannel SocketChannel
     * @param packetPool  RpcPacketPool
     * @param threadPool ThreadRequestPool
     */
    public RpcChannelPacketHandler(SocketChannel sockChannel, RpcPacketPool packetPool, ThreadRequestPool threadPool) {
        m_channel = sockChannel;

        m_pktPool = packetPool;
        m_threadPool = threadPool;
    }

    /**
     * Return the socket channel
     *
     * @return SocketChannel
     */
    public final SocketChannel getChannel() {
        return m_channel;
    }

    /**
     * Return the memory pool
     *
     * @return RpcPacketPool
     */
    public final RpcPacketPool getPacketPool() {
        return m_pktPool;
    }

    /**
     * Return the thread pool
     *
     * @return ThreadRequestPool
     */
    public final ThreadRequestPool getThreadPool() {
        return m_threadPool;
    }
}
