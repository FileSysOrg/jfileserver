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
import org.filesys.server.filesys.FileAccessToken;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;
import org.filesys.smb.OpLockType;
import org.filesys.smb.SharingMode;

import com.hazelcast.core.IMap;
import com.hazelcast.core.ITopic;

/**
 * Release File Access Task Class
 *
 * <p>Release access to a file, and return the updated file open count.
 *
 * @author gkspencer
 */
public class ReleaseFileAccessTask extends RemoteStateTask<Integer> {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Access token, allocated via a grant file access call
    private FileAccessToken m_token;

    // Cluster topic used to publish file server messages to
    private String m_clusterTopic;

    /**
     * Default constructor
     */
    public ReleaseFileAccessTask() {
    }

    /**
     * Class constructor
     *
     * @param mapName      String
     * @param key          String
     * @param token        FileAccessToken
     * @param clusterTopic String
     * @param debug        boolean
     * @param timingDebug  boolean
     */
    public ReleaseFileAccessTask(String mapName, String key, FileAccessToken token, String clusterTopic, boolean debug, boolean timingDebug) {
        super(mapName, key, true, false, debug, timingDebug);

        m_token = token;
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
            Debug.println("ReleaseFileAccessTask: Release token=" + m_token + " path " + fState);

        // Get the current file open count
        int openCount = fState.getOpenCount();

        // Release the oplock
        if (m_token instanceof HazelCastAccessToken) {

            HazelCastAccessToken hcToken = (HazelCastAccessToken) m_token;

            // Decrement the file open count, unless the token is from an attributes only file open
            if (hcToken.isAttributesOnly() == false) {

                // Decrement the file open count
                openCount = fState.decrementOpenCount();

                if (openCount == 0) {

                    // Reset the sharing mode and clear the primary owner, no current file opens
                    fState.setSharedAccess(SharingMode.ALL);
                    fState.setPrimaryOwner(null);
                }
            }

            // Check if the token indicates an oplock was granted during the file open
            if (fState.hasOpLock() && hcToken.getOpLockType() != OpLockType.LEVEL_NONE) {

                // Release the remote oplock
                fState.clearOpLock();

                // Inform cluster nodes that an oplock has been released
                ITopic<ClusterMessage> clusterTopic = getHazelcastInstance().getTopic(m_clusterTopic);
                OpLockMessage oplockMsg = new OpLockMessage(ClusterMessage.AllNodes, ClusterMessageType.OpLockBreakNotify, fState.getPath());
                clusterTopic.publish(oplockMsg);

                // DEBUG
                if (hasDebug())
                    Debug.println("Cleared remote oplock during token release");
            }

            // This is a copy of the access token, mark it as released
            hcToken.setReleased(true);
        }

        // Return the new file open count
        return new Integer(openCount);
    }
}
