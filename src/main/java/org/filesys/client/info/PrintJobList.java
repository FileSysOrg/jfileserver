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

import java.util.ArrayList;
import java.util.List;

/**
 * SMB print job list class
 * 
 * <p>
 * The PrintJobList class contains a list of PrintJob objects.
 * 
 * <p>
 * The AdminSession.getPrintJobs () method returns an PrintJobList containing the active/pending
 * print jobs for the specified remote print queue.
 * 
 * @author gkspencer
 */
public final class PrintJobList implements java.io.Serializable {

	// Print job list vector

	private List<PrintJob> m_list;

	/**
	 * Class constructor
	 */
	public PrintJobList() {
		m_list = new ArrayList<PrintJob>();
	}

	/**
	 * Add a print job object to the list.
	 * 
	 * @param prnjob PrintJob to add to the list.
	 */
	public final void addPrintJob(PrintJob prnjob) {
		m_list.add(prnjob);
	}

	/**
	 * Clear all print jobs from the list
	 * 
	 */
	public final void clearList() {
		m_list.clear();
	}

	/**
	 * Get a print job object from the list
	 * 
	 * @param idx Index of the print job to return
	 * @return PrintJob for the required printer.
	 * @exception java.lang.ArrayIndexOutOfBoundsException If the index is invalid
	 */
	public final PrintJob getPrintJob(int idx)
		throws java.lang.ArrayIndexOutOfBoundsException {

		// Bounds check the index

		if ( idx >= m_list.size())
			throw new java.lang.ArrayIndexOutOfBoundsException();

		// Return the required print job

		return m_list.get(idx);
	}

	/**
	 * Get the number of print jobs in the list
	 * 
	 * @return Number of PrintJob objects in the list.
	 */
	public final int NumberOfJobs() {
		return m_list.size();
	}
}