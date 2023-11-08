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
 * Remote Cache Task Class
 *
 * <p>Base class for remote cache tasks.
 *
 * @author gkspencer
 */
public abstract class RemoteCacheTask<T> implements Callable<T>, HazelcastInstanceAware, Serializable {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Task options
    public enum TaskOption {
        Debug,
        Timing
    };

    // Clustered map name
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
    public RemoteCacheTask() {
    }

    /**
     * Class constructor
     *
     * @param mapName String
     * @param key     String
     * @param options EnumSet&lt;TaskOption&gt;
     */
    public RemoteCacheTask(String mapName, String key, EnumSet<TaskOption> options) {
        m_mapName = mapName;
        m_keyName = key;

        m_taskOptions = options;
    }

    /**
     * Class constructor
     *
     * @param mapName     String
     * @param key         String
     * @param debug       boolean
     * @param timingDebug boolean
     */
    public RemoteCacheTask(String mapName, String key, boolean debug, boolean timingDebug) {
        m_mapName = mapName;
        m_keyName = key;

        m_taskOptions = EnumSet.noneOf( TaskOption.class);

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
     * Check if the specifed task option is enabled
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
     *
     * @return T
     */
    public T call()
            throws Exception {

        // DEBUG
        long startTime = 0L;
        if (hasTimingDebug())
            startTime = System.currentTimeMillis();

        // Get the clustered cache
        IMap<String, ClusterFileState> cache = getHazelcastInstance().getMap(getMapName());
        if (cache == null)
            throw new Exception("Failed to find clustered map " + getMapName());

        // Run the task
        T retVal = null;

        try {

            // Run the remote task
            retVal = runRemoteTask(cache, getKey());
        }
        finally {

            // DEBUG
            if (hasTimingDebug())
                Debug.println("Remote task executed in " + (System.currentTimeMillis() - startTime) + "ms");
        }

        // Return the task result
        return retVal;
    }

    /**
     * Run a remote task
     *
     * @param stateCache Map of paths/cluster file states
     * @param key        String
     * @return T
     * @throws Exception Error running the remote task
     */
    protected abstract T runRemoteTask(IMap<String, ClusterFileState> stateCache, String key)
            throws Exception;
}
