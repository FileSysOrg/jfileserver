/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
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

package org.filesys.server.filesys.cache.cluster;

/**
 * Cluster File Open Count Class
 *
 * <p>Keeps track of the file open count for a remote node
 *
 * @author gkspencer
 */
public class ClusterFileOpenCount {

    // Cluster node details
    private ClusterNode m_node;

    // Remote file open count
    private int m_openCount;

    // Link to next file open count
    private ClusterFileOpenCount m_link;

    /**
     * Class constructor
     *
     * @param node  ClusterNode
     * @param count int
     */
    public ClusterFileOpenCount(ClusterNode node, int count) {
        m_node = node;
        m_openCount = count;
    }

    /**
     * Return the cluster node
     *
     * @return ClusterNode
     */
    public final ClusterNode getNode() {
        return m_node;
    }

    /**
     * Return the remote file open count
     *
     * @return int
     */
    public final int getOpenCount() {
        return m_openCount;
    }

    /**
     * Check if there is a linked file open count object
     *
     * @return boolean
     */
    public final boolean hasLink() {
        return m_link != null ? true : false;
    }

    /**
     * Return the linked file open count object
     *
     * @return ClusterFileOpenCount
     */
    public final ClusterFileOpenCount getLink() {
        return m_link;
    }

    /**
     * Set the file open count
     *
     * @param count int
     */
    public final void setOpenCount(int count) {
        m_openCount = count;
    }

    /**
     * Set, or clear, the linked file open count object
     *
     * @param clusterCount ClusterFileOpenCount
     */
    public final void setLink(ClusterFileOpenCount clusterCount) {
        m_link = clusterCount;
    }

    /**
     * Return the cluster file open count details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(getNode().getName());
        str.append(":");
        str.append(getOpenCount());

        if (hasLink())
            str.append(" ->");
        str.append("]");

        return str.toString();
    }
}
