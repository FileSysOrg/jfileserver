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

package org.filesys.client.admin;

import java.io.*;

import org.filesys.client.IPCSession;
import org.filesys.client.SMBPacket;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.SMBException;
import org.filesys.smb.SMBStatus;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCECommand;
import org.filesys.smb.dcerpc.client.DCEPacket;
import org.filesys.util.DataPacker;

/**
 * IPC Pipe File Class
 * 
 * <p>Base class for the various IPC$ pipe file classes that are used to connect to remote
 * DCE/RPC services, such as for remote registry access.
 * 
 * @author gkspencer
 */
public class IPCPipeFile {

	//	IPC session that this pipe is associated with
	
	private IPCSession m_sess;
	
	//	DCE/RPC packet
	
	private DCEPacket m_pkt;
	
	//	DCE buffer for packing/receiving requests
	
	private DCEBuffer m_buffer;
	private DCEBuffer m_rxBuffer;
	
	//	Handle of the pipe file
	
	private int m_handle;
	
	//	Pipe file name
	
	private String m_name;
	
	//	DCE/RPC call id
	
	private int m_callId;
	
	//	Maximum transmit/receive size
	
	private int m_maxTxSize;
	private int m_maxRxSize;
	
	/**
	 * Class constructor
	 * 
	 * @param sess SMBIPCSession
	 * @param pkt DCEPacket
	 * @param handle int
	 * @param name String
	 * @param maxTxSize int
	 * @param maxRxSize int
	 */
	public IPCPipeFile(IPCSession sess, DCEPacket pkt, int handle, String name, int maxTxSize, int maxRxSize) {
		m_sess = sess;
		m_pkt = pkt;
		m_handle = handle;
		m_name = name;

		m_maxTxSize = maxTxSize;
		m_maxRxSize = maxRxSize;

		// Allocate a DCE buffer

		m_buffer = new DCEBuffer();

		// Initialize the next call id

		m_callId = 1;
	}

	/**
	 * Return the pipe name
	 * 
	 * @return String
	 */
	public final String getPipeName() {
		return m_name;
	}

	/**
	 * Return the associated SMB session
	 * 
	 * @return SMBIPCSession
	 */
	protected final IPCSession getSession() {
		return m_sess;
	}

	/**
	 * Return the DCE buffer
	 * 
	 * @return DCEBuffer
	 */
	protected final DCEBuffer getBuffer() {
		return m_buffer;
	}

	/**
	 * Return the receive DCE buffer
	 * 
	 * @return DCEBuffer
	 */
	protected final DCEBuffer getRxBuffer() {
		return m_rxBuffer;
	}

	/**
	 * Return the DCE packet
	 * 
	 * @return DCEPacket
	 */
	protected final DCEPacket getPacket() {
		return m_pkt;
	}

	/**
	 * Return the pipe file handle
	 * 
	 * @return int
	 */
	protected final int getHandle() {
		return m_handle;
	}

	/**
	 * Return the current DCE/RPC call id
	 * 
	 * @return int
	 */
	protected final int getCallId() {
		return m_callId;
	}

	/**
	 * Increment the call id and return the new value
	 * 
	 * @return int
	 */
	protected final int getNextCallId() {
		return ++m_callId;
	}

	/**
	 * Return the maximum transmit data size
	 * 
	 * @return int
	 */
	public final int getMaximumTransmitSize() {
		return m_maxTxSize;
	}

	/**
	 * Return the maximum receive data size
	 * 
	 * @return int
	 */
	public final int getMaximumReceiveSize() {
		return m_maxRxSize;
	}

	/**
	 * Set the maximum transmit data size
	 * 
	 * @param siz int
	 */
	public final void setMaximumTransmitSize(int siz) {
		m_maxTxSize = siz;
	}

	/**
	 * Set the maximum receive data size
	 * 
	 * @param siz int
	 */
	public final void setMaximumReceiveSize(int siz) {
		m_maxRxSize = siz;
	}

	/**
	 * Send the DCE request to the server and receive the response data. The response may not fit
	 * into a single reply packet in which case read requests must be made on the pipe to return the
	 * remaining data.
	 * 
	 * @param pkt DCEPacket
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void doDCERequest(DCEPacket pkt)
		throws java.io.IOException, SMBException {

		// Make sure the header flags are set

		int flags2 = SMBPacket.FLG2_LONGERRORCODE;
		if ( (getSession().getDefaultFlags2() & SMBPacket.FLG2_SECURITYSIG) != 0)
			flags2 += SMBPacket.FLG2_SECURITYSIG;

		pkt.setFlags(0);
		pkt.setFlags2(flags2);

		// Send the DCE request transaction and receive the initial response packet

		getSession().SendTransaction(pkt, pkt);

		boolean continuedReq = false;

		if ( pkt.isValidResponse() == false) {
			if ( pkt.hasLongErrorCode()) {

				// Check for a buffer overflow status, set if further reads are required on the pipe
				// to get
				// all of the data

				if ( pkt.getLongErrorCode() == SMBStatus.NTBufferOverflow)
					continuedReq = true;
				else
					throw new SMBException(SMBStatus.NTErr, pkt.getLongErrorCode());
			}
			else if ( pkt.getErrorCode() == SMBStatus.NETContinued)
				continuedReq = true;
			else
				throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());
		}

		// Get the allocation hint from the response

		pkt.resetDCEOffset();
		int dceSize = pkt.getAllocationHint();
		int fragLen = pkt.getFragmentLength();

		// Check if we received a valid DCE/RPC response

		if ( pkt.getPDUType() != DCECommand.RESPONSE)
			throw new SMBException(SMBStatus.DCERPCErr, SMBStatus.DCERPC_Fault);

		// Allocate a DCE buffer to hold the data and copy the response data to the
		// new buffer

		m_rxBuffer = new DCEBuffer(dceSize);
		int offset = 0;

		try {

			// Copy the data

			int rxSize = pkt.getParameter(6);
			offset = pkt.getParameter(7) + RFCNetBIOSProtocol.HEADER_LEN;
			m_rxBuffer.appendData(pkt.getBuffer(), offset + DCEPacket.HDRLEN_REQUEST, rxSize - DCEPacket.HDRLEN_REQUEST);

			// Update the remaining fragment length

			fragLen -= rxSize;
		}
		catch (DCEBufferException ex) {
		}

		// Check for a continued transaction response, use pipe reads to get the remaining data

		if ( continuedReq == true) {

			// Calculate the maximum read size

			int maxReadSize = pkt.getBufferLength() - pkt.getByteOffset();

			// Read the remaining data from the pipe

			boolean newFrag = false;
			boolean lastFrag = false;
			boolean dceDone = false;

			// Check if the last fragment flag is set

			int flags = (int) (pkt.getBuffer()[offset + DCEPacket.HEADERFLAGS] & 0xFF);
			if ( (flags & DCEPacket.FLG_LASTFRAG) != 0)
				lastFrag = true;

			while (dceDone == false) {

				// Calculate the read size, we must be careful not to read too much data as
				// it causes problems on older servers.

				int readSize = maxReadSize;

				if ( newFrag == false && fragLen < readSize)
					readSize = fragLen;

				// Read a buffer of data from the named pipe

				pkt.setCommand(PacketTypeV1.ReadAndX);

				pkt.setFlags(0);
				pkt.setFlags2(flags2);

				// Update the active transaction multiplex id

				pkt.setMultiplexId(m_sess.getNextMultiplexId());

				pkt.setParameterCount(12);
				pkt.setByteCount(0);

				pkt.setAndXCommand(0xFF);
				pkt.setParameter(2, getHandle());
				pkt.setParameter(3, 0);
				pkt.setParameter(4, 0);
				pkt.setParameter(5, readSize); // pkt.getBufferLength() - pkt.getByteOffset());
				pkt.setParameter(6, readSize);
				pkt.setParameter(7, 0);
				pkt.setParameter(8, 0);
				pkt.setParameter(9, 0);
				pkt.setParameterLong(10, 0);

				pkt.setFlags(0);
				pkt.setFlags2(SMBPacket.FLG2_LONGERRORCODE);

				pkt.setLongErrorCode(0);

				pkt.ExchangeSMB(getSession(), pkt);

				// Check if the named pipe read request was successful

				if ( pkt.isValidResponse() == false) {
					if ( pkt.hasLongErrorCode())
						throw new SMBException(SMBStatus.NTErr, pkt.getLongErrorCode());
					else if ( pkt.getErrorCode() == SMBStatus.NETContinued)
						continuedReq = true;
					else
						throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());
				}

				// Get the received data size

				readSize = pkt.getParameter(5);
				offset = pkt.getParameter(6) + RFCNetBIOSProtocol.HEADER_LEN;

				// Append the data to the DCE buffer

				try {

					// Update the remaining fragment size

					fragLen -= readSize;

					// Check if this is a new fragment, if so then strip the header when copying to
					// the
					// data buffer

					if ( newFrag) {

						// Strip out the header when copying to the DCE buffer

						m_rxBuffer.appendData(pkt.getBuffer(), offset + DCEPacket.HDRLEN_REQUEST, readSize
								- DCEPacket.HDRLEN_REQUEST);

						// Clear the new fragment flag and set the new fragment length

						fragLen = DataPacker.getIntelShort(pkt.getBuffer(), offset + DCEPacket.FRAGMENTLEN);

						// Check if this is the last fragment

						flags = (int) (pkt.getBuffer()[offset + DCEPacket.HEADERFLAGS] & 0xFF);
						if ( (flags & DCEPacket.FLG_LASTFRAG) != 0)
							lastFrag = true;

						// Subtract the current read length from the fragment length, if this is the
						// last fragment and there
						// is no more data set the completion flag

						fragLen -= readSize;

						if ( fragLen == 0) {
							if ( lastFrag == true)
								dceDone = true;
						}
						else
							newFrag = false;
					}
					else {

						// Copy the data

						m_rxBuffer.appendData(pkt.getBuffer(), offset, readSize);

						// Check if we have reached the end of the current fragment, if so then set
						// the new fragment
						// flag

						if ( fragLen == 0) {
							if ( lastFrag == true)
								dceDone = true;
							else
								newFrag = true;
						}
					}
				}
				catch (DCEBufferException ex) {
				}
			}
		}
	}

	/**
	 * Send the DCE request to the server and receive the response data. The response may not fit
	 * into a single reply packet in which case read requests must be made on the pipe to return the
	 * remaining data.
	 * 
	 * @param opCode int
	 * @param buf DCEBuffer
	 * @param maxTx int
	 * @param callId int
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 * @exception DCEBufferException Buffer error
	 */
	public final void doDCERequest(int opCode, DCEBuffer buf, int maxTx, int callId)
		throws java.io.IOException, SMBException, DCEBufferException {

		// Initialize the DCE request

		DCEPacket pkt = getPacket();

		int flags2 = SMBPacket.FLG2_LONGERRORCODE;
		if ( (getSession().getDefaultFlags2() & SMBPacket.FLG2_SECURITYSIG) != 0)
			flags2 += SMBPacket.FLG2_SECURITYSIG;

		// If the DCE/RPC request data will fit in a single fragment then send using a transaction
		// else
		// send using multiple writes to the pipe file

		boolean firstFrag = true;

		while (buf.getAvailableLength() > (maxTx - DCEPacket.OPERATIONDATA)) {

			// Initialize the fragment write request

			pkt.initializeDCEWrite(getHandle(), firstFrag ? DCEPacket.FLG_FIRSTFRAG : 0, callId, maxTx);

			// Setup the DCE/RPC header

			pkt.setOperationId(opCode);
			pkt.setAllocationHint(buf.getLength());
			pkt.setFragmentLength(maxTx);

			// Copy the data to the request

			byte[] pktbuf = pkt.getBuffer();
			int pktpos = pkt.getDCEBaseOffset() + DCEPacket.OPERATIONDATA;

			pktpos += buf.copyData(pktbuf, pktpos, maxTx - DCEPacket.OPERATIONDATA);
			pkt.setByteCount(pktpos - pkt.getByteOffset());

			// Send the fragment

			getSession().SendTransaction(pkt, pkt);

			// Clear the first fragment flag

			firstFrag = false;
		}

		// Send the DCE/RPC request, or last fragment of the request, using a transaction

		pkt.initializeDCERequest(getHandle(), opCode, buf, maxTx, callId);

		// Make sure the header flags are set

		pkt.setFlags(0);
		pkt.setFlags2(flags2);

		// Send the DCE/RPC request and receive the response

		getSession().SendTransaction(pkt, pkt);

		// Check if more reads of the pipe are required to read the whole response

		boolean continuedReq = false;

		if ( pkt.isValidResponse() == false) {
			if ( pkt.hasLongErrorCode()) {

				// Check for a buffer overflow status, set if further reads are required on the pipe
				// to get
				// all of the data

				if ( pkt.getLongErrorCode() == SMBStatus.NTBufferOverflow)
					continuedReq = true;
				else
					throw new SMBException(SMBStatus.NTErr, pkt.getLongErrorCode());
			}
			else if ( pkt.getErrorCode() == SMBStatus.NETContinued)
				continuedReq = true;
			else
				throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());
		}

		// Get the allocation hint from the response

		pkt.resetDCEOffset();
		int dceSize = pkt.getAllocationHint();
		int fragLen = pkt.getFragmentLength();

		// Check if we received a valid DCE/RPC response

		if ( pkt.getPDUType() != DCECommand.RESPONSE)
			throw new SMBException(SMBStatus.DCERPCErr, SMBStatus.DCERPC_Fault);

		// Allocate a DCE buffer to hold the data and copy the response data to the
		// new buffer

		m_rxBuffer = new DCEBuffer(dceSize);
		int offset = 0;

		try {

			// Copy the data

			int rxSize = pkt.getParameter(6);
			offset = pkt.getParameter(7) + RFCNetBIOSProtocol.HEADER_LEN;
			m_rxBuffer.appendData(pkt.getBuffer(), offset + DCEPacket.HDRLEN_REQUEST, rxSize - DCEPacket.HDRLEN_REQUEST);

			// Update the remaining fragment length

			fragLen -= rxSize;
		}
		catch (DCEBufferException ex) {
		}

		// Check for a continued transaction response, use pipe reads to get the remaining data

		if ( continuedReq == true) {

			// Calculate the maximum read size

			int maxReadSize = pkt.getBufferLength() - pkt.getByteOffset();

			// Read the remaining data from the pipe

			boolean newFrag = false;
			boolean lastFrag = false;
			boolean dceDone = false;

			// Check if the last fragment flag is set

			int flags = (int) (pkt.getBuffer()[offset + DCEPacket.HEADERFLAGS] & 0xFF);
			if ( (flags & DCEPacket.FLG_LASTFRAG) != 0)
				lastFrag = true;

			while (dceDone == false) {

				// Calculate the read size, we must be careful not to read too much data as
				// it causes problems on older servers.

				int readSize = maxReadSize;

				if ( newFrag == false && fragLen < readSize)
					readSize = fragLen;

				// Read a buffer of data from the named pipe

				pkt.setCommand(PacketTypeV1.ReadAndX);

				pkt.setFlags(0);
				pkt.setFlags2(flags2);

				// Update the active transaction multiplex id

				pkt.setMultiplexId(m_sess.getNextMultiplexId());

				pkt.setParameterCount(12);
				pkt.setByteCount(0);

				pkt.setAndXCommand(0xFF);
				pkt.setParameter(2, getHandle());
				pkt.setParameter(3, 0);
				pkt.setParameter(4, 0);
				pkt.setParameter(5, readSize); // pkt.getBufferLength() - pkt.getByteOffset());
				pkt.setParameter(6, readSize);
				pkt.setParameter(7, 0);
				pkt.setParameter(8, 0);
				pkt.setParameter(9, 0);
				pkt.setParameterLong(10, 0);

				pkt.setFlags(0);
				pkt.setFlags2(SMBPacket.FLG2_LONGERRORCODE);

				pkt.setLongErrorCode(0);

				pkt.ExchangeSMB(getSession(), pkt);

				// Check if the named pipe read request was successful

				if ( pkt.isValidResponse() == false) {
					if ( pkt.hasLongErrorCode())
						throw new SMBException(SMBStatus.NTErr, pkt.getLongErrorCode());
					else if ( pkt.getErrorCode() == SMBStatus.NETContinued)
						continuedReq = true;
					else
						throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());
				}

				// Get the received data size

				readSize = pkt.getParameter(5);
				offset = pkt.getParameter(6) + RFCNetBIOSProtocol.HEADER_LEN;

				// Append the data to the DCE buffer

				try {

					// Update the remaining fragment size

					fragLen -= readSize;

					// Check if this is a new fragment, if so then strip the header when copying to
					// the
					// data buffer

					if ( newFrag) {

						// Strip out the header when copying to the DCE buffer

						m_rxBuffer.appendData(pkt.getBuffer(), offset + DCEPacket.HDRLEN_REQUEST, readSize
								- DCEPacket.HDRLEN_REQUEST);

						// Clear the new fragment flag and set the new fragment length

						fragLen = DataPacker.getIntelShort(pkt.getBuffer(), offset + DCEPacket.FRAGMENTLEN);

						// Check if this is the last fragment

						flags = (int) (pkt.getBuffer()[offset + DCEPacket.HEADERFLAGS] & 0xFF);
						if ( (flags & DCEPacket.FLG_LASTFRAG) != 0)
							lastFrag = true;

						// Subtract the current read length from the fragment length, if this is the
						// last fragment and there
						// is no more data set the completion flag

						fragLen -= readSize;

						if ( fragLen == 0) {
							if ( lastFrag == true)
								dceDone = true;
						}
						else
							newFrag = false;
					}
					else {

						// Copy the data

						m_rxBuffer.appendData(pkt.getBuffer(), offset, readSize);

						// Check if we have reached the end of the current fragment, if so then set
						// the new fragment
						// flag

						if ( fragLen == 0) {
							if ( lastFrag == true)
								dceDone = true;
							else
								newFrag = true;
						}
					}
				}
				catch (DCEBufferException ex) {
				}
			}
		}
	}

	/**
	 * Check the specified return status, it if is an error status then throw an exception
	 * 
	 * @param sts int
	 * @exception SMBException SMB error
	 */
	protected final void checkStatus(int sts)
		throws SMBException {

		// Check for a success status

		if ( sts != SMBStatus.NTSuccess)
			throw new SMBException((sts & 0xC0000000) == 0 ? SMBStatus.Win32Err : SMBStatus.NTErr, sts);
	}

	/**
	 * Close the pipe
	 * 
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public void ClosePipe()
		throws IOException, SMBException {

		// Determine which packet to use to send the close file SMB

		SMBPacket pkt = new SMBPacket();
		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Close the remote file.

		pkt.setCommand(PacketTypeV1.CloseFile);

		pkt.setParameterCount(3);
		pkt.setParameter(0, m_handle);
		pkt.setParameter(1, 0);
		pkt.setParameter(2, 0);

		// Exchange the close file SMB packet with the file server

		pkt.ExchangeSMB(m_sess, pkt);

		// Indicate that the pipe is not open

		m_handle = -1;
	}

	/**
	 * Determine if the pipe file is closed
	 * 
	 * @return boolean
	 */
	public final boolean isClosed() {
		return m_handle == -1 ? true : false;
	}

	/**
	 * Return the pipe file as a string
	 * 
	 * @return String
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();

		str.append("[");
		str.append(getPipeName());
		str.append(":");
		str.append(getHandle());
		str.append("]");

		return str.toString();
	}
}
