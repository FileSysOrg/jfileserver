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

package org.filesys.smb.nt;

import org.filesys.util.DataBuffer;
import org.filesys.util.DataPacker;

/**
 * SymLink Class
 *
 * <p>Contains the details of a symlink reparse point.
 *
 * @author gkspencer
 */
public class SymLink {

    // Constants
    //
    // Offset to the string area of the symlink structure
    public static final int StringOffset = 20;

    // Substitute name
    private String m_substName;

    // Print name
    private String m_printName;

    /**
     * Class constructor
     *
     * @param dataBuf DataBuffer
     */
    public SymLink(DataBuffer dataBuf) {
        parseDataBuffer(dataBuf);
    }

    /**
     * Class constructor
     *
     * @param substName String
     * @param printName String
     */
    public SymLink(String substName, String printName) {
        m_substName = substName;
        m_printName = printName;
    }

    /**
     * Return the substitute name
     *
     * @return String
     */
    public final String getSubstituteName() {
        return m_substName;
    }

    /**
     * Return the print name
     *
     * @return String
     */
    public final String getPrintName() {
        return m_printName;
    }

    /**
     * Parse a symlink reparse point structure to get the symlink details
     *
     * @param symlinkBuf DataBuffer
     */
    public final void parseDataBuffer(DataBuffer symlinkBuf) {

        // Clear the current details
        m_substName = null;
        m_printName = null;

        // Make sure the details are for a symlink
        if (symlinkBuf.getInt() != ReparsePoint.TypeSymLink)
            return;

        symlinkBuf.skipBytes(4);

        // Unpack the substitute name string
        int offset = symlinkBuf.getShort();
        int len = symlinkBuf.getShort();

        m_substName = DataPacker.getUnicodeString(symlinkBuf.getBuffer(), StringOffset + offset, len / 2);

        // Unpack the print name string
        offset = symlinkBuf.getShort();
        len = symlinkBuf.getShort();

        m_printName = DataPacker.getUnicodeString(symlinkBuf.getBuffer(), StringOffset + offset, len / 2);
    }

    /**
     * Return the symlink details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[SymLink subst=");
        str.append(getSubstituteName());
        str.append(",print=");
        str.append(getPrintName());
        str.append("]");

        return str.toString();
    }
}
