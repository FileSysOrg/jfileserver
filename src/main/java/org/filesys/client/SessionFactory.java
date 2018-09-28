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

import java.net.*;
import java.security.*;
import java.io.*;

import org.filesys.client.info.ServerList;
import org.filesys.client.admin.AdminSession;
import org.filesys.client.info.FileInfo;
import org.filesys.client.info.RAPServerInfo;
import org.filesys.debug.Debug;
import org.filesys.netbios.NetBIOSName;
import org.filesys.netbios.NetBIOSNameList;
import org.filesys.netbios.NetBIOSSession;
import org.filesys.netbios.NetworkSession;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.server.filesys.AccessMode;
import org.filesys.server.filesys.FileAction;
import org.filesys.smb.*;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.dcerpc.info.WorkstationInfo;
import org.filesys.util.DataPacker;
import org.filesys.util.HexDump;
import org.filesys.util.IPAddress;
import org.filesys.util.StringList;

/**
 *  <p>The SessionFactory static class is used to create sessions to remote shared
 *  resources using the SMB/CIFS protocol. A PCShare object is used to specify the
 *  remote node and share details, as well as required access control details.
 *
 *  <p>The OpenDisk () method opens a session to a remote disk share. The returned
 *  session object provides disk specific methods such as opening remote files, file
 *  and directory operations such as deleting files, renaming files etc. The disk
 *  session may also be used to start directory searches to list files/directories
 *  in a particular remote path.
 *
 * <p>The OpenPrinter () method opens a session to a remote printer share. The returned
 *  session object provides print spooling functionality. To perform remote printer admin
 *  functions use the OpenAdminSession () method to create an admin session to the remote node.
 *
 * <p>The OpenPipe () method opens a session to a remote named pipe share.
 *
 * <p>The OpenAdminSession () method creates a session that is connected to the remote IPC$
 * share, that is used for admin related functions, such as listing the available shares on
 * the remote node, listing print queues and manipulating jobs in the remote printer queues.
 *
 * <p>An AdminSession can also be used to access various DCE/RPC services for remote registry,
 * remote eventlog, service manager and server/workstation functions.
 * 
 * @author gkspencer
 */
public final class SessionFactory {

	//	Constants
	
	private static final int BROADCAST_LOOKUP_TIMEOUT		= 4000;	// ms
	
	//  Session index, used to make each session request call id unique

	private static int m_sessIdx = 1;

	//	Local domain name, if known

	private static String m_localDomain = null;

	//	Local domains browse master, if known

	private static String m_localBrowseMaster = null;

	// 	Default session packet buffer size

	private static int m_defPktSize = 4096 + RFCNetBIOSProtocol.HEADER_LEN;

	//	List of local TCP/IP addresses
  
	private static InetAddress[] m_localAddrList;
  
	//	Password encryptor
  
	private static PasswordEncryptor m_encryptor;
  
	//	Flag to indicate if SMB signing is enabled, and if received packets are checked when signing is enabled.
  
	private static boolean m_smbSigningEnabled = true;
	private static boolean m_smbSigningCheckRx = true;
  
	//  Default user name, password and domain used by methods that create their own connections.

	private static String m_defUserName = "";
	private static String m_defPassword = "";
	private static String m_defDomain = "?";

	//  Default session settings
  
	private static SessionSettings m_defaultSettings;
  
	//  Session factory debug flag

	private static boolean m_debug = false;

	//  Flag to indicate if the local node details have been checked. A check is made on the local
	//  node to get the local domain name, and other workstation details.

	private static boolean m_localChecked = false;

	//  Use a global process id, so that all sessions share locks
  
	private static boolean m_globalPID = false;
  
	static {

		// Use the JCE based password encryptor if available, else use the BouncyCastle API based
		// encryptor

		try {

			// Load the JCE based password encryptor

			Object pwdEncObj = Class.forName("org.filesys.client.JCEPasswordEncryptor").newInstance();
			if ( pwdEncObj != null)
				m_encryptor = (PasswordEncryptor) pwdEncObj;
		}
		catch (Exception ex) {
		}

		// Check if the password encryptor has been set

		if ( m_encryptor == null) {
			try {
				Object pwdEncObj = Class.forName("org.filesys.client.j2me.J2MEPasswordEncryptor").newInstance();
				if ( pwdEncObj != null)
					m_encryptor = (PasswordEncryptor) pwdEncObj;
			}
			catch (Exception ex) {
			}
		}

		// Initialize the default session settings

		m_defaultSettings = new SessionSettings(Protocol.TCPNetBIOS, Protocol.NativeSMB);

		// Initialize the default dialect list

		SetupDefaultDialects();
	}

	/**
	 * Build an SMB negotiate dialect packet.
	 * 
	 * @param pkt SMBPacket to build the negotiate request
	 * @param dlct SMB dialects to negotiate
	 * @param pid Process id to be used by this new session
	 * @return StringList
	 */

	private final static StringList BuildNegotiatePacket(SMBPacket pkt, DialectSelector dlct, int pid) {

		// Initialize the SMB packet header fields

		pkt.setCommand(PacketTypeV1.Negotiate);
		pkt.setProcessId(pid);

		// If the NT dialect is enabled set the Unicode flag in the request flags

		int flags2 = 0;

		if ( dlct.hasDialect(Dialect.NT))
			flags2 += SMBPacket.FLG2_UNICODE;

		if ( isSMBSigningEnabled())
			flags2 += SMBPacket.FLG2_SECURITYSIG;

		pkt.setFlags2(flags2);

		// Build the SMB dialect list

		StringBuffer dia = new StringBuffer();
		StringList vec = new StringList();

		// Loop through all SMB dialect types and add the appropriate dialect strings
		// to the negotiate packet.

		int d = Dialect.Core;

		while (d < Dialect.Max) {

			// Check if the current dialect is selected

			if ( dlct.hasDialect(d)) {

				// Search the SMB dialect type string list and add all strings for the
				// current dialect

				for (int i = 0; i < Dialect.NumberOfDialects(); i++) {

					// Check if the current dialect string should be added to the list

					if ( Dialect.DialectType(i) == d) {

						// Get the current SMB dialect string

						String curDialect = Dialect.DialectString(i);
						vec.addString(curDialect);

						// Add the current SMB dialect type string to the negotiate packet

						dia.append(DataType.Dialect);
						dia.append(curDialect);
						dia.append((char) 0x00);
					}
				}
			}

			// Update the current dialect type

			d++;
		}

		// Copy the dialect strings to the SMB packet

		pkt.setBytes(dia.toString().getBytes());

		// Return the dialect strings

		return vec;
	}

	/**
	 * Get the local nodes workstation details, if it is running an SMB server.
	 */
	private static void CheckLocalNode() {

		try {

			// Make sure the local node checked flag is set, or we are going to loop

			m_localChecked = true;

			// Connect to the local node

			PCShare shr = new PCShare(InetAddress.getLocalHost().getHostName().toUpperCase(), "IPC$", getDefaultUserName(),
					getDefaultPassword());
			AdminSession admSess = OpenAdminSession(shr);

			// Get the local workstation details

			WorkstationInfo wrkInfo = admSess.getWorkstationInfo();
			if ( wrkInfo != null) {

				// Set the local domain/workgroup name

				m_localDomain = wrkInfo.getDomain();
			}

			// Close the admin session

			admSess.CloseSession();
		}
		catch (Exception ex) {
		}
	}

	/**
	 * Connect to a remote device.
	 * 
	 * @param shr Remote device details
	 * @param sess SMB session.
	 * @param devtyp Device type to connect to.
	 * @return Tree identifier if successful, else -1.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception org.filesys.smb.UnsupportedDeviceTypeException If the device type is not
	 *                supported by this SMB dialect
	 * @exception SMBException If an SMB error occurs
	 */
	private final static int ConnectDevice(PCShare shr, Session sess, int devtyp)
		throws java.io.IOException, UnsupportedDeviceTypeException, SMBException {

		// DEBUG

		if ( Debug.EnableInfo && hasSessionDebug())
			Debug.println("** Connecting to " + shr.getNodeName() + " " + shr.getShareName() + " (" + shr.getUserName()
					+ "/********)");

		// Create a tree connect packet

		SMBPacket pkt = new SMBPacket();

		pkt.setProcessId(sess.getProcessId());
		pkt.setFlags(sess.getDefaultFlags());
		pkt.setFlags2(sess.getDefaultFlags2());

		// Set the user id

		pkt.setUserId(sess.getUserId());

		// Determine if Unicode strings should be used, if so then use the TreeConnectAndX SMB

		if ( pkt.isUnicode()) {

			// Use the TreeConnectAndX SMB request

			pkt.setCommand(PacketTypeV1.TreeConnectAndX);

			// Set the parameter words

			pkt.setParameterCount(4);
			pkt.setAndXCommand(0xFF);
			pkt.setParameter(1, 0); // offset to next command
			pkt.setParameter(2, 0); // flags
			pkt.setParameter(3, 1); // password length, just count the null

			// Pack the password and share details

			pkt.resetBytePointer();
			pkt.packByte(0);

			StringBuffer uncPath = new StringBuffer();
			uncPath.append("\\\\");
			uncPath.append(shr.getNodeName());
			uncPath.append("\\");
			uncPath.append(shr.getShareName());

			pkt.packString(uncPath.toString(), true);

			switch (devtyp) {

				// Disk device

				case SMBDeviceType.Disk:
					pkt.packString("A:", false);
					break;

				// Printer device

				case SMBDeviceType.Printer:
					pkt.packString("LPT1:", false);
					break;

				// Pipe device

				case SMBDeviceType.Pipe:
					pkt.packString("IPC", false);
					break;
			}

			// Set the byte count for the request

			pkt.setByteCount();
		}
		else {

			// Use the older TreeConnect SMB request

			pkt.setCommand(PacketTypeV1.TreeConnect);

			// Set the parameter words

			pkt.setParameterCount(0);

			// Pack the request details

			StringBuffer shrbuf = new StringBuffer();

			shrbuf.append(DataType.ASCII);
			shrbuf.append("\\\\");
			shrbuf.append(shr.getNodeName().toUpperCase());
			shrbuf.append("\\");
			shrbuf.append(shr.getShareName().toUpperCase());
			shrbuf.append((char) 0x00);

			shrbuf.append(DataType.ASCII);
			shrbuf.append(shr.getPassword());
			shrbuf.append((char) 0x00);

			// Set the device type to be connected to

			shrbuf.append(DataType.ASCII);
			switch (devtyp) {

				// Disk device

				case SMBDeviceType.Disk:
					shrbuf.append("A:");
					break;

				// Printer device

				case SMBDeviceType.Printer:
					shrbuf.append("LPT1:");
					break;

				// Pipe device

				case SMBDeviceType.Pipe:
					shrbuf.append("IPC");
					break;
			}
			shrbuf.append((char) 0x00);

			// Copy the data bytes string to the SMB packet

			pkt.setBytes(shrbuf.toString().getBytes());
		}

		// Send/receive the SMB tree connect packet

		pkt.ExchangeSMB(sess, pkt);

		// Check if a valid response was received

		if ( pkt.isValidResponse()) {

			// Check for a TreeConnect or TreeConnectAndX response

			if ( pkt.getCommand() == PacketTypeV1.TreeConnect && pkt.getParameterCount() == 2)
				return pkt.getParameter(1);
			else if ( pkt.getCommand() == PacketTypeV1.TreeConnectAndX && pkt.getParameterCount() == 3)
				return pkt.getTreeId();
		}

		// Invalid response/error occurred

		if ( pkt.isLongErrorCode())
			throw new SMBException(SMBStatus.NTErr, pkt.getLongErrorCode());
		else
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());
	}

	/**
	 * Connect to a remote file server.
	 * 
	 * @param shr PC share information and access control information.
	 * @param sess SMB negotiate packet containing the receive negotiate packet.
	 * @param negpkt Negotiate SMB packet response.
	 * @param settings Session settings
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException Invalid username and/or password specified.
	 */
	private final static void ConnectSession(PCShare shr, Session sess, SMBPacket negpkt, SessionSettings settings)
		throws java.io.IOException, SMBException {

		// Set the session process id

		sess.setProcessId(negpkt.getProcessId());

		// Set the security mode flags

		int keyLen = 0;
		boolean unicodeStr = false;
		int encAlgorithm = PasswordEncryptor.LANMAN;
		int defFlags2 = 0;

		if ( sess.getDialect() == Dialect.NT) {

			// Read the returned negotiate parameters, for NT dialect the parameters are not aligned

			negpkt.resetParameterPointer();
			negpkt.skipBytes(2); // skip the dialect index

			sess.setSecurityMode(negpkt.unpackByte());

			// Set the maximum virtual circuits and multiplxed requests allowed by the server

			sess.setMaximumMultiplexedRequests(negpkt.unpackWord());
			sess.setMaximumVirtualCircuits(negpkt.unpackWord());

			// Set the maximum buffer size

			sess.setMaximumPacketSize(negpkt.unpackInt());

			// Skip the maximum raw buffer size and session key

			negpkt.skipBytes(8);

			// Set the server capabailities

			sess.setCapabilities(negpkt.unpackInt());

			// Get the server system time and timezone

			SMBDate srvTime = NTTime.toSMBDate(negpkt.unpackLong());
			int tzone = negpkt.unpackWord();

			// Get the encryption key length

			keyLen = negpkt.unpackByte();

			// Indicate that strings are UniCode

			unicodeStr = true;

			// Use NTLMv1 password encryption

			encAlgorithm = PasswordEncryptor.NTLM1;

			// Set the default flags for subsequent SMB requests

			defFlags2 = SMBPacket.FLG2_LONGFILENAMES + SMBPacket.FLG2_LONGERRORCODE;

			if ( sess.supportsUnicode())
				defFlags2 += SMBPacket.FLG2_UNICODE;

			if ( isSMBSigningEnabled())
				defFlags2 += SMBPacket.FLG2_SECURITYSIG;
		}
		else if ( sess.getDialect() > Dialect.CorePlus) {

			// Set the security mode and encrypted password mode

			int secMode = negpkt.getParameter(1);
			sess.setSecurityMode((secMode & 0x01) != 0 ? Session.SecurityModeUser : Session.SecurityModeShare);

			if ( negpkt.getParameterCount() >= 11)
				keyLen = negpkt.getParameter(11) & 0xFF; // should always be 8

			// Set the maximum virtual circuits and multiplxed requests allowed by the server

			sess.setMaximumMultiplexedRequests(negpkt.getParameter(3));
			sess.setMaximumVirtualCircuits(negpkt.getParameter(4));

			// Check if Unicode strings are being used

			if ( negpkt.isUnicode())
				unicodeStr = true;

			// Set the default flags for subsequent SMB requests

			defFlags2 = SMBPacket.FLG2_LONGFILENAMES;
		}

		// Set the default packet flags for this session

		sess.setDefaultFlags2(defFlags2);

		// Get the server details from the negotiate SMB packet

		if ( negpkt.getByteCount() > 0) {

			// Get the returned byte area length and offset

			int bytsiz = negpkt.getByteCount();
			int bytpos = negpkt.getByteOffset();
			byte[] buf = negpkt.getBuffer();

			// Extract the challenge response key, if specified

			if ( keyLen > 0) {

				// Allocate a buffer for the challenge response key

				byte[] encryptKey = new byte[keyLen];

				// Copy the challenge response key

				System.arraycopy(buf, bytpos, encryptKey, 0, keyLen);
				bytpos += keyLen;

				// Set the sessions encryption key

				sess.setEncryptionKey(encryptKey);

				// DEBUG

				if ( Debug.EnableInfo && Session.hasDebugOption(Session.DBGDumpPacket)) {
					Debug.print("** Encryption Key: ");
					Debug.print(HexDump.hexString(encryptKey));
					Debug.println(", length = " + keyLen);
				}
			}

			// Extract the domain name

			String dom;

			if ( unicodeStr == false)
				dom = DataPacker.getString(buf, bytpos, bytsiz);
			else
				dom = DataPacker.getUnicodeString(buf, bytpos, bytsiz / 2);
			sess.setDomain(dom);

			// DEBUG

			if ( Debug.EnableInfo && Session.hasDebugOption(Session.DBGDumpPacket))
				Debug.println("** Server domain : " + sess.getDomain() + ".");
		}

		// Set the password string, if encrypted passwords are required then
		// generate a 24 byte encrypted password.

		byte[] password = null;

		if ( sess.hasEncryptionKey() && shr.isNullLogon() == false) {

			try {

				// Generate a 24 byte encrypted password

				password = m_encryptor.generateEncryptedPassword(sess.getPassword(), sess.getEncryptionKey(), encAlgorithm);

				// DEBUG

				if ( Debug.EnableInfo && Session.hasDebugOption(Session.DBGDumpPacket)) {
					Debug.print("** Encrypted Password (");
					Debug.print(PasswordEncryptor.getAlgorithmName(encAlgorithm));
					Debug.print(") : ");
					Debug.println(HexDump.hexString(password));
				}

				// If SMB signing is enabled then generated a session key

				if ( (defFlags2 & SMBPacket.FLG2_SECURITYSIG) != 0) {

					// Create the session key

					byte[] sessKey = m_encryptor.generateSessionKey(sess.getPassword(), sess.getEncryptionKey(),
							PasswordEncryptor.NTLM1);

					// Enable SMB signing for this session

					sess.enableSMBSigning(sessKey);

					// DEBUG

					if ( Debug.EnableInfo && Session.hasDebugOption(Session.DBGSigning))
						Debug.print("** SMB signing enabled, session key=" + HexDump.hexString(sessKey, " "));
				}
			}
			catch (NoSuchAlgorithmException ex) {
				throw new IOException("Missing security provider - " + ex.toString());
			}
		}
		else {

			// Use a plain text password

			password = sess.getPassword().getBytes();
		}

		// Create a session setup packet

		SMBPacket pkt = new SMBPacket();

		pkt.setCommand(PacketTypeV1.SessionSetupAndX);
		pkt.setFlags(sess.getDefaultFlags());
		pkt.setFlags2(sess.getDefaultFlags2());

		// Check if the negotiated SMB dialect is NT LM 1.2 or an earlier dialect

		if ( sess.getDialect() == Dialect.NT) {

			// NT LM 1.2 SMB dialect

			pkt.setParameterCount(13);
			pkt.setAndXCommand(0xFF); // no secondary command
			pkt.setParameter(1, 0); // offset to next command
			pkt.setParameter(2, SessionFactory.DefaultPacketSize());
			pkt.setParameter(3, sess.getMaximumMultiplexedRequests());

			// Set the virtual circuit number
			//
			// Using a value of zero will cause a Windows server to disconnect all other sessions
			// from this
			// client.

			pkt.setParameter(4, settings.getVirtualCircuit());

			pkt.setParameterLong(5, 0); // session key

			// Set the share password length(s)

			pkt.setParameter(7, shr.isNullLogon() ? 1 : 0); // ANSI password length
			pkt.setParameter(8, shr.isNullLogon() ? 0 : password.length); // Unicode password length

			pkt.setParameter(9, 0); // reserved, must be zero
			pkt.setParameter(10, 0); // reserved, must be zero

			// Send the client capabilities

			int caps = Capability.V1LargeFiles + Capability.V1Unicode + Capability.V1NTSMBs + Capability.V1NTStatus
					+ Capability.V1RemoteAPIs;
			pkt.setParameterLong(11, caps);

			// Store the encrypted password

			pkt.setPosition(pkt.getByteOffset());
			if ( shr.isNullLogon()) {
				int pos = pkt.getByteOffset();
				pkt.getBuffer()[pos++] = (byte) 0;
				pkt.setPosition(pos);
			}
			else
				pkt.packBytes(password, password.length);

			// Pack the account name

			pkt.packString(shr.getUserName(), sess.supportsUnicode());

			// Check if the share has a domain, if not then use the default domain string

			if ( shr.isNullLogon())
				pkt.packString("", sess.supportsUnicode());
			else if ( shr.hasDomain())
				pkt.packString(shr.getDomain(), sess.supportsUnicode());
			else
				pkt.packString(getDefaultDomain(), sess.supportsUnicode());

			pkt.packString("Java VM", sess.supportsUnicode());
			pkt.packString("JFileSrv", sess.supportsUnicode());

			// Set the byte count

			pkt.setByteCount();
		}
		else {

			// Earlier SMB dialect

			pkt.setUserId(1);

			pkt.setParameterCount(10);
			pkt.setAndXCommand(0xFF); // no secondary command
			pkt.setParameter(1, 0); // offset to next command
			pkt.setParameter(2, SessionFactory.DefaultPacketSize());
			pkt.setParameter(3, 2); // max multiplexed pending requests
			pkt.setParameter(4, 0); // sess.getSessionId ());
			pkt.setParameter(5, 0);
			pkt.setParameter(6, 0);
			pkt.setParameter(7, shr.isNullLogon() ? 0 : password.length);
			pkt.setParameter(8, 0);
			pkt.setParameter(9, 0);

			// Put the password into the SMB packet

			byte[] buf = pkt.getBuffer();
			int pos = pkt.getByteOffset();

			if ( shr.isNullLogon())
				buf[pos++] = (byte) 0;
			else {
				for (int i = 0; i < password.length; i++)
					buf[pos++] = password[i];
			}

			// Build the account/client details

			StringBuffer clbuf = new StringBuffer();

			clbuf.append(shr.getUserName());
			clbuf.append((char) 0x00);

			// Check if the share has a domain, if not then use the unknown domain string

			if ( shr.isNullLogon())
				clbuf.append("");
			else if ( shr.hasDomain())
				clbuf.append(shr.getDomain());
			else
				clbuf.append(getDefaultDomain());
			clbuf.append((char) 0x00);

			clbuf.append("Java VM");
			clbuf.append((char) 0x00);

			clbuf.append("JFileSrv");
			clbuf.append((char) 0x00);

			// Copy the remaining data to the SMB packet

			byte[] byts = clbuf.toString().getBytes();
			for (int i = 0; i < byts.length; i++)
				buf[pos++] = byts[i];

			pkt.setByteCount(password.length + byts.length);
		}

		// Set the process id

		pkt.setProcessId(sess.getProcessId());

		// Exchange an SMB session setup packet with the remote file server

		pkt.ExchangeSMB(sess, pkt, true);

		// Save the session user id

		sess.setUserId(pkt.getUserId());

		// Check if SMB signing is enabled

		if ( pkt.hasSecuritySignature()) {

		}

		// Check if the session was created as a guest

		if ( pkt.getParameterCount() >= 3) {

			// Set the guest status for the session

			sess.setGuest(pkt.getParameter(2) != 0 ? true : false);
		}

		// The response packet should also have the server OS, LAN Manager type
		// and primary domain name.

		if ( pkt.getByteCount() > 0) {

			// Get the server OS

			pkt.setPosition(pkt.getByteOffset());
			String srvOS = pkt.unpackString(unicodeStr);
			sess.setOperatingSystem(srvOS);

			String lanman = pkt.unpackString(unicodeStr);
			sess.setLANManagerType(lanman);

			String domain = pkt.unpackString(unicodeStr);

			// Check if we have the primary domain for this session

			if ( domain != null && domain.length() > 0 && (sess.getDomain() == null || sess.getDomain().length() == 0))
				sess.setDomain(domain);
		}

		// Check for a core protocol session, set the maximum packet size

		if ( sess.getDialect() == Dialect.Core || sess.getDialect() == Dialect.CorePlus) {

			// Set the maximum packet size to be used on this session

			sess.setMaximumPacketSize(pkt.getParameter(2));
		}
	}

	/**
	 * Create a new SMB disk session
	 * 
	 * @param shr PC share information and access control information.
	 * @param pkt SMB negotiate packet containing the receive negotiate packet.
	 * @param netSess NetBIOS transport session connected to the remote file server.
	 * @param dialect SMB dialect that has been negotiated for this session.
	 * @param settings Session settings
	 * @return SMBDiskSession if the request was successful, else null
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException Invalid username and/or password.
	 */
	private final static DiskSession CreateDiskSession(PCShare shr, SMBPacket pkt, NetworkSession netSess, int dialect,
			SessionSettings settings)
		throws java.io.IOException, SMBException {

		// Create the SMB disk session depending on the SMB dialect negotiated

		DiskSession sess;

		if ( dialect == Dialect.Core || dialect == Dialect.CorePlus) {

			// Create a core protocol disk session

			sess = new CoreDiskSession(shr, dialect);
		}
		else {

			// Create a CIFS protocol disk session

			sess = new CIFSDiskSession(shr, dialect);

			// Set the maximum packet size allowed on this session

			sess.setMaximumPacketSize(pkt.getParameter(2));
		}

		// Attach the network session to the SMB session

		sess.setSession(netSess);

		// Connect the session

		ConnectSession(shr, sess, pkt, settings);

		// Connect to the remote share/disk

		try {
			int treeId = ConnectDevice(shr, sess, SMBDeviceType.Disk);
			if ( treeId != -1) {

				// Set the disk sessions allocated tree identifier, and return the
				// session.

				sess.setTreeId(treeId);
				return sess;
			}
		}
		catch (UnsupportedDeviceTypeException ex) {
		}

		// Failed to connect to the remote disk

		return null;
	}

	/**
	 * Create a new SMB session that is connected to a remote pipe resource.
	 * 
	 * @param shr PC share information and access control information.
	 * @param pkt SMB negotiate packet containing the received negotiate response.
	 * @param netSess Network transport session connected to the remote file server.
	 * @param dialect SMB dialect that has been negotiated for this session.
	 * @param settings Session settings
	 * @return SMBIPCSession if successful, else null
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException Invalid username and/or password.
	 */
	private final static IPCSession CreatePipeSession(PCShare shr, SMBPacket pkt, NetworkSession netSess, int dialect,
			SessionSettings settings)
		throws java.io.IOException, SMBException {

		// Create the SMB pipe session

		CIFSPipeSession sess = new CIFSPipeSession(shr, dialect);
		sess.setSession(netSess);

		// Check if the dialect is higher than core protocol

		if ( dialect > Dialect.CorePlus) {

			// Set the maximum packet size allowed on this session

			sess.setMaximumPacketSize(pkt.getParameter(2));
		}

		// Connect the session

		ConnectSession(shr, sess, pkt, settings);

		// Connect to the remote pipe resource

		try {
			int treeId = ConnectDevice(shr, sess, SMBDeviceType.Pipe);
			if ( treeId != -1) {

				// Set the pipe sessions allocated tree identifier, and return the
				// session.

				sess.setTreeId(treeId);
				return sess;
			}
		}
		catch (UnsupportedDeviceTypeException ex) {
		}

		// Failed to connect to the remote pipe

		return null;
	}

	/**
	 * Create a new SMB session that is connected to a remote printer resource.
	 * 
	 * @param shr PC share information and access control information.
	 * @param pkt SMB negotiate packet containing the received negotiate response.
	 * @param netSess Network transport session connected to the remote file server.
	 * @param dialect SMB dialect that has been negotiated for this session.
	 * @param settings Session settings
	 * @return SMBPrinterSession if successful, else null
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException Invalid username and/or password.
	 */
	private final static PrintSession CreatePrinterSession(PCShare shr, SMBPacket pkt, NetworkSession netSess, int dialect,
			SessionSettings settings)
		throws java.io.IOException, SMBException {

		// Create the SMB print session

		PrintSession sess = null;

		if ( dialect == Dialect.Core || dialect == Dialect.CorePlus) {

			// Create a core protocol print session

			sess = new CorePrintSession(shr, dialect);
		}
		else {

			// Create a CIFS protocol print session

			sess = new CIFSPrintSession(shr, dialect);

			// Set the maximum packet size allowed on this session

			sess.setMaximumPacketSize(pkt.getParameter(2));
		}

		// Attach the network session to the SMB session

		sess.setSession(netSess);

		// Connect the session

		ConnectSession(shr, sess, pkt, settings);

		// Connect to the remote printer device

		try {
			int treeId = ConnectDevice(shr, sess, SMBDeviceType.Printer);
			if ( treeId != -1) {

				// Set the print sessions allocated tree identifier, and return the
				// session.

				sess.setTreeId(treeId);
				return sess;
			}
		}
		catch (UnsupportedDeviceTypeException ex) {
		}

		// Failed to connect to the remote printer

		return null;
	}

	/**
	 * Create a new SMB session
	 * 
	 * @param shr PC share information and access control information.
	 * @param pkt SMB negotiate packet containing the receive negotiate packet.
	 * @param netSess Network transport session connected to the remote file server.
	 * @param dialect SMB dialect that has been negotiated for this session.
	 * @param settings Session settings
	 * @return SMBSession if the request was successful, else null
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException Invalid username and/or password.
	 */
	private final static Session CreateSession(PCShare shr, SMBPacket pkt, NetworkSession netSess, int dialect,
			SessionSettings settings)
		throws java.io.IOException, SMBException {

		// Create the SMB session

		Session sess = new Session(shr, dialect, null);
		sess.setSession(netSess);

		// Connect the session

		ConnectSession(shr, sess, pkt, settings);

		// Return the SMB session

		return sess;
	}

	/**
	 * Return the default SMB packet size
	 * 
	 * @return Default SMB packet size to allocate.
	 */
	protected final static int DefaultPacketSize() {
		return m_defPktSize;
	}

	/**
	 * Disable session factory debugging.
	 */
	public final static void disableDebug() {
		m_debug = false;
	}

	/**
	 * Disable the specified SMB dialect when setting up new sessions.
	 * 
	 * @param d int
	 */
	public final static void disableDialect(int d) {

		// Check if the dialect is enabled

		if ( m_defaultSettings.getDialects().hasDialect(d)) {

			// Disable the dialect

			m_defaultSettings.getDialects().RemoveDialect(d);
		}
	}

	/**
	 * Enable session factory debug output.
	 */
	public final static void enableDebug() {
		m_debug = true;
	}

	/**
	 * Enable the specified SMB dialect when setting up new sessions.
	 * 
	 * @param d int
	 */
	public final static void enableDialect(int d) {

		// Check if the dialect is already enabled

		if ( m_defaultSettings.getDialects().hasDialect(d))
			return;

		// Enable the specified dialect

		m_defaultSettings.getDialects().AddDialect(d);
	}

	/**
	 * Find the browse master for this network.
	 * 
	 * @return NetBIOSName
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final static NetBIOSName findBrowseMaster()
		throws SMBException, java.io.IOException {

		// Find a browse master to query for the domain list

		int retry = 0;
		NetBIOSName netName = null;

		while (retry++ < 5 && netName == null) {
			try {
				netName = NetBIOSSession.FindName(NetBIOSName.BrowseMasterName, NetBIOSName.BrowseMasterGroup,
						BROADCAST_LOOKUP_TIMEOUT);
			}
			catch (Exception ex) {
			}
		}

		// Return the browse master NetBIOS name details, or null

		return netName;
	}

	/**
	 * Return the list of SMB dialects that will be negotiated when a new session is created.
	 * 
	 * @return DialectSelector List of enabled SMB dialects.
	 */
	public final static DialectSelector getDefaultDialects() {
		return m_defaultSettings.getDialects();
	}

	/**
	 * Return the default domain name
	 * 
	 * @return String
	 */
	public static String getDefaultDomain() {
		return m_defDomain;
	}

	/**
	 * Return the default password.
	 * 
	 * @return String
	 */
	public static String getDefaultPassword() {
		return m_defPassword;
	}

	/**
	 * Return the default user name.
	 * 
	 * @return String
	 */
	public static String getDefaultUserName() {
		return m_defUserName;
	}

	/**
	 * Return the default session settings
	 * 
	 * @return SessionSettings
	 */
	public static SessionSettings getDefaultSettings() {
		return m_defaultSettings;
	}

	/**
	 * Return the list of available domains/workgroups.
	 * 
	 * @return org.filesys.smb.SMBServerList List of available domains.
	 * @exception SMBException If an SMB error occurs.
	 * @exception IOException If an I/O error occurs.
	 */
	public final static ServerList getDomainList()
		throws SMBException, IOException {

		// Check if this node is a browse master

		PCShare admShr = null;
		AdminSession admSess = null;

		try {

			// Try and connect to the local host, it may be a browse master

			String localHost = InetAddress.getLocalHost().getHostAddress();
			admShr = new PCShare(localHost, "", getDefaultUserName(), getDefaultPassword());
			admSess = SessionFactory.OpenAdminSession(admShr);

			// Get the domain list

			ServerList domList = admSess.getServerList(ServerType.DomainEnum);
			admSess.CloseSession();
			return domList;
		}
		catch (SMBException ex) {
		}
		catch (IOException ex) {
		}

		// Find a browse master to query for the domain list

		NetBIOSName browseMaster = NetBIOSSession.FindName(NetBIOSName.BrowseMasterName, NetBIOSName.BrowseMasterGroup, 2000);
		if ( browseMaster == null)
			return null;

		// Connect to the domain browse master IPC$ named pipe share

		String browseAddr = null;

		if ( browseMaster.numberOfAddresses() > 1) {

			// Find the best address to connect to the browse master

			int addrIdx = browseMaster.findBestMatchAddress(getLocalTcpipAddresses());
			if ( addrIdx != -1)
				browseAddr = browseMaster.getIPAddressString(addrIdx);
		}
		else {

			// Only one address available

			browseAddr = browseMaster.getIPAddressString(0);
		}

		// Connect to the browse master

		admShr = new PCShare(browseAddr, "", getDefaultUserName(), getDefaultPassword());
		admSess = SessionFactory.OpenAdminSession(admShr);

		ServerList domList = null;

		try {

			// Ask the browse master for the domain list

			domList = admSess.getServerList(ServerType.DomainEnum);
		}
		catch (SMBException ex) {

			// Add the local domain to the list as we can't get the list from the browse master

			domList = new ServerList();
			domList.addServerInfo(new RAPServerInfo(admSess.getSession().getDomain(), true));
		}

		// Close the session to the browse master

		admSess.CloseSession();
		return domList;
	}

	/**
	 * Return the local browse master node name.
	 * 
	 * @return Local browse master node name string.
	 */
	private String getLocalBrowser() {
		return m_localBrowseMaster;
	}

	/**
	 * Return the local domain name, if known.
	 * 
	 * @return Local domain name string, else null.
	 */
	public static String getLocalDomain() {

		// Check if the local node details have been checked

		if ( m_localChecked == false)
			CheckLocalNode();

		// Return the local domain/workgroup name

		return m_localDomain;
	}

	/**
	 * Return a list of the local servers, if the local domain/workgroup can be determined.
	 * 
	 * @return org.filesys.smb.SMBServerList
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 * @exception UnknownLocalDomainException Unknown local domain
	 */
	public static ServerList getLocalServerList()
		throws SMBException, IOException, UnknownLocalDomainException {

		// Check if the local node details have been checked

		if ( m_localChecked == false)
			CheckLocalNode();

		// Check if the local domain/workgroup name is known

		if ( getLocalDomain().length() == 0)
			throw new UnknownLocalDomainException();

		// Return the local server list

		return getServerList(getLocalDomain());
	}

	/**
	 * Return the NetBIOS scope id, or null if not set
	 * 
	 * @return String
	 */
	public static String getNetBIOSNameScope() {
		return m_defaultSettings.getNetBIOSNameScope();
	}

	/**
	 * Return the NetBIOS socket number that new sessions are connected to.
	 * 
	 * @return int NetBIOS session socket number.
	 */
	public static int getNetBIOSPort() {
		return m_defaultSettings.getNetBIOSSessionPort();
	}

	/**
	 * Return the primary connection protocol (either Protocol.TCPNetBIOS or Protocol.NativeSMB)
	 * 
	 * @return int
	 */
	public static final int getPrimaryProtocol() {
		return m_defaultSettings.getPrimaryProtocol();
	}

	/**
	 * Return the secondary connection protocol (Protocol.TCPNetBIOS, Protocol.NativeSMB or
	 * Protocol.None)
	 * 
	 * @return int
	 */
	public static final int getSecondaryProtocol() {
		return m_defaultSettings.getSecondaryProtocol();
	}

	/**
	 * Get the list of nodes in the specified domain
	 * 
	 * @param domnam Domain name to return nodes for
	 * @return SMBServerList containing the details of the nodes found
	 * @exception java.io.IOException I/O error occurred
	 * @exception SMBException SMB error occurred
	 */
	public final static ServerList getServerList(String domnam)
		throws java.io.IOException, SMBException {

		// Chain to the main server list method

		return getServerList(domnam, 0x0FFFFFFF);
	}

	/**
	 * Get the list of nodes in the specified domain that match the node type flags.
	 * 
	 * @param domnam Domain name to return nodes for
	 * @param srvFlags Node type flags
	 * @return SMBServerList containing the details of the nodes found.
	 * @exception SMBException If an SMB exception occurs.
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public final static ServerList getServerList(String domnam, int srvFlags)
		throws SMBException, java.io.IOException {

		// Check if this node is the browse master for the specified domain

		PCShare admShr = null;
		AdminSession admSess = null;
		String browseMaster = null;

		try {

			// Try and connect to the local host, it may be a browse master

			// String localHost = InetAddress.getLocalHost().getHostName().toUpperCase();
			String localHost = InetAddress.getLocalHost().getHostAddress();
			admShr = new PCShare(localHost, "", getDefaultUserName(), getDefaultPassword());
			admSess = SessionFactory.OpenAdminSession(admShr);

			// Check if the local domain is the required domain

			if ( admSess.getSession().getDomain() != null && admSess.getSession().getDomain().compareTo(domnam) == 0) {

				// Get the local domains server list

				ServerList srvList = admSess.getServerList(srvFlags & 0x0FFFFFFF);
				admSess.CloseSession();
				return srvList;
			}

			// Get the domain list

			ServerList domList = admSess.getServerList(ServerType.DomainEnum);
			if ( domList != null) {

				// Check if we have found the requested domain in the list

				int i = 0;

				while (i < domList.NumberOfServers() && browseMaster == null) {

					// Check if the current server information is the requested domain

					RAPServerInfo srvInfo = domList.getServerInfo(i);

					if ( srvInfo.getServerName().compareTo(domnam) == 0 && srvInfo.getComment().length() > 0) {

						// Set the browse master node name for the requested domain

						browseMaster = srvInfo.getComment();
					}
					else {

						// Update the server information index

						i++;
					}

				} // end while server information

				// Check if the browse master for the requested domain is the current node,
				// if so then keep the admin session open, if not then close the session.

				if ( browseMaster != null && browseMaster.compareTo(localHost) != 0) {

					// Close the admin session

					admSess.CloseSession();
					admSess = null;
				}
			}
		}
		catch (SMBException ex) {
			if ( Debug.EnableError && hasDebug())
				Debug.println("getServerList (): " + ex.toString());
			if ( admSess != null) {
				admSess.CloseSession();
				admSess = null;
			}
		}
		catch (java.io.IOException ex) {
			if ( Debug.EnableError && hasDebug())
				Debug.println("getServerList (): " + ex.toString());
			if ( admSess != null) {
				admSess.CloseSession();
				admSess = null;
			}
		}

		// If the browse master for the requested domain has not been set then try
		// and find it via a NetBIOS name lookup

		if ( browseMaster == null) {

			// Try to find the browse master via broadcast

			int retry = 0;

			while (retry++ < 5 && browseMaster == null) {
				try {
					NetBIOSName netName = NetBIOSSession.FindName(domnam.toUpperCase(), NetBIOSName.MasterBrowser,
							BROADCAST_LOOKUP_TIMEOUT);
					if ( netName != null) {

						// Get the browse master IP address

						if ( netName.numberOfAddresses() > 1) {

							// Find the best address to connect to the browse master

							int addrIdx = netName.findBestMatchAddress(getLocalTcpipAddresses());
							if ( addrIdx != -1)
								browseMaster = netName.getIPAddressString(addrIdx);
						}
						else {

							// Only one address available

							browseMaster = netName.getIPAddressString(0);
						}
					}
				}
				catch (Exception ex) {
				}
			}
		}

		// Connect to the domain browse master IPC$ named pipe share, if not already
		// connected.

		if ( admSess == null) {

			// Connect to the remote domains browse master

			admShr = new PCShare(browseMaster, "", getDefaultUserName(), getDefaultPassword());
			admSess = SessionFactory.OpenAdminSession(admShr);
		}

		// Return the server list for the domain, make sure we do not search for domains, mask
		// out the domain search flags.

		ServerList srvList = admSess.getServerList(srvFlags & 0x0FFFFFFF);
		admSess.CloseSession();
		return srvList;
	}

	/**
	 * Return the next session id
	 * 
	 * @return int
	 */
	private static synchronized int getSessionId() {
		int sessId = m_sessIdx++ + (NetBIOSSession.getJVMIndex() * 100);
		return sessId;
	}

	/**
	 * Get the list of local TCP/IP addresses
	 * 
	 * @return InetAddress[]
	 */
	private static synchronized InetAddress[] getLocalTcpipAddresses() {

		// Get the list of local TCP/IP addresses

		if ( m_localAddrList == null) {
			try {
				m_localAddrList = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
			}
			catch (UnknownHostException ex) {
			}
		}

		// Return the address list

		return m_localAddrList;
	}

	/**
	 * Determine if session factory debugging is enabled.
	 * 
	 * @return boolean
	 */
	public final static boolean hasDebug() {
		return m_debug;
	}

	/**
	 * Determine if a global process id is used for all sessions
	 * 
	 * @return boolean
	 */
	public final static boolean hasGlobalProcessId() {
		return m_globalPID;
	}

	/**
	 * Determine if the NetBIOS name scope is set
	 * 
	 * @return boolean
	 */
	public final static boolean hasNetBIOSNameScope() {
		return m_defaultSettings.hasNetBIOSNameScope();
	}

	/**
	 * Determine if SMB session debugging is enabled.
	 * 
	 * @return true if SMB session debugging is enabled, else false.
	 */
	public final static boolean hasSessionDebug() {
		return Session.hasDebug();
	}

	/**
	 * Determine if SMB signing is enabled
	 * 
	 * @return boolean
	 */
	public final static boolean isSMBSigningEnabled() {
		return m_smbSigningEnabled;
	}

	/**
	 * Determine if received packets should validate the SMB signing value
	 * 
	 * @return boolean
	 */
	public final static boolean isReceivedSMBSigningEnabled() {
		return m_smbSigningCheckRx;
	}

	/**
	 * Return a version string for this software release
	 * 
	 * @return Software version number string
	 */
	static final public String isVersion() {
		return "4.0.0";
	}

	/**
	 * Open a connection to a remote server admin pipe
	 * 
	 * @param shr Remote share information object.
	 * @return SMBAdminSession used to perform admin operations
	 * @exception java.io.IOException Network I/O error occurred.
	 * @exception java.net.UnknownHostException Remote node is unknown.
	 * @exception SMBException Failed to setup a new SMB session
	 */

	static final public AdminSession OpenAdminSession(PCShare shr)
		throws java.io.IOException, java.net.UnknownHostException, SMBException {

		// Use the default timeout for the session

		return OpenAdminSession(shr, getDefaultSettings());
	}

	/**
	 * Open a connection to a remote server admin pipe
	 * 
	 * @param shr Remote share information object.
	 * @param settings Session settings
	 * @return SMBAdminSession used to perform admin operations
	 * @exception java.io.IOException Network I/O error occurred.
	 * @exception java.net.UnknownHostException Remote node is unknown.
	 * @exception SMBException Failed to setup a new SMB session
	 */
	static final public AdminSession OpenAdminSession(PCShare shr, SessionSettings settings)
		throws java.io.IOException, java.net.UnknownHostException, SMBException {

		// Create a new IPC/pipe session to the remote admin pipe

		shr.setShareName("IPC$");
		IPCSession sess = (IPCSession) OpenSession(shr, SMBDeviceType.Pipe, settings);

		// Create an admin session that can perform admin operations using the
		// pipe session.

		return new AdminSession(sess);
	}

	/**
	 * Open a connection to a remote file server disk share.
	 * 
	 * @param shr Remote share information object.
	 * @return SMBSession used to access the remote share.
	 * @exception java.io.IOException Network I/O error occurred.
	 * @exception java.net.UnknownHostException Remote node is unknown.
	 * @exception SMBException Failed to setup a new SMB session
	 */
	static final public DiskSession OpenDisk(PCShare shr)
		throws java.io.IOException, java.net.UnknownHostException, SMBException {

		// Create a new disk session

		return (DiskSession) OpenSession(shr, SMBDeviceType.Disk, getDefaultSettings());
	}

	/**
	 * Open a connection to a remote file server disk share.
	 * 
	 * @param shr Remote share information object.
	 * @param settings Session settings
	 * @return SMBSession used to access the remote share.
	 * @exception java.io.IOException Network I/O error occurred.
	 * @exception java.net.UnknownHostException Remote node is unknown.
	 * @exception SMBException Failed to setup a new SMB session
	 */
	static final public DiskSession OpenDisk(PCShare shr, SessionSettings settings)
		throws java.io.IOException, java.net.UnknownHostException, SMBException {

		// Create a new disk session

		return (DiskSession) OpenSession(shr, SMBDeviceType.Disk, settings);
	}

	/**
	 * Open a connection to a remote file server disk share using the existing sessions network
	 * session.
	 * 
	 * @param shr Remote share information object.
	 * @param sess Existing connection to the remote server.
	 * @return SMBSession used to access the remote share.
	 * @exception java.io.IOException Network I/O error occurred.
	 * @exception java.net.UnknownHostException Remote node is unknown.
	 * @exception SMBException Failed to setup a new SMB session
	 */
	static final public DiskSession OpenDisk(PCShare shr, Session sess)
		throws java.io.IOException, java.net.UnknownHostException, SMBException {

		// Create the SMB disk session depending on the SMB dialect negotiated

		DiskSession diskSess;

		if ( sess.getDialect() == Dialect.Core || sess.getDialect() == Dialect.CorePlus) {

			// Create a core protocol disk session

			diskSess = new CoreDiskSession(shr, sess.getDialect());
		}
		else {

			// Create a CIFS protocol disk session

			diskSess = new CIFSDiskSession(shr, sess.getDialect());

			// Set the maximum packet size allowed on this session

			diskSess.setMaximumPacketSize(sess.getMaximumPacketSize());
		}

		// Attach the network session to the SMB session

		diskSess.setSession(sess.getSession());

		// Copy settings from the original session

		diskSess.setUserId(sess.getUserId());
		diskSess.setProcessId(sess.getProcessId());

		// Connect to the remote share/disk

		try {
			int treeId = ConnectDevice(shr, sess, SMBDeviceType.Disk);
			if ( treeId != -1) {

				// Set the disk sessions allocated tree identifier, and return the
				// session.

				diskSess.setTreeId(treeId);
				return diskSess;
			}
		}
		catch (UnsupportedDeviceTypeException ex) {
		}

		// Failed to connect to the remote disk

		return null;
	}

	/**
	 * Open a connection to a remote pipe/IPC
	 * 
	 * @param shr Remote share information object.
	 * @return SMBSession used to access the pipe/IPC session.
	 * @exception java.io.IOException Network I/O error occurred.
	 * @exception java.net.UnknownHostException Remote node is unknown.
	 * @exception SMBException Failed to setup a new SMB session
	 */
	static final public IPCSession OpenPipe(PCShare shr)
		throws java.io.IOException, java.net.UnknownHostException, SMBException {

		// Create a new IPC/pipe session

		return (IPCSession) OpenSession(shr, SMBDeviceType.Pipe, getDefaultSettings());
	}

	/**
	 * Open a connection to a remote pipe/IPC
	 * 
	 * @param shr Remote share information object.
	 * @param settings Session settings
	 * @return SMBSession used to access the pipe/IPC session.
	 * @exception java.io.IOException Network I/O error occurred.
	 * @exception java.net.UnknownHostException Remote node is unknown.
	 * @exception SMBException Failed to setup a new SMB session
	 */
	static final public IPCSession OpenPipe(PCShare shr, SessionSettings settings)
		throws java.io.IOException, java.net.UnknownHostException, SMBException {

		// Create a new IPC/pipe session

		return (IPCSession) OpenSession(shr, SMBDeviceType.Pipe, settings);
	}

	/**
	 * Open a connection to a remote pipe/IPC, in data mode
	 * 
	 * @param shr Remote share information object.
	 * @param pipeName String
	 * @return DataPipeFile used to read/write to the named pipe
	 * @exception java.io.IOException Network I/O error occurred.
	 * @exception java.net.UnknownHostException Remote node is unknown.
	 * @exception SMBException Failed to setup a new SMB session
	 */
	static final public DataPipeFile OpenDataPipe(PCShare shr, String pipeName)
		throws java.io.IOException, java.net.UnknownHostException, SMBException {

		// Create a new pipe session and connect a file

		return OpenDataPipe(shr, pipeName, getDefaultSettings());
	}

	/**
	 * Open a connection to a remote pipe/IPC, in data mode
	 * 
	 * @param shr Remote share information object.
	 * @param pipeName String
	 * @param settings Session settings
	 * @return DataPipeFile used to read/write to the named pipe
	 * @exception java.io.IOException Network I/O error occurred.
	 * @exception java.net.UnknownHostException Remote node is unknown.
	 * @exception SMBException Failed to setup a new SMB session
	 */
	static final public DataPipeFile OpenDataPipe(PCShare shr, String pipeName, SessionSettings settings)
		throws java.io.IOException, java.net.UnknownHostException, SMBException {

		// Create a new IPC/pipe session

		IPCSession sess = (IPCSession) OpenSession(shr, SMBDeviceType.Pipe, settings);

		// Open the pipe file

		// Check if we have negotiated NT dialect

		if ( sess.getDialect() != Dialect.NT)
			throw new SMBException(SMBStatus.NetErr, SMBStatus.NETUnsupported);

		// Build the NTCreateAndX SMB packet

		SMBPacket smbPkt = sess.m_pkt;

		smbPkt.setFlags(sess.getDefaultFlags());
		smbPkt.setFlags2(sess.getDefaultFlags2());

		smbPkt.setCommand(PacketTypeV1.NTCreateAndX);
		smbPkt.setUserId(sess.getUserId());
		smbPkt.setTreeId(sess.getTreeId());

		smbPkt.setParameterCount(24);
		smbPkt.resetParameterPointer();

		smbPkt.packByte(0xFF); // no chained command
		smbPkt.packByte(0); // reserved
		smbPkt.packWord(0); // AndX offset
		smbPkt.packByte(0); // reserved

		smbPkt.packWord((pipeName.length() * 2) + 2); // name length in bytes, inc null
		smbPkt.packInt(0); // flags
		smbPkt.packInt(0); // root FID
		smbPkt.packInt(AccessMode.NTReadWrite); // desired access mode
		smbPkt.packLong(0); // allocation size
		smbPkt.packInt(0); // file attributes
		smbPkt.packInt(SharingMode.READ_WRITE.intValue()); // share access mode
		smbPkt.packInt(FileAction.OpenIfExists); // action to take if file exists
		smbPkt.packInt(0); // file create options
		smbPkt.packInt(2); // impersonation level, 0=anonymous, 2=impersonation
		smbPkt.packByte(0); // security flags

		smbPkt.resetBytePointer();
		smbPkt.packString(pipeName, smbPkt.isUnicode());

		smbPkt.setByteCount();

		// Send/receive the NT create andX request

		smbPkt.ExchangeSMB(sess, smbPkt, true);

		// Unpack the file/directory details

		smbPkt.resetParameterPointer();
		smbPkt.skipBytes(5);

		int fid = smbPkt.unpackWord();
		int createAction = smbPkt.unpackInt();

		long createTime = smbPkt.unpackLong();
		long lastAccessTime = smbPkt.unpackLong();
		long lastWriteTime = smbPkt.unpackLong();
		long changeTime = smbPkt.unpackLong();

		int attr = smbPkt.unpackInt();

		long allocSize = smbPkt.unpackLong();
		long eofOffset = smbPkt.unpackLong();

		int devType = smbPkt.unpackWord();

		// Create the file information

		FileInfo finfo = new FileInfo(pipeName, eofOffset, attr);
		finfo.setFileId(fid);

		// Create the file object

		return new DataPipeFile(sess, finfo, fid);
	}

	/**
	 * Open a connection to a remote print server
	 * 
	 * @param shr Remote share information object.
	 * @param settings Session settings
	 * @return SMBSession used to access the remote share.
	 * @exception java.io.IOException Network I/O error occurred.
	 * @exception java.net.UnknownHostException Remote node is unknown.
	 * @exception SMBException Failed to setup a new SMB session
	 */
	static final public PrintSession OpenPrinter(PCShare shr, SessionSettings settings)
		throws java.io.IOException, java.net.UnknownHostException, SMBException {

		// Create a new print session

		return (PrintSession) OpenSession(shr, SMBDeviceType.Printer, settings);
	}

	/**
	 * Open a session to a remote server.
	 * 
	 * @param shr Remote server share and access control details.
	 * @param devtyp Device type to connect to on the remote node.
	 * @param settings Session settings
	 * @return SMBSession for the new session, else null.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception java.net.UnknownHostException Remote node is unknown.
	 * @exception SMBException Failed to setup a new session.
	 */
	private static Session OpenSession(PCShare shr, int devtyp, SessionSettings settings)
		throws java.io.IOException, java.net.UnknownHostException, SMBException {

		// Build a unique caller name

		int pid = getSessionId();

		StringBuffer nameBuf = new StringBuffer(InetAddress.getLocalHost().getHostName() + "_" + pid);
		String localName = nameBuf.toString();

		// Debug

		if ( Debug.EnableInfo && Session.hasDebug()) {
			Debug.println("** New session from " + localName + " to " + shr.toString());

			// Display the Java system variables

			Debug.println("** os.arch = " + System.getProperty("os.arch") + ", java.version: "
					+ System.getProperty("java.version"));
			Debug.println("** JFileSrv version is " + SessionFactory.isVersion());
			Debug.println("** Trying primary protocol - " + Protocol.asString(settings.getPrimaryProtocol()));
		}

		// Connect to the requested server using the primary protocol

		NetworkSession netSession = null;

		try {

			switch (settings.getPrimaryProtocol()) {

				// NetBIOS connection

				case Protocol.TCPNetBIOS:
					netSession = connectNetBIOSSession(shr.getNodeName(), localName, settings);
					break;

				// Native SMB connection

				case Protocol.NativeSMB:
					netSession = connectNativeSMBSession(shr.getNodeName(), localName, settings);
					break;
			}
		}
		catch (IOException ex) {

			// Check if there is a secondary protocolcfno configured, if not then rethrow the
			// exception

			if ( settings.getSecondaryProtocol() == Protocol.None)
				throw ex;
		}

		// If the connection was not made using the primary protocol try the secondary protocol, if
		// configured

		if ( netSession == null) {

			// DEBUG

			if ( Debug.EnableInfo && Session.hasDebug())
				Debug.println("** Trying secondary protocol - " + Protocol.asString(settings.getSecondaryProtocol()));

			// Try the secondary protocol

			switch (settings.getSecondaryProtocol()) {

				// NetBIOS connection

				case Protocol.TCPNetBIOS:
					netSession = connectNetBIOSSession(shr.getNodeName(), localName, settings);
					break;

				// Native SMB connection

				case Protocol.NativeSMB:
					netSession = connectNativeSMBSession(shr.getNodeName(), localName, settings);
					break;
			}

			// If the secondary connection was successful check if the protocol order should be
			// updated

			if ( settings.hasUpdateProtocol() && netSession != null) {

				// Update the primary protocol

				settings.setPrimaryProtocol(settings.getSecondaryProtocol());
				settings.setSecondaryProtocol(Protocol.None);

				// Debug

				if ( Debug.EnableInfo && Session.hasDebug())
					Debug.println("** Updated primary protocol : " + Protocol.asString(settings.getPrimaryProtocol()));
			}
		}

		// Check if we connected to the remote host

		if ( netSession == null)
			throw new IOException("Failed to connect to host, " + shr.getNodeName());

		// Debug

		if ( Debug.EnableInfo && Session.hasDebug())
			Debug.println("** Connected session, protocol : " + netSession.getProtocolName());

		// Build a protocol negotiation SMB packet, and send it to the remote
		// file server.

		SMBPacket pkt = new SMBPacket();
		DialectSelector selDialect = settings.getDialects();

		if ( selDialect == null) {

			// Use the default SMB dialect list

			selDialect = new DialectSelector();
			selDialect.copyFrom(m_defaultSettings.getDialects());
		}

		// Build the negotiate SMB dialect packet and exchange with the remote server

		StringList diaList = BuildNegotiatePacket(pkt, selDialect, hasGlobalProcessId() ? 1 : pid);
		pkt.ExchangeLowLevelSMB(netSession, pkt, true);

		// Determine the selected SMB dialect

		String diaStr = diaList.getStringAt(pkt.getParameter(0));
		int dialectId = Dialect.DialectType(diaStr);

		// DEBUG

		if ( Debug.EnableInfo && Session.hasDebug())
			Debug.println("** SessionFactory: Negotiated SMB dialect " + diaStr);

		if ( dialectId == Dialect.Unknown) {

			// Close the low level session

			netSession.Close();

			// Indicate that the SMB dialect could not be negotiated

			throw new java.io.IOException("Unknown SMB dialect");
		}

		// Determine the type of session that should be created

		Session sess = null;

		try {

			switch (devtyp) {

				// Disk share

				case SMBDeviceType.Disk:
					sess = CreateDiskSession(shr, pkt, netSession, dialectId, settings);
					break;

				// Printer share

				case SMBDeviceType.Printer:
					sess = CreatePrinterSession(shr, pkt, netSession, dialectId, settings);
					break;

				// IPC/pipe

				case SMBDeviceType.Pipe:
					sess = CreatePipeSession(shr, pkt, netSession, dialectId, settings);
					break;
			}
		}
		catch (SMBException ex) {

			// Close the low level session

			netSession.Close();

			// Rethrow the exception

			throw ex;
		}
		catch (IOException ex) {

			// Close the low level session

			netSession.Close();

			// Rethrow the exception

			throw ex;
		}

		// Set the sessions SMB dialect string, if valid

		if ( sess != null)
			sess.setDialectString(diaStr);

		// Return the new session

		return sess;
	}

	/**
	 * Open a session to a remote server, negotiate an SMB dialect and get the returned challenge
	 * key. Returns an AuthenticateSession which can then be used to provide passthru
	 * authentication.
	 * 
	 * @param shr Remote server share and access control details.
	 * @return AuthenticateSession for the new session, else null.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception java.net.UnknownHostException Remote node is unknown.
	 * @exception SMBException Failed to setup a new session.
	 */
	public static AuthenticateSession OpenAuthenticateSession(PCShare shr)
		throws java.io.IOException, java.net.UnknownHostException, SMBException {

		// Open an authentication session

		return OpenAuthenticateSession(shr, m_defaultSettings);
	}

	/**
	 * Open a session to a remote server, negotiate an SMB dialect and get the returned challenge
	 * key. Returns an AuthenticateSession which can then be used to provide passthru
	 * authentication.
	 * 
	 * @param shr Remote server share and access control details.
	 * @param settings Session settings
	 * @return AuthenticateSession for the new session, else null.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception java.net.UnknownHostException Remote node is unknown.
	 * @exception SMBException Failed to setup a new session.
	 */
	public static AuthenticateSession OpenAuthenticateSession(PCShare shr, SessionSettings settings)
		throws java.io.IOException, java.net.UnknownHostException, SMBException {

		// Build a unique caller name

		int pid = getSessionId();

		StringBuffer nameBuf = new StringBuffer(InetAddress.getLocalHost().getHostName() + "_" + pid);
		String localName = nameBuf.toString();

		// Debug

		if ( Debug.EnableInfo && Session.hasDebug()) {
			Debug.println("** New auth session from " + localName + " to " + shr.toString());

			// Display the Java system variables

			Debug.println("** os.arch = " + System.getProperty("os.arch") + ", java.version: "
					+ System.getProperty("java.version"));
			Debug.println("** JFileSrv version is " + SessionFactory.isVersion());
		}

		// Connect to the requested server using the primary protocol

		NetworkSession netSession = null;

		try {

			switch (settings.getPrimaryProtocol()) {

				// NetBIOS connection

				case Protocol.TCPNetBIOS:
					netSession = connectNetBIOSSession(shr.getNodeName(), localName, settings);
					break;

				// Native SMB connection

				case Protocol.NativeSMB:
					netSession = connectNativeSMBSession(shr.getNodeName(), localName, settings);
					break;
			}
		}
		catch (IOException ex) {

			// Check if there is a secondary protocolcfno configured, if not then rethrow the
			// exception

			if ( settings.getSecondaryProtocol() == Protocol.None)
				throw ex;
		}

		// If the connection was not made using the primary protocol try the secondary protocol, if
		// configured

		if ( netSession == null) {

			// DEBUG

			if ( Debug.EnableInfo && Session.hasDebug())
				Debug.println("** Trying secondary protocol - " + Protocol.asString(settings.getSecondaryProtocol()));

			// Try the secondary protocol

			switch (settings.getSecondaryProtocol()) {

				// NetBIOS connection

				case Protocol.TCPNetBIOS:
					netSession = connectNetBIOSSession(shr.getNodeName(), localName, settings);
					break;

				// Native SMB connection

				case Protocol.NativeSMB:
					netSession = connectNativeSMBSession(shr.getNodeName(), localName, settings);
					break;
			}

			// If the secondary connection was successful check if the protocol order should be
			// updated

			if ( settings.hasUpdateProtocol() && netSession != null) {

				// Update the primary protocol

				settings.setPrimaryProtocol(settings.getSecondaryProtocol());
				settings.setSecondaryProtocol(Protocol.None);

				// Debug

				if ( Debug.EnableInfo && Session.hasDebug())
					Debug.println("** Updated primary protocol : " + Protocol.asString(settings.getPrimaryProtocol()));
			}
		}

		// Check if we connected to the remote host

		if ( netSession == null)
			throw new IOException("Failed to connect to host, " + shr.getNodeName());

		// Debug

		if ( Debug.EnableInfo && Session.hasDebug())
			Debug.println("** Connected session, protocol : " + netSession.getProtocolName());

		// Build a protocol negotiation SMB packet, and send it to the remote
		// file server.

		SMBPacket pkt = new SMBPacket();
		DialectSelector selDialect = settings.getDialects();

		if ( selDialect == null) {

			// Use the default SMB dialect list

			selDialect = new DialectSelector();
			selDialect.copyFrom(m_defaultSettings.getDialects());
		}

		// Build the negotiate SMB dialect packet and exchange with the remote server

		StringList diaList = BuildNegotiatePacket(pkt, selDialect, hasGlobalProcessId() ? 1 : pid);
		pkt.ExchangeLowLevelSMB(netSession, pkt, true);

		// Determine the selected SMB dialect

		String diaStr = diaList.getStringAt(pkt.getParameter(0));
		int dialectId = Dialect.DialectType(diaStr);

		// DEBUG

		if ( Debug.EnableInfo && Session.hasDebug())
			Debug.println("** SessionFactory: Negotiated SMB dialect " + diaStr);

		if ( dialectId == Dialect.Unknown)
			throw new java.io.IOException("Unknown SMB dialect");

		// Create the authenticate session

		AuthenticateSession authSess = new AuthenticateSession(shr, netSession, dialectId, pkt);
		return authSess;
	}

	/**
	 * Send a message to a remote user.
	 * 
	 * @param dNode Destination node name.
	 * @param msg Message to be sent (maximum of 128 bytes).
	 * @param tmo int
	 * @exception SMBException If an SMB error occurs.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception java.net.UnknownHostException If the destination host is invalid/unknown.
	 */
	public final static void SendMessage(String dNode, String msg, int tmo)
		throws SMBException, java.io.IOException, UnknownHostException {

		// Enable debug

		// SMBSession.setDebug(0xFFFF);

		// Check if the destination address is a numeric IP address

		String remName = dNode;

		if ( IPAddress.isNumericAddress(remName)) {

			// Get a list of NetBIOS names from the remote host

			NetBIOSNameList nameList = NetBIOSSession.FindNamesForAddress(dNode);

			// Find the messenger service name

			NetBIOSName nbName = nameList.findName(NetBIOSName.RemoteMessenger, false);
			if ( nbName == null)
				throw new IOException("Messenger service not running");

			// Set the remote host name

			remName = nbName.getName();
		}

		// Build a unique caller NetBIOS name

		String localName = InetAddress.getLocalHost().getHostName().toUpperCase();

		// Debug

		if ( Debug.EnableInfo && Session.hasDebug()) {
			Debug.println("** New session from " + localName + " to " + dNode);

			// Display the Java system variables

			Debug.println("** os.arch = " + System.getProperty("os.arch") + ", java.version: "
					+ System.getProperty("java.version"));
		}

		// Connect to the requested server

		NetBIOSSession nbSession = new NetBIOSSession(tmo);
		nbSession.setLocalNameType(NetBIOSName.Messenger);
		nbSession.setRemoteNameType(NetBIOSName.RemoteMessenger);

		nbSession.Open(remName, localName, dNode);

		// Build a send message packet

		SMBPacket pkt = new SMBPacket();
		pkt.setCommand(PacketTypeV1.SendMessage);
		pkt.setFlags(0);
		pkt.setParameterCount(0);

		// Set the session id and sequence number

		pkt.setSID(0);
		pkt.setSeqNo(0);

		pkt.resetBytePointer();

		pkt.packByte(DataType.ASCII);
		pkt.packString(localName, false);

		pkt.packByte(DataType.ASCII);
		pkt.packString(remName.toUpperCase(), false);

		pkt.packByte(DataType.DataBlock);
		pkt.packWord(msg.length());
		pkt.packBytes(msg.getBytes(), msg.length());

		pkt.setByteCount();

		// Send the message

		pkt.ExchangeLowLevelSMB(nbSession, pkt, false);

		// Close the NetBIOS session

		nbSession.Close();

		// Check if the send got an error

		if ( pkt.getErrorClass() != SMBStatus.Success && pkt.getErrorCode() != SMBStatus.Success)
			throw new java.io.IOException(SMBErrorText.ErrorString(pkt.getErrorClass(), pkt.getErrorCode()));
	}

	/**
	 * Send a message to a remote user.
	 * 
	 * @param dNode Destination node name.
	 * @param msg Message to be sent (maximum of 128 bytes).
	 * @exception SMBException If an SMB error occurs.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception java.net.UnknownHostException If the destination host is invalid/unknown.
	 */
	public final static void SendMessage(String dNode, String msg)
		throws SMBException, java.io.IOException, UnknownHostException {

		// Send a message using the default timeout

		SendMessage(dNode, msg, RFCNetBIOSProtocol.TMO);
	}

	/**
	 * Set the default SMB dialects that are to be negotiated when a new session is created.
	 * 
	 * @param dialist DialectSelector containing the SMB dialects to negotiate.
	 */
	public final static void setDefaultDialects(DialectSelector dialist) {

		// Copy the SMB dialect list

		m_defaultSettings.setDialects(dialist);
	}

	/**
	 * Set the default domain.
	 * 
	 * @param domain String
	 */
	public static void setDefaultDomain(String domain) {
		m_defDomain = domain;
	}

	/**
	 * Set the default password.
	 * 
	 * @param pwd String
	 */
	public static void setDefaultPassword(String pwd) {
		m_defPassword = pwd;
	}

	/**
	 * Set the default user name.
	 * 
	 * @param user String
	 */
	public static void setDefaultUserName(String user) {
		m_defUserName = user;
	}

	/**
	 * Set/clear the global process id flag. If the flag is false a unique process id is generated
	 * for each session.
	 * 
	 * @param ena boolean
	 */
	public final static void setGlobalProcessId(boolean ena) {
		m_globalPID = ena;
	}

	/**
	 * Set the NetBIOS socket number to be used when setting up new sessions. The default socket is
	 * 139.
	 * 
	 * @param port int
	 */
	public static void setNetBIOSPort(int port) {
		m_defaultSettings.setNetBIOSSessionPort(port);
	}

	/**
	 * Set the NetBIOS scope id
	 * 
	 * @param scope String
	 */
	public static void setNetBIOSNameScope(String scope) {
		String nbScope = scope;
		if ( nbScope != null && nbScope.startsWith("."))
			nbScope = nbScope.substring(1);
		m_defaultSettings.setNetBIOSNameScope(nbScope);
	}

	/**
	 * Set the protocol connection order
	 * 
	 * @param pri Primary connection protocol
	 * @param sec Secondary connection protocol, or none
	 * @return boolean
	 */
	public static final boolean setProtocolOrder(int pri, int sec) {

		// Primary protocol must be specified

		if ( pri != Protocol.TCPNetBIOS && pri != Protocol.NativeSMB)
			return false;

		// Primary and secondary must be different

		if ( pri == sec)
			return false;

		// Save the settings

		m_defaultSettings.setPrimaryProtocol(pri);
		m_defaultSettings.setSecondaryProtocol(sec);

		return true;
	}

	/**
	 * Enable/disable SMB signing support
	 * 
	 * @param ena boolean
	 */
	public final static void setSMBSigningEnabled(boolean ena) {
		m_smbSigningEnabled = ena;
	}

	/**
	 * Enable/disable validation of received SMB signatures when signing is enabled
	 * 
	 * @param ena boolean
	 */
	public final static void setReceivedSMBSigningEnabled(boolean ena) {
		m_smbSigningCheckRx = ena;
	}

	/**
	 * Enable/disable SMB session debugging.
	 * 
	 * @param dbg true to enable SMB session debugging, else false.
	 */
	public final static void setSessionDebug(boolean dbg) {
		if ( dbg == true)
			Session.setDebug(Session.DBGPacketType);
		else
			Session.setDebug(0);
	}

	/**
	 * Set the subnet mask string for network broadcast requests
	 * 
	 * If the subnet mask is not set a default broadcast mask for the TCP/IP address class will be
	 * used.
	 * 
	 * @param subnet Subnet mask string, in 'nnn.nnn.nnn.nnn' format.
	 */
	public final static void setSubnetMask(String subnet) {
		NetBIOSSession.setDefaultSubnetMask(subnet);
	}

	/**
	 * Setup the default SMB dialects to be negotiated when creating new sessions.
	 */
	private static void SetupDefaultDialects() {

		// Initialize the default dialect list

		DialectSelector dialects = new DialectSelector();

		// Always enable core protocol

		dialects.AddDialect(Dialect.Core);
		dialects.AddDialect(Dialect.CorePlus);

		// Determine if the CIFS classes are available, if so then enable
		// negotiation of the extra SMB dialects

		try {

			// Try and load the CIFS session classes

			Class.forName("org.filesys.client.CIFSDiskSession");
			Class.forName("org.filesys.client.CIFSPrintSession");

			// The CIFS protocol session classes are available

			dialects.AddDialect(Dialect.DOSLanMan1);
			dialects.AddDialect(Dialect.DOSLanMan2);
			dialects.AddDialect(Dialect.LanMan1);
			dialects.AddDialect(Dialect.LanMan2);
			dialects.AddDialect(Dialect.LanMan2_1);
			dialects.AddDialect(Dialect.NT);
		}
		catch (java.lang.ClassNotFoundException ex) {
		}
		catch (java.lang.ExceptionInInitializerError ex) {
		}
		catch (java.lang.LinkageError ex) {
		}

		// Set the default dialects to negotiate

		m_defaultSettings.setDialects(dialects);
	}

	/**
	 * Connect a NetBIOS network session
	 * 
	 * @param toName Host name/address to connect to
	 * @param fromName Local host name/address
	 * @param settings Session settings
	 * @return NetworkSession
	 * @exception IOException If a network error occurs
	 */
	private static final NetworkSession connectNetBIOSSession(String toName, String fromName, SessionSettings settings)
		throws IOException {

		// Connect to the requested server

		NetBIOSSession nbSession = new NetBIOSSession(settings.getSessionTimeout(), settings.getNetBIOSSessionPort(), settings
				.getNetBIOSNamePort());

		// Set per session overrides for the new session

		nbSession.setSubnetMask(settings.getSubnetMask());
		nbSession.setWildcardFileServerName(settings.useWildcardServerName());
		nbSession.setWINSServer(settings.getWINSServer());
		nbSession.setLookupType(settings.getLookupType());
		nbSession.setLookupTimeout(settings.getLookupTimeout());

		// Make sure the destination name is uppercased

		toName = toName.toUpperCase();

		// Check if the remote host is specified as a TCP/IP address

		NetBIOSName nbName = null;

		if ( IPAddress.isNumericAddress(toName)) {

			// Convert the TCP/IP address to a NetBIOS name using a DNS or WINS/NetBIOS name lookup

			nbName = NetBIOSSession.ConvertAddressToName(toName, NetBIOSName.FileServer, false, nbSession);
		}
		else {

			IOException savedException = null;

			try {

				// Find the remote host and get a list of the network addresses it is using

				nbName = NetBIOSSession.FindName(toName, NetBIOSName.FileServer, 500, nbSession);

				// If the lookup type is DNS only then make sure the file server responds on that name. The DNS lookup
				// could return a name that has a domain but it it unlikely that NetBIOS is setup with name scopes on
				// the server.

				if ( nbSession.getLookupType() == NetBIOSSession.LookupType.DNS_ONLY && nbName != null && nbName.hasNameScope()) {

					// Clear the NetBIOS name until the connection is successful

					NetBIOSName srvName = nbName;
					srvName.setType(NetBIOSName.FileServer);

					nbName = null;

					// Search for the server name using NetBIOS

					nbName = NetBIOSSession.FindName(srvName, 500, NetBIOSSession.LookupType.WINS_ONLY, nbSession);
				}
			}
			catch (IOException ex) {
				savedException = ex;
			}

			// Check if the server name contains a name scope

			if ( nbName == null && toName.indexOf('.') != -1) {

				// Parse the NetBIOS server name

				NetBIOSName srvName = new NetBIOSName(toName);
				if ( srvName.hasNameScope()) {

					// Remove the NetBIOS name scope

					srvName.setNameScope(null);

					// Set the new server name

					toName = srvName.getName();

					// Try the connection attempt again without the NetBIOS name scope

					try {

						// Find the remote host and get a list of the network addresses it is using

						nbName = NetBIOSSession.FindName(toName, NetBIOSName.FileServer, 500, nbSession);
					}
					catch (IOException ex) {
						savedException = ex;
					}
				}
			}

			// If the NetBIOS name was not found then check if the local system has the name

			if ( nbName == null) {

				// Make sure NetBIOS name lookups are enabled

				if ( nbSession.getLookupType() != NetBIOSSession.LookupType.DNS_ONLY) {

					// Get a list of NetBIOS names for the local system

					NetBIOSNameList localList = NetBIOSSession.FindNamesForAddress(InetAddress.getLocalHost().getHostAddress());
					if ( localList != null) {
						nbName = localList.findName(toName, NetBIOSName.FileServer, false);
						if ( nbName != null)
							nbName.addIPAddress(InetAddress.getLocalHost().getAddress());
						else
							throw savedException;
					}
				}
				else
					throw savedException;
			}
		}

		// Check if the NetBIOS name scope has been set, if so then update the names to add the
		// scope id

		if ( nbName.hasNameScope()) {

			// Add the NetBIOS scope id to the to/from NetBIOS names

			toName = nbName.getFullName();
			fromName = fromName + "." + nbName.getNameScope();
		}
		else if ( settings.hasNetBIOSNameScope()) {

			// Add the NetBIOS scope id to the to/from NetBIOS names

			toName = toName + "." + settings.getNetBIOSNameScope();
			fromName = fromName + "." + settings.getNetBIOSNameScope();
		}

		// If the NetBIOS name has more than one TCP/IP address then find the best match for the client and
		// try to connect on that address first, if that fails then we will have to try each address in turn.

		if ( nbName.numberOfAddresses() > 1) {

			// Get the local TCP/IP address list and search for a best match address to connect to
			// the server on

			InetAddress[] addrList = getLocalTcpipAddresses();
			int addrIdx = nbName.findBestMatchAddress(addrList);

			if ( addrIdx != -1) {

				try {

					// Get the server IP address

					String ipAddr = nbName.getIPAddressString(addrIdx);

					// DEBUG

					if ( Debug.EnableInfo && hasSessionDebug())
						Debug.println("** Server is multi-homed, trying to connect to " + ipAddr);

					// Open the session to the remote host

					nbSession.Open(toName, fromName, ipAddr);

					// Check if the session is connected

					if ( nbSession.isConnected() == false) {

						// Close the session

						try {
							nbSession.Close();
						}
						catch (Exception ex) {
						}
					}
					else if ( Debug.EnableInfo && hasSessionDebug() && nbSession.isConnected())
						Debug.println("** Connected to address " + ipAddr);
				}
				catch (IOException ex) {
				}
			}
		}

		// DEBUG

		if ( Debug.EnableInfo && hasSessionDebug() && nbSession.isConnected() == false && nbName.numberOfAddresses() > 1)
			Debug.println("** Server is multi-homed, trying all addresses");

		// Loop through the available addresses for the remote file server until we get a successful
		// connection, or all addresses have been used

		IOException lastException = null;
		int addrIdx = 0;

		while (nbSession.isConnected() == false && addrIdx < nbName.numberOfAddresses()) {

			try {

				// Get the server IP address

				String ipAddr = nbName.getIPAddressString(addrIdx++);

				// DEBUG

				if ( Debug.EnableInfo && hasSessionDebug())
					Debug.println("** Trying address " + ipAddr);

				// Open the session to the remote host

				nbSession.Open(toName, fromName, ipAddr);

				// Check if the session is connected

				if ( nbSession.isConnected() == false) {

					// Close the session

					try {
						nbSession.Close();
					}
					catch (Exception ex) {
					}
				}
				else if ( Debug.EnableInfo && hasSessionDebug() && nbSession.isConnected())
					Debug.println("** Connected to address " + ipAddr);
			}
			catch (IOException ex) {

				// Save the last exception

				lastException = ex;
			}
		}

		// Check if the session is connected

		if ( nbSession.isConnected() == false) {

			// If there is a saved exception rethrow it

			if ( lastException != null)
				throw lastException;

			// Indicate that the session was not connected

			return null;
		}

		// Return the network session

		return nbSession;
	}

	/**
	 * Connect a native SMB network session
	 * 
	 * @param toName Host name/address to connect to
	 * @param fromName Local host name/address
	 * @param settings Session settings
	 * @return NetworkSession
	 * @exception IOException If a network error occurs
	 */
	private static final NetworkSession connectNativeSMBSession(String toName, String fromName, SessionSettings settings)
		throws IOException {

		// Connect to the requested server

		TcpipSMBNetworkSession tcpSession = new TcpipSMBNetworkSession(settings.getSessionTimeout(), settings.getNativeSMBPort());

		try {

			// Open the session

			tcpSession.Open(toName, fromName, null);

			// Check if the session is connected

			if ( tcpSession.isConnected() == false) {

				// Close the session

				try {
					tcpSession.Close();
				}
				catch (Exception ex) {
				}

				// Return a null session

				return null;
			}
		}
		catch (Exception ex) {
			try {
				tcpSession.Close();
			}
			catch (Exception ex2) {
			}
			tcpSession = null;
		}

		// Return the network session

		return tcpSession;
	}
}