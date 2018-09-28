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

package org.filesys.oncrpc.nfs;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

import org.filesys.debug.Debug;
import org.filesys.oncrpc.RpcAuthenticator;
import org.filesys.server.SrvSession;
import org.filesys.server.filesys.DiskInterface;
import org.filesys.server.filesys.FileStatus;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.TreeConnection;

/**
 * Network File Cache Class
 *
 * <p>
 * Caches the network files that are currently being accessed by the NFS server.
 *
 * @author gkspencer
 */
public class NetworkFileCache {

    // Default file timeout
    public static final long DefaultFileTimeout = 5000L;    // 5 seconds
    public static final long ClosedFileTimeout  = 30000L;   // 30 seconds

    // Network file cache, key is the file id
    private Hashtable<Integer, FileEntry> m_fileCache;

    // File expiry thread
    private FileExpiry m_expiryThread;

    // File timeouts
    private long m_fileIOTmo = DefaultFileTimeout;
    private long m_fileCloseTmo = ClosedFileTimeout;

    // NFS authenticator
    private RpcAuthenticator m_authenticator;

    // Debug enable flag
    private boolean m_debug = false;

    /**
     * File Entry Class
     */
    protected class FileEntry {

        // Network file and closed flag
        private NetworkFile m_file;
        private boolean m_closed;

        // Disk share connection
        private TreeConnection m_conn;

        // File timeout
        private long m_timeout;

        // Session that last accessed the file
        private NFSSrvSession m_sess;

        /**
         * Class constructor
         *
         * @param file NetworkFile
         * @param conn TreeConnection
         * @param sess NFSSrvSession
         */
        public FileEntry(NetworkFile file, TreeConnection conn, NFSSrvSession sess) {
            m_file = file;
            m_conn = conn;
            m_sess = sess;

            updateTimeout();
        }

        /**
         * Return the file timeout
         *
         * @return long
         */
        public final long getTimeout() {
            return m_timeout;
        }

        /**
         * Return the network file
         *
         * @return NetworkFile
         */
        public final NetworkFile getFile() {
            return m_file;
        }

        /**
         * Return the disk share connection
         *
         * @return TreeConnection
         */
        public final TreeConnection getConnection() {
            return m_conn;
        }

        /**
         * Get the session that last accessed the file
         *
         * @return NFSSrvSession
         */
        public final NFSSrvSession getSession() {
            return m_sess;
        }

        /**
         * Update the file timeout
         */
        public final void updateTimeout() {
            m_timeout = System.currentTimeMillis() + m_fileIOTmo;
        }

        /**
         * Update the file timeout
         *
         * @param tmo long
         */
        public final void updateTimeout(long tmo) {
            m_timeout = tmo;
        }

        /**
         * Set the session that last accessed the file
         *
         * @param sess NFSSrvSession
         */
        public final void setSession(NFSSrvSession sess) {
            m_sess = sess;
        }

        /**
         * Check if the network file has been closed due to no I/O activity
         *
         * @return boolean
         */
        public final boolean isClosed() {
            return m_closed;
        }

        /**
         * Close the file
         */
        public final void closeFile() {
            if (m_file != null) {
                try {
                    m_file.closeFile();
                    m_closed = true;
                }
                catch (IOException ex) {
                }
            }
        }

        /**
         * Open the network file
         */
        public final void openFile() {
            if (m_file != null) {
                try {
                    m_file.openFile(false);
                    m_closed = false;
                }
                catch (IOException ex) {
                }
            }
        }

        /**
         * Mark the file entry as closed
         */
        public final void markAsClosed() {
            if (m_file != null)
                m_closed = true;
        }
    }

    ;

    /**
     * File Expiry Thread Class
     */
    protected class FileExpiry implements Runnable {

        // Expiry thread
        private Thread m_thread;

        // Shutdown flag
        private boolean m_shutdown;

        /**
         * Class Constructor
         *
         * @param name String
         */
        public FileExpiry(String name) {

            // Create and start the file expiry thread
            m_thread = new Thread(this);
            m_thread.setDaemon(true);
            m_thread.setName("NFSFileExpiry_" + name);
            m_thread.start();
        }

        /**
         * Main thread method
         */
        public void run() {

            // Loop until shutdown
            while (m_shutdown == false) {

                // Sleep for a while
                try {
                    Thread.sleep(m_fileIOTmo / 2);
                }
                catch (InterruptedException ex) {
                }

                // Get the current system time
                long timeNow = System.currentTimeMillis();

                // Check for expired files
                synchronized (m_fileCache) {

                    // Enumerate the cache entries
                    Enumeration<Integer> enm = m_fileCache.keys();

                    while (enm.hasMoreElements()) {

                        // Get the current key
                        Integer fileId = enm.nextElement();

                        // Get the file entry and check if it has expired
                        FileEntry fentry = m_fileCache.get(fileId);

                        if (fentry != null && fentry.getTimeout() < timeNow) {

                            // Get the network file
                            NetworkFile netFile = fentry.getFile();

                            // Check if the file has an I/O request pending, if so then reset the file expiry time
                            // for the file
                            if (netFile.hasIOPending()) {

                                // Update the expiry time for the file entry
                                fentry.updateTimeout();

                                // DEBUG
                                if (Debug.EnableInfo && hasDebug())
                                    Debug.println("NFSFileExpiry: I/O pending file=" + fentry.getFile().getFullName() + ", fid=" + fileId);
                            } else {

                                // Make sure there is no active transaction
                                if (fentry.getSession().hasTransaction())
                                    fentry.getSession().endTransaction();

                                // Check if the network file is closed, if not  then close the file to release the file
                                // handle but keep the file entry in the file cache for a while as the file may be re-opened
                                if (fentry.isClosed() == false && netFile != null) {

                                    // We need to do the close in the context of the user that opened the file
                                    try {

                                        // Set the the current user context
                                        m_authenticator.setCurrentUser(fentry.getSession(), fentry.getSession().getNFSClientInformation());

                                        // Check if the filesystem is transactional, in this case only mark the file as closed
                                        if (netFile.allowsOpenCloseViaNetworkFile() == false) {

                                            // Mark the file as closed, wait for second stage expiry to actually close the file
                                            fentry.markAsClosed();

                                            // DEBUG
                                            if (Debug.EnableInfo && hasDebug())
                                                Debug.println("NFSFileExpiry: Marked as closed file=" + fentry.getFile().getFullName() + ", fid=" + fileId + " (cached)");
                                        } else {

                                            // Close the network file
                                            fentry.closeFile();

                                            // Update the file entry timeout to keep the file in the cache for a while
                                            fentry.updateTimeout(System.currentTimeMillis() + m_fileCloseTmo);

                                            // DEBUG
                                            if (Debug.EnableInfo && hasDebug())
                                                Debug.println("NFSFileExpiry: Closed file=" + fentry.getFile().getFullName() + ", fid=" + fileId + " (cached)");
                                        }

                                        // Clear the user context, flush any active transaction
                                        if (fentry.getSession().hasTransaction())
                                            fentry.getSession().endTransaction();

                                        m_authenticator.setCurrentUser(fentry.getSession(), null);
                                    }
                                    catch (Exception ex) {

                                        // DEBUG
                                        if (Debug.EnableInfo && hasDebug()) {
                                            Debug.println("Error closing file, fentry=" + fentry + ", ex=" + ex.getMessage());
                                            Debug.println(ex);
                                        }
                                    }
                                } else {

                                    // File entry has expired, remove it from the cache
                                    m_fileCache.remove(fileId);

                                    // Close the file via the disk interface
                                    try {

                                        // Set the the current user context
                                        m_authenticator.setCurrentUser(fentry.getSession(), fentry.getSession().getNFSClientInformation());

                                        // Get the disk interface
                                        DiskInterface disk = (DiskInterface) fentry.getConnection().getInterface();

                                        // Close the file
                                        if (disk.fileExists(fentry.getSession(), fentry.getConnection(), netFile.getFullName()) != FileStatus.NotExist) {

                                            // Check if the file has already been closed
                                            if (netFile.isClosed() == false) {

                                                // Close the file
                                                disk.closeFile(fentry.getSession(), fentry.getConnection(), netFile);

                                                // DEBUG
                                                if (Debug.EnableInfo && hasDebug())
                                                    Debug.println("NFSFileExpiry: Closed file=" + fentry.getFile().getFullName() + ", fid=" + fileId + " (removed)");
                                            } else if (Debug.EnableInfo && hasDebug())
                                                Debug.println("NFSFileExpiry: File already closed, file=" + fentry.getFile().getFullName() + ", fid=" + fileId);
                                        } else if (Debug.EnableInfo && hasDebug())
                                            Debug.println("NFSFileExpiry: File deleted before close, " + netFile.getFullName());

                                        // Clear the user context, flush any active transaction
                                        if (fentry.getSession().hasTransaction())
                                            fentry.getSession().endTransaction();

                                        m_authenticator.setCurrentUser(fentry.getSession(), null);
                                    }
                                    catch (Exception ex) {

                                        // DEBUG
                                        if (Debug.EnableInfo && hasDebug()) {
                                            Debug.println("Error closing file, fentry=" + fentry + ", ex=" + ex.getMessage());
                                            Debug.println(ex);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * Request the file expiry thread to shutdown
         */
        public final void requestShutdown() {

            // Set the shutdown flag
            m_shutdown = true;

            // Wakeup the thread
            try {
                m_thread.interrupt();
            }
            catch (Exception ex) {
            }

            // Wait for the expiry thread to complete
            try {
                m_thread.join(m_fileIOTmo);
            }
            catch (Exception ex) {
            }
        }
    }

    ;

    /**
     * Class constructor
     *
     * @param name String
     */
    public NetworkFileCache(String name) {

        // Create the file cache
        m_fileCache = new Hashtable<Integer, FileEntry>();

        // Start the file expiry thread
        m_expiryThread = new FileExpiry(name);
    }

    /**
     * Determine if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Add a file to the cache
     *
     * @param file NetworkFile
     * @param conn TreeConnection
     * @param sess NFSSrvSession
     */
    public synchronized final void addFile(NetworkFile file,
                                           TreeConnection conn, NFSSrvSession sess) {
        synchronized (m_fileCache) {
            m_fileCache.put(new Integer(file.getFileId()), new FileEntry(file, conn, sess));
        }
    }

    /**
     * Remove a file from the cache
     *
     * @param id int
     */
    public synchronized final void removeFile(int id) {

        // Create the search key
        Integer fileId = new Integer(id);

        synchronized (m_fileCache) {
            m_fileCache.remove(fileId);
        }
    }

    /**
     * Find a file via the file id
     *
     * @param id   int
     * @param sess SrvSession
     * @return NetworkFile
     */
    public synchronized final NetworkFile findFile(int id, SrvSession sess) {

        // Create the search key
        Integer fileId = new Integer(id);
        FileEntry fentry = null;

        synchronized (m_fileCache) {
            fentry = m_fileCache.get(fileId);
        }

        // Return the file, or null if not found
        if (fentry != null) {

            // Update the file timeout
            fentry.updateTimeout();

            // Check if the file is open
            if (fentry.isClosed())
                fentry.openFile();

            // Return the file
            return fentry.getFile();
        }

        // Invalid file id

        return null;
    }

    /**
     * Return the count of entries in the cache
     *
     * @return int
     */
    public final int numberOfEntries() {
        return m_fileCache.size();
    }

    /**
     * Close the expiry cache, close and remove all files from the cache and
     * stop the expiry thread.
     */
    public final void closeAllFiles() {

        // Enumerate the cache entries
        Enumeration<Integer> keys = m_fileCache.keys();

        while (keys.hasMoreElements()) {

            // Get the current key and lookup the matching value
            Integer key = keys.nextElement();
            FileEntry entry = m_fileCache.get(key);

            // Expire the file entry
            entry.updateTimeout(0L);
        }

        // Shutdown the expiry thread, this should close the files
        m_expiryThread.requestShutdown();
    }

    /**
     * Enable/disable debug output
     *
     * @param ena boolean
     */
    public final void setDebug(boolean ena) {
        m_debug = ena;
    }

    /**
     * Set the I/O cache timer value
     *
     * @param ioTimer long
     */
    public final void setIOTimer(long ioTimer) {
        m_fileIOTmo = ioTimer;
    }

    /**
     * Set the close file cache timer value
     *
     * @param closeTimer long
     */
    public final void setCloseTimer(long closeTimer) {
        m_fileCloseTmo = closeTimer;
    }

    /**
     * Set the RPC authenticator
     *
     * @param auth RpcAuthenticator
     */
    public final void setRpcAuthenticator(RpcAuthenticator auth) {
        m_authenticator = auth;
    }

    /**
     * Dump the cache entries to the debug device
     */
    public final void dumpCache() {

        // Dump the count of entries in the cache
        Debug.println("NetworkFileCache entries=" + numberOfEntries());

        // Enumerate the cache entries
        Enumeration<Integer> keys = m_fileCache.keys();

        while (keys.hasMoreElements()) {

            // Get the current key and lookup the matching value
            Integer key = keys.nextElement();
            FileEntry entry = m_fileCache.get(key);

            // Dump the entry details
            Debug.println("fid=" + key + ": " + entry);
        }
    }
}
