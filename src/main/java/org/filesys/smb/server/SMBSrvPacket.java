/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.smb.server;

import java.io.DataOutputStream;

import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.util.DataPacker;

/**
 * Server SMB Packet Class
 * 
 * @author gkspencer
 */
public class SMBSrvPacket {

	// Default buffer size to allocate for SMB packets
	public static final int DEFAULT_BUFSIZE = 4096;

	// SMB packet signature offset, assuming an RFC NetBIOS transport
	public static final int SIGNATURE 		= RFCNetBIOSProtocol.HEADER_LEN;

	// Packet status flags
	private static final int SMB_ASYNC_QUEUED	= 0x0001;
	private static final int SMB_REQUEST_PKT	= 0x0002;
	private static final int SMB_ENCRYPT        = 0x0004;
	private static final int SMB_NONPOOLEDBUFFER= 0x0008;

	// Packet versions
    public enum Version {
        V1,
        V2,
		V3
    }

	// SMB packet signatures
	private static final byte[] SMB_V1_SIGNATURE = { (byte) 0xFF, 'S', 'M', 'B'};
	private static final byte[] SMB_V2_SIGNATURE = { (byte) 0xFE, 'S', 'M', 'B'};
	private static final byte[] SMB_V3_SIGNATURE = { (byte) 0xFD, 'S', 'M', 'B'};

	// SMB parser factory
	private static ParserFactory _parserFactory = new DefaultParserFactory();

	// SMB packet buffer
	private byte[] m_smbbuf;

	// Received data length (actual buffer used)
	private int m_rxLen;

	// SMB parser
    protected SMBParser m_parser;

	// Current byte area pack/unpack position
	protected int m_pos;
	protected int m_endpos;

	// Associated packet
	private SMBSrvPacket m_assocPkt;

	// Packet status flags and SMB version
	private int m_flags;

	// Count of how many times the processing of this packet has been deferred
	private int m_deferredCount;
	
	// Packet lease time, when allocated from the memory pool
	private long m_leaseTime;

	/**
	 * Default constructor
	 */
	public SMBSrvPacket() {
		m_smbbuf = new byte[DEFAULT_BUFSIZE];
		InitializeBuffer(Version.V1);
	}

	/**
	 * Construct an SMB packet using the specified packet buffer.
	 * 
	 * @param buf SMB packet buffer.
	 */
	public SMBSrvPacket(byte[] buf) {
		m_smbbuf = buf;
	}

	/**
	 * Construct an SMB packet of the specified size.
	 * 
	 * @param siz Size of SMB packet buffer to allocate.
	 */
	public SMBSrvPacket(int siz) {
		m_smbbuf = new byte[siz];
		InitializeBuffer(Version.V1);
	}

	/**
	 * Construct an SMB packet of the specified size.
	 *
	 * @param siz Size of SMB packet buffer to allocate.
	 * @param ver SMB version to initialize
	 */
	public SMBSrvPacket(int siz, Version ver) {
		m_smbbuf = new byte[siz];

		InitializeBuffer(ver);
		setParser( ver);
	}

	/**
	 * Copy constructor.
	 * 
	 * @param pkt SMB packet buffer.
	 */
	public SMBSrvPacket(SMBSrvPacket pkt) {

		// Create a packet buffer of the same size
		m_smbbuf = new byte[pkt.getBuffer().length];

		// Copy the data from the specified packet
		System.arraycopy(pkt.getBuffer(), 0, m_smbbuf, 0, m_smbbuf.length);
	}

	/**
	 * Copy constructor.
	 * 
	 * @param pkt SMB packet buffer.
	 * @param len Length of packet to be copied
	 */
	public SMBSrvPacket(SMBSrvPacket pkt, int len) {

		// Create a packet buffer of the same size
		m_smbbuf = new byte[pkt.getBuffer().length];

		// Copy the data from the specified packet
		System.arraycopy(pkt.getBuffer(), 0, m_smbbuf, 0, len);
	}

    /**
     * Check if the SMB parser is valid
     *
     * @return boolean
     */
    public final boolean hasParser() {
        return m_parser != null ? true : false;
    }

    /**
     * Get the SMB parser
     *
     * @return SMBParser
     */
    public final SMBParser getParser() {

        if ( m_parser == null) {

            // Determine which parser we need for the received packet
			byte sigByte = (byte) (m_smbbuf[SIGNATURE] & 0xFF);

            if ( sigByte == SMB_V1_SIGNATURE[0]) {

                // SMB V1 packet
                setParser( Version.V1);
            }
            else if ( sigByte == SMB_V2_SIGNATURE[0] || sigByte == SMB_V3_SIGNATURE[0]) {

                // SMB V2 packet
                setParser( Version.V2);
            }
        }

        return m_parser;
    }

    /**
     * Set the required SMB parser
     *
     * @param smbVer SMB parser version required
	 * @return SMBParser
     */
    public final SMBParser setParser( Version smbVer) {

        try {
            m_parser = _parserFactory.createParser(smbVer, m_smbbuf, m_rxLen);
        }
        catch ( UnsupportedSMBVersionException ex) {

        }

        return m_parser;
    }

	/**
	 * Return the byte array used for the SMB packet
	 * 
	 * @return Byte array used for the SMB packet.
	 */
	public final byte[] getBuffer() {
		return m_smbbuf;
	}

	/**
	 * Return the total buffer size available to the SMB request
	 * 
	 * @return Total SMB buffer length available.
	 */
	public final int getBufferLength() {
		return m_smbbuf.length - RFCNetBIOSProtocol.HEADER_LEN;
	}

	/**
	 * Get the actual received data length.
	 *
	 * @return int
	 */
	public final int getReceivedLength() {
		return m_rxLen;
	}

	/**
	 * Set the actual received data length.
	 *
	 * @param len int
	 */
	public final void setReceivedLength(int len) {
		m_rxLen = len;
	}

	/**
	 * Return the length of the response
	 *
	 * @return int
	 */
	public final int getLength() {
		if ( hasParser())
			return m_parser.getLength();
		return 0;
	}

	/**
	 * Return the NetBIOS header flags value.
	 *
	 * @return int
	 */
	public final int getHeaderFlags() {
		return m_smbbuf[1] & 0x00FF;
	}

	/**
	 * Return the NetBIOS header data length value.
	 *
	 * @return int
	 */
	public final int getHeaderLength() {
		return DataPacker.getIntelShort(m_smbbuf, 2) & 0xFFFF;
	}

	/**
	 * Return the NetBIOS header message type.
	 *
	 * @return RFCNetBIOSProtocol.MSgType
	 */
	public final RFCNetBIOSProtocol.MsgType getHeaderType() {
		return RFCNetBIOSProtocol.MsgType.fromInt(m_smbbuf[0] & 0x00FF);
	}

	/**
	 * Set the NetBIOS packet header flags value.
	 *
	 * @param flg int
	 */
	public final void setHeaderFlags(int flg) {
		m_smbbuf[1] = (byte) (flg & 0x00FF);
	}

	/**
	 * Set the NetBIOS packet data length in the packet header.
	 *
	 * @param len int
	 */
	public final void setHeaderLength(int len) {
		DataPacker.putIntelShort(len, m_smbbuf, 2);
	}

	/**
	 * Set the NetBIOS packet type in the packet header.
	 *
	 * @param typ RFCNetBIOSProtocol.MsgType
	 */
	public final void setHeaderType(RFCNetBIOSProtocol.MsgType typ) {
		m_smbbuf[0] = (byte) (typ.intValue() & 0x00FF);
	}

	/**
	 *  Check if the packet has a valid SMB header (1, 2 or 3)
	 *
	 * @return boolean
	 */
	public final boolean isSMB() {

		// Should be 0xFF or 0xFE or 0xFD followed by 'SMB'
		byte sig1 = m_smbbuf[SIGNATURE];

		if (sig1 != SMB_V1_SIGNATURE[0] &&
				sig1 != SMB_V2_SIGNATURE[0] &&
				sig1 != SMB_V3_SIGNATURE[0])
			return false;

		for (int idx = 1; idx < SMB_V1_SIGNATURE.length; idx++) {
			if (m_smbbuf[idx + SIGNATURE] != SMB_V1_SIGNATURE[idx])
				return false;
		}

		// Valid SMB packet header
		return true;
	}

	/**
	 * Check if the packet is an SMB1 request/response
	 *
	 * @return boolean
	 */
	public final boolean isSMB1() {

		// Check for the SMB1 signature block
		for ( int idx = 0; idx < SMB_V1_SIGNATURE.length; idx++) {
			if ( m_smbbuf[idx + SIGNATURE] != SMB_V1_SIGNATURE[ idx])
				return false;
		}

		// SMB1 packet
		return true;
	}

	/**
	 * Check if the packet is an SMB2 request/response
	 *
	 * @return boolean
	 */
	public final boolean isSMB2() {

		// Check for the SMB2 signature block
		for ( int idx = 0; idx < SMB_V2_SIGNATURE.length; idx++) {
			if ( m_smbbuf[idx + SIGNATURE] != SMB_V2_SIGNATURE[ idx])
				return false;
		}

		// SMB2 packet
		return true;
	}

	/**
	 * Check if the packet is an encrypted SMB3 request/response
	 *
	 * @return boolean
	 */
	public final boolean isSMB3() {

		// Check for the SMB3 signature block
		for ( int idx = 0; idx < SMB_V3_SIGNATURE.length; idx++) {
			if ( m_smbbuf[idx + SIGNATURE] != SMB_V3_SIGNATURE[ idx])
				return false;
		}

		// SMB3 packet
		return true;
	}

	/**
	 * Initialize the SMB packet buffer
	 *
	 * @param ver SMB version to initialize
	 */
	private final void InitializeBuffer(Version ver) {

		// Set the packet signature
		if ( ver == Version.V1)
			m_smbbuf[SIGNATURE] = SMB_V1_SIGNATURE[0];
		else if ( ver == Version.V2)
			m_smbbuf[SIGNATURE] = SMB_V2_SIGNATURE[0];
		else
			m_smbbuf[SIGNATURE] = SMB_V3_SIGNATURE[0];

		m_smbbuf[SIGNATURE + 1] = (byte) 'S';
		m_smbbuf[SIGNATURE + 2] = (byte) 'M';
		m_smbbuf[SIGNATURE + 3] = (byte) 'B';
	}

	/**
	 * Send the SMB response packet.
	 * 
	 * @param out Output stream associated with the session socket.
	 * @param proto Protocol type, either PROTOCOL_NETBIOS or PROTOCOL_TCPIP
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public final void SendResponseSMB(DataOutputStream out, Protocol proto)
		throws java.io.IOException {

		// Use the packet length
		int siz = getParser().getLength();
		SendResponseSMB(out, proto, siz);
	}

	/**
	 * Send the SMB response packet.
	 * 
	 * @param out Output stream associated with the session socket.
	 * @param proto Protocol type, either PROTOCOL_NETBIOS or PROTOCOL_TCPIP
	 * @param len Packet length
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public final void SendResponseSMB(DataOutputStream out, Protocol proto, int len)
		throws java.io.IOException {

		// Make sure the response flag is set
        getParser().setResponse();

		// NetBIOS SMB protocol
		if ( proto == Protocol.NetBIOS) {

			// Fill in the NetBIOS message header, this is already allocated as
			// part of the users buffer.
			m_smbbuf[0] = (byte) RFCNetBIOSProtocol.MsgType.MESSAGE.intValue();
			m_smbbuf[1] = (byte) 0;

			DataPacker.putShort((short) len, m_smbbuf, 2);
		}
		else {

			// TCP/IP native SMB
			DataPacker.putInt(len, m_smbbuf, 0);
		}

		// Output the data packet
		len += RFCNetBIOSProtocol.HEADER_LEN;
		out.write(m_smbbuf, 0, len);
	}

	/**
	 * Send a success SMB response packet.
	 * 
	 * @param out Output stream associated with the session socket.
	 * @param proto Protocol type, either PROTOCOL_NETBIOS or PROTOCOL_TCPIP
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public final void SendSuccessSMB(DataOutputStream out, Protocol proto)
		throws java.io.IOException {

		// Pack a success response for the SMB version
        m_parser.packSuccessRespone();

		// Send the success response
		SendResponseSMB(out, proto);
	}

	/**
	 * Set the data buffer
	 * 
	 * @param buf byte[]
	 */
	public final void setBuffer(byte[] buf) {
		m_smbbuf = buf;
	}
	
	/**
	 * Check if there is an associated packet
	 * 
	 * @return boolean
	 */
	public final boolean hasAssociatedPacket() {
		return m_assocPkt != null ? true : false;
	}
	
	/**
	 * Return the associated packet
	 * 
	 * @return SMBSrvPacket
	 */
	public final SMBSrvPacket getAssociatedPacket() {
		return m_assocPkt;
	}
	
	/**
	 * Set the associated packet
	 * 
	 * @param smbPkt SMBSrvPacket
	 */
	public final void setAssociatedPacket( SMBSrvPacket smbPkt) {
		m_assocPkt = smbPkt;
	}
	
	/**
	 * Clear the associated packet
	 */
	public final void clearAssociatedPacket() {
		m_assocPkt = null;
	}
	
	/**
	 * Determine if the packet is queued for sending via asynchronous I/O
	 * 
	 * @return boolean
	 */
	public final boolean isQueuedForAsyncIO() {
		return (m_flags & SMB_ASYNC_QUEUED) != 0;
	}
	
	/**
	 * Set/clear the asynchronous I/O flag
	 * 
	 * @param asyncIO boolean
	 */
	public final void setQueuedForAsyncIO( boolean asyncIO) {
		if ( asyncIO)
			m_flags |= SMB_ASYNC_QUEUED;
		else
			m_flags &= ~SMB_ASYNC_QUEUED;
	}

    /**
     * Check if the packet requires encryption
     *
     * @return boolean
     */
    public final boolean requiresEncryption() {
        return (m_flags & SMB_ENCRYPT) != 0;
    }

    /**
     * Set/clear the encryption required flag
     *
     * @param encReq boolean
     */
    public final void setEncryptionRequired(boolean encReq) {
        if ( encReq)
            m_flags |= SMB_ENCRYPT;
        else
            m_flags &= ~SMB_ENCRYPT;
    }

	/**
	 * Check if the packet is using a non-pooled buffer, that does not need to be released back to the memory pool
	 *
	 * @return boolean
	 */
	public final boolean usingNonPooledBuffer() {
		return (m_flags & SMB_NONPOOLEDBUFFER) != 0;
	}

	/**
	 * Set/clear the non-pooled buffer flag
	 *
	 * @param nonPooled boolean
	 */
	public final void setUsingNonPooledBuffer(boolean nonPooled) {
		if ( nonPooled)
			m_flags |= SMB_NONPOOLEDBUFFER;
		else
			m_flags &= ~SMB_NONPOOLEDBUFFER;
	}

	/**
	 * Clear the packet header
	 */
	public final void clearHeader() {
		for (int i = SIGNATURE; i < (SIGNATURE + SMBV1.HeaderLength); m_smbbuf[ i++] = 0);
		InitializeBuffer(Version.V1);
	}
	
	/**
	 * Return the deferred processing count for this packet
	 * 
	 * @return int
	 */
	public final int getDeferredCount() {
		return m_deferredCount;
	}
	
	/**
	 * Increment the deferred processing count for this packet
	 */
	public final void incrementDeferredCount() {
		m_deferredCount++;
	}
	
	/**
	 * Check if the packet has a lease
	 * 
	 * @return boolean
	 */
	public final boolean hasLeaseTime() {
		return m_leaseTime != 0 ? true : false;
	}
	
	/**
	 * Return the packet lease time
	 * 
	 * @return long
	 */
	public final long getLeaseTime() {
		return m_leaseTime;
	}
	
	/**
	 * Clear the lease time
	 */
	public final void clearLeaseTime() {
		m_leaseTime = 0L;
	}
	
	/**
	 * Set the packet lease time
	 * 
	 * @param tmo long
	 */
	public final void setLeaseTime( long tmo) {
		m_leaseTime = tmo;
	}

	/**
	 * Return the parser factory
	 *
	 * @return ParserFactory
	 */
	public static final ParserFactory getParserFactory() {
		return _parserFactory;
	}

    /**
     * Set the parser factory
     *
     * @param parserFactory ParserFactory
     */
    public static final void setParserFactory(ParserFactory parserFactory) {
        _parserFactory = parserFactory;
    }

	/**
	 * Return the SMB packet details as a string
	 * 
	 * @return String
	 */
	public String toString() {
		StringBuilder str = new StringBuilder();

		str.append( "[SMBPkt ");

		if ( hasParser())
    		str.append( getParser().getName());
		else
		    str.append( "<NoParser>");

		if ( usingNonPooledBuffer())
			str.append(", NonPooled");

        str.append("]");

		return str.toString();
	}
}
