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

package org.filesys.smb.nt;

import java.util.ArrayList;
import java.util.List;

/**
 * Security Id List Class
 *
 * <p>Contains a list of SID objects.
 *
 * @author gkspencer
 */
public class SIDList {

    //	List of RID objects
    private List<SID> m_list;

    /**
     * Default constructor
     */
    public SIDList() {
        m_list = new ArrayList<SID>();
    }

    /**
     * Add a SRID to the list
     *
     * @param sid SID
     */
    public final void addSID(SID sid) {
        m_list.add(sid);
    }

    /**
     * Return a SID from the list
     *
     * @param idx int
     * @return SID
     */
    public final SID getSIDAt(int idx) {
        if (idx < 0 || idx >= m_list.size())
            return null;
        return m_list.get(idx);
    }

    /**
     * Return the number of SIDs in the list
     *
     * @return int
     */
    public final int numberOfSIDs() {
        return m_list.size();
    }

    /**
     * Find the SID with the specified name
     *
     * @param name String
     * @return SID
     */
    public final SID findSID(String name) {

        //	Search for the required SID
        for (int i = 0; i < m_list.size(); i++) {

            //	Get the current SID
            SID curSID = m_list.get(i);

            if (curSID.hasName() && curSID.getName().equals(name))
                return curSID;
        }

        //	SID not found
        return null;
    }

    /**
     * Find the SID that matches the specified SID
     *
     * @param sid SID
     * @return SID
     */
    public final SID findSID(SID sid) {

        //	Search for the required SID
        for (int i = 0; i < m_list.size(); i++) {

            //	Get the current SID
            SID curSID = m_list.get(i);

            if (curSID.equalsSID(sid))
                return curSID;
        }

        //	SID not found
        return null;
    }

    /**
     * Remove a SID from the list
     *
     * @param sid SID
     * @return SID
     */
    public final SID removeSID(SID sid) {

        //	Search for the SID in the list
        for (int i = 0; i < m_list.size(); i++) {

            //	Get the current SID from the list
            SID curSID = m_list.get(i);
            if (curSID.equalsSID(sid)) {

                //	Remove the SID from the list
                m_list.remove(i);

                //	Return the SID
                return curSID;
            }
        }

        //	SID not found, return null
        return null;
    }

    /**
     * Remove all SIDs from the list
     */
    public final void removeAllSIDs() {
        m_list.clear();
    }
}
