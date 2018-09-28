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
 * SMB server list class
 * 
 * <p>
 * The ServerList contains a list of ServerInfo objects.
 * 
 * <p>
 * An ServerList is returned by the AdminSession.getServerList () method.
 * 
 * @author gkspencer
 */
public class ServerList implements Serializable {

	// Server list vector

	private List<RAPServerInfo> m_list;

	/**
	 * Class constructor
	 */
	public ServerList() {
		m_list = new ArrayList<RAPServerInfo>();
	}

	/**
	 * Add a server information object to the list.
	 * 
	 * @param srvinf ServerInfo to add to the list.
	 */
	public final void addServerInfo(RAPServerInfo srvinf) {
		m_list.add(srvinf);
	}

	/**
	 * Clear all server information objects from the list
	 * 
	 */
	public final void clearList() {
		m_list.clear();
	}

	/**
	 * Get a server information object from the list
	 * 
	 * @param idx Index of the server information to return
	 * @return ServerInfo for the required server.
	 * @exception java.lang.ArrayIndexOutOfBoundsException If the index is invalid
	 */
	public final RAPServerInfo getServerInfo(int idx)
		throws ArrayIndexOutOfBoundsException {

		// Bounds check the index

		if ( idx >= m_list.size())
			throw new ArrayIndexOutOfBoundsException();

		// Return the required server information

		return m_list.get(idx);
	}

	/**
	 * Get the number of servers in the list
	 * 
	 * @return Number of ServerInfo objects in the list.
	 */
	public final int NumberOfServers() {
		return m_list.size();
	}
}