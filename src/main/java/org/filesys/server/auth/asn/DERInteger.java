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

package org.filesys.server.auth.asn;

import java.io.IOException;

/**
 * DER Integer Class
 *
 * @author gkspencer
 */
public class DERInteger extends DERObject {

    // Integer value
    private long m_integer;

    /**
     * Default constructor
     */
    public DERInteger() {
    }

    /**
     * Class constructor
     *
     * @param val int
     */
    public DERInteger(int val) {
        m_integer = val;
    }

    /**
     * Class constructor
     *
     * @param val long
     */
    public DERInteger(long val) {
        m_integer = val;
    }

    /**
     * Return the long value
     *
     * @return long
     */
    public final long getValue() {
        return m_integer;
    }

    /**
     * Return the integer value
     *
     * @return int
     */
    public final int intValue() {
        return (int) m_integer;
    }

    /**
     * Decode the object
     *
     * @param buf DERBuffer
     * @exception IOException Error decoding the DER object
     */
    public void derDecode(DERBuffer buf) throws IOException {

        // Decode the type
        if (buf.unpackType() == DER.Integer) {

            // Unpack the length and value
            int len = buf.unpackByte();
            m_integer = 0;

            if (len == 1)
                m_integer = buf.unpackByte();
            else if (len > 1) {
                while (len-- > 0)
                    m_integer = (m_integer << 8) + buf.unpackByte();
            }
        } else
            throw new IOException("Wrong DER type, expected Integer");
    }

    /**
     * Encode the object
     *
     * @param buf DERBuffer
     * @exception IOException Error encoding the DER object
     */
    public void derEncode(DERBuffer buf) throws IOException {

        // Pack the type, length and value
        buf.packByte(DER.Integer);

        // Calculate the number of bytes required to pack the integer value
        int bytLen = 8;

        while (bytLen > 0 && (m_integer & (0xFFL << ((bytLen - 1) * 8))) == 0)
            bytLen--;

        // Pack the length
        buf.packLength(bytLen);

        // Pack the integer bytes
        while (bytLen > 0)
            buf.packByte((int) (m_integer >> (--bytLen * 8)) & 0xFF);
    }

    /**
     * Return the integer as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[Integer:");
        str.append(getValue());
        str.append("]");

        return str.toString();
    }
}
