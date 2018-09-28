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

import org.filesys.server.filesys.cache.cluster.ClusterNode;

/**
 * File State Rename Message Class
 *
 * <p>Used to informa cluster members of a state rename. If a folder has been renamed any cached states
 * on the local node that are below the changed path will need to be updated, or deleted.
 *
 * @author gkspencer
 */
public class StateRenameMessage extends ClusterMessage {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Original and new path
    private String m_oldPath;
    private String m_newPath;

    // Indicate is path is to a folder
    private boolean m_isFolder;

    /**
     * Default constructor
     */
    public StateRenameMessage() {
    }

    /**
     * Class constructor
     *
     * @param targetNode String
     * @param fromNode   ClusterNode
     * @param oldPath    String
     * @param newPath    String
     * @param isFolder   boolean
     */
    public StateRenameMessage(String targetNode, ClusterNode fromNode, String oldPath, String newPath, boolean isFolder) {
        super(targetNode, fromNode, ClusterMessageType.RenameState);

        // Save the rename details
        m_oldPath = oldPath;
        m_newPath = newPath;

        m_isFolder = isFolder;
    }

    /**
     * Return the old state path
     *
     * @return String
     */
    public final String getOldPath() {
        return m_oldPath;
    }

    /**
     * Return the new state path
     *
     * @return String
     */
    public final String getNewPath() {
        return m_newPath;
    }

    /**
     * Check if the path is to a folder state
     *
     * @return boolean
     */
    public final boolean isFolderPath() {
        return m_isFolder;
    }

    /**
     * Return the rename state message as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(super.toString());
        str.append("fromPath=");
        str.append(getOldPath());
        str.append(",toPath=");
        str.append(getNewPath());
        str.append(",folder=");
        str.append(isFolderPath());
        str.append("]");

        return str.toString();
    }
}
