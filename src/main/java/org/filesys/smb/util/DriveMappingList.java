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

package org.filesys.smb.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Network Drive Mapping List Class
 *
 * @author gkspencer
 */
public class DriveMappingList {

    //	List of network drive mappings
    private List<DriveMapping> m_mappings;

    /**
     * Default constructor
     */
    public DriveMappingList() {
        m_mappings = new ArrayList<DriveMapping>();
    }

    /**
     * Add a drive mapping to the list
     *
     * @param mapping DriveMapping
     */
    public final void addMapping(DriveMapping mapping) {
        m_mappings.add(mapping);
    }

    /**
     * Return the count of mappings in the list
     *
     * @return int
     */
    public final int numberOfMappings() {
        return m_mappings.size();
    }

    /**
     * Return the required drive mapping
     *
     * @param idx int
     * @return DriveMapping
     */
    public final DriveMapping getMappingAt(int idx) {
        if (idx < 0 || idx >= m_mappings.size())
            return null;
        return (DriveMapping) m_mappings.get(idx);
    }

    /**
     * Find the mapping for the specified local drive
     *
     * @param localDrive String
     * @return DriveMapping
     */
    public final DriveMapping findMapping(String localDrive) {

        //	Search the drive mappings list
        for (int i = 0; i < m_mappings.size(); i++) {

            //	Get the current drive mapping
            DriveMapping driveMap = (DriveMapping) m_mappings.get(i);

            if (driveMap.getLocalDrive().equalsIgnoreCase(localDrive))
                return driveMap;
        }

        //	Drive mapping not found
        return null;
    }

    /**
     * Remove a drive mapping from the list
     *
     * @param idx int
     */
    public final void removeMapping(int idx) {
        if (idx < 0 || idx >= m_mappings.size())
            return;
        m_mappings.remove(idx);
    }

    /**
     * Remove all mappings from the list
     */
    public final void removeAllMappings() {
        m_mappings.clear();
    }
}
