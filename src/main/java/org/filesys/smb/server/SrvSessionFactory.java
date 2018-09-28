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

package org.filesys.smb.server;

/**
 * Server Session Factory Interface
 *
 * @author gkspencer
 */
public interface SrvSessionFactory {

    /**
     * Create a new server session object
     *
     * @param handler PacketHandler
     * @param server  SMBServer
     * @param sessId  int
     * @return SMBSrvSession
     */
    public SMBSrvSession createSession(PacketHandler handler, SMBServer server, int sessId);

    /**
     * Set the maximum virtual circuits per session
     *
     * @param maxVC int
     */
    public void setMaximumVirtualCircuits(int maxVC);
}
