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
import org.filesys.smb.SMBException;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.client.DCEPacket;
import org.filesys.smb.dcerpc.client.NetLogon;

/**
 * NetLogon Pipe File Class
 * 
 * @author gkspencer
 */
public class NetLogonPipeFile extends IPCPipeFile {

	/**
	 * Class constructor
	 * 
	 * @param sess SMBIPCSession
	 * @param pkt DCEPacket
	 * @param handle int
	 * @param name String
	 * @param maxTx int
	 * @param maxRx int
	 */
	public NetLogonPipeFile(IPCSession sess, DCEPacket pkt, int handle, String name, int maxTx, int maxRx) {
		super(sess, pkt, handle, name, maxTx, maxRx);
	}

	/**
	 * Get a server challenge
	 * 
	 * @param client String
	 * @param challenge byte[]
	 * @return byte[]
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final byte[] getServerChallenge(String client, byte[] challenge)
		throws IOException, SMBException {

		// Build the remote server name string

		String remName = "\\\\" + getSession().getPCShare().getNodeName();

		// Build the server request challenge request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putPointer(true);
		buf.putString(remName, DCEBuffer.ALIGN_INT, true);
		buf.putString(client, DCEBuffer.ALIGN_INT, true);
		buf.putBytes(challenge, 8);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), NetLogon.NetrServerRequestChallenge, buf, getMaximumTransmitSize(),
					getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the request

		doDCERequest(pkt);

		// Retrieve the server challenge

		DCEBuffer rxBuf = getRxBuffer();
		byte[] srvChallenge = new byte[8];

		try {
			checkStatus(rxBuf.getStatusCode());
			rxBuf.getBytes(srvChallenge, 8);
		}
		catch (DCEBufferException ex) {
		}

		// Return the server challenge

		return srvChallenge;
	}
}
