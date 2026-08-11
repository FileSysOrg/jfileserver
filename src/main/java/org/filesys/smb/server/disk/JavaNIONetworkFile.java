/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.smb.server.disk;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.DiskFullException;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.smb.SeekType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Network file implementation that uses the java.io.File class.
 *
 * @author gkspencer
 */
public class JavaNIONetworkFile extends NetworkFile {

    //	File path
    protected Path m_path;

    //	File channel used to read/write the actual file
    protected FileChannel m_io;

    //	End of file flag
    protected boolean m_eof;

    /**
     * Class constructor.
     *
     * @param path    Path
     * @param netPath String
     * @exception IOException I/O error
     */
    public JavaNIONetworkFile(Path path, String netPath)
            throws IOException {
        super( path.getFileName().toString());

        //  Set the file path
        m_path = path;

        //  Set the file size
        setFileSize(Files.size( m_path));
        m_eof = false;

        //	Set the modification date/time, if available. Fake the creation date/time as it's not
        //	available from the File class
        long modDate = Files.getLastModifiedTime( m_path).toMillis();
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
     * @exception IOException I/O error
     */
    public JavaNIONetworkFile(String name, String netPath)
            throws IOException {
        super(name);

        //  Create the path
        m_path = Paths.get( name);

        //  Check if the file exists
        if ( Files.exists( m_path) == false) {

            // Create the file
            Files.createFile( m_path);
        }

        //  Set the file size
        setFileSize(Files.size( m_path));
        m_eof = false;

        //	Set the modification date/time, if available. Fake the creation date/time as it's not
        //	available from the File class
        long modDate = Files.getLastModifiedTime( m_path).toMillis();
        setModifyDate(modDate);
        setCreationDate(modDate);

        //	Set the file id
        setFileId(netPath.hashCode());
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
                Files.setLastModifiedTime(m_path, FileTime.fromMillis(curTime));
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
                return m_io.position();
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
            m_io.force( false);
    }

    /**
     * Determine if the end of file has been reached.
     *
     * @return boolean
     * @exception IOException I/O error
     */
    public boolean isEndOfFile() throws IOException {

        //  Check if we reached end of file
        if (m_io != null && m_io.position() == m_io.size())
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

        synchronized (m_path) {

            //	Check if the file is open
            if (m_io == null) {

                //  Open the file
                Set<StandardOpenOption> openOptions = null;
                if ( getGrantedAccess() == Access.READ_WRITE)
                    openOptions = EnumSet.of( StandardOpenOption.READ, StandardOpenOption.WRITE);
                else
                    openOptions = EnumSet.of( StandardOpenOption.READ);
                m_io = FileChannel.open( m_path, openOptions);

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
        ByteBuffer bytBuf = ByteBuffer.wrap( buf, pos, len);
        int rdlen = m_io.read( bytBuf);

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
                    m_io.position( pos);
                break;

            //  From current position
            case SeekType.CurrentPos:
                m_io.position( m_io.position() + pos);
                break;

            //  From end of file
            case SeekType.EndOfFile: {
                long newPos = m_io.size() + pos;
                m_io.position(newPos);
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
            m_io.force( false);

        //	Check if the file length is being truncated or extended
        boolean extendFile = siz > getFileSize() ? true : false;

        //  Set the file length
        try {

            // Set the file to the required length
            m_io.truncate(siz);

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
        ByteBuffer bytBuf = ByteBuffer.wrap( buf, pos, len);

        while ( bytBuf.hasRemaining())
            m_io.write(bytBuf);

        //	Update the write count for the file
        incrementWriteCount();

        // Update the new file length
        setFileSize( m_io.size());
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
        long fileLen = m_io.size();

        if (offset > fileLen) {

            //	Extend the file
            m_io.truncate(offset + len);
        }

        //	Check for a zero length write
        if (len == 0)
            return;

        //	Seek to the write position
        m_io.position(offset);

        //  Write to the file
        ByteBuffer bytBuf = ByteBuffer.wrap( buf, pos, len);

        while( bytBuf.hasRemaining())
            m_io.write(bytBuf);

        //	Update the write count for the file
        incrementWriteCount();

        // Update the current file size if we have written passed the current end of file
        long newLen = offset + len;

        if ( newLen > getFileSize())
            setFileSize( newLen);
    }
}
