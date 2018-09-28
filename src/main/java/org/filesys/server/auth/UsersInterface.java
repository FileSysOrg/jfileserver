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

package org.filesys.server.auth;

import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.springframework.extensions.config.ConfigElement;


/**
 * Users Interface
 *
 * <p>Provides the user account information for the authenticator class.
 *
 * @author gkspencer
 */
public interface UsersInterface {

    /**
     * Initialize the users interface
     *
     * @param config ServerConfiguration
     * @param params ConfigElement
     * @exception InvalidConfigurationException Error initializing the users interface
     */
    void initializeUsers(ServerConfiguration config, ConfigElement params)
            throws InvalidConfigurationException;

    /**
     * Return the specified user account details
     *
     * @param userName String
     * @return UserAccount
     */
    UserAccount getUserAccount(String userName);
}
