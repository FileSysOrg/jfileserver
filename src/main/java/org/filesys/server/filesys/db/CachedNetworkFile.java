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

import java.io.FileNotFoundException;
import java.io.IOException;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.AccessDeniedException;
import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.FileOfflineException;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateProxy;
import org.filesys.server.filesys.loader.FileLoader;
import org.filesys.server.filesys.loader.FileRequest;
import org.filesys.server.filesys.loader.FileSegment;
import org.filesys.server.filesys.loader.FileSegmentInfo;
import org.filesys.server.filesys.loader.SingleFileRequest;

/**
 * Cached Data Network File Class
 *
 * <p>
 * Caches the file data in the local filesystem in a temporary area.
 *
 * @author gkspencer
 */
public class CachedNetworkFile extends DBNetworkFile {

    // Maximum time to wait for file data
    protected static final long DataLoadWaitTime    = 20000L; // 20 seconds
    protected static final long DataPollSleepTime   = 250L;    // milliseconds

    // Debug enable flag
    private static final boolean DEBUG = false;

    // File segment holding a local copy of the file data
    protected FileSegment m_cacheFile;

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
     * @param segment FileSegment
     * @param loader  FileLoader
     */
    public CachedNetworkFile(String name, int fid, int stid, int did, FileStateProxy state, FileSegment segment, FileLoader loader) {
        super(name, fid, stid, did);

        // Set the file segment and memory segment list
        m_cacheFile = segment;

        // Save the file state
        setFileState(state);

        // Set the associated file loader
        setLoader(loader);
    }

    /**
     * Return the associated file segment
     *
     * @return FileSegment
     */
    public final FileSegment getFileSegment() {
        return m_cacheFile;
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
     * Open the file
     *
     * @param createFlag boolean
     * @throws IOException Error opening the file
     */
    public void openFile(boolean createFlag)
            throws IOException {

        // Open the file segment temporary file
        try {

            // Check if the temporary file exists
            if (m_cacheFile.fileExists() == false) {

                // Create the temporary file
                m_cacheFile.createTemporaryFile();

                // DEBUG
                if (DEBUG)
                    Debug.println("CachedNetworkFile.openFile() created " + m_cacheFile.getTemporaryFile());
            }

            // Open the temporary file
            m_cacheFile.openFile();

            // Mark the file as open
            setClosed(false);
        }
        catch (FileNotFoundException ex) {
            if (DEBUG) {
                Debug.println("openFile() FAILED name=" + getFullName() + ", error=" + ex.toString());
                Debug.println("  fstate=" + getFileState());
                Debug.println("  cacheFile=" + m_cacheFile);
            }

            // Rethrow the exception
            throw ex;
        }
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
            Debug.println("CachedNetworkFile.readFile() offset=" + fileOff + ", len=" + len);

        // Determine if this is a sequential read
        boolean seqRead = false;

        if (m_lastReadPos != -1L && fileOff == (m_lastReadPos + m_lastReadLen)) {

            // Indicate that this is a sequential read
            seqRead = true;
            m_seqReads++;
        }

        // Check for a file segment error
        if (m_cacheFile.hasStatus() == FileSegmentInfo.Error) {

            // DEBUG
            if (DEBUG)
                Debug.println("CachedNetworkFile file segment error");

            // Indicate no more data
            throw new IOException("Load file error - " + getFullName());
        }

        // Update the last read position/length
        m_lastReadPos = fileOff;
        m_lastReadLen = len;

        // Check if the file data has been loaded
        if (m_cacheFile.hasStatus() == FileSegmentInfo.Initial && m_cacheFile.isQueued() == false) {

            // Check if the temporary file exists
            if (m_cacheFile.fileExists() == false) {

                // Create the temporary file
                m_cacheFile.createTemporaryFile();
            }

            synchronized (getFileState()) {

                // Queue a file data load request
                if (m_cacheFile.isQueued() == false)
                    getLoader().queueFileRequest(createFileRequest(FileRequest.LOAD));
            }
        }

        // Check if the file data is available, or still loading
        int rdlen = 0;

        if (m_cacheFile.isDataAvailable()) {

            // DEBUG
            if (DEBUG)
                Debug.println("CachedNetworkFile DataAvailable Read, file=" + getName() + ", fid=" + getFileId());

            // Read the file using the file segment
            rdlen = m_cacheFile.readBytes(buf, len, pos, fileOff);

            // DEBUG
            if (rdlen <= 0) {

                // DEBUG
                if (DEBUG) {
                    Debug.println("CachedNetworkFile.readFile() name=" + getFullName());
                    Debug.println("  State=" + getFileState());
                    Debug.println("  Segment=" + getFileSegment());
                    Debug.println("  Size=" + getFileSize());
                }

                m_cacheFile.closeFile();
                m_cacheFile.openFile();
                rdlen = m_cacheFile.readBytes(buf, len, pos, fileOff);
            }

            // Return the length of data read
            return rdlen;
        }

        // Wait for the required amount of data to be written to the temporary file
        long waitTime = 0L;
        boolean readDone = false;
        boolean dataAvailable = false;

        while (readDone == false && waitTime < DataLoadWaitTime && (m_cacheFile.isDataLoading() || m_cacheFile.isDataAvailable())) {

            // Check if there is enough data available to satisfy the read request
            dataAvailable = m_cacheFile.isDataAvailable();

            if (dataAvailable == false) {

                // File loader thread is still loading the file data, check the file length to see if
                // there is enough data to satisfy the read request. Check that there is more data available
                // than required as the loader may have extended the file and still be writing the last block
                // of data.
                long fileLen = m_cacheFile.getReadableLength();

                if (fileLen != -1 && (fileLen + 0xFFFF) > (fileOff + len))
                    rdlen = m_cacheFile.readBytes(buf, len, pos, fileOff);
            } else
                rdlen = m_cacheFile.readBytes(buf, len, pos, fileOff);

            // Check if the read was successful
            if (rdlen > 0) {

                // Indicate that the required data has been read
                if (dataAvailable == false && rdlen < len)
                    readDone = false;
                else
                    readDone = true;
            } else if (dataAvailable == true) {

                // No more data available
                readDone = true;
            } else {

                // Wait for some data to be loaded
                try {

                    // Indicate that an I/O is pending on this file
                    setIOPending(true);

                    // Wait for the file data to be loaded, or for large files the wait may timeout and we can check
                    // if there is enough data available to satisfy the current read request.
                    long startTime = System.currentTimeMillis();
                    m_cacheFile.waitForData(DataPollSleepTime);
                    long endTime = System.currentTimeMillis();

                    // Update the total data wait time
                    waitTime += (endTime - startTime);

                    // Clear the I/O pending flag
                    setIOPending(false);

                    // DEBUG
                    if (DEBUG)
                        Debug.println("CachedNetworkFile waited " + (endTime - startTime) + "ms for data, available="
                                + m_cacheFile.getReadableLength());
                }
                catch (Exception ex) {
                }

                // Check for a file load error
                if (m_cacheFile.hasLoadError())
                    throw new IOException("Load file error - " + getFullName());

                // DEBUG
                if (DEBUG) {
                    Debug.println("m_cacheFile=" + m_cacheFile + ", rdlen=" + rdlen);
                    Debug.println("fstate=" + getFileState());
                }
            }
        }

        // Check for a file load error
        if (m_cacheFile.hasLoadError())
            throw new IOException("Load file error - " + getFullName());

        // DEBUG
        if (DEBUG)
            Debug.println("CachedNetworkFile.readFile() Waited " + waitTime + "ms for data");

        // Check if the required data has been read
        if (readDone == true) {

            // Return the length of data read, may be shorter than requested
            return rdlen;
        } else {

            // DEBUG
            if (DEBUG) {
                Debug.println("ReadFault fname=" + getFullName() + ", fid=" + getFileId() + ", state=" + getFileState());
                Debug.println("  " + m_cacheFile.toString());
            }

            throw new FileOfflineException("File data not available");
        }
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
        if (getGrantedAccess() == Access.READ_ONLY)
            throw new AccessDeniedException("File is read-only");

        // Write the file using the file segment
        m_cacheFile.writeBytes(buf, len, pos, offset);

        // Update the write count for the file
        incrementWriteCount();

        // Update the cached file size
        long fileLen = m_cacheFile.getFileLength();
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

        // Flush all buffered output, if the file is open
        if (m_cacheFile != null && m_cacheFile.isOpen())
            m_cacheFile.flush();
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
        if (getGrantedAccess() == Access.READ_ONLY)
            throw new AccessDeniedException("File is read-only");

        // Truncate the file
        m_cacheFile.truncate(siz);

        // Update the cached file size
        updateFileSize(siz, siz);

        // Update the write count for the file
        incrementWriteCount();
    }

    /**
     * Close the file
     */
    public void closeFile() {

        // Close the associated file segment temporary file
        if (m_cacheFile != null) {
            try {

                // Update the file length
                if (m_cacheFile.isDataAvailable() && m_cacheFile.isOpen()) {
                    long fileSize = m_cacheFile.getFileLength();
                    if (fileSize != -1L)
                        setFileSize(fileSize);
                }

                // Close the file
                m_cacheFile.closeFile();

                // DEBUG
                if (DEBUG)
                    Debug.println("CachedNetworkFile.closeFile()");
            }
            catch (IOException ex) {
                if (DEBUG) {
                    Debug.println("**** Error closing file " + getName() + ", fid=" + getFileId() + " ****");
                    Debug.println(ex);
                }
            }
        }
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
     * Create a file load or save request. This method may be overridden to allow extending of the
     * SingleFileRequest class.
     *
     * @param typ int
     * @return FileRequest
     */
    protected FileRequest createFileRequest(int typ) {

        // DEBUG
        if (DEBUG)
            Debug.println("CachedNetworkFile.createFileRequest() fullName=" + getFullName() + ", state=" + getFileState());

        // Create a file load or save request
        return new SingleFileRequest(typ, getFileId(), getStreamId(), m_cacheFile.getInfo(), getFullName(), getFileState());
    }

    /**
     * Determine if network file debug output is enabled
     *
     * @return boolean
     */
    protected final boolean hasDebug() {
        return DEBUG;
    }

    /**
     * Object is about to be garbage collected
     */
    protected void finalize() {

        // Make sure the file is closed
        if (m_cacheFile != null && m_cacheFile.isOpen()) {
            try {
                m_cacheFile.closeFile();
                m_cacheFile = null;
            }
            catch (Exception ex) {
                Debug.println(ex);
            }
        }
    }
}
