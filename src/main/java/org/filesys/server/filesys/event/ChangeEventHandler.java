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

import org.filesys.server.filesys.DiskDeviceContext;

import java.util.Objects;

/**
 * Change Event Handler Interface
 *
 * <p>The change event handler receives a callback for all change notifications sent through the change
 * notification handler for a filesystem</p>
 *
 * @author gkspencer
 */
public abstract class ChangeEventHandler {

    /**
     * Change Handler Priority Enum Class
     */
    public enum Priority {
        Low,
        Normal,
        High
    }

    // Event handler name
    private String m_name;

    // Event handler priority
    private Priority m_priority;

    /**
     * Class constructor
     *
     * @param name String
     * @param priority Priority
     */
    public ChangeEventHandler( String name, Priority priority) {
        m_name = name;
        m_priority = priority;
    }

    /**
     * Return the change handler name
     *
     * @return String
     */
    public final String getName() { return m_name; }

    /**
     * Return the change handler priority
     *
     * @return Priority
     */
    public final Priority getPriority() { return m_priority; }

    /**
     * Register a filesystem with the change handler
     *
     * @param diskCtx DiskDeviceContext
     * @exception Exception Error during register
     */
    public abstract void registerFilesystem( DiskDeviceContext diskCtx)
        throws Exception;

    /**
     * Un-register a filesystem with the change handler
     *
     * @param diskCtx DiskDeviceContext
     * @exception Exception Error during unregister
     */
    public abstract void unregisterFilesystem( DiskDeviceContext diskCtx)
            throws Exception;

    /**
     * Check if this handler wants all change events
     *
     * @return boolean
     */
    public abstract boolean wantAllFSEvents();

    /**
     * Check if this handler wants to process the specified change event type
     *
     * @param typ FSChange
     * @param dir boolean
     * @param diskCtx DiskDeviceContext
     * @return boolean
     */
    public abstract boolean wantFSEvent( FSChange typ, boolean dir, DiskDeviceContext diskCtx);

    /**
     * Handle a filesystem event
     *
     * @param event ChangeEvent
     * @param diskCtx DiskDeviceContext
     */
    public abstract void handleFSEvent(ChangeEvent event, DiskDeviceContext diskCtx);

    /**
     * Close the change event handler
     */
    public void closeHandler() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ChangeEventHandler that = (ChangeEventHandler) o;
        return Objects.equals(m_name, that.m_name) && m_priority == that.m_priority;
    }

    @Override
    public int hashCode() {
        return Objects.hash(m_name, m_priority);
    }
}
