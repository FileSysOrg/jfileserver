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
import java.net.InetAddress;
import java.nio.channels.SocketChannel;

import org.filesys.server.ChannelSessionHandler;
import org.filesys.server.NetworkServer;
import org.filesys.smb.server.PacketHandler;
import org.filesys.smb.server.SMBServer;

/**
 * TCP/IP SMB Channel Session Handler Class
 *
 * <p>Handle CIFS socket connections via native SMB, usually port 445.
 *
 * @author gkspencer
 */
public class TcpipSMBChannelSessionHandler extends ChannelSessionHandler {

    /**
     * Class constructor
     *
     * @param server NetworkServer
     * @param addr   InetAddress
     * @param port   int
     */
    public TcpipSMBChannelSessionHandler(NetworkServer server, InetAddress addr, int port) {
        super("TCP-SMB", "SMB", server, addr, port);
    }

    /**
     * Create a packet handler for the new client socket connection
     *
     * @param sockChannel SocketChannel
     * @return PacketHandler
     * @throws IOException I/O error
     */
    public PacketHandler createPacketHandler(SocketChannel sockChannel)
            throws IOException {

        // Create a native SMB packet handler
        return new TcpipSMBChannelHandler(sockChannel, getSMBServer().getPacketPool());
    }

    /**
     * Return the CIFS server
     *
     * @return SMBServer
     */
    public final SMBServer getSMBServer() {
        return (SMBServer) getServer();
    }
}
