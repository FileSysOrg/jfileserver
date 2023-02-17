/*
 * Copyright (C) 2021 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */
package org.filesys.server.filesys.event;

import java.util.*;

/**
 * Change Events Handler List Class
 *
 * @author gkspencer
 */
public class ChangeEventHandlerList implements Iterable<ChangeEventHandler> {

    // Comparator to order change handlers by priority
    static Comparator<ChangeEventHandler> PRIORITY_ORDER = (h1, h2) -> h1.getPriority().compareTo( h2.getPriority());

    // List of change event handlers
    private List<ChangeEventHandler> m_handlers;

    /**
     * Default constructor
     */
    public ChangeEventHandlerList() {
        m_handlers = new ArrayList<>();
    }

    /**
     * Add a handler to the list
     *
     * @param handler ChangeEventHandler
     */
    public final synchronized void addHandler( ChangeEventHandler handler) {

        // Add the handler to the list
        m_handlers.add( handler);

        // Sort the list into priority order
        if ( m_handlers.size() > 1)
            Collections.sort( m_handlers, PRIORITY_ORDER);
    }

    /**
     * Remove a handler from the list
     *
     * @param handler ChangeEventHandler
     */
    public final synchronized void removeHandler( ChangeEventHandler handler) {
        m_handlers.remove( handler);
    }

    /**
     * Return the count of handlers in the list
     *
     * @return int
     */
    public final int numberOfHandlers() {
        return m_handlers.size();
    }

    /**
     * Find a handler by name
     *
     * @param name String
     * @return ChangeEventHandler
     */
    public final ChangeEventHandler findHandler( String name) {
        if ( m_handlers.size() == 0)
            return null;

        for ( ChangeEventHandler handler : m_handlers) {
            if ( handler.getName().equalsIgnoreCase( name))
                return handler;
        }

        return null;
    }

    @Override
    public Iterator<ChangeEventHandler> iterator() {
        return m_handlers.listIterator();
    }
}
