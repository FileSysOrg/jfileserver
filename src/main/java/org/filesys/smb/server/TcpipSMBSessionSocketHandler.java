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

package org.filesys.smb.server;

import java.net.InetAddress;
import java.net.Socket;

import org.filesys.debug.Debug;
import org.filesys.server.SocketSessionHandler;
import org.filesys.server.config.ServerConfiguration;

/**
 * Native SMB Session Socket Handler Class
 *
 * @author gkspencer
 */
public class TcpipSMBSessionSocketHandler extends SocketSessionHandler {

    // Thread group
    private static final ThreadGroup TcpipSMBGroup = new ThreadGroup("TcpipSMBSessions");

    /**
     * Class constructor
     *
     * @param srv      SMBServer
     * @param port     int
     * @param bindAddr InetAddress
     * @param debug    boolean
     */
    public TcpipSMBSessionSocketHandler(SMBServer srv, int port, InetAddress bindAddr, boolean debug) {
        super("TCP-SMB", "SMB", srv, bindAddr, port);

        // Enable/disable debug output
        setDebug(debug);
    }

    /**
     * Accept a new connection on the specified socket
     *
     * @param sock Socket
     */
    protected void acceptConnection(Socket sock) {

        try {

            // Set a socket timeout
            sock.setSoTimeout(getSocketTimeout());

            // Create a packet handler for the session
            SMBServer smbServer = (SMBServer) getServer();
            PacketHandler pktHandler = new TcpipSMBPacketHandler(sock, smbServer.getPacketPool());

            // Create a server session for the new request, and set the session id.
            SMBSrvSession srvSess = SMBSrvSession.createSession(pktHandler, smbServer, getNextSessionId());
            if (Debug.EnableInfo && hasDebug())
                Debug.println("[SMB] Created session " + srvSess.getUniqueId());

            // Start the new session in a seperate thread
            Thread srvThread = new Thread(TcpipSMBGroup, srvSess);
            srvThread.setDaemon(true);
            srvThread.setName("Sess_T" + srvSess.getSessionId() + "_" + sock.getInetAddress().getHostAddress());
            srvThread.start();
        }
        catch (Exception ex) {

            // Debug
            if (Debug.EnableInfo && hasDebug())
                Debug.println("[SMB] TCP-SMB Failed to create session, " + ex.toString());
        }
    }

    /**
     * Create the TCP/IP native SMB session socket handlers for the main SMB server
     *
     * @param server  SMBServer
     * @param sockDbg boolean
     * @throws Exception Failed to create the session handlers
     */
    public final static void createSessionHandlers(SMBServer server, boolean sockDbg)
            throws Exception {

        // Access the SMB server configuration
        ServerConfiguration config = server.getConfiguration();
        SMBConfigSection smbConfig = (SMBConfigSection) config.getConfigSection(SMBConfigSection.SectionName);

        // Create the NetBIOS SMB handler
        SocketSessionHandler sessHandler = new TcpipSMBSessionSocketHandler(server, smbConfig.getTcpipSMBPort(), smbConfig.getSMBBindAddress(), sockDbg);
        sessHandler.setSocketTimeout(smbConfig.getSocketTimeout());

        sessHandler.initializeSessionHandler(server);

        // Run the TCP/IP SMB session handler in a seperate thread
        Thread tcpThread = new Thread(sessHandler);
        tcpThread.setName("TcpipSMB_Handler");
        tcpThread.start();

        // DEBUG
        if (Debug.EnableError && sockDbg)
            Debug.println("[SMB] Native SMB TCP session handler created");
    }
}
