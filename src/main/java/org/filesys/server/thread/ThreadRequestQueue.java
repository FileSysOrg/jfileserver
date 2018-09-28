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

package org.filesys.server.thread;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Thread Request Queue Class
 *
 * <p>
 * Provides a request queue for a thread pool of worker threads.
 *
 * @author gkspencer
 */
public class ThreadRequestQueue {

    // List of requests
    private Queue<ThreadRequest> m_queue;

    /**
     * Class constructor
     */
    public ThreadRequestQueue() {
        m_queue = new LinkedList<ThreadRequest>();
    }

    /**
     * Return the number of requests in the queue
     *
     * @return int
     */
    public final int numberOfRequests() {
        synchronized (m_queue) {
            return m_queue.size();
        }
    }

    /**
     * Add a request to the queue
     *
     * @param req ThreadRequest
     */
    public final void addRequest(ThreadRequest req) {

        synchronized (m_queue) {

            // Add the request to the queue
            m_queue.add(req);

            // Notify a worker that there is a request to process
            m_queue.notify();
        }
    }

    /**
     * Add requests to the queue
     *
     * @param reqList List of ThreadRequest objects
     */
    public final void addRequests(List<ThreadRequest> reqList) {

        synchronized (m_queue) {

            // Add the requests to the queue
            m_queue.addAll(reqList);
            m_queue.notify();
        }
    }

    /**
     * Remove a request from the head of the queue
     *
     * @return ThreadRequest
     * @throws InterruptedException Wait interrupted
     */
    public final ThreadRequest removeRequest()
            throws InterruptedException {

        synchronized (m_queue) {

            // Wait until there is a request
            while (m_queue.size() == 0)
                m_queue.wait();

            // Get the request from the head of the queue
            return m_queue.poll();
        }
    }

    /**
     * Wait for a request to be added to the queue
     *
     * @throws InterruptedException Wait interrupted
     */
    public final void waitWhileEmpty()
            throws InterruptedException {

        synchronized (m_queue) {

            // Wait until some work arrives on the queue
            while (m_queue.size() == 0)
                m_queue.wait();
        }
    }

    /**
     * Wait for the request queue to be emptied
     *
     * @throws InterruptedException Wait interrupted
     */
    public final void waitUntilEmpty()
            throws InterruptedException {

        synchronized (m_queue) {

            // Wait until the request queue is empty
            while (m_queue.size() != 0)
                m_queue.wait();
        }
    }
}
