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
import org.filesys.oncrpc.Rpc;
import org.filesys.oncrpc.RpcPacket;
import org.filesys.oncrpc.RpcPacketHandler;
import org.filesys.oncrpc.nfs.NFSSrvSession;
import org.filesys.server.thread.ThreadRequest;

import java.nio.channels.SelectionKey;

/**
 * NIO RPC Thread Request Class
 *
 * <p>Holds the details of an NIO channel based NFS session request for processing by a thread pool.
 *
 * @author gkspencer
 */
public class NIORpcThreadRequest implements ThreadRequest {

    // Maximum packets to run per thread run
    private static final int MaxPacketsPerRun = 4;

    // NFS session
    private NFSSrvSession m_sess;

    // Selection key for this socket channel
    private SelectionKey m_selectionKey;

    /**
     * Class constructor
     *
     * @param sess   NFSSrvSession
     * @param selKey SelectionKey
     */
    public NIORpcThreadRequest(NFSSrvSession sess, SelectionKey selKey) {
        m_sess = sess;
        m_selectionKey = selKey;
    }

    /**
     * Run the SMB request
     */
    public void runRequest() {

        // Check if the session is still alive
        if (m_sess.isShutdown() == false) {

            // Read one or more packets from the socket for this session
            int pktCount = 0;
            boolean morePkts = true;
            boolean pktError = false;

            RpcPacket rpcPkt = null;
            RpcPacket rpcResponse = null;

            while (pktCount < MaxPacketsPerRun && morePkts == true && pktError == false) {

                try {

                    // Get the packet handler and read in the RPC request
                    RpcPacketHandler pktHandler = m_sess.getPacketHandler();
                    if ( pktHandler != null)
                        rpcPkt = pktHandler.receiveRpc();
                    else {
                        rpcPkt = null;
                        morePkts = false;

                        continue;
                    }

                    // If the request packet is not valid then close the session
                    if (rpcPkt == null) {

                        // If we have not processed any packets in this run it is an error
                        if (pktCount == 0) {

                            // DEBUG
                            if (Debug.EnableInfo && m_sess.hasDebug(NFSSrvSession.DBG_SOCKET))
                                Debug.println("Received null packet, closing session sess=" + m_sess.getUniqueId() + ", addr=" + m_sess.getRemoteAddress().getHostAddress());

                            // Close the session
                            m_sess.closeSession();

                            // Cancel the selection key
                            m_selectionKey.cancel();
                            m_selectionKey.selector().wakeup();

                            // Indicate socket/packet error
                            pktError = true;
                        }

                        // No more packets available
                        morePkts = false;
                    }
                    else {

                        // Update the count of packets processed
                        pktCount++;

                        // If this is the last packet before we hit the maximum packets per thread then re-enable read events
                        // for this socket channel
                        if (pktCount == MaxPacketsPerRun) {
                            m_selectionKey.interestOps(m_selectionKey.interestOps() | SelectionKey.OP_READ);
                            m_selectionKey.selector().wakeup();
                        }

                        // Set the RPC client address/port
                        rpcPkt.setClientDetails( m_sess.getRemoteAddress(), m_sess.getRemotePort(), Rpc.ProtocolId.TCP);

                        // Process the RPC request
                        rpcResponse = m_sess.getNFSServer().processRpc(rpcPkt);

                        if ( rpcResponse != null)
                            m_sess.getPacketHandler().sendRpcResponse( rpcResponse);

                        // Release the RPC packet back to the pool
                        m_sess.getNFSServer().getPacketPool().releasePacket( rpcPkt);

                        // Release the response packet
                        if ( rpcPkt.hasAssociatedPacket() && rpcPkt.getAssociatedPacket().isAllocatedFromPool())
                            m_sess.getNFSServer().getPacketPool().releasePacket( rpcPkt.getAssociatedPacket());

                        rpcPkt = null;
                        rpcResponse = null;
                    }
                }
                catch (Throwable ex) {

                    // DEBUG
                    if (Debug.EnableInfo && m_sess.hasDebug(NFSSrvSession.DBG_SOCKET)) {
                        Debug.println("Error during packet receive, closing session sess=" + m_sess.getUniqueId() + ", addr=" + m_sess.getRemoteAddress() + "/" +
                                m_sess.getRemotePort() + " ex=" + ex.getMessage());
                        Debug.println(ex);
                    }

                    // Close the session
                    m_sess.closeSession();

                    // Cancel the selection key
                    m_selectionKey.cancel();
                    m_selectionKey.selector().wakeup();

                    // Indicate socket/packet error
                    pktError = true;
                }
                finally {

                    // Make sure the request packet is returned to the pool
                    if (rpcPkt != null && rpcPkt.isAllocatedFromPool())
                        m_sess.getNFSServer().getPacketPool().releasePacket(rpcPkt);

                    // Release the response packet, if valid
                    if ( rpcPkt != null && rpcPkt.hasAssociatedPacket() && rpcPkt.getAssociatedPacket().isAllocatedFromPool())
                        m_sess.getNFSServer().getPacketPool().releasePacket( rpcPkt.getAssociatedPacket());
                }
            }

            // Re-enable read events for this socket channel, if there were no errors
            if (pktError == false && pktCount < MaxPacketsPerRun) {

                // Re-enable read events for this socket channel
                m_selectionKey.interestOps(m_selectionKey.interestOps() | SelectionKey.OP_READ);
                m_selectionKey.selector().wakeup();
            }

            // DEBUG
            if (Debug.EnableInfo && m_sess.hasDebug(NFSSrvSession.DBG_THREADPOOL) && pktCount > 1)
                Debug.println("Processed " + pktCount + " packets for addr=" + m_sess.getRemoteAddress().getHostAddress() + " in one thread run (max=" + MaxPacketsPerRun + ")");
        }
    }

    /**
     * Return the NFS request details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[NIO NFS Sess=");
        str.append(m_sess.getUniqueId());
        str.append("]");

        return str.toString();
    }
}
