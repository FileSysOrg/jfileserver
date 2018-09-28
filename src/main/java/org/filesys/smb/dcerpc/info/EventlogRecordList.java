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

import java.util.*;

import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;

/**
 * Eventlog Record List Class
 *
 * @author gkspencer
 */
public class EventlogRecordList {

    // List of eventlog records
    private List<EventlogRecord> m_records;

    /**
     * Default constructor
     */
    public EventlogRecordList() {
        m_records = new ArrayList<EventlogRecord>();
    }

    /**
     * Return the eventlog record list
     *
     * @return List of EventlogRecords
     */
    public final List<EventlogRecord> getRecordList() {
        return m_records;
    }

    /**
     * Return the specified eventlog record from the list
     *
     * @param idx int
     * @return EventlogRecord
     */
    public final EventlogRecord getRecordAt(int idx) {
        if (m_records == null || idx >= m_records.size())
            return null;
        return (EventlogRecord) m_records.get(idx);
    }

    /**
     * Return the count of eventlog records in the list
     *
     * @return int
     */
    public final int numberOfRecords() {
        return m_records.size();
    }

    /**
     * Add an eventlog record to the list
     *
     * @param rec EventlogRecord
     */
    public final void addRecord(EventlogRecord rec) {
        m_records.add(rec);
    }

    /**
     * Read the eventlog records from the DCE buffer
     *
     * @param buf DCEBuffer
     * @param cnt int
     * @throws DCEBufferException DCE buffer error
     */
    public void readRecords(DCEBuffer buf, int cnt)
            throws DCEBufferException {

        // Clear out any eventlog records
        m_records.clear();

        // Read the buffer size
        int siz = buf.getInt();

        // Read eventlog records until the buffer has been exhausted
        while (cnt-- > 0) {

            // Read an event log record
            EventlogRecord rec = new EventlogRecord();
            rec.readObject(buf);

            // Add to the list
            m_records.add(rec);
        }
    }

    /**
     * Write the eventlog records to the DCE buffer
     *
     * @param buf DCEBuffer
     */
    public void writeObject(DCEBuffer buf) {
    }
}
