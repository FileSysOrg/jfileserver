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
import org.filesys.server.auth.ClientInfo;
import org.filesys.server.core.SharedDevice;

/**
 * User Access Control Class
 *
 * <p>Allow/disallow access to a shared device by checking the user name.
 *
 * @author gkspencer
 */
public class UserAccessControl extends AccessControl {

    /**
     * Class constructor
     *
     * @param userName String
     * @param type     String
     * @param access   int
     */
    public UserAccessControl(String userName, String type, int access) {
        super(userName, type, access);
    }

    /**
     * Check if the user name matches the access control user name and return the allowed access.
     *
     * @param sess  SrvSession
     * @param share SharedDevice
     * @param mgr   AccessControlManager
     * @return int
     */
    public int allowsAccess(SrvSession sess, SharedDevice share, AccessControlManager mgr) {

        //	Check if the session has client information
        if (sess.hasClientInformation() == false)
            return Default;

        //	Check if the user name matches the access control name
        ClientInfo cInfo = sess.getClientInformation();

        if (cInfo.getUserName() != null && cInfo.getUserName().equalsIgnoreCase(getName()))
            return getAccess();
        return Default;
    }
}
