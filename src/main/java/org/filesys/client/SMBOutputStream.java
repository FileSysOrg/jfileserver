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

import org.filesys.smb.SMBException;

/**
 * SMB output stream class.
 * 
 * <p>
 * The SMBOutputStream class provides a standard OutputStream interface to an existing remote file,
 * or can be used to create a new remote file.
 * 
 * <p>
 * The class may be used with other I/O stream classes such as PrintWriter, DataOutputStream etc.
 * 
 * <p>
 * <strong>Note:</strong> It is not necessary to use a BufferedOutputStream or BufferedWriter class
 * with the SMBOutputStream as the underlying network connection will usually buffer 4Kb of data, up
 * to a maximum of 64Kb.
 * 
 * @author gkspencer
 */
public class SMBOutputStream extends java.io.OutputStream {

	// SMB file that this stream is associated with.

	private SMBFile m_file;

	/**
	 * Construct an SMB output stream attached to the specified SMB file
	 * 
	 * @param sfile SMBFile that this output stream is attached to.
	 */
	protected SMBOutputStream(SMBFile sfile) {
		m_file = sfile;
	}

	/**
	 * Close this output stream and release any system resources associated with the stream.
	 * 
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public void close()
		throws java.io.IOException {

		// Close the remote file

		try {
			m_file.Close();
		}
		catch (SMBException ex) {
			throw new IOException(ex.getErrorText());
		}
	}

	/**
	 * Return a reference to the associated SMBFile object.
	 * 
	 * @return SMBFile associated with this output stream.
	 */
	public final SMBFile File() {
		return m_file;
	}

	/**
	 * Flush this output stream, force any buffered data to be written out.
	 * 
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public void flush()
		throws java.io.IOException {

		// Flush buffered data on this output stream

		try {
			m_file.Flush();
		}
		catch (SMBException ex) {
			throw new IOException(ex.getErrorText());
		}
	}

	/**
	 * Write the specified byte array to the output stream, starting at the specified offset within
	 * the byte array.
	 * 
	 * @param buf Byte array containing the data to be output.
	 * @param off Offset within the buffer that the data starts.
	 * @param len Length of the data to be output.
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public void write(byte buf[], int off, int len)
		throws java.io.IOException {

		// Write the byte array to the output stream

		try {
			m_file.Write(buf, len, off);
		}
		catch (SMBException ex) {
			throw new IOException(ex.getErrorText());
		}
	}

	/**
	 * Write the specified byte to this output stream.
	 * 
	 * @param byt Byte to be output to this stream.
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public void write(int byt)
		throws java.io.IOException {

		// Create a byte array to hold the single byte

		byte[] buf = new byte[2];
		buf[0] = (byte) byt;

		// Write the byte to the output stream

		try {
			m_file.Write(buf, 1, 0);
		}
		catch (SMBException ex) {
			throw new IOException(ex.getErrorText());
		}
	}
}