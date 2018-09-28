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
 * Protocol Access Control Parser Class
 *
 * @author gkspencer
 */
public class ProtocolAccessControlParser extends AccessControlParser {

    /**
     * Default constructor
     */
    public ProtocolAccessControlParser() {
    }

    /**
     * Return the parser type
     *
     * @return String
     */
    public String getType() {
        return "protocol";
    }

    /**
     * Validate the parameters and create a user access control
     *
     * @param params ConfigElement
     * @return AccessControl
     * @exception ACLParseException Error parsing the ACL
     */
    public AccessControl createAccessControl(ConfigElement params)
            throws ACLParseException {

        //	Get the access type
        int access = parseAccessType(params);

        //	Get the list of protocols to check for
        String val = params.getAttribute("type");
        if (val == null || val.length() == 0)
            throw new ACLParseException("Protocol type not specified");

        String protList = val.trim();
        if (protList.length() == 0)
            throw new ACLParseException("Protocol type not valid");

        //	Validate the protocol list
        if (ProtocolAccessControl.validateProtocolList(protList) == false)
            throw new ACLParseException("Invalid protocol type");

        //	Create the protocol access control
        return new ProtocolAccessControl(protList, getType(), access);
    }
}
