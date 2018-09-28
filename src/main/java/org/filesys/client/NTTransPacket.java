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
 * NT Transaction Packet Class
 * 
 * @author gkspencer
 */
public class NTTransPacket extends SMBPacket {

	//  Define the number of standard parameter words/bytes

	private static final int StandardParams = 19;
	private static final int ParameterBytes = 36;		//	8 x 32bit params + max setup count byte + setup count byte + reserved word

	//	Offset to start of NT parameters from start of packet
	
	private static final int NTMaxSetupCount 	= SMBPacket.PARAMWORDS;
	private static final int NTParams 		    = SMBPacket.PARAMWORDS + 3;
	private static final int NTSetupCount     	= NTParams + 32;
	private static final int NTFunction		    = NTSetupCount + 1;

	//	Default return parameter/data byte counts
	
	private static final int DefaultReturnParams		= 4;
	private static final int DefaultReturnData			= 1024;
		
	/**
	 * Default constructor
	 */
	public NTTransPacket() {
		super();
	}

	/**
	 * Class constructor
	 * 
	 * @param buf byte[]
	 */
	public NTTransPacket(byte[] buf) {
		super(buf);
	}

	/**
	 * Return the data block size
	 * 
	 * @return Data block size in bytes
	 */
	public final int getDataLength() {
		return getNTParameter(6);
	}

	/**
	 * Return the data block offset
	 * 
	 * @return Data block offset within the SMB packet.
	 */
	public final int getDataOffset() {
		return getNTParameter(7) + RFCNetBIOSProtocol.HEADER_LEN;
	}

	/**
	 * Unpack the parameter block
	 * 
	 * @return int[]
	 */
	public final int[] getParameterBlock() {

		// Get the parameter count and allocate the parameter buffer

		int prmcnt = getParameterBlockCount() / 4; // convert to number of ints
		if ( prmcnt <= 0)
			return null;
		int[] prmblk = new int[prmcnt];

		// Get the offset to the parameter words, add the NetBIOS header length
		// to the offset.

		int pos = getParameterBlockOffset();

		// Unpack the parameter ints

		setBytePointer(pos, getByteCount());

		for (int idx = 0; idx < prmcnt; idx++) {

			// Unpack the current parameter value

			prmblk[idx] = unpackInt();
		}

		// Debug mode

		if ( Debug.EnableInfo && Session.hasDebugOption(Session.DBGDumpPacket)) {
			Debug.println("NT Transaction parameter dump - " + prmcnt + " params :-");
			for (int i = 0; i < prmcnt; i++)
				Debug.println(" " + i + ". = " + prmblk[i] + ", 0x" + Integer.toHexString(prmblk[i]));
		}

		// Return the parameter block

		return prmblk;
	}

	/**
	 * Return the total parameter count
	 * 
	 * @return int
	 */
	public final int getTotalParameterCount() {
		return getNTParameter(0);
	}

	/**
	 * Return the total data count
	 * 
	 * @return int
	 */
	public final int getTotalDataCount() {
		return getNTParameter(1);
	}

	/**
	 * Return the parameter block count
	 * 
	 * @return int
	 */
	public final int getParameterBlockCount() {
		return getNTParameter(2);
	}

	/**
	 * Return the parameter block offset
	 * 
	 * @return int
	 */
	public final int getParameterBlockOffset() {
		return getNTParameter(3) + RFCNetBIOSProtocol.HEADER_LEN;
	}

	/**
	 * Return the paramater block displacement
	 * 
	 * @return int
	 */
	public final int getParameterBlockDisplacement() {
		return getNTParameter(4);
	}

	/**
	 * Return the data block count
	 * 
	 * @return int
	 */
	public final int getDataBlockCount() {
		return getNTParameter(5);
	}

	/**
	 * Return the data block offset
	 * 
	 * @return int
	 */
	public final int getDataBlockOffset() {
		return getNTParameter(6) + RFCNetBIOSProtocol.HEADER_LEN;
	}

	/**
	 * Return the data block displacment
	 * 
	 * @return int
	 */
	public final int getDataBlockDisplacement() {
		return getNTParameter(7);
	}

	/**
	 * Initialize the transact SMB packet
	 * 
	 * @param func NT transaction function code
	 * @param paramblk Parameter block data bytes
	 * @param plen Parameter block data length
	 * @param datablk Data block data bytes
	 * @param dlen Data block data length
	 * @param setupcnt Number of setup parameters
	 */
	public final void InitializeNTTransact(int func, byte[] paramblk, int plen, byte[] datablk, int dlen, int setupcnt) {
		InitializeNTTransact(func, paramblk, plen, datablk, dlen, setupcnt, DefaultReturnParams, DefaultReturnData);
	}

	/**
	 * Initialize the transact SMB packet
	 * 
	 * @param func NT transaction function code
	 * @param paramblk Parameter block data bytes
	 * @param plen Parameter block data length
	 * @param datablk Data block data bytes
	 * @param dlen Data block data length
	 * @param setupcnt Number of setup parameters
	 * @param maxPrm Maximum parameter bytes to return
	 * @param maxData Maximum data bytes to return
	 */
	public final void InitializeNTTransact(int func, byte[] paramblk, int plen, byte[] datablk, int dlen, int setupcnt,
			int maxPrm, int maxData) {

		// Set the SMB command and parameter count

		setCommand(PacketTypeV1.NTTransact);
		setParameterCount(StandardParams + setupcnt);

		// Initialize the parameters

		setTotalParameterCount(plen);
		setTotalDataCount(dlen);
		setMaximumParameterReturn(maxPrm);
		setMaximumDataReturn(maxData);
		setNTParameterCount(plen);
		setParameterBlockOffset(0);
		setDataBlockCount(dlen);
		setDataBlockOffset(0);

		setSetupCount(setupcnt);
		setNTFunction(func);

		resetBytePointerAlign();

		// Pack the parameter block

		if ( paramblk != null) {

			// Set the parameter block offset, from the start of the SMB packet

			setParameterBlockOffset(getPosition());

			// Pack the parameter block

			packBytes(paramblk, plen);
		}

		// Pack the data block

		if ( datablk != null) {

			// Align the byte area offset and set the data block offset in the request

			alignBytePointer();
			setDataBlockOffset(getPosition());

			// Pack the data block

			packBytes(datablk, dlen);
		}

		// Set the byte count for the SMB packet

		setByteCount();
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

		setCommand(PacketTypeV1.NTTransact);

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

		// Set the parameter count

		if ( tbuf.hasSetupBuffer())
			setParameterCount(StandardParams + setupBuf.getLengthInWords());
		else
			setParameterCount(StandardParams);

		// Get the total parameter/data block lengths

		int totParamLen = paramBuf != null ? paramBuf.getLength() : 0;
		int totDataLen = dataBuf != null ? dataBuf.getLength() : 0;

		// Initialize the parameters

		setTotalParameterCount(totParamLen);
		setTotalDataCount(totDataLen);
		setMaximumParameterReturn(tbuf.getReturnParameterLimit());
		setMaximumDataReturn(tbuf.getReturnDataLimit());

		// Check if the transaction parameter block and data block will fit within a single request
		// packet

		int availBuf = getAvailableLength();
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

		setNTParameterCount(plen);
		setParameterBlockOffset(0);
		setDataBlockCount(dlen);
		setDataBlockOffset(0);

		setSetupCount(setupBuf != null ? setupBuf.getLengthInWords() : 0);
		setNTFunction(tbuf.getFunction());

		// Pack the setup bytes

		if ( setupBuf != null)
			setupBuf.copyData(getBuffer(), getSetupOffset());

		// Pack the parameter block

		resetBytePointerAlign();

		// Set the parameter block offset, from the start of the SMB packet

		int pos = getPosition();
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

				// Setup the NT transaction secondary packet to send the remaining parameter/data
				// blocks

				setCommand(PacketTypeV1.NTTransactSecond);

				setFlags(sess.getDefaultFlags());
				setFlags2(sess.getDefaultFlags2());

				setNTParameterCount(18);
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

				// Send the NT transaction secondary request, there is no response sent until all of
				// the transaction
				// parameter/data blocks have been sent and the server processes the transaction

				SendSMB(sess);
			}

			// Set the packet type so that the receive processing filters the correct packet

			setCommand(PacketTypeV1.NTTransact);

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
			int setupLen = getSetupCount() * 2;

			respBuf = new TransactBuffer(setupLen, totParamLen, totDataLen);

			// Get the individual buffers from the transact buffer

			setupBuf = respBuf.getSetupBuffer();
			paramBuf = respBuf.getParameterBuffer();
			dataBuf = respBuf.getDataBuffer();

			// Copy the return setup parameters, if any

			if ( setupLen > 0)
				setupBuf.appendData(getBuffer(), getSetupOffset(), setupLen);

			// Copy the parameter/data sections to the response transaction buffer and receive
			// additional response SMBs
			// until all of the response has been processed

			while ((paramBuf != null && paramBuf.getLength() < totParamLen)
					|| (dataBuf != null && dataBuf.getLength() < totDataLen)) {

				// Copy the parameter data from the packet to the response buffer

				plen = getParameterBlockCount();

				if ( plen > 0 && paramBuf != null) {

					// Copy the parameter block section to the response buffer

					paramBuf.appendData(getBuffer(), getParameterBlockOffset(), plen);
				}

				// Copy the data from the packet to the response buffer

				dlen = getDataBlockCount();

				if ( dlen > 0 && dataBuf != null) {

					// Copy the data block section to the response buffer

					dataBuf.appendData(getBuffer(), getDataBlockOffset(), dlen);
				}

				// Check if we have received all the parameter/data block data

				if ( (paramBuf != null && paramBuf.getLength() < totParamLen)
						|| (dataBuf != null && dataBuf.getLength() < totDataLen)) {

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
	 * Set the total parameter count
	 * 
	 * @param cnt int
	 */
	public final void setTotalParameterCount(int cnt) {
		setNTParameter(0, cnt);
	}

	/**
	 * Set the total data count
	 * 
	 * @param cnt int
	 */
	public final void setTotalDataCount(int cnt) {
		setNTParameter(1, cnt);
	}

	/**
	 * Set the maximum return parameter count
	 * 
	 * @param cnt int
	 */
	public final void setMaximumParameterReturn(int cnt) {
		setNTParameter(2, cnt);
	}

	/**
	 * Set the maximum return data count
	 * 
	 * @param cnt int
	 */
	public final void setMaximumDataReturn(int cnt) {
		setNTParameter(3, cnt);
	}

	/**
	 * Set the paramater block count
	 * 
	 * @param cnt int
	 */
	public final void setNTParameterCount(int cnt) {
		setNTParameter(4, cnt);
	}

	/**
	 * Set the parameter block offset within the packet
	 * 
	 * @param off int
	 */
	public final void setParameterBlockOffset(int off) {
		setNTParameter(5, off != 0 ? off - RFCNetBIOSProtocol.HEADER_LEN : 0);
	}

	/**
	 * Set the data block count
	 * 
	 * @param cnt int
	 */
	public final void setDataBlockCount(int cnt) {
		setNTParameter(6, cnt);
	}

	/**
	 * Set the data block offset
	 * 
	 * @param off int
	 */
	public final void setDataBlockOffset(int off) {
		setNTParameter(7, off != 0 ? off - RFCNetBIOSProtocol.HEADER_LEN : 0);
	}

	/**
	 * Get an NT parameter (32bit)
	 * 
	 * @param idx int
	 * @return int
	 */
	private final int getNTParameter(int idx) {
		int pos = NTParams + (4 * idx);
		return DataPacker.getIntelInt(getBuffer(), pos);
	}

	/**
	 * Get the setup parameter count
	 * 
	 * @return int
	 */
	public final int getSetupCount() {
		byte[] buf = getBuffer();
		return (int) buf[NTSetupCount] & 0xFF;
	}

	/**
	 * Return the offset to the setup words data
	 * 
	 * @return int
	 */
	public final int getSetupOffset() {
		return NTFunction + 2;
	}

	/**
	 * Get the NT transaction function code
	 * 
	 * @return int
	 */
	public final int getNTFunction() {
		byte[] buf = getBuffer();
		return DataPacker.getIntelShort(buf, NTFunction);
	}

	/**
	 * Set an NT parameter (32bit)
	 * 
	 * @param idx int
	 * @param val int
	 */
	private final void setNTParameter(int idx, int val) {
		int pos = NTParams + (4 * idx);
		DataPacker.putIntelInt(val, getBuffer(), pos);
	}

	/**
	 * Set the maximum setup parameter count
	 * 
	 * @param cnt Maximum count of setup paramater words
	 */
	public final void setMaximumSetupCount(int cnt) {
		byte[] buf = getBuffer();
		buf[NTMaxSetupCount] = (byte) cnt;
	}

	/**
	 * Set the setup parameter count
	 * 
	 * @param cnt Count of setup paramater words
	 */
	public final void setSetupCount(int cnt) {
		byte[] buf = getBuffer();
		buf[NTSetupCount] = (byte) cnt;
	}

	/**
	 * Set the NT transaction function code
	 * 
	 * @param func int
	 */
	public final void setNTFunction(int func) {
		byte[] buf = getBuffer();
		DataPacker.putIntelShort(func, buf, NTFunction);
	}

	/**
	 * Reset the byte/parameter pointer area for packing/unpacking setup paramaters items to the
	 * packet
	 */
	public final void resetSetupPointer() {
		m_pos = NTFunction + 2;
		m_endpos = m_pos;
	}

	/**
	 * Reset the byte/parameter pointer area for packing/unpacking the transaction data block
	 */
	public final void resetDataBlockPointer() {
		m_pos = getDataBlockOffset();
		m_endpos = m_pos;
	}

	/**
	 * Reset the byte/parameter pointer area for packing/unpacking the transaction paramater block
	 */
	public final void resetParameterBlockPointer() {
		m_pos = getParameterBlockOffset();
		m_endpos = m_pos;
	}
}
