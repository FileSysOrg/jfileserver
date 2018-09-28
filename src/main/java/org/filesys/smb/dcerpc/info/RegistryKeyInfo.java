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

import org.filesys.smb.NTTime;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCEReadable;

/**
 * Registry Key Information Class
 *
 * @author gkspencer
 */
public class RegistryKeyInfo implements DCEReadable {

    // Key details
    private String m_className;

    private int m_numSubKeys;
    private int m_maxSubKeyLen;
    private int m_maxSubKeySize;
    private int m_numValues;
    private int m_maxValNameLen;
    private int m_maxValBufSize;
    private int m_secDescSize;

    private long m_modifyTime;

    /**
     * Default constructor
     */
    public RegistryKeyInfo() {
    }

    /**
     * Determine if the key information has a class name
     *
     * @return boolean
     */
    public final boolean hasClassName() {
        return m_className != null ? true : false;
    }

    /**
     * Return the class name
     *
     * @return String
     */
    public final String getClassName() {
        return m_className;
    }

    /**
     * Return the maximum number of subkeys
     *
     * @return int
     */
    public final int getNumberOfSubkeys() {
        return m_numSubKeys;
    }

    /**
     * Return the maximum subkey name length
     *
     * @return int
     */
    public final int getMaximumSubkeyNameLength() {
        return m_maxSubKeyLen;
    }

    /**
     * Return the maximum subkey class name length
     *
     * @return int
     */
    public final int getMaximumSubkeyClassNameLength() {
        return m_maxSubKeySize;
    }

    /**
     * Return the number of values associated with the key
     *
     * @return int
     */
    public final int getNumberOfValues() {
        return m_numValues;
    }

    /**
     * Return the maximum value name length
     *
     * @return int
     */
    public final int getMaximumValueNameLength() {
        return m_maxValNameLen;
    }

    /**
     * Return the maximum value data length
     *
     * @return int
     */
    public final int getMaximumValueLength() {
        return m_maxValBufSize;
    }

    /**
     * Return the security descriptor length
     *
     * @return int
     */
    public final int getSecurityDescriptorLength() {
        return m_secDescSize;
    }

    /**
     * Return the key last modify date/time
     *
     * @return long
     */
    public final long getModifyDateTime() {
        return m_modifyTime;
    }

    /**
     * Read the registry key information
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public void readObject(DCEBuffer buf)
            throws DCEBufferException {

        // Get the class name string length
        int hasCls = buf.getUnicodeHeaderLength();
        if (hasCls != -1)
            buf.skipPointer();

        // Get the various registry key counts/lengths
        m_numSubKeys = buf.getInt();
        m_maxSubKeyLen = buf.getInt();
        m_maxSubKeySize = buf.getInt();

        m_numValues = buf.getInt();
        m_maxValNameLen = buf.getInt();
        m_maxValBufSize = buf.getInt();

        m_secDescSize = buf.getInt();

        // Get the modify date/time and convert to a Java date/time value
        long modTime = buf.getLong();
        if (modTime != 0)
            m_modifyTime = NTTime.toSMBDate(modTime).getTime();

        // Get the class name, if specified
        if (hasCls != -1)
            m_className = buf.getString();
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
     * Return the registry key information as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");

        if (hasClassName()) {
            str.append(getClassName());
            str.append(":");
        }

        str.append(getNumberOfSubkeys());
        str.append("/");
        str.append(getMaximumSubkeyNameLength());
        str.append("/");
        str.append(getMaximumSubkeyClassNameLength());

        str.append(",");

        str.append(getNumberOfValues());
        str.append("/");
        str.append(getMaximumValueNameLength());
        str.append("/");
        str.append(getMaximumValueLength());

        str.append(",");
        str.append(getSecurityDescriptorLength());
        str.append(",");

        if (getModifyDateTime() != 0)
            str.append(new java.util.Date(getModifyDateTime()));
        str.append("]");

        return str.toString();
    }
}
