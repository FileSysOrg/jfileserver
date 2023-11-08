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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.filesys.server.filesys.DeferFailedException;
import org.filesys.smb.OpLockType;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;

/**
 * OpLock Details Adapter Class
 *
 * @author gkspencer
 */
public class OpLockDetailsAdapter implements OpLockDetails, Serializable {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Flag to indicate the oplock break timed out
    private boolean m_failedBreak;

    // Oplock owner details
    private List<OplockOwner> m_oplockOwners;

    /**
     * Default constructor
     */
    public OpLockDetailsAdapter() {
        m_oplockOwners = new ArrayList<>();
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
     * Check if the oplock is a batch oplock
     *
     * @return boolean
     */
    public boolean isBatchOplock() { return getLockType() == OpLockType.LEVEL_BATCH; }

    /**
     * Check if the oplock is a level II oplock
     *
     * @return boolean
     */
    public boolean isLevelIIOplock() { return getLockType() == OpLockType.LEVEL_II; }

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
        return m_failedBreak;
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
    public void updateDeferredPacketLease() {}

    /**
     * Set the failed oplock break flag, to indicate the client did not respond to the oplock break
     * request within a reasonable time.
     */
    public void setOplockBreakFailed() {}

    /**
     * Check if there is an oplock owner
     *
     * @return boolean
     */
    public final boolean hasOplockOwner() {
        return m_oplockOwners != null && !m_oplockOwners.isEmpty();
    }

    /**
     * Return the oplock owner details
     *
     * @return OplockOwner
     */
    public final OplockOwner getOplockOwner() {
        if ( m_oplockOwners != null && !m_oplockOwners.isEmpty())
            return m_oplockOwners.get( 0);
        return null;
    }

    /**
     * For a shared level II oplock there can be multiple owners, return the number of owners
     *
     * @return int
     */
    public final int numberOfOwners() {
        if ( m_oplockOwners != null)
            return m_oplockOwners.size();
        return 0;
    }

    /**
     * Return the list of oplock owners
     *
     * @return List&lt;OplockOwner&gt;
     */
    public final List<OplockOwner> getOwnerList() { return m_oplockOwners; }

    /**
     * Add another owner to the list, for level II oplocks
     *
     * @param owner OplockOwner
     * @exception InvalidOplockStateException Not a level II oplock, or no owner list
     */
    public final void addOplockOwner(OplockOwner owner)
            throws InvalidOplockStateException {

        // Check the oplock type and state
        if ( m_oplockOwners == null)
            throw new InvalidOplockStateException( "No existing owner list");
        if ( getLockType() != OpLockType.LEVEL_II && !m_oplockOwners.isEmpty())
            throw new InvalidOplockStateException( "Not a level II oplock");

        m_oplockOwners.add( owner);
    }

    /**
     * Remove an owner from a level II shared oplock
     *
     * @param owner OplockOwner
     * @return OplockOwner
     * @exception InvalidOplockStateException Not a level II oplock, or no owner list
     */
    public final OplockOwner removeOplockOwner(OplockOwner owner)
            throws InvalidOplockStateException {

        // Make sure there is an owners list
        if ( m_oplockOwners == null)
            throw new InvalidOplockStateException( "No existing owner list");

        int idx = m_oplockOwners.indexOf( owner);

        if ( idx != -1)
            return m_oplockOwners.remove(idx);

        return null;
    }

    /**
     * Set the oplock owner list using an existing list
     *
     * @param owners List&lt;OplockOwner&gt;
     */
    protected void setOwnerList( List<OplockOwner> owners) { m_oplockOwners = owners; }

    /**
     * Set the owner file id
     *
     * @param fileId int
     */
    public void setOwnerFileId(int fileId) {}

    /**
     * Update the oplock path when the file is renamed
     *
     * @param path String
     */
    public void updatePath(String path) {}

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

    /**
     * Clear the oplock break in progress flag
     */
    public void clearBreakInProgress() { }

    /**
     * Validate an oplock break level
     *
     * @param toLevel int
     * @return boolean
     */
    public boolean isValidBreakLevel(int toLevel) {
        boolean validBreak = true;

        switch ( getLockType()) {

            // Batch or exclusive oplock can break to level II or none
            case LEVEL_BATCH:
            case LEVEL_EXCLUSIVE:
                if ( toLevel != BreakLevel.LEVEL_II && toLevel != BreakLevel.LEVEL_NONE)
                    validBreak = false;
                break;

            // Level II oplock can break to none
            case LEVEL_II:
                if ( toLevel != BreakLevel.LEVEL_NONE)
                    validBreak = false;
                break;
        }

        return validBreak;
    }

    /**
     * Set/clear the oplock break failed flag
     *
     * @param state boolean
     */
    protected void setBreakFailed( boolean state) { m_failedBreak = state; }
}
