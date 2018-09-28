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
 * Connection Information Class
 *
 * <p>
 * Contains the details of a connection on a remote server.
 *
 * @author gkspencer
 */
public class ConnectionInfo implements DCEReadable {

    // Information level
    private int m_infoLevel;

    // Connection id and type
    private int m_connId;
    private int m_connType;

    // Count of open files
    private int m_openFiles;

    // Number of users
    private int m_numUsers;

    // Time connected, in minutes
    private int m_connTime;

    // User name
    private String m_userName;

    // Client name
    private String m_clientName;

    /**
     * Default constructor
     */
    public ConnectionInfo() {
    }

    /**
     * Class constructor
     *
     * @param infoLevel int
     */
    public ConnectionInfo(int infoLevel) {
        m_infoLevel = infoLevel;
    }

    /**
     * Get the information level
     *
     * @return int
     */
    public final int getInformationLevel() {
        return m_infoLevel;
    }

    /**
     * Get the connection id
     *
     * @return int
     */
    public final int getConnectionId() {
        return m_connId;
    }

    /**
     * Get the connection type
     *
     * @return int
     */
    public final int getConnectionType() {
        return m_connType;
    }

    /**
     * Get the number of open files on the connection
     *
     * @return int
     */
    public final int getOpenFileCount() {
        return m_openFiles;
    }

    /**
     * Return the number of users on the connection
     *
     * @return int
     */
    public final int getNumberOfUsers() {
        return m_numUsers;
    }

    /**
     * Return the connection time in seconds
     *
     * @return int
     */
    public final int getConnectionTime() {
        return m_connTime;
    }

    /**
     * Return the user name
     *
     * @return String
     */
    public final String getUserName() {
        return m_userName;
    }

    /**
     * Return the client name
     *
     * @return String
     */
    public final String getClientName() {
        return m_clientName;
    }

    /**
     * Read a connection information object from a DCE buffer
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public void readObject(DCEBuffer buf)
            throws DCEBufferException {

        // Unpack the connection information
        switch (getInformationLevel()) {

            // Information level 0
            case 0:
                m_connId = buf.getInt();
                m_userName = null;
                m_clientName = null;
                break;

            // Information level 1
            case 1:
                m_connId = buf.getInt();
                m_connType = buf.getInt();
                m_openFiles = buf.getInt();
                m_numUsers = buf.getInt();
                m_connTime = buf.getInt();

                m_userName = buf.getPointer() != 0 ? "" : null;
                m_clientName = buf.getPointer() != 0 ? "" : null;
                break;
        }
    }

    /**
     * Read the strings for this connection information from the DCE/RPC buffer
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public void readStrings(DCEBuffer buf)
            throws DCEBufferException {

        // Read the strings for this connection information
        switch (getInformationLevel()) {

            // Information level 1
            case 1:
                if (getUserName() != null)
                    m_userName = buf.getString(DCEBuffer.ALIGN_INT);
                if (getClientName() != null)
                    m_clientName = buf.getString(DCEBuffer.ALIGN_INT);
                break;
        }
    }

    /**
     * Return the connection information as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[ID=");
        str.append(getConnectionId());
        str.append(":Level=");
        str.append(getInformationLevel());
        str.append(":");

        if (getInformationLevel() == 1) {
            str.append("Type=");
            str.append(getConnectionType());
            str.append(",OpenFiles=");
            str.append(getOpenFileCount());
            str.append(",NumUsers=");
            str.append(getNumberOfUsers());
            str.append(",Connected=");
            str.append(getConnectionTime());
            str.append(",User=");
            str.append(getUserName());
            str.append(",Client=");
            str.append(getClientName());
        }

        str.append("]");
        return str.toString();
    }
}
