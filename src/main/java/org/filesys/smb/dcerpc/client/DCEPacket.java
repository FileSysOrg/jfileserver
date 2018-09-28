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

package org.filesys.smb.dcerpc.client;

import org.filesys.client.SMBPacket;
import org.filesys.client.TransPacket;
import org.filesys.debug.Debug;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCECommand;
import org.filesys.smb.dcerpc.DCEDataPacker;
import org.filesys.smb.dcerpc.DCEPipeType;
import org.filesys.util.DataPacker;

/**
 * DCE/RPC Packet Class
 * 
 * @author gkspencer
 */
public class DCEPacket extends TransPacket {

	//	DCE/RPC header offsets
	public static final int VERSIONMAJOR		= 0;
	public static final int VERSIONMINOR		= 1;
	public static final int PDUTYPE				= 2;
	public static final int HEADERFLAGS			= 3;
	public static final int PACKEDDATAREP		= 4;
	public static final int FRAGMENTLEN			= 8;
	public static final int AUTHLEN				= 10;
	public static final int CALLID				= 12;
	public static final int DCEDATA				= 16;
	
	//	DCE/RPC Request offsets
	public static final int ALLOCATIONHINT		= 16;
	public static final int PRESENTIDENT		= 20;
	public static final int OPERATIONID			= 22;
	public static final int OPERATIONDATA		= 24;
	
	//	Header flags
	public static final int FLG_FIRSTFRAG		= 0x01;
	public static final int FLG_LASTFRAG		= 0x02;
	public static final int FLG_CANCEL			= 0x04;
	public static final int FLG_IDEMPOTENT		= 0x20;
	public static final int FLG_BROADCAST		= 0x40;
	
	public static final int FLG_ONLYFRAG		= 0x03;
	
	//	DCE/RPC header constants
	private static final byte HDR_VERSIONMAJOR	= 5;
	private static final byte HDR_VERSIONMINOR	= 0;
	private static final int HDR_PACKEDDATAREP	= 0x00000010;
	
	//	DCE Bind fragment length
	private static final int BIND_DATALEN		= 56;
	private static final int BIND_LENGTH		= 72;
	
	//	Header lengths
	public static final int HDRLEN_STANDARD		= 16;
	public static final int HDRLEN_REQUEST		= 24;

	//  Offset that the write data is placed within the write SMB packet, including protocol header of 4 bytes
	private static final int WriteDataOffset  = 64;
  
	// Offset to DCE/RPC header
	private int m_offset;

	/**
	 * Construct a DCE/RPC transaction packet
	 * 
	 * @param buf Buffer that contains the SMB transaction packet.
	 */
	public DCEPacket(byte[] buf) {
		super(buf);
		m_offset = getParameterOffset();
	}

	/**
	 * Construct a DCE/RPC transaction packet
	 * 
	 * @param siz Size of packet to allocate.
	 */
	public DCEPacket(int siz) {
		super(siz);
	}

	/**
	 * Construct a DCE/RPC transaction packet of the specified size, and initialize from the
	 * supplied SMB packet.
	 * 
	 * @param siz int
	 * @param pkt SMBPacket
	 */
	public DCEPacket(int siz, SMBPacket pkt) {
		super(siz);

		// Copy the basic session details from the supplied SMB packet
		setTreeId(pkt.getTreeId());
		setUserId(pkt.getUserId());
		setProcessId(pkt.getProcessId());
	}

	/**
	 * Return the parameter block offset
	 * 
	 * @return Paramter block offset within the SMB packet
	 */
	public final int getParameterOffset() {
		int prmIdx = isResponse() ? 4 : 10;
		return getParameter(prmIdx) + RFCNetBIOSProtocol.HEADER_LEN;
	}

	/**
	 * Return the major version number
	 * 
	 * @return int
	 */
	public final int getMajorVersion() {
		return (int) (getBuffer()[m_offset + VERSIONMAJOR] & 0xFF);
	}

	/**
	 * Return the minor version number
	 * 
	 * @return int
	 */
	public final int getMinorVersion() {
		return (int) (getBuffer()[m_offset + VERSIONMINOR] & 0xFF);
	}

	/**
	 * Return the PDU packet type
	 * 
	 * @return int
	 */
	public final int getPDUType() {
		return (int) (getBuffer()[m_offset + PDUTYPE] & 0xFF);
	}

	/**
	 * Return the header flags
	 * 
	 * @return int
	 */
	public final int getHeaderFlags() {
		return (int) (getBuffer()[m_offset + HEADERFLAGS] & 0xFF);
	}

	/**
	 * Set the header flags
	 * 
	 * @param flags int
	 */
	public final void setHeaderFlags(int flags) {
		getBuffer()[m_offset + HEADERFLAGS] = (byte) (flags & 0xFF);
	}

	/**
	 * Return the packed data representation
	 * 
	 * @return int
	 */
	public final int getPackedDataRepresentation() {
		return DataPacker.getIntelInt(getBuffer(), m_offset + PACKEDDATAREP);
	}

	/**
	 * Return the fragment length
	 * 
	 * @return int
	 */
	public final int getFragmentLength() {
		return DataPacker.getIntelShort(getBuffer(), m_offset + FRAGMENTLEN);
	}

	/**
	 * Set the fragment length
	 * 
	 * @param len int
	 */
	public final void setFragmentLength(int len) {

		// Set the DCE header fragment length
		DataPacker.putIntelShort(len, getBuffer(), m_offset + FRAGMENTLEN);
	}

	/**
	 * Return the authentication length
	 * 
	 * @return int
	 */
	public final int getAuthenticationLength() {
		return DataPacker.getIntelShort(getBuffer(), m_offset + AUTHLEN);
	}

	/**
	 * Return the call id
	 * 
	 * @return int
	 */
	public final int getCallId() {
		return DataPacker.getIntelInt(getBuffer(), m_offset + CALLID);
	}

	/**
	 * Determine if this is the first fragment
	 * 
	 * @return boolean
	 */
	public final boolean isFirstFragment() {
		if ( (getHeaderFlags() & FLG_FIRSTFRAG) != 0)
			return true;
		return false;
	}

	/**
	 * Determine if this is the last fragment
	 * 
	 * @return boolean
	 */
	public final boolean isLastFragment() {
		if ( (getHeaderFlags() & FLG_LASTFRAG) != 0)
			return true;
		return false;
	}

	/**
	 * Determine if this is the only fragment in the request
	 * 
	 * @return boolean
	 */
	public final boolean isOnlyFragment() {
		if ( (getHeaderFlags() & FLG_ONLYFRAG) == FLG_ONLYFRAG)
			return true;
		return false;
	}

	/**
	 * Return the DCE/RPC fragment offset
	 * 
	 * @return int
	 */
	public final int getDCEOffset() {
		return getParameterOffset();
	}

	/**
	 * Return the DCE data offset
	 * 
	 * @return int
	 */
	public final int getDCEBaseOffset() {
		return m_offset;
	}

	/**
	 * Get the offset to the DCE/RPC data within the SMB packet
	 * 
	 * @return int
	 */
	public final int getDCEDataOffset() {

		// Determine the data offset from the DCE/RPC packet type
		m_offset = getParameterOffset();
		int dataOff = -1;
		switch (getPDUType()) {

			// Bind/bind acknowledge
			case DCECommand.BIND:
			case DCECommand.BINDACK:
				dataOff = m_offset + DCEDATA;
				break;

			// Request/response
			case DCECommand.REQUEST:
			case DCECommand.RESPONSE:
				dataOff = m_offset + OPERATIONDATA;
				break;
		}

		// Return the data offset
		return dataOff;
	}

	/**
	 * Reset the DCE offset
	 */
	public final void resetDCEOffset() {
		m_offset = getParameterOffset();
	}

	/**
	 * Get the request allocation hint
	 * 
	 * @return int
	 */
	public final int getAllocationHint() {
		return DataPacker.getIntelInt(getBuffer(), m_offset + ALLOCATIONHINT);
	}

	/**
	 * Set the allocation hint
	 * 
	 * @param alloc int
	 */
	public final void setAllocationHint(int alloc) {
		DataPacker.putIntelInt(alloc, getBuffer(), m_offset + ALLOCATIONHINT);
	}

	/**
	 * Get the request presentation identifier
	 * 
	 * @return int
	 */
	public final int getPresentationIdentifier() {
		return DataPacker.getIntelShort(getBuffer(), m_offset + PRESENTIDENT);
	}

	/**
	 * Set the presentation identifier
	 * 
	 * @param ident int
	 */
	public final void setPresentationIdentifier(int ident) {
		DataPacker.putIntelShort(ident, getBuffer(), m_offset + PRESENTIDENT);
	}

	/**
	 * Get the request operation id
	 * 
	 * @return int
	 */
	public final int getOperationId() {
		return DataPacker.getIntelShort(getBuffer(), m_offset + OPERATIONID);
	}

	/**
	 * Set the request operation id
	 * 
	 * @param opCode int
	 */
	public final void setOperationId(int opCode) {
		DataPacker.putIntelShort(opCode, getBuffer(), m_offset + OPERATIONID);
	}

	/**
	 * Initialize the DCE/RPC transaction header. Set the SMB transaction parameter count so that
	 * the data offset can be calculated.
	 * 
	 * @param handle int
	 * @param typ byte
	 * @param flags int
	 * @param callId int
	 */
	protected final void initializeDCETransaction(int handle, byte typ, int flags, int callId) {

		// Set the SMB packet command and flags
		setCommand(PacketTypeV1.Transaction);
		setFlags(0);
		setFlags2(SMBPacket.FLG2_LONGERRORCODE);

		// Initialize the DCE request transaction packet
		setParameterCount(16);
		setParameter(0, 0); // parameter bytes being sent
		setParameter(1, 0); // data bytes being sent
		setParameter(2, 0); // parameter bytes to return
		setParameter(3, 1024); // maximum data bytes to return
		setParameter(4, 0); // setup words to return
		setParameter(5, 0); // additional information
		setParameter(6, 0); // timeout
		setParameter(7, 0); // "
		setParameter(8, 0);

		setParameter(9, 0); // number of parameter bytes this buffer
		setParameter(11, 0); // number of data bytes this buffer

		// Set the setup words

		setParameter(13, 2); // setup words
		setSetupParameter(0, PacketTypeV1.TransactNmPipe);
		setSetupParameter(1, handle);

		// Store the transact name, '\PIPE\'
		int bytPos = getByteOffset();
		bytPos = DataPacker.putString("\\PIPE\\", getBuffer(), bytPos, true);

		// Set the DCE offset
		m_offset = DCEDataPacker.longwordAlign(bytPos);

		setParameter(10, m_offset - RFCNetBIOSProtocol.HEADER_LEN); // offset to parameter bytes
		setParameter(12, m_offset - RFCNetBIOSProtocol.HEADER_LEN); // offset to data bytes

		// Build the DCE/RPC header
		byte[] buf = getBuffer();
		DataPacker.putZeros(buf, m_offset, 24);

		buf[m_offset + VERSIONMAJOR] = HDR_VERSIONMAJOR;
		buf[m_offset + VERSIONMINOR] = HDR_VERSIONMINOR;
		buf[m_offset + PDUTYPE] = typ;
		buf[m_offset + HEADERFLAGS] = (byte) (flags & 0xFF);
		DataPacker.putIntelInt(HDR_PACKEDDATAREP, buf, m_offset + PACKEDDATAREP);
		DataPacker.putIntelInt(0, buf, m_offset + AUTHLEN);
		DataPacker.putIntelInt(callId, buf, m_offset + CALLID);
	}

	/**
	 * Initialize the DCE/RPC write header, used for multi fragment DCE/RPC requests.
	 * 
	 * @param handle int
	 * @param flags int
	 * @param callId int
	 * @param fragLen int
	 */
	public final void initializeDCEWrite(int handle, int flags, int callId, int fragLen) {

		// Set the SMB packet command and flags
		setCommand(PacketTypeV1.WriteAndX);
		setFlags(0);
		setFlags2(SMBPacket.FLG2_LONGERRORCODE);

		// Initialize the DCE request write packet
		setParameterCount(14);
		setAndXCommand(PacketTypeV1.NoChainedCommand);
		setParameter(1, 0);

		setParameter(2, handle);
		setParameterLong(3, 0);
		setParameterLong(5, 0xFFFFFFFF);
		setParameter(7, 0x08); // write mode, message start
		setParameter(8, fragLen);
		setParameter(9, 0);
		setParameter(10, fragLen);
		setParameter(11, WriteDataOffset);
		setParameter(12, 0);
		setParameterLong(13, 0);

		// Build the DCE/RPC header
		m_offset = DCEDataPacker.wordAlign(getByteOffset());
		byte[] buf = getBuffer();
		DataPacker.putZeros(buf, m_offset, 24);

		buf[m_offset + VERSIONMAJOR] = HDR_VERSIONMAJOR;
		buf[m_offset + VERSIONMINOR] = HDR_VERSIONMINOR;
		buf[m_offset + PDUTYPE] = DCECommand.REQUEST;
		buf[m_offset + HEADERFLAGS] = (byte) (flags & 0xFF);
		DataPacker.putIntelInt(HDR_PACKEDDATAREP, buf, m_offset + PACKEDDATAREP);
		DataPacker.putIntelInt(0, buf, m_offset + AUTHLEN);
		DataPacker.putIntelInt(callId, buf, m_offset + CALLID);
	}

	/**
	 * Initialize the DCE/RPC reply. Set the SMB transaction parameter count so that the data offset
	 * can be calculated.
	 * 
	 * @param typ byte
	 * @param flags int
	 * @param callId int
	 */
	public final void initializeDCEReply(byte typ, int flags, int callId) {

		// Set the total parameter words
		setParameterCount(10);

		// Set the total parameter/data bytes
		setParameter(0, 0);
		setParameter(1, 0);

		// Set the parameter byte count/offset for this packet
		int bytPos = DCEDataPacker.longwordAlign(getByteOffset());

		setParameter(3, 0);
		setParameter(4, bytPos - RFCNetBIOSProtocol.HEADER_LEN);

		// Set the parameter displacement
		setParameter(5, 0);

		// Set the data byte count/offset for this packet
		setParameter(6, 0);
		setParameter(7, bytPos - RFCNetBIOSProtocol.HEADER_LEN);

		// Set the data displacement
		setParameter(8, 0);

		// Set up word count
		setParameter(9, 0);

		// Reset the DCE offset for a DCE reply
		m_offset = bytPos;

		// Build the DCE/RPC header
		byte[] buf = getBuffer();
		DataPacker.putZeros(buf, m_offset, 24);

		buf[m_offset + VERSIONMAJOR] = HDR_VERSIONMAJOR;
		buf[m_offset + VERSIONMINOR] = HDR_VERSIONMINOR;
		buf[m_offset + PDUTYPE] = typ;
		buf[m_offset + HEADERFLAGS] = (byte) (flags & 0xFF);
		DataPacker.putIntelInt(HDR_PACKEDDATAREP, buf, m_offset + PACKEDDATAREP);
		DataPacker.putIntelInt(0, buf, m_offset + AUTHLEN);
		DataPacker.putIntelInt(callId, buf, m_offset + CALLID);
	}

	/**
	 * Initialize a DCE Bind request
	 * 
	 * @param pipeHandle int
	 * @param maxTx int
	 * @param maxRx int
	 * @param pipeId DCEPipeType
	 * @param callId int
	 */
	public final void initializeDCEBind(int pipeHandle, int maxTx, int maxRx, DCEPipeType pipeId, int callId) {

		// Initialize the DCE header
		initializeDCETransaction(pipeHandle, DCECommand.BIND, DCEPacket.FLG_ONLYFRAG, callId);

		// Add the DCE bind fragment
		DCEBuffer dceBuf = new DCEBuffer(getBuffer(), getDCEDataOffset());
		dceBuf.putShort(maxTx);
		dceBuf.putShort(maxRx);
		dceBuf.putInt(0);

		dceBuf.putByte(1);
		dceBuf.putByte(0);
		dceBuf.putInt(0);
		dceBuf.putByte(1);
		dceBuf.putByte(0);

		// Check for the Netlogon pipe
		if ( pipeId == DCEPipeType.PIPE_NETLOGON)
			dceBuf.putUUID(DCEPipeType.getUUIDForType(DCEPipeType.PIPE_NETLOGON1), true);
		else
			dceBuf.putUUID(DCEPipeType.getUUIDForType(pipeId), true);

		dceBuf.putUUID(DCEPipeType.getUUIDForType(DCEPipeType.PIPE_NETLOGON), true);

		setFragmentLength(BIND_LENGTH);

		// Set the transaction data length
		setParameter(1, BIND_LENGTH);
		setParameter(11, BIND_LENGTH);

		// Set the data length
		int len = (getDCEDataOffset() + BIND_DATALEN) - getByteOffset();
		setByteCount(len);
	}

	/**
	 * Initialize a DCE request packet
	 * 
	 * @param pipeHandle int
	 * @param opCode int
	 * @param buf DCEBuffer
	 * @param maxTx int
	 * @param callId int
	 * @exception DCEBufferException DCE buffer error
	 */
	public final void initializeDCERequest(int pipeHandle, int opCode, DCEBuffer buf, int maxTx, int callId)
		throws DCEBufferException {

		// If we are at the start of the DCE buffered data then set the first fragment flag
		int flags = 0;
		if ( buf.getReadPosition() == 0)
			flags = FLG_FIRSTFRAG;

		// Initialize the DCE header
		initializeDCETransaction(pipeHandle, DCECommand.REQUEST, flags, callId);
		setOperationId(opCode);

		// Copy data from the DCE buffer to the transaction packet
		int len = buf.copyData(getBuffer(), getDCEDataOffset(), maxTx - OPERATIONDATA);
		int fragLen = len + OPERATIONDATA;

		setFragmentLength(fragLen);
		setAllocationHint(buf.getLength());

		// Check if there is any more buffered data, if not then set the last fragment flag
		if ( buf.getAvailableLength() == 0) {
			flags += FLG_LASTFRAG;
			setHeaderFlags(flags);
		}

		// Set the transaction data length
		setParameter(1, fragLen);
		setParameter(11, fragLen);

		// Set the data length
		int bytLen = (getDCEDataOffset() + len) - getByteOffset();
		setByteCount(bytLen);
	}

	/**
	 * Dump the DCE/RPC header details
	 */
	public final void DumpHeader() {

		// Dump the PDU type
		Debug.println("** DCE/RPC Header - PDU Type = " + DCECommand.getCommandString(getPDUType()));
		Debug.println("  Version         : " + getMajorVersion() + "." + getMinorVersion());
		Debug.println("  Flags           : 0x" + getHeaderFlags());
		Debug.println("  Packed Data Rep : 0x" + getPackedDataRepresentation());
		Debug.println("  Fragment Length : " + getFragmentLength());
		Debug.println("  Auth Length     : " + getAuthenticationLength());
		Debug.println("  Call ID         : " + getCallId());
	}
}
