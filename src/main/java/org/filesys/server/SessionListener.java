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

/**
 * <p>The session listener interface provides a hook into the server so that an application is notified when
 * a new session is created and closed by a network server.
 *
 * @author gkspencer
 */
public interface SessionListener {

    /**
     * Called when a network session is closed.
     *
     * @param sess Network session details.
     */
    public void sessionClosed(SrvSession sess);

    /**
     * Called when a new network session is created by a network server.
     *
     * @param sess Network session that has been created for the new connection.
     */
    public void sessionCreated(SrvSession sess);

    /**
     * Called when a user logs on to a network server
     *
     * @param sess Network session that has been logged on.
     */
    public void sessionLoggedOn(SrvSession sess);
}
