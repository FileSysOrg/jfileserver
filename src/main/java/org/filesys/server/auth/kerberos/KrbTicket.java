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
import org.filesys.server.auth.asn.DERInteger;
import org.filesys.server.auth.asn.DERObject;
import org.filesys.server.auth.asn.DEROctetString;
import org.filesys.server.auth.asn.DERSequence;

/**
 * Kerberos ticket Class
 *
 * @author gkspencer
 */
public class KrbTicket {

    // Realm and principal name
    private String m_realm;
    private PrincipalName m_principalName;

    // Encrypted part
    private int m_encType;
    private byte[] m_encPart;
    private int m_encKvno = -1;

    /**
     * Default constructor
     */
    public KrbTicket() {
    }

    /**
     * Class constructor
     *
     * @param blob byte[]
     * @exception IOException Error parsing security blob
     */
    public KrbTicket(byte[] blob)
            throws IOException {
        parseTicket(blob);
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
     * Return the encrypted part of the ticket
     *
     * @return byte[]
     */
    public final byte[] getEncryptedPart() {
        return m_encPart;
    }

    /**
     * Return the encrypted part type
     *
     * @return int
     */
    public final int getEncryptedType() {
        return m_encType;
    }

    /**
     * Return the encrypted part key version number
     *
     * @return int
     */
    public final int getEncryptedPartKeyVersion() {
        return m_encKvno;
    }

    /**
     * Parse a Kerberos ticket blob
     *
     * @param blob byte[]
     * @exception IOException Error parsing security blob
     */
    public final void parseTicket(byte[] blob)
            throws IOException {

        // Create a stream to parse the ASN.1 encoded Kerberos ticket blob
        DERBuffer derBuf = new DERBuffer(blob);

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
                        // Tkt-vno
                        case 0:
                            if (derObj instanceof DERInteger) {
                                DERInteger derInt = (DERInteger) derObj;
                                if (derInt.intValue() != 5)
                                    throw new IOException("Unexpected VNO value in Kerberos ticket");
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

                        // Encrypted part of the ticket
                        case 3:
                            if (derObj instanceof DERSequence) {
                                DERSequence derEncSeq = (DERSequence) derObj;

                                // Enumerate the sequence
                                Iterator<DERObject> iterEncSeq = derEncSeq.getObjects();

                                while (iterEncSeq.hasNext()) {
                                    // Get the current sequence element
                                    derObj = iterEncSeq.next();

                                    if (derObj != null && derObj.isTagged()) {
                                        switch (derObj.getTagNo()) {
                                            // Encryption type
                                            case 0:
                                                if (derObj instanceof DERInteger) {
                                                    DERInteger derInt = (DERInteger) derObj;
                                                    m_encType = derInt.intValue();
                                                }
                                                break;

                                            // Kvno
                                            case 1:
                                                if (derObj instanceof DERInteger) {
                                                    DERInteger derInt = (DERInteger) derObj;
                                                    m_encKvno = derInt.intValue();
                                                }
                                                break;

                                            // Cipher
                                            case 2:
                                                if (derObj instanceof DEROctetString) {
                                                    DEROctetString derOct = (DEROctetString) derObj;
                                                    m_encPart = derOct.getValue();
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
     * Return the Kerberos ticket as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[KrbTkt Realm=");
        str.append(getRealm());
        str.append(",Principal=");
        str.append(getPrincipalName());
        str.append(",EncPart=Type=");
        str.append(getEncryptedType());
        str.append(",KVNO=");
        str.append(getEncryptedPartKeyVersion());
        str.append(",Len=");
        str.append(getEncryptedPart() != null ? getEncryptedPart().length : 0);
        str.append("]");

        return str.toString();
    }
}
