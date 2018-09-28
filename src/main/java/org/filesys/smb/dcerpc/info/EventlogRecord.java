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
import org.filesys.smb.dcerpc.DCEReadable;
import org.filesys.util.StringList;

/**
 * Eventlog Record Class
 *
 * <p>Contains the details of an eventlog record in a remote event log.
 *
 * @author gkspencer
 */
public class EventlogRecord implements DCEReadable {

    //	Event types
    public static final int TypeSuccess         = 0x0000;
    public static final int TypeError           = 0x0001;
    public static final int TypeWarning         = 0x0002;
    public static final int TypeInformation     = 0x0004;
    public static final int TypeAuditSuccess    = 0x0008;
    public static final int TypeAuditFailure    = 0x0010;

    //  Event id masks
    public static final int IdSeverityMask      = 0x80000000;
    public static final int IdCustomerCode      = 0x20000000;
    public static final int IdFacilityMask      = 0x0FFF0000;
    public static final int IdEventCodeMask     = 0x0000FFFF;

    //  Event id severity codes
    public static final int SeveritySuccess     = 0;
    public static final int SeverityInfo        = 0x40000000;
    public static final int SeverityWarn        = 0x80000000;
    public static final int SeverityError       = 0xC0000000;

    //	Record details
    private int m_recno;
    private Date m_timeGenerated;
    private Date m_timeWritten;

    //	Event details
    private int m_eventId;
    private int m_eventType;
    private int m_eventCategory;

    //	Strings and event data
    private String m_source;
    private String m_computer;
    private StringList m_strings;

    /**
     * Default constructor
     */
    public EventlogRecord() {
    }

    /**
     * Return the record number
     *
     * @return int
     */
    public final int getRecordNumber() {
        return m_recno;
    }

    /**
     * Return the time the event was generated
     *
     * @return Date
     */
    public final Date getTimeGenerated() {
        return m_timeGenerated;
    }

    /**
     * Return the time the event was written
     *
     * @return Date
     */
    public final Date getTimeWritten() {
        return m_timeWritten;
    }

    /**
     * Return the raw/unmasked event id
     *
     * @return int
     */
    public final int getRawEventId() {
        return m_eventId;
    }

    /**
     * Return the event id
     *
     * @return int
     */
    public final int getEventId() {
        return m_eventId & IdEventCodeMask;
    }

    /**
     * Return the event severity
     *
     * @return int
     */
    public final int getEventSeverity() {
        return m_eventId & IdSeverityMask;
    }

    /**
     * Return the event facility code
     *
     * @return int
     */
    public final int getEventFacility() {
        return (m_eventId & IdFacilityMask) >> 16;
    }

    /**
     * Check if this is a system or customer event
     *
     * @return boolean
     */
    public final boolean isCustomerEvent() {
        return (m_eventId & IdCustomerCode) != 0 ? true : false;
    }

    /**
     * Return the event type
     *
     * @return int
     */
    public final int getEventType() {
        return m_eventType;
    }

    /**
     * Return the event type as a string
     *
     * @return String
     */
    public final String getEventTypeAsString() {

        String typ = "";
        switch (m_eventType) {
            case TypeSuccess:
                typ = "Success";
                break;
            case TypeError:
                typ = "Error";
                break;
            case TypeWarning:
                typ = "Warning";
                break;
            case TypeInformation:
                typ = "Information";
                break;
            case TypeAuditSuccess:
                typ = "AuditSuccess";
                break;
            case TypeAuditFailure:
                typ = "AuditFailure";
                break;
        }

        return typ;
    }

    /**
     * Return the event severity as a string
     *
     * @return String
     */
    public final String getEventSeverityAsString() {

        String sev = "";
        switch (getEventSeverity()) {
            case SeveritySuccess:
                sev = "Success";
                break;
            case SeverityInfo:
                sev = "Info";
                break;
            case SeverityWarn:
                sev = "Warn";
                break;
            case SeverityError:
                sev = "Error";
                break;
        }

        return sev;
    }

    /**
     * Return the event category
     *
     * @return int
     */
    public final int getEventCategory() {
        return m_eventCategory;
    }

    /**
     * Return the event source
     *
     * @return String
     */
    public final String getEventSource() {
        return m_source;
    }

    /**
     * Return the event that generated the event
     *
     * @return String
     */
    public final String getEventHost() {
        return m_computer;
    }

    /**
     * Return the event strings
     *
     * @return StringList
     */
    public final StringList getEventStringList() {
        return m_strings;
    }

    /**
     * Return the event strings as a concatenated string
     *
     * @return String
     */
    public final String getEventString() {

        // Check if there are any event strings
        if (m_strings == null || m_strings.numberOfStrings() == 0)
            return "";

        // Build the event string
        StringBuilder str = new StringBuilder(256);

        for (int i = 0; i < m_strings.numberOfStrings(); i++) {
            str.append(m_strings.getStringAt(i));
            str.append(", ");
        }

        // Trim the last comma from the string
        if (str.length() > 0)
            str.setLength(str.length() - 1);

        // Return the string
        return str.toString();
    }

    /**
     * Read the eventlog record from the DCE/RPC buffer
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public void readObject(DCEBuffer buf)
            throws DCEBufferException {

        // Read the eventlog record details
        int rdpos = buf.getReadPosition();
        int reclen = buf.getInt();
        if (reclen == 0)
            throw new DCEBufferException("No more event records");

        buf.skipPointer();

        m_recno = buf.getInt();

        long timeVal = ((long) buf.getInt()) & 0xFFFFFFFFL;
        if (timeVal != 0)
            m_timeGenerated = new Date(timeVal * 1000L);

        timeVal = ((long) buf.getInt()) & 0xFFFFFFFFL;
        if (timeVal != 0)
            m_timeWritten = new Date(timeVal * 1000L);

        m_eventId = buf.getInt();
        m_eventType = buf.getShort();

        int numStrs = buf.getShort();
        m_eventCategory = buf.getShort();
        buf.skipBytes(6);

        int strOff = buf.getInt();
        int sidLen = buf.getInt();
        int sidOff = buf.getInt();
        int datLen = buf.getInt();
        int datOff = buf.getInt();

        m_source = buf.getUnicodeString();
        m_computer = buf.getUnicodeString();

        buf.skipBytes(sidLen);

        if (((buf.getReadPosition() + 3) & 0xFFFFFFFC) != buf.getReadPosition())
            buf.getShort();

        m_strings = new StringList();

        for (int i = 0; i < numStrs; i++) {
            String s = buf.getUnicodeString();
            if (s != null && s.endsWith("\r\n"))
                s = s.substring(0, s.length() - 2);
            m_strings.addString(s);
        }

        // Leave the position at the next eventlog record
        buf.positionAt(rdpos + reclen);
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
     * Return the eventlog record as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(getEventHost());
        str.append(":");
        str.append(getEventSource());
        str.append(",");
        str.append(getEventSeverityAsString());
        str.append(",0x");
        str.append(Integer.toHexString(getEventId()));
        str.append(",");
        str.append(Integer.toHexString(getEventType()));
        str.append(" - ");
        str.append(getEventString());
        str.append("]");

        return str.toString();
    }
}
