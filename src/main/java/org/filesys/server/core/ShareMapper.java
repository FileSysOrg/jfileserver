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

import org.filesys.server.SrvSession;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.springframework.extensions.config.ConfigElement;


/**
 * Share Mapper Interface
 *
 * <p>The share mapper interface is used to allocate a share of the specified name and type. It is called by
 * the SMB server to allocate disk and print type shares.
 *
 * @author gkspencer
 */
public interface ShareMapper {

    /**
     * Initialize the share mapper
     *
     * @param config ServerConfiguration
     * @param params ConfigElement
     * @exception InvalidConfigurationException Error initializing the share mapper
     */
    public void initializeMapper(ServerConfiguration config, ConfigElement params)
            throws InvalidConfigurationException;

    /**
     * Return the share list for the specified host. The host name can be used to implement virtual
     * hosts.
     *
     * @param host      String
     * @param sess      SrvSession
     * @param allShares boolean
     * @return SharedDeviceList
     */
    public SharedDeviceList getShareList(String host, SrvSession sess, boolean allShares);

    /**
     * Find the share of the specified name/type
     *
     * @param tohost String
     * @param name   String
     * @param typ    ShareType
     * @param sess   SrvSession
     * @param create boolean
     * @return SharedDevice
     * @exception Exception Error during find
     */
    public SharedDevice findShare(String tohost, String name, ShareType typ, SrvSession sess, boolean create)
            throws Exception;

    /**
     * Delete any temporary shares created for the specified session
     *
     * @param sess SrvSession
     */
    public void deleteShares(SrvSession sess);

    /**
     * Close the share mapper, release any resources. Called when the server is shutting down.
     */
    public void closeMapper();
}
