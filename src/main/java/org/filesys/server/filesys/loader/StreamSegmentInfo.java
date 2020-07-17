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

import org.filesys.debug.Debug;
import org.filesys.util.MemorySize;

import java.io.IOException;
import java.util.EnumSet;

/**
 * Streamed Segment Info Class
 *
 *  <p>Buffers data in memory as it is read from or written to the back-end store. Used for larger files that cannot be
 *  held in memory.
 *
 * @author gkspencer
 */
public class StreamSegmentInfo extends MemorySegmentInfo {

    // Default number of buffer slots
    public static final int StreamBufferCount   = 4;

    // Enable debug output
    private static final boolean m_debug = false;

    // List of memory buffers that hold the current file data being read from or written to the back-end store
    private volatile MemoryBufferList m_rxBuffers;
    private volatile MemoryBufferList m_txBuffers;

    // List of memory buffers that hold out of sequence read data
    private volatile MemoryBufferList m_outOfSeqBuffers;

    // File length, may be the current written length
    private long m_fileLen;

    // Last file read offset
    private volatile long m_lastReadOffset;

    // Next write offset and allocation offset
    private volatile long m_nextWriteOffset;
    private volatile long m_nextAllocOffset;

    // Buffer size to allocate for each section
    private int m_bufferSize = (int) (2 * MemorySize.MEGABYTE);

    // Maximum number of buffers to use for streaming this file
    private int m_maxBuffers = StreamBufferCount;

    /**
     * Default constructor
     */
    public StreamSegmentInfo() {
        super( EnumSet.of( Flags.Streamed));

        // Allocate the read/write buffer lists
        m_rxBuffers = new MemoryBufferList( StreamBufferCount);
        m_txBuffers = new MemoryBufferList( StreamBufferCount);
    }

    /**
     * Class constructor
     *
     * @param bufSize int
     */
    public StreamSegmentInfo(int bufSize) {
        super( EnumSet.of( Flags.Streamed));

        // Allocate the read/write buffer lists
        m_rxBuffers = new MemoryBufferList( StreamBufferCount);
        m_txBuffers = new MemoryBufferList( StreamBufferCount);

        // Set the buffer size to allocate for sections
        m_bufferSize = bufSize;
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() { return m_debug; }

    /**
     * Return the count of read memory buffers
     *
     * @return int
     */
    public synchronized final int getRxBufferCount() { return m_rxBuffers != null ? m_rxBuffers.numberOfSegments() : 0; }

    /**
     * Return the count of write memory buffers
     *
     * @return int
     */
    public synchronized final int getTxBufferCount() { return m_txBuffers != null ? m_txBuffers.numberOfSegments() : 0; }

    /**
     * Return the read buffer list
     *
     * @return MemoryBufferList
     */
    public final MemoryBufferList getRxBufferList() { return m_rxBuffers; }

    /**
     * Return the write buffer list
     *
     * @return MemoryBufferList
     */
    public final MemoryBufferList getTxBufferList() { return m_txBuffers; }

    /**
     * Return the next write offset
     *
     * @return long
     */
    public final long getNextWriteOffset() { return m_nextWriteOffset; }

    /**
     * Check if the buffer list has spare read buffer slots available
     *
     * @return boolean
     */
    public synchronized final boolean hasFreeRxBufferSlots() {
        if ( m_rxBuffers == null)
            return true;
        return m_rxBuffers.numberOfSegments() < m_maxBuffers;
    }

    /**
     * Check if the buffer list has spare write buffer slots available
     *
     * @return boolean
     */
    public synchronized final boolean hasFreeTxBufferSlots() {
        if ( m_txBuffers == null)
            return true;
        return m_txBuffers.numberOfSegments() < m_maxBuffers;
    }

    /**
     * Return the file length
     *
     * @return long
     */
    public long getFileLength() {
        return m_fileLen;
    }

    /**
     * Set the file length
     *
     * @param fileLen long
     */
    public final void setFileLength(long fileLen) { m_fileLen = fileLen; }

    /**
     * Get the buffer size being used for file data sections
     *
     * @return int
     */
    public final int getBufferSize() { return m_bufferSize; }

    /**
     * Set the buffer size used for file data sections
     *
     * @param bufSize int
     */
    public final void setBufferSize(int bufSize) { m_bufferSize = bufSize; }

    /**
     * Check if there is buffered out of sequence data
     *
     * @return boolean
     */
    protected final boolean hasOutOfSequenceData() {
        return m_outOfSeqBuffers != null && m_outOfSeqBuffers.numberOfSegments() > 0;
    }

    /**
     * Get the memory buffer holding the file data
     *
     * @param fileOffset long
     * @param len int
     * @return MemoryBuffer
     */
    public final MemoryBuffer getFileData( long fileOffset, int len) {
        return m_rxBuffers.findSegment( fileOffset, len);
    }

    /**
     * Remove the specified memory buffer from the list
     *
     * @param memBuf MemoryBuffer
     */
    public final void removeFileData( MemoryBuffer memBuf) {
        m_rxBuffers.removeSegment( memBuf);
    }

    /**
     * Check the file data status for the specified file segment
     *
     * @param fileOff long
     * @param len int
     * @return LoadableStatus
     */
    public LoadableStatus hasDataFor( long fileOff, int len) {

        // DEBUG
        if ( hasDebug())
            Debug.println("StreamSegmentInfo: hasDataFor fileOff=" + fileOff + ", len=" + len);

        // Check if there are any sections loaded
        if ( m_rxBuffers.numberOfSegments() == 0 && m_fileLen > 0L) {

            // Check if this is a sequential read of the file or out of sequence
            //
            // Large read at the start of the file
            if (fileOff == 0L && len > getShortReadSize())
                return LoadableStatus.Loadable;

            // Short read or read passed the end of the next buffer to be loaded
            else if (len <= getShortReadSize() || fileOff > (m_lastReadOffset + getBufferSize()))
                return LoadableStatus.LoadableOutOfSeq;

            // Start, or continue, sequential data loading
            else
                return LoadableStatus.Loadable;
        }

        // Check if the required data is already loaded
        LoadableStatus dataSts = LoadableStatus.NotAvailable;
        int idx = 0;

        while ( idx < m_rxBuffers.numberOfSegments() && dataSts != LoadableStatus.Available) {

            // Check the current memory buffer
            MemoryBuffer curBuf = m_rxBuffers.getSegmentAt( idx);
            MemoryBuffer.Contains contains = curBuf.containsData( fileOff, len);

            if ( contains == MemoryBuffer.Contains.All) {

                // Required data is loaded
                dataSts = LoadableStatus.Available;
            }
            else if ( contains == MemoryBuffer.Contains.Partial) {

                // Update the file offset and length for the remaining data
                long newOff = curBuf.getFileOffset() + curBuf.getUsedLength();
                len -= (int) (newOff - fileOff);
                fileOff = newOff;

                // Change the status to loadable, if we do not have the remaining data in memory it will need to be
                // loaded
                dataSts = LoadableStatus.Loadable;
            }

            // Update the buffer index
            idx++;
        }

        // If we did not find a load segment then check if the read is within the file data range, it may be an out
        // of sequence read
        if ( dataSts == LoadableStatus.NotAvailable) {

            // Check if the required data is within the files available data
            if ( fileOff < getFileLength()) {

                long endOff = fileOff + len;
                if ( endOff <= getFileLength()) {

                    // Check if the required data is in the out of sequence buffer list
                    if ( hasOutOfSequenceData()) {

                        idx = 0;
                        while ( idx < m_outOfSeqBuffers.numberOfSegments() && dataSts != LoadableStatus.Available) {

                            // Check the current memory buffer
                            MemoryBuffer curBuf = m_outOfSeqBuffers.getSegmentAt(idx);
                            MemoryBuffer.Contains contains = curBuf.containsData(fileOff, len);

                            if (contains == MemoryBuffer.Contains.All) {

                                // Required data is loaded
                                dataSts = LoadableStatus.Available;
                            }

                            // Update the out of sequence buffer index
                            idx++;
                        }
                    }

                    // Required data can be loaded
                    if ( dataSts != LoadableStatus.Available)
                        dataSts = LoadableStatus.LoadableOutOfSeq;
                }
            }
        }

        // Check if there is data currently queued for loading and the status indicates the required data is loadable,
        // change the status to indicate there is a load in progress
        if ( dataSts == LoadableStatus.Loadable && isQueued()) {
            dataSts = LoadableStatus.Loading;

            // DEBUG
            if (hasDebug())
                Debug.println("StreamSegmentInfo: Loading seg=" + this);
        }

        // DEBUG
        if ( hasDebug())
            Debug.println("StreamSegmentInfo: hasDataFor sts=" + dataSts.name());

        // Return the status
        return dataSts;
    }

    /**
     * Add loaded data to the available data, this may be the whole file data or a section of the file
     *
     * @param fileData MemoryBuffer
     */
    public void addFileData( MemoryBuffer fileData) {

        // Check if the file data segment is already in the buffer list
        if ( hasDataFor( fileData.getFileOffset(), fileData.getUsedLength()) == LoadableStatus.Available) {

            // DEBUG
            if ( hasDebug())
                Debug.println("StreamSegmentInfo: Already loaded data for buf=" + fileData);
            return;
        }

        // Add the file data segment to the current buffer list
        if ( fileData.isOutOfSequence() == false) {

            // Add the buffer to the in sequence buffer list
            m_rxBuffers.addSegment(fileData);
        }
        else {

            // Add the buffer to the out of sequence buffer list
            if ( m_outOfSeqBuffers == null)
                m_outOfSeqBuffers = new MemoryBufferList();
            m_outOfSeqBuffers.addSegment(fileData);
        }

        // Update the file length, only if greater than the current length
        long newLen = fileData.getFileOffset() + fileData.getUsedLength();

        if ( newLen > m_fileLen)
            m_fileLen = newLen;

        // DEBUG
        if( hasDebug())
            Debug.println("StreamSegmentInfo: addFileData fileData=" + fileData + ", fileLen=" + m_fileLen +
                    ", buffers=" + m_rxBuffers.numberOfSegments());
    }

    /**
     * Read a block of data from the in memory file
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @return Length of data read.
     * @throws IOException Failed to read the file
     */
    public int readBytes(byte[] buf, int len, int pos, long fileOff)
        throws IOException {

        // DEBUG
        if ( hasDebug())
            Debug.println("StreamSegmentInfo: readBytes fileOff=" + fileOff + ", len=" + len);

        // Find the memory segment with the required data
        MemoryBuffer readBuf = m_rxBuffers.findSegment( fileOff, len);
        if ( readBuf == null) {

            // Check if there are any out of sequence buffers
            if ( m_outOfSeqBuffers != null)
                readBuf = m_outOfSeqBuffers.findSegment( fileOff, len);

            if ( readBuf == null)
                return 0;
        }

        // Copy data to the user buffer
        int rdlen = len;

        if ( readBuf.containsData( fileOff, len) == MemoryBuffer.Contains.All) {

            // Read the data from the buffer
            rdlen = readBuf.readBytes( buf, len, pos, fileOff);

            // Check if this buffer is an out of sequence buffer that matches the read offset and length, if so then
            // remove the buffer from the list, keep short out of sequence reads cached
            if ( readBuf.isOutOfSequence()  && len > getShortReadSize() &&
                    readBuf.getFileOffset() == fileOff && readBuf.getUsedLength() == len) {

                // Remove the out of sequence buffer from the list
                m_outOfSeqBuffers.removeSegment( readBuf);

                // DEBUG
                if ( hasDebug())
                    Debug.println("StreamSegmentInfo: Removed out of sequence buffer=" + readBuf);
            }
        }
        else {

            // Buffer contains some of the required data
            //
            // Read the first section of data
            rdlen = readBuf.readBytes(buf, len, pos, fileOff);

            // Adjust the remaining offset and length
            fileOff += rdlen;
            len -= rdlen;
            pos += rdlen;

            // Find the next data buffer
            readBuf = m_rxBuffers.findSegment(fileOff, len);

            if (readBuf != null) {

                // Copy the remaining data to the user buffer
                rdlen += readBuf.readBytes(buf, len, pos, fileOff);
            }
        }

        // Save the read offset
        m_lastReadOffset = fileOff;

        // Remove any segments that have been read
        int segCnt = m_rxBuffers.removeSegmentsBefore( m_lastReadOffset);

        // DEBUG
        if ( hasDebug())
            Debug.println("StreamSegmentInfo: readBytes len=" + len + ", rdlen=" + rdlen + ", removed=" + segCnt +
                    ", buffers=" + m_rxBuffers.numberOfSegments());

        // If we have read to the end of file then reset the read offset, and remove all buffers
        if (( m_lastReadOffset + rdlen) >= getFileLength()) {

            // Remove all buffered data
            m_rxBuffers.clearSegments();

            // Reset the read offset back to the start of the file
            m_lastReadOffset = 0;

            // DEBUG
            if ( hasDebug())
                Debug.println("StreamSegmentInfo: Read to end of file, reset buffers/read offset");
        }

        // Return the length of data read
        return rdlen;
    }

    /**
     * File closed by the client
     *
     * @return boolean true if there is buffered data
     */
    public boolean closeFile() {

        // Mark the file segment as closed
        setFileClosed( true);

        // Check if there are any buffers that need to be saved
        return m_txBuffers.hasUpdatedBuffers();
    }

    /**
     * Write a block of data to the segment file
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @return MemorySaveableFile.SaveableStatus
     * @throws IOException Failed to write the file
     */
    public SaveableStatus writeBytes(byte[] buf, int len, int pos, long fileOff)
        throws IOException {

        // DEBUG
        if ( hasDebug())
            Debug.println("StreamSegmentInfo: writeBytes fileOff=" + fileOff + ", len=" + len);

        // Make sure the write is in sequence, we may have already saved the buffer the write is trying to write into
        if ( fileOff < m_nextWriteOffset)
            throw new IOException("Out of sequence write to streamed file");

        // Find a current segment that the data can be written to
        SaveableStatus wrSts = SaveableStatus.Buffering;
        MemoryBuffer writeBuf = m_txBuffers.findSegment( fileOff);

        if ( writeBuf == null) {

            synchronized ( this) {

                // Check if the maximum number of buffers has been allocated, if so then return an error status
                if (m_txBuffers.numberOfSegments() >= m_maxBuffers)
                    return SaveableStatus.MaxBuffers;

                // Need to allocate a new buffer for the write
                writeBuf = new MemoryBuffer(new byte[getBufferSize()], nextBufferOffset(), 0);
                m_txBuffers.addSegment(writeBuf);
            }

            // DEBUG
            if ( hasDebug())
                Debug.println("StreamSegmentInfo: Add new buffer for write, buf=" + writeBuf +
                        ", buffers=" + m_txBuffers.numberOfSegments());
        }

        // Should have a valid buffer now
        if ( writeBuf != null) {

            // DEBUG
            if( hasDebug())
                Debug.println("StreamSegmentInfo: Write to buffer " + writeBuf);

            // If we have the maximum number of buffers allocated then make sure the write will fit into the current
            // buffer without needing to allocate another buffer
            if ( m_txBuffers.numberOfSegments() == m_maxBuffers && writeBuf.canFitData( fileOff, len) != MemoryBuffer.Contains.All)
                return SaveableStatus.MaxBuffers;

            // Write the data into the current buffer
            int wrlen = writeBuf.writeBytes( buf, len, pos, fileOff);

            if ( wrlen < len) {

                // Update the file offset to write to another buffer
                fileOff += wrlen;
                len -= wrlen;
                pos += wrlen;

                // Indicate that there is a buffer ready to be saved
                wrSts = SaveableStatus.Saveable;

                // Get the next buffer to write to, or create a new buffer
                writeBuf = m_txBuffers.findSegment( fileOff);

                if ( writeBuf == null) {

                    // Create a new buffer and to the buffer list
                    byte[] byts = new byte[ getBufferSize()];
                    writeBuf = new MemoryBuffer( byts, nextBufferOffset(), 0);

                    m_txBuffers.addSegment( writeBuf);

                    // DEBUG
                    if ( hasDebug())
                        Debug.println("StreamSegmentInfo: Add new buffer for second write, buf=" + writeBuf +
                                ", buffers=" + m_txBuffers.numberOfSegments());
                }

                // Write the remaining data
                wrlen += writeBuf.writeBytes( buf, len, pos, fileOff);
            }
            else if ( writeBuf.isFull()) {

                // We have a buffer ready to be saved
                wrSts = SaveableStatus.Saveable;
            }
        }
        else {

            // DEBUG
            if ( hasDebug())
                Debug.println("StreamSegmentInfo: No buffer for write, fileOff=" + fileOff + ", len=" + len);
        }

        // DEBUG
        if ( hasDebug())
            Debug.println("StreamSegmentInfo: writeBytes sts=" + wrSts.name());

        // Update the file length
        long fileLen = fileOff + len;

        if ( fileLen > m_fileLen)
            m_fileLen = fileLen;

        // Return the buffer status
        return wrSts;
    }

    /**
     * Truncate, or extend, the memory file to the specified size
     *
     * @param siz long
     * @exception IOException Failed to truncate the file
     */
    public void truncate(long siz)
        throws IOException {

        // DEBUG
        if ( hasDebug())
            Debug.println("StreamSegmentInfo: truncate siz=" + siz);

        // For truncate to zero length remove all buffers and update the file length
        if ( siz == 0) {

            // Remove all buffers
            m_rxBuffers.clearSegments();
            m_txBuffers.clearSegments();

            // Set the file length to zero
            m_fileLen = 0L;
        }
        else if ( siz < m_fileLen) {

            // Update the new file length
            m_fileLen = siz;

            // Remove all write buffers that are beyond the new end of file
            int idx = m_txBuffers.numberOfSegments() - 1;

            while ( idx >= 0) {

                // Check if the current segment is above the new end of file offset, or contains the new file
                // offset
                MemoryBuffer curBuf = m_txBuffers.getSegmentAt( idx);

                if ( curBuf.getFileOffset() >= m_fileLen) {

                    // Remove the segment
                    m_txBuffers.removeSegment(curBuf);
                }
                else if ( curBuf.containsData( m_fileLen, 0) == MemoryBuffer.Contains.All) {

                    // Trim the segment to the new end of file length
                    curBuf.setUsedLength((int) (m_fileLen - curBuf.getFileOffset()));
                }

                // Update the buffer index
                idx--;
            }
        }
    }

    /**
     * Return the details of a section of file data to be saved to the store
     *
     * @return MemoryBuffer
     */
    public MemoryBuffer dataToSave() {

        // Check if there is a full data buffer to be saved
        if ( m_txBuffers.numberOfSegments() == 0) {

            // DEBUG
            if (hasDebug())
                Debug.println("StreamSegmentInfo: dataToSave(), no buffers");

            return null;
        }

        MemoryBuffer firstBuf = m_txBuffers.getSegmentAt( 0);

        if ( firstBuf != null && (firstBuf.isFull() || isClosed()) && firstBuf.getFileOffset() == m_nextWriteOffset) {

            // DEBUG
            if ( hasDebug())
                Debug.println("StreamSegmentInfo: Data to save buf=" + firstBuf + ", isClosed=" + isClosed());

            return firstBuf;
        }

        // DEBUG
        if ( m_txBuffers.numberOfSegments() > 0 && hasDebug()) {
            Debug.println("StreamSegmentInfo: dataToSave(), no data, nextWrite=" + getNextWriteOffset() + ", firstBuf=" + firstBuf);
            Debug.println("StreamSegmentInfo: buffers=" + m_txBuffers);
        }

        return null;
    }

    /**
     * Indicate that the specified memory buffer has been saved
     *
     * @param memBuf MemoryBuffer
     */
    public void dataSaved( MemoryBuffer memBuf) {

        // Remove the memory buffer from the buffer list, should be the first element
        MemoryBuffer remBuf = m_txBuffers.removeSegmentAt( 0);

        if ( remBuf != null && remBuf.getFileOffset() == memBuf.getFileOffset()) {

            // Update the next write offset to write the file in sequence
            m_nextWriteOffset += memBuf.getUsedLength();

            // Signal that there is a writeable buffer slot available
            signalWriteBufferAvailable();

            // DEBUG
            if ( hasDebug())
                Debug.println("StreamSegmentInfo: dataSaved nextOffset=" + m_nextWriteOffset);
        }
        else {

            // DEBUG
            if ( hasDebug()) {
                Debug.println("StreamSegmentInfo: ** Removed wrong segment from the list head");
                Debug.println("  buffers=" + m_txBuffers);
            }
        }
    }

    /**
     * Wait for a writeable buffer to become available to continue a write request
     *
     * @param tmo long
     */
    public void waitForWriteBuffer(long tmo) {

        //	Check if the file data has been loaded, if not then wait
        if ( m_txBuffers.numberOfSegments() >= m_maxBuffers) {
            synchronized (this) {
                try {

                    //	Wait for a write buffer to become available
                    wait(tmo);
                }
                catch (InterruptedException ex) {
                }
            }
        }
    }

    /**
     * Signal that a write buffer slot is available, any threads using the waitForWriteBuffer() method
     * will return so that the threads can continue writing data
     */
    public final synchronized void signalWriteBufferAvailable() {

        //	Notify any waiting threads that a write buffer slot is available
        notifyAll();
    }

    /**
     * Return the next file offset for the next file segment buffer, and update the offset
     *
     * @return long
     */
    protected synchronized final long nextBufferOffset() {

        long nextOff = m_nextAllocOffset;
        m_nextAllocOffset += getBufferSize();

        return nextOff;
    }

    /**
     * Check if the file data is available
     *
     * @param fileOff long
     * @param len int
     * @return boolean
     */
    public boolean isDataAvailable( long fileOff, int len) {
        if ( hasDataFor( fileOff, len) == LoadableStatus.Available)
            return true;
        return false;
    }

    /**
     * Reset the segment save state
     */
    public final void resetTxState() {

        // Remove any save buffers
        m_txBuffers.clearSegments();

        // Reset the save offsets
        m_nextWriteOffset = 0L;
        m_nextAllocOffset = 0L;

        // Reset save flags
        setFlag( Flags.DeleteOnSave, false);
        setFlag( Flags.WriteError, false);
        setFlag( Flags.Updated, false);
        setFlag( Flags.FileClosed, false);
    }

    /**
     * Reset the segment load state
     */
    public final void resetRxState() {

        // Remove any read buffers
        m_rxBuffers.clearSegments();
        m_outOfSeqBuffers.clearSegments();

        // Reset the load offset
        m_lastReadOffset = 0L;

        // Reset flags
        setFlag( Flags.ReadError, false);
        setFlag( Flags.FileClosed, false);
    }

    /**
     * Return the file segment details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Stream:rxbufs=");
        str.append(m_rxBuffers);
        str.append(",txbufs=");
        str.append(m_txBuffers);
        str.append(",len=");
        str.append(getFileLength());
        str.append(":");
        str.append(hasStatus().name());
        str.append(",");

        if (isUpdated())
            str.append(",Updated");
        if (isQueued())
            str.append(",Queued");

        if ( m_nextAllocOffset != 0) {
            str.append(",nextAlloc=");
            str.append(m_nextAllocOffset);
        }

        if ( m_nextWriteOffset != 0) {
            str.append(",nextWrite=");
            str.append(m_nextWriteOffset);
        }

        str.append("]");

        return str.toString();
    }
}
