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

import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

import org.filesys.debug.Debug;

/**
 * Thread Request Pool Class
 *
 * <p>
 * Thread pool that processes a queue of thread requests.
 *
 * @author gkspencer
 */
public class ThreadRequestPool {

    // Default/minimum/maximum number of worker threads to use
    public static final int DefaultWorkerThreads = 25;
    public static final int MinimumWorkerThreads = 4;
    public static final int MaximumWorkerThreads = 250;

    // Initial size of the timed request queue
    public static final int TimedQueueInitialSize = 20;

    // Interval to sleep when waiting for a request to be queued
    private static long WaitForRequestSleep = 24 * 60 * 60000L; //  1 day

    // Queue of requests
    private ThreadRequestQueue m_queue;

    // Queue of timed requests, in time order, and timed request processing thread
    private PriorityBlockingQueue<TimedThreadRequest> m_timedQueue;

    // Timed request processor thread
    private TimedRequestProcessor m_timedProcessor;

    // Worker threads
    private ThreadWorker[] m_workers;

    // Debug enable flag
    protected boolean m_debug;
    protected boolean m_timedDebug;

    /**
     * Thread Worker Inner Class
     */
    protected class ThreadWorker implements Runnable {

        // Worker thread
        private Thread mi_thread;

        // Shutdown flag
        private boolean mi_shutdown = false;

        /**
         * Class constructor
         *
         * @param name String
         */
        public ThreadWorker(String name) {

            // Create the worker thread
            mi_thread = new Thread(this);
            mi_thread.setName(name);
            mi_thread.setDaemon(true);
            mi_thread.start();
        }

        /**
         * Request the worker thread to shutdown
         */
        public final void shutdownRequest() {
            mi_shutdown = true;
            try {
                mi_thread.interrupt();
            }
            catch (Exception ex) {
            }
        }

        /**
         * Run the thread
         */
        public void run() {

            // Loop until shutdown
            ThreadRequest threadReq = null;

            while (mi_shutdown == false) {

                try {

                    // Wait for an request to be queued
                    threadReq = m_queue.removeRequest();
                }
                catch (InterruptedException ex) {

                    // Check for shutdown
                    if (mi_shutdown == true)
                        break;
                }
                catch (Throwable ex2) {
                    ex2.printStackTrace();
                }

                // If the request is valid process it
                if (threadReq != null) {

                    // DEBUG
                    if (hasDebug())
                        Debug.println("Worker " + Thread.currentThread().getName() + ": Req=" + threadReq);

                    try {

                        // Process the request
                        threadReq.runRequest();
                    }
                    catch (Throwable ex) {

                        // Do not display errors if shutting down
                        if (mi_shutdown == false) {
                            Debug.println("Worker " + Thread.currentThread().getName() + ":");
                            Debug.println(ex);
                        }
                    }
                }
            }
        }
    }

    ;

    /**
     * Timed Request Processor Thread Inner Class
     */
    protected class TimedRequestProcessor implements Runnable {

        // Processor thread
        private Thread mi_thread;

        // Shutdown flag
        private boolean mi_shutdown = false;

        /**
         * Class constructor
         */
        public TimedRequestProcessor() {

            // Create the worker thread
            mi_thread = new Thread(this);
            mi_thread.setName("TimedRequestProcessor");
            mi_thread.setDaemon(true);
            mi_thread.start();
        }

        /**
         * Request the worker thread to shutdown
         */
        public final void shutdownRequest() {
            mi_shutdown = true;
            try {
                mi_thread.interrupt();
            }
            catch (Exception ex) {
            }
        }

        /**
         * Run the thread
         */
        public void run() {

            // Loop until shutdown
            while (mi_shutdown == false) {

                try {

                    // Sleep until the first queued event is due to be run, or sleep until a request is queued
                    if (m_timedQueue.size() == 0) {

                        // DEBUG
                        if (hasTimedDebug())
                            Debug.println("Waiting for timed request ...");

                        // Sleep until a request is queued
                        Thread.sleep(WaitForRequestSleep);
                    } else {

                        // Determine when the head of the queue request is due to run, sleep for the required time
                        TimedThreadRequest queueHead = m_timedQueue.peek();

                        if (queueHead != null) {

                            // Check if the queue only contains paused requests
                            if (queueHead.isPaused() == false) {

                                // Calculate the time to sleep until the request is due to run
                                long sleepTime = queueHead.getRunAtTime() - System.currentTimeMillis();
                                if (sleepTime > 0) {

                                    // DEBUG
                                    if (hasTimedDebug())
                                        Debug.println("Next timed request due in " + sleepTime + "ms ...");

                                    // Sleep until the request is due to run
                                    Thread.sleep(sleepTime);
                                }

                                //  Remove the head of the timed request queue and pass the request to the thread pool for
                                //  processing
                                queueHead = m_timedQueue.poll();
                                if (queueHead != null) {

                                    // DEBUG
                                    if (hasTimedDebug())
                                        Debug.println("Passing timed request to thread pool - " + queueHead + ", queue size = " + m_timedQueue.size());

                                    // Pass the request to the thread pool for processing
                                    queueRequest(queueHead);
                                }
                            } else {

                                // DEBUG
                                if (hasTimedDebug() && m_timedQueue != null)
                                    Debug.println("Waiting for timed request, none active (" + m_timedQueue.size() + ") ...");

                                // No active requests on the queue, sleep until an active request is queued or existing request becomes active
                                Thread.sleep(WaitForRequestSleep);
                            }
                        }
                    }
                }
                catch (InterruptedException ex) {

                    // Check for shutdown
                    if (mi_shutdown == true)
                        break;
                }
                catch (Throwable ex2) {
                    Debug.println(ex2);
                }
            }
        }

        /**
         * Wake up the processor thread to reset the sleep timer
         */
        public final void wakeupProcessor() {
            mi_thread.interrupt();
        }
    }

    ;

    /**
     * Class constructor
     *
     * @param threadName String
     */
    public ThreadRequestPool(String threadName) {
        this(threadName, DefaultWorkerThreads);
    }

    /**
     * Class constructor
     *
     * @param threadName String
     * @param poolSize   int
     */
    public ThreadRequestPool(String threadName, int poolSize) {

        // Create the request queue
        m_queue = new ThreadRequestQueue();

        // Create the timed request queue
        m_timedQueue = new PriorityBlockingQueue<TimedThreadRequest>(TimedQueueInitialSize);

        // Check that we have at least minimum worker threads
        if (poolSize < MinimumWorkerThreads)
            poolSize = MinimumWorkerThreads;

        // Create the worker threads
        m_workers = new ThreadWorker[poolSize];

        for (int i = 0; i < m_workers.length; i++)
            m_workers[i] = new ThreadWorker(threadName + (i + 1));

        // Create the timed request processor
        m_timedProcessor = new TimedRequestProcessor();
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Check if timed request debugging is enabled
     *
     * @return boolean
     */
    public final boolean hasTimedDebug() {
        return m_timedDebug;
    }

    /**
     * Return the number of requests in the queue
     *
     * @return int
     */
    public final int getNumberOfRequests() {
        return m_queue.numberOfRequests();
    }

    /**
     * Queue a request to the thread pool for processing
     *
     * @param req ThreadRequest
     */
    public final void queueRequest(ThreadRequest req) {
        m_queue.addRequest(req);
    }

    /**
     * Queue a number of requests to the thread pool for processing
     *
     * @param reqList List of thread requests
     */
    public final void queueRequests(List<ThreadRequest> reqList) {
        m_queue.addRequests(reqList);
    }

    /**
     * Queue a timed request to the thread pool for processing at a particular time
     *
     * @param timedReq TimedThreadRequest
     */
    public final void queueTimedRequest(TimedThreadRequest timedReq) {

        // Check if the request is already associated with the thread pool, it might be a requeue
        if (timedReq.hasThreadRequestPool()) {

            // Remove the request from the thread pool before requeueing it
            boolean wasRemoved = timedReq.getThreadRequestPool().removeTimedRequest(timedReq);

            // DEBUG
            if (hasTimedDebug())
                Debug.println("Removed timed request " + timedReq + ", removed=" + wasRemoved);
        }

        // Add, or requeue, the request
        synchronized (m_timedQueue) {

            // Get the current head of the timed request queue, may be null
            TimedThreadRequest queueHead = m_timedQueue.peek();

            // Add the new request to the queue
            m_timedQueue.add(timedReq);
            timedReq.setThreadRequestPool(this);

            // DEBUG
            if (hasTimedDebug()) {
                Debug.println("Queued timed request " + timedReq);
                Debug.println("  Queue=" + m_timedQueue);
            }

            // Check if the queue was empty or the request is the new head of the queue
            if (queueHead == null || timedReq.compareTo(queueHead) == -1) {

                // DEBUG
                if (hasTimedDebug())
                    Debug.println("New head of timed request queue, waking processor thread ...");

                // Wakeup the timed request processor thread to reset the sleep time, the new
                // request is the head of the queue
                m_timedProcessor.wakeupProcessor();
            }
        }
    }

    /**
     * Remove a timed request from the queue
     *
     * @param timedReq TimedThreadRequest
     * @return boolean
     */
    public final boolean removeTimedRequest(TimedThreadRequest timedReq) {
        boolean wasRemoved = false;

        synchronized (m_timedQueue) {

            // Remove the timed thread request
            wasRemoved = m_timedQueue.remove(timedReq);
            timedReq.setThreadRequestPool(null);
        }

        // Return the remove status
        return wasRemoved;
    }

    /**
     * Shutdown the thread pool and release all resources
     */
    public void shutdownThreadPool() {

        // Shutdown the worker threads
        if (m_workers != null) {
            for (int i = 0; i < m_workers.length; i++)
                m_workers[i].shutdownRequest();
        }

        // Shutdown the timed request handler
        if (m_timedProcessor != null)
            m_timedProcessor.shutdownRequest();
    }

    /**
     * Enable/disable debug output
     *
     * @param ena boolean
     */
    public final void setDebug(boolean ena) {
        m_debug = ena;
    }

    /**
     * Enable/disable timed request debug output
     *
     * @param ena boolean
     */
    public final void setTimedDebug(boolean ena) {
        m_timedDebug = ena;
    }
}
