/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
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

import java.util.HashMap;
import java.util.Iterator;

import org.filesys.debug.Debug;
import org.filesys.server.core.NoPooledMemoryException;
import org.filesys.server.memory.ByteBufferPool;
import org.filesys.server.thread.ThreadRequestPool;
import org.filesys.server.thread.TimedThreadRequest;

/**
 * SMB Packet Pool Class
 *
 * <p>Allocates buffers from the main byte buffer pool and wraps them in an SMB specific packet.
 *
 * @author gkspencer
 */
public class SMBPacketPool {

    // Constants
    public static final long SMBAllocateWaitTime   = 250;    // milliseconds
    public static final long SMBLeaseTime          = 5000;    // 5 seconds
    public static final long SMBLeaseTimeSecs      = SMBLeaseTime / 1000L;

    // SMB v2 header size
    private static final int SMBV2HeaderSize        = 64;

    // Main byte buffer pool and thread pool
    private ByteBufferPool m_bufferPool;
    private ThreadRequestPool m_threadPool;

    // Track leased out packets/byte buffers
    private HashMap<SMBSrvPacket, SMBSrvPacket> m_leasedPkts = new HashMap<SMBSrvPacket, SMBSrvPacket>();

    // Debug enable
    private boolean m_debug;
    private boolean m_allocDebug;

    // Allow over sized packet allocations, maximum over sized packet size to allow
    private boolean m_allowOverSize = true;
    private int m_maxOverSize = 128 * 1024;        // 128K

    // Maximum buffer size that the pool provides
    private int m_maxPoolBufSize;

    /**
     * SMB Packet Pool Lease Expiry Timed Thread Request Class
     */
    private class SMBLeaseExpiryTimedRequest extends TimedThreadRequest {

        /**
         * Constructor
         */
        public SMBLeaseExpiryTimedRequest() {
            super("SMBPacketPoolExpiry", -SMBLeaseTimeSecs, SMBLeaseTimeSecs);
        }

        /**
         * Expiry checker method
         */
        protected void runTimedRequest() {

            // Check for expired leases
            checkForExpiredLeases();
        }
    }

    /**
     * Class constructor
     *
     * @param bufPool    byteBufferPool
     * @param threadPool ThreadRequestPool
     */
    public SMBPacketPool(ByteBufferPool bufPool, ThreadRequestPool threadPool) {
        m_bufferPool = bufPool;
        m_threadPool = threadPool;

        // Set the maximum pooled buffer size
        m_maxPoolBufSize = m_bufferPool.getLargestSize();

        // Queue the SMB packet lease expiry timed request
        m_threadPool.queueTimedRequest(new SMBLeaseExpiryTimedRequest());
    }

    /**
     * Allocate an SMB packet with the specified buffer size
     *
     * @param reqSiz int
     * @return SMBSrvPacket
     * @throws NoPooledMemoryException No pooled memory available
     */
    public final SMBSrvPacket allocatePacket(int reqSiz)
            throws NoPooledMemoryException {

        // Check if the buffer can be allocated from the pool
        byte[] buf = null;
        boolean nonPooled = false;

        if (reqSiz <= m_maxPoolBufSize) {

            // Allocate the byte buffer for the SMB packet
            buf = m_bufferPool.allocateBuffer(reqSiz, SMBAllocateWaitTime);
        }

        // Check if over sized allocations are allowed
        else if (allowsOverSizedAllocations() && reqSiz <= getMaximumOverSizedAllocation()) {

            // DEBUG
            if (Debug.EnableDbg && hasAllocateDebug())
                Debug.println("[SMB] Allocating an over-sized packet, reqSiz=" + reqSiz);

            // Allocate an over sized packet
            buf = new byte[reqSiz];
            nonPooled = true;
        }

        // Check if the buffer was allocated
        if (buf == null) {

            // Try and allocate a non-pooled buffer if under the maximum buffer size
            if ( reqSiz < m_maxPoolBufSize) {
                buf = new byte[reqSiz];

                // Mark as a non-pooled buffer so there is no lease, it is not from the pool
                nonPooled = true;
            }
            else {

                // DEBUG
                if (Debug.EnableDbg && hasDebug())
                    Debug.println("[SMB] Packet allocate failed, reqSiz=" + reqSiz);

                // Throw an exception, no memory available
                throw new NoPooledMemoryException("Request size " + reqSiz + "/max size=" + m_maxPoolBufSize);
            }
        }

        // Create the SMB packet
        SMBSrvPacket packet = new SMBSrvPacket(buf);

        // Set the lease time, if allocated from a pool, and add to the leased packet list
        if (nonPooled == false) {
            synchronized (m_leasedPkts) {
                packet.setLeaseTime(System.currentTimeMillis() + SMBLeaseTime);
                m_leasedPkts.put(packet, packet);
            }
        }
        else {

            // Mark the packet as using a non-pooled buffer
            packet.setUsingNonPooledBuffer( true);
        }

        // Return the SMB packet with the allocated byte buffer
        return packet;
    }

    /**
     * Allocate a SMB packet with the specified buffer size, copy the header from the request packet
     *
     * @param reqSiz int
     * @param reqPkt SMBSrvPacket
     * @return SMBSrvPacket
     * @throws NoPooledMemoryException No pooled memory available
     */
    public final SMBSrvPacket allocatePacket(int reqSiz, SMBSrvPacket reqPkt)
            throws NoPooledMemoryException {

        // Allocate a new packet, copy the standard header length
        return allocatePacket(reqSiz, reqPkt, -1);
    }

    /**
     * Allocate a SMB packet with the specified buffer size, copy the header from the request packet
     *
     * @param reqSiz  int
     * @param reqPkt  SMBSrvPacket
     * @param copyLen int
     * @return SMBSrvPacket
     * @throws NoPooledMemoryException No pooled memory available
     */
    public final SMBSrvPacket allocatePacket(int reqSiz, SMBSrvPacket reqPkt, int copyLen)
            throws NoPooledMemoryException {

        // Allocate the response packet
        SMBSrvPacket respPkt = allocatePacket(reqSiz);

        // Determine how much data to copy from the original packet
        if ( copyLen == -1) {
            if ( reqPkt.isSMB1())
                copyLen = SMBV1.HeaderLength;
            else
                copyLen = SMBV2HeaderSize;
        }

        // Copy the header from the request to the response
        if ( copyLen > 0)
            System.arraycopy(reqPkt.getBuffer(), 4, respPkt.getBuffer(), 4, copyLen);

        // Attach the response packet to the request
        reqPkt.setAssociatedPacket(respPkt);

        // Make sure the lease time is copied, if either packet has been allocated from the pool
        if (reqPkt.hasLeaseTime() == false && respPkt.hasLeaseTime())
            reqPkt.setLeaseTime(respPkt.getLeaseTime());

        // DEBUG
        if (Debug.EnableDbg && hasAllocateDebug())
            Debug.println("[SMB]  Associated packet reqSiz=" + reqSiz + " with pktSiz=" + reqPkt.getBuffer().length);

        // Return the new packet
        return respPkt;
    }

    /**
     * Release a SMB packet buffer back to the pool
     *
     * @param smbPkt SMBSrvPacket
     */
    public final void releasePacket(SMBSrvPacket smbPkt) {

        // Clear the lease time, remove from the leased packet list
        if (smbPkt.hasLeaseTime()) {

            // Check if the packet lease time has expired
            if (hasDebug() && smbPkt.getLeaseTime() < System.currentTimeMillis())
                Debug.println("[SMB] Release expired packet: pkt=" + smbPkt);

            synchronized (m_leasedPkts) {
                smbPkt.clearLeaseTime();
                m_leasedPkts.remove(smbPkt);
            }
        }

        // Check if the packet is an over sized packet, just let the garbage collector pick it up
        if (smbPkt.getBuffer().length <= m_maxPoolBufSize && smbPkt.usingNonPooledBuffer() == false) {

            // Release the buffer from the SMB packet back to the pool
            m_bufferPool.releaseBuffer(smbPkt.getBuffer());

            // DEBUG
            if (Debug.EnableDbg && hasAllocateDebug() && smbPkt.hasAssociatedPacket() == false)
                Debug.println("[SMB] Packet released bufSiz=" + smbPkt.getBuffer().length);
        }
        else if (Debug.EnableDbg && hasAllocateDebug())
            Debug.println("[SMB] Non-pooled packet left for garbage collector, size=" + smbPkt.getBuffer().length);

        // Check if the packet has an associated packet which also needs releasing
        if (smbPkt.hasAssociatedPacket()) {

            // Check if the associated packet is using an over sized packet
            byte[] assocBuf = smbPkt.getAssociatedPacket().getBuffer();
            if (assocBuf.length <= m_maxPoolBufSize && smbPkt.getAssociatedPacket().usingNonPooledBuffer() == false) {

                // Release the associated packets buffer back to the pool
                m_bufferPool.releaseBuffer(smbPkt.getAssociatedPacket().getBuffer());

                // Remove the associated packet from the leased list
                m_leasedPkts.remove(smbPkt.getAssociatedPacket());

                // DEBUG
                if (Debug.EnableDbg && hasAllocateDebug())
                    Debug.println("[SMB] Packet released bufSiz=" + smbPkt.getBuffer().length + " and assoc packet, bufSiz=" + smbPkt.getAssociatedPacket().getBuffer().length);
            }
            else if (Debug.EnableDbg && hasAllocateDebug())
                Debug.println("[SMB] Non-pooled associated packet left for garbage collector, size=" + smbPkt.getAssociatedPacket().getBuffer().length);

            // Clear the associated packet
            smbPkt.clearAssociatedPacket();
        }
    }

    /**
     * Check for expired packet leases
     */
    private final void checkForExpiredLeases() {

        try {

            // Check if there are any packets leased out
            if (hasDebug()) {

                synchronized (m_leasedPkts) {

                    if (m_leasedPkts.isEmpty() == false) {

                        // Iterate the leased out packet list
                        Iterator<SMBSrvPacket> leaseIter = m_leasedPkts.keySet().iterator();
                        long timeNow = System.currentTimeMillis();

                        while (leaseIter.hasNext()) {

                            // Get the current leased packet and check if it has timed out
                            SMBSrvPacket curPkt = leaseIter.next();
                            if (curPkt.hasLeaseTime() && curPkt.getLeaseTime() < timeNow) {

                                // Report the packet, lease expired
                                Debug.println("[SMB] Packet lease expired, pkt=" + curPkt);
                            }
                        }
                    }
                }
            }
        }
        catch (Throwable ex) {
            Debug.println(ex);
        }
    }

    /**
     * Get the byte buffer pool
     *
     * @return ByteBufferPool
     */
    public final ByteBufferPool getBufferPool() {
        return m_bufferPool;
    }

    /**
     * Return the length of the smallest packet size available
     *
     * @return int
     */
    public final int getSmallestSize() {
        return m_bufferPool.getSmallestSize();
    }

    /**
     * Return the length of the largest packet size available
     *
     * @return int
     */
    public final int getLargestSize() {
        return m_bufferPool.getLargestSize();
    }

    /**
     * Check if over sized packet allocations are allowed
     *
     * @return boolean
     */
    public final boolean allowsOverSizedAllocations() {
        return m_allowOverSize;
    }

    /**
     * Return the maximum size of over sized packet that is allowed
     *
     * @return int
     */
    public final int getMaximumOverSizedAllocation() {
        return m_maxOverSize;
    }

    /**
     * Enable/disable debug output
     *
     * @param ena boolean
     */
    public final void setDebug(boolean ena) {
        m_debug = ena;
    }

    /**
     * Enable/disable allocate/release debug output
     *
     * @param ena boolean
     */
    public final void setAllocateDebug(boolean ena) {
        m_allocDebug = ena;
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Check if allocate/release debug is enabled
     *
     * @return boolean
     */
    public final boolean hasAllocateDebug() {
        return m_allocDebug;
    }

    /**
     * Enable/disable over sized packet allocations
     *
     * @param ena boolean
     */
    public final void setAllowOverSizedAllocations(boolean ena) {
        m_allowOverSize = ena;
    }

    /**
     * Set the maximum size of over sized packet that is allowed
     *
     * @param maxSize int
     */
    public final void setMaximumOverSizedAllocation(int maxSize) {
        m_maxOverSize = maxSize;
    }

    /**
     * Return the packet pool details as a string
     *
     * @return String
     */
    public String toString() {
        return m_bufferPool.toString();
    }
}
 
