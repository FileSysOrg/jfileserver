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

import org.filesys.oncrpc.nfs.v3.NFS3;

/**
 * Authentication Types Class
 *
 * @author gkspencer
 */
public enum AuthType {
    Null(0),
    Unix(1),
    Short(2),
    DES(3),
    RPCSEC_GSS(6),

    Invalid(0xFFFF);

    private final int authType;

    /**
     * Enum constructor
     *
     * @param typ int
     */
    AuthType(int typ) { authType = typ; }

    /**
     * Return the authentication type as an int
     *
     * @return int
     */
    public final int intValue() { return authType; }

    /**
     * Create an authentication type from an int
     *
     * @param typ int
     * @return AuthType
     */
    public static final AuthType fromInt(int typ) {
        AuthType aType = Invalid;

        switch( typ) {
            case 0:
                aType = Null;
                break;
            case 1:
                aType = Unix;
                break;
            case 2:
                aType = Short;
                break;
            case 3:
                aType = DES;
                break;
            case 6:
                aType = RPCSEC_GSS;
                break;
        }

        return aType;
    }
}
