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
import java.util.EnumSet;

import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.filesys.*;
import org.filesys.smb.server.ntfs.StreamInfo;
import org.filesys.smb.server.ntfs.StreamInfoList;
import org.springframework.extensions.config.ConfigElement;


/**
 * Database Interface
 *
 * <p>Used by the DBDiskDriver virtual filesystem to provide the interface to the database allowing different
 * databases to be used to store the file system strucuture, NTFS streams, file data and retention information.
 *
 * @author gkspencer
 */
public interface DBInterface {

    //	Constants
    //
    //	Database interface supported/requested features
    public enum Feature {
        NTFS,           // NTFS streams
        Retention,      // data retention
        Queue,          // background load/save queues
        Data,           // load/save file data to database fields
        JarData,        // load/save multiple file data to Jar files
        ObjectId,       // keep file id to object id mapping details
        SymLinks,       // symbolic links
        SecDescriptor   // security descriptors
    }

    //	File information levels, for the getFileInformation() method
    public enum FileInfoLevel {
        NameOnly,
        Ids,
        All
    }

    //	File stream information levels, for getStreamInformation() and getStreamsList() methods
    public enum StreamInfoLevel {
        NameOnly,
        Ids,
        All
    }

    /**
     * Return the database interface name
     *
     * @return String
     */
    public String getDBInterfaceName();

    /**
     * Determine if the database interface supports the specified feature
     *
     * @param feature Feature
     * @return boolean
     */
    public boolean supportsFeature(Feature feature);

    /**
     * Request the specified database features be enabled
     *
     * @param featureMask EnumSet&lt;Feature&gt;
     * @exception DBException Database error
     */
    public void requestFeatures(EnumSet<Feature> featureMask)
            throws DBException;

    /**
     * Initialize the database interface
     *
     * @param context DBDeviceContext
     * @param params  ConfigElement
     * @throws InvalidConfigurationException Failed to initialize the database interface
     */
    public void initializeDatabase(DBDeviceContext context, ConfigElement params)
            throws InvalidConfigurationException;

    /**
     * Shutdown the database interface
     *
     * @param context DBDeviceContext
     */
    public void shutdownDatabase(DBDeviceContext context);

    /**
     * Check if the database is online
     *
     * @return boolean
     */
    public boolean isOnline();

    /**
     * Check if a file/folder exists
     *
     * @param dirId int
     * @param fname String
     * @return FileStatus
     * @exception DBException Database error
     */
    public FileStatus fileExists(int dirId, String fname)
            throws DBException;

    /**
     * Create a file record for a new file or folder
     *
     * @param fname  String
     * @param dirId  int
     * @param params FileOpenParams
     * @param retain boolean
     * @return int
     * @exception DBException Database error
     * @throws FileExistsException File record already exists
     */
    public int createFileRecord(String fname, int dirId, FileOpenParams params, boolean retain)
            throws DBException, FileExistsException;

    /**
     * Create a stream record for a new file stream
     *
     * @param sname String
     * @param fid   int
     * @return int
     * @exception DBException Database error
     */
    public int createStreamRecord(String sname, int fid)
            throws DBException;

    /**
     * Delete a file or folder record
     *
     * @param dirId    int
     * @param fid      int
     * @param markOnly boolean
     * @exception DBException Database error
     * @exception IOException I/O error
     * @exception DirectoryNotEmptyException Directory is not empty
     */
    public void deleteFileRecord(int dirId, int fid, boolean markOnly)
            throws DBException, IOException, DirectoryNotEmptyException;

    /**
     * Delete a file stream record
     *
     * @param fid      int
     * @param stid     int
     * @param markOnly boolean
     * @exception DBException Database error
     */
    public void deleteStreamRecord(int fid, int stid, boolean markOnly)
            throws DBException;

    /**
     * Set file information for a file or folder
     *
     * @param dirId int
     * @param fid   int
     * @param finfo FileInfo
     * @exception DBException Database error
     */
    public void setFileInformation(int dirId, int fid, FileInfo finfo)
            throws DBException;

    /**
     * Set information for a file stream
     *
     * @param dirId int
     * @param fid   int
     * @param stid  int
     * @param sinfo StreamInfo
     * @exception DBException Database error
     */
    public void setStreamInformation(int dirId, int fid, int stid, StreamInfo sinfo)
            throws DBException;

    /**
     * Get the id for a file/folder, or -1 if the file/folder does not exist.
     *
     * @param dirid    int
     * @param fname    String
     * @param dirOnly  boolean
     * @param caseLess boolean
     * @return int
     * @exception DBException Database error
     */
    public int getFileId(int dirid, String fname, boolean dirOnly, boolean caseLess)
            throws DBException;

    /**
     * Get information for a file or folder
     *
     * @param dirId     int
     * @param fid       int
     * @param infoLevel DBInterface.FileInfoLevel
     * @return DBFileInfo
     * @exception DBException Database error
     */
    public DBFileInfo getFileInformation(int dirId, int fid, DBInterface.FileInfoLevel infoLevel)
            throws DBException;

    /**
     * Get information for a file stream
     *
     * @param fid       int
     * @param stid      int
     * @param infoLevel DBInterface.StreamInfoLevel
     * @return StreamInfo
     * @exception DBException Database error
     */
    public StreamInfo getStreamInformation(int fid, int stid, DBInterface.StreamInfoLevel infoLevel)
            throws DBException;

    /**
     * Return the list of streams for the specified file
     *
     * @param fid       int
     * @param infoLevel DBInterface.StreamInfoLevel
     * @return StreamInfoList
     * @exception DBException Database error
     */
    public StreamInfoList getStreamsList(int fid, DBInterface.StreamInfoLevel infoLevel)
            throws DBException;

    /**
     * Rename a file or folder, may also change the parent directory.
     *
     * @param dirId   int
     * @param fid     int
     * @param newName String
     * @param newDir  int
     * @exception DBException Database error
     * @exception FileNotFoundException File not found
     */
    public void renameFileRecord(int dirId, int fid, String newName, int newDir)
            throws DBException, FileNotFoundException;

    /**
     * Rename a file stream
     *
     * @param dirId   int
     * @param fid     int
     * @param stid    int
     * @param newName String
     * @exception DBException Database error
     */
    public void renameStreamRecord(int dirId, int fid, int stid, String newName)
            throws DBException;

    /**
     * Return the retention period expiry date/time for the specified file, or zero if the file/folder
     * is not under retention.
     *
     * @param dirId int
     * @param fid   int
     * @return RetentionDetails
     * @exception DBException Database error
     */
    public RetentionDetails getFileRetentionDetails(int dirId, int fid)
            throws DBException;

    /**
     * Start a directory search
     *
     * @param dirid      int
     * @param searchPath String
     * @param attrib     int
     * @param infoLevel  DBInterfcae.FileInfoLevel
     * @param maxRecords int
     * @return DBSearchContext
     * @exception DBException Database error
     */
    public DBSearchContext startSearch(int dirid, String searchPath, int attrib, DBInterface.FileInfoLevel infoLevel, int maxRecords)
            throws DBException;

    /**
     * Return the data for a symbolic link
     *
     * @param dirId int
     * @param fid   int
     * @return String
     * @exception DBException Database error
     */
    public String readSymbolicLink(int dirId, int fid)
            throws DBException;

    /**
     * Delete a symbolic link record
     *
     * @param dirId int
     * @param fid   int
     * @exception DBException Database error
     */
    public void deleteSymbolicLinkRecord(int dirId, int fid)
            throws DBException;

    /**
     * Return the used file space, or -1 if not supported.
     *
     * @return long
     */
    public long getUsedFileSpace();
}
