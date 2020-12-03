/*
 * Copyright (C) 2018-2020 GK Spencer
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
package org.filesys.oncrpc.nfs;

import org.filesys.oncrpc.Rpc;
import org.filesys.oncrpc.RpcPacketHandler;
import org.filesys.server.SessionLimitException;

import java.net.SocketAddress;

/**
 * NFS Server Session Factory Interface
 *
 * @author gkspencer
 */
public interface SrvSessionFactory {

    /**
     * Create a new NFS session
     *
     * @param pktHandler RpcPacketHandler
     * @param nfsServer NFSServer
     * @param sessId int
     * @param protocolType Rpc.ProtocolId
     * @param remAddr SocketAddress
     * @return NFSSrvSession
     * @exception SessionLimitException Session limit exceeded
     */
    public NFSSrvSession createSession(RpcPacketHandler pktHandler, NFSServer nfsServer, int sessId, Rpc.ProtocolId protocolType,
                                       SocketAddress remAddr)
        throws SessionLimitException;
}
