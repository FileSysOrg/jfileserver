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
import org.filesys.smb.DataType;
import org.filesys.smb.PCShare;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.SMBException;
import org.filesys.smb.SMBStatus;
import org.filesys.util.DataPacker;

/**
 * SMB CIFS protocol search context class
 * 
 * @author gkspencer
 */
final class CIFSSearchContext extends SearchContext {

	//	Directory entry offsets

	private static final int DirResumeLength 	= 21;
	private static final int DirAttrLength 		= 1;
	private static final int DirDateLength 		= 2;
	private static final int DirTimeLength 		= 2;
  	private static final int DirSizeLength 		= 4;
  	private static final int DirNameLength 		= 13;
  	private static final int DirNameOffset 		= 30;
  	private static final int DirInfoLen 		= 43;

	//	SMB packet used for the search

  	private SMBPacket m_pkt = null;

	//	Current directory entry index

  	private int m_dirIdx;
  
	/**
	 * Construct an SMB search context on the specified disk session.
	 * 
	 * @param sess Disk session that this search is associated with.
	 */
	protected CIFSSearchContext(DiskSession sess) {
		super(sess);
	}

	/**
	 * Continue the current search, request another packet of directory entries.
	 * 
	 * @return true if more files were returned, else false
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	protected final boolean ContinueSearch()
		throws java.io.IOException, SMBException {

		// Build the continue search packet using the last resume key from
		// the current search packet.

		int pos = m_pkt.getByteOffset() + 2;
		byte[] buf = m_pkt.getBuffer();

		buf[pos++] = (byte) DataType.ASCII;
		buf[pos++] = (byte) 0x00;
		buf[pos++] = (byte) DataType.VariableBlock;

		DataPacker.putIntelShort(DirResumeLength, buf, pos);
		pos += 2;

		int respos = getDirEntryOffset(getDirEntryCount() - 1);
		for (int i = 0; i < DirResumeLength; i++)
			buf[pos++] = buf[respos++];
		buf[pos++] = (byte) 0x00;

		DataPacker.putIntelShort((DirResumeLength + 6), buf, m_pkt.getByteOffset());

		// Reset the parameter words, can't do this until the resume key has been
		// moved into place.

		m_pkt.setParameterCount(2);
		m_pkt.setParameter(0, 50);
		m_pkt.setParameter(1, getSearchAttributes());
		m_pkt.setFlags(getSession().getDefaultFlags());
		m_pkt.setFlags2(getSession().getDefaultFlags2());

		// Send/receive the search SMB packet

		m_pkt.ExchangeSMB(getSession(), m_pkt);

		// Check if we received a valid response

		if ( m_pkt.isValidResponse() == false) {

			// Check if the error is 'no more files'

			if ( m_pkt.getErrorClass() == SMBStatus.ErrDos && m_pkt.getErrorCode() == SMBStatus.DOSNoMoreFiles)
				return false;
			else
				throw new java.io.IOException("Continue search failed");
		}

		// Reset the current directory entry index

		m_dirIdx = 0;
		return true;
	}

	/**
	 * Return the number of directory entries in the SMB search response packet.
	 * 
	 * @return Number of directory entries in the current SMB search packet.
	 */
	protected final int getDirEntryCount() {
		return m_pkt.getParameter(0);
	}

	/**
	 * Return the buffer offset of the specified directory entry.
	 * 
	 * @param idx Directory entry index.
	 * @return Offset within the SMB packet buffer that the directory entry is stored.
	 */
	protected final int getDirEntryOffset(int idx) {

		// Calculate the offset of the directory entry within the SMB packet

		int pos = m_pkt.getByteOffset() + 3; // data type + data length
		pos += (idx * DirInfoLen);
		return pos;
	}

	/**
	 * Return the next file in this search as an SMB file information object.
	 * 
	 * @return SMBFileInfo object, or null if there are no more files.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final FileInfo nextFileInfo()
		throws java.io.IOException, SMBException {

		// Check if the current search packet has been exhausted

		if ( m_dirIdx >= getDirEntryCount())
			if ( !ContinueSearch())
				return null;

		// Get the offset of the required directory entry

		int pos = getDirEntryOffset(m_dirIdx++) + DirResumeLength;
		byte[] buf = m_pkt.getBuffer();

		// Extract the various data fields from the directory entry

		int attr = (int) buf[pos++];

		int wrtime = (int) DataPacker.getIntelShort(buf, pos);
		pos += DirTimeLength;

		int wrdate = (int) DataPacker.getIntelShort(buf, pos);
		pos += DirDateLength;

		int fsize = DataPacker.getIntelInt(buf, pos);
		pos += DirSizeLength;

		int fnamelen = 0;

		while (fnamelen < DirNameLength && buf[pos + fnamelen] != 0x00)
			fnamelen++;
		String fname = new String(buf, pos, fnamelen);

		// Create an SMB file information object

		return new FileInfo(fname, fsize, attr, wrdate, wrtime);
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

		if ( m_dirIdx >= getDirEntryCount())
			if ( !ContinueSearch())
				return null;

		// Get the offset of the required directory entry

		int pos = getDirEntryOffset(m_dirIdx++) + DirNameOffset;
		byte[] buf = m_pkt.getBuffer();

		// Find the end of the file name string

		int fnamelen = 0;

		while (fnamelen < DirNameLength && buf[pos + fnamelen] != 0x00)
			fnamelen++;

		// Return the file name string

		return new String(buf, pos, fnamelen);
	}

	/**
	 * Start a new search using the default directory name and attributes.
	 * 
	 * @param fname File name string, may contain wilcards.
	 * @param attr File attributes bit mask.
	 * @param level Information level to be returned
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public final void StartSearch(String fname, int attr, int level)
		throws java.io.IOException, SMBException {

		// Save the search parameters

		setSearchParameters(fname, attr, level);

		// Check if an SMB packet has been allocated for the directory search

		if ( m_pkt == null) {

			// Allocate an SMB packet for the search, and initialize it

			m_pkt = new SMBPacket();

			m_pkt.setCommand(PacketTypeV1.Search);
			m_pkt.setUserId(getSession().getUserId());
			m_pkt.setTreeId(getSession().getTreeId());
		}

		// Initialize the search SMB packet

		m_pkt.setFlags(getSession().getDefaultFlags());
		m_pkt.setFlags2(getSession().getDefaultFlags2());

		m_pkt.setParameterCount(2);
		m_pkt.setParameter(0, 50); // number of directory entries to return
		m_pkt.setParameter(1, getSearchAttributes());

		// Check if the directory path has a leading '\', if not then the directory
		// is relative to the current working directory

		String searchPath = getSearchPath();
		if ( searchPath.startsWith("\\") == false)
			searchPath = PCShare.makePath(getSession().getWorkingDirectory(), getSearchPath());

		// Pack the search string

		m_pkt.resetBytePointer();
		m_pkt.packByte(DataType.ASCII);
		m_pkt.packString(searchPath, m_pkt.isUnicode());

		// Append a null resume key, to indicate the start of a new search

		m_pkt.packByte(DataType.VariableBlock);
		m_pkt.packWord(0);

		m_pkt.setByteCount();

		// Send/receive the search SMB packet

		m_pkt.ExchangeSMB(getSession(), m_pkt);

		// Check if we received a valid response

		if ( m_pkt.isValidResponse() == false)
			throw new java.io.IOException("Search failed");

		// Reset the current directory entry index

		m_dirIdx = 0;
	}
}