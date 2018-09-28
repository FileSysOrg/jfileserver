/*
 * Copyright (C) 2006-2012 Alfresco Software Limited.
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
import org.filesys.server.locking.OpLockDetails;
import org.filesys.smb.OpLockType;

import com.hazelcast.core.IMap;

/**
 * Change OpLock Type Remote Task Class
 *
 * <p>Used to synchronize changing an oplock type on a file state by executing on the remote
 * node that owns the file state/key.
 *
 * @author gkspencer
 */
public class ChangeOpLockTypeTask extends RemoteStateTask<Integer> {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // New oplock type
    private OpLockType m_oplockType;

    /**
     * Default constructor
     */
    public ChangeOpLockTypeTask() {
    }

    /**
     * Class constructor
     *
     * @param mapName     String
     * @param key         String
     * @param newLockType OpLockType
     * @param debug       boolean
     * @param timingDebug boolean
     */
    public ChangeOpLockTypeTask(String mapName, String key, OpLockType newLockType, boolean debug, boolean timingDebug) {
        super(mapName, key, true, false, debug, timingDebug);

        m_oplockType = newLockType;
    }

    /**
     * Run a remote task against a file state
     *
     * @param stateCache Map of paths/cluster file states
     * @param fState     ClusterFileState
     * @return Integer
     * @throws Exception Error running remote task
     */
    protected Integer runRemoteTaskAgainstState(IMap<String, ClusterFileState> stateCache, ClusterFileState fState)
            throws Exception {

        // DEBUG
        if (hasDebug())
            Debug.println("ChangeOpLockTypeTask: New type=" + m_oplockType.name() + " for state " + fState);

        // Get the oplock
        OpLockDetails oplock = fState.getOpLock();
        OpLockType newType = OpLockType.INVALID;

        if (oplock != null) {

            // Update the oplock type
            OpLockType oldOpLockType = oplock.getLockType();
            oplock.setLockType(m_oplockType);
            newType = m_oplockType;

            // DEBUG
            if (hasDebug())
                Debug.println("ChangeOpLockTypeTask: Changed type from=" + oldOpLockType.name() + " to=" + m_oplockType.name());
        }

        // Return the new oplock type, or -1 if no oplock to update
        return new Integer(newType.intValue());
    }
}
