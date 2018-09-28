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

package org.filesys.oncrpc.mount;

/**
 * Mount Entry Class
 *
 * <p>Contains the details of an active NFS mount.
 *
 * @author gkspencer
 */
public class MountEntry {

    //	Remote host name/address
    private String m_host;

    //	Mount path
    private String m_path;

    /**
     * Class constructor
     *
     * @param host String
     * @param path String
     */
    public MountEntry(String host, String path) {
        m_host = host;
        m_path = path;
    }

    /**
     * Return the host name/address
     *
     * @return String
     */
    public final String getHost() {
        return m_host;
    }

    /**
     * Return the mount path
     *
     * @return String
     */
    public final String getPath() {
        return m_path;
    }

    /**
     * Return the mount entry as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(getHost());
        str.append(":");
        str.append(getPath());
        str.append("]");

        return str.toString();
    }
}
