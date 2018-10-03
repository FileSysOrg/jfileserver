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

package org.filesys.server.auth;

import org.filesys.server.SrvSession;
import org.filesys.server.core.ShareType;
import org.filesys.server.core.SharedDevice;
import org.filesys.smb.server.SMBSrvSession;

/**
 * Local Authenticator Class.
 *
 * <p>The local authenticator implementation enables user level security mode and uses the
 * user account list that is part of the server configuration to determine if a user is allowed
 * to access the server/share.
 *
 * <p>Note: Switching off encrypted password support will cause later NT4 service pack releases and
 * Win2000 to refuse to connect to the server without a registry update on the client.
 *
 * @author gkspencer
 */
public class LocalAuthenticator extends SMBAuthenticator {

    /**
     * Local Authenticator Constructor
     *
     * <p>Default to user mode security with encrypted password support.
     */
    public LocalAuthenticator() {
        setAccessMode(AuthMode.USER);
        setExtendedSecurity(false);
    }

    @Override
    public ShareStatus authenticateShareConnect(ClientInfo client, SharedDevice share, String pwd, SrvSession sess) {

        //	If the server is in share mode security allow the user access
        if (getAccessMode() == AuthMode.SHARE)
            return ShareStatus.WRITEABLE;

        //	Check if the IPC$ share is being accessed
        if (share.getType() == ShareType.ADMINPIPE)
            return ShareStatus.WRITEABLE;

        //	Check if the user is allowed to access the specified shared device
        //
        //	If a user does not have access to the requested share the connection will still be allowed
        //	but any attempts to access files or search directories will result in a 'no access rights'
        //	error being returned to the client.
        UserAccount user = null;
        if (client != null)
            user = getUserDetails(client.getUserName());

        if (user == null) {

            //	Check if the guest account is enabled
            return allowGuest() ? ShareStatus.WRITEABLE : ShareStatus.NO_ACCESS;
        }
        else if (user.hasShare(share.getName()) == false)
            return ShareStatus.NO_ACCESS;

        //	Allow user to access this share
        return ShareStatus.WRITEABLE;
    }

    @Override
    public AuthStatus authenticateUser(ClientInfo client, SrvSession sess, PasswordAlgorithm alg) {

        //	Check if the user exists in the user list
        UserAccount userAcc = getUserDetails(client.getUserName());
        if (userAcc != null) {

            //	Validate the password
            boolean authSts = false;

            if (client.getPassword() != null) {

                //	Validate using the Unicode password
                authSts = validatePassword(userAcc, client, sess.getAuthenticationContext(), alg);
            } else if (client.hasANSIPassword()) {

                //	Validate using the ANSI password with the LanMan encryption
                authSts = validatePassword(userAcc, client, sess.getAuthenticationContext(), PasswordAlgorithm.LANMAN);
            }

            //	Return the authentication status
            return authSts == true ? AuthStatus.AUTHENTICATED : AuthStatus.BAD_PASSWORD;
        }

        //	Check if this is an SMB null session logon.
        //
        //	The null session will only be allowed to connect to the IPC$ named pipe share.
        if (client.isNullSession() && sess instanceof SMBSrvSession)
            return AuthStatus.AUTHENTICATED;

        //	Unknown user
        return allowGuest() ? AuthStatus.GUEST_LOGON : AuthStatus.DISALLOW;
    }
}
