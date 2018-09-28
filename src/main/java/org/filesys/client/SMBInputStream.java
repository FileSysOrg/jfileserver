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
import org.filesys.smb.SeekType;

/**
 * SMB input stream class.
 * 
 * <p>
 * The SMBInputStream class provides a standard InputStream interface to a remote file.
 * 
 * <p>
 * The class may be used with other I/O stream classes such as InputStreamReader, DataInputStream
 * etc.
 * 
 * <p>
 * <strong>Note:</strong> It is not necessary to use a BufferedInputStream or BufferedReader class
 * with the SMBInputStream as the underlying network connection will usually buffer 4Kb of data, up
 * to a maximum of 64Kb.
 * 
 * 
 * <p>
 * Example use of the SMBInputStream class
 * 
 * <p>
 * <code>PCShare shr = new PCShare ( "\\\\TEST\\C\\");<br>
 *      DiskSession sess = SessionFactory.OpenDisk ( shr);<br>
 *      SMBInputStream in = sess.OpenInputStream ( "DATAFILE.IN", AccessMode.ReadOnly);<br>
 *      LineNumberReader lnRdr = new LineNumberReader ( new InputStreamReader ( in));<br>
 *      String inRec = null;<br>
 *      while (( inRec = lnRdr.readLine ()) != null)<br>
 *      &nbsp;&nbsp;System.out.println ( lnRdr.getLineNumber () + ": " + inRec);<br>
 *      in.close ();</code>
 * 
 * @author gkspencer
 */
public class SMBInputStream extends java.io.InputStream {

	// SMB file that this stream is associated with.

	private SMBFile m_file;

	// Marked position and read limit

	private long m_markPos;
	private int m_readLimit;

	/**
	 * Construct an SMB input stream attached to the specified SMB file.
	 * 
	 * @param sfile SMBFile that this input stream is associated with.
	 */
	protected SMBInputStream(SMBFile sfile) {
		m_file = sfile;
	}

	/**
	 * Return the number of bytes that can be read from this input stream without blocking.
	 * 
	 * @return Number of bytes that can be read without the input stream blocking.
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public int available()
		throws java.io.IOException {
		return m_file.Available();
	}

	/**
	 * Close the input stream and release any system resources associated with the stream.
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
	 * @return SMBFile associated with this input stream.
	 */
	public final SMBFile File() {
		return m_file;
	}

	/**
	 * Read a byte of data from the input stream.
	 * 
	 * @return The next byte of data, or -1 if the end of file has been reached.
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public int read()
		throws java.io.IOException {

		// Read a byte from the SMB file

		try {
			byte[] buf = new byte[2];
			if ( m_file.Read(buf, 1, 0) == 1)
				return (int) buf[0];
			return -1;
		}
		catch (SMBException ex) {
			throw new IOException(ex.getErrorText());
		}
	}

	/**
	 * Read a block of bytes from the input stream.
	 * 
	 * @param buf The buffer to read the data into.
	 * @param off The start offset to place the received data.
	 * @param len The maximum number of bytes to read.
	 * @return The number of bytes read into the buffer, or -1 if the end of file has been reached.
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public int read(byte[] buf, int off, int len)
		throws java.io.IOException {

		// Read a block of bytes into the user buffer

		try {
			int rdlen = m_file.Read(buf, len, off);
			if ( rdlen > 0)
				return rdlen;
			return -1;
		}
		catch (SMBException ex) {
			throw new IOException(ex.getErrorText());
		}
	}

	/**
	 * Skip over a number of bytes in the input stream.
	 * 
	 * @param n Number of bytes to skip.
	 * @return The actual number of bytes skipped.
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public int skip(int n)
		throws java.io.IOException {

		// Seek to the new file read position

		long curPos = m_file.getReadPosition();
		long newPos = curPos;

		try {
			newPos = m_file.Seek((long) n, SeekType.CurrentPos);
		}
		catch (SMBException ex) {
			throw new IOException("skip error, " + ex.toString());
		}

		// Return the number of bytes skipped

		return (int) (newPos - curPos);
	}

	/**
	 * Mark the current file position
	 * 
	 * @param readLimit int
	 */
	public synchronized void mark(int readLimit) {

		// Save the current read position and the read limit

		m_markPos = m_file.getReadPosition();
		m_readLimit = readLimit;
	}

	/**
	 * Determine if mark is supported
	 * 
	 * @return boolean
	 */
	public boolean markSupported() {
		return true;
	}

	/**
	 * Reset the file pointer to the previous marked position
	 *
	 * @exception IOException Socket error
	 */
	public synchronized void reset()
		throws IOException {

		// Check if the current read position is past the read limit

		if ( m_file.getReadPosition() > (m_markPos + m_readLimit))
			throw new IOException("Position past read limit");

		// Reset the read position to the marked position

		try {
			m_file.Seek(m_markPos, SeekType.StartOfFile);
		}
		catch (SMBException ex) {
			throw new IOException(ex.getErrorText());
		}
	}
}