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
import java.util.ArrayList;
import java.util.List;

import org.filesys.server.auth.SecurityBlob;
import org.filesys.server.auth.asn.DERBitString;
import org.filesys.server.auth.asn.DERBuffer;
import org.filesys.server.auth.asn.DERGeneralString;
import org.filesys.server.auth.asn.DERObject;
import org.filesys.server.auth.asn.DEROctetString;
import org.filesys.server.auth.asn.DEROid;
import org.filesys.server.auth.asn.DERSequence;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;

/**
 * NegTokenInit Class
 *
 * <p>Contains the details of an SPNEGO NegTokenInit blob for use with SMB.
 *
 * @author gkspencer
 */
public class NegTokenInit {

    // Mech types list
    private Oid[] m_mechTypes;

    // Mech types list object bytes
    private byte[] m_mechTypesByts;

    // Context flags
    private int m_contextFlags = -1;

    // Mech token
    private byte[] m_mechToken;

    // MectListMIC principal
    private String m_mecListMICPrincipal;

    /**
     * Class constructor for decoding
     */
    public NegTokenInit() {
    }

    /**
     * Class constructor for encoding
     *
     * @param mechTypes     Oid[]
     * @param mechPrincipal String
     */
    public NegTokenInit(Oid[] mechTypes, String mechPrincipal) {
        m_mechTypes = mechTypes;
        m_mecListMICPrincipal = mechPrincipal;
    }

    /**
     * Class constructor for encoding
     *
     * @param mechTypes     List of OIDs
     * @param mechPrincipal String
     */
    public NegTokenInit(List<Oid> mechTypes, String mechPrincipal) {

        // Create the mechTypes array
        m_mechTypes = new Oid[mechTypes.size()];
        for (int i = 0; i < mechTypes.size(); i++)
            m_mechTypes[i] = mechTypes.get(i);

        m_mecListMICPrincipal = mechPrincipal;
    }

    /**
     * Return the mechTypes OID list
     *
     * @return Oid[]
     */
    public final Oid[] getOids() {
        return m_mechTypes;
    }

    /**
     * Return the context flags
     *
     * @return int
     */
    public final int getContextFlags() {
        return m_contextFlags;
    }

    /**
     * Return the mechToken
     *
     * @return byte[]
     */
    public final byte[] getMechtoken() {
        return m_mechToken;
    }

    /**
     * Return the mechListMIC principal
     *
     * @return String
     */
    public final String getPrincipal() {
        return m_mecListMICPrincipal;
    }

    /**
     * Check if the OID list contains the specified OID
     *
     * @param oid Oid
     * @return boolean
     */
    public final boolean hasOid(Oid oid) {
        boolean foundOid = false;

        if (m_mechTypes != null)
            foundOid = oid.containedIn(m_mechTypes);

        return foundOid;
    }

    /**
     * Return the count of OIDs
     *
     * @return int
     */
    public final int numberOfOids() {
        return m_mechTypes != null ? m_mechTypes.length : 0;
    }

    /**
     * Return the specified OID
     *
     * @param idx int
     * @return OID
     */
    public final Oid getOidAt(int idx) {
        if (m_mechTypes != null && idx >= 0 && idx < m_mechTypes.length)
            return m_mechTypes[idx];
        return null;
    }

    /**
     * Check if the mech types list bytes is valid
     *
     * @return boolean
     */
    public final boolean hasMechListBytes() {
        return m_mechTypesByts != null ? true : false;
    }

    /**
     * Return the mech types list original object bytes
     *
     * @return byte[]
     */
    public final byte[] getMechListBytes() {
        return m_mechTypesByts;
    }

    /**
     * Decode an SPNEGO NegTokenInit blob
     *
     * @param secBlob SecurityBlob
     * @exception IOException Error decoding token
     */
    public void decode(SecurityBlob secBlob)
        throws IOException {

        decode( secBlob.getSecurityBlob(), secBlob.getSecurityOffset(), secBlob.getSecurityLength());
    }

    /**
     * Decode an SPNEGO NegTokenInit blob
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
        DERObject derObj = derBuf.unpackApplicationSpecific();

        if (derObj instanceof DEROid) {

            // Check that the OID indicates SPNEGO
            DEROid derOid = (DEROid) derObj;

            // The blob is already kerberos5, no need to parse
            if (derOid.getOid().equals(OID.ID_KERBEROS5)) {
                m_mechTypes = new Oid[]{OID.KERBEROS5};
                m_mechToken = buf;
                return;
            }

            if (derOid.getOid().equals(OID.ID_SPNEGO) == false)
                throw new IOException("Not an SPNEGO blob");

            // Get the remaining objects from the DER buffer
            derObj = derBuf.unpackObject();

            if (derObj instanceof DERSequence) {

                // Access the sequence, should be a sequence of tagged values
                DERSequence derSeq = (DERSequence) derObj;

                // Get the mechTypes list
                derObj = derSeq.getTaggedObject(0);
                if (derObj == null)
                    throw new IOException("No mechTypes list in blob");
                if (derObj instanceof DERSequence == false)
                    throw new IOException("Invalid mechTypes object");

                // Save a copy of the original mech types list bytes
                int objPos = derObj.getBufferPosition();

                if ( objPos > 0) {

                    // Position at the object data
                    derBuf.setPosition( objPos);
                    int objTyp = derBuf.unpackType();
                    int objLen = derBuf.unpackLength();

                    derBuf.setPosition( objPos);
                    m_mechTypesByts = derBuf.unpackBytes( objLen + 2);
                }

                // Unpack the OID list (required)
                DERSequence derOidSeq = (DERSequence) derObj;
                m_mechTypes = new Oid[derOidSeq.numberOfObjects()];
                int idx = 0;

                for (int i = 0; i < derOidSeq.numberOfObjects(); i++) {
                    derObj = derOidSeq.getObjectAt(i);
                    if (derObj instanceof DEROid) {
                        derOid = (DEROid) derObj;
                        try {
                            m_mechTypes[idx++] = new Oid(derOid.getOid());
                        }
                        catch (GSSException ex) {
                            throw new IOException("Bad mechType OID");
                        }
                    }
                }

                // Unpack the context flags (optional)
                derObj = derSeq.getTaggedObject(1);
                if (derObj != null) {

                    // Check the type
                    if (derObj instanceof DERBitString) {

                        // Get the bit flags
                        DERBitString derBitStr = (DERBitString) derObj;
                        m_contextFlags = derBitStr.intValue();
                    }
                }

                // Unpack the mechToken (required)
                derObj = derSeq.getTaggedObject(2);
                if (derObj == null)
                    throw new IOException("No mechToken in blob");
                if (derObj instanceof DEROctetString == false)
                    throw new IOException("Invalid mechToken object");

                DEROctetString derStr = (DEROctetString) derObj;
                m_mechToken = derStr.getValue();

                // Unpack the mechListMIC (optional)
/**
 derObj = derSeq.getTaggedObject( 3);

 if ( derObj != null) {

 // Check for the Microsoft format mechListMIC

 if ( derObj instanceof DERSequence) {
 }
 }
 **/
            } else
                throw new IOException("Bad object type in blob");
        } else
            throw new IOException("Invalid security blob");
    }

    /**
     * Encode an SPNEGO NegTokenInit blob
     *
     * @return byte[]
     * @exception IOException Error encoding the token
     */
    public byte[] encode()
            throws IOException {

        // Create the list of objects to be encoded
        List objList = new ArrayList();

        objList.add(new DEROid(OID.ID_SPNEGO));

        // Build the sequence of tagged objects
        DERSequence derSeq = new DERSequence();
        derSeq.setTagNo(0);

        // mechTypes sequence
        DERSequence mechTypesSeq = new DERSequence();
        mechTypesSeq.setTagNo(0);

        for (int i = 0; i < m_mechTypes.length; i++) {
            Oid mechType = m_mechTypes[i];
            mechTypesSeq.addObject(new DEROid(mechType.toString()));
        }

        derSeq.addObject(mechTypesSeq);

        // negHints sequence
        DERSequence negHintsSeq = new DERSequence();
        negHintsSeq.setTagNo( 3);

        DERGeneralString hintNameStr = new DERGeneralString( "not_defined_in_RFC4178@please_ignore");
        hintNameStr.setTagNo( 0);

        negHintsSeq.addObject( hintNameStr);
        derSeq.addObject( negHintsSeq);

        // Add the sequence to the object list
        objList.add(derSeq);

        // Pack the objects
        DERBuffer derBuf = new DERBuffer();

        derBuf.packApplicationSpecific(objList);

        // Return the packed negTokenInit blob
        return derBuf.getBytes();
    }

    /**
     * Return the NegTokenInit object as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[NegTokenInit ");

        if (m_mechTypes != null) {
            str.append("mechTypes=");
            for (int i = 0; i < m_mechTypes.length; i++) {
                str.append(m_mechTypes[i].toString());
                str.append(",");
            }
        }

        if (m_contextFlags != -1) {
            str.append(" context=0x");
            str.append(Integer.toHexString(m_contextFlags));
        }

        if (m_mechToken != null) {
            str.append(" token=");
            str.append(m_mechToken.length);
            str.append(" bytes");
        }

        if (m_mecListMICPrincipal != null) {
            str.append(" principal=");
            str.append(m_mecListMICPrincipal);
        }
        str.append("]");

        return str.toString();
    }
}
