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

import java.io.*;
import java.util.*;

import org.filesys.client.info.DeviceAttributesInfo;
import org.filesys.client.info.DeviceInfo;
import org.filesys.client.info.DiskInfo;
import org.filesys.client.info.FileInfo;
import org.filesys.client.info.VolumeInfo;
import org.filesys.client.smb.DirectoryWatcher;
import org.filesys.server.filesys.AccessMode;
import org.filesys.server.filesys.FileAction;
import org.filesys.server.filesys.FileAttribute;
import org.filesys.smb.*;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.nt.LoadException;
import org.filesys.smb.nt.SaveException;
import org.filesys.smb.nt.SecurityDescriptor;
import org.filesys.smb.nt.SymLink;
import org.filesys.util.DataBuffer;
import org.filesys.util.DataPacker;
import org.filesys.smb.nt.NTIOCtl;

/**
 * SMB CIFS disk session class
 * 
 * <p>
 * The CIFSDiskSession class extends the DiskSession class and provides CIFS protocol specific
 * implementations for the DiskSession methods.
 * 
 * <p>
 * An CIFSDiskSession object will be created by the SessionFactory static class when the negotiated
 * SMB dialect indicates that the remote server supports an SMB dialect greater than Core or
 * CorePlus.
 * 
 * <p>
 * The SessionFactory.OpenDisk() method is used to create a session to a remote disk share. A
 * PCShare object specifies the remote server and share to connect to, along with any required
 * access control.
 * 
 * @author gkspencer
 */
public final class CIFSDiskSession extends DiskSession {

	// Constants
	//
	// SMB session keep-alive interval

	private final static long SessionKeepAlive = 60000L;

	// List of pending asynchronous requests

	private List<AsynchRequest> m_asynchRequests;

	// List of open files with an oplock
	
	private HashMap<Integer, CIFSFile> m_oplockFiles;
	
	/**
	 * Class constructor
	 * 
	 * @param shr Remote server details.
	 * @param dialect SMB dialect that this session is using
	 */
	protected CIFSDiskSession(PCShare shr, int dialect) {
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

		m_pkt.setCommand(PacketTypeV1.TreeDisconnect);
		m_pkt.setUserId(getUserId());
		m_pkt.setTreeId(m_treeid);

		m_pkt.setParameterCount(0);
		m_pkt.setByteCount(0);

		// Send the tree disconnect packet

		m_pkt.ExchangeSMB(this, m_pkt);

		// Indicate that the session has been closed

		m_treeid = Closed;

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

		// Build the new path

		String newPath = dir;
		if ( newPath.startsWith("\\") == false)
			newPath = PCShare.makePath(getWorkingDirectory(), dir);

		// Pre-NT dialect create directory

		if ( getDialect() != Dialect.NT || isUnicode() == false) {

			// Create an SMB create directory packet

			m_pkt.setCommand(PacketTypeV1.CreateDirectory);
			m_pkt.setUserId(this.getUserId());
			m_pkt.setTreeId(this.getTreeId());

			m_pkt.setFlags(getDefaultFlags());
			m_pkt.setFlags2(getDefaultFlags2());

			m_pkt.setParameterCount(0);

			// Copy the directory name data block to the SMB packet

			m_pkt.resetBytePointer();
			m_pkt.packByte(DataType.ASCII);
			m_pkt.packString(newPath, m_pkt.isUnicode());

			m_pkt.setByteCount();

			// Send/receive the SMB create directory packet

			m_pkt.ExchangeSMB(this, m_pkt, true);
		}
		else {

			// Use the NTCreateAndX SMB to create the directory

			CIFSFile dirFile = NTCreate(newPath, AccessMode.NTRead, FileAttribute.NTDirectory, SharingMode.READ_WRITE.intValue(),
					FileAction.NTCreate, 0, WinNT.CreateDirectory);

			// Close the directory file

			dirFile.Close();
		}
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

		m_pkt.setFlags(getDefaultFlags());
		m_pkt.setFlags2(getDefaultFlags2());

		m_pkt.setCommand(PacketTypeV1.DeleteDirectory);
		m_pkt.setUserId(this.getUserId());
		m_pkt.setTreeId(this.getTreeId());

		m_pkt.setParameterCount(0);

		// Check if the directory name contains a path

		String delPath = dir;
		if ( delPath.startsWith("\\") == false)
			delPath = PCShare.makePath(getWorkingDirectory(), dir);

		// Pack the path to be deleted

		m_pkt.resetBytePointer();

		m_pkt.packByte(DataType.ASCII);
		m_pkt.packString(delPath, m_pkt.isUnicode());

		m_pkt.setByteCount();

		// Send/receive the SMB delete directory packet

		m_pkt.ExchangeSMB(this, m_pkt, true);
	}

	/**
	 * Delete the specified file on the remote file server.
	 * 
	 * @param fname File name of the remote file to delete. If the file name does not have a leading
	 *            '\' the current working directory for this session will be prepended to the
	 *            string.
	 * @param attr File attributes of the file(s) to delete.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final void DeleteFile(String fname, int attr)
		throws java.io.IOException, SMBException {

		// Create an SMB delete file packet

		m_pkt.setFlags(getDefaultFlags());
		m_pkt.setFlags2(getDefaultFlags2());

		m_pkt.setCommand(PacketTypeV1.DeleteFile);
		m_pkt.setUserId(this.getUserId());
		m_pkt.setTreeId(this.getTreeId());

		m_pkt.setParameterCount(1);
		m_pkt.setParameter(0, attr);

		// Check if the file name contains a path

		String delName = fname;
		if ( delName.startsWith("\\") == false)
			delName = PCShare.makePath(getWorkingDirectory(), fname);

		// Copy the file name data block to the SMB packet

		m_pkt.resetBytePointer();
		m_pkt.packByte(DataType.ASCII);
		m_pkt.packString(delName, m_pkt.isUnicode());

		m_pkt.setByteCount();

		// Send/receive the SMB delete file packet

		m_pkt.ExchangeSMB(this, m_pkt, true);
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

		// Check if the NT dialect has been negotiated, or LanMan

		if ( this.getDialect() != Dialect.NT) {

			// Create a query disk information SMB packet

			m_pkt.setFlags(getDefaultFlags());
			m_pkt.setFlags2(getDefaultFlags2());

			m_pkt.setCommand(PacketTypeV1.DiskInformation);
			m_pkt.setUserId(this.getUserId());
			m_pkt.setTreeId(this.getTreeId());
			m_pkt.setParameterCount(0);
			m_pkt.setByteCount(0);

			// Send/receive the SMB file information packet

			m_pkt.ExchangeSMB(this, m_pkt, true);

			// Extract the disk information from the received SMB packet

			int totunit = m_pkt.getParameter(0);
			int blkperunit = m_pkt.getParameter(1);
			int blksize = m_pkt.getParameter(2);
			int freeblk = m_pkt.getParameter(3);

			// Create a disk information object

			return new DiskInfo(getPCShare(), totunit, blkperunit, blksize, freeblk);
		}
		else {

			// Create the transaction request

			TransactBuffer reqBuf = new TransactBuffer(PacketTypeV1.Trans2QueryFileSys, null, 0, 2, 0);

			// Pack the parameter block

			DataBuffer paramBuf = reqBuf.getParameterBuffer();

			paramBuf.putShort(FileInfoLevel.FSInfoQuerySize);

			// Perform the get file system information transaction

			TransPacket tpkt = new TransPacket(m_pkt.getBuffer());
			TransactBuffer respBuf = tpkt.doTransaction(this, reqBuf);

			// Unpack the response data

			DiskInfo dinfo = null;

			if ( respBuf != null && respBuf.hasDataBuffer()) {

				// Unpack the file system information

				DataBuffer dataBuf = respBuf.getDataBuffer();

				long fsTotalUnit = dataBuf.getLong();
				long fsAvailUnit = dataBuf.getLong();

				int fsSectorsPerUnit = dataBuf.getInt();
				int fsBytesPerSector = dataBuf.getInt();

				// Create the disk information details

				dinfo = new DiskInfo(getPCShare(), fsTotalUnit, fsSectorsPerUnit, fsBytesPerSector, fsAvailUnit);
			}

			// Return the disk information

			return dinfo;
		}
	}

	/**
	 * Get file information for the specified file.
	 * 
	 * @param fname File name of the file to return information for.
	 * @see FileInfoLevel
	 * @param level Information level required
	 * @return FileInfo if the request was successful, else null.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception java.io.FileNotFoundException If the remote file does not exist.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final FileInfo getFileInformation(String fname, int level)
		throws java.io.IOException, java.io.FileNotFoundException, SMBException {

		// Build the file name/path string

		String pathName = fname;
		if ( pathName.startsWith("\\") == false)
			pathName = PCShare.makePath(getWorkingDirectory(), fname);

		// Create the request transaction buffer

		TransactBuffer reqBuf = new TransactBuffer(PacketTypeV1.Trans2QueryPath, null, 0, 512, 0);

		// Pack the parameter block

		DataBuffer paramBuf = reqBuf.getParameterBuffer();

		paramBuf.putShort(level);
		paramBuf.putInt(0);
		paramBuf.putString(pathName, isUnicode());

		// Perform the get file information transaction

		TransPacket tpkt = new TransPacket(m_pkt.getBuffer());
		TransactBuffer respBuf = tpkt.doTransaction(this, reqBuf);

		// Unpack the received file information data

		FileInfo finfo = null;

		if ( respBuf != null && respBuf.hasDataBuffer()) {

			// Unpack the file information

			DataBuffer buf = respBuf.getDataBuffer();

			switch (level) {
				case FileInfoLevel.PathStandard:
					finfo = FileInfoPacker.unpackFileInfoStandard("", buf, false);
					break;
				case FileInfoLevel.PathQueryEASize:
					finfo = FileInfoPacker.unpackFileInfoStandard("", buf, true);
					break;
				case FileInfoLevel.PathAllEAs:
					finfo = FileInfoPacker.unpackQueryAllEAs("", buf);
					break;
				case FileInfoLevel.PathFileBasicInfo:
					finfo = FileInfoPacker.unpackQueryBasicInfo("", buf);
					break;
				case FileInfoLevel.PathFileStandardInfo:
					finfo = FileInfoPacker.unpackQueryStandardInfo("", buf);
					break;
				case FileInfoLevel.PathFileEAInfo:
					finfo = FileInfoPacker.unpackQueryEAInfo("", buf);
					break;
				case FileInfoLevel.PathFileNameInfo:
					finfo = FileInfoPacker.unpackQueryNameInfo(buf, respBuf.isUnicode());
					break;
				case FileInfoLevel.PathFileAllInfo:
					finfo = FileInfoPacker.unpackQueryAllInfo(buf, respBuf.isUnicode());
					break;
				case FileInfoLevel.PathFileAltNameInfo:
					finfo = FileInfoPacker.unpackQueryNameInfo(buf, respBuf.isUnicode());
					break;
				case FileInfoLevel.PathFileStreamInfo:
					finfo = FileInfoPacker.unpackQueryStreamInfo("", buf, respBuf.isUnicode());
					break;
				case FileInfoLevel.PathFileCompressionInfo:
					finfo = FileInfoPacker.unpackQueryCompressionInfo("", buf);
					break;
			}
		}

		// Return the file information

		return finfo;
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

		// Check if the NT dialect has been negotiated, or LanMan

		VolumeInfo volInfo = null;

		if ( this.getDialect() != Dialect.NT) {

			// Build the search request

			m_pkt.setCommand(PacketTypeV1.Search);
			m_pkt.setUserId(getUserId());
			m_pkt.setTreeId(getTreeId());

			// Initialize the search SMB packet

			m_pkt.setFlags(getDefaultFlags());
			m_pkt.setFlags2(getDefaultFlags2());

			m_pkt.setParameterCount(2);
			m_pkt.setParameter(0, 1); // number of directory entries to return
			m_pkt.setParameter(1, FileAttribute.Volume);

			// Pack the search string

			m_pkt.resetBytePointer();
			m_pkt.packByte(DataType.ASCII);
			m_pkt.packString("", false);

			// Append a null resume key, to indicate the start of a new search

			m_pkt.packByte(DataType.VariableBlock);
			m_pkt.packWord(0);

			m_pkt.setByteCount();

			// Send/receive the search SMB packet

			m_pkt.ExchangeSMB(this, m_pkt, true);

			// Unpack the volume label

			m_pkt.resetBytePointer();
			m_pkt.skipBytes(33); // data type byte + length word + offset to file name/volume label

			String label = m_pkt.unpackString(m_pkt.isUnicode());

			// Create the volume information object

			volInfo = new VolumeInfo(label);
		}
		else {

			// Create the transaction request

			TransactBuffer reqBuf = new TransactBuffer(PacketTypeV1.Trans2QueryFileSys, null, 0, 2, 0);

			// Pack the parameter block

			DataBuffer paramBuf = reqBuf.getParameterBuffer();

			paramBuf.putShort(FileInfoLevel.FSInfoQueryVolume);

			// Perform the get file system information transaction

			TransPacket tpkt = new TransPacket(m_pkt.getBuffer());
			TransactBuffer respBuf = tpkt.doTransaction(this, reqBuf);

			// Unpack the volume information

			if ( respBuf != null && respBuf.hasDataBuffer()) {

				// Get the data buffer

				DataBuffer dataBuf = respBuf.getDataBuffer();

				// Get the volume information

				long createTime = dataBuf.getLong();
				int serNo = dataBuf.getInt();

				int nameLen = dataBuf.getInt();
				if ( respBuf.isUnicode())
					nameLen = nameLen / 2;
				dataBuf.skipBytes(2);

				String label = dataBuf.getString(nameLen, respBuf.isUnicode());

				// Create the volume information

				volInfo = new VolumeInfo(label, serNo, NTTime.toSMBDate(createTime));
			}
		}

		// Return the volume information

		return volInfo;
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

		m_pkt.setFlags(getDefaultFlags());
		m_pkt.setFlags2(getDefaultFlags2());

		m_pkt.setCommand(PacketTypeV1.CheckDirectory);
		m_pkt.setUserId(this.getUserId());
		m_pkt.setTreeId(this.getTreeId());
		m_pkt.setParameterCount(0);

		// Build the remote directory tree relative path

		String pathName = dir;
		if ( pathName.startsWith("\\") == false)
			pathName = PCShare.makePath(getWorkingDirectory(), dir);

		// Pack the directory name

		m_pkt.resetBytePointer();
		m_pkt.packByte(DataType.ASCII);
		m_pkt.packString(pathName, m_pkt.isUnicode());

		m_pkt.setByteCount();

		// Send/receive the SMB check directory packet

		m_pkt.ExchangeSMB(this, m_pkt);

		// Check if a valid response was received, indicates the path is a directory

		return m_pkt.isValidResponse();
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

		// Build the file name details

		String fileName = fname;
		if ( fileName.startsWith("\\") == false)
			fileName = PCShare.makePath(getWorkingDirectory(), fname);

		// Pre-NT dialect open file

		if ( getDialect() != Dialect.NT || isUnicode() == false) {

			// Initialize the SMB request to open an existing file

			m_pkt.setCommand(PacketTypeV1.OpenAndX);
			m_pkt.setFlags(getDefaultFlags());
			m_pkt.setFlags2(getDefaultFlags2());

			m_pkt.setUserId(this.getUserId());
			m_pkt.setTreeId(this.getTreeId());

			// Set the parameter words

			m_pkt.setParameterCount(15);
			m_pkt.setAndXCommand(0xFF); // no secondary command
			m_pkt.setParameter(1, 0); // offset to next command
			m_pkt.setParameter(2, 0x01); // return additional information
			m_pkt.setParameter(3, flags);
			m_pkt.setParameter(4, 0); // normal files only for now
			m_pkt.setParameter(5, 0); // file attributes
			m_pkt.setParameter(6, 0); // creation time
			m_pkt.setParameter(7, 0); // creation date

			// Default open mode is 'open if file exists'

			int openMode = FileAction.OpenIfExists;

			if ( AccessMode.getAccessMode(flags) == AccessMode.WriteOnly) {

				// Truncate the file if it exists, create file if it does not exist

				openMode = FileAction.CreateNotExist + FileAction.TruncateExisting;
			}
			else if ( AccessMode.getAccessMode(flags) == AccessMode.ReadWrite) {

				// Open the file if it exists, create the file if it does not exist

				openMode = FileAction.CreateNotExist + FileAction.OpenIfExists;
			}

			m_pkt.setParameter(8, openMode);
			m_pkt.setParameter(9, 0); // default allocation on create/truncate (long)
			m_pkt.setParameter(10, 0); // ... high word
			m_pkt.setParameter(11, 0);
			m_pkt.setParameter(12, 0);
			m_pkt.setParameter(13, 0);
			m_pkt.setParameter(14, 0);

			// Pack the file name string

			m_pkt.resetBytePointer();
			m_pkt.packString(fileName, m_pkt.isUnicode());

			m_pkt.setByteCount();

			// Send/receive the SMB file open packet

			m_pkt.ExchangeSMB(this, m_pkt, true);

			// Extract the file information from the received SMB packet

			int fid = m_pkt.getParameter(2);
			int attr = m_pkt.getParameter(3);
			int fsiz = (m_pkt.getParameter(7) << 16) + m_pkt.getParameter(6);

			// Create a file information object

			FileInfo finfo = new FileInfo(fname, fsiz, attr);

			// Create an SMB file object

			return new CIFSFile(this, finfo, fid);
		}
		else {

			// Default open mode is 'open if file exists'

			int openMode = FileAction.NTOpen;
			int accessMode = AccessMode.NTRead;

			if ( AccessMode.getAccessMode(flags) == AccessMode.WriteOnly) {

				// Truncate the file if it exists, create file if it does not exist

				openMode = FileAction.NTOverwriteIf;
				accessMode = AccessMode.NTWrite;
			}
			else if ( AccessMode.getAccessMode(flags) == AccessMode.ReadWrite) {

				// Open the file if it exists, create the file if it does not exist

				openMode = FileAction.NTOpenIf;
				accessMode = AccessMode.NTReadWrite;
			}

			// Open the remote file

			return NTCreate(fileName, accessMode, FileAttribute.NTNormal, SharingMode.ALL.intValue(), openMode, 0, WinNT.CreateFile);
		}
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

		m_pkt.setFlags(getDefaultFlags());
		m_pkt.setFlags2(getDefaultFlags2());

		m_pkt.setCommand(PacketTypeV1.RenameFile);
		m_pkt.setUserId(this.getUserId());
		m_pkt.setTreeId(this.getTreeId());

		m_pkt.setParameterCount(1);
		m_pkt.setParameter(0, attr);

		// Add the current working directory path to the file names if they do not contain a path

		String fromName = curnam;
		if ( fromName.startsWith("\\") == false)
			fromName = PCShare.makePath(getWorkingDirectory(), curnam);

		String toName = newnam;
		if ( toName.startsWith("\\") == false)
			toName = PCShare.makePath(getWorkingDirectory(), newnam);

		// Pack the current and new file names

		m_pkt.resetBytePointer();

		m_pkt.packByte(DataType.ASCII);
		m_pkt.packString(fromName, m_pkt.isUnicode());

		m_pkt.packByte(DataType.ASCII);
		m_pkt.packString(toName, m_pkt.isUnicode());

		m_pkt.setByteCount();

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

		// Create an SMB set file information packet

		m_pkt.setCommand(PacketTypeV1.SetFileAttributes);
		m_pkt.setUserId(this.getUserId());
		m_pkt.setTreeId(this.getTreeId());

		m_pkt.setFlags(getDefaultFlags());
		m_pkt.setFlags2(getDefaultFlags2());

		// Set the call parameters

		m_pkt.setParameterCount(8);

		m_pkt.setParameter(0, finfo.getFileAttributes());
		m_pkt.setParameter(1, finfo.getModifyDateTime().asSMBTime());
		m_pkt.setParameter(2, finfo.getModifyDateTime().asSMBDate());

		for (int i = 3; i < 8; i++)
			m_pkt.setParameter(i, 0);

		// Build the full path string

		String fileName = fname;
		if ( fname.startsWith("\\") == false)
			fileName = PCShare.makePath(getWorkingDirectory(), fname);

		// Pack the file name

		m_pkt.resetBytePointer();
		m_pkt.packByte(DataType.ASCII);
		m_pkt.packString(fileName, m_pkt.isUnicode());

		m_pkt.setByteCount();

		// Send/receive the SMB set file information packet

		m_pkt.ExchangeSMB(this, m_pkt, true);
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

		// Create an SMB set file information packet

		m_pkt.setCommand(PacketTypeV1.SetInformation2);
		m_pkt.setUserId(this.getUserId());
		m_pkt.setTreeId(this.getTreeId());

		m_pkt.setFlags(getDefaultFlags());
		m_pkt.setFlags2(getDefaultFlags2());

		// Set the call parameters

		m_pkt.setParameterCount(7);

		m_pkt.setParameter(0, file.getFileId());

		// Pack the new creation date/time, if available

		if ( finfo.hasCreationDateTime()) {
			m_pkt.setParameter(1, finfo.getCreationDateTime().asSMBTime());
			m_pkt.setParameter(2, finfo.getCreationDateTime().asSMBDate());
		}
		else {
			m_pkt.setParameter(1, 0);
			m_pkt.setParameter(2, 0);
		}

		// Pack the new access date/time, if available

		if ( finfo.hasAccessDateTime()) {
			m_pkt.setParameter(3, finfo.getAccessDateTime().asSMBTime());
			m_pkt.setParameter(4, finfo.getAccessDateTime().asSMBDate());
		}
		else {
			m_pkt.setParameter(3, 0);
			m_pkt.setParameter(4, 0);
		}

		// Pack the new modify date/time, if available

		if ( finfo.hasModifyDateTime()) {
			m_pkt.setParameter(5, finfo.getModifyDateTime().asSMBTime());
			m_pkt.setParameter(6, finfo.getModifyDateTime().asSMBDate());
		}
		else {
			m_pkt.setParameter(5, 0);
			m_pkt.setParameter(6, 0);
		}

		// Clear the byte count

		m_pkt.setByteCount(0);

		// Send/receive the SMB set file information packet

		m_pkt.ExchangeSMB(this, m_pkt, true);
	}

	/**
	 * Set file attributes for the specified file, using the file name
	 * 
	 * @param fname File name of the file to set information for.
	 * @param attrib File attributes mask
	 * @see org.filesys.server.filesys.FileAttribute
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final void setFileAttributes(String fname, int attrib)
		throws java.io.IOException, SMBException {

		// Create an SMB set file information packet

		m_pkt.setCommand(PacketTypeV1.SetFileAttributes);
		m_pkt.setUserId(this.getUserId());
		m_pkt.setTreeId(this.getTreeId());

		m_pkt.setFlags(getDefaultFlags());
		m_pkt.setFlags2(getDefaultFlags2());

		// Set the call parameters

		m_pkt.setParameterCount(8);

		m_pkt.setParameter(0, attrib);
		m_pkt.setParameter(1, 0);
		m_pkt.setParameter(2, 0);

		for (int i = 3; i < 8; i++)
			m_pkt.setParameter(i, 0);

		// Build the full path string

		String fileName = fname;
		if ( fname.startsWith("\\") == false)
			fileName = PCShare.makePath(getWorkingDirectory(), fname);

		// Pack the file name

		m_pkt.resetBytePointer();
		m_pkt.packByte(DataType.ASCII);
		m_pkt.packString(fileName, m_pkt.isUnicode());

		m_pkt.setByteCount();

		// Send/receive the SMB set file information packet

		m_pkt.ExchangeSMB(this, m_pkt, true);
	}

	/**
	 * Start a search of the specified directory returning information for each file/directory
	 * found.
	 * 
	 * @param dir Directory to start searching. If the directory string does not start with a '\'
	 *            then the directory name is prepended with the current working directory.
	 * @param attr Search attributes, to determine the types of files/directories returned. @see
	 *            FileAttribute
	 * @param level Information level required
	 * @return SMBSearchContext for this search, else null
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public final SearchContext StartSearch(String dir, int attr, int level)
		throws java.io.IOException, SMBException {

		// Create a new SMB search context

		TransSearchContext srch = new TransSearchContext(this);
		if ( srch == null)
			return null;

		// Start the search and return the search context

		srch.StartSearch(dir, attr, level);
		return srch;
	}

	/**
	 * Perform an NTCreateAndX SMB to create/open a file or directory
	 * 
	 * @param name File/directory name
	 * @param access Desired access mode.
	 * @see org.filesys.server.filesys.AccessMode
	 * @param attrib Required file attributes.
	 * @see org.filesys.server.filesys.FileAttribute
	 * @param sharing Shared access mode
	 * @param exists Action to take if file/directory exists.
	 * @see org.filesys.server.filesys.FileAction
	 * @param initSize Initial file allocation size, in bytes
	 * @param createOpt Create file options
	 * @return CIFSFile
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final CIFSFile NTCreate(String name, int access, int attrib, int sharing, int exists, long initSize, int createOpt)
		throws IOException, SMBException {

		// Call the main NTCreate method

		return NTCreateInternal(name, 0, access, attrib, sharing, exists, initSize, createOpt, true);
	}

	/**
	 * Perform an NTCreateAndX SMB to create/open a file with an oplock
	 * 
	 * @param name File/directory name
	 * @param oplockFlags int
	 * @param oplockIface OplockInterface
	 * @param access Desired access mode.
	 * @see org.filesys.server.filesys.AccessMode
	 * @param attrib Required file attributes.
	 * @see org.filesys.server.filesys.FileAttribute
	 * @param sharing Shared access mode
	 * @param exists Action to take if file/directory exists.
	 * @see org.filesys.server.filesys.FileAction
	 * @param initSize Initial file allocation size, in bytes
	 * @param createOpt Create file options
	 * @return CIFSFile
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final CIFSFile NTCreateWithOplock(String name, int oplockFlags, OplockInterface oplockIface,
											 int access, int attrib, int sharing, int exists, long initSize, int createOpt)
		throws IOException, SMBException {

		// Call the main NTCreate method

		CIFSFile cifsFile = NTCreateInternal(name, oplockFlags, access, attrib, sharing, exists, initSize, createOpt, true);
		if ( cifsFile != null && cifsFile.getOplockType() != OpLockType.LEVEL_NONE.intValue()) {
			
			// Set the oplock interface
			
			cifsFile.setOplockInterface( oplockIface);
			
			// Add the file to the list of oplocked files, need to access the file to call
			// the oplock interface if an oplock break is received asynchronously from the server
			
			if ( m_oplockFiles == null)
				m_oplockFiles = new HashMap<Integer, CIFSFile>();
			m_oplockFiles.put( new Integer( cifsFile.getFileId()), cifsFile);
		}
		
		// Return the file
		
		return cifsFile;
	}

	/**
	 * Perform an NT query security descriptor transaction for the specified file or directory
	 * 
	 * @param fid File identifier, via SMBFile.getFileId() of an open file.
	 * @param flags Security descriptor elements to return (Owner, Group, SACL, DACL).
	 * @see SecurityDescriptor
	 * @return SecurityDescriptor
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final SecurityDescriptor NTQuerySecurityDescriptor(int fid, int flags)
		throws IOException, SMBException {

		// Check if we have negotiated NT dialect

		if ( getDialect() != Dialect.NT)
			throw new SMBException(SMBStatus.NetErr, SMBStatus.NETUnsupported);

		// Create the request transaction buffer

		TransactBuffer reqBuf = new TransactBuffer(PacketTypeV1.NTTransQuerySecurityDesc, null, 0, 8, 0);

		// Pack the parameter block for the request

		DataBuffer paramBuf = reqBuf.getParameterBuffer();

		paramBuf.putShort(fid);
		paramBuf.putShort(0);
		paramBuf.putInt(flags);

		// Perform the query security descriptor transaction

		NTTransPacket ntPkt = new NTTransPacket();
		TransactBuffer respBuf = ntPkt.doTransaction(this, reqBuf);

		// Check if a security descriptor has been returned

		SecurityDescriptor secDesc = null;

		if ( respBuf != null && respBuf.hasDataBuffer()) {

			// Get the returned data

			DataBuffer dataBuf = respBuf.getDataBuffer();

			try {
				secDesc = new SecurityDescriptor();
				secDesc.loadDescriptor(dataBuf.getBuffer(), dataBuf.getOffset());
			}
			catch (LoadException ex) {
				secDesc = null;
			}
		}

		// Return the security descriptor

		return secDesc;
	}

	/**
	 * Set the security descriptor for the specified file/directory
	 * 
	 * @param fid File identifier, via SMBFile.getFileId() of an open file.
	 * @param secdesc Security descriptor
	 * @param flags Fields to set (Owner, Group, SACL, DACL).
	 * @see SecurityDescriptor
	 * @exception IOException If a network error occurs
	 * @exception SMBException If an SMB level error occurs
	 * @exception SaveException If the security descriptor cannot be stored
	 */
	public final void NTSetSecurityDescriptor(int fid, SecurityDescriptor secdesc, int flags)
		throws IOException, SMBException, SaveException {

		// Check if we have negotiated NT dialect

		if ( getDialect() != Dialect.NT)
			throw new SMBException(SMBStatus.NetErr, SMBStatus.NETUnsupported);

		// Create the request transaction buffer

		TransactBuffer reqBuf = new TransactBuffer(PacketTypeV1.NTTransSetSecurityDesc, null, 0, 8, 65000);

		// Pack the parameter block

		DataBuffer paramBuf = reqBuf.getParameterBuffer();

		paramBuf.putShort(fid);
		paramBuf.putShort(0);
		paramBuf.putInt(flags);

		// Pack the data block

		DataBuffer dataBuf = reqBuf.getDataBuffer();
		int len = secdesc.saveDescriptor(dataBuf.getBuffer(), 0);
		dataBuf.setLength(len);

		// Perform the set security descriptor transaction

		NTTransPacket ntPkt = new NTTransPacket();
		ntPkt.doTransaction(this, reqBuf);
	}

	/**
	 * Add a change notification filter for the specified directory
	 * 
	 * @param fid File id, from SMBFile.getFileId() of an open directory. The directory should be
	 *            opened using the NTCreate() method.
	 * @param filter Directory watch filter flags. @see org.filesys.client.nt.NotifyChange.
	 * @param watchTree true to watch sub-directories, false to watch the specified directory only
	 * @param handler DirectoryWatcher implementation. @see
	 *            DirectoryWatcher
	 * @param autoResub true to automatically resubmit the notification filter after an event is
	 *            received
	 * @return AsynchRequest
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final AsynchRequest NTNotifyChange(int fid, int filter, boolean watchTree, DirectoryWatcher handler, boolean autoResub)
		throws IOException, SMBException {

		// Create an asynchronous request to hold the notify details

		NotifyChangeAsynchRequest areq = new NotifyChangeAsynchRequest(-1, fid, filter, watchTree, handler);
		areq.setAutoReset(autoResub);

		// Call the main notify change method to set the notify request

		return NTNotifyChange(areq);
	}

	/**
	 * Add a change notification filter for the specified directory
	 * 
	 * @param areq AsynchRequest
	 * @return AsynchRequest
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final AsynchRequest NTNotifyChange(AsynchRequest areq)
		throws IOException, SMBException {

		// Check if we have negotiated NT dialect

		if ( getDialect() != Dialect.NT)
			throw new SMBException(SMBStatus.NetErr, SMBStatus.NETUnsupported);

		// Make sure the request is a notify change request

		if ( areq instanceof NotifyChangeAsynchRequest == false)
			throw new IOException("Invalid asynchronous request class, " + areq.getClass().getName());

		// Get the notify change asynchronous request

		NotifyChangeAsynchRequest nreq = (NotifyChangeAsynchRequest) areq;

		// Build the NT notify change transaction SMB packet

		NTTransPacket tpkt = new NTTransPacket();

		tpkt.setUserId(getUserId());
		tpkt.setTreeId(getTreeId());

		tpkt.setFlags(getDefaultFlags());
		tpkt.setFlags2(getDefaultFlags2());

		// Set the multiplex id to identify this notify request

		int mid = getNextMultiplexId();
		tpkt.setMultiplexId(mid);

		// Save the request id and clear the completed status of the request

		nreq.setId(mid);
		nreq.setCompleted(false);

		// Initialize the NT transaction packet

		tpkt.InitializeNTTransact(PacketTypeV1.NTTransNotifyChange, null, 0, null, 0, 4, 32, 0);

		tpkt.resetSetupPointer();
		tpkt.packInt(nreq.getFilter());
		tpkt.packWord(nreq.getFileId());
		tpkt.packByte(nreq.hasWatchTree() ? 1 : 0);
		tpkt.packByte(0);

		tpkt.setByteCount(0);

		// Send the notify change transaction
		//
		// Note: There is no response until the notification triggers

		tpkt.SendSMB(this);

		// Add the request to the pending list and return the request

		addAsynchronousRequest(nreq);
		return nreq;
	}

	/**
	 * Cancel an outstanding request. Used to cancel change notifications.
	 * 
	 * @param areq AsynchRequest
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void NTCancel(AsynchRequest areq)
		throws IOException, SMBException {

		// Check if we have negotiated NT dialect

		if ( getDialect() != Dialect.NT)
			throw new SMBException(SMBStatus.NetErr, SMBStatus.NETUnsupported);

		// Check if the request has auto-resubmit enabled, if so then disable the resubmit

		if ( areq.hasAutoReset())
			areq.setAutoReset(false);

		// Check if the request has already completed, if so then there is no need to cancel it

		if ( areq.hasCompleted())
			return;

		// Check if there is any data available for this network session, the request we are about
		// to
		// cancel may have just completed

		if ( getSession().hasData()) {

			// Clear out the recieve buffer

			pingServer();

			// Check if the request has now completed

			if ( areq.hasCompleted())
				return;
		}

		// Build the NTCancel SMB packet

		m_pkt.setFlags(getDefaultFlags());
		m_pkt.setFlags2(getDefaultFlags2());

		m_pkt.setCommand(PacketTypeV1.NTCancel);
		m_pkt.setUserId(getUserId());
		m_pkt.setTreeId(getTreeId());
		m_pkt.setMultiplexId(areq.getId());

		m_pkt.setParameterCount(0);
		m_pkt.setByteCount(0);

		// Send/receive the NT cancel request

		m_pkt.ExchangeSMB(this, m_pkt, false);

		// Check the return status

		boolean isValid = m_pkt.isValidResponse();

		if ( isValid == true)
			return;
		else if ( m_pkt.isLongErrorCode() && m_pkt.getLongErrorCode() == SMBStatus.NTCancelled)
			return;
		else {

			// Throw the SMB exception

			if ( m_pkt.hasLongErrorCode())
				throw new SMBException(SMBStatus.NTErr, m_pkt.getLongErrorCode());
			else
				throw new SMBException(m_pkt.getErrorClass(), m_pkt.getErrorCode());
		}
	}

	/**
	 * NT I/O control
	 * 
	 * @param ctrlCode int
	 * @param fid int
	 * @param fsctl boolean
	 * @param data byte[]
	 * @param dlen int
	 * @param filter int
	 * @return DataBuffer
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final DataBuffer NTIOCtl(int ctrlCode, int fid, boolean fsctl, byte[] data, int dlen, int filter)
		throws IOException, SMBException {

		// Check if we have negotiated NT dialect

		if ( getDialect() != Dialect.NT)
			throw new SMBException(SMBStatus.NetErr, SMBStatus.NETUnsupported);

		// Create the request transaction buffer

		TransactBuffer reqBuf = new TransactBuffer(PacketTypeV1.NTTransIOCtl, 8, 0, data, 0, dlen);

		// Pack the setup block

		DataBuffer setupBuf = reqBuf.getSetupBuffer();

		setupBuf.putInt(ctrlCode);
		setupBuf.putShort(fid);
		setupBuf.putByte(fsctl ? 1 : 0);
		setupBuf.putByte(filter);

		// Perform the I/O control transaction

		NTTransPacket ntPkt = new NTTransPacket();
		TransactBuffer respBuf = ntPkt.doTransaction(this, reqBuf);

		// Check if there is any return data

		if ( respBuf != null)
			return respBuf.getDataBuffer();
		return null;
	}

	/**
	 * Get file information for the specified open file/directory, returning the requested
	 * information level
	 * 
	 * @param fid File id for the file or directory, from SMBFile.getFileId().
	 * @param level Information level. @see FileInfoLevel
	 * @return FileInfo
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final FileInfo NTGetFileInformation(int fid, int level)
		throws IOException, SMBException {

		// Create the request transaction buffer

		TransactBuffer reqBuf = new TransactBuffer(PacketTypeV1.Trans2QueryFile, null, 0, 4, 0);

		// Pack the parameter block

		DataBuffer paramBuf = reqBuf.getParameterBuffer();

		paramBuf.putShort(fid);
		paramBuf.putShort(level);

		// Perform the get file information transaction

		TransPacket tpkt = new TransPacket(m_pkt.getBuffer());
		TransactBuffer respBuf = tpkt.doTransaction(this, reqBuf);

		// Unpack the received file information data

		FileInfo finfo = null;

		if ( respBuf != null && respBuf.hasDataBuffer()) {

			// Unpack the file information

			DataBuffer buf = respBuf.getDataBuffer();

			switch (level) {
				case FileInfoLevel.PathStandard:
					finfo = FileInfoPacker.unpackFileInfoStandard("", buf, false);
					break;
				case FileInfoLevel.PathQueryEASize:
					finfo = FileInfoPacker.unpackFileInfoStandard("", buf, true);
					break;
				case FileInfoLevel.PathAllEAs:
					break;
				case FileInfoLevel.PathFileBasicInfo:
					finfo = FileInfoPacker.unpackQueryBasicInfo("", buf);
					break;
				case FileInfoLevel.PathFileStandardInfo:
					finfo = FileInfoPacker.unpackQueryStandardInfo("", buf);
					break;
				case FileInfoLevel.PathFileEAInfo:
					finfo = FileInfoPacker.unpackQueryEAInfo("", buf);
					break;
				case FileInfoLevel.PathFileNameInfo:
					finfo = FileInfoPacker.unpackQueryNameInfo(buf, respBuf.isUnicode());
					break;
				case FileInfoLevel.PathFileAllInfo:
					finfo = FileInfoPacker.unpackQueryAllInfo(buf, respBuf.isUnicode());
					break;
				case FileInfoLevel.PathFileAltNameInfo:
					finfo = FileInfoPacker.unpackQueryNameInfo(buf, respBuf.isUnicode());
					break;
				case FileInfoLevel.PathFileStreamInfo:
					finfo = FileInfoPacker.unpackQueryStreamInfo("", buf, respBuf.isUnicode());
					break;
				case FileInfoLevel.PathFileCompressionInfo:
					finfo = FileInfoPacker.unpackQueryCompressionInfo("", buf);
					break;
			}
		}

		// If the file information is valid, set the file id so the file information can be used
		// to set information

		if ( finfo != null)
			finfo.setFileId(fid);

		// Return the file information

		return finfo;
	}

	/**
	 * Get file information for the specified open file/directory, returning the requested
	 * information level
	 * 
	 * @param fid File id for the file or directory, from SMBFile.getFileId().
	 * @param level Information level. @see FileInfoLevel
	 * @return TransactBuffer
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final TransactBuffer NTGetFileInformationRaw(int fid, int level)
		throws IOException, SMBException {

		// Create the request transaction buffer

		TransactBuffer reqBuf = new TransactBuffer(PacketTypeV1.Trans2QueryFile, null, 0, 4, 0);

		// Pack the parameter block

		DataBuffer paramBuf = reqBuf.getParameterBuffer();

		paramBuf.putShort(fid);
		paramBuf.putShort(level);

		// Perform the get file information transaction

		TransPacket tpkt = new TransPacket(m_pkt.getBuffer());
		return tpkt.doTransaction(this, reqBuf);
	}

	/**
	 * Set file information that allows setting different information levels
	 * 
	 * @param finfo FileInfo
	 * @param level Information level. @see FileInfoLevel
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void NTSetFileInformation(FileInfo finfo, int level)
		throws IOException, SMBException {

		// Create the request transaction buffer

		TransactBuffer reqBuf = new TransactBuffer(PacketTypeV1.Trans2SetFile, null, 0, 6, 65000);

		// Pack the parameter block

		DataBuffer paramBuf = reqBuf.getParameterBuffer();

		paramBuf.putShort(finfo.getFileId());
		paramBuf.putShort(level);
		paramBuf.putShort(0);

		// Pack the file information into the data buffer

		DataBuffer dataBuf = reqBuf.getDataBuffer();

		switch (level) {
			case FileInfoLevel.SetStandard:
				FileInfoPacker.packFileInfoStandard(finfo, dataBuf, false);
				break;
			case FileInfoLevel.SetQueryEASize:
				FileInfoPacker.packFileInfoStandard(finfo, dataBuf, true);
				break;
			case FileInfoLevel.SetBasicInfo:
				FileInfoPacker.packFileBasicInfo(finfo, dataBuf);
				break;
			case FileInfoLevel.SetDispositionInfo:
				break;
			case FileInfoLevel.SetAllocationInfo:
				break;
			case FileInfoLevel.SetEndOfFileInfo:
				break;
		}
		;

		// Perform the get file information transaction

		TransPacket tpkt = new TransPacket(m_pkt.getBuffer());
		tpkt.doTransaction(this, reqBuf);
	}

	/**
	 * Set the delete on close flag for an open file
	 * 
	 * @param fid File id for the file or directory, from SMBFile.getFileId().
	 * @param delFlag true to delete the file on close, or false to clear a previous delete on close
	 *            request
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void NTSetDeleteOnClose(int fid, boolean delFlag)
		throws IOException, SMBException {

		// Create the data block for the set file disposition info level (0x102)

		byte[] dblock = new byte[4];
		dblock[0] = delFlag == true ? (byte) 1 : (byte) 0;

		// Set the delete on close setting for the open file

		NTSetFileInformationRaw(fid, dblock, 2, 0x102);
	}

	/**
	 * Set the end of file position for the open file
	 * 
	 * @param fid File id for the file or directory, from SMBFile.getFileId().
	 * @param pos New end of file position
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void NTSetEndOfFile(int fid, long pos)
		throws IOException, SMBException {

		// Create the data block for the set end of file info level (0x104)

		byte[] dblock = new byte[8];
		DataPacker.putIntelLong(pos, dblock, 0);

		// Set the end of file position for the open file

		NTSetFileInformationRaw(fid, dblock, 8, 0x104);
	}

	/**
	 * Set the file allocation size for the open file
	 * 
	 * @param fid File id for the file or directory, from SMBFile.getFileId().
	 * @param alloc New file allocation size
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void NTSetFileAllocation(int fid, long alloc)
		throws IOException, SMBException {

		// Create the data block for the set file allocation info level (0x103)

		byte[] dblock = new byte[8];
		DataPacker.putIntelLong(alloc, dblock, 0);

		// Set the file allocation for the open file

		NTSetFileInformationRaw(fid, dblock, 8, 0x103);
	}

	/**
	 * Set file information that allows setting different information levels
	 * 
	 * @param fid File id for the file or directory, from SMBFile.getFileId().
	 * @param data Raw file information data block.
	 * @param dlen Raw data block length.
	 * @param level File information level. @see FileInfoLevel
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	private final void NTSetFileInformationRaw(int fid, byte[] data, int dlen, int level)
		throws IOException, SMBException {

		// Create the request transaction buffer

		TransactBuffer reqBuf = new TransactBuffer(PacketTypeV1.Trans2SetFile, 0, 6, data, 0, dlen);

		// Pack the parameter block

		DataBuffer paramBuf = reqBuf.getParameterBuffer();

		paramBuf.putShort(fid);
		paramBuf.putShort(level);
		paramBuf.putShort(0);

		// Perform the get file information transaction

		TransPacket tpkt = new TransPacket(m_pkt.getBuffer());
		tpkt.doTransaction(this, reqBuf);
	}

	/**
	 * Get the device information
	 * 
	 * @return DeviceInfo @see DeviceInfo
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final DeviceInfo NTGetDeviceInfo()
		throws IOException, SMBException {

		// Create the request transaction buffer

		TransactBuffer reqBuf = new TransactBuffer(PacketTypeV1.Trans2QueryFileSys, null, 0, 2, 0);

		// Pack the parameter block

		DataBuffer paramBuf = reqBuf.getParameterBuffer();

		paramBuf.putShort(FileInfoLevel.FSInfoQueryDevice);

		// Perform the get file information transaction

		TransPacket tpkt = new TransPacket(m_pkt.getBuffer());
		TransactBuffer respBuf = tpkt.doTransaction(this, reqBuf);

		// Unpack the received device information data

		DeviceInfo devInfo = null;

		if ( respBuf != null && respBuf.hasDataBuffer()) {

			// Unpack the device information

			DataBuffer buf = respBuf.getDataBuffer();

			int typ = buf.getInt();
			int chr = buf.getInt();

			// Return the device information

			devInfo = new DeviceInfo(typ, chr);
		}

		// Return the device information

		return devInfo;
	}

	/**
	 * Get the device attributes information
	 * 
	 * @return DeviceAttributesInfo
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final DeviceAttributesInfo NTGetDeviceAttributes()
		throws IOException, SMBException {

		// Create the request transaction buffer

		TransactBuffer reqBuf = new TransactBuffer(PacketTypeV1.Trans2QueryFileSys, null, 0, 2, 0);

		// Pack the parameter block

		DataBuffer paramBuf = reqBuf.getParameterBuffer();

		paramBuf.putShort(FileInfoLevel.FSInfoQueryAttribute);

		// Perform the get file information transaction

		TransPacket tpkt = new TransPacket(m_pkt.getBuffer());
		TransactBuffer respBuf = tpkt.doTransaction(this, reqBuf);

		// Unpack the received device attribute information data

		DeviceAttributesInfo attrInfo = null;

		if ( respBuf != null && respBuf.hasDataBuffer()) {

			// Unpack the device attribute information

			DataBuffer buf = respBuf.getDataBuffer();

			int attr = buf.getInt();
			int maxLen = buf.getInt();
			int lblLen = buf.getInt();

			if ( respBuf.isUnicode())
				lblLen = lblLen / 2;

			String label = buf.getString(lblLen, respBuf.isUnicode());

			// Return the device attributes

			attrInfo = new DeviceAttributesInfo(attr, maxLen, label);
		}

		// Return the device attribute information

		return attrInfo;
	}

	/**
	 * Get file information for the specified file.
	 * 
	 * @param fname File name of the file to return information for.
	 * @param level Information level required. @see FileInfoLevel
	 * @return TransactBuffer
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception java.io.FileNotFoundException If the remote file does not exist.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final TransactBuffer getFileInformationRaw(String fname, int level)
		throws java.io.IOException, java.io.FileNotFoundException, SMBException {

		// Build the file name/path string

		String pathName = fname;
		if ( pathName.startsWith("\\") == false)
			pathName = PCShare.makePath(getWorkingDirectory(), fname);

		// Create the request transaction buffer

		TransactBuffer reqBuf = new TransactBuffer(PacketTypeV1.Trans2QueryPath, null, 0, 512, 0);

		// Pack the parameter block

		DataBuffer paramBuf = reqBuf.getParameterBuffer();

		paramBuf.putShort(level);
		paramBuf.putInt(0);
		paramBuf.putString(pathName, isUnicode());

		// Perform the get file information transaction

		TransPacket tpkt = new TransPacket(m_pkt.getBuffer());
		return tpkt.doTransaction(this, reqBuf);
	}

	/**
	 * Return the details for a symlink file/folder
	 * 
	 * @param linkPath String
	 * @return SymLink
	 * @exception Exception Error getting the symlink details
	 */
	public final SymLink getSymLinkDetails(String linkPath)
		throws Exception {

		// Open the symlink file

		CIFSFile linkFile = NTCreateInternal(linkPath, 0, AccessMode.NTRead + AccessMode.NTReadControl + AccessMode.NTReadAttrib
				+ AccessMode.NTReadEA, FileAttribute.NTNormal, SharingMode.READ_WRITE.intValue(), FileAction.NTOpen, 0,
				WinNT.CreateReparsePoint, false);

		SymLink symLink = null;
		Exception retError = null;

		try {

			// Make sure the file is a reparse point

			if ( linkFile.isReparsePoint()) {

				// Get the symlink details

				int ioctlCode = NTIOCtl.makeControlCode(NTIOCtl.DeviceFileSystem, NTIOCtl.FsCtlGetReparsePoint,
						NTIOCtl.MethodBuffered, NTIOCtl.AccessAny);
				DataBuffer linkBuf = NTIOCtl(ioctlCode, linkFile.getFileId(), true, null, 0, 0);

				// Parse the returned structure

				symLink = new SymLink(linkBuf);
			}
			else {

				// Return an exception

				retError = new IOException("Not a reparse point, " + linkPath);
			}
		}
		catch (Exception ex) {

			// Save the error

			retError = ex;
		}
		finally {

			// Close the link file

			try {
				linkFile.Close();
			}
			catch (Exception ex) {
			}
		}

		// Check if there is an error to return

		if ( retError != null)
			throw retError;

		// Return the symlink details

		return symLink;
	}

	/**
	 * Process incoming data checking for asynchronous response packets from the server
	 * 
	 * @param waitTime Receive timeout in milliseconds, zero for no timeout or -1 to not wait for
	 *            data
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void checkForAsynchReceive(int waitTime)
		throws IOException, SMBException {

		// Check if there is any data in the socket receive buffer, if the caller does not want
		// to wait for a packet then return immediately

		if ( waitTime == -1 && getSession().hasData() == false) {

			// Check if we need to send an echo packet to the server to keep the SMB session alive

			if ( (m_pkt.getLastPacketSendTime() + SessionKeepAlive) < System.currentTimeMillis())
				pingServer();
			return;
		}

		// Wait for an asynchronous response from the server

		m_pkt.ReceiveAsynchSMB(this, waitTime == -1 ? 0 : waitTime);

		// Check if we need to send an echo packet to the server to keep the SMB session alive.
		//
		// The asynchronous receive will usually result in the request being reset on the server, if
		// not then we may need to send an echo request.

		if ( (m_pkt.getLastPacketSendTime() + SessionKeepAlive) < System.currentTimeMillis())
			pingServer();
	}

	
	/**
	 * Refresh the file information for an open file
	 * 
	 * @param smbFile SMBFile
     * @exception IOException Socket error
     * @exception SMBException SMB error
	 */
	public void refreshFileInformation( SMBFile smbFile)
		throws IOException, SMBException {
		
	}
	
	/**
	 * Process an asynchronous response packet
	 * 
	 * @param pkt SMBPacket
	 */
	protected void processAsynchResponse(SMBPacket pkt) {

		// Check for a locking request from the server, an oplock break
		
		if ( pkt.isRequest() == true && pkt.getCommand() == PacketTypeV1.LockingAndX) {
			
			// Unpack the file id and flags
			
			int fileId = pkt.getParameter( 2);
			int flags  = pkt.getParameter( 3);
			
			// Check for an oplock break
			
			if ( m_oplockFiles != null && ( flags & LockingAndX.OplockBreak) != 0) {

				try {
					
					// Find the oplocked file
					
					CIFSFile cifsFile = m_oplockFiles.get( new Integer( fileId));
					int breakToOpLock = OpLockType.LEVEL_NONE.intValue();
					
					if ( cifsFile != null) {
						
						// Check if the file has an oplock callback interface
						
						if ( cifsFile.getOplockInterface() != null) {

							// Call the oplock interface
							
							breakToOpLock = cifsFile.getOplockInterface().oplockBreak( cifsFile);
						}
						else {
							
							// Flush any pending data on the file 
							
							cifsFile.Flush();
						}
					}
					
					// Check if an oplock break response should be sent
					
					if ( cifsFile.getOplockInterface().sendAutomaticBreakResponse() == true) {

						// Build an oplock break response
						
						SMBPacket respPkt = new SMBPacket( 128);
						
						respPkt.setCommand(PacketTypeV1.LockingAndX);
						respPkt.setUserId(this.getUserId());
						respPkt.setTreeId(this.getTreeId());
		
						respPkt.setFlags(getDefaultFlags() + SMBPacket.FLG_RESPONSE);
						respPkt.setFlags2(getDefaultFlags2());
		
						respPkt.setParameterCount(8);
						respPkt.setAndXCommand( PacketTypeV1.NoChainedCommand);
						respPkt.setParameter(1, 0);							// AndX offset
						respPkt.setParameter(2, fileId);
						
						// Break the oplock, or break to a level II shared oplock
						
						if ( breakToOpLock == OpLockType.LEVEL_II.intValue())
							respPkt.setParameter(3, LockingAndX.OplockBreak + LockingAndX.Level2OpLock);
						else
							respPkt.setParameter(3, LockingAndX.OplockBreak);
							
						respPkt.setParameterLong(4, 0);						// timeout
						respPkt.setParameter(6, 0);							// number of unlocks
						respPkt.setParameter(7, 0);							// number of locks
						
						respPkt.setByteCount( 0);
						
						// Send the oplock break to the server
						//
						// Note: The response flag must be set, and we do not expect a response from the server
						
						respPkt.SendSMB( this);
						
						// Set the new oplock type on the file
						
						cifsFile.setOplockType( breakToOpLock);
						cifsFile.setOplockInterface( null);
					}
				}
				catch (Exception ex) {
						
				}
			}
		}
		else {
			
			// Check if there are any pending asynchronous requests queued
	
			if ( m_asynchRequests == null || m_asynchRequests.size() == 0)
				return;
	
			// Find the matching asynchronous request and remove from the pending list
	
			AsynchRequest areq = removeAsynchronousRequest(pkt.getMultiplexId());
			if ( areq == null)
				return;
	
			// Mark the asynchronous request as completed
	
			areq.setCompleted(true);
	
			// Pass the packet to the asynchronous request for processing
	
			areq.processResponse(this, pkt);
	
			// Check if the request should be automatically resubmitted
	
			if ( areq.hasAutoReset()) {
	
				// Resubmit the request
	
				if ( areq.resubmitRequest(this, null) == true)
					addAsynchronousRequest(areq);
			}
		}
	}

	/**
	 * Add an asynchronous request to the list of pending requests
	 * 
	 * @param req AsynchRequest
	 */
	protected final void addAsynchronousRequest(AsynchRequest req) {

		// Check if the asynchronous request list has been allocated

		if ( m_asynchRequests == null)
			m_asynchRequests = new ArrayList<AsynchRequest>();

		// Add the request to the list

		m_asynchRequests.add(req);
	}

	/**
	 * Remove an asynchronous request from the pending list
	 * 
	 * @param id int
	 * @return AsynchRequest
	 */
	protected final AsynchRequest removeAsynchronousRequest(int id) {

		// Check if the list is valid

		if ( m_asynchRequests == null)
			return null;

		// Find the request

		AsynchRequest areq = null;
		int idx = 0;

		while (idx < m_asynchRequests.size() && areq == null) {

			// Get the current request and check if it is the required request

			AsynchRequest curReq = m_asynchRequests.get(idx);
			if ( curReq.getId() == id)
				areq = curReq;
			else
				idx++;
		}

		// Remove the request from the list

		if ( areq != null)
			m_asynchRequests.remove(areq);

		// Return the removed request

		return areq;
	}

	/**
	 * Remove an asynchronous request from the pending list
	 * 
	 * @param req AsynchRequest
	 * @return AsynchRequest
	 */
	protected final AsynchRequest removeAsynchronousRequest(AsynchRequest req) {

		// Check if the list is valid

		if ( m_asynchRequests == null)
			return null;

		// Remove the request from the list

		m_asynchRequests.remove(req);
		return req;
	}

	/**
	 * Perform an NTCreateAndX SMB to create/open a file or directory
	 * 
	 * @param name File/directory name
	 * @param createFlags int
	 * @param access Desired access mode.
	 * @see org.filesys.server.filesys.AccessMode
	 * @param attrib Required file attributes.
	 * @see org.filesys.server.filesys.FileAttribute
	 * @param sharing Shared access mode
	 * @param exists Action to take if file/directory exists.
	 * @see org.filesys.server.filesys.FileAction
	 * @param initSize Initial file allocation size, in bytes
	 * @param createOpt Create file options
	 * @param throwErr Throw errors from the CIFS packet exchange
	 * @return CIFSFile
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	protected final CIFSFile NTCreateInternal(String name, int createFlags, int access, int attrib, int sharing, int exists, long initSize,
			int createOpt, boolean throwErr)
		throws IOException, SMBException {

		// Check if we have negotiated NT dialect

		if ( getDialect() != Dialect.NT)
			throw new SMBException(SMBStatus.NetErr, SMBStatus.NETUnsupported);

		// Build the NTCreateAndX SMB packet

		m_pkt.setFlags(getDefaultFlags());
		m_pkt.setFlags2(getDefaultFlags2());

		m_pkt.setCommand(PacketTypeV1.NTCreateAndX);
		m_pkt.setUserId(getUserId());
		m_pkt.setTreeId(getTreeId());

		m_pkt.setParameterCount(24);
		m_pkt.resetParameterPointer();

		m_pkt.packByte(0xFF); // no chained command
		m_pkt.packByte(0); // reserved
		m_pkt.packWord(0); // AndX offset
		m_pkt.packByte(0); // reserved

		m_pkt.packWord((name.length() * 2) + 2); // name length in bytes, inc null
		m_pkt.packInt(createFlags); // oplocks/extended response
		m_pkt.packInt(0); // root FID
		m_pkt.packInt(access); // desired access mode
		m_pkt.packLong(initSize); // allocation size
		m_pkt.packInt(attrib); // file attributes
		m_pkt.packInt(sharing); // share access mode
		m_pkt.packInt(exists); // action to take if file exists
		m_pkt.packInt(createOpt); // file create options
		m_pkt.packInt(2); // impersonation level, 0=anonymous, 2=impersonation
		m_pkt.packByte(0); // security flags

		m_pkt.resetBytePointer();
		m_pkt.packString(name, m_pkt.isUnicode());

		m_pkt.setByteCount();

		// Send/receive the NT create andX request

		m_pkt.ExchangeSMB(this, m_pkt, throwErr);

		// Unpack the file/directory details

		m_pkt.resetParameterPointer();
		m_pkt.skipBytes(4);

		int oplockTyp = m_pkt.unpackByte();
		int fid = m_pkt.unpackWord();
		int createAction = m_pkt.unpackInt();

		long createTime = m_pkt.unpackLong();
		long lastAccessTime = m_pkt.unpackLong();
		long lastWriteTime = m_pkt.unpackLong();
		long changeTime = m_pkt.unpackLong();

		int attr = m_pkt.unpackInt();

		long allocSize = m_pkt.unpackLong();
		long eofOffset = m_pkt.unpackLong();

		int devType = m_pkt.unpackWord();

		// Create the file information

		FileInfo finfo = new FileInfo(name, eofOffset, attr);
		finfo.setFileId(fid);

		// Convert the granted oplock type to internal type
		
		if ( oplockTyp == WinNT.GrantedOplockBatch)
			oplockTyp = OpLockType.LEVEL_BATCH.intValue();
		else if ( oplockTyp == WinNT.GrantedOplockExclusive)
			oplockTyp = OpLockType.LEVEL_EXCLUSIVE.intValue();
		else if ( oplockTyp == WinNT.GrantedOplockLevelII)
			oplockTyp = OpLockType.LEVEL_II.intValue();
		else
			oplockTyp = OpLockType.LEVEL_NONE.intValue();
		
		// Create the file object

		return new CIFSFile(this, finfo, fid, oplockTyp);
	}
	
	/**
	 * File closed, remove from the oplocked file list
	 * 
	 * @param cifsFile CIFSFile
	 */
	protected final void fileClosed( CIFSFile cifsFile) {
		
		// Check if there are any oplocked files
		
		if ( m_oplockFiles == null || m_oplockFiles.size() == 0)
			return;
		
		// Remove the file from the oplocked list
		
		m_oplockFiles.remove( new Integer( cifsFile.getFileId()));
	}
}