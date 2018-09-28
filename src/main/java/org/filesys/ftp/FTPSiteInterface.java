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

import java.io.IOException;

import org.filesys.server.config.ServerConfiguration;
import org.springframework.extensions.config.ConfigElement;

/**
 * FTP SITE Command Interface
 *
 * <p>Optional interface that is used to provide processing for the FTP SITE command.
 *
 * @author gkspencer
 */
public interface FTPSiteInterface {

    /**
     * Initialize the site interface
     *
     * @param config ServerConfiguration
     * @param params ConfigElement
     */
    void initializeSiteInterface(ServerConfiguration config, ConfigElement params);

    /**
     * Process an FTP SITE specific command
     *
     * @param sess FTPSrvSession
     * @param req  FTPRequest
     * @exception IOException Error processing a site command
     */
    void processFTPSiteCommand(FTPSrvSession sess, FTPRequest req)
            throws IOException;
}
