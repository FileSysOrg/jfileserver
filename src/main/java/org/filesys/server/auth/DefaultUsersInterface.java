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
import org.filesys.server.config.SecurityConfigSection;
import org.filesys.server.config.ServerConfiguration;
import org.springframework.extensions.config.ConfigElement;

/**
 * Default Users Interface
 *
 * <p>Use the user account list from the server configuration to provide the user details.
 *
 * @author gkspencer
 */
public class DefaultUsersInterface implements UsersInterface {

    // Security configuration containing the user list
    private SecurityConfigSection m_securityConfig;

    /**
     * Return the specified user account details
     *
     * @param userName String
     * @return UserAccount
     */
    public UserAccount getUserAccount(String userName) {

        //  Get the user account list from the configuration
        UserAccountList userList = m_securityConfig.getUserAccounts();
        if (userList == null || userList.numberOfUsers() == 0)
            return null;

        //  Search for the required user account record
        return userList.findUser(userName);
    }

    /**
     * Initialize the users interface
     *
     * @param config ServerConfiguration
     * @param params ConfigElement
     * @exception InvalidConfigurationException Error initializing the users interface
     */
    public void initializeUsers(ServerConfiguration config, ConfigElement params)
            throws InvalidConfigurationException {

        // Save the security configuration to access the user account list
        m_securityConfig = (SecurityConfigSection) config.getConfigSection(SecurityConfigSection.SectionName);
    }
}
