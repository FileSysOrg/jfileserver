/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018-2021 GK Spencer
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
import org.filesys.server.filesys.NotifyChange;
import org.filesys.server.filesys.event.ChangeEvent;
import org.filesys.server.filesys.event.ChangeEventHandler;
import org.filesys.server.filesys.event.ChangeEventList;
import org.filesys.server.filesys.event.FSChange;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;

import java.util.*;

/**
 * SMB Notify Change Event Handler Class
 *
 * <p>Receives change events from the core server to be sent to SMB clients as asynchronous change notifications</p>
 *
 * @author gkspencer
 */
public class NotifyChangeHandler extends ChangeEventHandler {

    // Change event handler unique name
    public static final String Name = "SMB";

    //	Change notification request list
    private NotifyRequestList m_notifyList;

    // Set of filesystem change events that this handler is interested in
    private Set<FSChange> m_fsChanges = EnumSet.noneOf( FSChange.class);

    //	Associated disk device context
    private DiskDeviceContext m_diskCtx;

    //	Debug output enable
    private boolean m_debug = false;

    /**
     * Class constructor
     *
     * @param diskCtx DiskDeviceContext
     */
    public NotifyChangeHandler(DiskDeviceContext diskCtx) {
        super(NotifyChangeHandler.Name, Priority.Normal);

        //	Save the associated disk context details
        m_diskCtx = diskCtx;
    }

    @Override
    public void registerFilesystem(DiskDeviceContext diskCtx) throws Exception {
    }

    @Override
    public void unregisterFilesystem(DiskDeviceContext diskCtx) throws Exception {
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

        //	Regenerate the global change set
        m_fsChanges = m_notifyList.getGlobalFSChangeFilter();
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

        //	Regenerate the global change set
        if (updateMask == true)
            m_fsChanges = m_notifyList.getGlobalFSChangeFilter();
    }

    /**
     * Remove all notification requests owned by the specified session
     *
     * @param sess SMBSrvSession
     */
    public final void removeNotifyRequests(SMBSrvSession sess) {

        //	Remove all requests owned by the session
        m_notifyList.removeAllRequestsForSession(sess);

        //	Recalculate the global change set
        m_fsChanges = m_notifyList.getGlobalFSChangeFilter();
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
     * Return the global change set
     *
     * @return Set&lt;FSChange&gt;
     */
    public final Set<FSChange> getGlobalChangeSet() {
        return m_fsChanges;
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
     * Enable debug output
     *
     * @param ena boolean
     */
    public final void setDebug(boolean ena) {
        m_debug = ena;
    }

    /**
     * Convert a filesystem change type to a change notification filter type
     *
     * @param fsChange FSChange
     * @param dir boolean
     * @return Set&lt;NotifyChange&gt;
     */
    protected final Set<NotifyChange> convertToNotifyChange( FSChange fsChange, boolean dir) {

        Set<NotifyChange> notifyChange = EnumSet.noneOf( NotifyChange.class);

        switch ( fsChange) {
            case Created:
                notifyChange.add( NotifyChange.Creation);
                notifyChange.add( dir ? NotifyChange.DirectoryName : NotifyChange.FileName);
                break;
            case Deleted:
            case Modified:
            case Renamed:
                notifyChange.add( dir ? NotifyChange.DirectoryName : NotifyChange.FileName);
                break;
            case Attributes:
                notifyChange.add( NotifyChange.Attributes);
                break;
            case LastWrite:
                notifyChange.add( NotifyChange.LastWrite);
                break;
            case Security:
                notifyChange.add( NotifyChange.Security);
                break;
        }

        return notifyChange;
    }

    /**
     * Send buffered change notifications for a session
     *
     * @param req     NotifyRequest
     * @param evtList NotifyChangeEventList
     */
    public final void sendBufferedNotifications(NotifyRequest req, ChangeEventList evtList) {

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
                ChangeEvent evt = evtList.getEventAt(i);

                //	Get the relative file name for the event
                String relName = FileName.makeRelativePath(req.getWatchPath(), evt.getPath());
                if (relName == null)
                    relName = evt.getShortFileName();

                //	DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[Notify]   Notify evtPath=" + evt.getPath() + ", reqPath=" + req.getWatchPath() + ", relative=" + relName);

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
     * Send change notifications to sessions with notification enabled that match the change event.
     *
     * @param evt NotifyChangeEvent
     * @return int
     */
    protected final int sendChangeNotification(ChangeEvent evt) {

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[SMBNotify] sendChangeNotification event=" + evt);

        //	Get a list of notification requests that match the type/path
        List<NotifyRequest> reqList = findMatchingRequests( convertToNotifyChange( evt.isChange(), evt.isDirectory()), evt.getPath(), evt.isDirectory());

        if (reqList == null || reqList.size() == 0)
            return 0;

        //	DEBUG
        if (Debug.EnableInfo && hasDebug()) {
            Debug.println("[SMBNotify]   Found " + reqList.size() + " matching change listeners");

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
                            if (Debug.EnableInfo && req.getSession().hasDebug(SMBSrvSession.Dbg.NOTIFY))
                                req.getSession().debugPrintln("  Notification request was queued, sess=" + req.getSession().getSessionId() + ", ID=" + req.getId());
                        }
                        else if (Debug.EnableInfo && req.getSession().hasDebug(SMBSrvSession.Dbg.NOTIFY))
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
                if (Debug.EnableInfo && req.getSession().hasDebug(SMBSrvSession.Dbg.NOTIFY))
                    req.getSession().debugPrintln("Buffered notify req=" + req + ", event=" + evt + ", sess=" + req.getSession().getSessionId());
            }

            //	Reset the notification pending flag for the session
            req.getSession().setNotifyPending(false);

            //	DEBUG
            if (Debug.EnableInfo && req.getSession().hasDebug(SMBSrvSession.Dbg.NOTIFY))
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
                Debug.println("[SMBNotify] findMatchingRequests() req=" + curReq.toString());

            //	Check if the request has expired
            if (curReq.hasExpired(curTime)) {

                //	Remove the request from the list
                m_notifyList.removeRequestAt(idx);

                //	DEBUG
                if (Debug.EnableInfo && hasDebug()) {
                    Debug.println("[SMBNotify] Removed expired request req=" + curReq.toString());

                    if (curReq.getBufferedEventList() != null) {
                        ChangeEventList bufList = curReq.getBufferedEventList();
                        Debug.println("[SMBNotify]   Buffered events = " + bufList.numberOfEvents());
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
                    Debug.println("[SMBNotify]   hasFilter typ=" + filter + ", watchTree=" + curReq.hasWatchTree() + ", watchPath=" + curReq.getWatchPath() +
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
                        Debug.println("[SMBNotify]   Added request to matching list");
                }
                else if ( Debug.EnableInfo && hasDebug()) {

                    // DEBUG
                    Debug.println("[SMBNotify] Match failed for filter=" + filter + ", path=" + path + ", isDir=" + isdir + ", req=" + curReq);
                }
            }
            else if (Debug.EnableInfo && hasDebug()) {

                // DEBUG
                Debug.println("[SMBNotify] Not matched filter typ=" + filter + ", watchTree=" + curReq.hasWatchTree() + ", watchPath=" + curReq.getWatchPath() +
                        ", matchPath=" + matchPath + ", isDir=" + isdir + ", addr=" + curReq.getSession().getRemoteAddress());
            }

            //	Move to the next request in the list
            idx++;
        }

        //	If requests were removed from the queue the global change set must be recalculated
        if (removedReq == true)
            m_fsChanges = m_notifyList.getGlobalFSChangeFilter();

        //	Return the matching request list
        return reqList;
    }

    @Override
    public boolean wantAllFSEvents() {
        return false;
    }

    @Override
    public boolean wantFSEvent(FSChange typ, boolean dir, DiskDeviceContext diskCtx) {

        // Check if this handler is waiting for any event types
        if ( m_fsChanges.isEmpty())
            return false;

        // Make sure the device matches
        if ( diskCtx.getUniqueId() != m_diskCtx.getUniqueId())
            return false;

        // Check if the handler will process the event
        return m_fsChanges.contains( typ);
    }

    @Override
    public void handleFSEvent(ChangeEvent event, DiskDeviceContext diskCtx) {

        //	Send out change notifications to clients that match the filter/path
        int cnt = sendChangeNotification(event);

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[SMBNotify] Change notify event=" + event + ", clients=" + cnt);
    }
}
