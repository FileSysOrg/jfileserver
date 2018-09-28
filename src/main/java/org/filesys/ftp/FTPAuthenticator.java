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

package org.filesys.ftp;

import org.filesys.server.auth.ClientInfo;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.springframework.extensions.config.ConfigElement;

/**
 * FTP Authenticator Interface
 *
 * @author gkspencer
 */
public interface FTPAuthenticator {

    /**
     * Initialize the authenticator
     *
     * @param config ServerConfiguration
     * @param params ConfigElement
     * @exception InvalidConfigurationException Error initializing the authenticator
     */
    public void initialize(ServerConfiguration config, ConfigElement params)
            throws InvalidConfigurationException;

    /**
     * Authenticate the user
     *
     * @param cInfo ClientInfo
     * @param sess  FTPSrvSession
     * @return boolean
     */
    public boolean authenticateUser(ClientInfo cInfo, FTPSrvSession sess);

    /**
     * Close the authenticator, perform any cleanup
     */
    public void closeAuthenticator();
}
