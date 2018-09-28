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

import org.filesys.server.auth.asn.DERBitString;
import org.filesys.server.auth.asn.DERBuffer;
import org.filesys.server.auth.asn.DERGeneralString;
import org.filesys.server.auth.asn.DERInteger;
import org.filesys.server.auth.asn.DERObject;
import org.filesys.server.auth.asn.DEROctetString;
import org.filesys.server.auth.asn.DERSequence;

/**
 * Encrypted Part Kerberos Ticket Class
 *
 * @author gkspencer
 */
public class EncKrbTicket {

    // Encrypted ticket fields
    //
    // Flags
    private int m_flags;

    // Encryption key
    private int m_encKeyType;
    private byte[] m_encKey;

    // Realm
    private String m_realm;

    // Principal name
    private String m_principalName;

    // Authorization data
    private int m_authType = -1;
    private byte[] m_authData;

    /**
     * Default constructor
     */
    public EncKrbTicket() {
    }

    /**
     * Class constructor
     *
     * @param blob byte[]
     * @exception IOException Error parsing security blob
     */
    public EncKrbTicket(byte[] blob)
            throws IOException {
        parseEncTicket(blob);
    }

    /**
     * Return the flags
     *
     * @return int
     */
    public final int getFlags() {
        return m_flags;
    }

    /**
     * Return the realm
     *
     * @return String
     */
    public final String getRealm() {
        return m_realm;
    }

    /**
     * Return the encryption key type
     *
     * @return int
     */
    public final int getEncryptionKeyType() {
        return m_encKeyType;
    }

    /**
     * Return the encryption key
     *
     * @return byte[]
     */
    public final byte[] getEncryptionKey() {
        return m_encKey;
    }

    /**
     * Return the authorization data type, or -1 if not present
     *
     * @return int
     */
    public final int getAuthorizationDataType() {
        return m_authType;
    }

    /**
     * Return the authorization data
     *
     * @return byte[]
     */
    public final byte[] getAuthorizationData() {
        return m_authData;
    }

    /**
     * Parse an encrypted Kerberos ticket part
     *
     * @param blob byte[]
     * @exception IOException Error parsing security blob
     */
    public void parseEncTicket(byte[] blob)
            throws IOException {
        // Create a stream to parse the ASN.1 encoded Kerberos ticket blob
        DERBuffer derBuf = new DERBuffer(blob);

        DERObject derObj = derBuf.unpackObject();
        if (derObj instanceof DERSequence) {
            // Enumerate the Kerberos ticket objects
            DERSequence derSeq = (DERSequence) derObj;

            for (int idx = 0; idx < derSeq.numberOfObjects(); idx++) {
                // Read an object
                derObj = (DERObject) derSeq.getObjectAt(idx);

                if (derObj != null && derObj.isTagged()) {
                    switch (derObj.getTagNo()) {
                        // Flags
                        case 0:
                            if (derObj instanceof DERBitString) {
                                DERBitString derBits = (DERBitString) derObj;
                                m_flags = derBits.intValue();
                            }
                            break;

                        // Key
                        case 1:
                            if (derObj instanceof DERSequence) {
                                DERSequence derEncSeq = (DERSequence) derObj;

                                // Enumerate the sequence
                                for (int i = 0; i < derEncSeq.numberOfObjects(); i++) {
                                    // Get the current sequence element
                                    derObj = (DERObject) derEncSeq.getObjectAt(i);

                                    if (derObj != null && derObj.isTagged()) {
                                        switch (derObj.getTagNo()) {
                                            // Encryption key type
                                            case 0:
                                                if (derObj instanceof DERInteger) {
                                                    DERInteger derInt = (DERInteger) derObj;
                                                    m_encKeyType = (int) derInt.getValue();
                                                }
                                                break;

                                            // Encryption key
                                            case 1:
                                                if (derObj instanceof DEROctetString) {
                                                    DEROctetString derOct = (DEROctetString) derObj;
                                                    m_encKey = derOct.getValue();
                                                }
                                                break;
                                        }
                                    }
                                }
                            }
                            break;

                        // Realm
                        case 2:
                            if (derObj instanceof DERGeneralString) {
                                DERGeneralString derStr = (DERGeneralString) derObj;
                                m_realm = derStr.getValue();
                            }
                            break;

                        // Principal name
                        case 3:
                            break;

                        // Transited encoding
                        case 4:
                            break;

                        // Auth time
                        case 5:
                            break;

                        // Start time
                        case 6:
                            break;

                        // End time
                        case 7:
                            break;

                        // Renew till
                        case 8:
                            break;

                        // Host address
                        case 9:
                            break;

                        // Authorization data
                        case 10:
                            break;
                    }
                }
            }
        }
    }

    /**
     * Return the encrypted ticket part as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[EncKrbTkt Flags=0x");
        str.append(Integer.toHexString(getFlags()));
        str.append(",Key=Type=");
        str.append(getEncryptionKeyType());
        str.append(",Len=");
        str.append(getEncryptionKey().length);
        str.append(",Realm=");
        str.append(getRealm());
        str.append("]");

        return str.toString();
    }
}
