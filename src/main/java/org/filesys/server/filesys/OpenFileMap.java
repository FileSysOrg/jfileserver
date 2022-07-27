/*
 * Copyright (C) 2022 GK Spencer
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

package org.filesys.server.filesys;

import org.filesys.server.SrvSession;

import java.util.Iterator;

/**
 * Open File Map Base Class
 *
 * <p>A file map holds open file contexts for a session with a unique file id handle</p>
 *
 * @author gkspencer
 */
public abstract class OpenFileMap {

    //	Maximum number of open files allowed per connection.
    public static final int MAXFILES = 8192;

    // Number of initial file slots to allocate.
    public static final int INITIALFILES = 32;

    /**
     * Default constructor
     */
    public OpenFileMap() {
    }

    /**
     * Add a network file to the list of open files and return the allocated file id handle
     *
     * @param file NetworkFile
     * @param sess SrvSession
     * @return int
     * @exception TooManyFilesException Too many open files
     */
    public abstract int addFile(NetworkFile file, SrvSession sess)
        throws TooManyFilesException;

    /**
     * Return the specified network file.
     *
     * @param fid int
     * @return NetworkFile
     */
    public abstract NetworkFile findFile(int fid);

    /**
     * Iterate the in use file id handles
     *
     * @return Iterator&lt;Integer&gt;
     */
    public abstract Iterator<Integer> iterateFileHandles();

    /**
     * Return the count of open files on this tree connection.
     *
     * @return int
     */
    public abstract int openFileCount();

    /**
     * Remove all files from the tree connection.
     */
    public abstract void removeAllFiles();

    /**
     * Remove a network file from the list of open files for this connection.
     *
     * @param idx  int
     * @param sess SrvSession
     * @return NetworkFile
     */
    public abstract NetworkFile removeFile(int idx, SrvSession sess);
}
