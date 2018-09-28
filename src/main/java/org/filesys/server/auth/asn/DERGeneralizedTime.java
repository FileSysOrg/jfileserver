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
 * DER Generalized Time Class
 *
 * @author gkspencer
 */
public class DERGeneralizedTime extends DERObject {

    // Time value
    private String m_string;

    /**
     * Default constructor
     */
    public DERGeneralizedTime() {
    }

    /**
     * Class constructor
     *
     * @param str String
     */
    public DERGeneralizedTime(String str) {
        m_string = str;
    }

    /**
     * Return the string value
     *
     * @return String
     */
    public final String getValue() {
        return m_string;
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
        if (buf.unpackType() == DER.GeneralizedTime) {

            // Unpack the length and bytes
            int len = buf.unpackLength();
            if (len > 0) {

                // Get the string bytes
                byte[] byts = buf.unpackBytes(len);
                m_string = new String(byts);
            } else
                m_string = null;
        } else
            throw new IOException("Wrong DER type, expected GeneralString");
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
        buf.packByte(DER.GeneralizedTime);

        if (m_string != null) {
            byte[] byts = m_string.getBytes();
            buf.packLength(byts.length);
            buf.packBytes(byts, 0, byts.length);
        } else
            buf.packLength(0);
    }

    /**
     * Return as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[GeneralizedTime:");
        str.append(m_string);
        str.append("]");

        return str.toString();
    }
}
