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

/**
 * Asynchronous Request Class
 * 
 * <p>Abstract class used to track the details of an asynchronous SMB request where the request is sent to the server but no
 * reply is received until a particular event occurs on the server, such as a directory change notification.
 * 
 * @author gkspencer
 */
public abstract class AsynchRequest {

	//	Multiplex id that uniquely identifies this request
	
	private int m_id;
	
	//	Request name
	
	private String m_name;
	
	//	Asynchronous request completed flag
	
	private boolean m_completed;
	
	//	Auto-reset flag, used for asynchronous requests that need to be setup again each they have completed
	
	private boolean m_autoReset;
	
	/**
	 * Class constructor
	 * 
	 * @param mid int
	 */
	protected AsynchRequest(int mid) {
		m_id = mid;
	}

	/**
	 * Class constructor
	 * 
	 * @param mid int
	 * @param name String
	 */
	protected AsynchRequest(int mid, String name) {
		m_id = mid;
		m_name = name;
	}

	/**
	 * Get the request id
	 * 
	 * @return int
	 */
	public final int getId() {
		return m_id;
	}

	/**
	 * Return the request name
	 * 
	 * @return String
	 */
	public final String getName() {
		return m_name != null ? m_name : "";
	}

	/**
	 * Check if the asynchronous request has completed
	 * 
	 * @return boolean
	 */
	public final boolean hasCompleted() {
		return m_completed;
	}

	/**
	 * Check if the request should be automatically reset
	 * 
	 * @return boolean
	 */
	public final boolean hasAutoReset() {
		return m_autoReset;
	}

	/**
	 * Enable/disable auto-reset of the request
	 * 
	 * @param auto boolean
	 */
	public final void setAutoReset(boolean auto) {
		m_autoReset = auto;
	}

	/**
	 * Process the asynchronous response packet for this request
	 * 
	 * @param sess Session
	 * @param pkt SMBPacket
	 */
	protected abstract void processResponse(Session sess, SMBPacket pkt);

	/**
	 * Resubmit the request to the server
	 * 
	 * @param sess Session
	 * @param pkt SMBPacket
	 * @return boolean
	 */
	protected abstract boolean resubmitRequest(Session sess, SMBPacket pkt);

	/**
	 * Set the asynchronous request completion status
	 * 
	 * @param sts boolean
	 */
	protected final void setCompleted(boolean sts) {
		m_completed = sts;
	}

	/**
	 * Set the request id
	 * 
	 * @param id int
	 */
	protected final void setId(int id) {
		m_id = id;
	}

	/**
	 * Set the request name
	 * 
	 * @param name String
	 */
	protected final void setName(String name) {
		m_name = name;
	}

	/**
	 * Return the request as a string
	 * 
	 * @return String
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();

		str.append("[");
		str.append(getId());
		str.append(":");
		str.append(getName());
		str.append(":");
		str.append(hasCompleted() ? "Completed" : "Pending");
		if ( hasAutoReset())
			str.append(",Auto");
		str.append("]");

		return str.toString();
	}

	/**
	 * Compare objects for equality
	 * 
	 * @return boolean
	 */
	public boolean equals(Object obj) {

		// Check if the object is the same type

		if ( obj instanceof AsynchRequest) {

			// Compare the request id

			AsynchRequest ar = (AsynchRequest) obj;
			return ar.getId() == getId();
		}

		// Not the same object type

		return false;
	}

	/**
	 * Return a hashcode for the request
	 * 
	 * @return int
	 */
	public int hashCode() {
		return getId();
	}
}
