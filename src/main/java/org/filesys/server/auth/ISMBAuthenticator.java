/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
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
package org.filesys.server.auth;

import org.filesys.server.SrvSession;
import org.filesys.server.core.SharedDevice;
import org.filesys.smb.server.SMBSrvException;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;

/**
 * SMB Authenticator Interface
 *
 * <p>
 * An authenticator is used by the SMB server to authenticate users when in user level access mode
 * and authenticate requests to connect to a share when in share level access.
 *
 * @author gkspencer
 * @author dward
 */
public interface ISMBAuthenticator {

    // SMB v1 LanMan/NTLM1
    public static final int STANDARD_PASSWORD_LEN = 24;
    public static final int STANDARD_CHALLENGE_LEN = 8;

    // Authentication status
    public enum AuthStatus {
        AUTHENTICATED       ( 0),
        GUEST_LOGON         ( 1),

        MORE_PROCESSING     ( 2),

        DISALLOW            ( -1),
        BAD_PASSWORD        ( -2),
        BAD_USER            ( -3),
        PASSWORD_EXPIRED    ( -4),
        ACCOUNT_DISABLED    ( -5),
        UNSUPPORTED         ( -6);

        private final int authSts;

        /**
         * Enum constructor
         *
         * @param sts int
         */
        AuthStatus( int sts) { authSts = sts; }

        /**
         * Return the enum value as an int
         *
         * @return int
         */
        public final int intValue() { return authSts; }

        /**
         * Determine if the authentication status is an error status
         *
         * @return boolean
         */
        public final boolean isError() {
            return authSts < 0 ? true : false;
        }
    }

    // Share connection status
    public enum ShareStatus {
        NO_ACCESS,
        READ_ONLY,
        WRITEABLE;
    }

    // Password algorithms (SMB v1)
    public enum PasswordAlgorithm {
        LANMAN,
        NTLM1,
        NTLM2;
    }

    // Authentication mode
    public enum AuthMode {
        SHARE,
        USER;
    }

    /**
     * Authenticate a connection to a share.
     *
     * @param client   User/client details from the tree connect request.
     * @param share    Shared device the client wants to connect to.
     * @param sharePwd Share password.
     * @param sess     Server session.
     * @return ShareStatus
     */
    public ShareStatus authenticateShareConnect(ClientInfo client, SharedDevice share, String sharePwd, SrvSession sess);

    /**
     * Authenticate a user. A user may be granted full access, guest access or no access.
     *
     * @param client User/client details from the session setup request.
     * @param sess   Server session
     * @param alg    Encryption algorithm
     * @return AuthStatus Access level or disallow status.
     */
    public AuthStatus authenticateUser(ClientInfo client, SrvSession sess, PasswordAlgorithm alg);

    /**
     * Return the access mode of the server, either SHARE_MODE or USER_MODE.
     *
     * @return AuthMode
     */
    public AuthMode getAccessMode();

    /**
     * Determine if extended security methods are available
     *
     * @return boolean
     */
    public boolean hasExtendedSecurity();

    /**
     * Return the security mode flags
     *
     * @return int
     */
    public int getSecurityMode();

    /**
     * Process the SMB session setup request packet and build the session setup response
     *
     * @param sess   SMBSrvSession
     * @param reqPkt SMBSrvPacket
     * @exception SMBSrvException SMB error
     */
    public void processSessionSetup(SMBSrvSession sess, SMBSrvPacket reqPkt) throws SMBSrvException;

    /**
     * Process an authentication security blob which may return a security blob to the client or complete the
     * authentication process
     *
     * @param sess SMBSrvSession
     * @param client ClientInfo
     * @param secBlob SecurityBlob
     * @return AuthStatus
     * @exception SMBSrvException SMB error
     */
    public AuthStatus processSecurityBlob(SMBSrvSession sess, ClientInfo client, SecurityBlob secBlob)
        throws SMBSrvException;

    /**
     * Return the encryption key/challenge length
     *
     * @return int
     */
    public int getEncryptionKeyLength();

    /**
     * Return the server capability flags
     *
     * @return int
     */
    public int getServerCapabilities();

    /**
     * Close the authenticator, perform any cleanup
     */
    public void closeAuthenticator();

    /**
     * Set the current authenticated user context for this thread
     *
     * @param client ClientInfo
     */
    public void setCurrentUser(ClientInfo client);

    /**
     * Return the authentication context for the specified session
     *
     * @param sess SMBSrvSession
     * @return AuthContext
     */
    public AuthContext getAuthContext(SMBSrvSession sess);

    /**
     * Determine if the authentication is using SPNEGO
     *
     * @return boolean
     */
    public boolean usingSPNEGO();

    /**
     * Return the SPNEGO NegTokenInit token, or null if not supported
     *
     * @return byte[]
     */
    public byte[] getNegTokenInit();
}