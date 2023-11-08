/*
 * Copyright (C) 2006-2011 Alfresco Software Limited.
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

package org.filesys.server.filesys.cache.hazelcast;

import java.io.Serializable;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.FileAccessToken;
import org.filesys.server.filesys.cache.cluster.ClusterNode;
import org.filesys.smb.OpLockType;

/**
 * HazelCast Access Token Class
 *
 * <p>File access token used by the grantFileAccess()/releaseFileAccess() methods of the clustered state cache
 *
 * @author gkspencer
 */
public class HazelCastAccessToken extends FileAccessToken implements Serializable {

    // Serialization id
    private static final long serialVersionUID = 6L;

    //	Cluster node that owns the token
    private String m_ownerName;

    // Available oplock type, OpLockType.INVALID indicates an oplock is not available
    private OpLockType m_oplock;

    // Associated network file path
    private String m_path;

    /**
     * Default constructor
     */
    public HazelCastAccessToken() {
        setReleased(true);
    }

    /**
     * Class constructor
     *
     * @param clName         String
     * @param params         GrantAccessParams
     * @param oplock         OpLockType
     */
    protected HazelCastAccessToken(String clName, GrantAccessParams params, OpLockType oplock) {
        super( params.getProcessId(), params.getAccessMode(), params.getSharedAccess(), params.isAttributesOnlyAccess());

        m_ownerName = clName;
        m_oplock = oplock;
    }

    /**
     * Return the owner name
     *
     * @return String
     */
    public final String getOwnerName() {
        return m_ownerName;
    }

    /**
     * Return the available oplock type
     *
     * @return OpLockType
     */
    public final OpLockType getAvailableOpLockType() {
        return m_oplock;
    }

    /**
     * Set the oplock type granted
     *
     * @param oplock OpLockType
     */
    public final void setOpLockType(OpLockType oplock) {
        m_oplock = oplock;
    }

    /**
     * Return the associated network file path
     *
     * @return String
     */
    public final String getNetworkFilePath() {
        return m_path;
    }

    /**
     * Set the associated network file path
     *
     * @param path String
     */
    public final void setNetworkFilePath(String path) {
        m_path = path;
    }

    /**
     * Check for equality
     *
     * @return boolean
     */
    public boolean equals(Object obj) {

        boolean eq = false;

        if ( super.equals( obj)) {
            if (obj instanceof HazelCastAccessToken) {
                HazelCastAccessToken token = (HazelCastAccessToken) obj;

                // Need to check the cluster node id
                if ( token.getOwnerName().equals( getOwnerName()))
                    eq = true;
            }
        }

        return eq;
    }


    /**
     * Return the access token as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Token owner=");
        str.append(getOwnerName());
        str.append(",owner=");
        str.append( getOwnerId());

        str.append(",access=0x");
        str.append( Integer.toHexString( getAccessMode()));
        str.append(",sharing=");
        str.append( getSharedAccess());

        str.append(",oplock=");
        str.append(getAvailableOpLockType());

        if (isAttributesOnly())
            str.append(",AttribOnly");

        if (isReleased())
            str.append(",Released");
        else {
            str.append(",File=");
            str.append(getNetworkFilePath());
        }
        str.append("]");

        return str.toString();
    }

    /**
     * Finalize
     */
    public void finalize() {

        // Check if hte access token was released
        if ( !isReleased())
            Debug.println("** Access token finalized, not released, " + toString() + " **");
    }
}
