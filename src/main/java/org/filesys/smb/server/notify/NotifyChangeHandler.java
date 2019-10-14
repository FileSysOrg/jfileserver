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

import org.filesys.debug.Debug;
import org.filesys.server.filesys.DiskDeviceContext;
import org.filesys.server.filesys.FileName;
import org.filesys.server.filesys.NotifyAction;
import org.filesys.server.filesys.NotifyChange;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Notify Change Handler Class
 *
 * @author gkspencer
 */
public class NotifyChangeHandler implements Runnable {

    //	Change notification request list and global filter mask
    private NotifyRequestList m_notifyList;
    private Set<NotifyChange> m_globalNotifyMask = EnumSet.noneOf( NotifyChange.class);

    //	Associated disk device context
    private DiskDeviceContext m_diskCtx;

    //	Change notification processing thread
    private Thread m_procThread;

    //	Change events queue
    private NotifyChangeEventList m_eventList;

    //	Debug output enable
    private boolean m_debug = false;

    //	Shutdown request flag
    private boolean m_shutdown;

    /**
     * Class constructor
     *
     * @param diskCtx DiskDeviceContext
     */
    public NotifyChangeHandler(DiskDeviceContext diskCtx) {

        //	Save the associated disk context details
        m_diskCtx = diskCtx;

        //	Allocate the events queue
        m_eventList = new NotifyChangeEventList();

        //	Create the processing thread
        m_procThread = new Thread(this);

        m_procThread.setDaemon(true);
        m_procThread.setName("Notify_" + m_diskCtx.getDeviceName());

        m_procThread.start();
    }

    /**
     * Add a request to the change notification list
     *
     * @param req NotifyRequest
     */
    public final void addNotifyRequest(NotifyRequest req) {

        //	Check if the request list has been allocated
        if (m_notifyList == null)
            m_notifyList = new NotifyRequestList();

        //	Add the request to the list
        req.setDiskContext(m_diskCtx);
        m_notifyList.addRequest(req);

        //	Regenerate the global notify change filter mask
        m_globalNotifyMask = m_notifyList.getGlobalFilter();
    }

    /**
     * Remove a request from the notify change request list
     *
     * @param req NotifyRequest
     */
    public final void removeNotifyRequest(NotifyRequest req) {
        removeNotifyRequest(req, true);
    }

    /**
     * Remove a request from the notify change request list
     *
     * @param req        NotifyRequest
     * @param updateMask boolean
     */
    public final void removeNotifyRequest(NotifyRequest req, boolean updateMask) {

        //	Check if the request list has been allocated
        if (m_notifyList == null)
            return;

        //	Remove the request from the list
        m_notifyList.removeRequest(req);

        //	Regenerate the global notify change filter mask
        if (updateMask == true)
            m_globalNotifyMask = m_notifyList.getGlobalFilter();
    }

    /**
     * Remove all notification requests owned by the specified session
     *
     * @param sess SMBSrvSession
     */
    public final void removeNotifyRequests(SMBSrvSession sess) {

        //	Remove all requests owned by the session
        m_notifyList.removeAllRequestsForSession(sess);

        //	Recalculate the global notify change filter mask
        m_globalNotifyMask = m_notifyList.getGlobalFilter();
    }

    /**
     * Determine if the filter has file name change notification, triggered if a file is created, renamed or deleted
     *
     * @return boolean
     */
    public final boolean hasFileNameChange() {
        return hasFilterFlag(NotifyChange.FileName);
    }

    /**
     * Determine if the filter has directory name change notification, triggered if a directory is created or deleted.
     *
     * @return boolean
     */
    public final boolean hasDirectoryNameChange() {
        return hasFilterFlag(NotifyChange.DirectoryName);
    }

    /**
     * Determine if the filter has attribute change notification
     *
     * @return boolean
     */
    public final boolean hasAttributeChange() {
        return hasFilterFlag(NotifyChange.Attributes);
    }

    /**
     * Determine if the filter has file size change notification
     *
     * @return boolean
     */
    public final boolean hasFileSizeChange() {
        return hasFilterFlag(NotifyChange.Size);
    }

    /**
     * Determine if the filter has last write time change notification
     *
     * @return boolean
     */
    public final boolean hasFileWriteTimeChange() {
        return hasFilterFlag(NotifyChange.LastWrite);
    }

    /**
     * Determine if the filter has last access time change notification
     *
     * @return boolean
     */
    public final boolean hasFileAccessTimeChange() {
        return hasFilterFlag(NotifyChange.LastAccess);
    }

    /**
     * Determine if the filter has creation time change notification
     *
     * @return boolean
     */
    public final boolean hasFileCreateTimeChange() {
        return hasFilterFlag(NotifyChange.Creation);
    }

    /**
     * Determine if the filter has the security descriptor change notification
     *
     * @return boolean
     */
    public final boolean hasSecurityDescriptorChange() {
        return hasFilterFlag(NotifyChange.Security);
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Return the global notify filter mask
     *
     * @return Set of NotifyChange
     */
    public final Set<NotifyChange> getGlobalNotifyMask() {
        return m_globalNotifyMask;
    }

    /**
     * Return the notify request queue size
     *
     * @return int
     */
    public final int getRequestQueueSize() {
        return m_notifyList != null ? m_notifyList.numberOfRequests() : 0;
    }

    /**
     * Check if the change filter has the specified flag enabled
     *
     * @param flag NotifyChange
     * @return boolean
     */
    private final boolean hasFilterFlag(NotifyChange flag) {
        return m_globalNotifyMask.contains( flag);
    }

    /**
     * File changed notification
     *
     * @param action NotifyAction
     * @param path   String
     */
    public final void notifyFileChanged(NotifyAction action, String path) {

        //	Check if file change notifications are enabled
        if (getGlobalNotifyMask().isEmpty() || hasFileNameChange() == false)
            return;

        //	Queue the change notification
        queueNotification(new NotifyChangeEvent(NotifyChange.FileName, action, path, false));
    }

    /**
     * File/directory renamed notification
     *
     * @param oldName String
     * @param newName String
     */
    public final void notifyRename(String oldName, String newName) {

        //	Check if file change notifications are enabled
        if (getGlobalNotifyMask().isEmpty() || (hasFileNameChange() == false && hasDirectoryNameChange() == false))
            return;

        //	Queue the change notification event
        queueNotification(new NotifyChangeEvent(NotifyChange.FileName, NotifyAction.RenamedNewName, newName, oldName, false));
    }

    /**
     * Directory changed notification
     *
     * @param action NotifyAction
     * @param path   String
     */
    public final void notifyDirectoryChanged(NotifyAction action, String path) {

        //	Check if file change notifications are enabled
        if (getGlobalNotifyMask().isEmpty() || hasDirectoryNameChange() == false)
            return;

        //	Queue the change notification event
        queueNotification(new NotifyChangeEvent(NotifyChange.DirectoryName, action, path, true));
    }

    /**
     * Attributes changed notification
     *
     * @param path  String
     * @param isdir boolean
     */
    public final void notifyAttributesChanged(String path, boolean isdir) {

        //	Check if file change notifications are enabled
        if (getGlobalNotifyMask().isEmpty() || hasAttributeChange() == false)
            return;

        //	Queue the change notification event
        queueNotification(new NotifyChangeEvent(NotifyChange.Attributes, NotifyAction.Modified, path, isdir));
    }

    /**
     * File size changed notification
     *
     * @param path String
     */
    public final void notifyFileSizeChanged(String path) {

        //	Check if file change notifications are enabled
        if (getGlobalNotifyMask().isEmpty() || hasFileSizeChange() == false)
            return;

        //	Send the change notification
        queueNotification(new NotifyChangeEvent(NotifyChange.Size, NotifyAction.Modified, path, false));
    }

    /**
     * Last write time changed notification
     *
     * @param path  String
     * @param isdir boolean
     */
    public final void notifyLastWriteTimeChanged(String path, boolean isdir) {

        //	Check if file change notifications are enabled
        if (getGlobalNotifyMask().isEmpty() || hasFileWriteTimeChange() == false)
            return;

        //	Send the change notification
        queueNotification(new NotifyChangeEvent(NotifyChange.LastWrite, NotifyAction.Modified, path, isdir));
    }

    /**
     * Last access time changed notification
     *
     * @param path  String
     * @param isdir boolean
     */
    public final void notifyLastAccessTimeChanged(String path, boolean isdir) {

        //	Check if file change notifications are enabled
        if (getGlobalNotifyMask().isEmpty() || hasFileAccessTimeChange() == false)
            return;

        //	Send the change notification
        queueNotification(new NotifyChangeEvent(NotifyChange.LastAccess, NotifyAction.Modified, path, isdir));
    }

    /**
     * Creation time changed notification
     *
     * @param path  String
     * @param isdir boolean
     */
    public final void notifyCreationTimeChanged(String path, boolean isdir) {

        //	Check if file change notifications are enabled
        if (getGlobalNotifyMask().isEmpty() || hasFileCreateTimeChange() == false)
            return;

        //	Send the change notification
        queueNotification(new NotifyChangeEvent(NotifyChange.Creation, NotifyAction.Modified, path, isdir));
    }

    /**
     * Security descriptor changed notification
     *
     * @param path  String
     * @param isdir boolean
     */
    public final void notifySecurityDescriptorChanged(String path, boolean isdir) {

        //	Check if file change notifications are enabled
        if (getGlobalNotifyMask().isEmpty() || hasSecurityDescriptorChange() == false)
            return;

        //	Send the change notification
        queueNotification(new NotifyChangeEvent(NotifyChange.Security, NotifyAction.Modified, path, isdir));
    }

    /**
     * Enable debug output
     *
     * @param ena boolean
     */
    public final void setDebug(boolean ena) {
        m_debug = ena;
    }

    /**
     * Shutdown the change notification processing thread
     */
    public final void shutdownRequest() {

        //	Check if the processing thread is valid
        if (m_procThread != null) {

            //	Set the shutdown flag
            m_shutdown = true;

            //	Wakeup the processing thread
            m_procThread.interrupt();
        }
    }

    /**
     * Send buffered change notifications for a session
     *
     * @param req     NotifyRequest
     * @param evtList NotifyChangeEventList
     */
    public final void sendBufferedNotifications(NotifyRequest req, NotifyChangeEventList evtList) {

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[Notify] Send buffered notifications, req=" + req + ", evtList=" + (evtList != null ? "" + evtList.numberOfEvents() : "null"));

        //	Initialize the notification request timeout
        long tmo = System.currentTimeMillis() + NotifyRequest.DefaultRequestTimeout;

        //	Check if the notify enum status is set
        if (req.hasNotifyEnum()) {

            // Build the change notification response
            SMBSrvPacket smbPkt = req.getSession().getProtocolHandler().buildChangeNotificationResponse( null, req);

            if ( smbPkt != null) {

                try {

                    //	Send the response to the current session
                    req.getSession().sendAsynchResponseSMB(smbPkt, smbPkt.getLength());
                }
                catch (Exception ex) {

                    //  DEBUG
                    if (Debug.EnableError && hasDebug())
                        Debug.println("[Notify] Failed to send change notification, " + ex.getMessage());
                }
            }

            //	Set the notification request id to indicate that it has completed
            req.setCompleted(true, tmo);
            req.setNotifyEnum(false);
        }
        else if (evtList != null) {

            //	Pack the change notification events
            for (int i = 0; i < evtList.numberOfEvents(); i++) {

                //	Get the current event from the list
                NotifyChangeEvent evt = evtList.getEventAt(i);

                //	Get the relative file name for the event
                String relName = FileName.makeRelativePath(req.getWatchPath(), evt.getFileName());
                if (relName == null)
                    relName = evt.getShortFileName();

                //	DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[Notify]   Notify evtPath=" + evt.getFileName() + ", reqPath=" + req.getWatchPath() + ", relative=" + relName);

                // Build the change notification response
                SMBSrvPacket smbPkt = req.getSession().getProtocolHandler().buildChangeNotificationResponse( evt, req);

                if ( smbPkt != null) {

                    try {

                        //	Send the response to the current session
                        req.getSession().sendAsynchResponseSMB(smbPkt, smbPkt.getLength());
                    }
                    catch (Exception ex) {

                        //  DEBUG
                        if (Debug.EnableError && hasDebug())
                            Debug.println("[Notify] Failed to send change notification, " + ex.getMessage());
                    }
                }

                //	Set the notification request id to indicate that it has completed
                req.setCompleted(true, tmo);
            }
        }

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[Notify] sendBufferedNotifications() done");
    }

    /**
     * Queue a change notification event for processing
     *
     * @param evt NotifyChangeEvent
     */
    protected final void queueNotification(NotifyChangeEvent evt) {

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[Notify] Queue notification event=" + evt.toString());

        //	Queue the notification event to the main notification handler thread
        synchronized (m_eventList) {

            //	Add the event to the list
            m_eventList.addEvent(evt);

            //	Notify the processing thread that there are events to process
            m_eventList.notifyAll();
        }
    }

    /**
     * Send change notifications to sessions with notification enabled that match the change event.
     *
     * @param evt NotifyChangeEvent
     * @return int
     */
    protected final int sendChangeNotification(NotifyChangeEvent evt) {

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[Notify] sendChangeNotification event=" + evt);

        //	Get a list of notification requests that match the type/path
        List<NotifyRequest> reqList = findMatchingRequests(evt.getFilter(), evt.getFileName(), evt.isDirectory());

        if (reqList == null || reqList.size() == 0)
            return 0;

        //	DEBUG
        if (Debug.EnableInfo && hasDebug()) {
            Debug.println("[Notify]   Found " + reqList.size() + " matching change listeners");

            for ( NotifyRequest req : reqList)
                Debug.println( "   Request=" + req);
        }

        //	Initialize the notification request timeout
        long tmo = System.currentTimeMillis() + NotifyRequest.DefaultRequestTimeout;

        //	Send the notify response to each client in the list
        for (int i = 0; i < reqList.size(); i++) {

            //	Get the current request
            NotifyRequest req = reqList.get(i);

            //	Check if the request is already complete
            if (req.isCompleted() == false) {

                //	Set the notification request id to indicate that it has completed
                req.setCompleted(true, tmo);

                // Build the change notification packet
                SMBSrvPacket smbPkt = req.getSession().getProtocolHandler().buildChangeNotificationResponse(evt, req);

                if ( smbPkt != null) {

                    try {

                        //	Send the response to the current session
                        if (req.getSession().sendAsynchResponseSMB(smbPkt, smbPkt.getLength()) == false) {

                            //	DEBUG
                            if (Debug.EnableInfo && req.getSession().hasDebug(SMBSrvSession.DBG_NOTIFY))
                                req.getSession().debugPrintln("  Notification request was queued, sess=" + req.getSession().getSessionId() + ", ID=" + req.getId());
                        }
                        else if (Debug.EnableInfo && req.getSession().hasDebug(SMBSrvSession.DBG_NOTIFY))
                            req.getSession().debugPrintln("  Notification request was sent, sess=" + req.getSession().getSessionId() + ", ID=" + req.getId());
                    }
                    catch (Exception ex) {
                        Debug.println(ex);
                    }
                }
            }
            else {

                //	Buffer the event so it can be sent when the client resets the notify request
                req.addEvent(evt);

                //	DEBUG
                if (Debug.EnableInfo && req.getSession().hasDebug(SMBSrvSession.DBG_NOTIFY))
                    req.getSession().debugPrintln("Buffered notify req=" + req + ", event=" + evt + ", sess=" + req.getSession().getSessionId());
            }

            //	Reset the notification pending flag for the session
            req.getSession().setNotifyPending(false);

            //	DEBUG
            if (Debug.EnableInfo && req.getSession().hasDebug(SMBSrvSession.DBG_NOTIFY))
                req.getSession().debugPrintln("Asynch notify req=" + req + ", event=" + evt + ", sess=" + req.getSession().getUniqueId());
        }

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[Notify] sendChangeNotification() done");

        //	Return the count of matching requests
        return reqList.size();
    }

    /**
     * Find notify requests that match the type and path
     *
     * @param filter   Set of NotifyChange
     * @param path  String
     * @param isdir boolean
     * @return List of NotifyRequest
     */
    protected final synchronized List<NotifyRequest> findMatchingRequests(Set<NotifyChange> filter, String path, boolean isdir) {

        //	Create a vector to hold the matching requests
        List<NotifyRequest> reqList = new ArrayList<NotifyRequest>();

        //	Normalise the path string
        String matchPath = path.toUpperCase();

        if ( matchPath.length() == 0 || matchPath.startsWith( FileName.DOS_SEPERATOR_STR) == false)
            matchPath = FileName.DOS_SEPERATOR_STR + matchPath;

        //	Search for matching requests and remove them from the main request list
        int idx = 0;
        long curTime = System.currentTimeMillis();

        boolean removedReq = false;

        while (idx < m_notifyList.numberOfRequests()) {

            //	Get the current request
            NotifyRequest curReq = m_notifyList.getRequest(idx);

            //	DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("[Notify] findMatchingRequests() req=" + curReq.toString());

            //	Check if the request has expired
            if (curReq.hasExpired(curTime)) {

                //	Remove the request from the list
                m_notifyList.removeRequestAt(idx);

                //	DEBUG
                if (Debug.EnableInfo && hasDebug()) {
                    Debug.println("[Notify] Removed expired request req=" + curReq.toString());

                    if (curReq.getBufferedEventList() != null) {
                        NotifyChangeEventList bufList = curReq.getBufferedEventList();
                        Debug.println("[Notify]   Buffered events = " + bufList.numberOfEvents());
                        for (int b = 0; b < bufList.numberOfEvents(); b++)
                            Debug.println("    " + (b + 1) + ": " + bufList.getEventAt(b));
                    }
                }

                //	Indicate that a request has been removed from the queue, the global filter mask will need
                //	to be recalculated
                removedReq = true;

                //	Restart the loop
                continue;
            }

            //	Check if the request matches the filter
            if (curReq.containsFilter(filter)) {

                //	DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[Notify]   hasFilter typ=" + filter + ", watchTree=" + curReq.hasWatchTree() + ", watchPath=" + curReq.getWatchPath() +
                            ", matchPath=" + matchPath + ", isDir=" + isdir + ", addr=" + curReq.getSession().getRemoteAddress());

                //	Check if the path matches or is a subdirectory and the whole tree is being watched
                boolean wantReq = false;

                if (matchPath.length() == 0 && curReq.hasWatchTree()) {
                    wantReq = true;
                }
                else if (curReq.hasWatchTree() == true && matchPath.startsWith(curReq.getWatchPath()) == true) {
                    wantReq = true;
                }
                else if (isdir == true && matchPath.compareTo(curReq.getWatchPath()) == 0) {
                    wantReq = true;
                }
                else if (isdir == false) {

                    //	Strip the file name from the path and compare
                    String[] paths = FileName.splitPath(matchPath);

                    if (paths != null && paths[0] != null) {

                        //	Check if the directory part of the path is the directory being watched
                        if (curReq.getWatchPath().equalsIgnoreCase(paths[0])) {
                            wantReq = true;
                        }
                    }
                }

                //	Check if the request is required
                if (wantReq == true) {

                    //	For all notify requests in the matching list we set the 'notify pending' state on the associated SMB
                    //	session so that any socket writes on those sessions are synchronized until the change notification
                    //	response has been sent.
                    curReq.getSession().setNotifyPending(true);

                    //	Add the request to the matching list
                    reqList.add(curReq);

                    //	DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("[Notify]   Added request to matching list");
                }
                else if ( Debug.EnableInfo && hasDebug()) {

                    // DEBUG
                    Debug.println("[Notify] Match failed for filter=" + filter + ", path=" + path + ", isDir=" + isdir + ", req=" + curReq);
                }
            }
            else if (Debug.EnableInfo && hasDebug()) {

                // DEBUG
                Debug.println("[Notify] Not matched filter typ=" + filter + ", watchTree=" + curReq.hasWatchTree() + ", watchPath=" + curReq.getWatchPath() +
                        ", matchPath=" + matchPath + ", isDir=" + isdir + ", addr=" + curReq.getSession().getRemoteAddress());
            }

            //	Move to the next request in the list
            idx++;
        }

        //	If requests were removed from the queue the global filter mask must be recalculated
        if (removedReq == true)
            m_globalNotifyMask = m_notifyList.getGlobalFilter();

        //	Return the matching request list
        return reqList;
    }

    /**
     * Asynchronous change notification processing thread
     */
    public void run() {

        //	Loop until shutdown
        while (m_shutdown == false) {

            //	Wait for some events to process
            synchronized (m_eventList) {
                try {
                    m_eventList.wait();
                }
                catch (InterruptedException ex) {
                }
            }

            //	Check if the shutdown flag has been set
            if (m_shutdown == true)
                break;

            //	Loop until all pending events have been processed
            while (m_eventList.numberOfEvents() > 0) {

                //	Remove the event at the head of the queue
                NotifyChangeEvent evt = null;

                synchronized (m_eventList) {
                    evt = m_eventList.removeEventAt(0);
                }

                //	Check if the event is valid
                if (evt == null)
                    break;

                try {

                    //	Send out change notifications to clients that match the filter/path
                    int cnt = sendChangeNotification(evt);

                    //	DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("[Notify] Change notify event=" + evt.toString() + ", clients=" + cnt);
                }
                catch (Throwable ex) {
                    Debug.println("NotifyChangeHandler thread");
                    Debug.println(ex);
                }
            }
        }

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("NotifyChangeHandler thread exit");
    }
}
