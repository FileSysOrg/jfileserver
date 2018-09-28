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

/**
 * Configuration Section Abstract Class
 *
 * @author gkspencer
 */
public class ConfigSection {

    // Configuration section name
    private String m_name;

    // Server configuration that this section is associated with
    private ServerConfiguration m_config;

    // Flag to indicate that this section has been updated
    private boolean m_updated;

    /**
     * Class constructor
     *
     * @param name   String
     * @param config ServerConfiguration
     */
    protected ConfigSection(String name, ServerConfiguration config) {
        m_name = name;
        m_config = config;

        if (m_config != null)
            m_config.addConfigSection(this);
    }

    /**
     * Return the configuration section name, used to identify the configuration
     *
     * @return String
     */
    public String getSectionName() {
        return m_name;
    }

    /**
     * Check if this configuration section has been updated
     *
     * @return boolean
     */
    public final boolean isUpdated() {
        return m_updated;
    }

    /**
     * Return the server configuration that this section is associated with
     *
     * @return ServerConfiguration
     */
    protected final ServerConfiguration getServerConfiguration() {
        return m_config;
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

        // Listeners are registered with the main server configuration container
        int sts = -1;

        if (m_config != null)
            sts = m_config.fireConfigurationChange(id, newVal);

        // Check if the configuration change was accepted
        if (sts >= ConfigurationListener.StsAccepted)
            setUpdated(true);

        //  Return the status
        return sts;
    }

    /**
     * Set/clear the configuration section updated flag
     *
     * @param upd boolean
     */
    protected final void setUpdated(boolean upd) {
        m_updated = upd;
    }

    /**
     * Close the configuration section, perform any cleanup
     */
    public void closeConfig() {
    }
}
