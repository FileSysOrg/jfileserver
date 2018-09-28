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

package org.filesys.server;

import java.util.ArrayList;
import java.util.List;

/**
 * Session Handler List Class
 *
 * @author gkspencer
 */
public class SessionHandlerList {

    // List of session handlers
    private List<SessionHandlerInterface> m_handlers;

    /**
     * Default constructor
     */
    public SessionHandlerList() {
        m_handlers = new ArrayList<SessionHandlerInterface>();
    }

    /**
     * Add a handler to the list
     *
     * @param handler SessionHandlerInterface
     */
    public final void addHandler(SessionHandlerInterface handler) {
        m_handlers.add(handler);
    }

    /**
     * Return the number of handlers in the list
     *
     * @return int
     */
    public final int numberOfHandlers() {
        return m_handlers.size();
    }

    /**
     * Return the specified handler
     *
     * @param idx int
     * @return SessionHandlerInterface
     */
    public final SessionHandlerInterface getHandlerAt(int idx) {

        // Range check the index
        if (idx < 0 || idx >= m_handlers.size())
            return null;
        return m_handlers.get(idx);
    }

    /**
     * Find the required handler by name
     *
     * @param name String
     * @return SessionHandlerInterface
     */
    public final SessionHandlerInterface findHandler(String name) {

        // Search for the required handler
        for (int i = 0; i < m_handlers.size(); i++) {

            // Get the current handler
            SessionHandlerInterface handler = m_handlers.get(i);

            if (handler.getHandlerName().equals(name))
                return handler;
        }

        // Handler not found
        return null;
    }

    /**
     * Remove a handler from the list
     *
     * @param idx int
     * @return SessionHandlerInterface
     */
    public final SessionHandlerInterface remoteHandler(int idx) {

        // Range check the index
        if (idx < 0 || idx >= m_handlers.size())
            return null;

        // Remove the handler, and return it
        return m_handlers.remove(idx);
    }

    /**
     * Remove a handler from the list
     *
     * @param name String
     * @return SessionHandlerInterface
     */
    public final SessionHandlerInterface remoteHandler(String name) {

        // Search for the required handler
        for (int i = 0; i < m_handlers.size(); i++) {

            // Get the current handler
            SessionHandlerInterface handler = m_handlers.get(i);

            if (handler.getHandlerName().equals(name)) {

                // Remove the handler from the list
                m_handlers.remove(i);
                return handler;
            }
        }

        // Handler not found

        return null;
    }

    /**
     * Remove all handlers from the list
     */
    public final void removeAllHandlers() {
        m_handlers.clear();
    }

    /**
     * Wait for a session handler to be added to the list
     *
     * @exception InterruptedException Wait interrupted
     */
    public final synchronized void waitWhileEmpty()
            throws InterruptedException {

        // Wait until a session handler is added to the list
        while (m_handlers.size() == 0)
            wait();
    }
}
