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
import org.filesys.netbios.NetworkSession;
import org.filesys.smb.*;
import org.filesys.smb.PacketTypeV1;
import org.filesys.util.DataPacker;
import org.filesys.util.HexDump;

/**
 * Authenticate Session Class
 * 
 * <p>
 * Used for passthru authentication mechanisms.
 * 
 * @author gkspencer
 */
public class AuthenticateSession extends Session {

	/**
	 * Class constructor
	 * 
	 * @param shr PCShare
	 * @param sess NetworkSession
	 * @param dialect int
	 * @param pkt SMBPacket
	 */
	protected AuthenticateSession(PCShare shr, NetworkSession sess, int dialect, SMBPacket pkt) {
		super(shr, dialect, pkt);

		// Save the session and packet

		setSession(sess);

		// Extract the details from the negotiate response packet

		processNegotiateResponse();
	}

	/**
	 * Perform a session setup to create a session on the remote server validating the user.
	 * 
	 * @param userName String
	 * @param ascPwd ASCII password hash
	 * @param uniPwd Unicode password hash
	 * @param vc Virtual circuit number
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void doSessionSetup(String userName, byte[] ascPwd, byte[] uniPwd, int vc)
		throws IOException, SMBException {

		// Create a session setup packet

		SMBPacket pkt = new SMBPacket();

		pkt.setCommand(PacketTypeV1.SessionSetupAndX);

		// Check if the negotiated SMB dialect is NT LM 1.2 or an earlier dialect

		if ( getDialect() == Dialect.NT) {

			// NT LM 1.2 SMB dialect

			pkt.setParameterCount(13);
			pkt.setAndXCommand(0xFF); // no secondary command
			pkt.setParameter(1, 0); // offset to next command
			pkt.setParameter(2, SessionFactory.DefaultPacketSize());
			pkt.setParameter(3, getMaximumMultiplexedRequests());
			pkt.setParameter(4, vc); // virtual circuit number
			pkt.setParameterLong(5, 0); // session key

			// Set the share password length(s)

			pkt.setParameter(7, ascPwd != null ? ascPwd.length : 0); // ANSI password length
			pkt.setParameter(8, uniPwd != null ? uniPwd.length : 0); // Unicode password length

			pkt.setParameter(9, 0); // reserved, must be zero
			pkt.setParameter(10, 0); // reserved, must be zero

			// Send the client capabilities

			int caps = Capability.V1LargeFiles + Capability.V1Unicode + Capability.V1NTSMBs + Capability.V1NTStatus
					+ Capability.V1RemoteAPIs;
			pkt.setParameterLong(11, caps);

			// Store the encrypted passwords
			//
			// Store the ASCII password hash, if specified

			int pos = pkt.getByteOffset();
			pkt.setPosition(pos);

			if ( ascPwd != null)
				pkt.packBytes(ascPwd, ascPwd.length);

			// Store the Unicode password hash, if specified

			if ( uniPwd != null)
				pkt.packBytes(uniPwd, uniPwd.length);

			// Pack the account/client details

			pkt.packString(userName, false);

			// Check if the share has a domain, if not then use the default domain string

			if ( getPCShare().hasDomain())
				pkt.packString(getPCShare().getDomain(), false);
			else
				pkt.packString(SessionFactory.getDefaultDomain(), false);

			pkt.packString("Java VM", false);
			pkt.packString("JFileSrv", false);

			// Set the packet length

			pkt.setByteCount(pkt.getPosition() - pos);
		}
		else {

			// Earlier SMB dialect

			pkt.setUserId(1);

			pkt.setParameterCount(10);
			pkt.setAndXCommand(0xFF); // no secondary command
			pkt.setParameter(1, 0); // offset to next command
			pkt.setParameter(2, SessionFactory.DefaultPacketSize());
			pkt.setParameter(3, 2); // max multiplexed pending requests
			pkt.setParameter(4, 0); // getSessionId ());
			pkt.setParameter(5, 0);
			pkt.setParameter(6, 0);
			pkt.setParameter(7, ascPwd != null ? ascPwd.length : 0);
			pkt.setParameter(8, 0);
			pkt.setParameter(9, 0);

			// Put the password into the SMB packet

			byte[] buf = pkt.getBuffer();
			int pos = pkt.getByteOffset();

			if ( ascPwd != null) {
				for (int i = 0; i < ascPwd.length; i++)
					buf[pos++] = ascPwd[i];
			}

			// Build the account/client details

			StringBuffer clbuf = new StringBuffer();

			clbuf.append(getPCShare().getUserName());
			clbuf.append((char) 0x00);

			// Check if the share has a domain, if not then use the unknown domain string

			if ( getPCShare().hasDomain())
				clbuf.append(getPCShare().getDomain());
			else
				clbuf.append(SessionFactory.getDefaultDomain());
			clbuf.append((char) 0x00);

			clbuf.append("Java VM");
			clbuf.append((char) 0x00);

			clbuf.append("JFileSrv");
			clbuf.append((char) 0x00);

			// Copy the remaining data to the SMB packet

			byte[] byts = clbuf.toString().getBytes();
			for (int i = 0; i < byts.length; i++)
				buf[pos++] = byts[i];

			int pwdLen = ascPwd != null ? ascPwd.length : 0;
			pkt.setByteCount(pwdLen + byts.length);
		}

		// Exchange an SMB session setup packet with the remote file server

		pkt.ExchangeSMB(this, pkt, true);

		// Save the session user id

		setUserId(pkt.getUserId());

		// Check if the session was created as a guest

		if ( pkt.getParameterCount() >= 3) {

			// Set the guest status for the session

			setGuest(pkt.getParameter(2) != 0 ? true : false);
		}

		// The response packet should also have the server OS, LAN Manager type
		// and primary domain name.

		if ( pkt.getByteCount() > 0) {

			// Get the packet buffer and byte offset

			byte[] buf = pkt.getBuffer();
			int offset = pkt.getByteOffset();
			int maxlen = offset + pkt.getByteCount();

			// Get the server OS

			String srvOS = DataPacker.getString(buf, offset, maxlen);
			setOperatingSystem(srvOS);

			offset += srvOS.length() + 1;
			maxlen -= srvOS.length() + 1;

			// Get the LAN Manager type

			String lanman = DataPacker.getString(buf, offset, maxlen);
			setLANManagerType(lanman);

			// Check if we have the primary domain for this session

			if ( getDomain() == null || getDomain().length() == 0) {

				// Get the domain name string

				offset += lanman.length() + 1;
				maxlen += lanman.length() + 1;

				String dom = DataPacker.getString(buf, offset, maxlen);
				setDomain(dom);
			}
		}

		// Check for a core protocol session, set the maximum packet size

		if ( getDialect() == Dialect.Core || getDialect() == Dialect.CorePlus) {

			// Set the maximum packet size to be used on this session

			setMaximumPacketSize(pkt.getParameter(2));
		}
	}

	/**
	 * Process the negotiate response SMB packet
	 * 
	 */
	private void processNegotiateResponse() {

		// Set the security mode flags

		int keyLen = 0;
		boolean unicodeStr = false;
		int encAlgorithm = PasswordEncryptor.LANMAN;
		int defFlags2 = 0;

		if ( getDialect() == Dialect.NT) {

			// Read the returned negotiate parameters, for NT dialect the parameters are not aligned

			m_pkt.resetParameterPointer();
			m_pkt.skipBytes(2); // skip the dialect index

			setSecurityMode(m_pkt.unpackByte());

			// Set the maximum virtual circuits and multiplxed requests allowed by the server

			setMaximumMultiplexedRequests(m_pkt.unpackWord());
			setMaximumVirtualCircuits(m_pkt.unpackWord());

			// Set the maximum buffer size

			setMaximumPacketSize(m_pkt.unpackInt());

			// Skip the maximum raw buffer size and session key

			m_pkt.skipBytes(8);

			// Set the server capabailities

			setCapabilities(m_pkt.unpackInt());

			// Get the server system time and timezone

			SMBDate srvTime = NTTime.toSMBDate(m_pkt.unpackLong());
			int tzone = m_pkt.unpackWord();

			// Get the encryption key length

			keyLen = m_pkt.unpackByte();

			// Indicate that strings are UniCode

			unicodeStr = true;

			// Use NTLMv1 password encryption

			encAlgorithm = PasswordEncryptor.NTLM1;

			// Set the default flags for subsequent SMB requests

			defFlags2 = SMBPacket.FLG2_LONGFILENAMES + SMBPacket.FLG2_UNICODE + SMBPacket.FLG2_LONGERRORCODE;
		}
		else if ( getDialect() > Dialect.CorePlus) {

			// Set the security mode and encrypted password mode

			int secMode = m_pkt.getParameter(1);
			setSecurityMode((secMode & 0x01) != 0 ? SecurityModeUser : SecurityModeShare);

			if ( m_pkt.getParameterCount() >= 11)
				keyLen = m_pkt.getParameter(11) & 0xFF; // should always be 8

			// Set the maximum virtual circuits and multiplxed requests allowed by the server

			setMaximumMultiplexedRequests(m_pkt.getParameter(3));
			setMaximumVirtualCircuits(m_pkt.getParameter(4));

			// Check if Unicode strings are being used

			if ( m_pkt.isUnicode())
				unicodeStr = true;

			// Set the default flags for subsequent SMB requests

			defFlags2 = SMBPacket.FLG2_LONGFILENAMES;
		}

		// Set the default packet flags for this session

		setDefaultFlags2(defFlags2);

		// Get the server details from the negotiate SMB packet

		if ( m_pkt.getByteCount() > 0) {

			// Get the returned byte area length and offset

			int bytsiz = m_pkt.getByteCount();
			int bytpos = m_pkt.getByteOffset();
			byte[] buf = m_pkt.getBuffer();

			// Extract the challenge response key, if specified

			if ( keyLen > 0) {

				// Allocate a buffer for the challenge response key

				byte[] encryptKey = new byte[keyLen];

				// Copy the challenge response key

				for (int keyIdx = 0; keyIdx < keyLen; keyIdx++)
					encryptKey[keyIdx] = buf[bytpos++];

				// Set the sessions encryption key

				setEncryptionKey(encryptKey);

				// DEBUG

				if ( Debug.EnableInfo && hasDebugOption(DBGDumpPacket)) {
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
			setDomain(dom);

			// DEBUG

			if ( Debug.EnableInfo && hasDebugOption(DBGDumpPacket))
				Debug.println("** Server domain : " + getDomain() + ".");
		}
	}
}
