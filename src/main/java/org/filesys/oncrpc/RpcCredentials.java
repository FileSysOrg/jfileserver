/*
 * Copyright (C) 2020 GK Spencer
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

package org.filesys.oncrpc;

import java.util.Arrays;

/**
 * ONC/RPC Credentials Data Base Class
 *
 * <p>Base class for various RPC credentials data types</p>
 *
 * @author gkspencer
 */
public class RpcCredentials {

    // Authentication type
    private AuthType m_type;

    // Credentials data copied from the RPC header
    private byte[] m_data;

    /**
     * Class constructor
     *
     * @param authType AuthType
     */
    protected RpcCredentials( AuthType authType) {
        m_type = authType;
    }

    /**
     * Class constructor
     *
     *
     * @param authType AuthType
     * @param bytData byte[]
     */
    protected RpcCredentials( AuthType authType, byte[] bytData) {
        m_type = authType;
        m_data = bytData;
    }

    /**
     * Class constructor
     *
     * @param authType AuthType
     * @param bytData byte[]
     * @param offset int
     * @param len int
     */
    protected RpcCredentials( AuthType authType, byte[] bytData, int offset, int len) {
        m_type = authType;

        if ( len > 0) {
            m_data = new byte[len];
            System.arraycopy(bytData, offset, m_data, 0, len);
        }
    }

    /**
     * Return the authentication type
     *
     * @return AuthType
     */
    public final AuthType isType() { return m_type; }

    /**
     * Check if the credentials has raw data bytes
     *
     * @return boolean
     */
    public final boolean hasDataBytes() { return m_data != null; }

    /**
     * Return the raw credentials data bytes
     *
     * @return byte[]
     */
    public final byte[] getDataBytes() { return m_data; }

    /**
     * Compare credentials
     *
     * @param rpcCreds RpcCredentials
     * @return boolean
     */
    public boolean compareCredentials( RpcCredentials rpcCreds) {

        // Check if the credentials types match
        if ( isType() == rpcCreds.isType()) {

            // Compare the data bytes, if available
            if ( hasDataBytes()) {

                // Check the data bytes, if available
                if ( rpcCreds.hasDataBytes()) {

                    // Check if the credentials raw data matches
                    return Arrays.equals( getDataBytes(), rpcCreds.getDataBytes());
                }
                else
                    return false;
            }
            else if ( rpcCreds.hasDataBytes())
                return false;
        }

        // Not the same credentials types
        return false;
    }

    /**
     * Return the RPC credentials details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append( "[");
        str.append( "AuthType=");
        str.append( isType().name());

        if ( hasDataBytes()) {
            str.append( ", raw data len=");
            str.append( m_data.length);
        }
        str.append( "]");

        return str.toString();
    }
}
