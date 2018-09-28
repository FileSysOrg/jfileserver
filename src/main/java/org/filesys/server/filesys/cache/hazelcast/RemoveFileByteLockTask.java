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
import org.filesys.locking.NotLockedException;
import org.filesys.server.filesys.cache.cluster.ClusterFileLock;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;

import com.hazelcast.core.IMap;

/**
 * Remove File Byte Range Lock Remote Task Class
 *
 * <p>Used to synchronize removing a byte range lock on a file state by executing on the remote node
 * that owns the file state/key.
 *
 * @author gkspencer
 */
public class RemoveFileByteLockTask extends RemoteStateTask<ClusterFileState> {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Byte range lock details
    private ClusterFileLock m_lock;

    /**
     * Default constructor
     */
    public RemoveFileByteLockTask() {
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
    public RemoveFileByteLockTask(String mapName, String key, ClusterFileLock lock, boolean debug, boolean timingDebug) {
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
            Debug.println("RemoveFileByteLockTask: Remove lock=" + m_lock + " from " + fState);

        // Find the matching lock, make sure the owner node matches
        FileLockList lockList = fState.getLockList();
        ClusterFileLock clLock = (ClusterFileLock) lockList.findLock(m_lock);

        if (clLock != null && clLock.getOwnerNode().equalsIgnoreCase(m_lock.getOwnerNode()) == true) {

            // Remove the lock
            lockList.removeLock(clLock);
        } else {

            // Return a not locked exception, node does not own the matching lock
            throw new NotLockedException();
        }

        // Return the updated file state
        return fState;
    }
}
