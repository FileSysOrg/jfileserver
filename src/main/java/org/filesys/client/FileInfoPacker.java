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

package org.filesys.client;

import org.filesys.client.info.FileInfo;
import org.filesys.client.info.ExtendedFileInfo;
import org.filesys.client.info.StreamInfo;
import org.filesys.smb.NTTime;
import org.filesys.smb.SMBDate;
import org.filesys.smb.SMBException;
import org.filesys.smb.SMBStatus;
import org.filesys.util.DataBuffer;

/**
 * File Information Packer/Unpacker Class
 *
 * @author gkspencer
 */
class FileInfoPacker {

    /**
     * Unpack the standard file information levels 1 and 2
     *
     * @param fname String
     * @param buf   DataBuffer
     * @param ea    boolean
     * @return FileInfo
     */
    protected final static FileInfo unpackFileInfoStandard(String fname, DataBuffer buf, boolean ea) {

        // Unpack the various file date/times

        int cfdat = buf.getShort();
        int cftim = buf.getShort();

        int afdat = buf.getShort();
        int aftim = buf.getShort();

        int wfdat = buf.getShort();
        int wftim = buf.getShort();

        int fsiz = buf.getInt();
        int alloc = buf.getInt();
        int fattr = buf.getShort();

        int eaSize = 0;
        if (ea == true)
            eaSize = buf.getInt();

        // Create a file information object
        FileInfo info = null;
        if (ea == false) {

            // Create standard file information
            info = new FileInfo(fname, fsiz, fattr, wfdat, wftim);
        } else {

            // Create an extended file information to hold the extended attributes size
            ExtendedFileInfo extInfo = new ExtendedFileInfo(fname, fsiz, fattr, wfdat, wftim);
            extInfo.setExtendedAttributesSize(eaSize);

            info = extInfo;
        }

        // Set extra file information details
        if (cfdat != 0)
            info.setCreationDateTime(cfdat, cftim);

        if (afdat != 0)
            info.setAccessDateTime(afdat, aftim);

        info.setAllocationSize(alloc);

        // Return the file information
        return info;
    }

    /**
     * Pack the standard file information
     *
     * @param finfo FileInfo
     * @param buf   DataBuffer
     * @param ea    boolean
     * @throws SMBException If the file information does not match the required level
     */
    protected final static void packFileInfoStandard(FileInfo finfo, DataBuffer buf, boolean ea)
            throws SMBException {

        // If the extended attribute size is required the file information must contain extended
        // file information
        if (ea == true && finfo instanceof ExtendedFileInfo == false)
            throw new SMBException(SMBStatus.InternalErr, SMBStatus.IntInvalidFileInfo);

        // Pack the file creation date/time if available, or pack zero to indicate no change
        if (finfo.hasCreationDateTime()) {
            SMBDate cDate = finfo.getCreationDateTime();
            buf.putShort(cDate.asSMBTime());
            buf.putShort(cDate.asSMBDate());
        } else
            buf.putZeros(4);

        // Pack the file access date/time if available
        if (finfo.hasAccessDateTime()) {
            SMBDate aDate = finfo.getAccessDateTime();
            buf.putShort(aDate.asSMBTime());
            buf.putShort(aDate.asSMBDate());
        } else
            buf.putZeros(4);

        // Pack the file write date/time if available
        if (finfo.hasModifyDateTime()) {
            SMBDate mDate = finfo.getModifyDateTime();
            buf.putShort(mDate.asSMBTime());
            buf.putShort(mDate.asSMBDate());
        } else
            buf.putZeros(4);

        // Pack the file size
        buf.putInt(finfo.getSizeInt());

        // Pack the file allocation size
        buf.putInt(finfo.getAllocationSizeInt());

        // Pack the file attributes
        buf.putShort(finfo.getFileAttributes());

        // Pack the EA size, if required
        if (ea == true) {

            // Get the extended file information
            ExtendedFileInfo extInfo = (ExtendedFileInfo) finfo;

            // Pack the extended attribute size
            buf.putInt(extInfo.getExtendedAttributesSize());
        }
    }

    /**
     * Unpack the query standard information (FileInfoLevel.PathFileStandardInfo, 0x102)
     *
     * @param fname String
     * @param buf   DataBuffer
     * @return FileInfo
     */
    protected final static FileInfo unpackQueryStandardInfo(String fname, DataBuffer buf) {

        // Get the file allocation size and end of file offset
        long fileSize = buf.getLong();
        long allocSize = buf.getLong();

        // Get the delete pending and directory flags
        boolean delPending = buf.getByte() != 0 ? true : false;
        boolean isDirectory = buf.getByte() != 0 ? true : false;

        // Create the extended file information
        ExtendedFileInfo extInfo = new ExtendedFileInfo(fname, fileSize, 0);
        extInfo.setAllocationSize(allocSize);
        extInfo.setDeletePending(delPending);

        // Return the extended file information
        return extInfo;
    }

    /**
     * Unpack the query extended attribute information (FileInfoLevel.PathFileEAInfo, 0x103)
     *
     * @param fname String
     * @param buf   DataBuffer
     * @return FileInfo
     */
    protected final static FileInfo unpackQueryEAInfo(String fname, DataBuffer buf) {

        // Get the extended attribute size
        int eaSize = buf.getInt();

        // Create the extended file information
        ExtendedFileInfo extInfo = new ExtendedFileInfo(fname, 0, 0);
        extInfo.setExtendedAttributesSize(eaSize);

        // Return the extended file information
        return extInfo;
    }

    /**
     * Unpack the query name information and alternate name information
     * (FileInfoLevel.PathFileNameInfo and FileInfoLevel.PathFileAltNameInfo, 0x104 and 0x108)
     *
     * @param buf DataBuffer
     * @param uni boolean
     * @return FileInfo
     */
    protected final static FileInfo unpackQueryNameInfo(DataBuffer buf, boolean uni) {

        // Get the name size and string
        buf.getInt();
        String name = buf.getString(uni);

        // Create the file information
        FileInfo finfo = new ExtendedFileInfo(name, 0, 0);

        // Return the file information
        return finfo;
    }

    /**
     * Unpack the stream name information (FileInfoLevel.PathFileStreamInfo, 0x109)
     *
     * @param fname String
     * @param buf   DataBuffer
     * @param uni   boolean
     * @return FileInfo
     */
    protected final static FileInfo unpackQueryStreamInfo(String fname, DataBuffer buf, boolean uni) {

        // Get the offset to the next stream information structure
        int pos = buf.getPosition();
        int offset = buf.getInt();

        // Loop until the end of the stream list
        ExtendedFileInfo extInfo = new ExtendedFileInfo(fname, 0, 0);
        boolean endOfList = false;

        while (endOfList != true) {

            // Get the stream information
            int nameLen = buf.getInt();
            if (uni == true)
                nameLen = nameLen / 2;

            long strmSize = buf.getLong();
            long strmAlloc = buf.getLong();

            String name = buf.getString(nameLen, uni);

            // Create the extended file information
            StreamInfo stream = new StreamInfo(name, 0, 0, strmSize, strmAlloc);
            extInfo.addNTFSStreamInfo(stream);

            // Position at the next stream information record
            if (offset == 0)
                endOfList = true;
            else {
                pos += offset;
                buf.setPosition(pos);
                offset = buf.getInt();
            }
        }

        // Return the file information
        return extInfo;
    }

    /**
     * Unpack the compression information (FileInfoLevel.PathFileCompressionInfo, 0x10B)
     *
     * @param fname String
     * @param buf   DataBuffer
     * @return FileInfo
     */
    protected final static FileInfo unpackQueryCompressionInfo(String fname, DataBuffer buf) {

        // Get the compressed file size and compression format
        long compSize = buf.getLong();
        int compFmt = buf.getShort();

        // Create the extended file information
        ExtendedFileInfo extInfo = new ExtendedFileInfo(fname, 0, 0);
        extInfo.setCompressedSizeFormat(compSize, compFmt);

        // Return the file information
        return extInfo;
    }

    /**
     * Unpack the full file information (FileInfoLevel.PathFileAllInfo, 0x107)
     *
     * @param buf DataBuffer
     * @param uni boolean
     * @return FileInfo
     */
    protected final static FileInfo unpackQueryAllInfo(DataBuffer buf, boolean uni) {

        // Get the file create/access/write/change times, in 64bit NT format
        long createTime = buf.getLong();
        long accessTime = buf.getLong();
        long writeTime = buf.getLong();
        long changeTime = buf.getLong();

        // Get the file attributes
        int attr = buf.getInt();
        buf.skipBytes(4); // unknown value

        // Get the file size and allocation size
        long allocSize = buf.getLong();
        long fileSize = buf.getLong();

        // Delete pending and directory flags
        boolean delPending = buf.getByte() != 0 ? true : false;
        boolean isDir = buf.getByte() != 0 ? true : false;
        buf.skipBytes(2);

        // Extended attributes size
        int eaSize = buf.getInt();

        // File name
        int nameLen = buf.getInt();
        if (uni == true)
            nameLen = nameLen / 2;
        String name = buf.getString(nameLen, uni);

        // Create the extended file information
        ExtendedFileInfo extInfo = new ExtendedFileInfo(name, fileSize, attr);
        extInfo.setAllocationSize(allocSize);
        extInfo.setDeletePending(delPending);
        extInfo.setExtendedAttributesSize(eaSize);

        // Set the file times
        SMBDate smbDate = null;

        if (createTime != 0) {
            smbDate = NTTime.toSMBDate(createTime);
            extInfo.setCreationDateTime(smbDate);
        }

        if (accessTime != 0) {
            smbDate = NTTime.toSMBDate(accessTime);
            extInfo.setAccessDateTime(smbDate);
        }

        if (writeTime != 0) {
            smbDate = NTTime.toSMBDate(writeTime);
            extInfo.setModifyDateTime(smbDate);
        }

        // Return the file information
        return extInfo;
    }

    /**
     * Unpack the basic file information (FileInfoLevel.PathFileBasicInfo, 0x101)
     *
     * @param fname String
     * @param buf   DataBuffer
     * @return FileInfo
     */
    protected final static FileInfo unpackQueryBasicInfo(String fname, DataBuffer buf) {

        // Get the file create/access/write/change times, in 64bit NT format
        long createTime = buf.getLong();
        long accessTime = buf.getLong();
        long writeTime = buf.getLong();
        long changeTime = buf.getLong();

        // Get the file attributes
        int attr = buf.getInt();

        // Extended attributes size
        int eaSize = buf.getInt();

        // Create the extended file information
        ExtendedFileInfo extInfo = new ExtendedFileInfo(fname, 0, attr);
        extInfo.setExtendedAttributesSize(eaSize);

        // Set the file times
        SMBDate smbDate = null;

        if (createTime != 0) {
            smbDate = NTTime.toSMBDate(createTime);
            extInfo.setCreationDateTime(smbDate);
        }

        if (accessTime != 0) {
            smbDate = NTTime.toSMBDate(accessTime);
            extInfo.setAccessDateTime(smbDate);
        }

        if (writeTime != 0) {
            smbDate = NTTime.toSMBDate(writeTime);
            extInfo.setModifyDateTime(smbDate);
        }

        // Return the file information
        return extInfo;
    }

    /**
     * Pack the file basic information
     *
     * @param finfo FileInfo
     * @param buf   DataBuffer
     * @throws SMBException If the file information does not match the required level
     */
    protected final static void packFileBasicInfo(FileInfo finfo, DataBuffer buf)
            throws SMBException {

        // Extended file information is required by the file basic information
        if (finfo instanceof ExtendedFileInfo == false)
            throw new SMBException(SMBStatus.InternalErr, SMBStatus.IntInvalidFileInfo);
        ExtendedFileInfo extInfo = (ExtendedFileInfo) finfo;

        // Pack the file creation date/time in NT format, if available
        if (extInfo.hasCreationDateTime())
            buf.putLong(NTTime.toNTTime(extInfo.getCreationDateTime()));
        else
            buf.putZeros(8);

        // Pack the file access date/time in NT format, if available
        if (extInfo.hasAccessDateTime())
            buf.putLong(NTTime.toNTTime(extInfo.getAccessDateTime()));
        else
            buf.putZeros(8);

        // Pack the file write and change date/times in NT format, if available
        if (extInfo.hasModifyDateTime()) {
            buf.putLong(NTTime.toNTTime(extInfo.getModifyDateTime()));
            buf.putLong(NTTime.toNTTime(extInfo.getModifyDateTime()));
        } else
            buf.putZeros(16);

        // Pack the NT file attributes
        buf.putInt(extInfo.getFileAttributes());

        // Pack the extended attributes size
        buf.putInt(extInfo.getExtendedAttributesSize());
    }

    /**
     * Unpack the quesy all EAs file information (FileInfoLevel.PathQueryAllEAs, 0x04)
     *
     * @param fname String
     * @param buf   DataBuffer
     * @return FileInfo
     */
    protected final static FileInfo unpackQueryAllEAs(String fname, DataBuffer buf) {

        // Create the extended file information
        ExtendedFileInfo extInfo = new ExtendedFileInfo(fname, 0, 0);

        // Unpack the extended attribute raw data size
        int eaSize = buf.getInt() - 4;
        byte[] eaData = null;

        if (eaSize > 0) {

            // Allocate the extended attribute data block and copy the data from the buffer
            eaData = new byte[eaSize];
            System.arraycopy(buf.getBuffer(), buf.getPosition(), eaData, 0, eaSize);
        }

        // Set the extended attribute details
        extInfo.setExtendedAttributesSize(eaSize);
        extInfo.setExtendedAttributeData(eaData);

        // Return the extended attribute file information
        return extInfo;
    }
}
