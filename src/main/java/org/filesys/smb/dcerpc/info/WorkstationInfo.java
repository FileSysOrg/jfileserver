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
import org.filesys.smb.dcerpc.DCEWriteable;

/**
 * Workstation Information Class
 *
 * @author gkspencer
 */
public class WorkstationInfo implements DCEWriteable, DCEReadable {

    // Supported information levels
    public static final int InfoLevel100 = 100;

    // Information level
    private int m_infoLevel;

    // Server information
    private int m_platformId;
    private String m_name;
    private String m_domain;
    private int m_verMajor;
    private int m_verMinor;

    private String m_userName;
    private String m_logonDomain;
    private String m_otherDomain;

    /**
     * Default constructor
     */
    public WorkstationInfo() {
    }

    /**
     * Class constructor
     *
     * @param lev int
     */
    public WorkstationInfo(int lev) {
        m_infoLevel = lev;
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
     * Get the workstation name
     *
     * @return String
     */
    public final String getWorkstationName() {
        return m_name;
    }

    /**
     * Get the domain/workgroup
     *
     * @return String
     */
    public final String getDomain() {
        return m_domain;
    }

    /**
     * Get the workstation platform id
     *
     * @return int
     */
    public final int getPlatformId() {
        return m_platformId;
    }

    /**
     * Get the workstation major version
     *
     * @return int
     */
    public final int getMajorVersion() {
        return m_verMajor;
    }

    /**
     * Get the workstation minor version
     *
     * @return int
     */
    public final int getMinorVersion() {
        return m_verMinor;
    }

    /**
     * Reutrn the user name
     *
     * @return String
     */
    public final String getUserName() {
        return m_userName;
    }

    /**
     * Return the workstations logon domain.
     *
     * @return String
     */
    public String getLogonDomain() {
        return m_logonDomain;
    }

    /**
     * Return the list of domains that the workstation is enlisted in.
     *
     * @return String
     */
    public String getOtherDomains() {
        return m_otherDomain;
    }

    /**
     * Set the logon domain name.
     *
     * @param logdom String
     */
    public void setLogonDomain(String logdom) {
        m_logonDomain = logdom;
    }

    /**
     * Set the other domains that this workstation is enlisted in.
     *
     * @param othdom String
     */
    public void setOtherDomains(String othdom) {
        m_otherDomain = othdom;
    }

    /**
     * Set the workstation name
     *
     * @param name String
     */
    public final void setWorkstationName(String name) {
        m_name = name;
    }

    /**
     * Set the domain/workgroup
     *
     * @param domain String
     */
    public final void setDomain(String domain) {
        m_domain = domain;
    }

    /**
     * Set the information level
     *
     * @param lev int
     */
    public final void setInformationLevel(int lev) {
        m_infoLevel = lev;
    }

    /**
     * Set the platform id
     *
     * @param id int
     */
    public final void setPlatformId(int id) {
        m_platformId = id;
    }

    /**
     * Set the version
     *
     * @param verMajor int
     * @param verMinor int
     */
    public final void setVersion(int verMajor, int verMinor) {
        m_verMajor = verMajor;
        m_verMinor = verMinor;
    }

    /**
     * Set the logged in user name
     *
     * @param user String
     */
    public final void setUserName(String user) {
        m_userName = user;
    }

    /**
     * Clear the string values
     */
    protected final void clearStrings() {

        // Clear the string values
        m_userName = null;
        m_domain = null;
        m_logonDomain = null;
        m_otherDomain = null;
    }

    /**
     * Write a workstation information structure
     *
     * @param buf    DCEBuffer
     * @param strBuf DCEBuffer
     */
    public void writeObject(DCEBuffer buf, DCEBuffer strBuf) {

        // Output the workstation information structure
        buf.putInt(getInformationLevel());
        buf.putPointer(true);

        // Output the required information level
        switch (getInformationLevel()) {

            // Level 100
            case InfoLevel100:
                buf.putInt(getPlatformId());
                buf.putPointer(true);
                buf.putPointer(true);
                buf.putInt(getMajorVersion());
                buf.putInt(getMinorVersion());

                strBuf.putString(getWorkstationName(), DCEBuffer.ALIGN_INT, true);
                strBuf.putString(getDomain() != null ? getDomain() : "", DCEBuffer.ALIGN_INT, true);
                break;

            // Level 101
            case 101:
                break;

            // Level 102
            case 102:
                break;
        }
    }

    /**
     * Read the workstation information from the DCE/RPC buffer
     *
     * @param buf DCEBuffer
     * @exception DCEBufferException DCE buffer error
     */
    public void readObject(DCEBuffer buf)
            throws DCEBufferException {

        // Clear all existing strings
        clearStrings();

        // Unpack the information level and check that there is a valid information pointer
        buf.getInt();
        if (buf.getPointer() == 0)
            return;

        // Load the workstation information
        switch (getInformationLevel()) {

            // Information level 100
            case InfoLevel100:
                m_platformId = buf.getInt();
                m_name = buf.getPointer() != 0 ? "" : null;
                m_domain = buf.getPointer() != 0 ? "" : null;
                m_verMajor = buf.getInt();
                m_verMinor = buf.getInt();

                readStrings(buf);
                break;
        }
    }

    /**
     * Read the strings for this workstation from the DCE/RPC buffer
     *
     * @param buf DCEBuffer
     * @exception DCEBufferException DCE buffer error
     */
    public void readStrings(DCEBuffer buf)
            throws DCEBufferException {

        // Read the strings for this share information
        switch (getInformationLevel()) {

            // Information level 100
            case InfoLevel100:
                if (m_name != null)
                    m_name = buf.getString(DCEBuffer.ALIGN_INT);
                if (m_domain != null)
                    m_domain = buf.getString(DCEBuffer.ALIGN_INT);
                break;
        }
    }

    /**
     * Return the workstation information as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();
        str.append("[");

        str.append(getWorkstationName());
        str.append(":Domain=");
        str.append(getDomain());
        str.append(":User=");
        str.append(getUserName());
        str.append(":Id=");
        str.append(getPlatformId());

        str.append(":v");
        str.append(getMajorVersion());
        str.append(".");
        str.append(getMinorVersion());

        // Optional strings

        if (getLogonDomain() != null) {
            str.append(":Logon=");
            str.append(getLogonDomain());
        }

        if (getOtherDomains() != null) {
            str.append(":Other=");
            str.append(getOtherDomains());
        }

        // Return the workstation information as a string

        str.append("]");
        return str.toString();
    }
}
