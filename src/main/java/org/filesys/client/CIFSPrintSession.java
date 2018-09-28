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
import org.filesys.server.filesys.FileAction;
import org.filesys.smb.PCShare;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.SMBException;

/**
 * SMB protocol print session class
 * 
 * @author gkspencer
 */
final class CIFSPrintSession extends PrintSession {

	/**
	 * Class constructor
	 * 
	 * @param shr Remote server details.
	 * @param dialect SMB dialect that this session is using
	 */
	protected CIFSPrintSession(PCShare shr, int dialect) {
		super(shr, dialect);
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
	public final SMBFile OpenSpoolFile(String id, int mode, int setuplen)
		throws java.io.IOException, SMBException {

		// Initialize the SMB request to open a file

		m_pkt.setCommand(PacketTypeV1.OpenAndX);
		m_pkt.setFlags(getDefaultFlags());
		m_pkt.setFlags2(getDefaultFlags2());

		m_pkt.setUserId(this.getUserId());
		m_pkt.setTreeId(this.getTreeId());

		// Set the parameter words

		m_pkt.setParameterCount(15);
		m_pkt.setAndXCommand(0xFF); // no secondary command
		m_pkt.setParameter(1, 0); // offset to next command
		m_pkt.setParameter(2, 0x01); // return additional information
		m_pkt.setParameter(3, 0); // flags
		m_pkt.setParameter(4, 0); // normal files only for now
		m_pkt.setParameter(5, 0); // file attributes
		m_pkt.setParameter(6, 0); // creation time
		m_pkt.setParameter(7, 0); // creation date
		m_pkt.setParameter(8, FileAction.CreateNotExist + FileAction.TruncateExisting);
		m_pkt.setParameter(9, 0); // default allocation on create/truncate (long)
		m_pkt.setParameter(10, 0); // ... high word
		m_pkt.setParameter(11, 0);
		m_pkt.setParameter(12, 0);
		m_pkt.setParameter(13, 0);
		m_pkt.setParameter(14, 0);

		// Pack the file name string

		m_pkt.resetBytePointer();
		m_pkt.packString(id, m_pkt.isUnicode());

		m_pkt.setByteCount();

		// Send/receive the SMB file open packet

		m_pkt.ExchangeSMB(this, m_pkt, true);

		// Check if a valid response was received

		if ( m_pkt.isValidResponse()) {

			// Extract the file information from the received SMB packet

			int fid = m_pkt.getParameter(2);

			// Create a file information object

			FileInfo finfo = new FileInfo(id, 0, 0);

			// Create an SMB file object

			return new CIFSFile(this, finfo, fid);
		}

		// Invalid SMB response

		return null;
	}
}