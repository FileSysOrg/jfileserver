/*
 * Copyright (C) 2021 GK Spencer
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
package org.filesys.server.filesys.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Filesystem Change Enum Class
 *
 * <p>Contains the available filesystem change types</p>
 *
 * @author gkspencer
 */
public enum FSChange {
    Created (0),
    Deleted (1),
    Modified (2),
    Renamed (3),
    Attributes (4),
    LastWrite (5),
    Security (6),

    Invalid (-1);

    private int _fsChange;

    // Mapping change type to id
    private static Map<Integer, FSChange> _idMap = new HashMap<>();

    // Mapping change type to short name
    private static String[] _idName = { "Cre", "Del", "Mod", "Ren", "Atr", "LWr", "Sec"};

    /**
     * Static initializer
     */
    static {
        for ( FSChange idTyp : FSChange.values())
            _idMap.put( idTyp.intValue(), idTyp);
    }

    /**
     * Enum constructor
     *
     * @param chg int
     */
    FSChange(int chg) { _fsChange = chg; }

    /**
     * Return the filesystem change type as an integer
     *
     * @return int
     */
    public final int intValue() { return _fsChange; }

    /**
     * Return the filesystem change type as a short name
     *
     * @return String
     */
    public final String shortName() {
        if ( _fsChange >= 0 && _fsChange < _idName.length)
            return _idName[ _fsChange];
        return null;
    }

    /**
     * Create a filesystem change type from an int
     *
     * @param typ int
     * @return FSChange
     */
    public static final FSChange fromInt(int typ) {

        if ( _idMap.containsKey( typ))
            return _idMap.get( typ);

        return Invalid;
    }

    /**
     * Create a filesystem change type from a short name
     *
     * @param shName String
     * @return FSChange
     */
    public static final FSChange fromShortName(String shName) {
        if ( shName == null || shName.length() < 3)
            return Invalid;

        int id = 0;

        while( id < _idName.length && _idName[ id].equalsIgnoreCase( shName) == false)
            id++;

        if ( id < _idName.length)
            return fromInt( id);
        return Invalid;
    }
}
