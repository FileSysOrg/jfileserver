/*
 * Copyright (C) 2019 GK Spencer
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
package org.filesys.server.filesys.postprocess;

import org.filesys.server.SrvSession;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.TreeConnection;

import java.io.IOException;

/**
 * Post Close Processor Interface
 *
 * <p>Optional interface that a filesystem driver can implement to be called back after the protocol layer has sent a file
 * close response to the client to do more work.
 *
 * @author gkspencer
 */
public interface PostCloseProcessor {

    /**
     * Post close the file.
     *
     * @param sess  Server session
     * @param tree  Tree connection.
     * @param netFile Network file context.
     * @throws IOException If an error occurs.
     */
    public void postCloseFile(SrvSession sess, TreeConnection tree, NetworkFile netFile)
            throws IOException;
}
