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
import org.filesys.smb.SMBDate;
import org.filesys.smb.SMBException;
import org.filesys.smb.SMBStatus;

/**
 *  SMB file class.
 *
 *  <p>This is an abstract class that defines the standard SMB file methods.
 * 
 * @author gkspencer
 */
public abstract class SMBFile {

	//	Various file state flags.

	public static final int EndOfFile = 0x0001;
	public static final int Closed 	= 0x0002;

	//	Session that this file is associated with

	protected Session m_sess;

	//	File information

	private FileInfo m_info;

 	//	File identifier, allocated by the remote file server

	protected int m_FID;

	//	SMB packets for data send/receive

	protected SMBPacket m_rxpkt = null;
	protected SMBPacket m_txpkt = null;

	//	Read/write position within the file

	protected long m_rxpos = 0;
	protected long m_txpos = 0;

 	//	Current offset within the receive packet, current receive packet length.

	protected int m_rxoffset = 0;
	protected int m_rxlen = 0;

	//	Current offset within the transmit packet, current transmit data length

	protected int m_txoffset = 0;
	protected int m_txlen;

	//	File state flags

	private int m_flags = 0;

	/**
	 * Construct an SMBFile on the specified SMB session.
	 * 
	 * @param sess SMB session that this file is associated with.
	 * @param finfo File information for this file.
	 * @param fid File identifier, allocated when the file was opened.
	 */
	protected SMBFile(Session sess, FileInfo finfo, int fid) {
		m_sess = sess;
		m_info = finfo;
		m_FID = fid;

		// Initialize the file write position using the current file size

		m_txpos = getFileSize();
	}

	/**
	 * Check if the end of file has been reached.
	 * 
	 * @return true if end of file has been reached, else false.
	 */
	public final boolean atEndOfFile() {
		return (m_flags & EndOfFile) != 0 ? true : false;
	}

	/**
	 * Clear the end of file flag
	 */
	public final void clearEndOfFile() {
		if ( atEndOfFile())
			m_flags -= EndOfFile;
	}

	/**
	 * Return the number of bytes that are available for reading without blocking the input stream.
	 * 
	 * @return Number of bytes available for read without blocking the input stream.
	 */
	public final int Available() {
		return m_rxlen;
	}

	/**
	 * Close the remote file.
	 * 
	 * @param wrDateTime Set the last write date/time, or null to let the server set the date/time
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract void Close(SMBDate wrDateTime)
		throws java.io.IOException, SMBException;

	/**
	 * Close the remote file, let the remote server set the last write date/time
	 * 
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public final void Close()
		throws java.io.IOException, SMBException {

		// Do not specify the last write date/time so the server will set it for us

		Close(null);
	}

	/**
	 * Finalize, object destruction.
	 */
	protected void finalize() {

		// Close the file, if not already closed

		if ( !isClosed()) {
			try {
				Close();
			}
			catch (SMBException ex) {
			}
			catch (java.io.IOException ex) {
			}
		}
	}

	/**
	 * Flush any buffered data for this file.
	 * 
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract void Flush()
		throws java.io.IOException, SMBException;

	/**
	 * Return the file attributes
	 * 
	 * @return int
	 */
	public final int getAttributes() {
		return m_info.getFileAttributes();
	}

	/**
	 * Get the file name string.
	 * 
	 * @return File name string.
	 */
	public final String getFileName() {
		return m_info.getFileName();
	}

	/**
	 * Get the file path string.
	 * 
	 * @return File path string.
	 */
	public final String getFilePath() {
		return m_info.getPath();
	}

	/**
	 * Get the file size, in bytes.
	 * 
	 * @return File size in bytes.
	 */
	public final long getFileSize() {
		return m_info.getSize();
	}

	/**
	 * Return the file id
	 * 
	 * @return int
	 */
	public final int getFileId() {
		return m_FID;
	}

	/**
	 * Get the session that this file is associated with.
	 * 
	 * @return SMBSession that this file is associated with.
	 */
	protected final Session getSession() {
		return m_sess;
	}

	/**
	 * Return the current file read position
	 * 
	 * @return long
	 */
	public final long getReadPosition() {
		return m_rxpos;
	}

	/**
	 * Return the current write position
	 * 
	 * @return long
	 */
	public final long getWritePosition() {
		return m_txpos;
	}

	/**
	 * Check if the file has been closed.
	 * 
	 * @return true if the file has been closed, else false.
	 */
	public final boolean isClosed() {
		return (m_flags & Closed) != 0 ? true : false;
	}

	/**
	 * Determine if this file is a directory
	 * 
	 * @return boolean
	 */
	public final boolean isDirectory() {
		return m_info.isDirectory();
	}

	/**
	 * Determine if this file is hidden
	 * 
	 * @return boolean
	 */
	public final boolean isHidden() {
		return m_info.isHidden();
	}

	/**
	 * Determine if this file is read-only
	 * 
	 * @return boolean
	 */
	public final boolean isReadOnly() {
		return m_info.isReadOnly();
	}

	/**
	 * Determine if this file is a system file
	 * 
	 * @return boolean
	 */
	public final boolean isSystem() {
		return m_info.isSystem();
	}

	/**
	 * Read a block of data from the file.
	 * 
	 * @param buf Byte buffer to receive the data.
	 * @param siz Maximum length of data to receive.
	 * @param offset Offset within buffer to place received data.
	 * @return Actual length of data received.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract int Read(byte[] buf, int siz, int offset)
		throws java.io.IOException, SMBException;

	/**
	 * Read a block of data from the file.
	 * 
	 * @param buf Byte buffer to receive the data.
	 * @return Actual length of data received.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public final int Read(byte[] buf)
		throws java.io.IOException, SMBException {

		// Read a full buffer of data

		return Read(buf, buf.length, 0);
	}

	/**
	 * Write a block of data to the file.
	 * 
	 * @param buf Byte buffer containing data to be written.
	 * @param siz Length of data to be written.
	 * @param offset Offset within buffer to start writing data from.
	 * @return Actual length of data written.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract int Write(byte[] buf, int siz, int offset)
		throws java.io.IOException, SMBException;

	/**
	 * Write a block of data to the file.
	 * 
	 * @param buf Byte buffer containing data to be written.
	 * @return Actual length of data written.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public final int Write(byte[] buf)
		throws java.io.IOException, SMBException {

		// Write the whole buffer

		return Write(buf, buf.length, 0);
	}

	/**
	 * Write a string to the file.
	 * 
	 * @param str String to be written to the file
	 * @return Actual length of data written.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public final int Write(String str)
		throws java.io.IOException, SMBException {

		// Write the whole buffer

		byte[] byts = str.getBytes();
		return Write(byts, byts.length, 0);
	}

	/**
	 * Seek to the specified point in the file. The seek may be relative to the start of file,
	 * current file position or end of file.
	 * 
	 * @param pos Relative offset
	 * @param typ Seek type. @see SeekType
	 * @return New file offset from start of file
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public abstract long Seek(long pos, int typ)
		throws java.io.IOException, SMBException;

	/**
	 * Lock a range of bytes within the file
	 * 
	 * @param offset Offset within the file to start lock
	 * @param len Number of bytes to lock
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public abstract void Lock(long offset, long len)
		throws IOException, SMBException;

	/**
	 * Unlock a range of bytes within the file
	 * 
	 * @param offset Offset within the file to unlock
	 * @param len Number of bytes to unlock
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public abstract void Unlock(long offset, long len)
		throws IOException, SMBException;

	/**
	 * Set a file state flag.
	 * 
	 * @param flag File state flag to set/clear.
	 * @param sts New file flag state, true or false.
	 */
	protected void setStateFlag(int flag, boolean sts) {
		if ( sts == true && (m_flags & flag) == 0)
			m_flags += flag;
		else if ( sts == false && (m_flags & flag) != 0)
			m_flags -= flag;
	}

	/**
	 * Set/update the file information
	 * 
	 * @param fInfo FileInfo
	 */
	protected final void setFileInformation( FileInfo fInfo) {
		m_info = fInfo;
	}
	
	/**
	 * Create an input stream using this file
	 * 
	 * @return SMBInputStream
	 * @exception SMBException If the file is a directory
	 */
	public final SMBInputStream asInputStream()
		throws SMBException {

		// Check if the file is a directory

		if ( isDirectory())
			throw new SMBException(SMBStatus.DOSInvalidFunc, SMBStatus.ErrDos);

		// Create the input stream

		return new SMBInputStream(this);
	}

	/**
	 * Create an output stream using this file
	 * 
	 * @return SMBOutputStream
	 * @exception SMBException If the file is a directory
	 */
	public final SMBOutputStream asOutputStream()
		throws SMBException {

		// Check if the file is a directory

		if ( isDirectory())
			throw new SMBException(SMBStatus.DOSInvalidFunc, SMBStatus.ErrDos);

		// Create the output stream

		return new SMBOutputStream(this);
	}

	/**
	 * Refresh the file information for an open file
	 *
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public void refreshFileInformation()
		throws IOException, SMBException {
		
		// Get the latest file information for the file
		
		if ( m_sess instanceof DiskSession) {
			DiskSession diskSess = (DiskSession) m_sess;
		
			FileInfo fInfo = diskSess.getFileInformation( getFileName());
			if ( fInfo != null)
				setFileInformation( fInfo);
		}
	}
	
	/**
	 * Check if the specified state flag is set
	 * 
	 * @param flg int
	 * @return boolean
	 */
	protected final boolean hasStateFlag(int flg) {
		return (m_flags & flg) != 0 ? true : false;
	}

	/**
	 * Return the SMB file as a string
	 * 
	 * @return SMB file string.
	 */
	public final String toString() {

		// Build the file information string

		StringBuffer str = new StringBuffer();

		// Add the file information string

		str.append(m_info.toString());

		// Append the SMB file id

		str.append(" ,FID=");
		str.append(m_FID);

		// Return the string

		return str.toString();
	}
}