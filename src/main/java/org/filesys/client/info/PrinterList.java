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

package org.filesys.client.info;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * SMB printer list class
 * 
 * <p>
 * The PrinterList class contains a list of PrintQueueInfo ojbects, which contain details of a
 * remote print queue.
 * 
 * <p>
 * The AdminSession.getPrinterList () method returns an PrinterList containing the list of remote
 * print queues on the remote server.
 * 
 * @author gkspencer
 */
public class PrinterList implements Serializable {

	// Printer list vector

	private List<PrintQueueInfo> m_list;

	/**
	 * Class constructor
	 */
	public PrinterList() {
		m_list = new ArrayList<PrintQueueInfo>();
	}

	/**
	 * Add a printer queue information object to the list.
	 * 
	 * @param prninf PrintQueueInfo to add to the list.
	 */
	public final void addPrinterInfo(PrintQueueInfo prninf) {
		m_list.add(prninf);
	}

	/**
	 * Clear all printer information objects from the list
	 * 
	 */
	public final void clearList() {
		m_list.clear();
	}

	/**
	 * Get a printer queue information object from the list
	 * 
	 * @param idx Index of the printer information to return
	 * @return PrintQueueInfo for the required printer.
	 * @exception java.lang.ArrayIndexOutOfBoundsException If the index is invalid
	 */
	public final PrintQueueInfo getPrinterInfo(int idx)
		throws ArrayIndexOutOfBoundsException {

		// Bounds check the index

		if ( idx >= m_list.size())
			throw new ArrayIndexOutOfBoundsException();

		// Return the required printer information

		return m_list.get(idx);
	}

	/**
	 * Get the number of printers in the list
	 * 
	 * @return Number of SMBPrintQueueInfo objects in the list.
	 */
	public final int NumberOfPrinters() {
		return m_list.size();
	}
}