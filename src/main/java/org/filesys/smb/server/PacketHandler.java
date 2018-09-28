/*
 * Copyright (C) 2006-2011 Alfresco Software Limited.
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

import java.io.IOException;
import java.net.InetAddress;

import org.filesys.debug.Debug;

/**
 * Protocol Packet Handler Class
 *
 * @author gkspencer
 */
public abstract class PacketHandler {

    // Protocol type and name
    private Protocol m_protoType;
    private String m_protoName;
    private String m_shortName;

    // Client caller name and remote address
    private String m_clientName;
    private InetAddress m_remoteAddr;

    // Packet pool for allocating packet to incoming requests
    private SMBPacketPool m_packetPool;

    // Encrypted packet overhead, added to allocation size
    private int m_encryptedOverhead;

    // Debug output enable
    private boolean m_debug;

    /**
     * Class constructor
     *
     * @param typ        Protocol
     * @param name       String
     * @param shortName  String
     * @param packetPool SMBPacketPool
     * @throws IOException If a network error occurs
     */
    public PacketHandler(Protocol typ, String name, String shortName, SMBPacketPool packetPool) throws IOException {

        m_protoType = typ;
        m_protoName = name;
        m_shortName = shortName;

        m_packetPool = packetPool;
    }

    /**
     * Class constructor
     *
     * @param typ       Protocol
     * @param name      String
     * @param shortName String
     * @param clientName String
     * @param packetPool SMBPacketPool
     */
    public PacketHandler(Protocol typ, String name, String shortName, String clientName, SMBPacketPool packetPool) {
        m_protoType = typ;
        m_protoName = name;
        m_shortName = shortName;

        m_clientName = clientName;

        m_packetPool = packetPool;
    }

    /**
     * Return the protocol type
     *
     * @return Protocol
     */
    public final Protocol isProtocol() {
        return m_protoType;
    }

    /**
     * Return the protocol name
     *
     * @return String
     */
    public final String isProtocolName() {
        return m_protoName;
    }

    /**
     * Return the short protocol name
     *
     * @return String
     */
    public final String getShortName() {
        return m_shortName;
    }

    /**
     * Check if there is a remote address available
     *
     * @return boolean
     */
    public final boolean hasRemoteAddress() {
        return m_remoteAddr != null ? true : false;
    }

    /**
     * Return the remote address for the connection
     *
     * @return InetAddress
     */
    public final InetAddress getRemoteAddress() {
        return m_remoteAddr;
    }

    /**
     * Determine if the client name is available
     *
     * @return boolean
     */
    public final boolean hasClientName() {
        return m_clientName != null ? true : false;
    }

    /**
     * Return the client name
     *
     * @return String
     */
    public final String getClientName() {
        return m_clientName;
    }

    /**
     * Return the packet pool
     *
     * @return SMBPacketPool
     */
    public final SMBPacketPool getPacketPool() {
        return m_packetPool;
    }

    /**
     * Get the encrypted packet overhead, only non-zero if encryption is being used
     *
     * @return int
     */
    public final int getEncryptionOverhead() {
        return m_encryptedOverhead;
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
     * Set/clear the debug enable flag
     *
     * @param ena boolean
     */
    public final void setDebug(boolean ena) {
        m_debug = ena;
    }

    /**
     * Set the encryption packet overhead, added to the allocation size
     *
     * @param encOverhead int
     */
    public final void setEncryptionOverhead(int encOverhead) {
        m_encryptedOverhead = encOverhead;
    }

    /**
     * Return the count of available bytes in the receive input stream
     *
     * @return int
     * @throws IOException If a network error occurs.
     */
    public abstract int availableBytes()
            throws IOException;

    /**
     * Read a packet
     *
     * @return SMBSrvPacket
     * @throws IOException If a network error occurs.
     */
    public abstract SMBSrvPacket readPacket()
            throws IOException;

    /**
     * Send an SMB response packet
     *
     * @param pkt      SMBSrvPacket
     * @param len      int
     * @param writeRaw boolean
     * @throws IOException If a network error occurs.
     */
    public abstract void writePacket(SMBSrvPacket pkt, int len, boolean writeRaw)
            throws IOException;

    /**
     * Send an SMB response packet
     *
     * @param pkt SMBSrvPacket
     * @param len int
     * @throws IOException If a network error occurs.
     */
    public final void writePacket(SMBSrvPacket pkt, int len)
            throws IOException {
        writePacket(pkt, len, false);
    }

    /**
     * Send an SMB response packet
     *
     * @param pkt SMBSrvPacket
     * @throws IOException If a network error occurs.
     */
    public final void writePacket(SMBSrvPacket pkt)
            throws IOException {
        writePacket(pkt, pkt.getLength());
    }

    /**
     * Flush the output socket
     *
     * @throws IOException If a network error occurs
     */
    public abstract void flushPacket()
            throws IOException;

    /**
     * Close the protocol handler
     */
    public void closeHandler() {
    }

    /**
     * Rethrow an exception, allow for logging
     *
     * @param t Throwable
     * @throws IOException If a network error occurs
     */
    protected void rethrowException(Throwable t) throws IOException {
        if (Debug.EnableDbg && hasDebug())
            Debug.println(t);

        // Most method signatures allow IOExceptions so let these pass straight through
        if (t instanceof IOException)
            throw (IOException) t;

            // Allow unchecked exceptions straight through
        else if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        else if (t instanceof Error)
            throw (Error) t;

            // We have to wrap any other exceptions as unchecked
        else
            throw new RuntimeException(t);
    }

    /**
     * Set the client name
     *
     * @param name String
     */
    protected final void setClientName(String name) {
        m_clientName = name;
    }

    /**
     * Set the remote address
     *
     * @param addr InetAddress
     */
    protected final void setRemoteAddress(InetAddress addr) {
        m_remoteAddr = addr;
    }
}
