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
public class HazelCastAccessToken implements Serializable, FileAccessToken {

    // Serialization id
    private static final long serialVersionUID = 5L;

    //	Cluster node that owns the token
    private String m_ownerName;

    // Process id that owns the file
    private int m_pid;

    // Granted oplock type, if requested, and flag to indicate if the oplock is not available
    private OpLockType m_oplock;
    private boolean m_oplockNotAvailable;

    // Associated network file path
    private String m_path;

    // Attributes only file access
    private boolean m_attribOnly;

    // Access token has been released
    private transient boolean m_released = false;

    /**
     * Default constructor
     */
    public HazelCastAccessToken() {
        setReleased(true);
    }

    /**
     * Class constructor
     *
     * @param clName String
     * @param pid    int
     */
    protected HazelCastAccessToken(String clName, int pid) {
        m_ownerName = clName;
        m_pid = pid;
    }

    /**
     * Class constructor
     *
     * @param clNode ClusterNode
     * @param pid    int
     */
    protected HazelCastAccessToken(ClusterNode clNode, int pid) {
        m_ownerName = clNode.getName();
        m_pid = pid;
    }

    /**
     * Class constructor
     *
     * @param clName         String
     * @param pid            int
     * @param oplock         OpLockType
     * @param oplockNotAvail boolean
     */
    protected HazelCastAccessToken(String clName, int pid, OpLockType oplock, boolean oplockNotAvail) {
        m_ownerName = clName;
        m_pid = pid;
        m_oplock = oplock;
        m_oplockNotAvailable = oplockNotAvail;
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
     * Return the process id
     *
     * @return int
     */
    public final int getProcessId() {
        return m_pid;
    }

    /**
     * Check if the oplock was available
     *
     * @return boolean
     */
    public final boolean isOplockAvailable() {
        return m_oplockNotAvailable ? false : true;
    }

    /**
     * Return the oplock type
     *
     * @return OpLockType
     */
    public final OpLockType getOpLockType() {
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
     * Check if the access token has been released
     *
     * @return boolean
     */
    public final boolean isReleased() {
        return m_released;
    }

    /**
     * Set the released state of the access token
     *
     * @param released boolean
     */
    public final void setReleased(boolean released) {
        m_released = released;
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
     * Check if the access token is on attributes only file open
     *
     * @return boolean
     */
    public final boolean isAttributesOnly() {
        return m_attribOnly;
    }

    /**
     * Set/clear the attributes only flag
     *
     * @param attrOnly boolean
     */
    public final void setAttributesOnly(boolean attrOnly) {
        m_attribOnly = attrOnly;
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
        str.append(",pid=");
        str.append(getProcessId());
        if (getOpLockType() != OpLockType.LEVEL_NONE) {
            str.append(",oplock=");
            str.append(getOpLockType().name());
        } else {
            str.append(",opavail=");
            str.append(isOplockAvailable());
        }

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
        if (isReleased() == false)
            Debug.println("** Access token finalized, not released, " + toString() + " **");
    }
}
