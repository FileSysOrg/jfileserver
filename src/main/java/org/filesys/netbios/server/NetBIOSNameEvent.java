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

package org.filesys.netbios.server;

import org.filesys.netbios.NetBIOSName;

/**
 * NetBIOS name server event class.
 *
 * @author gkspencer
 */
public class NetBIOSNameEvent {

    // NetBIOS name event status codes
    public enum Status {
        ADD_SUCCESS,
        ADD_FAILED,
        ADD_DUPLICATE,
        ADD_IOERROR,
        QUERY_NAME,
        REGISTER_NAME,
        REFRESH_NAME,
        REFRESH_IOERROR;
    }

    /**
     * NetBIOS name event status codes
     */
/*
    public static final int ADD_SUCCESS     = 0;    // local name added successfully
    public static final int ADD_FAILED      = 1;    // local name add failure
    public static final int ADD_DUPLICATE   = 2;    // local name already in use
    public static final int ADD_IOERROR     = 3;    // I/O error during add name broadcast
    public static final int QUERY_NAME      = 4;    // query for local name
    public static final int REGISTER_NAME   = 5;    // remote name registered
    public static final int REFRESH_NAME    = 6;    // name refresh
    public static final int REFRESH_IOERROR = 7;    //	refresh name I/O error
*/
    /**
     * NetBIOS name details
     */
    private NetBIOSName m_name;

    /**
     * Name status
     */
    private Status m_status;

    /**
     * Create a NetBIOS name event.
     *
     * @param name NetBIOSName
     * @param sts  Status
     */
    protected NetBIOSNameEvent(NetBIOSName name, Status sts) {
        m_name = name;
        m_status = sts;
    }

    /**
     * Return the NetBIOS name details.
     *
     * @return NetBIOSName
     */
    public final NetBIOSName getNetBIOSName() {
        return m_name;
    }

    /**
     * Return the NetBIOS name status.
     *
     * @return Status
     */
    public final Status getStatus() {
        return m_status;
    }
}
