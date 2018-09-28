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

package org.filesys.server.filesys;

import org.filesys.server.SrvSession;

/**
 * <p>The share listener interface provides a hook into the server so that an application is notified when
 * a session connects/disconnects from a particular share.
 *
 * @author gkspencer
 */
public interface ShareListener {

    /**
     * Called when a session connects to a share
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     */
    public void shareConnect(SrvSession sess, TreeConnection tree);

    /**
     * Called when a session disconnects from a share
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     */
    public void shareDisconnect(SrvSession sess, TreeConnection tree);
}
