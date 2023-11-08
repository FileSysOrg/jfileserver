/*
 * Copyright (C) 2023 GK Spencer
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

package org.filesys.smb.server;

import java.util.HashMap;
import java.util.Map;

/**
 * File Alignment Enum Class
 *
 * <p>Contains the constants for the FileAlignmentInformation level as per MS-FSCC 2.4.3</p>
 */
public enum FileAlignment {
    Byte    (0x00000000),
    Word    (0x00000001),
    Long    (0x00000003),
    Quad    (0x00000007),
    Octa    (0x0000000F),
    Byte32  (0x0000001F),
    Byte64  (0x0000003F),
    Byte128 (0x0000007F),
    Byte256 (0x000000FF),
    Byte512 (0x000001FF),

    Invalid (-1);

    private final int _align;

    // Mapping command name to id
    private static Map<Integer, FileAlignment> _alignMap = new HashMap<>();

    /**
     * Static initializer
     */
    static {
        for ( FileAlignment align : FileAlignment.values())
            _alignMap.put( align.intValue(), align);
    }

    /**
     * Enum constructor
     *
     * @param align int
     */
    FileAlignment(int align) { _align = align; }

    /**
     * Return the file alignment as an int
     *
     * @return int
     */
    public final int intValue() { return _align; }

    /**
     * Create a file alignment from an int
     *
     * @param align int
     * @return FileAlignment
     */
    public static final FileAlignment fromInt(int align) {

        if ( _alignMap.containsKey( align))
            return _alignMap.get( align);

        return Invalid;
    }
}
