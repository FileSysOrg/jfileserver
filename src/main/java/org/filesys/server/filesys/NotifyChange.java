/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.server.filesys;

import java.util.EnumSet;
import java.util.Set;

/**
 * Notify Change Enum Class
 *
 * @author gkspencer
 */
public enum NotifyChange {
    FileName        (0x0001),
    DirectoryName   (0x0002),
    Attributes      (0x0004),
    Size            (0x0008),
    LastWrite       (0x0010),
    LastAccess      (0x0020),
    Creation        (0x0040),
    ExtendedAttr    (0x0080),
    Security        (0x0100),
    StreamName      (0x0200),
    StreamSize      (0x0400),
    StreamWrite     (0x0800);

    private final int changeTyp;

    /**
     * Enum condstructor
     *
     * @param typ int
     */
    NotifyChange(int typ) { changeTyp = typ; }

    /**
     * Convert an int value into a set of NotifyChange flags
     *
     * @param ival int
     * @return Set of change notifications
     */
    public static final Set<NotifyChange> setFromInt(int ival) {

        Set<NotifyChange> changeSet = EnumSet.noneOf( NotifyChange.class);
        int mask = 0x0001;

        while ( mask <= 0x0800) {

            // Check if the current bit is set
            if (( ival & mask) != 0) {
                switch ( mask) {
                    case 0x0001:
                        changeSet.add( NotifyChange.FileName);
                        break;
                    case 0x0002:
                        changeSet.add( NotifyChange.DirectoryName);
                        break;
                    case 0x0004:
                        changeSet.add( NotifyChange.Attributes);
                        break;
                    case 0x0008:
                        changeSet.add( NotifyChange.Size);
                        break;
                    case 0x010:
                        changeSet.add( NotifyChange.LastWrite);
                        break;
                    case 0x0020:
                        changeSet.add( NotifyChange.LastAccess);
                        break;
                    case 0x0040:
                        changeSet.add( NotifyChange.Creation);
                        break;
                    case 0x080:
                        changeSet.add( NotifyChange.ExtendedAttr);
                        break;
                    case 0x0100:
                        changeSet.add( NotifyChange.Security);
                        break;
                    case 0x0200:
                        changeSet.add( NotifyChange.StreamName);
                        break;
                    case 0x0400:
                        changeSet.add( NotifyChange.StreamSize);
                        break;
                    case 0x0800:
                        changeSet.add( NotifyChange.StreamWrite);
                        break;
                }
            }

            // Next bit
            mask <<= 1;
        }

        return changeSet;
    }
}
