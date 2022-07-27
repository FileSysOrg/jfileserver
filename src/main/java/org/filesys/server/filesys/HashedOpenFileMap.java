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

import java.util.HashMap;
import java.util.Iterator;

/**
 * Hashed Open File Map Class
 *
 * <p>Use a HashMap to hold open file contexts, with new file id handles being sequential until the maximum file id handle is reached</p>
 *
 * @author gkspencer
 */
public class HashedOpenFileMap extends OpenFileMap {

    // Maximum file id handle value
    public static final int MAX_FILE_ID_HANDLE  = 0x1FFFFFFF;

    // Hash map of open NetworkFile objects indexed by the file id handle
    private HashMap<Integer, NetworkFile> m_files;

    // Next file id handle to use
    private int m_nextFileId    = 1;

    /**
     * Default constructor
     */
    public HashedOpenFileMap() {
        m_files = new HashMap<>( INITIALFILES);
    }

    @Override
    public int addFile(NetworkFile file, SrvSession sess)
        throws TooManyFilesException {

        // Find a free id for the new file
        while ( m_files.get( m_nextFileId) != null) {
            m_nextFileId++;

            if ( m_nextFileId >= MAX_FILE_ID_HANDLE)
                m_nextFileId = 1;
        }

        // Store the opne file details
        m_files.put( m_nextFileId, file);

        // Return the file id handle, and bump the next handle id
        return m_nextFileId++;
    }

    @Override
    public NetworkFile findFile(int fid) {
        return m_files.get( fid);
    }

    @Override
    public Iterator<Integer> iterateFileHandles() {
        return m_files.keySet().iterator();
    }

    @Override
    public int openFileCount() {
        return m_files.size();
    }

    @Override
    public void removeAllFiles() {
        m_files.clear();
    }

    @Override
    public NetworkFile removeFile(int fid, SrvSession sess) {
        return m_files.remove( fid);
    }
}
