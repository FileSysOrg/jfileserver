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

import org.filesys.server.auth.AuthenticatorException;
import org.filesys.server.filesys.postprocess.PostRequestProcessor;
import org.filesys.server.locking.OpLockDetailsAdapter;
import org.filesys.smb.dcerpc.UUID;
import org.filesys.util.DataPacker;

/**
 * SMB Parser Base Class
 *
 * @author gkspencer
 */
public abstract class SMBParser {

    // Buffer to parse or pack
    protected byte[] m_smbbuf;

    // Buffer current position and end position
    protected int m_pos;
    protected int m_endpos;

    // Offset of the current request within a compound request
    protected int m_reqOffset;

    // Request post processor, called after the protocol response has been sent
    protected PostRequestProcessor m_postProcess;

    /**
     * Class constructor
     *
     * @param buf byte[]
     * @param len int
     */
    public SMBParser( byte[] buf, int len) {
        resetParser( buf, len);
    }

    /**
     * Reset the parser buffer and available length
     *
     * @param buffer byte[]
     * @param len int
     */
    public void resetParser( byte[] buffer, int len) {
        m_smbbuf = buffer;

        m_pos = 0;
        m_endpos = len;
    }

    /**
     * Return the count of bytes available in the buffer
     *
     * @return int
     */
    public final int getRemainingLength() {
        return m_smbbuf.length - (m_pos + m_reqOffset);
    }

    /**
     * Pack a byte (8 bit) value into the byte area
     *
     * @param val byte
     */
    public final void packByte(byte val) {
        m_smbbuf[m_reqOffset + m_pos++] = val;
    }

    /**
     * Pack a byte (8 bit) value into the byte area
     *
     * @param val int
     */
    public final void packByte(int val) {
        m_smbbuf[m_reqOffset + m_pos++] = (byte) val;
    }

    /**
     * Pack the specified bytes into the byte area
     *
     * @param byts byte[]
     * @param len int
     */
    public final void packBytes(byte[] byts, int len) {
        if (( m_reqOffset + m_pos + len) > m_smbbuf.length)
            throw new ArrayIndexOutOfBoundsException("buflen=" + m_smbbuf.length);

        System.arraycopy( byts, 0, m_smbbuf, m_reqOffset + m_pos, len);
        m_pos += len;
    }

    /**
     * Pack the specified bytes into the byte area
     *
     * @param byts byte[]
     */
    public final void packBytes(byte[] byts) {
        if (( m_reqOffset + m_pos + byts.length) > m_smbbuf.length)
            throw new ArrayIndexOutOfBoundsException("buflen=" + m_smbbuf.length);

        System.arraycopy( byts, 0, m_smbbuf, m_reqOffset + m_pos, byts.length);
        m_pos += byts.length;
    }

    /**
     * Pack a string using either ASCII or Unicode into the byte area
     *
     * @param str String
     * @param uni boolean
     */
    public final void packString(String str, boolean uni) {

        // Check for Unicode or ASCII
        if ( uni) {

            // Word align the buffer position, pack the Unicode string
            m_pos = DataPacker.wordAlign(m_pos);
            DataPacker.putUnicodeString(str, m_smbbuf, m_reqOffset + m_pos, true);
            m_pos += (str.length() * 2) + 2;
        }
        else {

            // Pack the ASCII string
            DataPacker.putString(str, m_smbbuf, m_reqOffset + m_pos, true);
            m_pos += str.length() + 1;
        }
    }

    /**
     * Pack a string using either ASCII or Unicode into the byte area
     *
     * @param str String
     * @param uni boolean
     * @param nul boolean
     */
    public final void packString(String str, boolean uni, boolean nul) {

        // Check for Unicode or ASCII
        if ( uni) {

            // Word align the buffer position, pack the Unicode string
            m_pos = DataPacker.wordAlign(m_pos);
            DataPacker.putUnicodeString(str, m_smbbuf, m_reqOffset + m_pos, nul);
            m_pos += (str.length() * 2);
            if ( nul == true)
                m_pos += 2;
        }
        else {

            // Pack the ASCII string
            DataPacker.putString(str, m_smbbuf, m_reqOffset + m_pos, nul);
            m_pos += str.length();
            if ( nul == true)
                m_pos++;
        }
    }

    /**
     * Pack a string using either ASCII or Unicode into the byte area at the specified buffer position
     *
     * @param pos int
     * @param str String
     * @param uni boolean
     * @param nul boolean
     * @return int
     */
    public final int packStringAt(int pos, String str, boolean uni, boolean nul) {

        // Check for Unicode or ASCII
        if ( uni) {

            // Word align the buffer position, pack the Unicode string
            return DataPacker.putUnicodeString(str, m_smbbuf, pos, nul);
        }
        else {

            // Pack the ASCII string
            return DataPacker.putString(str, m_smbbuf, pos, nul);
        }
    }

    /**
     * Pack a word (16 bit) value into the byte area
     *
     * @param val int
     */
    public final void packWord(int val) {
        DataPacker.putIntelShort(val, m_smbbuf, m_reqOffset + m_pos);
        m_pos += 2;
    }

    /**
     * Pack an integer (32 bit) value into the byte area
     *
     * @param val int
     */
    public final void packInt(int val) {
        DataPacker.putIntelInt(val, m_smbbuf, m_reqOffset + m_pos);
        m_pos += 4;
    }

    /**
     * Pack a long integer (64 bit) value into the byte area
     *
     * @param val long
     */
    public final void packLong(long val) {
        DataPacker.putIntelLong(val, m_smbbuf, m_reqOffset + m_pos);
        m_pos += 8;
    }

    /**
     * Pack a word (16 bit) value into the byte area at the specified position
     *
     * @param pos int
     * @param val int
     */
    public final void packWordAt(int pos, int val) {
        DataPacker.putIntelShort(val, m_smbbuf, m_reqOffset + pos);
    }

    /**
     * Pack an integer (32 bit) value into the byte area at the specified position
     *
     * @param pos int
     * @param val int
     */
    public final void packIntAt(int pos, int val) {
        DataPacker.putIntelInt(val, m_smbbuf, m_reqOffset + pos);
    }

    /**
     * Pack a long integer (64 bit) value into the byte area at the specified position
     *
     * @param pos int
     * @param val long
     */
    public final void packLongAt(int pos, long val) {
        DataPacker.putIntelLong(val, m_smbbuf, m_reqOffset + pos);
    }

    /**
     * Pack zero bytes at the current position
     *
     * @param cnt int
     */
    public final void packZeroes( int cnt) {
        DataPacker.putZeros( m_smbbuf, m_reqOffset + m_pos, cnt);
        m_pos += cnt;
    }

    /**
     * Return the current byte area buffer position
     *
     * @return int
     */
    public final int getPosition() {
        return m_pos;
    }

    /**
     * Return the base offset
     *
     * @return int
     */
    public final int getOffset() {
        return m_reqOffset;
    }

    /**
     * Get the received data length
     *
     * @return int
     */
    public final int getReceivedLength() {
        return m_endpos;
    }

    /**
     * Set the buffer offset
     *
     * @param offset int
     */
    public final void setOffset(int offset) {
        m_reqOffset = offset;
    }

    /**
     * Set the buffer position
     *
     * @param pos int
     */
    public void setPosition( int pos) {
        m_pos = pos;
    }

    /**
     * Unpack a byte value from the byte area
     *
     * @return int
     */
    public final int unpackByte() {
        return (int) m_smbbuf[m_reqOffset + m_pos++];
    }

    /**
     * Unpack a block of bytes from the byte area
     *
     * @param len int
     * @return byte[]
     */
    public final byte[] unpackBytes(int len) {
        if ( len <= 0)
            return null;

        byte[] buf = new byte[len];
        System.arraycopy(m_smbbuf, m_reqOffset + m_pos, buf, 0, len);
        m_pos += len;
        return buf;
    }

    /**
     * Unpack a word (16 bit) value from the byte area
     *
     * @return int
     */
    public final int unpackWord() {
        int val = DataPacker.getIntelShort(m_smbbuf, m_reqOffset + m_pos);
        m_pos += 2;
        return val;
    }

    /**
     * Unpack an integer (32 bit) value from the byte area
     *
     * @return int
     */
    public final int unpackInt() {
        int val = DataPacker.getIntelInt(m_smbbuf, m_reqOffset + m_pos);
        m_pos += 4;
        return val;
    }

    /**
     * Unpack a long integer (64 bit) value from the byte area
     *
     * @return long
     */
    public final long unpackLong() {
        long val = DataPacker.getIntelLong(m_smbbuf, m_reqOffset + m_pos);
        m_pos += 8;
        return val;
    }

    /**
     * Unpack a string from the byte area
     *
     * @param uni boolean
     * @return String
     */
    public final String unpackString(boolean uni) {

        // Check for Unicode or ASCII
        String ret = null;

        if ( uni) {

            // Word align the current buffer position
            m_pos = DataPacker.wordAlign(m_pos);
            ret = DataPacker.getUnicodeString(m_smbbuf, m_reqOffset + m_pos, m_smbbuf.length - m_pos);
            if ( ret != null)
                m_pos += (ret.length() * 2) + 2;
        }
        else {

            // Unpack the ASCII string
            ret = DataPacker.getString(m_smbbuf, m_reqOffset + m_pos, m_smbbuf.length - m_pos);
            if ( ret != null)
                m_pos += ret.length() + 1;
        }

        // Return the string
        return ret;
    }

    /**
     * Unpack a string with the specified length, not null terminated
     *
     * @param len int
     * @param uni boolean
     * @return String
     */
    public final String unpackStringWithLength(int len, boolean uni) {

        // Check for Unicode or ASCII
        String ret = null;

        if ( uni) {

            // Word align the current buffer position
            ret = DataPacker.getUnicodeString(m_smbbuf, m_reqOffset + m_pos, len);
            m_pos += ret.length() * 2;
        }
        else {

            // Unpack the ASCII string
            ret = DataPacker.getString(m_smbbuf, m_reqOffset + m_pos, len);
            m_pos += ret.length();
        }

        // Return the string
        return ret;
    }

    /**
     * Unpack a string from the specified buffer offset
     *
     * @param offset int
     * @param uni boolean
     * @return String
     */
    public final String unpackStringAt(int offset, boolean uni) {

        // Check for Unicode or ASCII
        String ret = null;

        if ( uni) {

            // Word align the current buffer position
            ret = DataPacker.getUnicodeString(m_smbbuf, offset, m_smbbuf.length - offset);
        }
        else {

            // Unpack the ASCII string
            ret = DataPacker.getString(m_smbbuf, offset, m_smbbuf.length - offset);
        }

        // Return the string
        return ret;
    }

    /**
     * Unpack a string from the specified buffer offset
     *
     * @param offset int
     * @param len int
     * @param uni boolean
     * @return String
     */
    public final String unpackStringAt(int offset, int len, boolean uni) {

        // Check for Unicode or ASCII
        String ret = null;

        if ( uni) {

            // Word align the current buffer position
            ret = DataPacker.getUnicodeString(m_smbbuf, m_reqOffset + offset, len);
        }
        else {

            // Unpack the ASCII string
            ret = DataPacker.getString(m_smbbuf, m_reqOffset + offset, len);
        }

        // Return the string
        return ret;
    }

    /**
     * Unpack a UUID value
     *
     * @return UUID
     */
    public final UUID unpackUUID() {
        UUID uuid = new UUID(m_smbbuf, m_reqOffset + m_pos);
        m_pos += UUID.UUID_LENGTH_BINARY;

        return uuid;
    }

    /**
     * Check if there is more data in the byte area
     *
     * @return boolean
     */
    public final boolean hasMoreData() {
        if ( m_pos < m_endpos)
            return true;
        return false;
    }

    /**
     * Skip a number of bytes in the parameter/byte area
     *
     * @param cnt int
     */
    public final void skipBytes(int cnt) {
        m_pos += cnt;
    }

    /**
     * Skip a byte
     */
    public final void skipByte() {
        m_pos++;
    }

    /**
     * Skip a word
     */
    public final void skipWord() {
        m_pos += 2;
    }

    /**
     * Skip an int
     */
    public final void skipInt() {
        m_pos += 4;
    }

    /**
     * Skip a long
     */
    public final void skipLong() {
        m_pos += 8;
    }

    /**
     * Return the packet buffer
     *
     * @return byte[]
     */
    public final byte[] getBuffer() {
        return m_smbbuf;
    }

    /**
     * Set the data buffer and maximum length
     *
     * @param buf byte[]
     * @param len int
     */
    public final void setBuffer(byte[] buf, int len) {
        m_smbbuf = buf;
        m_endpos = len;
    }

    /**
     * Check if the parser has a post processor
     *
     * @return boolean
     */
    public final boolean hasPostProcessor() {
        return m_postProcess != null ? true : false;
    }

    /**
     * Return the associated request post processor
     *
     * @return PostRequetProcesser
     */
    public final PostRequestProcessor getPostProcessor() {
        return m_postProcess;
    }

    /**
     * Set the request post processor
     *
     * @param postProcessor PostRequestProcessor
     */
    public final void setPostProcessor(PostRequestProcessor postProcessor) {
        m_postProcess = postProcessor;
    }

    /**
     * Check if the received SMB packet is valid
     *
     * @param reqWords Minimum number of parameter words expected.
     * @param reqBytes Minimum number of bytes expected.
     * @return boolean True if the packet passes the checks, else false.
     */
    public abstract boolean checkPacketIsValid(int reqWords, int reqBytes);

    /**
     * Dump the SMB packet
     *
     * @param dumpRaw Dump the packet as hex ASCII
     * @param dumpAll Dump the whole packet if true, or just the first 100 bytes
     */
    public abstract void dumpPacket( boolean dumpRaw, boolean dumpAll);

    /**
     * Return the SMB parser name
     *
     * @return String
     */
    public abstract String getName();

    /**
     * Return the parser type, either SMBSrvPacket.Version.V1 or SMBSrvPacket.Version.V2
     *
     * @return SMBSrvPacket.Version
     */
    public abstract SMBSrvPacket.Version isType();

    /**
     * Get the packet length
     *
     * @return int
     */
    public abstract int getLength();

    /**
     * Pack a success response
     */
    public abstract void packSuccessRespone();

    /**
     * Set the packet to be a response
     */
    public abstract void setResponse();

    /**
     * Return the parser details as a short string
     *
     * @return String
     */
    public abstract String toShortString();

    /**
     * Determine if long error codes are being used for this request/response
     *
     * @return boolean
     */
    public abstract boolean isLongErrorCode();

    /**
     * Get the error class from the packet
     *
     * @return int
     */
    public abstract int getErrorClass();

    /**
     * Get the error code from the packet
     *
     * @return int
     */
    public abstract int getErrorCode();

    /**
     * Get the next session state depending on the negotiated dialect
     *
     * @param dialectId int
     * @return SessionState
     */
    public abstract SessionState nextStateForDialect( int dialectId);

    /**
     * Build an error response
     *
     * @param errClass int
     * @param errCode int
     * @param protocolHandler ProtocolHandler
     */
    public abstract void buildErrorResponse( int errClass, int errCode, ProtocolHandler protocolHandler);

    /**
     * Parse a negotiate request and return the list of request SMB dialects
     *
     * @param sess SMBSrvSession
     * @return NegotiateContext
     * @exception SMBSrvException SMB error
     */
    public abstract NegotiateContext parseNegotiateRequest(SMBSrvSession sess)
        throws SMBSrvException;

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
    public abstract void packNegotiateResponse(SMBServer server, SMBSrvSession sess, SMBSrvPacket smbPkt, int selDialect,
                                               NegotiateContext negCtx)
        throws AuthenticatorException, SMBSrvException;

    /**
     * Set the owner details for an oplock
     *
     * @param sess SMBSrvSession
     * @param opLock OpLockDetailsAdapter
     */
    public abstract void setOplockOwner(SMBSrvSession sess, OpLockDetailsAdapter opLock);

    /**
     * Do any final processing to a response before it is sent out
     *
     * @param sess SMBSrvSession
     * @param smbPkt SMBSrvPacket
     */
    public void responsePreSend( SMBSrvSession sess, SMBSrvPacket smbPkt) {
    }
}
