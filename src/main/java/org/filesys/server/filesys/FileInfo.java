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

import java.util.Date;

import org.filesys.smb.SMBDate;

/**
 * File information class.
 * 
 * <p>
 * The FileInfo class is returned by the DiskInterface.getFileInformation () and
 * SearchContext.nextFileInfo() methods.
 * 
 * @see DiskInterface
 * @see SearchContext
 * 
 * @author gkspencer
 */
public class FileInfo {

	// Constants
	//
	// Set file information flags
	public static final int SetFileSize			= 0x0001;
	public static final int SetAllocationSize 	= 0x0002;
	public static final int SetAttributes		= 0x0004;
	public static final int SetModifyDate		= 0x0008;
	public static final int SetCreationDate		= 0x0010;
	public static final int SetAccessDate		= 0x0020;
	public static final int SetChangeDate		= 0x0040;
	public static final int SetGid				= 0x0080;
	public static final int SetUid				= 0x0100;
	public static final int SetMode				= 0x0200;
	public static final int SetDeleteOnClose 	= 0x0400;
	public static final int SetEncrypted		= 0x0800;

	// State flags
	public static final int FlagDeleteOnClose	= 0x0001;
	public static final int FlagPseudoFile		= 0x0002;
	public static final int FlagEncrypted		= 0x0004;
	
	// File name string
	protected String m_name;

	// 8.3 format file name
	protected String m_shortName;

	// Path string
	protected String m_path;

	// File size, in bytes
	protected long m_size;

	// File attributes bits
	protected int m_attr = -1;

	// File modification date/time
	private long m_modifyDate;

	// Creation date/time
	private long m_createDate;

	// Last access date/time (if available)
	private long m_accessDate;

	// Change date/time (for Un*x inode changes)
	private long m_changeDate;

	// Filesystem allocation size
	private long m_allocSize;

	// File identifier 
	private int m_fileId = -1;
	
	// parent directory id
	private int m_dirId = -1;

	// User/group id
	private int m_gid = -1;
	private int m_uid = -1;

	// Unix mode
	private int m_mode = -1;

	// File type
	private FileType m_fileType;

	// File flags
	private int m_flags;

	// Set file information flags
	//
	// Used to indicate which values in the file information object are valid and should be used to
	// set the file information.
	private int m_setFlags;

	// Network file object, used when a set information call uses the file id/handle
	private NetworkFile m_netFile;

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

		setAllocationSize(0);

		if (isDirectory())
			setFileType(FileType.Directory);
		else
			setFileType(FileType.RegularFile);
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
		m_modifyDate = new SMBDate(ftime).getTime();

		setAllocationSize(0);

		if (isDirectory())
			setFileType(FileType.Directory);
		else
			setFileType(FileType.RegularFile);
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

		if (fdate != 0 && ftime != 0)
			m_modifyDate = new SMBDate(fdate, ftime).getTime();

		setAllocationSize(0);

		if (isDirectory())
			setFileType(FileType.Directory);
		else
			setFileType(FileType.RegularFile);
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

		setAllocationSize(0);

		if (isDirectory())
			setFileType(FileType.Directory);
		else
			setFileType(FileType.RegularFile);
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
		m_modifyDate = new SMBDate(ftime).getTime();

		setAllocationSize(0);

		if (isDirectory())
			setFileType(FileType.Directory);
		else
			setFileType(FileType.RegularFile);
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
		m_modifyDate = new SMBDate(fdate, ftime).getTime();

		setAllocationSize(0);

		if (isDirectory())
			setFileType(FileType.Directory);
		else
			setFileType(FileType.RegularFile);
	}

	/**
	 * Return the files last access date/time.
	 *
	 * @return long
	 */
	public long getAccessDateTime() {
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
	 * Return the inode change date/time of the file.
	 * <p>
	 * This is the time of last metadata change.
	 *
	 * @return long
	 */
	public long getChangeDateTime() {
		return m_changeDate;
	}

	/**
	 * Return the creation date/time of the file.
	 *
	 * @return long
	 */
	public long getCreationDateTime() {
		return m_createDate;
	}

	/**
	 * Return the delete on close flag setting
	 *
	 * @return boolean
	 */
	public final boolean hasDeleteOnClose() {
		return (m_flags & FlagDeleteOnClose) != 0 ? true : false;
	}

	/**
	 * Return the encrypted flag setting
	 *
	 * @return boolean
	 */
	public final boolean isEncrypted() {
		return (m_flags & FlagEncrypted) != 0 ? true : false;
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
	 * Return the file name length
	 *
	 * @return int
	 */
	public final int getFileNameLength() {
		return m_name != null ? m_name.length() : 0;
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
	 * Return the short file name length
	 *
	 * @return int
	 */
	public final int getShortNameLength() {
		return m_shortName != null ? m_shortName.length() : 0;
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
	 * @return long
	 */
	public final long getModifyDateTime() {
		return m_modifyDate;
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
	 * Get the file identifier
	 *
	 * @return long
	 */
	public final long getFileIdLong() {
		return m_fileId & 0xFFFFFFFFL;
	}

	/**
	 * Get the parent directory identifier
	 *
	 * @return int
	 */
	public final int getDirectoryId() {
		return m_dirId;
	}

	/**
	 * Get the parent directory identifier
	 *
	 * @return long
	 */
	public final long getDirectoryIdLong() {
		return m_dirId & 0xFFFFFFFFL;
	}

	/**
	 * Determine if the last access date/time is available.
	 *
	 * @return boolean
	 */
	public boolean hasAccessDateTime() {
		return m_accessDate == 0L ? false : true;
	}

	/**
	 * Determine if the inode change date/time details are available.
	 * <p>
	 * Wireshark says this is the "last write" data/time.
	 *
	 * @return boolean
	 */
	public boolean hasChangeDateTime() {
		return m_changeDate == 0L ? false : true;
	}

	/**
	 * Determine if the creation date/time details are available.
	 *
	 * @return boolean
	 */
	public boolean hasCreationDateTime() {
		return m_createDate > 0L ? true : false;
	}

	/**
	 * Determine if the modify date/time details are available.
	 *
	 * @return boolean
	 */
	public boolean hasModifyDateTime() {
		return m_modifyDate > 0L ? true : false;
	}

	/**
	 * Determine if the file attributes field has been set
	 *
	 * @return boolean
	 */
	public final boolean hasFileAttributes() {
		return m_attr != -1 ? true : false;
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
	 * Return the temporary attribute status.
	 *
	 * @return true if the file is a temporary file, else false.
	 */
	public final boolean isTemporary() {
		return (m_attr & FileAttribute.NTTemporary) != 0 ? true : false;
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
	 * Check if this is a pseudo file
	 *
	 * @return boolean
	 */
	public boolean isPseudoFile() {
		return (m_flags & FlagPseudoFile) != 0 ? true : false;
	}

	/**
	 * Determine if the group id field has been set
	 *
	 * @return boolean
	 */
	public final boolean hasGid() {
		return m_gid != -1 ? true : false;
	}

	/**
	 * Return the owner group id
	 *
	 * @return int
	 */
	public final int getGid() {
		return m_gid;
	}

	/**
	 * Determine if the user id field has been set
	 *
	 * @return boolean
	 */
	public final boolean hasUid() {
		return m_uid != -1 ? true : false;
	}

	/**
	 * Return the owner user id
	 *
	 * @return int
	 */
	public final int getUid() {
		return m_uid;
	}

	/**
	 * Determine if the mode field has been set
	 *
	 * @return boolean
	 */
	public final boolean hasMode() {
		return m_mode != -1 ? true : false;
	}

	/**
	 * Return the Unix mode
	 *
	 * @return int
	 */
	public final int getMode() {
		return m_mode;
	}

	/**
	 * Return the file type
	 *
	 * @return FileType
	 */
	public final FileType isFileType() {
		return m_fileType;
	}

	/**
	 * Reset all values to zero/null values.
	 */
	public final void resetInfo() {
		m_name = "";
		m_shortName = null;
		m_path = null;

		m_size = 0L;
		m_allocSize = 0L;

		m_attr = 0;

		m_accessDate = 0L;
		m_createDate = 0L;
		m_modifyDate = 0L;
		m_changeDate = 0L;

		m_fileId = -1;
		m_dirId = -1;

		m_gid = -1;
		m_uid = -1;
		m_mode = -1;
	}

	/**
	 * Copy the file information
	 *
	 * @param finfo FileInfo
	 */
	public final void copyFrom(FileInfo finfo) {
		m_name = finfo.getFileName();
		m_shortName = finfo.getShortName();
		m_path = finfo.getPath();

		m_size = finfo.getSize();
		m_allocSize = finfo.getAllocationSize();

		m_attr = finfo.getFileAttributes();

		m_accessDate = finfo.getAccessDateTime();
		m_createDate = finfo.getCreationDateTime();
		m_modifyDate = finfo.getModifyDateTime();
		m_changeDate = finfo.getChangeDateTime();

		m_fileId = finfo.getFileId();
		m_dirId = finfo.getDirectoryId();

		m_gid = finfo.getGid();
		m_uid = finfo.getUid();
		m_mode = finfo.getMode();

		if (finfo.isDirectory())
			setFileType(FileType.Directory);
		else
			setFileType(FileType.RegularFile);

		m_flags = finfo.getFileFlags();
	}

	/**
	 * Set the files last access date/time.
	 *
	 * @param timesec long
	 */
	public void setAccessDateTime(long timesec) {

		// Create the access date/time
		m_accessDate = timesec;
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
	 * Set the inode change date/time for the file.
	 *
	 * @param timesec long
	 */
	public void setChangeDateTime(long timesec) {

		// Set the inode change date/time
		m_changeDate = timesec;
	}

	/**
	 * Set the creation date/time for the file.
	 *
	 * @param timesec long
	 */
	public void setCreationDateTime(long timesec) {

		// Set the creation date/time
		m_createDate = timesec;
	}

	/**
	 * Set/clear the delete on close flag
	 *
	 * @param del boolean
	 */
	public final void setDeleteOnClose(boolean del) {
		if (del)
			m_flags = m_flags | FlagDeleteOnClose;
		else
			m_flags = m_flags & ~FlagDeleteOnClose;
	}

	/**
	 * Set/clear the pseudo file flag
	 *
	 * @param pseudo boolean
	 */
	public final void setPseudoFile(boolean pseudo) {
		if (pseudo)
			m_flags = m_flags | FlagPseudoFile;
		else
			m_flags = m_flags & ~FlagPseudoFile;
	}

	/**
	 * Set the file attributes.
	 *
	 * @param attr int
	 */
	public final void setFileAttributes(int attr) {
		m_attr = attr;
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
	 * Set the modification date/time for the file.
	 *
	 * @param timesec long
	 */
	public void setModifyDateTime(long timesec) {

		// Set the date/time
		m_modifyDate = timesec;
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
	 * Set the parent directory id
	 *
	 * @param id int
	 */
	public final void setDirectoryId(int id) {
		m_dirId = id;
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
	 * Set the owner group id
	 *
	 * @param id int
	 */
	public final void setGid(int id) {
		m_gid = id;
	}

	/**
	 * Set the owner user id
	 *
	 * @param id int
	 */
	public final void setUid(int id) {
		m_uid = id;
	}

	/**
	 * Set the file mode
	 *
	 * @param mode int
	 */
	public final void setMode(int mode) {
		m_mode = mode;
	}

	/**
	 * Set the file type
	 *
	 * @param typ FileType
	 */
	public final void setFileType(FileType typ) {
		m_fileType = typ;
	}

	/**
	 * Set the file data encryption status
	 *
	 * @param enc boolean
	 */
	public final void setEncrypted(boolean enc) {
		if (enc)
			m_flags = m_flags | FlagEncrypted;
		else
			m_flags = m_flags & ~FlagEncrypted;

	}

	/**
	 * Set the set file information flags to indicated which values are to be set
	 *
	 * @param setFlags int
	 */
	public final void setFileInformationFlags(int setFlags) {
		m_setFlags = setFlags;
	}

	/**
	 * Determine if the specified set file information flags is enabled
	 *
	 * @param flag int
	 * @return boolean
	 */
	public final boolean hasSetFlag(int flag) {
		if ((m_setFlags & flag) != 0)
			return true;
		return false;
	}

	/**
	 * Return the set file information flags
	 *
	 * @return int
	 */
	public final int getSetFileInformationFlags() {
		return m_setFlags;
	}

	/**
	 * Check if the associated network file is valid
	 *
	 * @return boolean
	 */
	public final boolean hasNetworkFile() {
		return m_netFile != null ? true : false;
	}

	/**
	 * Return the associated network file
	 *
	 * @return NetworkFile
	 */
	public final NetworkFile getNetworkFile() {
		return m_netFile;
	}

	/**
	 * Set the associated network file
	 *
	 * @param netFile NetworkFile
	 */
	public final void setNetworkFile(NetworkFile netFile) {
		m_netFile = netFile;
	}

	/**
	 * Return the file flags
	 *
	 * @return int
	 */
	protected final int getFileFlags() {
		return m_flags;
	}

	/**
	 * Return the setter flags as a string
	 *
	 * @return String
	 */
	public final String getSetFileInformationFlagsString() {
		StringBuilder str = new StringBuilder();

		if (hasSetFlag(SetFileSize))
			str.append("Size,");
		if (hasSetFlag(SetAllocationSize))
			str.append("Alloc,");
		if (hasSetFlag(SetAttributes))
			str.append("Attr,");
		if (hasSetFlag(SetModifyDate))
			str.append("Modify,");
		if (hasSetFlag(SetCreationDate))
			str.append("Create,");
		if (hasSetFlag(SetAccessDate))
			str.append("Access,");
		if (hasSetFlag(SetChangeDate))
			str.append("Change,");
		if (hasSetFlag(SetGid))
			str.append("GID,");
		if (hasSetFlag(SetUid))
			str.append("UID,");
		if (hasSetFlag(SetMode))
			str.append("Mode,");
		if (hasSetFlag(SetDeleteOnClose))
			str.append("Delete,");

		if (str.length() > 0)
			str.setLength(str.length() - 1);

		return str.toString();
	}

	/**
	 * Return the file information as a string.
	 *
	 * @return File information string.
	 */
	public String toString() {
		StringBuilder str = new StringBuilder();

		// Append the path, and terminate with a trailing '\'
		if (m_path != null) {
			str.append(m_path);
			if (!m_path.endsWith("\\"))
				str.append("\\");
		}

		// Append the file name
		str.append(m_name);

		// Space fill
		while (str.length() < 15)
			str.append(" ");

		// Append the attribute states
		if (isReadOnly())
			str.append("R");
		else
			str.append("-");
		if (isHidden())
			str.append("H");
		else
			str.append("-");
		if (isSystem())
			str.append("S");
		else
			str.append("-");
		if (isDirectory())
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
		if (m_modifyDate != 0L) {
			str.append(" - ");
			str.append(new Date(m_modifyDate));
		}

		// Append the short (8.3) file name, if available
		if (hasShortName()) {
			str.append(" (");
			str.append(getShortName());
			str.append(")");
		}

		// Check if this is a pseudo file
		if (isPseudoFile())
			str.append(", Pseudo");

		// Check if this is an encrypted file
		if (isEncrypted())
			str.append(", Encrypted");

		// Return the file information string
		return str.toString();
	}
}
