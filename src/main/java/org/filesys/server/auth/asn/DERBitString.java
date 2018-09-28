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
 * DER Bit String Class
 *
 * @author gkspencer
 */
public class DERBitString extends DERObject {

    // Bit flags value
    private long m_bits;

    /**
     * Default constructor
     */
    public DERBitString() {
    }

    /**
     * Class constructor
     *
     * @param bits int
     */
    public DERBitString(long bits) {
        m_bits = bits;
    }

    /**
     * Return the value
     *
     * @return long
     */
    public final long getValue() {
        return m_bits;
    }

    /**
     * Return the value as an integer
     *
     * @return int
     */
    public final int intValue() {
        return (int) m_bits;
    }

    /**
     * Decode the object
     *
     * @param buf DERBuffer
     * @exception IOException Error decoding the DER object
     */
    public void derDecode(DERBuffer buf)
            throws IOException {

        // Decode the type
        if (buf.unpackType() == DER.BitString) {

            // Unpack the length and bytes
            int len = buf.unpackLength();
            int lastBits = buf.unpackByte();

            m_bits = 0;
            long curByt = 0L;
            len--;

            for (int idx = (len - 1); idx >= 0; idx--) {

                // Get the value bytes
                curByt = (long) buf.unpackByte();
                m_bits += curByt << (idx * 8);
            }
        } else
            throw new IOException("Wrong DER type, expected BitString");
    }

    /**
     * Encode the object
     *
     * @param buf DERBuffer
     * @exception IOException Error encoding the DER object
     */
    public void derEncode(DERBuffer buf)
            throws IOException {

        // Pack the type, length and bytes
        buf.packByte(DER.BitString);
        buf.packByte(0);

        buf.packLength(8);
        for (int idx = 7; idx >= 0; idx--) {
            long bytVal = m_bits >> (idx * 8);
            buf.packByte((int) (m_bits & 0xFF));
        }
    }

    /**
     * Return the bit string as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[BitString:0x");
        str.append(Long.toHexString(m_bits));
        str.append("]");

        return str.toString();
    }
}
