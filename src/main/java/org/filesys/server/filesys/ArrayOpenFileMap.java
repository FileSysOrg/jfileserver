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
 * Array Open File Map Class
 *
 * <p>Use an array to hold open file contexts, with empty array slots being re-used and the file id handle
 * is the index into the array</p>
 *
 * @author gkspencer
 */
public class ArrayOpenFileMap extends OpenFileMap {

    //	List of open files on this connection. Count of open file slots used.
    private NetworkFile[] m_files;
    private int m_fileCount;

    /**
     * Iterator Class
     */
    public class ArrayOpenFileMapIterator implements Iterator<Integer> {

        // Index of the next file slot
        private int m_nextId;

        /**
         * Default constructor
         */
        ArrayOpenFileMapIterator() {}

        @Override
        public boolean hasNext() {
            while (m_nextId < m_files.length && m_files[m_nextId] == null)
                m_nextId++;

            return m_nextId < m_files.length;
        }

        @Override
        public Integer next() {
            while (m_nextId < m_files.length && m_files[m_nextId] == null)
                m_nextId++;

            if ( m_nextId < m_files.length)
                return new Integer( m_nextId++);
            return null;
        }
    }

    /**
     * Default constructor
     */
    public ArrayOpenFileMap() {
    }

    @Override
    public int addFile(NetworkFile file, SrvSession sess) throws TooManyFilesException {

        //  Check if the file array has been allocated
        if (m_files == null)
            m_files = new NetworkFile[INITIALFILES];

        //  Find a free slot for the network file
        int idx = 0;

        while (idx < m_files.length && m_files[idx] != null)
            idx++;

        //  Check if we found a free slot
        if (idx == m_files.length) {

            //  The file array needs to be extended, check if we reached the limit.
            if (m_files.length >= MAXFILES)
                throw new TooManyFilesException();

            //  Extend the file array
            NetworkFile[] newFiles = new NetworkFile[m_files.length * 2];
            System.arraycopy(m_files, 0, newFiles, 0, m_files.length);
            m_files = newFiles;
        }

        //	Inform listeners that a file has been opened
        NetworkFileServer fileSrv = (NetworkFileServer) sess.getServer();
        if (fileSrv != null)
            fileSrv.fireOpenFileEvent(sess, file);

        //  Store the network file, update the open file count and return the index
        m_files[idx] = file;
        m_fileCount++;

        // Save the protocol level id
        file.setProtocolId(idx);
        return idx;
    }

    @Override
    public NetworkFile findFile(int fid) {

        //  Check if the file id and file array are valid
        if (m_files == null || fid >= m_files.length || fid < 0)
            return null;

        //  Get the required file details
        return m_files[fid];
    }

    @Override
    public Iterator<Integer> iterateFileHandles() {
        return new ArrayOpenFileMapIterator();
    }

    @Override
    public int openFileCount() {
        return m_fileCount;
    }

    @Override
    public void removeAllFiles() {

        //  Check if the file array has been allocated
        if (m_files == null)
            return;

        //  Clear the file list
        for (int idx = 0; idx < m_files.length; m_files[idx++] = null) ;
        m_fileCount = 0;
    }

    @Override
    public NetworkFile removeFile(int idx, SrvSession sess) {

        //  Range check the file index
        if (m_files == null || idx >= m_files.length)
            return null;

        //  Remove the file and update the open file count.
        NetworkFile netFile = m_files[idx];

        if (netFile != null)
            netFile.setProtocolId(-1);
        m_files[idx] = null;
        m_fileCount--;

        return netFile;
    }
}
