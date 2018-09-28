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
import org.filesys.server.filesys.cache.cluster.ClusterNode;

import com.hazelcast.core.IMap;

/**
 * File Data Update Remote Task Class
 *
 * <p>Used to synchronize setting/clearing the file data update in progress details on a file state by executing
 * on the remote node that owns the file state/key.
 *
 * @author gkspencer
 */
public class FileDataUpdateTask extends RemoteStateTask<Boolean> {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Node that has the updated data
    private String m_updateNode;

    // Start of update or completed update
    private boolean m_startUpdate;

    /**
     * Default constructor
     */
    public FileDataUpdateTask() {
    }

    /**
     * Class constructor
     *
     * @param mapName     String
     * @param key         String
     * @param node        ClusterNode
     * @param startUpdate boolean
     * @param debug       boolean
     * @param timingDebug boolean
     */
    public FileDataUpdateTask(String mapName, String key, ClusterNode node, boolean startUpdate, boolean debug, boolean timingDebug) {
        super(mapName, key, true, false, debug, timingDebug);

        m_updateNode = node.getName();
        m_startUpdate = startUpdate;
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
            Debug.println("FileDataUpdateTask: Update on node " + m_updateNode + " " + (m_startUpdate ? "started" : "completed") + " on " + fState);

        // Check if this is the start of the data update
        boolean updSts = false;

        if (m_startUpdate == true) {

            // Check if there is an existing data update on this file
            if (fState.hasDataUpdateInProgress()) {

                // DEBUG
                if (hasDebug())
                    Debug.println("Existing data update on state=" + fState);
            } else {

                // Set the node that has the updated file data
                fState.setDataUpdateNode(m_updateNode);
                updSts = true;

                // DEBUG
                if (hasDebug())
                    Debug.println("File data update start on node=" + m_updateNode + ", state=" + fState);
            }
        } else {

            // Check if the node matches the existing update node
            if (fState.hasDataUpdateInProgress()) {

                // Check the node
                if (fState.getDataUpdateNode().equals(m_updateNode) == false) {

                    // DEBUG
                    if (hasDebug())
                        Debug.println("Update is not the requesting node, node=" + m_updateNode + ", update=" + fState.getDataUpdateNode());
                } else {

                    // Clear the file data update, completed
                    fState.setDataUpdateNode(null);
                    updSts = true;

                    // DEBUG
                    if (hasDebug())
                        Debug.println("File data update complete on node=" + m_updateNode + ", state=" + fState);

                }
            }
        }

        // Return the updated file state
        return new Boolean(updSts);
    }
}
