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

import org.filesys.server.auth.asn.DERBuffer;
import org.filesys.server.auth.asn.DERGeneralString;
import org.filesys.server.auth.asn.DERGeneralizedTime;
import org.filesys.server.auth.asn.DERInteger;
import org.filesys.server.auth.asn.DERObject;
import org.filesys.server.auth.asn.DEROctetString;
import org.filesys.server.auth.asn.DERSequence;
import org.filesys.util.HexDump;

/**
 * Kerberos Authenitcator Class
 *
 * @author gkspencer
 */
public class KrbAuthenticator {

    // Kerberos authenticator fields
    //
    // Realm and principal
    private String m_realm;
    private PrincipalName m_principalName;

    // Microseconds
    private long m_microseconds;

    // Timestamp
    private String m_timestamp;

    // Sub-key
    private int m_subKeyType;
    private byte[] m_subKey;

    // Sequence number
    private int m_seqNo;

    /**
     * Default constructor
     */
    public KrbAuthenticator() {
    }

    /**
     * Class constructor
     *
     * @param blob byte[]
     * @exception IOException Error parsing security blob
     */
    public KrbAuthenticator(byte[] blob)
            throws IOException {
        parseAuthenticator(blob);
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
     * Return the principal name
     *
     * @return PrincipalName
     */
    public final PrincipalName getPrincipalName() {
        return m_principalName;
    }

    /**
     * Return the timestamp
     *
     * @return String
     */
    public final String getTimestamp() {
        return m_timestamp;
    }

    /**
     * Return the sub-key type
     *
     * @return int
     */
    public final int getSubKeyType() {
        return m_subKeyType;
    }

    /**
     * Return the sub-key
     *
     * @return byte[]
     */
    public final byte[] getSubKey() {
        return m_subKey;
    }

    /**
     * Return the sequence number
     *
     * @return int
     */
    public final int getSequenceNumber() {
        return m_seqNo;
    }

    /**
     * Parse the ASN/1 encoded authenticator
     *
     * @param auth byte[]
     * @exception IOException Error parsing security blob
     */
    public final void parseAuthenticator(byte[] auth)
            throws IOException {

        // Create a stream to parse the ASN.1 encoded Kerberos ticket blob
        DERBuffer derBuf = new DERBuffer(auth);

        DERObject derObj = derBuf.unpackObject();
        if (derObj instanceof DERSequence) {

            // Enumerate the Kerberos ticket objects
            DERSequence derSeq = (DERSequence) derObj;
            Iterator<DERObject> iterObj = derSeq.getObjects();

            while (iterObj.hasNext()) {

                // Read an object
                derObj = iterObj.next();

                if (derObj != null && derObj.isTagged()) {
                    switch (derObj.getTagNo()) {
                        // Authenticator VNO
                        case 0:
                            if (derObj instanceof DERInteger) {
                                DERInteger derInt = (DERInteger) derObj;
                                if (derInt.intValue() != 5)
                                    throw new IOException("");
                            }
                            break;

                        // Realm
                        case 1:
                            if (derObj instanceof DERGeneralString) {
                                DERGeneralString derStr = (DERGeneralString) derObj;
                                m_realm = derStr.getValue();
                            }
                            break;

                        // Principal name
                        case 2:
                            if (derObj instanceof DERSequence) {
                                DERSequence derPrincSeq = (DERSequence) derObj;
                                m_principalName = new PrincipalName();
                                m_principalName.parsePrincipalName(derPrincSeq);
                            }
                            break;

                        // Checksum
                        case 3:
                            break;

                        // Microseconds
                        case 4:
                            if (derObj instanceof DERInteger) {
                                DERInteger derInt = (DERInteger) derObj;
                                m_microseconds = derInt.intValue();
                            }
                            break;

                        // Timestamp
                        case 5:
                            if (derObj instanceof DERGeneralizedTime) {
                                DERGeneralizedTime derTime = (DERGeneralizedTime) derObj;
                                m_timestamp = derTime.getValue();
                            }
                            break;

                        // Sub-key
                        case 6:
                            if (derObj instanceof DERSequence) {
                                DERSequence derEncSeq = (DERSequence) derObj;

                                // Enumerate the sequence
                                Iterator<DERObject> iterSeq = derEncSeq.getObjects();

                                while (iterSeq.hasNext()) {
                                    // Get the current sequence element
                                    derObj = iterSeq.next();

                                    if (derObj != null && derObj.isTagged()) {
                                        switch (derObj.getTagNo()) {
                                            // Encryption key type
                                            case 0:
                                                if (derObj instanceof DERInteger) {
                                                    DERInteger derInt = (DERInteger) derObj;
                                                    m_subKeyType = derInt.intValue();
                                                }
                                                break;

                                            // Encryption key
                                            case 1:
                                                if (derObj instanceof DEROctetString) {
                                                    DEROctetString derOct = (DEROctetString) derObj;
                                                    m_subKey = derOct.getValue();
                                                }
                                                break;
                                        }
                                    }
                                }
                            }
                            break;

                        // Sequence number
                        case 7:
                            if (derObj instanceof DERInteger) {
                                DERInteger derInt = (DERInteger) derObj;
                                m_seqNo = derInt.intValue();
                            }
                            break;

                        // Authorization data
                        case 8:
                            break;
                    }
                }
            }
        }
    }

    /**
     * Return the authenticator as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[KrbAuth Realm=");
        str.append(getRealm());
        str.append(",Principal=");
        str.append(getPrincipalName());
        str.append(",uSec=");
        str.append(m_microseconds);
        str.append(",Time=");
        str.append(getTimestamp());
        str.append(",SubKey=Type=");
        str.append(getSubKeyType());
        str.append(",Key=");
        str.append(HexDump.hexString(getSubKey()));
        str.append(",SeqNo=");
        str.append(getSequenceNumber());
        str.append("]");

        return str.toString();
    }
}
