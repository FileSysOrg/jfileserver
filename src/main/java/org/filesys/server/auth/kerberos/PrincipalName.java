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

import org.filesys.server.auth.asn.DERGeneralString;
import org.filesys.server.auth.asn.DERInteger;
import org.filesys.server.auth.asn.DERObject;
import org.filesys.server.auth.asn.DERSequence;
import org.filesys.util.StringList;

/**
 * Kerberos Principal Name Class
 *
 * @author gkspencer
 */
public class PrincipalName {

    // Name type and naem string(s)
    private int m_type;
    private StringList m_names;

    /**
     * Default constructor
     */
    public PrincipalName() {
        m_names = new StringList();
    }

    /**
     * Class constructor
     *
     * @param typ   int
     * @param names StringList
     */
    public PrincipalName(int typ, StringList names) {
        m_type = typ;
        m_names = names;
    }

    /**
     * Return the name type
     *
     * @return int
     */
    public final int getType() {
        return m_type;
    }

    /**
     * Return the name list
     *
     * @return StringList
     */
    public final StringList getNames() {
        return m_names;
    }

    /**
     * Parse an ASN.1 principal name
     *
     * @param derSeq DERSequence
     */
    public final void parsePrincipalName(DERSequence derSeq) {

        // Allocate the name list
        m_names = new StringList();

        // Enumerate the sequence
        for (int idx = 0; idx < derSeq.numberOfObjects(); idx++) {
            // Read an object
            DERObject derObj = (DERObject) derSeq.getObjectAt(idx);

            if (derObj != null && derObj.isTagged()) {
                switch (derObj.getTagNo()) {
                    // Type
                    case 0:
                        if (derObj instanceof DERInteger) {
                            DERInteger derInt = (DERInteger) derObj;
                            m_type = (int) derInt.getValue();
                        }
                        break;

                    // Principal name components
                    case 1:
                        if (derObj instanceof DERSequence) {
                            DERSequence derNames = (DERSequence) derObj;

                            for (int namIdx = 0; namIdx < derNames.numberOfObjects(); namIdx++) {
                                DERGeneralString derStr = (DERGeneralString) derNames.getObjectAt(namIdx);
                                m_names.addString(derStr.getValue());
                            }
                        }
                }
            }
        }
    }

    /**
     * Return the principal name as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Typ=");
        str.append(getType());
        str.append(",Names=");

        if (m_names != null)
            str.append(m_names);
        else
            str.append("null");
        str.append("]");

        return str.toString();
    }
}
