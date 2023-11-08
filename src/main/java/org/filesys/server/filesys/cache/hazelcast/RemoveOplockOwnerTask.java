/*
 * Copyright (C) 2023 GK Spencer
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

package org.filesys.server.filesys.cache.hazelcast;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;

import com.hazelcast.map.IMap;
import org.filesys.server.locking.InvalidOplockStateException;
import org.filesys.server.locking.OpLockDetails;
import org.filesys.server.locking.OplockOwner;
import org.filesys.smb.OpLockType;

/**
 * Rename File State Task Class
 *
 * <p>Used to synchronize renaing a file state by executing on the remote node that owns the file state/key.
 *
 * @author gkspencer
 */
public class RemoveOplockOwnerTask extends RemoteStateTask<HazelCastClusterFileState> {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Oplock owner to be removed
    private OplockOwner m_owner;

    /**
     * Default constructor
     */
    public RemoveOplockOwnerTask() {
    }

    /**
     * Class constructor
     *
     * @param mapName     String
     * @param key         String
     * @param owner       OplockOwner
     * @param debug       boolean
     * @param timingDebug boolean
     */
    public RemoveOplockOwnerTask(String mapName, String key, OplockOwner owner, boolean debug, boolean timingDebug) {
        super(mapName, key, true, false, debug, timingDebug);

        m_owner = owner;
    }

    /**
     * Run a remote task against a file state
     *
     * @param stateCache Map of paths/cluster file states
     * @param fState     ClusterFileState
     * @return ClusterFileState
     * @throws Exception Error running remote task
     */
    protected HazelCastClusterFileState runRemoteTaskAgainstState(IMap<String, HazelCastClusterFileState> stateCache, HazelCastClusterFileState fState)
            throws Exception {

        // DEBUG
        if (hasDebug())
            Debug.println("RemoveOplockOwnerTask: Remove oplock owner from " + fState);

        // Remove the specified owner from the oplock
        if ( fState.hasOpLock()) {

            // Get the oplock details
            OpLockDetails oplock = fState.getOpLock();

            // For a shared level II oplock we remove the owner, there may be multiple owners
            if (oplock.getLockType() == OpLockType.LEVEL_II) {

                try {

                    // Remove the oplock owner
                    OplockOwner remOwner = oplock.removeOplockOwner( m_owner);

                    // Check if there are any remaining oplock owners
                    if (oplock.numberOfOwners() == 0) {

                        // Clear the oplock, no more owners
                        fState.clearOpLock();
                    }
                } catch (InvalidOplockStateException ex) {
                    if (Debug.hasDumpStackTraces())
                        Debug.println(ex);
                }
            } else {

                // Remove the oplock from the file state
                fState.clearOpLock();
            }

            // TODO: Send out an update message to all nodes
            // TODO: Either remove owner or clear oplock
        }

        // Return the updated file state
        return fState;
    }
}
