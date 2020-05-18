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

import java.io.IOException;

/**
 * Memory Segment Info Class
 *
 * <p>Contains the data and details of a file data segment that is held in memory, and may be shared by many users/sessions.
 *
 * @author gkspencer
 */
public class InMemorySegmentInfo extends MemorySegmentInfo {

    // File data bytes
    private volatile byte[] m_data;
    private volatile int m_usedLen;

    // Data has been updated
    private boolean m_updated;

    /**
     * Default constructor
     */
    public InMemorySegmentInfo() {

        // Indicate that this is all of the file data
        setAllFileData( true);
    }

    /**
     * Class constructor
     *
     * @param bufSize int
     */
    public InMemorySegmentInfo(int bufSize) {
        m_data = new byte[bufSize];

        // Indicate that this is all of the file data
        setAllFileData( true);
    }

    /**
     * Class constructor
     *
     * @param dataByts byte[]
     */
    public InMemorySegmentInfo(byte[] dataByts) {
        m_data = dataByts;
        m_usedLen = m_data.length;

        // Indicate that this is all of the file data
        setAllFileData( true);
    }

    /**
     * Class constructor
     *
     * @param dataByts byte[]
     * @param allData boolean
     */
    public InMemorySegmentInfo(byte[] dataByts, boolean allData) {
        m_data = dataByts;
        m_usedLen = m_data.length;

        setAllFileData( allData);
    }

    /**
     * Return the file length
     *
     * @return long
     */
    public long getFileLength() {
        return m_usedLen;
    }

    /**
     * Return the memory file buffer length
     *
     * @return long
     * @throws IOException Failed to get the file length
     */
    public long getBufferLength()
        throws IOException {

        //	Get the file data length
        if ( m_data != null)
            return m_data.length;
        return 0;
    }

    /**
     * Get the memory buffer holding the file data
     *
     * @return byte[]
     */
    public final byte[] getFileData() {
        return m_data;
    }

    /**
     * Get the used buffer length
     *
     * @return int
     */
    public final int getUsedLength() {
        return m_usedLen;
    }

    /**
     * Set the used buffer length
     *
     * @param usedLen int
     */
    public final void setUsedLength(int usedLen) {
        m_usedLen = usedLen;
    }

    /**
     * Add loaded data to the available data, this may be the whole file data or a section of the file
     *
     * @param fileData MemoryBuffer
     */
    public void addFileData( MemoryBuffer fileData) {

        // Set the file data
        m_data = fileData.getData();
        m_usedLen = fileData.getUsedLength();

        // Update the data status
        setStatus( State.Available);
    }

    /**
     * Check if the file data status for the specified file segment
     *
     * @param fileOff long
     * @param len int
     * @return LoadableStatus
     */
    public LoadableStatus hasDataFor( long fileOff, int len) {

        // Check if there is any file data loaded
        if ( hasStatus() == State.Initial)
            return LoadableStatus.Loadable;

        //	Check if the memory segment has enough data for the request
        if (len > getUsedLength())
            return LoadableStatus.NotAvailable;

        //	Check if the memory segment contains the required data
        long endOff = fileOff + len;
        long dataEnd = getUsedLength();

        if (fileOff < dataEnd && endOff <= dataEnd)
            return LoadableStatus.Available;

        //	Data not in this segment
        return LoadableStatus.NotAvailable;
    }

    /**
     * File has been closed
     *
     * @return boolean true if there is updated data in the buffer
     */
    public boolean closeFile() {
        return m_updated;
    }

    /**
     * Read a block of data from the segment file
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @return Length of data read.
     * @throws IOException Failed to read the file
     */
    public synchronized final int readBytes(byte[] buf, int len, int pos, long fileOff)
            throws IOException {

        // Check if the read can be satisfied from the current buffer with the file offset taken into account
        if ( hasDataFor( fileOff, len) != LoadableStatus.Available)
            throw new IOException("Read error, not within current buffer");

        // Check if the length is passed the current readable length, the file data may still be loading
        int endOff = (int) (fileOff + len);

        if ( endOff > getUsedLength()) {

            // Adjust the read length
            len = getUsedLength();
        }

        // Copy the data into the user buffer
        System.arraycopy( m_data, (int) fileOff, buf, pos, len);

        // Return the count of bytes read
        return len;
    }

    /**
     * Write a block of data to the segment file
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @return MemoryStorableFile.SaveableStatus
     * @throws IOException Failed to write the file
     */
    public synchronized final SaveableStatus writeBytes(byte[] buf, int len, int pos, long fileOff)
            throws IOException {

        // Make sure the write fits into the current buffer with the file offset taken into account
        int endPos = (int) ( fileOff + len);

        // Check if the buffer is allocated
        if ( m_data == null)
            m_data = new byte[endPos];
        else if ( endPos > m_data.length)
            return SaveableStatus.BufferOverflow;

        // Copy the data into the memory buffer
        System.arraycopy( buf, pos, m_data, (int) fileOff, len);

        // Indicate buffered data has been updated
        m_updated = true;

        // Check if the file length has been extended
        if ( endPos > m_usedLen) {
            setUsedLength(endPos);
            setReadableLength(endPos);
        }

        return SaveableStatus.Buffering;
    }

    /**
     * Truncate, or extend, the memory file to the specified size
     *
     * @param siz long
     * @exception IOException Failed to truncate the file
     */
    public final void truncate(long siz)
            throws IOException {

        // If truncating to zero length we will keep hold of the current buffer
        if ( siz == 0) {

            // Reset the user length
            setUsedLength( 0);
        }
        else if ( m_data != null && siz > m_data.length) {

            // Need to allocate a larger buffer, and copy the existing data to it
            byte[] newBuf = new byte[(int) siz];

            if ( getUsedLength() > 0)
                System.arraycopy( m_data, 0, newBuf, 0, getUsedLength());

            m_data = newBuf;
        }

        // Indicate that the buffered data has been updated
        m_updated = true;
    }

    /**
     * Return the details of a section of file data to be saved to the store
     *
     * @return MemoryBuffer
     */
    public MemoryBuffer dataToSave() {

        // Check if the buffered data has been updated
        if ( m_updated)
            return new MemoryBuffer( m_data, 0L, m_usedLen);
        return null;
    }

    /**
     * Indicate that the specified memory buffer has been saved
     *
     * @param memBuf MemoryBuffer
     */
    public void dataSaved( MemoryBuffer memBuf) {

        // Clear the updated flag, no more data to be saved
        m_updated = false;
    }

    /**
     * Wait for a writeable buffer to become available to continue a write request
     *
     * @param tmo long
     */
    public void waitForWriteBuffer(long tmo) {

        // Do not need to wait for a buffer
    }

    /**
     * Return the file segment details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[InMemory:");
        str.append(m_data);
        str.append(",used=");
        str.append(getUsedLength());
        str.append(":");
        str.append(hasStatus().name());
        str.append(",");

        if (isUpdated())
            str.append(",Updated");
        if (isQueued())
            str.append(",Queued");

        str.append("]");

        return str.toString();
    }
}
