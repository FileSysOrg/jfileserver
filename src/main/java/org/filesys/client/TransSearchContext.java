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

import org.filesys.client.info.FileInfo;
import org.filesys.client.info.ReparsePointFileInfo;
import org.filesys.server.filesys.FileAttribute;
import org.filesys.smb.*;
import org.filesys.smb.PacketTypeV1;
import org.filesys.util.DataBuffer;

/**
 * SMB transact protocol search context class
 * 
 * @author gkspencer
 */
class TransSearchContext extends SearchContext {

	//	Size of the SMB transaction packet to allocate

	protected static final int PacketSize = 16500;

	//	Number of directory entries to return per packet

	protected static final int EntriesPerPacket = 1200;

	//	Find first/find next flags

	protected static final int FlgCloseSearch 		= 0x01;
	protected static final int FlgCloseAtEnd 		= 0x02;
	protected static final int FlgReturnResumeKey 	= 0x04;
	protected static final int FlgResumePrevious 	= 0x08;
	protected static final int FlgBackupIntent 		= 0x10;

	//	Resume key length

	protected static final int ResumeKeyLen = 4;

	//	Data offsets for standard information data blocks.

	protected static final int StdCreateDate 	= 4;
	protected static final int StdCreateTime 	= 6;
	protected static final int StdAccessDate 	= 8;
	protected static final int StdAccessTime 	= 10;
	protected static final int StdWriteDate 	= 12;
	protected static final int StdWriteTime 	= 14;
	protected static final int StdFileSize 		= 16;
	protected static final int StdAllocSize 	= 20;
	protected static final int StdAttributes 	= 24;
	protected static final int StdNameLength 	= 26;
	protected static final int StdFileName 		= 27;

	//	SMB packet and buffers used for the search

	private TransPacket m_pkt;
	private TransactBuffer m_tbuf;
	private TransactBuffer m_rxtbuf;  

	//	Received data block containing the file information
	
	private DataBuffer m_dbuf;
	
	//	Search id

	private int m_searchId;
  
	//	Resume name and id
  
	private String m_resumeName;

	//	Current/maximum directory entry index

	private int m_dirIdx;
	private int m_maxIdx;

	//	Flag to indicate that the current data packet is the end of the search

	private boolean m_eof;
  
	/**
	 * Construct an SMB search context on the specified disk session.
	 * 
	 * @param sess Disk session that this search is associated with.
	 */
	protected TransSearchContext(DiskSession sess) {
		super(sess);
	}

	/**
	 * Continue the current search, request another packet of directory entries.
	 * 
	 * @return true if more files were returned, else false
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	protected boolean ContinueSearch()
		throws java.io.IOException, SMBException {

		// Check if the end of the search has been flagged

		if ( m_eof == true)
			return false;

		// Build the find next parameter block

		m_tbuf.setFunction(PacketTypeV1.Trans2FindNext);
		DataBuffer paramBuf = m_tbuf.getParameterBuffer();
		paramBuf.setPosition(0);

		paramBuf.putShort(m_searchId);
		paramBuf.putShort(EntriesPerPacket);
		paramBuf.putShort(getInformationLevel());
		paramBuf.putInt(0);
		paramBuf.putShort(FlgResumePrevious + FlgReturnResumeKey);

		if ( m_resumeName != null)
			paramBuf.putString(m_resumeName, getSession().isUnicode());

		// Perform the find next transaction

		m_rxtbuf = m_pkt.doTransaction(getSession(), m_tbuf);

		// Process the received parameter block

		if ( m_rxtbuf != null && m_rxtbuf.hasParameterBuffer()) {

			// Get the response parameter block

			paramBuf = m_rxtbuf.getParameterBuffer();

			// Reset the current directory entry index and set the maximum index for this
			// data packet

			m_dirIdx = 0;
			m_maxIdx = paramBuf.getShortAt(0);

			// Set the file information data buffer

			m_dbuf = m_rxtbuf.getDataBuffer();

			// Save the resume file name offset

			// m_resumeName = unpackResumeName(paramBuf.getShortAt(3));

			// Set the last search packet flag

			if ( paramBuf.getShortAt(1) != 0)
				m_eof = true;
			else
				m_eof = false;

			// Check if any data was returned

			if ( m_dbuf == null)
				return false;

			// Return a success status

			return true;
		}

		// Return an error status

		return false;
	}

	/**
	 * Return the next file in this search as an SMB file information object.
	 * 
	 * @return FileInfo object, or null if there are no more files.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final FileInfo nextFileInfo()
		throws java.io.IOException, SMBException {

		// Check if the current search packet has been exhausted

		if ( m_dirIdx == m_maxIdx)
			if ( !ContinueSearch())
				return null;

		// Unpack the file information

		FileInfo finfo = null;

		switch (getInformationLevel()) {

			// Standard file information

			case FileInfoLevel.FindStandard:
				finfo = unpackStandardInfo(false);
				break;

			// Standard file information with extended attribute size

			case FileInfoLevel.FindQueryEASize:
				finfo = unpackStandardInfo(true);
				break;

			// File/directory information

			case FileInfoLevel.FindFileDirectory:
				finfo = unpackFileDirectoryInfo();
				break;

			// File full directory information

			case FileInfoLevel.FindFileFullDirectory:
				finfo = unpackFileDirectoryInfo();
				break;

			// File/directory both information

			case FileInfoLevel.FindFileBothDirectory:
				finfo = unpackFileDirectoryInfo();
				break;

			// File names information

			case FileInfoLevel.FindFileNames:
				finfo = unpackFileNameInfo();
				break;
		}

		// Update the resume name

		if ( finfo != null)
			m_resumeName = finfo.getFileName();

		// Return the file information

		return finfo;
	}

	/**
	 * Return the next file name in this search.
	 * 
	 * @return Next file name string, or null if there are no more files.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final String nextFileName()
		throws java.io.IOException, SMBException {

		// Check if the current search packet has been exhausted

		if ( m_dirIdx == m_maxIdx)
			if ( !ContinueSearch())
				return null;

		// Unpack the file name

		String fname = null;
		FileInfo finfo = null;

		switch (getInformationLevel()) {

			// Standard file information

			case FileInfoLevel.FindStandard:
				fname = unpackStandardName();
				break;

			// File/directory information

			case FileInfoLevel.FindFileDirectory:
				finfo = unpackFileDirectoryInfo();
				if ( finfo != null)
					fname = finfo.getFileName();
				break;

			// File full directory information

			case FileInfoLevel.FindFileFullDirectory:
				finfo = unpackFileDirectoryInfo();
				if ( finfo != null)
					fname = finfo.getFileName();
				break;

			// File/directory both information

			case FileInfoLevel.FindFileBothDirectory:
				finfo = unpackFileDirectoryInfo();
				if ( finfo != null)
					fname = finfo.getFileName();
				break;

			// File names information

			case FileInfoLevel.FindFileNames:
				finfo = unpackFileNameInfo();
				if ( finfo != null)
					fname = finfo.getFileName();
				break;
		}

		// Update the resume name

		if ( fname != null)
			m_resumeName = fname;

		// Return the file name

		return fname;
	}

	/**
	 * Start a new search using the specified file name string and search attributes, return the
	 * specified file information level
	 * 
	 * @param fname File name string, may contain wilcards.
	 * @param attr File attributes bit mask. @see org.filesys.smb.client.FileAttribute
	 * @param level File information level to return. @see FileInfoLevel
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final void StartSearch(String fname, int attr, int level)
		throws java.io.IOException, SMBException {

		// Save the search parameters

		setSearchParameters(fname, attr, level);

		// Check if an SMB packet has been allocated for the directory search

		if ( m_pkt == null) {

			// Allocate an SMB transact packet for the search

			m_pkt = new TransPacket(PacketSize);
		}

		// Check if the transaction buffer has been allocated

		if ( m_tbuf == null) {

			// Allocate the transaction buffer

			m_tbuf = new TransactBuffer(PacketTypeV1.Trans2FindFirst, null, 0, 512, 65000);
		}
		else {

			// Reset the existing transaction buffer for a new search

			m_tbuf.setFunction(PacketTypeV1.Trans2FindFirst);
			m_tbuf.getParameterBuffer().setPosition(0);
		}

		// Check if the directory path has a leading '\', if not then the directory
		// is relative to the current working directory

		String searchPath = getSearchPath();
		if ( searchPath.startsWith("\\") == false)
			searchPath = PCShare.makePath(getSession().getWorkingDirectory(), getSearchPath());

		// Initialize the find first transaction parameter block

		DataBuffer paramBuf = m_tbuf.getParameterBuffer();

		paramBuf.putShort(getSearchAttributes());
		paramBuf.putShort(EntriesPerPacket);
		paramBuf.putShort(FlgReturnResumeKey + FlgCloseAtEnd);
		paramBuf.putShort(getInformationLevel());
		paramBuf.putInt(0);

		paramBuf.putString(fname, getSession().isUnicode());

		// Perform the find first transaction

		try {
			m_rxtbuf = m_pkt.doTransaction(getSession(), m_tbuf);
		}
		catch (IOException ex) {

			// Indicate no files available

			m_eof = true;
			m_dirIdx = m_maxIdx = 0;

			// Rethrow the exception, let the user handle it

			throw ex;
		}

		// Process the received parameter block

		if ( m_rxtbuf != null && m_rxtbuf.hasParameterBuffer()) {

			// Get the response parameter block

			paramBuf = m_rxtbuf.getParameterBuffer();

			// Save the search id

			m_searchId = paramBuf.getShortAt(0);

			// Reset the current directory entry index and set the maximum index for this
			// data packet

			m_dirIdx = 0;
			m_maxIdx = paramBuf.getShortAt(1);

			// Set the file information data buffer

			m_dbuf = m_rxtbuf.getDataBuffer();

			// Save the resume file name offset

			// m_resumeName = unpackResumeName(paramBuf.getShortAt(4));

			// Set the last search packet flag

			if ( paramBuf.getShortAt(2) != 0)
				m_eof = true;
			else
				m_eof = false;
		}
	}

	/**
	 * Unpack the standard file information
	 * 
	 * @param eaSize boolean
	 * @return FileInfo
	 */
	private final FileInfo unpackStandardInfo(boolean eaSize) {

		// Unpack the resume key

		int resKey = m_dbuf.getInt();

		// Unpack the current file entry details

		int crdate = m_dbuf.getShort();
		int crtime = m_dbuf.getShort();

		int acdate = m_dbuf.getShort();
		int actime = m_dbuf.getShort();

		int wrdate = m_dbuf.getShort();
		int wrtime = m_dbuf.getShort();

		int fsize = m_dbuf.getInt();
		int alloc = m_dbuf.getInt();

		int attr = m_dbuf.getShort();

		if ( eaSize)
			m_dbuf.getInt();

		int bytlen = m_dbuf.getByte();
		int fnamelen = bytlen;

		if ( m_rxtbuf.isUnicode()) {
			fnamelen = bytlen / 2;
			bytlen += 2;
			m_dbuf.wordAlign();
		}
		else
			bytlen++;

		// Unpack the file name string

		String fname = m_dbuf.getString(fnamelen, m_rxtbuf.isUnicode());

		// Update the entry index

		m_dirIdx++;

		// Create an SMB file information object

		return new FileInfo(fname, fsize, attr, wrdate, wrtime);
	}

	/**
	 * Unpack the standard file information file name
	 * 
	 * @return String
	 */
	private final String unpackStandardName() {

		// Get the offset of the file name string within the data block

		int nextOff = m_dbuf.getInt();

		m_dbuf.skipBytes(StdNameLength - 4);
		int bytlen = m_dbuf.getByte();
		int fnamelen = bytlen;

		if ( m_rxtbuf.isUnicode()) {
			m_dbuf.wordAlign();
			fnamelen = bytlen / 2;
			bytlen += 2;
		}
		else
			bytlen++;

		// Update the position and index of the next file information block

		m_dirIdx++;

		if ( m_dirIdx < m_maxIdx)
			m_dbuf.setPosition(nextOff);

		// Return the file name string

		return m_dbuf.getString(fnamelen, m_rxtbuf.isUnicode());
	}

	/**
	 * Unpack the file/directory information level
	 * 
	 * @return FileInfo
	 */
	private final FileInfo unpackFileDirectoryInfo() {

		// Get the offset to the next file information structure

		int startPos = m_dbuf.getPosition();
		int nextOff = m_dbuf.getInt();

		// Unpack the file index

		int fid = m_dbuf.getInt();

		// Unpack the file times (in NT 64 bit format)

		long createTime = m_dbuf.getLong();
		long accessTime = m_dbuf.getLong();
		long writeTime = m_dbuf.getLong();
		long changeTime = m_dbuf.getLong();

		// Unpack the file size and allocation size

		long fileSize = m_dbuf.getLong();
		long allocSize = m_dbuf.getLong();

		// Unpack the file attributes

		int attrib = m_dbuf.getInt();

		// Unpack the file name length

		int nameLen = m_dbuf.getInt();

		// Unpack the extended attributes length, if available in the requested information level

		int eaSize = 0;

		if ( getInformationLevel() == FileInfoLevel.FindFileFullDirectory
				|| getInformationLevel() == FileInfoLevel.FindFileBothDirectory)
			eaSize = m_dbuf.getInt();

		// Unpack the short file name, if available in the requested information level

		String shortName = null;

		if ( getInformationLevel() == FileInfoLevel.FindFileBothDirectory) {

			// Get the short name length

			int shortLen = m_dbuf.getByte();
			m_dbuf.skipBytes(1);
			int shortPos = m_dbuf.getPosition();

			if ( shortLen > 0) {

				// Get the short file name

				shortName = m_dbuf.getString(shortLen / 2, true);
			}

			// Update the buffer position

			m_dbuf.setPosition(shortPos + 24);
		}

		// Unpack the long file name

		if ( m_rxtbuf.isUnicode())
			nameLen = nameLen / 2;

		String fileName = m_dbuf.getString(nameLen, m_rxtbuf.isUnicode());

		// Create the file information

		FileInfo finfo = null;

		if ( FileAttribute.hasAttribute(attrib, FileAttribute.NTReparsePoint))
			finfo = new ReparsePointFileInfo(fileName, fileSize, attrib, eaSize);
		else
			finfo = new FileInfo(fileName, fileSize, attrib);
		finfo.setAllocationSize(allocSize);
		finfo.setFileId(fid);
		finfo.setShortName(shortName);

		if ( createTime != 0)
			finfo.setCreationDateTime(NTTime.toSMBDate(createTime));

		if ( accessTime != 0)
			finfo.setAccessDateTime(NTTime.toSMBDate(accessTime));

		if ( writeTime != 0)
			finfo.setModifyDateTime(NTTime.toSMBDate(writeTime));

		// Reset the byte pointer for the next file information structure, update the entry index

		if ( nextOff != 0)
			m_dbuf.setPosition(startPos + nextOff);
		m_dirIdx++;

		// Return the file information

		return finfo;
	}

	/**
	 * Unpack the file name information level
	 * 
	 * @return FileInfo
	 */
	private final FileInfo unpackFileNameInfo() {

		// Get the offset to the next file information structure

		int nextOff = m_dbuf.getInt();

		// Unpack the file index

		int fid = m_dbuf.getInt();

		// Unpack the file name

		int nameLen = m_dbuf.getInt();
		if ( m_rxtbuf.isUnicode())
			nameLen = nameLen / 2;

		String fileName = m_dbuf.getString(nameLen, m_rxtbuf.isUnicode());

		// Create the file information

		FileInfo finfo = new FileInfo(fileName, 0, 0);
		finfo.setFileId(fid);

		// Reset the byte pointer for the next file information structure, update the entry index

		if ( nextOff != 0)
			m_dbuf.setPosition(nextOff);
		m_dirIdx++;

		// Return the file information

		return finfo;
	}

	/**
	 * Unpack the resume file name
	 * 
	 * @param off int
	 * @return String
	 */
	protected final String unpackResumeName(int off) {

		// Check if the offset is valid

		if ( off == 0)
			return null;

		// Save the current buffer position and position at the resume offset

		int pos = m_dbuf.getPosition();
		m_dbuf.setPosition(off);

		// Get the resume file name for the specified information level

		String fname = null;
		int fnamelen = -1;

		switch (getInformationLevel()) {

			// Standard information level

			case FileInfoLevel.FindStandard:
			case FileInfoLevel.FindQueryEASize:

				// Read the resume key

				m_dbuf.skipBytes(-4);
				m_dbuf.getInt(); // resume id
				m_dbuf.skipBytes(22);

				// Get the file name length

				int bytlen = m_dbuf.getByte();
				fnamelen = bytlen;

				if ( m_rxtbuf.isUnicode()) {
					fnamelen = bytlen / 2;
					bytlen += 2;
					m_dbuf.wordAlign();
				}
				else
					bytlen++;

				// Unpack the file name string

				fname = m_dbuf.getString(fnamelen, m_rxtbuf.isUnicode());
				break;

			// File/directory information

			case FileInfoLevel.FindFileDirectory:

				// Skip the start of the structure and get the file name length

				m_dbuf.skipBytes(60);
				fnamelen = m_dbuf.getInt();

				// Unpack the long file name

				fname = m_dbuf.getString(fnamelen / 2, m_rxtbuf.isUnicode());
				break;

			// File full information

			case FileInfoLevel.FindFileFullDirectory:

				// Skip the start of the structure and get the file name length

				m_dbuf.skipBytes(60);
				fnamelen = m_dbuf.getInt();
				m_dbuf.skipBytes(4); // EA size

				// Unpack the long file name

				fname = m_dbuf.getString(fnamelen / 2, m_rxtbuf.isUnicode());
				break;

			// File

			case FileInfoLevel.FindFileBothDirectory:

				// Skip the start of the structure and get the file name length

				m_dbuf.skipBytes(60);
				fnamelen = m_dbuf.getInt();
				m_dbuf.skipBytes(30); // EA size and short name

				// Unpack the long file name

				fname = m_dbuf.getString(fnamelen / 2, m_rxtbuf.isUnicode());
				break;
		}

		// Reset the buffer position

		m_dbuf.setPosition(pos);

		// Return the resume file name

		return fname;
	}
}