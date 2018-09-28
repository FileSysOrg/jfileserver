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

package org.filesys.server.filesys.pseudo;

import org.filesys.server.filesys.FileAttribute;
import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.NetworkFile;

/**
 * Pseudo File Class
 *
 * <p>
 * Creates a pseudo file entry for a folder that maps to a file outside of the usual file area but
 * appears in folder listings for the owner folder.
 *
 * @author gkspencer
 */
public abstract class PseudoFile {

    // Dummy creation date/time to use for pseudo files
    protected static long _creationDateTime = System.currentTimeMillis();

    // File name for pseudo file
    private String m_fileName;

    // File flags/attributes
    private int m_fileFlags;

    // File information, used for file information/folder searches
    private FileInfo m_fileInfo;

    /**
     * Class constructor
     *
     * @param name String
     */
    protected PseudoFile(String name) {
        m_fileName = name;
    }

    /**
     * Class constructor
     *
     * @param name  String
     * @param flags int
     */
    protected PseudoFile(String name, int flags) {
        m_fileName = name;
        m_fileFlags = flags;
    }

    /**
     * Return the pseudo file name as it will appear in folder listings
     *
     * @return String
     */
    public final String getFileName() {
        return m_fileName;
    }

    /**
     * Return the standard file attributes
     *
     * @return int
     */
    public final int getAttributes() {
        return m_fileFlags;
    }

    /**
     * Check if the pseudo file is a file
     *
     * @return boolean
     */
    public final boolean isFile() {
        return (m_fileFlags & FileAttribute.Directory) != 0 ? false : true;
    }

    /**
     * Check if the pseudo file is a folder
     *
     * @return boolean
     */
    public final boolean isDirectory() {
        return (m_fileFlags & FileAttribute.Directory) != 0 ? true : false;
    }

    /**
     * Check if the pseudo file is read-only
     *
     * @return boolean
     */
    public final boolean isReadOnly() {
        return (m_fileFlags & FileAttribute.ReadOnly) != 0 ? true : false;
    }

    /**
     * Check if the pseudo file is hidden
     *
     * @return boolean
     */
    public final boolean isHidden() {
        return (m_fileFlags & FileAttribute.Hidden) != 0 ? true : false;
    }

    /**
     * Return the file information for the pseudo file
     *
     * @return FileInfo
     */
    public abstract FileInfo getFileInfo();

    /**
     * Return a network file for reading/writing the pseudo file
     *
     * @param netPath String
     * @return NetworkFile
     */
    public abstract NetworkFile getFile(String netPath);

    /**
     * Set the file information
     *
     * @param finfo FileInfo
     */
    protected final void setFileInfo(FileInfo finfo) {
        m_fileInfo = finfo;
    }

    /**
     * Return the file information
     *
     * @return FileInfo
     */
    protected final FileInfo getInfo() {
        return m_fileInfo;
    }

    /**
     * Return the pseudo file as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(getFileName());
        str.append(",");
        str.append(getFileInfo());
        str.append("]");

        return str.toString();
    }
}
