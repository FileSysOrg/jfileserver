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
import org.filesys.smb.dcerpc.DCEReadable;

/**
 * Service Status Ex Information Class
 *
 * <p>
 * Contains additional fields that are returned by the EnumServiceStatusEx() call.
 */
public class ServiceStatusExInfo extends ServiceStatusInfo {

    // Process id
    private int m_pid;

    // Service flags
    private int m_svcFlags;

    /**
     * Default constructor
     */
    public ServiceStatusExInfo() {
    }

    /**
     * Class constructor
     *
     * @param name     String
     * @param dispname String
     */
    public ServiceStatusExInfo(String name, String dispname) {
        super(name, dispname);
    }

    /**
     * Class constructor
     *
     * @param name      String
     * @param dispname  String
     * @param typ       int
     * @param state     int
     * @param ctrl      int
     * @param win32code int
     * @param srvexit   int
     * @param chkpoint  int
     * @param waithint  int
     * @param pid       int
     * @param svcFlags  int
     */
    public ServiceStatusExInfo(String name, String dispname, int typ, int state, int ctrl, int win32code, int srvexit,
                               int chkpoint, int waithint, int pid, int svcFlags) {
        super(name, dispname, typ, state, ctrl, win32code, srvexit, chkpoint, waithint);

        m_pid = pid;
        m_svcFlags = svcFlags;
    }

    /**
     * Return the process id of the service
     *
     * @return int
     */
    public final int getProcessId() {
        return m_pid;
    }

    /**
     * Return the service flags
     *
     * @return int
     */
    public final int getServiceFlags() {
        return m_svcFlags;
    }

    /**
     * Read the service status information from the DCE buffer
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     * @see DCEReadable#readObject(DCEBuffer)
     */
    public void readObject(DCEBuffer buf)
            throws DCEBufferException {

        // Read the base object details
        super.readObject(buf);

        // Read the additional values
        m_pid = buf.getInt();
        m_svcFlags = buf.getInt();
    }

    /**
     * Return the service status information as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(getDisplayName());
        str.append(",");
        str.append(getName());
        str.append(",");

        str.append(NTService.getTypeAsString(getType()));
        str.append(":");

        str.append(NTService.getStateAsString(getCurrentState()));
        str.append(":");

        str.append(NTService.getControlsAcceptedAsString(getControlsAccepted()));

        str.append(",PID=");
        str.append(getProcessId());

        str.append(",Flags=0x");
        str.append(Integer.toHexString(getServiceFlags()));

        str.append("]");

        return str.toString();
    }
}
