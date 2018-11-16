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
package org.filesys.smb.server;

import org.filesys.server.SessionHandlerInterface;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.smb.mailslot.HostAnnouncer;

/**
 * SMB Connections Handler Interface
 *
 * @author gkspencer
 */
public interface SMBConnectionsHandler {

    /**
     * Initialize the connections handler
     *
     * @param srv    SMBServer
     * @param config SMBConfigSection
     * @throws InvalidConfigurationException Failed to initialize the connections handler
     */
    public void initializeHandler(SMBServer srv, SMBConfigSection config)
            throws InvalidConfigurationException;

    /**
     * Start the connection handler thread
     */
    public void startHandler();

    /**
     * Stop the connections handler
     */
    public void stopHandler();

    /**
     * Return the count of active session handlers
     *
     * @return int
     */
    public int numberOfSessionHandlers();

    /**
     * Add a host announcer to the connections handler
     *
     * @param announcer HostAnnouncer
     */
    public void addHostAnnouncer(HostAnnouncer announcer);

    /**
     * Add a session handler to the connections handler
     *
     * @param sessHandler SessionHandlerInterface
     */
    public void addSessionHandler(SessionHandlerInterface sessHandler);

    /**
     * Determine if debug output is enabled
     *
     * @return boolean
     */
    public boolean hasDebug();
}
