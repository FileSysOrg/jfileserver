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

package org.filesys.client;

import org.filesys.client.info.FileInfo;
import org.filesys.smb.SMBException;

/**
 * SMB search context class
 * 
 * <p>
 * Holds the details of an in progress folder search on a remote file server.
 * 
 * <p>
 * For large folder listings the search will be split up over a number of SMB requests. The
 * SearchContext derived class is used to hold the search state between requests to the remote file
 * server.
 * 
 * @author gkspencer
 */
public abstract class SearchContext {

	// Disk session that this search is associated with

	private DiskSession m_sess;

	// Directory/file name to search for.

	private String m_dir;

	// Search file attributes

	private int m_attr;

	// Information level to be returned

	private int m_level;

	/**
	 * Class constructor
	 * 
	 * @param sess Disk session that the search is associated with
	 */
	protected SearchContext(DiskSession sess) {
		m_sess = sess;
	}

	/**
	 * Return the next file in this search as an SMB file information object.
	 * 
	 * @return FileInfo object, or null if there are no more files.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract FileInfo nextFileInfo()
		throws java.io.IOException, SMBException;

	/**
	 * Return the next file name in this search.
	 * 
	 * @return Next file name string, or null if there are no more files.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract String nextFileName()
		throws java.io.IOException, SMBException;

	/**
	 * Start a new search using the specified file name string and search attributes, return the
	 * specified file information level
	 * 
	 * @param fname File name string, may contain wilcards.
	 * @param attr File attributes bit mask. @see FileAttribute
	 * @param level File information level to return. @see FileInfoLevel
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract void StartSearch(String fname, int attr, int level)
		throws java.io.IOException, SMBException;

	/**
	 * Return the associated disk session
	 * 
	 * @return DiskSession
	 */
	protected final DiskSession getSession() {
		return m_sess;
	}

	/**
	 * Return the search path
	 * 
	 * @return String
	 */
	protected final String getSearchPath() {
		return m_dir;
	}

	/**
	 * Return the search attributes
	 * 
	 * @return int
	 */
	protected final int getSearchAttributes() {
		return m_attr;
	}

	/**
	 * Return the required information level
	 * 
	 * @return int
	 */
	protected final int getInformationLevel() {
		return m_level;
	}

	/**
	 * Set the search path
	 * 
	 * @param path String
	 */
	protected final void setSearchPath(String path) {
		m_dir = path;
	}

	/**
	 * Set the search attributes
	 * 
	 * @param attr int
	 */
	protected final void setSearchAttributes(int attr) {
		m_attr = attr;
	}

	/**
	 * Set the information level
	 * 
	 * @param level int
	 */
	protected final void setInformationLevel(int level) {
		m_level = level;
	}

	/**
	 * Set the search parameters
	 * 
	 * @param path String
	 * @param attr int
	 * @param level int
	 */
	protected final void setSearchParameters(String path, int attr, int level) {
		m_dir = path;
		m_attr = attr;
		m_level = level;
	}
}