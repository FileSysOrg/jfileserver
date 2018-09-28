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
import org.filesys.server.filesys.TreeConnection;
import org.springframework.extensions.config.ConfigElement;


/**
 * The device interface is the base of the shared device interfaces that are used by
 * shared devices on the SMB server.
 *
 * @author gkspencer
 */
public interface DeviceInterface {

    /**
     * Parse and validate the parameter string and create a device context object for this instance
     * of the shared device. The same DeviceInterface implementation may be used for multiple shares.
     *
     * @param shareName String
     * @param args      ConfigElement
     * @return DeviceContext
     * @exception DeviceContextException Error creating the device context
     */
    public DeviceContext createContext(String shareName, ConfigElement args)
            throws DeviceContextException;

    /**
     * Connection opened to this disk device
     *
     * @param sess Server session
     * @param tree Tree connection
     */
    public void treeOpened(SrvSession sess, TreeConnection tree);

    /**
     * Connection closed to this device
     *
     * @param sess Server session
     * @param tree Tree connection
     */
    public void treeClosed(SrvSession sess, TreeConnection tree);
}
