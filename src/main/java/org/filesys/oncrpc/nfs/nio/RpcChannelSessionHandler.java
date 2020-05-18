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
import org.filesys.oncrpc.RpcPacketHandler;
import org.filesys.server.NetworkServer;
import org.filesys.server.SessionHandlerBase;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * RPC Channel Session Handler Base Class
 *
 * <p>Base class for channel based RPC session handler implementations</p>
 *
 * @author gkspencer
 */
public abstract class RpcChannelSessionHandler extends SessionHandlerBase {

    // Server socket channel for receiving incoming connections
    private ServerSocketChannel m_srvSockChannel;

    /**
     * Class constructor
     *
     * @param name     String
     * @param protocol String
     * @param server   NetworkServer
     * @param addr     InetAddress
     * @param port     int
     */
    public RpcChannelSessionHandler(String name, String protocol, NetworkServer server, InetAddress addr, int port) {
        super(name, protocol, server, addr, port);
    }

    /**
     * Return the server socket channel
     *
     * @return ServerSocketChannel
     */
    public final ServerSocketChannel getSocketChannel() {
        return m_srvSockChannel;
    }

    /**
     * Initialize the session handler
     *
     * @param server NetworkServer
     * @exception IOException Socket error
     */
    public void initializeSessionHandler(NetworkServer server)
            throws IOException {

        // Create the server socket channel
        m_srvSockChannel = ServerSocketChannel.open();

        // Open the server socket
        InetSocketAddress sockAddr = null;

        if (hasBindAddress())
            sockAddr = new InetSocketAddress(getBindAddress(), getPort());
        else
            sockAddr = new InetSocketAddress(getPort());

        // Bind the socket
        m_srvSockChannel.socket().bind(sockAddr, getListenBacklog());

        // Set the allocated port
        if (getPort() == 0)
            setPort(m_srvSockChannel.socket().getLocalPort());

        // DEBUG
        if (Debug.EnableInfo && hasDebug()) {
            Debug.print("[" + getProtocolName() + "] Binding " + getHandlerName() + " session handler to address : ");
            if (hasBindAddress())
                Debug.println(getBindAddress().getHostAddress());
            else
                Debug.println("ALL");
        }
    }

    /**
     * Close the session handler
     *
     * @param server NetworkServer
     */
    public void closeSessionHandler(NetworkServer server) {

        // Request the main listener thread shutdown
        setShutdown(true);

        try {

            // Close the server socket to release any pending listen
            if (m_srvSockChannel != null)
                m_srvSockChannel.close();
        }
        catch (SocketException ex) {
        }
        catch (Exception ex) {
        }
    }

    /**
     * Create a packet handler for the new client socket connection
     *
     * @param sockChannel SocketChannel
     * @return RpcPacketHandler
     * @exception IOException Error creating the packet handler
     */
    public abstract RpcPacketHandler createPacketHandler(SocketChannel sockChannel)
            throws IOException;
}
