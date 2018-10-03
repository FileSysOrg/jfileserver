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
 * String Value Target Information class
 *
 * @author gkspencer
 */
public class StringTargetInfo extends TargetInfo {

    //  String value
    private String m_value;

    /**
     * Class constructor
     *
     * @param typ TargetInfo.Type
     * @param strVal String
     */
    public StringTargetInfo(TargetInfo.Type typ, String strVal) {
        super( typ);

        m_value = strVal;
    }

    /**
     * Return the target information value
     *
     * @return String
     */
    public final String getValue() {
        return m_value;
    }

    /**
     * Return the target information value as string
     *
     * @return String
     */
    public String valueAsString() {
        return getValue();
    }
}
