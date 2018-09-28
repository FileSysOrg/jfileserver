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

package org.filesys.server.auth.passthru;

import org.filesys.server.SrvSession;

/**
 * Passthru Details Class
 *
 * <p>Contains the details of a passthru connection to a remote server and the local session that the
 * request originated from.
 *
 * @author gkspencer
 */
public class PassthruDetails {

    //	Server session
    private SrvSession m_sess;

    //	Authentication session connected to the remote server
    private AuthenticateSession m_authSess;

    /**
     * Class constructor
     *
     * @param sess     SrvSession
     * @param authSess AuthenticateSession
     */
    public PassthruDetails(SrvSession sess, AuthenticateSession authSess) {
        m_sess = sess;
        m_authSess = authSess;
    }

    /**
     * Return the session details
     *
     * @return SrvSession
     */
    public final SrvSession getSession() {
        return m_sess;
    }

    /**
     * Return the authentication session that is connected to the remote server
     *
     * @return AuthenticateSession
     */
    public final AuthenticateSession getAuthenticateSession() {
        return m_authSess;
    }
}
