/*
 * Copyright (C) 2020 GK Spencer
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
package org.filesys.server.filesys.loader;

import org.filesys.server.filesys.cache.FileState;

/**
 * File State File Request Base Class
 *
 * <p>Base class for file requests that have an associated file state in the file state cache</p>
 *
 * @author gkspencer
 */
public class FileStateFileRequest extends FileRequest {

    //	Associated file state
    private FileState m_state;

    // Out of sequence load/save request
    private boolean m_outOfSeq;

    /**
     * Class constructor
     *
     * @param typ      RequestType
     * @param state    FileState
     */
    protected FileStateFileRequest(FileRequest.RequestType typ, FileState state) {
        super(typ);

        m_state = state;
        m_outOfSeq = false;
    }

    /**
     * Class constructor
     *
     * @param typ      RequestType
     * @param state    FileState
     * @param outOfSeq boolean
     */
    protected FileStateFileRequest(FileRequest.RequestType typ, FileState state, boolean outOfSeq) {
        super(typ);

        m_state = state;
        m_outOfSeq = outOfSeq;
    }

    /**
     * Check if the request has an associated file state
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
     * Set the associated file state for the request
     *
     * @param state FileState
     */
    public final void setFileState(FileState state) {
        m_state = state;
    }

    /**
     * Check if this is an out of sequence load/save request
     *
     * @return boolean
     */
    public final boolean isOutOfSequence() { return m_outOfSeq; }
}
