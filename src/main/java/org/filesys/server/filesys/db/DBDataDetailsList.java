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

package org.filesys.server.filesys.db;

import java.util.ArrayList;
import java.util.List;

/**
 * Database Data Details List Class
 *
 * <p>Contains a list of DBDataDetail objects.
 *
 * @author gkspencer
 */
public class DBDataDetailsList {

    //	List of database file/stream id details
    private List<DBDataDetails> m_list;

    /**
     * Default constructor
     */
    public DBDataDetailsList() {

        //	Allocate the list
        m_list = new ArrayList<DBDataDetails>();
    }

    /**
     * Return the number of files in the list
     *
     * @return int
     */
    public final int numberOfFiles() {
        return m_list.size();
    }

    /**
     * Add file details to the list
     *
     * @param details DBDataDetails
     */
    public final void addFile(DBDataDetails details) {
        m_list.add(details);
    }

    /**
     * Return the file details at the specified index
     *
     * @param idx int
     * @return DBDataDetails
     */
    public final DBDataDetails getFileAt(int idx) {
        if (idx < 0 || idx >= m_list.size())
            return null;
        return m_list.get(idx);
    }

    /**
     * Remove the file at the specified index within the list
     *
     * @param idx int
     * @return DBDataDetails
     */
    public final DBDataDetails removeFileAt(int idx) {
        if (idx < 0 || idx >= m_list.size())
            return null;
        DBDataDetails dbDetails = m_list.get(idx);
        m_list.remove(idx);
        return dbDetails;
    }

    /**
     * Clear the file details from the list
     */
    public final void remoteAllFiles() {
        m_list.clear();
    }
}
