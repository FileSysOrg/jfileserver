/*
 * Copyright (C) 2018-2019 GK Spencer
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

package org.filesys.server.filesys.version;

import org.filesys.server.SrvSession;
import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.FileOpenParams;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.TreeConnection;

import java.io.IOException;
import java.util.List;

/**
 * Optional filesystem interface that allows access to previous versions of a file.
 *
 * @author gkspencer
 */
public interface VersionInterface {

    /**
     * Get the list of available previous versions for the specified path
     *
     * @param sess Server session
     * @param tree Tree connection
     * @param file Network file
     * @return List &lt; FileVersionInfo &gt;
     * @throws IOException If an error occurs.
     */
    public List<FileVersionInfo> getPreviousVersions(SrvSession sess, TreeConnection tree, NetworkFile file)
        throws IOException;

    /**
     * Open a previous version of a file
     *
     * @param sess   Server session
     * @param tree   Tree connection
     * @param params File open parameters
     * @return NetworkFile
     * @throws IOException If an error occurs.
     */
    public NetworkFile openPreviousVersion(SrvSession sess, TreeConnection tree, FileOpenParams params)
        throws IOException;

    /**
     * Return the file information for a particular version of a file
     *
     * @param sess   Server session
     * @param tree   Tree connection
     * @param path   String
     * @param timeStamp long
     * @return FileInfo
     * @throws IOException If an error occurs.
     */
    public FileInfo getPreviousVersionFileInformation( SrvSession sess, TreeConnection tree, String path, long timeStamp)
        throws IOException;
}
