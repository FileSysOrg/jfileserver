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

package org.filesys.oncrpc;

import org.filesys.debug.Debug;
import org.filesys.server.thread.ThreadRequest;

/**
 * RPC Thread Request Class
 *
 * <p>Holds the details of an RPC request for processing by a thread pool.
 *
 * @author gkspencer
 */
public class RpcThreadRequest implements ThreadRequest {

    // RPC to be processed
    private RpcPacket m_rpc;

    // RPC processor used to process the packet
    private RpcProcessor m_processor;

    // RPC packet handler used to send back a response RPC, if processing returns a response
    private RpcPacketHandler m_handler;

    /**
     * Class constructor
     *
     * @param rpc        RpcPacket
     * @param rpcProc    RpcProcessor
     * @param rpcHandler RpcPacketHandler
     */
    public RpcThreadRequest(RpcPacket rpc, RpcProcessor rpcProc, RpcPacketHandler rpcHandler) {
        m_rpc = rpc;
        m_processor = rpcProc;
        m_handler = rpcHandler;
    }

    /**
     * Run the RPC request
     */
    public void runRequest() {

        //	If the request is valid process it
        if (m_rpc != null) {

            RpcPacket response = null;

            try {

                //	Process the request
                response = m_processor.processRpc(m_rpc);
                if (response != null)
                    m_rpc.getPacketHandler().sendRpcResponse(response);
            }
            catch ( Throwable ex) {

                // DEBUG
                Debug.println("[RPC] Error processing RPC");
                Debug.println(ex);
            }
            finally {

                //	Release the RPC packet(s) back to the packet pool
                if (m_rpc.getClientProtocol() == Rpc.ProtocolId.TCP) {

                    // Release the request RPC back to the pool
                    if (m_rpc.isAllocatedFromPool())
                        m_rpc.getOwnerPacketPool().releasePacket(m_rpc);

                    // Release the response RPC back to the pool, if valid and has not re-used the request buffer
                    if (response != null && response.getBuffer() != m_rpc.getBuffer() && response.isAllocatedFromPool())
                        response.getOwnerPacketPool().releasePacket(response);
                }
            }
        }
    }

    /**
     * Return the RPC request details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[RPC rpc=");
        str.append(m_rpc);
        str.append("]");

        return str.toString();
    }
}
