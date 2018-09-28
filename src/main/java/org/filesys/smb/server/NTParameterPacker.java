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

package org.filesys.smb.server;

import org.filesys.util.DataPacker;

/**
 * NT Dialect Parameter Packer Class
 *
 * <p>The NT SMB dialect uses parameters that are not always word/longword aligned.
 *
 * @author gkspencer
 */
class NTParameterPacker {

    //	Buffer and current offset
    private byte[] m_buf;
    private int m_pos;

    /**
     * Class constructor
     *
     * @param buf byte[]
     */
    public NTParameterPacker(byte[] buf) {
        m_buf = buf;
        m_pos = SMBV1.PARAMWORDS;
    }

    /**
     * Class constructor
     *
     * @param buf byte[]
     * @param pos int
     */
    public NTParameterPacker(byte[] buf, int pos) {
        m_buf = buf;
        m_pos = pos;
    }

    /**
     * Pack a byte (8 bit) value
     *
     * @param val byte
     */
    public final void packByte(byte val) {
        m_buf[m_pos++] = val;
    }

    /**
     * Pack a byte (8 bit) value
     *
     * @param val int
     */
    public final void packByte(int val) {
        m_buf[m_pos++] = (byte) val;
    }

    /**
     * Pack a word (16 bit) value
     *
     * @param val int
     */
    public final void packWord(int val) {
        DataPacker.putIntelShort(val, m_buf, m_pos);
        m_pos += 2;
    }

    /**
     * Pack an integer (32 bit) value
     *
     * @param val int
     */
    public final void packInt(int val) {
        DataPacker.putIntelInt(val, m_buf, m_pos);
        m_pos += 4;
    }

    /**
     * Pack a long (64 bit) value
     *
     * @param val long
     */
    public final void packLong(long val) {
        DataPacker.putIntelLong(val, m_buf, m_pos);
        m_pos += 8;
    }

    /**
     * Return the current buffer position
     *
     * @return int
     */
    public final int getPosition() {
        return m_pos;
    }

    /**
     * Return the buffer
     *
     * @return byte[]
     */
    public final byte[] getBuffer() {
        return m_buf;
    }

    /**
     * Unpack a byte value
     *
     * @return int
     */
    public final int unpackByte() {
        return (int) m_buf[m_pos++];
    }

    /**
     * Unpack a word (16 bit) value
     *
     * @return int
     */
    public final int unpackWord() {
        int val = DataPacker.getIntelShort(m_buf, m_pos);
        m_pos += 2;
        return val;
    }

    /**
     * Unpack an integer (32 bit) value
     *
     * @return int
     */
    public final int unpackInt() {
        int val = DataPacker.getIntelInt(m_buf, m_pos);
        m_pos += 4;
        return val;
    }

    /**
     * Unpack a long (64 bit) value
     *
     * @return int
     */
    public final long unpackLong() {
        long val = DataPacker.getIntelLong(m_buf, m_pos);
        m_pos += 8;
        return val;
    }

    /**
     * Reset the parameter packer/reader to use the new buffer/offset
     *
     * @param buf byte[]
     * @param pos int
     */
    public final void reset(byte[] buf, int pos) {
        m_buf = buf;
        m_pos = pos;
    }
}
