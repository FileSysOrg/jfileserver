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
import org.filesys.server.filesys.*;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateCache;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;
import org.filesys.server.locking.OpLockDetails;
import org.filesys.smb.ImpersonationLevel;
import org.filesys.smb.OpLockType;
import org.filesys.smb.SharingMode;

import com.hazelcast.map.IMap;

/**
 * Grant File Access Task Class
 *
 * <p>Check if the specified file can be accessed using the requested sharing mode, access mode. Return
 * a file sharing exception if the access cannot be granted.
 *
 * @author gkspencer
 */
public class GrantFileAccessTask extends RemoteStateTask<GrantAccessResponse> {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // File open parameters
    private GrantAccessParams m_params;

    /**
     * Default constructor
     */
    public GrantFileAccessTask() {
    }

    /**
     * Class constructor
     *
     * @param mapName     String
     * @param key         String
     * @param params      GrantAccessParams
     * @param debug       boolean
     * @param timingDebug boolean
     */
    public GrantFileAccessTask(String mapName, String key, GrantAccessParams params, boolean debug, boolean timingDebug) {
        super(mapName, key, true, false, debug, timingDebug);

        m_params = params;
    }

    /**
     * Run a remote task against a file state
     *
     * @param stateCache Map of paths/cluster file states
     * @param fState     HazelCastFileState
     * @return GrantAccessResponse
     * @throws Exception Error running remote task
     */
    protected GrantAccessResponse runRemoteTaskAgainstState(IMap<String, HazelCastClusterFileState> stateCache, HazelCastClusterFileState fState)
            throws Exception {

        // DEBUG
        if (hasDebug())
            Debug.println("GrantFileAccessTask: Open params=" + m_params + " state=" + fState);

        // Check if the current file open allows the required shared access
        OpLockType availOplock = OpLockType.LEVEL_NONE;
        HazelCastAccessToken hcToken = null;

        // Get the current oplock details
        OpLockDetails curOplock = fState.getOpLock();

        // Check if the file can be accessed with the requested access mode and sharing mode
        if (FileStateCache.checkFileAccess( m_params.asFileOpenParams(), fState, m_params.getFileStatus(), hasDebug())) {

            // Check if an oplock was requested
            if ( m_params.hasOpLockRequest() && !m_params.isDirectory()) {

                // For a batch oplock this should be the first file open
                if ( m_params.getOpLockType() == OpLockType.LEVEL_BATCH) {

                    // Check if this is the first file open
                    if ( fState.getOpenCount() == 0) {

                        // Batch oplock is available
                        availOplock = OpLockType.LEVEL_BATCH;
                    }
                    else if ( curOplock != null && curOplock.isLevelIIOplock()) {

                        // Shared Level II oplock available
                        availOplock = OpLockType.LEVEL_II;
                    }
                }
                else if ( m_params.getOpLockType() == OpLockType.LEVEL_II) {

                    // Check if this is the first file open or there is an existing Level II oplock on the file
                    if ( fState.getOpenCount() == 0 || curOplock != null && curOplock.getLockType() == OpLockType.LEVEL_II) {

                        // Shared Level II oplock is available
                        availOplock = OpLockType.LEVEL_II;
                    }
                }
            }

            // Create an access token for the file open
            hcToken = new HazelCastAccessToken(m_params.getOwnerName(), m_params, availOplock);
            hcToken.setReleased(true);

            // Add the file access token to the file state
            fState.addAccessToken( hcToken);

            // Set the file status
            if (m_params.getFileStatus() != FileStatus.Unknown)
                fState.setFileStatusInternal(m_params.getFileStatus(), FileState.ChangeReason.None);
        }

        // DEBUG
        if ( hasDebug())
            Debug.println("GrantFileAccessTask: Returning access token=" + hcToken + ", state=" + fState);

        // Return the file access token and updated cluster file state
        return new GrantAccessResponse( hcToken, fState);
    }
}
