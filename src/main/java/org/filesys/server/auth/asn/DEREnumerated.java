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
 * DER Enumerated Class
 *
 * @author gkspencer
 */
public class DEREnumerated extends DERObject {

    // Enumerated value
    private int m_enum;

    /**
     * Default constructor
     */
    public DEREnumerated() {
    }

    /**
     * Class constructor
     *
     * @param val int
     */
    public DEREnumerated(int val) {
        m_enum = val;
    }

    /**
     * Return the enumerated value
     *
     * @return int
     */
    public final int getValue() {
        return m_enum;
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
        if (buf.unpackType() == DER.Enumerated) {

            // Unpack the length and value
            int len = buf.unpackByte();
            m_enum = buf.unpackInt(len);
        } else
            throw new IOException("Wrong DER type, expected Enumerated");
    }

    /**
     * Encode the object
     *
     * @param buf DERBuffer
     * @exception IOException Error encoding the DER object
     */
    public void derEncode(DERBuffer buf)
            throws IOException {

        // Pack the type, length and value
        buf.packByte(DER.Enumerated);
        if (m_enum < 256) {
            buf.packLength(1);
            buf.packByte(m_enum);
        } else {
            buf.packLength(4);
            buf.packInt(m_enum);
        }
    }

    /**
     * Return the enumerated type as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[Enum:");
        str.append(getValue());
        str.append("]");

        return str.toString();
    }
}
