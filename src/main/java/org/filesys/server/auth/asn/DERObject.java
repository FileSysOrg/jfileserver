/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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
 * DER Object Class
 *
 * <p>Base class for ASN.1 DER encoded objects.
 *
 * @author gkspencer
 */
public abstract class DERObject {

    // Value to indicate that the object is not tagged
    public static final int NotTagged = -1;

    // Tag number, or -1 if not tagged
    private int m_tagNo = NotTagged;

    // Buffer position of the binary data
    private int m_bufPos;

    /**
     * Check if the object is tagged
     *
     * @return boolean
     */
    public final boolean isTagged() {
        return m_tagNo != -1 ? true : false;
    }

    /**
     * Return the tag number
     *
     * @return int
     */
    public final int getTagNo() {
        return m_tagNo;
    }

    /**
     * Set the tag number
     *
     * @param tagNo int
     */
    public final void setTagNo(int tagNo) {
        m_tagNo = tagNo;
    }

    /**
     * Return the buffer position of the object data
     *
     * @return int
     */
    public final int getBufferPosition() {
        return m_bufPos;
    }

    /**
     * Set the buffer position of the object data
     *
     * @param bufPos int
     */
    public final void setBufferPosition(int bufPos) {
        m_bufPos = bufPos;
    }

    /**
     * DER encode the object
     *
     * @param buf DERBuffer
     * @exception IOException Error encoding the DER object
     */
    public abstract void derEncode(DERBuffer buf)
            throws IOException;

    /**
     * DER decode the object
     *
     * @param buf DERBuffer
     * @exception IOException Error decoding the DER object
     */
    public abstract void derDecode(DERBuffer buf)
            throws IOException;
}
