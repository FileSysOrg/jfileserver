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

import org.filesys.smb.PCShare;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.SMBDeviceType;
import org.filesys.smb.SMBException;

/**
 * SMB IPC session class
 * 
 * <p>
 * Contains the details of a connection to a remote named pipe service.
 * 
 * @author gkspencer
 */
public abstract class IPCSession extends Session {

	/**
	 * Construct an IPC session
	 * 
	 * @param shr Remote server details.
	 * @param dialect SMB dialect that this session is using
	 */

	protected IPCSession(PCShare shr, int dialect) {
		super(shr, dialect, null);

		// Set the device type for this session

		setDeviceType(SMBDeviceType.Pipe);
	}

	/**
	 * Send/receive an SMB transaction packet on this pipe session
	 * 
	 * @param tpkt SMBTransPacket to send
	 * @param rxpkt Packet to receive the reply into
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */

	public abstract void SendTransaction(TransPacket tpkt, TransPacket rxpkt)
		throws java.io.IOException, SMBException;

	/**
	 * Close the connection to the IPC$ named pipe
	 *
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public void CloseSession()
		throws IOException, SMBException {

		// Build a tree disconnect packet

		m_pkt.setCommand(PacketTypeV1.TreeDisconnect);
		m_pkt.setUserId(getUserId());
		m_pkt.setTreeId(m_treeid);

		m_pkt.setParameterCount(0);
		m_pkt.setByteCount(0);

		// Send the tree disconnect packet

		m_pkt.ExchangeSMB(this, m_pkt);

		// Call the base class

		super.CloseSession();
	}
}