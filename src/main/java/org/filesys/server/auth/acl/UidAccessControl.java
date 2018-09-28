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
 * User Id Access Control Class
 *
 * <p>Allow/disallow access to a shared device by checking the Unix user id of the client.
 *
 * @author gkspencer
 */
public class UidAccessControl extends AccessControl {

    //	User id to check for
    private int m_uid;

    /**
     * Class constructor
     *
     * @param uidStr String
     * @param uid    int
     * @param type   String
     * @param access int
     */
    public UidAccessControl(String uidStr, int uid, String type, int access) {
        super(uidStr, type, access);

        //	Set the required user id
        m_uid = uid;
    }


    /**
     * Check if the session is an RPC session (NFS/mount) and the client has the required Unix user id.
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

        if (cInfo.getUid() != -1 && cInfo.getUid() == m_uid)
            return getAccess();
        return Default;
    }
}
