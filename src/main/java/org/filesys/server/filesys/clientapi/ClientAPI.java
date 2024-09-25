/*
 * Copyright (C) 2023 GK Spencer
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

package org.filesys.server.filesys.clientapi;

import org.filesys.server.SrvSession;
import org.filesys.server.filesys.TreeConnection;

/**
 * Client API Interface
 *
 * <p>Optional interface that a DiskInterface driver can implement to provide a client callable API where the
 * requests/responses are tunnelled over an existing protocol connection.
 *
 * @author gkspencer
 */
public interface ClientAPI {

    /**
     * Check if the client API interface is enabled
     *
     * @return boolean
     */
    public boolean isClientAPIEnabled();

    /**
     * Return the client API implementation associated with this virtual filesystem
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return ClientAPIInterface
     */
    public ClientAPIInterface getClientAPI(SrvSession<?> sess, TreeConnection tree);
}
