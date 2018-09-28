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

package org.filesys.server.filesys;

/**
 * File Access Class
 *
 * <p>Contains a list of the available file permissions that may be applied to a share, directory or file.
 *
 * @author gkspencer
 */
public final class FileAccess {

    //	Permissions
    public static final int NoAccess    = 0;
    public static final int ReadOnly    = 1;
    public static final int Writeable   = 2;

    /**
     * Return the file permission as a string.
     *
     * @param perm int
     * @return java.lang.String
     */
    public final static String asString(int perm) {
        String permStr = "";

        switch (perm) {
            case NoAccess:
                permStr = "NoAccess";
                break;
            case ReadOnly:
                permStr = "ReadOnly";
                break;
            case Writeable:
                permStr = "Writeable";
                break;
        }
        return permStr;
    }
}
