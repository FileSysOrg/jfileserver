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

package org.filesys.ftp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * FTP Data Session Class
 *
 * <p>A data connection is made when a PORT or PASV FTP command is received on the main control session.
 *
 * <p>The PORT command will actively connect to the specified address/port on the client. The PASV command will create a
 * listening socket and wait for the client to connect.
 *
 * @author gkspencer
 */
public class FTPDataSession implements Runnable {

    //	FTP session that this data connection is associated with
    private FTPSrvSession m_cmdSess;

    //	Connection details for active connection
    private InetAddress m_clientAddr;
    private int m_clientPort;

    //	Local port to use
    private int m_localPort;

    //	Active data session socket
    private Socket m_activeSock;

    //	Passive data session socket
    private ServerSocket m_passiveSock;

    //	Adapter to bind the passive socket to
    private InetAddress m_bindAddr;

    //	Transfer in progress and abort file transfer flags
    private boolean m_transfer;
    private boolean m_abort;

    //	Send/receive data byte count
    private long m_bytCount;

    /**
     * Class constructor
     *
     * <p>Create a data connection that listens for an incoming connection.
     *
     * @param sess FTPSrvSession
     * @exception IOException Error initializing the data session
     */
    protected FTPDataSession(FTPSrvSession sess)
            throws IOException {

        //	Set the associated command session
        m_cmdSess = sess;

        //	Create a server socket to listen for the incoming connection
        m_passiveSock = new ServerSocket(0, 1, null);
    }

    /**
     * Class constructor
     *
     * <p>Create a data connection that listens for an incoming connection on the specified
     * network adapter and local port.
     *
     * @param sess      FTPSrvSession
     * @param localPort int
     * @param bindAddr  InetAddress
     * @exception IOException Error initializing the data session
     */
    protected FTPDataSession(FTPSrvSession sess, int localPort, InetAddress bindAddr)
            throws IOException {

        //	Set the associated command session
        m_cmdSess = sess;

        //	Create a server socket to listen for the incoming connection on the specified network adapter
        m_localPort = localPort;
        m_passiveSock = new ServerSocket(localPort, 1, bindAddr);
    }

    /**
     * Class constructor
     *
     * <p>Create a data connection that listens for an incoming connection on the specified
     * network adapter.
     *
     * @param sess     FTPSrvSession
     * @param bindAddr InetAddress
     * @exception IOException Error initializing the data session
     */
    protected FTPDataSession(FTPSrvSession sess, InetAddress bindAddr)
            throws IOException {

        //	Set the associated command session
        m_cmdSess = sess;

        //	Create a server socket to listen for the incoming connection on the specified network adapter
        m_passiveSock = new ServerSocket(0, 1, bindAddr);
    }

    /**
     * Class constructor
     *
     * <p>Create a data connection to the specified client address and port.
     *
     * @param sess FTPSrvSession
     * @param addr InetAddress
     * @param port int
     */
    protected FTPDataSession(FTPSrvSession sess, InetAddress addr, int port) {

        //	Set the associated command session
        m_cmdSess = sess;

        //	Save the client address/port details, the actual connection will be made later when
        //	the client requests/sends a file
        m_clientAddr = addr;
        m_clientPort = port;
    }

    /**
     * Class constructor
     *
     * <p>Create a data connection to the specified client address and port, using the specified local
     * port.
     *
     * @param sess      FTPSrvSession
     * @param localPort int
     * @param addr      InetAddress
     * @param port      int
     */
    protected FTPDataSession(FTPSrvSession sess, int localPort, InetAddress addr, int port) {

        //	Set the associated command session
        m_cmdSess = sess;

        //	Save the local port
        m_localPort = localPort;

        //	Save the client address/port details, the actual connection will be made later when
        //	the client requests/sends a file
        m_clientAddr = addr;
        m_clientPort = port;
    }

    /**
     * Return the associated command session
     *
     * @return FTPSrvSession
     */
    public final FTPSrvSession getCommandSession() {
        return m_cmdSess;
    }

    /**
     * Return the local port
     *
     * @return int
     */
    public final int getLocalPort() {
        if (m_passiveSock != null)
            return m_passiveSock.getLocalPort();
        else if (m_activeSock != null)
            return m_activeSock.getLocalPort();
        return -1;
    }

    /**
     * Return the port that was allocated to the data session
     *
     * @return int
     */
    protected final int getAllocatedPort() {
        return m_localPort;
    }

    /**
     * Return the passive server socket address
     *
     * @return InetAddress
     */
    public final InetAddress getPassiveAddress() {
        if (m_passiveSock != null) {

            //	Get the server socket local address
            InetAddress addr = m_passiveSock.getInetAddress();
            if (addr.getHostAddress().compareTo("0.0.0.0") == 0) {
                try {
                    addr = InetAddress.getLocalHost();
                }
                catch (UnknownHostException ex) {
                }
            }
            return addr;
        }
        return null;
    }

    /**
     * Return the passive server socket port
     *
     * @return int
     */
    public final int getPassivePort() {
        if (m_passiveSock != null)
            return m_passiveSock.getLocalPort();
        return -1;
    }

    /**
     * Determine if a file transfer is active
     *
     * @return boolean
     */
    public final boolean isTransferActive() {
        return m_transfer;
    }

    /**
     * Abort an in progress file transfer
     */
    public final void abortTransfer() {
        m_abort = true;
    }

    /**
     * Return the transfer byte count
     *
     * @return long
     */
    public final synchronized long getTransferByteCount() {
        return m_bytCount;
    }

    /**
     * Return the data socket connected to the client
     *
     * @return Socket
     * @exception IOException Socket error
     */
    public final Socket getSocket()
            throws IOException {

        //	Check for a passive connection, get the incoming socket connection
        if (m_passiveSock != null)
            m_activeSock = m_passiveSock.accept();
        else {
            if (m_localPort != 0) {

                //	Use the specified local port
                m_activeSock = new Socket(m_clientAddr, m_clientPort, null, m_localPort);
            } else
                m_activeSock = new Socket(m_clientAddr, m_clientPort);
        }

        //	Set the socket to close immediately
        m_activeSock.setSoLinger(false, 0);

        //	Return the data socket
        return m_activeSock;
    }

    /**
     * Close the data connection
     */
    public final void closeSession() {

        //	If the data connection is active close it
        if (m_activeSock != null) {
            try {
                m_activeSock.close();
            }
            catch (Exception ex) {
            }
            m_activeSock = null;
        }

        //	Close the listening socket for a passive connection
        if (m_passiveSock != null) {
            try {
                m_passiveSock.close();
            }
            catch (Exception ex) {
            }
            m_passiveSock = null;
        }
    }

    /**
     * Run a file send/receive in a seperate thread
     */
    public void run() {
    }
}
