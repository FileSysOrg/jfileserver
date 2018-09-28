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

import org.filesys.debug.Debug;
import org.filesys.server.filesys.DiskDeviceContext;
import org.filesys.server.filesys.DiskSharedDevice;
import org.filesys.server.filesys.cache.FileStateCache;
import org.filesys.server.filesys.cache.StateCacheException;
import org.filesys.server.locking.OpLockManager;

/**
 * Cluster File State Cache Class
 *
 * @author gkspencer
 */
public abstract class ClusterFileStateCache extends FileStateCache {

    // Cluster interface
    private ClusterInterface m_cluster;

    // List of nodes to be purged from the state cache on the next expiry pass
    private ClusterNodeList m_purgeList;

    /**
     * Class constructor
     */
    public ClusterFileStateCache() {

        // Create the purge list
        m_purgeList = new ClusterNodeList();
    }

    /**
     * Determine if the cache is a clustered cache
     *
     * @return boolean
     */
    public boolean isClusteredCache() {
        return true;
    }

    /**
     * Return the per node state for a file state, and optionally create a new per node state
     *
     * @param fState      ClusterFileState
     * @param createState boolean
     * @return PerNodeState
     */
    public abstract PerNodeState getPerNodeState(ClusterFileState fState, boolean createState);

    /**
     * Return the per node state for a file state, and optionally create a new per node state
     *
     * @param path        String
     * @param createState boolean
     * @return PerNodeState
     */
    public abstract PerNodeState getPerNodeState(String path, boolean createState);

    /**
     * Check if the file is readable for the specified section of the file and process id
     *
     * @param clState ClusterFileState
     * @param offset  long
     * @param len     long
     * @param pid     int
     * @return boolean
     */
    public abstract boolean canReadFile(ClusterFileState clState, long offset, long len, int pid);

    /**
     * Check if the file is writeable for the specified section of the file and process id
     *
     * @param clState ClusterFileState
     * @param offset  long
     * @param len     long
     * @param pid     int
     * @return boolean
     */
    public abstract boolean canWriteFile(ClusterFileState clState, long offset, long len, int pid);

    /**
     * Update a file state, notify the cluster of the updates
     *
     * @param clState    ClusterFileState
     * @param updateMask int
     */
    public abstract void updateFileState(ClusterFileState clState, int updateMask);

    /**
     * Set the filesystem driver and driver context details, if required by the cache
     *
     * @param diskDev DiskSharedDevice
     */
    public void setDriverDetails(DiskSharedDevice diskDev) {

        // We need the oplock manager details from the driver/context
        if (diskDev.getContext() != null && diskDev.getContext() instanceof DiskDeviceContext) {

            // Get the oplock manager implementation
            DiskDeviceContext diskCtx = (DiskDeviceContext) diskDev.getContext();
            OpLockManager oplockMgr = diskCtx.getOpLockManager();

            // Cluster view needs access to the oplock manager
            m_cluster.setOpLockManager(oplockMgr);

            // Cluster view can send out change notifications
            m_cluster.setNotifyChangeHandler(diskCtx.getChangeHandler());
        }
    }

    /**
     * Return the associated cluster view
     *
     * @return ClusterInterface
     */
    public final ClusterInterface getCluster() {
        return m_cluster;
    }

    /**
     * Set the associated cluster
     *
     * @param cluster ClusterInterface
     */
    protected final void setCluster(ClusterInterface cluster) {
        m_cluster = cluster;
    }

    /**
     * Cache started
     */
    public void stateCacheStarted() {

        // Inform cache listener
        if (hasStateCacheListener())
            getStateCacheListener().stateCacheInitializing();

        // Start the cluster
        if (m_cluster != null) {
            try {
                m_cluster.startCluster();
            }
            catch (Exception ex) {
                throw new StateCacheException("Failed to start cluster", ex);
            }
        }
    }

    /**
     * Cache shutting down
     */
    public void stateCacheShuttingDown() {

        // Inform cache listener
        if (hasStateCacheListener())
            getStateCacheListener().stateCacheShuttingDown();

        // Check if the state cache entries should be dumped out during shutdown
        if (hasDumpOnShutdown())
            dumpCache(false);

        // Shutdown the cluster
        if (m_cluster != null) {
            try {
                m_cluster.shutdownCluster();
            }
            catch (Exception ex) {

                // DEBUG
                if (Debug.EnableDbg && hasDebug())
                    Debug.println(ex);
            }
        }
    }

    /**
     * Add a node to the state cache purge list, as it has left the cluster
     *
     * @param clNode ClusterNode
     */
    protected final void addNodeToPurgeList(ClusterNode clNode) {
        m_purgeList.addNode(clNode);
    }

    /**
     * Cluster connection is running
     */
    public final void clusterRunning() {

        // Inform cache listener
        if (hasStateCacheListener())
            getStateCacheListener().stateCacheRunning();
    }
}
