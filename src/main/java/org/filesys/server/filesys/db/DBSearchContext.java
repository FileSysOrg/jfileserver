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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.SearchContext;
import org.filesys.util.WildCard;

/**
 * Database Disk Search Context Class
 *
 * @author gkspencer
 */
public abstract class DBSearchContext extends SearchContext {

    //	Search resultset and statement
    protected ResultSet m_rs;
    protected Statement m_stmt;

    //	Complex wildcard filter
    protected WildCard m_filter;

    // Mark files as offline, optional file size of files to be marked as offline
    protected boolean m_offlineFiles;
    protected long m_offlineFileSize;

    /**
     * Class constructor
     *
     * @param rs ResultSet
     */
    public DBSearchContext(ResultSet rs) {
        super();
        m_rs = rs;
    }

    /**
     * Class constructor
     *
     * @param rs     ResultSet
     * @param filter WildCard
     */
    public DBSearchContext(ResultSet rs, WildCard filter) {
        super();
        m_rs = rs;
        m_filter = filter;
    }

    /**
     * Class constructor
     *
     * @param rs     ResultSet
     * @param stmt   Statement
     * @param filter WildCard
     */
    public DBSearchContext(ResultSet rs, Statement stmt, WildCard filter) {
        super();
        m_rs = rs;
        m_stmt = stmt;
        m_filter = filter;
    }

    /**
     * Return the resume id for the current file/directory in the search.
     *
     * @return int
     */
    public int getResumeId() {
        int resumeId = -1;
        try {
            resumeId = m_rs.getRow();
        }
        catch (SQLException ex) {
            Debug.println(ex);
        }

        return resumeId;
    }

    /**
     * Determine if there are more files for the active search.
     *
     * @return boolean
     */
    public boolean hasMoreFiles() {

        //	Check if the resultset is valid
        if (m_rs == null)
            return false;
        boolean moreFiles = true;

        //	Check if we have reached the end of the resultset
        try {
            moreFiles = m_rs.isAfterLast() ? false : true;
        }
        catch (SQLException ex) {
        }
        return moreFiles;
    }

    /**
     * Return the next file from the search, or return false if there are no more files
     *
     * @param info FileInfo
     * @return boolean
     */
    public abstract boolean nextFileInfo(FileInfo info);

    /**
     * Return the file name of the next file in the active search. Returns
     * null if the search is complete.
     *
     * @return String
     */
    public abstract String nextFileName();

    /**
     * Restart a search at the specified resume point.
     *
     * @param resumeId Resume point id.
     * @return true if the search can be restarted, else false.
     */
    public boolean restartAt(int resumeId) {

        boolean result = true;

        //	Skip to the required record, relative to the start of the resultset
        try {
            m_rs.beforeFirst();
            m_rs.absolute(resumeId);
        }
        catch (SQLException ex) {
            result = false;
        }

        //	Return status
        return result;
    }

    /**
     * Restart the current search at the specified file.
     *
     * @param info File to restart the search at.
     * @return true if the search can be restarted, else false.
     */
    public boolean restartAt(FileInfo info) {

        //	Skip to the previous record
        try {
            m_rs.previous();
        }
        catch (SQLException ex) {
        }
        return true;
    }

    /**
     * Return the total number of file entries for this search if known, else return -1
     *
     * @return int
     */
    public int numberOfEntries() {

        //	Get the number of entries in the resultset
        int rows = -1;

        try {
            m_rs.last();
            rows = m_rs.getRow();
            m_rs.beforeFirst();

        }
        catch (SQLException ex) {
        }

        //	Return the entry count for this search
        return rows;
    }

    /**
     * Close the search
     */
    public void closeSearch() {

        //	Check if the resultset is valid, if so then close it
        if (m_rs != null) {
            try {
                m_rs.close();
                if (m_stmt != null)
                    m_stmt.close();
            }
            catch (Exception ex) {
                Debug.println(ex);
            }
            m_rs = null;
            m_stmt = null;
        }

        //	Call the base class
        super.closeSearch();
    }

    /**
     * Determine if files should be marked as offline
     *
     * @return boolean
     */
    public final boolean hasMarkAsOffline() {
        return m_offlineFiles;
    }

    /**
     * Return the offline file size limit
     *
     * @return long
     */
    public final long getOfflineFileSize() {
        return m_offlineFileSize;
    }

    /**
     * Set/clear the mark files as offline setting
     *
     * @param offline boolean
     */
    public final void setMarkAsOffline(boolean offline) {
        m_offlineFiles = offline;
    }

    /**
     * Set the file size for offline files
     *
     * @param fsize long
     */
    public final void setOfflineFileSize(long fsize) {
        m_offlineFileSize = fsize;
    }
}
