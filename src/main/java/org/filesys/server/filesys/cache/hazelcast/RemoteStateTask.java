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

import java.io.Serializable;
import java.util.EnumSet;
import java.util.concurrent.Callable;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.map.IMap;

/**
 * Remote File State Cache Task Class
 *
 * <p>Base class for remote file state cache tasks.
 *
 * @author gkspencer
 */
public abstract class RemoteStateTask<T> implements Callable<T>, HazelcastInstanceAware, Serializable {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Task option flags
    public enum TaskOption {
        Debug,
        LockState,
        NoUpdate,
        Timing
    };

    // Clustered map name and key
    private String m_mapName;
    private String m_keyName;

    // Hazelcast instance
    private transient HazelcastInstance m_hcInstance;

    // Task options
    private EnumSet<TaskOption> m_taskOptions;

    // Task name
    private transient String m_taskName;

    /**
     * Default constructor
     */
    public RemoteStateTask() {
    }

    /**
     * Class constructor
     *
     * @param mapName String
     * @param key     String
     * @param options EnumSet&lt;TaskOption&gt;
     */
    public RemoteStateTask(String mapName, String key, EnumSet<TaskOption> options) {
        m_mapName = mapName;
        m_keyName = key;

        m_taskOptions = options;
    }

    /**
     * Class constructor
     *
     * @param mapName     String
     * @param key         String
     * @param lockState   boolean
     * @param noUpdate    boolean
     * @param debug       boolean
     * @param timingDebug boolean
     */
    public RemoteStateTask(String mapName, String key, boolean lockState, boolean noUpdate, boolean debug, boolean timingDebug) {
        m_mapName = mapName;
        m_keyName = key;

        m_taskOptions = EnumSet.noneOf( TaskOption.class);

        if (lockState)
            m_taskOptions.add( TaskOption.LockState);

        if (noUpdate)
            m_taskOptions.add( TaskOption.NoUpdate);

        if (debug)
            m_taskOptions.add( TaskOption.Debug);

        if (timingDebug)
            m_taskOptions.add( TaskOption.Timing);
    }

    /**
     * Get the Hazelcast instance
     *
     * @return HazelcastInstance
     */
    public HazelcastInstance getHazelcastInstance() {
        return m_hcInstance;
    }

    /**
     * Set the Hazelcast instance
     *
     * @param hcInstance HazelcastInstance
     */
    public void setHazelcastInstance(HazelcastInstance hcInstance) {
        m_hcInstance = hcInstance;
    }

    /**
     * Return the clustered map name
     *
     * @return String
     */
    public final String getMapName() {
        return m_mapName;
    }

    /**
     * Return the file state key
     *
     * @return String
     */
    public final String getKey() {
        return m_keyName;
    }

    /**
     * Check if the specified task option is enabled
     *
     * @param option TaskOption
     * @return boolean
     */
    public final boolean hasOption(TaskOption option) { return m_taskOptions.contains( option); }

    /**
     * Check if debug output is enabled for this remote task
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return hasOption(TaskOption.Debug);
    }

    /**
     * Check if the timing debug output is enabled for this remote task
     *
     * @return boolean
     */
    public final boolean hasTimingDebug() {
        return hasOption(TaskOption.Timing);
    }

    /**
     * Get the task name
     *
     * @return String
     */
    public final String getTaskName() {
        if (m_taskName == null)
            m_taskName = this.getClass().getSimpleName();
        return m_taskName;
    }

    /**
     * Run the remote task
     */
    public T call()
            throws Exception {

        // DEBUG
        long startTime = 0L;
        long lockTime = 0L;
        long unlockTime = 0L;

        if (hasTimingDebug())
            startTime = System.currentTimeMillis();

        // Get the clustered cache
        IMap<String, HazelCastClusterFileState> cache = getHazelcastInstance().getMap(getMapName());
        if (cache == null)
            throw new Exception("Failed to find clustered map " + getMapName());

        // Lock the file state if required, and load the current state
        if (hasOption(TaskOption.LockState)) {

            // DEBUG
            long lockStart = 0L;
            if (hasTimingDebug())
                lockStart = System.currentTimeMillis();

            // Lock the key
            cache.lock(getKey());

            // DEBUG
            if (hasTimingDebug())
                lockTime = System.currentTimeMillis() - lockStart;
        }

        HazelCastClusterFileState fState = cache.get(getKey());

        if (fState == null) {

            // Unlock the file state key and return an error
            if (hasOption(TaskOption.LockState))
                cache.unlock(getKey());

            throw new Exception("Failed to find file state for " + getKey());
        }

        // Run the task against the file state
        T retVal = null;

        try {

            // Run the remote task
            retVal = runRemoteTaskAgainstState(cache, fState);

            // Update the file state
            if ( !hasOption(TaskOption.NoUpdate)) {
                fState.setStateValid( true);
                cache.put(getKey(), fState);

                // DEBUG
                if (hasDebug())
                    Debug.println("Remote task " + getTaskName() + " updated state=" + fState);
            }
        }
        finally {

            // Make sure the key is unlocked
            if (hasOption(TaskOption.LockState)) {

                // DEBUG
                long lockEnd = 0L;

                if (hasTimingDebug())
                    lockEnd = System.currentTimeMillis();

                // Unlock the key
                cache.unlock(getKey());

                // DEBUG
                if (hasTimingDebug())
                    unlockTime = System.currentTimeMillis() - lockEnd;
            }

            // DEBUG
            if (hasTimingDebug())
                Debug.println("Remote task " + getTaskName() + " executed in " + (System.currentTimeMillis() - startTime) + "ms (lock " + lockTime + "ms, unlock " + unlockTime + "ms)");
        }

        // Return the task result
        return retVal;
    }

    /**
     * Run a remote task against a file state
     *
     * @param stateCache Map of paths/cluster file states
     * @param fState     ClusterFileState
     * @return T
     * @throws Exception Error running remote task
     */
    protected abstract T runRemoteTaskAgainstState(IMap<String, HazelCastClusterFileState> stateCache, HazelCastClusterFileState fState)
            throws Exception;
}
