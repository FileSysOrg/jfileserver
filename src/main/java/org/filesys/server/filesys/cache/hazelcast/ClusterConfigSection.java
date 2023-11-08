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
import java.util.Iterator;

import com.hazelcast.config.*;
import org.filesys.server.config.ConfigSection;
import org.filesys.server.config.ServerConfiguration;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.filesys.util.StringList;

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

    // Hazelcast cluster name, if using default Hazelcast configuration
    private String m_clusterName;

    // List of IP addresses to use on the host
    private StringList m_ipAddressList;

    // Disable use of multicast to find other cluster members
    private boolean m_disableMulticast = false;

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
     * Check if the Hazelcast configuraiton file has been specified
     *
     * @return boolean
     */
    public final boolean hasConfigFile() { return m_configFile != null; }

    /**
     * Return the Hazelcast config file path
     *
     * @return String
     */
    public final String getConfigFile() {
        return m_configFile;
    }

    /**
     * Check if a cluster name has been specified
     *
     * @return boolean
     */
    public final boolean hasClusterName() { return m_clusterName != null; }

    /**
     * Return the cluster name
     *
     * @return String
     */
    public final String getClusterName() { return m_clusterName; }

    /**
     * Check if there is a list of one or more IP addresses the cluster should be configured to
     *
     * @return boolean
     */
    public final boolean hasIPAddressList() { return m_ipAddressList != null; }

    /**
     * Return the list of IP addresses the cluster should be configured to use
     *
     * @return StringList
     */
    public final StringList getIPAddressList() { return m_ipAddressList; }

    /**
     * Check if multicast discovery is disabled for the cluster
     *
     * @return boolean
     */
    public final boolean hasDisableMulticast() { return m_disableMulticast; }

    /**
     * Set the Hazelcast configuration file path
     *
     * @param path String
     */
    public final void setConfigFile(String path) {
        m_configFile = path;
    }

    /**
     * Set the cluster name, when using the default Hazelcast configuration
     *
     * @param name String
     */
    public final void setClusterName(String name) { m_clusterName = name; }

    /**
     * Add an IP address to the list of interfaces the cluster will use
     *
     * @param ipAddr String
     */
    public final void addIPAddress(String ipAddr) {
        if ( m_ipAddressList == null)
            m_ipAddressList = new StringList();
        m_ipAddressList.addString( ipAddr);
    }

    /**
     * Enable/disable the use of multicast to discover cluster members
     *
     * @param disableMulticast boolean
     */
    public final void setDisableMulticast(boolean disableMulticast) { m_disableMulticast = disableMulticast; }

    /**
     * Return the Hazelcast instance, or create it
     *
     * @param mapName String
     * @return HazelcastInstance
     * @throws FileNotFoundException File not found
     */
    public synchronized HazelcastInstance getHazelcastInstance(String mapName)
            throws FileNotFoundException {

        // Check if the Hazelcast instance has been initialized
        if (m_hazelcastInstance == null) {

            // Check if we are using an external Hazelcast configuration file
            if ( hasConfigFile()) {

                // Create the Hazelcast instance
                Config hcConfig = new FileSystemXmlConfig(getConfigFile());
                m_hazelcastInstance = Hazelcast.newHazelcastInstance(hcConfig);

                // Indicate we own the Hazelcast instance
                m_externalHazelcast = false;
            }

            // Using the default Hazelcast configuration
            else {
                // Use a default configuration
                Config hcConfig = new Config();
                hcConfig.setClusterName( getClusterName());

                // Check if the distributed map should be created
                if ( mapName != null) {

                    // Create the distributed map
                    MapConfig mapConfig = new MapConfig(mapName);
                    hcConfig.addMapConfig( mapConfig);
                }

                // Check if there is a list of IP addresses the cluster should use
                if ( hasIPAddressList()) {

                    // Create the network interface configuration
                    NetworkConfig netConfig = hcConfig.getNetworkConfig();
                    InterfacesConfig ifConfig = netConfig.getInterfaces();

                    ifConfig.setEnabled( true);

                    Iterator<String> it = getIPAddressList().iterator();
                    while ( it.hasNext()) {
                        ifConfig.addInterface( it.next());
                    }
                }

                // Check if multicast cluster discovery is disabled
                if ( hasDisableMulticast()) {
                    JoinConfig joinConfig = hcConfig.getNetworkConfig().getJoin();
                    joinConfig.getMulticastConfig().setEnabled( false);
                }

                // Create the Hazelcast instance
                m_hazelcastInstance = Hazelcast.newHazelcastInstance(hcConfig);

                // Indicate we own the Hazelcast instance
                m_externalHazelcast = false;
            }
        }

        // Return the Hazelcast instance
        return m_hazelcastInstance;
    }

    /**
     * Check if the Hazelcast instance being used is from an external source
     *
     * @return boolean
     */
    public final boolean isExternalHazelcast() {
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
            if ( !isExternalHazelcast())
                Hazelcast.shutdownAll();
        }
    }
}
