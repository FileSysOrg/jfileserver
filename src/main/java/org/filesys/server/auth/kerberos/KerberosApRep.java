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

import org.filesys.server.auth.asn.DERApplicationSpecific;
import org.filesys.server.auth.asn.DERBuffer;
import org.filesys.server.auth.asn.DERInteger;
import org.filesys.server.auth.asn.DERObject;
import org.filesys.server.auth.asn.DEROctetString;
import org.filesys.server.auth.asn.DERSequence;

/**
 * Kerberos AP-REP Class
 *
 * @author gkspencer
 */
public class KerberosApRep {

    // AP-REP fields
    //
    // Encrypted part
    private int m_encType;
    private byte[] m_encData;
    private int m_encKvno = -1;

    /**
     * Default constructor
     */
    public KerberosApRep() {
    }

    /**
     * Class constructor
     *
     * @param blob byte[]
     * @exception IOException Error parsing security blob
     */
    public KerberosApRep(byte[] blob)
            throws IOException {
        parseApRep(blob);
    }

    /**
     * Return the encryption type
     *
     * @return int
     */
    public final int getEncryptionType() {
        return m_encType;
    }

    /**
     * Return the encrypted data block
     *
     * @return byte[]
     */
    public final byte[] getEncryptedPart() {
        return m_encData;
    }

    /**
     * Return the encrypted part key version number, or -1 if not set
     *
     * @return int
     */
    public final int getKeyVersion() {
        return m_encKvno;
    }

    /**
     * Set the encrypted AP-REP data
     *
     * @param encType int
     * @param encData byte[]
     * @param encVno  int
     */
    public final void setEncryptedPart(int encType, byte[] encData, int encVno) {
        m_encType = encType;
        m_encData = encData;
        m_encKvno = encVno;
    }

    /**
     * Parse an AP-REP blob
     *
     * @param blob byte[]
     * @exception IOException Error parsing security blob
     */
    private final void parseApRep(byte[] blob)
            throws IOException {
        // Create a stream to parse the ASN.1 encoded AP-REP blob
        DERBuffer derBuf = new DERBuffer(blob);
        DERObject derObj = derBuf.unpackObject();

        if (derObj instanceof DERSequence) {
            // Enumerate the AP-REP objects
            DERSequence derSeq = (DERSequence) derObj;

            for (int idx = 0; idx < derSeq.numberOfObjects(); idx++) {
                // Read an object
                derObj = (DERObject) derSeq.getObjectAt(idx);

                if (derObj != null && derObj.isTagged()) {
                    switch (derObj.getTagNo()) {
                        // PVno
                        case 0:
                            if (derObj instanceof DERInteger) {
                                DERInteger derInt = (DERInteger) derObj;
                                if (derInt.getValue() != 5)
                                    throw new IOException("Unexpected PVNO value in AP-REP");
                            }
                            break;

                        // Message type
                        case 1:
                            if (derObj instanceof DERInteger) {
                                DERInteger derInt = (DERInteger) derObj;
                                if (derInt.getValue() != 15)
                                    throw new IOException("Unexpected msg-type value in AP-REP");
                            }
                            break;

                        // Encrypted part
                        case 2:
                            if (derObj instanceof DERSequence) {
                                DERSequence derAuthSeq = (DERSequence) derObj;

                                // Enumerate the sequence
                                for (int i = 0; i < derAuthSeq.numberOfObjects(); i++) {
                                    // Get the current sequence element
                                    derObj = (DERObject) derAuthSeq.getObjectAt(i);

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
                                                    m_encData = derOct.getValue();
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
     * Parse a response token to get the AP-REP
     *
     * @param respToken byte[]
     * @exception IOException Error parsing security blob
     */
    public final void parseResponseToken(byte[] respToken)
            throws IOException {
        // Create a stream to parse the ASN.1 encoded response token
        DERBuffer derBuf = new DERBuffer(respToken);
        byte[] aprepBlob = null;

        // Get the application specific object
        byte[] appByts = derBuf.unpackApplicationSpecificBytes();
        if (appByts != null) {
            // Read the OID and token id
            DERBuffer appBuf = new DERBuffer(appByts);
            DERObject derObj = appBuf.unpackObject();
            appBuf.unpackByte();
            appBuf.unpackByte();

            // Read the AP-REP object
            derObj = appBuf.unpackObject();
            if (derObj instanceof DERApplicationSpecific) {
                DERApplicationSpecific derApp = (DERApplicationSpecific) derObj;
                aprepBlob = derApp.getValue();
            }
        }

        // Parse the AP-REP, if found
        if (aprepBlob != null)
            parseApRep(aprepBlob);
        else
            throw new IOException("AP-REP blob not found in responseToken");
    }

    /**
     * ASN.1 encode the Kerberos AP-REP
     *
     * @return byte[]
     * @exception IOException Error encoding security blob
     */
    public final byte[] encodeApRep()
            throws IOException {
        // Build the sequence of tagged objects
        DERSequence derList = new DERSequence();

        // Add the PVno and msg-type
        DERObject derObj = new DERInteger(5);
        derObj.setTagNo(0);
        derList.addObject(derObj);

        derObj = new DERInteger(15);
        derObj.setTagNo(1);
        derList.addObject(derObj);

        // Add the encrypted AP-REP part
        DERSequence aprepSeq = new DERSequence(2);

        derObj = new DERInteger(m_encType);
        derObj.setTagNo(0);
        aprepSeq.addObject(derObj);

        if (m_encKvno != -1) {
            derObj = new DERInteger(m_encKvno);
            derObj.setTagNo(1);
            aprepSeq.addObject(derObj);
        }

        derObj = new DEROctetString(m_encData);
        derObj.setTagNo(2);
        aprepSeq.addObject(derObj);

        derList.addObject(aprepSeq);

        // Pack the objects
        DERBuffer derBuf = new DERBuffer();
        derBuf.packApplicationSpecific(15, derList);

        // Return the packed Kerberos AP-REP blob
        return derBuf.getBytes();
    }

    /**
     * Return the AP-REQ as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[AP-REP EncPart=Type=");
        str.append(getEncryptionType());
        str.append(",Kvno=");
        str.append(getKeyVersion());
        str.append(",Len=");
        str.append(m_encData != null ? m_encData.length : 0);
        str.append("]");

        return str.toString();
    }

}
