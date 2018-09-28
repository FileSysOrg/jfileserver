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

package org.filesys.smb.nt;

/**
 * Relative Id Class
 *
 * @author gkspencer
 */
public class RID {

    //	RID types
    public static final int TypeUser            = 1;
    public static final int TypeDomainGroup     = 2;
    public static final int TypeDomain          = 3;
    public static final int TypeAlias           = 4;
    public static final int TypeWellKnownGroup  = 5;
    public static final int TypeDeleted         = 6;
    public static final int TypeInvalid         = 7;
    public static final int TypeUnknown         = 8;

    //	RID type strings
    private static final String[] _types = {"User", "DomainGroup", "Domain", "Alias", "WellKnownGroup",
            "Deleted", "Invalid", "Unknown"
    };

    //	Relative id
    private int m_rid;

    //	RID type
    private int m_type;

    //	Name
    private String m_name;

    /**
     * Default constructor
     */
    public RID() {
    }

    /**
     * Class constructor
     *
     * @param rid  int
     * @param type int
     * @param name String
     */
    public RID(int rid, int type, String name) {
        m_rid = rid;
        m_type = type;
        m_name = name;
    }

    /**
     * Return the relative id
     *
     * @return int
     */
    public final int getRID() {
        return m_rid;
    }

    /**
     * Return the id type
     *
     * @return int
     */
    public final int isType() {
        return m_type;
    }

    /**
     * Return the type as a string
     *
     * @return String
     */
    public final String getTypeString() {
        return getTypeAsString(isType());
    }

    /**
     * Return the object name
     *
     * @return String
     */
    public final String getName() {
        return m_name;
    }

    /**
     * Set the relative id
     *
     * @param id int
     */
    public final void setRID(int id) {
        m_rid = id;
    }

    /**
     * Set the type
     *
     * @param typ int
     */
    public final void setType(int typ) {
        m_type = typ;
    }

    /**
     * Set the object name
     *
     * @param name String
     */
    public final void setName(String name) {
        m_name = name;
    }

    /**
     * Return the id type as a string
     *
     * @param typ int
     * @return String
     */
    public final static String getTypeAsString(int typ) {

        //	Range check the type
        if (typ >= 1 && typ <= 8)
            return _types[typ - 1];
        return null;
    }

    /**
     * Return the relative id as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(getRID());
        str.append(":");
        str.append(getTypeString());
        str.append(":");
        str.append(getName());
        str.append("]");

        return str.toString();
    }
}
