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

package org.filesys.server.config;

import java.io.IOException;
import java.util.*;

import org.filesys.server.NetworkServer;
import org.filesys.server.NetworkServerList;
import org.filesys.util.PlatformType;


/**
 * Server Configuration Class
 *
 * <p>Provides the configuration parameters for the network file servers (SMB/CIFS, FTP and NFS).
 *
 * @author gkspencer
 */
public class ServerConfiguration implements ServerConfigurationAccessor {

    //	Constants
    //
    //  Server name
    private String m_serverName;

    //	Active server list
    private NetworkServerList m_serverList;

    //  Configuration sections
    private HashMap<String, ConfigSection> m_configSections;

    //	Configuration change listeners
    private List<ConfigurationListener> m_listeners;

    //	Flag to indicate that the server configuration has been changed
    private boolean m_updated;

    /**
     * Construct a server configuration object
     *
     * @param name String
     */
    public ServerConfiguration(String name) {

        //  Allocate the config sections table
        m_configSections = new HashMap<String, ConfigSection>();

        //	Allocate the active server list
        m_serverList = new NetworkServerList();

        // Set the server name
        m_serverName = name;
    }

    /**
     * Add a configuration section
     *
     * @param config ConfigSection
     */
    public final void addConfigSection(ConfigSection config) {

        // Add the config section
        m_configSections.put(config.getSectionName(), config);

        // Notify listeners of the new configuration section
        try {
            fireConfigurationChange(ConfigId.ConfigSection, config);
        }
        catch (InvalidConfigurationException ex) {
        }
    }

    /**
     * Check if the specified configuration section name is available
     *
     * @param name String
     * @return boolean
     */
    public final boolean hasConfigSection(String name) {
        return m_configSections.containsKey(name);
    }

    /* (non-Javadoc)
   * @see ServerConfigurationAccessor#getConfigSection(java.lang.String)
   */
    public final ConfigSection getConfigSection(String name) {
        return m_configSections.get(name);
    }

    /**
     * Remove a configuration section
     *
     * @param name String
     * @return ConfigSection
     */
    public final ConfigSection removeConfigSection(String name) {
        return m_configSections.remove(name);
    }

    /**
     * Remove all configuration sections
     */
    public final void removeAllConfigSections() {
        m_configSections.clear();
    }

    /* (non-Javadoc)
   * @see ServerConfigurationAccessor#getServerName()
   */
    public final String getServerName() {
        return m_serverName;
    }

    /**
     * Add a server to the list of active servers
     *
     * @param srv NetworkServer
     */
    public final void addServer(NetworkServer srv) {
        m_serverList.addServer(srv);
    }

    /**
     * Find an active server using the protocol name
     *
     * @param proto String
     * @return NetworkServer
     */
    public final NetworkServer findServer(String proto) {
        return m_serverList.findServer(proto);
    }

    /**
     * Remove an active server
     *
     * @param proto String
     * @return NetworkServer
     */
    public final NetworkServer removeServer(String proto) {
        final NetworkServer server = m_serverList.removeServer(proto);
        if (server instanceof ConfigurationListener) {
            removeListener((ConfigurationListener) server);
        }
        return server;
    }

    /**
     * Remove all active servers
     */
    public final void removeAllServers() {
        for (NetworkServer server : m_serverList) {
            if (server instanceof ConfigurationListener) {
                removeListener((ConfigurationListener) server);
            }
        }
        m_serverList.removeAll();
    }

    /**
     * Return the number of active servers
     *
     * @return int
     */
    public final int numberOfServers() {
        return m_serverList.numberOfServers();
    }

    /* (non-Javadoc)
     * @see ServerConfigurationAccessor#isServerRunning()
     */
    public final boolean isServerRunning(String proto) {

        //  Check if the server exists
        NetworkServer srv = findServer(proto);
        if (srv != null)
            return srv.isActive();

        return false;
    }

    /**
     * Return the platform type
     *
     * @return Platorm.Type
     */
    public final PlatformType.Type getPlatformType() {
        return PlatformType.isPlatformType();
    }

    /**
     * Return the platform type as a string
     *
     * @return String
     */
    public final String getPlatformTypeString() {
        return PlatformType.isPlatformType().toString();
    }

    /**
     * Return the server at the specified index
     *
     * @param idx int
     * @return NetworkServer
     */
    public final NetworkServer getServer(int idx) {
        return m_serverList.getServer(idx);
    }

    /**
     * Determine if the server configuration has been updated since loaded
     *
     * @return boolean
     */
    public final boolean isUpdated() {
        return m_updated;
    }

    /**
     * Set or clear the updated configuration flag
     *
     * @param upd boolean
     */
    protected final void setUpdated(boolean upd) {
        m_updated = upd;
    }

    /**
     * Set the server name
     *
     * @param name String
     */
    public final void setServerName(String name) {
        m_serverName = name;
    }

    /**
     * Add a configuration change listener
     *
     * @param listener ConfigurationListener
     */
    public final void addListener(ConfigurationListener listener) {

        //	Check if the listener list is allocated
        if (m_listeners == null)
            m_listeners = new ArrayList<ConfigurationListener>();

        //	Add the configuration change listener
        m_listeners.add(listener);
    }

    /**
     * Remove a configuration change listener
     *
     * @param listener ConfigurationListener
     */
    public final void removeListener(ConfigurationListener listener) {

        //	Check if the listener list is valid
        if (m_listeners == null)
            return;

        //	Remove the listener
        m_listeners.remove(listener);
    }

    /**
     * Check if there are any configuration change listeners
     *
     * @return boolean
     */
    public final boolean hasConfigurationListeners() {
        if (m_listeners == null)
            return false;
        return m_listeners.size() > 0 ? true : false;
    }

    /**
     * Notify all registered configuration change listeners of a configuration change
     *
     * @param id     int
     * @param newVal Object
     * @return int
     * @exception InvalidConfigurationException Error setting the configuration change
     */
    protected final int fireConfigurationChange(int id, Object newVal)
            throws InvalidConfigurationException {

        //	Set the configuration updated flag
        setUpdated(true);

        //	Check if there are any listeners registered
        if (hasConfigurationListeners() == false)
            return ConfigurationListener.StsIgnored;

        //	Inform each registered listener of the change
        int sts = ConfigurationListener.StsIgnored;

        for (int i = 0; i < m_listeners.size(); i++) {

            //	Get the current configuration change listener
            ConfigurationListener cl = m_listeners.get(i);

            //	Inform the listener of the configuration change
            int clSts = cl.configurationChanged(id, this, newVal);

            //	Keep the highest status
            if (clSts > sts)
                sts = clSts;
        }

        //	Return the change status
        return sts;
    }

    /**
     * Load the configuration from the specified location. The location depends on the
     * implementation of the configuration.
     *
     * @param location String
     * @exception IOException Error reading the configuration
     * @exception InvalidConfigurationException Error parsing the configuration
     */
    public void loadConfiguration(String location)
            throws IOException, InvalidConfigurationException {

        //	Not implemented in the base class
        throw new IOException("Not implemented");
    }

    /**
     * Save the configuration to the specified location. The location depends on the
     * implementation of the configuration.
     *
     * @param location String
     * @exception IOException Error saving the configuration
     */
    public void saveConfiguration(String location)
            throws IOException {

        //	Not implemented in the base class
        throw new IOException("Not implemented");
    }

    /**
     * Close the configuration
     */
    public void closeConfiguration() {

        // Close the configuration sections
        if (m_configSections != null) {

            Set<String> keys = m_configSections.keySet();
            Iterator<String> keysIter = keys.iterator();

            while (keysIter.hasNext()) {
                String configName = keysIter.next();
                ConfigSection configSection = m_configSections.get(configName);

                try {

                    // Close the configuration section and remove
                    configSection.closeConfig();
                }
                catch (Exception ex) {
                }
            }

            // Clear the config sections list
            m_configSections.clear();
        }
    }
}
