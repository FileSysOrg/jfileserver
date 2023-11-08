/*
 * Copyright (C) 2023 GK Spencer
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

package org.filesys.server.filesys.cache.hazelcast;

import org.filesys.server.filesys.FileAccessToken;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;

import java.io.Serializable;

/**
 * Grant Access Task Response Class
 *
 * <p>Contains the granted file access token and updated cluster file state</p>
 *
 * @author gkspencer
 */
public class GrantAccessResponse implements Serializable {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Granted file access token
    private HazelCastAccessToken m_token;

    // Updated cluster file state
    private ClusterFileState m_clState;

    /**
     * Class constructor
     *
     * @param token HazelcastAccessToken
     * @param state ClusterFileState
     */
    public GrantAccessResponse(HazelCastAccessToken token, ClusterFileState state) {
        m_token = token;
        m_clState = state;
    }

    /**
     * Return the granted file access token
     *
     * @return HazelCastAccessToken
     */
    public final HazelCastAccessToken getAccessToken() {
        return m_token;
    }

    /**
     * Return the updated cluster file state
     *
     * @return ClusterFileState
     */
    public final ClusterFileState getFileState() {
        return m_clState;
    }

    /**
     * Return the grant access response as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Token=");
        str.append(m_token);
        str.append(", state=");
        str.append(m_clState);
        str.append("]");

        return str.toString();
    }
}
