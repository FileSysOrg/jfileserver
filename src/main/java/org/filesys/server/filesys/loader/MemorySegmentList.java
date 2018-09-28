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

package org.filesys.server.filesys.loader;

import java.util.ArrayList;
import java.util.List;

/**
 * Memory Segment List Class
 *
 * <p>Contains an ordered list of MemorySegment objects.
 *
 * @author gkspencer
 */
public class MemorySegmentList {

    //	List of memory segments
    private List<MemorySegment> m_segments;

    /**
     * Default constructor
     */
    public MemorySegmentList() {
        m_segments = new ArrayList<MemorySegment>();
    }

    /**
     * Add a segment to the list
     *
     * @param seg MemorySegment
     */
    public synchronized final void addSegment(MemorySegment seg) {
        m_segments.add(seg);
    }

    /**
     * Return the specified memory segment
     *
     * @param idx int
     * @return MemorySegment
     */
    public final MemorySegment getSegmentAt(int idx) {
        if (idx >= 0 && idx <= numberOfSegments())
            return m_segments.get(idx);
        return null;
    }

    /**
     * Return the count of memory segments
     *
     * @return int
     */
    public final int numberOfSegments() {
        return m_segments.size();
    }

    /**
     * Find the segment that contains the required data
     *
     * @param fileOff long
     * @param len     int
     * @return MemorySegment
     */
    public final MemorySegment findSegment(long fileOff, int len) {

        //	Check if the list is empty
        if (m_segments == null || m_segments.size() == 0)
            return null;

        //	Find the memory segment for the required data
        for (int i = 0; i < numberOfSegments(); i++) {

            //	Get the current memory segment
            MemorySegment seg = m_segments.get(i);

            //	Check if this segment contains the required data
            if (seg.containsData(fileOff, len))
                return seg;
        }

        //	No segments have the required data
        return null;
    }

    /**
     * Remove memory segments with a read count equal or below the specified level
     *
     * @param readLev int
     * @return int
     */
    public final synchronized int removeSegments(int readLev) {

        //	Check if there are any segments in the list
        if (m_segments == null || m_segments.size() == 0)
            return 0;

        //	Remove segments with a read hit count equal/below the specified level
        int remCount = 0;
        int idx = 0;

        while (idx < m_segments.size()) {

            //	Get the current segment from the list
            MemorySegment memSeg = m_segments.get(idx);

            //	Check if the current memory segment is equal/below the read hit threshold
            if (memSeg.getReadCounter() <= readLev) {

                //	Remove the memory segment from the list
                m_segments.remove(idx);
                remCount++;
            } else {

                //	Advance to the next segment
                idx++;
            }
        }

        //	Return the removed segment count
        return remCount;
    }

    /**
     * Decrement the read hit counts for all memory segments by the specified amount, and
     * optionally remove memory segments that have a hit count of zero.
     *
     * @param decr    int
     * @param remZero boolean
     * @return int
     */
    public final synchronized int decrementHitCounts(int decr, boolean remZero) {

        //	Check if there are any segments in the list
        if (m_segments == null || m_segments.size() == 0)
            return 0;

        //	Remove segments with a read hit count equal/below the specified level
        int remCount = 0;
        int idx = 0;

        while (idx < m_segments.size()) {

            //	Get the current segment from the list
            MemorySegment memSeg = m_segments.get(idx);

            //	Decrement the read hit counter
            memSeg.decrementReadCounter(decr);

            //	Check if the current memory segment is equal/below the read hit threshold
            if (remZero && memSeg.getReadCounter() == 0) {

                //	Remove the memory segment from the list
                m_segments.remove(idx);
                remCount++;
            } else {

                //	Advance to the next segment
                idx++;
            }
        }

        //	Return the count of removed segments
        return remCount;
    }
}
