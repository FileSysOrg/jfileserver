/*
 * Copyright (C) 2006-2011 Alfresco Software Limited.
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

package org.filesys.server.filesys.cache.hazelcast;

import org.filesys.debug.Debug;
import org.filesys.locking.FileLockList;
import org.filesys.locking.LockConflictException;
import org.filesys.server.filesys.cache.cluster.ClusterFileLock;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;

import com.hazelcast.core.IMap;

/**
 * Add File Byte Range Lock Remote Task Class
 *
 * <p>Used to synchronize adding a byte range lock to a file state by executing on the remote node
 * that owns the file state/key.
 *
 * @author gkspencer
 */
public class AddFileByteLockTask extends RemoteStateTask<ClusterFileState> {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Byte range lock details
    private ClusterFileLock m_lock;

    /**
     * Default constructor
     */
    public AddFileByteLockTask() {
    }

    /**
     * Class constructor
     *
     * @param mapName     String
     * @param key         String
     * @param lock        ClusterFileLock
     * @param debug       boolean
     * @param timingDebug boolean
     */
    public AddFileByteLockTask(String mapName, String key, ClusterFileLock lock, boolean debug, boolean timingDebug) {
        super(mapName, key, true, false, debug, timingDebug);

        m_lock = lock;
    }

    /**
     * Run a remote task against a file state
     *
     * @param stateCache Map of paths/cluster file states
     * @param fState     ClusterFileState
     * @return ClusterFileState
     * @throws Exception Error running remote task
     */
    protected ClusterFileState runRemoteTaskAgainstState(IMap<String, ClusterFileState> stateCache, ClusterFileState fState)
            throws Exception {

        // DEBUG
        if (hasDebug())
            Debug.println("AddFileByteLockTask: Add lock=" + m_lock + " to " + fState);

        // Check if there are any locks on the file
        if (fState.hasActiveLocks() == false) {

            // Add the lock
            fState.addLock(m_lock);
        } else {

            // Check for lock conflicts
            FileLockList lockList = fState.getLockList();
            int idx = 0;
            boolean lockConflict = false;

            while (idx < lockList.numberOfLocks() && lockConflict == false) {

                // Get the current file lock
                ClusterFileLock curLock = (ClusterFileLock) lockList.getLockAt(idx++);

                // Check if the lock overlaps with the new lock
                if (curLock.hasOverlap(m_lock)) {

                    // Check the if the lock owner is the same
                    if (curLock.getProcessId() != m_lock.getProcessId() ||
                            curLock.getOwnerNode().equalsIgnoreCase(m_lock.getOwnerNode()) == false) {

                        // DEBUG
                        if (hasDebug())
                            Debug.println("AddLock Lock conflict with lock=" + curLock);

                        // Lock conflict
                        throw new LockConflictException();
                    }
                }
            }

            // Add the lock
            fState.addLock(m_lock);
        }

        // Return the updated file state
        return fState;
    }
}
