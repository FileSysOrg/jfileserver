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

package org.filesys.smb.dcerpc.server;

import org.filesys.smb.dcerpc.DCEPipeType;

/**
 * DCE Pipe Handler Class
 *
 * <p>Contains a list of the available DCE pipe handlers.
 *
 * @author gkspencer
 */
public class DCEPipeHandler {

    //	DCE/RPC pipe request handlers
    private static DCEHandler[] _handlers = {
            new SrvsvcDCEHandler(),
            null,                // samr
            null,                // winreg
            new WkssvcDCEHandler(),
            null,                // NETLOGON
            null,                // lsarpc
            null,                // spoolss
            null,                // netdfs
            null,                // service control
            null,                // eventlog
            null                // netlogon1
    };

    /**
     * Return the DCE/RPC request handler for the pipe type
     *
     * @param typ DCEPipeType
     * @return DCEHandler
     */
    public final static DCEHandler getHandlerForType(DCEPipeType typ) {
        int ival = typ.intValue();

        if (ival >= 0 && ival < _handlers.length)
            return _handlers[ival];
        return null;
    }
}
