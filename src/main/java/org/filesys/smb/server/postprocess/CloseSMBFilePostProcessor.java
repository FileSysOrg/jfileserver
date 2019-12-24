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
package org.filesys.smb.server.postprocess;

import org.filesys.server.SrvSession;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.TreeConnection;
import org.filesys.server.filesys.postprocess.PostCloseProcessor;

import java.io.IOException;

/**
 * Close File Post Processor Class
 *
 * <p>Calls the SMB close file post processor after the SMB response has been sent to the client using the same thread
 * that processed the request</p>
 */
public class CloseSMBFilePostProcessor extends SMBFileRequestPostProcessor {

    // File close post processor to be called
    private PostCloseProcessor m_closePostProcessor;

    /**
     * Class constructor
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param netFile NetworkFile
     * @param postProc PostCloseProcessor
     */
    public CloseSMBFilePostProcessor(SrvSession sess, TreeConnection tree, NetworkFile netFile, PostCloseProcessor postProc) {
        super( sess, tree, netFile);

        m_closePostProcessor = postProc;
    }

    /**
     * Network file post processor implementation
     */
    public void runPostProcessor()
        throws IOException {

        // Call the post processor
        if ( m_closePostProcessor != null)
            m_closePostProcessor.postCloseFile( getSession(), getTreeConnection(), getFile());
    }
}
