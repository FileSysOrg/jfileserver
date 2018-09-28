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

import java.io.IOException;

/**
 * Session Handler Interface
 *
 * <p>Implemented by classes that wait for an incoming session request.
 *
 * @author gkspencer
 */
public interface SessionHandlerInterface {

    /**
     * Return the protocol name
     *
     * @return String
     */
    public String getHandlerName();

    /**
     * Initialize the session handler
     *
     * @param server NetworkServer
     * @exception IOException Socket error
     */
    public void initializeSessionHandler(NetworkServer server)
            throws IOException;

    /**
     * Close the session handler
     *
     * @param server NetworkServer
     */
    public void closeSessionHandler(NetworkServer server);
}
