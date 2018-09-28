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

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.*;

import com.hazelcast.core.*;
import org.filesys.debug.Debug;
import org.filesys.locking.FileLock;
import org.filesys.locking.FileLockList;
import org.filesys.locking.LockConflictException;
import org.filesys.locking.NotLockedException;
import org.filesys.server.RequestPostProcessor;
import org.filesys.server.config.CoreServerConfigSection;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.server.filesys.*;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateProxy;
import org.filesys.server.filesys.cache.LocalFileStateProxy;
import org.filesys.server.filesys.cache.cluster.ClusterFileLock;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;
import org.filesys.server.filesys.cache.cluster.ClusterFileStateCache;
import org.filesys.server.filesys.cache.cluster.ClusterInterface;
import org.filesys.server.filesys.cache.cluster.ClusterNode;
import org.filesys.server.filesys.cache.cluster.ClusterNodeList;
import org.filesys.server.filesys.cache.cluster.PerNodeState;
import org.filesys.server.locking.LocalOpLockDetails;
import org.filesys.server.locking.OpLockDetails;
import org.filesys.server.locking.OpLockManager;
import org.filesys.server.thread.ThreadRequestPool;
import org.filesys.smb.OpLockType;
import org.filesys.smb.SharingMode;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;
import org.filesys.smb.server.notify.NotifyChangeHandler;
import org.springframework.extensions.config.ConfigElement;

/**
 * HazelCast Clustered File State Cache Class
 *
 * <p>Base class for Hazelcast v2 and v3 implementations</p>
 *
 * @author gkspencer
 */
public abstract class HazelCastClusterFileStateCache extends ClusterFileStateCache implements ClusterInterface, MembershipListener {

    // Debug levels
    public static final int DebugStateCache     = 0x00000001;    // cache get/put/remove/rename/find
    public static final int DebugExpire         = 0x00000002;    // cache expiry
    public static final int DebugNearCache      = 0x00000004;    // near cache get/put/hits
    public static final int DebugOplock         = 0x00000008;    // oplock grant/release
    public static final int DebugByteLock       = 0x00000010;    // byte range lock/unlock
    public static final int DebugFileAccess     = 0x00000020;    // file access grant/release
    public static final int DebugMembership     = 0x00000040;    // cluster membership changes
    public static final int DebugCleanup        = 0x00000080;    // cleanup when node leaves cluster
    public static final int DebugPerNode        = 0x00000100;    // per node updates
    public static final int DebugClusterEntry   = 0x00000200;    // cluster entry updates
    public static final int DebugClusterMessage = 0x00000400;    // cluster messaging
    public static final int DebugRemoteTask     = 0x00000800;    // remote tasks
    public static final int DebugRemoteTiming   = 0x00001000;    // remote task timing, key lock/unlock timing
    public static final int DebugRename         = 0x00002000;    // rename state
    public static final int DebugFileDataUpdate = 0x00004000;    // file data updates
    public static final int DebugFileStatus     = 0x00008000;    // file status changes (exist/not exist)

    // Debug level names
    //
    // Note: Must match the order of the big flags
    private static final String[] _debugLevels = {"StateCache", "Expire", "NearCache", "Oplock", "ByteLock", "FileAccess", "Membership",
            "Cleanup", "PerNode", "ClusterEntry", "ClusterMessage", "RemoteTask", "RemoteTiming",
            "Rename", "FileDataUpdate", "FileStatus"
    };

    // Hazlecast Executor service name
    protected static final String ExecutorName = "Executor";

    // Near-cache timeout values
    public static final long DefaultNearCacheTimeout = 5000L;    // 5 seconds

    public static final long MinimumNearCacheTimeout = 3000L;    // 3 seconds
    public static final long MaximumNearCacheTimeout = 120000L;    // 2 minutes

    // Update mask to disable the state update post processor
    private final int DisableAllStateUpdates = -1;

    // Cluster name, map name in HazelCast, and messaging topic name
    protected String m_clusterName;
    protected String m_topicName;

    // Cluster configuration section
    protected ClusterConfigSection m_clusterConfig;

    // HazelCast instance and cluster
    protected HazelcastInstance m_hazelCastInstance;
    protected Cluster m_cluster;

    // Clustered state cache
    protected IMap<String, HazelCastClusterFileState> m_stateCache;

    // Pub/sub message topic used to receive oplock break requests from remote nodes
    protected ITopic<ClusterMessage> m_clusterTopic;

    // Per node state cache, data that is not shared with the cluster, or cannot be shared
    protected HashMap<String, PerNodeState> m_perNodeCache;

    // Near-cache of file states being accessed via this cluster node
    protected ConcurrentHashMap<String, HazelCastClusterFileState> m_nearCache;
    protected long m_nearCacheTimeout = DefaultNearCacheTimeout;

    // Thread pool from core config
    protected ThreadRequestPool m_threadPool;

    // List of current cluster member nodes
    protected ClusterNodeList m_nodes;

    // Local cluster node
    protected ClusterNode m_localNode;

    // Local oplock manager
    protected OpLockManager m_oplockManager;

    // Change notification handler, if configured for the filesystem
    protected NotifyChangeHandler m_notifyHandler;

    // Option to send state updates for files/folders that do not exist
    protected boolean m_sendNotExist = false;

    // Debug flags
    private int m_debugFlags;

    /**
     * Class constructor
     */
    public HazelCastClusterFileStateCache() {

    }

    /**
     * Initialize the file state cache
     *
     * @param srvConfig ServerConfiguration
     * @throws InvalidConfigurationException Failed to initialize the file state cache
     */
    public void initializeCache(ConfigElement config, ServerConfiguration srvConfig)
            throws InvalidConfigurationException {

        // Call the base class
        super.initializeCache(config, srvConfig);

        // Make sure there is a valid cluster configuration
        m_clusterConfig = (ClusterConfigSection) srvConfig.getConfigSection(ClusterConfigSection.SectionName);

        if (m_clusterConfig == null)
            throw new InvalidConfigurationException("Cluster configuration not available");

        // Check if the cluster name has been specfied
        ConfigElement elem = config.getChild("clusterName");
        if (elem != null && elem.getValue() != null) {

            // Set the cluster name
            m_clusterName = elem.getValue();

            // Validate the cluster name
            if (m_clusterName == null || m_clusterName.length() == 0)
                throw new InvalidConfigurationException("Empty cluster name");
        } else
            throw new InvalidConfigurationException("Cluster name not specified");

        // Check if the cluster topic name has been specfied
        elem = config.getChild("clusterTopic");
        if (elem != null && elem.getValue() != null) {

            // Set the cluster topic name
            m_topicName = elem.getValue();

            // Validate the oplocks name
            if (m_topicName == null || m_topicName.length() == 0)
                throw new InvalidConfigurationException("Empty cluster topic name");
        } else
            throw new InvalidConfigurationException("Cluster topic name not specified");

        // Create the near-cache, unless disabled via the configuration
        elem = config.getChild("nearCache");
        boolean useNearCache = true;

        if (elem != null) {

            // Check if the near cache has been disabled
            String disableNear = elem.getAttribute("disable");

            if (Boolean.parseBoolean(disableNear) == true)
                useNearCache = false;

            // Check if the cache timeout value has been specified
            String cacheTmo = elem.getAttribute("timeout");
            try {

                // Convert, validate, the cache timeout value
                m_nearCacheTimeout = Long.parseLong(cacheTmo) * 1000L;
                if (m_nearCacheTimeout < MinimumNearCacheTimeout || m_nearCacheTimeout > MaximumNearCacheTimeout)
                    throw new InvalidConfigurationException("Near-cache timeout value out of valid range (" + MinimumNearCacheTimeout / 1000L +
                            "-" + MaximumNearCacheTimeout / 1000L + ")");
            }
            catch (NumberFormatException ex) {
                throw new InvalidConfigurationException("Invalid near-cache timeout value specified, " + cacheTmo);
            }
        }

        // Create the near cache
        if (useNearCache == true)
            m_nearCache = new ConcurrentHashMap<String, HazelCastClusterFileState>();

        // Get the global thread pool
        CoreServerConfigSection coreConfig = (CoreServerConfigSection) srvConfig.getConfigSection(CoreServerConfigSection.SectionName);
        m_threadPool = coreConfig.getThreadPool();

        // Set the cluster interface, embedded with the file state cache
        setCluster(this);

        // Create the per node state cache
        m_perNodeCache = new HashMap<String, PerNodeState>();

        // Check if debugging is enabled
        elem = config.getChild("cacheDebug");
        if (elem != null) {

            // Check for state cache debug flags
            String flags = elem.getAttribute("flags");
            int cacheDbg = 0;

            if (flags != null) {

                // Parse the flags
                flags = flags.toUpperCase();
                StringTokenizer token = new StringTokenizer(flags, ",");

                while (token.hasMoreTokens()) {

                    // Get the current debug flag token
                    String dbg = token.nextToken().trim();

                    // Find the debug flag name
                    int idx = 0;

                    while (idx < _debugLevels.length && _debugLevels[idx].equalsIgnoreCase(dbg) == false)
                        idx++;

                    if (idx >= _debugLevels.length)
                        throw new InvalidConfigurationException("Invalid state cache debug flag, " + dbg);

                    // Set the debug flag
                    cacheDbg += 1 << idx;
                }
            }

            // Set the cache debug flags
            m_debugFlags = cacheDbg;
        }
    }

    /**
     * Return the number of states in the cache
     *
     * @return int
     */
    public int numberOfStates() {
        return m_stateCache != null ? m_stateCache.size() : 0;
    }

    /**
     * Enumerate the file state cache
     *
     * @return Enumeration of cache entries
     */
    public Enumeration<String> enumerateCache() {
        return null;
    }

    /**
     * Dump the state cache entries to the debug device
     *
     * @param dumpAttribs boolean
     */
    public void dumpCache(boolean dumpAttribs) {

        // Dump the file state cache entries to the specified stream
        if (m_stateCache.size() > 0)
            Debug.println("++ HazelCastFileStateCache Entries:");

        // Dump the local keys only
        Set<String> localKeys = m_stateCache.localKeySet();

        // Check if there are any items in the cache
        if (localKeys.size() == 0)
            return;

        // Enumerate the file state cache and remove expired file state objects
        Iterator<String> keysIter = localKeys.iterator();
        long curTime = System.currentTimeMillis();

        while (keysIter.hasNext()) {
            String fname = keysIter.next();
            FileState state = m_stateCache.get(fname);

            Debug.println("++  " + fname + "(" + state.getSecondsToExpire(curTime) + ") : " + state.toString());

            // Check if the state attributes should be output
            if (dumpAttribs == true)
                state.DumpAttributes();
        }
    }

    /**
     * Return a file state proxy for the specified file state
     *
     * @param fstate FileState
     */
    public FileStateProxy getFileStateProxy(FileState fstate) {

        // Use a cluster proxy to avoid storing a reference to a copied file state object
        // Need to retrieve the file state on request, possibly from a local cache
        return new LocalFileStateProxy(fstate);
    }

    /**
     * Check if the near-cache is enabled
     *
     * @return boolean
     */
    public final boolean hasNearCache() {
        return m_nearCache != null ? true : false;
    }

    /**
     * Find the file state for the specified path
     *
     * @param path String
     * @return FileState
     */
    public FileState findFileState(String path) {
        HazelCastClusterFileState fstate = m_stateCache.get(FileState.normalizePath(path, isCaseSensitive()));

        // Set the state cache the state belongs to, may have been fetched from the cluster
        if (fstate != null) {
            fstate.setStateCache(this);
        }
        return fstate;
    }

    /**
     * Find the file state for the specified path, and optionally create a new file state if not
     * found
     *
     * @param path   String
     * @param create boolean
     * @return FileState
     */
    public FileState findFileState(String path, boolean create) {
        return findFileState(path, create, FileStatus.Unknown);
    }

    /**
     * Find the file state for the specified path, and optionally create a new file state if not
     * found with the specified initial status
     *
     * @param path   String
     * @param create boolean
     * @param status FileStatus
     * @return FileState
     */
    public FileState findFileState(String path, boolean create, FileStatus status) {

        // Normalize the path, used as the cache key
        String normPath = FileState.normalizePath(path, isCaseSensitive());

        // If the near-cache is enabled check there first
        HazelCastClusterFileState state = getStateFromNearCache(normPath);

        // If the file state was not found in the near-cache, or the near-cache is not enabled, then check the clustered cache
        if (state == null)
            state = m_stateCache.get(normPath);

        // DEBUG
        if (hasDebugLevel(DebugStateCache))
            Debug.println("findFileState path=" + path + ", create=" + create + ", sts=" + status.name() + ", state=" + state);

        // Check if we should create a new file state
        if (state == null && create == true) {

            // Create a new file state
            state = new HazelCastClusterFileState(path, isCaseSensitive());

            // Set the file state timeout and add to the cache
            state.setExpiryTime(System.currentTimeMillis() + getFileStateExpireInterval());
            if (status != FileStatus.Unknown)
                state.setFileStatus(status);

            HazelCastClusterFileState curState = m_stateCache.putIfAbsent(state.getPath(), state);

            if (curState != null) {

                // DEBUG
                if (hasDebugLevel(DebugStateCache)) {
                    Debug.println("Using existing state from putIfAbsent() returnedState=" + curState);
                    Debug.println("  newState=" + state);
                }

                // Switch to the existing file state
                state = curState;
            }

            // DEBUG
            if (hasDebugLevel(DebugStateCache))
                Debug.println("findFileState created state=" + state);

            // Add the new state to the near-cache, if enabled
            if (hasNearCache()) {

                // Set the time the state was added to the near-cache
                state.setNearCacheTime();

                // Add to the near-cache
                m_nearCache.put(normPath, state);

                // DEBUG
                if (hasDebugLevel(DebugNearCache))
                    Debug.println("Added state to near-cache state=" + state);
            }
        }

        // Set the state cache the state belongs to, may have been fetched from the cluster
        if (state != null)
            state.setStateCache(this);

        // Return the file state
        return state;
    }

    /**
     * Remove the file state for the specified path
     *
     * @param path String
     * @return FileState
     */
    public FileState removeFileState(String path) {

        // Remove the file state from the cache, and any associated per node data
        String normPath = FileState.normalizePath(path, isCaseSensitive());

        FileState state = m_stateCache.remove(normPath);
        m_perNodeCache.remove(normPath);

        // DEBUG
        if (hasDebugLevel(DebugStateCache))
            Debug.println("removeFileState path=" + path + ", state=" + state);

        // Remove from the near-cache, if enabled
        if (hasNearCache()) {
            HazelCastClusterFileState hcState = m_nearCache.remove(normPath);

            // DEBUG
            if (hasDebugLevel(DebugNearCache))
                Debug.println("Removed state from near-cache state=" + hcState);
        }

        // Check if there is a state listener
        if (hasStateListener() && state != null)
            getStateListener().fileStateClosed(state);

        // Return the removed file state
        return state;
    }

    /**
     * Rename a file state, remove the existing entry, update the path and add the state back into
     * the cache using the new path.
     *
     * @param newPath String
     * @param state   FileState
     * @param isDir   boolean
     */
    public void renameFileState(String newPath, FileState state, boolean isDir) {

        // DEBUG
        if (hasDebugLevel(DebugRename))
            Debug.println("Request rename via remote call, curPath=" + state.getPath() + ", newPath=" + newPath + ", isDir=" + isDir);

        // Save the current path
        String oldPath = state.getPath();

        // Normalize the new path
        String newPathNorm = FileState.normalizePath(newPath, isCaseSensitive());

        // Rename the state via a remote call to the node that owns the file state
        try {

            // Wait for the remote task to complete, check status
            if ( executeRenameFileState(state.getPath(), newPathNorm, isDir) == true) {

                // Normalize the new path
                String newNormPath = FileState.normalizePath(newPath, isCaseSensitive());

                // Update the per node data to the new path
                PerNodeState perNode = m_perNodeCache.remove(oldPath);
                if (perNode != null)
                    m_perNodeCache.put(newNormPath, perNode);

                // Check if there is a near-cache entry
                if (hasNearCache()) {

                    // Check if the file state is in the near-cache
                    HazelCastClusterFileState hcState = m_nearCache.remove(oldPath);

                    if (hcState != null) {

                        // Update the state path
                        hcState.setPathInternal(newNormPath);

                        // Set the file/folder status
                        hcState.setFileStatusInternal(isDir ? FileStatus.DirectoryExists : FileStatus.FileExists, FileState.ChangeReason.None);

                        // Clear all attributes from the state
                        hcState.removeAllAttributes();

                        // Add the entry back using the new path
                        m_nearCache.put(hcState.getPath(), hcState);

                        // DEBUG
                        if (hasDebugLevel(DebugNearCache))
                            Debug.println("Rename near-cache entry, from=" + oldPath + ", to=" + hcState);
                    } else {

                        // Make sure we have a cluster file state
                        if (state instanceof HazelCastClusterFileState) {

                            // Set the time the state was added to the near-cache
                            hcState = (HazelCastClusterFileState) state;
                            hcState.setNearCacheTime();
                            hcState.setPathInternal(newNormPath);

                            // Add to the near-cache
                            m_nearCache.put(newNormPath, hcState);

                            // DEBUG
                            if (hasDebugLevel(DebugNearCache))
                                Debug.println("Added state to near-cache state=" + state + " (rename)");
                        }
                    }
                }

                // Notify cluster of the rename
                StateRenameMessage stateRenameMsg = new StateRenameMessage(ClusterMessage.AllNodes, m_localNode, state.getPath(), newPath, isDir);
                m_clusterTopic.publish(stateRenameMsg);

                // DEBUG
                if (hasDebugLevel(DebugClusterMessage))
                    Debug.println("Sent file state rename to cluster, state=" + state + ", msg=" + stateRenameMsg);
            } else {

                // Rename task failed
                throw new RuntimeException("Rename state task failed, state=" + state);
            }
        }
        catch (ExecutionException ex) {

            // DEBUG
            if (hasDebugLevel(DebugRename)) {
                Debug.println("Error renaming state, fstate=" + state + ", newPath=" + newPath);
                Debug.println(ex);
            }

            // Problem executing the remote task
            throw new RuntimeException("Failed to rename state " + state.getPath(), ex);
        }
        catch (InterruptedException ex2) {

            // DEBUG
            if (hasDebugLevel(DebugRename)) {
                Debug.println("Error renaming state, fstate=" + state + ", newPath=" + newPath);
                Debug.println(ex2);
            }

            // Problem executing the remote task
            throw new RuntimeException("Failed to rename state " + state.getPath(), ex2);
        }
    }

    /**
     * Remove all file states from the cache
     */
    public void removeAllFileStates() {

        // Clear the near cache
        if (hasNearCache())
            m_nearCache.clear();

        // Clear the per-node data cache
        m_perNodeCache.clear();
    }

    /**
     * Remove expired file states from the cache
     * <p>
     * As the cache data is spread across the cluster we only expire file states that are stored on
     * the local node.
     *
     * @return int
     */
    public int removeExpiredFileStates() {

        // Only check the file states that are being stored on the local node
        if (m_stateCache == null)
            return 0;

        Set<String> localKeys = m_stateCache.localKeySet();

        // Check if there are any items in the cache
        int expiredCnt = 0;

        if (localKeys.size() > 0) {

            // DEBUG
            if (hasDebugLevel(DebugExpire))
                Debug.println("Removing expired file states from local partition");

            // Enumerate the file state cache and remove expired file state objects
            Iterator<String> keysIter = localKeys.iterator();
            long curTime = System.currentTimeMillis();

            int openCnt = 0;

            while (keysIter.hasNext()) {

                // Get the file state
                ClusterFileState state = m_stateCache.get(keysIter.next());

                if (state != null && state.isPermanentState() == false) {

                    synchronized (state) {

                        // Check if the file state has expired and there are no open references to the
                        // file
                        if (state.hasExpired(curTime) && state.getOpenCount() == 0) {

                            // Check if there is a state listener
                            if (hasStateListener() && getStateListener().fileStateExpired(state) == true) {

                                // Remove the expired file state
                                HazelCastClusterFileState hcState = m_stateCache.remove(state.getPath());

                                // Remove per node data for the expired file state
                                PerNodeState perNode = m_perNodeCache.remove(state.getPath());

                                // DEBUG
                                if (hasDebugLevel(DebugExpire))
                                    Debug.println("++ Expired file state=" + hcState + ", perNode=" + perNode);

                                // Update the expired count
                                expiredCnt++;
                            }
                        } else if (state.getOpenCount() > 0)
                            openCnt++;
                    }
                }
            }

            // DEBUG
            if (hasDebugLevel(DebugExpire)) { // && openCnt > 0) {
                Debug.println("++ Open files " + openCnt);
                dumpCache(false);
            }
        }

        // Expire states from the near-cache
        boolean nearDebug = hasDebugLevel(DebugNearCache);
        long checkTime = System.currentTimeMillis() - m_nearCacheTimeout;
        int nearExpireCnt = 0;

        if (hasNearCache() && m_nearCache.size() > 0) {

            // Iterate the near-cache
            Enumeration<String> nearEnum = m_nearCache.keys();

            while (nearEnum.hasMoreElements()) {

                // Get the current key and file state
                String nearKey = nearEnum.nextElement();
                HazelCastClusterFileState hcState = m_nearCache.get(nearKey);

                // Check if the near-cache entry has expired
                if (hcState.isStateValid() && hcState.getNearCacheLastAccessTime() < checkTime) {

                    // Remove the entry from the near-cache
                    m_nearCache.remove(nearKey);
                    nearExpireCnt++;

                    // Mark the file state as invalid
                    hcState.setStateValid(false);

                    // DEBUG
                    if (nearDebug)
                        Debug.println("Removed from near-cache state=" + hcState);
                }
            }

            // DEBUG
            if (nearDebug && nearExpireCnt > 0)
                Debug.println("Removed " + nearExpireCnt + " states from near-cache, " + m_nearCache.size() + " states remaining");
        }

        // Return the count of expired file states that were removed
        return expiredCnt;
    }

    /**
     * Return the oplock details for a file, or null if there is no oplock
     *
     * @param fstate FileState
     * @return OpLockDetails
     */
    public OpLockDetails getOpLock(FileState fstate) {

        // Check if the file has an oplock
        OpLockDetails oplock = null;

        if (fstate.hasOpLock()) {

            // Check if the oplock is local to this node
            PerNodeState perNode = m_perNodeCache.get(fstate.getPath());
            if (perNode != null && perNode.hasOpLock())
                oplock = perNode.getOpLock();

            // Check if we found a local oplock, if not then must be owned by another node
            if (oplock == null) {
                oplock = fstate.getOpLock();

                if (oplock instanceof RemoteOpLockDetails) {
                    RemoteOpLockDetails remoteOplock = (RemoteOpLockDetails) oplock;
                    ClusterNode clNode = m_nodes.findNode(remoteOplock.getOwnerName());

                    if (clNode.isLocalNode()) {
                        oplock = null;

                        // Cleanup the near cache oplock
                        HazelCastClusterFileState hcState = getStateFromNearCache(fstate.getPath());
                        if (hcState != null)
                            hcState.clearOpLock();

                        // DEBUG
                        if (hasDebugLevel(DebugOplock))
                            Debug.println("Local oplock out of sync, cleared near cache for " + fstate);
                    }
                }
            }
        }

        // Return the oplock details
        return oplock;
    }

    /**
     * Add an oplock
     *
     * @param fstate  FileState
     * @param oplock  OpLockDetails
     * @param netFile NetworkFile
     * @return boolean
     * @throws ExistingOpLockException Oplock already exists
     */
    public boolean addOpLock(FileState fstate, OpLockDetails oplock, NetworkFile netFile)
            throws ExistingOpLockException {

        // Make sure the oplock is a local oplock
        if (oplock instanceof LocalOpLockDetails == false)
            throw new RuntimeException("Attempt to add non-local oplock to file state " + fstate.getPath());

        // DEBUG
        if (hasDebugLevel(DebugOplock))
            Debug.println("Add oplock for state=" + fstate + ", oplock=" + oplock);

        // Check if the oplock has already been granted by the file access check when the file was opened/created
        ClusterFileState clState = (ClusterFileState) fstate;

        if (clState.hasLocalOpLock()) {

            // Check if the granted oplock matches the requested oplock details
            LocalOpLockDetails grantedOplock = clState.getLocalOpLock();
            LocalOpLockDetails reqOplock = (LocalOpLockDetails) oplock;

            if (reqOplock.getPath().equalsIgnoreCase(grantedOplock.getPath()) &&
                    reqOplock.getLockType() == grantedOplock.getLockType() &&
                    reqOplock.hasOplockOwner() && reqOplock.getOplockOwner().equals( grantedOplock.getOplockOwner())) {

                try {

                    // Switch to the new oplock, it contains the full details, the file id will be set later
                    // once the file open completes in the protocol layer
                    clState.clearLocalOpLock();
                    clState.setLocalOpLock(reqOplock);
                }
                catch (ExistingOpLockException ex) {
                    Debug.println(ex);
                }

                // DEBUG
                if (hasDebugLevel(DebugOplock))
                    Debug.println("Oplock already granted via file access check, oplock=" + grantedOplock);

                // Return a success status, oplock already granted, no need to make a remote call
                return true;
            }
        } else if (netFile.hasAccessToken()) {

            // Access token may indicate that the oplock is not available, no need to make a remote call
            FileAccessToken token = netFile.getAccessToken();
            if (token != null && token instanceof HazelCastAccessToken) {
                HazelCastAccessToken hcToken = (HazelCastAccessToken) token;
                if (hcToken.isOplockAvailable() == false) {

                    // DEBUG
                    if (hasDebugLevel(DebugOplock))
                        Debug.println("Oplock not available, via access token=" + hcToken);

                    // Oplock not available
                    return false;
                }
            }
        }

        // Create remote oplock details that can be stored in the cluster cache
        RemoteOpLockDetails remoteOpLock = new RemoteOpLockDetails(getLocalNode(), oplock, this);

        // DEBUG
        if (hasDebugLevel(DebugOplock))
            Debug.println("Request oplock via remote call, remoteOplock=" + remoteOpLock);

        // Add the oplock via a remote call to the node that owns the file state
        boolean sts = false;

        try {

            // Wait for the remote task to complete, check status
            if ( executeAddOpLock(fstate.getPath(), remoteOpLock) == true) {

                // Oplock added successfully, save the local oplock details in the per node data
                clState.setLocalOpLock((LocalOpLockDetails) oplock);

                // Update the near-cache
                if (hasNearCache()) {

                    // Check if the file state is in the near-cache
                    HazelCastClusterFileState hcState = getStateFromNearCache(fstate.getPath());
                    if (hcState != null) {

                        // Add the remote oplock
                        hcState.setOpLock(remoteOpLock);

                        // DEBUG
                        if (hasDebugLevel(DebugNearCache))
                            Debug.println("Added oplock to near-cache state=" + hcState);
                    }
                }

                // Indicate the oplock was added successfully
                sts = true;
            }
        }
        catch (ExecutionException ex) {

            // DEBUG
            if (hasDebugLevel(DebugOplock)) {
                Debug.println("Error adding oplock, fstate=" + fstate + ", oplock=" + oplock);
                Debug.println(ex);
            }

            // Problem executing the remote task
            throw new ExistingOpLockException("Failed to execute remote oplock add on " + fstate.getPath(), ex);
        }
        catch (InterruptedException ex2) {

            // DEBUG
            if (hasDebugLevel(DebugOplock)) {
                Debug.println("Error adding oplock, fstate=" + fstate + ", oplock=" + oplock);
                Debug.println(ex2);
            }

            // Problem executing the remote task
            throw new ExistingOpLockException("Failed to execute remote oplock add on " + fstate.getPath(), ex2);
        }

        // Return the add oplock status
        return sts;
    }

    /**
     * Clear an oplock
     *
     * @param fstate FileState
     */
    public void clearOpLock(FileState fstate) {

        // Access the cluster file state
        ClusterFileState clState = (ClusterFileState) fstate;

        // DEBUG
        if (hasDebugLevel(DebugOplock))
            Debug.println("Clear oplock for state=" + fstate);

        // Remove the oplock from local oplock list
        PerNodeState perNode = m_perNodeCache.get(clState.getPath());

        if (perNode != null && perNode.hasOpLock()) {

            // Remove the oplock using a remote call to the node that owns the file state
            IExecutorService execService = m_hazelCastInstance.getExecutorService( ExecutorName);
            Callable<Boolean> callable = new RemoveOpLockTask(getClusterName(), fstate.getPath(), hasTaskDebug(), hasTaskTiming());

            Future<Boolean> removeOpLockTask = execService.submitToKeyOwner( callable, fstate.getPath());

            try {

                // Wait for the remote task to complete
                executeClearOpLock( fstate.getPath());

                // Update the near-cache
                if (hasNearCache()) {

                    // Check if the file state is in the near-cache
                    HazelCastClusterFileState hcState = getStateFromNearCache(fstate.getPath());
                    if (hcState != null) {

                        // Remove the remote oplock details
                        hcState.clearOpLock();

                        // DEBUG
                        if (hasDebugLevel(DebugNearCache))
                            Debug.println("Cleared oplock from near-cache state=" + hcState);
                    }
                }
            }
            catch (Exception ex) {

                // Problem executing the remote task
                Debug.println(ex, Debug.Error);
            }

            // Inform cluster nodes that an oplock has been released
            OpLockMessage oplockMsg = new OpLockMessage(ClusterMessage.AllNodes, ClusterMessageType.OpLockBreakNotify, clState.getPath());
            m_clusterTopic.publish(oplockMsg);
        } else if (hasDebugLevel(DebugOplock))
            Debug.println("No local oplock found for " + fstate);
    }

    /**
     * Create a file lock object
     *
     * @param file   NetworkFile
     * @param offset long
     * @param len    long
     * @param pid    int
     * @return FileLock
     */
    public FileLock createFileLockObject(NetworkFile file, long offset, long len, int pid) {

        //	Create a lock object to represent the file lock
        return new ClusterFileLock(m_localNode, offset, len, pid);
    }

    /**
     * Check if there are active locks on this file
     *
     * @param fstate FileState
     * @return boolean
     */
    public boolean hasActiveLocks(FileState fstate) {
        return fstate.hasActiveLocks();
    }

    /**
     * Add a lock to this file
     *
     * @param fstate FileState
     * @param lock   FileLock
     * @throws LockConflictException Lock conflicts with an existing lock
     */
    public void addLock(FileState fstate, FileLock lock)
            throws LockConflictException {

        // Make sure the lock is a cluster lock
        if (lock instanceof ClusterFileLock == false)
            throw new RuntimeException("Attempt to add non-cluster byte lock to file state " + fstate.getPath());

        // DEBUG
        if (hasDebugLevel(DebugByteLock))
            Debug.println("Add byte lock for state=" + fstate + ", lock=" + lock);

        // Add the oplock via a remote call to the node that owns the file state
        try {

            // Wait for the remote task to complete
            ClusterFileState clState = executeAddLock(fstate.getPath(), (ClusterFileLock) lock);

            // Update the near-cache with the new state
            updateNearCacheState(clState);
        }
        catch (ExecutionException ex) {

            // DEBUG
            if (hasDebugLevel(DebugByteLock)) {
                Debug.println("Error adding byte lock, fstate=" + fstate + ", lock=" + lock);
                Debug.println(ex);
            }

            // Problem executing the remote task
            throw new LockConflictException("Failed to execute remote lock add on " + fstate.getPath(), ex);
        }
        catch (InterruptedException ex2) {

            // DEBUG
            if (hasDebugLevel(DebugByteLock)) {
                Debug.println("Error adding byte lock, fstate=" + fstate + ", lock=" + lock);
                Debug.println(ex2);
            }

            // Problem executing the remote task
            throw new LockConflictException("Failed to execute remote lock add on " + fstate.getPath(), ex2);
        }
    }

    /**
     * Remove a lock on this file
     *
     * @param fstate FileState
     * @param lock   FileLock
     * @throws NotLockedException File is not locked
     */
    public void removeLock(FileState fstate, FileLock lock)
            throws NotLockedException {

        // Make sure the lock is a cluster lock
        if (lock instanceof ClusterFileLock == false)
            throw new RuntimeException("Attempt to remove non-cluster byte lock from file state " + fstate.getPath());

        // DEBUG
        if (hasDebugLevel(DebugByteLock))
            Debug.println("Remove byte lock for state=" + fstate + ", lock=" + lock);

        // Remove the oplock via a remote call to the node that owns the file state
        try {

            // Wait for the remote task to complete
            ClusterFileState clState = executeRemoveLock(fstate.getPath(), (ClusterFileLock) lock);

            // Update the near-cache with the new state
            updateNearCacheState(clState);
        }
        catch (ExecutionException ex) {

            // DEBUG
            if (hasDebugLevel(DebugByteLock)) {
                Debug.println("Error removing byte lock, fstate=" + fstate + ", lock=" + lock);
                Debug.println(ex);
            }

            // Problem executing the remote task
            throw new NotLockedException("Failed to execute remote unlock add on " + fstate.getPath(), ex);
        }
        catch (InterruptedException ex2) {

            // DEBUG
            if (hasDebugLevel(DebugByteLock)) {
                Debug.println("Error removing byte lock, fstate=" + fstate + ", lock=" + lock);
                Debug.println(ex2);
            }

            // Problem executing the remote task
            throw new NotLockedException("Failed to execute remote unlock add on " + fstate.getPath(), ex2);
        }
    }

    /**
     * Start the cluster
     *
     * @throws Exception Error starting the cluster
     */
    public void startCluster()
            throws Exception {

        // DEBUG
        if (Debug.EnableDbg && hasDebug())
            Debug.println("Starting cluster, name=" + getClusterName());

        // Create/join a cluster using the specified configuration
        m_hazelCastInstance = m_clusterConfig.getHazelcastInstance();
        m_cluster = m_hazelCastInstance.getCluster();

        // Build the initial cluster node list
        rebuildClusterNodeList();

        // Add a listener to receive cluster membership events
        m_cluster.addMembershipListener(this);

        // Create the clustered state cache map
        m_stateCache = m_hazelCastInstance.getMap(getClusterName());
        if (m_stateCache == null)
            throw new Exception("Failed to initialize state cache, " + getClusterName());

        // Create the pub/sub message topic for cluster messages
        m_clusterTopic = m_hazelCastInstance.getTopic(m_topicName);
        if (m_clusterTopic == null)
            throw new Exception("Failed to initialize cluster topic, " + m_topicName);
    }

    /**
     * Shutdown the cluster
     *
     * @exception Exception Error shutting down the cluster
     */
    public void shutdownCluster()
            throws Exception {

        // DEBUG
        if (Debug.EnableDbg && hasDebug())
            Debug.println("Shutting cluster, name=" + getClusterName());

        // Hazelcast will be shutdown when the cluster configuration section is closed, it may be shared
        // by multiple components/filessytems.
    }

    /**
     * Request an oplock break
     *
     * @param path   String
     * @param oplock OpLockDetails
     * @param sess   SMBSrvSession
     * @param pkt    SMBSrvPacket
     * @throws IOException I/O error
     * @throws DeferFailedException Failed to defer the session processing
     */
    public void requestOplockBreak(String path, OpLockDetails oplock, SMBSrvSession sess, SMBSrvPacket pkt)
            throws IOException, DeferFailedException {

        // Check if the oplock is owned by the local node
        String normPath = FileState.normalizePath(path, isCaseSensitive());
        PerNodeState perNode = m_perNodeCache.get(normPath);

        if (perNode != null && perNode.hasOpLock()) {

            // Get the local oplock
            LocalOpLockDetails localOpLock = perNode.getOpLock();

            // Save the session/packet details so the request can be continued once the client owning the
            // oplock releases it
            localOpLock.addDeferredSession(sess, pkt);

            // DEBUG
            if (hasDebugLevel(DebugOplock))
                Debug.println("Request oplock break, path=" + path + ", via local oplock=" + localOpLock);

            // Request an oplock break
            localOpLock.requestOpLockBreak();
        }
        else if (oplock instanceof RemoteOpLockDetails) {

            // DEBUG
            if (hasDebugLevel(DebugOplock))
                Debug.println("Request oplock break, path=" + path + ", via remote oplock=" + oplock);

            // Remote oplock, get the oplock owner cluster member details
            RemoteOpLockDetails remoteOplock = (RemoteOpLockDetails) oplock;
            ClusterNode clNode = m_nodes.findNode(remoteOplock.getOwnerName());

            if (clNode == null) {

                // DEBUG
                if (hasDebugLevel(DebugOplock))
                    Debug.println("Cannot find node details for " + remoteOplock.getOwnerName());

                // Cannot find the node that owns the oplock
                throw new IOException("Cannot find remote oplock node details for " + remoteOplock.getOwnerName());
            } else if (clNode.isLocalNode()) {

                // Should not be the local node
                throw new IOException("Attempt to send remote oplock break to local node, path=" + path);
            }

            // Make sure the associated state cache is set for the remote oplock
            remoteOplock.setStateCache(this);

            // Save the session/packet details so the request can be continued once the client owning the
            // oplock releases it
            remoteOplock.addDeferredSession(sess, pkt);

            // Send an oplock break request to the cluster
            OpLockMessage oplockMsg = new OpLockMessage(clNode.getName(), ClusterMessageType.OpLockBreakRequest, normPath);
            m_clusterTopic.publish(oplockMsg);
        }
        else if (hasDebugLevel(DebugOplock))
            Debug.println("Unable to send oplock break, oplock=" + oplock);
    }

    /**
     * Change an oplock type
     *
     * @param oplock OpLockDetails
     * @param newTyp OpLockType
     */
    public void changeOpLockType(OpLockDetails oplock, OpLockType newTyp) {

        // DEBUG
        if (hasDebugLevel(DebugOplock))
            Debug.println("Change oplock type to=" + newTyp.name() + " for oplock=" + oplock);

        // Run the file access checks via the node that owns the file state
        String normPath = FileState.normalizePath(oplock.getPath(), isCaseSensitive());

        try {

            // Wait for the remote task to complete, get the returned oplock type
            OpLockType newOplockType = executeChangeOpLockType( normPath,  newTyp);

            // Check that the update was successful
            if (newOplockType == newTyp) {

                // Check if this node owns the oplock
                PerNodeState perNode = m_perNodeCache.get(normPath);

                if (perNode != null && perNode.hasOpLock()) {

                    // Get the local oplock
                    LocalOpLockDetails localOpLock = perNode.getOpLock();

                    // Update the local oplock type
                    localOpLock.setLockType(newTyp);
                }

                // Check if the near cache has a copy of the oplock
                if (hasNearCache()) {

                    // Check if we have the state cached in the near-cache
                    HazelCastClusterFileState hcState = getStateFromNearCache(normPath);
                    if (hcState != null) {

                        // Check if the near cache copy has the oplock details
                        if (hcState.hasOpLock()) {

                            // Update the near cache oplock details
                            hcState.getOpLock().setLockType(newTyp);

                            // DEBUG
                            if (hasDebugLevel(DebugNearCache))
                                Debug.println("Near-cache updated oplock type to=" + newTyp.name() + ", nearState=" + hcState);
                        } else {

                            // Out of sync near cache state, mark it as invalid
                            hcState.setStateValid(false);

                            // DEBUG
                            if (hasDebugLevel(DebugNearCache))
                                Debug.println("Near-cache no oplock, marked as invalid, nearState=" + hcState);
                        }
                    }
                }

                // Inform all nodes of the oplock type change
                OpLockMessage oplockMsg = new OpLockMessage(ClusterMessage.AllNodes, ClusterMessageType.OplockTypeChange, normPath);
                m_clusterTopic.publish(oplockMsg);
            } else {

                // DEBUG
                if (hasDebugLevel(DebugOplock))
                    Debug.println("Failed to change oplock type, no oplock on file state, path=" + oplock.getPath());
            }
        }
        catch (Exception ex) {

            // DEBUG
            if (hasDebugLevel(DebugOplock)) {
                Debug.println("Error changing oplock type to=" + newTyp.name() + ", for oplock=" + oplock);
                Debug.println(ex);
            }
        }
    }

    /**
     * Cluster member added
     *
     * @param membershipEvent MembershipEvent
     */
    public void memberAdded(MembershipEvent membershipEvent) {

        // DEBUG
        if (Debug.EnableDbg && hasDebugLevel(DebugMembership))
            Debug.println("Cluster added member " + membershipEvent.getMember());

        // Rebuild the cluster node list
        rebuildClusterNodeList();
    }

    /**
     * Cluster member removed
     *
     * @param membershipEvent MembershipEvent
     */
    public void memberRemoved(MembershipEvent membershipEvent) {

        // DEBUG
        if (Debug.EnableDbg && hasDebugLevel(DebugMembership))
            Debug.println("Cluster removed member " + membershipEvent.getMember());

        // Rebuild the cluster node list
        rebuildClusterNodeList();

        // Remove file state resources owned by the node that has just left the cluster, such as
        // oplocks, byte range locks
        removeMemberData(membershipEvent.getMember());
    }

    /**
     * Cluster member attributes changed
     *
     * @param attributeEvent MemberAttributeEvent
     */
    public void memberAttributeChanged(MemberAttributeEvent attributeEvent) {

    }

    /**
     * Map evicted
     *
     * @param event MapEvent
     */
    public void mapEvicted(MapEvent event) {

    }

    /**
     * Map cleared
     *
     * @param event MapEvent
     */
    public void mapCleared(MapEvent event) {

    }

    /**
     * Rebuild the cluster node list
     */
    private synchronized final void rebuildClusterNodeList() {

        // DEBUG
        if (Debug.EnableDbg && hasDebugLevel(DebugMembership))
            Debug.println("Rebuilding cluster node list");

        // Get the current node list
        ClusterNodeList curList = getNodeList();

        // Create a new list
        ClusterNodeList newList = new ClusterNodeList();

        // Get the current cluster member list
        Set<Member> members = m_cluster.getMembers();
        Iterator<Member> iterMembers = members.iterator();
        int nodeId = 1;

        while (iterMembers.hasNext()) {

            // Get the next cluster member
            Member curMember = iterMembers.next();
            ClusterNode clNode = null;
            String clName = curMember.getSocketAddress().toString();

            if (curList != null && curList.numberOfNodes() > 0) {

                // Check if the node exists in the current list
                clNode = curList.findNode(clName);
            }

            // Create a new node if not found in the current list
            if (clNode == null)
                clNode = new HazelCastClusterNode(clName, nodeId, this, curMember);
            else
                clNode.setPriority(nodeId);

            // Add the node to the new list
            newList.addNode(clNode);

            // Check for the local node
            if (clNode.isLocalNode())
                setLocalNode(clNode);

            // Update the node id
            nodeId++;
        }

        // Update the list of nodes
        setNodeList(newList);

        // DEBUG
        if (Debug.EnableDbg && hasDebugLevel(DebugMembership))
            Debug.println("  New member list: " + newList);
    }

    /**
     * Remove cluster cache data that is owned by the specified cluster member as the member has left
     * the cluster (such as file locks and oplocks).
     * <p>
     * As the cache data is spread across the cluster we remove data that is on the file states that
     * are stored on the local node.
     *
     * @param member Member
     * @return int
     */
    protected int removeMemberData(Member member) {

        // Only check the file states that are being stored on the local node
        if (m_stateCache == null)
            return 0;

        Set<String> localKeys = m_stateCache.localKeySet();

        // Check if there are any items in the cache
        if (localKeys.size() == 0)
            return 0;

        // DEBUG
        if (hasDebugLevel(DebugCleanup))
            Debug.println("Removing state data for member " + member);

        // Get the member name
        String memberName = member.toString();

        // Enumerate the file state cache and remove expired file state objects
        int stateCnt = 0;
        Iterator<String> keysIter = localKeys.iterator();

        while (keysIter.hasNext()) {

            // Get the file state
            HazelCastClusterFileState state = m_stateCache.get(keysIter.next());

            // Check if the node had the file open as the primary owner
            String primaryOwner = (String) state.getPrimaryOwner();

            if (primaryOwner != null && primaryOwner.equals(memberName)) {

                // Reduce the file open count
                if (state.getOpenCount() > 0)
                    state.decrementOpenCount();

                // Reset the shared access mode, and clear the primary owner
                state.setSharedAccess(SharingMode.ALL);
                state.setPrimaryOwner(null);

                // DEBUG
                if (hasDebugLevel(DebugCleanup))
                    Debug.println("  Cleared primary owner, state=" + state);
            }

            // Check if there are any byte range locks owned by the member
            if (state.hasActiveLocks()) {

                // Check the lock list, without locking the file state on the first pass. If there are locks
                // belonging to the member then lock and remove them in a second pass
                FileLockList lockList = state.getLockList();
                int lockCnt = 0;

                for (int idx = 0; idx < lockList.numberOfLocks(); idx++) {
                    ClusterFileLock curLock = (ClusterFileLock) lockList.getLockAt(idx);
                    if (curLock.getOwnerNode().equalsIgnoreCase(memberName))
                        lockCnt++;
                }

                // DEBUG
                if (hasDebugLevel(DebugCleanup) && lockCnt > 0)
                    Debug.println("  Removing " + lockCnt + " file locks, state=" + state);

                // If there are locks owned by the member then lock the file state and remove them in a second
                // pass of the lock list. We must get the file state again after locking as it might have been updated.
                m_stateCache.lock(state.getPath());
                state = m_stateCache.get(state.getPath());

                lockList = state.getLockList();

                int idx = 0;

                while (idx < lockList.numberOfLocks()) {

                    // Get the current lock, if is owned by the member then remove it
                    ClusterFileLock curLock = (ClusterFileLock) lockList.getLockAt(idx);
                    if (curLock.getOwnerNode().equalsIgnoreCase(memberName))
                        lockList.removeLockAt(idx);
                    else
                        idx++;
                }

                // Check the oplock whilst we have the state locked
                if (state.hasOpLock()) {

                    // Get the oplock details
                    RemoteOpLockDetails oplock = (RemoteOpLockDetails) state.getOpLock();
                    if (oplock.getOwnerName().equalsIgnoreCase(memberName)) {

                        // Remove the oplock
                        state.clearOpLock();

                        // DEBUG
                        if (hasDebugLevel(DebugCleanup))
                            Debug.println("  And removing oplock");
                    }
                }

                // Update the state in the cache, and unlock
                m_stateCache.put(state.getPath(), state);
                m_stateCache.unlock(state.getPath());

                // Increment the updated state count
                stateCnt++;
            }

            // Check if the state has an oplock owned by the member
            if (state.hasOpLock()) {

                // Get the oplock details
                RemoteOpLockDetails oplock = (RemoteOpLockDetails) state.getOpLock();
                if (oplock.getOwnerName().equalsIgnoreCase(memberName)) {

                    // DEBUG
                    if (hasDebugLevel(DebugCleanup))
                        Debug.println("  Removing oplock, state=" + state);

                    // Lock the file state and reload it, may have changed
                    m_stateCache.lock(state.getPath());
                    state = m_stateCache.get(state.getPath());

                    // Clear the oplock
                    state.clearOpLock();

                    // Update the state in the cache, and unlock
                    m_stateCache.put(state.getPath(), state);
                    m_stateCache.unlock(state.getPath());

                    // Increment the updated state count
                    stateCnt++;
                }
            }
        }

        // Return the count of file states that were updated
        return stateCnt;
    }

    /**
     * Return the per node state for a file state, and optionally create a new per node state
     *
     * @param fState      ClusterFileState
     * @param createState boolean
     * @return PerNodeState
     */
    public PerNodeState getPerNodeState(ClusterFileState fState, boolean createState) {
        PerNodeState perNode = m_perNodeCache.get(fState.getPath());
        if (perNode == null && createState == true) {
            perNode = new PerNodeState();
            m_perNodeCache.put(fState.getPath(), perNode);
        }

        return perNode;
    }

    /**
     * Return the per node state for a file path, and optionally create a new per node state
     *
     * @param path        String
     * @param createState boolean
     * @return PerNodeState
     */
    public PerNodeState getPerNodeState(String path, boolean createState) {
        String normPath = FileState.normalizePath(path, isCaseSensitive());
        PerNodeState perNode = m_perNodeCache.get(normPath);
        if (perNode == null && createState == true) {
            perNode = new PerNodeState();
            m_perNodeCache.put(normPath, perNode);
        }

        return perNode;
    }

    /**
     * Grant the required file access
     *
     * @param params  FileOpenParams
     * @param fstate  FileState
     * @param fileSts FileStatus
     * @return FileAccessToken
     * @throws FileSharingException File sharing error
     * @throws AccessDeniedException Access denied
     * @throws FileExistsException File already exists
     */
    public FileAccessToken grantFileAccess(FileOpenParams params, FileState fstate, FileStatus fileSts)
            throws FileSharingException, AccessDeniedException, FileExistsException {

        // DEBUG
        if (hasDebugLevel(DebugFileAccess))
            Debug.println("Grant file access for state=" + fstate + ", params=" + params + ", fileSts=" + fileSts.name());

        // Send a subset of the file open parameters to the remote task
        GrantAccessParams grantParams = new GrantAccessParams(getLocalNode(), params, fileSts);

        // Run the file access checks via the node that owns the file state
        HazelCastAccessToken accessToken = null;

        try {

            // Wait for the remote task to complete, get the returned access token
            accessToken = executeGrantFileAccess(fstate.getPath(), grantParams);

            // Set the associated path for the access token, and mark as not released
            accessToken.setNetworkFilePath(params.getPath());
            accessToken.setReleased(false);

            // Check if an oplock was also granted during the file access check
            if (accessToken.getOpLockType() != OpLockType.LEVEL_NONE) {

                // Create the local oplock details
                LocalOpLockDetails localOplock = new LocalOpLockDetails(accessToken.getOpLockType(), params.getFullPath(), (SMBSrvSession) params.getSession(),
                        params.getOplockOwner(), fileSts == FileStatus.DirectoryExists ? true : false);

                // Save the local oplock, in the per node data
                ClusterFileState clState = (ClusterFileState) fstate;
                clState.setLocalOpLock(localOplock);

                // Update the near-cache
                if (hasNearCache()) {

                    // Check if the file state is in the near-cache
                    HazelCastClusterFileState hcState = getStateFromNearCache(fstate.getPath());
                    if (hcState != null) {

                        // Create a remote oplock
                        RemoteOpLockDetails remoteOpLock = new RemoteOpLockDetails(getLocalNode(), localOplock, this);

                        // Add the remote oplock, set the file open count, must be one as oplock was granted
                        hcState.setOpLock(remoteOpLock);
                        hcState.setOpenCount(1);

                        // DEBUG
                        if (hasDebugLevel(DebugNearCache))
                            Debug.println("Added oplock to near-cache (via grant access) state=" + hcState);
                    }
                }
            } else if (hasNearCache()) {

                // Check if the file state is in the near-cache
                HazelCastClusterFileState hcState = getStateFromNearCache(fstate.getPath());
                if (hcState != null) {

                    // Update the file open count
                    hcState.incrementOpenCount();

                    // DEBUG
                    if (hasDebugLevel(DebugNearCache))
                        Debug.println("Update near-cache open count state=" + hcState);
                }
            }

            // Clear any state update post-processor that may be queued
            clearLowPriorityStateUpdates(DisableAllStateUpdates);
        }
        catch (ExistingOpLockException ex) {

            // Should not get this error as the remote task verified and granted the oplock

            // DEBUG
            if (hasDebugLevel(DebugFileAccess)) {
                Debug.println("Error saving oplock, fstate=" + fstate + ", params=" + params);
                Debug.println(ex);
            }
        }
        catch (ExecutionException ex) {

            // DEBUG
            if (hasDebugLevel(DebugFileAccess)) {
                Debug.println("Error granting access, fstate=" + fstate + ", params=" + params);
                Debug.println(ex);
            }

            // Problem executing the remote task
            if (ex.getCause() != null) {
                if (ex.getCause() instanceof FileSharingException)
                    throw (FileSharingException) ex.getCause();
                else if (ex.getCause() instanceof AccessDeniedException)
                    throw (AccessDeniedException) ex.getCause();
            } else
                throw new AccessDeniedException("Failed to execute remote grant access on " + fstate.getPath(), ex);
        }
        catch (InterruptedException ex) {

            // DEBUG
            if (hasDebugLevel(DebugFileAccess)) {
                Debug.println("Error granting access, fstate=" + fstate + ", params=" + params);
                Debug.println(ex);
            }

            // Problem executing the remote task
            throw new AccessDeniedException("Failed to execute remote grant access on " + fstate.getPath(), ex);
        }

        // Return the access token
        return accessToken;
    }

    /**
     * Release access to a file
     *
     * @param fstate FileState
     * @param token  FileAccessToken
     * @return int
     */
    public int releaseFileAccess(FileState fstate, FileAccessToken token) {

        // If there is no token then the file/folder was not granted access, do not update the file state
        if (token == null)
            return fstate.getOpenCount();

        // Make sure the token is from the cluster
        if (token instanceof HazelCastAccessToken == false)
            throw new RuntimeException("Attempt to release Invalid access token type=" + token.getClass().getCanonicalName() + ", file state " + fstate.getPath());

        // DEBUG
        if (hasDebugLevel(DebugFileAccess))
            Debug.println("Release file access for state=" + fstate + ", token=" + token);

        // Remove the near cached details
        if (hasNearCache())
            m_nearCache.remove(fstate.getPath());

        // Run the file access checks via the node that owns the file state
        int openCnt = -1;

        try {

            // Wait for the remote task to complete, get the updated file open count
            openCnt = executeReleaseFileAccess( fstate.getPath(), (HazelCastAccessToken) token);

            // Clear the local oplock if the token indicates an oplock on the file
            HazelCastAccessToken hcToken = (HazelCastAccessToken) token;
            hcToken.setReleased(true);

            PerNodeState perNode = m_perNodeCache.get(fstate.getPath());

            if (perNode != null && perNode.hasOpLock()) {

                // Check if the file token indicates an oplock was granted, or the file open count is now zero
                if (openCnt == 0 || hcToken.getOpLockType() != OpLockType.LEVEL_NONE) {

                    // Check if the oplock has a break in progress, the client may be closing the file to release the oplock
                    // rather than acknowledging the oplock break
                    if (perNode.getOpLock().hasBreakInProgress()) {

                        // Inform cluster nodes that an oplock has been released
                        OpLockMessage oplockMsg = new OpLockMessage(ClusterMessage.AllNodes, ClusterMessageType.OpLockBreakNotify, fstate.getPath());
                        m_clusterTopic.publish(oplockMsg);

                        // DEBUG
                        if (hasDebugLevel(DebugFileAccess | DebugOplock))
                            Debug.println("Sent oplock break notify for in-progress break, file closed to release oplock, state=" + fstate);
                    }

                    // Clear the local oplock
                    perNode.clearOpLock();

                    // DEBUG
                    if (hasDebugLevel(DebugFileAccess | DebugOplock))
                        Debug.println("Cleared local oplock during token release, token=" + token);
                }
            }

            // Update the near-cache
            if (hasNearCache()) {

                // Check if the file state is in the near-cache
                HazelCastClusterFileState hcState = getStateFromNearCache(fstate.getPath());
                if (hcState != null) {

                    // Set the open count
                    hcState.setOpenCount(openCnt);

                    // DEBUG
                    if (hasDebugLevel(DebugNearCache))
                        Debug.println("Update near-cache open count state=" + hcState);

                    // Check if the token indicates an oplock was granted, or the file count is zero
                    if (openCnt == 0 || hcToken.getOpLockType() != OpLockType.LEVEL_NONE) {

                        // Clear the oplock details
                        hcState.clearOpLock();

                        // DEBUG
                        if (hasDebugLevel(DebugNearCache))
                            Debug.println("Cleared oplock from near-cache (release token) state=" + hcState);
                    }
                }
            }
        }
        catch (Exception ex) {

            // DEBUG
            if (hasDebugLevel(DebugFileAccess)) {
                Debug.println("Error releasing access, fstate=" + fstate + ", token=" + token);
                Debug.println(ex);
            }
        }

        // Return the updated open file count
        return openCnt;
    }

    /**
     * Check if the file is readable for the specified section of the file and process id
     *
     * @param clState ClusterFileState
     * @param offset  long
     * @param len     long
     * @param pid     int
     * @return boolean
     */
    public boolean canReadFile(ClusterFileState clState, long offset, long len, int pid) {

        // Check if the file is open by multiple users
        boolean canRead = true;

        if (clState.getOpenCount() > 1) {

            // Need to check if the file is readable using a remote call, to synchronize the check
            canRead = checkFileAccess(clState, offset, len, pid, false);
        } else if (hasDebugLevel(DebugByteLock))
            Debug.println("Check file readable for state=" + clState + ", fileCount=" + clState.getOpenCount());

        // Return the read status
        return canRead;
    }

    /**
     * Check if the file is writeable for the specified section of the file and process id
     *
     * @param clState ClusterFileState
     * @param offset  long
     * @param len     long
     * @param pid     int
     * @return boolean
     */
    public boolean canWriteFile(ClusterFileState clState, long offset, long len, int pid) {

        // Check if the file is open by multiple users
        boolean canWrite = true;

        if (clState.getOpenCount() > 1) {

            // Need to check if the file is writeable using a remote call, to synchronize the check
            canWrite = checkFileAccess(clState, offset, len, pid, true);
        } else if (hasDebugLevel(DebugByteLock))
            Debug.println("Check file writeable for state=" + clState + ", fileCount=" + clState.getOpenCount());

        // Return the write status
        return canWrite;
    }

    /**
     * Check file access using a remote call
     *
     * @param clState    ClusterFileState
     * @param offset     long
     * @param len        long
     * @param pid        int
     * @param writeCheck boolean
     * @return boolean
     */
    protected boolean checkFileAccess(ClusterFileState clState, long offset, long len, int pid, boolean writeCheck) {

        // Create a lock to hold the details of the area to be checked
        ClusterFileLock checkLock = new ClusterFileLock(getLocalNode(), offset, len, pid);

        // DEBUG
        if (hasDebugLevel(DebugByteLock))
            Debug.println("Check file " + (writeCheck ? "writeable" : "readable") + " for state=" + clState + ", area=" + checkLock);

        // Check the file access via a remote call to the node that owns the file state
        boolean canAccess = false;

        try {

            // Wait for the remote task to complete

            canAccess = executeCheckFileAccess( clState.getPath(), checkLock, writeCheck);
        }
        catch (Exception ex) {

            // DEBUG
            if (hasDebugLevel(DebugByteLock)) {
                Debug.println("Error checking file access, fstate=" + clState + ", area=" + checkLock);
                Debug.println(ex);
            }
        }

        // Return the access status
        return canAccess;
    }

    /**
     * Update a file state using a remote task call
     *
     * @param clState    ClusterFileState
     * @param updateMask int
     * @return boolean
     */
    protected boolean remoteUpdateState(ClusterFileState clState, int updateMask) {

        // DEBUG
        if (hasDebugLevel(DebugRemoteTask | DebugFileStatus))
            Debug.println("Remote state update state=" + clState + ", updateMask=" + ClusterFileState.getUpdateMaskAsString(updateMask));

        // Only support file status update for now
        if (updateMask != ClusterFileState.UpdateFileStatus)
            throw new RuntimeException("Remote state update for " + ClusterFileState.getUpdateMaskAsString(updateMask) + " not supported");

        // Update the file status via a remote call to the node that owns the file state
        boolean stateUpdated = false;

        try {

            // Wait for the remote task to complete
            stateUpdated = executeRemoteUpdateState( clState.getPath(), clState.getFileStatus());

            // If the state was updated then inform cluster members of the change
            if (stateUpdated == true) {

                // Inform cluster members of the state update
                updateFileState(clState, updateMask);

                // Update the near-cache
                if (hasNearCache()) {

                    // Get the local cached value
                    HazelCastClusterFileState hcState = getStateFromNearCache(clState.getPath());
                    if (hcState != null) {

                        // Update the file status
                        hcState.setFileStatusInternal(clState.getFileStatus(), clState.getStatusChangeReason());

                        // If the status indicates the file/folder no longer exists then clear the file id, state attributes
                        if (clState.getFileStatus() == FileStatus.NotExist) {

                            // Reset the file id
                            hcState.setFileId(FileState.UnknownFileId);

                            // Clear out any state attributes
                            hcState.removeAllAttributes();
                        }

                        // DEBUG
                        if (hasDebugLevel(DebugNearCache))
                            Debug.println("Updated near-cache file status, state=" + hcState);
                    }
                }
            }
        }
        catch (Exception ex) {

            // DEBUG
            if (hasDebugLevel(DebugRemoteTask | DebugFileStatus)) {
                Debug.println("Error updating status, fstate=" + clState + ", updateMask=" + ClusterFileState.getUpdateMaskAsString(updateMask));
                Debug.println(ex);
            }
        }

        // Return the update status
        return stateUpdated;
    }

    /**
     * Update a file state, notify the cluster of the updates
     *
     * @param clState    ClusterFileState
     * @param updateMask int
     */
    public void updateFileState(ClusterFileState clState, int updateMask) {

        // Create a file status update message and broadcast to the cluster
        StateUpdateMessage stateUpdMsg = new StateUpdateMessage(ClusterMessage.AllNodes, m_localNode, clState, updateMask);
        m_clusterTopic.publish(stateUpdMsg);

        // DEBUG
        if (hasDebugLevel(DebugClusterMessage))
            Debug.println("Sent file state update to cluster, state=" + clState + ", update=" + ClusterFileState.getUpdateMaskAsString(updateMask));
    }

    /**
     * Return the cluster name
     *
     * @return String
     */
    public String getClusterName() {
        return m_clusterName;
    }

    /**
     * Return the list of nodes
     *
     * @return ClusterNodeList
     */
    public ClusterNodeList getNodeList() {
        return m_nodes;
    }

    /**
     * Return the local node details
     *
     * @return ClusterNode
     */
    public ClusterNode getLocalNode() {
        return m_localNode;
    }

    /**
     * Return the associated cluster state cache
     *
     * @return ClusterFileStateCache
     */
    public ClusterFileStateCache getStateCache() {
        return this;
    }

    /**
     * Return the thread pool
     *
     * @return ThreadRequestPool
     */
    public ThreadRequestPool getThreadPool() {
        return m_threadPool;
    }

    /**
     * Check if none existent file/folder states should be sent to the cluster
     *
     * @return boolean
     */
    public boolean hasSendNotExistStates() {
        return m_sendNotExist;
    }

    /**
     * Return the oplock manager
     *
     * @return OpLockManager
     */
    public OpLockManager getOpLockManager() {
        return m_oplockManager;
    }

    /**
     * Check if the change notification handler is set
     *
     * @return boolean
     */
    public boolean hasNotifyChangeHandler() {
        return m_notifyHandler != null ? true : false;
    }

    /**
     * Return the change notification handler, if configured for the filesystem
     *
     * @return NotifyChangeHandler
     */
    public NotifyChangeHandler getNotifyChangeHandler() {
        return m_notifyHandler;
    }

    /**
     * Set the send none existent file/folder states to the cluster
     *
     * @param notExist boolean
     */
    public void setSendNotExistStates(boolean notExist) {
        m_sendNotExist = notExist;
    }

    /**
     * Set the oplock manager
     *
     * @param oplockMgr OpLockManager
     */
    public void setOpLockManager(OpLockManager oplockMgr) {
        m_oplockManager = oplockMgr;
    }

    /**
     * Set the change notification handler
     *
     * @param notifyHandler NotifyChangeHandler
     */
    public void setNotifyChangeHandler(NotifyChangeHandler notifyHandler) {
        m_notifyHandler = notifyHandler;
    }

    /**
     * Set the cluster node list
     *
     * @param nodeList ClusterNodeList
     */
    public void setNodeList(ClusterNodeList nodeList) {
        m_nodes = nodeList;
    }

    /**
     * Set the local cluster node
     *
     * @param localNode ClusterNode
     */
    public void setLocalNode(ClusterNode localNode) {
        m_localNode = localNode;
    }

    /**
     * Check if the specified debug level is enabled
     *
     * @param flg int
     * @return boolean
     */
    public final boolean hasDebugLevel(int flg) {
        return (m_debugFlags & flg) != 0 ? true : false;
    }

    /**
     * Check if remote task debugging is enabled
     *
     * @return boolean
     */
    public final boolean hasTaskDebug() {
        return hasDebugLevel(DebugRemoteTask);
    }

    /**
     * Check if remote task timing is enabled
     *
     * @return boolean
     */
    public final boolean hasTaskTiming() {
        return hasDebugLevel(DebugRemoteTiming);
    }

    /**
     * Invoked when an entry is added to the clustered cache
     *
     * @param event entry event
     */
    public void entryAdded(EntryEvent<String, HazelCastClusterFileState> event) {

        // DEBUG
        if (hasDebugLevel(DebugClusterEntry))
            Debug.println("EntryAdded: key=" + event.getKey());
    }

    /**
     * Invoked when an entry is removed from the clustered cache
     *
     * @param event entry event
     */
    public void entryRemoved(EntryEvent<String, HazelCastClusterFileState> event) {

        // DEBUG
        if (hasDebugLevel(DebugClusterEntry))
            Debug.println("EntryRemoved: key=" + event.getKey());

        // Check if there is an entry in the local per-node cache
        PerNodeState perNode = m_perNodeCache.remove(event.getKey());

        // DEBUG
        if (perNode != null && hasDebugLevel(DebugPerNode))
            Debug.println("Removed entry " + event.getKey() + " from per-node cache (remote remove), perNode=" + perNode);

        // Check if the near-cache is enabled, remove from the near-cache
        if (hasNearCache()) {

            // Remove the state from the near-cache
            HazelCastClusterFileState hcState = m_nearCache.remove(event.getKey());

            // DEBUG
            if (hcState != null && hasDebugLevel(DebugNearCache))
                Debug.println("Removed entry from near-cache (remote remove), state=" + hcState);
        }
    }

    /**
     * Invoked when an entry is updated in the clustered cache
     *
     * @param event entry event
     */
    public void entryUpdated(EntryEvent<String, HazelCastClusterFileState> event) {

        // DEBUG
        if (hasDebugLevel(DebugClusterEntry))
            Debug.println("EntryUpdated: key=" + event.getKey());

        // If the near cache is enabled then check if we have the entry cached
        if (hasNearCache()) {

            // Check if the entry is in the near cache
            HazelCastClusterFileState hcState = getStateFromNearCache(event.getKey());
            if (hcState != null) {

                // Update the remote update time for the near cache version of the file state
                hcState.setNearRemoteUpdateTime();

                // DEBUG
                if (hasDebugLevel(DebugNearCache))
                    Debug.println("Near-cache remote update time state=" + hcState);
            }
        }
    }

    /**
     * Invoked when an entry is evicted from the clustered cache
     *
     * @param event entry event
     */
    public void entryEvicted(EntryEvent<String, HazelCastClusterFileState> event) {

        // DEBUG
        if (hasDebugLevel(DebugClusterEntry))
            Debug.println("EntryEvicted: key=" + event.getKey());

        // Check if the near-cache is enabled, remove from the near-cache
        if (hasNearCache()) {

            // Remove the state from the near-cache
            HazelCastClusterFileState hcState = m_nearCache.remove(event.getKey());

            // DEBUG
            if (hcState != null && hasDebugLevel(DebugNearCache))
                Debug.println("Removed entry " + event.getKey() + " from near-cache (remote evict), state=" + hcState);
        }
    }

    /**
     * Cluster topic message listener
     *
     * @param hzMessage ClusterMessage
     */
    public void onMessage(Message<ClusterMessage> hzMessage) {

        // Check is the message is addressed to this node, or all nodes
        ClusterMessage msg = hzMessage.getMessageObject();
        if (msg.isAllNodes() || m_localNode.nameMatches(msg.getTargetNode())) {

            // Process the message
            switch (msg.isType()) {

                // Oplock break request
                case ClusterMessageType.OpLockBreakRequest:
                    procOpLockBreakRequest((OpLockMessage) msg);
                    break;

                // Oplock break notify
                case ClusterMessageType.OpLockBreakNotify:
                    procOpLockBreakNotify((OpLockMessage) msg);
                    break;

                // Oplock type changed
                case ClusterMessageType.OplockTypeChange:
                    procOpLockTypeChange((OpLockMessage) msg);
                    break;

                // File state update
                case ClusterMessageType.FileStateUpdate:
                    procFileStateUpdate((StateUpdateMessage) msg);
                    break;

                // File state rename
                case ClusterMessageType.RenameState:
                    procFileStateRename((StateRenameMessage) msg);
                    break;

                // File data update in progress/completed
                case ClusterMessageType.DataUpdate:
                    procDataUpdate((DataUpdateMessage) msg);
                    break;

                // Unknown message type
                default:

                    // DEBUG
                    if (hasDebugLevel(DebugClusterMessage))
                        Debug.println("Unknown cluster message msg=" + msg);
                    break;
            }
        }
    }

    /**
     * Process a remote oplock break request message
     *
     * @param msg OpLockMessage
     */
    protected void procOpLockBreakRequest(OpLockMessage msg) {

        // DEBUG
        if (hasDebugLevel(DebugClusterMessage | DebugOplock))
            Debug.println("Process oplock break request msg=" + msg);

        // Check if the oplock is owned by the local node
        PerNodeState perNode = m_perNodeCache.get(msg.getPath());

        if (perNode != null && perNode.hasOpLock()) {

            // Get the local oplock
            LocalOpLockDetails localOpLock = perNode.getOpLock();

            // DEBUG
            if (hasDebugLevel(DebugClusterMessage | DebugOplock))
                Debug.println("Request oplock break, path=" + msg.getPath() + ", via local oplock=" + localOpLock);

            try {

                // Request an oplock break
                localOpLock.requestOpLockBreak();
            }
            catch (Exception ex) {

                // DEBUG
                if (hasDebugLevel(DebugClusterMessage | DebugOplock))
                    Debug.println("Oplock break failed, ex=" + ex);
            }
        } else if (hasDebugLevel(DebugClusterMessage | DebugOplock)) {

            // Send back an oplock break response to the requestor, oplock already released
            OpLockMessage oplockMsg = new OpLockMessage(msg.getFromNode(), ClusterMessageType.OpLockBreakNotify, msg.getPath());
            m_clusterTopic.publish(oplockMsg);

            // DEBUG
            Debug.println("No oplock on path=" + msg.getPath());
        }
    }

    /**
     * Process a remote oplock break notify message
     *
     * @param msg OpLockMessage
     */
    protected void procOpLockBreakNotify(OpLockMessage msg) {

        // DEBUG
        if (hasDebugLevel(DebugClusterMessage | DebugOplock))
            Debug.println("Process oplock break notify msg=" + msg);

        // Check if the path has a state in the near cache, invalidate it
        if (hasNearCache()) {

            // Check if we have the state cached in the near-cache
            HazelCastClusterFileState hcState = getStateFromNearCache(msg.getPath());
            if (hcState != null) {

                // Invalidate the near-cache entry
                hcState.setStateValid(false);
            }
        }

        // Check if the path has a pending oplock break
        PerNodeState perNode = m_perNodeCache.get(msg.getPath());

        if (perNode != null && perNode.hasDeferredSessions()) {

            // Cancel the oplock timer for this oplock
            m_oplockManager.cancelOplockTimer(msg.getPath());

            // Requeue the deferred request(s) to the thread pool, oplock released
            perNode.requeueDeferredRequests();
        }
    }

    /**
     * Process a remote oplock type change message
     *
     * @param msg OpLockMessage
     */
    protected void procOpLockTypeChange(OpLockMessage msg) {

        // DEBUG
        if (hasDebugLevel(DebugClusterMessage | DebugOplock))
            Debug.println("Process oplock change type msg=" + msg);

        // Check if the update came from the local node
        if (msg.isFromLocalNode(m_localNode) == false) {

            // Check if the path has a state in the near cache, invalidate it
            if (hasNearCache()) {

                // Check if we have the state cached in the near-cache
                HazelCastClusterFileState hcState = getStateFromNearCache(msg.getPath());
                if (hcState != null) {

                    // Invalidate the near-cache entry
                    hcState.setStateValid(false);
                }
            }

            // Check if there are any local sessions waiting on an oplock break/type change
            PerNodeState perNode = m_perNodeCache.get(msg.getPath());

            if (perNode != null && perNode.hasDeferredSessions()) {

                // Cancel the oplock timer for this oplock
                m_oplockManager.cancelOplockTimer(msg.getPath());

                // Requeue the deferred request(s) to the thread pool, oplock released
                perNode.requeueDeferredRequests();
            }

        }
    }

    /**
     * Process a remote file state update message
     *
     * @param msg StateUpdateMessage
     */
    protected void procFileStateUpdate(StateUpdateMessage msg) {

        // DEBUG
        if (hasDebugLevel(DebugClusterMessage))
            Debug.println("Process file state update msg=" + msg);

        // Check if this node owns the file state key
        //
        // Note: File status updates are done via a remote task
        HazelCastClusterFileState clState = null;

        if (isLocalKey(msg.getPath()) && msg.getUpdateMask() != ClusterFileState.UpdateFileStatus) {

            // Update the file status in the cache, need to lock/get/put/unlock
            m_stateCache.lock(msg.getPath());

            clState = m_stateCache.get(msg.getPath());

            if (clState != null) {

                // Update the file state
                clState.updateState(msg);

                // Put the updated file state back into the cluster cache
                m_stateCache.put(msg.getPath(), clState);

                // DEBUG
                if (hasDebugLevel(DebugClusterMessage))
                    Debug.println("Updated file status, state=" + clState);
            }

            // Unlock the key
            m_stateCache.unlock(msg.getPath());
        }

        // Check if the update came from the local node
        if (msg.isFromLocalNode(m_localNode) == false) {

            // Update the near-cache
            FileState.ChangeReason reason = msg.getStatusChangeReason();

            if (hasNearCache()) {

                // Check if we have the state cached in the near-cache
                HazelCastClusterFileState hcState = getStateFromNearCache(msg.getPath());
                if (hcState != null) {

                    // Update the file state
                    hcState.updateState(msg);

                    // Change a NotExist file status to Unknown, so the local node will reinitialize any per node details if required
                    if (msg.hasUpdate(ClusterFileState.UpdateFileStatus) && hcState.getFileStatus() == FileStatus.NotExist)
                        hcState.setFileStatusInternal(FileStatus.Unknown, FileState.ChangeReason.None);

                    // If a file has been deleted or a new version created then clear the file id and cached details
                    // so they are reloaded from the database
                    if (reason == FileState.ChangeReason.FileDeleted || reason == FileState.ChangeReason.FileCreated) {

                        // Clear the file id and cached file details
                        hcState.setFileId(FileState.UnknownFileId);
                        hcState.removeAllAttributes();

                        // DEBUG
                        if (hasDebugLevel(DebugNearCache))
                            Debug.println("File " + (reason == FileState.ChangeReason.FileCreated ? " Created" : "Deleted") + ", path=" + msg.getPath() + ", cleared file id/attributes");
                    }

                    // DEBUG
                    if (hasDebugLevel(DebugNearCache))
                        Debug.println("Updated near-cache file state=" + hcState);
                }
            }

            // Check if there is cached data in the per-node cache
            PerNodeState perNode = m_perNodeCache.get(msg.getPath());

            if (perNode != null) {

                // Check if a file has been deleted or a new version created, clear per node cached details
                if (reason == FileState.ChangeReason.FileDeleted || reason == FileState.ChangeReason.FileCreated) {

                    perNode.setFileId(FileState.UnknownFileId);
                    perNode.remoteAllAttributes();

                    // DEBUG
                    if (hasDebugLevel(DebugPerNode))
                        Debug.println("Reset fileId, removed attributes for path=" + msg.getPath() + ", perNode=" + perNode + ", reason=" + reason.name());
                }
            }

            // Send out change notifications
            if (hasNotifyChangeHandler()) {

                // Check for a file status update
                if (msg.hasUpdate(ClusterFileState.UpdateFileStatus) && msg.getStatusChangeReason() != FileState.ChangeReason.None) {

                    // Get the file status reason
                    FileState.ChangeReason reasonCode = msg.getStatusChangeReason();
                    String path = msg.getPath();

                    switch (reasonCode) {
                        case FileCreated:
                            getNotifyChangeHandler().notifyFileChanged(NotifyAction.Added, path);
                            break;
                        case FolderCreated:
                            getNotifyChangeHandler().notifyDirectoryChanged(NotifyAction.Added, path);
                            break;
                        case FileDeleted:
                            getNotifyChangeHandler().notifyFileChanged(NotifyAction.Removed, path);
                            break;
                        case FolderDeleted:
                            getNotifyChangeHandler().notifyDirectoryChanged(NotifyAction.Removed, path);
                            break;
                    }

                    // DEBUG
                    if (hasDebugLevel(DebugClusterMessage))
                        Debug.println("Sent change notification path=" + path + ", reason=" + reasonCode.name());
                }
            }
        }
    }

    /**
     * Process a remote file state rename message
     *
     * @param msg StateRenameMessage
     */
    protected void procFileStateRename(StateRenameMessage msg) {

        // DEBUG
        if (hasDebugLevel(DebugClusterMessage))
            Debug.println("Process file state rename msg=" + msg);

        // Check if the message is from another node
        if (msg.isFromLocalNode(m_localNode) == false) {

            // Update the per node data to the new path
            PerNodeState perNode = m_perNodeCache.remove(msg.getOldPath());
            if (perNode != null)
                m_perNodeCache.put(msg.getNewPath(), perNode);

            // Check if there is a near-cache entry
            if (hasNearCache()) {

                // Check if the file state is in the near-cache
                HazelCastClusterFileState hcState = m_nearCache.remove(msg.getOldPath());
                if (hcState != null) {

                    // Update the state path
                    hcState.setPath(msg.getNewPath(), isCaseSensitive());

                    // Remove any attributes from the near-cache copy of the state
                    hcState.removeAllAttributes();

                    // Add the entry back using the new path
                    m_nearCache.put(hcState.getPath(), hcState);

                    // DEBUG
                    if (hasDebugLevel(DebugNearCache))
                        Debug.println("Rename near-cache entry (remote), from=" + msg.getOldPath() + ", to=" + hcState);
                }
            }

            // Send out a change notification
            if (hasNotifyChangeHandler()) {

                // Inform local SMB clients of the rename
                getNotifyChangeHandler().notifyRename(msg.getOldPath(), msg.getNewPath());

                // DEBUG
                if (hasDebugLevel(DebugClusterMessage))
                    Debug.println("Sent rename change notification newPath=" + msg.getNewPath());
            }
        }

        // Check if the rename is for a folder, we need to update all locally owned states that are
        // using that path in the main cache, per node cache and near-cache
        if (msg.isFolderPath()) {

            // Get the old and new paths, make sure they are terminated correctly, and normalized
            String oldPathPrefix = msg.getOldPath();
            if (oldPathPrefix.endsWith(FileName.DOS_SEPERATOR_STR) == false)
                oldPathPrefix = oldPathPrefix + FileName.DOS_SEPERATOR_STR;
            oldPathPrefix = FileState.normalizePath(oldPathPrefix, isCaseSensitive());

            String newPathPrefix = msg.getNewPath();
            if (newPathPrefix.endsWith(FileName.DOS_SEPERATOR_STR) == false)
                newPathPrefix = newPathPrefix + FileName.DOS_SEPERATOR_STR;
            newPathPrefix = FileState.normalizePath(newPathPrefix, isCaseSensitive());

            // Iterate the locally owned keys in the main cache, check if there are any entries that use the old
            // folder path
            Set<String> localKeys = m_stateCache.localKeySet();

            // Check if there are any items in the cache
            StringBuilder newStatePath = new StringBuilder(newPathPrefix.length() + 64);
            newStatePath.append(newPathPrefix);

            if (localKeys.size() > 0) {

                // DEBUG
                if (hasDebugLevel(DebugRename))
                    Debug.println("Rename folder, checking local cache entries, oldPath=" + oldPathPrefix);

                // Enumerate the file state cache, only enumerate keys owned locally
                Iterator<String> keysIter = localKeys.iterator();

                while (keysIter.hasNext()) {

                    // Get the current local key, check if it is below the renamed path
                    String curKey = keysIter.next();
                    if (curKey.startsWith(oldPathPrefix)) {

                        // Build the new path for the file state
                        newStatePath.setLength(newPathPrefix.length());
                        newStatePath.append(curKey.substring(oldPathPrefix.length()));

                        String newPath = newStatePath.toString();

                        // We need to move the file state to point to the new parent path
                        m_stateCache.lock(curKey);
                        HazelCastClusterFileState hcState = m_stateCache.remove(curKey);

                        // Update the file state path, and store in the cache using the new path
                        hcState.setPathInternal(newPath);
                        m_stateCache.put(newPath, hcState);

                        m_stateCache.unlock(curKey);

                        // DEBUG
                        if (hasDebugLevel(DebugRename))
                            Debug.println("Renamed state path from=" + curKey + " to=" + newPath);
                    }
                }
            }

            // Update near cache entries
            if (hasNearCache()) {

                // Enumerate the near cache entries
                Enumeration<String> nearEnum = m_nearCache.keys();

                while (nearEnum.hasMoreElements()) {

                    // Get the current key, check if it is below the renamed path
                    String nearKey = nearEnum.nextElement();

                    if (nearKey.startsWith(oldPathPrefix)) {

                        // Build the new path for the file state
                        newStatePath.setLength(newPathPrefix.length());
                        newStatePath.append(nearKey.substring(oldPathPrefix.length()));

                        String newPath = newStatePath.toString();

                        // Update the file state path, and store in the cache using the new path
                        HazelCastClusterFileState hcState = m_nearCache.remove(nearKey);

                        hcState.setPathInternal(newPath);
                        m_nearCache.put(newPath, hcState);

                        // DEBUG
                        if (hasDebugLevel(DebugNearCache | DebugRename))
                            Debug.println("Renamed near-cache state from=" + nearKey + " to=" + newPath);
                    }
                }
            }
        }
    }

    /**
     * Process a remote file data update message
     *
     * @param msg DataUpdateMessage
     */
    protected void procDataUpdate(DataUpdateMessage msg) {

        // DEBUG
        if (hasDebugLevel(DebugClusterMessage))
            Debug.println("Process file data update msg=" + msg);

        // Check if the message is from another node
        if (msg.isFromLocalNode(m_localNode) == false) {

            // Check if there is a near-cache entry
            if (hasNearCache()) {

                // Check if the file state is in the near-cache
                HazelCastClusterFileState hcState = m_nearCache.remove(msg.getPath());
                if (hcState != null) {

                    // Update the state, check for start or completion of data update
                    if (msg.isStartOfUpdate()) {

                        // Store the details of the node that is updating the file data
                        hcState.setDataUpdateNode(msg.getFromNode());
                    } else {

                        // Clear the data update status, update completed
                        hcState.setDataUpdateNode(null);
                    }

                    // DEBUG
                    if (hasDebugLevel(DebugNearCache))
                        Debug.println("Data update on node=" + msg.getFromNode() + ", to=" + hcState + (msg.isStartOfUpdate() ? ", Start" : ", Completed"));
                }
            }
        }
    }

    /**
     * Check if the path is in the locally owned cache partition
     *
     * @param path String
     * @return boolean
     */
    protected boolean isLocalKey(String path) {

        // Check if the local node owns the partition that the path/key belongs to
        Partition keyPart = m_hazelCastInstance.getPartitionService().getPartition(path);
        if (keyPart.getOwner().equals((Member) m_localNode.getAddress()))
            return true;
        return false;
    }

    /**
     * Clear some, or all, low priority state updates that may be queued
     *
     * @param updateMask int
     */
    protected final void clearLowPriorityStateUpdates(int updateMask) {

        // Check if there is a state update post processor queued for this thread
        StateUpdatePostProcessor updatePostProc = (StateUpdatePostProcessor) RequestPostProcessor.findPostProcessor(StateUpdatePostProcessor.class);
        if (updatePostProc != null) {

            // Check if the state update post processor should be removed
            if (updateMask == DisableAllStateUpdates || updatePostProc.getUpdateMask() == updateMask) {

                // Remove the post processor
                RequestPostProcessor.removePostProcessorFromQueue(updatePostProc);

                // DEBUG
                if (hasDebugLevel(DebugClusterMessage))
                    Debug.println("Removed state update post processor");
            } else {

                // Remove specific state updates from being sent out at the end of request
                // processing
                updatePostProc.removeFromUpdateMask(updateMask);

                // DEBUG
                if (hasDebugLevel(DebugClusterMessage))
                    Debug.println("Removed state updates from post processor, mask=" + ClusterFileState.getUpdateMaskAsString(updateMask));
            }
        }
    }

    /**
     * Update a near-cache state with a new state received from a remote task call
     *
     * @param clState ClusterFileState
     */
    protected final void updateNearCacheState(ClusterFileState clState) {

        // Update the locally cached copy of the file state
        if (hasNearCache() && clState instanceof HazelCastClusterFileState) {

            // Check if the state is cached in the near-cache
            HazelCastClusterFileState curState = getStateFromNearCache(clState.getPath());
            HazelCastClusterFileState newState = (HazelCastClusterFileState) clState;

            // Copy near-cache details from the current state to the new state
            if (curState != null) {

                // Copy the current near-cache timeout/stats
                newState.copyNearCacheDetails(curState);
            } else {

                // Initialize the near-cache timeout
                newState.setNearCacheTime();
            }

            // Update the near-cache copy with the updated state
            m_nearCache.put(clState.getPath(), newState);

            // DEBUG
            if (hasDebugLevel(DebugNearCache))
                Debug.println("Updated near-cache from task result, state=" + newState + (curState != null ? " Copied" : "New"));
        }
    }

    /**
     * Indicate a data update is in progress for the specified file
     *
     * @param fstate FileState
     */
    public void setDataUpdateInProgress(FileState fstate) {

        // Indicate updated file data exists, update pending/running
        updateFileDataStatus((ClusterFileState) fstate, true);
    }

    /**
     * Indicate that a data update has completed for the specified file
     *
     * @param fstate FileState
     */
    public void setDataUpdateCompleted(FileState fstate) {

        // Indicate updated file data exists, update completed
        updateFileDataStatus((ClusterFileState) fstate, false);
    }

    /**
     * Update the file data update in progress status for a file state
     *
     * @param fState      ClusterFileState
     * @param startUpdate boolean
     */
    private void updateFileDataStatus(ClusterFileState fState, boolean startUpdate) {

        // DEBUG
        if (hasDebugLevel(DebugFileDataUpdate))
            Debug.println("File data update " + (startUpdate ? "started" : "completed") + " on state=" + fState);

        // Set the file data update status via a remote call to the node that owns the file state
        try {

            // Wait for the remote task to complete
            if( executeUpdateFileDataStatus( fState.getPath(), startUpdate) == true) {

                // Update the locally cached copy of the file state
                if (hasNearCache() && fState instanceof HazelCastClusterFileState) {

                    // Check if the state is cached in the near-cache
                    HazelCastClusterFileState nearState = getStateFromNearCache(fState.getPath());
                    if (nearState != null) {

                        // Update the file data update details
                        nearState.setDataUpdateNode(startUpdate ? getLocalNode() : null);

                        // DEBUG
                        if (hasDebugLevel(DebugNearCache))
                            Debug.println("Updated near-cache (file data update), state=" + nearState);
                    }
                }

                // Create a file data update message and broadcast to the cluster
                DataUpdateMessage dataUpdMsg = new DataUpdateMessage(ClusterMessage.AllNodes, m_localNode, fState.getPath(), startUpdate);
                m_clusterTopic.publish(dataUpdMsg);

                // DEBUG
                if (hasDebugLevel(DebugClusterMessage))
                    Debug.println("Sent file data update to cluster, state=" + fState + ", startUpdate=" + startUpdate);
            }
        }
        catch (Exception ex) {

            // DEBUG
            if (hasDebugLevel(DebugFileDataUpdate)) {
                Debug.println("Error setting file data update, fstate=" + fState + ", startUpdate=" + startUpdate);
                Debug.println(ex);
            }
        }
    }

    /**
     * Try and get a file state from the near cache
     *
     * @param path String
     * @return HazelCastClusterFileState
     */
    protected final HazelCastClusterFileState getStateFromNearCache(String path) {

        // Check if the near-cache is enabled
        HazelCastClusterFileState hcState = null;

        if (hasNearCache()) {

            // See if we have a local copy of the file state, and it is valid
            hcState = m_nearCache.get(path);

            if (hcState != null) {

                // If the locally cached state is valid then update the hit counter
                if (hcState.isStateValid() && hcState.hasExpired(System.currentTimeMillis()) == false) {

                    // Update the cache hit counter
                    hcState.incrementNearCacheHitCount();

                    // DEBUG
                    if (hasDebugLevel(DebugNearCache))
                        Debug.println("Found state in near-cache state=" + hcState);
                } else {

                    // Do not use the near cache state, remove it from the cache
                    hcState = null;
                    m_nearCache.remove(path);

                    // DEBUG
                    if (hasDebugLevel(DebugNearCache))
                        Debug.println("Removed invalid state from near-cache state=" + hcState);
                }
            }
        }

        // Return the file state, or null if not found/not valid
        return hcState;
    }

    /**
     * Execute a rename file state
     *
     * @param oldPath String
     * @param newPath String
     * @param isDir boolean
     * @return boolean
     * @exception InterruptedException Exceution interrupted
     * @exception ExecutionException Execution error
     */
    public abstract boolean executeRenameFileState( String oldPath, String newPath, boolean isDir)
        throws InterruptedException, ExecutionException;

    /**
     * Execute adding an oplock
     *
     * @param path String
     * @param remoteOplock RemoteOpLockDetails
     * @return boolean
     * @exception InterruptedException Exceution interrupted
     * @exception ExecutionException Execution error
     */
    public abstract boolean executeAddOpLock( String path, RemoteOpLockDetails remoteOplock)
        throws InterruptedException, ExecutionException;

    /**
     * Execute clear oplock
     *
     * @param path String
     * @exception InterruptedException Exceution interrupted
     * @exception ExecutionException Execution error
     */
    public abstract void executeClearOpLock( String path)
        throws InterruptedException, ExecutionException;

    /**
     * Execute an add lock
     *
     * @param path String
     * @param lock ClusterFileLock
     * @return ClusterFileState
     * @exception InterruptedException Exceution interrupted
     * @exception ExecutionException Execution error
     */
    public abstract ClusterFileState executeAddLock( String path, ClusterFileLock lock)
        throws InterruptedException, ExecutionException;

    /**
     * Execute a remove lock
     *
     * @param path String
     * @param lock ClusterFileLock
     * @return ClusterFileState
     * @exception InterruptedException Exceution interrupted
     * @exception ExecutionException Execution error
     */
    public abstract ClusterFileState executeRemoveLock( String path, ClusterFileLock lock)
        throws InterruptedException, ExecutionException;

    /**
     * Execute an oplock change type
     *
     * @param path String
     * @param newTyp OpLockType
     * @return OpLockType
     * @exception InterruptedException Exceution interrupted
     * @exception ExecutionException Execution error
     */
    public abstract OpLockType executeChangeOpLockType( String path, OpLockType newTyp)
        throws InterruptedException, ExecutionException;

    /**
     * Execute a grant file access
     *
     * @param path String
     * @param params GrantAccessParams
     * @return HazelCastAccessToken
     * @exception InterruptedException Exceution interrupted
     * @exception ExecutionException Execution error
     */
    public abstract HazelCastAccessToken executeGrantFileAccess( String path, GrantAccessParams params)
        throws InterruptedException, ExecutionException;

    /**
     * Execute release file access
     *
     * @param path String
     * @param token HazelCastAccessToken
     * @return int
     * @exception InterruptedException Exceution interrupted
     * @exception ExecutionException Execution error
     */
    public abstract int executeReleaseFileAccess( String path, HazelCastAccessToken token)
        throws InterruptedException, ExecutionException;

    /**
     * Execute check file access
     *
     * @param path String
     * @param chkLock ClusterFileLock
     * @param writeChk boolean
     * @return boolean
     * @exception InterruptedException Exceution interrupted
     * @exception ExecutionException Execution error
     */
    public abstract boolean executeCheckFileAccess( String path, ClusterFileLock chkLock, boolean writeChk)
        throws InterruptedException, ExecutionException;

    /**
     * Exceute a remote update state
     *
     * @param path String
     * @param fileSts FileStatus
     * @return boolean
     * @exception InterruptedException Exceution interrupted
     * @exception ExecutionException Execution error
     */
    public abstract boolean executeRemoteUpdateState( String path, FileStatus fileSts)
        throws InterruptedException, ExecutionException;

    /**
     * Execute update file data status
     *
     * @param path String
     * @param startUpdate boolean
     * @return boolean
     * @exception InterruptedException Exceution interrupted
     * @exception ExecutionException Execution error
     */
    public abstract boolean executeUpdateFileDataStatus( String path, boolean startUpdate)
        throws InterruptedException, ExecutionException;
}
