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

package org.filesys.client.info;

import org.filesys.smb.nt.ReparsePoint;

/**
 * Reparse Point File Info Class
 * 
 * <p>
 * Contains extra details about a reparse point type file entry.
 * 
 * @author gkspencer
 */
public class ReparsePointFileInfo extends FileInfo {

	// Reparse point type

	private int m_reparseType;

	/**
	 * Default constructor
	 */
	public ReparsePointFileInfo() {
		super();
	}

	/**
	 * Class constructor
	 * 
	 * @param name String
	 * @param size long
	 * @param attr int
	 * @param reparseType int
	 */
	public ReparsePointFileInfo(String name, long size, int attr, int reparseType) {
		super(name, size, attr);

		setReparsePointType(reparseType);
	}

	/**
	 * Construct an SMB file information object.
	 * 
	 * @param fname File name string.
	 * @param fsize File size, in bytes.
	 * @param fattr File attributes.
	 * @param fdate SMB encoded file date.
	 * @param ftime SMB encoded file time.
	 */
	public ReparsePointFileInfo(String fname, long fsize, int fattr, int fdate, int ftime) {
		super(fname, fsize, fattr, fdate, ftime);
	}

	/**
	 * Get the reparse point type
	 * 
	 * @return int
	 */
	public final int getReparsePointType() {
		return m_reparseType;
	}

	/**
	 * Set the reparse point type
	 * 
	 * @param reparseType int
	 */
	public final void setReparsePointType(int reparseType) {
		m_reparseType = reparseType;
	}

	/**
	 * Return the file information as a string.
	 * 
	 * @return File information string.
	 */
	public String toString() {
		return super.toString() + " [Reparse point, type " + ReparsePoint.getTypeAsString(m_reparseType) + "]";
	}
}
