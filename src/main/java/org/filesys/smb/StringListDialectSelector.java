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

package org.filesys.smb;

import org.filesys.util.StringList;

/**
 * String List Dialector Selector class
 *
 * <p>Adds a StringList with the original list of dialects sent by the client so that the selected dialect can
 * be mapped to the index of the dialect string</p>
 *
 * @author gkspencer
 */
public class StringListDialectSelector extends DialectSelector {

    // Original list of client order dialect strings
    private StringList m_dialectStrs;

    /**
     * Class constructor
     */
    public StringListDialectSelector() {
        super();

        m_dialectStrs = new StringList();
    }

    /**
     * Add a dialect with the client string
     *
     * @param diaId int
     * @param diaStr String
     */
    public final void AddDialectAndString( int diaId, String diaStr) {

        // Dialect id may be invalid, but we still need to build the original dialect string list, in the same order
        if ( diaId != Dialect.Unknown)
            AddDialect( diaId);

        m_dialectStrs.addString( diaStr);
    }

    /**
     * Convert a dialect id into a string index
     *
     * @param diaId int
     * @return int
     */
    public final int getStringIndexForDialect( int diaId) {

        // Get the dialect string
        String diaStr = Dialect.DialectTypeString( diaId);
        int idx = -1;

        if ( diaStr != null) {
            idx = m_dialectStrs.findString( diaStr);
        }

        return idx;
    }

    /**
     * Return the dialect selector list as a string.
     *
     * @return String
     */
    public String toString() {

        //  Create a string buffer to build the return string
        StringBuffer str = new StringBuffer();
        str.append("[dialects=");
        str.append( super.toString());
        str.append( " strs=");
        str.append( m_dialectStrs.toString());
        str.append("]");

        return str.toString();
    }
}
