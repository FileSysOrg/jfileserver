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

package org.filesys.server.filesys.cache.cluster;

import org.filesys.server.locking.OpLockManager;
import org.filesys.server.thread.ThreadRequestPool;
import org.filesys.smb.server.notify.NotifyChangeHandler;

/**
 * Cluster Interface
 *
 * @author gkspencer
 */
public interface ClusterInterface {

    /**
     * Start the cluster
     *
     * @exception Exception Failed to start the cluster
     */
    public void startCluster()
            throws Exception;

    /**
     * Shutdown the cluster
     *
     * @exception Exception Failed to shutdown the cluster
     */
    public void shutdownCluster()
            throws Exception;

    /**
     * Return the cluster name
     *
     * @return String
     */
    public String getClusterName();

    /**
     * Return the list of nodes
     *
     * @return ClusterNodeList
     */
    public ClusterNodeList getNodeList();

    /**
     * Return the local node details
     *
     * @return ClusterNode
     */
    public ClusterNode getLocalNode();

    /**
     * Return the associated cluster state cache
     *
     * @return ClusterFileStateCache
     */
    public ClusterFileStateCache getStateCache();

    /**
     * Return the thread pool
     *
     * @return ThreadRequestPool
     */
    public ThreadRequestPool getThreadPool();

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public boolean hasDebug();

    /**
     * Check if none existent file/folder states should be sent to the cluster
     *
     * @return boolean
     */
    public boolean hasSendNotExistStates();

    /**
     * Return the oplock manager
     *
     * @return OpLockManager
     */
    public OpLockManager getOpLockManager();

    /**
     * Return the change notification handler, if configured for the filesystem
     *
     * @return NotifyChangeHandler
     */
    public NotifyChangeHandler getNotifyChangeHandler();

    /**
     * Set the send none existent file/folder states to the cluster
     *
     * @param notExist boolean
     */
    public void setSendNotExistStates(boolean notExist);

    /**
     * Set the oplock manager
     *
     * @param oplockMgr OpLockManager
     */
    public void setOpLockManager(OpLockManager oplockMgr);

    /**
     * Set the change notification handler
     *
     * @param notifyHandler NotifyChangeHandler
     */
    public void setNotifyChangeHandler(NotifyChangeHandler notifyHandler);

    /**
     * Set the cluster node list
     *
     * @param nodeList ClusterNodeList
     */
    public void setNodeList(ClusterNodeList nodeList);

    /**
     * Set the local cluster node
     *
     * @param localNode ClusterNode
     */
    public void setLocalNode(ClusterNode localNode);
}
