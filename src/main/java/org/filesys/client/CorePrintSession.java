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

import org.filesys.client.info.FileInfo;
import org.filesys.client.info.PrintQueueEnumerator;
import org.filesys.smb.DataType;
import org.filesys.smb.PCShare;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.SMBException;

/**
 * SMB core protocol print session class.
 * 
 * @author gkspencer
 */
class CorePrintSession extends PrintSession {

	/**
	 * Class constructor
	 * 
	 * @param shr Remote server details.
	 * @param dialect SMB dialect that this session is using
	 */
	protected CorePrintSession(PCShare shr, int dialect) {
		super(shr, dialect);
	}

	/**
	 * Return an SMBPrintQueue object that can be used to list the pending print jobs in the print
	 * servers queue.
	 * 
	 * @param idx Specifies the starting index of the first entry in the queue to be returned.
	 * @param cnt Number of entries to return, may be a positive value for a forward search and
	 *            negative for a backward search.
	 * @return SMBPrintQueue for this print queue.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public PrintQueueEnumerator getPrintQueue(int idx, int cnt)
		throws java.io.IOException, SMBException {
		return null;
	}

	/**
	 * Open a spool file on the remote print server.
	 * 
	 * @param id Identifier string for this print request.
	 * @param mode Print mode, either TextMode or GraphicsMode.
	 * @param setuplen Length of data in the start of the spool file that is printer setup code.
	 * @return SMBFile for the new spool file, else null.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */

	public SMBFile OpenSpoolFile(String id, int mode, int setuplen)
		throws java.io.IOException, SMBException {

		// Initialize the SMB requets to open a spool file

		m_pkt.setCommand(PacketTypeV1.OpenPrintFile);
		m_pkt.setFlags(0);

		// Set the parameter words

		m_pkt.setParameterCount(2);
		m_pkt.setParameter(0, setuplen);
		m_pkt.setParameter(1, mode);

		// Set the user id

		m_pkt.setUserId(this.getUserId());

		// Set the tree id

		m_pkt.setTreeId(this.getTreeId());

		// Build the spool file identifier

		StringBuffer idbuf = new StringBuffer();

		idbuf.append((char) DataType.ASCII);
		idbuf.append(id);
		idbuf.append((char) 0x00);

		m_pkt.setBytes(idbuf.toString().getBytes());

		// Send/receive the SMB open spool file packet

		m_pkt.ExchangeSMB(this, m_pkt);

		// Check if a valid response was received

		if ( m_pkt.isValidResponse()) {

			// Extract the file information from the received SMB packet

			int fid = m_pkt.getParameter(0);

			// Create a file information object

			FileInfo finfo = new FileInfo(id, 0, 0);

			// Create an SMB file object

			return new CoreFile(this, finfo, fid);
		}

		// Invalid SMB response

		return null;
	}
}