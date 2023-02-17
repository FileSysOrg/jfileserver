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

package org.filesys.server.filesys.event;

import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.NetworkFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;

/**
 * Change Event Class
 *
 * <p>Contains the details of a filesystem change
 *
 * @author gkspencer
 */
public class ChangeEvent {

    // Change event flags
    public enum Flags {
        Directory,
        Closed,
        PostProcessed,
        Ignore,
        FileDetails
    }

    // Invalid event id value
    public static final long INVALID_EVENTID    = 0L;

    // Change type
    private FSChange m_change;

    // Change file/directory path
    private String m_path;

    // Event flags
    private EnumSet<Flags> m_flags;

    // Original file name for file/directory rename
    private String m_oldPath;

    // File id of the file/directory
    private long m_fileId;

    // Raw timestamp of the event
    private long m_eventTime;

    // Filesystem id of the filesystem that generated the event
    private long m_fsysId;

    // Filesystem event id, may not be set until the event is persisted
    private long m_eventId;

    // File details, if available
    //
    // File size
    private long m_fileSize;

    // File attributes
    private int m_fileAttr;

    // Modification date/time
    private long m_fileModifyDateTime;

    /**
     * Default constructor
     */
    public ChangeEvent() {
    }

    /**
     * Class constructor
     *
     * @param change FSChange
     * @param path   String
     * @param fileId long
     * @param dir    boolean
     */
    public ChangeEvent(FSChange change, String path, long fileId, boolean dir) {
        m_change = change;
        m_path   = path;
        m_fileId = fileId;

        if ( dir)
            m_flags  = EnumSet.of( Flags.Directory);

        //	Normalize the path
        if (m_path.indexOf('/') != -1)
            m_path = m_path.replace('/', '\\');

        // Set the raw event timestamp
        m_eventTime = System.currentTimeMillis();

        // Invalidate the event id
        m_eventId = INVALID_EVENTID;
    }

    /**
     * Class constructor
     *
     * @param change FSChange
     * @param path    String
     * @param oldPath String
     * @param fileId  long
     * @param dir     boolean
     */
    public ChangeEvent(FSChange change, String path, String oldPath, long fileId, boolean dir) {
        m_change  = change;
        m_path    = path;
        m_oldPath = oldPath;
        m_fileId  = fileId;

        if ( dir)
            m_flags  = EnumSet.of( Flags.Directory);

        //	Normalize the paths
        if (m_path.indexOf('/') != -1)
            m_path = m_path.replace('/', '\\');

        if (m_oldPath.indexOf('/') != -1)
            m_oldPath = m_oldPath.replace('/', '\\');

        // Set the raw event timestamp
        m_eventTime = System.currentTimeMillis();

        // Invalidate the event id
        m_eventId = INVALID_EVENTID;
    }

    /**
     * Class constructor
     *
     * @param change FSChange
     * @param eventId long
     * @param eventTime long
     * @param path    String
     * @param oldPath String
     * @param fileId  long
     * @param dir     boolean
     */
    public ChangeEvent(FSChange change, long eventId, long eventTime, String path, String oldPath, long fileId, boolean dir) {
        m_change  = change;
        m_eventId = eventId;

        // Set the raw event timestamp
        m_eventTime = eventTime;

        m_path    = path;
        m_oldPath = oldPath;
        m_fileId  = fileId;

        if ( dir)
            m_flags  = EnumSet.of( Flags.Directory);

        //	Normalize the paths
        if (m_path.indexOf('/') != -1)
            m_path = m_path.replace('/', '\\');

        if (m_oldPath.indexOf('/') != -1)
            m_oldPath = m_oldPath.replace('/', '\\');
    }

    /**
     * Class constructor
     *
     * @param change FSChange
     * @param path   String
     * @param fileId long
     * @param flags  EnumSet&lt;Flags&gt;
     */
    public ChangeEvent(FSChange change, String path, long fileId, EnumSet<Flags> flags) {
        m_change = change;
        m_path   = path;
        m_fileId = fileId;

        m_flags  = flags;

        //	Normalize the path
        if (m_path.indexOf('/') != -1)
            m_path = m_path.replace('/', '\\');

        // Set the raw event timestamp
        m_eventTime = System.currentTimeMillis();

        // Invalidate the event id
        m_eventId = INVALID_EVENTID;
    }

    /**
     * Class constructor
     *
     * @param change FSChange
     * @param path String
     * @param fInfo FileInfo
     * @param flags  EnumSet&lt;Flags&gt;
     */
    public ChangeEvent( FSChange change, String path, FileInfo fInfo, EnumSet<Flags> flags) {
        m_change = change;
        m_path   = path;
        m_fileId = fInfo.getFileId();
        m_flags  = flags;

        if ( fInfo.isDirectory())
            setFlag( Flags.Directory);

        // Set the raw event timestamp
        m_eventTime = System.currentTimeMillis();

        // Set file details
        m_fileSize = fInfo.getSize();
        m_fileAttr = fInfo.getFileAttributes();
        m_fileModifyDateTime = fInfo.getModifyDateTime();

        setFlag( Flags.FileDetails);
    }

    /**
     * Class constructor
     *
     * @param change FSChange
     * @param netFile NetworkFile
     * @param flags  EnumSet&lt;Flags&gt;
     */
    public ChangeEvent( FSChange change, NetworkFile netFile, EnumSet<Flags> flags) {
        m_change = change;
        m_path   = netFile.getFullName();
        m_fileId = netFile.getFileId();
        m_flags  = flags;

        if ( netFile.isDirectory())
            setFlag( Flags.Directory);

        // Set the raw event timestamp
        m_eventTime = System.currentTimeMillis();

        // Set file details
        m_fileSize = netFile.getFileSize();
        m_fileAttr = netFile.getFileAttributes();
        m_fileModifyDateTime = netFile.getModifyDate();

        setFlag( Flags.FileDetails);
    }

    /**
     * Return the change type
     *
     * @return FSChange
     */
    public final FSChange isChange() {
        return m_change;
    }

    /**
     * Return the file/directory path
     *
     * @return String
     */
    public final String getPath() {
        return m_path;
    }

    /**
     * Return the file/directory name only by stripping any leading path
     *
     * @return String
     */
    public final String getShortFileName() {

        //	Find the last '\' in the path string
        int pos = m_path.lastIndexOf("\\");
        if (pos != -1)
            return m_path.substring(pos + 1);
        return m_path;
    }

    /**
     * Return the old file/directory name, for rename events
     *
     * @return String
     */
    public final String getOldPath() {
        return m_oldPath;
    }

    /**
     * Return the filesystem id of the filesystem that generated the event
     *
     * @return long
     */
    public final long getFilesystemId() { return m_fsysId; }

    /**
     * Update the change event type, path and flags
     *
     * @param fsChange FSChange
     * @param path String
     * @param flags EnumSet&lt;ChangeEvent.Flags&gt;
     */
    public final void updateEvent( FSChange fsChange, String path, EnumSet<ChangeEvent.Flags> flags) {
        m_change = fsChange;
        m_path   = path;

        if ( m_flags == null)
            m_flags = flags;
        else
            m_flags.addAll( flags);
    }

    /**
     * Update the change event typeand flags
     *
     * @param fsChange FSChange
     * @param flags EnumSet&lt;ChangeEvent.Flags&gt;
     */
    public final void updateEvent( FSChange fsChange, EnumSet<ChangeEvent.Flags> flags) {
        m_change = fsChange;

        if ( m_flags == null)
            m_flags = flags;
        else
            m_flags.addAll( flags);
    }

    /**
     * Return the old file/directory name only by stripping any leading path
     *
     * @return String
     */
    public final String getShortOldFileName() {

        //	Check if the old path string is valid
        if (m_oldPath == null)
            return null;

        //	Find the last '\' in the path string
        int pos = m_oldPath.lastIndexOf("\\");
        if (pos != -1)
            return m_oldPath.substring(pos + 1);
        return m_oldPath;
    }

    /**
     * Check if the old file/directory name is valid
     *
     * @return boolean
     */
    public final boolean hasOldPath() {
        return m_oldPath != null ? true : false;
    }

    /**
     * Check if the path refers to a directory
     *
     * @return boolean
     */
    public final boolean isDirectory() {
        return hasFlag( Flags.Directory);
    }

    /**
     * check if the path refers to a file
     *
     * @return boolean
     */
    public final boolean isFile() { return hasFlag( Flags.Directory) == false; }

    /**
     * Check if the event was triggered during a file close
     *
     * @return boolean
     */
    public final boolean isClosed() { return hasFlag( Flags.Closed); }

    /**
     * Check if the event has extra file details
     *
     * @return boolean
     */
    public final boolean hasFileDetails() { return hasFlag( Flags.FileDetails); }

    /**
     * Check if the specified status flag is set for the event
     *
     * @param flg ChangeEvent.Flags
     * @return boolean
     */
    public final boolean hasFlag( ChangeEvent.Flags flg) { return m_flags != null && m_flags.contains( flg); }

    /**
     * Return the date/time of the filesystem event
     *
     * @return long
     */
    public final long getEventDateTime() { return m_eventTime; }

    /**
     * Return the file id of the file/directory
     *
     * @return long
     */
    public final long getFileId() { return m_fileId; }

    /**
     * Check if the event id has been set
     *
     * @return boolean
     */
    public final boolean hasEventId() { return m_eventId != INVALID_EVENTID; }

    /**
     * Return the event id
     *
     * @return long
     */
    public final long getEventId() { return m_eventId; }

    /**
     * Return the file size
     *
     * @return long
     */
    public final long getFileSize() { return m_fileSize; }

    /**
     * Return the file attributes
     *
     * @return int
     */
    public final int getFileAttributes() { return m_fileAttr; }

    /**
     * Return the file modification timestamp
     *
     * @return long
     */
    public final long getModificationDateTime() { return m_fileModifyDateTime; }

    /**
     * Set the change event type
     *
     * @param evtTyp FSChange
     */
    public final void setChange( FSChange evtTyp) { m_change = evtTyp; }

    /**
     * Set the path
     *
     * @param path String
     */
    public final void setPath(String path) { m_path = path; }

    /**
     * Set the old path, for rename events
     *
     * @param oldPath String
     */
    public final void setOldPath(String oldPath) { m_oldPath = oldPath; }

    /**
     * Set the event id
     *
     * @param eventId long
     */
    public final void setEventId(long eventId) { m_eventId = eventId; }

    /**
     * Set the file id
     *
     * @param fileId long
     */
    public final void setFileId(long fileId) { m_fileId = fileId; }

    /**
     * Set the event date/time
     *
     * @param evtTime long
     */
    public final void setEventTime( long evtTime) { m_eventTime = evtTime; }

    /**
     * Set the specified event flag
     *
     * @param flg ChangeEvent.Flags
     */
    public final void setFlag(ChangeEvent.Flags flg) {
        if ( m_flags == null)
            m_flags = EnumSet.of( flg);
        else
            m_flags.add( flg);
    }

    /**
     * Set the specified event flags
     *
     * @param flgs EnumSet&lt;ChangeEvent.Flags&gt;
     */
    public final void setFlags( EnumSet<ChangeEvent.Flags> flgs) {
        if ( m_flags == null)
            m_flags = flgs;
        else
            m_flags.addAll( flgs);
    }

    /**
     * Clear the specified event flag
     *
     * @param flg ChangeEvent.Flags
     */
    public final void clearFlag(ChangeEvent.Flags flg) {
        if ( m_flags != null)
            m_flags.remove( flg);
    }

    /**
     * Set the filesystem id of the filesystem that generated the event
     *
     * @param fsysId long
     */
    public final void setFilesystemId(long fsysId) { m_fsysId = fsysId; }

    /**
     * Set the file size
     *
     * @param fsize long
     */
    public final void setFileSize(long fsize) { m_fileSize = fsize; }

    /**
     * Set the file attributes
     *
     * @param fattr int
     */
    public final void setFileAttributes(int fattr) { m_fileAttr = fattr; }

    /**
     * Set the file modification timestamp
     *
     * @param modtime long
     */
    public final void setModificationDateTime(long modtime) { m_fileModifyDateTime = modtime; }

    /**
     * Return the notify change event as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(isChange().name());
        str.append(" at ");
        str.append( LocalDateTime.ofInstant( Instant.ofEpochMilli( getEventDateTime()), ZoneId.of( "UTC")));

        str.append(":");
        str.append(getPath());

        if ( m_flags != null) {
            str.append(",Flags=");
            str.append( m_flags);
        }

        if (hasOldPath()) {
            str.append(",Old=");
            str.append(getOldPath());
        }

        str.append(",FID=");
        str.append( getFileId());

        if ( hasEventId()) {
            str.append(",EventId=");
            str.append( getEventId());
        }

        if ( getFilesystemId() != 0L) {
            str.append(",FSysId=");
            str.append( getFilesystemId());
        }

        str.append(",FSize=");
        str.append( getFileSize());
        str.append(",Attr=0x");
        str.append( Integer.toHexString( getFileAttributes()));
        str.append(",Modify=");
        str.append( getModificationDateTime());

        str.append("]");

        return str.toString();
    }
}
