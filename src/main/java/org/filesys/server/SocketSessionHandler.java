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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.filesys.debug.Debug;

/**
 * Socket Session Handler Class
 *
 * <p>
 * Implementation of a session handler that uses a Java socket to listen for incoming session
 * requests.
 *
 * @author gkspencer
 */
public abstract class SocketSessionHandler extends SessionHandlerBase implements Runnable {

    // Server socket to listen for incoming connections
    private ServerSocket m_srvSock;

    // Client socket read timeout
    private int m_clientSockTmo;

    /**
     * Class constructor
     *
     * @param name     String
     * @param protocol String
     * @param server   NetworkServer
     * @param addr     InetAddress
     * @param port     int
     */
    public SocketSessionHandler(String name, String protocol, NetworkServer server, InetAddress addr, int port) {
        super(name, protocol, server, addr, port);
    }

    /**
     * Return the server socket
     *
     * @return ServerSocket
     */
    public final ServerSocket getSocket() {
        return m_srvSock;
    }

    /**
     * Return the client socket timeout, in milliseconds
     *
     * @return int
     */
    public final int getSocketTimeout() {
        return m_clientSockTmo;
    }

    /**
     * Set the client socket timeout, in milliseconds, zero for no timeout
     *
     * @param tmo int
     */
    public final void setSocketTimeout(int tmo) {
        m_clientSockTmo = tmo;
    }

    /**
     * Initialize the session handler
     *
     * @param server NetworkServer
     */
    public void initializeSessionHandler(NetworkServer server)
            throws IOException {

        // Open the server socket
        if (hasBindAddress())
            m_srvSock = new ServerSocket(getPort(), getListenBacklog(), getBindAddress());
        else
            m_srvSock = new ServerSocket(getPort(), getListenBacklog());

        // Set the allocated port
        if (getPort() == 0)
            setPort(m_srvSock.getLocalPort());

        // DEBUG
        if (Debug.EnableInfo && hasDebug()) {
            Debug.print("[" + getProtocolName() + "] Binding " + getHandlerName() + " session handler to address : ");
            if (hasBindAddress())
                Debug.println(getBindAddress().getHostAddress());
            else
                Debug.println("ALL");
        }
    }

    /**
     * Close the session handler
     *
     * @param server NetworkServer
     */
    public void closeSessionHandler(NetworkServer server) {

        // Request the main listener thread shutdown
        setShutdown(true);

        try {

            // Close the server socket to release any pending listen
            if (m_srvSock != null)
                m_srvSock.close();
        }
        catch (SocketException ex) {
        }
        catch (Exception ex) {
        }
    }

    /**
     * Socket listener thread
     */
    public void run() {

        try {

            // Clear the shutdown flag
            clearShutdown();

            // Wait for incoming connection requests
            while (hasShutdown() == false) {

                // Debug
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[" + getProtocolName() + "] Waiting for session request ...");

                // Wait for a connection
                Socket sessSock = m_srvSock.accept();

                // Debug
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[" + getProtocolName() + "] Session request received from "
                            + sessSock.getInetAddress().getHostAddress());

                try {

                    // Process the new connection request
                    acceptConnection(sessSock);
                }
                catch (Exception ex) {

                    // Debug
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("[" + getProtocolName() + "] Failed to create session, " + ex.toString());
                }
            }
        }
        catch (SocketException ex) {

            // Do not report an error if the server has shutdown, closing the server socket
            // causes an exception to be thrown.
            if (hasShutdown() == false) {
                Debug.println("[" + getProtocolName() + "] Socket error : " + ex.toString());
                Debug.println(ex);
            }
        }
        catch (Exception ex) {

            // Do not report an error if the server has shutdown, closing the server socket
            // causes an exception to be thrown.
            if (hasShutdown() == false) {
                Debug.println("[" + getProtocolName() + "] Server error : " + ex.toString());
                Debug.println(ex);
            }
        }

        // Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[" + getProtocolName() + "] " + getHandlerName() + " session handler closed");
    }

    /**
     * Accept a new connection on the specified socket
     *
     * @param sock Socket
     */
    protected abstract void acceptConnection(Socket sock);
}
