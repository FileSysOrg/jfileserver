/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.smb.dcerpc;

import org.filesys.util.DataPacker;

/**
 * Universal Unique Identifier Class
 *
 * @author gkspencer
 */
public class UUID {

    //	UUID constants
    public static final int UUID_LENGTH = 36;
    public static final int UUID_LENGTH_BINARY = 16;
    private static final String UUID_VALIDCHARS = "0123456789ABCDEFabcdef";

    //	UUID string
    protected String m_uuid;

    //	Interface version
    protected int m_ifVersion;

    //	UUID bytes
    protected byte[] m_uuidBytes;

    /**
     * Class constructor
     *
     * @param id String
     */
    public UUID(String id) {
        if (validateUUID(id)) {
            m_uuid = id;
            m_ifVersion = 1;
        }
    }

    /**
     * Class constructor
     *
     * @param id  String
     * @param ver int
     */
    public UUID(String id, int ver) {
        if (validateUUID(id)) {
            m_uuid = id;
            m_ifVersion = ver;
        }
    }

    /**
     * Class constructor
     *
     * @param buf byte[]
     * @param off int
     */
    public UUID(byte[] buf, int off) {

        //	Copy the UUID bytes and generate the UUID string
        if ((off + UUID_LENGTH_BINARY) <= buf.length) {

            //	Take a copy of the UUID bytes
            m_uuidBytes = new byte[UUID_LENGTH_BINARY];
            for (int i = 0; i < UUID_LENGTH_BINARY; i++)
                m_uuidBytes[i] = buf[off + i];

            //	Generate the string version of the UUID
            m_uuid = generateUUIDString(m_uuidBytes);
        }
    }

    /**
     * Class constructor
     *
     * @param lowBits  long
     * @param highBits long
     */
    public UUID(long lowBits, long highBits) {
        setUsingLongs( lowBits, highBits);
    }

    /**
     * Default constructor
     */
    protected UUID() {
    }

    /**
     * Determine if the UUID is valid
     *
     * @return boolean
     */
    public final boolean isValid() {
        return m_uuid != null ? true : false;
    }

    /**
     * Check if the UUID has all bits set
     *
     * @return boolean
     */
    public final boolean isAllBits() {
        int idx = 0;

        while ( idx < UUID_LENGTH_BINARY) {
            if ( m_uuidBytes[ idx++] != -1)
                return false;
        }

        return true;
    }

    /**
     * Return the low long from the UUID
     *
     * @return long
     */
    public final long getLowLong() {
        return DataPacker.getIntelLong( m_uuidBytes, 0);
    }

    /**
     * Return the high long from the UUID
     *
     * @return long
     */
    public final long getHighLong() {
        return DataPacker.getIntelLong( m_uuidBytes, 8);
    }

    /**
     * Return the UUID string
     *
     * @return String
     */
    public final String getUUID() {
        return m_uuid;
    }

    /**
     * Return the interface version
     *
     * @return int
     */
    public final int getVersion() {
        return m_ifVersion;
    }

    /**
     * Set the interface version
     *
     * @param ver int
     */
    public final void setVersion(int ver) {
        m_ifVersion = ver;
    }

    /**
     * Set the low/high long values
     *
     * @param lowBits long
     * @param highBits long
     */
    protected void setUsingLongs( long lowBits, long highBits) {

        //  Generate the UUID bytes
        m_uuidBytes = new byte[UUID_LENGTH_BINARY];

        DataPacker.putIntelLong(lowBits, m_uuidBytes, 0);
        DataPacker.putIntelLong(highBits, m_uuidBytes, 8);

        //  Generate the string version of the UUID
        m_uuid = generateUUIDString(m_uuidBytes);
    }

    /**
     * Return the UUID as a byte array
     *
     * @return byte[]
     */
    public final byte[] getBytes() {

        //	Check if the byte array has been created
        if (m_uuidBytes == null) {

            //	Allocate the byte array
            m_uuidBytes = new byte[UUID_LENGTH_BINARY];

            try {

                //	Convert the first integer and pack into the buffer
                String val = m_uuid.substring(0, 8);
                long lval = Long.parseLong(val, 16);
                DataPacker.putIntelInt((int) (lval & 0xFFFFFFFF), m_uuidBytes, 0);

                //	Convert the second word and pack into the buffer
                val = m_uuid.substring(9, 13);
                int ival = Integer.parseInt(val, 16);
                DataPacker.putIntelShort(ival, m_uuidBytes, 4);

                //	Convert the third word and pack into the buffer
                val = m_uuid.substring(14, 18);
                ival = Integer.parseInt(val, 16);
                DataPacker.putIntelShort(ival, m_uuidBytes, 6);

                //	Convert the fourth word and pack into the buffer
                val = m_uuid.substring(19, 23);
                ival = Integer.parseInt(val, 16);
                DataPacker.putShort((short) (ival & 0xFFFF), m_uuidBytes, 8);

                //	Convert the final block of hex pairs to bytes
                int strPos = 24;
                int bytPos = 10;

                for (int i = 0; i < 6; i++) {
                    val = m_uuid.substring(strPos, strPos + 2);
                    m_uuidBytes[bytPos++] = (byte) (Short.parseShort(val, 16) & 0xFF);
                    strPos += 2;
                }
            }
            catch (NumberFormatException ex) {
                m_uuidBytes = null;
            }
        }

        //	Return the UUID bytes
        return m_uuidBytes;
    }

    /**
     * Validate a UUID string
     *
     * @param idStr String
     * @return boolean
     */
    public static final boolean validateUUID(String idStr) {

        //	Check if the UUID string is the correct length
        if (idStr == null || idStr.length() != UUID_LENGTH)
            return false;

        //	Check for seperators
        if (idStr.charAt(8) != '-' || idStr.charAt(13) != '-' || idStr.charAt(18) != '-' || idStr.charAt(23) != '-')
            return false;

        //	Check for hex digits
        int i = 0;
        for (i = 0; i < 8; i++)
            if (UUID_VALIDCHARS.indexOf(idStr.charAt(i)) == -1)
                return false;
        for (i = 9; i < 13; i++)
            if (UUID_VALIDCHARS.indexOf(idStr.charAt(i)) == -1)
                return false;
        for (i = 14; i < 18; i++)
            if (UUID_VALIDCHARS.indexOf(idStr.charAt(i)) == -1)
                return false;
        for (i = 19; i < 23; i++)
            if (UUID_VALIDCHARS.indexOf(idStr.charAt(i)) == -1)
                return false;
        for (i = 24; i < 36; i++)
            if (UUID_VALIDCHARS.indexOf(idStr.charAt(i)) == -1)
                return false;

        //	Valid UUID string
        return true;
    }

    /**
     * Generate a UUID string from the binary representation
     *
     * @param buf byte[]
     * @return String
     */
    public static final String generateUUIDString(byte[] buf) {

        //	Build up the UUID string
        StringBuffer str = new StringBuffer(UUID_LENGTH);

        //	Convert the first longword
        int ival = DataPacker.getIntelInt(buf, 0);
        str.append(Integer.toHexString(ival));
        while (str.length() != 8)
            str.insert(0, ' ');
        str.append("-");

        //	Convert the second word
        ival = DataPacker.getIntelShort(buf, 4) & 0xFFFF;
        str.append(Integer.toHexString(ival));
        while (str.length() != 13)
            str.insert(9, '0');
        str.append("-");

        //	Convert the third word
        ival = DataPacker.getIntelShort(buf, 6) & 0xFFFF;
        str.append(Integer.toHexString(ival));
        while (str.length() != 18)
            str.insert(14, '0');
        str.append("-");

        //	Convert the remaining bytes
        for (int i = 8; i < UUID_LENGTH_BINARY; i++) {

            //	Get the current byte value and add to the string
            ival = (buf[i] & 0xFF);
            if (ival < 16)
                str.append('0');
            str.append(Integer.toHexString(ival));

            //	Add the final seperator
            if (i == 9)
                str.append("-");
        }

        //	Return the UUID string
        return str.toString();
    }

    /**
     * Compare a UUID with the current UUID
     *
     * @param id UUID
     * @return boolean
     */
    public final boolean compareTo(UUID id) {

        //	Compare the UUID versions
        if (getVersion() != id.getVersion())
            return false;

        //	Compare the UUID bytes
        byte[] thisBytes = getBytes();
        byte[] idBytes = id.getBytes();

        for (int i = 0; i < UUID_LENGTH_BINARY; i++)
            if (thisBytes[i] != idBytes[i])
                return false;
        return true;
    }

    /**
     * Write the binary UUID to the specified buffer, and optionally the UUID version
     *
     * @param buf      byte[]
     * @param off      int
     * @param writeVer boolean
     * @return int
     */
    public final int storeUUID(byte[] buf, int off, boolean writeVer) {

        //	Get the UUID bytes
        int pos = off;
        byte[] uuidByts = getBytes();
        if (uuidByts == null)
            return pos;

        //	Write the binary UUID to the buffer
        for (int i = 0; i < UUID_LENGTH_BINARY; i++)
            buf[pos + i] = uuidByts[i];
        pos += UUID_LENGTH_BINARY;

        //	Check if version should be written to the buffer
        if (writeVer) {
            DataPacker.putIntelInt(getVersion(), buf, pos);
            pos += 4;
        }

        //	Return the new buffer position
        return pos;
    }

    /**
     * Return the UUID as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(m_uuid);
        str.append(":");
        str.append(m_ifVersion);
        str.append("]");

        return str.toString();
    }

    /***********************************************************************************************
     * Test Code
     *
     * @param args String[]
     */
/**
    public final static void main(String[] args) {
        System.out.println("UUID Test");
        System.out.println("---------");

        String[] uuids = {"12345678-1234-abcd-ef00-01234567cffb",
                "8a885d04-1ceb-11c9-9fe8-08002b104860",
                "338cd001-2244-31f1-aaaa-900038001003",
                "367abb81-9844-35f1-ad32-98f038001003",
                "4b324fc8-1670-01d3-1278-5a47bf6ee188",
                "6bffd098-a112-3610-9833-46c3f87e345a",
                "12345678-1234-abcd-ef00-0123456789ac",
                "12345778-1234-abcd-ef00-0123456789ab",
                "1ff70682-0a51-30e8-076d-740be8cee98b"
        };

        //	Validate and convert the UUIDs

        for (int i = 0; i < uuids.length; i++) {
            UUID u = new UUID(uuids[i]);
            if (u.isValid()) {
                System.out.println("" + (i + 1) + ": " + u.toString());
                byte[] bytes = u.getBytes();
                HexDump.Dump(bytes, bytes.length, 0);
                System.out.println("Convert to string: " + generateUUIDString(bytes));
            } else
                System.out.println("Invalid UUID: " + uuids[i]);
        }
    }
**/
}
