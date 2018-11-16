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

package org.filesys.smb.server.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.filesys.debug.Debug;
import org.filesys.server.ChannelSessionHandler;
import org.filesys.server.SessionHandlerInterface;
import org.filesys.server.SessionHandlerList;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.smb.mailslot.HostAnnouncer;
import org.filesys.smb.server.SMBConfigSection;
import org.filesys.smb.server.SMBConnectionsHandler;
import org.filesys.smb.server.PacketHandler;
import org.filesys.smb.server.SMBServer;
import org.filesys.smb.server.SMBSrvSession;

/**
 * NIO Connections Handler Class
 *
 * <p>Initializes the configured SMB session handlers and listens for incoming requests using a single thread.
 *
 * @author gkspencer
 */
public class NIOSMBConnectionsHandler implements SMBConnectionsHandler, RequestHandlerListener, Runnable {

    // Constants
    //
    // Number of session socket channels each request handler thread monitors
    public static final int SessionSocketsPerHandler = 50; // 250;

    // List of session handlers that are waiting for incoming requests
    private SessionHandlerList m_handlerList;

    // Selector used to monitor incoming connections
    private Selector m_selector;

    // Session request handler(s)
    //
    // Each handler processes the socket read events for a number of session socket channels
    private List<SMBRequestHandler> m_requestHandlers;

    // SMB server
    private SMBServer m_server;

    // Connection handler thread
    private Thread m_thread;

    // Shutdown request flag
    private boolean m_shutdown;

    // Session id
    private int m_sessId;

    // Client socket timeout, in milliseconds
    private int m_clientSocketTimeout;

    // Idle session reper thread
    private IdleSessionReaper m_idleSessReaper;

    // Debug output
    private boolean m_debug;

    // Thread pool debug
    private boolean m_threadDebug;

    /**
     * Idle Session Reaper Thread Class
     *
     * <p>Check for sessions that have no recent I/O requests. The session timeout is configurable.
     */
    protected class IdleSessionReaper implements Runnable {

        //	Reaper wakeup interval
        private long m_wakeup;

        // Reaper thread
        private Thread m_reaperThread;

        //	Shutdown request flag
        private boolean m_shutdown = false;

        /**
         * Class constructor
         *
         * @param intvl long
         */
        public IdleSessionReaper(long intvl) {
            m_wakeup = intvl;

            // Create a thread for the reaper, and start the thread
            m_reaperThread = new Thread(this);
            m_reaperThread.setDaemon(true);
            m_reaperThread.setName("SMB_IdleSessionReaper_NIO");

            m_reaperThread.start();
        }

        /**
         * Shutdown the connection reaper
         */
        public final void shutdownRequest() {
            m_shutdown = true;
            m_reaperThread.interrupt();
        }

        /**
         * Connection reaper thread
         */
        public void run() {

            //	Loop forever, or until shutdown
            while (m_shutdown == false) {

                //	Sleep for a while
                try {
                    Thread.sleep(m_wakeup);
                }
                catch (InterruptedException ex) {
                }

                //	Check if there is a shutdown pending
                if (m_shutdown == true)
                    break;

                // Check for idle sessions in the active SMB request handlers
                Iterator<SMBRequestHandler> enumHandlers = m_requestHandlers.iterator();

                while (enumHandlers.hasNext()) {

                    // Get the current request handler and check for idle session
                    SMBRequestHandler curHandler = enumHandlers.next();
                    if (curHandler != null) {

                        // Check for idle sessions
                        int idleCnt = curHandler.checkForIdleSessions();

                        // DEBUG
                        if (idleCnt > 0 && Debug.EnableInfo && hasDebug())
                            Debug.println("[SMB] Idle session check, removed " + idleCnt + " sessions for " + curHandler.getName());
                    }
                }
            }
        }
    }

    ;

    /**
     * Class constructor
     */
    public NIOSMBConnectionsHandler() {
        m_handlerList = new SessionHandlerList();
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
     * Return the count of active session handlers
     *
     * @return int
     */
    public int numberOfSessionHandlers() {
        return m_handlerList.numberOfHandlers();
    }

    /**
     * Add a host announcer to the connections handler
     *
     * @param announcer HostAnnouncer
     */
    public void addHostAnnouncer(HostAnnouncer announcer) {
        // Not used
    }

    /**
     * Add a session handler to the connections handler
     *
     * @param sessHandler SessionHandlerInterface
     */
    public void addSessionHandler(SessionHandlerInterface sessHandler) {
        // Not used
    }

    /**
     * Initialize the connections handler
     *
     * @param srv    SMBServer
     * @param config SMBConfigSection
     * @throws InvalidConfigurationException Failed to initialize the connection handler
     */
    public final void initializeHandler(SMBServer srv, SMBConfigSection config)
            throws InvalidConfigurationException {

        // Save the server the handler is associated with
        m_server = srv;

        // Check if socket debug output is enabled
        if ((config.getSessionDebugFlags() & SMBSrvSession.DBG_SOCKET) != 0)
            m_debug = true;

        // Check if thread pool debug is enabled
        if ((config.getSessionDebugFlags() & SMBSrvSession.DBG_THREADPOOL) != 0)
            m_threadDebug = true;

        // Create the native SMB/port 445 session handler, if enabled
        if (config.hasTcpipSMB()) {

            // Create the native SMB/port 445 session handler
            ChannelSessionHandler sessHandler = new TcpipSMBChannelSessionHandler(srv, config.getSMBBindAddress(), config.getTcpipSMBPort());
            sessHandler.setDebug(hasDebug());

            try {

                // Initialize the session handler, and add to the active list
                sessHandler.initializeSessionHandler(srv);
                m_handlerList.addHandler(sessHandler);
            }
            catch (IOException ex) {
                throw new InvalidConfigurationException("Error initializing TCP-IP SMB session handler, " + ex.getMessage());
            }
        }

        // Create the NetBIOS session handler, if enabled
        if (config.hasNetBIOSSMB()) {

            // Create the NetBIOS SMB session handler
            ChannelSessionHandler sessHandler = new NetBIOSSMBChannelSessionHandler(srv, config.getSMBBindAddress(), config.getSessionPort());
            sessHandler.setDebug(hasDebug());

            try {

                // Initialize the session handler, and add to the active list
                sessHandler.initializeSessionHandler(srv);
                m_handlerList.addHandler(sessHandler);
            }
            catch (IOException ex) {
                throw new InvalidConfigurationException("Error initializing NetBIOS SMB session handler, " + ex.getMessage());
            }
        }

        // Check if any session handlers were created
        if (m_handlerList.numberOfHandlers() == 0)
            throw new InvalidConfigurationException("No SMB session handlers enabled");

        // Set the client socket timeout
        m_clientSocketTimeout = config.getSocketTimeout();

        // Create the session request handler list and add the first handler
        m_requestHandlers = new ArrayList<SMBRequestHandler>();
        SMBRequestHandler reqHandler = new SMBRequestHandler(m_server.getThreadPool(), SessionSocketsPerHandler, m_clientSocketTimeout, hasDebug());
        reqHandler.setThreadDebug(m_threadDebug);
        reqHandler.setListener(this);

        m_requestHandlers.add(reqHandler);
    }

    /**
     * Start the connection handler thread
     */
    public final void startHandler() {

        // Start the connection handler in its own thread
        m_thread = new Thread(this);
        m_thread.setName("SMBConnectionsHandler");
        m_thread.setDaemon(false);
        m_thread.start();

        // Start the idle session reaper thread, if session timeouts are enabled
        if (m_clientSocketTimeout > 0)
            m_idleSessReaper = new IdleSessionReaper(m_clientSocketTimeout / 2);
    }

    /**
     * Stop the connections handler
     */
    public final void stopHandler() {

        // Check if the thread is running
        if (m_thread != null) {
            m_shutdown = true;
            try {
                m_thread.interrupt();
            }
            catch (Exception ex) {
            }

            // Stop the idle session reaper thread, if enabled
            if (m_idleSessReaper != null)
                m_idleSessReaper.shutdownRequest();
        }
    }

    /**
     * Run the connections handler in a seperate thread
     */
    public void run() {

        // Clear the shutdown flag, may have been restarted
        m_shutdown = false;

        // Initialize the socket selector
        try {

            // Create the selector
            m_selector = Selector.open();

            // Register the server sockets with the selector
            for (int idx = 0; idx < m_handlerList.numberOfHandlers(); idx++) {

                // Get the current server socket channel and register with the selector for socket accept events
                ChannelSessionHandler curHandler = (ChannelSessionHandler) m_handlerList.getHandlerAt(idx);

                ServerSocketChannel sockChannel = curHandler.getSocketChannel();
                sockChannel.configureBlocking(false);
                sockChannel.register(m_selector, SelectionKey.OP_ACCEPT, curHandler);

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[SMB] Listening for connections on " + curHandler);
            }
        }
        catch (IOException ex) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug()) {
                Debug.println("[SMB] Error opening/registering Selector");
                Debug.println(ex);
            }

            m_shutdown = true;
        }

        // Loop until shutdown
        while (m_shutdown != true) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("[SMB] Waiting for new connection ...");

            // Wait until there are some connections
            int connCnt = 0;

            try {
                connCnt = m_selector.select();
            }
            catch (IOException ex) {

                // DEBUG
                if (Debug.EnableError && hasDebug()) {
                    Debug.println("[SMB] Error waiting for connection");
                    Debug.println(ex);
                }
            }

            // Check if there are any connection events to process
            if (connCnt == 0)
                continue;

            // Iterate the selected keys
            Iterator<SelectionKey> keysIter = m_selector.selectedKeys().iterator();

            while (keysIter.hasNext()) {

                // Get the current selection key and check if there is an incoming connection
                SelectionKey selKey = keysIter.next();
                if (selKey.isAcceptable()) {

                    try {

                        // Get the listening server socket, accept the new client connection
                        ServerSocketChannel srvChannel = (ServerSocketChannel) selKey.channel();
                        SocketChannel sockChannel = srvChannel.accept();

                        // Create a packet handler for the new connection
                        ChannelSessionHandler channelHandler = (ChannelSessionHandler) selKey.attachment();
                        PacketHandler pktHandler = channelHandler.createPacketHandler(sockChannel);

                        // Create the new session
                        SMBSrvSession sess = SMBSrvSession.createSession(pktHandler, m_server, ++m_sessId);

                        // DEBUG
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("[SMB] Created session " + sess.getUniqueId());

                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("[SMB] Connection from " + sockChannel.socket().getRemoteSocketAddress() + ", handler=" + channelHandler + ", sess=" + sess.getUniqueId());

                        // Add the new session to a request handler thread
                        queueSessionToHandler(sess);
                    }
                    catch (IOException ex) {

                        // DEBUG
                        if (Debug.EnableError && hasDebug()) {
                            Debug.println("[SMB] Failed to accept connection");
                            Debug.println(ex);
                        }
                    }
                }

                // Remove the key from the selected list
                keysIter.remove();
            }
        }

        // Close the session handlers
        for (int idx = 0; idx < m_handlerList.numberOfHandlers(); idx++) {

            // Close the current session handler
            ChannelSessionHandler sessHandler = (ChannelSessionHandler) m_handlerList.getHandlerAt(idx);
            sessHandler.closeSessionHandler(null);

            // DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("[SMB] Closed session handler " + sessHandler);
        }

        // Close the request handlers
        while (m_requestHandlers.size() > 0) {

            // Close the current request handler
            SMBRequestHandler reqHandler = m_requestHandlers.remove(0);
            reqHandler.closeHandler();

            // DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("[SMB] Closed request handler, " + reqHandler.getName());
        }

        // Close the selector
        if (m_selector != null) {

            try {
                m_selector.close();
            }
            catch (Exception ex) {

                // DEBUG
                if (Debug.EnableError && hasDebug())
                    Debug.println("[SMB] Error closing socket selector, " + ex.getMessage());
            }
        }

        // Clear the active thread before exiting
        m_thread = null;
    }

    /**
     * Queue a new session to a request handler
     *
     * @param sess SMBSrvSession
     */
    private final void queueSessionToHandler(SMBSrvSession sess) {

        // Check if the current handler has room for a new session
        SMBRequestHandler reqHandler = null;

        synchronized (m_requestHandlers) {

            // Get the head of the request handler list
            reqHandler = m_requestHandlers.get( 0);

            if (reqHandler == null || reqHandler.hasFreeSessionSlot() == false) {

                // Create a new session request handler and add to the head of the list
                reqHandler = new SMBRequestHandler(m_server.getThreadPool(), SessionSocketsPerHandler, m_clientSocketTimeout, hasDebug());
                reqHandler.setThreadDebug(m_threadDebug);
                reqHandler.setListener(this);

                m_requestHandlers.add(0, reqHandler);

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[SMB] Added new SMB request handler, " + reqHandler);
            }
        }

        // Queue the new session to the current request handler
        reqHandler.queueSessionToHandler(sess);
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
     * Request handler has no sessions to listen for events for
     *
     * @param reqHandler RequestHandler
     */
    public void requestHandlerEmpty(RequestHandler reqHandler) {

        synchronized (m_handlerList) {

            // Check if the request handler is the current head of the handler list, if not then we can close
            // this request handler
            if (m_requestHandlers.get(0).getName().equals(reqHandler.getName()) == false) {

                // Remove the handler from the request handler list
                m_requestHandlers.remove(reqHandler);

                // Close the request handler
                reqHandler.closeHandler();

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[SMB] Removed empty request handler, " + reqHandler.getName());
            }
        }
    }
}
