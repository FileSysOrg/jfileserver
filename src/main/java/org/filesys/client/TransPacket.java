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

import org.filesys.debug.Debug;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.SMBException;
import org.filesys.smb.TransactBuffer;
import org.filesys.util.DataBuffer;
import org.filesys.util.DataPacker;

/**
 * SMB transact packet class
 * 
 * @author gkspencer
 */
public class TransPacket extends SMBPacket {

	// Define the number of standard parameters sent/received

	protected static final int StandardParams = 14;
	protected static final int RxStandardParams = 10;

	// Offset to the setup paramaters, not including the sub-function

	protected static final int SetupOffset = PARAMWORDS + (StandardParams * 2) + 2;

	// Transact name, not used for transact 2

	protected String m_transName;

	// Parameter count for this transaction

	protected int m_paramCnt;

	/**
	 * Class constructor
	 * 
	 * @param buf Buffer that contains the SMB transaction packet.
	 */
	public TransPacket(byte[] buf) {
		super(buf);
	}

	/**
	 * Class constructor
	 * 
	 * @param siz Size of packet to allocate.
	 */
	public TransPacket(int siz) {
		super(siz);
	}

	/**
	 * Return the data block size
	 * 
	 * @return Data block size in bytes
	 */
	public final int getDataLength() {
		return getParameter(6);
	}

	/**
	 * Return the data block offset
	 * 
	 * @return Data block offset within the SMB packet.
	 */
	public final int getDataOffset() {
		return getParameter(7) + RFCNetBIOSProtocol.HEADER_LEN;
	}

	/**
	 * Unpack the parameter block into the supplied array.
	 * 
	 * @param prmblk Array to unpack the parameter block words into.
	 */
	public final void getParameterBlock(short[] prmblk)
		throws java.lang.ArrayIndexOutOfBoundsException {

		// Determine how many parameters are to be unpacked, check if the user
		// buffer is long enough

		int prmcnt = getParameter(3) / 2; // convert to number of words
		if ( prmblk.length < prmcnt)
			throw new java.lang.ArrayIndexOutOfBoundsException();

		// Get the offset to the parameter words, add the NetBIOS header length
		// to the offset.

		int pos = getParameter(4) + RFCNetBIOSProtocol.HEADER_LEN;

		// Unpack the parameter words

		byte[] buf = getBuffer();

		for (int idx = 0; idx < prmcnt; idx++) {

			// Unpack the current parameter word

			prmblk[idx] = (short) DataPacker.getIntelShort(buf, pos);
			pos += 2;
		}

		// Debug mode

		if ( Debug.EnableInfo && Session.hasDebugOption(Session.DBGDumpPacket)) {
			Debug.println("Transaction parameter dump - " + prmcnt + " params :-");
			for (int i = 0; i < prmcnt; i++)
				Debug.println(" " + i + ". = " + prmblk[i] + ", 0x" + Integer.toHexString(prmblk[i]));
		}
	}

	/**
	 * Perform a transaction request and receive the response data
	 * 
	 * @param sess Session
	 * @param tbuf TransactBuffer
	 * @return TransactBuffer
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final TransactBuffer doTransaction(Session sess, TransactBuffer tbuf)
		throws IOException, SMBException {

		// Initialize the transaction request packet

		int mid = sess.getNextMultiplexId();

		if ( tbuf.hasName())
			setCommand(PacketTypeV1.Transaction);
		else
			setCommand(PacketTypeV1.Transaction2);

		setFlags(sess.getDefaultFlags());
		setFlags2(sess.getDefaultFlags2());
		setMultiplexId(mid);
		setTreeId(sess.getTreeId());
		setUserId(sess.getUserId());

		// Get the individual buffers from the transact buffer

		tbuf.setEndOfBuffer();

		DataBuffer setupBuf = tbuf.getSetupBuffer();
		DataBuffer paramBuf = tbuf.getParameterBuffer();
		DataBuffer dataBuf = tbuf.getDataBuffer();

		// Set the parameter count, add the function code setup parameter

		if ( tbuf.hasSetupBuffer())
			setParameterCount(StandardParams + setupBuf.getLengthInWords() + 1);
		else
			setParameterCount(StandardParams + 1);

		// Clear the parameters

		for (int i = 0; i < getParameterCount(); i++)
			setParameter(i, 0);

		// Get the total parameter/data block lengths

		int totParamLen = paramBuf != null ? paramBuf.getLength() : 0;
		int totDataLen = dataBuf != null ? dataBuf.getLength() : 0;

		// Initialize the parameters

		setTotalParameterCount(totParamLen);
		setTotalDataCount(totDataLen);
		setMaximumParameterReturn(tbuf.getReturnParameterLimit());
		setMaximumDataReturn(tbuf.getReturnDataLimit());

		// Pack the transaction name for Transaction2

		int availBuf = getAvailableLength();
		int pos = getByteOffset();

		if ( tbuf.hasName()) {

			// Pack the transaction name string

			pos = DataPacker.putString(tbuf.getName(), getBuffer(), pos, false);

			// Update the available buffer space

			availBuf -= tbuf.getName().length();
		}

		// Check if the transaction parameter block and data block will fit within a single request
		// packet

		int plen = totParamLen;
		int dlen = totDataLen;

		if ( (plen + dlen) > availBuf) {

			// Calculate the parameter/data block sizes to send in the first request packet

			if ( plen > 0) {

				// Check if the parameter block can fit into the packet

				if ( plen <= availBuf) {

					// Pack all of the parameter block and fill the remaining buffer with the data
					// block

					if ( dlen > 0)
						dlen = availBuf - plen;
				}
				else {

					// Split the parameter/data space in the packet

					plen = availBuf / 2;
					dlen = plen;
				}
			}
			else if ( dlen > availBuf) {

				// Fill the packet with the first section of the data block

				dlen = availBuf;
			}
		}

		// Set the parameter/data block counts for this packet

		setParameterBlockCount(plen);
		setParameterBlockOffset(0);
		setDataBlockCount(dlen);
		setDataBlockOffset(0);

		setSetupCount(setupBuf != null ? setupBuf.getLengthInWords() : 1);
		setSetupParameter(0, tbuf.getFunction());

		// Pack the setup bytes

		if ( setupBuf != null)
			setupBuf.copyData(getBuffer(), SetupOffset);

		// Pack the parameter block

		pos = DataPacker.wordAlign(pos);
		setPosition(pos);

		// Set the parameter block offset, from the start of the SMB packet

		setParameterBlockOffset(pos);

		int packLen = -1;

		if ( paramBuf != null) {

			// Pack the parameter block

			packLen = paramBuf.copyData(getBuffer(), pos, plen);

			// Update the buffer position for the data block

			pos = DataPacker.wordAlign(pos + packLen);
			setPosition(pos);
		}

		// Set the data block offset

		setDataBlockOffset(pos);

		// Pack the data block

		if ( dataBuf != null) {

			// Pack the data block

			packLen = dataBuf.copyData(getBuffer(), pos, dlen);

			// Update the end of buffer position

			setPosition(pos + packLen);
		}

		// Set the byte count for the SMB packet

		setByteCount();

		// Send/receive the transaction

		TransactBuffer respBuf = null;

		try {

			// Indicate that we are in a transaction, for SMB signing

			sess.setTransactionMID(mid);

			// Send the start of the transaction request

			SendSMB(sess);

			// If the transaction has been split over several requests the server will send an
			// interim response

			if ( (paramBuf != null && paramBuf.getAvailableLength() > 0) || (dataBuf != null && dataBuf.getAvailableLength() > 0)) {

				// Receive the interim response SMB

				ReceiveSMB(sess);
			}

			// Get the available parameter/data block buffer space for the secondary packet

			availBuf = getAvailableLength();

			// Loop until all parameter/data block data has been sent to the server

			while ((paramBuf != null && paramBuf.getAvailableLength() > 0)
					|| (dataBuf != null && dataBuf.getAvailableLength() > 0)) {

				// Setup the transaction secondary packet to send the remaining parameter/data
				// blocks

				setCommand(tbuf.isType() == PacketTypeV1.Transaction ? PacketTypeV1.TransactionSecond : PacketTypeV1.Transaction2Second);

				setFlags(sess.getDefaultFlags());
				setFlags2(sess.getDefaultFlags2());

				setParameterCount(8);
				setTotalParameterCount(totParamLen);
				setTotalDataCount(totDataLen);

				// Set a new multiple id for each secondary packet

				setMultiplexId(sess.getNextMultiplexId());

				// Get the remaining parameter/data block lengths

				plen = paramBuf != null ? paramBuf.getAvailableLength() : 0;
				dlen = dataBuf != null ? dataBuf.getAvailableLength() : 0;

				if ( (plen + dlen) > availBuf) {

					// Calculate the parameter/data block sizes to send in the first request packet

					if ( plen > 0) {

						// Check if the remaining parameter block can fit into the packet

						if ( plen <= availBuf) {

							// Pack all of the parameter block and fill the remaining buffer with
							// the data block

							if ( dlen > 0)
								dlen = availBuf - plen;
						}
						else {

							// Split the parameter/data space in the packet

							plen = availBuf / 2;
							dlen = plen;
						}
					}
					else if ( dlen > availBuf) {

						// Fill the packet with the first section of the data block

						dlen = availBuf;
					}
				}

				// Pack the parameter block data, if any

				resetBytePointerAlign();

				packLen = -1;
				pos = getPosition();

				if ( plen > 0 && paramBuf != null) {

					// Set the parameter block offset, from the start of the SMB packet

					setParameterBlockOffset(pos);

					// Pack the parameter block

					packLen = paramBuf.copyData(getBuffer(), pos, plen);

					// Update the buffer position for the data block

					pos = DataPacker.wordAlign(pos + packLen);
					setPosition(pos);
				}

				// Pack the data block, if any

				if ( dlen > 0 && dataBuf != null) {

					// Set the data block offset

					setDataBlockOffset(pos);

					// Pack the data block

					packLen = dataBuf.copyData(getBuffer(), pos, dlen);

					// Update the end of buffer position

					setPosition(pos + packLen);
				}

				// Set the byte count for the SMB packet to set the overall length

				setByteCount();

				// Send the transaction secondary request, there is no response sent until all of
				// the transaction
				// parameter/data blocks have been sent and the server processes the transaction

				SendSMB(sess);
			}

			// Set the packet type so that the receive processing filters the correct packet

			setCommand(tbuf.hasName() ? PacketTypeV1.Transaction : PacketTypeV1.Transaction2);

			// Receive the start of the transaction response

			ReceiveSMB(sess, false);

			// Check if the response is an error, there may be a warning to indicate that the reply
			// buffer is too short

			if ( isValidResponse() == false) // || warning_status
				checkForError();

			// Get the total return parameter block and data block lengths, and allocate the
			// response transaction buffer

			totParamLen = getTotalParameterCount();
			totDataLen = getTotalDataCount();
			int setupLen = (getParameterCount() - RxStandardParams) * 2;

			respBuf = new TransactBuffer(setupLen, totParamLen, totDataLen);
			respBuf.setUnicode(isUnicode());

			// Get the individual buffers from the transact buffer

			setupBuf = respBuf.getSetupBuffer();
			paramBuf = respBuf.getParameterBuffer();
			dataBuf = respBuf.getDataBuffer();

			// Copy the return setup parameters, if any

			if ( setupLen > 0)
				setupBuf.appendData(getBuffer(), SetupOffset, setupLen);

			// DEBUG

			if ( Debug.EnableInfo && Session.hasDebug())
				Debug.println("Receive transaction totParamLen=" + totParamLen + ", totDataLen=" + totDataLen);

			// Copy the parameter/data sections to the response transaction buffer and receive
			// additional response SMBs until all of the response has been processed

			while ((paramBuf != null && paramBuf.getUsedLength() < totParamLen)
					|| (dataBuf != null && dataBuf.getUsedLength() < totDataLen)) {

				// Copy the parameter data from the packet to the response buffer

				plen = getParameterBlockCount();

				if ( plen > 0 && paramBuf != null) {

					// Copy the parameter block section to the response buffer

					paramBuf.appendData(getBuffer(), getParameterBlockOffset(), plen);

					// DEBUG

					if ( Debug.EnableInfo && Session.hasDebug())
						Debug.println("  Receive param plen=" + plen + ", poff=" + getParameterBlockOffset());
				}

				// Copy the data from the packet to the response buffer

				dlen = getDataBlockCount();

				if ( dlen > 0 && dataBuf != null) {

					// Copy the data block section to the response buffer

					dataBuf.appendData(getBuffer(), getDataBlockOffset(), dlen);

					// DEBUG

					if ( Debug.EnableInfo && Session.hasDebug())
						Debug.println("  Receive data dlen=" + dlen + ", doff=" + getDataBlockOffset());
				}

				// Check if we have received all the parameter/data block data

				if ( (paramBuf != null && paramBuf.getUsedLength() < totParamLen)
						|| (dataBuf != null && dataBuf.getUsedLength() < totDataLen)) {

					// DEBUG

					if ( Debug.EnableInfo && Session.hasDebug())
						Debug.println("  Reading secondary trans pkt ...");

					// Read another packet of transaction response data

					ReceiveSMB(sess, false);

					// Check the receive status

					if ( isValidResponse() == false)
						checkForError();

					// Get the total parameter/data block lengths as they can change

					totParamLen = getTotalParameterCount();
					totDataLen = getTotalDataCount();
				}
			}

			// DEBUG

			if ( Debug.EnableInfo && Session.hasDebug())
				Debug.println("  Finished reading trans data respBuf=" + respBuf);

			// Reset the receive data buffers to read the data

			if ( respBuf != null)
				respBuf.setEndOfBuffer();
		}
		finally {

			// Indicate that the transaction is complete, for SMB signing

			sess.setTransactionMID(Session.NO_TRANSACTION);
		}

		// Return the response transaction buffer

		return respBuf;
	}

	/**
	 * Return the total parameter block length
	 * 
	 * @return int
	 */
	public final int getTotalParameterCount() {
		return getParameter(0);
	}

	/**
	 * Return the total data block length
	 * 
	 * @return int
	 */
	public final int getTotalDataCount() {
		return getParameter(1);
	}

	/**
	 * Return the parameter block length for the current packet
	 * 
	 * @return int
	 */
	public final int getParameterBlockCount() {
		if ( isResponse())
			return getParameter(3);
		else if ( isType() == PacketTypeV1.Transaction || isType() == PacketTypeV1.Transaction2)
			return getParameter(9);
		else
			return getParameter(2);
	}

	/**
	 * Return the parameter block offset for the current packet
	 * 
	 * @return int
	 */
	public final int getParameterBlockOffset() {
		int off = -1;
		if ( isResponse())
			off = getParameter(4);
		else if ( isType() == PacketTypeV1.Transaction || isType() == PacketTypeV1.Transaction2)
			off = getParameter(10);
		else
			return getParameter(3);
		return off + RFCNetBIOSProtocol.HEADER_LEN;
	}

	/**
	 * Return the data block length for the current packet
	 * 
	 * @return int
	 */
	public final int getDataBlockCount() {
		if ( isResponse())
			return getParameter(6);
		if ( isType() == PacketTypeV1.Transaction || isType() == PacketTypeV1.Transaction2)
			return getParameter(11);
		else
			return getParameter(5);
	}

	/**
	 * Return the data block offset for the current packet
	 * 
	 * @return int
	 */
	public final int getDataBlockOffset() {
		int off = -1;
		if ( isResponse())
			off = getParameter(7);
		else if ( isType() == PacketTypeV1.Transaction || isType() == PacketTypeV1.Transaction2)
			off = getParameter(12);
		else
			off = getParameter(6);
		return off + RFCNetBIOSProtocol.HEADER_LEN;
	}

	/**
	 * Set the specific setup parameter within the SMB packet.
	 * 
	 * @param idx Setup parameter index.
	 * @param val Setup parameter value.
	 */
	public final void setSetupParameter(int idx, int val) {
		setParameter(StandardParams + idx, val);
	}

	/**
	 * Set the transaction name for normal transactions
	 * 
	 * @param tname Transaction name string
	 */
	public final void setTransactionName(String tname) {
		m_transName = tname;
	}

	/**
	 * Set the total parameter block length parameter
	 * 
	 * @param len int
	 */
	public final void setTotalParameterCount(int len) {
		setParameter(0, len);
	}

	/**
	 * Set the total data block length parameter
	 * 
	 * @param len int
	 */
	public final void setTotalDataCount(int len) {
		setParameter(1, len);
	}

	/**
	 * Set the maximum return parameter block length parameter
	 * 
	 * @param len int
	 */
	public final void setMaximumParameterReturn(int len) {
		setParameter(2, len);
	}

	/**
	 * Set the maximum return data block length parameter
	 * 
	 * @param len int
	 */
	public final void setMaximumDataReturn(int len) {
		setParameter(3, len);
	}

	/**
	 * Set the parameter block section length parameter
	 * 
	 * @param len int
	 */
	public final void setParameterBlockCount(int len) {
		setParameter(9, len);
	}

	/**
	 * Set the parameter block section offset parameter
	 * 
	 * @param off int
	 */
	public final void setParameterBlockOffset(int off) {
		setParameter(10, off != 0 ? off - RFCNetBIOSProtocol.HEADER_LEN : 0);
	}

	/**
	 * Set the data block section length parameter
	 * 
	 * @param len int
	 */
	public final void setDataBlockCount(int len) {
		setParameter(11, len);
	}

	/**
	 * Set the data block section offset parameter
	 * 
	 * @param off int
	 */
	public final void setDataBlockOffset(int off) {
		setParameter(12, off != 0 ? off - RFCNetBIOSProtocol.HEADER_LEN : 0);
	}

	/**
	 * Set the setup paramater count
	 * 
	 * @param cnt int
	 */
	public final void setSetupCount(int cnt) {
		setParameter(13, cnt);
	}
}