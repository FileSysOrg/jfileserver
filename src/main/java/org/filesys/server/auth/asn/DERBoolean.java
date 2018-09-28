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
 * DER Boolean Class
 *
 * @author gkspencer
 */
public class DERBoolean extends DERObject {

    // Object value
    private boolean m_bool;

    /**
     * Default constructor
     */
    public DERBoolean() {
    }

    /**
     * Class constructor
     *
     * @param bool boolean
     */
    public DERBoolean(boolean bool) {
        m_bool = bool;
    }

    /**
     * Return the boolean value
     *
     * @return boolean
     */
    public final boolean getValue() {
        return m_bool;
    }

    /**
     * Decode the object
     *
     * @param buf DERBuffer
     * @exception IOException Error decoding the DER object
     */
    public void derDecode(DERBuffer buf) throws IOException {

        // Decode the type
        if (buf.unpackType() == DER.Boolean) {

            // Unpack the length and value
            buf.unpackByte();
            m_bool = buf.unpackByte() == 0xFF ? true : false;
        } else
            throw new IOException("Wrong DER type, expected Boolean");
    }

    /**
     * Encode the object
     *
     * @param buf DERBuffer
     * @exception IOException Error encoding the DER object
     */
    public void derEncode(DERBuffer buf) throws IOException {

        // Pack the type, length and value
        buf.packByte(DER.Boolean);
        buf.packByte(1);
        buf.packByte(m_bool ? 0xFF : 0);
    }

    /**
     * Return the boolean as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[Boolean:");
        str.append(m_bool);
        str.append("]");

        return str.toString();
    }
}
