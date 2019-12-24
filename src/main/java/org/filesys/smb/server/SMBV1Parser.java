/*
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.smb.server;

import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.server.auth.AuthenticatorException;
import org.filesys.server.auth.ChallengeAuthContext;
import org.filesys.server.auth.ISMBAuthenticator;
import org.filesys.server.locking.OpLockDetailsAdapter;
import org.filesys.smb.*;
import org.filesys.smb.dcerpc.UUID;
import org.filesys.util.DataPacker;
import org.filesys.util.HexDump;

/**
 * SMB V1 Parser Class
 *
 * @author gkspencer
 */
public class SMBV1Parser extends SMBParser {

    // SMB V1 packet signature
    private static final byte[] SMB_V1_SIGNATURE = { (byte) 0xFF, 'S', 'M', 'B'};

    // NT transactions
    //
    // Define the number of standard parameter words/bytes
    private static final int StandardParams = 19;
    private static final int ParameterBytes = 36;   // 8 x 32bit params + max setup count byte +
                                                    // setup count byte + reserved word

    // Standard reply word count
    private static final int ReplyParams = 18;

    // Offset to start of NT parameters from start of packet
    private static final int NTMaxSetupCount    = SMBPacket.PARAMWORDS;
    private static final int NTParams           = SMBPacket.PARAMWORDS + 3;
    private static final int NTSetupCount       = NTParams + 32;
    private static final int NTFunction         = NTSetupCount + 1;

    // Default return parameter/data byte counts
    private static final int DefaultReturnParams    = 4;
    private static final int DefaultReturnData      = 1024;

    /**
     * Class constructor
     *
     * @param buf byte[]
     * @param len int
     */
    public SMBV1Parser( byte[] buf, int len) {
        super( buf, len);
    }

    /**
     * Return the SMB parser name
     *
     * @return String
     */
    public String getName() {
        return "SMBv1";
    }

    /**
     * Return the parser type, this is an SMB v1 parser
     *
     * @return SMBSrvPacket.Version
     */
    public SMBSrvPacket.Version isType() {
        return SMBSrvPacket.Version.V1;
    }

    /**
     * Return the parser details as a short string
     *
     * @return String
     */
    public String toShortString() {

        StringBuffer str = new StringBuffer();

        str.append("[V1 cmd=");
        str.append(PacketTypeV1.getCommandName( getCommand()));
        str.append("]");

        return str.toString();
    }

    /**
     * Check if the received SMB packet is valid
     *
     * @param reqWords Minimum number of parameter words expected.
     * @param reqBytes Minimum number of bytes expected.
     * @return boolean True if the packet passes the checks, else false.
     */
    public boolean checkPacketIsValid(int reqWords, int reqBytes) {

        // Check for the SMB signature block
        for ( int idx = 0; idx < SMB_V1_SIGNATURE.length; idx++) {
            if ( m_smbbuf[idx + SMBV1.SIGNATURE] != SMB_V1_SIGNATURE[ idx])
                return false;
        }

        // Check the received parameter word count
        if (getParameterCount() < reqWords || getByteCount() < reqBytes)
            return false;

        return true;
    }

    /**
     * Pack a success response
     */
    public void packSuccessRespone() {
        setParameterCount( 0);
        setByteCount( 0);
    }

    /**
     * Set the packet to be a response
     */
    public void setResponse() {
        setRequestPacket( false);
    }

    /**
     * Clear the data byte count
     */
    public final void clearBytes() {
        int offset = getByteOffset() - 2;
        DataPacker.putIntelShort((short) 0, m_smbbuf, offset);
    }

    /**
     * Get the data byte count for the SMB AndX command.
     *
     * @param off Offset to the AndX command.
     * @return Data byte count
     */
    public final int getAndXByteCount(int off) {

        // Calculate the offset of the byte count
        int pos = off + 1 + (2 * getParameterCount());
        return (int) DataPacker.getIntelShort(m_smbbuf, pos);
    }

    /**
     * Get the AndX data byte area offset within the SMB packet
     *
     * @param off Offset to the AndX command.
     * @return Data byte offset within the SMB packet.
     */
    public final int getAndXByteOffset(int off) {

        // Calculate the offset of the byte buffer
        int pCnt = getAndXParameterCount(off);
        int pos = off + (2 * pCnt) + 3; // parameter words + parameter count byte + byte data length word
        return pos;
    }

    /**
     * Get the secondary command code
     *
     * @return Secondary command code
     */
    public final int getAndXCommand() {
        return (int) (m_smbbuf[SMBV1.ANDXCOMMAND] & 0xFF);
    }

    /**
     * Get an AndX parameter word from the SMB packet.
     *
     * @param off Offset to the AndX command.
     * @param idx Parameter index (zero based).
     * @return Parameter word value.
     * @exception java.lang.IndexOutOfBoundsException If the parameter index is out of range.
     */
    public final int getAndXParameter(int off, int idx)
            throws java.lang.IndexOutOfBoundsException {

        // Range check the parameter index
        if ( idx > getAndXParameterCount(off))
            throw new java.lang.IndexOutOfBoundsException();

        // Calculate the parameter word offset
        int pos = off + (2 * idx) + 1;
        return (int) (DataPacker.getIntelShort(m_smbbuf, pos) & 0xFFFF);
    }

    /**
     * Get an AndX parameter integer from the SMB packet.
     *
     * @param off Offset to the AndX command.
     * @param idx Parameter index (zero based).
     * @return Parameter integer value.
     * @exception java.lang.IndexOutOfBoundsException If the parameter index is out of range.
     */
    public final int getAndXParameterLong(int off, int idx)
            throws java.lang.IndexOutOfBoundsException {

        // Range check the parameter index
        if ( idx > getAndXParameterCount(off))
            throw new java.lang.IndexOutOfBoundsException();

        // Calculate the parameter word offset
        int pos = off + (2 * idx) + 1;
        return DataPacker.getIntelInt(m_smbbuf, pos);
    }

    /**
     * Get the AndX command parameter count.
     *
     * @param off Offset to the AndX command.
     * @return Parameter word count.
     */
    public final int getAndXParameterCount(int off) {
        return (int) m_smbbuf[off];
    }

    /**
     * Return the total buffer size available to the SMB request
     *
     * @return Total SMB buffer length available.
     */
    public final int getBufferLength() {
        return m_smbbuf.length - RFCNetBIOSProtocol.HEADER_LEN;
    }

    /**
     * Get the data byte count for the SMB packet
     *
     * @return Data byte count
     */
    public final int getByteCount() {

        // Calculate the offset of the byte count
        int pos = SMBV1.PARAMWORDS + (2 * getParameterCount());
        return (int) DataPacker.getIntelShort(m_smbbuf, pos);
    }

    /**
     * Get the data byte area offset within the SMB packet
     *
     * @return Data byte offset within the SMB packet.
     */
    public final int getByteOffset() {

        // Calculate the offset of the byte buffer
        return SMBV1.WORDCNT + (2 * getParameterCount()) + 3;
    }

    /**
     * Get the SMB command
     *
     * @return SMB command code.
     */
    public final int getCommand() {
        return (int) (m_smbbuf[SMBV1.COMMAND] & 0xFF);
    }

    /**
     * Get the SMB error class
     *
     * @return SMB error class.
     */
    public final int getErrorClass() {
        return (int) m_smbbuf[SMBV1.ERRORCLASS] & 0xFF;
    }

    /**
     * Get the SMB error code
     *
     * @return SMB error code.
     */
    public final int getErrorCode() {
        return (int) m_smbbuf[SMBV1.ERROR] & 0xFF;
    }

    /**
     * Get the SMB flags value.
     *
     * @return SMB flags value.
     */
    public final int getFlags() {
        return (int) m_smbbuf[SMBV1.FLAGS] & 0xFF;
    }

    /**
     * Get the SMB flags2 value.
     *
     * @return SMB flags2 value.
     */
    public final int getFlags2() {
        return (int) DataPacker.getIntelShort(m_smbbuf, SMBV1.FLAGS2);
    }

    /**
     * Return the NetBIOS header flags value.
     *
     * @return int
     */
    public final int getHeaderFlags() {
        return m_smbbuf[1] & 0x00FF;
    }

    /**
     * Return the NetBIOS header data length value.
     *
     * @return int
     */
    public final int getHeaderLength() {
        return DataPacker.getIntelShort(m_smbbuf, 2) & 0xFFFF;
    }

    /**
     * Return the NetBIOS header message type.
     *
     * @return int
     */
    public final int getHeaderType() {
        return m_smbbuf[0] & 0x00FF;
    }

    /**
     * Calculate the total used packet length.
     *
     * @return Total used packet length.
     */
    public int getLength() {

        // Get the length of the first command in the packet
        return (getByteOffset() + getByteCount()) - SMBV1.SIGNATURE;
    }

    /**
     * Return the available buffer space for data bytes
     *
     * @return int
     */
    public final int getAvailableLength() {
        return m_smbbuf.length - DataPacker.longwordAlign(getByteOffset());
    }

    /**
     * Return the available buffer space for data bytes for the specified buffer length
     *
     * @param len int
     * @return int
     */
    public final int getAvailableLength(int len) {
        return len - DataPacker.longwordAlign(getByteOffset());
    }

    /**
     * Get the long SMB error code
     *
     * @return Long SMB error code.
     */
    public final int getLongErrorCode() {
        return DataPacker.getIntelInt(m_smbbuf, SMBV1.ERRORCODE);
    }

    /**
     * Get the multiplex identifier.
     *
     * @return Multiplex identifier.
     */
    public final int getMultiplexId() {
        return DataPacker.getIntelShort(m_smbbuf, SMBV1.MID);
    }

    /**
     * Get a parameter word from the SMB packet.
     *
     * @param idx Parameter index (zero based).
     * @return Parameter word value.
     * @exception java.lang.IndexOutOfBoundsException If the parameter index is out of range.
     */

    public final int getParameter(int idx)
            throws java.lang.IndexOutOfBoundsException {

        // Range check the parameter index
        if ( idx > getParameterCount())
            throw new java.lang.IndexOutOfBoundsException();

        // Calculate the parameter word offset
        int pos = SMBV1.WORDCNT + (2 * idx) + 1;
        return (int) (DataPacker.getIntelShort(m_smbbuf, pos) & 0xFFFF);
    }

    /**
     * Get the parameter count
     *
     * @return Parameter word count.
     */

    public final int getParameterCount() {
        return (int) m_smbbuf[SMBV1.WORDCNT];
    }

    /**
     * Get the specified parameter words, as an int value.
     *
     * @param idx Parameter index (zero based).
     * @return int
     */

    public final int getParameterLong(int idx) {
        int pos = SMBV1.WORDCNT + (2 * idx) + 1;
        return DataPacker.getIntelInt(m_smbbuf, pos);
    }

    /**
     * Get the process identifier (PID)
     *
     * @return Process identifier value.
     */
    public final int getProcessId() {
        return DataPacker.getIntelShort(m_smbbuf, SMBV1.PID);
    }

    /**
     * Get the process identifier (PID) high bytes, or zero if not used
     *
     * @return Process identifier value.
     */
    public final int getProcessIdHigh() {
        return DataPacker.getIntelShort(m_smbbuf, SMBV1.PIDHIGH);
    }

    /**
     * Return the 32bit process id value
     *
     * @return int
     */
    public final int getProcessIdFull() {
        int pid = getProcessId();
        int pidHigh = getProcessIdHigh();

        if ( pidHigh != 0)
            pid += pidHigh << 16;

        return pid;
    }

    /**
     * Get the session identifier (SID)
     *
     * @return Session identifier (SID)
     */

    public final int getSID() {
        return DataPacker.getIntelShort(m_smbbuf, SMBV1.SID);
    }

    /**
     * Get the tree identifier (TID)
     *
     * @return Tree identifier (TID)
     */

    public final int getTreeId() {
        return DataPacker.getIntelShort(m_smbbuf, SMBV1.TID);
    }

    /**
     * Get the user identifier (UID)
     *
     * @return User identifier (UID)
     */

    public final int getUserId() {
        return DataPacker.getIntelShort(m_smbbuf, SMBV1.UID);
    }

    /**
     * Determine if there is a secondary command in this packet.
     *
     * @return Secondary command code
     */

    public final boolean hasAndXCommand() {

        // Check if there is a secondary command
        int andxCmd = getAndXCommand();

        if ( andxCmd != 0xFF && andxCmd != 0)
            return true;
        return false;
    }

    /**
     * Initialize the SMB packet buffer.
     */

    private final void InitializeBuffer() {

        // Set the packet signature
        m_smbbuf[SMBV1.SIGNATURE] = (byte) 0xFF;
        m_smbbuf[SMBV1.SIGNATURE + 1] = (byte) 'S';
        m_smbbuf[SMBV1.SIGNATURE + 2] = (byte) 'M';
        m_smbbuf[SMBV1.SIGNATURE + 3] = (byte) 'B';
    }

    /**
     * Determine if this packet is an SMB response, or command packet
     *
     * @return true if this SMB packet is a response, else false
     */

    public final boolean isResponse() {
        int resp = getFlags();
        if ( (resp & SMBV1.FLG_RESPONSE) != 0)
            return true;
        return false;
    }

    /**
     * Check if the packet contains ASCII or Unicode strings
     *
     * @return boolean
     */
    public final boolean isUnicode() {
        return (getFlags2() & SMBV1.FLG2_UNICODE) != 0 ? true : false;
    }

    /**
     * Check if the packet is using caseless filenames
     *
     * @return boolean
     */
    public final boolean isCaseless() {
        return (getFlags() & SMBV1.FLG_CASELESS) != 0 ? true : false;
    }

    /**
     * Check if long file names are being used
     *
     * @return boolean
     */
    public final boolean isLongFileNames() {
        return (getFlags2() & SMBV1.FLG2_LONGFILENAMES) != 0 ? true : false;
    }

    /**
     * Check if long error codes are being used
     *
     * @return boolean
     */
    public final boolean isLongErrorCode() {
        return (getFlags2() & SMBV1.FLG2_LONGERRORCODE) != 0 ? true : false;
    }

    /**
     * Check if this is a request packet
     *
     * @return boolean
     */
    public final boolean isRequestPacket() {
        return isResponse() == false;
    }

    /**
     * Set/clear the request packet flag
     *
     * @param reqPkt boolean
     */
    public final void setRequestPacket( boolean reqPkt) {
        int flgs = getFlags();
        if ( reqPkt)
            flgs &= ~SMBV1.FLG_RESPONSE;
        else
            flgs |= SMBV1.FLG_RESPONSE;
        setFlags( flgs);
    }

    /**
     * Set the AndX data byte count for this SMB packet.
     *
     * @param off AndX command offset.
     * @param cnt Data byte count.
     */
    public final void setAndXByteCount(int off, int cnt) {
        int offset = getAndXByteOffset(off) - 2;
        DataPacker.putIntelShort(cnt, m_smbbuf, offset);
    }

    /**
     * Set the AndX data byte area in the SMB packet
     *
     * @param off Offset to the AndX command.
     * @param byts Byte array containing the data to be copied to the SMB packet.
     */
    public final void setAndXBytes(int off, byte[] byts) {
        int offset = getAndXByteOffset(off) - 2;
        DataPacker.putIntelShort(byts.length, m_smbbuf, offset);

        offset += 2;

        for (int idx = 0; idx < byts.length; m_smbbuf[offset + idx] = byts[idx++]);
    }

    /**
     * Set the secondary SMB command
     *
     * @param cmd Secondary SMB command code.
     */
    public final void setAndXCommand(int cmd) {
        m_smbbuf[SMBV1.ANDXCOMMAND] = (byte) cmd;
        m_smbbuf[SMBV1.ANDXRESERVED] = (byte) 0;
    }

    /**
     * Set the AndX command for an AndX command block.
     *
     * @param off Offset to the current AndX command.
     * @param cmd Secondary SMB command code.
     */
    public final void setAndXCommand(int off, int cmd) {
        m_smbbuf[off + 1] = (byte) cmd;
        m_smbbuf[off + 2] = (byte) 0;
    }

    /**
     * Set the specified AndX parameter word.
     *
     * @param off Offset to the AndX command.
     * @param idx Parameter index (zero based).
     * @param val Parameter value.
     */
    public final void setAndXParameter(int off, int idx, int val) {
        int pos = off + (2 * idx) + 1;
        DataPacker.putIntelShort(val, m_smbbuf, pos);
    }

    /**
     * Set the AndX parameter count
     *
     * @param off Offset to the AndX command.
     * @param cnt Parameter word count.
     */
    public final void setAndXParameterCount(int off, int cnt) {
        m_smbbuf[off] = (byte) cnt;
    }

    /**
     * Set the data byte count for this SMB packet
     *
     * @param cnt Data byte count.
     */
    public final void setByteCount(int cnt) {
        int offset = getByteOffset() - 2;
        DataPacker.putIntelShort(cnt, m_smbbuf, offset);
    }

    /**
     * Set the data byte count for this SMB packet
     */
    public final void setByteCount() {
        int offset = getByteOffset() - 2;
        int len = m_pos - getByteOffset();
        DataPacker.putIntelShort(len, m_smbbuf, offset);
    }

    /**
     * Set the data byte area in the SMB packet
     *
     * @param byts Byte array containing the data to be copied to the SMB packet.
     */
    public final void setBytes(byte[] byts) {
        int offset = getByteOffset() - 2;
        DataPacker.putIntelShort(byts.length, m_smbbuf, offset);

        offset += 2;

        for (int idx = 0; idx < byts.length; m_smbbuf[offset + idx] = byts[idx++]);
    }

    /**
     * Set the SMB command
     *
     * @param cmd SMB command code
     */
    public final void setCommand(int cmd) {
        m_smbbuf[SMBV1.COMMAND] = (byte) cmd;
    }

    /**
     * Set the error class and code.
     *
     * @param errCode int
     * @param errClass int
     */
    public final void setError(int errCode, int errClass) {

        // Set the error class and code
        setErrorClass(errClass);
        setErrorCode(errCode);
    }

    /**
     * Set the error class/code.
     *
     * @param longError boolean
     * @param ntErr int
     * @param errCode int
     * @param errClass int
     */
    public final void setError(boolean longError, int ntErr, int errCode, int errClass) {

        // Check if the error code is a long/NT status code
        if ( longError) {

            // Set the NT status code
            setLongErrorCode(ntErr);

            // Set the NT status code flag
            if ( isLongErrorCode() == false)
                setFlags2(getFlags2() + SMBV1.FLG2_LONGERRORCODE);
        }
        else {

            // Set the error class and code
            setErrorClass(errClass);
            setErrorCode(errCode);
        }
    }

    /**
     * Set the SMB error class.
     *
     * @param cl SMB error class.
     */

    public final void setErrorClass(int cl) {
        m_smbbuf[SMBV1.ERRORCLASS] = (byte) (cl & 0xFF);
    }

    /**
     * Set the SMB error code
     *
     * @param sts SMB error code.
     */

    public final void setErrorCode(int sts) {
        m_smbbuf[SMBV1.ERROR] = (byte) (sts & 0xFF);
    }

    /**
     * Set the long SMB error code
     *
     * @param err Long SMB error code.
     */

    public final void setLongErrorCode(int err) {
        DataPacker.putIntelInt(err, m_smbbuf, SMBV1.ERRORCODE);
    }

    /**
     * Set a success status code
     */
    public final void setSuccessStatus() {
        setLongErrorCode( SMBStatus.NTSuccess);
    }

    /**
     * Set the SMB flags value.
     *
     * @param flg SMB flags value.
     */
    public final void setFlags(int flg) {
        m_smbbuf[SMBV1.FLAGS] = (byte) flg;
    }

    /**
     * Set the SMB flags2 value.
     *
     * @param flg SMB flags2 value.
     */
    public final void setFlags2(int flg) {
        DataPacker.putIntelShort(flg, m_smbbuf, SMBV1.FLAGS2);
    }

    /**
     * Set the NetBIOS packet header flags value.
     *
     * @param flg int
     */
    public final void setHeaderFlags(int flg) {
        m_smbbuf[1] = (byte) (flg & 0x00FF);
    }

    /**
     * Set the NetBIOS packet data length in the packet header.
     *
     * @param len int
     */
    public final void setHeaderLength(int len) {
        DataPacker.putIntelShort(len, m_smbbuf, 2);
    }

    /**
     * Set the NetBIOS packet type in the packet header.
     *
     * @param typ int
     */
    public final void setHeaderType(int typ) {
        m_smbbuf[0] = (byte) (typ & 0x00FF);
    }

    /**
     * Set the multiplex identifier.
     *
     * @param mid Multiplex identifier
     */
    public final void setMultiplexId(int mid) {
        DataPacker.putIntelShort(mid, m_smbbuf, SMBV1.MID);
    }

    /**
     * Set the specified parameter word.
     *
     * @param idx Parameter index (zero based).
     * @param val Parameter value.
     */
    public final void setParameter(int idx, int val) {
        int pos = SMBV1.WORDCNT + (2 * idx) + 1;
        DataPacker.putIntelShort(val, m_smbbuf, pos);
    }

    /**
     * Set the parameter count
     *
     * @param cnt Parameter word count.
     */
    public final void setParameterCount(int cnt) {

        // Set the parameter count
        m_smbbuf[SMBV1.WORDCNT] = (byte) cnt;

        // Reset the byte area pointer
        resetBytePointer();
    }

    /**
     * Set the specified parameter words.
     *
     * @param idx Parameter index (zero based).
     * @param val Parameter value.
     */
    public final void setParameterLong(int idx, int val) {
        int pos = SMBV1.WORDCNT + (2 * idx) + 1;
        DataPacker.putIntelInt(val, m_smbbuf, pos);
    }

    /**
     * Set the process identifier value (PID).
     *
     * @param pid Process identifier value.
     */
    public final void setProcessId(int pid) {
        DataPacker.putIntelShort(pid, m_smbbuf, SMBV1.PID);
    }

    /**
     * Set the packet sequence number, for connectionless commands.
     *
     * @param seq Sequence number.
     */
    public final void setSeqNo(int seq) {
        DataPacker.putIntelShort(seq, m_smbbuf, SMBV1.SEQNO);
    }

    /**
     * Set the session id.
     *
     * @param sid Session id.
     */
    public final void setSID(int sid) {
        DataPacker.putIntelShort(sid, m_smbbuf, SMBV1.SID);
    }

    /**
     * Set the tree identifier (TID)
     *
     * @param tid Tree identifier value.
     */
    public final void setTreeId(int tid) {
        DataPacker.putIntelShort(tid, m_smbbuf, SMBV1.TID);
    }

    /**
     * Set the user identifier (UID)
     *
     * @param uid User identifier value.
     */
    public final void setUserId(int uid) {
        DataPacker.putIntelShort(uid, m_smbbuf, SMBV1.UID);
    }

    /**
     * Reset the byte pointer area for packing/unpacking data items from the packet
     */
    public final void resetBytePointer() {
        m_pos = getByteOffset();
        m_endpos = m_pos + getByteCount();
    }

    /**
     * Set the unpack pointer to the specified offset, for AndX processing
     *
     * @param off int
     * @param len int
     */
    public final void setBytePointer(int off, int len) {
        m_pos = off;
        m_endpos = m_pos + len;
    }

    /**
     * Align the byte area pointer on an int (32bit) boundary
     */
    public final void alignBytePointer() {
        m_pos = DataPacker.longwordAlign(m_pos);
    }

    /**
     * Reset the byte/parameter pointer area for packing/unpacking data items from the packet, and
     * align the buffer on an int (32bit) boundary
     */
    public final void resetBytePointerAlign() {
        m_pos = DataPacker.longwordAlign(getByteOffset());
        m_endpos = m_pos + getByteCount();
    }

    /**
     * Calculate the header length for the specified number of parameters
     *
     * @param numParams int
     * @return int
     */
    public final int calculateHeaderLength( int numParams) {
        return SMBV1.HeaderLength + ( numParams * 2) + RFCNetBIOSProtocol.HEADER_LEN;
    }

    /**
     * Return the data block size
     *
     * @return Data block size in bytes
     */
    public final int getDataLength() {
        return getNTParameter(6);
    }

    /**
     * Return the data block offset
     *
     * @return Data block offset within the SMB packet.
     */
    public final int getDataOffset() {
        return getNTParameter(7) + RFCNetBIOSProtocol.HEADER_LEN;
    }

    /**
     * Unpack the parameter block
     *
     * @return int[]
     */
    public final int[] getParameterBlock() {

        // Get the parameter count and allocate the parameter buffer
        int prmcnt = getParameterBlockCount() / 4; // convert to number of ints
        if (prmcnt <= 0)
            return null;
        int[] prmblk = new int[prmcnt];

        // Get the offset to the parameter words, add the NetBIOS header length
        // to the offset.
        int pos = getParameterBlockOffset();

        // Unpack the parameter ints
        setBytePointer(pos, getByteCount());

        for (int idx = 0; idx < prmcnt; idx++) {

            // Unpack the current parameter value
            prmblk[idx] = unpackInt();
        }

        // Return the parameter block
        return prmblk;
    }

    /**
     * Return the total parameter count
     *
     * @return int
     */
    public final int getTotalParameterCount() {
        return getNTParameter(0);
    }

    /**
     * Return the total data count
     *
     * @return int
     */
    public final int getTotalDataCount() {
        return getNTParameter(1);
    }

    /**
     * Return the maximum parameter block length to be returned
     *
     * @return int
     */
    public final int getMaximumParameterReturn() {
        return getNTParameter(2);
    }

    /**
     * Return the maximum data block length to be returned
     *
     * @return int
     */
    public final int getMaximumDataReturn() {
        return getNTParameter(3);
    }

    /**
     * Return the parameter block count
     *
     * @return int
     */
    public final int getParameterBlockCount() {
        return getNTParameter(getCommand() == PacketTypeV1.NTTransact ? 4 : 2);
    }

    /**
     * Return the parameter block offset
     *
     * @return int
     */
    public final int getParameterBlockOffset() {
        return getNTParameter(getCommand() == PacketTypeV1.NTTransact ? 5 : 3) + RFCNetBIOSProtocol.HEADER_LEN;
    }

    /**
     * Return the paramater block displacement
     *
     * @return int
     */
    public final int getParameterBlockDisplacement() {
        return getNTParameter(4);
    }

    /**
     * Return the data block count
     *
     * @return int
     */
    public final int getDataBlockCount() {
        return getNTParameter(getCommand() == PacketTypeV1.NTTransact ? 6 : 5);
    }

    /**
     * Return the data block offset
     *
     * @return int
     */
    public final int getDataBlockOffset() {
        return getNTParameter(getCommand() == PacketTypeV1.NTTransact ? 7 : 6) + RFCNetBIOSProtocol.HEADER_LEN;
    }

    /**
     * Return the data block displacment
     *
     * @return int
     */
    public final int getDataBlockDisplacement() {
        return getNTParameter(7);
    }

    /**
     * Get an NT parameter (32bit)
     *
     * @param idx int
     * @return int
     */
    protected final int getNTParameter(int idx) {
        int pos = NTParams + (4 * idx);
        return DataPacker.getIntelInt(getBuffer(), pos);
    }

    /**
     * Get the setup parameter count
     *
     * @return int
     */
    public final int getSetupCount() {
        byte[] buf = getBuffer();
        return (int) buf[NTSetupCount] & 0xFF;
    }

    /**
     * Return the offset to the setup words data
     *
     * @return int
     */
    public final int getSetupOffset() {
        return NTFunction + 2;
    }

    /**
     * Get the NT transaction function code
     *
     * @return int
     */
    public final int getNTFunction() {
        byte[] buf = getBuffer();
        return DataPacker.getIntelShort(buf, NTFunction);
    }

    /**
     * Calculate the buffer length required to hold a transaction response
     *
     * @param plen Parameter block length
     * @param dlen Data block length
     * @param setupcnt Setup parameter count
     * @return int
     */
    public static final int calculateResponseLength(int plen, int dlen, int setupcnt) {

        // Standard SMB header + reply parameters + setup parameters + parameter block length + data block length
        return SMBV1.HeaderLength + ((ReplyParams + setupcnt) * 2) + plen + dlen;
    }

    /**
     * Initialize the transact SMB packet
     *
     * @param func     NT transaction function code
     * @param paramblk Parameter block data bytes
     * @param plen     Parameter block data length
     * @param datablk  Data block data bytes
     * @param dlen     Data block data length
     * @param setupcnt Number of setup parameters
     */
    public final void initTransact(int func, byte[] paramblk, int plen, byte[] datablk, int dlen, int setupcnt) {
        initTransact(func, paramblk, plen, datablk, dlen, setupcnt, DefaultReturnParams, DefaultReturnData);
    }

    /**
     * Initialize the transact SMB packet
     *
     * @param func     NT transaction function code
     * @param paramblk Parameter block data bytes
     * @param plen     Parameter block data length
     * @param datablk  Data block data bytes
     * @param dlen     Data block data length
     * @param setupcnt Number of setup parameters
     * @param maxPrm   Maximum parameter bytes to return
     * @param maxData  Maximum data bytes to return
     */
    public final void initTransact(int func, byte[] paramblk, int plen, byte[] datablk, int dlen, int setupcnt, int maxPrm,
                                   int maxData) {

        // Set the SMB command and parameter count
        setCommand(PacketTypeV1.NTTransact);
        setParameterCount(StandardParams + setupcnt);

        // Initialize the parameters
        setTotalParameterCount(plen);
        setTotalDataCount(dlen);
        setMaximumParameterReturn(maxPrm);
        setMaximumDataReturn(maxData);
        setParameterCount(plen);
        setParameterBlockOffset(0);
        setDataBlockCount(dlen);
        setDataBlockOffset(0);

        setSetupCount(setupcnt);
        setNTFunction(func);

        resetBytePointerAlign();

        // Pack the parameter block
        if (paramblk != null) {

            // Set the parameter block offset, from the start of the SMB packet
            setParameterBlockOffset(getPosition());

            // Pack the parameter block
            packBytes(paramblk, plen);
        }

        // Pack the data block
        if (datablk != null) {

            // Align the byte area offset and set the data block offset in the request
            alignBytePointer();
            setDataBlockOffset(getPosition());

            // Pack the data block
            packBytes(datablk, dlen);
        }

        // Set the byte count for the SMB packet
        setByteCount();
    }

    /**
     * Initialize the NT transaction reply
     *
     * @param paramblk Parameter block data bytes
     * @param plen     Parameter block data length
     * @param datablk  Data block data bytes
     * @param dlen     Data block data length
     */
    public final void initTransactReply(byte[] paramblk, int plen, byte[] datablk, int dlen) {

        // Set the parameter count
        setParameterCount(ReplyParams);
        setSetupCount(0);

        // Initialize the parameters
        setTotalParameterCount(plen);
        setTotalDataCount(dlen);

        setReplyParameterCount(plen);
        setReplyParameterOffset(0);
        setReplyParameterDisplacement(0);

        setReplyDataCount(dlen);
        setDataBlockOffset(0);
        setReplyDataDisplacement(0);

        setSetupCount(0);

        resetBytePointerAlign();

        // Pack the parameter block
        if (paramblk != null) {

            // Set the parameter block offset, from the start of the SMB packet
            setReplyParameterOffset(getPosition() - 4);

            // Pack the parameter block
            packBytes(paramblk, plen);
        }

        // Pack the data block
        if (datablk != null) {

            // Align the byte area offset and set the data block offset in the request
            alignBytePointer();
            setReplyDataOffset(getPosition() - 4);

            // Pack the data block
            packBytes(datablk, dlen);
        }

        // Set the byte count for the SMB packet
        setByteCount();
    }

    /**
     * Initialize the NT transaction reply
     *
     * @param paramblk Parameter block data bytes
     * @param plen     Parameter block data length
     * @param datablk  Data block data bytes
     * @param dlen     Data block data length
     * @param setupCnt Number of setup parameter
     */
    public final void initTransactReply(byte[] paramblk, int plen, byte[] datablk, int dlen, int setupCnt) {

        // Set the parameter count, add the setup parameter count
        setParameterCount(ReplyParams + setupCnt);
        setSetupCount(setupCnt);

        // Initialize the parameters
        setTotalParameterCount(plen);
        setTotalDataCount(dlen);

        setReplyParameterCount(plen);
        setReplyParameterOffset(0);
        setReplyParameterDisplacement(0);

        setReplyDataCount(dlen);
        setDataBlockOffset(0);
        setReplyDataDisplacement(0);

        setSetupCount(setupCnt);

        resetBytePointerAlign();

        // Pack the parameter block
        if (paramblk != null) {

            // Set the parameter block offset, from the start of the SMB packet
            setReplyParameterOffset(getPosition() - 4);

            // Pack the parameter block
            packBytes(paramblk, plen);
        }

        // Pack the data block
        if (datablk != null) {

            // Align the byte area offset and set the data block offset in the request
            alignBytePointer();
            setReplyDataOffset(getPosition() - 4);

            // Pack the data block
            packBytes(datablk, dlen);
        }

        // Set the byte count for the SMB packet
        setByteCount();
    }

    /**
     * Set the total parameter count
     *
     * @param cnt int
     */
    public final void setTotalParameterCount(int cnt) {
        setNTParameter(0, cnt);
    }

    /**
     * Set the total data count
     *
     * @param cnt int
     */
    public final void setTotalDataCount(int cnt) {
        setNTParameter(1, cnt);
    }

    /**
     * Set the maximum return parameter count
     *
     * @param cnt int
     */
    public final void setMaximumParameterReturn(int cnt) {
        setNTParameter(2, cnt);
    }

    /**
     * Set the maximum return data count
     *
     * @param cnt int
     */
    public final void setMaximumDataReturn(int cnt) {
        setNTParameter(3, cnt);
    }

    /**
     * Set the paramater block count
     *
     * @param cnt int
     */
    public final void setTransactParameterCount(int cnt) {
        setNTParameter(4, cnt);
    }

    /**
     * Set the reply parameter byte count
     *
     * @param cnt int
     */
    public final void setReplyParameterCount(int cnt) {
        setNTParameter(2, cnt);
    }

    /**
     * Set the reply parameter offset
     *
     * @param off int
     */
    public final void setReplyParameterOffset(int off) {
        setNTParameter(3, off);
    }

    /**
     * Set the reply parameter bytes displacement
     *
     * @param disp int
     */
    public final void setReplyParameterDisplacement(int disp) {
        setNTParameter(4, disp);
    }

    /**
     * Set the reply data byte count
     *
     * @param cnt int
     */
    public final void setReplyDataCount(int cnt) {
        setNTParameter(5, cnt);
    }

    /**
     * Set the reply data offset
     *
     * @param off int
     */
    public final void setReplyDataOffset(int off) {
        setNTParameter(6, off);
    }

    /**
     * Set the reply data bytes displacement
     *
     * @param disp int
     */
    public final void setReplyDataDisplacement(int disp) {
        setNTParameter(7, disp);
    }

    /**
     * Set the parameter block offset within the packet
     *
     * @param off int
     */
    public final void setParameterBlockOffset(int off) {
        setNTParameter(5, off != 0 ? off - RFCNetBIOSProtocol.HEADER_LEN : 0);
    }

    /**
     * Set the data block count
     *
     * @param cnt int
     */
    public final void setDataBlockCount(int cnt) {
        setNTParameter(6, cnt);
    }

    /**
     * Set the data block offset
     *
     * @param off int
     */
    public final void setDataBlockOffset(int off) {
        setNTParameter(7, off != 0 ? off - RFCNetBIOSProtocol.HEADER_LEN : 0);
    }

    /**
     * Set an NT parameter (32bit)
     *
     * @param idx int
     * @param val int
     */
    public final void setNTParameter(int idx, int val) {
        int pos = NTParams + (4 * idx);
        DataPacker.putIntelInt(val, getBuffer(), pos);
    }

    /**
     * Set the maximum setup parameter count
     *
     * @param cnt Maximum count of setup paramater words
     */
    public final void setMaximumSetupCount(int cnt) {
        byte[] buf = getBuffer();
        buf[NTMaxSetupCount] = (byte) cnt;
    }

    /**
     * Set the setup parameter count
     *
     * @param cnt Count of setup paramater words
     */
    public final void setSetupCount(int cnt) {
        byte[] buf = getBuffer();
        buf[NTSetupCount] = (byte) cnt;
    }

    /**
     * Set the specified setup parameter
     *
     * @param setupIdx Setup parameter index
     * @param setupVal Setup parameter value
     */
    public final void setSetupParameter(int setupIdx, int setupVal) {
        int pos = NTSetupCount + 1 + (setupIdx * 2);
        DataPacker.putIntelShort(setupVal, getBuffer(), pos);
    }

    /**
     * Set the NT transaction function code
     *
     * @param func int
     */
    public final void setNTFunction(int func) {
        byte[] buf = getBuffer();
        DataPacker.putIntelShort(func, buf, NTFunction);
    }

    /**
     * Reset the byte/parameter pointer area for packing/unpacking setup paramaters items to the
     * packet
     */
    public final void resetSetupPointer() {
        m_pos = NTFunction + 2;
        m_endpos = m_pos;
    }

    /**
     * Reset the byte/parameter pointer area for packing/unpacking the transaction data block
     */
    public final void resetDataBlockPointer() {
        m_pos = getDataBlockOffset();
        m_endpos = m_pos;
    }

    /**
     * Reset the byte/parameter pointer area for packing/unpacking the transaction paramater block
     */
    public final void resetParameterBlockPointer() {
        m_pos = getParameterBlockOffset();
        m_endpos = m_pos;
    }

    /**
     * Build an SMB v1 error response
     *
     * @param errClass int
     * @param errCode int
     * @param protocolHandler ProtocolHandler
     */
    public void buildErrorResponse( int errClass, int errCode, ProtocolHandler protocolHandler) {

        // Build an SMB v1 error response
        //
        // Make sure the response flag is set
        setResponse();;

        // Set the error code and error class in the response packet
        setParameterCount(0);
        setByteCount(0);

        // Add default flags/flags2 values
        // TODO: Are default flags needed here ?
        setFlags(getFlags()); // | getDefaultFlags());
        setFlags2(getFlags2()); // | getDefaultFlags2());

        // Check if the error is a NT 32bit error status
        if (errClass == SMBStatus.NTErr) {

            // Enable the long error status flag
            if (isLongErrorCode() == false)
                setFlags2(getFlags2() + SMBV1.FLG2_LONGERRORCODE);

            // Set the NT status code
            setLongErrorCode(errCode);
        }
        else {

            // Disable the long error status flag
            if (isLongErrorCode() == true)
                setFlags2(getFlags2() - SMBV1.FLG2_LONGERRORCODE);

            // Set the error status/class
            setErrorCode(errCode);
            setErrorClass(errClass);
        }
    }

    /**
     * Parse a negotiate request and return the list of request SMB dialects
     *
     * @param sess SMBSrvSession
     * @return NegotiateContext
     * @exception SMBSrvException SMB error
     */
    public NegotiateContext parseNegotiateRequest( SMBSrvSession sess)
        throws SMBSrvException {

        // Check if the received packet looks like a valid SMB v1 Negotiate
        if (getCommand() != PacketTypeV1.Negotiate || checkPacketIsValid(0, 2) == false)
            throw new SMBSrvException( SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);

        // Decode the data block into a list of requested SMB dialects
        int dataPos = getByteOffset();
        int dataLen = getByteCount();

        String diaStr = null;
        StringListDialectSelector dialects = new StringListDialectSelector();

        while (dataLen > 0) {

            // Decode an SMB dialect string from the data block, always ASCII strings
            diaStr = DataPacker.getDataString(DataType.Dialect, m_smbbuf, dataPos, dataLen, false);

            if (diaStr != null) {

                // Get the dialect id
                int diaId = Dialect.DialectType( diaStr);

                // Need to add the dialect string even if the dialect id is invalid
                dialects.AddDialectAndString( diaId, diaStr);
            }
            else {

                // Invalid dialect block in the negotiate packet, send an error response and hangup the session
                throw new SMBSrvException( SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            }

            // Update the remaining data position and count
            dataPos += diaStr.length() + 2; // data type and null
            dataLen -= diaStr.length() + 2;
        }

        // Return the list of requested dialects
        return new NegotiateContext( dialects);
    }

    /**
     * Get the next session state depending on the negotiated dialect
     *
     * @param dialectId int
     * @return SessionState
     */
    public SessionState nextStateForDialect( int dialectId) {

        if ( Dialect.DialectSupportsCommand( dialectId, PacketTypeV1.SessionSetupAndX)) {

            // Negotiated dialect requires authentication
            return SessionState.SMB_SESSSETUP;
        }
        else {

            // Negotiated dialect does not require authentication
            return SessionState.SMB_SESSION;
        }
    }

    /**
     * Pack a negotiate response, authenticating the user if required
     *
     * @param server SMBServer
     * @param sess SMBSrvSession
     * @param smbPkt SMBSrvPacket
     * @param selDialect int
     * @param negCtx NegotiateContext
     * @throws AuthenticatorException Authentication error
     * @throws SMBSrvException SMB error
     */
    public void packNegotiateResponse(SMBServer server, SMBSrvSession sess, SMBSrvPacket smbPkt, int selDialect,
                                      NegotiateContext negCtx)
            throws AuthenticatorException, SMBSrvException {

        // Convert the selected dialect into an index within the received list of dialect strings
        StringListDialectSelector strDiaSelector = (StringListDialectSelector) negCtx.getDialects();
        int selIdx = strDiaSelector.getStringIndexForDialect( selDialect);

        // Check if the extended security flag has been set by the client
        boolean extendedSecurity = (getFlags2() & SMBV1.FLG2_EXTENDEDSECURITY) != 0 ? true : false;

        // Get the authenticator from the server
        ISMBAuthenticator auth = server.getSMBAuthenticator();

        // Build the negotiate response SMB for Core dialect
        if (selDialect == -1 || selDialect <= Dialect.CorePlus) {

            // Core dialect negotiate response, or no valid dialect response
            setParameterCount(1);
            setParameter(0, selIdx);
            setByteCount(0);

            setTreeId(0);
            setUserId(0);
        }
        else if (selDialect <= Dialect.LanMan2_1) {

            // We are using case sensitive pathnames and long file names
            setFlags(SMBV1.FLG_CASELESS);
            setFlags2(SMBV1.FLG2_LONGFILENAMES);

            // LanMan dialect negotiate response
            setParameterCount(13);
            setParameter(0, selIdx);
            setParameter(1, auth.getSecurityMode());
            setParameter(2, SMBV1.LanManBufferSize);
            setParameter(3, SMBV1.LanManMaxMultiplexed); // maximum multiplexed requests
            setParameter(4, SMBV1.MaxVirtualCircuits); // maximum number of virtual circuits
            setParameter(5, 0); // read/write raw mode support

            // Create a session token, using the system clock
            setParameterLong(6, (int) (System.currentTimeMillis() & 0xFFFFFFFF));

            // Return the current server date/time
            SMBDate srvDate = new SMBDate(System.currentTimeMillis());
            setParameter(8, srvDate.asSMBTime());
            setParameter(9, srvDate.asSMBDate());

            // Server timezone offset from UTC
            setParameter(10, server.getGlobalConfiguration().getTimeZoneOffset());

            // Encryption key length
            setParameter(11, auth.getEncryptionKeyLength());
            setParameter(12, 0);

            setTreeId(0);
            setUserId(0);

            // Pack the security section, using the older challenge/response mechanism
            packNegotiateSecuritySection( server, sess, false);
        }
        else if (selDialect == Dialect.NT) {

            // We are using case sensitive pathnames and long file names
            CoreProtocolHandler smbV1Handler = (CoreProtocolHandler) sess.getProtocolHandler();
            smbV1Handler.setDefaultFlags(SMBV1.FLG_CASELESS);
            smbV1Handler.setDefaultFlags2(SMBV1.FLG2_LONGFILENAMES + SMBV1.FLG2_UNICODE);

            // Check if the authenticator supports extended security, override the client setting
            if (auth.hasExtendedSecurity() == false)
                extendedSecurity = false;

            // NT dialect negotiate response
            NTParameterPacker nt = new NTParameterPacker(getBuffer());

            setParameterCount(17);
            nt.packWord(selIdx);                    // selected dialect index
            nt.packByte(auth.getSecurityMode());
            nt.packWord(SMBV1.NTMaxMultiplexed);        // maximum multiplexed requests
                                                        // setting to 1 will disable change notify requests from the client
            nt.packWord(SMBV1.MaxVirtualCircuits);      // maximum number of virtual circuits

            int maxBufSize = server.getPacketPool().getLargestSize() - RFCNetBIOSProtocol.HEADER_LEN;
            nt.packInt(maxBufSize);

            nt.packInt(0); // maximum raw size

            // Create a session token, using the system clock
            if (auth.hasExtendedSecurity() == false || extendedSecurity == false)
                nt.packInt((int) (System.currentTimeMillis() & 0xFFFFFFFFL));
            else
                nt.packInt(0);

            // Set server capabilities, switch off extended security if the client does not support it
            int srvCapabs = auth.getServerCapabilities();
            if (auth.hasExtendedSecurity() == false || extendedSecurity == false)
                srvCapabs &= ~Capability.V1ExtendedSecurity;

            nt.packInt(srvCapabs);

            // Return the current server date/time, and timezone offset
            long srvTime = NTTime.toNTTime(new java.util.Date(System.currentTimeMillis()));

            nt.packLong(srvTime);
            nt.packWord(server.getGlobalConfiguration().getTimeZoneOffset());

            // Encryption key length
            if (auth.hasExtendedSecurity() == false || extendedSecurity == false)
                nt.packByte(auth.getEncryptionKeyLength());
            else
                nt.packByte(0);

            setFlags(smbV1Handler.getDefaultFlags());
            setFlags2(smbV1Handler.getDefaultFlags2());

            setTreeId(0);
            setUserId(0);

            // Pack the security section
            packNegotiateSecuritySection( server, sess, extendedSecurity);
        }

        // Tree and user id are not valid
        setTreeId(0xFFFF);
        setUserId(0xFFFF);

        // Make sure the response flag is set
        setResponse();
    }

    /**
     * Pack the negotiate security section response
     *
     * @param server SMBServer
     * @param sess SMBSrvSession
     * @param extendedSecurity boolean
     */
    private void packNegotiateSecuritySection( SMBServer server, SMBSrvSession sess, boolean extendedSecurity) {

        // Get the authenticator from the server
        ISMBAuthenticator auth = server.getSMBAuthenticator();

        // Check if extended security is enabled
        if ( extendedSecurity == false) {

            // Pack the negotiate response for NT/LanMan challenge/response authentication
            ChallengeAuthContext authCtx = (ChallengeAuthContext) auth.getAuthContext(sess);

            // Encryption key and primary domain string should be returned in the byte area
            int pos = getByteOffset();
            byte[] buf = getBuffer();

            if (authCtx == null || authCtx.getChallenge() == null) {

                // Return a dummy encryption key
                packZeroes(ISMBAuthenticator.STANDARD_CHALLENGE_LEN);
            }
            else {

                // Store the encryption key
                byte[] key = authCtx.getChallenge();

                for (int i = 0; i < key.length; i++)
                    buf[pos++] = key[i];
            }

            // Pack the local domain name
            String domain = sess.getSMBServer().getSMBConfiguration().getDomainName();
            if (domain != null)
                pos = DataPacker.putString(domain, buf, pos, true, true);

            // Pack the local server name
            pos = DataPacker.putString(sess.getSMBServer().getServerName(), buf, pos, true, true);

            // Set the packet length
            setByteCount(pos - getByteOffset());
        }
        else {

            // Make sure the extended security negotiation flag is set
            int flags2 = getFlags2();
            flags2 |= SMBV1.FLG2_EXTENDEDSECURITY + SMBV1.FLG2_LONGERRORCODE;

            setFlags2(flags2);

            // Get the negotiate response byte area position
            int pos = getByteOffset();
            byte[] buf = getBuffer();

            // Pack the SMB server GUID into the negotiate response
            UUID serverGUID = sess.getSMBServer().getServerGUID();

            System.arraycopy(serverGUID.getBytes(), 0, buf, pos, 16);
            pos += 16;

            // If SPNEGO is enabled then pack the NegTokenInit blob
            if ( auth.usingSPNEGO() && auth.getNegTokenInit() != null) {
                byte[] negTokenInit = auth.getNegTokenInit();

                System.arraycopy( negTokenInit, 0, buf, pos, negTokenInit.length);
                pos += negTokenInit.length;
            }

            // Set the negotiate response length
            setByteCount(pos - getByteOffset());
        }
    }

    /**
     * Do any final processing to a response before it is sent out
     *
     * @param sess SMBSrvSession
     * @param smbPkt SMBSrvPacket
     */
    public void responsePreSend( SMBSrvSession sess, SMBSrvPacket smbPkt) {

        // Get the response parser
        SMBV1Parser parser = (SMBV1Parser) smbPkt.getParser();

        if (parser.isResponse() == false)
            parser.setFlags(parser.getFlags() + SMBV1.FLG_RESPONSE);

        // Add default flags/flags2 values
        CoreProtocolHandler smbV1Handler = (CoreProtocolHandler) sess.getProtocolHandler();

        if ( smbV1Handler != null) {
            parser.setFlags(parser.getFlags() | smbV1Handler.getDefaultFlags());

            // Mask out certain flags that the client may have sent
            int flags2 = parser.getFlags2() | smbV1Handler.getDefaultFlags2();
            flags2 &= ~(SMBV1.FLG2_EXTENDEDATTRIB + SMBV1.FLG2_DFSRESOLVE + SMBV1.FLG2_SECURITYSIGS);

            parser.setFlags2(flags2);
        }
    }

    /**
     * Set the owner details for an oplock
     *
     * @param sess SMBSrvSession
     * @param opLock OpLockDetailsAdapter
     */
    public void setOplockOwner(SMBSrvSession sess, OpLockDetailsAdapter opLock) {
        opLock.setOplockOwner( new SMBV1OplockOwner( getTreeId(), getProcessId(), getUserId()));
    }

    /**
     * Determine if the request is a chained (AndX) type command and there is a chained
     * command in this request.
     *
     * @return true if there is a chained request to be handled, else false.
     */
    protected final boolean hasChainedCommand() {

        // Check for a chained command
        int cmd = getCommand();

        if (cmd == PacketTypeV1.SessionSetupAndX
                || cmd == PacketTypeV1.TreeConnectAndX
                || cmd == PacketTypeV1.OpenAndX
                || cmd == PacketTypeV1.WriteAndX
                || cmd == PacketTypeV1.ReadAndX
                || cmd == PacketTypeV1.LogoffAndX
                || cmd == PacketTypeV1.LockingAndX
                || cmd == PacketTypeV1.NTCreateAndX) {

            //  Check if there is a chained command
            return hasAndXCommand();
        }

        //  Not a chained type command
        return false;
    }

    /**
     * Dump the SMB packet
     *
     * @param dumpRaw Dump the packet as hex ASCII
     * @param dumpAll Dump the whole packet if true, or just the first 100 bytes
     */
    public void dumpPacket( boolean dumpRaw, boolean dumpAll) {

        // Dump an SMB v1 packet
        int pCount = getParameterCount();
        System.out.print("** SMB V1 Packet Type: " + SMBV1.getPacketTypeString(getCommand()));

        // Check if this is a response packet
        if (isResponse())
            System.out.println(" [Response]");
        else
            System.out.println();

        // Dump flags/secondary flags
        if (true) {

            // Dump the packet length
            System.out.println("** SMB Packet Dump");
            System.out.println("Packet Length : " + getLength());
            System.out.println("Byte Offset: " + getByteOffset() + ", Byte Count: " + getByteCount());

            // Dump the flags
            System.out.println("Flags: " + Integer.toBinaryString(getFlags()));
            System.out.println("Flags2: " + Integer.toBinaryString(getFlags2()));

            // Dump various ids
            System.out.println("TID: " + getTreeId());
            System.out.println("PID: " + getProcessId());
            System.out.println("UID: " + getUserId());
            System.out.println("MID: " + getMultiplexId());

            // Dump parameter words/count
            System.out.println("Parameter Words: " + pCount);
            StringBuffer str = new StringBuffer();

            for (int i = 0; i < pCount; i++) {
                str.setLength(0);
                str.append(" P");
                str.append(Integer.toString(i + 1));
                str.append(" = ");
                str.append(Integer.toString(getParameter(i)));
                while (str.length() < 16)
                    str.append(" ");
                str.append("0x");
                str.append(Integer.toHexString(getParameter(i)));
                System.out.println(str.toString());
            }

            // Response packet fields
            if (isResponse()) {

                // Dump the error code
                System.out.println("Error: 0x" + Integer.toHexString(getErrorCode()));
                System.out.print("Error Class: ");

                switch (getErrorClass()) {
                    case SMBStatus.Success:
                        System.out.println("SUCCESS");
                        break;
                    case SMBStatus.ErrDos:
                        System.out.println("ERRDOS");
                        break;
                    case SMBStatus.ErrSrv:
                        System.out.println("ERRSRV");
                        break;
                    case SMBStatus.ErrHrd:
                        System.out.println("ERRHRD");
                        break;
                    case SMBStatus.ErrCmd:
                        System.out.println("ERRCMD");
                        break;
                    default:
                        System.out.println("0x" + Integer.toHexString(getErrorClass()));
                        break;
                }

                // Display the SMB error text
                System.out.print("Error Text: ");
                System.out.println(SMBErrorText.ErrorString(getErrorClass(), getErrorCode()));
            }
        }

        // Dump the raw data
        if ( dumpRaw) {
            System.out.println("********** Raw SMB Data Dump **********");
            if ( dumpAll)
                HexDump.Dump(m_smbbuf, getLength(), 4);
            else
                HexDump.Dump(m_smbbuf, getLength() < 100 ? getLength() : 100, 4);
        }

        System.out.println();
        System.out.flush();

    }
}
