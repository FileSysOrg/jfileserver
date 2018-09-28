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

import java.util.StringTokenizer;

import org.filesys.server.SrvSession;
import org.filesys.server.core.SharedDevice;


/**
 * Protocol Access Control Class
 *
 * <p>Allow/disallow access to a share based on the protocol type.
 *
 * @author gkspencer
 */
public class ProtocolAccessControl extends AccessControl {

    //	Available protocol type names
    private static final String[] _protoTypes = {"SMB", "NFS", "FTP"};

    //	Parsed list of protocol types
    private String[] m_checkList;

    /**
     * Class constructor
     *
     * @param protList String
     * @param type     String
     * @param access   int
     */
    public ProtocolAccessControl(String protList, String type, int access) {
        super(protList, type, access);

        //	Parse the protocol list
        m_checkList = listFromString(protList);
    }

    /**
     * Check if the protocol matches the access control protocol list and return the allowed access.
     *
     * @param sess  SrvSession
     * @param share SharedDevice
     * @param mgr   AccessControlManager
     * @return int
     */
    public int allowsAccess(SrvSession sess, SharedDevice share, AccessControlManager mgr) {

        //	Determine the session protocol type
        String sessProto = null;
        String sessName = sess.getClass().getName();

        if (sessName.endsWith(".SMBSrvSession"))
            sessProto = "SMB";
        else if (sessName.endsWith(".FTPSrvSession"))
            sessProto = "FTP";
        else if (sessName.endsWith(".NFSSrvSession"))
            sessProto = "NFS";

        //	Check if the session protocol type is in the protocols to be checked
        if (sessProto != null && indexFromList(sessProto, m_checkList, false) != -1)
            return getAccess();
        return Default;
    }

    /**
     * Validate the protocol list
     *
     * @param protList String
     * @return boolean
     */
    public static final boolean validateProtocolList(String protList) {

        //	Check if the protocol list string is valid
        if (protList == null || protList.length() == 0)
            return false;

        //	Split the protocol list and validate each protocol name
        StringTokenizer tokens = new StringTokenizer(protList, ",");

        while (tokens.hasMoreTokens()) {

            //	Get the current protocol name and validate
            String name = tokens.nextToken().toUpperCase();
            if (indexFromList(name, _protoTypes, false) == -1)
                return false;
        }

        //	Protocol list is valid
        return true;
    }
}
