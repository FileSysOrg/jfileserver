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

package org.filesys.server.core;

import org.springframework.extensions.config.ConfigElement;

/**
 * <p>The device context is passed to the methods of a device interface. Each shared device has a
 * device interface and a device context associated with it. The device context allows a single
 * device interface to be used for multiple shared devices.
 *
 * @author gkspencer
 */
public class DeviceContext {

    //	Device name that the interface is associated with
    private String m_devName;

    //	Configuration parameters
    private ConfigElement m_params;

    //	Flag to indicate if the device is available. Unavailable devices will not be listed by the various
    //	protocol servers.
    private boolean m_available = true;

    // Shared device name
    private String m_shareName;

    // Enable debug output
    private boolean m_debug;

    /**
     * DeviceContext constructor.
     */
    public DeviceContext() {
        super();
    }

    /**
     * Class constructor
     *
     * @param devName String
     */
    public DeviceContext(String devName) {
        m_devName = devName;
    }

    /**
     * Class constructor
     *
     * @param devName   String
     * @param shareName String
     */
    public DeviceContext(String devName, String shareName) {
        m_devName = devName;
        m_shareName = shareName;
    }

    /**
     * Return the device name.
     *
     * @return java.lang.String
     */
    public final String getDeviceName() {
        return m_devName;
    }

    /**
     * Return the shared device name
     *
     * @return String
     */
    public final String getShareName() {
        return m_shareName;
    }

    /**
     * Determine if the device context has any configuration parameters
     *
     * @return boolean
     */
    public final boolean hasConfigurationParameters() {
        return m_params != null ? true : false;
    }

    /**
     * Determine if the filesystem is available
     *
     * @return boolean
     */
    public final boolean isAvailable() {
        return m_available;
    }

    /**
     * Return the configuration parameters
     *
     * @return ConfigElement
     */
    public final ConfigElement getConfigurationParameters() {
        return m_params;
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Set the filesystem as available, or not
     *
     * @param avail boolean
     */
    public final void setAvailable(boolean avail) {
        m_available = avail;
    }

    /**
     * Set the device name.
     *
     * @param name java.lang.String
     */
    public final void setDeviceName(String name) {
        m_devName = name;
    }

    /**
     * Set the shared device name
     *
     * @param shareName String
     */
    public final void setShareName(String shareName) {
        m_shareName = shareName;
    }

    /**
     * Set the configuration parameters
     *
     * @param params ConfigElement
     */
    public final void setConfigurationParameters(ConfigElement params) {
        m_params = params;
    }

    /**
     * Enable/disable debug output
     *
     * @param dbg boolean
     */
    public final void setDebug(boolean dbg) { m_debug = dbg; }
    
    /**
     * Close the device context, free any resources allocated by the context
     */
    public void CloseContext() {
    }

    /**
     * Return the context as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(getDeviceName());
        str.append("]");

        return str.toString();
    }
}
