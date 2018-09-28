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

import java.io.IOException;

import org.filesys.client.IPCSession;
import org.filesys.smb.SMBException;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.Wkssvc;
import org.filesys.smb.dcerpc.client.DCEPacket;
import org.filesys.smb.dcerpc.info.WorkstationInfo;

/**
 * Workstation Service Pipe File Class
 * 
 * <p>
 * Pipe file connected to a remote workstation DCE/RPC service that can be used to receive
 * information about the remote workstation.
 * 
 * @author gkspencer
 */
public class WkssvcPipeFile extends IPCPipeFile {

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
	public WkssvcPipeFile(IPCSession sess, DCEPacket pkt, int handle, String name, int maxTx, int maxRx) {
		super(sess, pkt, handle, name, maxTx, maxRx);
	}

	/**
	 * Return the workstation information
	 * 
	 * @return WorkstationInfo
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final WorkstationInfo getWorkstationInformation()
		throws IOException, SMBException {

		// Build the remote server name string

		String remName = "\\\\" + getSession().getPCShare().getNodeName();

		// Build the get workstation information request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putPointer(true);
		buf.putString(remName, DCEBuffer.ALIGN_INT, true);
		buf.putInt(WorkstationInfo.InfoLevel100); // information level

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Wkssvc.NetWkstaGetInfo, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the get workstation information request

		doDCERequest(pkt);

		// Retrieve the workstation information from the response

		DCEBuffer rxBuf = getRxBuffer();
		WorkstationInfo wksInfo = new WorkstationInfo(WorkstationInfo.InfoLevel100);

		try {
			checkStatus(rxBuf.getStatusCode());
			wksInfo.readObject(rxBuf);
		}
		catch (DCEBufferException ex) {
		}

		// Return the workstation information

		return wksInfo;
	}
}
