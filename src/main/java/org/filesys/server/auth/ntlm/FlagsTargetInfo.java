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
 * Flags Target Information Class
 *
 * @author gkspencer
 */
public class FlagsTargetInfo extends TargetInfo {

    //  Flags value
    private int m_value;

    /**
     * Default constructor
     */
    public FlagsTargetInfo() {
        super( Type.FLAGS);
    }

    /**
     * Class constructor
     *
     * @param flagsVal int
     */
    public FlagsTargetInfo(int flagsVal) {
        super( Type.FLAGS);

        m_value = flagsVal;
    }

    /**
     * Return the target information value
     *
     * @return int
     */
    public final int getValue() {
        return m_value;
    }

    /**
     * Return the target information value as string
     *
     * @return String
     */
    public String valueAsString() {
        return "0x" + Integer.toHexString( m_value);
    }
}
