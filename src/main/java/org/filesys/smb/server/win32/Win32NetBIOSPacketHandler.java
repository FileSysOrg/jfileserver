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

package org.filesys.smb.server.win32;

import java.io.IOException;

import org.filesys.debug.Debug;
import org.filesys.netbios.win32.NetBIOS;
import org.filesys.netbios.win32.Win32NetBIOS;
import org.filesys.smb.server.SMBPacketPool;
import org.filesys.smb.server.PacketHandler;
import org.filesys.smb.server.Protocol;
import org.filesys.smb.server.SMBSrvPacket;

/**
 * Win32 NetBIOS Packet Handler Class
 *
 * <p>
 * Uses the Win32 Netbios() call to provide the low level session layer for better integration with
 * Windows.
 *
 * @author gkspencer
 */
public class Win32NetBIOSPacketHandler extends PacketHandler {

    // Constants
    //
    // Receive error encoding and length masks
    private static final int ReceiveErrorMask = 0xFF000000;
    private static final int ReceiveLengthMask = 0x0000FFFF;

    // Network LAN adapter to use
    private int m_lana;

    // NetBIOS session id
    private int m_lsn;

    /**
     * Class constructor
     *
     * @param lana       int
     * @param lsn        int
     * @param callerName String
     * @param packetPool CIFSPacketPool
     */
    public Win32NetBIOSPacketHandler(int lana, int lsn, String callerName, SMBPacketPool packetPool) {
        super(Protocol.Win32NetBIOS, "Win32NB", "WNB", callerName, packetPool);

        m_lana = lana;
        m_lsn = lsn;
    }

    /**
     * Return the LANA number
     *
     * @return int
     */
    public final int getLANA() {
        return m_lana;
    }

    /**
     * Return the NetBIOS session id
     *
     * @return int
     */
    public final int getLSN() {
        return m_lsn;
    }

    /**
     * Return the count of available bytes in the receive input stream
     *
     * @return int
     * @throws IOException If a network error occurs.
     */
    public int availableBytes()
            throws IOException {

        // Do not know the available byte count
        return -1;
    }

    /**
     * Read a packet from the client
     *
     * @return SMBSrvPacket
     * @exception IOException Network error occurred
     */
    public SMBSrvPacket readPacket()
            throws IOException {

        // As we cannot find the length of the incoming packet we must allocate a full length packet
        SMBSrvPacket pkt = getPacketPool().allocatePacket(getPacketPool().getLargestSize());

        try {
            // Wait for a packet on the Win32 NetBIOS session
            //
            // As Windows is handling the NetBIOS session layer we only receive the SMB packet. In order
            // to be compatible with the other packet handlers we allow for the 4 byte header.
            int pktLen = pkt.getBuffer().length;
            if (pktLen > NetBIOS.MaxReceiveSize)
                pktLen = NetBIOS.MaxReceiveSize;

            int rxLen = Win32NetBIOS.Receive(m_lana, m_lsn, pkt.getBuffer(), 4, pktLen - 4);

            if ((rxLen & ReceiveErrorMask) != 0) {

                // Check for an incomplete message status code
                int sts = (rxLen & ReceiveErrorMask) >> 24;

                if (sts == NetBIOS.NRC_Incomp) {

                    // DEBUG
                    if (hasDebug())
                        Debug.println("Win32NetBIOSPacketHandle: readPacket() NRC_Incomp error");

                    // Check if the packet buffer is already at the maximum size (we assume the maximum
                    // size is the maximum that RFC NetBIOS can send which is 17bits)
                    if (pkt.getBuffer().length < getPacketPool().getMaximumOverSizedAllocation()) {

                        // Allocate a new buffer
                        SMBSrvPacket pkt2 = getPacketPool().allocatePacket(getPacketPool().getMaximumOverSizedAllocation());

                        // Copy the first part of the received data to the new buffer
                        System.arraycopy(pkt.getBuffer(), 4, pkt2.getBuffer(), 4, pktLen - 4);

                        // Move the new buffer in as the main packet buffer, release the original buffer
                        getPacketPool().releasePacket(pkt);
                        pkt = pkt2;

                        // DEBUG
                        if (hasDebug())
                            Debug.println("readPacket() extended buffer to " + pkt.getBuffer().length);
                    }

                    // Set the original receive size
                    rxLen = (rxLen & ReceiveLengthMask);

                    // Receive the remaining data
                    //
                    // Note: If the second read request is issued with a size of 64K or 64K-4 it returns
                    // with another incomplete status and returns no data.
                    int rxLen2 = Win32NetBIOS.Receive(m_lana, m_lsn, pkt.getBuffer(), rxLen + 4, 32768);

                    if ((rxLen2 & ReceiveErrorMask) != 0) {
                        sts = (rxLen2 & ReceiveErrorMask) >> 24;
                        throw new IOException("Win32 NetBIOS multi-part receive failed, sts=0x" + sts + ", err="
                                + NetBIOS.getErrorString(sts));
                    }

                    // DEBUG
                    if (hasDebug())
                        Debug.println("readPacket() rxlen2=" + rxLen2 + ", total read len = " + (rxLen + rxLen2));

                    // Set the total received data length
                    rxLen += rxLen2;
                }
                else {

                    // Indicate that the session has closed
                    throw new IOException(NetBIOS.getErrorString(sts));
                }
            }

            // Set the received packet length
            if (pkt != null)
                pkt.setReceivedLength(rxLen);
        }
        catch (Throwable t) {
            getPacketPool().releasePacket(pkt);
            rethrowException(t);
        }

        // Return the received packet
        return pkt;
    }

    /**
     * Write a packet to the client
     *
     * @param pkt      SMBSrvPacket
     * @param len      int
     * @param writeRaw boolean
     * @exception IOException Network error occurred
     */
    public void writePacket(SMBSrvPacket pkt, int len, boolean writeRaw)
            throws IOException {

        // Output the packet on the Win32 NetBIOS session
        //
        // As Windows is handling the NetBIOS session layer we do not send the 4 byte header that is
        // used by the NetBIOS over TCP/IP and native SMB packet handlers.
        Win32NetBIOS.Send(m_lana, m_lsn, pkt.getBuffer(), 4, len);

        // Do not check the status, if the session has been closed the next receive will fail
    }

    /**
     * Flush the output socket
     *
     * @throws IOException If a network error occurs
     */
    public void flushPacket()
            throws IOException {

        // Nothing to do
    }

    /**
     * Close the Win32 NetBIOS packet handler. Hangup the NetBIOS session
     */
    public void closeHandler() {
        super.closeHandler();

        // Hangup the Win32 NetBIOS session
        Win32NetBIOS.Hangup(m_lana, m_lsn);
    }
}
