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

package org.filesys.server.auth.acl;

import org.filesys.server.SrvSession;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.core.SharedDeviceList;
import org.springframework.extensions.config.ConfigElement;


/**
 * Access Control Manager Interface
 *
 * <p>Used to control access to shared filesystems.
 *
 * @author gkspencer
 */
public interface AccessControlManager {

    /**
     * Initialize the access control manager
     *
     * @param config ServerConfiguration
     * @param params ConfigElement
     * @exception InvalidConfigurationException Error initializing the access control manager
     */
    public void initialize(ServerConfiguration config, ConfigElement params)
            throws InvalidConfigurationException;

    /**
     * Check access to the shared filesystem for the specified session
     *
     * @param sess  SrvSession
     * @param share SharedDevice
     * @return int
     */
    public int checkAccessControl(SrvSession sess, SharedDevice share);

    /**
     * Filter a shared device list to remove shares that are not visible or the session does
     * not have access to.
     *
     * @param sess   SrvSession
     * @param shares SharedDeviceList
     * @return SharedDeviceList
     */
    public SharedDeviceList filterShareList(SrvSession sess, SharedDeviceList shares);

    /**
     * Create an access control
     *
     * @param type   String
     * @param params ConfigElement
     * @return AccessControl
     * @exception ACLParseException Error parsing the ACL
     * @exception InvalidACLTypeException Invalid ACL type
     */
    public AccessControl createAccessControl(String type, ConfigElement params)
            throws ACLParseException, InvalidACLTypeException;

    /**
     * Add an access control parser to the list of available access control types.
     *
     * @param parser AccessControlParser
     */
    public void addAccessControlType(AccessControlParser parser);
}
