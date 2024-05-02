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
package org.filesys.server.filesys.clientapi;

import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.SearchContext;

/**
 * Client API Search Context Class
 *
 * <p>Contains the details of an open API file to be returned via a folder search</p>
 *
 * @author gkspencer
 */
public class ClientAPISearchContext extends SearchContext {

    //	Open client API file
    private ClientAPINetworkFile m_apiFile;

    /**
     * Class constructor
     *
     * @param apiFile ClientAPINetworkFile
     */
    public ClientAPISearchContext( ClientAPINetworkFile apiFile) {
        super();
        m_apiFile = apiFile;
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
        return m_apiFile != null;
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

        if (m_apiFile != null) {

            //	Copy the file information details into the callers object
            info.setFileId(m_apiFile.getFileId());
            info.setDirectoryId(m_apiFile.getDirectoryId());

            //	Set the file name
            info.setFileName(m_apiFile.getName());

            //	Set the file attributes
            info.setFileAttributes(m_apiFile.getFileAttributes());

            //	Set the file size
            info.setSize(m_apiFile.hasRequestData() ? m_apiFile.getRequestData().length : 0);
            info.setAllocationSize( info.getSize());

            //	Set the file creation/access/modify/change date/times
            info.setCreationDateTime(m_apiFile.getCreationDate());
            info.setAccessDateTime(m_apiFile.getAccessDate());
            info.setModifyDateTime(m_apiFile.getModifyDate());
            info.setChangeDateTime(m_apiFile.getModifyDate());

            //	Clear the file information
            m_apiFile = null;
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
        return m_apiFile != null ? m_apiFile.getName() : null;
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
