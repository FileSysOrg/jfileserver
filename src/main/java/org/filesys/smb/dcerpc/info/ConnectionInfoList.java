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

package org.filesys.smb.dcerpc.info;

import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCEList;
import org.filesys.smb.dcerpc.DCEReadable;

/**
 * Connection Information List Class
 *
 * @author gkspencer
 */
public class ConnectionInfoList extends DCEList {

    /**
     * Default constructor
     */
    public ConnectionInfoList() {
        super();
    }

    /**
     * Class constructor
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public ConnectionInfoList(DCEBuffer buf) throws DCEBufferException {
        super(buf);
    }

    /**
     * Create a new connection information object
     *
     * @return DCEReadable
     */
    protected DCEReadable getNewObject() {
        return new ConnectionInfo(getInformationLevel());
    }
}
