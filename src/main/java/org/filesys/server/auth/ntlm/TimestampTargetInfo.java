/*
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.server.auth.ntlm;

/**
 * @author gkspencer
 */
public class TimestampTargetInfo extends TargetInfo {

    //  Timestamp value
    private long m_value;

    /**
     * Default constructor
     */
    public TimestampTargetInfo() {
        super( Type.TIMESTAMP);

        m_value = System.currentTimeMillis();
    }

    /**
     * Class constructor
     *
     * @param timeVal long
     */
    public TimestampTargetInfo(long timeVal) {
        super( Type.TIMESTAMP);

        m_value = timeVal;
    }

    /**
     * Return the target information value
     *
     * @return long
     */
    public final long getValue() {
        return m_value;
    }

    /**
     * Return the target information value as string
     *
     * @return String
     */
    public String valueAsString() {
        return Long.toString( m_value);
    }
}
