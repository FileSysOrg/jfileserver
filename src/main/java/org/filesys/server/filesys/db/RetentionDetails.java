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

import java.util.Date;


/**
 * File Retention Details Class
 *
 * <p>Contains the start and end date/times that a file/folder is to be retained. During the retention period
 * the file/folder may not be deleted or modified.
 *
 * @author gkspencer
 */
public class RetentionDetails {

    //	File id
    private int m_fid;

    //	Retention start/end date/time
    private long m_startRetain;
    private long m_endRetain;

    /**
     * Class constructor
     *
     * @param fid     int
     * @param endTime long
     */
    public RetentionDetails(int fid, long endTime) {
        m_fid = fid;

        m_startRetain = -1L;
        m_endRetain = endTime;
    }

    /**
     * Class constructor
     *
     * @param fid       int
     * @param startTime long
     * @param endTime   long
     */
    public RetentionDetails(int fid, long startTime, long endTime) {
        m_fid = fid;

        m_startRetain = startTime;
        m_endRetain = endTime;
    }

    /**
     * Return the file id
     *
     * @return int
     */
    public final int getFileId() {
        return m_fid;
    }

    /**
     * Check if a start date/time is set
     *
     * @return boolean
     */
    public final boolean hasStartTime() {
        return m_startRetain != -1L ? true : false;
    }

    /**
     * Return the start of retention date/time
     *
     * @return long
     */
    public final long getStartTime() {
        return m_startRetain;
    }

    /**
     * Return the end of retention date/time
     *
     * @return long
     */
    public final long getEndTime() {
        return m_endRetain;
    }

    /**
     * Check if the file is within the retention period
     *
     * @param timeNow long
     * @return boolean
     */
    public final boolean isWithinRetentionPeriod(long timeNow) {
        if ((hasStartTime() == false || timeNow >= getStartTime()) &&
                timeNow <= getEndTime())
            return true;
        return false;
    }

    /**
     * Return the retention period as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[FID=");
        str.append(getFileId());
        str.append(",Start=");
        if (hasStartTime())
            str.append(new Date(getStartTime()));
        else
            str.append("NotSet");
        str.append(",End=");
        str.append(new Date(getEndTime()));
        str.append("]");

        return str.toString();
    }
}
