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

package org.filesys.smb;

/**
 * File Sharing Mode Class
 *
 * <p>Defines sharing mode constants used when opening a file
 *
 * @author gkspencer
 */
public enum SharingMode {
    NOSHARING       (0x0000),
    READ            (0x0001),
    WRITE           (0x0002),
    READ_WRITE      (0x0003),
    DELETE          (0x0004),
    READ_DELETE     (0x0005),
    WRITE_DELETE    (0x0006),

    ALL             (0x0007),

    INVALID         (-1);

    private final int shareMode;

    /**
     * Enum constructor
     *
     * @param mode int
     */
    SharingMode(int mode) { shareMode = mode; }

    /**
     * Return the sharing mode as an int
     *
     * @return int
     */
    public final int intValue() { return shareMode; }

    /**
     * Check for the read bit
     *
     * @return boolean
     */
    public final boolean hasRead() { return (shareMode & READ.intValue()) != 0 ? true : false; }

    /**
     * Check for the write bit
     *
     * @return boolean
     */
    public final boolean hasWrite() { return (shareMode & WRITE.intValue()) != 0 ? true : false; }

    /**
     * Check for the delete bit
     *
     * @return boolean
     */
    public final boolean hasDelete() { return (shareMode & DELETE.intValue()) != 0 ? true : false; }

    /**
     * Create a sharing mode from an int
     *
     * @param mode int
     * @return SharingMode
     */
    public static final SharingMode fromInt(int mode) {
        SharingMode shrMode = INVALID;

        switch( mode) {
            case 0x0000:
                shrMode = NOSHARING;
                break;
            case 0x0001:
                shrMode = READ;
                break;
            case 0x0002:
                shrMode = WRITE;
                break;
            case 0x0003:
                shrMode = READ_WRITE;
                break;
            case 0x0004:
                shrMode = DELETE;
                break;
            case 0x0005:
                shrMode = READ_DELETE;
                break;
            case 0x0006:
                shrMode = WRITE_DELETE;
                break;
            case 0x0007:
                shrMode = ALL;
                break;
        }

        return shrMode;
    }
}
