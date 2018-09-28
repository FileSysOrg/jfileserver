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

package org.filesys.server.filesys.db;

import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.SearchContext;

/**
 * Cached Search Context Class
 *
 * <p>Contains the details of a single file search using file information cached by the file state.
 *
 * @author gkspencer
 */
public class CachedSearchContext extends SearchContext {

    //	File information
    private DBFileInfo m_info;

    /**
     * Class constructor
     *
     * @param finfo DBFileInfo
     */
    public CachedSearchContext(DBFileInfo finfo) {
        super();
        m_info = finfo;
    }

    /**
     * Return the resume id for the current file/directory in the search.
     *
     * @return int
     */
    public int getResumeId() {
        return -1;
    }

    /**
     * Determine if there are more files for the active search.
     *
     * @return boolean
     */
    public boolean hasMoreFiles() {
        return m_info != null ? true : false;
    }

    /**
     * Return the next file from the search, or return false if there are no more files
     *
     * @param info FileInfo
     * @return boolean
     */
    public boolean nextFileInfo(FileInfo info) {

        //	Check if the file information is valid
        boolean infoValid = false;

        if (m_info != null) {

            //	Copy the file information details into the callers object
            info.setFileId(m_info.getFileId());
            info.setDirectoryId(m_info.getDirectoryId());

            //	Set the file name
            info.setFileName(m_info.getFileName());

            //	Set the file attributes
            info.setFileAttributes(m_info.getFileAttributes());

            //	Set the file size
            info.setSize(m_info.getSize());
            info.setAllocationSize(m_info.getAllocationSize());

            //	Set the file creation/access/modify/change date/times
            info.setCreationDateTime(m_info.getCreationDateTime());
            info.setAccessDateTime(m_info.getAccessDateTime());
            info.setModifyDateTime(m_info.getModifyDateTime());
            info.setChangeDateTime(m_info.getChangeDateTime());

            //	Set the owner uid/gid and file mode
            info.setUid(m_info.getUid());
            info.setGid(m_info.getGid());
            info.setMode(m_info.getMode());

            //	Clear the file information
            m_info = null;
            infoValid = true;
        }

        //	Return the information valid status
        return infoValid;
    }

    /**
     * Return the file name of the next file in the active search. Returns
     * null if the search is complete.
     *
     * @return String
     */
    public String nextFileName() {
        return m_info != null ? m_info.getFileName() : null;
    }

    /**
     * Restart a search at the specified resume point.
     *
     * @param resumeId Resume point id.
     * @return true if the search can be restarted, else false.
     */
    public boolean restartAt(int resumeId) {

        //	Cannot restart the search
        return false;
    }

    /**
     * Restart the current search at the specified file.
     *
     * @param info File to restart the search at.
     * @return true if the search can be restarted, else false.
     */
    public boolean restartAt(FileInfo info) {

        //	Cannot restart the search
        return false;
    }
}
