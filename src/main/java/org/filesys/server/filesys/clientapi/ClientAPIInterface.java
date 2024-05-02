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
import org.filesys.server.filesys.FileOpenParams;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.TreeConnection;

/**
 * Client API Interface
 *
 * <p>Provides an optional interface that a filesystem can implement in order to handle requests from
 * a client side application</p>
 *
 * @author gkspencer
 */
public interface ClientAPIInterface {

    /**
     * Return the path of the special file that is used to send the request to the API and receives the
     * response data
     *
     * @return String
     */
    public String getClientAPIPath();

    /**
     * Open a network file that is used to send requests to the API from a client session, and receives the response
     * via the file.
     *
     * @param sess   Server session
     * @param tree   Tree connection
     * @param params File open parameters
     * @return ClientAPINetworkFile
     * @throws java.io.IOException If an error occurs.
     */
    public ClientAPINetworkFile openClientAPIFile(SrvSession<?> sess, TreeConnection tree, FileOpenParams params)
        throws java.io.IOException;

    /**
     * Process a client API request
     *
     * @param netFile ClientAPINetworkFile
     * @throws java.io.IOException If an error occurs
     */
    public void processRequest( ClientAPINetworkFile netFile)
        throws java.io.IOException;
}
