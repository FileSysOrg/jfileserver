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

package org.filesys.server.filesys;

import org.filesys.server.SrvSession;
import org.filesys.server.core.DeviceContext;
import org.filesys.server.core.DeviceInterface;

/**
 * The disk interface is implemented by classes that provide an interface for a disk type shared
 * device.
 *
 * @author gkspencer
 */
public interface DiskInterface extends DeviceInterface {

    /**
     * Close the file.
     *
     * @param sess  Server session
     * @param tree  Tree connection.
     * @param param Network file context.
     * @throws java.io.IOException If an error occurs.
     */
    public void closeFile(SrvSession sess, TreeConnection tree, NetworkFile param)
            throws java.io.IOException;

    /**
     * Create a new directory on this file system.
     *
     * @param sess   Server session
     * @param tree   Tree connection.
     * @param params Directory create parameters
     * @throws java.io.IOException If an error occurs.
     */
    public void createDirectory(SrvSession sess, TreeConnection tree, FileOpenParams params)
            throws java.io.IOException;

    /**
     * Create a new file on the file system.
     *
     * @param sess   Server session
     * @param tree   Tree connection
     * @param params File create parameters
     * @return NetworkFile
     * @throws java.io.IOException If an error occurs.
     */
    public NetworkFile createFile(SrvSession sess, TreeConnection tree, FileOpenParams params)
            throws java.io.IOException;

    /**
     * Delete the directory from the filesystem.
     *
     * @param sess Server session
     * @param tree Tree connection
     * @param dir  Directory name.
     * @throws java.io.IOException If an error occurs.
     */
    public void deleteDirectory(SrvSession sess, TreeConnection tree, String dir)
            throws java.io.IOException;

    /**
     * Delete the specified file.
     *
     * @param sess Server session
     * @param tree Tree connection
     * @param name File name
     * @throws java.io.IOException If an error occurs.
     */
    public void deleteFile(SrvSession sess, TreeConnection tree, String name)
            throws java.io.IOException;

    /**
     * Check if the specified file exists, and whether it is a file or directory.
     *
     * @param sess Server session
     * @param tree Tree connection
     * @param name java.lang.String
     * @return FileStatus
     * @see FileStatus
     */
    FileStatus fileExists(SrvSession sess, TreeConnection tree, String name);

    /**
     * Flush any buffered output for the specified file.
     *
     * @param sess Server session
     * @param tree Tree connection
     * @param file Network file context.
     * @throws java.io.IOException If an error occurs.
     */
    public void flushFile(SrvSession sess, TreeConnection tree, NetworkFile file)
            throws java.io.IOException;

    /**
     * Get the file information for the specified file.
     *
     * @param sess Server session
     * @param tree Tree connection
     * @param name File name/path that information is required for.
     * @return File information if valid, else null
     * @throws java.io.IOException If an error occurs.
     */
    public FileInfo getFileInformation(SrvSession sess, TreeConnection tree, String name)
            throws java.io.IOException;

    /**
     * Determine if the disk device is read-only.
     *
     * @param sess Server session
     * @param ctx  Device context
     * @return boolean
     * @throws java.io.IOException If an error occurs.
     */
    boolean isReadOnly(SrvSession sess, DeviceContext ctx)
            throws java.io.IOException;

    /**
     * Open a file on the file system.
     *
     * @param sess   Server session
     * @param tree   Tree connection
     * @param params File open parameters
     * @return NetworkFile
     * @throws java.io.IOException If an error occurs.
     */
    public NetworkFile openFile(SrvSession sess, TreeConnection tree, FileOpenParams params)
            throws java.io.IOException;

    /**
     * Read a block of data from the specified file.
     *
     * @param sess    Session details
     * @param tree    Tree connection
     * @param file    Network file
     * @param buf     Buffer to return data to
     * @param bufPos  Starting position in the return buffer
     * @param siz     Maximum size of data to return
     * @param filePos File offset to read data
     * @return Number of bytes read
     * @throws java.io.IOException If an error occurs.
     */
    public int readFile(SrvSession sess, TreeConnection tree, NetworkFile file, byte[] buf, int bufPos, int siz, long filePos)
            throws java.io.IOException;

    /**
     * Rename the specified file.
     *
     * @param sess    Server session
     * @param tree    Tree connection
     * @param oldName java.lang.String
     * @param newName java.lang.String
     * @throws java.io.IOException If an error occurs.
     */
    public void renameFile(SrvSession sess, TreeConnection tree, String oldName, String newName)
            throws java.io.IOException;

    /**
     * Seek to the specified file position.
     *
     * @param sess Server session
     * @param tree Tree connection
     * @param file Network file.
     * @param pos  Position to seek to.
     * @param typ  Seek type.
     * @return New file position, relative to the start of file.
     * @throws java.io.IOException If an error occurs.
     */
    long seekFile(SrvSession sess, TreeConnection tree, NetworkFile file, long pos, int typ)
            throws java.io.IOException;

    /**
     * Set the file information for the specified file.
     *
     * @param sess Server session
     * @param tree Tree connection
     * @param name java.lang.String
     * @param info FileInfo
     * @throws java.io.IOException If an error occurs.
     */
    public void setFileInformation(SrvSession sess, TreeConnection tree, String name, FileInfo info)
            throws java.io.IOException;

    /**
     * Start a new search on the filesystem using the specified searchPath that may contain
     * wildcards.
     *
     * @param sess       Server session
     * @param tree       Tree connection
     * @param searchPath File(s) to search for, may include wildcards.
     * @param attrib     Attributes of the file(s) to search for, see class SMBFileAttribute.
     * @return SearchContext
     * @throws java.io.FileNotFoundException If the search could not be started.
     */
    public SearchContext startSearch(SrvSession sess, TreeConnection tree, String searchPath, int attrib)
            throws java.io.FileNotFoundException;

    /**
     * Truncate a file to the specified size
     *
     * @param sess Server session
     * @param tree Tree connection
     * @param file Network file details
     * @param siz  New file length
     * @throws java.io.IOException If an error occurs.
     */
    public void truncateFile(SrvSession sess, TreeConnection tree, NetworkFile file, long siz)
            throws java.io.IOException;

    /**
     * Write a block of data to the file.
     *
     * @param sess    Server session
     * @param tree    Tree connection
     * @param file    Network file details
     * @param buf     byte[]  	Data to be written
     * @param bufoff  Offset within the buffer that the data starts
     * @param siz     int      	Data length
     * @param fileoff Position within the file that the data is to be written.
     * @return Number of bytes actually written
     * @throws java.io.IOException If an error occurs.
     */
    public int writeFile(SrvSession sess, TreeConnection tree, NetworkFile file, byte[] buf, int bufoff, int siz,
                         long fileoff)
            throws java.io.IOException;
}
