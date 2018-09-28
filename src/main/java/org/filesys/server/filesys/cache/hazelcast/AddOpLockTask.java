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
import org.filesys.server.filesys.cache.cluster.ClusterFileState;

import com.hazelcast.core.IMap;

/**
 * Add OpLock Remote Task Class
 *
 * <p>Used to synchronize adding an oplock to a file state by executing on the remote node
 * that owns the file state/key.
 *
 * @author gkspencer
 */
public class AddOpLockTask extends RemoteStateTask<Boolean> {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Remote oplock details
    private RemoteOpLockDetails m_oplock;

    /**
     * Default constructor
     */
    public AddOpLockTask() {
    }

    /**
     * Class constructor
     *
     * @param mapName     String
     * @param key         String
     * @param oplock      RemoteOpLockDetails
     * @param debug       boolean
     * @param timingDebug boolean
     */
    public AddOpLockTask(String mapName, String key, RemoteOpLockDetails oplock, boolean debug, boolean timingDebug) {
        super(mapName, key, true, false, debug, timingDebug);

        m_oplock = oplock;
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
            Debug.println("AddOpLockTask: Add oplock=" + m_oplock + " to " + fState);

        // May throw an exception if there is an existing oplock on the file
        fState.setOpLock(m_oplock);

        // Return a success status
        return new Boolean(true);
    }
}
