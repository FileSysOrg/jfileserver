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

import org.filesys.debug.Debug;
import org.filesys.server.core.NoPooledMemoryException;
import org.filesys.server.memory.ByteBufferPool;
import org.filesys.server.thread.ThreadRequestPool;
import org.filesys.server.thread.TimedThreadRequest;
import org.filesys.smb.server.SMBPacketPool;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBV1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Rpc Packet Pool Class
 *
 * <p>
 * Contains a pool of small and large RpcPacket objects for use by multi-threaded RPC servers.
 *
 * @author gkspencer
 */
public class RpcPacketPool {

    // Constants
    public static final long RpcAllocateWaitTime   = 250;    // milliseconds
    public static final long RpcLeaseTime          = 5000;    // 5 seconds
    public static final long RpcLeaseTimeSecs      = RpcLeaseTime / 1000L;

    // Main byte buffer pool and thread pool
    private ByteBufferPool m_bufferPool;
    private ThreadRequestPool m_threadPool;

    // Track leased out packets/byte buffers
    private HashMap<RpcPacket, RpcPacket> m_leasedPkts = new HashMap<RpcPacket, RpcPacket>();

    // Debug enable
    private boolean m_debug;
    private boolean m_allocDebug;

    // Allow over sized packet allocations, maximum over sized packet size to allow
    private boolean m_allowOverSize = true;
    private int m_maxOverSize = 128 * 1024;        // 128K

    // Maximum buffer size that the pool provides
    private int m_maxPoolBufSize;

    /**
     * RPC Packet Pool Lease Expiry Timed Thread Request Class
     */
    private class RpcLeaseExpiryTimedRequest extends TimedThreadRequest {

        /**
         * Constructor
         */
        public RpcLeaseExpiryTimedRequest() {
            super("RpcPacketPoolExpiry", -RpcLeaseTimeSecs, RpcLeaseTimeSecs);
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
    public RpcPacketPool(ByteBufferPool bufPool, ThreadRequestPool threadPool) {
        m_bufferPool = bufPool;
        m_threadPool = threadPool;

        // Set the maximum pooled buffer size
        m_maxPoolBufSize = m_bufferPool.getLargestSize();

        // Queue the SMB packet lease expiry timed request
        m_threadPool.queueTimedRequest(new RpcPacketPool.RpcLeaseExpiryTimedRequest());
    }

    /**
     * Allocate an RPC packet with the specified buffer size
     *
     * @param reqSiz int
     * @return RpcPacket
     * @throws NoPooledMemoryException No pooled memory available
     */
    public final RpcPacket allocatePacket(int reqSiz)
            throws NoPooledMemoryException {

        // Check if the buffer can be allocated from the pool
        byte[] buf = null;
        boolean nonPooled = false;

        if (reqSiz <= m_maxPoolBufSize) {

            // Allocate the byte buffer for the RPC packet
            buf = m_bufferPool.allocateBuffer(reqSiz, RpcAllocateWaitTime);
        }

        // Check if over sized allocations are allowed
        else if (allowsOverSizedAllocations() && reqSiz <= getMaximumOverSizedAllocation()) {

            // DEBUG
            if (Debug.EnableDbg && hasAllocateDebug())
                Debug.println("[RPC] Allocating an over-sized packet, reqSiz=" + reqSiz);

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
                    Debug.println("[RPC] Packet allocate failed, reqSiz=" + reqSiz);

                // Throw an exception, no memory available
                throw new NoPooledMemoryException("Request size " + reqSiz + "/max size=" + m_maxPoolBufSize);
            }
        }

        // Create the RPC packet
        RpcPacket packet = new RpcPacket(buf);

        // Set the lease time, if allocated from a pool, and add to the leased packet list
        if (nonPooled == false) {

            // Set the owner memory pool for the packet
            packet.setOwnerPacketPool( this);

            // Add the packet to the list of leased out packets
            synchronized (m_leasedPkts) {
                packet.setLeaseTime(System.currentTimeMillis() + RpcLeaseTime);
                m_leasedPkts.put(packet, packet);
            }
        }

        // Return the SMB packet with the allocated byte buffer
        return packet;
    }

    /**
     * Allocate an associated RPC packet with the specified buffer size, and copy bytes from the original RPC
     *
     * @param reqSiz int
     * @param assocRpc RpcPacket
     * @param copyLen int
     * @return RpcPacket
     * @throws NoPooledMemoryException No pooled memory available
     */
    public final RpcPacket allocateAssociatedPacket(int reqSiz, RpcPacket assocRpc, int copyLen)
            throws NoPooledMemoryException {

        // Allocate an RPC packet
        RpcPacket packet = allocatePacket( reqSiz);

        if ( packet == null)
            return packet;

        // Associate the new packet with the original packet
        assocRpc.setAssociatedPacket( packet);

        // Copy bytes from the original packet to the new packet
        if ( copyLen > 0) {

            // Copy data to the new packet
            System.arraycopy( assocRpc.getBuffer(), 0, packet.getBuffer(), 0, copyLen);
        }
        else if ( copyLen == -1) {

            // Copy the request header to the new packet
            System.arraycopy(assocRpc.getBuffer(), 0, packet.getBuffer(), 0, assocRpc.getRequestHeaderLength());
        }

        // Return the new packet
        return packet;
    }

    /**
     * Release an RPC packet buffer back to the pool
     *
     * @param rpcPkt RpcPacket
     */
    public final void releasePacket(RpcPacket rpcPkt) {

        // Clear the lease time, remove from the leased packet list
        if (rpcPkt.hasLeaseTime()) {

            // Check if the packet lease time has expired
            if (hasDebug() && rpcPkt.getLeaseTime() < System.currentTimeMillis())
                Debug.println("[RPC] Release expired packet: pkt=" + rpcPkt);

            synchronized (m_leasedPkts) {
                rpcPkt.clearLeaseTime();
                m_leasedPkts.remove(rpcPkt);
            }
        }

        // Check if the packet is an over sized packet, just let the garbage collector pick it up
        if (rpcPkt.getBuffer().length <= m_maxPoolBufSize && rpcPkt.isAllocatedFromPool()) {

            // Release the buffer from the SMB packet back to the pool
            m_bufferPool.releaseBuffer(rpcPkt.getBuffer());

            // DEBUG
            if (Debug.EnableDbg && hasAllocateDebug())
                Debug.println("[RPC] Packet released bufSiz=" + rpcPkt.getBuffer().length);
        }
        else if (Debug.EnableDbg && hasAllocateDebug())
            Debug.println("[RPC] Non-pooled packet left for garbage collector, size=" + rpcPkt.getBuffer().length);
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
                        Iterator<RpcPacket> leaseIter = m_leasedPkts.keySet().iterator();
                        long timeNow = System.currentTimeMillis();

                        while (leaseIter.hasNext()) {

                            // Get the current leased packet and check if it has timed out
                            RpcPacket curPkt = leaseIter.next();
                            if (curPkt.hasLeaseTime() && curPkt.getLeaseTime() < timeNow) {

                                // Report the packet, lease expired
                                Debug.println("[RPC] Packet lease expired, pkt=" + curPkt);
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
