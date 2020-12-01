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
import org.filesys.oncrpc.Rpc;
import org.filesys.oncrpc.RpcPacketHandler;
import org.filesys.oncrpc.nfs.NFSConfigSection;
import org.filesys.oncrpc.nfs.NFSServer;
import org.filesys.oncrpc.nfs.NFSSrvSession;
import org.filesys.server.ChannelSessionHandler;
import org.filesys.server.SessionHandlerList;
import org.filesys.server.SessionLimitException;
import org.filesys.server.config.InvalidConfigurationException;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * NFS Connections Handler Class
 *
 * <p>Initializes the configured NFS session handlers and listens for incoming requests using a single thread.
 *
 * @author gkspencer
 */
public class NFSConnectionsHandler implements Runnable, NFSRequestHandlerListener {

    // Constants
    //
    // Number of session socket channels each request handler thread monitors
    public static final int SessionSocketsPerHandler = 50;

    // List of session handlers that are waiting for incoming requests
    private SessionHandlerList m_handlerList;

    // Selector used to monitor incoming connections
    private Selector m_selector;

    // Session request handler(s)
    //
    // Each handler processes the socket read events for a number of session socket channels
    private List<NFSRequestHandler> m_requestHandlers;

    // NFS server
    private NFSServer m_server;

    // Connection handler thread
    private Thread m_thread;

    // Shutdown request flag
    private boolean m_shutdown;

    // Session id
    private int m_sessId;

    // Client socket timeout, in milliseconds
    private int m_clientSocketTimeout;

    // Idle session reaper thread
    private NFSConnectionsHandler.IdleSessionReaper m_idleSessReaper;

    // Port that the connection handler is using
    private int m_port;

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
            m_reaperThread.setName("NFS_IdleSessionReaper_NIO");

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
                Iterator<NFSRequestHandler> enumHandlers = m_requestHandlers.iterator();

                while (enumHandlers.hasNext()) {

                    // Get the current request handler and check for idle session
                    NFSRequestHandler curHandler = enumHandlers.next();
                    if (curHandler != null) {

                        // Check for idle sessions
                        int idleCnt = curHandler.checkForIdleSessions();

                        // DEBUG
                        if (idleCnt > 0 && Debug.EnableInfo && hasDebug())
                            Debug.println("[NFS] Idle session check, removed " + idleCnt + " sessions for " + curHandler.getName());
                    }
                }
            }
        }
    }

    ;

    /**
     * Class constructor
     */
    public NFSConnectionsHandler() {
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
     * Return the TCP port that the connection is listening on
     *
     * @return int
     */
    public final int getPort() {
        return m_port;
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
     * Initialize the connections handler
     *
     * @param srv    NFSServer
     * @param config NFSConfigSection
     * @throws InvalidConfigurationException Failed to initialize the connection handler
     */
    public final void initializeHandler(NFSServer srv, NFSConfigSection config)
            throws InvalidConfigurationException {

        // Save the server the handler is associated with
        m_server = srv;

        // Check if socket debug output is enabled
        if (config.getNFSDebug().contains( NFSSrvSession.Dbg.SESSION))
            m_debug = true;

        // Save the port the connection handler is listening on
        m_port = config.getNFSServerPort();

        // Create the TCP/IP NFS session handler
        TcpRpcChannelSessionHandler sessHandler = new TcpRpcChannelSessionHandler( m_server, null,  getPort());
        sessHandler.setDebug( hasDebug());

        try {

            // Initialize the session handler, and add to the active list
            sessHandler.initializeSessionHandler(srv);
            m_handlerList.addHandler(sessHandler);
        }
        catch (IOException ex) {
            throw new InvalidConfigurationException("Error initializing TCP-IP NFS session handler, " + ex.getMessage());
        }

        // Check if any session handlers were created
        if (m_handlerList.numberOfHandlers() == 0)
            throw new InvalidConfigurationException("No NFS session handlers enabled");

        // Create the session request handler list and add the first handler
        m_requestHandlers = new ArrayList<>();
        NFSRequestHandler reqHandler = new NFSRequestHandler( m_server, SessionSocketsPerHandler, m_clientSocketTimeout, m_debug);
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
        m_thread.setName("NFSConnectionsHandler");
        m_thread.setDaemon(false);
        m_thread.start();

        // Start the idle session reaper thread, if session timeouts are enabled
        if (m_clientSocketTimeout > 0)
            m_idleSessReaper = new NFSConnectionsHandler.IdleSessionReaper(m_clientSocketTimeout / 2);
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
                RpcChannelSessionHandler curHandler = (RpcChannelSessionHandler) m_handlerList.getHandlerAt(idx);

                ServerSocketChannel sockChannel = curHandler.getSocketChannel();
                sockChannel.configureBlocking(false);
                sockChannel.register(m_selector, SelectionKey.OP_ACCEPT, curHandler);

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[NFS] Listening for connections on " + curHandler);
            }
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
        while (m_shutdown != true) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("[NFS] Waiting for new connection ...");

            // Wait until there are some connections
            int connCnt = 0;

            try {
                connCnt = m_selector.select();
            }
            catch (IOException ex) {

                // DEBUG
                if (Debug.EnableError && hasDebug()) {
                    Debug.println("[NFS] Error waiting for connection");
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
                SocketChannel sockChannel = null;

                if (selKey.isAcceptable()) {

                    try {

                        // Get the listening server socket, accept the new client connection
                        ServerSocketChannel srvChannel = (ServerSocketChannel) selKey.channel();
                        sockChannel = srvChannel.accept();

                        // Create a packet handler for the new connection
                        RpcChannelSessionHandler channelHandler = (RpcChannelSessionHandler) selKey.attachment();
                        RpcPacketHandler pktHandler = channelHandler.createPacketHandler(sockChannel);

                        // Create the new session
                        NFSSrvSession sess = NFSSrvSession.createSession(pktHandler, m_server, ++m_sessId, Rpc.ProtocolId.TCP,
                                sockChannel.socket().getRemoteSocketAddress());

                        // DEBUG
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("[NFS] Created session " + sess.getUniqueId());

                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("[NFS] Connection from " + sockChannel.socket().getRemoteSocketAddress() + ", handler=" + channelHandler + ", sess=" + sess.getUniqueId());

                        // Add the new session to a request handler thread
                        queueSessionToHandler(sess);
                    }
                    catch ( SessionLimitException ex) {

                        // Log the error
                        Debug.println("[NFS] Session limit reached - " + ex.getMessage());

                        // Close the new socket connection
                        if ( sockChannel != null) {
                            try {
                                sockChannel.close();
                            }
                            catch (IOException ex2) {
                            }
                        }
                    }
                    catch (IOException ex) {

                        // DEBUG
                        if (Debug.EnableError && hasDebug()) {
                            Debug.println("[NFS] Failed to accept connection");
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
            RpcChannelSessionHandler sessHandler = (RpcChannelSessionHandler) m_handlerList.getHandlerAt(idx);
            sessHandler.closeSessionHandler(null);

            // DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("[NFS] Closed session handler " + sessHandler);
        }

        // Close the request handlers
        while (m_requestHandlers.size() > 0) {

            // Close the current request handler
            NFSRequestHandler reqHandler = m_requestHandlers.remove(0);
            reqHandler.closeHandler();

            // DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("[NFS] Closed request handler, " + reqHandler.getName());
        }

        // Close the selector
        if (m_selector != null) {

            try {
                m_selector.close();
            }
            catch (Exception ex) {

                // DEBUG
                if (Debug.EnableError && hasDebug())
                    Debug.println("[NFS] Error closing socket selector, " + ex.getMessage());
            }
        }

        // Clear the active thread before exiting
        m_thread = null;
    }

    /**
     * Queue a new session to a request handler
     *
     * @param sess NFSSrvSession
     */
    private final void queueSessionToHandler(NFSSrvSession sess) {

        // Check if the current handler has room for a new session
        NFSRequestHandler reqHandler = null;

        synchronized (m_requestHandlers) {

            // Get the head of the request handler list
            reqHandler = m_requestHandlers.get( 0);

            if (reqHandler == null || reqHandler.hasFreeSessionSlot() == false) {

                // Create a new session request handler and add to the head of the list
                reqHandler = new NFSRequestHandler(m_server, SessionSocketsPerHandler, m_clientSocketTimeout, hasDebug());
                reqHandler.setThreadDebug(m_threadDebug);
                reqHandler.setListener(this);

                m_requestHandlers.add(0, reqHandler);

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[NFS] Added new NFS request handler, " + reqHandler);
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
     * @param reqHandler NFSRequestHandler
     */
    public void requestHandlerEmpty(NFSRequestHandler reqHandler) {

        synchronized (m_handlerList) {

            // Check if the request handler is the current head of the handler list, if not then we can close
            // this request handler
            if (m_requestHandlers.size() > 0 && m_requestHandlers.get(0).getName().equals(reqHandler.getName()) == false) {

                // Remove the handler from the request handler list
                m_requestHandlers.remove(reqHandler);

                // Close the request handler
                reqHandler.closeHandler();

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[NFS] Removed empty request handler, " + reqHandler.getName());
            }
        }
    }
}
