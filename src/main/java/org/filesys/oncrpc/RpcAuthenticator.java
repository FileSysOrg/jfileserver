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

package org.filesys.oncrpc;

import org.filesys.server.SrvSession;
import org.filesys.server.auth.ClientInfo;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.springframework.extensions.config.ConfigElement;


/**
 * RPC Authenticator Interface
 *
 * <p>Provides authentication support for ONC/RPC requests.
 *
 * @author gkspencer
 */
public interface RpcAuthenticator {

    /**
     * Initialize the RPC authenticator
     *
     * @param config ServerConfiguration
     * @param params ConfigElement
     * @exception InvalidConfigurationException Failed to initialize the authenticator
     */
    public void initialize(ServerConfiguration config, ConfigElement params)
            throws InvalidConfigurationException;

    /**
     * Authenticate an RPC client using the credentials within the RPC request. The object that is returned is
     * used as the key to find the associated session object.
     *
     * @param authType int
     * @param rpc      RpcPacket
     * @return Object
     * @exception RpcAuthenticationException Authentication error
     */
    public Object authenticateRpcClient(int authType, RpcPacket rpc)
            throws RpcAuthenticationException;

    /**
     * Get RPC client information from the RPC request.
     *
     * <p>This method is called when a new session object is created by an RPC server.
     *
     * @param sessKey Object
     * @param rpc     RpcPacket
     * @return ClientInfo
     */
    public ClientInfo getRpcClientInformation(Object sessKey, RpcPacket rpc);

    /**
     * Return a list of the authentication types that the RPC authenticator implementation supports. The
     * authentication types are specified in the AuthType class.
     *
     * @return int[]
     */
    public int[] getRpcAuthenticationTypes();

    /**
     * Set the current authenticated user context for processing of the current RPC request
     *
     * @param sess   SrvSession
     * @param client ClientInfo
     */
    public void setCurrentUser(SrvSession sess, ClientInfo client);
}
