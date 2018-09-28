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
    public static final int FeatureNTFS             = 0x0001;    //	NTFS streams
    public static final int FeatureRetention        = 0x0002;    //	data retention
    public static final int FeatureQueue            = 0x0004;    //	background load/save queues
    public static final int FeatureData             = 0x0008;    //	load/save file data to database fields
    public static final int FeatureJarData          = 0x0010;    //  load/save multiple file data to Jar files
    public static final int FeatureObjectId         = 0x0020;    //	keep file id to object id mapping details
    public static final int FeatureSymLinks         = 0x0040;    //  symbolic links
    public static final int FeatureSecDescriptor    = 0x0080;    // 	security descriptors

    //	File information levels, for the getFileInformation() method
    public static final int FileNameOnly    = 0;    //	file name only
    public static final int FileIds         = 1;    //	name, directory id and file id
    public static final int FileAll         = 2;    //	all available information

    //	File stream information levels, for getStreamInformation() and getStreamsList() methods
    public static final int StreamNameOnly  = 0;    //	stream name only
    public static final int StreamIds       = 1;    //	stream name, file id and stream id
    public static final int StreamAll       = 2;    //	all available information

    /**
     * Return the database interface name
     *
     * @return String
     */
    public String getDBInterfaceName();

    /**
     * Determine if the database interface supports the specified feature
     *
     * @param feature int
     * @return boolean
     */
    public boolean supportsFeature(int feature);

    /**
     * Request the specified database features be enabled
     *
     * @param featureMask int
     * @exception DBException Database error
     */
    public void requestFeatures(int featureMask)
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
     * @param infoLevel int
     * @return DBFileInfo
     * @exception DBException Database error
     */
    public DBFileInfo getFileInformation(int dirId, int fid, int infoLevel)
            throws DBException;

    /**
     * Get information for a file stream
     *
     * @param fid       int
     * @param stid      int
     * @param infoLevel int
     * @return StreamInfo
     * @exception DBException Database error
     */
    public StreamInfo getStreamInformation(int fid, int stid, int infoLevel)
            throws DBException;

    /**
     * Return the list of streams for the specified file
     *
     * @param fid       int
     * @param infoLevel int
     * @return StreamInfoList
     * @exception DBException Database error
     */
    public StreamInfoList getStreamsList(int fid, int infoLevel)
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
     * @param infoLevel  int
     * @param maxRecords int
     * @return DBSearchContext
     * @exception DBException Database error
     */
    public DBSearchContext startSearch(int dirid, String searchPath, int attrib, int infoLevel, int maxRecords)
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
