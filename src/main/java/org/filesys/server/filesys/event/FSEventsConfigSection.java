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

import org.filesys.server.config.ConfigSection;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;

/**
 * Filesystem Events Configuration Section Class
 *
 * <p>Contains the global filesystem events handler</p>
 *
 * @author gkspencer
 */
public class FSEventsConfigSection extends ConfigSection {

    // Filesystem events configuration section name
    public static final String SectionName = "FSEvents";

    // Global filesystem events handler
    private FSEventsHandler m_fsHandler;

    /**
     * Class constructor
     *
     * @param config ServerConfiguration
     */
    public FSEventsConfigSection(ServerConfiguration config) {
        super(SectionName, config);

        // Create the global filesystem events handler
        m_fsHandler = new FSEventsHandler();
    }

    /**
     * Return the filesystem events handler
     *
     * @return FSEventsHandler
     */
    public final FSEventsHandler getFSEventsHandler() { return m_fsHandler; }

    /**
     * Set the filesystem events handler
     *
     * @param evtHandler FSEventsHandler
     * @exception InvalidConfigurationException Error setting the thread pool sizes
     */
    public final void setFSEventsHandler(FSEventsHandler evtHandler)
            throws InvalidConfigurationException {

        m_fsHandler = evtHandler;
    }

    @Override
    public void closeConfig() {
        super.closeConfig();

        // Close the filesystem events handler
        if ( m_fsHandler != null)
            m_fsHandler.shutdownRequest();
    }
}
