/*
 * Copyright (C) 2020 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.oncrpc.nfs.nio;

import org.filesys.debug.Debug;
import org.filesys.oncrpc.nfs.NFSServer;
import org.filesys.oncrpc.nfs.NFSSrvSession;
import org.filesys.server.SessionListener;
import org.filesys.server.SrvSession;
import org.filesys.server.SrvSessionQueue;
import org.filesys.server.core.NoPooledMemoryException;
import org.filesys.server.thread.ThreadRequest;
import org.filesys.server.thread.ThreadRequestPool;

import java.io.IOException;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NFS Request Handler Class
 *
 * <p>Handles the receiving of RPCs for a number of NFS sessions
 *
 * @author gkspencer
 */
public class NFSRequestHandler implements Runnable, SessionListener {

    // Maximum number of sessions to handle
    private int m_maxSessions;

    // Request handler index, used to generate the thread name
    private static int _handlerId;

    // Selector used to monitor a group of socket channels for incoming requests
    private Selector m_selector;

    // List of thread requests, used during socket event processing
    private List<ThreadRequest> m_reqList = new ArrayList<ThreadRequest>();

    // Count of the number of selector channels, maintained by the main thread
    private AtomicInteger m_sessionCount = new AtomicInteger();

    // Thread that the request handler runs in
    private Thread m_thread;

    // Thread pool for processing requests
    private ThreadRequestPool m_threadPool;

    // Queue of sessions that are pending setup with the selector
    private SrvSessionQueue m_sessQueue;

    // Client socket session timeout
    private int m_clientSocketTimeout;

    // Flag to indicate the idle session reaper should be run by the main thread
    private AtomicBoolean m_runIdleSessReaper = new AtomicBoolean();

    // Request handler listener
    private NFSRequestHandlerListener m_listener;

    // Shutdown request flag
    private boolean m_shutdown;

    // Debug enable flag
    private boolean m_debug;

    /**
     * Class constructor
     *
     * @param nfsServer  NFSServer
     * @param maxSess    int
     * @param sockTmo    int
     * @param debug      boolean
     */
    public NFSRequestHandler(NFSServer nfsServer, int maxSess, int sockTmo, boolean debug) {

        // Set the maximum number of sessions for this handler
        m_maxSessions = maxSess;
        
        // Set the thread pool to use for request processing
        m_threadPool = nfsServer.getThreadPool();

        // Set the client socket timeout
        m_clientSocketTimeout = sockTmo;

        // Create the session queue
        m_sessQueue = new SrvSessionQueue();

        // Add the session listener to the NFS server to receive session close events
        nfsServer.addSessionListener( this);

        // Set the debug output enable
        m_debug = debug;

        // Start the request handler in a seperate thread
        m_thread = new Thread(this);
        m_thread.setName("NFSRequestHandler_" + ++_handlerId);
        m_thread.setDaemon(false);

        m_thread.start();
    }

    /**
     * Return the current session count
     *
     * @return int
     */
    public final int getCurrentSessionCount() {
        return m_sessionCount.get();
    }

    /**
     * Check if this request handler has free session slots available
     *
     * @return boolean
     */
    public final boolean hasFreeSessionSlot() {
        return (getCurrentSessionCount() + m_sessQueue.numberOfSessions()) < getMaximumSessionCount() ? true : false;
    }

    /**
     * Return the client socket timeout, in milliseconds
     *
     * @return int
     */
    public final int getSocketTimeout() {
        return m_clientSocketTimeout;
    }

    /**
     * Set the client socket timeout, in milliseconds
     *
     * @param tmo int
     */
    public final void setSocketTimeout(int tmo) {
        m_clientSocketTimeout = tmo;
    }

    /**
     * Return the maximum session count
     *
     * @return int
     */
    public final int getMaximumSessionCount() {
        return m_maxSessions;
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
     * Enable/disable debug output
     *
     * @param ena boolean
     */
    public final void setDebug(boolean ena) {
        m_debug = ena;
    }

    /**
     * Queue a new session to the request handler, wakeup the request handler thread to register it with the
     * selector.
     *
     * @param sess NFSSrvSession
     */
    public final void queueSessionToHandler(NFSSrvSession sess) {

        // Add the new session to the pending queue
        m_sessQueue.addSession(sess);

        // Wakeup the main thread to process the new session queue
        if (m_selector != null)
            m_selector.wakeup();
    }

    /**
     * Return the request handler name
     *
     * @return String
     */
    public final String getName() {
        if (m_thread != null)
            return m_thread.getName();
        return "NFSRequestHandler";
    }

    /**
     * Enable/disable thread pool debugging
     *
     * @param dbg boolean
     */
    public final void setThreadDebug(boolean dbg) {
        m_threadPool.setDebug(dbg);
    }

    /**
     * check if the request handler has an associated request handler listener
     *
     * @return boolean
     */
    public final boolean hasListener() {
        return m_listener != null ? true : false;
    }

    /**
     * Return the associated request handler listener
     *
     * @return RequestHandlerListener
     */
    public final NFSRequestHandlerListener getListener() {
        return m_listener;
    }

    /**
     * Set the associated request handler listener
     *
     * @param listener NFSRequestHandlerListener
     */
    public final void setListener(NFSRequestHandlerListener listener) {
        m_listener = listener;
    }

    /**
     * Inform the listener that this request handler has no sessions to listen for incoming
     * requests.
     */
    protected final void fireRequestHandlerEmptyEvent() {
        if (hasListener())
            getListener().requestHandlerEmpty(this);
    }

    /**
     * Run the main processing in a seperate thread
     */
    public void run() {

        // Clear the shutdown flag, may have been restarted
        m_shutdown = false;

        // Initialize the socket selector
        try {

            // Create the selector
            m_selector = Selector.open();
        }
        catch (IOException ex) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug()) {
                Debug.println("[NFS] Error opening/registering Selector");
                Debug.println(ex);
            }

            m_shutdown = true;
        }

        // Loop until shutdown
        while (m_shutdown == false) {

            try {

                // Check if there are any sessions registered
                int sessCnt = 0;

                m_sessionCount.set(m_selector.keys().size());

                if (m_sessionCount.get() == 0) {

                    // Indicate that this request handler has no active sessions
                    fireRequestHandlerEmptyEvent();

                    // DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("[NFS] Request handler " + m_thread.getName() + " waiting for session ...");

                    // Wait for a session to be added to the handler
                    try {
                        m_sessQueue.waitWhileEmpty();
                    }
                    catch (InterruptedException ex) {
                    }
                }
                else {

                    // Wait for client requests
                    try {
                        sessCnt = m_selector.select();
                    }
                    catch (CancelledKeyException ex) {

                        // DEBUG
                        if (Debug.EnableError && hasDebug() && m_shutdown == false) {
                            Debug.println("[NFS] Request handler error waiting for events");
                            Debug.println(ex);
                        }
                    }
                    catch (IOException ex) {

                        // DEBUG
                        if (Debug.EnableError && hasDebug()) {
                            Debug.println("[NFS] Request handler error waiting for events");
                            Debug.println(ex);
                        }
                    }
                }

                // Check if the shutdown flag has been set
                if (m_shutdown == true)
                    continue;

                // Check if there are any events to process
                if (sessCnt > 0) {

                    try {

                        // Process the socket events
                        processSocketEvents();
                    }
                    catch (Throwable ex) {
                        Debug.println(Thread.currentThread().getName() + ": Exception in processSocketEvents()");
                        Debug.println(ex);
                    }
                }

                // Check if there are any new sessions that need to be registered with the selector, or sessions to be removed
                if (m_sessQueue.numberOfSessions() > 0) {

                    try {

                        // Add new sessions to the selector
                        addNewSockets();
                    }
                    catch (Throwable ex) {
                        Debug.println(Thread.currentThread().getName() + ": Exception in addNewSockets()");
                        Debug.println(ex);
                    }
                }

                // Check if the idle session reaper should be run to remove stale sessions
                if (m_runIdleSessReaper.get() == true) {

                    try {

                        // Run the idle session reaper
                        int remCnt = runIdleSessionsReaper();

                        // DEBUG
                        if (remCnt > 0 && Debug.EnableError && hasDebug())
                            Debug.println("[NFS] Idle session reaper removed " + remCnt + " sessions");
                    }
                    catch (Throwable ex) {
                        Debug.println(Thread.currentThread().getName() + ": Exception in runIdleSessionsReaper()");
                        Debug.println(ex);
                    }
                }
            }
            catch (Throwable ex) {
                Debug.println(Thread.currentThread().getName() + ": Exception in run() method");
                Debug.println(ex);
            }
        }

        // Close all sessions
        if (m_selector != null) {

            // Enumerate the selector keys to get the session list
            Iterator<SelectionKey> selKeys = m_selector.keys().iterator();

            while (selKeys.hasNext()) {

                // Get the current session via the selection key
                SelectionKey curKey = selKeys.next();
                NFSSrvSession sess = (NFSSrvSession) curKey.attachment();

                // Close the session
                sess.closeSession();
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[NFS] Closed session, " + sess.getUniqueId() + ", addr=" + sess.getRemoteAddress().getHostAddress());
            }

            // Close the selector
            try {
                m_selector.close();
            }
            catch (IOException ex) {
                if (Debug.EnableInfo && hasDebug()) {
                    Debug.println("[NFS] Error closing Selector");
                    Debug.println(ex);
                }
            }
        }

        // DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[NFS] Closed SMB request handler, " + m_thread.getName());
    }

    /**
     * Process socket events, queue sessions/sockets to the thread pool for processing
     */
    private void processSocketEvents() {

        // DEBUG
//		if ( Debug.EnableInfo && hasDebug()) // && sessCnt > 1)
//			Debug.println( "[NFS] Request handler " + m_thread.getName() + " session events, sessCnt=" + sessCnt + "/" + m_selector.keys().size());

        // Clear the thread request list
        m_reqList.clear();

        // Iterate the selected keys
        Iterator<SelectionKey> keysIter = m_selector.selectedKeys().iterator();
        long timeNow = System.currentTimeMillis();

        while (keysIter.hasNext()) {

            // Get the current selection key and check if has an incoming request
            SelectionKey selKey = keysIter.next();
            keysIter.remove();

            if (selKey.isValid() == false) {

                // Remove the selection key
                Debug.println("NFSRequestHandler: Cancelling selection key - " + selKey);
                selKey.cancel();

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[NFS] NIO Selection key not valid, sess=" + selKey.attachment());
            }
            else if (selKey.isReadable()) {

                // DEBUG
//				if ( Debug.EnableInfo && hasDebug())
//					Debug.println("[NFS] Socket read event");

                // Switch off read events for this channel until the current processing is complete
                selKey.interestOps(selKey.interestOps() & ~SelectionKey.OP_READ);

                // Get the associated session and queue a request to the thread pool to read and process the SMB request
                NFSSrvSession sess = (NFSSrvSession) selKey.attachment();
                m_reqList.add(new NIORpcThreadRequest(sess, selKey));

                // Update the last I/O time for the session
                sess.setLastIOTime(timeNow);

                // Check if there are enough thread requests to be queued
                if (m_reqList.size() >= 5) {

                    // DEBUG
//					if ( Debug.EnableInfo && hasDebug())
//						Debug.println( "[NFS] Queueing " + reqList.size() + " thread requests");

                    // Queue the requests to the thread pool
                    m_threadPool.queueRequests(m_reqList);
                    m_reqList.clear();
                }
            }
            else if (selKey.isValid() == false) {

                // Remove the selection key
                Debug.println("SMBRequestHandler: Cancelling selection key - " + selKey);
                selKey.cancel();

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[NFS] NIO Selection key not valid, sess=" + selKey.attachment());
            }
            else {

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[NFS] Unprocessed selection key, " + selKey);
            }
        }

        // Queue the thread requests
        if (m_reqList.size() > 0) {

            // DEBUG
//			if ( Debug.EnableInfo && hasDebug()) // && reqList.size() > 1)
//				Debug.println( "[NFS] Queueing " + reqList.size() + " thread requests (last)");

            // Queue the requests to the thread pool
            m_threadPool.queueRequests(m_reqList);
            m_reqList.clear();
        }
    }

    /**
     * Add new sockets/sessions to the event listener list
     */
    private void addNewSockets() {

        // Register the new sessions with the selector
        while (m_sessQueue.numberOfSessions() > 0) {

            // Get a new session from the queue
            NFSSrvSession sess = (NFSSrvSession) m_sessQueue.removeSessionNoWait();

            if (sess != null) {

                // If the session does not yet have an associated RPC processor it is a new session
                if (sess.getRpcProcessor() == null) {

                    // DEBUG
                    if (Debug.EnableError && hasDebug())
                        Debug.println("[NFS] Register session with request handler, handler=" + m_thread.getName() + ", sess=" + sess.getUniqueId());

                    // Get the socket channel from the sessions packet handler
                    if (sess.getPacketHandler() instanceof RpcChannelPacketHandler) {

                        // Get the channel packet handler and register the socket channel with the selector
                        RpcChannelPacketHandler chanPktHandler = (RpcChannelPacketHandler) sess.getPacketHandler();
                        SocketChannel sessChannel = chanPktHandler.getChannel();

                        try {

                            // Register the session channel with the selector
                            sessChannel.configureBlocking(false);
                            sessChannel.register(m_selector, SelectionKey.OP_READ, sess);

                            // Update the last I/O time for the session
                            sess.setLastIOTime(System.currentTimeMillis());
                        }
                        catch (ClosedChannelException ex) {

                            // DEBUG
                            if (Debug.EnableError && hasDebug())
                                Debug.println("[NFS] Failed to register session channel, closed channel");
                        }
                        catch (IOException ex) {

                            // DEBUG
                            if (Debug.EnableError && hasDebug())
                                Debug.println("[NFS] Failed to set channel blocking mode, " + ex.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * Close the request handler
     */
    public final void closeHandler() {

        // Check if the thread is running
        if (m_thread != null) {
            m_shutdown = true;
            try {
                m_thread.interrupt();

                if (m_selector != null)
                    m_selector.wakeup();
            }
            catch (Exception ex) {
            }
        }
    }

    /**
     * Check for idle sessions
     *
     * @return int
     */
    protected final int checkForIdleSessions() {

        // Set the idle session reaper flag and wakeup the main thread
        if (m_thread != null && m_selector != null && m_sessionCount.get() > 0) {

            // Check if the idle session run flag is still set, this indicates that the main thread has hung
            if (m_runIdleSessReaper.get() == true) {

                // Main thread appears to have hung, dump out details
                dumpHandlerDetails();
            }

            // Trigger the idle session reaper
            m_runIdleSessReaper.set(true);

            // Wakeup the main selector thread
            m_selector.wakeup();
        }

        // Indicate no sessions closed, not run yet
        return 0;
    }

    /**
     * Run the idle session check
     *
     * @return int
     */
    private int runIdleSessionsReaper() {

        // Clear the idle session reaper run flag
        m_runIdleSessReaper.set(false);

        // Check if the request handler has any active sessions
        int idleCnt = 0;

        if (m_selector != null && m_sessionCount.get() > 0) {

            // Time to check
            long checkTime = System.currentTimeMillis() - (long) m_clientSocketTimeout;

            // Enumerate the selector keys to get the session list
            Iterator<SelectionKey> selKeys = m_selector.keys().iterator();

            while (selKeys.hasNext()) {

                // Get the current session via the selection key
                SelectionKey curKey = selKeys.next();
                NFSSrvSession sess = (NFSSrvSession) curKey.attachment();

                // Check the time of the last I/O request on this session
                if (sess != null && sess.getLastIOTime() < checkTime) {

                    // DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("[NFS] Closing idle session, " + sess.getUniqueId() + ", addr="
                                + sess.getRemoteAddress() + ":" + sess.getRemotePort());

                    // Close the session
                    sess.closeSession();

                    // Update the idle session count
                    idleCnt++;
                }
            }

            // If any sessions were closed then wakeup the selector thread
            if (idleCnt > 0)
                m_selector.wakeup();
        }

        // Return the count of idle sessions that were closed
        return idleCnt;
    }

    /**
     * Equality test
     *
     * @param obj Object
     * @return boolean
     */
    public boolean equals(Object obj) {

        // Check for the same type
        if (obj instanceof NFSRequestHandler) {
            NFSRequestHandler reqHandler = (NFSRequestHandler) obj;
            return reqHandler.getName().equals(getName());
        }
        return false;
    }
    
    /**
     * Dump the request handler details
     */
    private void dumpHandlerDetails() {

        // Dump out the request handler details
        Debug.println("NFSRequestHandler details:");
        Debug.println("  Thread: " + m_thread);
        if (m_thread != null) {
            Debug.println("    Name  : " + m_thread.getName());
            Debug.println("    State : " + m_thread.getState());

            StackTraceElement[] thStack = m_thread.getStackTrace();
            if (thStack != null) {
                Debug.println("    Stack : ");

                for (StackTraceElement stElem : thStack) {
                    Debug.println("        " + stElem);
                }
            }
            else
                Debug.println("    No Stack");
        }
        Debug.println("  Sessions: " + m_sessionCount.get());
        Debug.println("  Session Queue: " + m_sessQueue.numberOfSessions());
        Debug.println("  Selector: " + m_selector);

        Debug.println("  ThreadRequestPool: queue=" + m_threadPool.numberOfRequests());
        Debug.println("  NoPooledMemoryException: count=" + NoPooledMemoryException.getExceptionCounter());
    }

    /********** SessionListener interface **********/

    /**
     * Called when a network session is closed.
     *
     * @param sess Network session details.
     */
    public void sessionClosed(SrvSession sess) {


    }

    /**
     * Called when a new network session is created by a network server.
     *
     * @param sess Network session that has been created for the new connection.
     */
    public void sessionCreated(SrvSession sess) {

        // Not interested in create events
    }

    /**
     * Called when a user logs on to a network server
     *
     * @param sess Network session that has been logged on.
     */
    public void sessionLoggedOn(SrvSession sess) {

        // Not interested in logon events
    }
}
