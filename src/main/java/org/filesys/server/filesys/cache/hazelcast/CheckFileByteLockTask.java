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
import org.filesys.server.filesys.cache.cluster.ClusterFileLock;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;

import com.hazelcast.core.IMap;

/**
 * Check File Byte Range Lock Remote Task Class
 *
 * <p>Used to synchronize checking if an area of a file is readable/writeable by executing on the remote node
 * that owns the file state/key.
 *
 * @author gkspencer
 */
public class CheckFileByteLockTask extends RemoteStateTask<Boolean> {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // File area details
    private ClusterFileLock m_lockCheck;

    // Check write access
    private boolean m_writeCheck;

    /**
     * Default constructor
     */
    public CheckFileByteLockTask() {
    }

    /**
     * Class constructor
     *
     * @param mapName     String
     * @param key         String
     * @param lockCheck   ClusterFileLock
     * @param writeCheck  boolean
     * @param debug       boolean
     * @param timingDebug boolean
     */
    public CheckFileByteLockTask(String mapName, String key, ClusterFileLock lockCheck, boolean writeCheck, boolean debug, boolean timingDebug) {
        super(mapName, key, true, false, debug, timingDebug);

        m_lockCheck = lockCheck;
        m_writeCheck = writeCheck;
    }

    /**
     * Run a remote task against a file state
     *
     * @param stateCache Map of paths/cluster file states
     * @param fState     ClusterFileState
     * @return Boolean
     * @throws Exception Error running remote task
     */
    protected Boolean runRemoteTaskAgainstState(IMap<String, ClusterFileState> stateCache, ClusterFileState fState)
            throws Exception {

        // DEBUG
        if (hasDebug())
            Debug.println("CheckFileByteLockTask: checkArea=" + m_lockCheck + (m_writeCheck ? " (Write)" : " (Read)") + " on " + fState);

        // Check if there are any locks on the file
        boolean accessOK = true;

        if (fState.hasActiveLocks() == true) {

            // Check if the area is readable/writeable by this user
            if (m_writeCheck == true) {

                // Check if the file area is writeable
                accessOK = fState.getLockList().canWriteFile(m_lockCheck);
            } else {

                // Check if the file area is readable
                accessOK = fState.getLockList().canReadFile(m_lockCheck);
            }
        }

        // Return the access status
        return accessOK;
    }
}
