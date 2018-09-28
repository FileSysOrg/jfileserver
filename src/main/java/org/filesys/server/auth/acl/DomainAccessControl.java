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
import org.filesys.smb.server.SMBSrvSession;

/**
 * Domain Name Access Control Class
 *
 * <p>Allow/disallow access based on the SMB session callers domain name.
 *
 * @author gkspencer
 */
public class DomainAccessControl extends AccessControl {

    /**
     * Class constructor
     *
     * @param domainName String
     * @param type       String
     * @param access     int
     */
    public DomainAccessControl(String domainName, String type, int access) {
        super(domainName, type, access);
    }

    /**
     * Check if the domain name matches the access control domain name and return the allowed access.
     *
     * @param sess  SrvSession
     * @param share SharedDevice
     * @param mgr   AccessControlManager
     * @return int
     */
    public int allowsAccess(SrvSession sess, SharedDevice share, AccessControlManager mgr) {

        //	Check if the session has client information
        if (sess.hasClientInformation() == false || sess instanceof SMBSrvSession == false)
            return Default;

        //	Check if the domain name matches the access control name
        ClientInfo cInfo = sess.getClientInformation();

        if (cInfo.getDomain() != null && cInfo.getDomain().equalsIgnoreCase(getName()))
            return getAccess();
        return Default;
    }
}
