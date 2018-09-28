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

/**
 * File Status Enum Class
 * 
 * @author gkspencer
 */
public enum FileStatus {
    Unknown,
    NotExist,
    FileExists,
    DirectoryExists

    // File status constants
//    public final static int Unknown         = -1;
//    public final static int NotExist        = 0;
//    public final static int FileExists      = 1;
//    public final static int DirectoryExists = 2;
    
//    public final static int MaxStatus       = 2;

    /**
     * Return the file status as a string
     *
     * @param sts int
     * @return String
     */
/*
    public final static String asString(int sts) {

        // Convert the status to a string
        String ret = "";

        switch (sts) {
            case Unknown:
                ret = "Unknown";
                break;
            case NotExist:
                ret = "NotExist";
                break;
            case FileExists:
                ret = "FileExists";
                break;
            case DirectoryExists:
                ret = "DirExists";
                break;
        }

        return ret;
    }
*/
}
