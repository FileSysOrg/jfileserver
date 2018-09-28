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

import org.filesys.client.Session;
import org.filesys.client.TransPacket;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.smb.PacketTypeV1;
import org.filesys.util.DataPacker;

/**
 * Client Transaction Packet Class
 * 
 * <p>
 * Provides methods for building client-side SMB transaction requests.
 * 
 * @author gkspencer
 */
class ClientTransPacket extends TransPacket {

	/**
	 * Class constructor
	 * 
	 * @param buf Buffer that contains the SMB transaction packet.
	 */
	public ClientTransPacket(byte[] buf) {
		super(buf);
	}

	/**
	 * Class constructor
	 * 
	 * @param siz Size of packet to allocate.
	 */
	public ClientTransPacket(int siz) {
		super(siz);
	}

	/**
	 * Initialize the transact SMB packet
	 * 
	 * @param sess Session to get the unique multiplex id for this transaction
	 * @param pcnt Total parameter count for this transaction
	 * @param paramblk Parameter block data bytes
	 * @param plen Parameter block data length
	 * @param datablk Data block data bytes
	 * @param dlen Data block data length
	 */
	public final void InitializeTransact(Session sess, int pcnt, byte[] paramblk, int plen, byte[] datablk, int dlen) {

		// Set the SMB command code

		if ( m_transName == null)
			setCommand(PacketTypeV1.Transaction2);
		else
			setCommand(PacketTypeV1.Transaction);

		// Set the parameter count

		setParameterCount(pcnt);

		// Set the multiplex id

		setMultiplexId(sess.getNextMultiplexId());

		// Save the parameter count, add an extra parameter for the data byte count

		m_paramCnt = pcnt;

		// Initialize the parameters

		setParameter(0, plen); // total parameter bytes being sent
		setParameter(1, dlen); // total data bytes being sent

		for (int i = 2; i < 9; setParameter(i++, 0))
			;

		setParameter(9, plen); // parameter bytes sent in this packet
		setParameter(11, dlen); // data bytes sent in this packet

		setParameter(13, pcnt - StandardParams); // number of setup words

		// Get the data byte offset

		int pos = getByteOffset();
		int startPos = pos;

		// Check if this is a named transaction, if so then store the name

		int idx;
		byte[] buf = getBuffer();

		if ( m_transName != null) {

			// Store the transaction name

			byte[] nam = m_transName.getBytes();

			for (idx = 0; idx < nam.length; idx++)
				buf[pos++] = nam[idx];
		}

		// Align the buffer offset

		pos = DataPacker.longwordAlign(pos);

		// Store the parameter block

		if ( paramblk != null) {

			// Set the parameter block offset

			setParameter(10, pos - RFCNetBIOSProtocol.HEADER_LEN);

			// Store the parameter block

			System.arraycopy(paramblk, 0, buf, pos, plen);
			pos += plen;
		}
		else {

			// Clear the parameter block offset

			setParameter(10, 0);
		}

		// Store the data block

		if ( datablk != null) {

			// Word align the data block

			pos = DataPacker.longwordAlign(pos);

			// Set the data block offset

			setParameter(12, pos - RFCNetBIOSProtocol.HEADER_LEN);

			// Store the data block

			System.arraycopy(datablk, 0, buf, pos, dlen);
			pos += dlen;
		}
		else {

			// Zero the data block offset

			setParameter(12, 0);
		}

		// Set the byte count for the SMB packet

		setByteCount(pos - startPos);
	}
}
