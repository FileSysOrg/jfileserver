/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
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
 * An interface providing access to file server configuration sections.
 *
 * @author dward
 */
public interface ServerConfigurationAccessor {

    /**
     * Return the required configuration section
     *
     * @param name String
     * @return ConfigSection
     */
    public ConfigSection getConfigSection(String name);

    /**
     * Return the server name
     *
     * @return String
     */
    public String getServerName();

    /**
     * Check if the specified protocol server exists and is running
     *
     * @param proto String
     * @return boolean
     */
    public boolean isServerRunning(String proto);
}