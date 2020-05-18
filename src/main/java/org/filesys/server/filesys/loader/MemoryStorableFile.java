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
 * Memory Storable File Interface
 *
 * <p>Used by network file implementations that store file data to in memory buffer(s)</p>
 *
 * @author gkspencer
 */
public interface MemoryStorableFile {

    // Saveable data status
    public enum SaveableStatus {
        Buffering,      // data is buffering, nothing to save
        Saveable,       // data ready to be saved
        MaxBuffers,     // all buffers are full, no more writes until one or more buffers emptied
        BufferOverflow  // write is beyond a fixed buffer size when using an in-memory buffer
    }

    /**
     * Write a block of data to the segment file
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @return SaveableStatus
     * @throws IOException Failed to write the file
     */
    public SaveableStatus writeBytes(byte[] buf, int len, int pos, long fileOff)
        throws IOException;

    /**
     * Truncate, or extend, the memory file to the specified size
     *
     * @param siz long
     * @exception IOException Failed to truncate the file
     */
    public void truncate(long siz)
        throws IOException;


    /**
     * Return the details of a section of file data to be saved to the store
     *
     * @return MemoryBuffer
     */
    public MemoryBuffer dataToSave();

    /**
     * Indicate that the specified memory buffer has been saved
     *
     * @param memBuf MemoryBuffer
     */
    public void dataSaved( MemoryBuffer memBuf);

    /**
     * Wait for a writeable buffer to become available to continue a write request
     *
     * @param tmo long
     */
    public void waitForWriteBuffer(long tmo);
}
