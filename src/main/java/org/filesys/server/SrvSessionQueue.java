/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
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
package org.filesys.server;

import java.util.LinkedList;

/**
 * Server Session Queue Class
 * 
 * @author gkspencer
 */
public class SrvSessionQueue {

	// List of sessions
	private LinkedList<SrvSession> m_queue;

	/**
	 * Class constructor
	 */
	public SrvSessionQueue() {
		m_queue = new LinkedList<SrvSession>();
	}

	/**
	 * Return the number of sessions in the queue
	 * 
	 * @return int
	 */
	public final synchronized int numberOfSessions() {
		return m_queue.size();
	}

	/**
	 * Add a session to the queue
	 * 
	 * @param sess SrvSession
	 */
	public final synchronized void addSession(SrvSession sess) {

		// Add the session to the queue
		m_queue.add( sess);

		// Notify a listener that there is a session to process
		notify();
	}

	/**
	 * Remove a session from the head of the queue
	 * 
	 * @return SrvSession
	 * @exception InterruptedException Wait interrupted
	 */
	public final synchronized SrvSession removeSession()
		throws InterruptedException {

		// Wait until there is a session
		waitWhileEmpty();

		// Get the session from the head of the queue
		return m_queue.removeFirst();
	}

	/**
	 * Remove a session from the queue, without waiting if there are no sessions in the queue
	 * 
	 * @return SrvSession
	 */
	public final synchronized SrvSession removeSessionNoWait() {
		
		SrvSession sess = null;
		
		if ( m_queue.size() > 0)
			sess = m_queue.removeFirst();
			
		return sess;
	}
	
	/**
	 * Wait for a session to be added to the queue
	 * 
	 * @exception InterruptedException Wait interrupted
	 */
	public final synchronized void waitWhileEmpty()
		throws InterruptedException {

		// Wait until a session arrives on the queue
		while (m_queue.size() == 0)
			wait();
	}

	/**
	 * Wait for the session queue to be emptied
	 * 
	 * @exception InterruptedException Wait interrupted
	 */
	public final synchronized void waitUntilEmpty()
		throws InterruptedException {

		// Wait until the session queue is empty
		while (m_queue.size() != 0)
			wait();
	}
}
