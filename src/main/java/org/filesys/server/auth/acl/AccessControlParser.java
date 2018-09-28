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
 * Access Control Parser Class
 *
 * <p>Creates an AccessControl instance by parsing a set of name/value parameters.
 *
 * @author gkspencer
 */
public abstract class AccessControlParser {

    //	Constants
    //
    //	Standard parameter names
    public final static String ParameterAccess = "access";

    //	Access control type names
    private final static String[] _accessTypes = {"None", "Read", "Write"};

    /**
     * Return the access control type name that uniquely identifies this type of access control.
     *
     * @return String
     */
    public abstract String getType();

    /**
     * Create an AccessControl instance by parsing the set of name/value parameters
     *
     * @param params ConfigElement
     * @return AccessControl
     * @exception ACLParseException Error parsing the ACL
     */
    public abstract AccessControl createAccessControl(ConfigElement params)
            throws ACLParseException;

    /**
     * Find the access parameter and parse the value
     *
     * @param params ConfigElement
     * @return int
     * @exception ACLParseException Error parsing the ACL
     */
    protected final int parseAccessType(ConfigElement params)
            throws ACLParseException {

        //	Check if the parameter list is valid
        if (params == null)
            throw new ACLParseException("Empty parameter list");

        //	Find the access type parameter
        String accessParam = params.getAttribute(ParameterAccess);
        if (accessParam == null || accessParam.length() == 0)
            throw new ACLParseException("Required parameter 'access' missing");

        //	Parse the access type value
        return parseAccessTypeString(accessParam);
    }

    /**
     * Parse the access level type and validate
     *
     * @param accessType String
     * @return int
     * @exception ACLParseException Error parsing the ACL
     */
    public static final int parseAccessTypeString(String accessType)
            throws ACLParseException {

        //	Check if the access type is valid
        if (accessType == null || accessType.length() == 0)
            throw new ACLParseException("Empty access type string");

        //	Parse the access type value
        int access = -1;

        for (int i = 0; i < _accessTypes.length; i++) {

            //	Check if the access type matches the current type
            if (accessType.equalsIgnoreCase(_accessTypes[i]))
                access = i;
        }

        //	Check if we found a valid access type
        if (access == -1)
            throw new ACLParseException("Invalid access type, " + accessType);

        //	Return the access type
        return access;
    }

    /**
     * Return the parser details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(getType());
        str.append("]");

        return str.toString();
    }
}
