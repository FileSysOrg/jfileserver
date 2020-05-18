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
import java.util.EnumSet;

/**
 * Memory Segment Info Class
 *
 * <p>Base class for in memory or partial memory buffering of file data, and may be shared by many users/sessions.
 *
 * @author gkspencer
 */
public abstract class MemorySegmentInfo extends SegmentInfo implements MemoryLoadableFile, MemoryStorableFile {

    /**
     * Default constructor
     */
    public MemorySegmentInfo() {
    }

    /**
     * Class constructor
     *
     * @param flags EnumSet&lt;Flags&gt;
     */
    public MemorySegmentInfo(EnumSet<Flags> flags) {
        super( flags);
    }

    /**
     * Close the memory file
     *
     * @return boolean true if there is buffered data to be saved
     */
    public abstract boolean closeFile();

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
    public abstract int readBytes(byte[] buf, int len, int pos, long fileOff)
        throws IOException;

    /**
     * Check if the file data status for the specified file segment
     *
     * @param fileOff long
     * @param len int
     * @return LoadableStatus
     */
    public abstract LoadableStatus hasDataFor( long fileOff, int len);

    /**
     * Add loaded data to the available data, this may be the whole file data or a section of the file
     *
     * @param fileData MemoryBuffer
     */
    public abstract void addFileData( MemoryBuffer fileData);

    /**
     * Write a block of data to the segment file
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @return MemorySaveableFile.SaveableStatus
     * @throws IOException Failed to write the file
     */
    public abstract MemoryStorableFile.SaveableStatus writeBytes(byte[] buf, int len, int pos, long fileOff)
        throws IOException;

    /**
     * Truncate, or extend, the memory file to the specified size
     *
     * @param siz long
     * @exception IOException Failed to truncate the file
     */
    public abstract void truncate(long siz)
        throws IOException;

    /**
     * Return the details of a section of file data to be saved to the store
     *
     * @return MemoryBuffer
     */
    public abstract MemoryBuffer dataToSave();

    /**
     * Indicate that the specified memory buffer has been saved
     *
     * @param memBuf MemoryBuffer
     */
    public abstract void dataSaved( MemoryBuffer memBuf);
}
