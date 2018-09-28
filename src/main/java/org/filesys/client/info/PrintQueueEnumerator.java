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

import java.io.IOException;

/**
 * SMB print queue interface
 * 
 * @author gkspencer
 */
public interface PrintQueueEnumerator {

	/**
	 * Return the next print queue entry.
	 * 
	 * @return Next PrintJob in this print queue.
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public PrintJob nextEntry()
		throws IOException;

	/**
	 * Start a new scan of the print servers queue.
	 * 
	 * @param idx Starting index for the first queue entry to return.
	 * @param cnt Number of queue entries to return. A positive value indicates a forward search,
	 *            and a negative value indicates a reverse search.
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public void StartQueueSearch(int idx, int cnt)
		throws IOException;
}