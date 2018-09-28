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

package org.filesys.smb.server.notify;


import java.util.ArrayList;
import java.util.List;

/**
 * Notify Change Event List Class
 *
 * @author gkspencer
 */
public class NotifyChangeEventList {

	//	List of notify events
	
	private List<NotifyChangeEvent> m_list;
	
	/**
	 * Default constructor
	 */
	public NotifyChangeEventList() {
		m_list = new ArrayList<NotifyChangeEvent>();
	}
	
	/**
	 * Return the count of notify events
	 * 
	 * @return int
	 */
	public final int numberOfEvents() {
		return m_list.size();
	}
	
	/**
	 * Return the specified change event
	 * 
	 * @param idx int
	 * @return NotifyChangeEvent
	 */
	public final NotifyChangeEvent getEventAt(int idx) {
		
		//	Range check the index
		
		if ( idx < 0 || idx >= m_list.size())
			return null;
			
		//	Return the required notify event
		
		return m_list.get(idx);
	}
	
	/**
	 * Add a change event to the list
	 * 
	 * @param evt NotifyChangeEvent
	 */
	public final void addEvent(NotifyChangeEvent evt) {
		m_list.add(evt);
	}
	
	/**
	 * Remove the specified change event
	 * 
	 * @param idx int
	 * @return NotifyChangeEvent
	 */
	public final NotifyChangeEvent removeEventAt(int idx) {
		
		//	Range check the index
		
		if ( idx < 0 || idx >= m_list.size())
			return null;
			
		//	Return the required notify event

    return m_list.remove( idx);
	}
	
	/**
	 * Remove all events from the list
	 */
	public final void removeAllEvents() {
		m_list.clear();
	}
}
