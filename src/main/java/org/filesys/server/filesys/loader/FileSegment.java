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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.filesys.debug.Debug;


/**
 * File Segment Class
 *
 * <p>Contains the details of a segment of file data.
 *
 * @author gkspencer
 */
public class FileSegment {

    //	Shared file segment details
    private FileSegmentInfo m_info;

    //	Local file containing the data segment
    private RandomAccessFile m_segment;

    //	Open file for write access
    private boolean m_writeable;

    /**
     * Class constructor
     *
     * <p>Create a file segment to hold all data for a file.
     *
     * @param info      FileSegmentInfo
     * @param writeable boolean
     */
    public FileSegment(FileSegmentInfo info, boolean writeable) {
        m_info = info;
        m_writeable = writeable;
    }

    /**
     * Return the temporary file length, or -1 if the file is not open
     *
     * @return long
     * @throws IOException Failed to get the file length
     */
    public final long getFileLength()
            throws IOException {
        if (isOpen())
            return m_segment.length();
        return -1;
    }

    /**
     * Return the file segment information
     *
     * @return FileSegmentInfo
     */
    public final FileSegmentInfo getInfo() {
        return m_info;
    }

    /**
     * Return the readable file data length
     *
     * @return long
     */
    public final long getReadableLength() {
        return m_info.getReadableLength();
    }

    /**
     * Return the temporary file path
     *
     * @return String
     */
    public final String getTemporaryFile() {
        return m_info.getTemporaryFile();
    }

    /**
     * Check if the file data is loaded or queued for loading
     *
     * @return boolean
     */
    public final boolean isDataLoading() {
        if (m_info.hasStatus() == FileSegmentInfo.Initial &&
                m_info.isQueued() == false)
            return false;
        return true;
    }

    /**
     * Check if the file data is available
     *
     * @return boolean
     */
    public final boolean isDataAvailable() {
        if (m_info.hasStatus() >= FileSegmentInfo.Available &&
                m_info.hasStatus() < FileSegmentInfo.Error)
            return true;
        return false;
    }

    /**
     * Return the segment status
     *
     * @return int
     */
    public final int hasStatus() {
        return m_info.hasStatus();
    }

    /**
     * Check if the file load had an error
     *
     * @return boolean
     */
    public final boolean hasLoadError() {
        return m_info.hasStatus() == FileSegmentInfo.Error;
    }

    /**
     * Set the readable data length for the file, used during data loading to allow the file to be read before
     * the file load completes.
     *
     * @param readable long
     */
    public final void setReadableLength(long readable) {
        m_info.setReadableLength(readable);
    }

    /**
     * Set the segment load/update status
     *
     * @param sts int
     */
    public final void setStatus(int sts) {
        m_info.setStatus(sts);
    }

    /**
     * Set the segment load/update status and queued status
     *
     * @param sts    int
     * @param queued boolean
     */
    public final synchronized void setStatus(int sts, boolean queued) {
        m_info.setStatus(sts);
        m_info.setQueued(queued);
    }

    /**
     * Check if the temporary file is open
     *
     * @return boolean
     */
    public final boolean isOpen() {
        return m_segment != null ? true : false;
    }

    /**
     * Check if the file segment has been updated
     *
     * @return boolean
     */
    public final boolean isUpdated() {
        return m_info.isUpdated();
    }

    /**
     * Check if the file segment has a file request queued
     *
     * @return boolean
     */
    public final boolean isQueued() {
        return m_info.isQueued();
    }

    /**
     * Check if a save request is queued for this file segment
     *
     * @return boolean
     */
    public final synchronized boolean isSaveQueued() {
        if (m_info.isQueued() && m_info.hasStatus() == FileSegmentInfo.SaveWait)
            return true;
        return false;
    }

    /**
     * Check if the file segment is being saved
     *
     * @return boolean
     */
    public final synchronized boolean isSaving() {
        if (m_info.isQueued() && m_info.hasStatus() == FileSegmentInfo.Saving)
            return true;
        return false;
    }

    /**
     * Check if the file segment is being loaded
     *
     * @return boolean
     */
    public final synchronized boolean isLoading() {
        if (m_info.isQueued() && m_info.hasStatus() == FileSegmentInfo.Loading)
            return true;
        return false;
    }

    /**
     * Check if the file is writeable
     *
     * @return boolean
     */
    public final boolean isWriteable() {
        return m_writeable;
    }

    /**
     * Get the load lock for this file. If successful the current thread will proceed and can load the file, else
     * the thread will wait until the load has been completed by the thread with the lock.
     *
     * @return boolean        true if the current thread has the load lock, else false to indicate that the file
     * should now be loaded.
     * @throws InterruptedException Error during wait
     */
    public final synchronized boolean getLoadLock()
            throws InterruptedException {

        //	Check if the file is currently being loaded
        boolean sts = false;

        if (isLoading() == true) {

            //	Wait until the file has been loaded by another thread
            wait();
        } else {

            //	Set the file status to loading
            setStatus(FileSegmentInfo.Loading);
            sts = true;
        }

        //	Return the lock status
        return sts;
    }

    /**
     * Wait for another thread to load the file data
     *
     * @param tmo long
     */
    public final void waitForData(long tmo) {
        m_info.waitForData(tmo);
    }

    /**
     * Signal that the file data is available, any threads using the waitForData() method
     * will return so that the threads can access the file data.
     */
    public final void signalDataAvailable() {
        m_info.signalDataAvailable();
    }

    /**
     * Check if the temporary file exists
     *
     * @return boolean
     */
    public final boolean fileExists() {

        //	Check if the file is open
        if (m_segment != null)
            return true;

        //	Check if the temporary file exists
        File tempFile = new File(getTemporaryFile());
        return tempFile.exists();
    }

    /**
     * Create the temporary file
     *
     * @throws IOException Failed to create the temporary file
     */
    public final void createTemporaryFile()
            throws IOException {

        //	Check if the temporary file already exists
        File tempFile = new File(getTemporaryFile());
        tempFile.createNewFile();
    }

    /**
     * Close the temporary file
     *
     * @throws IOException Failed to close the temporary file
     */
    public final void closeFile()
            throws IOException {

        //	Close the temporary file
        if (m_segment != null) {
            m_segment.close();
            m_segment = null;
        }
    }

    /**
     * Open the temporary file
     *
     * @throws IOException Failed to open the temporary file
     */
    public final void openFile()
            throws IOException {
        if (m_segment == null) {

            //	Open the temporary file
            m_segment = new RandomAccessFile(m_info.getTemporaryFile(), "rw");
        }
    }

    /**
     * Read a block of data from the segment file
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @return Length of data read.
     * @throws IOException Failed to read the file
     */
    public synchronized final int readBytes(byte[] buf, int len, int pos, long fileOff)
            throws IOException {

        //	Check if the temporary file is open
        if (m_segment == null) {

            //	Open the temporary file
            openFile();
        } else {

            //	Check that the file descriptor is valid
            checkFileDescriptor();
        }

        //	Seek to the read position within the segment
        m_segment.seek(fileOff);

        //	Fill the user buffer

        int totLen = 0;
        int rdLen = len;
        int bufPos = pos;

        try {

            while (totLen < len && rdLen > 0) {

                //	Read data into the user buffer
                rdLen = m_segment.read(buf, bufPos, rdLen);

                //	Update the total read length
                if (rdLen > 0) {
                    totLen += rdLen;
                    bufPos += rdLen;
                    rdLen = len - totLen;
                }
            }
        }
        catch (Exception ex) {
            Debug.println("***** FileSegment Read Error *****");
            Debug.println(ex);
        }

        //	Return the total read length
        return totLen;
    }

    /**
     * Write a block of data to the segment file
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @throws IOException Failed to write the file
     */
    public synchronized final void writeBytes(byte[] buf, int len, int pos, long fileOff)
            throws IOException {

        //	Check if the temporary file is open
        if (m_segment == null) {

            //	Open the temporary file
            openFile();
        } else {

            //	Check that the file descriptor is valid
            checkFileDescriptor();
        }

        //	We need to seek to the write position. If the write position is off the end of the file
        //	we must null out the area between the current end of file and the write position.
        long fileLen = m_segment.length();
        long endpos = fileOff + len;

        if (fileOff > fileLen) {

            //	Extend the file
            m_segment.setLength(endpos);
        }

        //	Check for a zero length write
        if (len == 0)
            return;

        //	Seek to the write position within the segment
        m_segment.seek(fileOff);

        //	Write data to the segment file
        m_segment.write(buf, pos, len);

        //	Update the file segment status to indicate the data has been updated
        if (m_info.isUpdated() == false)
            m_info.setUpdated(true);
    }

    /**
     * Flush buffered output to the file
     *
     * @throws IOException Failed to flush the file
     */
    public final void flush()
            throws IOException {

        //	If the file is open flush all buffered output
        if (m_segment != null)
            m_segment.getFD().sync();
    }

    /**
     * Truncate the file to the specified size
     *
     * @param siz long
     * @exception IOException Failed to truncate the file
     */
    public final void truncate(long siz)
            throws IOException {

        //	Check if the temporary file is open
        if (m_segment == null)
            openFile();

        //	Set the temporary file size
        m_segment.setLength(siz);
    }

    /**
     * Delete the temporary file associated with this file segment
     *
     * @throws IOException Failed to delete the temporary file
     */
    public final synchronized void deleteTemporaryFile()
            throws IOException {

        //	Delete the temporary file
        if (m_segment != null)
            throw new IOException("Attempt to delete file segment whilst open");
        else if (m_info != null)
            m_info.deleteTemporaryFile();
    }

    /**
     * Create a file segment
     *
     * @param info      FileSegmentInfo
     * @param prefix    String
     * @param fname     String
     * @param tempDir   File
     * @param writeable boolean
     * @return FileSegment
     * @throws IOException Failed to create the file segment
     */
    public final static FileSegment createSegment(FileSegmentInfo info, String prefix, String fname, File tempDir, boolean writeable)
            throws IOException {

        //	Create a temporary file for the data segment
        File tempFile = File.createTempFile(prefix, fname, tempDir);
        info.setTemporaryFile(tempFile.getAbsolutePath());

        //	Create the file segment to hold the entire file data
        return new FileSegment(info, writeable);
    }

    /**
     * Create a file segment
     *
     * @param info      FileSegmentInfo
     * @param fname     String
     * @param tempDir   File
     * @param writeable boolean
     * @return FileSegment
     * @throws IOException Failed to create the file segment
     */
    public final static FileSegment createSegment(FileSegmentInfo info, String fname, File tempDir, boolean writeable)
            throws IOException {

        //	Create a temporary file for the data segment
        File tempFile = new File(tempDir, fname);
        info.setTemporaryFile(tempFile.getAbsolutePath());

        //	Create the file segment to hold the entire file data
        return new FileSegment(info, writeable);
    }

    /**
     * Return the file segment details as a string
     *
     * @return String
     */
    public String toString() {
        return m_info.toString();
    }

    /**
     * Object is about to be garbage collected
     */
    protected void finalize() {

        //	Make sure the file is closed
        if (m_segment != null) {
            try {
                m_segment.close();
                m_segment = null;
            }
            catch (Exception ex) {
                Debug.println(ex);
            }
        }
    }

    /**
     * Check if the file descriptor is valid
     *
     * @throws IOException Error checking the file descriptor
     */
    private final void checkFileDescriptor()
            throws IOException {

        //	Check if the file is open
        if (m_segment != null) {

            //	Check if the file descriptor is valid
            if (m_segment.getFD() != null && m_segment.getFD().valid() == false) {

                //	Close the file
                try {
                    closeFile();
                }
                catch (Exception ex) {
                }

                //	Re-open the file
                openFile();
            }
        }
    }
}
