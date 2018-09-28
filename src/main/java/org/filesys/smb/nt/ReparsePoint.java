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

package org.filesys.smb.nt;

/**
 * Reparse Point Class
 *
 * <p>Contains reparse point constants.
 *
 * @author gkspencer
 */
public class ReparsePoint {

    // Reparse point types
    public static final int TypeDFS         = 0x8000000A;
    public static final int TypeDFSR        = 0x80000012;
    public static final int TypeHSM         = 0xC0000004;
    public static final int TypeHSM2        = 0x80000006;
    public static final int TypeMountPoint  = 0xA0000003;
    public static final int TypeSIS         = 0x80000007;
    public static final int TypeSymLink     = 0xA000000C;

    /**
     * Return a reparse point type as a string
     *
     * @param typ int
     * @return String
     */
    public static final String getTypeAsString(int typ) {

        String typStr = "Unknown";

        switch (typ) {
            case TypeDFS:
                typStr = "V1DFS";
                break;
            case TypeDFSR:
                typStr = "DFSR";
                break;
            case TypeHSM:
                typStr = "HSM";
                break;
            case TypeHSM2:
                typStr = "HSM2";
                break;
            case TypeMountPoint:
                typStr = "MountPoint";
                break;
            case TypeSIS:
                typStr = "SIS";
                break;
            case TypeSymLink:
                typStr = "SymLink";
                break;
        }

        return typStr;
    }
}
