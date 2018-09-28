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
import org.filesys.smb.dcerpc.client.DCEPacket;

/**
 * LSA RPC Pipe File Class
 * 
 * @author gkspencer
 */
public class LsarpcPipeFile extends IPCPipeFile {

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
	public LsarpcPipeFile(IPCSession sess, DCEPacket pkt, int handle, String name, int maxTx, int maxRx) {
		super(sess, pkt, handle, name, maxTx, maxRx);
	}

	/**
	 * Open the Local Security Authority (LSA) on the remote server
	 * 
	 * @return LSAPolicyHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final LSAPolicyHandle openPolicy()
		throws IOException, SMBException {
		return null;
	}
}
