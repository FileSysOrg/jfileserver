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

package org.filesys.smb;

import java.util.ArrayList;
import java.util.List;

/**
 * PC share list class.
 *
 * <p>The PCShareList class contains a list of PCShare objects.
 *
 * @author gkspencer
 */
public class PCShareList implements java.io.Serializable {

    private static final long serialVersionUID = 6318830926098970791L;

    //	Vector used to store the PCShare objects
    private List<PCShare> m_list;

    /**
     * Class constructor
     */
    public PCShareList() {
        m_list = new ArrayList<PCShare>();
    }

    /**
     * Add a PCShare to the list
     *
     * @param shr PCShare object to be added to the list
     */
    public final void addPCShare(PCShare shr) {
        m_list.add(shr);
    }

    /**
     * Clear the list of PCShare objects
     */
    public final void clearList() {
        m_list.clear();
    }

    /**
     * Return the required PCShare object from the list.
     *
     * @param idx Index of the PCShare to be returned
     * @return PCShare
     * @throws java.lang.ArrayIndexOutOfBoundsException If the index is not valid
     */
    public final PCShare getPCShare(int idx)
            throws ArrayIndexOutOfBoundsException {

        //  Bounds check the index
        if (idx >= m_list.size())
            throw new ArrayIndexOutOfBoundsException();

        //  Return the required share information
        return m_list.get(idx);
    }

    /**
     * Return the number of PCShare objects that are in this list.
     *
     * @return Number of PCShare objects in the list.
     */
    public final int NumberOfPCShares() {
        return m_list.size();
    }
}
