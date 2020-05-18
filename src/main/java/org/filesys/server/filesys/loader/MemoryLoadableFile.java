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

import java.io.IOException;

/**
 * Memory Loadable File Interface
 *
 * <p>Used by network file implementations that load file data in to memory buffer(s)</p>
 *
 * @author gkspencer
 */
public interface MemoryLoadableFile {

    // Loadable data status
    public enum LoadableStatus {
        Available,          // data has been loaded and is available
        Loadable,           // data needs to be loaded
        LoadableOutOfSeq,   // data needs to be loaded using an out of sequence read
        Loading,            // data is being loaded
        NotAvailable        // data not available
    }

    /**
     * Read a block of data from the in memory file
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @return Length of data read.
     * @throws IOException Failed to read the file
     */
    public int readBytes(byte[] buf, int len, int pos, long fileOff)
        throws IOException;

    /**
     * Check if the file data status for the specified file segment
     *
     * @param fileOff long
     * @param len int
     * @return LoadableStatus
     */
    public LoadableStatus hasDataFor( long fileOff, int len);

    /**
     * Add loaded data to the available data, this may be the whole file data or a section of the file
     *
     * @param fileData MemoryBuffer
     */
    public void addFileData( MemoryBuffer fileData);
}
