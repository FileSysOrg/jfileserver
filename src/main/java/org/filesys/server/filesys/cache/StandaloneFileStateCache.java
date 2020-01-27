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

package org.filesys.server.filesys.cache;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.filesys.debug.Debug;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.server.filesys.FileName;
import org.filesys.server.filesys.FileStatus;
import org.filesys.server.locking.OpLockDetails;
import org.springframework.extensions.config.ConfigElement;

/**
 * File State Cache Class
 *
 * <p>
 * Contains a cache of file/directory information for recently accessed files/directories to reduce
 * the calls made by the core server code to the database.
 *
 * @author gkspencer
 */
public class StandaloneFileStateCache extends FileStateCache {

    // Initial allocation size for the state cache
    private static final int InitialCacheSize = 500;
    private static final int MinimumCacheSize = 100;

    // File state cache, keyed by file path
    private Map<String, FileState> m_stateCache;

    /**
     * Class constructor
     */
    public StandaloneFileStateCache() {
    }

    /**
     * Initialize the file state cache
     *
     * @param srvConfig ServerConfiguration
     * @throws InvalidConfigurationException Failed to initialize the file state cache
     */
    public void initializeCache(ConfigElement config, ServerConfiguration srvConfig)
            throws InvalidConfigurationException {

        // Call the base class
        super.initializeCache(config, srvConfig);

        // Check if the initial cache size has been specified
        int initSize = InitialCacheSize;

        ConfigElement elem = config.getChild("initialSize");
        if (elem != null && elem.getValue() != null) {

            // Validate the initial size value
            try {

                // Convert the initial cache size value
                initSize = Integer.parseInt(elem.getValue());

                // Range check the initial cache size value
                if (initSize < MinimumCacheSize)
                    throw new InvalidConfigurationException("Initial cache size value too low, " + initSize);
            }
            catch (NumberFormatException ex) {
                throw new InvalidConfigurationException("Invalid initial cache size value, " + elem.getValue());
            }
        }

        // Allocate the state cache
        m_stateCache = new HashMap<String, FileState>(initSize);
    }

    /**
     * Return the number of states in the cache
     *
     * @return int
     */
    public final int numberOfStates() {
        synchronized (m_stateCache) {
            return m_stateCache.size();
        }
    }

    /**
     * Find the file state for the specified path
     *
     * @param path String
     * @return FileState
     */
    public final FileState findFileState(String path) {
        FileState fState = null;

        synchronized (m_stateCache) {
            fState = m_stateCache.get(FileState.normalizePath(path, isCaseSensitive()));
        }

        return fState;
    }

    /**
     * Find the file state for the specified path, and optionally create a new file state if not
     * found
     *
     * @param path   String
     * @param create boolean
     * @return FileState
     */
    public final FileState findFileState(String path, boolean create) {

        FileState state = null;

        synchronized (m_stateCache) {

            // Find the required file state, if it exists
            state = m_stateCache.get(FileState.normalizePath(path, isCaseSensitive()));

            // Check if we should create a new file state
            if (state == null && create == true) {

                // Create a new file state
                state = new LocalFileState(path, isCaseSensitive());

                // Set the file state timeout and add to the cache
                state.setExpiryTime(System.currentTimeMillis() + getFileStateExpireInterval());
                m_stateCache.put(state.getPath(), state);
            }
        }

        // Return the file state
        return state;
    }

    /**
     * Find the file state for the specified path, and optionally create a new file state if not
     * found with the specified initial status
     *
     * @param path   String
     * @param create boolean
     * @param status FileStatus
     * @return FileState
     */
    public final FileState findFileState(String path, boolean create, FileStatus status) {

        FileState state = null;

        synchronized (m_stateCache) {

            // Find the required file state, if it exists
            state = m_stateCache.get(FileState.normalizePath(path, isCaseSensitive()));

            // Check if we should create a new file state
            if (state == null && create == true) {

                // Create a new file state
                state = new LocalFileState(path, isCaseSensitive());

                // Set the file state timeout and add to the cache
                state.setExpiryTime(System.currentTimeMillis() + getFileStateExpireInterval());
                state.setFileStatus(status);
                m_stateCache.put(state.getPath(), state);
            }
        }

        // Return the file state
        return state;
    }

    /**
     * Remove the file state for the specified path
     *
     * @param path String
     * @return FileState
     */
    public final FileState removeFileState(String path) {

        FileState state = null;

        synchronized (m_stateCache) {

            // Remove the file state from the cache
            state = m_stateCache.remove(FileState.normalizePath(path, isCaseSensitive()));
        }

        // Check if there is a state listener
        if (hasStateListener() && state != null)
            getStateListener().fileStateClosed(state);

        // Return the removed file state
        return state;
    }

    /**
     * Rename a file state, remove the existing entry, update the path and add the state back into
     * the cache using the new path.
     *
     * @param newPath String
     * @param state   FileState
     * @param isDir   boolean
     */
    public final void renameFileState(String newPath, FileState state, boolean isDir) {

        // Synchronize the cache update
        String oldPath = state.getPath();

        synchronized (m_stateCache) {

            // Remove the existing file state from the cache, using the original name
            m_stateCache.remove(state.getPath());

            // Update the file state path and add it back to the cache using the new name
            state.setPath(newPath, isCaseSensitive());
            state.setFileStatus(isDir ? FileStatus.DirectoryExists : FileStatus.FileExists);

            m_stateCache.put(state.getPath(), state);

            // If the path is to a folder we must change the file status of all file states that are
            // using the old path
            if (isDir == true) {

                // Get the old path and normalize
                if (oldPath.endsWith(FileName.DOS_SEPERATOR_STR) == false)
                    oldPath = oldPath + FileName.DOS_SEPERATOR_STR;
                oldPath = oldPath.toUpperCase();

                // Enumerate the file states
                for (String statePath : m_stateCache.keySet()) {

                    // Check if the path is below the renamed path
                    if (statePath.length() > oldPath.length() && statePath.startsWith(oldPath)) {

                        // Get the associated file state, mark as not existing
                        FileState renState = (FileState) m_stateCache.get(statePath);

                        renState.setFileStatus(FileStatus.NotExist);
                        renState.setFileId(FileState.UnknownFileId);

                        // DEBUG
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("++ Rename update " + statePath);
                    }
                }
            }
        }
    }

    /**
     * Remove all file states from the cache
     */
    public final void removeAllFileStates() {

        // Check if there are any items in the cache
        if (m_stateCache == null)
            return;

        synchronized (m_stateCache) {

            // Check if there are any items in the cache
            if (m_stateCache.isEmpty())
                return;

            // Enumerate the file state cache and remove expired file state objects
            for (FileState state : m_stateCache.values()) {

                // Check if there is a state listener
                if (hasStateListener())
                    getStateListener().fileStateClosed(state);

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("++ Closed: " + state.getPath());
            }

            // Remove all the file states
            m_stateCache.clear();
        }
    }

    /**
     * Remove expired file states from the cache
     *
     * @return int
     */
    public final int removeExpiredFileStates() {

        // Check if there are any items in the cache
        if (m_stateCache == null)
            return 0;

        synchronized (m_stateCache) {

            // Check if there are any items in the cache
            if (m_stateCache.isEmpty())
                return 0;

            // Enumerate the file state cache and remove expired file state objects
            Iterator<Map.Entry<String, FileState>> enm = m_stateCache.entrySet().iterator();
            long curTime = System.currentTimeMillis();

            int expiredCnt = 0;
            int openCnt = 0;

            while (enm.hasNext()) {

                // Get the file state
                Map.Entry<String, FileState> entry = enm.next();
                FileState state = entry.getValue();

                if (state != null && state.isPermanentState() == false) {

                    // Check if the file state has expired and there are no open references to the
                    // file
                    if (state.hasExpired(curTime) && state.getOpenCount() == 0) {

                        // Check if there is a state listener to veto the file state expiration
                        if (hasStateListener() == false || getStateListener().fileStateExpired(state) == true) {

                            // Remove the expired file state
                            enm.remove();

                            // DEBUG
                            if (hasDebugExpiredStates())
                                Debug.println("++ Expired file state: " + state);

                            // Update the expired count
                            expiredCnt++;
                        }
                    } else if (state.getOpenCount() > 0)
                        openCnt++;
                }
            }

            // DEBUG
            if (hasDebugExpiredStates() && openCnt > 0) {
                Debug.println("++ Open files " + openCnt);
                dumpCache(false);
            }

            // Return the count of expired file states that were removed
            return expiredCnt;
        }
    }

    /**
     * Dump the state cache entries to the specified stream
     *
     * @param dumpAttribs boolean
     */
    public final void dumpCache(boolean dumpAttribs) {

        synchronized (m_stateCache) {

            // Dump the file state cache entries to the specified stream
            if (m_stateCache.size() > 0)
                Debug.println("++ FileStateCache Entries:");

            long curTime = System.currentTimeMillis();

            for (Map.Entry<String, FileState> entry : m_stateCache.entrySet()) {

                FileState state = entry.getValue();
                Debug.println("++  " + entry.getKey() + "(" + state.getSecondsToExpire(curTime) + ") : " + state.toString());

                // Check if the state attributes should be output
                if (dumpAttribs == true)
                    state.DumpAttributes();
            }
        }
    }

    /**
     * Request an oplock break
     *
     * @param path   String
     * @param oplock OpLockDetails
     * @throws IOException I/O error
     */
    public void requestOplockBreak(String path, OpLockDetails oplock)
            throws IOException {

        // Only used for remote oplocks
    }
}
