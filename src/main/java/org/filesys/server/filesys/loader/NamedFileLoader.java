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

import java.io.IOException;

import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.FileOpenParams;
import org.filesys.server.filesys.FileStatus;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.cache.FileState;


/**
 * Named File Loader Interface
 *
 * <p>The NamedFileLoader adds methods that are required to keep track of directory trees and renaming of
 * files/directories by a FileLoader.
 *
 * @author gkspencer
 */
public interface NamedFileLoader {

    /**
     * Check if the file data or folder entry exists for a file/folder
     *
     * @param path String
     * @return FileStatus
     */
    public FileStatus fileExists(String path);

    /**
     * Create a directory
     *
     * @param params FileOpenParams
     * @param fid int
     * @param parentId Parent directory file id
     * @throws IOException Failed to create the directory
     */
    public void createDirectory(FileOpenParams params, int fid, int parentId)
            throws IOException;

    /**
     * Delete a directory
     *
     * @param dir String
     * @param fid int
     * @throws IOException Failed to delete the directory
     */
    public void deleteDirectory(String dir, int fid)
            throws IOException;

    /**
     * Rename a file or directory
     *
     * @param curName String
     * @param fid     int
     * @param newName String
     * @param fstate  FileState
     * @param isdir   boolean
     * @param netFile NetworkFile for handle based rename, or null for path based rename
     * @throws IOException Failed to rename the file or directory
     */
    public void renameFileDirectory(String curName, int fid, String newName, FileState fstate, boolean isdir, NetworkFile netFile)
            throws IOException;

    /**
     * Change file attributes/settings
     *
     * @param path  String
     * @param fid   int
     * @param finfo FileInfo
     * @throws IOException Failed to set the file attributes
     */
    public void setFileInformation(String path, int fid, FileInfo finfo)
            throws IOException;
}
