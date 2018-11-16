/*
 * Copyright (C) 2018 GK Spencer
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
package org.filesys.smb.server;

import org.filesys.server.config.InvalidConfigurationException;

/**
 * SMB Child Connect Handler Interface
 *
 * <p>Used to provide optional connection handlers to the main connection handler via dynamic class loading</p>
 */
public interface SMBChildConnectionHandler {

    /**
     * Initialize the connections handler
     *
     * @param srv    SMBServer
     * @param config SMBConfigSection
     * @param parentHandler SMBConnectionsHandler
     * @throws InvalidConfigurationException Failed to initialize the connections handler
     */
    public void initializeHandler(SMBServer srv, SMBConfigSection config, SMBConnectionsHandler parentHandler)
            throws InvalidConfigurationException;
}
