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

import org.filesys.client.info.DiskInfo;
import org.filesys.client.info.FileInfo;
import org.filesys.client.info.VolumeInfo;
import org.filesys.server.filesys.AccessMode;
import org.filesys.smb.*;
import org.filesys.smb.PacketTypeV1;

/**
 * SMB core disk session class
 * 
 * @author gkspencer
 */
public class CoreDiskSession extends DiskSession {

	// Core protocol packet lengths

	private static final int CORE_LEN_FILEOPEN = 256;

	/**
	 * Class constructor
	 * 
	 * @param shr Remote server details.
	 * @param dialect SMB dialect that this session is using
	 */
	protected CoreDiskSession(PCShare shr, int dialect) {
		super(shr, dialect);
	}

	/**
	 * Close this connection with the remote server share.
	 * 
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public void CloseSession()
		throws java.io.IOException, SMBException {

		// Build a tree disconnect packet

		SMBPacket pkt = new SMBPacket();

		pkt.setCommand(PacketTypeV1.TreeDisconnect);
		pkt.setUserId(getUserId());
		pkt.setTreeId(m_treeid);

		m_pkt.setParameterCount(0);
		m_pkt.setByteCount(0);

		// Send the tree disconnect packet

		pkt.ExchangeSMB(this, pkt);

		// Indicate that the session has been closed

		this.m_treeid = Closed;

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
	public final void CreateDirectory(String dir)
		throws java.io.IOException, SMBException {

		// Create an SMB create directory packet

		m_pkt.setCommand(PacketTypeV1.CreateDirectory);
		m_pkt.setUserId(this.getUserId());
		m_pkt.setTreeId(this.getTreeId());

		m_pkt.setParameterCount(0);

		// Check if the directory name contains a path

		StringBuffer pathbuf = new StringBuffer();
		pathbuf.append((char) DataType.ASCII);

		if ( dir.startsWith("\\")) {

			// Use the directory names as is

			pathbuf.append(dir);
		}
		else {

			// Add the current working directory to the directory

			pathbuf.append(PCShare.makePath(this.getWorkingDirectory(), dir));
		}
		pathbuf.append((char) 0x00);

		// Copy the directory name data block to the SMB packet

		m_pkt.setBytes(pathbuf.toString().getBytes());

		// Send/receive the SMB create directory packet

		m_pkt.ExchangeSMB(this, m_pkt, true);
	}

	/**
	 * Create and open a file on the remote file server.
	 * 
	 * @param fname Remote file name string.
	 * @return SMBFile for the opened file, else null.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	public final SMBFile CreateFile(String fname)
		throws java.io.IOException, SMBException {

		// Create a new file

		return OpenFile(fname, AccessMode.WriteOnly);
	}

	/**
	 * Delete the specified directory on the remote file server.
	 * 
	 * @param dir Directory name string. If the directory name does not have a leading '\' the
	 *            current working directory for this session will be preprended to the string.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final void DeleteDirectory(String dir)
		throws java.io.IOException, SMBException {

		// Create an SMB delete directory packet

		m_pkt.setCommand(PacketTypeV1.DeleteDirectory);
		m_pkt.setUserId(this.getUserId());
		m_pkt.setTreeId(this.getTreeId());

		m_pkt.setParameterCount(0);

		// Check if the directory name contains a path

		StringBuffer pathbuf = new StringBuffer();
		pathbuf.append((char) DataType.ASCII);

		if ( dir.startsWith("\\")) {

			// Use the directory names as is

			pathbuf.append(dir);
		}
		else {

			// Add the current working directory to the directory

			pathbuf.append(PCShare.makePath(this.getWorkingDirectory(), dir));
		}
		pathbuf.append((char) 0x00);

		// Copy the directory name data block to the SMB packet

		m_pkt.setBytes(pathbuf.toString().getBytes());

		// Send/receive the SMB delete directory packet

		m_pkt.ExchangeSMB(this, m_pkt, true);
	}

	/**
	 * Delete the specified file on the remote server.
	 * 
	 * @param fname File to delete on the remote server.
	 * @param attr Attributes of the file to be deleted. @see
	 *            FileAttribute
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final void DeleteFile(String fname, int attr)
		throws java.io.IOException, SMBException {
	}

	/**
	 * Get disk information for this remote disk.
	 * 
	 * @return Disk information object, or null.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final DiskInfo getDiskInformation()
		throws java.io.IOException, SMBException {

		// Create a query disk information SMB packet

		m_pkt.setCommand(PacketTypeV1.DiskInformation);
		m_pkt.setUserId(this.getUserId());
		m_pkt.setTreeId(this.getTreeId());
		m_pkt.setParameterCount(0);
		m_pkt.setByteCount(0);

		// Send/receive the SMB file information packet

		m_pkt.ExchangeSMB(this, m_pkt);

		// Check if a valid response was received

		if ( m_pkt.isValidResponse()) {

			// Extract the disk information from the received SMB packet

			int totunit = m_pkt.getParameter(0);
			int blkperunit = m_pkt.getParameter(1);
			int blksize = m_pkt.getParameter(2);
			int freeblk = m_pkt.getParameter(3);

			// Create a disk information object

			return new DiskInfo(getPCShare(), totunit, blkperunit, blksize, freeblk);
		}

		// Invalid SMB response

		return null;
	}

	/**
	 * Get file information for the specified file.
	 * 
	 * @param fname File name of the file to return information for.
	 * @param level Information level required. @see FileInfoLevel
	 * @return FileInfo if the request was successful, else null.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final FileInfo getFileInformation(String fname, int level)
		throws java.io.IOException, SMBException {

		// Create a query information SMB packet

		m_pkt.setCommand(PacketTypeV1.GetFileAttributes);
		m_pkt.setUserId(this.getUserId());
		m_pkt.setTreeId(this.getTreeId());
		m_pkt.setParameterCount(0);

		// Build the remote files share relative path

		StringBuffer pathbuf = new StringBuffer();

		pathbuf.append((char) DataType.ASCII);

		if ( fname.startsWith("\\"))
			pathbuf.append(fname);
		else
			pathbuf.append(PCShare.makePath(this.getWorkingDirectory(), fname));
		pathbuf.append((char) 0x00);

		m_pkt.setBytes(pathbuf.toString().getBytes());

		// Send/receive the SMB file information packet

		m_pkt.ExchangeSMB(this, m_pkt);

		// Check if a valid response was received

		if ( m_pkt.isValidResponse()) {

			// Extract the file information from the received SMB packet

			int attr = m_pkt.getParameter(0);
			int ftim = (m_pkt.getParameter(2) << 16) + m_pkt.getParameter(1);
			int fsiz = (m_pkt.getParameter(4) << 16) + m_pkt.getParameter(3);

			// Create a file information object

			return new FileInfo(fname, fsiz, attr, ftim);
		}

		// Invalid SMB response

		return null;
	}

	/**
	 * Get the disk volume information
	 * 
	 * @return VolumeInfo, or null
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public final VolumeInfo getVolumeInformation()
		throws java.io.IOException, SMBException {
		return null;
	}

	/**
	 * Check if the specified file name is a directory.
	 * 
	 * @param dir Directory name string. If the directory name does not have a leading '\' the
	 *            current working directory for this session will be preprended to the string.
	 * @return true if the specified file name is a directory, else false.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final boolean isDirectory(String dir)
		throws java.io.IOException, SMBException {

		// Allocate an SMB packet for the check directory request

		SMBPacket pkt = new SMBPacket();
		pkt.setCommand(PacketTypeV1.CheckDirectory);
		pkt.setUserId(this.getUserId());
		pkt.setTreeId(this.getTreeId());
		pkt.setParameterCount(0);

		// Build the remote directory tree relative path

		StringBuffer pathbuf = new StringBuffer();
		pathbuf.append((char) DataType.ASCII);

		if ( dir.startsWith("\\"))
			pathbuf.append(dir);
		else
			pathbuf.append(PCShare.makePath(this.getWorkingDirectory(), dir));

		if ( pathbuf.charAt(pathbuf.length() - 1) != '\\')
			pathbuf.append("\\");
		pathbuf.append((char) 0x00);

		pkt.setBytes(pathbuf.toString().getBytes());

		// Send/receive the SMB check directory packet

		pkt.ExchangeSMB(this, pkt);

		// Check if a valid response was received

		if ( pkt.isValidResponse()) {

			// File name is a directory

			return true;
		}

		// Error response, file name is not a directory

		return false;
	}

	/**
	 * Open a file on the remote file server.
	 * 
	 * @param fname Remote file name string.
	 * @param flags File open option flags.
	 * @return SMBFile for the opened file, else null.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public final SMBFile OpenFile(String fname, int flags)
		throws java.io.IOException, SMBException {

		// Check if the path is a valid file path

		if ( isValidFilePath(fname) == false)
			throw new SMBException(SMBStatus.NTErr, SMBStatus.NTInvalidParameter);

		// Initialize the SMB requets to open an existing file

		m_pkt.setCommand(PacketTypeV1.OpenFile);
		m_pkt.setFlags(0);

		// Set the parameter words

		m_pkt.setParameterCount(2);
		m_pkt.setParameter(0, flags); // access mode
		m_pkt.setParameter(1, 0); // search attributes

		// Set the user id

		m_pkt.setUserId(this.getUserId());

		// Set the tree id

		m_pkt.setTreeId(this.getTreeId());

		// Build the password and share details

		StringBuffer pathbuf = new StringBuffer();

		pathbuf.append((char) DataType.ASCII);

		if ( fname.startsWith("\\"))
			pathbuf.append(fname);
		else
			pathbuf.append(PCShare.makePath(this.getWorkingDirectory(), fname));
		pathbuf.append((char) 0x00);

		m_pkt.setBytes(pathbuf.toString().getBytes());

		// Send/receive the SMB file open packet

		m_pkt.ExchangeSMB(this, m_pkt);

		// Check if a valid response was received

		if ( m_pkt.isValidResponse()) {

			// Extract the file information from the received SMB packet

			int fid = m_pkt.getParameter(0);
			int attr = m_pkt.getParameter(1);
			int fsiz = (m_pkt.getParameter(5) << 16) + m_pkt.getParameter(4);

			// Create a file information object

			FileInfo finfo = new FileInfo(fname, fsiz, attr);

			// Create an SMB file object

			return new CoreFile(this, finfo, fid);
		}

		// Check if the error indicates that the requested file does not exist, and
		// this is a open for write type request, if so then try and create a new file.

		/*
		 * else if ( m_pkt.getErrorClass () == SMBStatus.ErrDos && m_pkt.getErrorCode () ==
		 * SMBStatus.DOSFileNotFound && SMBAccessMode.AccessMode ( flags) ==
		 * SMBAccessMode.WriteOnly) {
		 */
		else if ( AccessMode.getAccessMode(flags) == AccessMode.WriteOnly) {

			// Initialize the SMB request to create a new file

			m_pkt.setCommand(PacketTypeV1.CreateNew);
			m_pkt.setFlags(0);

			// Set the parameter words

			m_pkt.setParameterCount(3);
			m_pkt.setParameter(0, flags);
			m_pkt.setParameter(1, 0); // time file was created
			m_pkt.setParameter(2, 0); // date file was created

			// Set the user id/tree id

			m_pkt.setUserId(this.getUserId());
			m_pkt.setTreeId(this.getTreeId());

			// Clear the error code/class

			m_pkt.setErrorClass(SMBStatus.Success);
			m_pkt.setErrorCode(0);

			// Set the remote path name in the create file packet

			m_pkt.setBytes(pathbuf.toString().getBytes());

			// Send/receive the SMB file create packet

			m_pkt.ExchangeSMB(this, m_pkt);

			// Check if a valid response was received

			if ( m_pkt.isValidResponse()) {

				// Extract the file information from the received SMB packet

				int fid = m_pkt.getParameter(0);

				// Create a file information object

				FileInfo finfo = new FileInfo(fname, 0, 0);

				// Create an SMB file object

				return new CoreFile(this, finfo, fid);
			}
		}

		// Invalid response/error occurred

		return null;
	}

	/**
	 * Rename a file, or set of files, on the remote file server.
	 * 
	 * @param curnam Current file name string, may contain wildcards. If the path does not start
	 *            with a '\' the current working directory string will be preprended.
	 * @param newnam New file name.
	 * @param attr Search attributes, to determine which file(s) to rename.
	 * @see org.filesys.server.filesys.FileAttribute
	 * @return true if the file rename request was successful, else false.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final boolean RenameFile(String curnam, String newnam, int attr)
		throws java.io.IOException, SMBException {

		// Create an SMB rename packet

		m_pkt.setCommand(PacketTypeV1.RenameFile);
		m_pkt.setUserId(this.getUserId());
		m_pkt.setTreeId(this.getTreeId());

		m_pkt.setParameterCount(1);
		m_pkt.setParameter(0, attr);

		// Check if the input file name contains a path

		StringBuffer pathbuf = new StringBuffer();

		if ( curnam.startsWith("\\")) {

			// Use the input/output file names as is

			pathbuf.append((char) DataType.ASCII);
			pathbuf.append(curnam);
			pathbuf.append((char) 0x00);

			pathbuf.append((char) DataType.ASCII);
			pathbuf.append(newnam);
			pathbuf.append((char) 0x00);
		}
		else {

			// Add the current working directory to the input path

			pathbuf.append(PCShare.makePath(this.getWorkingDirectory(), curnam));
			pathbuf.append((char) 0x00);

			// Check if the output file name contains a path

			pathbuf.append((char) DataType.ASCII);
			if ( newnam.startsWith("\\"))
				pathbuf.append(newnam);
			else
				pathbuf.append(PCShare.makePath(this.getWorkingDirectory(), newnam));
			pathbuf.append((char) 0x00);
		}

		// Copy the input/output file name data block to the SMB packet

		m_pkt.setBytes(pathbuf.toString().getBytes());

		// Send/receive the SMB rename file(s) packet

		m_pkt.ExchangeSMB(this, m_pkt, true);

		// Check if we got a valid response

		if ( m_pkt.isValidResponse())
			return true;

		// Invalid rename request

		return false;
	}

	/**
	 * Set file information for the specified file.
	 * 
	 * @param fname File name of the file to set information for.
	 * @param finfo File information containing the new values.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final void setFileInformation(String fname, FileInfo finfo)
		throws java.io.IOException, SMBException {
	}

	/**
	 * Set file information for the specified file, using the file id
	 * 
	 * @param file File to set information for.
	 * @param finfo File information containing the new values.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final void setFileInformation(SMBFile file, FileInfo finfo)
		throws java.io.IOException, SMBException {
	}

	/**
	 * Set file attributes for the specified file, using the file name
	 * 
	 * @param fname File name of the file to set information for.
	 * @param attrib File attributes mask. @see FileAttribute
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final void setFileAttributes(String fname, int attrib)
		throws java.io.IOException, SMBException {
	}

	/**
	 * Start a search of the specified directory returning information for each file/directory
	 * found.
	 * 
	 * @param dir Directory to start searching. If the directory string does not start with a '\'
	 *            then the directory name is prepended with the current working directory.
	 * @param attr Search attributes, to determine the types of files/directories returned. @see
	 *            FileAttribute
	 * @param level Information level required. @see FileInfoLevel
	 * @return SMBSearchContext for this search, else null
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public final SearchContext StartSearch(String dir, int attr, int level)
		throws java.io.IOException, SMBException {

		// Create a new SMB search context

		CoreSearchContext srch = new CoreSearchContext(this);
		if ( srch == null)
			return null;

		// Start the search and return the search context

		srch.StartSearch(dir, attr, level);
		return srch;
	}
}