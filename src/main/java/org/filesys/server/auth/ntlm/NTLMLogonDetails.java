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

package org.filesys.server.auth.ntlm;

import java.io.Serializable;
import java.util.Date;

/**
 * NTLM Logon Details Class
 *
 * <p>Contains the details from the NTLM authentication session that was used to authenticate a user.
 *
 * @author gkspencer
 */
public class NTLMLogonDetails implements Serializable {
    // User name, workstation name and domain
    private String m_user;
    private String m_workstation;
    private String m_domain;

    // Authentication server name/address
    private String m_authSrvAddr;

    // Date/time the user was authenticated
    private long m_authTime;

    // User logged on via guest access
    private boolean m_guestAccess;

    // Cached type 2 NTLM message and copy of the NTLM hash used to successfully logon
    private Type2NTLMMessage m_type2Msg;
    private byte[] m_ntlmHash;

    // Authentication token object
    private Object m_authToken;

    /**
     * Default constructor
     */
    public NTLMLogonDetails() {
    }

    /**
     * Class constructor
     *
     * @param user    String
     * @param wks     String
     * @param domain  String
     * @param guest   boolean
     * @param authSrv String
     */
    public NTLMLogonDetails(String user, String wks, String domain, boolean guest, String authSrv) {
        setDetails(user, wks, domain, guest, authSrv);
    }

    /**
     * Return the user name
     *
     * @return String
     */
    public final String getUserName() {
        return m_user;
    }

    /**
     * Return the workstation name
     *
     * @return String
     */
    public final String getWorkstation() {
        return m_workstation;
    }

    /**
     * Return the domain name
     *
     * @return String
     */
    public final String getDomain() {
        return m_domain;
    }

    /**
     * Return the authentication server name/address
     *
     * @return String
     */
    public final String getAuthenticationServer() {
        return m_authSrvAddr;
    }

    /**
     * Return the date/time the user was authenticated
     *
     * @return long
     */
    public final long authenticatedAt() {
        return m_authTime;
    }

    /**
     * Determine if the type 2 NTLM message has been cached
     *
     * @return boolean
     */
    public final boolean hasType2Message() {
        return m_type2Msg != null ? true : false;
    }

    /**
     * Return the cached type 2 NTLM message
     *
     * @return Type2NTLMMessage
     */
    public final Type2NTLMMessage getType2Message() {
        return m_type2Msg;
    }

    /**
     * Determine if there is a cached NTLM hashed password
     *
     * @return boolean
     */
    public final boolean hasNTLMHashedPassword() {
        return m_ntlmHash != null ? true : false;
    }

    /**
     * Return the cached NTLM hashed password
     *
     * @return byte[]
     */
    public final byte[] getNTLMHashedPassword() {
        return m_ntlmHash;
    }

    /**
     * Return the challenge key from the type2 message
     *
     * @return byte[]
     */
    public final byte[] getChallengeKey() {
        if (m_type2Msg != null)
            return m_type2Msg.getChallenge();
        return null;
    }

    /**
     * Determine if there is a valid authentication token
     *
     * @return boolean
     */
    public final boolean hasAuthenticationToken() {
        return m_authToken != null ? true : false;
    }

    /**
     * Return the authentication token
     *
     * @return Object
     */
    public final Object getAuthenticationToken() {
        return m_authToken;
    }

    /**
     * Set the authentication token
     *
     * @param token Object
     */
    public final void setAuthenticationToken(Object token) {
        m_authToken = token;
    }

    /**
     * Set the authentication date/time
     *
     * @param authTime long
     */
    public final void setAuthenticatedAt(long authTime) {
        m_authTime = authTime;
    }

    /**
     * Set the client details
     *
     * @param user    String
     * @param wks     String
     * @param domain  String
     * @param guest   boolean
     * @param authSrv String
     */
    public final void setDetails(String user, String wks, String domain, boolean guest, String authSrv) {
        m_user = user;
        m_workstation = wks;
        m_domain = domain;

        m_authSrvAddr = authSrv;

        m_guestAccess = guest;

        m_authTime = System.currentTimeMillis();
    }

    /**
     * Set the type 2 NTLM message
     *
     * @param type2 Type2NTLMMessage
     */
    public final void setType2Message(Type2NTLMMessage type2) {
        m_type2Msg = type2;
    }

    /**
     * Set the cached NTLM hashed password
     *
     * @param ntlmHash byte[]
     */
    public final void setNTLMHashedPassword(byte[] ntlmHash) {
        m_ntlmHash = ntlmHash;
    }

    /**
     * Return the NTLM logon details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(getUserName());
        str.append(",Wks:");
        str.append(getWorkstation());
        str.append(",Dom:");
        str.append(getDomain());
        str.append(",AuthSrv:");
        str.append(getAuthenticationServer());
        str.append(",");
        str.append(new Date(authenticatedAt()));

        str.append("]");

        return str.toString();
    }
}
