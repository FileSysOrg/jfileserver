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

package org.filesys.server.config;

import java.util.Date;
import java.util.TimeZone;

/**
 * Global Server Configuration Section Class
 *
 * @author gkspencer
 */
public class GlobalConfigSection extends ConfigSection {

    // Global configuration section name
    public static final String SectionName = "Global";

    //  Timezone name and offset from UTC in minutes
    private String m_timeZone;
    private int m_tzOffset;

    /**
     * Class constructor
     *
     * @param config ServerConfiguration
     */
    public GlobalConfigSection(ServerConfiguration config) {
        super(SectionName, config);
    }

    /**
     * Return the timezone name
     *
     * @return String
     */
    public final String getTimeZone() {
        return m_timeZone;
    }

    /**
     * Return the timezone offset from UTC in seconds
     *
     * @return int
     */
    public final int getTimeZoneOffset() {
        return m_tzOffset;
    }

    /**
     * Set the server timezone name
     *
     * @param name String
     * @return int
     * @exception InvalidConfigurationException Error setting the timezone
     */
    public final int setTimeZone(String name)
            throws InvalidConfigurationException {

        //  Validate the timezone
        TimeZone tz = TimeZone.getTimeZone(name);
        if (tz == null)
            throw new InvalidConfigurationException("Invalid timezone, " + name);

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.ServerTimezone, name);

        //  Get the daylight savings value
        int dst = 0;
        if (tz.inDaylightTime(new Date()))
            dst = tz.getDSTSavings();

        //  Set the timezone name and offset from UTC in minutes
        //
        //  Invert the result of TimeZone.getRawOffset() as SMB/CIFS requires positive minutes west of UTC
        m_timeZone = name;
        m_tzOffset = -((tz.getRawOffset() + dst) / 60000);

        //  Return the change status
        return sts;
    }

    /**
     * Set the timezone offset from UTC in seconds (+/-)
     *
     * @param offset int
     * @return int
     * @exception InvalidConfigurationException Error setting the timezone offset
     */
    public final int setTimeZoneOffset(int offset)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.ServerTZOffset, new Integer(offset));
        m_tzOffset = offset;

        //  Return the change status
        return sts;
    }
}
