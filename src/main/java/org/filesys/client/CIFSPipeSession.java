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

import org.filesys.smb.PCShare;
import org.filesys.smb.SMBException;

/**
 * SMB pipe session class
 * 
 * <p>
 * Used when connecting to the special IPC$ named pipe on a remote server, that is used to access
 * DCE/RPC services on a remote server.
 * 
 * @author gkspencer
 */
public final class CIFSPipeSession extends IPCSession {

	/**
	 * Class constructor
	 * 
	 * @param shr Remote server details.
	 * @param dialect SMB dialect that this session is using
	 */

	protected CIFSPipeSession(PCShare shr, int dialect) {
		super(shr, dialect);
	}

	/**
	 * Close this connection with the remote server.
	 * 
	 * @exception java.io.IOException If an I/O error occurs.
	 */

	public void CloseSession()
		throws java.io.IOException, SMBException {

		// Close the network session

		super.CloseSession();
	}

	/**
	 * Send/receive an SMB transaction packet on this pipe session
	 * 
	 * @param tpkt SMBTransPacket to send
	 * @param rxpkt Packet to receive the reply into
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */

	public void SendTransaction(TransPacket tpkt, TransPacket rxpkt)
		throws java.io.IOException, SMBException {

		// Exchange the SMB transaction with the server

		tpkt.ExchangeSMB(this, rxpkt);
	}
}