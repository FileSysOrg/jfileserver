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

package org.filesys.server.auth.kerberos;

import java.io.IOException;
import java.util.Iterator;

import org.filesys.server.auth.asn.DERApplicationSpecific;
import org.filesys.server.auth.asn.DERBitString;
import org.filesys.server.auth.asn.DERBuffer;
import org.filesys.server.auth.asn.DERInteger;
import org.filesys.server.auth.asn.DERObject;
import org.filesys.server.auth.asn.DEROctetString;
import org.filesys.server.auth.asn.DERSequence;

/**
 * Kerberos AP-REQ Class
 *
 * @author gkspencer
 */
public class KerberosApReq {

    // Constants
    public static final int APOptionUseSessionKey = 1;
    public static final int APOptionMutualAuthReq = 2;

    // Bit masks for AP options
    private static final int APOptionUseSessionKeyMask = 0x40000000;
    private static final int APOptionsMutualAuthReqMask = 0x20000000;

    // AP-REQ fields
    private int m_APOptions;
    private byte[] m_ticket;

    // Authenticator encrypted data
    private int m_authEncType;
    private byte[] m_authEncData;
    private int m_authEncKvno = -1;

    /**
     * Default constructor
     */
    public KerberosApReq() {
    }

    /**
     * Class constructor
     *
     * @param blob byte[]
     * @exception IOException Error parsing security blob
     */
    public KerberosApReq(byte[] blob)
            throws IOException {
        parseApReq(blob);
    }

    /**
     * Return the APOptions
     *
     * @return int
     */
    public final int getAPOptions() {
        return m_APOptions;
    }

    /**
     * Check if the use session key option is enabled
     *
     * @return boolean
     */
    public final boolean useSessionKey() {
        return (m_APOptions & APOptionUseSessionKeyMask) != 0 ? true : false;
    }

    /**
     * Check if the mutual authentication required option is enabled
     *
     * @return boolean
     */
    public final boolean hasMutualAuthentication() {
        return (m_APOptions & APOptionsMutualAuthReqMask) != 0 ? true : false;
    }

    /**
     * Return the ticket
     *
     * @return byte[]
     */
    public final byte[] getTicket() {
        return m_ticket;
    }

    /**
     * Return the authenticator encryption type
     *
     * @return int
     */
    public final int getAuthenticatorEncType() {
        return m_authEncType;
    }

    /**
     * Return the authenticator encrypted data block
     *
     * @return byte[]
     */
    public final byte[] getAuthenticator() {
        return m_authEncData;
    }

    /**
     * Return the authenticator key version number
     *
     * @return int
     */
    public final int getAuthenticatorKeyVersion() {
        return m_authEncKvno;
    }

    /**
     * Parse an AP-REQ blob
     *
     * @param blob byte[]
     * @exception IOException Error parsing security blob
     */
    private final void parseApReq(byte[] blob)
            throws IOException {

        // Create a stream to parse the ASN.1 encoded AP-REQ blob
        DERBuffer derBuf = new DERBuffer(blob);

        DERObject derObj = derBuf.unpackObject();
        if (derObj instanceof DERSequence) {
            // Enumerate the AP-REQ objects
            DERSequence derSeq = (DERSequence) derObj;
            Iterator<DERObject> iterObj = derSeq.getObjects();

            while (iterObj.hasNext()) {
                // Read an object
                derObj = (DERObject) iterObj.next();

                if (derObj != null && derObj.isTagged()) {
                    switch (derObj.getTagNo()) {
                        // PVno
                        case 0:
                            if (derObj instanceof DERInteger) {
                                DERInteger derInt = (DERInteger) derObj;
                                if (derInt.getValue() != 5)
                                    throw new IOException("Unexpected PVNO value in AP-REQ");
                            }
                            break;

                        // Message type
                        case 1:
                            if (derObj instanceof DERInteger) {
                                DERInteger derInt = (DERInteger) derObj;
                                if (derInt.getValue() != 14)
                                    throw new IOException("Unexpected msg-type value in AP-REQ");
                            }
                            break;

                        // AP-Options
                        case 2:
                            if (derObj instanceof DERBitString) {
                                DERBitString derBit = (DERBitString) derObj;
                                m_APOptions = derBit.intValue();
                            }
                            break;

                        // Ticket
                        case 3:
                            if (derObj instanceof DERApplicationSpecific) {
                                DERApplicationSpecific derApp = (DERApplicationSpecific) derObj;
                                m_ticket = derApp.getValue();
                            }
                            break;

                        // Authenticator
                        case 4:
                            if (derObj instanceof DERSequence) {
                                DERSequence derAuthSeq = (DERSequence) derObj;

                                // Enumerate the sequence
                                Iterator<DERObject> iterSeq = derAuthSeq.getObjects();

                                while (iterSeq.hasNext()) {
                                    // Get the current sequence element
                                    derObj = (DERObject) iterSeq.next();

                                    if (derObj != null && derObj.isTagged()) {
                                        switch (derObj.getTagNo()) {
                                            // Encryption type
                                            case 0:
                                                if (derObj instanceof DERInteger) {
                                                    DERInteger derInt = (DERInteger) derObj;
                                                    m_authEncType = derInt.intValue();
                                                }
                                                break;

                                            // Kvno
                                            case 1:
                                                if (derObj instanceof DERInteger) {
                                                    DERInteger derInt = (DERInteger) derObj;
                                                    m_authEncKvno = derInt.intValue();
                                                }
                                                break;

                                            // Cipher
                                            case 2:
                                                if (derObj instanceof DEROctetString) {
                                                    DEROctetString derOct = (DEROctetString) derObj;
                                                    m_authEncData = derOct.getValue();
                                                }
                                                break;
                                        }
                                    }
                                }
                            }
                            break;
                    }
                }
            }
        }
    }

    /**
     * Parse a mech token to get the AP-REQ
     *
     * @param mechToken byte[]
     * @exception IOException Error parsing security blob
     */
    public final void parseMechToken(byte[] mechToken)
            throws IOException {

        // Create a stream to parse the ASN.1 encoded mech token
        DERBuffer derBuf = new DERBuffer(mechToken);
        byte[] apreqBlob = null;

        // Get the application specific object
        byte[] appByts = derBuf.unpackApplicationSpecificBytes();

        if (appByts != null) {

            // Read the OID and token id
            derBuf = new DERBuffer(appByts);

            DERObject derObj = derBuf.unpackObject();
            derBuf.unpackByte();
            derBuf.unpackByte();

            // Read the AP-REQ object
            apreqBlob = derBuf.unpackApplicationSpecificBytes();
        }

        // Parse the AP-REQ, if found
        if (apreqBlob != null)
            parseApReq(apreqBlob);
        else
            throw new IOException("AP-REQ blob not found in mechToken");
    }

    /**
     * Return the AP-REQ as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[AP-REQ:APOptions=");
        str.append(hasMutualAuthentication() ? "MutualAuth " : "");
        str.append(useSessionKey() ? "UseSessKey" : "");

        str.append(",Ticket=Len=");
        str.append(m_ticket != null ? m_ticket.length : 0);
        str.append(",Authenticator=EncType=");
        str.append(m_authEncType);
        str.append(",Kvno=");
        str.append(getAuthenticatorKeyVersion());
        str.append(",Len=");
        str.append(m_authEncData != null ? m_authEncData.length : 0);
        str.append("]");

        return str.toString();
    }
}
