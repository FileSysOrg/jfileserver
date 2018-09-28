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

package org.filesys.oncrpc;

/**
 * Authentication Types Class
 *
 * @author gkspencer
 */
public final class AuthType {

    //	Authentication type contants
    public static final int Null    = 0;
    public static final int Unix    = 1;
    public static final int Short   = 2;
    public static final int DES     = 3;

    //	Authentication type strings
    private static final String[] _authTypes = {"Null", "Unix", "Short", "DES"};

    /**
     * Return the authentication type as string
     *
     * @param type int
     * @return String
     */
    public static final String getTypeAsString(int type) {
        if (type < 0 || type >= _authTypes.length)
            return "" + type;
        return _authTypes[type];
    }
}
