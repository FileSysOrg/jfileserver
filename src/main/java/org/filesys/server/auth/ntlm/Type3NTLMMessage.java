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

package org.filesys.server.auth.ntlm;

import org.filesys.util.HexDump;

/**
 * Type 3 NTLM Message Class
 *
 * @author gkspencer
 */
public class Type3NTLMMessage extends NTLMMessage {

    // Minimal type 3 message length
    public static final int MinimalMessageLength = 52;

    // Type 3 field offsets
    public static final int OffsetLMResponse        = 12;
    public static final int OffsetNTLMResponse      = 20;
    public static final int OffsetDomain            = 28;
    public static final int OffsetUserName          = 36;
    public static final int OffsetWorkstationName   = 44;
    public static final int OffsetDataMinimum       = 52;
    public static final int OffsetSessionKey        = 52; // optional
    public static final int OffsetFlags             = 60; // optional
    public static final int OffsetData              = 64;
    public static final int OffsetVersion           = 64; // optional
    public static final int OffsetMIC               = 72;

    // NTLM message integrity code length
    public static final int NTLM_MIC_LENGTH         = 16;

    // Flag to indicate if Unicode strings have been negotiated
    private boolean m_unicode;

    // Data block offset, used to indicate if session key and flags have been specified
    private int m_dataOffset = -1;

    /**
     * Default constructor
     */
    public Type3NTLMMessage() {
        super();
    }

    /**
     * Class constructor
     *
     * @param buf byte[]
     */
    public Type3NTLMMessage(byte[] buf) {
        super(buf, 0, buf.length);
    }

    /**
     * Class constructor
     *
     * @param buf     byte[]
     * @param offset  int
     * @param len     int
     * @param unicode boolean
     */
    public Type3NTLMMessage(byte[] buf, int offset, int len, boolean unicode) {
        super(buf, offset, len);

        m_unicode = unicode;
    }

    /**
     * Return the flags value
     *
     * @return int
     */
    public int getFlags() {
        return getIntValue(OffsetFlags);
    }

    /**
     * Return the length of the LM hash
     *
     * @return int
     */
    public final int getLMHashLength() {
        return getShortValue(OffsetLMResponse);
    }

    /**
     * Return the LM password hash
     *
     * @return byte[]
     */
    public final byte[] getLMHash() {
        return getByteValue(OffsetLMResponse);
    }

    /**
     * Return the length of the NTLM hash
     *
     * @return int
     */
    public final int getNTLMHashLength() {
        return getShortValue(OffsetNTLMResponse);
    }

    /**
     * Return the NTLM password hash
     *
     * @return byte[]
     */
    public final byte[] getNTLMHash() {
        return getByteValue(OffsetNTLMResponse);
    }

    /**
     * Return the domain name
     *
     * @return String
     */
    public final String getDomain() {
        return getStringValue(OffsetDomain, hasFlag(NTLM.FlagNegotiateUnicode));
    }

    /**
     * Return the user name
     *
     * @return String
     */
    public final String getUserName() {
        return getStringValue(OffsetUserName, hasFlag(NTLM.FlagNegotiateUnicode));
    }

    /**
     * Return the workstation name
     *
     * @return String
     */
    public final String getWorkstation() {
        return getStringValue(OffsetWorkstationName, hasFlag(NTLM.FlagNegotiateUnicode));
    }

    /**
     * Determine if the session key has been specified
     *
     * @return boolean
     */
    public final boolean hasSessionKey() {
        return getShortValue(OffsetSessionKey) > 0;
    }

    /**
     * Return the session key, or null if the session key is not present
     *
     * @return byte[]
     */
    public final byte[] getSessionKey() {

        if (hasSessionKey() == false)
            return null;

        // Get the session key bytes
        return getByteValue(OffsetSessionKey);
    }

    /**
     * Get the message integrity code
     *
     * @return byte[]
     */
    public final byte[] getMIC() {
        return getRawBytes(OffsetMIC, NTLM_MIC_LENGTH);
    }

    /**
     * Clear the NTLM MIC
     */
    public final void clearMIC() {
        zeroBytes(OffsetMIC, NTLM_MIC_LENGTH);
    }

    /**
     * Build a type 3 message
     *
     * @param lmHash   byte[]
     * @param ntlmHash byte[]
     * @param domain   String
     * @param username String
     * @param wksname  String
     * @param sessKey  byte[]
     * @param flags    int
     */
    public final void buildType3(byte[] lmHash, byte[] ntlmHash, String domain, String username, String wksname, byte[] sessKey,
                                 int flags) {

        initializeHeader(Type.Authenticate, 0);

        // Set the data offset
        int dataOff = OffsetData;

        // Pack the domain, user and workstation names
        dataOff = setStringValue(OffsetDomain, domain, dataOff, m_unicode);
        dataOff = setStringValue(OffsetUserName, username, dataOff, m_unicode);
        dataOff = setStringValue(OffsetWorkstationName, wksname, dataOff, m_unicode);

        // Pack the LM and NTLM password hashes
        dataOff = setByteValue(OffsetLMResponse, lmHash, dataOff);
        dataOff = setByteValue(OffsetNTLMResponse, ntlmHash, dataOff);

        // Pack the session key
        dataOff = setByteValue(OffsetSessionKey, sessKey, dataOff);

        // Make sure various flags are set
        int typ3flags = NTLM.FlagNegotiateNTLM + NTLM.FlagRequestTarget;
        if (m_unicode)
            flags += NTLM.FlagNegotiateUnicode;

        // Pack the flags
        setIntValue(OffsetFlags, typ3flags);

        // Set the message length
        setLength(dataOff);
    }

    /**
     * Set the message flags
     *
     * @param flags int
     */
    protected void setFlags(int flags) {
        setIntValue(OffsetFlags, flags);
    }

    /**
     * Find the data block offset
     *
     * @return int
     */
    private final int findDataBlockOffset() {

        // Find the lowest data offset
        //
        // Check the LM hash
        int offset = getByteOffset(OffsetLMResponse);

        if (m_dataOffset == -1 || offset < m_dataOffset)
            m_dataOffset = offset;

        // Check the NTLM hash
        offset = getByteOffset(OffsetNTLMResponse);
        if (offset < m_dataOffset)
            m_dataOffset = offset;

        // Check the domain name
        offset = getStringOffset(OffsetDomain);
        if (offset < m_dataOffset)
            m_dataOffset = offset;

        // Check the user name
        offset = getStringOffset(OffsetUserName);
        if (offset < m_dataOffset)
            m_dataOffset = offset;

        // Check the workstation
        offset = getStringOffset(OffsetWorkstationName);
        if (offset < m_dataOffset)
            m_dataOffset = offset;

        // Return the new data offset
        return m_dataOffset;
    }

    /**
     * Clone the current NTLM type 3 message
     *
     * @return Type3NTLMMessage
     */
    public final Type3NTLMMessage clone() {
        Type3NTLMMessage newType3 = new Type3NTLMMessage();
        newType3.copyFrom(this.getBuffer(), this.getOffset(), this.getLength());

        return newType3;
    }

    /**
     * Return the type 3 message as a string
     *
     * @return String
     */
    public String toString() {

        StringBuffer str = new StringBuffer();

        str.append("[Type3:");

        str.append(",LM:");
        if (getLMHash() != null)
            str.append(HexDump.hexString(getLMHash()));
        else
            str.append("<Null>");

        str.append(",NTLM:");
        if (getNTLMHash() != null)
            str.append(HexDump.hexString(getNTLMHash()));
        else
            str.append("<Null>");

        str.append(",Dom:");
        str.append(getDomain());
        str.append(",User:");
        str.append(getUserName());
        str.append(",Wks:");
        str.append(getWorkstation());

        if (hasSessionKey()) {
            str.append(",SessKey:");
            str.append(HexDump.hexString(getSessionKey()));
            str.append(",Flags:0x");
            str.append(Integer.toHexString(getFlags()));
        }
        str.append("]");

        return str.toString();
    }
}
