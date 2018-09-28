/*
 * Copyright (C) 2006-2011 Alfresco Software Limited.
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

package org.filesys.server.filesys.cache;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.FileAccessToken;

/**
 * Local File Access Token Class
 *
 * @author gkspencer
 */
public class LocalFileAccessToken implements FileAccessToken {

    // Use the request process id
    private int m_pid;

    // Attributes only file access
    private boolean m_attribOnly;

    // Access token has been released
    private transient boolean m_released = false;

    /**
     * Class constructor
     *
     * @param pid int
     */
    public LocalFileAccessToken(int pid) {
        m_pid = pid;
    }

    /**
     * Return the process id
     *
     * @return int
     */
    public final int getProcessId() {
        return m_pid;
    }

    /**
     * Check if the access token has been released
     *
     * @return boolean
     */
    public final boolean isReleased() {
        return m_released;
    }

    /**
     * Set the released state of the access token
     *
     * @param released boolean
     */
    public final void setReleased(boolean released) {
        m_released = released;
    }

    /**
     * Check if the access token is on attributes only file open
     *
     * @return boolean
     */
    public final boolean isAttributesOnly() {
        return m_attribOnly;
    }

    /**
     * Set/clear the attributes only flag
     *
     * @param attrOnly boolean
     */
    public final void setAttributesOnly(boolean attrOnly) {
        m_attribOnly = attrOnly;
    }

    /**
     * Return the access token as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Token pid=");
        str.append(getProcessId());

        if (isAttributesOnly())
            str.append(",AttribOnly");

        if (isReleased())
            str.append(",Released");
        str.append("]");

        return str.toString();
    }

    /**
     * Finalize
     */
    public void finalize() {

        // Check if the access token was released
        if (isReleased() == false)
            Debug.println("** Access token finalized, not released, " + toString() + " **");
    }
}
