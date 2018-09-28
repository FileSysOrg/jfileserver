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

package org.filesys.client;

import java.io.*;
import java.net.SocketTimeoutException;

import org.filesys.client.info.FileInfo;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.smb.*;
import org.filesys.smb.PacketTypeV1;

/**
 * SMB Data Pipe File Class
 *
 * <p>An SMB file provides read and/or write access to a remote named pipe.
 *
 * @author gkspencer
 */
public final class DataPipeFile extends SMBFile {

    //	Size of protocol packet to allocate
    private static final int DataSize = 4000;
    private static final int PacketSize = DataSize + 128;

    //	Offset that the write data is placed within the write SMB packet, including protocol header of 4 bytes, and
    //	padding byte count to word align the data
    private static final int WriteDataOffset = 64;
    private static final int WriteDataPadding = 1;

    //	Maximum file offset for 32bit files
    private static final long Maximum32BitOffset = 0x0FFFFFFFFL;

    // State flag to indicate that the pipe is broken
    private static final int PipeBroken = 0x1000;

    //  No timeout for read
    public static final int NoTimeout = 0;

    //	Flag to indicate we are using NT dialect SMBs
    private boolean m_NTdialect;

    /**
     * Class constructor
     *
     * @param sess  Session that this file is associated with
     * @param finfo File information for the new file
     * @param fid   File identifier for this file
     */
    protected DataPipeFile(Session sess, FileInfo finfo, int fid) {
        super(sess, finfo, fid);

        // Set the NT dialect flag
        if (sess.getDialect() == Dialect.NT)
            m_NTdialect = true;
        else
            m_NTdialect = false;
    }

    /**
     * Close the remote file.
     *
     * @param wrDateTime Set the last write date/time, or null to let the server set the date/time
     * @throws java.io.IOException If an I/O error occurs
     * @throws SMBException        If an SMB level error occurs
     */
    public final void Close(SMBDate wrDateTime)
            throws java.io.IOException, SMBException {

        // Flush any buffered write data
        if (m_txlen > 0)
            Flush();

        // Determine which packet to use to send the close file SMB
        SMBPacket pkt = new SMBPacket();
        pkt.setUserId(m_sess.getUserId());
        pkt.setTreeId(m_sess.getTreeId());

        // Close the remote file.
        pkt.setCommand(PacketTypeV1.CloseFile);

        pkt.setParameterCount(3);
        pkt.setParameter(0, m_FID);

        // Do not set a modified date/time
        pkt.setParameter(1, 0);
        pkt.setParameter(2, 0);

        // Indicate that the file has been closed
        this.setStateFlag(Closed, true);

        // Exchange the close file SMB packet with the file server
        try {
            pkt.ExchangeSMB(m_sess, pkt);
        }
        catch (java.io.IOException ex) {
            return;
        }

        // Release the transmit/receive packets
        m_rxpkt = null;
        m_txpkt = null;

        // Close the associated session
        getSession().CloseSession();
    }

    /**
     * Flush data to the remote file.
     *
     * @throws java.io.IOException If an I/O error occurs
     * @throws SMBException        If an SMB level error occurs
     */
    public final void Flush()
            throws java.io.IOException, SMBException {

        // Check if there is any buffered write data
        if (m_txlen > 0)
            WriteData();
    }

    /**
     * Read a block of data from the file.
     *
     * @param buf    Byte buffer to receive the data.
     * @param siz    Maximum length of data to receive.
     * @param offset Offset within buffer to place received data.
     * @return Actual length of data received.
     * @throws java.io.IOException If an I/O error occurs
     * @throws SMBException        If an SMB level error occurs
     */
    public final int Read(byte[] buf, int siz, int offset)
            throws java.io.IOException, SMBException {

        // Read from the pipe
        return Read(buf, siz, offset, NoTimeout, siz);
    }

    /**
     * Read a block of data from the file.
     *
     * @param buf    Byte buffer to receive the data.
     * @param siz    Maximum length of data to receive.
     * @param offset Offset within buffer to place received data.
     * @param minSiz Minimum read size, zero will return immediately if there is no data
     * @return Actual length of data received.
     * @throws java.io.IOException If an I/O error occurs
     * @throws SMBException        If an SMB level error occurs
     */
    public final int Read(byte[] buf, int siz, int offset, int minSiz)
            throws java.io.IOException, SMBException {

        // Read from the pipe
        return Read(buf, siz, offset, NoTimeout, minSiz);
    }

    /**
     * Read a block of data from the file.
     *
     * @param buf    Byte buffer to receive the data.
     * @param siz    Maximum length of data to receive.
     * @param offset Offset within buffer to place received data.
     * @param tmo    Read timeout
     * @param minSiz Mimimum read size, zero will return immediately if there is no data
     * @return Actual length of data received.
     * @throws java.io.IOException If an I/O error occurs
     * @throws SMBException        If an SMB level error occurs
     */
    private final int Read(byte[] buf, int siz, int offset, int tmo, int minSiz)
            throws java.io.IOException, SMBException {

        // Check if the file has been closed
        if (this.isClosed())
            return -1;

        // Check if there is any data buffered
        if (m_rxlen == 0) {

            // Read a packet of data from the remote pipe
            if (ReadData(tmo, minSiz) == false)
                return -1;

            // Check if there is any buffered data
            if (m_rxlen == 0)
                return 0;
        }

        // Copy data to the users buffer
        int retlen = m_rxlen;
        if (retlen > siz)
            retlen = siz;
        byte[] pktbuf = m_rxpkt.getBuffer();
        System.arraycopy(pktbuf, m_rxoffset, buf, offset, retlen);

        // Update the buffered data offset/length
        m_rxlen -= retlen;
        m_rxoffset += retlen;

        // Return the amount of data read
        return retlen;
    }

    /**
     * Read a packet of data from the remote file.
     *
     * @param tmo    Read timeout, zero for no timeout
     * @param minSiz Minimum read size, zero will return imeediately if there is no data
     * @return true if a valid data packet has been received, else false
     * @throws SMBException If an SMB level error occurs
     * @throws IOException  If an I/O error occurs
     */
    private final boolean ReadData(int tmo, int minSiz)
            throws SMBException, IOException {

        // Check if the file offset requires large file support (64bit offsets)
        if (isNTDialect() == false && m_rxpos > Maximum32BitOffset)
            throw new SMBException(SMBStatus.InternalErr, SMBStatus.IntLargeFilesNotSupported);

        // If the pipe is broken then return an error
        if (isPipeBroken())
            throw new SMBException(SMBStatus.NTErr, SMBStatus.NTPipeBroken);

        // Allocate and initialize a receive packet, if not already allocated
        if (m_rxpkt == null) {

            // Allocate a receive packet
            m_rxpkt = m_sess.allocatePacket(PacketSize);

            // Initialize the packet
            m_rxpkt.setUserId(m_sess.getUserId());
            m_rxpkt.setTreeId(m_sess.getTreeId());
        }

        // Read a packet of data from the remote file
        m_rxpkt.setCommand(PacketTypeV1.ReadAndX);
        m_rxpkt.setParameterCount(isNTDialect() ? 12 : 10);
        m_rxpkt.setAndXCommand(PacketTypeV1.NoChainedCommand);
        m_rxpkt.setFlags(m_sess.getDefaultFlags());
        m_rxpkt.setFlags2(m_sess.getDefaultFlags2());
        m_rxpkt.setProcessId(m_sess.getProcessId());

        // Set the file id and read offset
        m_rxpkt.setParameter(2, getFileId());
        m_rxpkt.setParameterLong(3, (int) (m_rxpos & 0xFFFFFFFF));

        // Set the maximum read size
        int maxCount = m_rxpkt.getBuffer().length - 64;
        m_rxpkt.setParameter(5, maxCount);

        // Set the minimum read size
        m_rxpkt.setParameter(6, minSiz);

        // Read timeout, zero for no timeout
        m_rxpkt.setParameterLong(7, tmo);

        // Bytes remaining to satisfy request
        m_rxpkt.setParameter(9, maxCount);

        // Set the top 32bits of the file offset for NT dialect
        if (isNTDialect())
            m_rxpkt.setParameterLong(10, (int) ((m_rxpos >> 32) & 0x0FFFFFFFFL));

        // No byte data
        m_rxpkt.setByteCount(0);

        // Exchange the read data SMB packet with the file server
        try {
            m_rxpkt.ExchangeSMB(m_sess, m_rxpkt, true);
        }
        catch (SocketTimeoutException ex) {
            return false;
        }
        catch (SMBException ex) {

            // Check for a pipe empty status
            if (ex.getErrorClass() == SMBStatus.NTErr && ex.getErrorCode() == SMBStatus.NTPipeEmpty) {

                // Indicate success, but no data received so receive length will still be zero
                return true;
            } else if (ex.getErrorClass() == SMBStatus.NTErr
                    && (ex.getErrorCode() == SMBStatus.NTPipeBroken || ex.getErrorCode() == SMBStatus.NTPipeDisconnected)) {

                // Indicate end of file, and set the pipe broken state
                setStateFlag(EndOfFile, true);
                setStateFlag(PipeBroken, true);
                return false;
            } else {

                // Rethrow the exception
                throw ex;
            }
        }

        // Check if a valid response was received
        if (m_rxpkt.isValidResponse()) {

            // Set the received data length and offset within the received data
            m_rxlen = m_rxpkt.getParameter(5);
            m_rxoffset = m_rxpkt.getParameter(6) + RFCNetBIOSProtocol.HEADER_LEN;

            // Update the current receive file position
            m_rxpos += m_rxlen;

            // Check if we have reached the end of file, indicated by a zero length read
            // if (m_rxlen == 0)
            // setStateFlag(SMBFile.EndOfFile, true);
            return true;
        }

        // Return a failure status
        return false;
    }

    /**
     * Write a block of data to the file.
     *
     * @param buf    Byte buffer containing data to be written.
     * @param siz    Length of data to be written.
     * @param offset Offset within buffer to start writing data from.
     * @return Actual length of data written.
     * @throws java.io.IOException If an I/O error occurs
     * @throws SMBException        If an SMB level error occurs
     */
    public final int Write(byte[] buf, int siz, int offset)
            throws java.io.IOException, SMBException {

        // Check if the file has been closed
        if (this.isClosed())
            return 0;

        // Allocate and initialize a transmit packet, if not already allocated
        if (m_txpkt == null) {

            // Allocate a transmit packet
            m_txpkt = m_sess.allocatePacket(PacketSize);

            // Initialize the packet
            m_txpkt.setUserId(m_sess.getUserId());
            m_txpkt.setTreeId(m_sess.getTreeId());
            m_txpkt.setProcessId(m_sess.getProcessId());

            // Set the write SMB parameter count now, so that we can calculate the
            // offset of the byte buffer within the packet.
            m_txpkt.setParameterCount(isNTDialect() ? 14 : 12);

            // Clear the write packet length and initialize the write packet offset.
            m_txlen = 0;
            m_txoffset = WriteDataOffset;

            if (isNTDialect())
                m_txoffset += 4;
        }

        // Move the data to the write packet and send write requests until the
        // user write has been done.
        int txlen = 0;

        while (txlen < siz) {

            // Determine if the current write request can be buffered in full
            byte[] pktbuf = m_txpkt.getBuffer();
            int len = pktbuf.length - m_txoffset;
            if (len > (siz - txlen))
                len = siz - txlen;

            // Move the user data to the write packet
            System.arraycopy(buf, offset, pktbuf, m_txoffset, len);
            m_txoffset += len;
            offset += len;

            // Update the written data length
            txlen += len;
            m_txlen += len;

            // Write the data immediately for pipes
            WriteData();

        } // end while writing

        // Return the length of the data that was written
        return txlen;
    }

    /**
     * Write a packet of data to the remote file.
     *
     * @return true if the write was successful, else false
     * @throws SMBException If an SMB level error occurs
     * @throws IOException  If an I/O error occurs
     */
    private final boolean WriteData()
            throws SMBException, IOException {

        // Check if the file offset requires large file support (64bit offsets)
        if (isNTDialect() == false && m_txpos > Maximum32BitOffset)
            throw new SMBException(SMBStatus.InternalErr, SMBStatus.IntLargeFilesNotSupported);

        // Write a packet of data to the remote file
        m_txpkt.setCommand(PacketTypeV1.WriteAndX);
        m_txpkt.setAndXCommand(PacketTypeV1.NoChainedCommand);

        m_txpkt.setFlags(m_sess.getDefaultFlags());
        m_txpkt.setFlags2(m_sess.getDefaultFlags2());

        m_txpkt.setParameterCount(isNTDialect() ? 14 : 12);

        // Set the file id and file offset
        m_txpkt.setParameter(2, getFileId());
        m_txpkt.setParameterLong(3, (int) (m_txpos & 0xFFFFFFFF));

        m_txpkt.setParameterLong(5, 0);

        // Set the write mode
        m_txpkt.setParameter(7, 0x0008); // pipe message start

        // Set the bytes remaining for request and reserved area
        m_txpkt.setParameter(8, m_txlen);
        m_txpkt.setParameter(9, 0);

        // Set the data length and offset from start of packet
        m_txpkt.setParameter(10, m_txlen);

        int offset = WriteDataOffset - RFCNetBIOSProtocol.HEADER_LEN;
        if (isNTDialect())
            offset += 4;
        m_txpkt.setParameter(11, offset);

        // Add the top 32bits of the file offset, for NT dialect. Set the byte count, includes any
        // padding bytes
        if (isNTDialect()) {

            // Set the top 32bits of the file offset
            m_txpkt.setParameterLong(12, (int) ((m_txpos >> 32) & 0xFFFFFFFFL));
        }

        // Set the byte count, includes any padding bytes
        m_txpkt.setByteCount(m_txlen + WriteDataPadding);

        // Exchange the write data SMB packet with the file server
        m_txpkt.ExchangeSMB(m_sess, m_txpkt, false);

        // Check if a valid response was received
        if (m_txpkt.isValidResponse()) {

            // Set the write data length
            int txlen = m_txpkt.getParameter(2);

            // Update the current write file position
            m_txpos += txlen;

            // Reset the write packet and write length
            m_txlen = 0;
            m_txoffset = WriteDataOffset;

            if (isNTDialect())
                m_txoffset += 4;
            return true;
        } else {

            // Reset the write packet and write length
            m_txlen = 0;
            m_txoffset = WriteDataOffset;

            if (isNTDialect())
                m_txoffset += 4;

            // Throw an exception
            m_txpkt.checkForError();
        }

        // Return a failure status
        return false;
    }

    /**
     * Seek to the specified point in the file. The seek may be relative to the start of file,
     * current file position or end of file.
     *
     * @param pos Relative offset
     * @param typ Seek type (@see SeekType)
     * @return New file offset from start of file
     * @exception IOException Socket error
     * @exception SMBException SMB error
     */
    public long Seek(long pos, int typ)
            throws IOException, SMBException {

        // Check if the file has been closed
        if (this.isClosed())
            throw new IOException("Seek on closed file");

        // Flush any buffered data
        Flush();

        // Reset the read/write offsets to the new file position
        switch (typ) {

            // Seek relative to the start of file
            case SeekType.StartOfFile:
                m_txpos = m_rxpos = pos;
                break;

            // Seek realtive to the current file position
            case SeekType.CurrentPos:
                m_txpos = m_rxpos + pos;
                m_rxpos = m_txpos;
                break;

            // Seek relative to end of file
            case SeekType.EndOfFile:
                m_txpos = m_rxpos = getFileSize() + pos;
                break;
        }

        // Indicate that there is no buffered data
        m_txlen = 0;
        m_rxlen = 0;

        // Return the new file offset
        return m_rxpos;
    }

    /**
     * Lock a range of bytes within the file
     *
     * @param offset Offset within the file to start lock
     * @param len    Number of bytes to lock
     * @exception IOException Socket error
     * @exception SMBException SMB error
     */
    public void Lock(long offset, long len)
            throws IOException, SMBException {

        // Check if the file offset requires large file support (64bit offsets)
        if (isNTDialect() == false && offset > Maximum32BitOffset)
            throw new SMBException(SMBStatus.InternalErr, SMBStatus.IntLargeFilesNotSupported);

        // Create the lock request packet
        SMBPacket pkt = new SMBPacket();
        pkt.setUserId(m_sess.getUserId());
        pkt.setTreeId(m_sess.getTreeId());

        pkt.setCommand(PacketTypeV1.LockingAndX);
        pkt.setProcessId(m_sess.getProcessId());

        // Set the parameters
        pkt.setParameterCount(8);
        pkt.setAndXCommand(PacketTypeV1.NoChainedCommand);
        pkt.setParameter(2, m_FID);
        pkt.setParameter(3, m_sess.supportsLargeFiles() ? LockingAndX.LargeFiles : 0);
        pkt.setParameterLong(4, 0); // timeout, for unlock
        pkt.setParameter(6, 0); // number of unlock structures
        pkt.setParameter(7, 1); // number of lock structures

        // Pack the lock structure
        pkt.resetBytePointer();

        if (m_sess.supportsLargeFiles()) {

            // Pack a large file (64bit) format structure
            pkt.packWord(m_sess.getProcessId());
            pkt.packWord(0);

            pkt.packInt((int) ((offset >> 32) & 0xFFFFFFFFL));
            pkt.packInt((int) (offset & 0xFFFFFFFFL));

            pkt.packInt((int) ((len >> 32) & 0xFFFFFFFFL));
            pkt.packInt((int) (len & 0xFFFFFFFFL));
        } else {

            // Pack a normal (32bit) format structure
            pkt.packWord(m_sess.getProcessId());
            pkt.packInt((int) (offset & 0xFFFFFFFFL));
            pkt.packInt((int) (len & 0xFFFFFFFFL));
        }

        // Set the byte count
        pkt.setByteCount();

        // Send the lock request
        pkt.ExchangeSMB(m_sess, pkt, true);
    }

    /**
     * Unlock a range of bytes within the file
     *
     * @param offset Offset within the file to unlock
     * @param len    Number of bytes to unlock
     * @exception IOException Socket error
     * @exception SMBException SMB error
     */
    public void Unlock(long offset, long len)
            throws IOException, SMBException {

        // Check if the file offset requires large file support (64bit offsets)
        if (isNTDialect() == false && offset > Maximum32BitOffset)
            throw new SMBException(SMBStatus.InternalErr, SMBStatus.IntLargeFilesNotSupported);

        // Create the unlock request packet
        SMBPacket pkt = new SMBPacket();
        pkt.setUserId(m_sess.getUserId());
        pkt.setTreeId(m_sess.getTreeId());

        pkt.setCommand(PacketTypeV1.LockingAndX);
        pkt.setProcessId(m_sess.getProcessId());

        // Set the parameters
        pkt.setParameterCount(8);
        pkt.setAndXCommand(PacketTypeV1.NoChainedCommand);
        pkt.setParameter(2, m_FID);
        pkt.setParameter(3, m_sess.supportsLargeFiles() ? LockingAndX.LargeFiles : 0);
        pkt.setParameterLong(4, 0); // timeout, for unlock
        pkt.setParameter(6, 1); // number of unlock structures
        pkt.setParameter(7, 0); // number of lock structures

        // Pack the unlock structure
        pkt.resetBytePointer();

        if (m_sess.supportsLargeFiles()) {

            // Pack a large file (64bit) format structure
            pkt.packWord(m_sess.getProcessId());
            pkt.packWord(0);

            pkt.packInt((int) ((offset >> 32) & 0xFFFFFFFFL));
            pkt.packInt((int) (offset & 0xFFFFFFFFL));

            pkt.packInt((int) ((len >> 32) & 0xFFFFFFFFL));
            pkt.packInt((int) (len & 0xFFFFFFFFL));
        } else {

            // Pack a normal (32bit) format structure
            pkt.packWord(m_sess.getProcessId());
            pkt.packInt((int) (offset & 0xFFFFFFFFL));
            pkt.packInt((int) (len & 0xFFFFFFFFL));
        }

        // Set the byte count
        pkt.setByteCount();

        // Send the unlock request
        pkt.ExchangeSMB(m_sess, pkt, true);
    }

    /**
     * Check if NT dialect SMBs should be used
     *
     * @return boolean
     */
    protected final boolean isNTDialect() {
        return m_NTdialect;
    }

    /**
     * Check if the pipe is broken
     *
     * @return boolean
     */
    public final boolean isPipeBroken() {
        return hasStateFlag(PipeBroken);
    }
}