/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
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
 * Search Context Adapter Class
 *
 * @author gkspencer
 */
public class SearchContextAdapter extends SearchContext {

    /**
     * Determine if there are more files for the active search.
     *
     * @return boolean
     */
    public boolean hasMoreFiles() {
        return false;
    }

    /**
     * Return file information for the next file in the active search. Returns
     * false if the search is complete.
     *
     * @param info FileInfo to return the file information.
     * @return true if the file information is valid, else false
     */
    public boolean nextFileInfo(FileInfo info) {
        return false;
    }

    /**
     * Return the file name of the next file in the active search. Returns
     * null is the search is complete.
     *
     * @return String
     */
    public String nextFileName() {
        return null;
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
     * Restart a search at the specified resume point.
     *
     * @param resumeId Resume point id.
     * @return true if the search can be restarted, else false.
     */
    public boolean restartAt(int resumeId) {
        return false;
    }

    /**
     * Restart the current search at the specified file.
     *
     * @param info File to restart the search at.
     * @return true if the search can be restarted, else false.
     */
    public boolean restartAt(FileInfo info) {
        return false;
    }
}
