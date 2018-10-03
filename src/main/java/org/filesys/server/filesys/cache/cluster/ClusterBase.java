/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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

import java.io.IOException;

import org.filesys.server.locking.OpLockManager;
import org.filesys.server.thread.ThreadRequestPool;
import org.filesys.smb.server.notify.NotifyChangeHandler;

/**
 * Cluster Interface Base Class
 *
 * @author gkspencer
 */
public abstract class ClusterBase implements ClusterInterface {

    // Default state cache cluster name
    public static final String DefaultClusterName = "JFileSrvCluster";

    // Default low priority state update interval
    public static final long DefaultLowPriorityQueueInterval = -2L;    // seconds

    // Cluster name
    private String m_clusterName = DefaultClusterName;

    // List of current cluster member nodes
    private ClusterNodeList m_nodes;

    // Local cluster node
    private ClusterNode m_localNode;

    // File state cache
    private ClusterFileStateCache m_stateCache;

    // Local oplock manager
    private OpLockManager m_oplockManager;

    // Change notification handler, if configured for the filesystem
    private NotifyChangeHandler m_notifyHandler;

    // Thread pool
    private ThreadRequestPool m_threadPool;

    // Option to send state updates for files/folders that do not exist
    private boolean m_sendNotExist = false;

    // Debug logging enable
    private boolean m_debug;

    /**
     * Class constructor
     *
     * @param stateCache  ClusterFileStateCache
     * @param clusterName String
     * @param threadPool  ThreadRequestPool
     */
    public ClusterBase(ClusterFileStateCache stateCache, String clusterName, ThreadRequestPool threadPool) {

        // Set the cluster name
        if (clusterName != null)
            m_clusterName = clusterName;

        // Set the file state cache the cluster view is associated with
        m_stateCache = stateCache;

        // Allocate the cluster node list
        m_nodes = new ClusterNodeList();

        // Set the thread pool
        m_threadPool = threadPool;

        // Copy debug setting from the cache
        m_debug = stateCache.hasDebug();
    }

    /**
     * Start the cluster
     *
     * @throws Exception Failed to start the cluster
     */
    public abstract void startCluster()
            throws Exception;

    /**
     * Shutdown the cluster
     *
     * @throws Exception Failed to shutdown the cluster
     */
    public abstract void shutdownCluster()
            throws Exception;

    /**
     * Return the cluster name
     *
     * @return String
     */
    public final String getClusterName() {
        return m_clusterName;
    }

    /**
     * Return the list of nodes
     *
     * @return ClusterNodeList
     */
    public final ClusterNodeList getNodeList() {
        return m_nodes;
    }

    /**
     * Return the local node details
     *
     * @return ClusterNode
     */
    public final ClusterNode getLocalNode() {
        return m_localNode;
    }

    /**
     * Return the associated cluster state cache
     *
     * @return ClusterFileStateCache
     */
    public final ClusterFileStateCache getStateCache() {
        return m_stateCache;
    }

    /**
     * Return the thread pool
     *
     * @return ThreadRequestPool
     */
    public final ThreadRequestPool getThreadPool() {
        return m_threadPool;
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Check if none existent file/folder states should be sent to the cluster
     *
     * @return boolean
     */
    public final boolean hasSendNotExistStates() {
        return m_sendNotExist;
    }

    /**
     * Return the oplock manager
     *
     * @return OpLockManager
     */
    public final OpLockManager getOpLockManager() {
        return m_oplockManager;
    }

    /**
     * Return the change notification handler, if configured for the filesystem
     *
     * @return NotifyChangeHandler
     */
    public final NotifyChangeHandler getNotifyChangeHandler() {
        return m_notifyHandler;
    }

    /**
     * Set the send none existent file/folder states to the cluster
     *
     * @param notExist boolean
     */
    public final void setSendNotExistStates(boolean notExist) {
        m_sendNotExist = notExist;
    }

    /**
     * Set the oplock manager
     *
     * @param oplockMgr OpLockManager
     */
    public final void setOpLockManager(OpLockManager oplockMgr) {
        m_oplockManager = oplockMgr;
    }

    /**
     * Set the change notification handler
     *
     * @param notifyHandler NotifyChangeHandler
     */
    public final void setNotifyChangeHandler(NotifyChangeHandler notifyHandler) {
        m_notifyHandler = notifyHandler;
    }

    /**
     * Set the cluster node list
     *
     * @param nodeList ClusterNodeList
     */
    public final void setNodeList(ClusterNodeList nodeList) {
        m_nodes = nodeList;
    }

    /**
     * Set the local cluster node
     *
     * @param localNode ClusterNode
     */
    public final void setLocalNode(ClusterNode localNode) {
        m_localNode = localNode;
    }

    /**
     * Request an oplock break
     *
     * @param clNode  ClusterNode
     * @param clState ClusterFileState
     * @throws IOException Error processing oplock break
     */
    public abstract void requestOplockBreak(ClusterNode clNode, ClusterFileState clState)
            throws IOException;
}