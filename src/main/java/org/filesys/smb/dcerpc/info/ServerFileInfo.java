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

package org.filesys.smb.dcerpc.info;

import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCEReadable;

/**
 * Server Open File Information Class
 *
 * @author gkspencer
 */
public class ServerFileInfo implements DCEReadable {

    // Information level
    private int m_infoLevel;

    // File details
    private int m_fileId;
    private int m_permissions;
    private int m_numLocks;

    private String m_path;
    private String m_user;

    /**
     * Default constructor
     */
    public ServerFileInfo() {
    }

    /**
     * Class constructor
     *
     * @param infoLevel int
     */
    public ServerFileInfo(int infoLevel) {
        m_infoLevel = infoLevel;
    }

    /**
     * Get the information level
     *
     * @return int
     */
    public final int getInformationLevel() {
        return m_infoLevel;
    }

    /**
     * Get the file id
     *
     * @return int
     */
    public final int getFileId() {
        return m_fileId;
    }

    /**
     * Return the file permissions
     *
     * @return int
     */
    public final int getFilePermissions() {
        return m_permissions;
    }

    /**
     * Return the number of file locks
     *
     * @return int
     */
    public final int getNumberOfLocks() {
        return m_numLocks;
    }

    /**
     * Return the file path
     *
     * @return String
     */
    public final String getFilePath() {
        return m_path;
    }

    /**
     * Return the user
     *
     * @return String
     */
    public final String getUserName() {
        return m_user;
    }

    /**
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public void readObject(DCEBuffer buf)
            throws DCEBufferException {

        // Unpack the file information
        switch (getInformationLevel()) {

            // Information level 2
            case 2:
                m_fileId = buf.getInt();
                m_path = null;
                m_user = null;
                break;

            // Information level 3
            case 3:
                m_fileId = buf.getInt();
                m_permissions = buf.getInt();
                m_numLocks = buf.getInt();

                m_path = buf.getPointer() != 0 ? "" : null;
                m_user = buf.getPointer() != 0 ? "" : null;
                break;
        }
    }

    /**
     * Read the strings for this file information from the DCE/RPC buffer
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public void readStrings(DCEBuffer buf)
            throws DCEBufferException {

        // Read the strings for this file information
        switch (getInformationLevel()) {

            // Information level 3
            case 3:
                if (getFilePath() != null)
                    m_path = buf.getString(DCEBuffer.ALIGN_INT);
                if (getUserName() != null)
                    m_user = buf.getString(DCEBuffer.ALIGN_INT);
                break;
        }
    }

    /**
     * Return the file information as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[FID=0x");
        str.append(Integer.toHexString(getFileId()));
        str.append(":Level=");
        str.append(getInformationLevel());
        str.append(":");

        if (getInformationLevel() == 3) {
            str.append("Path=");
            str.append(getFilePath());
            str.append(",Perm=0x");
            str.append(Integer.toHexString(getFilePermissions()));
            str.append(",Locks=");
            str.append(getNumberOfLocks());
            str.append(",User=");
            str.append(getUserName());
        }

        str.append("]");
        return str.toString();
    }
}
