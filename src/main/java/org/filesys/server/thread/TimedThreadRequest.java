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

package org.filesys.server.thread;

import java.util.Date;

import org.filesys.debug.Debug;

/**
 * Timer Thread Request Class
 *
 * <p>Run a thread request at a particular time, with options to repeat.
 *
 * @author gkspencer
 */
public abstract class TimedThreadRequest implements ThreadRequest, Comparable<TimedThreadRequest> {

    // Run at time that indicates the timed request is paused
    public final static long TimedRequestPaused = 0L;

    // Time to run the request at and repeat interval
    // A repeat interval of zero indicates a one off request.
    private long m_runAt;
    private long m_repeatSecs;

    // Description/name
    private String m_description;

    // Thread request pool that this request is queued to
    private ThreadRequestPool m_threadPool;

    /**
     * Class constructor
     *
     * @param desc  String
     * @param runAt long
     */
    public TimedThreadRequest(String desc, long runAt) {
        setRunAtTime(runAt);
        m_description = desc;
    }

    /**
     * Class constructor
     *
     * @param desc       String
     * @param runAt      long
     * @param repeatSecs long
     */
    public TimedThreadRequest(String desc, long runAt, long repeatSecs) {
        setRunAtTime(runAt);
        setRepeatInterval(repeatSecs);
        m_description = desc;
    }

    /**
     * Return the timed reqeust description/name string
     *
     * @return String
     */
    public final String getDescription() {
        return m_description;
    }

    /**
     * Return the time the request should run at
     *
     * @return long
     */
    public final long getRunAtTime() {
        return m_runAt;
    }

    /**
     * Check if hte timed thread request has been paused
     *
     * @return boolean
     */
    public final boolean isPaused() {
        return m_runAt == TimedRequestPaused ? true : false;
    }

    /**
     * Check if the timed request has a repeat interval
     *
     * @return boolean
     */
    public final boolean hasRepeatInterval() {
        return m_repeatSecs <= 0L ? false : true;
    }

    /**
     * Return the repeat interval for the timed request, or zero if this is not a repeating
     * request.
     *
     * @return long
     */
    public final long getRepeatInterval() {
        return m_repeatSecs;
    }

    /**
     * Check if the request is associated with a thread pool, ie. queued
     *
     * @return boolean
     */
    public final boolean hasThreadRequestPool() {
        return m_threadPool != null ? true : false;
    }

    /**
     * Return the associated thread pool
     *
     * @return ThreadRequestPool
     */
    public final ThreadRequestPool getThreadRequestPool() {
        return m_threadPool;
    }

    /**
     * Set the time to run the interval. A positive value indicates an absolute time value, a negative
     * value indicates the number of seconds from now.
     *
     * @param runAt long
     */
    public final void setRunAtTime(long runAt) {
        if (runAt < 0L)
            m_runAt = System.currentTimeMillis() + (-runAt * 1000L);
        else
            m_runAt = runAt;

        // If the request is associated with a thread pool then requeue the request
        if (hasThreadRequestPool())
            getThreadRequestPool().queueTimedRequest(this);
    }

    /**
     * Set the repeat interval, in seconds
     *
     * @param repeatSecs long
     */
    public final void setRepeatInterval(long repeatSecs) {
        m_repeatSecs = repeatSecs;
    }

    /**
     * Thread request pool that the request is queued to
     *
     * @param threadPool ThreadRequestPool
     */
    protected final void setThreadRequestPool(ThreadRequestPool threadPool) {
        m_threadPool = threadPool;
    }

    /**
     * Restart a timed request that has been paused
     */
    public final void restartRequest() {
        if (isPaused())
            setRunAtTime(System.currentTimeMillis() + (getRepeatInterval() * 1000L));
    }

    /**
     * Run the request in a thread from the thread pool
     */
    public void runRequest() {

        try {

            // Run the timed request
            runTimedRequest();
        }
        catch (Throwable ex) {
            Debug.println(ex, Debug.Error);
        }

        // Check if the timed request should be requeued
        if (isPaused() == false) {

            // Check if the timed request has a repeat interval, if so then requeue it
            if (hasRepeatInterval()) {

                // Update the next scheduled run time for the request
                setRunAtTime(System.currentTimeMillis() + (getRepeatInterval() * 1000L));

                // Requeue the request
                if ( m_threadPool != null)
                    m_threadPool.queueTimedRequest(this);
            } else {

                // Clear the associated thread pool, request no longer queued
                m_threadPool = null;
            }
        } else {

            // Requeue the paused request
            if ( m_threadPool != null)
                m_threadPool.queueTimedRequest(this);
        }
    }

    /**
     * Timed request processing
     */
    protected abstract void runTimedRequest();

    /**
     * Compare timed thread requests for ordering
     *
     * @param timedReq TimedThreadRequest
     * @return int
     */
    public int compareTo(TimedThreadRequest timedReq) {
        if (isPaused() && timedReq.isPaused())
            return 0;
        if (isPaused())
            return 1;
        if (timedReq.isPaused())
            return -1;

        if (getRunAtTime() < timedReq.getRunAtTime())
            return -1;
        else if (getRunAtTime() == timedReq.getRunAtTime())
            return 0;
        return 1;
    }

    /**
     * Return the timed thread request as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(getDescription());
        str.append(",RunAt=");
        if (getRunAtTime() == TimedRequestPaused)
            str.append("Paused");
        else
            str.append(new Date(getRunAtTime()));
        str.append(",Repeat=");
        if (getRepeatInterval() > 0L)
            str.append(getRepeatInterval());
        else
            str.append("None");
        str.append("]");

        return str.toString();
    }
}
