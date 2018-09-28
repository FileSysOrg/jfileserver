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

package org.filesys.server.auth.spnego;

import java.io.IOException;

import org.filesys.server.auth.SecurityBlob;
import org.filesys.server.auth.asn.DERBuffer;
import org.filesys.server.auth.asn.DEREnumerated;
import org.filesys.server.auth.asn.DERObject;
import org.filesys.server.auth.asn.DEROctetString;
import org.filesys.server.auth.asn.DEROid;
import org.filesys.server.auth.asn.DERSequence;
import org.filesys.util.HexDump;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;

/**
 * NegTokenTarg Class
 *
 * <p>
 * Contains the details of an SPNEGO NegTokenTarg blob for use with CIFS.
 *
 * @author gkspencer
 */
public class NegTokenTarg {

    // Result code
    private SPNEGO.Result m_result = SPNEGO.Result.Invalid;

    // Supported mechanism
    private Oid m_supportedMech;

    // Response token
    private byte[] m_responseToken;

    // Mech list MIC
    private byte[] m_mechListMIC;

    /**
     * Class constructor for decoding
     */
    public NegTokenTarg() {
    }

    /**
     * Class constructor
     *
     * @param result   SPNEGO.Result
     * @param mech     Oid
     * @param response byte[]
     */
    public NegTokenTarg(SPNEGO.Result result, Oid mech, byte[] response) {

        m_result = result;
        m_supportedMech = mech;
        m_responseToken = response;
    }

    /**
     * Return the result
     *
     * @return SPNEGO.Result
     */
    public final SPNEGO.Result getResult() {
        return m_result;
    }

    /**
     * Return the supported mech type Oid
     *
     * @return Oid
     */
    public final Oid getSupportedMech() {
        return m_supportedMech;
    }

    /**
     * Determine if there is a valid response token
     *
     * @return boolean
     */
    public final boolean hasResponseToken() {
        return m_responseToken != null ? true : false;
    }

    /**
     * Return the response token
     *
     * @return byte[]
     */
    public final byte[] getResponseToken() {
        return m_responseToken;
    }

    /**
     * Check if there is a mech list message integrity code
     *
     * @return boolean
     */
    public final boolean hasMechListMIC() {
        return m_mechListMIC != null;
    }

    /**
     * Return the mech list message integrity code
     *
     * @return byte[]
     */
    public final byte[] getMechListMIC() {
        return m_mechListMIC;
    }

    /**
     * Set the mech list message integrity code
     *
     * @param mic byte[]
     */
    public final void setMechListMIC( byte[] mic) {
        m_mechListMIC = mic;
    }

    /**
     * Decode an SPNEGO NegTokenTarg blob
     *
     * @param secBlob SecurityBlob
     * @exception IOException Error decoding the token
     */
    public void decode(SecurityBlob secBlob)
            throws IOException {

        decode( secBlob.getSecurityBlob(), secBlob.getSecurityOffset(), secBlob.getSecurityLength());
    }

    /**
     * Decode an SPNEGO NegTokenTarg blob
     *
     * @param buf byte[]
     * @param off int
     * @param len int
     * @exception IOException Error decoding the token
     */
    public void decode(byte[] buf, int off, int len)
            throws IOException {

        // Create a DER buffer to decode the blob
        DERBuffer derBuf = new DERBuffer(buf, off, len);

        // Get the first object from the blob
        DERObject derObj = derBuf.unpackObject();

        if (derObj instanceof DERSequence) {

            // Access the sequence
            DERSequence derSeq = (DERSequence) derObj;

            // Get the status
            derObj = derSeq.getTaggedObject(0);
            if (derObj != null) {

                if (derObj instanceof DEREnumerated == false)
                    throw new IOException("Invalid status object");

                DEREnumerated derEnum = (DEREnumerated) derObj;
                m_result = SPNEGO.Result.fromInt( derEnum.getValue());
            }

            // Get the supportedMech (optional)
            derObj = derSeq.getTaggedObject(1);
            if (derObj != null) {

                // Check the object type
                if (derObj instanceof DEROid == false)
                    throw new IOException("Invalid supportedMech object");

                DEROid derMech = (DEROid) derObj;
                try {
                    m_supportedMech = new Oid(derMech.getOid());
                }
                catch (GSSException ex) {
                    throw new IOException("Bad supportedMech OID");
                }
            } else
                m_supportedMech = null;

            // Get the responseToken (optional)
            derObj = derSeq.getTaggedObject(2);
            if (derObj != null) {

                // Check the object type
                if (derObj instanceof DEROctetString == false)
                    throw new IOException("Invalid responseToken object");

                DEROctetString derResp = (DEROctetString) derObj;
                m_responseToken = derResp.getValue();
            } else
                m_responseToken = null;

            // Get the mecListMIC (optional)
            derObj = derSeq.getTaggedObject(3);
            if (derObj != null) {

                // Check the object type
                if (derObj instanceof DEROctetString == false)
                    throw new IOException("Invalid mecListMIC object");

                DEROctetString derMec = (DEROctetString) derObj;
                m_mechListMIC = derMec.getValue();
            }
        } else
            throw new IOException("Bad format in security blob");
    }

    /**
     * Encode an SPNEGO NegTokenTarg blob
     *
     * @return byte[]
     * @exception IOException Error encoding the token
     */
    public byte[] encode()
            throws IOException {

        // Build the sequence of tagged objects
        DERSequence derSeq = new DERSequence();
        derSeq.setTagNo(1);

        // Add the result
        DEREnumerated derEnum = new DEREnumerated(m_result.intValue());
        derEnum.setTagNo(0);
        derSeq.addObject(derEnum);

        // Pack the supportedMech, if valid
        if (m_supportedMech != null) {
            DEROid derOid = new DEROid(m_supportedMech.toString());
            derOid.setTagNo(1);
            derSeq.addObject(derOid);
        }

        // Pack the response token, if valid
        if (m_responseToken != null) {
            DEROctetString derResp = new DEROctetString(m_responseToken);
            derResp.setTagNo(2);
            derSeq.addObject(derResp);
        }

        // Pack the mech list MIC, if valid
        if ( m_mechListMIC != null) {
            DEROctetString derMIC = new DEROctetString(m_mechListMIC);
            derMIC.setTagNo(3);
            derSeq.addObject(derMIC);
        }

        // Pack the objects
        DERBuffer derBuf = new DERBuffer();
        derBuf.packObject(derSeq);

        // Return the packed negTokenTarg blob
        return derBuf.getBytes();
    }

    /**
     * Return the NegtokenTarg object as a string
     *
     * @return String
     */
    public String toString() {

        StringBuffer str = new StringBuffer();

        str.append("[NegtokenTarg result=");
        str.append(getResult().name());

        str.append(" oid=");
        str.append(getSupportedMech());

        str.append(" response=");
        if (hasResponseToken()) {
            str.append(getResponseToken().length);
            str.append(" bytes");
        } else
            str.append("null");

        str.append(", mechListMIC=");
        if ( hasMechListMIC())
            str.append(HexDump.hexString( getMechListMIC()));
        else
            str.append("null");

        str.append("]");

        return str.toString();
    }
}
