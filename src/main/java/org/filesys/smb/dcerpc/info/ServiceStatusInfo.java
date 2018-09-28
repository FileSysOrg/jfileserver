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
 * Service Status Information Class
 *
 * Contains the status details of a remote NT service.
 *
 * @author gkspencer
 */
public class ServiceStatusInfo implements DCEReadable {

    // Service name and display name
    private String m_srvName;
    private String m_dispName;

    // Service status values
    private int m_srvType;
    private int m_currentState;
    private int m_ctrlAccepted;
    private int m_win32ExitCode;
    private int m_srvExitCode;
    private int m_checkPoint;
    private int m_waitHint;

    /**
     * Default constructor
     */
    public ServiceStatusInfo() {
    }

    /**
     * Class constructor
     *
     * @param name     String
     * @param dispname String
     */
    public ServiceStatusInfo(String name, String dispname) {
        m_srvName = name;
        m_dispName = dispname;
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
     */
    public ServiceStatusInfo(String name, String dispname, int typ, int state, int ctrl, int win32code, int srvexit,
                             int chkpoint, int waithint) {
        m_srvName = name;
        m_dispName = dispname;

        m_srvType = typ;
        m_currentState = state;
        m_ctrlAccepted = ctrl;
        m_win32ExitCode = win32code;
        m_srvExitCode = srvexit;
        m_checkPoint = chkpoint;
        m_waitHint = waithint;
    }

    /**
     * Return the service name
     *
     * @return String
     */
    public final String getName() {
        return m_srvName;
    }

    /**
     * Return the service display name
     *
     * @return String
     */
    public final String getDisplayName() {
        return m_dispName;
    }

    /**
     * Return the service type
     *
     * @return int
     */
    public final int getType() {
        return m_srvType;
    }

    /**
     * Return the current service state
     *
     * @return int
     */
    public final int getCurrentState() {
        return m_currentState;
    }

    /**
     * Return the service control functions accepted by this service
     *
     * @return int
     */
    public final int getControlsAccepted() {
        return m_ctrlAccepted;
    }

    /**
     * Return the service start/stop Win32 error code
     *
     * @return int
     */
    public final int getWin32ErrorCode() {
        return m_win32ExitCode;
    }

    /**
     * Return the service specific error code
     *
     * @return int
     */
    public final int getServiceErrorCode() {
        return m_srvExitCode;
    }

    /**
     * Return the checkpoint value, updated by the service during lengthy start/stop/pause
     * opertions.
     *
     * @return int
     */
    public final int getCheckpoint() {
        return m_checkPoint;
    }

    /**
     * Return the wait hint for the service
     *
     * @return int
     */
    public final int getWaitHint() {
        return m_waitHint;
    }

    /**
     * Set the service name
     *
     * @param name String
     */
    public final void setName(String name) {
        m_srvName = name;
    }

    /**
     * Set the service display name
     *
     * @param dispname String
     */
    public final void setDisplayName(String dispname) {
        m_dispName = dispname;
    }

    /**
     * Clear the string values
     */
    protected final void clearStrings() {

        // Clear the string values
        m_srvName = null;
        m_dispName = null;
    }

    /**
     * Read the service status information from the DCE buffer
     *
     * @param buf DCEBuffer
     * @exception DCEBufferException DCE buffer error
     * @see DCEReadable#readObject(DCEBuffer)
     */
    public void readObject(DCEBuffer buf)
            throws DCEBufferException {

        // Read the status values
        m_srvType = buf.getInt();
        m_currentState = buf.getInt();
        m_ctrlAccepted = buf.getInt();
        m_win32ExitCode = buf.getInt();
        m_srvExitCode = buf.getInt();
        m_checkPoint = buf.getInt();
        m_waitHint = buf.getInt();
    }

    /**
     * Read the strings for this object from the DCE/RPC buffer
     *
     * @param buf DCEBuffer
     * @exception DCEBufferException DCE buffer error
     */
    public void readStrings(DCEBuffer buf)
            throws DCEBufferException {

        // Not required
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
        str.append("]");

        return str.toString();
    }
}
