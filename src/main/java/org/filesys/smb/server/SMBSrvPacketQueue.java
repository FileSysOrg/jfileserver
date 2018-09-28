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

package org.filesys.smb.server;

import java.util.ArrayList;
import java.util.List;

/**
 * SMB Packet Queue Class
 *
 * <p>Packet queue used for asynchronous I/O queueing.
 *
 * @author gkspencer
 */
public class SMBSrvPacketQueue {

    // Queue of pending SMB response packets
    private List<QueuedSMBPacket> m_pktQueue;

    /**
     * Queued SMB Packet Class
     */
    public class QueuedSMBPacket {

        // SMB response details
        private SMBSrvPacket m_pkt;
        private int m_offset;
        private int m_len;
        private boolean m_writeRaw;

        /**
         * Class constructor
         *
         * @param pkt      SMBSrvPacket
         * @param offset   int
         * @param len      int
         * @param writeRaw boolean
         */
        public QueuedSMBPacket(SMBSrvPacket pkt, int offset, int len, boolean writeRaw) {
            m_pkt = pkt;
            m_offset = offset;
            m_len = len;
            m_writeRaw = writeRaw;
        }

        /**
         * Return the SMB packet
         *
         * @return SMBSrvPacket
         */
        public final SMBSrvPacket getPacket() {
            return m_pkt;
        }

        /**
         * Return the write buffer offset
         *
         * @return int
         */
        public final int getWriteOffset() {
            return m_offset;
        }

        /**
         * Return the write request length
         *
         * @return int
         */
        public final int getWriteLength() {
            return m_len;
        }

        /**
         * Return the write raw flag
         *
         * @return boolean
         */
        public final boolean hasWriteRaw() {
            return m_writeRaw;
        }

        /**
         * Update the queued packet details
         *
         * @param offset int
         * @param len    int
         */
        public final void updateSettings(int offset, int len) {
            m_offset = offset;
            m_len = len;
        }
    }

    /**
     * Default constructor
     */
    public SMBSrvPacketQueue() {
        m_pktQueue = new ArrayList<QueuedSMBPacket>();
    }

    /**
     * Add an SMB packet to the queue
     *
     * @param pkt      SMBSrvPacket
     * @param offset   int
     * @param len      int
     * @param writeRaw boolean
     */
    public final synchronized void addToQueue(SMBSrvPacket pkt, int offset, int len, boolean writeRaw) {

        // Mark the packet as queued
        pkt.setQueuedForAsyncIO(true);

        // Add to the queue of pending packets
        m_pktQueue.add(new QueuedSMBPacket(pkt, offset, len, writeRaw));
    }

    /**
     * Remove an SMB packet from the head of the queue
     *
     * @return QueuedSMBPacket
     */
    public final synchronized QueuedSMBPacket removeFromQueue() {
        return m_pktQueue.remove(0);
    }

    /**
     * Return the request at the head of the queue without removing from the queue
     *
     * @return QueuedSMBPacket
     */
    public final synchronized QueuedSMBPacket getHeadOfQueue() {
        return m_pktQueue.get(0);
    }

    /**
     * Return the count of packets in the queue
     *
     * @return int
     */
    public final synchronized int numberOfPackets() {
        return m_pktQueue.size();
    }

    /**
     * Return the queue details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[QueueLen=");
        str.append(numberOfPackets());
        str.append(":");

        if (numberOfPackets() > 0) {

            // Dump the first few packet types from the queue
            int idx = 0;

            while (idx < 5 && idx < m_pktQueue.size()) {
                str.append(idx);
                str.append("=");

                SMBSrvPacket srvPkt = m_pktQueue.get(idx).getPacket();
                str.append( srvPkt.getParser().toShortString());
                str.append(",");
            }
        }
        str.append("]");

        return str.toString();
    }
}
