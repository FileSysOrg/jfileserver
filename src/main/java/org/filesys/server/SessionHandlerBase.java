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

package org.filesys.server;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Session Handler Base Class
 *
 * <p>
 * Implementation of a session handler that uses a Java socket to listen for incoming session
 * requests.
 *
 * @author gkspencer
 */
public abstract class SessionHandlerBase implements SessionHandlerInterface {

    // Constants
    //
    // Default socket listen back log limit
    public static final int ListenBacklog = 100;

    // Server that the handler is associated with
    private NetworkServer m_server;

    // Address/port to use
    private int m_port;
    private InetAddress m_bindAddr;

    // Socket listen back log limit
    private int m_backLog = ListenBacklog;

    // Session id
    private int m_sessId;

    // Session handler name, protocol name
    private String m_name;
    private String m_protocol;

    // Shutdown request flag
    private boolean m_shutdown;

    // Debug enable
    private boolean m_debug;

    /**
     * Class constructor
     *
     * @param name     String
     * @param protocol String
     * @param server   NetworkServer
     * @param addr     InetAddress
     * @param port     int
     */
    public SessionHandlerBase(String name, String protocol, NetworkServer server, InetAddress addr, int port) {
        m_name = name;
        m_protocol = protocol;
        m_server = server;

        m_bindAddr = addr;
        m_port = port;
    }

    /**
     * Return the server
     *
     * @return NetworkServer
     */
    public final NetworkServer getServer() {
        return m_server;
    }

    /**
     * Return the session handler name
     *
     * @return String
     */
    public final String getHandlerName() {
        return m_name;
    }

    /**
     * Return the short protocol name
     *
     * @return String
     */
    public final String getProtocolName() {
        return m_protocol;
    }

    /**
     * Check if the server should bind to a specific network address
     *
     * @return boolean
     */
    public final boolean hasBindAddress() {
        return m_bindAddr != null ? true : false;
    }

    /**
     * Return the network address that the server should bind to
     *
     * @return InetAddress
     */
    public final InetAddress getBindAddress() {
        return m_bindAddr;
    }

    /**
     * Return the port that the server should bind to
     *
     * @return int
     */
    public final int getPort() {
        return m_port;
    }

    /**
     * Return the socket listen backlog limit
     *
     * @return int
     */
    public final int getListenBacklog() {
        return m_backLog;
    }

    /**
     * Determine if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Clear the shutdown flag
     */
    protected final void clearShutdown() {
        m_shutdown = false;
    }

    /**
     * Determine if the shutdown flag has been set
     *
     * @return boolean
     */
    protected final boolean hasShutdown() {
        return m_shutdown;
    }

    /**
     * Get the next available session id
     *
     * @return int
     */
    protected synchronized int getNextSessionId() {
        return m_sessId++;
    }

    /**
     * Enable/disable debug output
     *
     * @param dbg boolean
     */
    public final void setDebug(boolean dbg) {
        m_debug = dbg;
    }

    /**
     * Set the local port that the session handler is using
     *
     * @param port int
     */
    protected final void setPort(int port) {
        m_port = port;
    }

    /**
     * Set/clear the shutdown flag
     *
     * @param shut boolean
     */
    protected final void setShutdown(boolean shut) {
        m_shutdown = shut;
    }

    /**
     * Initialize the session handler
     *
     * @param server NetworkServer
     */
    public abstract void initializeSessionHandler(NetworkServer server)
            throws IOException;

    /**
     * Close the session handler
     *
     * @param server NetworkServer
     */
    public abstract void closeSessionHandler(NetworkServer server);

    /**
     * Return the session handler details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("[");
        str.append(getProtocolName());
        str.append(",");
        str.append(getHandlerName());

        str.append(",");
        if (hasBindAddress())
            str.append(getBindAddress().getHostAddress());
        else
            str.append("ALL");
        str.append(":");
        str.append(getPort());
        str.append("]");

        return str.toString();
    }
}
