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
import org.filesys.server.filesys.DiskInterface;
import org.filesys.server.filesys.NetworkFile;

import java.io.IOException;

/**
 * File Request Post Processor Abstract Class
 *
 * <p>Base class for network file based request post processor implementations</p>
 */
public abstract class FileRequestPostProcessor implements PostRequestProcessor {

    // Context required for the network file post processor
    private SrvSession m_sess;
    private NetworkFile m_netFile;

    /**
     * Class constructor
     *
     * @param sess SrvSession
     * @param netFile NetworkFile
     */
    public FileRequestPostProcessor(SrvSession sess, NetworkFile netFile) {
        m_sess = sess;
        m_netFile = netFile;
    }

    /**
     * Return the server session
     *
     * @return SrvSession
     */
    public final SrvSession getSession() { return m_sess; }

    /**
     * Return the network file
     *
     * @return NetworkFile
     */
    public final NetworkFile getFile() { return m_netFile; }

    /**
     * Network file post processor implementation
     *
     * @exception IOException I/O error occurred
     */
    public abstract void runPostProcessor()
        throws IOException;
}
