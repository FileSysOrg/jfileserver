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

import org.filesys.util.StringList;

/**
 * SMB file attribute class.
 *
 * <p>Defines various bit masks that may be returned in an FileInfo object, that
 * is returned by the DiskInterface.getFileInformation () and SearchContext.nextFileInfo()
 * methods.
 *
 * <p>The values are also used by the DiskInterface.StartSearch () method to determine
 * the file/directory types that are returned.
 *
 * @author gkspencer
 * @see DiskInterface
 * @see SearchContext
 */
public final class FileAttribute {

    //	Standard file attribute constants
    public static final int Normal      = 0x00;
    public static final int ReadOnly    = 0x01;
    public static final int Hidden      = 0x02;
    public static final int System      = 0x04;
    public static final int Volume      = 0x08;
    public static final int Directory   = 0x10;
    public static final int Archive     = 0x20;

    //	NT file attribute flags
    public static final int NTReadOnly          = 0x00000001;
    public static final int NTHidden            = 0x00000002;
    public static final int NTSystem            = 0x00000004;
    public static final int NTVolumeId          = 0x00000008;
    public static final int NTDirectory         = 0x00000010;
    public static final int NTArchive           = 0x00000020;
    public static final int NTDevice            = 0x00000040;
    public static final int NTNormal            = 0x00000080;
    public static final int NTTemporary         = 0x00000100;
    public static final int NTSparse            = 0x00000200;
    public static final int NTReparsePoint      = 0x00000400;
    public static final int NTCompressed        = 0x00000800;
    public static final int NTOffline           = 0x00001000;
    public static final int NTIndexed           = 0x00002000;
    public static final int NTEncrypted         = 0x00004000;
    public static final int NTOpenNoRecall      = 0x00100000;
    public static final int NTOpenReparsePoint  = 0x00200000;
    public static final int NTPosixSemantics    = 0x01000000;
    public static final int NTBackupSemantics   = 0x02000000;
    public static final int NTDeleteOnClose     = 0x04000000;
    public static final int NTSequentialScan    = 0x08000000;
    public static final int NTRandomAccess      = 0x10000000;
    public static final int NTNoBuffering       = 0x20000000;
    public static final int NTOverlapped        = 0x40000000;
    public static final int NTWriteThrough      = 0x80000000;

    //	Standard attribute names
    private static String[] _stdNames = {"ReadOnly", "Hidden", "System", "Volume", "Directory", "Archive"};

    //	NT attribute names
    private static String[] _ntNames = {"ReadOnly",
            "Hidden",
            "System",
            "VolumeId",
            "Directory",
            "Archive",
            "Device",
            "Normal",
            "Temporary",
            "Sparse",
            "ReparsePoint",
            "Compressed",
            "Offline",
            "Indexed",
            "Encrypted",
            "",
            "OpenNoRecall",
            "OpenReparsePoint",
            "",
            "",
            "PosixSemantics",
            "DeleteOnClose",
            "SequentialScan",
            "RandomAccess",
            "NoBuffering",
            "Overlapped",
            "WriteThrough"
    };

    /**
     * Determine if the specified file attribute mask has the specified file attribute
     * enabled.
     *
     * @param attr    int
     * @param reqattr int
     * @return boolean
     */
    public final static boolean hasAttribute(int attr, int reqattr) {

        //  Check for the specified attribute
        if ((attr & reqattr) != 0)
            return true;
        return false;
    }

    /**
     * Check if the read-only attribute is set
     *
     * @param attr int
     * @return boolean
     */
    public static final boolean isReadOnly(int attr) {
        return (attr & ReadOnly) != 0 ? true : false;
    }

    /**
     * Check if the directory attribute is set
     *
     * @param attr int
     * @return boolean
     */
    public static final boolean isDirectory(int attr) {
        return (attr & Directory) != 0 ? true : false;
    }

    /**
     * Check if the hidden attribute is set
     *
     * @param attr int
     * @return boolean
     */
    public static final boolean isHidden(int attr) {
        return (attr & Hidden) != 0 ? true : false;
    }

    /**
     * Check if the system attribute is set
     *
     * @param attr int
     * @return boolean
     */
    public static final boolean isSystem(int attr) {
        return (attr & System) != 0 ? true : false;
    }

    /**
     * Check if the archive attribute is set
     *
     * @param attr int
     * @return boolean
     */
    public static final boolean isArchived(int attr) {
        return (attr & Archive) != 0 ? true : false;
    }

    /**
     * Return the specified file attributes as a comma seperated string
     *
     * @param attr int
     * @return String
     */
    public final static String getAttributesAsString(int attr) {

        //	Check if no bits are set
        if (attr == 0)
            return "Normal";

        //	Get a list of the atttribute names, for attributes that are set
        StringList names = getAttributesAsList(attr);

        //	Build the attribute names string
        StringBuffer str = new StringBuffer(128);

        for (int i = 0; i < names.numberOfStrings(); i++) {
            str.append(names.getStringAt(i));
            str.append(",");
        }

        //	Trim the last comma
        if (str.length() > 0)
            str.setLength(str.length() - 1);

        //	Return the attribute string
        return str.toString();
    }

    /**
     * Return the specified file attribute as a list of attribute names
     *
     * @param attr int
     * @return StringList
     */
    public final static StringList getAttributesAsList(int attr) {

        //	Allocate the name vector
        StringList names = new StringList();
        if (attr == 0)
            return names;

        //	Build the list of set attribute names
        int mask = 1;
        int idx = 0;

        while (idx < _stdNames.length) {

            //	Check if the current attribute is set
            if ((attr & mask) != 0)
                names.addString(_stdNames[idx]);

            //	Update the index and mask
            idx++;
            mask = mask << 1;
        }

        //	Return the names list
        return names;
    }

    /**
     * Return the specified NT file attributes as a comma seperated string
     *
     * @param attr int
     * @return String
     */
    public final static String getNTAttributesAsString(int attr) {

        //	Get a list of the atttribute names, for attributes that are set
        StringList names = getNTAttributesAsList(attr);

        //	Build the attribute names string
        StringBuffer str = new StringBuffer(128);

        for (int i = 0; i < names.numberOfStrings(); i++) {
            str.append(names.getStringAt(i));
            str.append(",");
        }

        //	Trim the last comma
        if (str.length() > 0)
            str.setLength(str.length() - 1);

        //	Return the attribute string
        return str.toString();
    }

    /**
     * Return the specified NT file attribute as a list of attribute names
     *
     * @param attr int
     * @return StringList
     */
    public final static StringList getNTAttributesAsList(int attr) {

        //	Allocate the name vector
        StringList names = new StringList();
        if (attr == 0)
            return names;

        //	Build the list of set attribute names
        int mask = 1;
        int idx = 0;

        while (idx < _ntNames.length) {

            //	Check if the current attribute is set
            if ((attr & mask) != 0)
                names.addString(_ntNames[idx]);

            //	Update the index and mask
            idx++;
            mask = mask << 1;
        }

        //	Return the names list
        return names;
    }
}
