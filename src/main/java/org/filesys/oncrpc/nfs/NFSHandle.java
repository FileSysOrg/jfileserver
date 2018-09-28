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

package org.filesys.oncrpc.nfs;

import org.filesys.oncrpc.RpcPacket;
import org.filesys.util.DataPacker;

/**
 * NFS Handle Class
 *
 * <p>Contains constants and static methods used with NFS handles in the Java File Server.
 *
 * @author gkspencer
 */
public class NFSHandle {

    //	Version
    public static final byte VERSION        = 1;

    public static final byte MIN_VERSION    = 1;
    public static final byte MAX_VERSION    = 1;

    //	Handle types
    public static final byte TYPE_SHARE     = 1;
    public static final byte TYPE_DIR       = 2;
    public static final byte TYPE_FILE      = 3;

    //  Offsets to fields within the handle
    private static final int VERSION_OFFSET = 0;
    private static final int TYPE_OFFSET    = 1;
    private static final int SHARE_OFFSET   = 2;
    private static final int DIR_OFFSET     = 6;
    private static final int FILE_OFFSET    = 10;
    private static final int NAME_OFFSET    = 14;

    /**
     * Return the handle version
     *
     * @param handle byte[]
     * @return int
     */
    public static final int isVersion(byte[] handle) {
        return handle[0];
    }

    /**
     * Return the handle type
     *
     * @param handle byte[]
     * @return int
     */
    public static final int isType(byte[] handle) {
        return handle[1];
    }

    /**
     * Check if the handle is a share type handle
     *
     * @param handle byte[]
     * @return boolean
     */
    public static final boolean isShareHandle(byte[] handle) {
        if (handle[1] == TYPE_SHARE)
            return true;
        return false;
    }

    /**
     * Check if the handle is a directory type handle
     *
     * @param handle byte[]
     * @return boolean
     */
    public static final boolean isDirectoryHandle(byte[] handle) {
        if (handle[1] == TYPE_DIR)
            return true;
        return false;
    }

    /**
     * Check if the handle is a file type handle
     *
     * @param handle byte[]
     * @return boolean
     */
    public static final boolean isFileHandle(byte[] handle) {
        if (handle[1] == TYPE_FILE)
            return true;
        return false;
    }

    /**
     * Pack a share handle
     *
     * @param name   String
     * @param handle byte[]
     */
    public static final void packShareHandle(String name, byte[] handle) {

        //	Pack a share handle
        handle[0] = VERSION;
        handle[1] = TYPE_SHARE;

        //	Pack the hash code of the share name
        DataPacker.putInt(name.hashCode(), handle, SHARE_OFFSET);

        //	Null pad the handle
        int pos = SHARE_OFFSET + 4;

        while (pos < handle.length)
            handle[pos++] = 0;
    }

    /**
     * Pack a share handle
     *
     * @param name String
     * @param rpc  RpcPacket
     * @param hlen int
     */
    public static final void packShareHandle(String name, RpcPacket rpc, int hlen) {

        //	Pack a share handle
        rpc.packInt(hlen);

        rpc.packByte(VERSION);
        rpc.packByte(TYPE_SHARE);

        //	Pack the hash code of the share name
        rpc.packInt(name.hashCode());

        //	Null pad the handle
        rpc.packNulls(hlen - 6);
    }

    /**
     * Pack a directory handle
     *
     * @param shareId int
     * @param dirId   int
     * @param handle  byte[]
     */
    public static final void packDirectoryHandle(int shareId, int dirId, byte[] handle) {

        //	Pack a directory handle
        handle[0] = VERSION;
        handle[1] = TYPE_DIR;

        DataPacker.putInt(shareId, handle, 2);
        DataPacker.putInt(dirId, handle, 6);

        //	Null pad the handle
        for (int i = 10; i < handle.length; i++)
            handle[i] = 0;
    }

    /**
     * Pack a directory handle
     *
     * @param shareId int
     * @param dirId   int
     * @param rpc     RpcPacket
     * @param hlen    int
     */
    public static final void packDirectoryHandle(int shareId, int dirId, RpcPacket rpc, int hlen) {

        //	Pack a directory handle
        rpc.packInt(hlen);

        rpc.packByte(VERSION);
        rpc.packByte(TYPE_DIR);

        rpc.packInt(shareId);
        rpc.packInt(dirId);

        //	Null pad the handle
        rpc.packNulls(hlen - 10);
    }

    /**
     * Pack a file handle
     *
     * @param shareId int
     * @param dirId   int
     * @param fileId  int
     * @param handle  byte[]
     */
    public static final void packFileHandle(int shareId, int dirId, int fileId, byte[] handle) {

        //	Pack a directory handle
        handle[0] = VERSION;
        handle[1] = TYPE_FILE;

        DataPacker.putInt(shareId, handle, 2);
        DataPacker.putInt(dirId, handle, 6);
        DataPacker.putInt(fileId, handle, 10);

        //	Null pad the handle
        for (int i = 14; i < handle.length; i++)
            handle[i] = 0;
    }

    /**
     * Pack a file handle
     *
     * @param shareId int
     * @param dirId   int
     * @param fileId  int
     * @param rpc     RpcPacket
     * @param hlen    int
     */
    public static final void packFileHandle(int shareId, int dirId, int fileId, RpcPacket rpc, int hlen) {

        //	Pack a directory handle
        rpc.packInt(hlen);

        rpc.packByte(VERSION);
        rpc.packByte(TYPE_FILE);

        rpc.packInt(shareId);
        rpc.packInt(dirId);
        rpc.packInt(fileId);

        //	Null pad the handle
        rpc.packNulls(hlen - 14);
    }

    /**
     * Unpack a share id from a handle
     *
     * @param handle byte[]
     * @return int
     */
    public static final int unpackShareId(byte[] handle) {

        //	Check if the handle is a share type handle
        int shareId = -1;

        if (handle[1] == TYPE_SHARE || handle[1] == TYPE_DIR || handle[1] == TYPE_FILE) {

            //	Unpack the share id
            shareId = DataPacker.getInt(handle, 2);
        }

        //	Return the share id, or -1 if wrong handle type
        return shareId;
    }

    /**
     * Unpack a directory id from a handle
     *
     * @param handle byte[]
     * @return int
     */
    public static final int unpackDirectoryId(byte[] handle) {

        //	Check if the handle is a directory or file type handle
        int dirId = -1;

        if (handle[1] == TYPE_DIR || handle[1] == TYPE_FILE) {

            //	Unpack the directory id
            dirId = DataPacker.getInt(handle, 6);
        }

        //	Return the directory id, or -1 if wrong handle type
        return dirId;
    }

    /**
     * Unpack a file id from a handle
     *
     * @param handle byte[]
     * @return int
     */
    public static final int unpackFileId(byte[] handle) {

        //	Check if the handle is a file type handle
        int fileId = -1;

        if (handle[1] == TYPE_FILE) {

            //	Unpack the file id
            fileId = DataPacker.getInt(handle, 10);
        }

        //	Return the file id, or -1 if wrong handle type
        return fileId;
    }

    /**
     * Return an NFS handle as a string
     *
     * @param handle byte[]
     * @return String
     */
    public static final String asString(byte[] handle) {

        //	Check if the handle is a valid type
        StringBuffer str = new StringBuffer();
        str.append("[");

        switch (handle[1]) {

            //	Share/mountpoint type handle
            case TYPE_SHARE:
                str.append("Share:0x");
                str.append(Integer.toHexString(DataPacker.getInt(handle, 2)));
                break;

            //	Directory handle
            case TYPE_DIR:
                str.append("Dir:share=0x");
                str.append(Integer.toHexString(DataPacker.getInt(handle, 2)));
                str.append(",dir=0x");
                str.append(Integer.toHexString(DataPacker.getInt(handle, 6)));
                break;

            //	File handle
            case TYPE_FILE:
                str.append("File:share=0x");
                str.append(Integer.toHexString(DataPacker.getInt(handle, 2)));
                str.append(",dir=0x");
                str.append(Integer.toHexString(DataPacker.getInt(handle, 6)));
                str.append(",file=0x");
                str.append(Integer.toHexString(DataPacker.getInt(handle, 10)));
                break;
        }

        //	Return the handle string
        str.append("]");
        return str.toString();
    }

    /**
     * Check if a handle is valid
     *
     * @param handle byte[]
     * @return boolean
     */
    public static final boolean isValid(byte[] handle) {

        //	Check if the version is valid
        if (handle[0] < MIN_VERSION || handle[0] > MAX_VERSION)
            return false;

        //	Check if the handle type is valid
        if (handle[1] == TYPE_SHARE || handle[1] == TYPE_DIR || handle[1] == TYPE_FILE)
            return true;
        return false;
    }
}
