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
 * Filesystem Attributes Class
 *
 * <p>Contains constant attributes used to define filesystem features available. The values are taken from the SMB/CIFS
 * protocol query filesystem call.
 *
 * @author gkspencer
 */
public final class FileSystem {

    //	Filesystem attributes
    public static final int CaseSensitiveSearch     = 0x00000001;
    public static final int CasePreservedNames      = 0x00000002;
    public static final int UnicodeOnDisk           = 0x00000004;
    public static final int PersistentACLs          = 0x00000008;
    public static final int FileCompression         = 0x00000010;
    public static final int VolumeQuotas            = 0x00000020;
    public static final int SparseFiles             = 0x00000040;
    public static final int ReparsePoints           = 0x00000080;
    public static final int RemoteStorage           = 0x00000100;
    public static final int VolumeIsCompressed      = 0x00008000;
    public static final int ObjectIds               = 0x00010000;
    public static final int Encryption              = 0x00020000;
    public static final int NTFSStreams             = 0x00040000;
    public static final int ReadOnlyVolume          = 0x00080000;

    // Filesystem type strings
    public static final String TypeFAT  = "FAT";
    public static final String TypeNTFS = "NTFS";
}
