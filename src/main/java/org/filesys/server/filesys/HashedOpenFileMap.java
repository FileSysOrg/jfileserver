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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    private ConcurrentHashMap<Integer, NetworkFile> m_files;

    // Next file id handle to use
    private AtomicInteger m_nextFileId;

    /**
     * Default constructor
     */
    public HashedOpenFileMap() {
        m_files = new ConcurrentHashMap<Integer, NetworkFile>( INITIALFILES);
        m_nextFileId = new AtomicInteger( 1);
    }

    @Override
    public synchronized int addFile(NetworkFile file, SrvSession sess)
        throws TooManyFilesException {

        // Find a free id for the new file
        int id = m_nextFileId.getAndIncrement();

        while ( m_files.get( id) != null) {
            id = m_nextFileId.getAndIncrement();

            if ( m_nextFileId.get() >= MAX_FILE_ID_HANDLE) {
                m_nextFileId.set(1);
                id = 1;
            }
        }

        // Store the open file details
        m_files.put( id, file);

        // Return the file id handle, and bump the next handle id
        return id;
    }

    @Override
    public synchronized NetworkFile findFile(int fid) {
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
    public synchronized void removeAllFiles() {
        m_files.clear();
    }

    @Override
    public synchronized NetworkFile removeFile(int fid, SrvSession sess) {
        return m_files.remove( fid);
    }
}
