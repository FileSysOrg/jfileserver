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

import org.filesys.server.filesys.FileInfo;

/**
 * Database File Information Class
 *
 * @author gkspencer
 */
public class DBFileInfo extends FileInfo {

    //	Full file name
    private String m_fullName;

    /**
     * Class constructor
     */
    public DBFileInfo() {
        super();
    }

    /**
     * Class constructor
     *
     * @param name     String
     * @param fullName String
     * @param fid      int
     * @param did      int
     */
    public DBFileInfo(String name, String fullName, int fid, int did) {
        super();
        setFileName(name);
        setFullName(fullName);
        setFileId(fid);
        setDirectoryId(did);
    }

    /**
     * Return the full file path
     *
     * @return String
     */
    public final String getFullName() {
        return m_fullName;
    }

    /**
     * Set the full file path
     *
     * @param name String
     */
    public final void setFullName(String name) {
        m_fullName = name;
    }

    /**
     * Return the file information as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(super.toString());
        str.append(" - FID=");
        str.append(getFileId());
        str.append(",DID=");
        str.append(getDirectoryId());
        str.append("]");

        return str.toString();
    }
}
