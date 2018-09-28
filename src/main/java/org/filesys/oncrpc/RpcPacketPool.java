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

import java.util.ArrayList;
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
    //
    // Default small/large packet sizes
    public static final int DefaultSmallSize = 512;
    public static final int DefaultLargeSize = 32768;

    public static final int DefaultSmallLimit = -1; // no allocation limit
    public static final int DefaultLargeLimit = -1; // " " "

    // Small/large packet lists
    private List<RpcPacket> m_smallPackets;
    private List<RpcPacket> m_largePackets;

    // Small packet size and maximum allowed packets
    private int m_smallPktSize;
    private int m_smallPktLimit;

    // Large packet size and maximum allowed packets
    private int m_largePktSize;
    private int m_largePktLimit;

    // Count of allocated small/large packets
    private int m_smallPktCount;
    private int m_largePktCount;

    // Debug enable
    private static final boolean m_debug = false;

    /**
     * Default constructor
     */
    public RpcPacketPool() {

        // Create the small/large packet lists
        m_smallPackets = new ArrayList<RpcPacket>();
        m_largePackets = new ArrayList<RpcPacket>();

        // Set the packet sizes/limits
        m_smallPktSize = DefaultSmallSize;
        m_smallPktLimit = DefaultSmallLimit;

        m_largePktSize = DefaultLargeSize;
        m_largePktLimit = DefaultLargeLimit;
    }

    /**
     * Class constructor
     *
     * @param smallSize  int
     * @param smallLimit int
     * @param largeSize  int
     * @param largeLimit int
     */
    public RpcPacketPool(int smallSize, int smallLimit, int largeSize, int largeLimit) {

        // Create the small/large packet lists
        m_smallPackets = new ArrayList<RpcPacket>();
        m_largePackets = new ArrayList<RpcPacket>();

        // Save the packet sizes/limits
        m_smallPktSize = smallSize;
        m_smallPktLimit = smallLimit;

        m_largePktSize = largeSize;
        m_largePktLimit = largeLimit;
    }

    /**
     * Class constructor
     *
     * @param largeSize  int
     * @param largeLimit int
     */
    public RpcPacketPool(int largeSize, int largeLimit) {

        // Create the small/large packet lists
        m_smallPackets = new ArrayList<RpcPacket>();
        m_largePackets = new ArrayList<RpcPacket>();

        // Save the packet sizes/limits
        m_smallPktSize = DefaultSmallSize;
        m_smallPktLimit = largeLimit;

        m_largePktSize = largeSize;
        m_largePktLimit = largeLimit;
    }

    /**
     * Return the small packet size
     *
     * @return int
     */
    public final int getSmallPacketSize() {
        return m_smallPktSize;
    }

    /**
     * Return the count of allocated small packets
     *
     * @return int
     */
    public final int getSmallPacketCount() {
        return m_smallPktCount;
    }

    /**
     * Return the small packet allocation limit
     *
     * @return int
     */
    public final int getSmallPacketAllocationLimit() {
        return m_smallPktLimit;
    }

    /**
     * Return the count of available large packets
     *
     * @return int
     */
    public final int availableLargePackets() {
        return m_largePackets.size();
    }

    /**
     * Return the large packet size
     *
     * @return int
     */
    public final int getLargePacketSize() {
        return m_largePktSize;
    }

    /**
     * Return the count of allocated large packets
     *
     * @return int
     */
    public final int getLargePacketCount() {
        return m_largePktCount;
    }

    /**
     * Return the large packet allocation limit
     *
     * @return int
     */
    public final int getLargePacketAllocationLimit() {
        return m_largePktLimit;
    }

    /**
     * Return the count of available small packets
     *
     * @return int
     */
    public final int availableSmallPackets() {
        return m_smallPackets.size();
    }

    /**
     * Allocate a packet from the packet pool
     *
     * @param reqSize int
     * @return RpcPacket
     */
    public final RpcPacket allocatePacket(int reqSize) {

        // Check if the packet should come from the small or large packet list
        RpcPacket pkt = null;

        if (reqSize <= m_smallPktSize) {

            // Allocate a packet from the small packet list
            pkt = allocateSmallPacket();

            // DEBUG
            if (m_debug)
                Debug.println("RpcPacketPool Allocated (small) " + pkt.getBuffer() + ", len=" + pkt.getBuffer().length
                        + ", list=" + m_smallPackets.size() + "/" + m_smallPktLimit);
        } else {

            // Allocate a packet from the large packet list
            pkt = allocateLargePacket();

            // DEBUG
            if (m_debug)
                Debug.println("RpcPacketPool Allocated (large) " + pkt.getBuffer() + ", len=" + pkt.getBuffer().length
                        + ", list=" + m_largePackets.size() + "/" + m_largePktLimit);
        }

        // Return the allocated packet
        return pkt;
    }

    /**
     * Release an RPC packet back to the pool
     *
     * @param pkt RpcPacket
     */
    public final void releasePacket(RpcPacket pkt) {

        // Check if the packet should be released to the small or large list
        if (pkt.getBuffer().length >= m_largePktSize) {

            // Release the packet to the large packet list
            synchronized (m_largePackets) {

                // Add the packet back to the free list
                m_largePackets.add(pkt);

                // Signal any waiting threads that there are packets available
                m_largePackets.notify();

                // DEBUG
                if (m_debug)
                    Debug.println("RpcPacketPool Released (large) " + pkt.getBuffer() + ", len=" + pkt.getBuffer().length
                            + ", list=" + m_largePackets.size());
            }
        } else {

            // Release the packet to the small packet list
            synchronized (m_smallPackets) {

                // Add the packet back to the free list
                m_smallPackets.add(pkt);

                // Signal any waiting threads that there are packets available
                m_smallPackets.notify();

                // DEBUG
                if (m_debug)
                    Debug.println("RpcPacketPool Released (small) " + pkt.getBuffer() + ", len=" + pkt.getBuffer().length);
            }
        }
    }

    /**
     * Allocate, or create, a small RPC packet
     *
     * @return RpcPacket
     */
    private final RpcPacket allocateSmallPacket() {

        RpcPacket pkt = null;

        synchronized (m_smallPackets) {

            // Check if there is a packet available from the small packet list
            if (m_smallPackets.size() > 0) {

                // Remove a packet from the head of the free list
                pkt = m_smallPackets.remove(0);
            } else if (m_smallPktLimit == -1 || m_smallPktCount < m_smallPktLimit) {

                // Allocate a new packet
                pkt = new RpcPacket(m_smallPktSize, this);
                m_smallPktCount++;
            } else {

                // Wait for a packet to be released to the small packet list
                try {

                    // Wait for a packet
                    m_smallPackets.wait();

                    // Try to get the packet from the small packet list again
                    if (m_smallPackets.size() > 0) {

                        // Remove a packet from the head of the free list
                        pkt = m_smallPackets.remove(0);
                    }
                }
                catch (InterruptedException ex) {
                }
            }
        }

        // Return the allocated packet
        return pkt;
    }

    /**
     * Allocate, or create, a large RPC packet
     *
     * @return RpcPacket
     */
    private final RpcPacket allocateLargePacket() {

        RpcPacket pkt = null;

        synchronized (m_largePackets) {

            // Check if there is a packet available from the large packet list
            if (m_largePackets.size() > 0) {

                // Remove a packet from the head of the free list
                pkt = m_largePackets.remove(0);
            } else if (m_largePktLimit == -1 || m_largePktCount < m_largePktLimit) {

                // Allocate a new packet
                pkt = new RpcPacket(m_largePktSize, this);
                m_largePktCount++;
            } else {

                // Wait for a packet to be released to the large packet list
                try {

                    // Wait for a packet
                    while (m_largePackets.isEmpty())
                        m_largePackets.wait();

                    // Try to get the packet from the large packet list again
                    if (m_largePackets.size() > 0) {

                        // Remove a packet from the head of the free list
                        pkt = m_largePackets.remove(0);
                    }
                }
                catch (InterruptedException ex) {
                }
            }
        }

        // Return the allocated packet
        return pkt;
    }
}
