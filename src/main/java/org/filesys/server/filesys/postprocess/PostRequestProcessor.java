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

import java.io.IOException;

/**
 * Post Request Processor Interface
 *
 * <p>Implementations call into a filesystem driver to run additional code after a request has been processed and
 * the response has been sent to the client by the protocol layer</p>
 *
 * @author gkspencer
 */
public interface PostRequestProcessor {

    /**
     * Run the post request processor, the implementation must store the required context for the callback
     *
     * @exception IOException I/O error occurred
     */
    public void runPostProcessor()
        throws IOException;
}
