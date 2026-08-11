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

package org.filesys.server.filesys.pseudo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.LocalFileState;
import org.filesys.server.filesys.cache.NetworkFileStateInterface;
import org.filesys.smb.SeekType;

/**
 * Pseudo File Network File Class
 *
 * <p>
 * Represents an open pseudo file and provides access to the file data.
 *
 * @author gkspencer
 */
public class PseudoNetworkFile extends NetworkFile implements NetworkFileStateInterface {

    // File details
    protected File m_file;

    // Random access file used to read/write the actual file
    protected RandomAccessFile m_io;

    // End of file flag
    protected boolean m_eof;

    // Dummy file state, required to implement byte range locking
    private FileState m_state;

    /**
     * Class constructor.
     *
     * @param name      String
     * @param localPath String
     * @param netPath   String
     */
    public PseudoNetworkFile(String name, String localPath, String netPath) {
        super(name);

        // Set the file using the existing file object
        m_file = new File(localPath);

        // Set the file size
        setFileSize(m_file.length());
        m_eof = false;

        // Set the modification date/time, if available. Fake the creation date/time as it's not
        // available from the File class
        long modDate = m_file.lastModified();
        setModifyDate(modDate);
        setCreationDate(modDate);

        // Set the file id
        setFileId(netPath.hashCode());

        // Set the full relative path
        setFullName(netPath);
    }

    /**
     * Close the network file.
     *
     * @exception IOException Failed to close the file
     */
    public void closeFile()
            throws IOException {

        // Close the file, if used
        if (m_io != null) {

            // Close the file
            m_io.close();
            m_io = null;

            // Set the last modified date/time for the file
            if (this.isModifyDateDirty()) {
                long curTime = System.currentTimeMillis();
                m_file.setLastModified(curTime);
                this.setModifyDate(curTime);
            }

            // Indicate that the file is closed
            setClosed(true);
        }

        // Clear the file state
        m_state = null;
    }

    /**
     * Return the current file position.
     *
     * @return long
     */
    public long currentPosition() {

        // Check if the file is open
        try {
            if (m_io != null)
                return m_io.getFilePointer();
        }
        catch (Exception ex) {
        }
        return 0;
    }

    /**
     * Flush the file.
     *
     * @throws IOException Failed to flush the file
     */
    public void flushFile()
            throws IOException {

        // Flush all buffered data
        if (m_io != null)
            m_io.getFD().sync();
    }

    /**
     * Determine if the end of file has been reached.
     *
     * @return boolean
     * @exception IOException Error during end of file check
     */
    public boolean isEndOfFile()
            throws IOException {

        // Check if we reached end of file
        if (m_io != null && m_io.getFilePointer() == m_io.length())
            return true;
        return false;
    }

    /**
     * Open the file.
     *
     * @param createFlag boolean
     * @throws IOException Failed to open the file
     */
    public void openFile(boolean createFlag)
            throws IOException {

        synchronized (m_file) {

            // Check if the file is open
            if (m_io == null) {

                // Open the file, always read-only for now
                m_io = new RandomAccessFile(m_file, "r");

                // Indicate that the file is open
                setClosed(false);
            }
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
     * @throws IOException Failed to read the file
     */
    public int readFile(byte[] buf, int len, int pos, long fileOff)
            throws IOException {

        // Open the file, if not already open
        if (m_io == null)
            openFile(false);

        // Seek to the required file position
        if (currentPosition() != fileOff)
            seekFile(fileOff, SeekType.StartOfFile);

        // Read from the file
        int rdlen = m_io.read(buf, pos, len);

        // Return the actual length of data read
        return rdlen;
    }

    /**
     * Seek to the specified file position.
     *
     * @param pos long
     * @param typ int
     * @return long
     * @throws IOException Failed to set the file position
     */
    public long seekFile(long pos, int typ)
            throws IOException {

        // Open the file, if not already open
        if (m_io == null)
            openFile(false);

        // Check if the current file position is the required file position
        switch (typ) {

            // From start of file
            case SeekType.StartOfFile:
                if (currentPosition() != pos)
                    m_io.seek(pos);
                break;

            // From current position
            case SeekType.CurrentPos:
                m_io.seek(currentPosition() + pos);
                break;

            // From end of file
            case SeekType.EndOfFile: {
                long newPos = m_io.length() + pos;
                m_io.seek(newPos);
            }
            break;
        }

        // Return the new file position
        return currentPosition();
    }

    /**
     * Truncate the file
     *
     * @param siz long
     * @throws IOException Failed to truncate the file
     */
    public void truncateFile(long siz)
            throws IOException {

        // Allow the truncate, just do not do anything
    }

    /**
     * Write a block of data to the file.
     *
     * @param buf byte[]
     * @param len int
     * @param pos int
     * @throws IOException Failed to write the file
     */
    public void writeFile(byte[] buf, int len, int pos)
            throws IOException {

        // Allow the write, just do not do anything
    }

    /**
     * Write a block of data to the file.
     *
     * @param buf    byte[]
     * @param len    int
     * @param pos    int
     * @param offset long
     * @throws IOException Failed to write the file
     */
    public void writeFile(byte[] buf, int len, int pos, long offset)
            throws IOException {

        // Allow the write, just do not do anything
    }

    /**
     * Return a dummy file state for this file
     *
     * @return FileState
     */
    public FileState getFileState() {

        // Create a dummy file state

        if (m_state == null)
            m_state = new LocalFileState(getFullName(), false);
        return m_state;
    }
}
