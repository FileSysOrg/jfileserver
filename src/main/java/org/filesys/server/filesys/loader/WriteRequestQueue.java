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

package org.filesys.server.filesys.loader;

import java.util.LinkedList;

/**
 * Write Request Queue Class
 *
 * @author gkspencer
 */
public class WriteRequestQueue {

    //	List of file requests
    private LinkedList<WriteRequest> m_queue;

    /**
     * Class constructor
     */
    public WriteRequestQueue() {
        m_queue = new LinkedList<WriteRequest>();
    }

    /**
     * Return the number of requests in the queue
     *
     * @return int
     */
    public final synchronized int numberOfRequests() {
        return m_queue.size();
    }

    /**
     * Add a request to the queue
     *
     * @param req WriteRequest
     */
    public final synchronized void addRequest(WriteRequest req) {

        //	Add the request to the queue
        m_queue.add(req);

        //	Notify workers that there is a request to process
        notifyAll();
    }

    /**
     * Remove a request from the head of the queue
     *
     * @return WriteRequest
     * @throws InterruptedException Error during wait
     */
    public final synchronized WriteRequest removeRequest()
            throws InterruptedException {

        //	Wait until there is a request
        waitWhileEmpty();

        //	Get the request from the head of the queue
        return m_queue.removeFirst();
    }

    /**
     * Wait for a write request to be added to the queue
     *
     * @throws InterruptedException Error during wait
     */
    public final synchronized void waitWhileEmpty()
            throws InterruptedException {

        //	Wait until some work arrives on the queue
        while (m_queue.size() == 0)
            wait();
    }

    /**
     * Wait for the file request queue to be emptied
     *
     * @throws InterruptedException Error during wait
     */
    public final synchronized void waitUntilEmpty()
            throws InterruptedException {

        //	Wait until the request queue is empty
        while (m_queue.size() != 0)
            wait();
    }
}
