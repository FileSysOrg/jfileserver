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
package org.filesys.server.filesys.loader;

import java.util.ArrayList;
import java.util.List;

/**
 * Memory Segment List Class
 *
 * <p>Contains an ordered list of memory buffers that hold part of a files data.
 *
 * @author gkspencer
 */
public class MemoryBufferList {

    //	List of memory buffers
    private List<MemoryBuffer> m_buffers;

    // Next sequential buffer offset
    private long m_nextSeqOffset;

    /**
     * Default constructor
     */
    public MemoryBufferList() {
        m_buffers = new ArrayList<MemoryBuffer>();
    }

    /**
     * Class constructor
     *
     * @param bufCnt int
     */
    public MemoryBufferList( int bufCnt) {
        m_buffers = new ArrayList<MemoryBuffer>( bufCnt);
    }

    /**
     * Add a segment to the list
     *
     * @param seg MemoryBuffer
     */
    public synchronized final void addSegment(MemoryBuffer seg) {

        // Add the memory buffer
        m_buffers.add(seg);

        // Check if the buffer is the next in the sequence
        if ( seg.getFileOffset() == m_nextSeqOffset)
            m_nextSeqOffset += seg.getUsedLength();
    }

    /**
     * Return the specified memory segment
     *
     * @param idx int
     * @return MemoryBuffer
     */
    public synchronized final MemoryBuffer getSegmentAt(int idx) {
        if (idx >= 0 && idx <= numberOfSegments())
            return m_buffers.get(idx);
        return null;
    }

    /**
     * Return the count of memory segments
     *
     * @return int
     */
    public synchronized final int numberOfSegments() {
        return m_buffers.size();
    }

    /**
     * Remove a segment from the list
     *
     * @param memBuf MemoryBuffer
     */
    public synchronized final void removeSegment( MemoryBuffer memBuf) {
        m_buffers.remove( memBuf);
    }

    /**
     * Remove a segment from the list
     *
     * @param idx int
     * @return MemoryBuffer
     */
    public synchronized final MemoryBuffer removeSegmentAt(int idx) { return m_buffers.remove( idx); }

    /**
     * Remove segments that contain data before the specified file offset, this does not remove segments that are
     * marked as written
     *
     * @param fileOff long
     * @return int Number of segments removed
     */
    public synchronized final int removeSegmentsBefore( long fileOff) {

        //	Check if the list is empty
        if (m_buffers == null || m_buffers.size() == 0)
            return 0;

        //	Find the memory segment for the required data
        int segCnt = 0;
        int segIdx = numberOfSegments() - 1;

        while ( segIdx >= 0) {

            //	Get the current memory segment
            MemoryBuffer seg = m_buffers.get( segIdx);

            //	Check if this segment contains the required data
            if ((seg.getFileOffset() + seg.getUsedLength()) < fileOff && seg.hasWriteData() == false) {

                // Remove the segment
                m_buffers.remove( segIdx);

                // Update the count of segments removed
                segCnt++;
            }

            // Update the segment index
            segIdx--;
        }

        //	Return the count of segments removed
        return segCnt;
    }

    /**
     * Remove all out of sequence buffers
     *
     * @return int
     */
    public synchronized final int removeOutOfSequenceBuffers() {

        //	Check if the list is empty
        if (m_buffers == null || m_buffers.size() == 0)
            return 0;

        //	Find the memory segment for the required data
        int segCnt = 0;
        int segIdx = numberOfSegments() - 1;

        while ( segIdx >= 0) {

            //	Get the current memory segment
            MemoryBuffer seg = m_buffers.get( segIdx);

            //	Check if this segment contains out of sequence data
            if ( seg.isOutOfSequence()) {

                // Remove the segment
                m_buffers.remove( segIdx);

                // Update the count of segments removed
                segCnt++;
            }

            // Update the segment index
            segIdx--;
        }

        //	Return the count of segments removed
        return segCnt;
    }

    /**
     * Clear the buffer list
     */
    public synchronized final void clearSegments() {
        m_buffers.clear();
    }

    /**
     * Find the segment that contains the required data for reading
     *
     * @param fileOff long
     * @param len     int
     * @return MemoryBuffer
     */
    public synchronized final MemoryBuffer findSegment(long fileOff, int len) {

        //	Check if the list is empty
        if (m_buffers == null || m_buffers.size() == 0)
            return null;

        //	Find the memory segment for the required data
        for (int i = 0; i < numberOfSegments(); i++) {

            //	Get the current memory segment
            MemoryBuffer seg = m_buffers.get(i);

            //	Check if this segment contains the required data
            if (seg.containsData(fileOff, len) != MemoryBuffer.Contains.None)
                return seg;
        }

        //	No segments have the required data
        return null;
    }

    /**
     * Find the segment that contains the required data section for writing
     *
     * @param fileOff long
     * @return MemoryBuffer
     */
    public synchronized final MemoryBuffer findSegment(long fileOff) {

        //	Check if the list is empty
        if (m_buffers == null || m_buffers.size() == 0)
            return null;

        //	Find the memory segment for the required data
        for (int i = 0; i < numberOfSegments(); i++) {

            //	Get the current memory segment
            MemoryBuffer seg = m_buffers.get(i);

            //	Check if this segment contains the required data section
            if ( fileOff >= seg.getFileOffset() && fileOff < (seg.getFileOffset() + seg.getBufferSize()))
                return seg;
        }

        //	No segments have the required data
        return null;
    }

    /**
     * Check if the buffer list has any buffers that have been written to
     *
     * @return boolean
     */
    public synchronized final boolean hasUpdatedBuffers() {

        //	Check if the list is empty
        if (m_buffers == null || m_buffers.size() == 0)
            return false;

        //	Check for any buffers that have been updated
        for (int i = 0; i < numberOfSegments(); i++) {

            //	Get the current memory segment
            MemoryBuffer seg = m_buffers.get(i);

            //	Check if this segment has been updated
            if ( seg.hasWriteData())
                return true;
        }

        //	No segments have been updated
        return false;
    }

    /**
     * Return the memory buffer list details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[MemList:cnt=");
        str.append(m_buffers.size());
        str.append("-");

        synchronized ( this) {
            if ( m_buffers != null && m_buffers.size() > 0) {
                for (MemoryBuffer buf : m_buffers) {
                    str.append(buf);
                    str.append(",");
                }
            }
        }

        str.append("]");

        return str.toString();
    }
}
