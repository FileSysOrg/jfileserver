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

import java.util.ArrayList;
import java.util.List;

import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCEList;
import org.filesys.smb.dcerpc.DCEReadable;

/**
 * Server Share Information List Class
 *
 * <p>
 * Holds the details for a DCE/RPC share enumeration request or response.
 *
 * @author gkspencer
 */
public class ShareInfoList extends DCEList {

    /**
     * Default constructor
     */
    public ShareInfoList() {
        super();
    }

    /**
     * Class constructor
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public ShareInfoList(DCEBuffer buf) throws DCEBufferException {
        super(buf);
    }

    /**
     * Class constructor
     *
     * @param infoLevel int
     */
    public ShareInfoList(int infoLevel) {
        super(infoLevel);
    }

    /**
     * Return share information object from the list
     *
     * @param idx int
     * @return ShareInfo
     */
    public final ShareInfo getShare(int idx) {
        return (ShareInfo) getElement(idx);
    }

    /**
     * Create a new share information object
     *
     * @return DCEReadable
     */
    protected DCEReadable getNewObject() {
        return new ShareInfo(getInformationLevel());
    }

    /**
     * Add a share to the list
     *
     * @param share ShareInfo
     */
    public final void addShare(ShareInfo share) {

        // Check if the share list is valid
        if (getList() == null)
            setList(new ArrayList<Object>());

        // Add the share
        getList().add(share);
    }

    /**
     * Set the share information list
     *
     * @param list List of objects
     */
    public final void setShareList(List<Object> list) {
        setList(list);
    }
}
