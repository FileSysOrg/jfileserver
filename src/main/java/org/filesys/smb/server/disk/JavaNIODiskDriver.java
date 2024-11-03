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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.core.DeviceContext;
import org.filesys.server.core.DeviceContextException;
import org.filesys.server.filesys.*;
import org.filesys.server.filesys.AccessDeniedException;
import org.filesys.server.filesys.FileSystem;
import org.filesys.smb.server.SMBSrvSession;
import org.filesys.util.WildCard;
import org.springframework.extensions.config.ConfigElement;


/**
 * Disk interface implementation that uses the java.io.File class.
 *
 * @author gkspencer
 */
public class JavaNIODiskDriver implements DiskInterface {

    //	SMB date used as the creation date/time for all files
    protected static long _globalCreateDate = System.currentTimeMillis();

    // Background thread used for long running actions such as large file delete
    protected ExecutorService m_fileActionsExecutor = Executors.newSingleThreadExecutor();

    // Trashcan filename prefix
    protected static final String TRASHCAN_NAME_PREFIX = "trash_";

    // Random number generator for trashcan file names
    private static Random _trashCanIdGenerator = new Random();

    /**
     * Class constructor
     */
    public JavaNIODiskDriver() {
        super();
    }

    /**
     * Build the file information for the specified file/directory, if it exists.
     *
     * @param path    String
     * @param relPath String
     * @return FileInfo
     */
    protected FileInfo buildFileInformation(String path, String relPath) {

        //  Now split the path up again !
        String[] pathStr = FileName.splitPath(path, java.io.File.separatorChar);
        FileInfo finfo = null;

        try {

            // Build the path
            Path curPath = null;

            if ( pathStr[1] != null)
                curPath = Paths.get( pathStr[0], pathStr[1]);
            else
                curPath = Paths.get( pathStr[0]);

            //  Get the file/directory information
            if ( curPath != null) {

                if (Files.exists(curPath, LinkOption.NOFOLLOW_LINKS)) {

                    //  Create a file information object for the file
                    int fattr = 0;
                    long falloc = 0L;
                    long flen = 0L;

                    String fname = curPath.getFileName().toString();

                    if (Files.isDirectory(curPath, LinkOption.NOFOLLOW_LINKS)) {

                        // Set the directory attribute
                        fattr = FileAttribute.Directory;

                        // Check if the diretory should be hidden
                        if (Files.isHidden(curPath))
                            fattr += FileAttribute.Hidden;
                    }
                    else {

                        //	Set the file length
                        flen = Files.size(curPath);
                        falloc = (flen + 512L) & 0xFFFFFFFFFFFFFE00L;

                        //	Check if the file/folder is read-only
                        if (Files.isWritable(curPath) == false)
                            fattr += FileAttribute.ReadOnly;

                        //	Check for common hidden files
                        if (Files.isHidden(curPath))
                            fattr += FileAttribute.Hidden;
                        else if (fname.equalsIgnoreCase("Desktop.ini") ||
                                fname.equalsIgnoreCase("Thumbs.db") ||
                                fname.startsWith("."))
                            fattr += FileAttribute.Hidden;
                    }

                    //  Create the file information object
                    finfo = new FileInfo(pathStr[1], flen, fattr);
                    finfo.setAllocationSize(falloc);

                    // Generate the file id from the relative path
                    finfo.setFileId(relPath.toString().hashCode());

                    // Set the file timestamps
                    FileTime modifyDate = Files.getLastModifiedTime(curPath, LinkOption.NOFOLLOW_LINKS);
                    long modifyDateMs = modifyDate.toMillis();

                    finfo.setModifyDateTime(modifyDateMs);
                    finfo.setChangeDateTime(modifyDateMs);

                    long dummyCreate = getGlobalCreateDateTime();

                    if (dummyCreate > modifyDateMs)
                        dummyCreate = modifyDateMs;
                    finfo.setCreationDateTime(dummyCreate);
                }
            }
        }
        catch ( Exception ex) {
        }

        // Return the file information, or null if the path does not exist
        return finfo;
    }

    /**
     * Close the specified file
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param file Network file details
     * @exception IOException I/O error
     */
    public void closeFile(SrvSession sess, TreeConnection tree, NetworkFile file)
            throws java.io.IOException {

        //	Close the file
        file.closeFile();

        //	Check if the file/directory is marked for delete
        if (file.hasDeleteOnClose()) {

            //	Check for a file or directory
            if (file.isDirectory())
                deleteDirectory(sess, tree, file.getFullName());
            else
                deleteFile(sess, tree, file.getFullName());
        }
    }

    /**
     * Create a new directory
     *
     * @param sess   Session details
     * @param tree   Tree connection
     * @param params Directory parameters
     * @exception IOException I/O error
     */
    public void createDirectory(SrvSession sess, TreeConnection tree, FileOpenParams params)
            throws java.io.IOException {

        //  Get the full path for the new directory
        String dirname = FileName.buildPath(tree.getContext().getDeviceName(), params.getPath(), null, java.io.File.separatorChar);

        //  Create the new directory
        File newDir = new File(dirname);
        if (newDir.mkdir() == false)
            throw new IOException("Failed to create directory " + dirname);
    }

    /**
     * Create a new file
     *
     * @param sess   Session details
     * @param tree   Tree connection
     * @param params File open parameters
     * @return NetworkFile
     * @exception IOException I/O error
     */
    public NetworkFile createFile(SrvSession sess, TreeConnection tree, FileOpenParams params)
            throws java.io.IOException {

        //  Get the full path for the new file
        DeviceContext ctx = tree.getContext();
        Path newPath = Paths.get( mapPath( ctx.getDeviceName(), params.getPath()));

        //  Check if the file already exists
        if ( Files.exists( newPath, LinkOption.NOFOLLOW_LINKS))
            throw new FileExistsException();

        //  Create the new file
        Files.createFile( newPath);

        //  Create a Java network file
        JavaNIONetworkFile netFile = new JavaNIONetworkFile( newPath, params.getPath());

        netFile.setGrantedAccess(NetworkFile.Access.READ_WRITE);
        netFile.setFullName(params.getPath());

        //  Check if the file is a hidden file
        if (Files.isHidden(newPath))
            netFile.setAttributes(FileAttribute.Hidden);
        else {

            // Get the file name
            String fname = FileName.getFileNamePart(params.getPath());

            if (fname != null && (fname.equalsIgnoreCase("Desktop.ini") ||
                    fname.equalsIgnoreCase("Thumbs.db") ||
                    fname.startsWith(".")))
                netFile.setAttributes(FileAttribute.Hidden);
        }

        //  Return the network file
        return netFile;
    }

    /**
     * Delete a directory
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param dir  Path of directory to delete
     * @exception IOException I/O error
     */
    public void deleteDirectory(SrvSession sess, TreeConnection tree, String dir)
            throws java.io.IOException {

        //  Get the full path for the directory
        DeviceContext ctx = tree.getContext();
        Path dirPath = Paths.get( FileName.buildPath(ctx.getDeviceName(), dir, null, java.io.File.separatorChar));

        //  Check if the directory exists, and it is a directory
        if ( Files.exists( dirPath) && Files.isDirectory( dirPath)) {

            //	Delete the directory
            try {
                Files.delete(dirPath);
            }
            catch ( java.nio.file.DirectoryNotEmptyException ex) {
                throw new org.filesys.server.filesys.DirectoryNotEmptyException( "Directory not empty");
            }
        }

        //  If the path does not exist then try and map it to a real path, there may be case differences
        else if ( Files.exists( dirPath) == false) {

            //  Map the path to a real path
            String mappedPath = mapPath(ctx.getDeviceName(), dir);

            if (mappedPath != null) {

                //  Check if the path is a directory
                dirPath = Paths.get( mappedPath);

                if ( Files.isDirectory( dirPath)) {

                    //	Delete the directory
                    try {
                        Files.delete(dirPath);
                    }
                    catch ( java.nio.file.DirectoryNotEmptyException ex) {
                        throw new org.filesys.server.filesys.DirectoryNotEmptyException( "Directory not empty");
                    }
                }
            }
        }
    }

    /**
     * Delete a file
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param name Name of file to delete
     * @exception IOException I/O error
     */
    public void deleteFile(SrvSession sess, TreeConnection tree, String name)
            throws java.io.IOException {

        //  Get the full path for the file
        JavaNIODeviceContext ctx = (JavaNIODeviceContext) tree.getContext();
        Path filePath = Paths.get( FileName.buildPath(ctx.getDeviceName(), name, null, java.io.File.separatorChar));

        //  Check if the file exists, and it is a file
        if ( Files.exists( filePath) && Files.isDirectory( filePath) == false) {

            // If the file size is below the large file threshold then delete the file
            if ( Files.size( filePath) < ctx.getLargeFileSize()) {

                // Delete the file
                Files.delete( filePath);
            }
            else {

                // Get the file name from the path
                String[] paths = FileName.splitPath( name);
                String fileName = null;

                if ( paths[1] != null)
                    fileName = paths[1];

                // Create a unique name for the file in the trashcan folder
                StringBuilder trashName = new StringBuilder();
                trashName.append( ctx.getTrashFolder().getAbsolutePath());
                trashName.append( File.separatorChar);
                trashName.append( TRASHCAN_NAME_PREFIX);
                trashName.append( sess.getSessionId());
                trashName.append( "_");
                trashName.append( _trashCanIdGenerator.nextLong());

                final Path trashPath = Paths.get( trashName.toString());

                // DEBUG
                if ( ctx.hasDebug())
                    Debug.println("Delete file " + name + " via trashcan");

                try {
                    // Rename the file into the trashcan folder
                    Files.move( filePath, trashPath, StandardCopyOption.ATOMIC_MOVE);

                    // Queue a delete to the background thread
                    m_fileActionsExecutor.execute( new Runnable() {
                        public void run() {
                            try {
                                Files.delete(trashPath);
                            }
                            catch ( Exception ex) {
                            }
                        }
                    });
                }
                catch ( Exception ex) {

                    // Failed to move the file to the trashcan folder, just delete it where it is
                    Files.delete( filePath);
                }
            }
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

        //  Get the full path for the file
        DeviceContext ctx = tree.getContext();
        Path filePath = Paths.get( FileName.buildPath(ctx.getDeviceName(), name, null, java.io.File.separatorChar));

        if ( Files.exists( filePath, LinkOption.NOFOLLOW_LINKS)) {

            //	Check if the path is a file or directory
            if ( Files.isDirectory( filePath, LinkOption.NOFOLLOW_LINKS))
                return FileStatus.DirectoryExists;
            else
                return FileStatus.FileExists;
        }

        // Map the path, and re-check
        try {
            String mappedPath = mapPath(ctx.getDeviceName(), name);

            if ( mappedPath != null) {
                filePath = Paths.get( mappedPath);

                if ( Files.exists( filePath, LinkOption.NOFOLLOW_LINKS)) {

                    //	Check if the path is a file or directory
                    if ( Files.isDirectory( filePath, LinkOption.NOFOLLOW_LINKS))
                        return FileStatus.DirectoryExists;
                    else
                        return FileStatus.FileExists;
                }
            }
        }
        catch ( Exception ex) {
        }

        //  Path does not exist or is not a file
        return FileStatus.NotExist;
    }

    /**
     * Flush buffered data for the specified file
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param file Network file
     * @exception IOException I/O error
     */
    public void flushFile(SrvSession sess, TreeConnection tree, NetworkFile file)
            throws java.io.IOException {

        //	Flush the file
        file.flushFile();
    }

    /**
     * Return file information about the specified file
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param name File name
     * @return SMBFileInfo
     * @exception IOException I/O error
     */
    public FileInfo getFileInformation(SrvSession sess, TreeConnection tree, String name)
            throws java.io.IOException {

        //  Get the full path for the file/directory
        DeviceContext ctx = tree.getContext();
        String path = FileName.buildPath(ctx.getDeviceName(), name, null, java.io.File.separatorChar);

        //  Build the file information for the file/directory
        FileInfo info = buildFileInformation(path, name);

        if (info != null)
            return info;

        //  Try and map the path to a real path
        String mappedPath = mapPath(ctx.getDeviceName(), name);
        if (mappedPath != null)
            return buildFileInformation(mappedPath, name);

        //  Looks like a bad path
        return null;
    }

    /**
     * Determine if the disk device is read-only.
     *
     * @param sess Session details
     * @param ctx  Device context
     * @return true if the device is read-only, else false
     * @exception IOException I/O error
     */
    public boolean isReadOnly(SrvSession sess, DeviceContext ctx)
            throws java.io.IOException {

        //  Check if the directory exists, and it is a directory
        Path rootPath = Paths.get( ctx.getDeviceName());

        if ( Files.exists( rootPath) == false || Files.isDirectory( rootPath) == false)
            throw new FileNotFoundException(ctx.getDeviceName());

        // Check if the root path is writable
        return ! Files.isWritable( rootPath);
    }

    /**
     * Map the input path to a real path, this may require changing the case of various parts of the
     * path.
     *
     * @param path Share relative path
     * @return Real path on the local filesystem
     * @exception FileNotFoundException The path could not be mapped to a real path.
     * @exception PathNotFoundException Part of the path is not valid
     */
    protected final String mapPath(String path)
            throws java.io.FileNotFoundException, PathNotFoundException {
        return mapPath("", path);
    }

    /**
     * Map the input path to a real path, this may require changing the case of various parts of the
     * path. The base path is not checked, it is assumed to exist.
     *
     * @param base String
     * @param path String
     * @return String
     * @exception FileNotFoundException The path could not be mapped to a real path.
     * @exception PathNotFoundException Part of the path is not valid
     */
    protected final String mapPath(String base, String path)
            throws java.io.FileNotFoundException, PathNotFoundException {

        //  Split the path string into seperate directory components
        String pathCopy = path;
        if (pathCopy.length() > 0 && pathCopy.startsWith( FileName.DOS_SEPERATOR_STR))
            pathCopy = pathCopy.substring(1);

        StringTokenizer token = new StringTokenizer(pathCopy, "\\/");
        int tokCnt = token.countTokens();

        //  The mapped path string, if it can be mapped
        String mappedPath = null;

        if (tokCnt > 0) {

            //  Allocate an array to hold the directory names
            String[] dirs = new String[token.countTokens()];

            //  Get the directory names
            int idx = 0;
            while (token.hasMoreTokens())
                dirs[idx++] = token.nextToken();

            //  Check if the path ends with a directory or file name, ie. has a trailing '\' or not
            int maxDir = dirs.length;

            if (path.endsWith( FileName.DOS_SEPERATOR_STR) == false) {

                //  Ignore the last token as it is a file name
                maxDir--;
            }

            //  Build up the path string and validate that the path exists at each stage.
            StringBuffer pathStr = new StringBuffer(base);
            if (base.endsWith(java.io.File.separator) == false)
                pathStr.append(java.io.File.separator);

            int lastPos = pathStr.length();
            idx = 0;
            File lastDir = null;
            if (base != null && base.length() > 0)
                lastDir = new File(base);
            File curDir = null;

            while (idx < maxDir) {

                //  Append the current directory to the path
                pathStr.append(dirs[idx]);
                pathStr.append(java.io.File.separator);

                //  Check if the current path exists
                curDir = new File(pathStr.toString());

                if (curDir.exists() == false) {

                    //  Check if there is a previous directory to search
                    if (lastDir == null)
                        throw new PathNotFoundException();

                    //  Search the current path for a matching directory, the case may be different
                    String[] fileList = lastDir.list();
                    if (fileList == null || fileList.length == 0)
                        throw new PathNotFoundException();

                    int fidx = 0;
                    boolean foundPath = false;

                    while (fidx < fileList.length && foundPath == false) {

                        //  Check if the current file name matches the required directory name
                        if (fileList[fidx].equalsIgnoreCase(dirs[idx])) {

                            //  Use the current directory name
                            pathStr.setLength(lastPos);
                            pathStr.append(fileList[fidx]);
                            pathStr.append(java.io.File.separator);

                            //  Check if the path is valid
                            curDir = new File(pathStr.toString());
                            if (curDir.exists()) {
                                foundPath = true;
                                break;
                            }
                        }

                        //  Update the file name index
                        fidx++;
                    }

                    //  Check if we found the required directory
                    if (foundPath == false)
                        throw new PathNotFoundException();
                }

                //  Set the last valid directory file
                lastDir = curDir;

                //  Update the end of valid path location
                lastPos = pathStr.length();

                //  Update the current directory index
                idx++;
            }

            //  Check if there is a file name to be added to the mapped path
            if (path.endsWith( FileName.DOS_SEPERATOR_STR) == false) {

                //  Map the file name
                String[] fileList = lastDir.list();
                String fileName = dirs[dirs.length - 1];

                //	Check if the file list is valid, if not then the path is not valid
                if (fileList == null)
                    throw new FileNotFoundException(path);

                //	Search for the required file
                idx = 0;
                boolean foundFile = false;

                while (idx < fileList.length && foundFile == false) {
                    if (fileList[idx].compareTo(fileName) == 0)
                        foundFile = true;
                    else
                        idx++;
                }

                //  Check if we found the file name, if not then do a case insensitive search
                if (foundFile == false) {

                    //  Search again using a case insensitive search
                    idx = 0;

                    while (idx < fileList.length && foundFile == false) {
                        if (fileList[idx].equalsIgnoreCase(fileName)) {
                            foundFile = true;
                            fileName = fileList[idx];
                        }
                        else
                            idx++;
                    }
                }

                //  Append the file name
                pathStr.append(fileName);
            }

            //  Set the new path string
            mappedPath = pathStr.toString();

            // Check for a Netware style path and remove the leading slash
            if (File.separator.equals( FileName.DOS_SEPERATOR_STR) && mappedPath.startsWith( FileName.DOS_SEPERATOR_STR) && mappedPath.indexOf(':') > 1)
                mappedPath = mappedPath.substring(1);
        }

        //  Return the mapped path string, if successful.
        return mappedPath;
    }

    /**
     * Open a file
     *
     * @param sess   Session details
     * @param tree   Tree connection
     * @param params File open parameters
     * @return NetworkFile
     * @exception IOException I/O error
     */
    public NetworkFile openFile(SrvSession sess, TreeConnection tree, FileOpenParams params)
            throws java.io.IOException {

        //  Create a Java network file
        DeviceContext ctx = tree.getContext();
        Path filePath = Paths.get( FileName.buildPath(ctx.getDeviceName(), params.getPath(), null, java.io.File.separatorChar));

        if ( Files.exists( filePath) == false) {

            //  Try and map the file name string to a local path
            String mappedPath = mapPath(ctx.getDeviceName(), params.getPath());
            if (mappedPath == null)
                throw new java.io.FileNotFoundException(filePath.toString());

            //  Create the file object for the mapped file and check if the file exists
            filePath = Paths.get( mappedPath);

            if ( Files.exists( filePath) == false)
                throw new FileNotFoundException(filePath.toString());
        }

        //	Check if the file is read-only and write access has been requested
        if ( Files.isWritable( filePath) == false && (params.isReadWriteAccess() || params.isWriteOnlyAccess()))
            throw new AccessDeniedException("File " + filePath.toString() + " is read-only");

        //	Create the network file object for the opened file/folder
        NetworkFile netFile = new JavaNIONetworkFile(filePath, params.getPath());

        if (params.isReadOnlyAccess())
            netFile.setGrantedAccess(NetworkFile.Access.READ_ONLY);
        else
            netFile.setGrantedAccess(NetworkFile.Access.READ_WRITE);

        // Save the original access mask from the request
        netFile.setAccessMask( params.getAccessMode());

        // Set the share relative path
        netFile.setFullName(params.getPath());

        //  Check if the file is actually a directory
        if ( Files.isDirectory( filePath))
            netFile.setAttributes(FileAttribute.Directory);
        else {

            //	Check for common hidden files
            if (Files.isHidden(filePath))
                netFile.setAttributes(FileAttribute.Hidden);
            else {

                // Get the file name
                String fname = FileName.getFileNamePart(params.getPath());

                if (fname != null && (fname.equalsIgnoreCase("Desktop.ini") ||
                        fname.equalsIgnoreCase("Thumbs.db") ||
                        fname.startsWith(".")))
                    netFile.setAttributes(FileAttribute.Hidden);
            }
        }

        //  Return the network file
        return netFile;
    }

    /**
     * Read a block of data from a file
     *
     * @param sess    Session details
     * @param tree    Tree connection
     * @param file    Network file
     * @param buf     Buffer to return data to
     * @param bufPos  Starting position in the return buffer
     * @param siz     Maximum size of data to return
     * @param filePos File offset to read data
     * @return Number of bytes read
     * @exception IOException I/O error
     */
    public int readFile(SrvSession sess, TreeConnection tree, NetworkFile file, byte[] buf, int bufPos, int siz, long filePos)
            throws java.io.IOException {

        //	Check if the file is a directory
        if (file.isDirectory())
            throw new AccessDeniedException();

        //  Read the file
        int rdlen = file.readFile(buf, siz, bufPos, filePos);

        //  If we have reached end of file return a zero length read
        if (rdlen == -1)
            rdlen = 0;

        //  Return the actual read length
        return rdlen;
    }

    /**
     * Rename a file
     *
     * @param sess    Session details
     * @param tree    Tree connection
     * @param oldName Existing file name
     * @param newName New file name
     * @param netFile NetworkFile for handle based rename, or null for path based rename
     * @exception IOException I/O error
     */
    public void renameFile(SrvSession sess, TreeConnection tree, String oldName, String newName, NetworkFile netFile)
            throws java.io.IOException {

        //  Get the full path for the existing file and the new file name
        DeviceContext ctx = tree.getContext();

        Path oldPath = Paths.get( FileName.buildPath(ctx.getDeviceName(), oldName, null, java.io.File.separatorChar));
        Path newPath = Paths.get( FileName.buildPath(ctx.getDeviceName(), newName, null, java.io.File.separatorChar));

        //	Check if the current file/directory exists
        if ( Files.exists( oldPath) == false)
            throw new FileNotFoundException("Rename file, does not exist " + oldName);

        //	Check if the new file/directory exists
        // Check if we are just changing the case of the file/folder name, in which case it will exist
        if ( Files.exists(newPath) && oldName.equalsIgnoreCase( newName) == false)
            throw new FileExistsException("Rename file, path exists " + newName);

        //  Rename the file
        try {
            Files.move( oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
        }
        catch ( Exception ex) {
            throw new IOException("Rename " + oldPath + " to " + newPath + " failed");
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
     * @exception IOException I/O error
     */
    public long seekFile(SrvSession sess, TreeConnection tree, NetworkFile file, long pos, int typ)
            throws java.io.IOException {

        //  Check that the network file is our type
        return file.seekFile(pos, typ);
    }

    /**
     * Set file information
     *
     * @param sess Session details
     * @param tree Tree connection
     * @param name File name
     * @param info File information to be set
     * @exception IOException I/O error
     */
    public void setFileInformation(SrvSession sess, TreeConnection tree, String name, FileInfo info)
            throws java.io.IOException {

        //	Check if the modify date/time should be updated
        if (info.hasSetFlag(FileInfo.SetModifyDate)) {

            //	Build the path to the file
            DeviceContext ctx = tree.getContext();
            Path filePath = Paths.get( mapPath( ctx.getDeviceName(), name));

            //	Update the file/folder modify date/time
            Files.setLastModifiedTime( filePath, FileTime.fromMillis( info.getModifyDateTime()));

            // Update the associated network file, too, if possible
            if (info.hasNetworkFile())
                info.getNetworkFile().setModifyDate(info.getModifyDateTime());
        }
    }

    /**
     * Start a file search
     *
     * @param sess        Session details
     * @param tree        Tree connection
     * @param searchPath  Search path, may include wildcards
     * @param attrib      Search attributes
     * @param searchFlags Search flags
     * @return SearchContext
     * @throws FileNotFoundException File not found
     */
    public SearchContext startSearch(SrvSession sess, TreeConnection tree, String searchPath, int attrib, EnumSet<SearchFlags> searchFlags)
            throws java.io.FileNotFoundException {

        //  Create the full search path string
        String path = FileName.buildPath(tree.getContext().getDeviceName(), null, searchPath, File.separatorChar);
        JavaNIOSearchContext ctx = null;

        try {

            //	Map the path, this may require changing the case on some or all path components
            path = mapPath(path);

            // Split the search path to get the share relative path
            String[] paths = FileName.splitPath(path, File.separatorChar);

            //	DEBUG
            if (Debug.EnableInfo && sess != null && sess.hasDebug(SMBSrvSession.Dbg.SEARCH))
                sess.debugPrintln("  Start search path=" + path + ", relPath=" + paths[0]);

            if (paths[1] != null && WildCard.containsWildcards(paths[1]) == false) {

                //  Path may be a file
                Path singlePath = Paths.get(paths[0], paths[1]);

                if (Files.exists( singlePath, LinkOption.NOFOLLOW_LINKS) == false)
                    throw new java.io.FileNotFoundException( singlePath.toString());

                // Create a search context for a single file/folder search
                ctx = new JavaNIOSearchContext();
                ctx.initSinglePathSearch( singlePath);
            }
            else {

                //  Wildcard search of a directory
                String root = paths[0];

                if (root.endsWith(":"))
                    root = root + FileName.DOS_SEPERATOR;

                Path rootPath = Paths.get( root);

                if ( Files.isDirectory( rootPath, LinkOption.NOFOLLOW_LINKS)) {

                    //  Check if there is a file spec, if not then the search is for the directory only
                    if (paths[1] == null) {

                        // Create a search context for a single file/folder search
                        ctx = new JavaNIOSearchContext();
                        ctx.initSinglePathSearch( rootPath);
                    }
                    else {

                        // Create a wildcard folder search
                        ctx = new JavaNIOSearchContext();

                        try {
                            ctx.initWildcardSearch(rootPath, attrib, new WildCard(paths[1], false));
                        }
                        catch ( IOException ex) {
                            throw new FileNotFoundException( rootPath.toString());
                        }
                    }
                }
            }

            // Set the relative path for the search
            if ( ctx != null)
                ctx.setRelativePath(paths[0]);
        }
        catch (PathNotFoundException ex) {
            throw new FileNotFoundException();
        }

        // Return the search context
        return ctx;
    }

    /**
     * Truncate a file to the specified size
     *
     * @param sess Server session
     * @param tree Tree connection
     * @param file Network file details
     * @param siz  New file length
     * @exception IOException I/O error
     */
    public void truncateFile(SrvSession sess, TreeConnection tree, NetworkFile file, long siz)
            throws IOException {

        // Check for a truncate to zero length of a large file
        if ( siz == 0) {

            // Access the device context
            JavaNIODeviceContext ctx = (JavaNIODeviceContext) tree.getContext();

            if ( file.getFileSize() >= ctx.getLargeFileSize()) {

                // DEBUG
                if ( ctx.hasDebug())
                    Debug.println("Truncate large file via delete - " + file.getFullName());

                // Close the existing file
                file.closeFile();

                // Delete the existing file by moving to the trashcan folder and deleting via a background thread
                deleteFile( sess, tree, file.getFullName());

                // Create a new zero length file with the original name
                file.openFile( true);
                return;
            }
        }

        //	Truncate or extend the file
        file.truncateFile(siz);
        file.flushFile();
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
     * @exception IOException I/O error
     */
    public int writeFile(SrvSession sess, TreeConnection tree, NetworkFile file, byte[] buf, int bufoff, int siz, long fileoff)
            throws java.io.IOException {

        //	Check if the file is a directory
        if (file.isDirectory())
            throw new AccessDeniedException();

        //	Write the data to the file
        file.writeFile(buf, siz, bufoff, fileoff);

        //  Return the actual write length
        return siz;
    }

    /**
     * Parse and validate the parameter string and create a device context for this share
     *
     * @param shareName String
     * @param args      ConfigElement
     * @return DeviceContext
     * @throws DeviceContextException Error creating the device context
     */
    public DeviceContext createContext(String shareName, ConfigElement args)
            throws DeviceContextException {

        // Parse the configuration and return the device context for this share
        JavaNIODeviceContext ctx = new JavaNIODeviceContext( shareName, args);

        // If the trashcan folder is configured then check if there are any trash files left over from a previous
        // server run
        if ( ctx.hasTrashFolder()) {

            // Get the trashcan folder
            Path trashPath = Paths.get( ctx.getTrashFolder().getAbsolutePath());

            // Search for any trash files
            try (DirectoryStream<Path> trashStream = Files.newDirectoryStream( trashPath, JavaNIODiskDriver.TRASHCAN_NAME_PREFIX + "*")) {

                // Queue trash files for delete
                for (final Path trashFile : trashStream) {

                    // DEBUG
                    if ( ctx.hasDebug())
                        Debug.println("Queue trash file for delete - " + trashFile.getFileName());

                    // Queue a delete to the background thread
                    m_fileActionsExecutor.execute(new Runnable() {
                        public void run() {
                        try {
                            Files.delete(trashFile);
                        }
                        catch (Exception ex) {
                        }
                        }
                    });
                }
            } catch (IOException ex) {
            }
        }

        // Return the device context
        return ctx;
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
     * Return the global file creation date/time
     *
     * @return long
     */
    public final static long getGlobalCreateDateTime() {
        return _globalCreateDate;
    }
}
