/*
 * Copyright (C) 2024 GK Spencer
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
package org.filesys.util;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * File Date/Time Formatting Class
 *
 * <p>Format various file date/time values</p>
 */
public class FileDateTime {

    /**
     * Format a file date/time millisecond value
     *
     * @param fTime long
     * @return String
     */
    public static String longToString( long fTime) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone( TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis( fTime);

        return String.format("%02d-%02d-%04d %02d:%02d:%02d", cal.get( Calendar.DAY_OF_MONTH), cal.get( Calendar.MONTH), cal.get( Calendar.YEAR),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));
    }
}
