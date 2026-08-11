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
import java.util.ArrayList;
import java.util.EnumSet;

import org.filesys.debug.Debug;
import org.filesys.locking.LockConflictException;
import org.filesys.server.SrvSession;
import org.filesys.server.core.DeviceContext;
import org.filesys.server.core.DeviceContextException;
import org.filesys.server.filesys.*;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateCache;
import org.filesys.server.filesys.cache.NetworkFileStateInterface;
import org.filesys.server.filesys.loader.NamedFileLoader;
import org.filesys.server.filesys.postprocess.PostCloseProcessor;
import org.filesys.server.filesys.quota.QuotaManager;
import org.filesys.server.locking.FileLockingInterface;
import org.filesys.server.locking.LockManager;
import org.filesys.server.locking.OpLockInterface;
import org.filesys.server.locking.OpLockManager;
import org.filesys.smb.SMBStatus;
import org.filesys.smb.SharingMode;
import org.filesys.smb.WinNT;
import org.filesys.smb.nt.SecurityDescriptor;
import org.filesys.smb.server.SMBSrvException;
import org.filesys.smb.server.SMBSrvSession;
import org.filesys.smb.server.ntfs.NTFSStreamsInterface;
import org.filesys.smb.server.ntfs.StreamInfo;
import org.filesys.smb.server.ntfs.StreamInfoList;
import org.filesys.util.DataBuffer;
import org.filesys.util.MemorySize;
import org.filesys.util.WildCard;
import org.springframework.extensions.config.ConfigElement;

/**
 * Database Disk Driver Class
 *
 * @author gkspencer
 */
public class DBDiskDriver implements DiskInterface, DiskSizeInterface, DiskVolumeInterface, NTFSStreamsInterface,
        FileLockingInterface, FileIdInterface, SymbolicLinkInterface, OpLockInterface, SecurityDescriptorInterface,
        PostCloseProcessor {

    // Root directory file id
    public static final int RootDirId   = 0;

    //  Attributes attached to the file state
    public static final String DBStreamList = "DBStreamList";

    //  Default mode values for files/folders, if not specified in the file/folder create parameters
    public static final int DefaultNFSFileMode  = 0644;
    public static final int DefaultNFSDirMode   = 0755;

    //  Maximum file name length
    public static final int MaxFileNameLen = 255;

    //  Maximum timestamp value to allow for file timestamps (01-Jan-2030 00:00:00)
    public static final long MaxTimestampValue = 1896134400000L;

    //  Enable/disable debug output
    private boolean m_debug = false;

    /**
     * Close the specified file
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param file Network file details
     * @throws IOException Error closing the file
     */
    public void closeFile(SrvSession sess, TreeConnection tree, NetworkFile file)
            throws IOException {

        //  Access the database context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the file has been written to, and the post close processor is enabled
        //
        // Note: Only use post close for SMB sessions
        if (dbCtx != null && dbCtx.isPostCloseEnabled() && !file.isReadOnly() && file.getWriteCount() > 0 && sess instanceof SMBSrvSession) {

            // Run the file close via a post close processor after the protocol layer has sent the response to the client
            file.setStatusFlag(NetworkFile.Flags.POST_CLOSE_FILE, true);
            return;
        }

        // Close the file
        doCloseFile( sess, tree, file);
    }

    /**
     * Do the actual file close, it may be run from the post close processor
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param file Network file details
     * @throws IOException Error closing the file
     */
    public void doCloseFile(SrvSession sess, TreeConnection tree, NetworkFile file)
            throws IOException {

        //  Access the database context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        //  Check if the file is an NTFS stream
        if (file.isStream()) {

            //  Close the NTFS stream
            closeStream(sess, tree, file);

            //  Check if the stream is marked for deletion
            if (file.hasDeleteOnClose())
                deleteStream(sess, tree, file.getFullNameStream());
            return;
        }

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("DB closeFile() file=" + file.getFullName());

        //  Close the file
        dbCtx.getFileLoader().closeFile(sess, file);
        file.setClosed(true);

        //  Access the JDBC file
        DBNetworkFile jdbcFile = null;

        if (file instanceof DBNetworkFile) {

            //  Access the JDBC file
            jdbcFile = (DBNetworkFile) file;

            //  Decrement the open file count
            FileState fstate = jdbcFile.getFileState();

            //  Check if the file state is valid, if not then check the main file state cache
            if (fstate == null) {

                //  Check the main file state cache
                fstate = getFileState(file.getFullName(), dbCtx, false);

                //  DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("** Last file close, no file state for " + file.getFullName());
            }
            else if ( !jdbcFile.isDirectory()) {

                // If the file open count is now zero then reset the stored sharing mode
                if (dbCtx.getStateCache().releaseFileAccess(fstate, file.getAccessToken()) == 0) {

                    //  DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("** Last file close, reset shared access for " + file.getFullName() + ", state=" + fstate);
                } else if (Debug.EnableInfo && hasDebug())
                    Debug.println("** File close, file=" + file.getFullName() + ", openCount=" + fstate.getOpenCount());

                // Check if there is an oplock on the file
                if (jdbcFile.hasOpLock() && sess instanceof SMBSrvSession) {

                    // Access the SMB session
                    SMBSrvSession smbSess = (SMBSrvSession) sess;

                    // Release the oplock
                    OpLockInterface flIface = (OpLockInterface) this;
                    OpLockManager oplockMgr = flIface.getOpLockManager(sess, tree);

                    oplockMgr.releaseOpLock(jdbcFile.getOpLock().getPath(), jdbcFile.getOplockOwner());

                    //  DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("Released oplock for closed file, file=" + jdbcFile.getFullName());
                }

                // Clear the access token
                file.setAccessToken(null);
            }

            //  Release any locks on the file owned by this session
            if (jdbcFile.hasLocks()) {

                //  Get the lock manager
                FileLockingInterface flIface = (FileLockingInterface) this;
                LockManager lockMgr = flIface.getLockManager(sess, tree);

                //  DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("Releasing locks for closed file, file=" + jdbcFile.getFullName() + ", locks=" + jdbcFile.numberOfLocks());

                //  Release all locks on the file owned by this session
                lockMgr.releaseLocksForFile(sess, tree, file);
            }

            //  Check if we have a valid file state
            if (fstate != null) {

                //  Update the cached file size
                DBFileInfo finfo = (DBFileInfo) fstate.findAttribute(FileState.FileInformation);
                if (finfo != null && file.getWriteCount() > 0) {

                    //  Update the file size
                    finfo.setSize(jdbcFile.getFileSize());

                    //  Update the modified date/time
                    finfo.setModifyDateTime(jdbcFile.getModifyDate());

                    //  DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("  File size=" + jdbcFile.getFileSize() + ", modifyDate=" + jdbcFile.getModifyDate());
                }

                //  DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("  Open count=" + jdbcFile.getFileState().getOpenCount());
            }

            //  Check if the file/directory is marked for delete
            if (file.hasDeleteOnClose()) {

                //  Check for a file or directory
                if (file.isDirectory())
                    deleteDirectory(sess, tree, file.getFullName());
                else
                    deleteFile(sess, tree, file.getFullName());

                //  DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("  Marked for delete");
            }
        } else if (Debug.EnableError)
            Debug.println("closeFile() Not DBNetworkFile file=" + file);

        //  Check if the file was opened for write access, if so then update the file size and modify date/time
        if (file.getGrantedAccess() != NetworkFile.Access.READ_ONLY && !file.isDirectory() &&
                file.getWriteCount() > 0 && !file.hasDeleteOnClose()) {

            //  DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("  Update file size=" + file.getFileSize());

            //  Get the current date/time
            long modifiedTime = 0L;
            if (file.hasModifyDate())
                modifiedTime = file.getModifyDate();
            else
                modifiedTime = System.currentTimeMillis();

            //  Check if the modified time is earlier than the file creation date/time
            if (file.hasCreationDate() && modifiedTime < file.getCreationDate()) {

                //  Use the creation date/time for the modified date/time
                modifiedTime = file.getCreationDate();

                //  DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("Close file using creation date/time for modified date/time");
            }

            //  Update the file details
            try {

                //  Update the file details
                FileInfo finfo = new FileInfo();

                finfo.setFileSize(file.getFileSize());
                finfo.setModifyDateTime(modifiedTime);

                finfo.setFileInformationFlags(FileInfo.SetFileSize + FileInfo.SetModifyDate);

                //  Call the database interface
                dbCtx.getDBInterface().setFileInformation(file.getDirectoryId(), file.getFileId(), finfo);
            }
            catch (DBException ex) {
                throw new IOException( ex.getMessage(), ex.getCause());
            }
        }
    }

    /**
     * Create a new directory
     *
     * @param sess   Session details
     * @param tree   Tree connection
     * @param params Directory create parameters
     * @throws IOException Error creating the directory
     */
    public void createDirectory(SrvSession sess, TreeConnection tree, FileOpenParams params)
            throws IOException {

        //  Access the database context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the database is online
        if (dbCtx.getDBInterface().isOnline() == false)
            throw new DiskOfflineException("Database is offline");

        // Check if the file loader is online
        if (dbCtx.getFileLoader().isOnline() == false)
            throw new DiskOfflineException("File loader is offline");

        //  Get, or create, a file state for the new path. Initially this will indicate that the directory
        //  does not exist.
        FileState fstate = getFileState(params.getPath(), dbCtx, false);
        if (fstate != null && fstate.fileExists() == true)
            throw new FileExistsException("Path " + params.getPath() + " exists");

        //  If there is no file state check if the directory exists
        if (fstate == null) {

            //  Create a file state for the new directory
            fstate = getFileState(params.getPath(), dbCtx, true);

            //  Get the file details for the directory
            if (getFileDetails(params.getPath(), dbCtx, fstate) != null)
                throw new FileExistsException("Path " + params.getPath() + " exists");
        }

        //  Find the parent directory id for the new directory
        int dirId = findParentDirectoryId(dbCtx, params.getPath(), true);
        if (dirId == -1)
            throw new IOException("Cannot find parent directory");

        //  Create the new directory entry
        FileAccessToken accessToken = null;
        int fid = -1;

        try {

            //  Get the directory name
            String[] paths = FileName.splitPath(params.getPath());
            String dname = paths[1];

            //  Check if the directory name is too long
            if (dname != null && dname.length() > MaxFileNameLen)
                throw new FileNameException("Directory name too long, " + dname);

            //  If retention is enabled check if the file is a temporary folder
            boolean retain = true;

            if (dbCtx.hasRetentionPeriod()) {

                //  Check if the file is marked delete on close
                if (params.isDeleteOnClose())
                    retain = false;
            }

            //  Set the default NFS file mode, if not set
//            if (params.hasMode() == false)
//                params.setMode(DefaultNFSDirMode);

            //  Make sure the create directory option is enabled
            if (params.hasCreateOption(WinNT.CreateDirectory) == false)
                throw new IOException("Create directory called for non-directory");

            // Check if the file can be opened in the requested mode
            //
            // Note: The file status is set to NotExist at this point, the file record creation may fail
            accessToken = dbCtx.getStateCache().grantFileAccess(params, fstate, FileStatus.NotExist);

            // Synchronize on the file state to avoid concurrent database create of the same file record
            synchronized ( fstate) {

                //  Use the database interface to create the new file record
                fid = dbCtx.getDBInterface().createFileRecord(dname, dirId, params, retain);

                //  Indicate that the path exists
                fstate.setFileStatus(FileStatus.DirectoryExists, FileState.ChangeReason.FolderCreated);

                //  Set the file id for the new directory
                fstate.setFileId(fid);
            }

            //  If retention is enabled get the expiry date/time
            if (dbCtx.hasRetentionPeriod() && retain == true) {
                RetentionDetails retDetails = dbCtx.getDBInterface().getFileRetentionDetails(dirId, fid);
                if (retDetails != null)
                    fstate.setRetentionExpiryDateTime(retDetails.getEndTime());
            }

            //  Check if the file loader handles create directory requests
            if (fid != -1 && dbCtx.getFileLoader() instanceof NamedFileLoader) {

                //  Create the directory in the filesystem/repository
                NamedFileLoader namedLoader = (NamedFileLoader) dbCtx.getFileLoader();
                namedLoader.createDirectory(params, fid, dirId);
            }

            // Release the access token
            if (accessToken != null) {
                dbCtx.getStateCache().releaseFileAccess(fstate, accessToken);
                accessToken = null;
            }

            // Update the parent folder timestamps
            updateParentFolderTimestamps( params.getPath(), dbCtx);
        }
        catch (Exception ex) {
            throw new IOException( "Create directory error", ex);
        }
        finally {

            // Check if the file is not valid but an access token has been allocated
            if (fid == -1 && accessToken != null)
                dbCtx.getStateCache().releaseFileAccess(fstate, accessToken);
        }
    }

    /**
     * Create a new file entry
     *
     * @param sess   SrvSession
     * @param tree   TreeConnection
     * @param params FileOpenParams
     * @return NetworkFile
     */
    public NetworkFile createFile(SrvSession sess, TreeConnection tree, FileOpenParams params)
            throws IOException {

        //  Access the database context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the database is online
        if (dbCtx.getDBInterface().isOnline() == false)
            throw new DiskOfflineException("Database is offline");

        // Check if the file loader is online
        if (dbCtx.getFileLoader().isOnline() == false)
            throw new DiskOfflineException("File loader is offline");

        // Check if this is a stream create
        FileState fstate = getFileState(params.getPath(), dbCtx, true);

        if (params.isStream()) {

            // Make sure the parent file exists
            if (fileExists(sess, tree, params.getPath()) == FileStatus.FileExists) {

                //  Create a new stream associated with the existing file
                return createStream(sess, tree, params, fstate, dbCtx);
            } else {

                // Parent file does not exist
                throw new FileNotFoundException("Parent file does not exist to create stream, " + params.getPath());
            }
        } else if (fstate.fileExists()) {

            // File already exists
            throw new FileExistsException("File exists, " + params.getPath());
        }

        //  Split the path string and find the directory id to attach the file to
        int dirId = findParentDirectoryId(dbCtx, params.getPath(), true);
        if (dirId == -1)
            throw new IOException("Cannot find parent directory");

        //  Check if the allocation size for the new file is greater than the maximum allowed file size
        if (dbCtx.hasMaximumFileSize() && params.getAllocationSize() > dbCtx.getMaximumFileSize())
            throw new DiskFullException("Required allocation greater than maximum file size");

        //  Create a new file
        DBNetworkFile file = null;
        FileAccessToken accessToken = null;
        int fid = -1;

        try {

            //  Get the file name
            String[] paths = FileName.splitPath(params.getPath());
            String fname = paths[1];

            //  Check if the file name is too long
            if (fname != null && fname.length() > MaxFileNameLen)
                throw new FileNameException("File name too long, " + fname);

            //  If retention is enabled check if the file is a temporary file
            boolean retain = true;

            if (dbCtx.hasRetentionPeriod()) {

                //  Check if the file is marked delete on close
                if (params.isDeleteOnClose())
                    retain = false;
            }

            //  Set the default NFS file mode, if not set
//            if (params.hasMode() == false)
//                params.setMode(DefaultNFSFileMode);

            // Check if the current file open allows the required shared access
            if (params.getPath().equals("\\") == false) {

                // Check if the file can be opened in the requested mode
                //
                // Note: The file status is set to NotExist at this point, the file record creation may fail
                accessToken = dbCtx.getStateCache().grantFileAccess(params, fstate, FileStatus.NotExist);
            }

            // Synchronize on the file state to avoid concurrent database create of the same file record
            synchronized ( fstate) {

                //  Create a new file record
                fid = dbCtx.getDBInterface().createFileRecord(fname, dirId, params, retain);

                //  Indicate that the file exists
                fstate.setFileStatus(FileStatus.FileExists, FileState.ChangeReason.FileCreated);

                //  Save the file id
                fstate.setFileId(fid);
            }

            //  If retention is enabled get the expiry date/time
            if (dbCtx.hasRetentionPeriod() && retain == true) {
                RetentionDetails retDetails = dbCtx.getDBInterface().getFileRetentionDetails(dirId, fid);
                if (retDetails != null)
                    fstate.setRetentionExpiryDateTime(retDetails.getEndTime());
            }

            //  Create a network file to hold details of the new file entry
            file = (DBNetworkFile) dbCtx.getFileLoader().openFile(params, fid, 0, dirId, true, false);
            file.setFullName(params.getPath());
            file.setDirectoryId(dirId);
            file.setAttributes(params.getAttributes());
            file.setFileState(dbCtx.getStateCache().getFileStateProxy(fstate));

            file.setAccessToken(accessToken);

            //  Open the file
            file.openFile(true);

            // Update the parent folder timestamps
            updateParentFolderTimestamps( params.getPath(), dbCtx);
        }
        catch (DBException ex) {

            // Remove the file state for the new file
            dbCtx.getStateCache().removeFileState(fstate.getPath());

            // Close the file, if valid
            if ( file != null) {
                try {
                    file.closeFile();
                    file = null;
                }
                catch ( Exception ex2) {
                }
            }

            throw new IOException( "Failed to create file " + params.getPath(), ex);
        }
        finally {

            // Check if the file is not valid but an access token has been allocated
            if (file == null && accessToken != null)
                dbCtx.getStateCache().releaseFileAccess(fstate, accessToken);
        }

        //  Return the new file details
        if (file == null)
            throw new IOException("Failed to create file " + params.getPath());

        //  Return the network file
        return file;
    }

    /**
     * Delete a directory
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param dir  Path of directory to delete
     * @throws IOException Error deleting the directory
     */
    public void deleteDirectory(SrvSession sess, TreeConnection tree, String dir)
            throws IOException {

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("DB deleteDirectory() dir=" + dir);

        //  Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the database is online
        if (dbCtx.getDBInterface().isOnline() == false)
            throw new DiskOfflineException("Database is offline");

        // Check if the file loader is online
        if (dbCtx.getFileLoader().isOnline() == false)
            throw new DiskOfflineException("File loader is offline");

        //  Get the file state for the path
        FileState fstate = getFileState(dir, dbCtx, false);
        if (fstate != null && fstate.fileExists() == false)
            throw new FileNotFoundException("Path does not exist, " + dir);

        //  Create a file state if it does not exist
        if (fstate == null)
            fstate = getFileState(dir, dbCtx, true);

        //  Get the directory details
        DBFileInfo dinfo = getFileDetails(dir, dbCtx, fstate);
        if (dinfo == null)
            throw new FileNotFoundException(dir);

        //  Check if the directory contains any files
        try {

            //  Check if the file loader handles delete directory requests. Called first as the loader may throw an exception
            //  to stop the directory being deleted.
            if (dbCtx.isTrashCanEnabled() == false && dbCtx.getFileLoader() instanceof NamedFileLoader) {

                //  Delete the directory in the filesystem/repository
                NamedFileLoader namedLoader = (NamedFileLoader) dbCtx.getFileLoader();
                namedLoader.deleteDirectory(dir, dinfo.getFileId());
            }

            //  Delete the directory file record, or mark as deleted if the trashcan is enabled
            dbCtx.getDBInterface().deleteFileRecord(dinfo.getDirectoryId(), dinfo.getFileId(), dbCtx.isTrashCanEnabled());

            //  Indicate that the path does not exist
            fstate.setFileStatus(FileStatus.NotExist, FileState.ChangeReason.FolderDeleted);
            fstate.setFileId(-1);
            fstate.removeAttribute(FileState.FileInformation);

            // Update the parent folder timestamps
            updateParentFolderTimestamps( dir, dbCtx);
        }
        catch (DBException ex) {
            throw new IOException( "Failed to delete directory " + dir, ex.getCause());
        }
    }

    /**
     * Delete a file
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param name Name of file to delete
     * @throws IOException Error deleting the file
     */
    public void deleteFile(SrvSession sess, TreeConnection tree, String name)
            throws IOException {

        //  Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the database is online
        if (dbCtx.getDBInterface().isOnline() == false)
            throw new DiskOfflineException("Database is offline");

        // Check if the file loader is online
        if (dbCtx.getFileLoader().isOnline() == false)
            throw new DiskOfflineException("File loader is offline");

        //  Check if the file name is a stream
        if (FileName.containsStreamName(name)) {

            //  Delete a stream within a file
            deleteStream(sess, tree, name);
            return;
        }

        //  Get the file state for the path
        FileState fstate = getFileState(name, dbCtx, false);
        if (fstate != null && fstate.fileExists() == false)
            throw new FileNotFoundException("File does not exist, " + name);

        //  Create a file state for the file, if not already valid
        if (fstate == null)
            fstate = getFileState(name, dbCtx, true);

        DBFileInfo dbInfo = null;

        try {

            //  Check if the file is within an active retention period
            getRetentionDetailsForState(dbCtx, fstate);

            if (fstate.hasActiveRetentionPeriod())
                throw new AccessDeniedException("File retention active");

            //  Get the file details
            dbInfo = getFileDetails(name, dbCtx, fstate);

            if (dbInfo == null)
                throw new FileNotFoundException(name);

            //  DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("DBDiskDriver deleteFile() name=" + name + ", state=" + fstate);

            //  Delete the file in the filesystem/repository, the loader may prevent the file delete by throwing
            //  an exception
            if (dbCtx.isTrashCanEnabled() == false)
                dbCtx.getFileLoader().deleteFile(name, fstate.getFileId(), 0);

            //  If the file is a symbolic link delete the symbolic link record
            if (dbInfo.isFileType() == FileType.SymbolicLink)
                dbCtx.getDBInterface().deleteSymbolicLinkRecord(dbInfo.getDirectoryId(), fstate.getFileId());

            // Check if the file has any NTFS streams
            StreamInfoList streamList = getStreamList(sess, tree, name);

            if (streamList != null && streamList.numberOfStreams() > 0) {

                // Make a copy of the streams list as streams are removed from the original list as we delete them
                StreamInfoList sList = new StreamInfoList(streamList);

                // Delete the streams
                StringBuilder sPath = new StringBuilder(256);
                sPath.append(name);
                int delCnt = 0;

                for (int idx = 0; idx < sList.numberOfStreams(); idx++) {

                    // Get the current stream details
                    StreamInfo sInfo = sList.getStreamAt(idx);
                    if (sInfo.getName().equals(FileName.MainDataStreamName) == false) {

                        // Build the full path to the stream
                        sPath.setLength(name.length());
                        sPath.append(sInfo.getName());

                        // Delete the stream
                        deleteStream(sess, tree, sPath.toString());
                        delCnt++;
                    }
                }

                // DEBUG
                if (Debug.EnableInfo && hasDebug() && delCnt > 0)
                    Debug.println("DBDiskDriver deleted " + delCnt + " streams for name=" + name);
            }

            //  Delete the file record
            dbCtx.getDBInterface().deleteFileRecord(dbInfo.getDirectoryId(), fstate.getFileId(), dbCtx.isTrashCanEnabled());

            //  Indicate that the path does not exist
            fstate.setFileStatus(FileStatus.NotExist, FileState.ChangeReason.FileDeleted);
            fstate.setFileId(-1);
            fstate.removeAttribute(FileState.FileInformation);

            //  Check if there is a quota manager, if so then release the file space
            if (dbCtx.hasQuotaManager()) {

                //  Release the file space back to the filesystem free space
                dbCtx.getQuotaManager().releaseSpace(sess, tree, fstate.getFileId(), null, dbInfo.getSize());
            }

            // Update the parent folder timestamps
            updateParentFolderTimestamps( name, dbCtx);
        }
        catch (DBException ex) {
            throw new IOException("Failed to delete file " + name, ex);
        }
    }

    /**
     * Check if the specified file exists, and it is a file.
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param name File name
     * @return FileStatus
     */
    public FileStatus fileExists(SrvSession sess, TreeConnection tree, String name) {

        //  Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        //  Check if the path contains an NTFS stream name
        FileStatus fileSts = FileStatus.NotExist;
        FileState fstate = null;

        if (FileName.containsStreamName(name)) {

            // Get the file information for the stream
            FileInfo fInfo = null;

            try {
                fInfo = getFileInformation(sess, tree, name);
            }
            catch (IOException ex) {
            }

            // Check if the file information was retrieved for the stream
            if (fInfo != null)
                fileSts = FileStatus.FileExists;

            //  Debug
            if (Debug.EnableInfo && hasDebug())
                Debug.println("DB fileExists() nameWithStream=" + name + ", fileSts=" + fileSts.name());

        } else {

            //  Get, or create, the file state for the path
            fstate = getFileState(name, dbCtx, true);

            //  Check if the file exists status has been cached
            fileSts = fstate.getFileStatus();

            if (fstate.getFileStatus() == FileStatus.Unknown) {

                //  Get the file details
                DBFileInfo dbInfo = getFileDetails(name, dbCtx, fstate);

                if (dbInfo != null) {
                    if (dbInfo.isDirectory() == true)
                        fileSts = FileStatus.DirectoryExists;
                    else
                        fileSts = FileStatus.FileExists;

                    // Save the file id
                    if (dbInfo.getFileId() != -1)
                        fstate.setFileId(dbInfo.getFileId());
                } else {

                    //  Indicate that the file does not exist
                    fstate.setFileStatus(FileStatus.NotExist);
                    fileSts = FileStatus.NotExist;
                }

                //  Debug
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("DB fileExists() name=" + name + ", fileSts=" + fileSts.name());
            } else {

                //  DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("@@ Cache hit - fileExists() name=" + name + ", fileSts=" + fileSts.name());
            }
        }

        //  Return the file exists status
        return fileSts;
    }

    /**
     * Flush buffered data for the specified file
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param file Network file
     * @throws IOException Error flushing the file
     */
    public void flushFile(SrvSession sess, TreeConnection tree, NetworkFile file)
            throws IOException {

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("DB flushFile()");

        //  Flush any buffered data
        file.flushFile();
    }

    /**
     * Return file information about the specified file
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param name File name
     * @return SMBFileInfo
     * @throws IOException Error getting the file information
     */
    public FileInfo getFileInformation(SrvSession sess, TreeConnection tree, String name)
            throws IOException {

        //  Check for the null file name
        if (name == null)
            return null;

        //  Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the database is online
        if (dbCtx.getDBInterface().isOnline() == false)
            throw new DiskOfflineException("Database is offline");

        // Check if the file loader is online
        if (dbCtx.getFileLoader().isOnline() == false)
            throw new DiskOfflineException("File loader is offline");

        //  Check if the path is a file stream
        FileState fstate = null;
        FileInfo finfo = null;

        if (FileName.containsStreamName(name)) {

            //  Check if there is an active file state for the stream
            fstate = getFileState(name, dbCtx, true);

            if (fstate != null) {

                // Check if the file stream exists
                if (fstate.getFileStatus() != FileStatus.NotExist) {

                    //  Check if the file information is available
                    finfo = (FileInfo) fstate.findAttribute(FileState.FileInformation);
                } else
                    return null;
            }

            //  If the cached file information is not available then create it
            if (finfo == null) {

                //  Get, or create, the file state for main file path
                String filePath = FileName.getParentPathForStream(name);
                FileState parent = getFileState(filePath, dbCtx, false);

                // Get the file information for the parent file to load the cache
                if (parent == null) {

                    // Get the file information for the parent file
                    getFileInformation(sess, tree, filePath);

                    // File state should exist for the parent now
                    parent = getFileState(filePath, dbCtx, false);
                }

                //  Check if the top level file exists
                if (parent != null && parent.fileExists() == true) {

                    //  Get the top level file details
                    DBFileInfo dbInfo = getFileDetails(filePath, dbCtx, parent);

                    if (dbInfo != null) {

                        //  Get the list of available streams
                        StreamInfoList streams = getStreamList(sess, tree, filePath);

                        if (streams != null && streams.numberOfStreams() > 0) {

                            // Parse the path into directory, file and stream names
                            String[] paths = FileName.splitPathStream(name);

                            //  Get the details for the stream, if the information is valid copy it to a file information
                            //  object
                            StreamInfo sInfo = streams.findStream(paths[2]);

                            if (sInfo != null) {

                                //  Create a file information object, copy the stream details to it
                                finfo = new DBFileInfo(paths[1], name, dbInfo.getFileId(), dbInfo.getDirectoryId());

                                finfo.setFileId(sInfo.getFileId());
                                finfo.setFileSize(sInfo.getSize());

                                finfo.setCreationDateTime(sInfo.getCreationDateTime());
                                finfo.setAccessDateTime(sInfo.getAccessDateTime());
                                finfo.setModifyDateTime(sInfo.getModifyDateTime());

                                //  Attach to the file state
                                fstate.addAttribute(FileState.FileInformation, finfo);

                                //  DEBUG
                                if (Debug.EnableInfo && hasDebug())
                                    Debug.println("getFileInformation() stream=" + name + ", info=" + finfo);
                            }
                        }
                    }
                }
            }
        } else {

            //  Get, or create, the file state for the path
            fstate = getFileState(name, dbCtx, true);

            //  Get the file details for the path
            DBFileInfo dbInfo = getFileDetails(name, dbCtx, fstate);

            //  Set the full file/path name
            if (dbInfo != null)
                dbInfo.setFullName(name);
            finfo = dbInfo;
        }

        //  DEBUG
        if (Debug.EnableInfo && hasDebug() && finfo != null)
            Debug.println("getFileInformation info=" + finfo.toString());

        //  Return the file information
        return finfo;
    }

    /**
     * Determine if the disk device is read-only.
     *
     * @param sess Session details
     * @param ctx  Device context
     * @return true if the device is read-only, else false
     * @throws IOException If an error occurs.
     */
    public boolean isReadOnly(SrvSession sess, DeviceContext ctx)
            throws IOException {
        return false;
    }

    /**
     * Open a file
     *
     * @param sess   Session details
     * @param tree   Tree connection
     * @param params File open parameters
     * @return NetworkFile
     * @throws IOException If an error occurs.
     */
    public NetworkFile openFile(SrvSession sess, TreeConnection tree, FileOpenParams params)
            throws IOException {

        //  Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the database is online
        if (dbCtx.getDBInterface().isOnline() == false)
            throw new DiskOfflineException("Database is offline");

        // Check if the file loader is online
        if (dbCtx.getFileLoader().isOnline() == false)
            throw new DiskOfflineException("File loader is offline");

        //  Get, or create, the file state
        FileState fstate = getFileState(params.getPath(), dbCtx, true);

        // Check if the file has a data update in progress, the file will be offline until the
        // data update completes
        if (fstate != null && fstate.hasDataUpdateInProgress())
            throw new FileOfflineException("Data update in progress");

        //  Check if we are opening a stream associated with the main file
        if (fstate != null && params.isStream()) {

            //  Open an NTFS stream
            return openStream(sess, tree, params, fstate, dbCtx);
        }

        //  Get the file name
        String[] paths = FileName.splitPath(params.getPath());
        String fname = paths[1];

        //  Check if the file name is too long
        if (fname != null && fname.length() > MaxFileNameLen)
            throw new FileNameException("File name too long, " + fname);

        //  Get the file information
        DBFileInfo finfo = getFileDetails(params.getPath(), dbCtx, fstate);

        if (finfo == null)
            throw new FileNotFoundException( params.getPath());

        // If the file data is not available then return an error
        if ( finfo.hasAttribute( FileAttribute.NTOffline))
            throw new FileOfflineException( params.getPath());

        //  If retention is enabled get the expiry date/time
        if (dbCtx.hasRetentionPeriod()) {
            try {

                //  Get the file retention expiry date/time
                RetentionDetails retDetails = dbCtx.getDBInterface().getFileRetentionDetails(finfo.getDirectoryId(), finfo.getFileId());
                if (retDetails != null)
                    fstate.setRetentionExpiryDateTime(retDetails.getEndTime());
            }
            catch (DBException ex) {
                throw new AccessDeniedException("Retention error for " + params.getPath(), ex);
            }
        }

        // Check if the current file open allows the required shared access
        FileAccessToken accessToken = null;

        if ( !params.getPath().equals("\\")) {

            // Check if the file can be opened in the requested mode
            accessToken = dbCtx.getStateCache().grantFileAccess(params, fstate, finfo.isDirectory() ? FileStatus.DirectoryExists : FileStatus.FileExists);
        }

        // DEBUG
        if (Debug.EnableDbg && hasDebug())
            Debug.println("DB openFile() name=" + params.getPath() + ", sharing=" + params.getSharedAccess().name() + ", PID=" + params.getProcessId() + ", token=" + accessToken);

        DBNetworkFile jdbcFile = null;

        try {

            //  Create a JDBC network file and open the top level file
            jdbcFile = (DBNetworkFile) dbCtx.getFileLoader().openFile(params, finfo.getFileId(), 0, finfo.getDirectoryId(), false, finfo.isDirectory());

            jdbcFile.setFileDetails(finfo);
            jdbcFile.setFileState(dbCtx.getStateCache().getFileStateProxy(fstate));

            jdbcFile.openFile(false);

            // Set the granted file access
            if (params.isReadOnlyAccess())
                jdbcFile.setGrantedAccess(NetworkFile.Access.READ_ONLY);
            else if (params.isWriteOnlyAccess())
                jdbcFile.setGrantedAccess(NetworkFile.Access.WRITE_ONLY);
            else
                jdbcFile.setGrantedAccess(NetworkFile.Access.READ_WRITE);

            //  Set the file owner
            if (sess != null)
                jdbcFile.setOwnerSessionId(sess.getUniqueId());

            // Check if the delete-on-close create option is set
            if ( (params.getCreateOptions() & WinNT.CreateDeleteOnClose) != 0)
                jdbcFile.setDeleteOnClose( true);

            // Save the access token
            jdbcFile.setAccessToken(accessToken);

            // Set the full path of the file, if not already set
            if ( jdbcFile.getFullName() == null)
                jdbcFile.setFullName( params.getPath());

            // Update the access date/time
            if ( finfo != null)
                finfo.setAccessDateTime( System.currentTimeMillis());
        }
        finally {

            // If the file object is not valid then release the file access that was granted
            if (jdbcFile == null)
                dbCtx.getStateCache().releaseFileAccess(fstate, accessToken);
        }

        //  Return the network file
        return jdbcFile;
    }

    /**
     * Read a block of data from a file
     *
     * @param sess   Session details
     * @param tree   Tree connection
     * @param file   Network file
     * @param buf    Buffer to return data to
     * @param bufPos Starting position in the return buffer
     * @param siz    Maximum size of data to return
     * @param pos    File offset to read data
     * @return Number of bytes read
     * @throws IOException If an error occurs.
     */
    public int readFile(SrvSession sess, TreeConnection tree, NetworkFile file, byte[] buf, int bufPos, int siz, long pos)
            throws IOException {

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("DB readFile() filePos=" + pos + ", len=" + siz);

        //  Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the database is online
        if (dbCtx.getDBInterface().isOnline() == false)
            throw new DiskOfflineException("Database is offline");

        // Check if the file loader is online
        if (dbCtx.getFileLoader().isOnline() == false)
            throw new DiskOfflineException("File loader is offline");

        //  Check that the network file is our type
        int rxsiz = 0;

        if (file instanceof DBNetworkFile) {

            //  Access the JDBC network file
            DBNetworkFile jfile = (DBNetworkFile) file;

            //  Check if there are any locks on the file
            if (jfile.hasFileState() && jfile.getFileState().hasActiveLocks()) {

                //  Check if this session has write access to the required section of the file
                if (jfile.getFileState().canReadFile(pos, siz, sess.getProcessId()) == false)
                    throw new LockConflictException();
            }

            //  Read from the file
            rxsiz = jfile.readFile(buf, siz, bufPos, pos);

            //  Check if we have reached the end of file
            if (rxsiz == -1)
                rxsiz = 0;
        }

        //  Return the actual read length
        return rxsiz;
    }

    /**
     * Rename a file
     *
     * @param sess    Session details
     * @param tree    Tree connection
     * @param oldName Existing file name
     * @param newName New file name
     * @param netFile NetworkFile for handle based rename, or null for path based rename
     * @throws IOException If an error occurs.
     */
    public void renameFile(SrvSession sess, TreeConnection tree, String oldName, String newName, NetworkFile netFile)
            throws IOException {

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("DB renameFile() from=" + oldName + " to=" + newName);

        //  Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the database is online
        if (dbCtx.getDBInterface().isOnline() == false)
            throw new DiskOfflineException("Database is offline");

        // Check if the file loader is online
        if (dbCtx.getFileLoader().isOnline() == false)
            throw new DiskOfflineException("File loader is offline");

        //  Get, or create, the file state for the existing file
        FileState fstate = getFileState(oldName, dbCtx, true);

        try {

            //  Get the file name
            String[] paths = FileName.splitPath(newName);
            String fname = paths[1];

            //  Check if the file name is too long
            if (fname != null && fname.length() > MaxFileNameLen)
                throw new FileNameException("Destination name too long, " + fname);

            //  Check if the file is within an active retention period
            getRetentionDetailsForState(dbCtx, fstate);

            if (fstate.hasActiveRetentionPeriod())
                throw new AccessDeniedException("File retention active");

            //  Get the file id of the existing file
            int fid = fstate.getFileId();
            int dirId = -1;

            if (fid == -1) {

                //  Split the current path string and find the file id of the existing file/directory
                dirId = findParentDirectoryId(dbCtx, oldName, true);
                if (dirId == -1)
                    throw new FileNotFoundException(oldName);

                //  Get the current file/directory name
                String[] oldPaths = FileName.splitPath(oldName);
                fname = oldPaths[1];

                //  Get the file id
                fid = getFileId(oldName, fname, dirId, dbCtx);
                if (fid == -1)
                    throw new FileNotFoundException(oldName);

                //  Update the file state
                fstate.setFileId(fid);
            }

            //  Get the existing file/directory details
            DBFileInfo curInfo = getFileDetails(oldName, dbCtx, fstate);
            if (dirId == -1 && curInfo != null)
                dirId = curInfo.getDirectoryId();

            //  Check if the new name file/folder already exists
            DBFileInfo newInfo = getFileDetails(newName, dbCtx);

            // Check if we are just changing the case of the file/folder name, in which case it will exist
            if (newInfo != null && oldName.equalsIgnoreCase( newName) == false)
                throw new FileExistsException("Rename to file/folder already exists," + newName);

            //  Check if the loader handles rename requests, an exception may be thrown by the loader
            //  to prevent the file/directory rename.
            if (dbCtx.getFileLoader() instanceof NamedFileLoader) {

                //  Rename the file/directory
                NamedFileLoader namedLoader = (NamedFileLoader) dbCtx.getFileLoader();
                namedLoader.renameFileDirectory(oldName, fid, newName, fstate, curInfo.isDirectory(), netFile);
            }

            //  Get the new file/directory name
            int newDirId = findParentDirectoryId(dbCtx, newName, true);
            if (newDirId == -1)
                throw new FileNotFoundException(newName);
            String[] newPaths = FileName.splitPath(newName);
            String newFname = newPaths[1];

            //  Rename the file/folder, this may also link the file/folder to a new parent directory
            dbCtx.getDBInterface().renameFileRecord(dirId, fid, newFname, newDirId);

            //  Update the file state with the new file name/path
            dbCtx.getStateCache().renameFileState(newName, fstate, curInfo.isDirectory());

            // Update the network file fle state
            if ( netFile instanceof DBNetworkFile) {
                DBNetworkFile dbFile = (DBNetworkFile) netFile;

                // Update the file path/name
                dbFile.setName( fname);
                dbFile.setFullName( newName);

                // Update teh file state
                fstate = getFileState(newName, dbCtx, false);
                dbFile.setFileState( dbCtx.getStateCache().getFileStateProxy(fstate));
            }

            // Update the cached file information name
            if ( curInfo != null) {
                curInfo.setFileName(newFname);
                curInfo.setFullName(newName);
                curInfo.setDirectoryId(newDirId);
            }

            // Update the parent folder timestamps
            updateParentFolderTimestamps( newName, dbCtx);

            if ( dirId != newDirId)
                updateParentFolderTimestamps( oldName, dbCtx);
        }
        catch (DBException ex) {
            throw new IOException("Failed to rename " + oldName, ex);
        }
    }

    /**
     * Seek to the specified point within a file
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param file Network file
     * @param pos  New file position
     * @param typ  Seek type
     * @return New file position
     * @throws IOException If an error occurs.
     */
    public long seekFile(SrvSession sess, TreeConnection tree, NetworkFile file, long pos, int typ)
            throws IOException {

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("DB seekFile()");

        //  Check that the network file is our type
        long newpos = 0;

        if (file instanceof DBNetworkFile) {

            //  Seek within the file
            DBNetworkFile jfile = (DBNetworkFile) file;
            newpos = jfile.seekFile(pos, typ);
        }

        //  Return the new file position
        return newpos;
    }

    /**
     * Set file information
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param name File name
     * @param info File information to be set
     * @throws IOException If an error occurs.
     */
    public void setFileInformation(SrvSession sess, TreeConnection tree, String name, FileInfo info)
            throws IOException {

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("DB setFileInformation() name=" + name + ", info=" + info.toString() + ", set flags=" + info.getSetFileInformationFlagsString());

        // If the only flag set is the delete on close flag then return, nothing to do
        if (info.getSetFileInformationFlags() == FileInfo.SetDeleteOnClose)
            return;

        //  Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the database is online
        if (dbCtx.getDBInterface().isOnline() == false)
            throw new DiskOfflineException("Database is offline");

        // Check if the file loader is online
        if (dbCtx.getFileLoader().isOnline() == false)
            throw new DiskOfflineException("File loader is offline");

        //  Get, or create, the file state
        FileState fstate = getFileState(name, dbCtx, true);

        //  Get the file details
        DBFileInfo dbInfo = getFileDetails(name, dbCtx, fstate);
        if (dbInfo == null)
            throw new FileNotFoundException(name);

        try {

            //  Check if the file is within an active retention period
            getRetentionDetailsForState(dbCtx, fstate);

            if (fstate.hasActiveRetentionPeriod())
                throw new AccessDeniedException("File retention active");

            //  Check if the loader handles set file information requests, an exception may be thrown by the loader
            //  to prevent the update
            if (dbCtx.getFileLoader() instanceof NamedFileLoader) {

                //  Set the file information
                NamedFileLoader namedLoader = (NamedFileLoader) dbCtx.getFileLoader();
                namedLoader.setFileInformation(name, dbInfo.getFileId(), info);
            }

            //  Validate any timestamp updates
            //
            //  Switch off invalid updates from being written to the database but allow them to be cached.
            //  To allow test apps such as IFSTEST to complete successfully.
            int origFlags = info.getSetFileInformationFlags();
            int dbFlags = origFlags;

            if (info.hasSetFlag(FileInfo.SetAccessDate) && info.getAccessDateTime() > MaxTimestampValue)
                dbFlags -= FileInfo.SetAccessDate;

            if (info.hasSetFlag(FileInfo.SetCreationDate) && info.getCreationDateTime() > MaxTimestampValue)
                dbFlags -= FileInfo.SetCreationDate;

            if (info.hasSetFlag(FileInfo.SetModifyDate) && info.getModifyDateTime() > MaxTimestampValue)
                dbFlags -= FileInfo.SetModifyDate;

            //  Check if the inode change date/time has been set
            if (info.hasChangeDateTime() == false) {
                info.setChangeDateTime(System.currentTimeMillis());
                if (info.hasSetFlag(FileInfo.SetChangeDate) == false)
                    info.setFileInformationFlags(info.getSetFileInformationFlags() + FileInfo.SetChangeDate);
            } else if (info.hasSetFlag(FileInfo.SetChangeDate) && info.getChangeDateTime() > MaxTimestampValue)
                dbFlags -= FileInfo.SetChangeDate;

            // Check if file attributes are being set
            if (info.hasSetFlag(FileInfo.SetAttributes)) {

                // Check if this is a folder, make sure the Directory attribute does not get reset
                if (dbInfo.isDirectory() && (info.getFileAttributes() & FileAttribute.Directory) == 0)
                    info.setFileAttributes(info.getFileAttributes() + FileAttribute.Directory);
            }

            //  Update the information flags for the database update
            info.setFileInformationFlags(dbFlags);

            //  Update the file information
            if (dbFlags != 0)
                dbCtx.getDBInterface().setFileInformation(dbInfo.getDirectoryId(), dbInfo.getFileId(), info);

            //  Use the original information flags when updating the cached file information details
            info.setFileInformationFlags(origFlags);

            //  Copy the updated values to the file state
            if (info.hasSetFlag(FileInfo.SetFileSize))
                dbInfo.setFileSize(info.getSize());

            if (info.hasSetFlag(FileInfo.SetAllocationSize))
                dbInfo.setAllocationSize(info.getAllocationSize());

            if (info.hasSetFlag(FileInfo.SetAccessDate))
                dbInfo.setAccessDateTime(info.getAccessDateTime());

            if (info.hasSetFlag(FileInfo.SetCreationDate))
                dbInfo.setAccessDateTime(info.getCreationDateTime());

            if (info.hasSetFlag(FileInfo.SetModifyDate)) {
                long modifyDate = info.getModifyDateTime();
                dbInfo.setAccessDateTime(modifyDate);
                if (info.hasNetworkFile())
                    info.getNetworkFile().setModifyDate(modifyDate);
            }

            if (info.hasSetFlag(FileInfo.SetChangeDate))
                dbInfo.setAccessDateTime(info.getChangeDateTime());

            if (info.hasSetFlag(FileInfo.SetGid))
                dbInfo.setGid(info.getGid());

            if (info.hasSetFlag(FileInfo.SetUid))
                dbInfo.setUid(info.getUid());

            if (info.hasSetFlag(FileInfo.SetMode))
                dbInfo.setMode(info.getMode());

            if (info.hasSetFlag(FileInfo.SetAttributes))
                dbInfo.setFileAttributes(info.getFileAttributes());

            //  Update the file state
            fstate.setFileId(dbInfo.getFileId());
        }
        catch (DBException ex) {
            throw new IOException("Failed to set file information for " + name, ex);
        }
    }

    /**
     * Start a search of the file system
     *
     * @param sess        SrvSession
     * @param tree        TreeConnection
     * @param searchPath  String
     * @param attrib      int
     * @param searchFlags EnumSet&lt;SearchFlags&gt;
     * @return SearchContext
     * @throws FileNotFoundException File not found
     */
    public SearchContext startSearch(SrvSession sess, TreeConnection tree, String searchPath, int attrib, EnumSet<SearchFlags> searchFlags)
            throws FileNotFoundException {

        //  Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the database is online
        if (dbCtx.getDBInterface().isOnline() == false)
            throw new FileNotFoundException("Database is offline");

        // Check if the file loader is online
        if (dbCtx.getFileLoader().isOnline() == false)
            throw new FileNotFoundException("File loader is offline");

        //  Prepend a leading slash to the path if not on the search path
        if (searchPath.startsWith( FileName.DOS_SEPERATOR_STR) == false)
            searchPath = FileName.DOS_SEPERATOR_STR + searchPath;

        // Check if the path is to a file or folder, if there is no trailing seperator
        if ( searchPath.endsWith( FileName.DOS_SEPERATOR_STR) == false) {

            // Get the file name part of the path and check if it contains wildcard character(s)
            String fileNamePart = FileName.getFileNamePart( searchPath);

            if ( fileNamePart == null || WildCard.containsWildcards( fileNamePart) == false) {

                // Check if the search path is to a file or folder
                FileStatus pathSts = fileExists(sess, tree, searchPath);

                if (pathSts == FileStatus.DirectoryExists && searchFlags.contains( SearchFlags.SingleEntry) == false)
                    searchPath = searchPath + FileName.DOS_SEPERATOR_STR;
            }
        }

        //  Get the directory id for the last directory in the path
        int dirId = findParentDirectoryId(dbCtx, searchPath, true);
        if (dirId == -1)
            throw new FileNotFoundException("Invalid path");

        //  Start the search
        SearchContext search = null;

        try {

            //  Check if the search path is a none wildcard search, the file information may be in the
            //  state cache
            if (WildCard.containsWildcards(searchPath) == false) {

                //  Check if there is a file state for the search path
                FileState searchState = getFileState(searchPath, dbCtx, false);
                if (searchState != null && searchState.fileExists() == true) {

                    //  Check if the file state has the file information attached
                    DBFileInfo finfo = (DBFileInfo) searchState.findAttribute(FileState.FileInformation);

                    if (finfo != null) {

                        //  Create a single file search context using the cached file information
                        search = new CachedSearchContext(finfo);

                        //  DEBUG
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("DB StartSearch using cached file information, path=" + searchPath + ", info=" + finfo);
                    }
                }
            }

            //  Start the search via the database interface, if the search is not valid
            if (search == null) {

                // Start the search
                DBSearchContext dbSearch = dbCtx.getDBInterface().startSearch(dirId, searchPath, attrib, DBInterface.FileInfoLevel.All, -1);

                // Check if files should be marked as offline
                dbSearch.setMarkAsOffline(dbCtx.hasOfflineFiles());
                dbSearch.setOfflineFileSize(dbCtx.getOfflineFileSize());

                search = dbSearch;
            }
        }
        catch (DBException ex) {
            throw new FileNotFoundException( ex.getMessage());
        }

        //  Return the search context
        return search;
    }

    /**
     * Truncate a file to the specified size
     *
     * @param sess Server session
     * @param tree Tree connection
     * @param file Network file details
     * @param siz  New file length
     * @throws IOException If an error occurs.
     */
    public void truncateFile(SrvSession sess, TreeConnection tree, NetworkFile file, long siz)
            throws java.io.IOException {

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("DB truncateFile()");

        //  Check that the network file is our type
        if (file instanceof DBNetworkFile) {

            //  Access the JDBC context
            DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

            //  Get the JDBC file
            DBNetworkFile jfile = (DBNetworkFile) file;

            //  Get, or create, the file state
            FileState fstate = jfile.getFileState();

            //  Get the file details
            DBFileInfo dbInfo = getFileDetails(jfile.getFullName(), dbCtx, fstate);
            if (dbInfo == null)
                throw new FileNotFoundException(jfile.getFullName());

            //  Check if the new file size is greater than the maximum allowed file size, if enabled
            if (dbCtx.hasMaximumFileSize() && siz > dbCtx.getMaximumFileSize()) {

                // Mark the file to delete on close
                file.setDeleteOnClose(true);

                // Return a disk full error
                throw new DiskFullException("Write is beyond maximum allowed file size");
            }

            //  Keep track of the allocation/release size in case the file resize fails
            long allocSize = 0L;
            long releaseSize = 0L;

            //  Check if there is a quota manager
            QuotaManager quotaMgr = dbCtx.getQuotaManager();

            if (dbCtx.hasQuotaManager()) {

                //  Determine if the new file size will release space or require space allocating
                if (siz > dbInfo.getAllocationSize()) {

                    //  Calculate the space to be allocated
                    allocSize = siz - dbInfo.getAllocationSize();

                    //  Allocate space to extend the file
                    quotaMgr.allocateSpace(sess, tree, file, allocSize);
                } else {

                    //  Calculate the space to be released as the file is to be truncated, release the space if
                    //  the file truncation is successful
                    releaseSize = dbInfo.getAllocationSize() - siz;
                }
            }

            //  Set the file length
            try {
                jfile.truncateFile(siz);
            }
            catch (IOException ex) {

                //  Check if we allocated space to the file
                if (allocSize > 0 && quotaMgr != null)
                    quotaMgr.releaseSpace(sess, tree, file.getFileId(), null, allocSize);

                //  Rethrow the exception
                throw ex;
            }

            //  Check if space has been released by the file resizing
            if (releaseSize > 0 && quotaMgr != null)
                quotaMgr.releaseSpace(sess, tree, file.getFileId(), null, releaseSize);

            //  Update the file information
            if (allocSize > 0)
                dbInfo.setAllocationSize(dbInfo.getAllocationSize() + allocSize);
            else if (releaseSize > 0)
                dbInfo.setAllocationSize(dbInfo.getAllocationSize() - releaseSize);

            //  Update the last file change date/time
            try {

                //  Build the file information to set the change date/time
                FileInfo finfo = new FileInfo();

                finfo.setChangeDateTime(System.currentTimeMillis());
                finfo.setFileInformationFlags(FileInfo.SetChangeDate);

                //  Set the file change date/time
                dbCtx.getDBInterface().setFileInformation(jfile.getDirectoryId(), jfile.getFileId(), finfo);

                //  Update the cached file information
                dbInfo.setChangeDateTime(finfo.getChangeDateTime());
                dbInfo.setAllocationSize(siz);
            }
            catch (DBException ex) {
                throw new IOException( "Failed to truncate " + file.getFullName(), ex);
            }
        }
    }

    /**
     * Write a block of data to a file
     *
     * @param sess    Session details
     * @param tree    Tree connection
     * @param file    Network file
     * @param buf     Data to be written
     * @param bufoff  Offset of data within the buffer
     * @param siz     Number of bytes to be written
     * @param fileoff Offset within the file to start writing the data
     */
    public int writeFile(SrvSession sess, TreeConnection tree, NetworkFile file, byte[] buf, int bufoff, int siz, long fileoff)
            throws IOException {

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("DB writeFile()");

        //  Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the database is online
        if (dbCtx.getDBInterface().isOnline() == false)
            throw new DiskOfflineException("Database is offline");

        // Check if the file loader is online
        if (dbCtx.getFileLoader().isOnline() == false)
            throw new DiskOfflineException("File loader is offline");

        //  Check that the network file is our type
        if (file instanceof DBNetworkFile) {

            //  Access the JDBC network file
            DBNetworkFile jfile = (DBNetworkFile) file;

            //  Check if there are any locks on the file
            if (jfile.hasFileState() && jfile.getFileState().hasActiveLocks()) {

                //  Check if this session has write access to the required section of the file
                if (jfile.getFileState().canWriteFile(fileoff, siz, sess.getProcessId()) == false)
                    throw new LockConflictException();
            }

            // Check if there is a maximum file size, if so then check if the write is beyond the allowed file size
            if (dbCtx.hasMaximumFileSize() && (fileoff + siz) > dbCtx.getMaximumFileSize()) {

                // Mark the file to delete on close
                file.setDeleteOnClose(true);

                // Return a disk full error
                throw new DiskFullException("Write is beyond maximum allowed file size");
            }

            //  Check if there is a quota manager
            QuotaManager quotaMgr = dbCtx.getQuotaManager();

            if (quotaMgr != null) {

                //  Get the file information
                DBFileInfo finfo = getFileDetails(jfile.getFullName(), dbCtx, jfile.getFileState());
                if (finfo == null)
                    throw new FileNotFoundException(jfile.getFullName());

                //  Check if the file requires extending
                long extendSize = 0L;
                long endOfWrite = fileoff + siz;

                if (endOfWrite > finfo.getSize()) {

                    //  Calculate the amount the file must be extended
                    extendSize = endOfWrite - finfo.getSize();

                    //  Allocate space for the file extend
                    quotaMgr.allocateSpace(sess, tree, file, extendSize);
                }

                //  Write to the file
                try {
                    jfile.writeFile(buf, siz, bufoff, fileoff);
                }
                catch (IOException ex) {

                    //  Check if we allocated space to the file
                    if (extendSize > 0 && quotaMgr != null)
                        quotaMgr.releaseSpace(sess, tree, file.getFileId(), null, extendSize);

                    //  Rethrow the exception
                    throw ex;
                }

                //  Update the file information
                if (extendSize > 0)
                    finfo.setAllocationSize(MemorySize.roundupLongSize(endOfWrite));
            } else {

                //  Write to the file
                jfile.writeFile(buf, siz, bufoff, fileoff);

                //  Update the cached file size, set the Archive attribute, update the last access time
                DBFileInfo finfo = getFileDetails(jfile.getFullName(), dbCtx, jfile.getFileState());
                if (finfo != null) {
                    finfo.setFileSize(jfile.getFileSize());
                    if ( !finfo.isArchived())
                        finfo.setFileAttributes( finfo.getFileAttributes() + FileAttribute.Archive);
                    finfo.setAccessDateTime( System.currentTimeMillis());
                }
            }
        }

        //  Return the actual write length
        return siz;
    }

    /**
     * Parse/validate the parameter string and create a device context for this share
     *
     * @param shareName String
     * @param args      ConfigElement
     * @return DeviceContext
     * @throws DeviceContextException Device context error
     */
    public DeviceContext createContext(String shareName, ConfigElement args)
            throws DeviceContextException {

        //  Check the arguments
        if (args.getChildCount() < 3)
            throw new DeviceContextException("Not enough context arguments");

        //  Check for the debug enable flags
        if (args.getChild("Debug") != null)
            m_debug = true;

        //  Create the database device context
        DBDeviceContext ctx = new DBDeviceContext(args);

        //  Return the database device context
        return ctx;
    }


    /**
     * Get the file information for a path
     *
     * @param path  String
     * @param dbCtx DBDeviceContext
     * @return DBFileInfo
     */
    protected final DBFileInfo getFileDetails(String path, DBDeviceContext dbCtx) {
        return getFileDetails(path, dbCtx, null);
    }

    /**
     * Get the file information for a path
     *
     * @param path   String
     * @param dbCtx  DBDeviceContext
     * @param fstate FileState
     * @return DBFileInfo
     */
    protected final DBFileInfo getFileDetails(String path, DBDeviceContext dbCtx, FileState fstate) {

        //  Check if the file details are attached to the file state
        if (fstate != null) {

            //  Return the file information
            DBFileInfo finfo = (DBFileInfo) fstate.findAttribute(FileState.FileInformation);
            if (finfo != null)
                return finfo;
        }

        //  Check for the root directory
        if (path.length() == 0 || path.compareTo("\\") == 0) {

            //  Get the root directory information from the device context
            DBFileInfo rootDir = dbCtx.getRootDirectoryInfo();

            //  Mark the directory as existing
            if (fstate != null)
                fstate.setFileStatus(FileStatus.DirectoryExists);
            return rootDir;
        }

        //  Split the path string and find the parent directory id
        int dirId = findParentDirectoryId(dbCtx, path, true);
        if (dirId == -1)
            return null;

        // Strip any trailing slash from the path
        if (path.length() > 1 && path.endsWith(FileName.DOS_SEPERATOR_STR))
            path = path.substring(0, path.length() - 1);

        //  Get the file name
        String[] paths = FileName.splitPathStream(path);
        String fname = paths[1];

        String filePath = null;

        if (paths[0] != null && paths[0].endsWith(FileName.DOS_SEPERATOR_STR) == false)
            filePath = paths[0] + FileName.DOS_SEPERATOR_STR + paths[1];
        else
            filePath = paths[0] + paths[1];

        //  Get the file id for the specified file
        int fid = getFileId(filePath, fname, dirId, dbCtx);
        if (fid == -1) {

            // Set the file status as not existing
            if (fstate != null)
                fstate.setFileStatus(FileStatus.NotExist);
            return null;
        }

        //  Create the file information
        DBFileInfo finfo = getFileInfo(filePath, dirId, fid, dbCtx);

        if (finfo != null && fstate != null) {

            //  Set various file state details
            fstate.setFileStatus(finfo.isDirectory() ? FileStatus.DirectoryExists : FileStatus.FileExists);
            fstate.setFileId(finfo.getFileId());

            //  Set the file name
            finfo.setFileName(fname);
            finfo.setFullName(path);

            // Check if files should be marked as offline
            if (dbCtx.hasOfflineFiles() && finfo.hasAttribute(FileAttribute.NTOffline) == false) {
                if (dbCtx.getOfflineFileSize() == 0 || finfo.getSize() >= dbCtx.getOfflineFileSize())
                    finfo.setFileAttributes(finfo.getFileAttributes() + FileAttribute.NTOffline);
            }

        } else if (finfo == null && fstate != null) {

            // Set the file status
            fstate.setFileStatus(FileStatus.NotExist);
        }

        //  Check if the path is a file stream
        if (paths[2] != null) {

            //  Get the file information for the stream
            finfo = getStreamInfo(fstate, paths, dbCtx);
        }

        //  Return the file/stream information
        return finfo;
    }

    /**
     * Get the file id for a file
     *
     * @param path  String
     * @param name  String
     * @param dirId int
     * @param dbCtx DBDeviceContext
     * @return int
     */
    protected final int getFileId(String path, String name, int dirId, DBDeviceContext dbCtx) {

        //  Check if the file is in the cache
        FileStateCache cache = dbCtx.getStateCache();
        FileState state = null;

        if (cache != null) {

            //  Search for the file state
            state = cache.findFileState(path);
            if (state != null) {

                //  Check if the file id is cached
                if (state.getFileId() != -1) {

                    //  Debug
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("@@ Cache hit - getFileId() name=" + name);

                    //  Return the file id
                    return state.getFileId();
                } else if (state.getFileStatus() == FileStatus.NotExist) {

                    //  DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("@@ Cache hit - getFileStatus() name=" + name + ", sts=NotExist");

                    //  Indicate that the file does not exist
                    return -1;
                }
            }
        }

        //  Get the file id from the database
        int fileId = -1;

        try {

            //  Get the file id
            fileId = dbCtx.getDBInterface().getFileId(dirId, name, false, true);
        }
        catch (DBException ex) {
            Debug.println( ex);
        }

        //  Update the cache entry, if available
        if (state != null && fileId != -1)
            state.setFileId(fileId);

        //  Return the file id, or -1 if the file was not found
        return fileId;
    }

    /**
     * Load the retention details for a file state, if enabled
     *
     * @param dbCtx  DBDeviceContext
     * @param fstate FileState
     * @throws DBException Database error
     */
    protected final void getRetentionDetailsForState(DBDeviceContext dbCtx, FileState fstate)
            throws DBException {

        //  If retention is enabled get the expiry date/time
        if (dbCtx.hasRetentionPeriod()) {

            //  Get the file retention expiry date/time
            RetentionDetails retDetails = dbCtx.getDBInterface().getFileRetentionDetails(-1, fstate.getFileId());
            if (retDetails != null)
                fstate.setRetentionExpiryDateTime(retDetails.getEndTime());
        }
    }

    /**
     * Find the directory id for the parent directory in the specified path
     *
     * @param ctx      DBDeviceContext
     * @param path     String
     * @param filePath boolean
     * @return int
     */
    protected final int findParentDirectoryId(DBDeviceContext ctx, String path, boolean filePath) {

        //  Split the path
        String[] paths = null;

        if (path != null && path.startsWith( FileName.DOS_SEPERATOR_STR)) {

            //  Split the path
            paths = FileName.splitPath(path);
        } else {

            //  Add a leading slash to the path before parsing
            paths = FileName.splitPath(FileName.DOS_SEPERATOR_STR + path);
        }

        if (paths[0] != null && paths[0].compareTo(FileName.DOS_SEPERATOR_STR) == 0 ||
                paths[0].startsWith(FileName.DOS_SEPERATOR_STR) == false)
            return RootDirId;

        //  Check if the file is in the cache
        FileStateCache cache = ctx.getStateCache();
        FileState state = null;

        if (cache != null) {

            //  Search for the file state
            state = cache.findFileState(paths[0]);
            if (state != null && state.getFileId() != -1) {

                //  Debug
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("@@ Cache hit - findParentDirectoryId() path=" + paths[0]);

                //  Return the file id
                return state.getFileId();
            }
        }

        //  Get the directory id list
        int[] ids = findParentDirectoryIdList(ctx, path, filePath);
        if (ids == null)
            return -1;

        //  Check for the root directory id only
        if (ids.length == 1)
            return ids[0];

        //  Return the directory id of the last directory
        int idx = ids.length - 1;
        if (filePath == true && ids[ids.length - 1] == -1)
            idx--;

        return ids[idx];
    }

    /**
     * Find the directory ids for the specified path list
     *
     * @param ctx      DBDeviceContext
     * @param path     String
     * @param filePath boolean
     * @return int[]
     */
    protected final int[] findParentDirectoryIdList(DBDeviceContext ctx, String path, boolean filePath) {

        //  Validate the paths and check for the root path
        String[] paths = FileName.splitAllPaths(path);

        if (paths == null || paths.length == 0)
            return null;
        if (paths[0].compareTo("*.*") == 0 || paths[0].compareTo("*") == 0 ||
                (filePath == true && paths.length == 1)) {
            int[] ids = {0};
            return ids;
        }
        if (paths[0].startsWith("\\")) {

            //  Trim the leading slash from the first path
            paths[0] = paths[0].substring(1);
        }

        //  Find the directory id by traversing the list of directories
        int endIdx = paths.length - 1;
        if (filePath == true)
            endIdx--;

        //  If there are no paths to check then return the root directory id
        if (endIdx <= 1 && paths[0].length() == 0) {
            int[] ids = new int[1];
            ids[0] = 0;
            return ids;
        }

        //  Allocate the directory id list
        int[] ids = new int[paths.length];
        for (int i = 0; i < ids.length; i++)
            ids[i] = -1;

        //  Build up the current path as we traverse the list
        StringBuffer pathStr = new StringBuffer("\\");

        //  Check for paths in the file state cache
        FileStateCache cache = ctx.getStateCache();
        FileState fstate = null;

        //  Traverse the path list, initialize the directory id to the root id
        int dirId = 0;
        int parentId = -1;
        int idx = 0;

        try {

            //  Loop until the end of the path list
            while (idx <= endIdx) {

                //  Get the current path, and add to the full path string
                String curPath = paths[idx];
                pathStr.append(curPath);

                //  Check if there is a file state for the current path
                fstate = cache.findFileState(pathStr.toString());

                if (fstate != null && fstate.getFileId() != -1) {

                    //  Get the file id from the cached information
                    ids[idx] = fstate.getFileId();
                    parentId = dirId;
                    dirId = ids[idx];
                } else {

                    //  Search for the current directory in the database
                    parentId = dirId;
                    dirId = ctx.getDBInterface().getFileId(dirId, curPath, true, true);

                    if (dirId != -1) {

                        //  Get the next directory id
                        ids[idx] = dirId;

                        //  Check if we have a file state, or create a new file state for the current path
                        if (fstate != null) {

                            //  Set the file id for the file state
                            fstate.setFileId(dirId);
                        } else {

                            //  Create a new file state for the current path
                            fstate = cache.findFileState(pathStr.toString(), true);

                            //  Get the file information
                            DBFileInfo finfo = ctx.getDBInterface().getFileInformation(parentId, dirId, DBInterface.FileInfoLevel.All);
                            fstate.addAttribute(FileState.FileInformation, finfo);
                            fstate.setFileStatus(finfo.isDirectory() ? FileStatus.DirectoryExists : FileStatus.FileExists);
                            fstate.setFileId(dirId);
                        }
                    } else
                        return null;
                }

                //  Update the path index
                idx++;

                //  Update the current path string
                pathStr.append("\\");
            }
        }
        catch (DBException ex) {
            Debug.println(ex);
            return null;
        }

        //  Return the directory id list
        return ids;
    }

    /**
     * Return file information about the specified file, using the internal file id
     *
     * @param path  String
     * @param dirId int
     * @param fid   int
     * @param dbCtx DBDeviceContext
     * @return DBFileInfo
     */
    public DBFileInfo getFileInfo(String path, int dirId, int fid, DBDeviceContext dbCtx) {

        //  Check if the file is in the cache
        FileState state = getFileState(path, dbCtx, true);

        if (state != null && state.getFileId() != -1) {

            //  Debug
            if (Debug.EnableInfo && hasDebug())
                Debug.println("@@ Cache hit - getFileInfo() path=" + path);

            //  Return the file information
            DBFileInfo finfo = (DBFileInfo) state.findAttribute(FileState.FileInformation);
            if (finfo != null)
                return finfo;
        }

        //  Get the file information from the database
        DBFileInfo finfo = null;

        try {

            //  Get the file information
            finfo = dbCtx.getDBInterface().getFileInformation(dirId, fid, DBInterface.FileInfoLevel.All);
        }
        catch (DBException ex) {
            Debug.println(ex);
            finfo = null;
        }

        //  Set the full path for the file
        if (finfo != null)
            finfo.setFullName(path);

        //  Update the cached information, if available
        if (state != null && finfo != null) {
            state.addAttribute(FileState.FileInformation, finfo);
            state.setFileStatus(finfo.isDirectory() ? FileStatus.DirectoryExists : FileStatus.FileExists);
        }

        //  Return the file information
        return finfo;
    }

    /**
     * Get the details for a file stream
     *
     * @param parent FileState
     * @param paths  String[]
     * @param dbCtx  DBDeviceContext
     * @return DBFileInfo
     */
    public DBFileInfo getStreamInfo(FileState parent, String[] paths, DBDeviceContext dbCtx) {

        //  Check if the file is in the cache
        String streamPath = paths[0] + paths[1] + paths[2];
        FileState state = getFileState(streamPath, dbCtx, true);

        if (state != null && state.getFileId() != -1) {

            //  Debug
            if (Debug.EnableInfo && hasDebug())
                Debug.println("@@ Cache hit - getStreamInfo() path=" + streamPath);

            //  Return the file information
            DBFileInfo finfo = (DBFileInfo) state.findAttribute(FileState.FileInformation);
            if (finfo != null)
                return finfo;
        }

        //  DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("DBDiskDriver getStreamInfo parent=" + parent.getPath() + ", stream=" + paths[2]);

        //  Get a list of the streams for the parent file
        DBFileInfo finfo = null;

        try {

            //  Get the list of streams
            StreamInfoList sList = (StreamInfoList) parent.findAttribute(DBStreamList);

            if (sList == null) {

                //  No cached stream information, get the list from the database
                sList = dbCtx.getDBInterface().getStreamsList(parent.getFileId(), DBInterface.StreamInfoLevel.All);

                //  Cache the information
                parent.addAttribute(DBStreamList, sList);
            }

            //  Find the required stream information
            if (sList != null) {

                //  Find the required stream information
                StreamInfo sInfo = sList.findStream(paths[2]);

                //  Convert the stream information to file information
                if (sInfo != null) {

                    //  Load the stream information
                    finfo = new DBFileInfo();
                    finfo.setFileId(parent.getFileId());

                    //  Copy the stream information
                    finfo.setFileName(sInfo.getName());
                    finfo.setSize(sInfo.getSize());

                    //  Get the file creation date, or use the current date/time
                    if (sInfo.hasCreationDateTime())
                        finfo.setCreationDateTime(sInfo.getCreationDateTime());

                    //  Get the modification date, or use the current date/time
                    if (sInfo.hasModifyDateTime())
                        finfo.setModifyDateTime(sInfo.getModifyDateTime());
                    else if (sInfo.hasCreationDateTime())
                        finfo.setModifyDateTime(sInfo.getCreationDateTime());

                    //  Get the last access date, or use the current date/time
                    if (sInfo.hasAccessDateTime())
                        finfo.setAccessDateTime(sInfo.getAccessDateTime());
                    else if (sInfo.hasCreationDateTime())
                        finfo.setAccessDateTime(sInfo.getCreationDateTime());
                }
            }
        }
        catch (DBException ex) {
            Debug.println(ex);
            finfo = null;
        }

        //  Set the full path for the file
        if (finfo != null)
            finfo.setFullName(streamPath);

        //  Update the cached information, if available
        if (state != null && finfo != null) {
            state.addAttribute(FileState.FileInformation, finfo);
            state.setFileStatus(FileStatus.FileExists);
        }

        //  Return the file information for the stream
        return finfo;
    }

    /**
     * Get the cached file state for the specified path
     *
     * @param path   String
     * @param ctx    DBDeviceContext
     * @param create boolean
     * @return FileState
     */
    protected final FileState getFileState(String path, DBDeviceContext ctx, boolean create) {

        //  Access the file state cache
        FileStateCache cache = ctx.getStateCache();
        if (cache == null)
            return null;

        //  Return the required file state
        return cache.findFileState(path, create);
    }

    /**
     * Update the parent folder path file information last write and change timestamps
     *
     * @param path String
     * @param ctx DBDeviceContext
     */
    protected final void updateParentFolderTimestamps(String path, DBDeviceContext ctx) {

        // Get the parent folder path
        String parentPath = FileName.removeFileName( path);
        if ( parentPath == null)
            return;

        // Get the file state for the parent folder, or create it
        FileState fstate = getFileState( parentPath, ctx, true);

        if ( fstate != null) {

            // Update the cached last write, access and change timestamps for the folder
            long updTime = System.currentTimeMillis();

            fstate.updateModifyDateTime( updTime);
            fstate.updateChangeDateTime( updTime);
            fstate.updateAccessDateTime();

            // Update the cached file information, if available
            DBFileInfo finfo = (DBFileInfo) fstate.findAttribute(FileState.FileInformation);
            if (finfo != null) {

                // Update the file information timestamps
                finfo.setModifyDateTime( updTime);
                finfo.setChangeDateTime( updTime);
                finfo.setAccessDateTime( updTime);
            }

            // DEBUG
//            if ( hasDebug()) {
//                Debug.println("$$$ Update parent folder path=" + parentPath + ", fstate=" + fstate);
//                ctx.getStateCache().dumpCache( true);
//            }
        }
    }

    /**
     * Connection opened to this disk device
     *
     * @param sess Server session
     * @param tree Tree connection
     */
    public void treeOpened(SrvSession sess, TreeConnection tree) {
    }

    /**
     * Connection closed to this device
     *
     * @param sess Server session
     * @param tree Tree connection
     */
    public void treeClosed(SrvSession sess, TreeConnection tree) {
    }

    /**
     * Check if general debug output is enabled
     *
     * @return boolean
     */
    protected final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Return disk information about a shared filesystem
     *
     * @param ctx  DiskDeviceContext
     * @param info SrvDiskInfo
     * @throws IOException If an error occurs.
     */
    public final void getDiskInformation(DiskDeviceContext ctx, SrvDiskInfo info)
            throws IOException {

        //  Check if there is static disk size information available
        if (ctx.hasDiskInformation())
            info.copyFrom(ctx.getDiskInformation());

        //  Check that the context is a JDBC context
        if (ctx instanceof DBDeviceContext) {

            //  Access the associated file loader class, if it implements the disk size interface then call the loader
            //  to fill in the disk size details
            DBDeviceContext dbCtx = (DBDeviceContext) ctx;

            if (dbCtx.getFileLoader() instanceof DiskSizeInterface) {

                //  Pass the disk size request to the associated file loader
                DiskSizeInterface sizeInterface = (DiskSizeInterface) dbCtx.getFileLoader();

                sizeInterface.getDiskInformation(ctx, info);

                //  DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("DBDiskDriver getDiskInformation() handed to file loader");
            }
        }

        //  Check if there is a quota manager, if so then get the current free space from the quota manager
        if (ctx.hasQuotaManager()) {

            //  Get the free space, in bytes, from the quota manager
            long freeSpace = ctx.getQuotaManager().getAvailableFreeSpace();

            //  Convert the free space to free units
            long freeUnits = freeSpace / info.getUnitSize();
            info.setFreeUnits(freeUnits);
        }
    }

    /**
     * Determine if NTFS streams support is enabled. Check if the associated file loader
     * supports NTFS streams.
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return boolean
     */
    public boolean hasStreamsEnabled(SrvSession sess, TreeConnection tree) {

        //  Check that the context is a JDBC context
        if (tree.getContext() instanceof DBDeviceContext) {

            //  Access the associated file loader class to check if it supports NTFS streams
            DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
            if (dbCtx.hasNTFSStreamsEnabled()) {

                //  Check if the file loader supports NTFS streams
                return dbCtx.getFileLoader().supportsStreams();
            }
        }

        //  Disable streams
        return false;
    }

    /**
     * Get the stream information for the specified file stream
     *
     * @param sess       SrvSession
     * @param tree       TreeConnection
     * @param streamInfo StreamInfo
     * @return StreamInfo
     * @throws IOException If an error occurs.
     */
    public StreamInfo getStreamInformation(SrvSession sess, TreeConnection tree, StreamInfo streamInfo)
            throws IOException {

        //  DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("### getStreamInformation() called ###");

        return null;
    }

    /**
     * Return the list of available streams for the specified file
     *
     * @param sess     SrvSession
     * @param tree     TreeConnection
     * @param fileName String
     * @return StreamInfoList
     * @throws IOException If an error occurs.
     */
    public StreamInfoList getStreamList(SrvSession sess, TreeConnection tree, String fileName)
            throws IOException {

        //  Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        //  Get the file state for the top level file
        String parentPath = FileName.getParentPathForStream(fileName);
        FileState fstate = getFileState(parentPath, dbCtx, true);

        //  Check if the file state already has the streams list cached
        StreamInfoList streamList = (StreamInfoList) fstate.findAttribute(DBStreamList);
        if (streamList != null && streamList.numberOfStreams() > 0)
            return streamList;

        //  Get the main file information and convert to stream information
        DBFileInfo finfo = getFileDetails(fileName, dbCtx, fstate);
        if (finfo == null)
            return null;

        //  Create the stream list
        streamList = new StreamInfoList();

        // Add an entry for the main file data stream
        StreamInfo sinfo = new StreamInfo("::$DATA", finfo.getFileId(), 0, finfo.getSize(), finfo.getAllocationSize());
        streamList.addStream(sinfo);

        //  Get the list of streams
        StreamInfoList sList = loadStreamList(fstate, finfo, dbCtx, true);
        if (sList != null) {

            //  Copy the stream information to the main list
            for (int i = 0; i < sList.numberOfStreams(); i++) {

                //  Add the stream to the main list
                streamList.addStream(sList.getStreamAt(i));
            }
        }

        //  Cache the stream list
        fstate.addAttribute(DBStreamList, streamList);

        //  Return the stream list
        return streamList;
    }

    /**
     * Create a new stream with the specified parent file
     *
     * @param sess   SrvSession
     * @param tree   TreeConnection
     * @param params FileOpenParams
     * @param parent FileState
     * @param dbCtx  DBDeviceContext
     * @return NetworkFile
     * @throws IOException If an error occurs.
     */
    protected final NetworkFile createStream(SrvSession sess, TreeConnection tree, FileOpenParams params, FileState parent, DBDeviceContext dbCtx)
            throws IOException {

        //  Get the file information for the parent file
        DBFileInfo finfo = getFileDetails(params.getPath(), dbCtx, parent);

        if (finfo == null)
            throw new AccessDeniedException();

        //  Get the list of streams for the file
        StreamInfoList streamList = (StreamInfoList) parent.findAttribute(DBStreamList);
        if (streamList == null)
            streamList = getStreamList(sess, tree, params.getPath());

        if (streamList == null)
            throw new AccessDeniedException();

        //  Check if the stream already exists
        if (streamList.findStream(params.getStreamName()) != null)
            throw new FileExistsException("Stream exists, " + params.getFullPath());

        //  Create a new stream record
        DBNetworkFile file = null;
        FileAccessToken accessToken = null;
        FileState fstate = null;

        try {

            // Check if the file can be opened in the requested mode
            fstate = getFileState(params.getFullPath(), dbCtx, true);
            accessToken = dbCtx.getStateCache().grantFileAccess(params, fstate, FileStatus.FileExists);

            //  Create a new stream record
            int stid = dbCtx.getDBInterface().createStreamRecord(params.getStreamName(), finfo.getFileId());

            //  Create a network file to hold details of the new stream
            file = (DBNetworkFile) dbCtx.getFileLoader().openFile(params, finfo.getFileId(), stid, finfo.getDirectoryId(), true, false);
            file.setFullName(params.getFullPath());
            file.setStreamId(stid);
            file.setStreamName(params.getStreamName());
            file.setDirectoryId(finfo.getDirectoryId());
            file.setAttributes(params.getAttributes());

            //  Set the file state for the file
            file.setFileState(dbCtx.getStateCache().getFileStateProxy(fstate));

            // Store the access token
            file.setAccessToken(accessToken);

            //  Open the stream file
            file.openFile(true);

            //  Add an entry to the stream list for the new stream
            StreamInfo stream = new StreamInfo(params.getStreamName(), file.getFileId(), stid);
            streamList.addStream(stream);

            //  DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("createStream() file=" + params.getPath() + ", stream=" + params.getStreamName() + ", fid/stid=" + file.getFileId() + "/" + stid);
        }
        catch (DBException ex) {
            if (Debug.EnableError && hasDebug()) {
                Debug.println("Error: " + ex.toString());
                Debug.println(ex);
            }
        }
        finally {

            // Check if the stream file is not valid but an access token has been allocated
            if (file == null && accessToken != null)
                dbCtx.getStateCache().releaseFileAccess(fstate, accessToken);
        }

        //  If the file/stream is not valid throw an exception
        if (file == null)
            throw new AccessDeniedException(params.getFullPath());

        //  Return the network file for the new stream
        return file;
    }

    /**
     * Open an existing stream with the specified parent file
     *
     * @param sess   SrvSession
     * @param tree   TreeConnection
     * @param params FileOpenParams
     * @param parent FileState
     * @param dbCtx  DBDeviceContext
     * @return NetworkFile
     * @throws IOException If an error occurs.
     */
    protected final NetworkFile openStream(SrvSession sess, TreeConnection tree, FileOpenParams params, FileState parent, DBDeviceContext dbCtx)
            throws IOException {

        //  Get the file information for the parent file
        DBFileInfo finfo = getFileDetails(params.getPath(), dbCtx, parent);

        if (finfo == null)
            throw new AccessDeniedException();

        //  Get the list of streams for the file
        StreamInfoList streamList = getStreamList(sess, tree, params.getPath());
        if (streamList == null)
            throw new AccessDeniedException();

        //  Check if the stream exists
        StreamInfo sInfo = streamList.findStream(params.getStreamName());

        if (sInfo == null)
            throw new FileNotFoundException("Stream does not exist, " + params.getFullPath());

        // Open the stream
        DBNetworkFile jdbcFile = null;
        FileAccessToken accessToken = null;
        FileState fstate = null;

        try {

            //  Get, or create, a file state for the stream
            fstate = getFileState(params.getFullPath(), dbCtx, true);

            // Check if the file stream can be opened in the requested mode
            accessToken = dbCtx.getStateCache().grantFileAccess(params, fstate, FileStatus.FileExists);

            //  Check if the file shared access indicates exclusive file access
            if (params.getSharedAccess() == SharingMode.NOSHARING && fstate.getOpenCount() > 0)
                throw new FileSharingException("File already open, " + params.getPath());

            //  Set the file information for the stream, using the stream information
            DBFileInfo sfinfo = new DBFileInfo(sInfo.getName(), params.getFullPath(), finfo.getFileId(), finfo.getDirectoryId());

            sfinfo.setFileSize(sInfo.getSize());
            sfinfo.setFileAttributes(FileAttribute.Normal);

            sfinfo.setCreationDateTime(sInfo.getCreationDateTime());
            sfinfo.setModifyDateTime(sInfo.getModifyDateTime());
            sfinfo.setAccessDateTime(sInfo.getAccessDateTime());

            fstate.addAttribute(FileState.FileInformation, sfinfo);

            //  Create a JDBC network file and open the stream
            if (Debug.EnableInfo && hasDebug())
                Debug.println("DB openStream() file=" + params.getPath() + ", stream=" + sInfo.getName());

            jdbcFile = (DBNetworkFile) dbCtx.getFileLoader().openFile(params, finfo.getFileId(), sInfo.getStreamId(),
                    finfo.getDirectoryId(), false, false);

            jdbcFile.setFileState(dbCtx.getStateCache().getFileStateProxy(fstate));
            jdbcFile.setFileDetails(sfinfo);

            //  Open the stream file, if not a directory
            jdbcFile.openFile(false);
        }
        finally {

            // Check if the stream file is not valid but an access token has been allocated
            if (jdbcFile == null && accessToken != null)
                dbCtx.getStateCache().releaseFileAccess(fstate, accessToken);
        }

        //  Return the network file
        return jdbcFile;
    }

    /**
     * Close an NTFS stream
     *
     * @param sess   Session details
     * @param tree   Tree connection
     * @param stream Network file details
     * @throws IOException If an error occurs.
     */
    protected final void closeStream(SrvSession sess, TreeConnection tree, NetworkFile stream)
            throws IOException {

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("DB closeStream() file=" + stream.getFullName() + ", stream=" + stream.getStreamName() +
                    ", fid/stid=" + stream.getFileId() + "/" + stream.getStreamId());

        //  Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        //  Close the file
        dbCtx.getFileLoader().closeFile(sess, stream);

        //  Access the JDBC file
        DBNetworkFile jdbcFile = null;

        if (stream instanceof DBNetworkFile) {

            //  Access the JDBC file
            jdbcFile = (DBNetworkFile) stream;

            //  Decrement the open file count
            FileState fstate = jdbcFile.getFileState();

            //  Check if the file state is valid, if not then check the main file state cache
            if (fstate == null) {

                //  Check the main file state cache
                fstate = getFileState(stream.getFullName(), dbCtx, false);
            }

            // Release the file access token for the stream
            if (jdbcFile.hasAccessToken()) {

                // Release the access token, update the open file count
                dbCtx.getStateCache().releaseFileAccess(fstate, jdbcFile.getAccessToken());
            }

            //  Check if we have a valid file state
            if (fstate != null) {

                //  Update the cached file size
                FileInfo finfo = getFileInformation(sess, tree, fstate.getPath());
                if (finfo != null && stream.getWriteCount() > 0) {

                    //  Update the file size
                    finfo.setSize(jdbcFile.getFileSize());

                    //  Update the modified date/time
                    finfo.setModifyDateTime(jdbcFile.getModifyDate());

                    //  DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("  Stream size=" + jdbcFile.getFileSize() + ", modifyDate=" + jdbcFile.getModifyDate());
                }
            }
        } else
            Debug.println("closeFile() Not DBNetworkFile file=" + stream);

        //  Check if the stream was opened for write access, if so then update the stream size
        if (stream.getGrantedAccess() != NetworkFile.Access.READ_ONLY && stream.isDirectory() == false &&
                stream.getWriteCount() > 0) {

            //  DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("  Update stream size=" + stream.getFileSize());

            //  Get the current date/time
            long modifiedTime = 0L;
            if (stream.hasModifyDate())
                modifiedTime = stream.getModifyDate();
            else
                modifiedTime = System.currentTimeMillis();

            //  Check if the modified time is earlier than the file creation date/time
            if (stream.hasCreationDate() && modifiedTime < stream.getCreationDate()) {

                //  Use the creation date/time for the modified date/time
                modifiedTime = stream.getCreationDate();

                //  DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("Close stream using creation date/time for modified date/time");
            }

            //  Update the in-memory stream information
            String parentPath = FileName.getParentPathForStream(stream.getFullName());
            FileState parent = getFileState(parentPath, dbCtx, false);
            StreamInfo sInfo = null;
            int sattr = 0;

            if (parent != null) {

                //  Check if the stream list has been loaded
                StreamInfoList streamList = getStreamList(sess, tree, parentPath);
                if (streamList != null) {

                    //  Find the stream information
                    sInfo = streamList.findStream(stream.getStreamName());
                    if (sInfo != null) {

                        //  Update the stream size
                        sInfo.setSize(stream.getFileSize());
                        sattr += StreamInfo.SetStreamSize;

                        //  DEBUG
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("Updated stream file size");
                    } else {

                        //  DEBUG
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("** Failed to find details for stream " + stream.getStreamName());
                    }
                } else {

                    //  DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("** Failed to get streams list for " + parentPath);
                }
            }

            //  Update the file details for the file stream in the database
            try {

                //  Check if the file strea, details are valid
                if (sInfo == null) {

                    //  Create the stream information
                    sInfo = new StreamInfo();

                    sInfo.setSize(stream.getFileSize());
                    sInfo.setStreamId(stream.getStreamId());

                    sattr += StreamInfo.SetStreamSize;
                }

                //  Set the modify date/time for the stream
                sInfo.setModifyDateTime(System.currentTimeMillis());
                sattr += StreamInfo.SetModifyDate;

                // Set the stream information values to be updated
                sInfo.setStreamInformationFlags(sattr);

                //  Update the stream details
                dbCtx.getDBInterface().setStreamInformation(stream.getDirectoryId(), stream.getFileId(), stream.getStreamId(), sInfo);
            }
            catch (DBException ex) {
                throw new IOException( "Failed to close stream " + stream.getFullName(), ex);
            }
        }
    }

    /**
     * Delete a stream within a file
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param name String
     * @throws IOException If an error occurs.
     */
    protected final void deleteStream(SrvSession sess, TreeConnection tree, String name)
            throws IOException {

        //  Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        //  Get, or create, the file state for main file path and stream
        String filePath = FileName.getParentPathForStream(name);
        FileState fstate = getFileState(filePath, dbCtx, true);
        FileState sstate = getFileState(name, dbCtx, false);

        //  Check if the file is within an active retention period
        if (fstate.hasActiveRetentionPeriod())
            throw new AccessDeniedException("File retention active");

        //  Get the top level file information
        DBFileInfo finfo = getFileDetails(filePath, dbCtx, fstate);

        //  Get the stream list for the top level file
        StreamInfoList streamList = (StreamInfoList) fstate.findAttribute(DBStreamList);
        if (streamList == null)
            streamList = getStreamList(sess, tree, filePath);

        if (streamList == null)
            throw new FileNotFoundException("Stream not found, " + name);

        //  Parse the path string to get the directory, file name and stream name
        String[] paths = FileName.splitPathStream(name);

        //  Find the required stream details
        StreamInfo sInfo = streamList.findStream(paths[2]);
        if (sInfo == null)
            throw new FileNotFoundException("Stream not found, " + name);

        //  Delete the stream record from the database
        try {

            //  Call the file loader to delete the stream data
            dbCtx.getFileLoader().deleteFile(name, sInfo.getFileId(), sInfo.getStreamId());

            //  Delete the stream record
            dbCtx.getDBInterface().deleteStreamRecord(sInfo.getFileId(), sInfo.getStreamId(), dbCtx.isTrashCanEnabled());

            //  Remove the stream information from the in memory list
            streamList.removeStream(sInfo.getName());

            //  Set the streams file state to indicate that it does not exist
            if (sstate != null)
                sstate.setFileStatus(FileStatus.NotExist);
        }
        catch (DBException ex) {
            throw new IOException( "Failed to delete stream " + name, ex);
        }
    }

    /**
     * Load the stream list for the specified file
     *
     * @param fstate FileState
     * @param finfo  DBFileInfo
     * @param dbCtx  DBDeviceContext
     * @param dbLoad boolean
     * @return StreamInfoList
     */
    protected final StreamInfoList loadStreamList(FileState fstate, DBFileInfo finfo, DBDeviceContext dbCtx, boolean dbLoad) {

        //  Check if the stream list has already been loaded
        StreamInfoList sList = (StreamInfoList) fstate.findAttribute(FileState.StreamsList);

        //  If the streams list is not loaded then load it from the database
        if (sList == null && dbLoad == true) {

            //  Load the streams list from the database, if NTFS streams are enabled
            try {

                //  Load the streams list
                sList = dbCtx.getDBInterface().getStreamsList(finfo.getFileId(), DBInterface.StreamInfoLevel.All);

                // Cache the streams list via the parent file state
                if (sList != null)
                    fstate.addAttribute(DBStreamList, sList);
            }
            catch (DBException ex) {
                Debug.println(ex);
            }
        }

        //  Return the streams list
        return sList;
    }

    /**
     * Rename a stream
     *
     * @param sess      SrvSession
     * @param tree      TreeConnection
     * @param oldName   String
     * @param newName   String
     * @param overWrite boolean
     * @throws IOException If an error occurs.
     */
    public void renameStream(SrvSession sess, TreeConnection tree, String oldName, String newName, boolean overWrite)
            throws IOException {
    }

    /**
     * Return the volume information
     *
     * @param ctx DiskDeviceContext
     * @return VolumeInfo
     */
    public VolumeInfo getVolumeInformation(DiskDeviceContext ctx) {

        //  Check if the context has volume information
        VolumeInfo volInfo = ctx.getVolumeInformation();

        if (volInfo == null) {

            //  Create volume information for the filesystem
            volInfo = new VolumeInfo(ctx.getDeviceName());

            //  Add to the device context
            ctx.setVolumeInformation(volInfo);
        }

        //  Check if the serial number is valid
        if (volInfo.getSerialNumber() == 0) {

            //  Generate a random serial number
            volInfo.setSerialNumber(new java.util.Random().nextInt());
        }

        //  Check if the creation date is valid
        if ( !volInfo.hasCreationDateTime()) {

            //  Set the creation date to now
            volInfo.setCreationDateTime(new java.util.Date());
        }

        //  Return the volume information
        return volInfo;
    }

    /**
     * Return the lock manager implementation
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return LockManager
     */
    public LockManager getLockManager(SrvSession sess, TreeConnection tree) {

        // Access the context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
        return dbCtx.getFileStateLockManager();
    }

    /**
     * Return the oplock manager implementation
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return OpLockManager
     */
    public OpLockManager getOpLockManager(SrvSession sess, TreeConnection tree) {

        // Access the context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
        return dbCtx.getFileStateLockManager();
    }

    /**
     * Enable/disable oplock support
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return boolean
     */
    public boolean isOpLocksEnabled(SrvSession sess, TreeConnection tree) {

        // Access the context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
        return dbCtx.isOpLocksEnabled();
    }

    /**
     * Convert a file id to a share relative path
     *
     * @param sess   SrvSession
     * @param tree   TreeConnection
     * @param dirid  int
     * @param fileid int
     * @return String
     * @throws FileNotFoundException File not found
     */
    public String buildPathForFileId(SrvSession sess, TreeConnection tree, int dirid, int fileid)
            throws FileNotFoundException {

        // Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        //  Build an array of folder names working back from the files id
        ArrayList<String> names = new ArrayList<String>(16);

        try {

            //  Loop, walking backwards up the tree until we hit root
            int curFid = fileid;
            int curDid = dirid;

            FileInfo finfo = null;

            do {

                //  Search for the current file in the database
                finfo = dbCtx.getDBInterface().getFileInformation(curDid, curFid, DBInterface.FileInfoLevel.Ids);

                if (finfo != null) {

                    //  Get the filename
                    names.add(finfo.getFileName());

                    //  The directory id becomes the next file id to search for
                    curFid = finfo.getDirectoryId();
                    curDid = -1;
                } else
                    throw new FileNotFoundException("" + curFid);

            } while (curFid > RootDirId);
        }
        catch (DBException ex) {
            Debug.println(ex);
            return null;
        }

        //  Build the path string
        StringBuilder pathStr = new StringBuilder(256);
        pathStr.append(FileName.DOS_SEPERATOR_STR);

        for (int i = names.size() - 1; i >= 0; i--) {
            pathStr.append(names.get(i));
            pathStr.append(FileName.DOS_SEPERATOR_STR);
        }

        //  Remove the trailing slash from the path
        if (pathStr.length() > 0)
            pathStr.setLength(pathStr.length() - 1);

        //  Return the path string
        return pathStr.toString();
    }

    /**
     * Determine if symbolic links are enabled
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return boolean
     */
    public boolean hasSymbolicLinksEnabled(SrvSession sess, TreeConnection tree) {

        //  Access the associated database interface to check if it supports symbolic links
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
        if (dbCtx.getDBInterface().supportsFeature(DBInterface.Feature.SymLinks)) {

            //  Database interface supports symbolic links
            return true;
        }

        //  Symbolic links not supported
        return false;
    }

    /**
     * Read the link data for a symbolic link
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param path String
     * @return String
     * @throws AccessDeniedException Access denied
     * @throws FileNotFoundException File not found
     */
    public String readSymbolicLink(SrvSession sess, TreeConnection tree, String path)
            throws AccessDeniedException, FileNotFoundException {

        //  Access the associated database interface to check if it supports symbolic links
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();
        DBInterface dbInterface = dbCtx.getDBInterface();
        String symLink = null;

        if (dbInterface.supportsFeature(DBInterface.Feature.SymLinks)) {

            //  Get, or create, the file state for the existing file
            FileState fstate = getFileState(path, dbCtx, true);

            //  Get the file id of the existing file
            int fid = fstate.getFileId();
            int dirId = -1;

            if (fid == -1) {

                //  Split the current path string and find the file id of the existing file/directory
                dirId = findParentDirectoryId(dbCtx, path, true);
                if (dirId == -1)
                    throw new FileNotFoundException(path);

                //  Get the file/directory name
                String[] oldPaths = FileName.splitPath(path);
                String fname = oldPaths[1];

                //  Get the file id
                fid = getFileId(path, fname, dirId, dbCtx);
                if (fid == -1)
                    throw new FileNotFoundException(path);

                //  Update the file state
                fstate.setFileId(fid);
            }

            try {

                //  Database interface supports symbolic links, read the symbolic link
                symLink = dbInterface.readSymbolicLink(dirId, fid);
            }
            catch (DBException ex) {
                throw new FileNotFoundException(path + ":" + ex.getMessage());
            }
        }

        //  Return the symbolic link data
        return symLink;
    }

    /**
     * Return the security descriptor length for the specified file
     *
     * @param sess    Server session
     * @param tree    Tree connection
     * @param netFile Network file
     * @return int
     * @throws SMBSrvException SMB error
     */
    public int getSecurityDescriptorLength(SrvSession sess, TreeConnection tree, NetworkFile netFile)
            throws SMBSrvException {

        // Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the file loader supports the security descriptor interface, if so then pass the request to the
        // file loader
        if ( dbCtx.getFileLoader() instanceof SecurityDescriptorInterface) {

            // Pass the request to the file loader
            SecurityDescriptorInterface secDescIface = (SecurityDescriptorInterface) dbCtx.getFileLoader();
            return secDescIface.getSecurityDescriptorLength( sess, tree, netFile);
        }

        // File loader does not support security descriptors
        return 0;
    }

    /**
     * Load a security descriptor for the specified file
     *
     * @param sess    Server session
     * @param tree    Tree connection
     * @param netFile Network file
     * @return Databuffer
     * @throws SMBSrvException SMB error
     */
    public DataBuffer loadSecurityDescriptor(SrvSession sess, TreeConnection tree, NetworkFile netFile)
            throws SMBSrvException {

        // Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the file loader supports the security descriptor interface, if so then pass the request to the
        // file loader
        if ( dbCtx.getFileLoader() instanceof SecurityDescriptorInterface) {

            // Pass the request to the file loader
            SecurityDescriptorInterface secDescIface = (SecurityDescriptorInterface) dbCtx.getFileLoader();
            return secDescIface.loadSecurityDescriptor( sess, tree, netFile);
        }

        // File loader does not support security descriptors
        return null;
    }

    /**
     * Save the security descriptor for the specified file
     *
     * @param sess    Server session
     * @param tree    Tree connection
     * @param netFile Network file
     * @param secInfoFlags int
     * @param secDesc Security descriptor bytes
     * @throws SMBSrvException SMB error
     */
    public void saveSecurityDescriptor(SrvSession sess, TreeConnection tree, NetworkFile netFile, int secInfoFlags, DataBuffer secDesc)
            throws SMBSrvException {

        // Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) tree.getContext();

        // Check if the file loader supports the security descriptor interface, if so then pass the request to the
        // file loader
        if ( dbCtx.getFileLoader() instanceof SecurityDescriptorInterface) {

            // Pass the request to the file loader
            SecurityDescriptorInterface secDescIface = (SecurityDescriptorInterface) dbCtx.getFileLoader();
            secDescIface.saveSecurityDescriptor( sess, tree, netFile, secInfoFlags, secDesc);
        }
        else {

            // File loader does not support security descriptors
            throw new SMBSrvException(SMBStatus.NTNotImplemented);
        }
    }

    /**
     * Post close the file, called after the protocol layer has sent the close response to the client.
     *
     * @param sess    Server session
     * @param tree    Tree connection.
     * @param netFile Network file context.
     * @throws IOException If an error occurs.
     */
    public void postCloseFile(SrvSession sess, TreeConnection tree, NetworkFile netFile)
            throws IOException
    {
        // Close the file
        doCloseFile(sess, tree, netFile);
    }
}
