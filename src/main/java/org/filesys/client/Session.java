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
import java.security.*;

import org.filesys.debug.Debug;
import org.filesys.netbios.NetworkSession;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.smb.*;
import org.filesys.smb.PacketTypeV1;
import org.filesys.util.DataPacker;

/**
 *  SMB Session Class
 * 
 * <p>Base class for sessions connected to remote disk, print, named pipe and administration named pipe shares.
 * 
 * @author gkspencer
 */
public class Session {

	//	Session security mode
	
	public static final int SecurityModeUser	= 1;
	public static final int SecurityModeShare	= 2;
	
	//	Tree identifier that indicates that the disk session has been closed

	protected final static int Closed = -1;

	//	Debug flags

	public final static int DBGPacketType 	= 0x0001;
	public final static int DBGDumpPacket 	= 0x0002;
	public final static int DBGHexDump 		= 0x0004;
  	public final static int DBGSigning		= 0x0008;

	//	Default SMB packet size to allocate

  	public static final int DEFAULT_BUFSIZE = 4096;

  	//  Multiplex id to indicate the session is not in a transaction
  
  	public static final int NO_TRANSACTION  = -1;
  
	//	SMB dialect id and string for this session

  	private int m_dialect;
  	private String m_diaStr;

	//	Network session

  	private NetworkSession m_netSession;

	//	SMB packet for protocol exhanges

  	protected SMBPacket m_pkt;

	//	Default packet flags
	
	private int m_defFlags 	= SMBPacket.FLG_CASELESS;
	private int m_defFlags2 = SMBPacket.FLG2_LONGFILENAMES;
	
	//	Server connection details

	private PCShare m_remoteShr;

	//	Domain name

	private String m_domain;

	//	Remote operating system and LAN manager type

	private String m_srvOS;
  	private String m_srvLM;

	//	Security mode (user or share)
	
	private int m_secMode;
	
	//	Challenge encryption key

  	private byte[] m_encryptKey;

	//	SMB session information

  	private int m_sessIdx;
  	private int m_userId;
  	private int m_processId;

	//	Tree identifier for this connection

  	protected int m_treeid;

	//	Device type that this session is connected to

  	private int m_devtype;

	//	Maximum transmit buffer size allowed

  	private int m_maxPktSize;

	//	Session capabilities

  	private int m_sessCaps;

	//	Maximum virtual circuits allowed on this session, and maximum multiplxed read/writes
	
	private int m_maxVCs;
	private int m_maxMPX;
	
	//	Indicate if the session was created as a guest rather than using the supplied username/password
	
	private boolean m_guest;
	
	//	SMB signing support
	//
	//	Session key, packet sequence number and MD5 digest
	
	private byte[] m_sessionKey;
	private int m_seqNo;
	private MessageDigest m_md5;
	
	private long m_lastTxSig;
	
	//  Multiplex id of an active transaction
	//
	//  A transaction may span multiple requests/responses, when signing is enabled the sequence number is not
	//  incremented for a transaction that spans multiple packets
	//
	//  -1 indicates that there is no currently active transaction
  
	private int m_transMID = NO_TRANSACTION;
  
	//	Global session id

	private static int m_sessionIdx = 1;

	//	Multiplex id
	
	private static int m_multiplexId = 1;
	
	//	Debug support

	private static int m_debug = 0;
  
	/**
	 * Construct an SMB session
	 * 
	 * @param shr Remote server details.
	 * @param dialect SMB dialect for this session.
	 * @param pkt SMB packet
	 */
	protected Session(PCShare shr, int dialect, SMBPacket pkt) {

		// Set the SMB dialect for this session

		m_dialect = dialect;

		// Save the remote share details

		m_remoteShr = shr;

		// Allocate a unique session index

		m_sessIdx = getNextSessionId();

		// Allocate an SMB protocol packet

		m_pkt = pkt;
		if ( pkt == null)
			m_pkt = new SMBPacket(DEFAULT_BUFSIZE);
	}

	/**
	 * Allocate an SMB packet for this session. The preferred packet size is specified, if a smaller
	 * buffer size has been negotiated a smaller SMB packet will be returned.
	 * 
	 * @param pref Preferred SMB packet size
	 * @return Allocated SMB packet
	 */
	protected final SMBPacket allocatePacket(int pref) {

		// Check if the preferred size is larger than the maximum allowed packet
		// size for this session.

		if ( pref > m_maxPktSize)
			return new SMBPacket(m_maxPktSize + RFCNetBIOSProtocol.HEADER_LEN);

		// Return the preferred SMB packet size

		return new SMBPacket(pref + RFCNetBIOSProtocol.HEADER_LEN);
	}

	/**
	 * Determine if the session supports raw mode read/writes
	 * 
	 * @return true if this session supports raw mode, else false
	 */
	public final boolean supportsRawMode() {
		return (m_sessCaps & Capability.V1RawMode) != 0 ? true : false;
	}

	/**
	 * Determine if the session supports Unicode
	 * 
	 * @return boolean
	 */
	public final boolean supportsUnicode() {
		return (m_sessCaps & Capability.V1Unicode) != 0 ? true : false;
	}

	/**
	 * Determine if the session supports large files (ie. 64 bit file offsets)
	 * 
	 * @return boolean
	 */
	public final boolean supportsLargeFiles() {
		return (m_sessCaps & Capability.V1LargeFiles) != 0 ? true : false;
	}

	/**
	 * Determine if the session supports NT specific SMBs
	 * 
	 * @return boolean
	 */
	public final boolean supportsNTSmbs() {
		return (m_sessCaps & Capability.V1NTSMBs) != 0 ? true : false;
	}

	/**
	 * Determine if the session supports RPC API requests
	 * 
	 * @return boolean
	 */
	public final boolean supportsRPCAPIs() {
		return (m_sessCaps & Capability.V1RemoteAPIs) != 0 ? true : false;
	}

	/**
	 * Determine if the session supports NT status codes
	 * 
	 * @return boolean
	 */
	public final boolean supportsNTStatusCodes() {
		return (m_sessCaps & Capability.V1NTStatus) != 0 ? true : false;
	}

	/**
	 * Determine if the session supports level 2 oplocks
	 * 
	 * @return boolean
	 */
	public final boolean supportsLevel2Oplocks() {
		return (m_sessCaps & Capability.V1Level2Oplocks) != 0 ? true : false;
	}

	/**
	 * Determine if the session supports lock and read
	 * 
	 * @return boolean
	 */
	public final boolean supportsLockAndRead() {
		return (m_sessCaps & Capability.V1LockAndRead) != 0 ? true : false;
	}

	/**
	 * Determine if the session supports NT find
	 * 
	 * @return boolean
	 */
	public final boolean supportsNTFind() {
		return (m_sessCaps & Capability.V1NTFind) != 0 ? true : false;
	}

	/**
	 * Close this connection with the remote server.
	 * 
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public void CloseSession()
		throws java.io.IOException, SMBException {

		// If the NetBIOS session is valid then hangup the session

		if ( isActive()) {

			// Close the network session

			m_netSession.Close();

			// Clear the session

			m_netSession = null;
		}
	}

	/**
	 * Return the default flags settings for this session
	 * 
	 * @return int
	 */
	public final int getDefaultFlags() {
		return m_defFlags;
	}

	/**
	 * Return the default flags2 settings for this session
	 * 
	 * @return int
	 */
	public final int getDefaultFlags2() {
		return m_defFlags2;
	}

	/**
	 * Get the device type that this session is connected to.
	 * 
	 * @return Device type for this session.
	 */
	public final int getDeviceType() {
		return m_devtype;
	}

	/**
	 * Get the SMB dialect property
	 * 
	 * @return SMB dialect that this session has negotiated.
	 */
	public final int getDialect() {
		return m_dialect;
	}

	/**
	 * Get the SMB dialect string
	 * 
	 * @return SMB dialect string for this session.
	 */
	public final String getDialectString() {
		return m_diaStr;
	}

	/**
	 * Get the servers primary domain name
	 * 
	 * @return Servers primary domain name, if known, else null.
	 */
	public final String getDomain() {
		return m_domain;
	}

	/**
	 * Determine if there is a challenge encryption key
	 * 
	 * @return boolean
	 */
	public final boolean hasEncryptionKey() {
		return m_encryptKey != null ? true : false;
	}

	/**
	 * Return the cahllenge encryption key
	 * 
	 * @return byte[]
	 */
	public final byte[] getEncryptionKey() {
		return m_encryptKey;
	}

	/**
	 * Get the servers LAN manager type
	 * 
	 * @return Servers LAN manager type, if known, else null.
	 */
	public final String getLANManagerType() {
		return m_srvLM;
	}

	/**
	 * Get the maximum number of multiplxed requests that are allowed
	 * 
	 * @return int
	 */
	public final int getMaximumMultiplexedRequests() {
		return m_maxMPX;
	}

	/**
	 * Get the maximum packet size allowed for this session
	 * 
	 * @return Maximum packet size, in bytes.
	 */
	public final int getMaximumPacketSize() {
		return m_maxPktSize;
	}

	/**
	 * Get the maximum virtual circuits allowed on this session
	 * 
	 * @return int
	 */
	public final int getMaximumVirtualCircuits() {
		return m_maxVCs;
	}

	/**
	 * Get the next multiplex id to uniquely identify a transaction
	 * 
	 * @return Unique multiplex id for a transaction
	 */
	public final synchronized int getNextMultiplexId() {
		return m_multiplexId++;
	}

	/**
	 * Get the next session id
	 * 
	 * @return int
	 */
	protected final synchronized int getNextSessionId() {
		return m_sessionIdx++;
	}

	/**
	 * Get the servers operating system type
	 * 
	 * @return Servers operating system, if known, else null.
	 */
	public final String getOperatingSystem() {
		return m_srvOS;
	}

	/**
	 * Get the remote share password string
	 * 
	 * @return Remote share password string
	 */
	public final String getPassword() {
		return m_remoteShr.getPassword();
	}

	/**
	 * Get the remote share details for this session
	 * 
	 * @return PCShare information for this session
	 */
	public final PCShare getPCShare() {
		return m_remoteShr;
	}

	/**
	 * Return the security mode of the session (user or share)
	 * 
	 * @return int
	 */
	public final int getSecurityMode() {
		return m_secMode;
	}

	/**
	 * Get the remote server name
	 * 
	 * @return Remote server name
	 */
	public final String getServer() {
		return m_remoteShr.getNodeName();
	}

	/**
	 * Access the associated network session
	 * 
	 * @return NetworkSession that the SMB session is using
	 */
	public final NetworkSession getSession() {
		return m_netSession;
	}

	/**
	 * Return the session capability flags.
	 * 
	 * @return int
	 */
	public final int getCapabilities() {
		return m_sessCaps;
	}

	/**
	 * Get the process id for this session
	 * 
	 * @return int
	 */
	public final int getProcessId() {
		return m_processId;
	}

	/**
	 * Get the session identifier property
	 * 
	 * @return Session identifier
	 */
	public final int getSessionId() {
		return m_sessIdx;
	}

	/**
	 * Get the remote share name
	 * 
	 * @return Remote share name string
	 */
	public final String getShareName() {
		return m_remoteShr.getShareName();
	}

	/**
	 * Get the connected tree identifier.
	 * 
	 * @return Tree identifier.
	 */
	public final int getTreeId() {
		return m_treeid;
	}

	/**
	 * Return the assigned use id for this SMB session
	 * 
	 * @return Assigned user id
	 */
	public final int getUserId() {
		return m_userId;
	}

	/**
	 * Get the remote share user name string
	 * 
	 * @return Remote share user name string
	 */
	public final String getUserName() {
		return m_remoteShr.getUserName();
	}

	/**
	 * Check if there is data available in the network receive buffer
	 * 
	 * @return boolean
	 * @exception IOException Socket error
	 */
	public final boolean hasDataAvailable()
		throws IOException {
		return m_netSession.hasData();
	}

	/**
	 * Determine if the specified debugging option is enabled
	 * 
	 * @param opt Debug option bit mask
	 * @return true if the debug option is enabled, else false
	 */
	public static boolean hasDebugOption(int opt) {
		if ( m_debug == 0)
			return false;
		if ( (m_debug & opt) != 0)
			return true;
		return false;
	}

	/**
	 * Check if SMB signing is enabled on this session
	 * 
	 * @return boolean
	 */
	public final boolean hasSMBSigning() {
		return m_sessionKey != null;
	}

	/**
	 * Determine if the session is valid, ie. still open.
	 * 
	 * @return true if the session is still active, else false.
	 */
	public final boolean isActive() {
		return (m_netSession == null) ? false : true;
	}

	/**
	 * Determine if SMB session debugging is enabled
	 * 
	 * @return true if debugging is enabled, else false.
	 */
	public static boolean hasDebug() {
		return m_debug != 0 ? true : false;
	}

	/**
	 * Determine if the session has been created as a guest logon
	 * 
	 * @return boolean
	 */
	public final boolean isGuest() {
		return m_guest;
	}

	/**
	 * Determine if the Unicode flag is enabled
	 * 
	 * @return boolean
	 */
	public final boolean isUnicode() {
		return (m_defFlags2 & SMBPacket.FLG2_UNICODE) != 0 ? true : false;
	}

	/**
	 * Send a single echo request to the server
	 *
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void pingServer()
		throws java.io.IOException, SMBException {

		// Send a single echo request to the server

		pingServer(1);
	}

	/**
	 * Send an echo request to the server
	 * 
	 * @param cnt Number of packets to echo from the remote server
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException SMB error occurred
	 */
	public final void pingServer(int cnt)
		throws java.io.IOException, SMBException {

		// Build a server ping SMB packet

		m_pkt.setCommand(PacketTypeV1.Echo);
		m_pkt.setFlags(0);
		m_pkt.setTreeId(getTreeId());
		m_pkt.setUserId(getUserId());
		m_pkt.setProcessId(getProcessId());
		m_pkt.setMultiplexId(1);

		// Set the parameter words

		m_pkt.setParameterCount(1);
		m_pkt.setParameter(0, cnt); // number of packets that the server should return
		String echoStr = "ECHO";
		m_pkt.setBytes(echoStr.getBytes());

		// Send the echo request

		m_pkt.SendSMB(this);

		// Receive the reply packets, if any

		while (cnt > 0) {

			// Receive a reply packet

			m_pkt.ReceiveSMB(this);

			// Decrement the reply counter

			cnt--;
		}
	}

	/**
	 * Enable/disable SMB session debugging
	 * 
	 * @param dbg Bit mask of debug options to enable, or zero to disable
	 */
	public static void setDebug(int dbg) {
		m_debug = dbg;
	}

	/**
	 * Set the default SMB packet flags for this session
	 * 
	 * @param flg int
	 */
	protected final void setDefaultFlags(int flg) {
		m_defFlags = flg;
	}

	/**
	 * Set the SMB packet default flags2 for this session
	 * 
	 * @param flg2 int
	 */
	protected final void setDefaultFlags2(int flg2) {
		m_defFlags2 = flg2;
	}

	/**
	 * Set the device type for this session.
	 * 
	 * @param dev Device type for this session.
	 */
	protected final void setDeviceType(int dev) {
		m_devtype = dev;
	}

	/**
	 * Set the dialect for this session
	 * 
	 * @param dia SMB dialect that this session is using.
	 */
	protected final void setDialect(int dia) {
		m_dialect = dia;
	}

	/**
	 * Set the dialect string for this session
	 * 
	 * @param dia SMB dialect string
	 */
	protected final void setDialectString(String dia) {
		m_diaStr = dia;
	}

	/**
	 * Set the remote servers primary domain name
	 * 
	 * @param dom Servers primary domain name.
	 */
	protected final void setDomain(String dom) {
		m_domain = dom;
	}

	/**
	 * Set the encryption key
	 * 
	 * @param key byte[]
	 */
	public final void setEncryptionKey(byte[] key) {

		// Set the challenge response encryption key

		m_encryptKey = key;
	}

	/**
	 * Set the guest status for the session
	 * 
	 * @param sts boolean
	 */
	protected final void setGuest(boolean sts) {
		m_guest = sts;
	}

	/**
	 * Set the remote servers LAN manager type
	 * 
	 * @param lm Servers LAN manager type string.
	 */
	protected final void setLANManagerType(String lm) {
		m_srvLM = lm;
	}

	/**
	 * Set the maximum number of multiplexed requests allowed
	 * 
	 * @param maxMulti int
	 */
	protected final void setMaximumMultiplexedRequests(int maxMulti) {
		m_maxMPX = maxMulti;
	}

	/**
	 * Set the maximum packet size allowed on this session
	 * 
	 * @param siz Maximum allowed packet size.
	 */
	protected final void setMaximumPacketSize(int siz) {
		m_maxPktSize = siz;
	}

	/**
	 * Set the maximum number of virtual circuits allowed on this session
	 * 
	 * @param maxVC int
	 */
	protected final void setMaximumVirtualCircuits(int maxVC) {
		m_maxVCs = maxVC;
	}

	/**
	 * Set the remote servers operating system type
	 * 
	 * @param os Servers operating system type string.
	 */
	protected final void setOperatingSystem(String os) {
		m_srvOS = os;
	}

	/**
	 * Set the remote share password
	 * 
	 * @param pwd Remtoe share password string.
	 */
	protected final void setPassword(String pwd) {
		m_remoteShr.setPassword(pwd);
	}

	/**
	 * Set the session security mode (user or share)
	 * 
	 * @param secMode int
	 */
	public final void setSecurityMode(int secMode) {
		m_secMode = secMode;
	}

	/**
	 * Set the remote server name
	 * 
	 * @param srv Server name string
	 */
	protected final void setServer(String srv) {
		m_remoteShr.setNodeName(srv);
	}

	/**
	 * Set the network session that this SMB session is associated with
	 * 
	 * @param netSess Network session that this SMB session is to be associated with.
	 */
	protected final void setSession(NetworkSession netSess) {
		m_netSession = netSess;
	}

	/**
	 * Set the session capability flags
	 * 
	 * @param caps Capability flags.
	 */
	protected final void setCapabilities(int caps) {
		m_sessCaps = caps;
	}

	/**
	 * Set the remote share name
	 * 
	 * @param shr Remote share name string
	 */
	protected final void setShareName(String shr) {
		m_remoteShr.setShareName(shr);
	}

	/**
	 * Set the process id for this session
	 * 
	 * @param id int
	 */
	public final void setProcessId(int id) {
		m_processId = id;
	}

	/**
	 * Set the connected tree identifier for this session.
	 * 
	 * @param id Tree identifier for this session.
	 */
	protected final void setTreeId(int id) {
		m_treeid = id;
	}

	/**
	 * Set the user identifier for this session
	 * 
	 * @param uid User identifier
	 */
	protected final void setUserId(int uid) {
		m_userId = uid;
	}

	/**
	 * Set the remote share user name
	 * 
	 * @param user Remote share user name string
	 */
	protected final void setUserName(String user) {
		m_remoteShr.setUserName(user);
	}

	/**
	 * Process an asynchronous packet
	 * 
	 * @param pkt SMBPacket
	 */
	protected void processAsynchResponse(SMBPacket pkt) {

		// Default is to ignore the packet
		//
		// This method is overridden by SMB dialects that can generate asynchronous responses

		if ( Debug.EnableInfo && hasDebug())
			Debug.println("++ Asynchronous response received, command = 0x" + pkt.getCommand());
	}

	/**
	 * Enable SMB signing for this session
	 * 
	 * @param sessKey byte[]
	 * @exception NoSuchAlgorithmException If the MD5 message digest is not available
	 */
	protected final void enableSMBSigning(byte[] sessKey)
		throws NoSuchAlgorithmException {

		// Save the session key

		m_sessionKey = sessKey;

		// Set the starting sequence number

		m_seqNo = 0;

		// Allocate the MD5 message digest for calculating the SMB signatures

		m_md5 = MessageDigest.getInstance("MD5");
	}

	/**
	 * Disable SMB signing for this session
	 */
	protected final void disableSMBSigning() {

		// Clear the session key and message digest to disable signing

		m_sessionKey = null;
		m_md5 = null;
	}

	/**
	 * Add an SMB signature to an outgoing SMB request
	 * 
	 * @param pkt SMBPacket
	 */
	protected final void signTxPacket(SMBPacket pkt) {

		// Replace the signature with the sequence number

		int seqNo = m_seqNo;
		pkt.setSignature(seqNo);

		// Clear the status code area

		pkt.setLongErrorCode(0);

		// Update the sequence number if not in a transaction

		if ( hasActiveTransaction() == false)
			m_seqNo++;

		// Calculate the signature value

		m_md5.update(m_sessionKey);
		m_md5.update(pkt.getBuffer(), 4, pkt.getLength());

		byte[] sigByts = m_md5.digest();
		m_lastTxSig = DataPacker.getIntelLong(sigByts, 0);

		// Set the SMB signature for the request

		pkt.setSignature(m_lastTxSig);

		// DEBUG

		if ( Debug.EnableInfo && hasDebugOption(DBGSigning)) {
			Debug.println("Sign request " + PacketTypeV1.getCommandName(pkt.getCommand()) + ", seq=" + seqNo + ", signature = 0x"
					+ Long.toHexString(m_lastTxSig));
			Debug.println("Send length = " + pkt.getLength());
		}
	}

	/**
	 * Verify the SMB signature on an incoming SMB response
	 * 
	 * @param pkt SMBPacket
	 * @exception SMBException If the received packet SMB signature is not valid
	 */
	protected final void verifyRxPacket(SMBPacket pkt)
		throws SMBException {

		// Get the signature value

		long rxSig = pkt.getSignature();

		// Update the sequence number if not in a transaction

		int seqNo = m_seqNo;

		if ( hasActiveTransaction() == false) {
			m_seqNo++;
		}
		else {

			// Check if the response is valid

			if ( pkt.isValidResponse())
				seqNo = m_seqNo + 1;
			else {

				// DEBUG

				if ( Debug.EnableInfo && hasDebugOption(DBGSigning))
					Debug.println("Transaction error returned, signature NOT checked");

				// Do not check the signature

				return;
			}
		}

		// Replace the signature with the sequence number

		pkt.setSignature(seqNo);

		// Calculate the signature value

		m_md5.update(m_sessionKey);
		m_md5.update(pkt.getBuffer(), 4, pkt.getLength());

		byte[] sigByts = m_md5.digest();
		long calcSig = DataPacker.getIntelLong(sigByts, 0);

		// DEBUG

		if ( Debug.EnableInfo && hasDebugOption(DBGSigning)) {
			Debug.println("Verify sign " + PacketTypeV1.getCommandName(pkt.getCommand()) + ", seq=" + seqNo + ", signature = 0x"
					+ Long.toHexString(rxSig) + ", calc=" + Long.toHexString(calcSig));
			Debug.println("Received length = " + pkt.getLength());
			if ( calcSig != rxSig)
				Debug.println("#### SMB signature mismatch ####");
		}

		// Check if the signature is valid

		if ( calcSig != rxSig) {

			// Check if the received signature is the same as the one sent in the request, if so it
			// looks like
			// signing is not enabled

			if ( rxSig == m_lastTxSig) {

				// Disable signing

				disableSMBSigning();

				// DEBUG

				if ( Debug.EnableInfo && hasDebugOption(DBGSigning))
					Debug.println("Received signature equals sent, disabling signing");
			}
			else {

				// Bad signature received

				throw new SMBException(SMBStatus.InternalErr, SMBStatus.IntInvalidSMBSignature);
			}
		}
	}

	/**
	 * Set the multiplex id of an active transaction
	 * 
	 * @param mid int
	 */
	public final void setTransactionMID(int mid) {
		m_transMID = mid;

		// Bump the sequence number at the end of a transaction

		if ( mid == NO_TRANSACTION)
			m_seqNo += 2;
	}

	/**
	 * Determine if there is an active transaction
	 * 
	 * @return boolean
	 */
	public final boolean hasActiveTransaction() {
		return m_transMID != -1 ? true : false;
	}

	/**
	 * Get the SMB signing sequence number
	 * 
	 * @return int
	 */
	public final int getSMBSequence() {
		return m_seqNo;
	}

	/**
	 * Set the SMB signing sequence number
	 * 
	 * @param seq int
	 */
	public final void setSMBSequence(int seq) {
		m_seqNo = seq;
	}

	/**
	 * Output the session details as a string
	 * 
	 * @return Session details string
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();

		str.append("[\\\\");
		str.append(getServer());
		str.append("\\");
		str.append(getShareName());
		str.append(":");
		str.append(Dialect.DialectTypeString(m_dialect));
		str.append(",UserId=");
		str.append(getUserId());
		str.append("]");

		return str.toString();
	}
}