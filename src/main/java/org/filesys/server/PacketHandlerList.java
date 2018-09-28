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
 * Packet Handler List Class
 *
 * @author gkspencer
 */
public class PacketHandlerList {

    //	List of session handlers
    private List<PacketHandlerInterface> m_handlers;

    /**
     * Default constructor
     */
    public PacketHandlerList() {
        m_handlers = new ArrayList<PacketHandlerInterface>();
    }

    /**
     * Add a handler to the list
     *
     * @param handler PacketHandlerInterface
     */
    public final void addHandler(PacketHandlerInterface handler) {
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
     * @return PacketHandlerInterface
     */
    public final PacketHandlerInterface getHandlerAt(int idx) {

        //	Range check the index
        if (idx < 0 || idx >= m_handlers.size())
            return null;
        return m_handlers.get(idx);
    }

    /**
     * Remove a handler from the list
     *
     * @param idx int
     * @return PacketHandlerInterface
     */
    public final PacketHandlerInterface remoteHandler(int idx) {

        //	Range check the index
        if (idx < 0 || idx >= m_handlers.size())
            return null;

        //	Remove the handler, and return it
        return m_handlers.remove(idx);
    }

    /**
     * Remove all handlers from the list
     */
    public final void removeAllHandlers() {
        m_handlers.clear();
    }
}
