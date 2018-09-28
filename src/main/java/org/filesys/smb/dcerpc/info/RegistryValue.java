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
import org.filesys.smb.dcerpc.client.RegistryType;
import org.filesys.util.DataPacker;
import org.filesys.util.HexDump;
import org.filesys.util.StringList;

/**
 * Registry Value Class
 *
 * <p>
 * Contains the value and type of a registry value.
 *
 * @author gkspencer
 */
public class RegistryValue implements DCEReadable {

    // Registry value and type
    private byte[] m_rawValue;
    private String m_regName;
    private int m_regType;

    /**
     * Default constructor
     */
    public RegistryValue() {
    }

    /**
     * Class constructor
     *
     * @param name String
     */
    public RegistryValue(String name) {
        m_regName = name;
    }

    /**
     * Class constructor
     *
     * @param name  String
     * @param typ   int
     * @param value byte[]
     */
    public RegistryValue(String name, int typ, byte[] value) {
        m_regName = name;
        m_regType = typ;
        m_rawValue = value;
    }

    /**
     * Class constructor
     *
     * @param name  String
     * @param value String
     */
    public RegistryValue(String name, String value) {
        m_regName = name;
        m_regType = RegistryType.REG_SZ;

        m_rawValue = new byte[(value.length() * 2) + 2];
        DataPacker.putUnicodeString(value, m_rawValue, 0, true);
    }

    /**
     * Class constructor
     *
     * @param name  String
     * @param value int
     */
    public RegistryValue(String name, int value) {
        m_regName = name;
        m_regType = RegistryType.REG_DWORD;

        m_rawValue = new byte[4];
        DataPacker.putIntelInt(value, m_rawValue, 0);
    }

    /**
     * Return the raw registry value
     *
     * @return byte[]
     */
    public final byte[] getRawValue() {
        return m_rawValue;
    }

    /**
     * Return the value as an object, converted using the type
     *
     * @return Object
     */
    public final Object getValue() {

        // Check if there is a raw value
        if (getRawValue() == null)
            return null;

        // Get the raw data and convert to the appropriate object type
        byte[] buf = getRawValue();
        Object objVal = null;

        switch (getDataType()) {

            // String
            case RegistryType.REG_SZ:
                objVal = DataPacker.getUnicodeString(buf, 0, buf.length / 2);
                break;

            // String with unexpanded environment variables
            case RegistryType.REG_EXPAND_SZ:
                if (buf.length <= 2)
                    objVal = "";
                else
                    objVal = DataPacker.getUnicodeString(buf, 0, (buf.length / 2) - 1);
                break;

            // Integer
            case RegistryType.REG_DWORD:
                objVal = new Integer(DataPacker.getIntelInt(buf, 0));
                break;

            // Multi-string
            case RegistryType.REG_MULTI_SZ:
                objVal = unpackMultiSz();
                break;

            // Default, return the raw data
            default:
                objVal = buf;
                break;
        }

        // Return the object value
        return objVal;
    }

    /**
     * Return the registry value name
     *
     * @return String
     */
    public final String getName() {
        return m_regName;
    }

    /**
     * Return the registry value type
     *
     * @return int
     */
    public final int getDataType() {
        return m_regType;
    }

    /**
     * Return the registry value type string
     *
     * @return String
     */
    public final String getDataTypeString() {
        return RegistryType.getTypeAsString(m_regType);
    }

    /**
     * Read the value and type from a DCE buffer
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public void readValue(DCEBuffer buf)
            throws DCEBufferException {

        // Read the data type
        if (buf.getPointer() != 0)
            m_regType = buf.getInt();

        // Get the raw data
        if (buf.getPointer() != 0)
            m_rawValue = buf.getDataBlock(DCEBuffer.ALIGN_INT);

        // Read the return lengths
        if (buf.getPointer() != 0)
            buf.skipBytes(4);
        if (buf.getPointer() != 0)
            buf.skipBytes(4);
    }

    /**
     * Read the registry value information
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public void readObject(DCEBuffer buf)
            throws DCEBufferException {

        // Read the Unicode header and type string
        int len = buf.getUnicodeHeaderLength();
        if (len > 0)
            m_regName = buf.getString();

        if (buf.getPointer() != 0)
            m_regType = buf.getInt() & 0xFF;

        if (buf.getPointer() != 0)
            m_rawValue = buf.getDataBlock(DCEBuffer.ALIGN_INT);

        if (buf.getPointer() != 0)
            buf.skipBytes(4);
        if (buf.getPointer() != 0)
            buf.skipBytes(4);
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
     * Unpack a REG_MULTI_SZ data type into a list of strings
     *
     * @return StringList
     */
    private final StringList unpackMultiSz() {

        // Create the list for the strings
        StringList strList = new StringList();
        if (getRawValue() == null || getRawValue().length == 0)
            return strList;

        // Create a buffer for building the individual strings
        StringBuffer buf = new StringBuffer(256);

        // Process the raw data
        for (int i = 0; i < m_rawValue.length; i += 2) {

            // Get the current character
            char ch = (char) (m_rawValue[i] + ((int) m_rawValue[i + 1] << 8));
            if (ch == 0) {

                // Add the current string to the list and start a new string
                strList.addString(buf.toString());
                buf.setLength(0);
            } else {

                // Append the character to the current string
                buf.append(ch);
            }
        }

        // Return the list of strings
        return strList;
    }

    /**
     * Return the registry value as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(getName());
        str.append(":");
        str.append(getDataTypeString());
        str.append(":");
        if (getValue() != null) {
            if (getDataType() == RegistryType.REG_SZ || getDataType() == RegistryType.REG_DWORD
                    || getDataType() == RegistryType.REG_MULTI_SZ || getDataType() == RegistryType.REG_EXPAND_SZ)
                str.append(getValue());
            else
                str.append(HexDump.hexString((byte[]) getValue()));
        }
        str.append("]");

        return str.toString();
    }
}
