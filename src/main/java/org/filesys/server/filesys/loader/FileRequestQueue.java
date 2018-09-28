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
 * File Request Queue Class
 *
 * <p>Synchronized queue of FileRequest objects.
 *
 * @author gkspencer
 */
public class FileRequestQueue {

    //	List of file requests
    private LinkedList<FileRequest> m_queue;

    /**
     * Class constructor
     */
    public FileRequestQueue() {
        m_queue = new LinkedList<FileRequest>();
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
     * @param req FileRequest
     */
    public final synchronized void addRequest(FileRequest req) {

        //	Add the request to the queue
        m_queue.add(req);

        //	Notify workers that there is a request to process
        notifyAll();
    }

    /**
     * Remove a request from the head of the queue
     *
     * @return FileRequest
     * @throws InterruptedException Wait interrupted
     */
    public final synchronized FileRequest removeRequest()
            throws InterruptedException {

        //	Wait until there is a request
        waitWhileEmpty();

        //	Get the request from the head of the queue
        return m_queue.removeFirst();
    }

    /**
     * Remove a request from the head of the queue without waiting
     *
     * @return FileRequest
     */
    public final synchronized FileRequest removeRequestNoWait() {

        //	Get the request from the head of the queue
        return m_queue.removeFirst();
    }

    /**
     * Remove all requests from the queue
     */
    public final void removeAllRequests() {
        m_queue.clear();
    }

    /**
     * Wait for a file request to be added to the queue
     *
     * @throws InterruptedException Wait interrupted
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
     * @throws InterruptedException Wait interrupted
     */
    public final synchronized void waitUntilEmpty()
            throws InterruptedException {

        //	Wait until the request queue is empty
        while (m_queue.size() != 0)
            wait();
    }
}
