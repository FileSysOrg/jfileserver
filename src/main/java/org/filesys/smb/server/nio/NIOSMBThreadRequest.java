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

import java.nio.channels.SelectionKey;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.postprocess.PostRequestProcessor;
import org.filesys.server.thread.ThreadRequest;
import org.filesys.server.thread.ThreadRequestPool;
import org.filesys.smb.server.PacketHandler;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;

/**
 * NIO SMB Thread Request Class
 *
 * <p>Holds the details of an NIO channel based SMB session request for processing by a thread pool.
 *
 * @author gkspencer
 */
public class NIOSMBThreadRequest implements ThreadRequest {

    // Maximum packets to run per thread run
    private static final int MaxPacketsPerRun = 8;

    // SMB session
    private SMBSrvSession m_sess;

    // Selection key for this socket channel
    private SelectionKey m_selectionKey;

    // Maximum number of SMB packets to process in this thread run
    private int m_maxPktsPerRun;

    /**
     * Class constructor
     *
     * @param sess   SMBSrvSession
     * @param selKey SelectionKey
     * @param maxPktsPerRun int
     */
    public NIOSMBThreadRequest(SMBSrvSession sess, SelectionKey selKey, int maxPktsPerRun) {
        m_sess = sess;
        m_selectionKey = selKey;

        m_maxPktsPerRun = maxPktsPerRun;
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
            boolean asyncPkt = false;

            SMBSrvPacket smbPkt = null;
            PostRequestProcessor postProc = null;

            while (pktCount < m_maxPktsPerRun && morePkts == true && pktError == false) {

                try {

                    // Get the packet handler and read in the SMB request
                    PacketHandler pktHandler = m_sess.getPacketHandler();
                    if ( pktHandler != null)
                        smbPkt = pktHandler.readPacket();
                    else {
                        smbPkt = null;
                        morePkts = false;

                        continue;
                    }

                    // If the request packet is not valid then close the session
                    if (smbPkt == null) {

                        // If we have not processed any packets in this run it is an error
                        if (pktCount == 0) {

                            // DEBUG
                            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.SOCKET))
                                Debug.println("Received null packet, closing session sess=" + m_sess.getUniqueId() + ", addr=" + m_sess.getRemoteAddress().getHostAddress());

                            // Close the session
                            m_sess.hangupSession("Client closed socket");
                            m_sess.processPacket(null);

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

                        // Check if there are any asynchronous packets queued for this session, they may be queued by
                        // another session/thread for oplock breaks. Do not re-enable socket read events until the asynchronous
                        // packet queue has been cleared
                        if (asyncPkt == false && m_sess.hasAsyncResponseQueued()) {

                            // Indicate this session has asynchronous packets queued
                            asyncPkt = true;
                        }

                        // If this is the last packet before we hit the maximum packets per thread then
                        // re-enable read events for this socket channel
                        else if (pktCount == m_maxPktsPerRun && asyncPkt == false) {
                            m_selectionKey.interestOps(m_selectionKey.interestOps() | SelectionKey.OP_READ);
                            m_selectionKey.selector().wakeup();
                        }

                        // Process the SMB request
                        m_sess.processPacket(smbPkt);

                        // Save any post processor that has been queued on the packet
                        postProc = smbPkt.getPostProcessor();
                        if ( postProc != null) {

                            // Stop processing any more packets, re-enable socket events for the channel
                            morePkts = false;
                        }

                        // Clear the packet
                        smbPkt = null;
                    }
                }
                catch (Throwable ex) {

                    // DEBUG
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.SOCKET)) {

                        // Do not log client connection resets
                        if ( ex.getMessage() != null && ex.getMessage().startsWith( "Connection reset") == false) {
                            Debug.println("Error during packet receive, closing session sess=" + m_sess.getUniqueId() + ", addr=" + m_sess.getRemoteAddressString() + " ex=" + ex.getMessage());

                            if ( Debug.hasDumpStackTraces())
                                Debug.println(ex);
                        }
                    }

                    // Close the session
                    m_sess.hangupSession("Client closed socket");
                    m_sess.processPacket(null);

                    // Cancel the selection key
                    m_selectionKey.cancel();
                    m_selectionKey.selector().wakeup();

                    // Indicate socket/packet error
                    pktError = true;
                }
                finally {

                    // Make sure the request packet is returned to the pool
                    if (smbPkt != null)
                        m_sess.getPacketPool().releasePacket(smbPkt);
                }
            }

            // Process the asynchronous packet queue for the session
            if (asyncPkt == true) {

                // Process any asynchronous packets (oplock breaks and change notifications)
                int asyncCnt = m_sess.sendQueuedAsyncResponses();

                // DEBUG
                if (asyncCnt > 0 && Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.SOCKET))
                    Debug.println("Sent queued async packets (NIO) count=" + asyncCnt + ", sess=" + m_sess.getUniqueId() + ", addr=" + m_sess.getRemoteAddress().getHostAddress());
            }

            // Re-enable read events for this socket channel, if there were no errors, and the session has not been reconnected
            if (pktError == false && (pktCount < m_maxPktsPerRun || asyncPkt == true)) {

                // Re-enable read events for this socket channel
                m_selectionKey.interestOps(m_selectionKey.interestOps() | SelectionKey.OP_READ);
                m_selectionKey.selector().wakeup();
            }

            // Check if there is a post processor to run
            if ( postProc != null) {

                // DEBUG
                if ( Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.POSTPROC))
                    Debug.println("Running post processor " + postProc);

                // Call the post processor
                try {
                    postProc.runPostProcessor();
                }
                catch ( Throwable ex) {
                    if ( Debug.hasDumpStackTraces() || m_sess.hasDebug(SMBSrvSession.Dbg.POSTPROC))
                        Debug.println( ex);
                }
                finally {

                    // Check if there is an active transaction
                    if (m_sess.hasTransaction()) {

                        // DEBUG
                        if (Debug.EnableError)
                            Debug.println("** Active transaction after packet processing (post process), cleaning up **");

                        // Close the active transaction
                        m_sess.endTransaction();
                    }

                }
            }

            // DEBUG
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.THREADPOOL) && pktCount > 1)
                Debug.println("Processed " + pktCount + " packets for addr=" + m_sess.getRemoteAddress().getHostAddress() + " in one thread run (max=" + m_maxPktsPerRun + ")");
        }
    }

    /**
     * Return the SMB request details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[NIO SMB Sess=");
        str.append(m_sess.getUniqueId());
        str.append("]");

        return str.toString();
    }
}
