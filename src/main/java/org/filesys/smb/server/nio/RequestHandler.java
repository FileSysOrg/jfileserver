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

package org.filesys.smb.server.nio;

import org.filesys.smb.server.SMBSrvSession;

/**
 * Request Handler Class
 *
 * <P>Base for all requets handler implementations.
 *
 * @author gkspencer
 */
public abstract class RequestHandler {

    // Maximum number of sessions to handle
    private int m_maxSessions;

    // Debug enable flag
    private boolean m_debug;

    // Request handler listener
    private RequestHandlerListener m_listener;

    /**
     * Class constructor
     *
     * @param maxSess int
     */
    public RequestHandler(int maxSess) {
        m_maxSessions = maxSess;
    }

    /**
     * Return the current session count
     *
     * @return int
     */
    public abstract int getCurrentSessionCount();

    /**
     * Return the maximum session count
     *
     * @return int
     */
    public final int getMaximumSessionCount() {
        return m_maxSessions;
    }

    /**
     * Check if this request handler has free session slots available
     *
     * @return boolean
     */
    public abstract boolean hasFreeSessionSlot();

    /**
     * Queue a new session to the request handler, wakeup the request handler thread to register it with the
     * selector.
     *
     * @param sess SMBSrvSession
     */
    public abstract void queueSessionToHandler(SMBSrvSession sess);

    /**
     * Return the request handler name
     *
     * @return String
     */
    public abstract String getName();

    /**
     * Close the request handler
     */
    public abstract void closeHandler();

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
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
     * check if the request handler has an associated request handler listener
     *
     * @return boolean
     */
    public final boolean hasListener() {
        return m_listener != null ? true : false;
    }

    /**
     * Return the associated request handler listener
     *
     * @return RequestHandlerListener
     */
    public final RequestHandlerListener getListener() {
        return m_listener;
    }

    /**
     * Set the associated request handler listener
     *
     * @param listener RequestHandlerListener
     */
    public final void setListener(RequestHandlerListener listener) {
        m_listener = listener;
    }

    /**
     * Inform the listener that this request handler has no sessions to listen for incoming
     * requests.
     */
    protected final void fireRequestHandlerEmptyEvent() {
        if (hasListener())
            getListener().requestHandlerEmpty(this);
    }

    /**
     * Equality test
     *
     * @param obj Object
     * @return boolean
     */
    public boolean equals(Object obj) {

        // Check for the same type
        if (obj instanceof RequestHandler) {
            RequestHandler reqHandler = (RequestHandler) obj;
            return reqHandler.getName().equals(getName());
        }
        return false;
    }
}
