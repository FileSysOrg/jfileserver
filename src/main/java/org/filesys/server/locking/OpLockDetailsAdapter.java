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

package org.filesys.server.locking;

import java.io.IOException;

import org.filesys.server.filesys.DeferFailedException;
import org.filesys.smb.OpLockType;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;

/**
 * OpLock Details Adapter Class
 *
 * @author gkspencer
 */
public class OpLockDetailsAdapter implements OpLockDetails {

    /**
     * Default constructor
     */
    public OpLockDetailsAdapter() {
    }

    /**
     * Return the oplock type
     *
     * @return OpLockType
     */
    public OpLockType getLockType() {
        return OpLockType.LEVEL_NONE;
    }

    /**
     * Return the share relative path of the locked file
     *
     * @return String
     */
    public String getPath() {
        return null;
    }

    /**
     * Check if the oplock is on a file or folder
     *
     * @return boolean
     */
    public boolean isFolder() {
        return false;
    }

    /**
     * Check if there is a deferred session attached to the oplock, this indicates an oplock break is
     * in progress for this oplock.
     *
     * @return boolean
     */
    public boolean hasDeferredSessions() {
        return false;
    }

    /**
     * Return the count of deferred requests
     *
     * @return int
     */
    public int numberOfDeferredSessions() {
        return 0;
    }

    /**
     * Requeue deferred requests to the thread pool for processing, oplock has been released
     *
     * @return int Number of deferred requests requeued
     */
    public int requeueDeferredRequests() {
        return 0;
    }

    /**
     * Fail any deferred requests that are attached to this oplock, and clear the deferred list
     *
     * @return int Number of deferred requests that were failed
     */
    public int failDeferredRequests() {
        return 0;
    }

    /**
     * Return the time that the oplock break was sent to the client
     *
     * @return long
     */
    public long getOplockBreakTime() {
        return 0;
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
     * Check if this is a remote oplock
     *
     * @return boolean
     */
    public boolean isRemoteLock() {
        return false;
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

        throw new DeferFailedException("Deferred requests not implemented");
    }

    /**
     * Update the deferred packet lease time(s) as we wait for an oplock break or timeout
     */
    public void updateDeferredPacketLease() {
    }

    /**
     * Set the failed oplock break flag, to indicate the client did not respond to the oplock break
     * request within a reasonable time.
     */
    public void setOplockBreakFailed() {
    }

    /**
     * Set the oplock owner details
     *
     * @param owner OplockOwner
     */
    public void setOplockOwner(OplockOwner owner) {
    }

    /**
     * Set the owner file id
     *
     * @param fileId int
     */
    public void setOwnerFileId(int fileId) {
    }

    /**
     * Request an oplock break
     *
     * @throws IOException I/O error
     */
    public void requestOpLockBreak()
            throws IOException {
    }

    /**
     * Set the lock type
     *
     * @param lockTyp OpLockType
     * @exception RuntimeException Method needs to be overridden
     */
    public void setLockType(OpLockType lockTyp) {
        throw new RuntimeException("OplockDetailsAdapter.setLockType() needs override");
    }

    /**
     * Check if there is an oplock break in progress for this oplock
     *
     * @return boolean
     */
    public boolean hasBreakInProgress() {
        return false;
    }
}
