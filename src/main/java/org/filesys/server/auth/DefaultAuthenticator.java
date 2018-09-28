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

package org.filesys.server.auth;

import org.filesys.server.SrvSession;
import org.filesys.server.core.SharedDevice;

/**
 * Default Authenticator class
 *
 * <p>The default authenticator implementation enables user level security mode and allows
 * any user to connect to the server.
 *
 * @author gkspencer
 */
public class DefaultAuthenticator extends SMBAuthenticator {

    /**
     * Class constructor
     */
    public DefaultAuthenticator() {
        setAccessMode(AuthMode.USER);
    }

    @Override
    public ShareStatus authenticateShareConnect(ClientInfo client, SharedDevice share, String pwd, SrvSession sess) {
        return ShareStatus.WRITEABLE;
    }

    @Override
    public AuthStatus authenticateUser(ClientInfo client, SrvSession sess, PasswordAlgorithm alg) {
        return AuthStatus.AUTHENTICATED;
    }
}
