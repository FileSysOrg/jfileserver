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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;

import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.auth.ClientInfo;
import org.filesys.server.core.DeviceContext;
import org.filesys.server.filesys.*;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.db.DBDeviceContext;
import org.filesys.server.filesys.db.DBInterface;
import org.filesys.server.filesys.db.DirectoryNetworkFile;
import org.filesys.server.filesys.db.LocalDataNetworkFile;
import org.springframework.extensions.config.ConfigElement;


/**
 * Simple File Loader Class
 *
 * <p>The simple file loader class maps the file load/store requests to files within the local filesystem.
 *
 * @author gkspencer
 */
public class SimpleFileLoader implements FileLoader, NamedFileLoader {

    // Local path that the virtual filesystem is mapped to
    private String m_rootPath;

    // Filesystem is caseless
    private boolean m_fsCaseless = true;

    // Enable debug output
    private boolean m_debug;

    // Enable setting of the file owner, if there is a logged on user name (only available when using Kerberos authentication)
    private boolean m_setOwner = false;

    /**
     * Default constructor
     */
    public SimpleFileLoader() {
    }

    /**
     * Return the database features required by this file loader. Return zero if no database features
     * are required by the loader.
     *
     * @return EnumSet&lt;Feature&gt;
     */
    public EnumSet<DBInterface.Feature> getRequiredDBFeatures() {

        //	No database features required
        return EnumSet.noneOf( DBInterface.Feature.class);
    }

    /**
     * Determine if the file loader is online
     *
     * @return boolean
     */
    public boolean isOnline() {
        return true;
    }

    /**
     * Check if the filesystem that the loader is using is caseless
     *
     * @return boolean
     */
    protected final boolean isCaseLess() { return m_fsCaseless; }

    /**
     * Check if the file data or folder entry exists for a file/folder
     *
     * @param path String
     * @return FileStatus
     */
    public FileStatus fileExists(String path) {

        // Get the local path to the file/folder
        String fullName = FileName.buildPath(getRootPath(), path, null, java.io.File.separatorChar);
        Path fullPath = Paths.get( fullName);

        if ( Files.exists( fullPath)) {

            // Check if the path is to a file or folder
            if ( Files.isDirectory( fullPath))
                return FileStatus.DirectoryExists;
            else
                return FileStatus.FileExists;
        }

        // Path does not exist
        return FileStatus.NotExist;
    }

    /**
     * Open/create a file
     *
     * @param params FileOpenParams
     * @param fid    int
     * @param stid   int
     * @param did    int
     * @param create boolean
     * @param dir    boolean
     * @throws IOException Failed to open the file
     * @throws FileNotFoundException File not found
     */
    public NetworkFile openFile(FileOpenParams params, int fid, int stid, int did, boolean create, boolean dir)
            throws IOException, FileNotFoundException {

        //  Get the full path for the new file
        String fullName = FileName.buildPath(getRootPath(), params.getPath(), null, java.io.File.separatorChar);

        //	DEBUG
        if (m_debug)
            Debug.println("SimpleFileLoader.openFile() fname=" + params.getPath() + ", fid=" + fid + ", did=" + did + ", fullName=" + fullName);

        //  Check if the file exists
        File file = new File(fullName);
        if (file.exists() == false) {

            //  Try and map the file name string to a local path
            String mappedPath = FileName.mapPath(getRootPath(), params.getPath(), isCaseLess());
            if (mappedPath == null && create == false)
                throw new FileNotFoundException("File does not exist, " + params.getPath());

            //  Create the file object for the mapped file and check if the file exists
            file = new File(mappedPath);
//            if (file.exists() == false && create == false)
//                throw new FileNotFoundException("File does not exist, " + params.getPath());

            //	Set the new full path
            fullName = mappedPath;
        }

        //  Create the new file, if create is enabled
        if (create) {
            FileWriter newFile = new FileWriter(file);
            newFile.close();

            // Set the file owner, if available
            if ( m_setOwner && params.hasClientInformation()) {

                // Set the file owner
                Path filePath = Paths.get(fullName);
                setOwner( filePath, params.getClientInformation());
            }
        }

        // Check for a file or folder
        NetworkFile netFile = null;

        if ( dir == false) {

            //  Create a Java network file
            file = new File(fullName);
            netFile = new LocalDataNetworkFile(params.getPath(), fid, did, file);
            netFile.setGrantedAccess(NetworkFile.Access.READ_WRITE);
        }
        else {

            // Create a folder network file
            netFile = new DirectoryNetworkFile( params.getPath(), fid, did);
        }

        //  Return the network file
        return netFile;
    }

    /**
     * Close the network file
     *
     * @param sess    SrvSession
     * @param netFile NetworkFile
     * @throws IOException Failed to close the file
     */
    public void closeFile(SrvSession sess, NetworkFile netFile)
            throws IOException {

        //	Close the file
        netFile.closeFile();
    }

    /**
     * Delete a file
     *
     * @param fname String
     * @param fid   int
     * @param stid  int
     * @throws IOException Failed to delete the file
     */
    public void deleteFile(String fname, int fid, int stid)
            throws IOException {

        //	DEBUG
        if (m_debug)
            Debug.println("SimpleFileLoader.deleteFile() fname=" + fname + ", fid=" + fid);

        //  Get the full path for the file
        String name = FileName.buildPath(getRootPath(), fname, null, java.io.File.separatorChar);
        Path filePath = Paths.get( name);

        //  Check if the file exists, and it is a file
        if ( Files.exists( filePath) && Files.isDirectory( filePath) == false) {

            // Delete the file
            Files.delete( filePath);
        }
    }

    /**
     * Create a directory
     *
     * @param params FileOpenParams
     * @param fid int
     * @param parentId int
     * @throws IOException Failed to create the directory
     */
    public void createDirectory(FileOpenParams params, int fid, int parentId)
            throws IOException {

        //	DEBUG
        if (m_debug)
            Debug.println("SimpleFileLoader.createDirectory() dir=" + params.getPath() + ", fid=" + fid);

        //  Get the full path for the new directory
        String dirName = FileName.buildPath(getRootPath(), params.getPath(), null, java.io.File.separatorChar);

        //  Create the new directory
        File newDir = new File(dirName);

        if (newDir.mkdir() == false)
            throw new IOException("Failed to create directory " + dirName);

        // Set the file owner, if available
        if ( m_setOwner && params.hasClientInformation()) {

            // Set the file owner
            Path filePath = Paths.get(dirName);
            setOwner( filePath, params.getClientInformation());
        }
    }

    /**
     * Delete a directory
     *
     * @param dir String
     * @param fid int
     * @throws IOException Failed to delete the directory
     */
    public void deleteDirectory(String dir, int fid)
            throws IOException {

        //	DEBUG
        if (m_debug)
            Debug.println("SimpleFileLoader.deleteDirectory() dir=" + dir + ", fid=" + fid);

        //  Get the full path for the directory
        String dirname = FileName.buildPath(getRootPath(), dir, null, java.io.File.separatorChar);
        Path dirPath = Paths.get( dirname);

        //  Check if the directory exists, and it is a directory
        if ( Files.exists( dirPath) && Files.isDirectory( dirPath)) {

            //	Delete the directory
            try {
                Files.delete(dirPath);
            }
            catch ( java.nio.file.DirectoryNotEmptyException ex) {
                throw new org.filesys.server.filesys.DirectoryNotEmptyException( "Directory not empty - " + dirPath);
            }
        }

        //  If the path does not exist then try and map it to a real path, there may be case differences
        else if ( Files.exists( dirPath) == false && !isCaseLess()) {

            //  Map the path to a real path
            String mappedPath = FileName.mapPath( getRootPath(), dir, isCaseLess());

            if (mappedPath != null) {

                //  Check if the path is a directory
                dirPath = Paths.get( mappedPath);

                if ( Files.isDirectory( dirPath)) {

                    //	Delete the directory
                    try {
                        Files.delete(dirPath);
                    }
                    catch ( java.nio.file.DirectoryNotEmptyException ex) {
                        throw new org.filesys.server.filesys.DirectoryNotEmptyException( "Directory not empty - " + dirPath);
                    }
                }
            }
        }
    }

    /**
     * Set file information
     *
     * @param path  String
     * @param fid   int
     * @param finfo FileInfo
     * @throws IOException Failed to set the file information
     */
    public void setFileInformation(String path, int fid, FileInfo finfo)
            throws IOException {
    }

    /**
     * Rename a file or directory
     *
     * @param curName String
     * @param fid     int
     * @param newName String
     * @param fstate  FileState
     * @param isdir   boolean
     * @param netFile NetworkFile for a handle based rename, else null
     * @throws IOException Failed to rename the file or directory
     */
    public void renameFileDirectory(String curName, int fid, String newName, FileState fstate, boolean isdir, NetworkFile netFile)
            throws IOException {

        //	DEBUG
        if (m_debug)
            Debug.println("SimpleFileLoader.renameFileDirectory() curName=" + curName + ", fid=" + fid + ", newName=" + newName);

        //  Get the full path for the existing file and the new file name
        String curPath = FileName.buildPath(getRootPath(), curName, null, java.io.File.separatorChar);
        String newPath = FileName.buildPath(getRootPath(), newName, null, java.io.File.separatorChar);

        //  Rename the file
        Path curFile = Paths.get(curPath);
        Path newFile = Paths.get(newPath);

        // Check if the file has opened the underlying file
        if ( netFile.getWriteCount() > 0 || netFile.getReadCount() > 0) {
            Debug.println("SimpleFileLoader: Rename of open file, writes=" + netFile.getWriteCount() + ", reads=" + netFile.getReadCount());

            // Close the underlying file
            netFile.closeFile();
        }

        try {
            Files.move(curFile, newFile);
        }
        catch ( Exception ex) {
            throw new IOException( "Rename failed from " + curPath + " to " + newPath, ex);
        }
    }

    /**
     * Return the root path
     *
     * @return String
     */
    protected final String getRootPath() {
        return m_rootPath;
    }

    /**
     * Initialize the file loader using the specified parameters
     *
     * @param params ConfigElement
     * @param ctx    DeviceContext
     * @throws FileLoaderException Failed to initialize the file loader
     */
    public void initializeLoader(ConfigElement params, DeviceContext ctx)
            throws FileLoaderException {

        //	Check if debug output is enabled
        if (params.getChild("Debug") != null)
            m_debug = true;

        //	Get the root path to be used to load/store files
        ConfigElement nameVal = params.getChild("RootPath");
        if (nameVal == null || nameVal.getValue() == null || nameVal.getValue().length() == 0)
            throw new FileLoaderException("SimpleFileLoader RootPath parameter required");
        m_rootPath = FileName.asPlatformPath( nameVal.getValue());

        //	Check that the root path is valid
        Path root = Paths.get( m_rootPath);

        if ( Files.exists( root) == false || Files.isDirectory( root) == false)
            throw new FileLoaderException("SimpleFileLoader RootPath does not exist or is not a directory, " + m_rootPath);

        // Check if the filesystem storing the files  is caseless
        try {

            // Create a test file on the filesystem
            String caseTestName = new String("__FsCaseTest__");
            Path caseTestPath = Paths.get( m_rootPath, caseTestName);

            if ( !Files.exists( caseTestPath))
                Files.createFile( caseTestPath);

            // Check if the file can be found when using different case names
            boolean upperExists = Files.exists( Paths.get( m_rootPath, caseTestName.toUpperCase()));
            boolean lowerExists = Files.exists( Paths.get( m_rootPath, caseTestName.toLowerCase()));
            boolean originalExists = Files.exists( caseTestPath);

            m_fsCaseless = upperExists && lowerExists && originalExists;

            Files.delete( caseTestPath);

            // DEBUG
            if ( m_debug)
                Debug.println("SimpleFileLoader: File store path " + m_rootPath + " caseLess=" + isCaseLess());
        }
        catch ( Exception ex) {
            if ( Debug.hasDumpStackTraces())
                Debug.println( ex);
        }

        // Check if the file/folder owner should be set when creating new files/folders
        if(params.getChild( "SetOwner") != null)
            m_setOwner = true;
    }

    /**
     * Load/save file data, not implemented in this loader.
     *
     * @param fileReq FileRequest
     */
    public final void queueFileRequest(FileRequest fileReq) {

        //	Nothing to do
    }

    /**
     * Start the file loader
     *
     * @param ctx DeviceContext
     */
    public void startLoader(DeviceContext ctx) {

        //	Nothing to do
    }

    /**
     * Shutdown the file loader and release all resources
     *
     * @param immediate boolean
     */
    public void shutdownLoader(boolean immediate) {

        //	Nothing to do
    }

    /**
     * Indicate that the file loader does not support NTFS streams
     *
     * @return boolean
     */
    public boolean supportsStreams() {
        return false;
    }

    /**
     * Add a file processor to process files before storing and after loading.
     *
     * @param fileProc FileProcessor
     * @throws FileLoaderException Failed to add the file processor
     */
    public void addFileProcessor(FileProcessor fileProc)
            throws FileLoaderException {

        //	Not supported by this file loader implementation
        throw new FileLoaderException("File processors not supported");
    }

    /**
     * Set the database context
     *
     * @param dbCtx DBDeviceContext
     */
    public final void setContext(DBDeviceContext dbCtx) {
    }

    /**
     * Set the file owner for a new file/folder
     *
     * @param path Path
     * @param cInfo ClientInformation
     * @return boolean
     */
    private final boolean setOwner(Path path, ClientInfo cInfo) {

        boolean sts = false;

        try {

            // Set the file owner
            UserPrincipal userPrincipal = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName( cInfo.getUserName());

            Files.setOwner( path, userPrincipal);

            sts = true;

            // DEBUG
            if ( m_debug)
                Debug.println("SimpleFileLoader: Set owner path=" + path + " to " + cInfo.getUserName() + " (principal=" + userPrincipal + ")");
        }
        catch ( Exception ex) {
            if (m_debug) {
                Debug.println("SimpleFileLoader: Failed to set file owner for " + path + " to " + cInfo.getUserName());
                Debug.println("Exception: " + ex);
            }
        }

        return sts;
    }
}
