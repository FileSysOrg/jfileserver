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
package org.filesys.server.filesys.db;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.AccessDeniedException;
import org.filesys.server.filesys.DiskFullException;
import org.filesys.server.filesys.FileOfflineException;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.cache.FileStateProxy;
import org.filesys.server.filesys.loader.*;

import java.io.IOException;

/**
 * Memory Cached Network File Class
 *
 * <p>Cached network file implementation using a memory buffer to cache the file data</p>
 *
 * @author gkspencer
 */
public abstract class MemCachedNetworkFile extends CachedNetworkFile {

    // Number of seconds to wait for a writeable buffer to become available before returning a write error
    public static final long WriteBufferWaitTime    = 20000L;   // 20 seconds

    // Memory segment holding all or part of the file data in memory
    protected MemorySegmentInfo m_memFile;

    /**
     * Class constructor
     *
     * @param name    String
     * @param fid     int
     * @param stid    int
     * @param did     int
     * @param state   FileStateProxy
     * @param segment MemorySegmentInfo
     * @param loader  FileLoader
     */
    public MemCachedNetworkFile(String name, int fid, int stid, int did, FileStateProxy state, MemorySegmentInfo segment, FileLoader loader) {
        super(name, fid, stid, did, state, loader);

        // Set the memory segment
        m_memFile = segment;
    }

    /**
     * Return the associated memory segment
     *
     * @return MemorySegmentInfo
     */
    public final MemorySegmentInfo getMemorySegment() {
        return m_memFile;
    }

    /**
     * Set the associated memory segment
     *
     * @param memInfo MemorySegmentInfo
     */
    public final void setMemorySegment(MemorySegmentInfo memInfo) {
        m_memFile = memInfo;
    }

    /**
     * Return the maximum size for an in-memory cached file, if the file grows above this size it will be converted
     * to a streamed file
     *
     * @return long
     */
    public abstract long getInMemoryMaximumSize();

    /**
     * Open the file
     *
     * @param createFlag boolean
     * @throws IOException Error opening the file
     */
    public void openFile(boolean createFlag)
        throws IOException {

        // Mark the file as open
        setClosed(false);
    }

    /**
     * Read from the file.
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @return Length of data read.
     * @throws IOException Error reading the file
     */
    public int readFile(byte[] buf, int len, int pos, long fileOff)
        throws IOException {

        // DEBUG
        if (DEBUG)
            Debug.println("MemCachedNetworkFile.readFile() file=" + getName() + ", offset=" + fileOff + ", len=" + len);

        // Check for a read error
        if ( m_memFile.hasLoadError())
            throw new FileOfflineException("Error loading from store");

        // Update the last read position/length
        m_lastReadPos = fileOff;
        m_lastReadLen = len;

        // Check if the memory file has the data loaded, or it can be loaded, to satisfy the read request
        MemoryLoadableFile.LoadableStatus loadSts = m_memFile.hasDataFor( fileOff, len);
        int rdlen = 0;

        if ( loadSts == MemoryLoadableFile.LoadableStatus.Available) {

            // DEBUG
            if (DEBUG)
                Debug.println("MemCachedNetworkFile Data Available Read");

            // Read the file using the file segment
            rdlen = m_memFile.readBytes(buf, len, pos, fileOff);
        }
        else if ( loadSts == MemoryLoadableFile.LoadableStatus.Loadable ||
                  loadSts == MemoryLoadableFile.LoadableStatus.LoadableOutOfSeq ||
                  loadSts == MemoryLoadableFile.LoadableStatus.Loading) {

            // Wait for the required amount of data to be read is loaded
            long waitTime = 0L;
            boolean readDone = false;
            boolean dataAvailable = false;

            while (readDone == false && waitTime < DataLoadWaitTime) {

                // Check if the required data is loadable
                if ( loadSts == MemoryLoadableFile.LoadableStatus.Loadable ||
                     loadSts == MemoryLoadableFile.LoadableStatus.LoadableOutOfSeq) {

                    // Check if a file data load request needs to be queued
                    synchronized (m_memFile) {

                        // Queue a file data load request
                        if (m_memFile.isQueued() == false) {

                            // DEBUG
                            if (DEBUG)
                                Debug.println("MemCachedNetworkFile: loadsts=" + loadSts.name() + ", flags=" + m_memFile.getFlags());

                            // Indicate file data is being loaded
                            m_memFile.setStatus(SegmentInfo.State.Loading);

                            // Queue a data load for the required file data
                            boolean outOfSeq = loadSts == MemoryLoadableFile.LoadableStatus.LoadableOutOfSeq ? true : false;
                            getLoader().queueFileRequest(createFileRequest(FileRequest.RequestType.Load, fileOff, len, outOfSeq));
                        }
                    }
                }

                // Wait for some data to be loaded
                try {

                    // Indicate that an I/O is pending on this file
                    setIOPending(true);

                    // Wait for the file data to be loaded, or for large files the section of data required to satisfy this
                    // read request
                    long startTime = System.currentTimeMillis();
                    m_memFile.waitForData(DataPollSleepTime, fileOff, len);
                    long endTime = System.currentTimeMillis();

                    // Update the total data wait time
                    waitTime += (endTime - startTime);

                    // Clear the I/O pending flag
                    setIOPending(false);

                    // DEBUG
                    if (DEBUG)
                        Debug.println("MemCachedNetworkFile waited " + (endTime - startTime) + "ms for data, available="
                                + m_memFile.getReadableLength());
                }
                catch (Exception ex) {
                }

                // Check if there was a data load error
                if ( m_memFile.hasLoadError())
                    throw new FileOfflineException("Load error for " + getFullName());

                // Check if the data is available
                loadSts = m_memFile.hasDataFor( fileOff, len);

                if ( loadSts == MemoryLoadableFile.LoadableStatus.Available) {

                    // Read the file using the file segment
                    rdlen = m_memFile.readBytes(buf, len, pos, fileOff);

                    // Indicate the read is complete, maybe less data than requested
                    readDone = true;

                    // DEBUG
                    if ( hasDebug())
                        Debug.println("MemCachedNetworkFile waited " + waitTime + "ms for data, rdlen=" + rdlen + ", len=" + len);
                }
            }

            // Check if the read did not complete within the required time
            if ( readDone == false)
                throw new FileOfflineException("Failed to load file data in " + DataLoadWaitTime + "ms for " + getFullName());
        }
        else {

            // Data is not available for the read request
            //
            // TODO: Might need to distinguish between read at end of file and other reads
            throw new FileOfflineException("Data not available for read, offset=" + fileOff + ", len=" + len);
        }

        // Return the length of data read
        return rdlen;
    }

    /**
     * Write a block of data to the file.
     *
     * @param buf    byte[]
     * @param len    int
     * @param pos    int
     * @param offset long
     * @throws IOException Error writing to the file
     */
    public void writeFile(byte[] buf, int len, int pos, long offset)
        throws IOException {

        // Check if the file is writeable
        if (getGrantedAccess() == NetworkFile.Access.READ_ONLY)
            throw new AccessDeniedException("File is read-only");

        // Check if the memory segment has a save error
        if ( m_memFile.hasSaveError())
            throw new IOException("Write error saving to store");

        // Write the file using the file segment
        MemoryStorableFile.SaveableStatus writeSts = m_memFile.writeBytes(buf, len, pos, offset);

        if ( writeSts == MemoryStorableFile.SaveableStatus.Saveable) {

            synchronized ( m_memFile) {

                if ( m_memFile.isQueued() == false) {

                    // Queue a file data save request
                    getLoader().queueFileRequest(createFileRequest(FileRequest.RequestType.Save, offset, len, false));
                }
            }
        }
        else if ( writeSts == MemoryStorableFile.SaveableStatus.MaxBuffers) {

            // Waiting for a writeable buffer to become available
            long waitTime = 0L;
            boolean writeDone = false;

            while (writeDone == false && waitTime < WriteBufferWaitTime) {

                // Wait for a writeable buffer to become available
                try {

                    // Indicate that an I/O is pending on this file
                    setIOPending(true);

                    // Wait for a writeable buffer slot to become available
                    long startTime = System.currentTimeMillis();
                    m_memFile.waitForWriteBuffer(WriteBufferWaitTime / 10);
                    long endTime = System.currentTimeMillis();

                    // Update the total data wait time
                    waitTime += (endTime - startTime);

                    // Clear the I/O pending flag
                    setIOPending(false);

                    // DEBUG
                    if (DEBUG)
                        Debug.println("MemCachedNetworkFile waited " + (endTime - startTime) + "ms for writeable buffer");
                }
                catch (Exception ex) {
                }

                // Retry the write request
                writeSts = m_memFile.writeBytes(buf, len, pos, offset);

                if (writeSts == MemoryStorableFile.SaveableStatus.Saveable) {

                    synchronized (m_memFile) {

                        if (m_memFile.isQueued() == false) {

                            // Queue a file data save request
                            getLoader().queueFileRequest(createFileRequest(FileRequest.RequestType.Save, offset, len, false));
                        }
                    }

                    // Indicate the write was successful
                    writeDone = true;
                }
                else if (writeSts == MemoryStorableFile.SaveableStatus.Buffering) {

                    // Indicate the write was successful
                    writeDone = true;
                }

                // DEBUG
                if (writeDone == true && hasDebug())
                    Debug.println("MemCachedNetworkFile waited " + waitTime + "ms for write buffer, writeSts=" + writeSts.name());
            }
        }
        else if ( writeSts == MemoryStorableFile.SaveableStatus.BufferOverflow) {

            // We should only get a buffer overflow status when writing to an in-memory file, it indicates the write is beyond
            // the allocated buffer size so we need to convert the memory file to a streamed file
            if ( m_memFile.isAllFileData()) {

                // Calcualte the new file length
                long fileLen = offset + len;

                // Check if the file loader implements the in-memory interface, required to convert the file to a streamed
                // file
                if ( getLoader() instanceof InMemoryLoader) {

                    // Convert the in-memory file to a streamed file, and send the current written data to be saved
                    InMemoryLoader inMemLoader = (InMemoryLoader) getLoader();

                    if ( inMemLoader.convertInMemoryToStreamedFile( this)) {

                        // DEBUG
                        if ( hasDebug())
                            Debug.println("MemCachedNetworkFile: Converted to streamed file, path=" + getFullName() + ", size=" + fileLen);

                        // Try the write again
                        writeFile( buf, len, pos, offset);
                    }
                    else {

                        // Failed to convert the in-memory file to a streamed file
                        throw new IOException("Failed to convert to streamed file, " + getFullName() + ", size=" + fileLen);
                    }
                }
                else {

                    // Cannot convert the file to a streamed file
                    throw new DiskFullException("Cannot convert in-memory file to streamed, " + getFullName() + ", size=" + fileLen);
                }
            }
        }

        // Update the write count for the file
        incrementWriteCount();

        // Update the cached file size
        long fileLen = m_memFile.getFileLength();

        if (fileLen != -1L)
            updateFileSize(fileLen, -1L);
    }

    /**
     * Flush any buffered output to the file
     *
     * @throws IOException Error flushing the file
     */
    public void flushFile()
        throws IOException {

        // Nothing to do
    }

    /**
     * Seek to the specified file position.
     *
     * @param pos long
     * @param typ int
     * @return long
     * @throws IOException Error setting the file pointer
     */
    public long seekFile(long pos, int typ)
        throws IOException {
        return 0;
    }

    /**
     * Truncate the file to the specified file size
     *
     * @param siz long
     * @throws IOException Error truncating the file
     */
    public void truncateFile(long siz)
        throws IOException {

        // Check if the file is writeable
        if (getGrantedAccess() == NetworkFile.Access.READ_ONLY)
            throw new AccessDeniedException("File is read-only");

        // Truncate the file
        m_memFile.truncate(siz);

        // Update the cached file size
        updateFileSize(siz, siz);

        // Update the write count for the file
        incrementWriteCount();
    }

    /**
     * Close the file
     */
    public void closeFile() {

        // Mark the file as closed
        setClosed( true);

        // File has been closed, may need to flush data from the memory file if it was updated
        if (getWriteCount() > 0) {

            // Check if there is updated data to be saved
            MemoryBuffer updData = m_memFile.dataToSave();

            if ( updData != null) {

                // Indicate the file has been closed
                m_memFile.closeFile();

                synchronized (m_memFile) {

                    if (m_memFile.isQueued() == false) {

                        // Queue a file data save request
                        getLoader().queueFileRequest(createFileRequest(FileRequest.RequestType.Save, updData.getFileOffset(), updData.getUsedLength(), false));
                    }
                }
            }
        }
    }
}
