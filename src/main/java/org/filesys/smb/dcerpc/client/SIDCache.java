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

package org.filesys.smb.dcerpc.client;

import java.util.*;

import org.filesys.smb.nt.SID;

/**
 * SID Cache Class
 * 
 * <p>
 * Cache used to hold SIDs from lookups on a remote server to prevent multiple lookups for the same
 * name/id.
 * 
 * @author gkspencer
 */
public class SIDCache {

	// SID cache
	private Hashtable<String, SID> m_cache;

	/**
	 * Default constructor
	 */
	public SIDCache() {
		m_cache = new Hashtable<String, SID>();
	}

	/**
	 * Return the number of SIDs in the cache
	 * 
	 * @return int
	 */
	public final int numberOfSIDs() {
		return m_cache.size();
	}

	/**
	 * Add a SID to the cache
	 * 
	 * @param name String
	 * @param sid SID
	 */
	public final void addSID(String name, SID sid) {
		m_cache.put(name, sid);
	}

	/**
	 * Return the SID for the specified name
	 * 
	 * @param name String
	 * @return SID
	 */
	public final SID findSID(String name) {
		return m_cache.get(name);
	}

	/**
	 * Find the name of the matching SID
	 * 
	 * @param sid SID
	 * @return String
	 */
	public final String findName(SID sid) {

		// Enumerate the names, get the corresponding SID and compare to the required SID
		Enumeration<String> names = m_cache.keys();

		while (names.hasMoreElements()) {

			// Get the current name
			String name = (String) names.nextElement();

			// Get the associated SID
			SID curSID = (SID) m_cache.get(name);

			// Check if the SID matches
			if ( curSID.equalsSID(sid))
				return name;
		}

		// Match not found
		return null;
	}

	/**
	 * Delete a SID from the cache
	 * 
	 * @param index String
	 * @return SID
	 */
	public final SID removeSID(String index) {
		return (SID) m_cache.remove(index);
	}

	/**
	 * Enumerate the names in the cache
	 * 
	 * @return Enumeration of names
	 */
	public final Enumeration<String> enumerateNames() {
		return m_cache.keys();
	}

	/**
	 * Enumerate the SIDs in the cache
	 * 
	 * @return Enumeration of SIDs
	 */
	public final Enumeration<SID> enumerateSIDs() {
		return m_cache.elements();
	}

	/**
	 * Clear all SIDs from the cache
	 */
	public final void removeAllSIDs() {
		m_cache.clear();
	}
}
