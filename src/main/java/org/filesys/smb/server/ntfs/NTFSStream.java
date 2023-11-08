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

package org.filesys.smb.server.ntfs;

/**
 * NTFS Stream Constants Class
 *
 * @author gkspencer
 */
public class NTFSStream {

    // Internal NTFS stream names
    public static String[]  InternalStreamNames = {"$I30", "$O", "$Q", "$R", "$J", "$MAX", "$SDH", "$SII"};

    public static String DirectoryStreamName    = "$I30";

    // Stream type names
    public static String DataStreamType         = "$DATA";
    public static String DirectoryStreamType    = "$INDEX_ALLOCATION";
    public static String BitmapStreamType       = "$BITMAP";

    /**
     * Validate an NTFS stream type
     *
     * @param typ String
     * @param isDir boolean
     * @return boolean
     */
    public static boolean isValidStreamType( String typ, boolean isDir) {

        boolean valid = false;

        if ( !isDir && typ.equals( DataStreamType))
            valid = true;
        else if ( isDir && typ.equals( DirectoryStreamType))
            valid = true;

        return valid;
    }
}
