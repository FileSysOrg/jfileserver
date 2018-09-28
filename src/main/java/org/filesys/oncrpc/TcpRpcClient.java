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
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP RPC Client Connection Class
 *
 * @author gkspencer
 */
public class TcpRpcClient extends RpcClient {

    //	TCP RPC client connection
    private TcpRpcPacketHandler m_client;

    /**
     * Class constructor
     *
     * @param addr       InetAddress
     * @param port       int
     * @param maxRpcSize int
     * @exception IOException Socket error
     */
    public TcpRpcClient(InetAddress addr, int port, int maxRpcSize)
            throws IOException {
        super(addr, port, Rpc.TCP, maxRpcSize);

        //	Connect a socket to the remote server
        Socket sock = new Socket();
        sock.setReuseAddress(true);
        sock.setSoLinger(false, 0);

        sock.connect(new InetSocketAddress(addr, port));

        //	Create the TCP RPC packet handler for the client connection
        m_client = new TcpRpcPacketHandler(sock, maxRpcSize);
    }

    /**
     * Class constructor
     *
     * @param addr       InetAddress
     * @param port       int
     * @param fromAddr   InetAddress
     * @param fromPort   int
     * @param maxRpcSize int
     * @exception IOException Socket error
     */
    public TcpRpcClient(InetAddress addr, int port, InetAddress fromAddr, int fromPort, int maxRpcSize)
            throws IOException {
        super(addr, port, Rpc.TCP, maxRpcSize);

        //	Connect a socket to the remote server
        Socket sock = new Socket();
        sock.setReuseAddress(true);
        sock.setSoLinger(false, 0);

        sock.bind(new InetSocketAddress(fromAddr, fromPort));
        sock.connect(new InetSocketAddress(addr, port));

        //	Create the TCP RPC packet handler for the client connection
        m_client = new TcpRpcPacketHandler(sock, maxRpcSize);
    }

    /**
     * Send an RPC request using the socket connection, and receive a response
     *
     * @param rpc   RpcPacket
     * @param rxRpc RpcPacket
     * @return RpcPacket
     * @exception IOException Socket error
     */
    public RpcPacket sendRPC(RpcPacket rpc, RpcPacket rxRpc)
            throws IOException {

        //	Use the TCP packet handler to send the RPC
        m_client.sendRpc(rpc);

        //	Receive a response RPC
        RpcPacket rxPkt = rxRpc;
        if (rxPkt == null)
            rxPkt = new RpcPacket(getMaximumRpcSize());

        m_client.receiveRpc(rxPkt);

        //	Return the RPC response
        return rxPkt;
    }

    /**
     * Close the connection to the remote RPC server
     */
    public void closeConnection() {

        //	Close the packet handler
        if (m_client != null) {
            m_client.closePacketHandler();
            m_client = null;
        }
    }
}
