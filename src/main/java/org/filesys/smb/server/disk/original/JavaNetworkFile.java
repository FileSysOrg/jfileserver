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

package org.filesys.smb.server.disk.original;

import org.filesys.server.filesys.AccessMode;
import org.filesys.server.filesys.DiskFullException;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.smb.SeekType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Network file implementation that uses the java.io.File class.
 *
 * @author gkspencer
 */
public class JavaNetworkFile extends NetworkFile {

    //	File details
    protected File m_file;

    //	Random access file used to read/write the actual file
    protected RandomAccessFile m_io;

    //	End of file flag
    protected boolean m_eof;

    /**
     * Class constructor.
     *
     * @param file    File
     * @param netPath String
     */
    public JavaNetworkFile(File file, String netPath) {
        super(file.getName());

        //  Set the file using the existing file object
        m_file = file;

        //  Set the file size
        setFileSize(m_file.length());
        m_eof = false;

        //	Set the modification date/time, if available. Fake the creation date/time as it's not
        //	available from the File class
        long modDate = m_file.lastModified();
        setModifyDate(modDate);
        setCreationDate(modDate);

        //	Set the file id
        setFileId(netPath.hashCode());
    }

    /**
     * Class constructor.
     *
     * @param name    String
     * @param netPath String
     */
    public JavaNetworkFile(String name, String netPath) {
        super(name);

        //  Create the file object
        File newFile = new File(name);

        //  Check if the file exists
        if (newFile.exists()) {

            //  Set the file object
            m_file = newFile;
        }
        else {

            //  Convert the file name to lowercase and try again
            String lowerName = name.toLowerCase();
            File newFile2 = new File(lowerName);

            if (newFile2.exists()) {

                //  Set the file
                m_file = newFile2;
            }
            else {

                //  Set the file to be the original file name
                m_file = newFile;

                //  Create the file
                try {
                    FileOutputStream outFile = new FileOutputStream(newFile);
                    outFile.close();
                }
                catch (Exception ex) {
                }
            }
        }

        //  Set the file size
        setFileSize(m_file.length());
        m_eof = false;

        //	Set the modification date/time, if available. Fake the creation date/time as it's not
        //	available from the File class
        long modDate = m_file.lastModified();
        setModifyDate(modDate);
        setCreationDate(modDate);

        //	Set the file id
        setFileId(netPath.hashCode());
    }

    /**
     * Class constructor.
     *
     * @param name File name/path
     * @param mode File access mode
     */
    public JavaNetworkFile(String name, int mode) {
        super(name);

        //  Create the file object
        File newFile = new File(name);

        //  Check if the file exists
        if (newFile.exists() == false) {

            //  Convert the file name to lowercase and try again
            String lowerName = name.toLowerCase();
            File newFile2 = new File(lowerName);

            if (newFile2.exists()) {

                //  Set the file
                m_file = newFile2;
            }
            else {

                //  Set the file to be the original file name
                m_file = newFile;

                //  Create the file, if not opening the file read-only
                if (AccessMode.getAccessMode(mode) != AccessMode.ReadOnly) {

                    //  Create a new file
                    try {
                        FileOutputStream outFile = new FileOutputStream(newFile);
                        outFile.close();
                    }
                    catch (Exception ex) {
                    }
                }
            }
        }

        //  Set the file size
        setFileSize(m_file.length());
        m_eof = false;

        //	Set the modification date/time, if available. Fake the creation date/time as it's not
        //	available from the File class
        long modDate = m_file.lastModified();
        setModifyDate(modDate);
        setCreationDate(modDate);
    }

    /**
     * Close the network file.
     *
     * @exception IOException I/O error
     */
    public void closeFile() throws IOException {

        //  Close the file, if used
        if (m_io != null) {

            //	Close the file
            m_io.close();
            m_io = null;

            //	Set the last modified date/time for the file
            if (this.isModifyDateDirty()) {
                long curTime = System.currentTimeMillis();
                m_file.setLastModified(curTime);
                this.setModifyDate(curTime);
            }

            //	Indicate that the file is closed
            setClosed(true);
        }
    }

    /**
     * Return the current file position.
     *
     * @return long
     */
    public long currentPosition() {

        //  Check if the file is open
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
     * @exception IOException I/O error
     */
    public void flushFile()
            throws IOException {

        //	Flush all buffered data
        if (m_io != null)
            m_io.getFD().sync();
    }

    /**
     * Determine if the end of file has been reached.
     *
     * @return boolean
     * @exception IOException I/O error
     */
    public boolean isEndOfFile() throws IOException {

        //  Check if we reached end of file
        if (m_io != null && m_io.getFilePointer() == m_io.length())
            return true;
        return false;
    }

    /**
     * Open the file.
     *
     * @param createFlag boolean
     * @exception IOException I/O error
     */
    public void openFile(boolean createFlag)
            throws IOException {

        synchronized (m_file) {

            //	Check if the file is open
            if (m_io == null) {

                //  Open the file
                m_io = new RandomAccessFile(m_file, getGrantedAccess() == Access.READ_WRITE ? "rw" : "r");

                //	Indicate that the file is open
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
     * @exception IOException I/O error
     */
    public int readFile(byte[] buf, int len, int pos, long fileOff)
            throws IOException {

        //  Open the file, if not already open
        if (m_io == null)
            openFile(false);

        //	Seek to the required file position
        if (currentPosition() != fileOff)
            seekFile(fileOff, SeekType.StartOfFile);

        //  Read from the file
        int rdlen = m_io.read(buf, pos, len);

        //	Return the actual length of data read
        return rdlen;
    }

    /**
     * Seek to the specified file position.
     *
     * @param pos long
     * @param typ int
     * @return long
     * @exception IOException I/O error
     */
    public long seekFile(long pos, int typ) throws IOException {

        //  Open the file, if not already open
        if (m_io == null)
            openFile(false);

        //  Check if the current file position is the required file position
        switch (typ) {

            //  From start of file
            case SeekType.StartOfFile:
                if (currentPosition() != pos)
                    m_io.seek(pos);
                break;

            //  From current position
            case SeekType.CurrentPos:
                m_io.seek(currentPosition() + pos);
                break;

            //  From end of file
            case SeekType.EndOfFile: {
                long newPos = m_io.length() + pos;
                m_io.seek(newPos);
            }
            break;
        }

        //  Return the new file position
        return currentPosition();
    }

    /**
     * Truncate the file
     *
     * @param siz long
     * @exception IOException I/O error
     */
    public void truncateFile(long siz)
            throws IOException {

        //  Open the file, if not already open
        if (m_io == null)
            openFile(true);
        else
            m_io.getFD().sync();

        //	Check if the file length is being truncated or extended
        boolean extendFile = siz > getFileSize() ? true : false;

        //  Set the file length
        try {
            m_io.setLength(siz);

            //	Update the file size
            setFileSize(siz);
        }
        catch (IOException ex) {

            //	Error during file extend, assume it's a disk full type error
            if (extendFile == true)
                throw new DiskFullException("Failed to extend file, " + getFullName());
            else {

                //	Rethrow the original I/O exception
                throw ex;
            }
        }
    }

    /**
     * Write a block of data to the file.
     *
     * @param buf byte[]
     * @param len int
     * @param pos int
     * @exception IOException I/O error
     */
    public void writeFile(byte[] buf, int len, int pos)
            throws IOException {

        //  Open the file, if not already open
        if (m_io == null)
            openFile(true);

        //  Write to the file
        m_io.write(buf, pos, len);

        //	Update the write count for the file
        incrementWriteCount();
    }

    /**
     * Write a block of data to the file.
     *
     * @param buf    byte[]
     * @param len    int
     * @param pos    int
     * @param offset long
     * @exception IOException I/O error
     */
    public void writeFile(byte[] buf, int len, int pos, long offset)
            throws IOException {

        //  Open the file, if not already open
        if (m_io == null)
            openFile(true);

        //	We need to seek to the write position. If the write position is off the end of the file
        //	we must null out the area between the current end of file and the write position.
        long fileLen = m_io.length();

        if (offset > fileLen) {

            //	Extend the file
            m_io.setLength(offset + len);
        }

        //	Check for a zero length write
        if (len == 0)
            return;

        //	Seek to the write position
        m_io.seek(offset);

        //  Write to the file
        m_io.write(buf, pos, len);

        //	Update the write count for the file
        incrementWriteCount();
    }
}
