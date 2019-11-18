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

package org.filesys.util;

/**
 * Data Buffer Class
 *
 * <p>
 * Dynamic buffer for getting/setting data blocks.
 *
 * @author gkspencer
 */
public class DataBuffer {

    // Constants
    private static final int DefaultBufferSize = 256;

    // Data buffer, current position and offset
    private byte[] m_data;
    private int m_pos;
    private int m_endpos;
    private int m_offset;

    // Flag to indicate this is an external buffer, and cannot be extended
    private boolean m_external;

    // Long word alignment adjustment
    private int m_longAdjust;

    // Buffer alignment required, when packing various information structures
    private boolean m_alignBuffer = true;

    /**
     * Default constructor
     */
    public DataBuffer() {
        m_data = new byte[DefaultBufferSize];
        m_pos = 0;
        m_offset = 0;

        m_endpos = m_data.length;
    }

    /**
     * Create a data buffer to write data to
     *
     * @param siz int
     */
    public DataBuffer(int siz) {
        m_data = new byte[siz];
        m_pos = 0;
        m_offset = 0;

        m_endpos = m_data.length;
    }

    /**
     * Create a data buffer to read data from or write data to an external buffer
     *
     * @param buf byte[]
     * @param off int
     * @param len int
     */
    public DataBuffer(byte[] buf, int off, int len) {
        m_data = buf;
        m_offset = off;
        m_pos = off;
        m_endpos = off + len;

        // Indicate that this is an external buffer, do not try and extend it
        m_external = true;
    }

    /**
     * Create a data buffer to read data from or write data to an external buffer
     *
     * @param buf byte[]
     * @param off int
     * @param len int
     * @param alignAdjust int
     */
    public DataBuffer(byte[] buf, int off, int len, int alignAdjust) {
        m_data = buf;
        m_offset = off;
        m_pos = off;
        m_endpos = off + len;

        // Indicate that this is an external buffer, do not try and extend it
        m_external = true;

        // Set the longword alignment adjust
        m_longAdjust = alignAdjust;
    }

    /**
     * Create a data buffer to read data from or write data to an external buffer
     *
     * @param buf byte[]
     * @param off int
     * @param len int
     * @param alignRequired boolean
     */
    public DataBuffer(byte[] buf, int off, int len, boolean alignRequired) {
        m_data = buf;
        m_offset = off;
        m_pos = off;
        m_endpos = off + len;

        // Indicate that this is an external buffer, do not try and extend it
        m_external = true;

        // Indicate if buffer alignment is required
        m_alignBuffer = alignRequired;
    }

    /**
     * Return the data buffer
     *
     * @return byte[]
     */
    public final byte[] getBuffer() {
        return m_data;
    }

    /**
     * Check if buffer alignment is required, after packing a structure
     *
     * @return boolean
     */
    public final boolean hasAlignmentRequired() {
        return m_alignBuffer;
    }

    /**
     * Set the alignment required flag
     *
     * @param alignReq boolean
     */
    public final void setAlignmentRequired( boolean alignReq) {
        m_alignBuffer = alignReq;
    }

    /**
     * Return the data length
     *
     * @return int
     */
    public final int getLength() {
        if (m_endpos != 0)
            return m_endpos - m_offset;
        return m_pos - m_offset;
    }

    /**
     * Return the data length in words
     *
     * @return int
     */
    public final int getLengthInWords() {
        return getLength() / 2;
    }

    /**
     * Return the available data length
     *
     * @return int
     */
    public final int getAvailableLength() {
        if (m_endpos == 0)
            return -1;
        return m_endpos - m_pos;
    }

    /**
     * Return the used buffer length
     *
     * @return int
     */
    public final int getUsedLength() {
        return m_pos - m_offset;
    }

    /**
     * Return the displacement from the start of the buffer to the current buffer position
     *
     * @return int
     */
    public final int getDisplacement() {
        return m_pos - m_offset;
    }

    /**
     * Return the buffer base offset
     *
     * @return int
     */
    public final int getOffset() {
        return m_offset;
    }

    /**
     * Get a byte from the buffer
     *
     * @return int
     */
    public final int getByte() {

        // Check if there is enough data in the buffer
        if (m_data.length - m_pos < 1)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        // Unpack the byte value
        int bval = (int) (m_data[m_pos] & 0xFF);
        m_pos++;
        return bval;
    }

    /**
     * Get a short from the buffer
     *
     * @return int
     */
    public final int getShort() {

        // Check if there is enough data in the buffer
        if (m_data.length - m_pos < 2)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        // Unpack the integer value
        int sval = (int) DataPacker.getIntelShort(m_data, m_pos);
        m_pos += 2;
        return sval;
    }

    /**
     * Get an integer from the buffer
     *
     * @return int
     */
    public final int getInt() {

        // Check if there is enough data in the buffer
        if (m_data.length - m_pos < 4)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        // Unpack the integer value
        int ival = DataPacker.getIntelInt(m_data, m_pos);
        m_pos += 4;
        return ival;
    }

    /**
     * Get a long (64 bit) value from the buffer
     *
     * @return long
     */
    public final long getLong() {

        // Check if there is enough data in the buffer
        if (m_data.length - m_pos < 8)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        // Unpack the long value
        long lval = DataPacker.getIntelLong(m_data, m_pos);
        m_pos += 8;
        return lval;
    }

    /**
     * Get a string from the buffer
     *
     * @param uni boolean
     * @return String
     */
    public final String getString(boolean uni) {
        int maxLen = getAvailableLength();
        if (uni)
            maxLen = maxLen / 2;
        return getString(maxLen, uni);
    }

    /**
     * Get a string from the buffer
     *
     * @param maxlen int
     * @param uni    boolean
     * @return String
     */
    public final String getString(int maxlen, boolean uni) {

        // Check for Unicode or ASCII
        String ret = null;
        int availLen = -1;

        if (uni) {

            // Word align the current buffer position, calculate the available length
            m_pos = DataPacker.wordAlign(m_pos);
            availLen = (m_endpos - m_pos) / 2;
            if (availLen < maxlen)
                maxlen = availLen;

            ret = DataPacker.getUnicodeString(m_data, m_pos, maxlen);
            if (ret != null)
                m_pos += (ret.length() * 2) + 2;
        }
        else {

            // Calculate the available length
            availLen = m_endpos - m_pos;
            if (availLen < maxlen)
                maxlen = availLen;

            // Unpack the ASCII string
            ret = DataPacker.getString(m_data, m_pos, maxlen);
            if (ret != null)
                m_pos += ret.length() + 1;
        }

        // Return the string
        return ret;
    }

    /**
     * Get a fixed length string from the buffer
     *
     * @param len int
     * @param uni boolean
     * @return String
     */
    public final String getFixedString(int len, boolean uni) {

        // Check for Unicode or ASCII
        String ret = null;
        int availLen = -1;

        if (uni) {

            // Word align the current buffer position, calculate the available length
            m_pos = DataPacker.wordAlign(m_pos);
            availLen = (m_endpos - m_pos) / 2;
            if (availLen >= len) {
                ret = DataPacker.getUnicodeString(m_data, m_pos, len);
                if (ret != null)
                    m_pos += len * 2;
            }
        }
        else {

            // Calculate the available length
            availLen = m_endpos - m_pos;
            if (availLen >= len) {
                ret = DataPacker.getString(m_data, m_pos, len);
                if (ret != null)
                    m_pos += len;
            }
        }

        // Return the string
        return ret != null ? ret : "";
    }

    /**
     * Get a short from the buffer at the specified index
     *
     * @param idx int
     * @return int
     */
    public final int getShortAt(int idx) {

        // Check if there is enough data in the buffer
        int pos = m_offset + (idx * 2);
        if (m_data.length - pos < 2)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        // Unpack the integer value
        int sval = (int) DataPacker.getIntelShort(m_data, pos) & 0xFFFF;
        return sval;
    }

    /**
     * Get an integer from the buffer at the specified index
     *
     * @param idx int
     * @return int
     */
    public final int getIntAt(int idx) {

        // Check if there is enough data in the buffer
        int pos = m_offset + (idx * 2);
        if (m_data.length - pos < 4)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        // Unpack the integer value
        int ival = DataPacker.getIntelInt(m_data, pos);
        return ival;
    }

    /**
     * Get a long (64 bit) value from the buffer at the specified index
     *
     * @param idx int
     * @return long
     */
    public final long getLongAt(int idx) {

        // Check if there is enough data in the buffer
        int pos = m_offset + (idx * 2);
        if (m_data.length - pos < 8)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        // Unpack the long value
        long lval = DataPacker.getIntelLong(m_data, pos);
        return lval;
    }

    /**
     * Skip over a number of bytes
     *
     * @param cnt int
     */
    public final void skipBytes(int cnt) {

        // Check if there is enough data in the buffer
        if (m_data.length - m_pos < cnt)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        // Skip bytes
        m_pos += cnt;
    }

    /**
     * Return the data position
     *
     * @return int
     */
    public final int getPosition() {
        return m_pos;
    }

    /**
     * Set the read/write buffer position
     *
     * @param pos int
     */
    public final void setPosition(int pos) {
        m_pos = pos;
    }

    /**
     * Set the end of buffer position, and reset the read position to the beginning of the buffer
     */
    public final void setEndOfBuffer() {
        m_endpos = m_pos;
        m_pos = m_offset;
    }

    /**
     * Set the end of buffer position, and reset the read position to the beginning of the buffer
     *
     * @param endPos int
     */
    public final void setEndOfBuffer(int endPos) {
        m_endpos = endPos;
        m_pos = m_offset;
    }

    /**
     * Set the data length
     *
     * @param len int
     */
    public final void setLength(int len) {
        m_endpos = m_offset + len;
    }

    /**
     * Reset the data buffer
     */
    public final void resetBuffer() {
        m_pos = 0;
        m_endpos = m_data.length;
    }

    /**
     * Append a byte value to the buffer
     *
     * @param bval int
     */
    public final void putByte(int bval) {

        // Check if there is enough space in the buffer
        if (m_data.length - m_pos < 1)
            extendBuffer();

        // Pack the byte value
        m_data[m_pos++] = (byte) (bval & 0xFF);
    }

    /**
     * Append a short value to the buffer
     *
     * @param sval int
     */
    public final void putShort(int sval) {

        // Check if there is enough space in the buffer
        if (m_data.length - m_pos < 2)
            extendBuffer();

        // Pack the short value
        DataPacker.putIntelShort(sval, m_data, m_pos);
        m_pos += 2;
    }

    /**
     * Append an integer to the buffer
     *
     * @param ival int
     */
    public final void putInt(int ival) {

        // Check if there is enough space in the buffer
        if (m_data.length - m_pos < 4)
            extendBuffer();

        // Pack the integer value
        DataPacker.putIntelInt(ival, m_data, m_pos);
        m_pos += 4;
    }

    /**
     * Append a long to the buffer
     *
     * @param lval long
     */
    public final void putLong(long lval) {

        // Check if there is enough space in the buffer
        if (m_data.length - m_pos < 8)
            extendBuffer();

        // Pack the long value
        DataPacker.putIntelLong(lval, m_data, m_pos);
        m_pos += 8;
    }

    /**
     * Append a short value to the buffer at the specified index
     *
     * @param idx  int
     * @param sval int
     */
    public final void putShortAt(int idx, int sval) {

        // Check if there is enough space in the buffer
        int pos = m_offset + (idx * 2);
        if (m_data.length - pos < 2)
            extendBuffer();

        // Pack the short value
        DataPacker.putIntelShort(sval, m_data, pos);
    }

    /**
     * Append an integer to the buffer at the specified index
     *
     * @param idx  int
     * @param ival int
     */
    public final void putIntAt(int idx, int ival) {

        // Check if there is enough space in the buffer
        int pos = m_offset + (idx * 4);
        if (m_data.length - pos < 4)
            extendBuffer();

        // Pack the integer value
        DataPacker.putIntelInt(ival, m_data, pos);
    }

    /**
     * Append a long to the buffer at the specified index
     *
     * @param idx  int
     * @param lval long
     */
    public final void putLongAt(int idx, int lval) {

        // Check if there is enough space in the buffer
        int pos = m_offset + (idx * 8);
        if (m_data.length - pos < 8)
            extendBuffer();

        // Pack the long value
        DataPacker.putIntelLong(lval, m_data, pos);
    }

    /**
     * Pack an integer at the specified buffer position, it is assumed the buffer does not need extending
     *
     * @param pos int
     * @param ival int
     */
    public final void putIntAtPosition(int pos, int ival) {

        // Pack the integer value
        DataPacker.putIntelInt(ival, m_data, pos);
    }

    /**
     * Append a string to the buffer
     *
     * @param str String
     * @param uni boolean
     */
    public final void putString(String str, boolean uni) {
        putString(str, uni, true);
    }

    /**
     * Append a string to the buffer
     *
     * @param str     String
     * @param uni     boolean
     * @param nulTerm boolean
     */
    public final void putString(String str, boolean uni, boolean nulTerm) {

        // Check for Unicode or ASCII
        if (uni) {

            // Check if there is enough space in the buffer
            int bytLen = str.length() * 2;
            if (nulTerm) {
                bytLen++;
            }
            if (m_data.length - m_pos < bytLen)
                extendBuffer(bytLen + 4);

            // Word align the buffer position, pack the Unicode string
            m_pos = DataPacker.wordAlign(m_pos);
            DataPacker.putUnicodeString(str, m_data, m_pos, nulTerm);
            m_pos += (str.length() * 2);
            if (nulTerm)
                m_pos += 2;
        }
        else {

            // Check if there is enough space in the buffer
            int bytLen = str.length();
            if (nulTerm) {
                bytLen++;
            }

            if (m_data.length - m_pos < bytLen)
                extendBuffer(bytLen + 2);

            // Pack the ASCII string
            DataPacker.putString(str, m_data, m_pos, nulTerm);
            m_pos += str.length();
            if (nulTerm)
                m_pos++;
        }
    }

    /**
     * Append a fixed length string to the buffer
     *
     * @param str String
     * @param len int
     */
    public final void putFixedString(String str, int len) {

        // Check if there is enough space in the buffer
        if (m_data.length - m_pos < str.length())
            extendBuffer(str.length() + 2);

        // Pack the ASCII string
        DataPacker.putString(str, len, m_data, m_pos);
        m_pos += len;
    }

    /**
     * Append a string to the buffer at the specified buffer position
     *
     * @param str     String
     * @param pos     int
     * @param uni     boolean
     * @param nulTerm boolean
     * @return int
     */
    public final int putStringAt(String str, int pos, boolean uni, boolean nulTerm) {

        // Check for Unicode or ASCII
        int retPos = -1;

        if (uni) {

            // Check if there is enough space in the buffer
            int bytLen = str.length() * 2;
            if (m_data.length - pos < bytLen)
                extendBuffer(bytLen + 4);

            // Word align the buffer position, pack the Unicode string
            pos = DataPacker.wordAlign(pos);
            retPos = DataPacker.putUnicodeString(str, m_data, pos, nulTerm);
        }
        else {

            // Check if there is enough space in the buffer
            if (m_data.length - pos < str.length())
                extendBuffer(str.length() + 2);

            // Pack the ASCII string
            retPos = DataPacker.putString(str, m_data, pos, nulTerm);
        }

        // Return the end of string buffer position
        return retPos;
    }

    /**
     * Append a fixed length string to the buffer at the specified position
     *
     * @param str String
     * @param len int
     * @param pos int
     * @return int
     */
    public final int putFixedStringAt(String str, int len, int pos) {

        // Check if there is enough space in the buffer
        if (m_data.length - pos < str.length())
            extendBuffer(str.length() + 2);

        // Pack the ASCII string
        return DataPacker.putString(str, len, m_data, pos);
    }

    /**
     * Append a string pointer to the specified buffer offset
     *
     * @param off int
     */
    public final void putStringPointer(int off) {

        // Calculate the offset from the start of the data buffer to the string position
        DataPacker.putIntelInt(off - m_offset, m_data, m_pos);
        m_pos += 4;
    }

    /**
     * Append zero bytes to the buffer
     *
     * @param cnt int
     */
    public final void putZeros(int cnt) {

        // Check if there is enough space in the buffer
        if (m_data.length - m_pos < cnt)
            extendBuffer(cnt);

        // Pack the zero bytes
        for (int i = 0; i < cnt; i++)
            m_data[m_pos++] = 0;
    }

    /**
     * Word align the buffer position
     */
    public final void wordAlign() {
        m_pos = DataPacker.wordAlign(m_pos);
    }

    /**
     * Longword align the buffer position
     */
    public final void longwordAlign() {
        m_pos = DataPacker.longwordAlign(m_pos + m_longAdjust) - m_longAdjust;
    }

    /**
     * Append a raw data block to the data buffer
     *
     * @param buf byte[]
     */
    public final void appendData(byte[] buf) {
        appendData( buf, 0, buf.length);
    }

    /**
     * Append a raw data block to the data buffer
     *
     * @param buf byte[]
     * @param off int
     * @param len int
     */
    public final void appendData(byte[] buf, int off, int len) {

        // Check if there is enough space in the buffer
        if (m_data.length - m_pos < len)
            extendBuffer(len);

        // Copy the data to the buffer and update the current write position
        System.arraycopy(buf, off, m_data, m_pos, len);
        m_pos += len;
    }

    /**
     * Copy all data from the data buffer to the user buffer, and update the read position
     *
     * @param buf byte[]
     * @param off int
     * @return int
     */
    public final int copyData(byte[] buf, int off) {
        return copyData(buf, off, getLength());
    }

    /**
     * Copy data from the data buffer to the user buffer, and update the current read position.
     *
     * @param buf byte[]
     * @param off int
     * @param cnt int
     * @return int
     */
    public final int copyData(byte[] buf, int off, int cnt) {

        // Check if there is any more data to copy
        if (m_pos == m_endpos)
            return 0;

        // Calculate the amount of data to copy
        int siz = m_endpos - m_pos;

        if (siz > cnt)
            siz = cnt;

        // Copy the data to the user buffer and update the current read position
        System.arraycopy(m_data, m_pos, buf, off, siz);
        m_pos += siz;

        // Return the amount of data copied
        return siz;
    }

    /**
     * Extend the data buffer by the specified amount
     *
     * @param ext int
     */
    private final void extendBuffer(int ext) {

        // Do not extend an externally allocated buffer
        if (isExternalBuffer())
            throw new RuntimeException("Attempt to extend externally allocated buffer, from " + m_data.length + " to " + (m_data.length + ext));

        // Create a new buffer of the required size
        byte[] newBuf = new byte[m_data.length + ext];

        // Copy the data from the current buffer to the new buffer
        System.arraycopy(m_data, 0, newBuf, 0, m_data.length);

        // Set the new buffer to be the main buffer
        m_data = newBuf;

        // Set the new buffer end position
        m_endpos = m_data.length;
    }

    /**
     * Extend the data buffer, double the currently allocated buffer size
     */
    private final void extendBuffer() {
        extendBuffer(m_data.length * 2);
    }

    /**
     * Check if the buffer is an externally allocated buffer
     *
     * @return boolean
     */
    protected final boolean isExternalBuffer() {
        return m_external;
    }

    /**
     * Return the data buffer details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[data=");
        str.append(m_data);
        str.append(",");
        str.append(m_pos);
        str.append("/");
        str.append(m_offset);
        str.append("/");
        str.append(getLength());

        if ( hasAlignmentRequired() == false)
            str.append(" NoAlign");
        str.append("]");

        return str.toString();
    }
}
