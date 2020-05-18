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
public class FileSegment extends Segment {

    //	Local file containing the data segment
    private RandomAccessFile m_tempFile;

    /**
     * Class constructor
     *
     * <p>Create a file segment to hold all data for a file.
     *
     * @param info      FileSegmentInfo
     * @param writeable boolean
     */
    public FileSegment(FileSegmentInfo info, boolean writeable) {
        super( info, writeable);
    }

    /**
     * Return the temporary file length, or -1 if the file is not open
     *
     * @return long
     * @throws IOException Failed to get the file length
     */
    public long getFileLength()
        throws IOException {
        if (isOpen())
            return m_tempFile.length();
        return -1;
    }

    /**
     * Return the file segment information
     *
     * @return FileSegmentInfo
     */
    public final FileSegmentInfo getFileInfo() {
        return (FileSegmentInfo) getInfo();
    }

    /**
     * Return the temporary file path
     *
     * @return String
     */
    public final String getTemporaryFile() { return getFileInfo().getTemporaryFile(); }

    /**
     * Check if the temporary file is open
     *
     * @return boolean
     */
    public final boolean isOpen() {
        return m_tempFile != null ? true : false;
    }

    /**
     * Check if the temporary file exists
     *
     * @return boolean
     */
    public final boolean fileExists() {

        //	Check if the file is open
        if (m_tempFile != null)
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
        if (m_tempFile != null) {
            m_tempFile.close();
            m_tempFile = null;
        }
    }

    /**
     * Open the temporary file
     *
     * @throws IOException Failed to open the temporary file
     */
    public final void openFile()
            throws IOException {
        if (m_tempFile == null) {

            //	Open the temporary file
            m_tempFile = new RandomAccessFile(getFileInfo().getTemporaryFile(), "rw");
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
        if (m_tempFile == null) {

            //	Open the temporary file
            openFile();
        } else {

            //	Check that the file descriptor is valid
            checkFileDescriptor();
        }

        //	Seek to the read position within the segment
        m_tempFile.seek(fileOff);

        //	Fill the user buffer

        int totLen = 0;
        int rdLen = len;
        int bufPos = pos;

        try {

            while (totLen < len && rdLen > 0) {

                //	Read data into the user buffer
                rdLen = m_tempFile.read(buf, bufPos, rdLen);

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
        if (m_tempFile == null) {

            //	Open the temporary file
            openFile();
        } else {

            //	Check that the file descriptor is valid
            checkFileDescriptor();
        }

        //	We need to seek to the write position. If the write position is off the end of the file
        //	we must null out the area between the current end of file and the write position.
        long fileLen = m_tempFile.length();
        long endpos = fileOff + len;

        if (fileOff > fileLen) {

            //	Extend the file
            m_tempFile.setLength(endpos);
        }

        //	Check for a zero length write
        if (len == 0)
            return;

        //	Seek to the write position within the segment
        m_tempFile.seek(fileOff);

        //	Write data to the segment file
        m_tempFile.write(buf, pos, len);

        //	Update the file segment status to indicate the data has been updated
        if (getInfo().isUpdated() == false)
            getInfo().setUpdated(true);
    }

    /**
     * Flush buffered output to the file
     *
     * @throws IOException Failed to flush the file
     */
    public final void flush()
            throws IOException {

        //	If the file is open flush all buffered output
        if (m_tempFile != null)
            m_tempFile.getFD().sync();
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
        if (m_tempFile == null)
            openFile();

        //	Set the temporary file size
        m_tempFile.setLength(siz);
    }

    /**
     * Delete the temporary file associated with this file segment
     *
     * @throws IOException Failed to delete the temporary file
     */
    public final synchronized void deleteTemporaryFile()
            throws IOException {

        //	Delete the temporary file
        if (m_tempFile != null)
            throw new IOException("Attempt to delete file segment whilst open");
        else if (getFileInfo() != null)
            getFileInfo().deleteTemporaryFile();
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
     * Object is about to be garbage collected
     */
    protected void finalize() {

        //	Make sure the file is closed
        if (m_tempFile != null) {
            try {
                m_tempFile.close();
                m_tempFile = null;
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
        if (m_tempFile != null) {

            //	Check if the file descriptor is valid
            if (m_tempFile.getFD() != null && m_tempFile.getFD().valid() == false) {

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
