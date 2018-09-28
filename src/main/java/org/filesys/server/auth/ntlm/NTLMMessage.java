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

package org.filesys.server.auth.ntlm;

import java.io.UnsupportedEncodingException;

import org.filesys.debug.Debug;
import org.filesys.util.DataPacker;

/**
 * NTLM Message Types Base Class
 *
 * @author gkspencer
 */
public abstract class NTLMMessage {

    // Message types
    public enum Type {
        Negotiate       (1),
        Challenge       (2),
        Authenticate    (3),

        Invalid         (-1);

        private final int msgType;

        /**
         * Enum constructor
         *
         * @param typ int
         */
        Type(int typ) { msgType = typ; }

        /**
         * Return the message type as an int
         *
         * @return int
         */
        public final int intValue() { return msgType; }

        /**
         * Create a message type from an int
         *
         * @param ival int
         * @return Type
         */
        public static final Type fromInt(int ival) {
            Type msgType = Invalid;

            switch( ival) {
                case 1:
                    msgType = Negotiate;
                    break;
                case 2:
                    msgType = Challenge;
                    break;
                case 3:
                    msgType = Authenticate;
                    break;
            }

            return msgType;
        }
    }

    // Default buffer size to allocate
    private static final int DefaultBlobSize = 256;

    // Field offsets
    public static final int OffsetSignature = 0;
    public static final int OffsetType = 8;

    // Buffer header length
    public static final int BufferHeaderLen = 8;

    // Buffer, offset and lenght of the NTLM blob
    private byte[] m_buf;
    private int m_offset;
    private int m_len;

    /**
     * Default constructor
     */
    protected NTLMMessage() {

        // Allocate a buffer
        m_buf = new byte[DefaultBlobSize];
        m_len = DefaultBlobSize;
    }

    /**
     * Class constructor
     *
     * @param buf    byte[]
     * @param offset int
     * @param len    int
     */
    protected NTLMMessage(byte[] buf, int offset, int len) {
        m_buf = buf;
        m_offset = offset;

        m_len = len;
    }

    /**
     * Return the message type
     *
     * @return Type
     */
    public final Type isMessageType() {
        return Type.fromInt(DataPacker.getIntelInt(m_buf, m_offset + OffsetType));
    }

    /**
     * Return the message flags
     *
     * @return int
     */
    public abstract int getFlags();

    /**
     * Return the state of the specified flag
     *
     * @param flag int
     * @return boolean
     */
    public final boolean hasFlag(int flag) {
        return (getFlags() & flag) != 0 ? true : false;
    }

    /**
     * Return the buffer
     *
     * @return byte[]
     */
    protected final byte[] getBuffer() {
        return m_buf;
    }

    /**
     * Return the message length
     *
     * @return int
     */
    public final int getLength() {
        return m_len;
    }

    /**
     * Return the buffer offset
     *
     * @return int
     */
    public final int getOffset() {
        return m_offset;
    }

    /**
     * Set the message type
     *
     * @param typ Type
     */
    public final void setMessageType(Type typ) {
        DataPacker.putIntelInt(typ.intValue(), m_buf, m_offset + OffsetType);
    }

    /**
     * Copy the NTLM blob data from the specified buffer
     *
     * @param buf    byte[]
     * @param offset int
     * @param len    int
     */
    public final void copyFrom(byte[] buf, int offset, int len) {

        // Allocate a new buffer, if required
        if (m_buf == null || m_offset != 0 || m_buf.length < len)
            m_buf = new byte[len];

        // Copy the security blob data
        System.arraycopy(buf, offset, m_buf, 0, len);

        m_len = len;
        m_offset = 0;
    }

    /**
     * Return the NTLM message as a byte array
     *
     * @return byte[]
     */
    public final byte[] getBytes() {
        byte[] byts = new byte[getLength()];
        System.arraycopy(m_buf, m_offset, byts, 0, getLength());
        return byts;
    }

    /**
     * Set the message flags
     *
     * @param flags int
     */
    protected abstract void setFlags(int flags);

    /**
     * Initialize the blob
     *
     * @param typ   Type
     * @param flags int
     */
    protected void initializeHeader(Type typ, int flags) {

        // Set the signature
        System.arraycopy(NTLM.Signature, 0, m_buf, m_offset, NTLM.Signature.length);

        setMessageType(typ);
        setFlags(flags);
    }

    /**
     * Return a short/16bit value
     *
     * @param offset int
     * @return int
     */
    protected final int getShortValue(int offset) {
        return DataPacker.getIntelShort(m_buf, m_offset + offset);
    }

    /**
     * Return an int/32bit value
     *
     * @param offset int
     * @return int
     */
    protected final int getIntValue(int offset) {
        return DataPacker.getIntelInt(m_buf, m_offset + offset);
    }

    /**
     * Return a long/64bit value
     *
     * @param offset int
     * @return long
     */
    protected final long getLongValue(int offset) {
        return DataPacker.getIntelLong(m_buf, m_offset + offset);
    }

    /**
     * Return the offset for a byte value
     *
     * @param offset int
     * @return int
     */
    protected final int getByteOffset(int offset) {
        return getIntValue(offset + 4);
    }

    /**
     * Return a byte value that has a header
     *
     * @param offset int
     * @return byte[]
     */
    protected final byte[] getByteValue(int offset) {

        // Get the byte block length
        int bLen = getShortValue(offset);
        if (bLen == 0)
            return null;

        int bOff = getIntValue(offset + 4);
        return getRawBytes(bOff, bLen);
    }

    /**
     * Return a block of byte data
     *
     * @param offset int
     * @param len    int
     * @return byte[]
     */
    protected final byte[] getRawBytes(int offset, int len) {

        byte[] byts = new byte[len];
        System.arraycopy(m_buf, m_offset + offset, byts, 0, len);

        return byts;
    }

    /**
     * Return the length of a string
     *
     * @param offset int
     * @return int
     */
    protected final int getStringLength(int offset) {

        int bufpos = m_offset + offset;

        if (bufpos + 2 > m_len)
            return -1;
        return DataPacker.getIntelShort(m_buf, bufpos);
    }

    /**
     * Return the allocated length of a string
     *
     * @param offset int
     * @return int
     */
    protected final int getStringAllocatedLength(int offset) {

        int bufpos = m_offset + offset;

        if (bufpos + 8 > m_len)
            return -1;
        return DataPacker.getIntelShort(m_buf, bufpos + 2);
    }

    /**
     * Return the string data offset
     *
     * @param offset int
     * @return int
     */
    protected final int getStringOffset(int offset) {

        int bufpos = m_offset + offset;

        if (bufpos + 8 > m_len)
            return -1;
        return DataPacker.getIntelInt(m_buf, bufpos + 4);
    }

    /**
     * Return a string value
     *
     * @param offset int
     * @param isuni  boolean
     * @return String
     */
    protected final String getStringValue(int offset, boolean isuni) {

        int bufpos = m_offset + offset;

        if (offset + 8 > m_len)
            return null;

        // Get the offset to the string
        int len = DataPacker.getIntelShort(m_buf, bufpos);
        int pos = DataPacker.getIntelInt(m_buf, bufpos + 4);

        // Get the string value
        if (pos + len > m_len)
            return null;

        // Unpack the string
        String str = null;
        try {
            str = new String(m_buf, m_offset + pos, len, isuni ? "UnicodeLittle" : "US-ASCII");
        }
        catch (UnsupportedEncodingException ex) {
            Debug.println(ex);
        }

        return str;
    }

    /**
     * Return a raw string
     *
     * @param offset int
     * @param len    int
     * @param isuni  boolean
     * @return String
     */
    protected final String getRawString(int offset, int len, boolean isuni) {
        return DataPacker.getString(m_buf, m_offset + offset, len, isuni);
    }

    /**
     * Set a byte/8bit value
     *
     * @param offset int
     * @param bval int
     */
    protected final void setByteValue(int offset, int bval) {
        m_buf[m_offset + offset] = (byte) (bval & 0xFF);
    }

    /**
     * Set a short/16bit value
     *
     * @param offset int
     * @param sval   int
     */
    protected final void setShortValue(int offset, int sval) {
        DataPacker.putIntelShort(sval, m_buf, m_offset + offset);
    }

    /**
     * Set an int/32bit value
     *
     * @param offset int
     * @param val    int
     */
    protected final void setIntValue(int offset, int val) {
        DataPacker.putIntelInt(val, m_buf, m_offset + offset);
    }

    /**
     * Set a long/64bit value
     *
     * @param offset int
     * @param lval long
     */
    protected final void setLongValue( int offset, long lval) {
        DataPacker.putIntelLong(lval, m_buf, m_offset + offset);
    }

    /**
     * Set a raw byte value
     *
     * @param offset int
     * @param byts   byte[]
     */
    protected final void setRawBytes(int offset, byte[] byts) {
        System.arraycopy(byts, 0, m_buf, m_offset + offset, byts.length);
    }

    /**
     * Set raw int values
     *
     * @param offset int
     * @param ints   int[]
     */
    protected final void setRawInts(int offset, int[] ints) {
        int bufpos = m_offset + offset;

        for (int i = 0; i < ints.length; i++) {
            DataPacker.putIntelInt(ints[i], m_buf, bufpos);
            bufpos += 4;
        }
    }

    /**
     * Pack a raw string
     *
     * @param offset int
     * @param str    String
     * @param isuni  boolean
     * @return int
     */
    protected final int setRawString(int offset, String str, boolean isuni) {
        return DataPacker.putString(str, m_buf, m_offset + offset, false, isuni);
    }

    /**
     * Zero out an area of bytes
     *
     * @param offset int
     * @param len    int
     */
    protected final void zeroBytes(int offset, int len) {
        int bufpos = m_offset + offset;
        for (int i = 0; i < len; i++)
            m_buf[bufpos++] = (byte) 0;
    }

    /**
     * Set a byte array value
     *
     * @param offset     int
     * @param byts       byte[]
     * @param dataOffset int
     * @return int
     */
    protected final int setByteValue(int offset, byte[] byts, int dataOffset) {

        int bytsLen = byts != null ? byts.length : 0;

        if (m_offset + offset + 12 > m_buf.length || m_offset + dataOffset + bytsLen > m_buf.length)
            throw new ArrayIndexOutOfBoundsException();

        // Pack the byte pointer block
        DataPacker.putIntelShort(bytsLen, m_buf, m_offset + offset);
        DataPacker.putIntelShort(bytsLen, m_buf, m_offset + offset + 2);
        DataPacker.putIntelInt(dataOffset, m_buf, m_offset + offset + 4);

        // Pack the bytes
        if (bytsLen > 0)
            System.arraycopy(byts, 0, m_buf, m_offset + dataOffset, bytsLen);

        // Return the new data buffer offset
        return dataOffset + DataPacker.wordAlign(bytsLen);
    }

    /**
     * Set a string value
     *
     * @param offset    int
     * @param val       String
     * @param strOffset int
     * @param isuni     boolean
     * @return int
     */
    protected final int setStringValue(int offset, String val, int strOffset, boolean isuni) {

        // Get the length in bytes
        int len = val.length();
        if (isuni)
            len *= 2;

        if (m_offset + offset + 8 > m_buf.length || m_offset + strOffset + len > m_buf.length)
            throw new ArrayIndexOutOfBoundsException();

        // Pack the string pointer block
        DataPacker.putIntelShort(len, m_buf, m_offset + offset);
        DataPacker.putIntelShort(len, m_buf, m_offset + offset + 2);
        DataPacker.putIntelInt(strOffset, m_buf, m_offset + offset + 4);

        // Pack the string
        return DataPacker.putString(val, m_buf, m_offset + strOffset, false, isuni) - m_offset;
    }

    /**
     * Set the message length
     *
     * @param len int
     */
    protected final void setLength(int len) {
        m_len = len;
    }

    /**
     * Validate and determine the NTLM message type
     *
     * @param buf byte[]
     * @return Type
     */
    public final static Type isNTLMType(byte[] buf) {
        return isNTLMType(buf, 0);
    }

    /**
     * Validate and determine the NTLM message type
     *
     * @param buf    byte[]
     * @param offset int
     * @return Type
     */
    public final static Type isNTLMType(byte[] buf, int offset) {

        // Validate the buffer
        if (buf == null || buf.length < BufferHeaderLen)
            return Type.Invalid;

        for (int i = 0; i < NTLM.Signature.length; i++) {
            if (buf[offset + i] != NTLM.Signature[i])
                return Type.Invalid;
        }

        // Get the NTLM message type
        return Type.fromInt(DataPacker.getIntelInt(buf, offset + OffsetType));
    }
}
