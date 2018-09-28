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

/**
 * Memory Segment Class
 *
 * <p>Contains an in-memory copy of file data.
 *
 * @author gkspencer
 */
public class MemorySegment {

    //	Data buffer and file offset
    private byte[] m_buffer;
    private long m_fileOff;

    //	Read count
    private int m_readCount;

    /**
     * Class constructor
     *
     * @param buf     byte[]
     * @param fileOff long
     */
    public MemorySegment(byte[] buf, long fileOff) {
        m_buffer = buf;
        m_fileOff = fileOff;
    }

    /**
     * Class constructor
     *
     * @param buf     byte[]
     * @param pos     int
     * @param len     int
     * @param fileOff long
     */
    public MemorySegment(byte[] buf, int pos, int len, long fileOff) {
        m_buffer = new byte[len];
        System.arraycopy(buf, pos, m_buffer, 0, len);
        m_fileOff = fileOff;
    }

    /**
     * Return the buffer
     *
     * @return byte[]
     */
    public final byte[] getBuffer() {
        return m_buffer;
    }

    /**
     * Return the buffer length
     *
     * @return int
     */
    public final int getLength() {
        return m_buffer.length;
    }

    /**
     * Return the file offset of the data
     *
     * @return long
     */
    public final long getFileOffset() {
        return m_fileOff;
    }

    /**
     * Return the memory segment read count
     *
     * @return int
     */
    public final int getReadCounter() {
        return m_readCount;
    }

    /**
     * Check if this segment contains the data for the specified request
     *
     * @param fileOff long
     * @param len     int
     * @return boolean
     */
    public final boolean containsData(long fileOff, int len) {

        //	Check if the memory segment has enough data for the request
        if (len > getLength())
            return false;

        //	Check if the memory segment contains the required data
        long endOff = fileOff + len;
        long dataEnd = getFileOffset() + getLength();

        if (fileOff >= getFileOffset() && endOff <= dataEnd)
            return true;

        //	Data not in this segment
        return false;
    }

    /**
     * Copy data to the specified user buffer
     *
     * @param buf     byte[]
     * @param pos     int
     * @param len     int
     * @param fileOff long
     */
    public final void copyBytes(byte[] buf, int pos, int len, long fileOff) {

        //	Update the file offset
        int bufOff = (int) (fileOff - getFileOffset());

        //	Copy data to the user buffer
        System.arraycopy(m_buffer, bufOff, buf, pos, len);

        //	Update the read count
        m_readCount++;
    }

    /**
     * Increment the read counter by the specified amount
     *
     * @param incr int
     */
    public final void incrementReadCounter(int incr) {
        m_readCount += incr;
    }

    /**
     * Decrement the read counter, if greater than zero
     *
     * @param decr int
     */
    protected final void decrementReadCounter(int decr) {
        if (m_readCount > decr)
            m_readCount -= decr;
        else
            m_readCount = 0;
    }

    /**
     * Decrement the read counter, if greater than zero
     */
    protected final void decrementReadCounter() {
        decrementReadCounter(1);
    }

    /**
     * Return the memory segment as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();
        str.append("[len=");
        str.append(getLength());
        str.append(",fileOff=");
        str.append(getFileOffset());
        str.append(",reads=");
        str.append(getReadCounter());
        str.append("]");

        return str.toString();
    }
}
