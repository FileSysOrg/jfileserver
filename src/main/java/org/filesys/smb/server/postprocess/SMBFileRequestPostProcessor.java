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
import org.filesys.server.filesys.DiskInterface;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.TreeConnection;
import org.filesys.server.filesys.postprocess.FileRequestPostProcessor;

/**
 * SMB File Request Post Processor Base Class
 *
 * <p>Abstract base class for SMB file request post processor implementations</p>
 *
 * @author gkspencer
 */
public abstract class SMBFileRequestPostProcessor extends FileRequestPostProcessor {

    // Additional context required for the SMB request post processor
    private TreeConnection m_tree;

    /**
     * Class constructor
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param netFile NetworkFile
     */
    public SMBFileRequestPostProcessor(SrvSession sess, TreeConnection tree, NetworkFile netFile) {
        super( sess, netFile);

        m_tree = tree;
    }

    /**
     * Return the tree connection
     *
     * @return TreeConnection
     */
    public final TreeConnection getTreeConnection() { return m_tree; }
}
