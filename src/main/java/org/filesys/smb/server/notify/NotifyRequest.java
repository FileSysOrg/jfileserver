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

package org.filesys.smb.server.notify;

import java.util.Date;
import java.util.Set;

import org.filesys.server.filesys.DiskDeviceContext;
import org.filesys.server.filesys.FileName;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.NotifyChange;
import org.filesys.smb.server.SMBSrvSession;


/**
 * Notify Change Request Details Class
 *
 * @author gkspencer
 */
public class NotifyRequest {

    //	Constants
    public final static long DefaultRequestTimeout = 10000L;    //	10 seconds

    //	Notify change filter
    private Set<NotifyChange> m_filter;

    //	Flag to indicate if sub-directories of the directory being watched will also trigger notifications
    private boolean m_watchTree;

    //	Session that posted the notify change request
    private SMBSrvSession m_sess;

    //	Directory being watched
    private NetworkFile m_watchDir;

    //	Root relative path, normalised to uppercase
    private String m_watchPath;

    //	Unique client request id.
    private long m_id;

    // Tree, process and user ids from the original request
    private int m_tid;
    private int m_pid;
    private int m_uid;

    //	Notifications to buffer whilst waiting for request to be reset
    private int m_maxQueueLen;

    //	Disk device context that the request is associated with
    private DiskDeviceContext m_diskCtx;

    //	Buffered event list
    private NotifyChangeEventList m_bufferedEvents;

    //	Notify request completed flag
    private boolean m_completed;
    private long m_expiresAt;

    //	Flag to indicate that many file changes have occurred and a notify enum status should be returned
    //	to the client
    private boolean m_notifyEnum;

    // Maximum response length
    private int m_maxRespLen;

    /**
     * Class constructor
     *
     * @param filter    Set of NotifyChange
     * @param watchTree boolean
     * @param sess      SMBSrvSession
     * @param dir       NetworkFile
     * @param mid       int
     * @param tid       int
     * @param pid       int
     * @param uid       int
     * @param qlen      int
     */
    public NotifyRequest(Set<NotifyChange> filter, boolean watchTree, SMBSrvSession sess, NetworkFile dir, int mid, int tid, int pid, int uid, int qlen) {
        m_filter = filter;
        m_watchTree = watchTree;
        m_sess = sess;
        m_watchDir = dir;

        m_id  = mid;
        m_tid = tid;
        m_pid = pid;
        m_uid = uid;

        m_maxQueueLen = qlen;

        //	Set the normalised watch path
        m_watchPath = m_watchDir.getFullName().toUpperCase();
        if (m_watchPath.length() == 0)
            m_watchPath = "\\";
        else if (m_watchPath.indexOf('/') != -1)
            m_watchPath = m_watchPath.replace('/', '\\');
    }

    /**
     * Class constructor
     *
     * @param filter    Set of NotifyChange
     * @param watchTree boolean
     * @param sess      SMBSrvSession
     * @param dir       NetworkFile
     * @param id        long
     * @param treeId    int
     * @param procId    int
     * @param vcId      int
     * @param maxRespLen int
     */
    public NotifyRequest(Set<NotifyChange> filter, boolean watchTree, SMBSrvSession sess, NetworkFile dir, long id, int treeId, int procId, int vcId, int maxRespLen) {
        m_filter = filter;
        m_watchTree = watchTree;
        m_sess = sess;
        m_watchDir = dir;

        m_id = id;

        m_tid = treeId;
        m_pid = procId;
        m_uid = vcId;

        m_maxRespLen = maxRespLen;

        //	Set the normalised watch path
        m_watchPath = m_watchDir.getFullName().toUpperCase();
        if (m_watchPath.length() == 0)
            m_watchPath = "\\";
        else if (m_watchPath.indexOf('/') != -1)
            m_watchPath = m_watchPath.replace('/', '\\');
    }

    /**
     * Get the notify change filter
     *
     * @return Set of NotifyChange
     */
    public final Set<NotifyChange> getFilter() {
        return m_filter;
    }

    /**
     * Determine if the request has completed
     *
     * @return boolean
     */
    public final boolean isCompleted() {
        return m_completed;
    }

    /**
     * Determine if the request has expired
     *
     * @param curTime long
     * @return boolean
     */
    public final boolean hasExpired(long curTime) {
        if (isCompleted() == false)
            return false;
        else if (m_expiresAt < curTime)
            return true;
        return false;
    }

    /**
     * Determine if the filter has file name change notification, triggered if a file is created, renamed or deleted
     *
     * @return boolean
     */
    public final boolean hasFileNameChange() {
        return m_filter.contains(NotifyChange.FileName);
    }

    /**
     * Determine if the filter has directory name change notification, triggered if a directory is created or deleted.
     *
     * @return boolean
     */
    public final boolean hasDirectoryNameChange() {
        return m_filter.contains(NotifyChange.DirectoryName);
    }

    /**
     * Determine if the filter has attribute change notification
     *
     * @return boolean
     */
    public final boolean hasAttributeChange() {
        return m_filter.contains(NotifyChange.Attributes);
    }

    /**
     * Determine if the filter has file size change notification
     *
     * @return boolean
     */
    public final boolean hasFileSizeChange() {
        return m_filter.contains(NotifyChange.Size);
    }

    /**
     * Determine if the filter has last write time change notification
     *
     * @return boolean
     */
    public final boolean hasFileWriteTimeChange() {
        return m_filter.contains(NotifyChange.LastWrite);
    }

    /**
     * Determine if the filter has last access time change notification
     *
     * @return boolean
     */
    public final boolean hasFileAccessTimeChange() {
        return m_filter.contains(NotifyChange.LastAccess);
    }

    /**
     * Determine if the filter has creation time change notification
     *
     * @return boolean
     */
    public final boolean hasFileCreateTimeChange() {
        return m_filter.contains(NotifyChange.Creation);
    }

    /**
     * Determine if the filter has the security descriptor change notification
     *
     * @return boolean
     */
    public final boolean hasSecurityDescriptorChange() {
        return m_filter.contains(NotifyChange.Security);
    }

    /**
     * Check if the change filter has the specified flag enabled
     *
     * @param flag NotifyChange
     * @return boolean
     */
    public final boolean hasFilter(NotifyChange flag) {
        return m_filter.contains( flag);
    }

    /**
     * Check if the change filter contains the specified set of change flags
     *
     * @param filter Set of NotifyChange
     * @return boolean
     */
    public final boolean containsFilter(Set<NotifyChange> filter) {

        for(final NotifyChange changeType : filter) {
            if ( m_filter.contains( changeType))
                return true;
        }

        return false;
    }

    /**
     * Check if the notify enum flag is set
     *
     * @return boolean
     */
    public final boolean hasNotifyEnum() {
        return m_notifyEnum;
    }

    /**
     * Determine if sub-directories of the directory being watched should also trigger notifications
     *
     * @return boolean
     */
    public final boolean hasWatchTree() {
        return m_watchTree;
    }

    /**
     * Get the session that posted the notify request
     *
     * @return SMBSrvSession
     */
    public final SMBSrvSession getSession() {
        return m_sess;
    }

    /**
     * Get the directory being watched
     *
     * @return NetworkFile
     */
    public final NetworkFile getDirectory() {
        return m_watchDir;
    }

    /**
     * Get the normalised watch path
     *
     * @return String
     */
    public final String getWatchPath() {
        return m_watchPath;
    }

    /**
     * Get the id of the request
     *
     * @return long
     */
    public final long getId() {
        return m_id;
    }

    /**
     * Get the id of the request as an int
     *
     * @return int
     */
    public final int getIdAsInt() {
        return (int) m_id & 0xFFFFFFFF;
    }

    /**
     * Get the tree id of the request
     *
     * @return int
     */
    public final int getTreeId() {
        return m_tid;
    }

    /**
     * Get the process id of the request
     *
     * @return int
     */
    public final int getProcessId() {
        return m_pid;
    }

    /**
     * Get the user id of the request
     *
     * @return int
     */
    public final int getUserId() {
        return m_uid;
    }

    /**
     * Get the virtual circuit id of the request
     *
     * @return int
     */
    public final int getVirtualCircuitId() {
        return m_uid;
    }

    /**
     * Return the expiry time that a completed request must be reset by before being removed from
     * the queue.
     *
     * @return long
     */
    public final long getExpiryTime() {
        return m_expiresAt;
    }

    /**
     * Get the associated disk context
     *
     * @return DiskDeviceContext
     */
    public final DiskDeviceContext getDiskContext() {
        return m_diskCtx;
    }

    /**
     * Return the maximum number of notifications to buffer whilst waiting for the request to be reset
     *
     * @return int
     */
    public final int getMaximumQueueLength() {
        return m_maxQueueLen;
    }

    /**
     * Return the maximum response length
     *
     * @return int
     */
    public final int getMaximumResponseLength() {
        return m_maxRespLen;
    }

    /**
     * Determine if there are buffered events
     *
     * @return boolean
     */
    public final boolean hasBufferedEvents() {
        if (m_bufferedEvents != null && m_bufferedEvents.numberOfEvents() > 0)
            return true;
        return false;
    }

    /**
     * Return the buffered notification event list
     *
     * @return NotifyChangeEventList
     */
    public final NotifyChangeEventList getBufferedEventList() {
        return m_bufferedEvents;
    }

    /**
     * Add a buffered notification event, to be sent when the notify request is reset by the client
     *
     * @param evt NotifyChangeEvent
     */
    public final void addEvent(NotifyChangeEvent evt) {

        //	Check if the notify enum flag is set, if so then do not buffer any events
        if (hasNotifyEnum())
            return;

        //	Check if the buffered event list has been allocated
        if (m_bufferedEvents == null)
            m_bufferedEvents = new NotifyChangeEventList();

        //	Add the event if the list has not reached the maximum buffered event count
        if (m_bufferedEvents.numberOfEvents() < getMaximumQueueLength()) {

            //	Buffer the event until the client resets the notify filter
            m_bufferedEvents.addEvent(evt);
        }
        else {

            //	Remove all buffered events and set the notify enum flag to indicate that there
            //	have been many file changes
            removeAllEvents();
            setNotifyEnum(true);
        }
    }

    /**
     * Remove all buffered events from the request
     */
    public final void removeAllEvents() {
        if (m_bufferedEvents != null) {
            m_bufferedEvents.removeAllEvents();
            m_bufferedEvents = null;
        }
    }

    /**
     * Clear the buffered event list, do not destroy the list
     */
    public final void clearBufferedEvents() {
        m_bufferedEvents = null;
    }

    /**
     * Set/clear the notify enum flag that indicates if there have been many file changes
     *
     * @param ena boolean
     */
    public final void setNotifyEnum(boolean ena) {
        m_notifyEnum = ena;
    }

    /**
     * Set the associated disk device context
     *
     * @param ctx DiskDeviceContext
     */
    protected final void setDiskContext(DiskDeviceContext ctx) {
        m_diskCtx = ctx;
    }

    /**
     * Set the id for the notification
     *
     * @param id long
     */
    public final void setId(long id) {
        m_id = id;
    }

    /**
     * Set the request completed flag
     *
     * @param comp boolean
     */
    public final void setCompleted(boolean comp) {
        m_completed = comp;

        if (comp)
            m_expiresAt = System.currentTimeMillis() + DefaultRequestTimeout;
    }

    /**
     * Set the request completed flag and set an expiry time when the request expires
     *
     * @param comp    boolean
     * @param expires long
     */
    public final void setCompleted(boolean comp, long expires) {
        m_completed = comp;
        m_expiresAt = expires;
    }

    /**
     * Convert the specified path into a watch path relative path
     *
     * @param evtPath String
     * @return String
     */
    public final String makeWatchRelativePath( String evtPath) {

        // Make sure the path is a root relative path
        if ( evtPath == null || evtPath.length() == 0 || evtPath.startsWith(FileName.DOS_SEPERATOR_STR) == false)
            return evtPath;

        // Check if the path starts with the watched folder path
        String relPath = null;

        if ( evtPath.toUpperCase().startsWith( m_watchPath)) {
            relPath = evtPath.substring( m_watchPath.length());
            if ( relPath.length() > 1 && relPath.startsWith( FileName.DOS_SEPERATOR_STR))
                relPath = relPath.substring( 1);
        }

        return relPath;
    }

    /**
     * Return the notify request as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");

        str.append(getSession().getUniqueId());
        str.append(":");

        if (getWatchPath().length() == 0)
            str.append("Root");
        else
            str.append(getWatchPath());

        str.append(":Filter");
        str.append(m_filter.toString());

        if (hasWatchTree())
            str.append(", Tree");
        else
            str.append(", NoTree");

        if ( getProcessId() != 0 && getUserId() != 0) {
            str.append(" MID=");
            str.append(getId());

            str.append(" PID=");
            str.append(getProcessId());

            str.append(" TID=");
            str.append(getTreeId());

            str.append(" UID=");
            str.append(getUserId());

            if ( getMaximumQueueLength() > 0) {
                str.append(", maxQueue=");
                str.append(getMaximumQueueLength());
            }
        }
        else {
            str.append(" ID=");
            str.append(getId());

            str.append(" treeId=");
            str.append(getTreeId());

            str.append(" procId=");
            str.append(getProcessId());

            str.append(", maxRespLen=");
            str.append(getMaximumResponseLength());
        }

        if (isCompleted()) {
            str.append(",Completed,TMO=");
            str.append(new Date(getExpiryTime()).toString());
        }

        if (hasBufferedEvents()) {
            str.append(", buf=");
            str.append(getBufferedEventList().numberOfEvents());
        }

        if ( m_sess.getRemoteAddress() != null) {
            str.append(", addr=");
            str.append( m_sess.getRemoteAddress());
        }

        if (hasNotifyEnum())
            str.append(",ENUM");

        str.append("]");

        return str.toString();
    }
}
