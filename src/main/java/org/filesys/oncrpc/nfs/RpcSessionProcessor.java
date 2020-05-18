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

package org.filesys.oncrpc.nfs;

import org.filesys.oncrpc.RpcPacket;

import java.io.IOException;

/**
 * RPC Session Processor Interface
 *
 * @author gkspencer
 */
public interface RpcSessionProcessor {

    /**
     * Return the RPC program id that this RPC processes
     *
     * @return int
     */
    public int getProgamId();

    /**
     * Return the RPC version id that this RPC processes
     *
     * @return int
     */
    public int getVersionId();

    /**
     * Process an RPC request
     *
     * @param rpc RpcPacket
     * @param sess NFSSrvSession
     * @return RpcPacket
     * @exception IOException Socket error
     */
    public RpcPacket processRpc(RpcPacket rpc, NFSSrvSession sess)
            throws IOException;
}
