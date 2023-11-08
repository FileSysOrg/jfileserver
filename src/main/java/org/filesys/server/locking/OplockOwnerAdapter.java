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

package org.filesys.server.locking;

import org.filesys.smb.ImpersonationLevel;
import org.filesys.smb.OpLockType;
import org.filesys.smb.server.SMBSrvSession;

import java.io.Serializable;

/**
 * Oplock Owner Adapter Class
 *
 * @author gkspencer
 */
public abstract class OplockOwnerAdapter implements OplockOwner, Serializable {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Owner session
    private transient SMBSrvSession m_ownerSess;

    // Oplock owner details
    private long m_sessId;
    private int m_treeId;
    private int m_userId;
    private int m_processId;

    // Owner node id, for cluster environment
    private String m_ownerNode;

    /**
     * Class constructor
     *
     * @param sess SMBSrvSession
     * @param sessId long
     * @param treeId int
     */
    protected OplockOwnerAdapter( SMBSrvSession sess, long sessId, int treeId) {
        m_ownerSess = sess;
        m_sessId = sessId;
        m_treeId = treeId;
    }

    /**
     * Class constructor
     *
     * @param sess SMBSrvSession
     * @param treeId int
     * @param procId int
     * @param userId int
     */
    protected OplockOwnerAdapter( SMBSrvSession sess, int treeId, int procId, int userId) {
        m_ownerSess = sess;
        m_treeId = treeId;
        m_userId = userId;
        m_processId = procId;
    }

    /**
     * Check if the oplock owner matches this oplock owner for the type of oplock
     *
     * @param opType    OplockType
     * @param opOwner   OplockOwner
     * @return boolean
     */
    public abstract boolean isOwner(OpLockType opType, OplockOwner opOwner);

    /**
     * Return the session that owns the oplock
     *
     * @return SMBSrvSession
     */
    public SMBSrvSession getSession() { return m_ownerSess; }

    /**
     * Return a short unique id string for the oplock owner
     *
     * @return String
     */
    public abstract String getUniqueId();

    /**
     * Return the owner node id, for cluster environment
     *
     * @return String
     */
    public String getOwnerNode() { return m_ownerNode; }

    /**
     * Return the owner session id
     *
     * @return int
     */
    public final long getSessionId() {
        return m_sessId;
    }

    /**
     * Return the tree id
     *
     * @return int
     */
    public final int getTreeId() {
        return m_treeId;
    }

    /**
     * Return the process id
     *
     * @return int
     */
    public final int getProcessId() {
        return m_processId;
    }

    /**
     * Return the user id
     *
     * @return int
     */
    public final int getUserId() {
        return m_userId;
    }

    /**
     * Set the owner node id, for cluster environment
     *
     * @param nodeId String
     */
    public void setOwnerNode(String nodeId) { m_ownerNode = nodeId; }
}
