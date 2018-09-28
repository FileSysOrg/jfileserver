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

import java.io.IOException;

import org.filesys.client.info.FileInfo;
import org.filesys.smb.DataType;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.SMBDate;
import org.filesys.smb.SMBException;
import org.filesys.util.DataPacker;

/**
 * SMB core file class
 * 
 * @author gkspencer
 */
public class CoreFile extends SMBFile {

	// Size of protocol packet to allocate

	private static final int DataSize = 4000;
	private static final int PacketSize = DataSize + 64;

	// Offset that the write data is placed within the write SMB packet

	private static final int WriteDataOffset = 52;

	/**
	 * Class constructor
	 * 
	 * @param sess Session that this file is associated with
	 * @param finfo File information for the new file
	 * @param fid File identifier for this file
	 */
	protected CoreFile(Session sess, FileInfo finfo, int fid) {
		super(sess, finfo, fid);
	}

	/**
	 * Close the remote file.
	 * 
	 * @param wrDateTime Set the last write date/time, or null to let the server set the date/time
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public void Close(SMBDate wrDateTime)
		throws java.io.IOException, SMBException {

		// Flush any buffered write data

		if ( m_txlen > 0)
			Flush();

		// Determine which packet to use to send the close file SMB

		SMBPacket pkt = new SMBPacket();
		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Close the remote file.

		pkt.setCommand(PacketTypeV1.CloseFile);
		pkt.setParameterCount(3);
		pkt.setParameter(0, m_FID);

		// Check if the last write date/time should be set

		if ( wrDateTime != null) {

			// Set the last write date/time for the file

			pkt.setParameter(1, wrDateTime.asSMBTime());
			pkt.setParameter(2, wrDateTime.asSMBDate());
		}
		else {

			// Let the server set the last write date/time

			pkt.setParameter(1, 0);
			pkt.setParameter(2, 0);
		}

		// Indicate that the file has been closed

		this.setStateFlag(Closed, true);

		// Exchange the close file SMB packet with the file server

		try {
			pkt.ExchangeSMB(m_sess, pkt);
		}
		catch (java.io.IOException ex) {
			return;
		}

		// Release the transmit/receive packets

		m_rxpkt = null;
		m_txpkt = null;
		return;
	}

	/**
	 * Flush data to the remote file.
	 * 
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public void Flush()
		throws java.io.IOException, SMBException {

		// Check if there is any buffered write data

		if ( m_txlen > 0)
			WriteData();
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
	public int Read(byte[] buf, int siz, int offset)
		throws java.io.IOException, SMBException {

		// Check if the file has been closed

		if ( this.isClosed())
			return 0;

		// Copy data into the users receive buffer

		int retlen = 0;
		int bufidx = offset;

		while (retlen < siz && !this.atEndOfFile()) {

			// Check if there is any data buffered

			if ( m_rxlen == 0) {

				// Read a packet of data from the remote file

				if ( ReadData() == false)
					return -1;
			}

			// Check if enough data is buffered

			int rxlen = siz;
			if ( rxlen > m_rxlen)
				rxlen = m_rxlen;

			// Copy data to the users buffer

			byte[] pktbuf = m_rxpkt.getBuffer();
			for (int idx = 0; idx < rxlen; idx++)
				buf[bufidx + idx] = pktbuf[m_rxoffset + idx];

			// Update the buffered data offset/length

			m_rxlen -= rxlen;
			m_rxoffset += rxlen;
			bufidx += rxlen;

			// Update the returned data length

			retlen += rxlen;

		} // end while

		// Return the actual data received

		return retlen;
	}

	/**
	 * Read a packet of data from the remote file.
	 * 
	 * @return true if a valid data packet has been received, else false
	 */
	private final boolean ReadData() {

		// Allocate and initialize a receive packet, if not already allocated

		if ( m_rxpkt == null) {

			// Allocate a receive packet

			m_rxpkt = new SMBPacket();

			// Initialize the packet

			m_rxpkt.setUserId(m_sess.getUserId());
			m_rxpkt.setTreeId(m_sess.getTreeId());
		}

		// Read a packet of data from the remote file.
		// Initialize the read packet.

		m_rxpkt.setCommand(PacketTypeV1.ReadFile);
		m_rxpkt.setParameterCount(5);
		m_rxpkt.setParameter(0, m_FID);

		if ( (m_rxpos + DataSize) > getFileSize())
			m_rxpkt.setParameter(1, (int) (getFileSize() - m_rxpos));
		else
			m_rxpkt.setParameter(1, DataSize);

		m_rxpkt.setParameter(2, (int) m_rxpos & 0xFFFF);
		m_rxpkt.setParameter(3, (int) (m_rxpos & 0xFFFF0000) >> 16);
		m_rxpkt.setParameter(4, DataSize);

		// Exchange the read data SMB packet with the file server

		try {

			m_rxpkt.ExchangeSMB(m_sess, m_rxpkt);
		}
		catch (SMBException ex) {
			return false;
		}
		catch (java.io.IOException ex) {
			return false;
		}

		// Check if a valid response was received

		if ( m_rxpkt.isValidResponse()) {

			// Set the received data length and offset within the received data

			int rxlen = m_rxpkt.getParameter(0);
			m_rxoffset = m_rxpkt.getByteOffset();
			byte[] buf = m_rxpkt.getBuffer();

			if ( buf[m_rxoffset++] != DataType.DataBlock)
				return false;

			// Get the received data block length

			m_rxlen = DataPacker.getIntelShort(buf, m_rxoffset);
			m_rxoffset += 2;

			// Update the current receive file position

			m_rxpos += m_rxlen;

			// Check if we have reached the end of file, indicated by a zero length
			// read.

			if ( m_rxlen == 0)
				setStateFlag(EndOfFile, true);
			return true;
		}

		// Return a failure status

		return false;
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
	public int Write(byte[] buf, int siz, int offset)
		throws java.io.IOException, SMBException {

		// Check if the file has been closed

		if ( this.isClosed())
			return 0;

		// Allocate and initialize a transmit packet, if not already allocated

		if ( m_txpkt == null) {

			// Allocate a transmit packet

			m_txpkt = new SMBPacket();

			// Initialize the packet

			m_txpkt.setUserId(m_sess.getUserId());
			m_txpkt.setTreeId(m_sess.getTreeId());

			// Set the write SMB parameter count now, so that we can calculate the
			// offset of the byte buffer within the packet.

			m_txpkt.setParameterCount(5);

			// Clear the write packet length and initialize the write packet offset.

			m_txlen = 0;
			m_txoffset = WriteDataOffset;
		}

		// Move the data to the write packet and send write requests until the
		// user write has been done.

		int txlen = 0;
		byte[] pktbuf = m_txpkt.getBuffer();

		while (txlen < siz) {

			// Determine if the current write request can be buffered in full

			int len = pktbuf.length - m_txoffset;
			if ( len > (siz - txlen))
				len = (siz - txlen);

			// Move the user data to the write packet

			for (int idx = 0; idx < len; idx++)
				pktbuf[m_txoffset++] = buf[offset++];

			// Update the written data length

			txlen += len;
			m_txlen += len;

			// Check if the write packet is full, if so then send the write packet

			if ( m_txoffset >= pktbuf.length)
				WriteData();

		} // end while writing

		// Return the length of the data that was written

		return txlen;
	}

	/**
	 * Write a packet of data to the remote file.
	 * 
	 * @return true if the write was successful, else false
	 */
	private final boolean WriteData() {

		// Write a packet of data to the remote file.
		// Initialize the write packet.

		m_txpkt.setCommand(PacketTypeV1.WriteFile);
		m_txpkt.setParameterCount(5);
		m_txpkt.setParameter(0, m_FID);

		m_txpkt.setParameter(1, m_txlen);

		m_txpkt.setParameter(2, (int) m_txpos & 0xFFFF);
		m_txpkt.setParameter(3, (int) (m_txpos & 0xFFFF0000) >> 16);
		m_txpkt.setParameter(4, m_txlen);

		// Set the byte count

		m_txpkt.setByteCount(m_txlen + 3);

		// Initialize the write data block

		byte[] buf = m_txpkt.getBuffer();
		int bytoff = m_txpkt.getByteOffset();

		buf[bytoff++] = (byte) DataType.DataBlock;
		DataPacker.putIntelShort(m_txlen, buf, bytoff);

		// Exchange the write data SMB packet with the file server

		try {

			m_txpkt.ExchangeSMB(m_sess, m_txpkt);
		}
		catch (SMBException ex) {
			return false;
		}
		catch (java.io.IOException ex) {
			return false;
		}

		// Check if a valid response was received

		if ( m_txpkt.isValidResponse()) {

			// Set the write data length

			int txlen = m_txpkt.getParameter(0);

			// Update the current write file position

			m_txpos += txlen;

			// Reset the write packet and write length

			m_txlen = 0;
			m_txoffset = WriteDataOffset;
			return true;
		}

		// Return a failure status

		return false;
	}

	/**
	 * Seek to the specified point in the file. The seek may be relative to the start of file,
	 * current file position or end of file.
	 * 
	 * @param pos Relative offset
	 * @param typ Seek type (@see SeekType)
	 * @return New file offset from start of file
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public long Seek(long pos, int typ)
		throws IOException, SMBException {

		// Check if the file has been closed

		if ( this.isClosed())
			throw new IOException("Seek on closed file");

		// Flush any buffered data

		Flush();

		// Initialize the file seek packet.

		m_txpkt.setCommand(PacketTypeV1.SeekFile);
		m_txpkt.setParameterCount(4);
		m_txpkt.setParameter(0, m_FID);

		m_txpkt.setParameter(1, typ);
		m_txpkt.setParameterLong(2, (int) pos & 0xFFFFFFFF);

		m_txpkt.setByteCount(0);

		// Exchange the seek file SMB packet with the file server

		m_txpkt.ExchangeSMB(m_sess, m_txpkt);

		// Check if a valid response was received

		if ( m_txpkt.isValidResponse()) {

			// Get the new file position, relative to the start of the file

			int filePos = m_txpkt.getParameter(0) + (m_txpkt.getParameter(1) << 16);

			// Reset the file read/write offsets

			m_txpos = filePos;
			m_rxpos = filePos;

			// Indicate that there is no buffered data

			m_txlen = 0;
			m_rxlen = 0;

			// Return the new file offset

			return filePos;
		}

		// Return a failure status

		return -1;
	}

	/**
	 * Lock a range of bytes within the file
	 * 
	 * @param offset Offset within the file to start lock
	 * @param len Number of bytes to lock
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public void Lock(long offset, long len)
		throws IOException, SMBException {
	}

	/**
	 * Unlock a range of bytes within the file
	 * 
	 * @param offset Offset within the file to unlock
	 * @param len Number of bytes to unlock
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public void Unlock(long offset, long len)
		throws IOException, SMBException {
	}
}