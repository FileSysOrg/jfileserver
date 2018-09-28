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

package org.filesys.smb.dcerpc.client;

/**
 * DCE/RPC Enumeration Resume Id Class
 *
 * <p>
 * Contains the resume id integer value used to continue DCE/RPC requests that return large amounts
 * of data.
 *
 * @author gkspencer
 */
public class ResumeId {

    // Resume id
    private int m_resumeId;

    /**
     * Default constructor
     */
    public ResumeId() {
        m_resumeId = 0;
    }

    /**
     * Class constructor
     *
     * @param resId int
     */
    public ResumeId(int resId) {
        m_resumeId = resId;
    }

    /**
     * Return the resume id
     *
     * @return int
     */
    public final int getResumeId() {
        return m_resumeId;
    }

    /**
     * Set the resume id
     *
     * @param resId int
     */
    public final void setResumeId(int resId) {
        m_resumeId = resId;
    }

    /**
     * Return the resume id as a string
     *
     * @return String
     */
    public String toString() {
        return Integer.toString(m_resumeId);
    }
}
