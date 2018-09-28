/*
 * Copyright (C) 2006-2011 Alfresco Software Limited.
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

import java.io.IOException;
import java.io.Serializable;

import org.filesys.server.filesys.DeferFailedException;
import org.filesys.server.filesys.cache.cluster.ClusterFileStateCache;
import org.filesys.server.filesys.cache.cluster.ClusterNode;
import org.filesys.server.filesys.cache.cluster.PerNodeState;
import org.filesys.server.locking.OpLockDetails;
import org.filesys.server.locking.OpLockDetailsAdapter;
import org.filesys.smb.OpLockType;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;

/**
 * Remote OpLock Details Class
 *
 * <p>Contains the oplock details that need to be stored in the cluster cache, the actual oplock
 * details will be stored locally on the node that owns the oplock.
 *
 * @author gkspencer
 */
public class RemoteOpLockDetails extends OpLockDetailsAdapter implements Serializable {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Oplock type
    private OpLockType m_lockType;

    // Owner node name
    private String m_ownerName;

    // Oplocked path
    private String m_path;

    // State cache that this oplock belongs to
    private transient ClusterFileStateCache m_stateCache;

    /**
     * Default constructor
     */
    public RemoteOpLockDetails() {
    }

    /**
     * Class constructor
     *
     * @param clNode     ClusterNode
     * @param lockTyp    OpLockType
     * @param path       String
     * @param stateCache ClusterFileStateCache
     */
    protected RemoteOpLockDetails(ClusterNode clNode, OpLockType lockTyp, String path, ClusterFileStateCache stateCache) {
        m_ownerName = clNode.getName();
        m_lockType = lockTyp;
        m_path = path;

        m_stateCache = stateCache;
    }

    /**
     * Class constructor
     *
     * @param clNode      ClusterNode
     * @param localOpLock OpLockDetails
     * @param stateCache  ClusterFileStateCache
     */
    protected RemoteOpLockDetails(ClusterNode clNode, OpLockDetails localOpLock, ClusterFileStateCache stateCache) {
        m_ownerName = clNode.getName();
        m_lockType = localOpLock.getLockType();
        m_path = localOpLock.getPath();

        m_stateCache = stateCache;
    }

    /**
     * Class constructor
     *
     * @param ownerName  String
     * @param lockTyp    OpLockType
     * @param path       String
     * @param stateCache ClusterFileStateCache
     */
    protected RemoteOpLockDetails(String ownerName, OpLockType lockTyp, String path, ClusterFileStateCache stateCache) {
        m_ownerName = ownerName;
        m_lockType = lockTyp;
        m_path = path;

        m_stateCache = stateCache;
    }

    /**
     * Return the oplock type
     *
     * @return OpLockType
     */
    public OpLockType getLockType() {
        return m_lockType;
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
     * Return the share relative path of the locked file
     *
     * @return String
     */
    public String getPath() {
        return m_path;
    }

    /**
     * Check if this is a remote oplock
     *
     * @return boolean
     */
    public boolean isRemoteLock() {
        return true;
    }

    /**
     * Request an oplock break
     *
     * @throws IOException I/O error
     */
    public void requestOpLockBreak()
            throws IOException {

        throw new IOException("Attempt to break remote oplock, owner=" + getOwnerName() + ", type=" + getLockType().name());
    }

    /**
     * Check if there is a deferred session attached to the oplock, this indicates an oplock break is
     * in progress for this oplock.
     *
     * @return boolean
     */
    public boolean hasDeferredSessions() {
        boolean hasDefer = false;

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(getPath(), false);
            if (perNode != null)
                hasDefer = perNode.hasDeferredSessions();
        }
        return hasDefer;
    }

    /**
     * Return the count of deferred requests
     *
     * @return int
     */
    public int numberOfDeferredSessions() {
        int deferCnt = 0;

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(getPath(), false);
            if (perNode != null)
                deferCnt = perNode.numberOfDeferredSessions();
        }
        return deferCnt;
    }

    /**
     * Add a deferred session/packet, whilst an oplock break is in progress
     *
     * @param deferredSess SMBSrvSession
     * @param deferredPkt  SMBSrvPacket
     * @throws DeferFailedException If the session/packet cannot be deferred
     */
    public void addDeferredSession(SMBSrvSession deferredSess, SMBSrvPacket deferredPkt)
            throws DeferFailedException {

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(getPath(), false);
            if (perNode != null)
                perNode.addDeferredSession(deferredSess, deferredPkt);
        }
    }

    /**
     * Update the deferred packet lease time(s) as we wait for an oplock break or timeout
     */
    public void updateDeferredPacketLease() {
        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(getPath(), false);
            if (perNode != null)
                perNode.updateDeferredPacketLease();
        }
    }

    /**
     * Requeue deferred requests to the thread pool for processing, oplock has been released
     *
     * @return int Number of deferred requests requeued
     */
    public int requeueDeferredRequests() {
        int requeueCnt = 0;

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(getPath(), false);
            if (perNode != null)
                requeueCnt = perNode.requeueDeferredRequests();
        }
        return requeueCnt;
    }

    /**
     * Fail any deferred requests that are attached to this oplock, and clear the deferred list
     *
     * @return int Number of deferred requests that were failed
     */
    public int failDeferredRequests() {
        int failCnt = 0;

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(getPath(), false);
            if (perNode != null)
                failCnt = perNode.failDeferredRequests();
        }
        return failCnt;
    }

    /**
     * Return the time that the oplock break was sent to the client
     *
     * @return long
     */
    public long getOplockBreakTime() {
        long breakTime = 0L;

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(getPath(), true);
            if (perNode != null)
                breakTime = perNode.getOplockBreakTime();
        }

        return breakTime;
    }

    /**
     * Check if this oplock is still valid, or an oplock break has failed
     *
     * @return boolean
     */
    public boolean hasOplockBreakFailed() {
        return false;
    }

    /**
     * Set the failed oplock break flag, to indicate the client did not respond to the oplock break
     * request within a reasonable time.
     */
    public void setOplockBreakFailed() {
    }

    /**
     * Get the state cache that this state belongs to
     *
     * @return ClusterFileStateCache
     */
    public final ClusterFileStateCache getStateCache() {
        return m_stateCache;
    }

    /**
     * Set the state cache that this state belongs to
     *
     * @param stateCache ClusterFileStateCache
     */
    public final void setStateCache(ClusterFileStateCache stateCache) {
        m_stateCache = stateCache;
    }

    /**
     * Set the lock type
     *
     * @param lockTyp OpLockType
     */
    public void setLockType(OpLockType lockTyp) {
        m_lockType = lockTyp;
    }

    /**
     * Return the remote oplock as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Remote Owner=");
        str.append(getOwnerName());
        str.append(",type=");
        str.append(getLockType().name());

        if (getStateCache() == null)
            str.append(",stateCache=null");
        str.append("]");

        return str.toString();
    }
}
