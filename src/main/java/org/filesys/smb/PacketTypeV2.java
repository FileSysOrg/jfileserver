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

import java.util.HashMap;
import java.util.Map;

/**
 * SMB V2 Packet Types Class
 *
 * @author gkspencer
 */
public enum PacketTypeV2 {
    Negotiate       (0x0000),
    SessionSetup    (0x0001),
    Logoff          (0x0002),
    TreeConnect     (0x0003),
    TreeDisconnect  (0x0004),
    Create          (0x0005),
    Close           (0x0006),
    Flush           (0x0007),
    Read            (0x0008),
    Write           (0x0009),
    Lock            (0x000A),
    IOCtl           (0x000B),
    Cancel          (0x000C),
    Echo            (0x000D),
    QueryDirectory  (0x000E),
    ChangeNotify    (0x000F),
    QueryInfo       (0x0010),
    SetInfo         (0x0011),
    OplockBreak     (0x0012),

    Invalid         (0xFFFF);

    private final int pktType;

    // Mapping command name to id
    private static Map<Integer, PacketTypeV2> _typeMap = new HashMap<>();

    /**
     * Static initializer
     */
    static {
        for ( PacketTypeV2 typeV2 : PacketTypeV2.values())
            _typeMap.put( typeV2.intValue(), typeV2);
    }

    /**
     * Enum constructor
     *
     * @param typ int
     */
    PacketTypeV2(int typ) { pktType = typ; }

    /**
     * Return the packet type as an int
     *
     * @return int
     */
    public final int intValue() { return pktType; }

    /**
     * Create a packet type from an int
     *
     * @param typ int
     * @return PacketTypeV2
     */
    public static final PacketTypeV2 fromInt(int typ) {

        if ( _typeMap.containsKey( typ))
            return _typeMap.get( typ);

        return Invalid;
    }
}
