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

package org.filesys.smb.dcerpc;

import org.filesys.util.DataPacker;

/**
 * DCE Data Packer Class
 *
 * <p>Contains static methods for packing/unpacking different data types into a byte buffer.
 *
 * @author gkspencer
 */
public class DCEDataPacker {

    /**
     * Unpack a DCE string from the buffer
     *
     * @param buf byte[]
     * @param off int
     * @return String
     * @throws java.lang.IndexOutOfBoundsException If there is not enough data in the buffer.
     */
    public final static String getDCEString(byte[] buf, int off)
            throws IndexOutOfBoundsException {

        //	Check if the buffer is big enough to hold the String header
        if (buf.length < off + 12)
            throw new IndexOutOfBoundsException();

        //	Get the maximum and actual string length
        int maxLen = DataPacker.getIntelInt(buf, off);
        int strLen = DataPacker.getIntelInt(buf, off + 8);

        //	Read the Unicode string
        return DataPacker.getUnicodeString(buf, off + 12, strLen);
    }

    /**
     * Pack a DCE string into the buffer
     *
     * @param buf    byte[]
     * @param off    int
     * @param str    String
     * @param incNul boolean
     * @return int
     */
    public final static int putDCEString(byte[] buf, int off, String str, boolean incNul) {

        //	Pack the string header
        DataPacker.putIntelInt(str.length() + 1, buf, off);
        DataPacker.putZeros(buf, off + 4, 4);

        if (incNul == false)
            DataPacker.putIntelInt(str.length(), buf, off + 8);
        else
            DataPacker.putIntelInt(str.length() + 1, buf, off + 8);

        //	Pack the string
        return DataPacker.putUnicodeString(str, buf, off + 12, incNul);
    }

    /**
     * Align a buffer offset on a longword boundary
     *
     * @param pos int
     * @return int
     */
    public final static int wordAlign(int pos) {
        return (pos + 1) & 0xFFFFFFFE;
    }

    /**
     * Align a buffer offset on a longword boundary
     *
     * @param pos int
     * @return int
     */
    public final static int longwordAlign(int pos) {
        return (pos + 3) & 0xFFFFFFFC;
    }
}
