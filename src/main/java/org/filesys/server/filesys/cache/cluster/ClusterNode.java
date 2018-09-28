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
 * Cluster Node Class
 *
 * @author gkspencer
 */
public abstract class ClusterNode {

    // Node name and cluster address
    private String m_name;
    private Object m_address;

    // Flag to indicate the local node
    private boolean m_local;

    // Node state
    private int m_state;

    // Node priority
    private int m_priority;

    // Cluster that this node belongs to
    private ClusterInterface m_cluster;

    /**
     * Class constructor
     *
     * @param name     String
     * @param priority int
     * @param cluster  ClusterInterface
     * @param addr     Object
     */
    public ClusterNode(String name, int priority, ClusterInterface cluster, Object addr) {
        m_name = name;
        m_address = addr;
        m_priority = priority;
        m_cluster = cluster;
    }

    /**
     * Class constructor
     *
     * @param name      String
     * @param priority  int
     * @param localNode boolean
     * @param cluster   ClusterBase
     * @param addr      Object
     */
    public ClusterNode(String name, int priority, boolean localNode, ClusterInterface cluster, Object addr) {
        m_name = name;
        m_address = addr;
        m_priority = priority;
        m_local = localNode;
        m_cluster = cluster;
    }

    /**
     * Return the node name
     *
     * @return String
     */
    public final String getName() {
        return m_name;
    }

    /**
     * Return the cluster address for this node
     *
     * @return Object
     */
    public final Object getAddress() {
        return m_address;
    }

    /**
     * Check if this is the local node
     *
     * @return boolean
     */
    public final boolean isLocalNode() {
        return m_local;
    }

    /**
     * Return the cluster that this node belongs to
     *
     * @return ClusterInterface
     */
    public final ClusterInterface getCluster() {
        return m_cluster;
    }

    /**
     * Return the state cache that this node is associated with
     *
     * @return ClusterFileStateCache
     */
    public final ClusterFileStateCache getStateCache() {
        ClusterFileStateCache clCache = null;
        if (m_cluster != null)
            clCache = m_cluster.getStateCache();
        return clCache;
    }

    /**
     * Return the node priority
     *
     * @return int
     */
    public final int getPriority() {
        return m_priority;
    }

    /**
     * Returning the cluster node state
     *
     * @return int
     */
    public final int getState() {
        return m_state;
    }

    /**
     * Return the cluster node state as a string
     *
     * @return String
     */
    public abstract String getStateAsString();

    /**
     * Set the local node status
     *
     * @param localNode boolean
     */
    public final void setLocalNode(boolean localNode) {
        m_local = localNode;
    }

    /**
     * Set the node name
     *
     * @param name String
     */
    public final void setName(String name) {
        m_name = name;
    }

    /**
     * Set the cluster that this node belongs to
     *
     * @param cluster ClusterBase
     */
    public final void setCluster(ClusterBase cluster) {
        m_cluster = cluster;
    }

    /**
     * Set the node priority
     *
     * @param priority int
     */
    public final void setPriority(int priority) {
        m_priority = priority;
    }

    /**
     * Set the cluster node state
     *
     * @param state int
     */
    public final void setState(int state) {
        m_state = state;
    }

    /**
     * Equality check
     *
     * @param obj Object
     */
    public boolean equals(Object obj) {

        // Same object ?
        if (this == obj)
            return true;

        // Same type ?
        if (obj instanceof ClusterNode == false)
            return false;

        // Check if the cluster node is the same address/name
        ClusterNode clNode = (ClusterNode) obj;

        return clNode.getAddress().equals(getAddress());
    }

    /**
     * Check if this cluster node matches the specified name
     *
     * @param name String
     * @return boolean
     */
    public boolean nameMatches(String name) {
        return getName().equalsIgnoreCase(name);
    }

    /**
     * Return the cluster node details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(getName());
        str.append(isLocalNode() ? ":Local," : ":Remote,");
        str.append(getStateAsString());
        str.append(",Priority=");
        str.append(getPriority());
        str.append("]");

        return str.toString();
    }
}
