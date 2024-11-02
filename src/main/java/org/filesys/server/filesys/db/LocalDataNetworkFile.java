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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.filesys.server.filesys.cache.FileState;
import org.filesys.smb.SeekType;

/**
 * Local Data Network File Class
 *
 * <p>Maps a file in a virtual filesystem to a file in the local filesystem.
 *
 * @author gkspencer
 */
public class LocalDataNetworkFile extends DBNetworkFile {

    //	File details
    protected File m_file;

    //	Random access file used to read/write the actual file
    protected RandomAccessFile m_io;

    /**
     * Class constructor
     *
     * @param name String
     * @param fid  int
     * @param did  int
     * @param file File
     */
    public LocalDataNetworkFile(String name, int fid, int did, File file) {
        super(name, fid, 0, did);

        //	Set the file details
        m_file = file;
        setClosed( false);

        //  Set the file size
        setFileSize(m_file.length());

        //	Set the modification date/time, if available
        setModifyDate(m_file.lastModified());
    }

    /**
     * Open the file
     *
     * @param createFlag boolean
     * @throws IOException Error opening the file
     */
    public void openFile(boolean createFlag)
            throws IOException {

        // Only open the file if we are creating a new file
        if ( createFlag)
            m_io = new RandomAccessFile(m_file, "rw");

        // Set/clear the create required flag
        setCreateRequired( createFlag);
    }

    /**
     * Read from the file.
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff int
     * @return Length of data read.
     * @throws IOException Error reading the file
     */
    public int readFile(byte[] buf, int len, int pos, long fileOff)
            throws IOException {

        //  Open the file, if not already open
        if (m_io == null)
            openUnderlyingFile();

        //	Seek to the read position
        m_io.seek(fileOff);

        //  Read from the file
        int rdlen = m_io.read(buf, pos, len);
        incrementReadCount();
        return rdlen;
    }

    /**
     * Write a block of data to the file.
     *
     * @param buf    byte[]
     * @param len    int
     * @param pos    int
     * @param offset int
     * @throws IOException Error writing to the file
     */
    public void writeFile(byte[] buf, int len, int pos, long offset)
            throws IOException {

        //  Open the file, if not already open
        if (m_io == null)
            openUnderlyingFile();

        //	We need to seek to the write position. If the write position is off the end of the file
        //	we must null out the area between the current end of file and the write position.
        long fileLen = m_io.length();
        long endpos = offset + (long) len;

        if (endpos > fileLen) {

            //	Extend the file
            m_io.setLength(endpos);

            // Update the file size
            setFileSize( endpos);
        }

        //	Check for a zero length write
        if (len == 0)
            return;

        //	Seek to the write position
        m_io.seek(offset);

        //  Write to the file
        m_io.write(buf, pos, len);
        incrementWriteCount();
        setStatus(FileState.DataStatus.Updated);
    }

    /**
     * Flush any buffered output to the file
     *
     * @throws IOException Error flushing the file
     */
    public final void flushFile()
            throws IOException {

        //	Flush any buffered data
        if (m_io != null) {
            m_io.getFD().sync();
            setFileSize(m_io.length());
        }
    }

    /**
     * Seek to the specified file position.
     *
     * @param pos long
     * @param typ int
     * @return long
     * @throws IOException Error setting the file position
     */
    public long seekFile(long pos, int typ)
            throws IOException {

        //  Open the file, if not already open
        if (m_io == null)
            openUnderlyingFile();

        //  Check if the current file position is the required file position
        long curPos = m_io.getFilePointer();

        switch (typ) {

            //  From start of file
            case SeekType.StartOfFile:
                if (curPos != pos)
                    m_io.seek(pos);
                break;

            //  From current position
            case SeekType.CurrentPos:
                m_io.seek(curPos + pos);
                break;

            //  From end of file
            case SeekType.EndOfFile: {
                long newPos = m_io.length() + pos;
                m_io.seek(newPos);
            }
            break;
        }

        //  Return the new file position
        return (int) (m_io.getFilePointer() & 0xFFFFFFFF);
    }

    /**
     * Truncate the file to the specified file size
     *
     * @param siz long
     * @throws IOException Error truncating the file
     */
    public void truncateFile(long siz)
            throws IOException {

        //  Open the file, if not already open
        if (m_io == null)
            openUnderlyingFile();

        //  Set the file length
        m_io.setLength(siz);

        //	Indicate that the file data has changed
        incrementWriteCount();
    }

    /**
     * Close the file
     */
    public void closeFile() {

        //  Close the file, if used
        if (m_io != null) {

            //	Close the file
            try {
                m_io.close();
            }
            catch (Exception ex) {
            }
            m_io = null;

            //	Set the last modified date/time for the file
            if (this.isModifyDateDirty()) {
                long curTime = System.currentTimeMillis();
                m_file.setLastModified(curTime);
                this.setModifyDate(curTime);
            }

            //	Set the new file size
            setFileSize(m_file.length());
        }
    }

    /**
     * Internal method to open the underlying file
     *
     * @exception IOException Error opening the file
     */
    private void openUnderlyingFile()
        throws IOException {

        // Check if the file should exist or can be created
        if (!hasCreateRequired() && !m_file.exists())
            throw new IOException();

        // Open or create the file
        m_io = new RandomAccessFile(m_file, "rw");
        setStatusFlag(Flags.CLOSED, false);
    }
}
