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

import java.util.EnumSet;

/**
 * Memory Buffer Class
 *
 * <p>Holds a section of a large file as it is read from or written to the backend store</p>
 *
 * @author gkspencer
 */
public class MemoryBuffer {

    // Memory buffer flags
    public enum Flags {
        Written,        // memory buffer has been written to
        OutOfSeq        // buffer is an out of sequence read/write
    }

    // File data bytes, offset within the file
    private byte[] m_data;
    private long m_fileOffset;

    // Length of the used data within the buffer
    private volatile int m_usedLen;

    // Buffer flags
    private EnumSet<Flags> m_flags = EnumSet.noneOf( Flags.class);

    // Contains check status codes
    public enum Contains {
        All,        // contains all the required file data
        Partial,    // contains some of the required file data
        None        // does not contain required file data
    }

    /**
     * Default constructor
     */
    public MemoryBuffer() {
    }

    /**
     * Class constructor
     *
     * @param byts byte[]
     * @param fileOff long
     */
    public MemoryBuffer( byte[] byts, long fileOff) {
        m_data = byts;
        m_fileOffset = fileOff;

        m_usedLen = m_data.length;
    }

    /**
     * Class constructor
     *
     * @param byts byte[]
     * @param fileOff long
     * @param usedLen int
     */
    public MemoryBuffer( byte[] byts, long fileOff, int usedLen) {
        m_data = byts;
        m_fileOffset = fileOff;

        m_usedLen = usedLen;
    }

    /**
     * Return the file data buffer
     *
     * @return byte[]
     */
    public final byte[] getData() { return m_data; }

    /**
     * Return the offset of this data within the file
     *
     * @return long
     */
    public final long getFileOffset() { return m_fileOffset; }

    /**
     * Return the used data length within the file data buffer
     *
     * @return int
     */
    public final int getUsedLength() { return m_usedLen; }

    /**
     * Return the allocated buffer size
     *
     * @return int
     */
    public final int getBufferSize() { return m_data != null ? m_data.length : 0; }

    /**
     * Check if the memory buffer is full
     *
     * @return boolean
     */
    public final boolean isFull() {
        return m_data != null && m_data.length == m_usedLen;
    }

    /**
     * Check if the buffer has been written to
     *
     * @return boolean
     */
    public final boolean hasWriteData() { return m_flags.contains( Flags.Written); }

    /**
     * Check if the buffer is an out of sequence read/write
     *
     * @return boolean
     */
    public final boolean isOutOfSequence() { return m_flags.contains( Flags.OutOfSeq); }

    /**
     * Set the file offset for this buffer
     *
     * @param fileOff long
     */
    public final void setFileOffset(long fileOff) {
        m_fileOffset = fileOff;
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
     * Mark the buffer as written to, or truncated
     *
     * @param writtenTo boolean
     */
    public final void setWrittenTo( boolean writtenTo) {
        if ( writtenTo)
            m_flags.add( Flags.Written);
        else
            m_flags.remove( Flags.Written);
    }

    /**
     * Set the out of sequence flag
     *
     * @param outOfSeq boolean
     */
    public final void setOutOfSequence(boolean outOfSeq) {
        if ( outOfSeq)
            m_flags.add( Flags.OutOfSeq);
        else
            m_flags.remove( Flags.OutOfSeq);
    }

    /**
     * Check if this memory buffer contains the required file data, or part of the required data
     *
     * @param fileOff long
     * @param dataLen int
     * @return MemoryBuffer.Contains
     */
    public final MemoryBuffer.Contains containsData(long fileOff, int dataLen) {

        //	Check if the memory segment contains the required data
        long dataEndOff = fileOff + dataLen;
        long endOff = getFileOffset() + getUsedLength();

        if (fileOff >= getFileOffset() && fileOff < endOff) {

            // Check if the buffer contains all of the required data
            if (dataEndOff <= endOff)
                return Contains.All;
            else
                return Contains.Partial;
        }

        //	Data not in this segment
        return Contains.None;
    }

    /**
     * Check if this memory buffer can fit the specified data
     *
     * @param fileOff long
     * @param dataLen int
     * @return MemoryBuffer.Contains
     */
    public final MemoryBuffer.Contains canFitData(long fileOff, int dataLen) {

        //	Check if the memory segment can fit the required data
        long dataEndOff = fileOff + dataLen;
        long endOff = getFileOffset() + getBufferSize();

        if (fileOff >= getFileOffset() && fileOff < endOff) {

            // Check if the buffer can contain all of the required data
            if (dataEndOff <= endOff)
                return Contains.All;
            else
                return Contains.Partial;
        }

        //	Data does not fit into this segment
        return Contains.None;
    }

    /**
     * Read data from the buffer
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @return Length of data read.
     */
    public final int readBytes( byte[] buf, int len, int pos, long fileOff) {

        // Validate the file offset
        long endOffset = getFileOffset() + getUsedLength();

        if ( fileOff < getFileOffset() || fileOff > endOffset)
            return 0;

        // Adjust the file offset to get the buffer offset
        int bufOffset = (int) (fileOff - getFileOffset());

        // Check if we can read all the required data
        if (( fileOff + len) > endOffset)
            len = (int) (endOffset - fileOff);

        // Copy the data to the user buffer
        System.arraycopy( getData(), bufOffset, buf, pos, len);

        // Return the actual length of data copied
        return len;
    }

    /**
     * Write a block of data to the segment file
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @return int
     */
    public final int writeBytes( byte[] buf, int len, int pos, long fileOff) {

        // Validate the file offset
        long endOffset = getFileOffset() + getBufferSize();

        if ( fileOff < getFileOffset() || fileOff > endOffset)
            return 0;

        // Adjust the file offset to get the buffer offset
        int bufOffset = (int) (fileOff - getFileOffset());

        // Check if we can write all the required data
        if (( fileOff + len) > endOffset)
            len = (int) (endOffset - fileOff);

        // Copy the data from the user buffer
        System.arraycopy( buf, pos, getData(), bufOffset, len);

        // Update the used buffer length
        int usedLen = bufOffset + len;
        if ( usedLen > m_usedLen)
            m_usedLen = usedLen;

        // Indicate the buffer has been written to
        setWrittenTo( true);

        // Return the actual length of data copied
        return len;
    }

    /**
     * Return the memory buffer details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append( "[Data:");
        str.append( getData());
        str.append( ",fileOffset:");
        str.append( getFileOffset());

        if ( m_data != null) {
            str.append(",len=");
            str.append(m_data.length);

            if (getUsedLength() != m_data.length) {
                str.append(",used=");
                str.append(getUsedLength());
            }

            if ( isFull())
                str.append(",Full");

            if ( hasWriteData())
                str.append(",Write");

            if ( isOutOfSequence())
                str.append(",OutOfSeq");
        }

        str.append("]");

        return str.toString();
    }
}
