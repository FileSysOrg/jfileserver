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
import org.filesys.oncrpc.nfs.NFSServer;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.channels.SocketChannel;

/**
 * TCP/IP RPC Channel Session Handler Class
 *
 * <p>Handle RPC socket connections via a socket channel.
 *
 * @author gkspencer
 */
public class TcpRpcChannelSessionHandler extends RpcChannelSessionHandler {

    /**
     * Class constructor
     *
     * @param server NFSServer
     * @param addr   InetAddress
     * @param port   int
     */
    public TcpRpcChannelSessionHandler(NFSServer server, InetAddress addr, int port) {
        super("TCP-NFS", "NFS", server, addr, port);
    }

    /**
     * Create a packet handler for the new client socket connection
     *
     * @param sockChannel SocketChannel
     * @return RpcPacketHandler
     * @throws IOException I/O error
     */
    public RpcPacketHandler createPacketHandler(SocketChannel sockChannel)
            throws IOException {

        // Create a native RPC packet handler
        return new TcpRpcChannelPacketHandler(sockChannel, getNFSServer().getPacketPool(), getNFSServer().getThreadPool());
    }

    /**
     * Return the NFS server
     *
     * @return NFSServer
     */
    public final NFSServer getNFSServer() {
        return (NFSServer) getServer();
    }
}
