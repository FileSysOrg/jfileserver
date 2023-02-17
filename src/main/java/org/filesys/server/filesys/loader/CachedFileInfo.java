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

package org.filesys.server.filesys.loader;

import org.filesys.server.filesys.cache.FileState;

/**
 * Cached File Information Class
 *
 * <p>Contains the details of a file in the temporary cache area.
 *
 * @author gkspencer
 */
public class CachedFileInfo {

    //	File id and stream id
    private long m_fid;
    private long m_stid;

    //	Temporary file path
    private String m_tempPath;

    //	Virtual path
    private String m_virtPath;

    //	Associated file state
    private FileState m_state;

    /**
     * Class constructor
     *
     * @param fid      long
     * @param stid     long
     * @param tempPath String
     * @param virtPath String
     */
    public CachedFileInfo(long fid, long stid, String tempPath, String virtPath) {
        m_fid = fid;
        m_stid = stid;
        m_tempPath = tempPath;
        m_virtPath = virtPath;
    }

    /**
     * Return the file id
     *
     * @return long
     */
    public final long getFileId() {
        return m_fid;
    }

    /**
     * Return the stream id
     *
     * @return long
     */
    public final long getStreamId() {
        return m_stid;
    }

    /**
     * Check if there is an associated file state
     *
     * @return boolean
     */
    public final boolean hasFileState() {
        return m_state != null ? true : false;
    }

    /**
     * Return the associated file state
     *
     * @return FileState
     */
    public final FileState getFileState() {
        return m_state;
    }

    /**
     * Return the temporary file path
     *
     * @return String
     */
    public final String getTemporaryPath() {
        return m_tempPath;
    }

    /**
     * Return the virtual file path
     *
     * @return String
     */
    public final String getVirtualPath() {
        return m_virtPath;
    }

    /**
     * Set the associated file state
     *
     * @param state FileState
     */
    public final void setFileState(FileState state) {
        m_state = state;
    }

    /**
     * Return the cached file information as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[FID=");
        str.append(getFileId());
        str.append(",Temp=");
        str.append(getTemporaryPath());
        str.append(",Virt=");
        str.append(getVirtualPath());
        str.append("]");

        return str.toString();
    }
}
