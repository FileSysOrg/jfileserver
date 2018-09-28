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

import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.core.DeviceContext;
import org.filesys.server.filesys.AccessDeniedException;
import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.FileName;
import org.filesys.server.filesys.FileOpenParams;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.db.DBDeviceContext;
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

    //	Local path that the virtual filesystem is mapped to
    private String m_rootPath;

    //	Enable debug output
    private boolean m_debug;

    /**
     * Default constructor
     */
    public SimpleFileLoader() {
    }

    /**
     * Return the database features required by this file loader. Return zero if no database features
     * are required by the loader.
     *
     * @return int
     */
    public int getRequiredDBFeatures() {

        //	No database features required
        return 0;
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
            String mappedPath = FileName.mapPath(getRootPath(), params.getPath());
            if (mappedPath == null && create == false)
                throw new FileNotFoundException("File does not exist, " + params.getPath());

            //  Create the file object for the mapped file and check if the file exists
            file = new File(mappedPath);
            if (file.exists() == false && create == false)
                throw new FileNotFoundException("File does not exist, " + params.getPath());

            //	Set the new full path
            fullName = mappedPath;
        }

        //  Create the new file, if create is enabled
        if (create) {
            FileWriter newFile = new FileWriter(file);
            newFile.close();
        }

        //  Create a Java network file
        file = new File(fullName);
        LocalDataNetworkFile netFile = new LocalDataNetworkFile(params.getPath(), fid, did, file);
        netFile.setGrantedAccess(NetworkFile.Access.READ_WRITE);

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

        //  Check if the file exists, and it is a file
        File delFile = new File(name);
        if (delFile.exists() && delFile.isFile()) {

            //	Delete the file
            delFile.delete();
        }
    }

    /**
     * Create a directory
     *
     * @param dir String
     * @param fid int
     * @throws IOException Failed to create the directory
     */
    public void createDirectory(String dir, int fid)
            throws IOException {

        //	DEBUG
        if (m_debug)
            Debug.println("SimpleFileLoader.createDirectory() dir=" + dir + ", fid=" + fid);

        //  Get the full path for the new directory
        String dirname = FileName.buildPath(getRootPath(), dir, null, java.io.File.separatorChar);

        //  Create the new directory
        File newDir = new File(dirname);
        newDir.mkdir();
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

        //  Check if the directory exists, and it is a directory
        File delDir = new File(dirname);
        if (delDir.exists() && delDir.isDirectory()) {

            //	Check if the directory contains any files
            String[] fileList = delDir.list();
            if (fileList != null && fileList.length > 0)
                throw new AccessDeniedException("Directory not empty");

            //	Delete the directory
            delDir.delete();
        }

        //  If the path does not exist then try and map it to a real path, there may be case differences
        else if (delDir.exists() == false) {

            //  Map the path to a real path
            String mappedPath = FileName.mapPath(getRootPath(), dir);
            if (mappedPath != null) {

                //  Check if the path is a directory
                delDir = new File(mappedPath);
                if (delDir.isDirectory()) {

                    //	Check if the directory contains any files
                    String[] fileList = delDir.list();
                    if (fileList != null && fileList.length > 0)
                        throw new AccessDeniedException("Directory not empty");

                    //	Delete the directory
                    delDir.delete();
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
     * @param isdir   boolean
     * @throws IOException Failed to rename the file or directory
     */
    public void renameFileDirectory(String curName, int fid, String newName, boolean isdir)
            throws IOException {

        //	DEBUG
        if (m_debug)
            Debug.println("SimpleFileLoader.renameFileDirectory() curName=" + curName + ", fid=" + fid + ", newName=" + newName);

        //  Get the full path for the existing file and the new file name
        String curPath = FileName.buildPath(getRootPath(), curName, null, java.io.File.separatorChar);
        String newPath = FileName.buildPath(getRootPath(), newName, null, java.io.File.separatorChar);

        //  Rename the file
        File curFile = new File(curPath);
        File newFile = new File(newPath);

        if (curFile.renameTo(newFile) == false)
            throw new IOException("Rename " + curPath + " to " + newPath + " failed");
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
        m_rootPath = nameVal.getValue();

        //	Check that the root path is valid
        File root = new File(m_rootPath);
        if (root.exists() == false || root.isFile())
            throw new FileLoaderException("SimpleFileLoader RootPath does not exist or is not a directory, " + m_rootPath);
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
}
