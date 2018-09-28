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
 * DER Octet String Class
 *
 * @author gkspencer
 */
public class DEROctetString extends DERObject {

    // String bytes
    private byte[] m_string;

    /**
     * Default constructor
     */
    public DEROctetString() {
    }

    /**
     * Class constructor
     *
     * @param byts byte[]
     */
    public DEROctetString(byte[] byts) {
        m_string = byts;
    }

    /**
     * Class constructor
     *
     * @param str String
     */
    public DEROctetString(String str) {
        m_string = str.getBytes();
    }

    /**
     * Return the string bytes
     *
     * @return byte[]
     */
    public byte[] getValue() {
        return m_string;
    }

    /**
     * Return as a string
     *
     * @return String
     */
    public final String asString() {
        if (m_string != null)
            return new String(m_string);
        return null;
    }

    /**
     * DER decode the object
     *
     * @param buf DERBuffer
     */
    public void derDecode(DERBuffer buf)
            throws IOException {

        // Decode the type
        if (buf.unpackType() == DER.OctetString) {

            // Unpack the length and bytes
            int len = buf.unpackLength();
            if (len > 0) {

                // Get the string bytes
                m_string = buf.unpackBytes(len);
            } else
                m_string = null;
        } else
            throw new IOException("Wrong DER type, expected OctetString");
    }

    /**
     * DER encode the object
     *
     * @param buf DERBuffer
     */
    public void derEncode(DERBuffer buf)
            throws IOException {

        // Get the string bytes
        byte[] byts = m_string;

        // Pack the type, length and bytes
        buf.packByte(DER.OctetString);

        if (byts != null) {
            buf.packLength(byts.length);
            buf.packBytes(byts, 0, byts.length);
        } else
            buf.packLength(0);
    }

    /**
     * Return the string details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[OctetString:");
        str.append(m_string != null ? m_string.length : 0);
        str.append("]");

        return str.toString();
    }
}
