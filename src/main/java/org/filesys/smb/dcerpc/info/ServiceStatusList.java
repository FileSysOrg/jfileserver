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

import java.util.*;

import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCEDataPacker;
import org.filesys.smb.dcerpc.DCEReadableList;

/**
 * Service Status List Class
 *
 * <p>
 * Contains a list of service status information objects.
 *
 * @author gkspencer
 * @see ServiceStatusInfo
 */
public class ServiceStatusList implements DCEReadableList {

    // List of ServiceStatusInfo objects
    private List<ServiceStatusInfo> m_list;

    // More data handle
    private int m_moreData;

    /**
     * Class constructor
     */
    public ServiceStatusList() {
        m_list = new ArrayList<ServiceStatusInfo>();
    }

    /**
     * Return the service status list
     *
     * @return List of ServiceStatusInfo
     */
    public final List<ServiceStatusInfo> getList() {
        return m_list;
    }

    /**
     * Return the specified service status info
     *
     * @param idx int
     * @return ServiceStatusInfo
     */
    public final ServiceStatusInfo getInfo(int idx) {
        if (m_list == null || idx >= m_list.size())
            return null;
        return m_list.get(idx);
    }

    /**
     * Return the service status list size
     *
     * @return int
     */
    public final int numberOfServices() {
        return m_list.size();
    }

    /**
     * Add a service status to the list
     *
     * @param stsInfo ServiceStatusInfo
     */
    public final void addServiceStatus(ServiceStatusInfo stsInfo) {
        m_list.add(stsInfo);
    }

    /**
     * Read the service status info list from the DCE buffer
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     * @see org.filesys.smb.dcerpc.DCEReadable#readObject(DCEBuffer)
     */
    public void readObject(DCEBuffer buf)
            throws DCEBufferException {

        // Get the service count from the end of the buffer
        int dataLen = buf.getInt();
        buf.positionAt(DCEDataPacker.longwordAlign(dataLen + 8));

        int numSvcs = buf.getInt();
        if (buf.getPointer() != 0)
            m_moreData = buf.getInt();
        else
            m_moreData = 0;

        // Read the service status records
        buf.positionAt(4);

        for (int i = 0; i < numSvcs; i++) {

            // Get the service name and display name offsets
            int pName = buf.getInt();
            int pDisp = buf.getInt();

            // Get the service status values
            ServiceStatusInfo stsInfo = new ServiceStatusInfo();
            stsInfo.readObject(buf);

            // Read the service name and display name strings
            String srvName = "";
            if (pName != 0)
                srvName = buf.getStringAt(pName + 4);

            String dspName = "";
            if (pDisp != 0)
                dspName = buf.getStringAt(pDisp + 4);

            // Set the service name/display name
            stsInfo.setName(srvName);
            stsInfo.setDisplayName(dspName);

            // Create a service status object
            m_list.add(stsInfo);
        }
    }

    /**
     * Return the continuation handle for multi part requests
     *
     * @return int
     */
    public final int getMultiPartHandle() {
        return m_moreData;
    }
}
