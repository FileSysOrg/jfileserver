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

package org.filesys.debug;

import org.filesys.server.config.ConfigId;
import org.filesys.server.config.ConfigSection;
import org.filesys.server.config.ConfigurationListener;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.springframework.extensions.config.ConfigElement;

/**
 * Debug Configuration Section Class
 *
 * @author gkspencer
 */
public class DebugConfigSection extends ConfigSection {

    // Debug configuration section name
    public static final String SectionName = "Debug";

    //  Debugging interface to use
    private DebugInterface m_debugDev;
    private ConfigElement m_debugParams;

    /**
     * Class constructor
     *
     * @param config ServerConfiguration
     */
    public DebugConfigSection(ServerConfiguration config) {
        super(SectionName, config);
    }

    /**
     * Return the debug interface.
     *
     * @return DebugInterface
     */
    public final DebugInterface getDebug() {
        return m_debugDev;
    }

    /**
     * Return the debug device initialization parameters
     *
     * @return ConfigElement
     */
    public final ConfigElement getDebugParameters() {
        return m_debugParams;
    }

    /**
     * Detemrine if the configuration has a valid debug interface.
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debugDev != null ? true : false;
    }

    /**
     * Set the debug interface to be used to output debug messages.
     *
     * @param dbgClass String
     * @param params   ConfigElement
     * @return int
     * @exception InvalidConfigurationException Error setting the debug interface
     */
    public final int setDebug(String dbgClass, ConfigElement params)
            throws InvalidConfigurationException {

        int sts = ConfigurationListener.StsIgnored;

        try {

            //  Check if the debug device is being set
            if (dbgClass != null) {

                //  Validate the debug output class
                Object obj = Class.forName(dbgClass).newInstance();

                //  Check if the debug class implements the Debug interface
                if (obj instanceof DebugInterface) {

                    //  Initialize the debug output class
                    DebugInterface dbg = (DebugInterface) obj;
                    dbg.initialize(params, getServerConfiguration());

                    //  Inform listeners of the configuration change
                    sts = fireConfigurationChange(ConfigId.DebugDevice, dbg);

                    //  Set the debug class and initialization parameters
                    m_debugDev = dbg;
                    m_debugParams = params;

                    //  Update the global debug interface
                    Debug.setDebugInterface(dbg);
                } else
                    throw new InvalidConfigurationException("Debugclass does not implement the Debug interface");
            } else {

                //  Clear the debug device and parameters
                m_debugDev = null;
                m_debugParams = null;

                //  Inform listeners of the configuration change
                sts = fireConfigurationChange(ConfigId.DebugDevice, null);
            }
        }
        catch (ClassNotFoundException ex) {
            throw new InvalidConfigurationException("Debugclass not found, " + dbgClass);
        }
        catch (IllegalAccessException ex) {
            throw new InvalidConfigurationException("Cannot load debugclass " + dbgClass + ", access error");
        }
        catch (InstantiationException ex) {
            throw new InvalidConfigurationException("Cannot instantiate debugclass " + dbgClass);
        }
        catch (Exception ex) {
            throw new InvalidConfigurationException("Failed to initialize debug class, " + ex.toString());
        }

        //  Return the change status
        return sts;
    }

    /**
     * Close the configuration section, perform any cleanup
     */
    public void closeConfig() {

        // Close the debug interface
        if (m_debugDev != null) {
            m_debugDev.close();
            m_debugDev = null;
        }
    }
}
