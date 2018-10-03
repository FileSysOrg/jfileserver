/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.server.core;

/**
 * <p>Available shared resource types.
 *
 * @author gkspencer
 */
public enum ShareType {
    DISK        (0),
    PRINTER     (1),
    NAMEDPIPE   (2),
    ADMINPIPE   (3),
    UNKNOWN     (-1);

    private final int shareType;

    /**
     * Enum constructor
     *
     * @param typ int
     */
    ShareType(int typ) { shareType = typ; }

    /**
     * Return the share type as an integer
     *
     * @return int
     */
    public final int intValue() { return shareType; }

    /**
     * Return the share type as a share information type.
     *
     * @param typ ShareType
     * @return int
     */
    public final static int asShareInfoType(ShareType typ) {

        // Convert the share type value to a valid share information structure share type value
        int shrTyp = 0;

        switch (typ) {
            case DISK:
                shrTyp = 0;
                break;
            case PRINTER:
                shrTyp = 1;
                break;
            case NAMEDPIPE:
            case ADMINPIPE:
                shrTyp = 3;
                break;
        }
        return shrTyp;
    }

    /**
     * Return the SMB service name as a shared device type
     *
     * @param srvName String
     * @return ShareType
     */
    public final static ShareType ServiceAsType(String srvName) {

        //  Check the service name
        if (srvName.compareTo("A:") == 0)
            return DISK;
        else if (srvName.compareTo("LPT1:") == 0)
            return PRINTER;
        else if (srvName.compareTo("IPC") == 0)
            return NAMEDPIPE;

        //  Unknown service name string
        return UNKNOWN;
    }

    /**
     * Return the share type as a service string
     *
     * @param typ ShareType
     * @return String
     */
    public final static String TypeAsService(ShareType typ) {

        if (typ == DISK)
            return "A:";
        else if (typ == PRINTER)
            return "LPT1:";
        else if (typ == NAMEDPIPE || typ == ADMINPIPE)
            return "IPC";
        return "";
    }

    /**
     * Return the share type as a string
     *
     * @param typ ShareType
     * @return String
     */
    public final static String TypeAsString(ShareType typ) {

        if (typ == DISK)
            return "DISK";
        else if (typ == PRINTER)
            return "PRINT";
        else if (typ == NAMEDPIPE)
            return "PIPE";
        else if (typ == ADMINPIPE)
            return "IPC$";
        return "<Unknown>";
    }
}
