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
import org.filesys.smb.SMBDate;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCEReadable;

/**
 * Performance Data Block Class
 *
 * <p>
 * Contains the details from PERF_DATA_BLOCK
 *
 * @author gkspencer
 */
public class PerfDataBlock implements DCEReadable {

    // Little endian flag, version and revision
    private int m_endian;
    private int m_version;
    private int m_revision;

    // Total length and header length of the perf data block
    private int m_totalLen;
    private int m_headerLen;

    // Number of object types in this block
    private int m_numObjs;

    // Default object
    private int m_defObj;

    // Time when the system is monitored
    private SMBDate m_sysTime;

    // Performance counter value in counts, counts per second and 100 nanosecond units
    private long m_perfTime;
    private long m_perfFreq;
    private long m_perfFreq100Ns;

    // System name
    private String m_sysName;

    /**
     * Default constructor
     */
    public PerfDataBlock() {
    }

    /**
     * Read the performance data block object
     *
     * @param buf DCEBuffer
     */
    public void readObject(DCEBuffer buf)
            throws DCEBufferException {

        // Check for the signature string
        String sig = buf.getChars(4);
        if (sig.compareTo("PERF") != 0)
            throw new DCEBufferException("Invalid perf block");

        // Read the endian flag, version and revision
        m_endian = buf.getInt();
        m_version = buf.getInt();
        m_revision = buf.getInt();

        // Get the total and header lengths
        m_totalLen = buf.getInt();
        m_headerLen = buf.getInt();

        // Get the number of object types being monitored and default object index
        m_numObjs = buf.getInt();
        m_defObj = buf.getInt();

        // Get the system time (UTC)
        m_sysTime = NTTime.toSMBDate(buf.getLong());

        // Get the performance time/frequency
        m_perfTime = buf.getLong();
        m_perfFreq = buf.getLong();
        m_perfFreq100Ns = buf.getLong();

        // Get the system name length (in bytes) and offset
        int nameLen = buf.getInt();
        buf.getInt(); // name offset

        m_sysName = buf.getChars(nameLen / 2 - 2);
        buf.skipBytes(2); // null from system name
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
}
