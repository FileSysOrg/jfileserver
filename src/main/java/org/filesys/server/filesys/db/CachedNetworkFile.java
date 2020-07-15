/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.server.filesys.db;

import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateProxy;
import org.filesys.server.filesys.loader.FileLoader;
import org.filesys.server.filesys.loader.FileRequest;

/**
 * Cached Data Network File Base Class
 *
 * <p>Base class for cached file implementations.
 *
 * @author gkspencer
 */
public abstract class CachedNetworkFile extends DBNetworkFile {

    // Maximum time to wait for file data
    protected static final long DataLoadWaitTime    = 20000L; // 20 seconds
    protected static final long DataPollSleepTime   = 250L;    // milliseconds

    // Debug enable flag
    protected static boolean _debug = false;

    // Read request details
    protected long m_lastReadPos = -1L;
    protected int m_lastReadLen = -1;

    protected int m_seqReads;

    // Sequential access only flag
    protected boolean m_seqOnly;

    /**
     * Class constructor
     *
     * @param name    String
     * @param fid     int
     * @param stid    int
     * @param did     int
     * @param state   FileStateProxy
     * @param loader  FileLoader
     */
    public CachedNetworkFile(String name, int fid, int stid, int did, FileStateProxy state, FileLoader loader) {
        super(name, fid, stid, did);

        // Save the file state
        setFileState(state);

        // Set the associated file loader
        setLoader(loader);
    }

    /**
     * Determine if the file will only be accessed sequentially
     *
     * @return boolean
     */
    public final boolean isSequentialOnly() {
        return m_seqOnly;
    }

    /**
     * Set the sequential access only flag
     *
     * @param seq boolean
     */
    public final void setSequentialOnly(boolean seq) {
        m_seqOnly = seq;
    }

    /**
     * Update the cached file information file size
     *
     * @param siz   long
     * @param alloc long
     */
    protected final void updateFileSize(long siz, long alloc) {

        // Get the cached file information, if available
        if (hasFileState()) {

            // Get the cached file information for this file
            FileInfo finfo = (FileInfo) getFileState().findAttribute(FileState.FileInformation);

            if (finfo != null && finfo.getSize() != siz) {

                // Update the file size and allocation size
                finfo.setSize(siz);
                if (alloc != -1L || finfo.getSize() > finfo.getAllocationSize())
                    finfo.setAllocationSize(alloc);
            }
        }

        // Update the open file size
        setFileSize(siz);
    }

    /**
     * Create a file load or save request. This method may be overridden to allow extending of the SingleFileRequest class.
     *
     * @param typ FileRequest.RequestType
     * @param fileOff long
     * @param len int
     * @param outOfSeq boolean
     * @return FileRequest
     */
    protected abstract FileRequest createFileRequest(FileRequest.RequestType typ, long fileOff, int len, boolean outOfSeq);

    /**
     * Determine if network file debug output is enabled
     *
     * @return boolean
     */
    protected final boolean hasDebug() {
        return _debug;
    }

    /**
     * Enable/disable debug output
     *
     * @param ena boolean
     */
    public static final void setDebug(boolean ena) { _debug = ena; }
}
