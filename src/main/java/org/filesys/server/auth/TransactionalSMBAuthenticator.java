/*
 * Copyright (C) 2018 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.server.auth;

import org.filesys.smb.server.SMBSrvException;
import org.filesys.smb.server.SMBSrvSession;

/**
 * Transactional SMB Authenticator Interface
 *
 * <p>Used by the SMB authenticator when the authentication processing needs to be wrapped in a transaction</p>
 *
 * @author gkspencer
 */
public interface TransactionalSMBAuthenticator {

    /**
     * Wrap authentication processing in a transaction
     *
     * @param sess SMBSrvSession
     * @param client ClientInfo
     * @param secBlob SecurityBlob
     * @return AuthStatus
     * @exception SMBSrvException SMB error
     */
    public ISMBAuthenticator.AuthStatus processSecurityBlobInTransaction(SMBSrvSession sess, ClientInfo client, SecurityBlob secBlob)
        throws SMBSrvException;
}
