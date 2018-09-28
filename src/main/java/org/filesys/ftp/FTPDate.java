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

package org.filesys.ftp;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * FTP Date Utility Class
 *
 * @author gkspencer
 */
public class FTPDate {

    // Constants
    //
    // Six months in ticks
    protected final static long SIX_MONTHS = 183L * 24L * 60L * 60L * 1000L;

    // Month names
    protected final static String[] _months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov",
            "Dec"};

    // Machine listing date/time formatters
    protected final static SimpleDateFormat _mlstFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    protected final static SimpleDateFormat _mlstFormatLong = new SimpleDateFormat("yyyyMMddHHmmss.SSS");


    /**
     * Static initializer
     */
    static {

        // Set the formatters to UTC
        _mlstFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        _mlstFormatLong.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Pack a date string in Unix format
     * <p>
     * The format is 'Mmm dd hh:mm' if the file is less than six months old, else the format is 'Mmm
     * dd yyyy'.
     *
     * @param buf StringBuffer
     * @param dt  Date
     */
    public final static void packUnixDate(StringBuffer buf, Date dt) {

        // Check if the date is valid
        if (dt == null) {
            buf.append("------------");
            return;
        }

        // Get the time raw value
        long timeVal = dt.getTime();
        if (timeVal < 0) {
            buf.append("------------");
            return;
        }

        // Add the month name and date parts to the string
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.setTime(dt);
        buf.append(_months[cal.get(Calendar.MONTH)]);
        buf.append(" ");

        int dayOfMonth = cal.get(Calendar.DATE);
        if (dayOfMonth < 10)
            buf.append(" ");
        buf.append(dayOfMonth);
        buf.append(" ");

        // If the file is less than six months old we append the file time, else we append the year
        long timeNow = System.currentTimeMillis();
        if (Math.abs(timeNow - timeVal) > SIX_MONTHS) {

            // Append the year

            buf.append(" ");
            buf.append(cal.get(Calendar.YEAR));
        } else {

            // Append the file time as hh:mm
            int hr = cal.get(Calendar.HOUR_OF_DAY);
            if (hr < 10)
                buf.append("0");
            buf.append(hr);
            buf.append(":");

            int mins = cal.get(Calendar.MINUTE);
            if (mins < 10)
                buf.append("0");
            buf.append(mins);
        }
    }

    /**
     * Return a machine listing date/time, in the format 'YYYYMMDDHHSS'.
     *
     * @param dateTime long
     * @return String
     */
    public final static String packMlstDateTime(long dateTime) {
        return _mlstFormat.format(new Date(dateTime));
    }

    /**
     * Return a machine listing date/time, in the format 'YYYYMMDDHHSS.sss'.
     *
     * @param dateTime long
     * @return String
     */
    public final static String packMlstDateTimeLong(long dateTime) {
        return _mlstFormatLong.format(new Date(dateTime));
    }
}
