/*
 * Copyright (C) 2012 GK Spencer
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

package org.filesys.server.filesys.db;

import org.filesys.server.filesys.*;
import org.filesys.smb.WinNT;

import java.nio.file.attribute.BasicFileAttributes;

/**
 * Populate Database File Open Parameters Class
 *
 * <p>Extends the base FileOpenParams class to add values for all available database fields</p>
 *
 * @author gkspencer
 */
public class PopulateDBFileOpenParams extends FileOpenParams {

    // File size
    private long m_fileSize;

    // Modification and access timestamps
    private long m_modifyTimestamp;
    private long m_accessTimestamp;

    // File data encrypted flag
    private boolean m_encrypted;

    //	Owner group and user id
    private int m_gid = -1;
    private int m_uid = -1;

    //	Unix mode
    private int m_mode = -1;

    //  Symbolic link name
    private String m_symName;

    /**
     * Class constructor
     *
     * @param path String
     * @param fileSize long
     * @param fileAttr int
     * @param createdAt long
     * @param modifiedAt long
     * @param accessedAt long
     * @param encrypted boolean
     */
    public PopulateDBFileOpenParams(String path, long fileSize, int fileAttr, long createdAt, long modifiedAt, long accessedAt, boolean encrypted) {
        super( path, FileAction.CreateNotExist, AccessMode.ReadWrite, fileAttr, 0);

        // Set the file size
        m_fileSize = fileSize;

        // Set the file timestamps
        setCreationDateTime( createdAt);
        m_modifyTimestamp = modifiedAt;
        m_accessTimestamp = accessedAt;

        // Indicate if the file is encrypted
        m_encrypted = encrypted;
    }

    /**
     * Class constructor
     *
     * @param path String
     * @param basicAttr BasicFileAttributes
     * @param fileAttr int
     * @param encrypted boolean
     */
    public PopulateDBFileOpenParams(String path, BasicFileAttributes basicAttr, int fileAttr, boolean encrypted) {
        super( path, FileAction.CreateNotExist, AccessMode.ReadWrite, fileAttr, 0);

        if ( basicAttr != null){

            // Check if the directory attribute is set
            if ( basicAttr.isDirectory()) {

                if ( isDirectory() == false) {
                    fileAttr += FileAttribute.Directory;
                    setFileAttributes(fileAttr);
                }
                setCreateOption(WinNT.CreateDirectory);
            }

            // Set the file size
            m_fileSize = basicAttr.size();

            // Set the file timestamps
            setCreationDateTime(basicAttr.creationTime().toMillis());
            m_modifyTimestamp = basicAttr.lastModifiedTime().toMillis();
            m_accessTimestamp = basicAttr.lastAccessTime().toMillis();
        }

        // Indicate if the file is encrypted
        m_encrypted = encrypted;
    }

    /**
     * Class constructor
     *
     * @param path String
     * @param basicAttr BasicFileAttributes
     * @param fileAttr int
     * @param encrypted boolean
     * @param mode int
     * @param groupId int
     * @param userId int
     * @param symLink String
     */
    public PopulateDBFileOpenParams(String path, BasicFileAttributes basicAttr, int fileAttr, boolean encrypted,
                                    int mode, int groupId, int userId, String symLink) {
        super( path, FileAction.CreateNotExist, AccessMode.ReadWrite, fileAttr, 0);

        if ( basicAttr != null){

            // Check if the directory attribute is set
            if ( basicAttr.isDirectory()) {

                if ( isDirectory() == false) {
                    fileAttr += FileAttribute.Directory;
                    setFileAttributes(fileAttr);
                }
                setCreateOption(WinNT.CreateDirectory);
            }

            // Set the file size
            m_fileSize = basicAttr.size();

            // Set the file timestamps
            setCreationDateTime(basicAttr.creationTime().toMillis());
            m_modifyTimestamp = basicAttr.lastModifiedTime().toMillis();
            m_accessTimestamp = basicAttr.lastAccessTime().toMillis();
        }

        // Indicate if the file is encrypted
        m_encrypted = encrypted;

        // Set the Unix properties
        m_mode = mode;

        m_gid = groupId;
        m_uid = userId;

        if ( symLink != null)
            setSymbolicLink( symLink);
    }

    /**
     * Return the file size
     *
     * @return long
     */
    public final long getFileSize() { return m_fileSize; }

    /**
     * Return the modification timestamp
     *
     * @return long
     */
    public final long getModificationTimestamp() { return m_modifyTimestamp; }

    /**
     * Return the access timestamp
     *
     * @return long
     */
    public final long getAccessTimestamp() { return m_accessTimestamp; }

    /**
     * Return the encryption status
     *
     * @return boolean
     */
    public final boolean isEncrypted() { return m_encrypted; }

    /**
     * Determine if the group id has been set
     *
     * @return boolean
     */
    public final boolean hasGid() {
        return m_gid != -1 ? true : false;
    }

    /**
     * Return the owner group id
     *
     * @return int
     */
    public final int getGid() {
        return m_gid;
    }

    /**
     * Determine if the user id has been set
     *
     * @return boolean
     */
    public final boolean hasUid() {
        return m_uid != -1 ? true : false;
    }

    /**
     * Return the owner user id
     *
     * @return int
     */
    public final int getUid() {
        return m_uid;
    }

    /**
     * Determine if the mode has been set
     *
     * @return boolean
     */
    public final boolean hasMode() {
        return m_mode != -1 ? true : false;
    }

    /**
     * Return the Unix mode
     *
     * @return int
     */
    public final int getMode() {
        return m_mode;
    }

    /**
     * Return the symbolic link name
     *
     * @return String
     */
    public final String getSymbolicLinkName() {
        return m_symName;
    }

    /**
     * Set the Unix mode
     *
     * @param mode int
     */
    public final void setMode(int mode) {
        m_mode = mode;
    }

    /**
     * Set the symbolic link name
     *
     * @param name String
     */
    public final void setSymbolicLink(String name) {
        m_symName = name;
        setFileType( FileType.SymbolicLink);
    }

    @Override
    protected void additionalToString(StringBuilder str) {

        str.append(",size=");
        str.append( getFileSize());

        str.append(",modifiedAt=");
        str.append( getModificationTimestamp());
        str.append(",accessedAt=");
        str.append( getAccessTimestamp());

        str.append(",encrypted=");
        str.append( isEncrypted());

        if ( hasGid()) {
            str.append(",gid=");
            str.append( getGid());
        }

        if ( hasUid()) {
            str.append(",uid");
            str.append( getUid());
        }

        if ( hasMode()) {
            str.append(",mode=0");
            str.append( Integer.toOctalString( getMode()));
        }

        if ( isSymbolicLink()) {
            str.append(",symlink=");
            str.append( getSymbolicLinkName());
        }
    }
}
