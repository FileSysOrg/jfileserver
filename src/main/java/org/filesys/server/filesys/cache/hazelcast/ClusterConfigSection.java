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

import java.io.FileNotFoundException;

import org.filesys.server.config.ConfigSection;
import org.filesys.server.config.ServerConfiguration;

import com.hazelcast.config.Config;
import com.hazelcast.config.FileSystemXmlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

/**
 * Hazelcast Cluster configuration Section Class
 *
 * @author gkspencer
 */
public class ClusterConfigSection extends ConfigSection {

    // Global configuration section name
    public static final String SectionName = "HazelcastCluster";

    //  Hazelcast cluster configuration file
    private String m_configFile;

    // Hazelcast instance shared by various components/filesystems
    private HazelcastInstance m_hazelcastInstance;

    // Flag to indicate if the Hazelcast instance is from an external source
    private boolean m_externalHazelcast;

    /**
     * Class constructor
     *
     * @param config ServerConfiguration
     */
    public ClusterConfigSection(ServerConfiguration config) {
        super(SectionName, config);
    }

    /**
     * Return the Hazelcast config file path
     *
     * @return String
     */
    public String getConfigFile() {
        return m_configFile;
    }

    /**
     * Set the Hazelcast configuration file path
     *
     * @param path String
     */
    public void setConfigFile(String path) {
        m_configFile = path;
    }

    /**
     * Return the Hazelcast instance, or create it
     *
     * @return HazelcastInstance
     * @throws FileNotFoundException File not found
     */
    public synchronized HazelcastInstance getHazelcastInstance()
            throws FileNotFoundException {

        // Check if the Hazelcast instance has been initialized
        if (m_hazelcastInstance == null) {

            // Create the Hazelcast instance
            Config hcConfig = new FileSystemXmlConfig(getConfigFile());
            m_hazelcastInstance = Hazelcast.newHazelcastInstance(hcConfig);

            // Indicate we own the Hazelcast instance
            m_externalHazelcast = false;
        }

        // Return the Hazelcast instance
        return m_hazelcastInstance;
    }

    /**
     * Check if the Hazelcast instance being used is from an external source
     *
     * @return boolean
     */
    public final boolean isExternalHazlecast() {
        return m_externalHazelcast;
    }

    /**
     * Set an external Hazelcast instance to be used
     *
     * @param hazelcast HazelcastInstance
     */
    public final void setHazelcastInstance(HazelcastInstance hazelcast) {

        // Use an external Hazelcast instance rather than creating our own
        m_externalHazelcast = true;
        m_hazelcastInstance = hazelcast;
    }

    /**
     * Close the configuration section, perform any cleanup
     */
    public void closeConfig() {

        // Close the Hazelcast instance
        if (m_hazelcastInstance != null) {

            // Clear the Hazelcast instance, shut it down if we created it
            m_hazelcastInstance = null;
            if (isExternalHazlecast() == false)
                Hazelcast.shutdownAll();
        }
    }
}
