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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * DER Sequence Class
 *
 * <p>Class to hold a list of DERObjects.
 *
 * @author gkspencer
 */
public class DERSequence extends DERObject {

    // List of objects
    private List<DERObject> m_list;

    /**
     * Default constructor
     */
    public DERSequence() {
        m_list = new ArrayList<DERObject>();
    }

    /**
     * Class constructor
     *
     * @param tagNo int
     */
    public DERSequence(int tagNo) {
        setTagNo(tagNo);
        m_list = new ArrayList<DERObject>();
    }

    /**
     * Return the count of objects in the sequence
     *
     * @return int
     */
    public final int numberOfObjects() {
        return m_list.size();
    }

    /**
     * Return an object from the sequence
     *
     * @param idx int
     * @return DERObject
     */
    public final DERObject getObjectAt(int idx) {
        DERObject derObj = null;

        if (idx >= 0 && idx < m_list.size())
            derObj = m_list.get(idx);

        return derObj;
    }

    /**
     * Find a tagged object within the list
     *
     * @param tagNo int
     * @return DERObject
     */
    public final DERObject getTaggedObject(int tagNo) {

        // Check if the list is empty
        if (m_list == null || m_list.size() == 0)
            return null;

        // Search for the required tagged object
        for (int i = 0; i < m_list.size(); i++) {
            DERObject derObj = m_list.get(i);

            if (derObj.getTagNo() == tagNo)
                return derObj;
        }

        return null;
    }

    /**
     * Add an object to the sequence
     *
     * @param derObj DERObject
     */
    public final void addObject(DERObject derObj) {
        m_list.add(derObj);
    }

    /**
     * DER decode the object
     *
     * @param buf DERBuffer
     */
    public void derDecode(DERBuffer buf)
            throws IOException {

        // Get the object type
        int typ = buf.unpackType();
        if (DER.isType(typ) != DER.Sequence)
            throw new IOException("Wrong DER type, expected Sequence");

        // Get the sequence length and current buffer position
        int len = buf.unpackLength();
        int pos = buf.getPosition();

        // Clear the list and tag number
        m_list.clear();
        setTagNo(NotTagged);

        // Read objects from the buffer and add to the sequence
        if (len > 0) {

            // Read objects until sequence length data has been read
            while ((buf.getPosition() - pos) < len) {

                // Read an object from the buffer and add to the sequence
                DERObject obj = buf.unpackObject();
                addObject(obj);
            }
        }
    }

    /**
     * DER encode the object
     *
     * @param buf DERBuffer
     */
    public void derEncode(DERBuffer buf)
            throws IOException {

        // Pack the elements into a seperate buffer to get the total length
        int totLen = 0;
        DERBuffer objBuf = new DERBuffer();

        if (numberOfObjects() > 0) {

            for (int i = 0; i < numberOfObjects(); i++) {

                // Pack the current object
                DERObject derObj = getObjectAt(i);
                objBuf.packObject(derObj);
            }

            // Get the total length for the sequence
            totLen = objBuf.getLength();
        }

        // Pack the data type
        buf.packByte(DER.Sequence + DER.Constructed);

        buf.packLength(totLen);
        if (totLen > 0)
            buf.packBytes(objBuf);
    }

    /**
     * Enumerate the list
     *
     * @return Iterator
     */
    public final Iterator<DERObject> getObjects() {
        return m_list.iterator();
    }

    /**
     * Return the sequence as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[Sequence:");
        str.append(numberOfObjects());
        str.append("]");

        return str.toString();
    }
}
