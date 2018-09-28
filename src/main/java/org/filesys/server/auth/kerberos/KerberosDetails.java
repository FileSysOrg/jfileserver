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

import org.ietf.jgss.GSSName;

/**
 * Kerberos Details Class
 *
 * <p>Holds the Kerberos response token and session details about the user.
 *
 * @author gkspencer
 */
public class KerberosDetails {

    // Source and target details
    private String m_krbSource;
    private String m_krbTarget;

    // Kerberos response token
    private byte[] m_krbResponse;

    /**
     * Class constructor
     *
     * @param source   GSSName
     * @param target   GSSName
     * @param response byte[]
     */
    public KerberosDetails(GSSName source, GSSName target, byte[] response) {

        m_krbSource = source.toString();
        m_krbTarget = target.toString();

        m_krbResponse = response;
    }

    /**
     * Return the context initiator for the Kerberos authentication
     *
     * @return String
     */
    public final String getSourceName() {

        return m_krbSource;
    }

    /**
     * Return the context acceptor for the Kerberos authentication
     *
     * @return String
     */
    public final String getTargetName() {

        return m_krbTarget;
    }

    /**
     * Return the Kerberos response token
     *
     * @return byte[]
     */
    public final byte[] getResponseToken() {

        return m_krbResponse;
    }

    /**
     * Set the response token
     *
     * @param tok byte[]
     */
    public final void setResponseToken(byte[] tok) {
        m_krbResponse = tok;
    }

    /**
     * Parse the source name to return the user name part only
     *
     * @return String
     */
    public final String getUserName() {

        String userName = m_krbSource;

        if (m_krbSource != null) {
            int pos = m_krbSource.indexOf('@');
            if (pos != -1) {
                userName = m_krbSource.substring(0, pos);
            }
        }

        return userName;
    }

    /**
     * Return the response token length
     *
     * @return int
     */
    public final int getResponseLength() {

        return m_krbResponse != null ? m_krbResponse.length : 0;
    }

    /**
     * Return the Kerberos authentication details as a string
     *
     * @return String
     */
    public String toString() {

        StringBuffer str = new StringBuffer();

        str.append("[Source=");
        str.append(getSourceName());
        str.append(",Target=");
        str.append(getTargetName());
        str.append(":Response=");
        str.append(getResponseLength());
        str.append(" bytes]");

        return str.toString();
    }
}
