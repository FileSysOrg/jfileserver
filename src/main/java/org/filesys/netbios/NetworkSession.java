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

package org.filesys.netbios;

import java.io.IOException;
import java.net.UnknownHostException;

/**
 * Network Session Abstract Class
 *
 * <p>Base class for client network sessions.
 *
 * @author gkspencer
 */
public abstract class NetworkSession {

    //  Default socket timeout value
    private static int _defTimeout = 30000;   //  30 seconds, in milliseconds

    // Protocol name
    private String m_protoName;

    // Session send/receive timeout, in milliseconds
    private int m_tmo = _defTimeout;

    /**
     * Class constructor
     *
     * @param protoName String
     */
    public NetworkSession(String protoName) {
        m_protoName = protoName;
    }

    /**
     * Return the protocol name
     *
     * @return String
     */
    public final String getProtocolName() {
        return m_protoName;
    }

    /**
     * Open a connection to a remote host
     *
     * @param toName   Host name/address being called
     * @param fromName Local host name/address
     * @param toAddr   Optional address of the remote host
     * @exception UnknownHostException Host name lookup failed
     * @exception IOException Socket error
     */
    public abstract void Open(String toName, String fromName, String toAddr)
            throws IOException, UnknownHostException;

    /**
     * Determine if the session is connected to a remote host
     *
     * @return boolean
     */
    public abstract boolean isConnected();

    /**
     * Check if the network session has data available
     *
     * @return boolean
     * @exception IOException Socket error
     */
    public abstract boolean hasData()
            throws IOException;

    /**
     * Return the send/receive timeout, in milliseconds
     *
     * @return int
     */
    public final int getTimeout() {
        return m_tmo;
    }

    /**
     * Receive a data packet from the remote host.
     *
     * @param buf Byte buffer to receive the data into.
     * @return Length of the received data.
     * @throws java.io.IOException I/O error occurred.
     */
    public abstract int Receive(byte[] buf)
            throws IOException;

    /**
     * Send a data packet to the remote host.
     *
     * @param data Byte array containing the data to be sent.
     * @param siz  Length of the data to send.
     * @return true if the data was sent successfully, else false.
     * @throws java.io.IOException I/O error occurred.
     */
    public abstract boolean Send(byte[] data, int siz)
            throws IOException;

    /**
     * Close the network session
     *
     * @throws java.io.IOException I/O error occurred
     */
    public abstract void Close()
            throws IOException;

    /**
     * Set the send/receive timeout, in milliseconds
     *
     * @param tmo int
     */
    public void setTimeout(int tmo) {
        m_tmo = tmo;
    }

    /**
     * Return the default socket timeout value
     *
     * @return int
     */
    public static final int getDefaultTimeout() {
        return _defTimeout;
    }

    /**
     * Set the default socket timeout for new sessions
     *
     * @param tmo int
     */
    public static final void setDefaultTimeout(int tmo) {
        _defTimeout = tmo;
    }
}
