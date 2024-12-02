/*
 * Copyright (C) 2019 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.smb.server.disk;

import org.filesys.debug.Debug;
import org.filesys.server.core.DeviceContextException;
import org.filesys.server.filesys.DiskDeviceContext;
import org.filesys.server.filesys.FileSystem;
import org.filesys.util.MemorySize;
import org.springframework.extensions.config.ConfigElement;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

/**
 * Java NIO Filesystem Device Context Class
 */
public class JavaNIODeviceContext extends DiskDeviceContext {

    // Constants
    //
    // Define the minimum large file size
    private static final long MinimumLargeFileSize  = MemorySize.MEGABYTE;

    // Default large file size
    private static final long DefaultLargeFileSize  = 500 * MemorySize.MEGABYTE;

    // Default trachcan folder name
    private static final String TrashcanFolderName  = ".Trashcan";

    // Trashcan folder, used for large file deletes
    private File m_trashDir;

    // Large file size, require special processing for deletes/truncates
    private long m_largeFileSize = DefaultLargeFileSize;

    /**
     * Class constructor
     *
     * @param args ConfigElement
     * @throws DeviceContextException Error initializing the device context
     */
    public JavaNIODeviceContext(ConfigElement args) throws DeviceContextException {
        super();

        // Initialize the database interface
        initialize(args);
    }

    /**
     * Class constructor
     *
     * @param name String
     * @param args ConfigElement
     * @throws DeviceContextException Error initializing the device context
     */
    public JavaNIODeviceContext(String name, ConfigElement args) throws DeviceContextException {
        super();

        // Set the shared device name
        setShareName(name);

        // Initialize the database interface
        initialize(args);
    }

    /**
     * Initialize the Java NIO filesystem device context
     *
     * @param args ConfigElement
     * @throws DeviceContextException Error initializing the device context
     */
    protected final void initialize(ConfigElement args)
            throws DeviceContextException {

        //	Get the device name argument
        ConfigElement path = args.getChild("LocalPath");
        DiskDeviceContext ctx = null;

        if (path != null) {

            //	Validate the path and convert to an absolute path
            File rootDir = new File(path.getValue());
            setDeviceName( rootDir.getAbsolutePath());

            // Get the large file size, for special delete/truncate processing
            ConfigElement largeSize = args.getChild( "LargeFileSize");
            if ( largeSize != null) {

                // Parse the large file size
                m_largeFileSize = MemorySize.getByteValue( largeSize.getValue());

                if ( m_largeFileSize < MinimumLargeFileSize)
                    m_largeFileSize = MinimumLargeFileSize;
            }

            // Get the trashcan folder path
            ConfigElement trashCanPath = args.getChild("TrashcanPath");
            if ( trashCanPath != null) {

                // Get the trashcan path
                m_trashDir = new File( trashCanPath.getValue());

                if ( m_trashDir.exists() == false)
                    throw new DeviceContextException("Trashcan folder does not exist - " + m_trashDir.getAbsolutePath());
                else if ( m_trashDir.isFile())
                    throw new DeviceContextException("Trashcan path is not a folder - " + m_trashDir.getAbsolutePath());

                // Make sure the trashcan folder is on the same volume as the shared folder, so we can rename deleted files
                if (!m_trashDir.getAbsolutePath().startsWith(rootDir.getAbsolutePath())
                        && !rootDir.getParent().equalsIgnoreCase(m_trashDir.getParent())) {

                    // File share and trash folders are not on the same volume
                    throw new DeviceContextException("File share and trash folders must be on the same volume");
                }
            }
            else {

                // Use/create a folder within the shared folder as the trashcan folder
                File trashDir = new File( rootDir, TrashcanFolderName);
                if ( trashDir.exists() == false) {

                    // Create the trashcan folder
                    if (trashDir.mkdir() == false)
                        throw new DeviceContextException("Failed to create trashcan folder - " + trashDir.getAbsolutePath());
                }

                // Set the trashcan path
                m_trashDir = trashDir;
            }

            // Check if debug output is enabled
            if ( args.getChild( "Debug") != null)
                setDebug( true);

            //	Set filesystem flags
            setFilesystemAttributes(FileSystem.CasePreservedNames + FileSystem.UnicodeOnDisk);

            //	If the path is not valid then set the filesystem as unavailable
            if (rootDir.exists() == false || rootDir.isDirectory() == false || rootDir.list() == null) {

                //	Mark the filesystem as unavailable
                setAvailable(false);

                // DEBUG
                if ( hasDebug())
                    Debug.println("Share " + getShareName() + ", local path=" + rootDir.getPath() + " unavailable");
            }
        }
        else {

            //	Local path not specified
            throw new DeviceContextException("LocalPath parameter not specified");
        }
    }

    /**
     * Check if the trashcan folder is configured
     *
     * @return boolean
     */
    protected final boolean hasTrashFolder() {
        return m_trashDir != null ? true : false;
    }

    /**
     * Return the trashcan folder path
     *
     * @return File
     */
    protected final File getTrashFolder() {
        return m_trashDir;
    }

    /**
     * Return the large file size, in bytes
     *
     * @return long
     */
    protected final long getLargeFileSize() {
        return m_largeFileSize;
    }
}
