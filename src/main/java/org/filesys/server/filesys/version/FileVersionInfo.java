/*
 * Copyright (C) 2018-2019 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.server.filesys.version;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * File Version Information Class
 *
 * <p>Contains the details of a previous version of a file</p>
 */
public class FileVersionInfo implements Comparable<FileVersionInfo> {

    // Date/time formatter
    private static SimpleDateFormat _dateTimeFormat = new SimpleDateFormat( "YYYY.MM.dd-HH.mm.ss");

    // Timestamp of the previous file version
    private long m_timestamp;

    /**
     * Class constructor
     *
     * @param tstamp Previous version timestamp
     */
    public FileVersionInfo( long tstamp) {

        m_timestamp = tstamp;
    }

    /**
     * Return the previous version timestamp
     *
     * @return long
     */
    public final long getTimestamp() {
        return m_timestamp;
    }

    /**
     * Return the file version information as a string
     *
     * @return String
     */
    public final String toString() {
        return _dateTimeFormat.format( new Date( m_timestamp));
    }

    /**
     * Compare file version info objects
     *
     * @param verInfo FileVersionInfo
     * @return int
     */
    public int compareTo(FileVersionInfo verInfo) {
        if ( getTimestamp() < verInfo.getTimestamp())
            return -1;
        else if ( getTimestamp() == verInfo.getTimestamp())
            return 0;
        else
            return 1;
    }
}
