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

import java.io.FileNotFoundException;
import java.io.IOException;

import org.filesys.client.info.DiskInfo;
import org.filesys.client.info.FileInfo;
import org.filesys.client.info.VolumeInfo;
import org.filesys.server.filesys.FileAttribute;
import org.filesys.smb.PCShare;
import org.filesys.smb.SMBDeviceType;
import org.filesys.smb.SMBException;

/**
 *  <p>The DiskSession class provides disk, directory and file related methods on
 *  a remote disk share.
 *
 *  <p>The disk session maintains a current working directory, initially set from the
 *  PCShare object that was used to open the disk session. Methods such as CreateDirectory(),
 *  DeleteDirectory(), OpenFile() etc. will prepend the working directory string to the
 *  specified file or directory string, unless the specified file or directory contains a
 *  path. The current working directory can be changed using the setWorkingDirectory() method.
 *
 *  <p>A disk session is created using the SessionFactory.OpenDiskSession() method. The
 *  SessionFactory negotiates the appropriate SMB dialect with the remote server and creates
 *  the appropriate DiskSession derived object.
 * 
 * @see SessionFactory
 * 
 * @author gkspencer
 */
public abstract class DiskSession extends Session {

	//	Default information level to be returned by a directory search
	
	public static final int DefaultInformationLevel	= 1;
	
	//	Flags for the setFileInformation() method
	
	public final static int Attributes 	= 0x0001;
	public final static int WriteTime 	= 0x0002;
	public final static int WriteDate 	= 0x0004;

	/**
	 * Class constructor
	 * 
	 * @param shr PCShare
	 * @param dialect int
	 */
	protected DiskSession(PCShare shr, int dialect) {
		super(shr, dialect, null);

		// Set the device type

		this.setDeviceType(SMBDeviceType.Disk);
	}

	/**
	 * Close this connection with the remote server share.
	 * 
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public void CloseSession()
		throws IOException, SMBException {

		// Close the network session

		super.CloseSession();
	}

	/**
	 * Create a new directory on the remote file server.
	 * 
	 * @param dir Directory name string. If the directory name does not have a leading '\' the
	 *            current working directory for this session will be prepended to the string.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract void CreateDirectory(String dir)
		throws IOException, SMBException;

	/**
	 * Create and open a file on the remote file server.
	 * 
	 * @param fname Remote file name string.
	 * @return SMBFile for the opened file, else null.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	public abstract SMBFile CreateFile(String fname)
		throws IOException, SMBException;

	/**
	 * Delete the specified directory on the remote file server.
	 * 
	 * @param dir Directory name string. If the directory name does not have a leading '\' the
	 *            current working directory for this session will be preprended to the string.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract void DeleteDirectory(String dir)
		throws IOException, SMBException;

	/**
	 * Delete the specified file on the remote file server.
	 * 
	 * @param fname File name of the remote file to delete. If the file name does not have a leading
	 *            '\' the current working directory for this session will be prepended to the
	 *            string. The string may contain wildcard characters to delete multiple files. '?'
	 *            matches a single character and '*' matches none, one or more characters.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public void DeleteFile(String fname)
		throws IOException, SMBException {

		// Call the delete file method for normal files

		DeleteFile(fname, FileAttribute.Normal);
	}

	/**
	 * Delete the specified file on the remote file server.
	 * 
	 * @param fname File name of the remote file to delete. If the file name does not have a leading
	 *            '\' the current working directory for this session will be prepended to the
	 *            string. The string may contain wildcard characters to delete multiple files. '?'
	 *            matches a single character and '*' matches none, one or more characters.
	 * @param attr File attributes of the file(s) to delete.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract void DeleteFile(String fname, int attr)
		throws IOException, SMBException;

	/**
	 * Check if a file exists on the remote file server.
	 * 
	 * @param fname File name to test for on the remote file server. If the file name does not start
	 *            with a '\' then the working directory is prepended to the file name string.
	 * @return true if the file exists, else false.
	 */
	public boolean FileExists(String fname) {

		// Try and read the file attributes of the specified file on the remote server

		boolean sts = false;

		try {

			// Check if the file name has a path

			if ( fname.startsWith("\\")) {

				// Read the file information for the remote file

				if ( getFileInformation(fname) != null)
					sts = true;
			}
			else {

				// Add the current working directory to the file name string

				if ( getFileInformation(PCShare.makePath(getWorkingDirectory(), fname)) != null)
					sts = true;
			}
		}
		catch (SMBException ex) {
		}
		catch (FileNotFoundException ex) {
		}
		catch (IOException ex) {
		}

		// Return the file status

		return sts;
	}

	/**
	 * Finalize the object
	 */
	protected void finalize() {

		// Make sure the session is closed

		if ( !isClosed()) {

			// Close the disk session

			try {
				CloseSession();
			}
			catch (SMBException ex) {
			}
			catch (IOException ex) {
			}
		}
	}

	/**
	 * Get disk information for this remote disk.
	 * 
	 * @return Disk information object, or null.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract DiskInfo getDiskInformation()
		throws IOException, SMBException;

	/**
	 * Get file information for the specified file.
	 * 
	 * @param fname File name of the file to return information for.
	 * @param level Information level required
	 * @return SMBFileInfo if the request was successful, else null.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception java.io.FileNotFoundException If the remote file does not exist.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract FileInfo getFileInformation(String fname, int level)
		throws IOException, FileNotFoundException, SMBException;

	/**
	 * Get file information for the specified file, returning the default information level
	 * 
	 * @param fname File name of the file to return information for.
	 * @return SMBFileInfo if the request was successful, else null.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception java.io.FileNotFoundException If the remote file does not exist.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final FileInfo getFileInformation(String fname)
		throws IOException, FileNotFoundException, SMBException {

		// Return the default information level

		return getFileInformation(fname, DefaultInformationLevel);
	}

	/**
	 * Get the disk volume information
	 * 
	 * @return VolumeInfo, or null
	 * @exception java.io.FileNotFoundException If the remote file does not exist.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract VolumeInfo getVolumeInformation()
		throws IOException, SMBException;

	/**
	 * Get the current working directory, relative to the share that is being accessed.
	 * 
	 * @return Current working directory path string.
	 */
	public final String getWorkingDirectory() {
		return this.getPCShare().getPath();
	}

	/**
	 * Detemine if the disk session has been closed.
	 * 
	 * @return true if the disk session has been closed, else false.
	 */
	public final boolean isClosed() {
		return m_treeid == Closed ? true : false;
	}

	/**
	 * Check if the specified file name is a directory.
	 * 
	 * @param dir Directory name string. If the directory name does not have a leading '\' the
	 *            current working directory for this session will be prepended to the string.
	 * @return true if the specified file name is a directory, else false.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract boolean isDirectory(String dir)
		throws IOException, SMBException;

	/**
	 * Open a file on the remote file server.
	 * 
	 * @param fname Remote file name string.
	 * @param flags File open option flags.
	 * @return SMBFile for the opened file, else null.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	public abstract SMBFile OpenFile(String fname, int flags)
		throws IOException, SMBException;

	/**
	 * Open a file as an input stream.
	 * 
	 * @param fname Remote file name string.
	 * @param flags File open option flags.
	 * @return SMBInputStream for the opened file, else null.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	public SMBInputStream OpenInputStream(String fname, int flags)
		throws IOException, SMBException {

		// Open an SMBFile first

		SMBFile sfile = OpenFile(fname, flags);
		if ( sfile == null)
			return null;

		// Create an input stream attached to the SMBFile

		return new SMBInputStream(sfile);
	}

	/**
	 * Open a file as an output stream.
	 * 
	 * @param fname Remote file name string.
	 * @param flags File open option flags.
	 * @return SMBOutputStream for the opened file, else null.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	public SMBOutputStream OpenOutputStream(String fname, int flags)
		throws IOException, SMBException {

		// Open an SMBFile first

		SMBFile sfile = OpenFile(fname, flags);
		if ( sfile == null)
			return null;

		// Create an output stream attached to the SMBFile

		return new SMBOutputStream(sfile);
	}

	/**
	 * Rename a file, or set of files, on the remote file server.
	 * 
	 * @param curnam Current file name string, may contain wildcards.
	 * @param newnam New file name.
	 * @return true if the file(s) were renamed, else false
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public boolean RenameFile(String curnam, String newnam)
		throws IOException, SMBException {

		// Rename the normal attribute file(s)

		return RenameFile(curnam, newnam, FileAttribute.Normal);
	}

	/**
	 * Rename a file, or set of files, on the remote file server.
	 * 
	 * @param curnam Current file name string, may contain wildcards.
	 * @param newnam New file name.
	 * @param attr Search attributes, to determine which file(s) to rename.
	 * @see org.filesys.server.filesys.FileAttribute
	 * @return true if the file(s) were renamed, else false
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract boolean RenameFile(String curnam, String newnam, int attr)
		throws IOException, SMBException;

	/**
	 * Set file information for the specified file.
	 * 
	 * @param fname File name of the file to set information for.
	 * @param finfo File information containing the new values.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract void setFileInformation(String fname, FileInfo finfo)
		throws IOException, SMBException;

	/**
	 * Set file information for the specified file, using the file id
	 * 
	 * @param file File to set information for
	 * @param finfo File information containing new values
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract void setFileInformation(SMBFile file, FileInfo finfo)
		throws IOException, SMBException;

	/**
	 * Set file attributes for the specified file, using the file name
	 * 
	 * @param fname File name of the file to set information for.
	 * @param attrib File attributes mask. @see FileAttribute
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract void setFileAttributes(String fname, int attrib)
		throws IOException, SMBException;

	/**
	 * Set the current working directory, relative to the share that is being accessed.
	 * 
	 * @param wd Working directory path string.
	 */
	public final void setWorkingDirectory(String wd) {
		this.getPCShare().setPath(wd);
	}

	/**
	 * Start a search of the specified directory returning information for each file/directory
	 * found.
	 * 
	 * @param dir Directory/file name string, which may contain wildcards. If the directory string
	 *            does not start with a '\' then the directory name is prepended with the current
	 *            working directory.
	 * @param attr Search attributes, to determine the types of files/directories returned
	 * @param level Information level to be returned. @see FileInfoLevel
	 * @return SearchContext for this search, else null
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract SearchContext StartSearch(String dir, int attr, int level)
		throws IOException, SMBException;

	/**
	 * Start a search of the specified directory returning the default information level
	 * 
	 * @param dir Directory/file name string, which may contain wildcards. If the directory string
	 *            does not start with a '\' then the directory name is prepended with the current
	 *            working directory.
	 * @param attr Search attributes, to determine the types of files/directories returned. @see
	 *            FileAttribute
	 * @return SearchContext for this search, else null
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public final SearchContext StartSearch(String dir, int attr)
		throws IOException, SMBException {

		// Start a search using the default information level

		return StartSearch(dir, attr, DefaultInformationLevel);
	}

	/**
	 * Check if a path looks like a valid file path
	 * 
	 * @param path String
	 * @return boolean
	 */
	protected final boolean isValidFilePath(String path) {

		// Check the path

		if ( path == null || path.endsWith("\\"))
			return false;
		return true;
	}
}