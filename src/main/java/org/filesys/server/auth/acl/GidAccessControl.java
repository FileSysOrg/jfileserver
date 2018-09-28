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
 * Group Id Access Control Class
 *
 * <p>Allow/disallow access to a shared device by checking the Unix group ids of the client.
 *
 * @author gkspencer
 */
public class GidAccessControl extends AccessControl {

    //	Group id to check for
    private int m_gid;

    /**
     * Class constructor
     *
     * @param gidStr String
     * @param gid    int
     * @param type   String
     * @param access int
     */
    public GidAccessControl(String gidStr, int gid, String type, int access) {
        super(gidStr, type, access);

        //	Set the required group id
        m_gid = gid;
    }

    /**
     * Check if the session is an RPC session (NFS/mount) and the client is a member of the required
     * group.
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

        //	Check if the client main group id is set and matches the required group id
        ClientInfo cInfo = sess.getClientInformation();

        if (cInfo.getGid() != -1 && cInfo.getGid() == m_gid)
            return getAccess();

        //	Check if the client has a group list, if so check if any of the group match the required group id
        if (cInfo.hasGroupsList()) {

            //	Get the groups list and check for a matching group id
            int[] groups = cInfo.getGroupsList();

            for (int i = 0; i < groups.length; i++) {
                if (groups[i] == m_gid)
                    return getAccess();
            }
        }
        return Default;
    }
}
