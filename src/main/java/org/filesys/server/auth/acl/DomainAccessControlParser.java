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
 * Domain Name Access Control Parser Class
 *
 * @author gkspencer
 */
public class DomainAccessControlParser extends AccessControlParser {

    /**
     * Default constructor
     */
    public DomainAccessControlParser() {
    }

    /**
     * Return the parser type
     *
     * @return String
     */
    public String getType() {
        return "domain";
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

        //	Get the domain name to check for
        String val = params.getAttribute("name");
        if (val == null || val.length() == 0)
            throw new ACLParseException("Domain name not specified");

        String domainName = val.trim();
        if (domainName.length() == 0)
            throw new ACLParseException("Domain name not valid");

        //	Create the domain access control
        return new DomainAccessControl(domainName, getType(), access);
    }
}
