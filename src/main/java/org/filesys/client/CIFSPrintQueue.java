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

import org.filesys.client.info.PrintQueueEnumerator;
import org.filesys.client.info.PrintJob;

/**
 * SMB CIFS protocol print queue class
 * 
 * @author gkspencer
 */
class CIFSPrintQueue implements PrintQueueEnumerator {

	// Print session that this search is associated with

	private PrintSession m_sess;

	// SMB packet used for the print queue list

	private SMBPacket m_pkt;

	// Number of queue elements to return, per packet.

	private int m_qcnt;

	/**
	 * Class constructor.
	 * 
	 * @param sess SMBPrintSession that this queue is associated with.
	 */

	protected CIFSPrintQueue(PrintSession sess) {
		m_sess = sess;
	}

	/**
	 * Continue the queue search, get the next block of queue elements.
	 * 
	 * @param stidx Starting index for the next block of elements to be returned.
	 * @return true if more elements were returned, else false.
	 * @exception java.io.IOException If an I/O error occurs.
	 */

	protected final boolean ContinueQueueSearch(int stidx)
		throws java.io.IOException {
		return false;
	}

	/**
	 * Return the number if queue elements in the current SMB packet.
	 * 
	 * @return Number of queue elements in the current packet.
	 */

	protected final int getNumberOfQueueElements() {
		return m_pkt.getParameter(0);
	}

	/**
	 * Return the offset of the specified print queue element within the current packet.
	 * 
	 * @param idx Index of the specified print queue element.
	 * @return Offset of the print queue element within the packet buffer.
	 */

	protected final int getPrintEntryOffset(int idx) {
		return 0;
	}

	/**
	 * Return the queue list restart index.
	 * 
	 * @return Queue list restart index.
	 */

	protected final int getRestartIndex() {
		return m_pkt.getParameter(1);
	}

	/**
	 * Return the next print queue entry.
	 * 
	 * @return Next SMBPrintJob in this print queue.
	 * @exception java.io.IOException If an I/O error occurs.
	 */

	public PrintJob nextEntry()
		throws java.io.IOException {
		return null;
	}

	/**
	 * Start a new scan of the print servers queue.
	 * 
	 * @param idx Starting index for the first queue entry to return.
	 * @param cnt Number of queue entries to return. A positive value indicates a forward search,
	 *            and a negative value indicates a reverse search.
	 * @exception java.io.IOException If an I/O error occurs.
	 */

	public void StartQueueSearch(int idx, int cnt)
		throws java.io.IOException {

		// Save the number of queue elements to return

		m_qcnt = cnt;

		// Start the print queue search

		ContinueQueueSearch(idx);
	}
}