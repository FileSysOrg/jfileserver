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
import org.filesys.smb.dcerpc.client.DCEPacket;
import org.filesys.smb.dcerpc.client.InitShutdown;

/**
 * InitShutodwn Pipe File Class
 * 
 * @author gkspencer
 */
public class InitShutPipeFile extends IPCPipeFile {

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
	public InitShutPipeFile(IPCSession sess, DCEPacket pkt, int handle, String name, int maxTx, int maxRx) {
		super(sess, pkt, handle, name, maxTx, maxRx);
	}

	/**
	 * Shutdown a remote system, and optionally reboot the system
	 * 
	 * @param msg String
	 * @param tmo int
	 * @param reboot boolean
	 * @param force boolean
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void shutdownServer(String msg, int tmo, boolean reboot, boolean force)
		throws IOException, SMBException {

		// Build the shutdown server request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		// Host name parameter

		buf.putPointer(true);
		buf.putPointer(true);

		// Message parameter

		buf.putPointer(true);
		buf.putUnicodeHeader(msg, false);
		buf.putString(msg, DCEBuffer.ALIGN_INT);

		buf.putInt(tmo);
		buf.putByte(force ? 1 : 0);
		buf.putByte(reboot ? 1 : 0);

		buf.putInt(0);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), InitShutdown.Init, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the shutdown DCE request

		doDCERequest(pkt);

		// Get the reply status

		buf = getRxBuffer();

		try {
			checkStatus(buf.getInt());
		}
		catch (DCEBufferException ex) {
		}
	}

	/**
	 * Abort a shutdown
	 * 
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void abortShutdown()
		throws IOException, SMBException {

		// Build the abort shutdown request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		// Host name parameter

		buf.putPointer(true);
		buf.putPointer(true);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), InitShutdown.Abort, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the abort shutdown request

		doDCERequest(pkt);

		// Get the reply status

		buf = getRxBuffer();

		try {
			checkStatus(buf.getInt());
		}
		catch (DCEBufferException ex) {
		}
	}
}
