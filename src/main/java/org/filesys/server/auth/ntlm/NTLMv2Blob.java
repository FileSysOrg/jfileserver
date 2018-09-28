/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.filesys.smb.NTTime;
import org.filesys.util.DataBuffer;
import org.filesys.util.DataPacker;
import org.filesys.util.HexDump;

/**
 * NTLMv2 Blob Class
 *
 * <P>Contains methods to pack/unpack and calculate the hash of an NTLMv2 blob.
 *
 * @author gkspencer
 */
public class NTLMv2Blob {

    //  Constants
    public static final int HMAC_LEN        = 16;
    public static final int CHALLENGE_LEN   = 8;

    //  Offsets
    public static final int OFFSET_HMAC         = 0;
    public static final int OFFSET_HEADER       = 16;
    public static final int OFFSET_RESERVED     = 20;
    public static final int OFFSET_TIMESTAMP    = 24;
    public static final int OFFSET_CHALLENGE    = 32;
    public static final int OFFSET_UNKNOWN      = 36;
    public static final int OFFSET_TARGETINFO   = 44;

    //  NTLMv2 blob
    private byte[] m_blob;
    private int m_offset;
    private int m_len;

    /**
     * Class constructor
     *
     * @param buf byte[]
     */
    public NTLMv2Blob(byte[] buf) {
        m_blob = buf;
        m_offset = 0;
        m_len = m_blob.length;
    }

    /**
     * Class constructor
     *
     * @param buf    byte[]
     * @param offset int
     * @param len    int
     */
    public NTLMv2Blob(byte[] buf, int offset, int len) {
        m_blob = buf;
        m_offset = offset;
        m_len = len;
    }

    /**
     * Return the buffer
     *
     * @return byte[]
     */
    public final byte[] getBuffer() {
        return m_blob;
    }

    /**
     * Return the offset
     *
     * @return int
     */
    public final int getOffset() {
        return m_offset;
    }

    /**
     * Return the blob length
     *
     * @return int
     */
    public final int getLength() {
        return m_len;
    }

    /**
     * Return the HMAC from the buffer
     *
     * @return byte[]
     */
    public final byte[] getHMAC() {
        byte[] hmac = new byte[HMAC_LEN];
        System.arraycopy(m_blob, m_offset, hmac, 0, HMAC_LEN);

        return hmac;
    }

    /**
     * Return the timestamp from the buffer, in NT 64bit time format
     *
     * @return long
     */
    public final long getTimeStamp() {
        return DataPacker.getIntelLong(m_blob, m_offset + OFFSET_TIMESTAMP);
    }

    /**
     * Return the client challenge
     *
     * @return byte[]
     */
    public final byte[] getClientChallenge() {
        byte[] challenge = new byte[CHALLENGE_LEN];
        System.arraycopy(m_blob, m_offset + OFFSET_CHALLENGE, challenge, 0, CHALLENGE_LEN);

        return challenge;
    }

    /**
     * Get the target information list
     *
     * @return List of target information
     */
    public final List<TargetInfo> getTargetInfo() {

        // Wrap a data buffer around the target information section of the buffer
        DataBuffer tBuf = new DataBuffer( getBuffer(), getOffset() + OFFSET_TARGETINFO, getLength() - OFFSET_TARGETINFO);
        List<TargetInfo> tList = null;
        boolean endOfList = false;

        while ( endOfList == false) {

            // Get the current target information type and data length
            TargetInfo.Type tTyp = TargetInfo.Type.fromInt( tBuf.getShort());
            int tLen = tBuf.getShort();

            if ( tTyp == TargetInfo.Type.END_OF_LIST) {
                endOfList = true;
                continue;
            }

            // Get the target information value
            TargetInfo tInfo = null;

            switch ( tTyp) {

                // String type target information
                case SERVER:
                case DOMAIN:
                case FULL_DNS:
                case DNS_DOMAIN:
                case DNS_TREE:
                case SPN:
                    String sVal = tBuf.getString( tLen/2, true);
                    tInfo = new StringTargetInfo( tTyp, sVal);
                    break;

                // Timestamp type target information
                case TIMESTAMP:
                    long lVal = tBuf.getLong();
                    tInfo = new TimestampTargetInfo( lVal);
                    break;

                // Integer type target information
                case FLAGS:
                    int iVal = tBuf.getInt();
                    tInfo = new FlagsTargetInfo( iVal);
                    break;
            }

            // Add the target information to the list if valid
            if ( tInfo != null) {
                 if ( tList == null)
                     tList = new ArrayList<>();
                 tList.add( tInfo);
            }
        }

        // Return the target information list
        return tList;
    }

    /**
     * Calculate the HMAC of the blob using the specified NTLMv2 hash and challenge
     *
     * @param challenge byte[]
     * @param v2hash    byte[]
     * @return byte[]
     * @exception Exception Invalid hash
     */
    public final byte[] calculateHMAC(byte[] challenge, byte[] v2hash)
            throws Exception {

        // Create a copy of the NTLMv2 blob with room for the challenge
        byte[] blob = new byte[(m_len - HMAC_LEN) + CHALLENGE_LEN];
        System.arraycopy(challenge, 0, blob, 0, CHALLENGE_LEN);
        System.arraycopy(m_blob, m_offset + OFFSET_HEADER, blob, CHALLENGE_LEN, m_len - HMAC_LEN);

        // Generate the HMAC of the blob using the v2 hash as the key
        Mac hmacMd5 = Mac.getInstance("HMACMD5");
        SecretKeySpec blobKey = new SecretKeySpec(v2hash, 0, v2hash.length, "MD5");

        hmacMd5.init(blobKey);
        return hmacMd5.doFinal(blob);
    }

    /**
     * Calcualte the LMv2 HMAC value
     *
     * @param v2hash       byte[]
     * @param srvChallenge byte[]
     * @param clChallenge  byte[]
     * @return byte[]
     * @exception Exception Invalid hash
     */
    public final byte[] calculateLMv2HMAC(byte[] v2hash, byte[] srvChallenge, byte[] clChallenge)
            throws Exception {

        // Concatenate the server and client challenges
        byte[] blob = new byte[16];
        System.arraycopy(srvChallenge, 0, blob, 0, srvChallenge.length);
        System.arraycopy(clChallenge, 0, blob, 8, clChallenge.length);

        // Generate the LMv2 HMAC of the blob using the v2 hash as the key
        Mac hmacMd5 = Mac.getInstance("HMACMD5");
        SecretKeySpec blobKey = new SecretKeySpec(v2hash, 0, v2hash.length, "MD5");

        hmacMd5.init(blobKey);
        return hmacMd5.doFinal(blob);
    }

    /**
     * Dump the NTLMv2 blob details
     */
    public final void Dump() {
        System.out.println("NTLMv2 blob :");
        System.out.println("       HMAC : " + HexDump.hexString(getHMAC()));
        System.out.println("     Header : 0x" + Integer.toHexString(DataPacker.getIntelInt(m_blob, m_offset + OFFSET_HEADER)));
        System.out.println("  Timestamp : " + new Date(NTTime.toJavaDate(getTimeStamp())));
        System.out.println("  Challenge : " + HexDump.hexString(getClientChallenge()));
    }
}
