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

import java.io.*;
import java.util.*;

import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCEReadable;
import org.filesys.smb.dcerpc.DCEWriteable;
import org.filesys.util.StringList;

/**
 * Service Configuration Information Class
 *
 * <p>
 * Contains the details of a remote NT service.
 *
 * @author gkspencer
 */
public class ServiceConfigInfo implements DCEReadable, DCEWriteable {

    // Service type
    private int m_serviceType = NTService.TypeNoChange;

    // Start type
    private int m_startType = NTService.TypeNoChange;

    // Error control
    private int m_errorControl = NTService.TypeNoChange;

    // Service executable file name, including path
    private String m_binaryPathName;

    // Service load order group
    private String m_loadOrderGroup;

    // Load order tag id
    private int m_tagId;

    // Service dependency list
    private StringList m_depends;

    // Service start name
    private String m_startName;

    // Dispalyed name of the service
    private String m_displayName;

    /**
     * Default constructor
     */
    public ServiceConfigInfo() {
    }

    /**
     * Return the service type
     *
     * @return int
     */
    public final int getServiceType() {
        return m_serviceType;
    }

    /**
     * Return the service start type
     *
     * @return int
     */
    public final int getStartType() {
        return m_startType;
    }

    /**
     * Return the service error control
     *
     * @return int
     */
    public final int getErrorControl() {
        return m_errorControl;
    }

    /**
     * Return the service executable path
     *
     * @return String
     */
    public final String getBinaryPath() {
        return m_binaryPathName;
    }

    /**
     * Return the service load order group
     *
     * @return String
     */
    public final String getLoadOrderGroup() {
        return m_loadOrderGroup;
    }

    /**
     * Return the load order group tag id
     *
     * @return int
     */
    public final int getLoadOrderTag() {
        return m_tagId;
    }

    /**
     * Determine if the service has a dependency list
     *
     * @return boolean
     */
    public final boolean hasDependencies() {
        return m_depends != null ? true : false;
    }

    /**
     * Return the service dependency list
     *
     * @return StringList
     */
    public final StringList getDependencies() {
        return m_depends;
    }

    /**
     * Return the service start name
     *
     * @return String
     */
    public final String getStartName() {
        return m_startName;
    }

    /**
     * Return the service display name
     *
     * @return String
     */
    public final String getDisplayName() {
        return m_displayName;
    }

    /**
     * Set the service type
     *
     * @param typ int
     */
    public final void setServiceType(int typ) {
        m_serviceType = typ;
    }

    /**
     * Set the start type
     *
     * @param typ int
     */
    public final void setStartType(int typ) {
        m_startType = typ;
    }

    /**
     * Set the error control
     *
     * @param errCtrl int
     */
    public final void setErrorControl(int errCtrl) {
        m_errorControl = errCtrl;
    }

    /**
     * Set the binary path
     *
     * @param path String
     */
    public final void setBinaryPath(String path) {
        m_binaryPathName = path;
    }

    /**
     * Set the load order group
     *
     * @param group String
     */
    public final void setLoadOrderGroup(String group) {
        m_loadOrderGroup = group;
    }

    /**
     * Set the dependent service name list
     *
     * @param depList StringList
     */
    public final void setDependencies(StringList depList) {
        m_depends = depList;
    }

    /**
     * Set the startup name
     *
     * @param name String
     */
    public final void setStartName(String name) {
        m_startName = name;
    }

    /**
     * Set the service display name
     *
     * @param dispName String
     */
    public final void setDisplayName(String dispName) {
        m_displayName = dispName;
    }

    /**
     * Read the service configuration information from the DCE buffer
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     * @see DCEReadable#readObject(DCEBuffer)
     */
    public void readObject(DCEBuffer buf)
            throws DCEBufferException {

        // Read the service configuration information
        m_serviceType = buf.getInt();
        m_startType = buf.getInt();
        m_errorControl = buf.getInt();

        int pBinPath = buf.getPointer();
        int pLoadGroup = buf.getPointer();

        m_tagId = buf.getInt();

        int pDeps = buf.getPointer();
        int pStartName = buf.getPointer();
        int pDispName = buf.getPointer();

        // Load the actual strings from the buffer
        if (pBinPath != 0)
            m_binaryPathName = buf.getString(DCEBuffer.ALIGN_INT);

        if (pLoadGroup != 0)
            m_loadOrderGroup = buf.getString(DCEBuffer.ALIGN_INT);

        if (pDeps != 0) {

            // Get the dependency list string, allocate the list for the individual service
            // name strings.
            String deps = buf.getString(DCEBuffer.ALIGN_INT);
            m_depends = new StringList();

            // Split into seperate service names
            StringTokenizer token = new StringTokenizer(deps, "/");

            while (token.hasMoreTokens())
                m_depends.addString(token.nextToken());
        }

        if (pStartName != 0)
            m_startName = buf.getString(DCEBuffer.ALIGN_INT);

        if (pDispName != 0)
            m_displayName = buf.getString(DCEBuffer.ALIGN_INT);
    }

    /**
     * Read the strings for this object from the DCE/RPC buffer
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public void readStrings(DCEBuffer buf)
            throws DCEBufferException {

        // Not required
    }

    /**
     * Write the service configuration to the DCE buffer
     *
     * @param buf    DCEBuffer
     * @param strBuf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public void writeObject(DCEBuffer buf, DCEBuffer strBuf)
            throws DCEBufferException {

        // Pack the service type, startup and error control
        buf.putInt(getServiceType());
        buf.putInt(getStartType());
        buf.putInt(getErrorControl());

        // Pack the binary path if specified
        if (m_binaryPathName != null) {
            buf.putPointer(true);
            buf.putString(m_binaryPathName, DCEBuffer.ALIGN_INT, true);
        } else
            buf.putPointer(false);

        // Pack the load order group name
        if (m_loadOrderGroup != null) {
            buf.putPointer(true);
            buf.putString(m_loadOrderGroup, DCEBuffer.ALIGN_INT, true);

            // Return the allocated tag id
            buf.putPointer(true);
        } else {
            buf.putPointer(false);
            buf.putPointer(false);
        }

        // Pack the dependencies list
        if (m_depends != null && m_depends.numberOfStrings() > 0) {

            // Build the list of strings
            StringBuffer depStr = new StringBuffer();

            for (int i = 0; i < m_depends.numberOfStrings(); i++) {

                // Pack the current dependency name followed by a null
                depStr.append(m_depends.getStringAt(i));
                depStr.append("\u0000");
            }

            // End the list string with a second null
            depStr.append("\u0000");

            // Pack the dependency list
            byte[] depByts = null;

            try {
                depByts = depStr.toString().getBytes("UnicodeLittleUnmarked");
            }
            catch (UnsupportedEncodingException ex) {
                throw new DCEBufferException("Failed to convert string, " + ex.getMessage());
            }

            buf.putPointer(true);
            buf.putInt(depByts.length);
            buf.putBytes(depByts, depByts.length, DCEBuffer.ALIGN_INT);
            buf.putInt(depByts.length);
        } else
            buf.putZeroInts(2);

        // Pack the service start name
        if (m_startName != null) {
            buf.putPointer(true);
            buf.putString(m_startName, DCEBuffer.ALIGN_INT, true);
        } else
            buf.putPointer(false);

        // Pack the password, currently always null
        buf.putPointer(false);
        buf.putInt(0);

        // Pack the display name
        if (m_displayName != null) {
            buf.putPointer(true);
            buf.putString(m_displayName, DCEBuffer.ALIGN_INT, true);
        } else
            buf.putPointer(false);

        // Padding, unknown
        buf.putInt(0);
    }

    /**
     * Return the service configuration information as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(getDisplayName());
        str.append(":");
        str.append(getBinaryPath());
        str.append(",0x");
        str.append(Integer.toHexString(getServiceType()));
        str.append(",0x");
        str.append(Integer.toHexString(getStartType()));
        str.append(",0x");
        str.append(Integer.toHexString(getErrorControl()));
        str.append(",");
        str.append(getLoadOrderGroup());
        str.append(",");
        str.append(getLoadOrderTag());
        str.append("]");

        return str.toString();
    }
}
