/*
 * Copyright (C) 2006-2011 Alfresco Software Limited.
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

package org.filesys.server.filesys.cache;

import java.util.HashMap;

import org.filesys.server.filesys.FileStatus;
import org.filesys.server.filesys.pseudo.PseudoFileList;

/**
 * Local File State Class
 *
 * @author gkspencer
 */
public class LocalFileState extends FileState {

    // Serialization id
    private static final long serialVersionUID = 1L;

    //	File identifier
    private int m_fileId = UnknownFileId;

    //	File data status
    private DataStatus m_dataStatus = DataStatus.Available;

    //	Cache of various file information
    private HashMap<String, Object> m_cache;

    // Pseudo file list
    private PseudoFileList m_pseudoFiles;

    // Filesystem specific object
    private Object m_filesysObj;

    /**
     * Class constructor
     *
     * @param fname         String
     * @param caseSensitive boolean
     */
    public LocalFileState(String fname, boolean caseSensitive) {
        super(fname, caseSensitive);
    }

    /**
     * Class constructor
     *
     * @param fname         String
     * @param status        FileStatus
     * @param caseSensitive boolean
     */
    public LocalFileState(String fname, FileStatus status, boolean caseSensitive) {
        super(fname, status, caseSensitive);
    }

    /**
     * Get the file id
     *
     * @return int
     */
    public int getFileId() {
        return m_fileId;
    }

    /**
     * Return the file data status
     *
     * @return DataStatus
     */
    public DataStatus getDataStatus() {
        return m_dataStatus;
    }

    /**
     * Set the file identifier
     *
     * @param id int
     */
    public void setFileId(int id) {
        m_fileId = id;
    }

    /**
     * Set the file data status
     *
     * @param sts DataStatus
     */
    public void setDataStatus(DataStatus sts) {
        m_dataStatus = sts;
    }

    /**
     * Determine if a folder has pseudo files associated with it
     *
     * @return boolean
     */
    public boolean hasPseudoFiles() {
        if (m_pseudoFiles != null)
            return m_pseudoFiles.numberOfFiles() > 0;
        return false;
    }

    /**
     * Return the pseudo file list
     *
     * @param createList boolean
     * @return PseudoFileList
     */
    protected PseudoFileList getPseudoFileList(boolean createList) {
        if (m_pseudoFiles == null && createList == true)
            m_pseudoFiles = new PseudoFileList();
        return m_pseudoFiles;
    }

    /**
     * Check if this is a copy file state, or the master file state object
     *
     * @return boolean
     */
    public final boolean isCopyState() {
        return false;
    }

    /**
     * Return the filesystem object
     *
     * @return Object
     */
    public Object getFilesystemObject() {
        return m_filesysObj;
    }

    /**
     * Set the filesystem object
     *
     * @param filesysObj Object
     */
    public void setFilesystemObject(Object filesysObj) {
        m_filesysObj = filesysObj;
    }

    /**
     * Return the map of additional attribute objects attached to this file state, and
     * optionally create the map if it does not exist
     *
     * @param createMap boolean
     * @return HashMap
     */
    protected HashMap<String, Object> getAttributeMap(boolean createMap) {
        if (m_cache == null && createMap == true)
            m_cache = new HashMap<String, Object>();
        return m_cache;
    }
}
