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
import org.filesys.server.filesys.FileStatus;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;

import com.hazelcast.core.IMap;

/**
 * Update File State Task Class
 *
 * <p>Update a file state using a synchronous update.
 *
 * @author gkspencer
 */
public class UpdateStateTask extends RemoteStateTask<Boolean> {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // File status
    private FileStatus m_fileStatus;

    /**
     * Default constructor
     */
    public UpdateStateTask() {
    }

    /**
     * Class constructor
     *
     * @param mapName     String
     * @param key         String
     * @param fileSts     FileStatus
     * @param debug       boolean
     * @param timingDebug boolean
     */
    public UpdateStateTask(String mapName, String key, FileStatus fileSts, boolean debug, boolean timingDebug) {
        super(mapName, key, true, false, debug, timingDebug);

        m_fileStatus = fileSts;
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
            Debug.println("UpdateStateTask: Update file status=" + m_fileStatus.name() + ", state=" + fState);

        // Check if the file status has changed
        boolean changedSts = false;

        if (fState.getFileStatus() != m_fileStatus) {

            // Update the file status
            fState.setFileStatusInternal(m_fileStatus, FileState.ChangeReason.None);
            changedSts = true;

            // If the status indicates the file/folder no longer exists then clear the file id, state attributes
            if (fState.getFileStatus() == FileStatus.NotExist) {

                // Reset the file id
                fState.setFileId(FileState.UnknownFileId);

                // Clear out any state attributes
                fState.removeAllAttributes();
            }

            // DEBUG
            if (hasDebug())
                Debug.println("UpdateStateTask: Status updated, state=" + fState);
        }

        // Return a status
        return new Boolean(changedSts);
    }
}
