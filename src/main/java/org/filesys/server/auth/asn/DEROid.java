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
import java.util.StringTokenizer;

/**
 * DER Object Identifier Class
 *
 * @author gkspencer
 */
public class DEROid extends DERObject {

    // Object identifier string
    private String m_oid;

    /**
     * Default constructor
     */
    public DEROid() {
    }

    /**
     * Class constructor
     *
     * @param oid String
     */
    public DEROid(String oid) {
        m_oid = oid;
    }

    /**
     * Return the object identifier string
     *
     * @return String
     */
    public final String getOid() {
        return m_oid;
    }

    /**
     * DER decode the object
     *
     * @param buf DERBuffer
     * @exception IOException Error coding the DER object
     */
    public void derDecode(DERBuffer buf)
            throws IOException {

        // Make sure the object type is an Oid
        if (buf.unpackType() == DER.ObjectIdentifier) {

            // Get the Oid length
            int len = buf.unpackLength();

            long oidVal = 0L;
            StringBuffer oidStr = new StringBuffer();

            for (int i = 0; i < len; i++) {

                // Get a byte from the Oid data, and add the bottom 7 bits to the current value
                int byt = buf.unpackByte();

                oidVal = (oidVal << 7) + (byt & 0x7F);

                // Check if this is the last byte for the current value
                if ((byt & 0x80) == 0) {

                    // Add the prefix for the first value
                    if (oidStr.length() == 0) {
                        switch ((int) (oidVal / 40)) {
                            case 0:
                                oidStr.append("0");
                                break;
                            case 1:
                                oidStr.append("1");
                                oidVal -= 40;
                                break;
                            default:
                                oidStr.append("2");
                                oidVal -= 80;
                                break;
                        }
                    }

                    // Append the current value
                    oidStr.append(".");
                    oidStr.append(Long.toString(oidVal));

                    // Reset the current Oid value
                    oidVal = 0L;
                }
            }

            // Set the Oid string
            m_oid = oidStr.toString();
        } else
            throw new IOException("Invalid type in buffer, not ObjectIdentifier");
    }

    /**
     * DER encode the object
     *
     * @param buf DERBuffer
     * @exception IOException Error encoding the DER object
     */
    public void derEncode(DERBuffer buf)
            throws IOException {

        // Pack the data type and skip the length, save the buffer position for the length
        buf.packByte(DER.ObjectIdentifier);

        int lenPos = buf.getPosition();
        buf.packByte(0);

        // Split the OID into seperate fields and encode
        StringTokenizer tok = new StringTokenizer(m_oid, ".");

        packOidField(buf, (Integer.parseInt(tok.nextToken()) * 40) + Integer.parseInt(tok.nextToken()));

        while (tok.hasMoreTokens()) {
            packOidField(buf, Integer.parseInt(tok.nextToken()));
        }

        // Pack the length
        int len = buf.getPosition() - (lenPos + 1);
        buf.packByteAt(lenPos, len);
    }

    /**
     * Pack an OID field value in ASN.1 format
     *
     * @param buf    DERBuffer
     * @param oidVal long
     * @exception IOException Error encoding the DER object
     */
    private void packOidField(DERBuffer buf, long oidVal)
            throws IOException {
        int fieldShift = 56;

        while (fieldShift > 0) {
            if (oidVal > (1L << fieldShift))
                buf.packByte((int) (oidVal >> fieldShift) | 0x80);
            fieldShift -= 7;
        }
        buf.packByte((int) (oidVal & 0x7F));
    }

    /**
     * Return the OID as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[Oid:");
        str.append(m_oid);
        str.append("]");

        return str.toString();
    }
}
