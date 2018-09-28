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

import org.springframework.extensions.config.ConfigElement;

/**
 * Group Id Access Control Parser Class
 *
 * @author gkspencer
 */
public class GidAccessControlParser extends AccessControlParser {

    /**
     * Default constructor
     */
    public GidAccessControlParser() {
    }

    /**
     * Return the parser type
     *
     * @return String
     */
    public String getType() {
        return "gid";
    }

    /**
     * Validate the parameters and create a group id access control
     *
     * @param params ConfigElement
     * @return AccessControl
     * @exception ACLParseException Error parsing the ACL
     */
    public AccessControl createAccessControl(ConfigElement params)
            throws ACLParseException {

        //	Get the access type
        int access = parseAccessType(params);

        //	Get the group id to check for
        String val = params.getAttribute("id");
        if (val == null || val.length() == 0)
            throw new ACLParseException("Group id not specified");

        //	Validate the group id
        String groupId = val.trim();
        int gid = -1;

        try {
            gid = Integer.parseInt(groupId);

            if (gid < 0 || gid > 32768)
                throw new ACLParseException("Invalid group id, out of valid range");
        }
        catch (NumberFormatException ex) {
            throw new ACLParseException("Group id not valid");
        }

        //	Create the group id access control
        return new GidAccessControl(groupId, gid, getType(), access);
    }
}
