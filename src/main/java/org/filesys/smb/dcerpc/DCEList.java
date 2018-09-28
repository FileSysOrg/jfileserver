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

import java.util.ArrayList;
import java.util.List;

/**
 * DCE/RPC List Class
 *
 * <p>Base class for lists of objects that are DCE/RPC readable and/or writeable.
 *
 * @author gkspencer
 */
public abstract class DCEList {

    //	Information level
    private int m_infoLevel;

    //	List of DCE/RPC readable/writeable objects
    private List<Object> m_dceObjects;

    /**
     * Default constructor
     */
    protected DCEList() {
        m_dceObjects = new ArrayList<Object>();
    }

    /**
     * Class constructor
     *
     * @param infoLevel int
     */
    protected DCEList(int infoLevel) {
        m_dceObjects = new ArrayList<Object>();
        m_infoLevel = infoLevel;
    }

    /**
     * Class constructor
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    protected DCEList(DCEBuffer buf)
            throws DCEBufferException {

        //	Read the header from the DCE/RPC buffer that contains the information level and container pointer
        m_infoLevel = buf.getInt();
        buf.skipBytes(4);

        if (buf.getPointer() != 0) {

            //	Indicate that the container is valid
            m_dceObjects = new ArrayList<Object>();
        } else {

            //	Container is not valid, no more data to follow
            m_dceObjects = null;
        }
    }

    /**
     * Return the information level
     *
     * @return int
     */
    public final int getInformationLevel() {
        return m_infoLevel;
    }

    /**
     * Return the number of entries in the list
     *
     * @return int
     */
    public final int numberOfEntries() {
        return m_dceObjects != null ? m_dceObjects.size() : 0;
    }

    /**
     * Return the object list
     *
     * @return List of objects
     */
    public final List<Object> getList() {
        return m_dceObjects;
    }

    /**
     * Return an element from the list
     *
     * @param idx int
     * @return Object
     */
    public final Object getElement(int idx) {

        //	Range check the index
        if (m_dceObjects == null || idx < 0 || idx >= m_dceObjects.size())
            return null;

        //	Return the object
        return m_dceObjects.get(idx);
    }

    /**
     * Determine if the container is valid
     *
     * @return boolean
     */
    protected final boolean containerIsValid() {
        return m_dceObjects != null ? true : false;
    }

    /**
     * Add an object to the list
     *
     * @param obj Object
     */
    protected final void addObject(Object obj) {
        m_dceObjects.add(obj);
    }

    /**
     * Set the information level
     *
     * @param infoLevel int
     */
    protected final void setInformationLevel(int infoLevel) {
        m_infoLevel = infoLevel;
    }

    /**
     * Set the object list
     *
     * @param list List of objects
     */
    protected final void setList(List<Object> list) {
        m_dceObjects = list;
    }

    /**
     * Get a new object for the list to fill in
     *
     * @return DCEReadable
     */
    protected abstract DCEReadable getNewObject();

    /**
     * Read a list of objects from the DCE buffer
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public void readList(DCEBuffer buf)
            throws DCEBufferException {

        //	Check if the container is valid, if so the object list will be valid
        if (containerIsValid() == false)
            return;

        //	Read the container object count and array pointer
        int numEntries = buf.getInt();
        if (buf.getPointer() != 0) {

            //	Get the array element count
            int elemCnt = buf.getInt();

            if (elemCnt > 0) {

                //	Read in the array elements
                while (elemCnt-- > 0) {

                    //	Create a readable object and add to the list
                    DCEReadable element = getNewObject();
                    addObject(element);

                    //	Load the main object details
                    element.readObject(buf);
                }

                //	Load the strings for the readable information objects
                for (int i = 0; i < numberOfEntries(); i++) {

                    //	Get a readable object
                    DCEReadable element = (DCEReadable) getList().get(i);

                    //	Load the strings for the readable object
                    element.readStrings(buf);
                }
            }
        }
    }

    /**
     * Write the list of objects to a DCE buffer
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public final void writeList(DCEBuffer buf)
            throws DCEBufferException {

        //	Pack the container header
        buf.putInt(getInformationLevel());
        buf.putInt(getInformationLevel());

        //	Check if the object list is valid
        if (m_dceObjects != null) {

            //	Add a pointer to the container and the number of objects
            buf.putPointer(true);
            buf.putInt(m_dceObjects.size());

            //	Add the pointer to the array of objects and number of objects
            buf.putPointer(true);
            buf.putInt(m_dceObjects.size());

            //	Create a seperate DCE buffer to build the string list which may follow the main
            //	object list, depending on the object
            DCEBuffer strBuf = new DCEBuffer();

            //	Pack the object information
            for (int i = 0; i < m_dceObjects.size(); i++) {

                //	Get an object from the list
                DCEWriteable object = (DCEWriteable) m_dceObjects.get(i);

                //	Write the object to the buffer, strings may go into the seperate string buffer which will be appended
                //	to the main buffer after all the objects have been written
                object.writeObject(buf, strBuf);
            }

            //	If the string buffer has been used append it to the main buffer
            buf.putBuffer(strBuf);

            //	Add the trailing list size
            buf.putInt(m_dceObjects.size());

            //	Add the enum handle
            buf.putInt(0);
        } else {

            //	Add an empty container/array
            buf.putZeroInts(4);
        }
    }

    /**
     * Return the list as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[Level=");
        str.append(getInformationLevel());
        str.append(",Entries=");
        str.append(numberOfEntries());
        str.append(",Class=");
        str.append(getNewObject().getClass().getName());
        str.append("]");

        return str.toString();
    }
}
