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
 * <p>The search context represents the state of an active search by a disk interface
 * based class. The context is used to continue a search across multiple requests.
 *
 * @author gkspencer
 */
public abstract class SearchContext {

    //	Maximum number of files to return per search request
    private int m_maxFiles;

    // Maximum response buffer size to return per search request
    private int m_maxSize;

    //	Tree identifier that this search is associated with
    private int m_treeId;

    //	Search string
    private String m_searchStr;

    //	Flags
    private int m_flags;

    // Search is closed
    private boolean m_closed;

    /**
     * Default constructor.
     */
    public SearchContext() {
    }

    /**
     * Class constructor
     *
     * @param maxFiles int
     * @param treeId   int
     */
    protected SearchContext(int maxFiles, int treeId) {
        m_maxFiles = maxFiles;
        m_treeId = treeId;
    }

    /**
     * Close the search.
     */
    public void closeSearch() {
    }

    /**
     * Return the search context flags.
     *
     * @return int
     */
    public final int getFlags() {
        return m_flags;
    }

    /**
     * Return the maximum number of files that should be returned per search request.
     *
     * @return int
     */
    public final int getMaximumFiles() {
        return m_maxFiles;
    }

    /**
     * Return the maximum repsonse buffer size per search request, in bytes
     *
     * @return int
     */
    public final int getMaximumResponseSize() {
        return m_maxSize;
    }

    /**
     * Return the resume id for the current file/directory in the search.
     *
     * @return int
     */
    public abstract int getResumeId();

    /**
     * Return the search string, used for resume keys in some SMB dialects.
     *
     * @return java.lang.String
     */
    public final String getSearchString() {
        return m_searchStr != null ? m_searchStr : "";
    }

    /**
     * Return the tree identifier of the tree connection that this search is associated with.
     *
     * @return int
     */
    public final int getTreeId() {
        return m_treeId;
    }

    /**
     * Check if the search has been closed
     *
     * @return boolean
     */
    public final boolean isClosed() {
        return m_closed;
    }

    /**
     * Determine if there are more files for the active search.
     *
     * @return boolean
     */
    public abstract boolean hasMoreFiles();

    /**
     * Return file information for the next file in the active search. Returns
     * false if the search is complete.
     *
     * @param info FileInfo to return the file information.
     * @return true if the file information is valid, else false
     */
    public abstract boolean nextFileInfo(FileInfo info);

    /**
     * Return the file name of the next file in the active search. Returns
     * null is the search is complete.
     *
     * @return java.lang.String
     */
    public abstract String nextFileName();

    /**
     * Return the total number of file entries for this search if known, else return -1
     *
     * @return int
     */
    public int numberOfEntries() {
        return -1;
    }

    /**
     * Restart a search at the specified resume point.
     *
     * @param resumeId Resume point id.
     * @return true if the search can be restarted, else false.
     */
    public abstract boolean restartAt(int resumeId);

    /**
     * Restart the current search at the specified file.
     *
     * @param info File to restart the search at.
     * @return true if the search can be restarted, else false.
     */
    public abstract boolean restartAt(FileInfo info);

    /**
     * Set the search context flags.
     *
     * @param flg int
     */
    public final void setFlags(int flg) {
        m_flags = flg;
    }

    /**
     * Set the maximum files to return per request packet.
     *
     * @param maxFiles int
     */
    public final void setMaximumFiles(int maxFiles) {
        m_maxFiles = maxFiles;
    }

    /**
     * Set the maximum response buffer size per search request, in bytes
     *
     * @param maxSize int
     */
    public final void setMaximumResponseSize(int maxSize) {
        m_maxSize = maxSize;
    }

    /**
     * Set the search string.
     *
     * @param str String
     */
    public final void setSearchString(String str) {
        m_searchStr = str;
    }

    /**
     * Set the tree connection id that the search is associated with.
     *
     * @param id int
     */
    public final void setTreeId(int id) {
        m_treeId = id;
    }

    /**
     * Wildcard searches return entries for the '.' and '..' pseudo entries
     *
     * @return boolean
     */
    public boolean hasDotFiles() {
        return false;
    }

    /**
     * Return the '.' pseudo entry details
     *
     * @param finfo FileInfo
     * @return boolean
     */
    public boolean getDotInfo(FileInfo finfo) {
        return false;
    }

    /**
     * Return the '..' pseudo entry details
     *
     * @param finfo FileInfo
     * @return boolean
     */
    public boolean getDotDotInfo(FileInfo finfo) {
        return false;
    }

    /**
     * Mark the search as closed
     */
    public final void setClosed() {
        m_closed = true;
    }

    /**
     * Return the search context as a string.
     *
     * @return java.lang.String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();
        str.append("[");
        str.append(getSearchString());
        str.append(", maxFiles=");
        str.append(getMaximumFiles());
        str.append(", maxSize=");
        str.append(getMaximumResponseSize());
        str.append(", flags=0x");
        str.append(Integer.toHexString(getFlags()));
        if (hasDotFiles())
            str.append(",DotFiles");
        if ( isClosed())
            str.append(", CLOSED");
        str.append("]");

        return str.toString();
    }
}
