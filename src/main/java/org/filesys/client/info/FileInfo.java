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

import org.filesys.server.filesys.FileAttribute;
import org.filesys.smb.SMBDate;

/**
 * SMB file information class.
 * 
 * <p>
 * The FileInfo class is returned by the DiskSession.getFileInformation () method and contains
 * details of the requested file.
 * 
 * <p>
 * FileInfo objects are also returned by a directory search that is started using the
 * DiskSession.StartSearch () method. The search request creates an SearchContext object which can
 * return either file names, via the nextFileName () method, or can return FileInfo objects for each
 * file found, via the nextFileInfo () method.
 * 
 * @see org.filesys.client.DiskSession
 * 
 * @author gkspencer
 */
public class FileInfo {

	// File name string

	protected String m_name;

	// 8.3 format file name

	protected String m_shortName;

	// Path string

	protected String m_path;

	// File size, in bytes

	protected long m_size;

	// File attributes bits

	protected int m_attr;

	// File date/time

	private SMBDate m_datetime;

	// Creation date/time

	private SMBDate m_createDate;

	// Last access date/time (if available)

	private SMBDate m_accessDate;

	// Filesystem allocation size

	private long m_allocSize;

	// File identifier

	private int m_fileId = -1;

	/**
	 * Default constructor
	 */
	public FileInfo() {
	}

	/**
	 * Construct an SMB file information object.
	 * 
	 * @param fname File name string.
	 * @param fsize File size, in bytes.
	 * @param fattr File attributes.
	 */
	public FileInfo(String fname, long fsize, int fattr) {
		m_name = fname;
		m_size = fsize;
		m_attr = fattr;
		m_datetime = null;

		setAllocationSize(0);
	}

	/**
	 * Construct an SMB file information object.
	 * 
	 * @param fname File name string.
	 * @param fsize File size, in bytes.
	 * @param fattr File attributes.
	 * @param ftime File time, in seconds since 1-Jan-1970 00:00:00
	 */
	public FileInfo(String fname, long fsize, int fattr, int ftime) {
		m_name = fname;
		m_size = fsize;
		m_attr = fattr;
		m_datetime = new SMBDate(ftime);

		setAllocationSize(0);
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
	public FileInfo(String fname, long fsize, int fattr, int fdate, int ftime) {
		m_name = fname;
		m_size = fsize;
		m_attr = fattr;

		if ( fdate != 0 && ftime != 0)
			m_datetime = new SMBDate(fdate, ftime);

		setAllocationSize(0);
	}

	/**
	 * Construct an SMB file information object.
	 * 
	 * @param fpath File path string.
	 * @param fname File name string.
	 * @param fsize File size, in bytes.
	 * @param fattr File attributes.
	 */
	public FileInfo(String fpath, String fname, long fsize, int fattr) {
		m_path = fpath;
		m_name = fname;
		m_size = fsize;
		m_attr = fattr;
		m_datetime = null;

		setAllocationSize(0);
	}

	/**
	 * Construct an SMB file information object.
	 * 
	 * @param fpath File path string.
	 * @param fname File name string.
	 * @param fsize File size, in bytes.
	 * @param fattr File attributes.
	 * @param ftime File time, in seconds since 1-Jan-1970 00:00:00
	 */
	public FileInfo(String fpath, String fname, long fsize, int fattr, int ftime) {
		m_path = fpath;
		m_name = fname;
		m_size = fsize;
		m_attr = fattr;
		m_datetime = new SMBDate(ftime);

		setAllocationSize(0);
	}

	/**
	 * Construct an SMB file information object.
	 * 
	 * @param fpath File path string.
	 * @param fname File name string.
	 * @param fsize File size, in bytes.
	 * @param fattr File attributes.
	 * @param fdate SMB encoded file date.
	 * @param ftime SMB encoded file time.
	 */
	public FileInfo(String fpath, String fname, long fsize, int fattr, int fdate, int ftime) {
		m_path = fpath;
		m_name = fname;
		m_size = fsize;
		m_attr = fattr;
		m_datetime = new SMBDate(fdate, ftime);

		setAllocationSize(0);
	}

	/**
	 * Return the files last access date/time.
	 * 
	 * @return SMBDate
	 */
	public SMBDate getAccessDateTime() {
		return m_accessDate;
	}

	/**
	 * Get the files allocated size.
	 * 
	 * @return long
	 */
	public long getAllocationSize() {
		return m_allocSize;
	}

	/**
	 * Get the files allocated size, as a 32bit value
	 * 
	 * @return int
	 */
	public int getAllocationSizeInt() {
		return (int) (m_allocSize & 0x0FFFFFFFFL);
	}

	/**
	 * Return the creation date/time of the file.
	 * 
	 * @return SMBDate
	 */
	public SMBDate getCreationDateTime() {
		return m_createDate;
	}

	/**
	 * Return the file attributes value.
	 * 
	 * @return File attributes value.
	 */
	public int getFileAttributes() {
		return m_attr;
	}

	/**
	 * Get the file name string
	 * 
	 * @return File name string.
	 */
	public final String getFileName() {
		return m_name;
	}

	/**
	 * Check if the short (8.3) file name is available
	 * 
	 * @return boolean
	 */
	public final boolean hasShortName() {
		return m_shortName != null ? true : false;
	}

	/**
	 * Get the short file name (8.3 format)
	 * 
	 * @return String
	 */
	public final String getShortName() {
		return m_shortName;
	}

	/**
	 * Get the files date/time of last write
	 * 
	 * @return File write date/time.
	 */
	public final SMBDate getModifyDateTime() {
		return m_datetime;
	}

	/**
	 * Get the file path string.
	 * 
	 * @return File path string, relative to the share.
	 */
	public final String getPath() {
		return m_path;
	}

	/**
	 * Get the file size, in bytes.
	 * 
	 * @return File size in bytes.
	 */
	public final long getSize() {
		return m_size;
	}

	/**
	 * Get the file size in bytes, as a 32bit value
	 * 
	 * @return File size in bytes, as an int
	 */
	public final int getSizeInt() {
		return (int) (m_size & 0x0FFFFFFFFL);
	}

	/**
	 * Get the file identifier
	 * 
	 * @return int
	 */
	public final int getFileId() {
		return m_fileId;
	}

	/**
	 * Determine if the last access date/time is available.
	 * 
	 * @return boolean
	 */
	public boolean hasAccessDateTime() {
		return m_accessDate == null ? false : true;
	}

	/**
	 * Determine if the creation date/time details are available.
	 * 
	 * @return boolean
	 */
	public boolean hasCreationDateTime() {
		return m_createDate == null ? false : true;
	}

	/**
	 * Determine if the modify date/time details are available.
	 * 
	 * @return boolean
	 */
	public boolean hasModifyDateTime() {
		return m_datetime == null ? false : true;
	}

	/**
	 * Return the specified attribute status
	 * 
	 * @param attr int
	 * @return true if the file has the specified attribute set, else false
	 */
	public final boolean hasAttribute(int attr) {
		return (m_attr & attr) != 0 ? true : false;
	}

	/**
	 * Return the directory file attribute status.
	 * 
	 * @return true if the file is a directory, else false.
	 */
	public final boolean isDirectory() {
		return (m_attr & FileAttribute.Directory) != 0 ? true : false;
	}

	/**
	 * Return the hidden file attribute status.
	 * 
	 * @return true if the file is hidden, else false.
	 */
	public final boolean isHidden() {
		return (m_attr & FileAttribute.Hidden) != 0 ? true : false;
	}

	/**
	 * Return the read-only file attribute status.
	 * 
	 * @return true if the file is read-only, else false.
	 */
	public final boolean isReadOnly() {
		return (m_attr & FileAttribute.ReadOnly) != 0 ? true : false;
	}

	/**
	 * Return the system file attribute status.
	 * 
	 * @return true if the file is a system file, else false.
	 */
	public final boolean isSystem() {
		return (m_attr & FileAttribute.System) != 0 ? true : false;
	}

	/**
	 * Return the archived attribute status
	 * 
	 * @return boolean
	 */
	public final boolean isArchived() {
		return (m_attr & FileAttribute.Archive) != 0 ? true : false;
	}

	/**
	 * Return the compressed file status
	 * 
	 * @return boolean
	 */
	public final boolean isCompressed() {
		return (m_attr & FileAttribute.NTCompressed) != 0 ? true : false;
	}

	/**
	 * Return the offline file status
	 * 
	 * @return boolean
	 */
	public final boolean isOffline() {
		return (m_attr & FileAttribute.NTOffline) != 0 ? true : false;
	}

	/**
	 * Return the encrypted file status
	 * 
	 * @return boolean
	 */
	public final boolean isEncrypted() {
		return (m_attr & FileAttribute.NTEncrypted) != 0 ? true : false;
	}

	/**
	 * Return the temporary file status
	 * 
	 * @return boolean
	 */
	public final boolean isTemporary() {
		return (m_attr & FileAttribute.NTTemporary) != 0 ? true : false;
	}

	/**
	 * Return the indexed file status
	 * 
	 * @return boolean
	 */
	public final boolean isIndexed() {
		return (m_attr & FileAttribute.NTIndexed) != 0 ? true : false;
	}

	/**
	 * Reset all values to zero/null values.
	 */
	public final void resetInfo() {
		m_name = "";
		m_path = null;

		m_size = 0L;
		m_allocSize = 0L;

		m_attr = 0;

		m_accessDate = null;
		m_createDate = null;
		m_datetime = null;

		m_fileId = -1;
	}

	/**
	 * Set the files last access date/time.
	 * 
	 * @param dat int
	 * @param tim int
	 */
	public void setAccessDateTime(int dat, int tim) {

		// Create the access date/time

		m_accessDate = new SMBDate(dat, tim);
	}

	/**
	 * Set the files last access date/time.
	 * 
	 * @param dattim Last access date/time as an SMBDate value.
	 */
	public void setAccessDateTime(SMBDate dattim) {

		// Create the access date/time

		m_accessDate = dattim;
	}

	/**
	 * Set the files allocation size.
	 * 
	 * @param siz long
	 */
	public void setAllocationSize(long siz) {
		m_allocSize = siz;
	}

	/**
	 * Set the creation date/time for the file.
	 * 
	 * @param dat int Creation date in SMB format.
	 * @param tim int Creation time in SMB format.
	 */
	public void setCreationDateTime(int dat, int tim) {

		// Set the creation date/time

		m_createDate = new SMBDate(dat, tim);
	}

	/**
	 * Set the creation date/time for the file.
	 * 
	 * @param dattim Creation date as an SMBDate value.
	 */
	public void setCreationDateTime(SMBDate dattim) {

		// Set the creation date/time

		m_createDate = dattim;
	}

	/**
	 * Set the file name.
	 * 
	 * @param name java.lang.String
	 */
	public final void setFileName(String name) {
		m_name = name;
	}

	/**
	 * Set the file size in bytes
	 * 
	 * @param siz long
	 */
	public final void setFileSize(long siz) {
		m_size = siz;
	}

	/**
	 * Set the date/time for the file.
	 * 
	 * @param dat int Creation date in SMB format.
	 * @param tim int Creation time in SMB format.
	 */
	public void setModifyDateTime(int dat, int tim) {

		// Set the date/time

		m_datetime = new SMBDate(dat, tim);
	}

	/**
	 * Set the date/time file
	 * 
	 * @param datetime SMBDate
	 */
	public final void setModifyDateTime(SMBDate datetime) {
		m_datetime = datetime;
	}

	/**
	 * Set the file identifier
	 * 
	 * @param id int
	 */
	public final void setFileId(int id) {
		m_fileId = id;
	}

	/**
	 * Set the short (8.3 format) file name
	 * 
	 * @param name String
	 */
	public final void setShortName(String name) {
		m_shortName = name;
	}

	/**
	 * Set the path
	 * 
	 * @param path String
	 */
	public final void setPath(String path) {
		m_path = path;
	}

	/**
	 * Set the file size.
	 * 
	 * @param siz int
	 */
	public final void setSize(int siz) {
		m_size = siz;
	}

	/**
	 * Set the file size.
	 * 
	 * @param siz long
	 */
	public final void setSize(long siz) {
		m_size = siz;
	}

	/**
	 * Return the file attributes as a formatted string.
	 * 
	 * <p>
	 * The returned string is in the format 'RHSD' where 'R' indicates read-only, 'H' indicates
	 * hidden, 'S' indicates system and 'D' indicates directory
	 * 
	 * @return String
	 */
	public final String getFormattedAttributes() {

		StringBuffer str = new StringBuffer();

		// Append the attribute states

		if ( isReadOnly())
			str.append("R");
		else
			str.append("-");
		if ( isHidden())
			str.append("H");
		else
			str.append("-");
		if ( isSystem())
			str.append("S");
		else
			str.append("-");
		if ( isDirectory())
			str.append("D");
		else
			str.append("-");

		// Return the string

		return str.toString();
	}

	/**
	 * Return the file information as a string.
	 * 
	 * @return File information string.
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();

		// Append the path, and terminate with a trailing '\'

		if ( m_path != null) {
			str.append(m_path);
			if ( !m_path.endsWith("\\"))
				str.append("\\");
		}

		// Append the file name

		str.append(m_name);

		// Space fill

		while (str.length() < 15)
			str.append(" ");

		// Append the attribute states

		if ( isReadOnly())
			str.append("R");
		else
			str.append("-");
		if ( isHidden())
			str.append("H");
		else
			str.append("-");
		if ( isSystem())
			str.append("S");
		else
			str.append("-");
		if ( isDirectory())
			str.append("D");
		else
			str.append("-");

		// Append the file size, in bytes

		str.append(" ");
		str.append(m_size);

		// Space fill

		while (str.length() < 30)
			str.append(" ");

		// Append the file write date/time, if available

		if ( m_datetime != null) {
			str.append(" - ");
			str.append(m_datetime.toString());
		}

		// Append the short (8.3) file name, if available

		if ( hasShortName()) {
			str.append(" (");
			str.append(getShortName());
			str.append(")");
		}

		// Return the file information string

		return str.toString();
	}

	/**
	 * Set the file attributes.
	 * 
	 * @param attr int
	 */
	public final void setFileAttributes(int attr) {
		m_attr = attr;
	}
}