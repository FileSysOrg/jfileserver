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

import org.filesys.debug.Debug;
import org.filesys.server.RequestPostProcessor;
import org.filesys.server.filesys.ExistingOpLockException;
import org.filesys.server.filesys.FileStatus;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;
import org.filesys.server.filesys.cache.cluster.ClusterFileStateCache;
import org.filesys.server.locking.OpLockDetails;

/**
 * HazelCast Cluster File State Class
 *
 * @author gkspencer
 */
public class HazelCastClusterFileState extends ClusterFileState {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Transient fields used by the near-cache
    //
    // Time the state was added and last accessed in the near-cache
    private transient long m_nearCacheTime;
    private transient long m_nearLastAccess;

    // Time the state was last updated remotely
    private transient long m_nearUpdateTime;

    // Near-cache hit counter and state valid flag
    private transient long m_nearCacheHits;
    private transient boolean m_nearCacheValid;

    /**
     * Default constructor
     */
    public HazelCastClusterFileState() {
    }

    /**
     * Class constructor
     *
     * @param fname         String
     * @param caseSensitive boolean
     */
    public HazelCastClusterFileState(String fname, boolean caseSensitive) {
        super(fname, caseSensitive);
    }

    /**
     * Check if this is a copy file state, or the master file state object
     *
     * @return boolean
     */
    public boolean isCopyState() {
        return true;
    }

    /**
     * Return the time the state was added to the near-cache
     *
     * @return long
     */
    public final long getNearCacheTime() {
        return m_nearCacheTime;
    }

    /**
     * Return the near-cache hit counter
     *
     * @return long
     */
    public final long getNearCacheHitCount() {
        return m_nearCacheHits;
    }

    /**
     * Return the time the near-cache entry was last accessed
     *
     * @return long
     */
    public final long getNearCacheLastAccessTime() {
        return m_nearLastAccess;
    }

    /**
     * Return the time the state was last updated remotely
     *
     * @return long
     */
    public final long getNearRemoteUpdateTime() {
        return m_nearUpdateTime;
    }

    /**
     * Check if the near-cache entry is valid
     *
     * @return boolean
     */
    public final boolean isStateValid() {
        return m_nearCacheValid;
    }

    /**
     * Set the state cache that this state belongs to
     *
     * @param stateCache ClusterFileStateCache
     */
    public void setStateCache(ClusterFileStateCache stateCache) {
        super.setStateCache(stateCache);

        // Set the state cache for the remote oplock, if available
        // Needs to be set after deserialization of the file state/remote oplock
        if (hasOpLock() && getOpLock() instanceof RemoteOpLockDetails) {
            RemoteOpLockDetails remoteOplock = (RemoteOpLockDetails) getOpLock();
            remoteOplock.setStateCache(stateCache);
        }
    }

    /**
     * Set the oplock for this file
     *
     * @param oplock OpLockDetails
     * @throws ExistingOpLockException If there is an active oplock on this file
     */
    public synchronized void setOpLock(OpLockDetails oplock)
            throws ExistingOpLockException {

        super.setOpLock(oplock);

        // Set the state cache for the remote oplock
        if (oplock instanceof RemoteOpLockDetails) {
            RemoteOpLockDetails remoteOplock = (RemoteOpLockDetails) getOpLock();
            remoteOplock.setStateCache(getStateCache());
        }
    }

    /**
     * Set the file status (not exist, file exists, folder exists)
     *
     * @param status FileStatus
     * @param reason ChangeReason
     */
    public void setFileStatus(FileStatus status, ChangeReason reason) {

        // Check if the file status has changed, or a reason has been specified
        if (getFileStatus() != status || reason != ChangeReason.None) {

            // Update the file status, and change reason code
            super.setFileStatusInternal(status, reason);

            // Run a high priority state update
            runHighPriorityUpdate(UpdateFileStatus);
        }
    }

    /**
     * Set the file size
     *
     * @param fileSize long
     */
    public void setFileSize(long fileSize) {

        // Check if the file size has changed
        if (getFileSize() != fileSize) {

            // Update the file size
            super.setFileSize(fileSize);

            // Queue a low priority state update
            queueLowPriorityUpdate(UpdateFileSize);
        }
    }

    /**
     * Set the allocation size
     *
     * @param allocSize long
     */
    public void setAllocationSize(long allocSize) {

        // Check if the allocation size has changed
        if (getAllocationSize() != allocSize) {

            // Update the allocation size
            super.setAllocationSize(allocSize);

            // Queue a low priority state update
            queueLowPriorityUpdate(UpdateAllocSize);
        }
    }

    /**
     * Update the modify date/time
     *
     * @param modTime long
     */
    public void updateModifyDateTime(long modTime) {

        // Check if the modification date/time has changed
        if (getModifyDateTime() != modTime) {

            // Update the modification date/time
            super.updateModifyDateTime(modTime);

            // Queue a low priority state update
            queueLowPriorityUpdate(UpdateModifyDate);
        }
    }

    /**
     * Update the change date/time
     *
     * @param changeTime long
     */
    public void updateChangeDateTime(long changeTime) {

        // Check if the change date/time has changed
        if (getChangeDateTime() != changeTime) {

            // Update the change date/time
            super.updateChangeDateTime(changeTime);

            // Queue a low priority state update
            queueLowPriorityUpdate(UpdateChangeDate);
        }
    }

    /**
     * Set the retention period expiry date/time
     *
     * @param expires long
     */
    public void setRetentionExpiryDateTime(long expires) {

        // Check if the retention date/time has changed
        if (getRetentionExpiryDateTime() != expires) {

            // Update the retention date/time
            super.setRetentionExpiryDateTime(expires);

            // Queue a low priority state update
            queueLowPriorityUpdate(UpdateRetentionExpire);
        }
    }

    /**
     * Set the time the state was added to the near-cache
     */
    public final void setNearCacheTime() {
        m_nearCacheTime = System.currentTimeMillis();
        setStateValid(true);
    }

    /**
     * Set the remote update time for a near-cached state
     */
    public final void setNearRemoteUpdateTime() {
        m_nearUpdateTime = System.currentTimeMillis();
    }

    /**
     * Set the remote update time for a near-cached state
     *
     * @param updateTime long
     */
    public final void setNearRemoteUpdateTime(long updateTime) {
        m_nearUpdateTime = updateTime;
    }

    /**
     * Increment the near-cache hit counter
     *
     * @return long
     */
    public final long incrementNearCacheHitCount() {
        m_nearLastAccess = System.currentTimeMillis();
        return ++m_nearCacheHits;
    }

    /**
     * Set the near-cache entry valid flag
     *
     * @param nearValid boolean
     */
    public final void setStateValid(boolean nearValid) {
        m_nearCacheValid = nearValid;
    }

    /**
     * Copy near-cache details to a new copy of the file state
     *
     * @param hcState HazelCastClusterFileState
     */
    protected final void copyNearCacheDetails(HazelCastClusterFileState hcState) {
        m_nearCacheTime = hcState.getNearCacheTime();
        m_nearLastAccess = hcState.getNearCacheLastAccessTime();
        m_nearCacheHits = hcState.getNearCacheHitCount();
    }

    /**
     * Queue a low priority update for this file state
     *
     * @param updateMask int
     */
    protected synchronized void queueLowPriorityUpdate(int updateMask) {

        // Check if there is a state update post processor already queued
        StateUpdatePostProcessor updatePostProc = (StateUpdatePostProcessor) RequestPostProcessor.findPostProcessor(StateUpdatePostProcessor.class);
        if (updatePostProc == null) {

            // Create and queue a state update post processor
            updatePostProc = new StateUpdatePostProcessor(getStateCache(), this, updateMask);
            RequestPostProcessor.queuePostProcessor(updatePostProc);
        } else {

            // Update the existing post processor
            updatePostProc.addToUpdateMask(updateMask);
        }
    }

    /**
     * Run a high priority update for this file state
     *
     * @param updateMask int
     */
    protected void runHighPriorityUpdate(int updateMask) {

        if (getStateCache() != null) {

            // Update the file state via the state cache
            HazelCastClusterFileStateCache hcStateCache = (HazelCastClusterFileStateCache) getStateCache();
            hcStateCache.remoteUpdateState(this, updateMask);
        }
    }

    /**
     * Update the file state from values in the update message
     *
     * @param updateMsg StateUpdateMessage
     */
    protected final void updateState(StateUpdateMessage updateMsg) {

        // Update the file state from the update message
        //
        // Note: Only update the FileState class values using the super methods.
        //       The HazelCastClusterFileState set/update methods must not be used.

        // File status
        if (updateMsg.hasUpdate(UpdateFileStatus)) {
            super.setFileStatus(updateMsg.getFileStatus());

            // TEST
            if (getFileStatus() == FileStatus.NotExist && getOpenCount() > 0)
                Debug.println("Setting status to NotExist when openCount>0, fid=" + getFileId() + ", name=" + getPath());

            // If the file/folder no longer exists then clear the file id and state attributes
            if (getFileStatus() == FileStatus.NotExist) {
                setFileId(UnknownFileId);
                removeAllAttributes();
            }
        }

        // File size/allocation size
        if (updateMsg.hasUpdate(UpdateFileSize))
            super.setFileSize(updateMsg.getFileSize());

        if (updateMsg.hasUpdate(UpdateAllocSize))
            super.setAllocationSize(updateMsg.getAllocationSize());

        // Change/modification date/time
        if (updateMsg.hasUpdate(UpdateChangeDate))
            super.updateChangeDateTime(updateMsg.getChangeDateTime());

        if (updateMsg.hasUpdate(UpdateModifyDate))
            super.updateModifyDateTime(updateMsg.getModificationDateTime());

        // Retention expiry date/time
        if (updateMsg.hasUpdate(UpdateRetentionExpire))
            super.setRetentionExpiryDateTime(updateMsg.getRetentionDateTime());
    }

    /**
     * Return the file state as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(getPath());
        str.append(",");
        str.append(getFileStatus().name());
        str.append(":Opn=");

        str.append(super.getOpenCount());    // Local open count only
        if (getOpenCount() > 0) {
            str.append("(shr=");
            str.append(getSharedAccess().name());
            str.append(",pid=");
            str.append(getProcessId());
            str.append(",primary=");
            str.append(getPrimaryOwner());
            str.append(")");
        }

        str.append(",Fid=");
        str.append(getFileId());

        str.append(",Expire=");
        str.append(getSecondsToExpire(System.currentTimeMillis()));

        str.append(",Sts=");
        str.append(getDataStatus().name());

        str.append(",Locks=");
        str.append(numberOfLocks());

        if (hasOpLock()) {
            str.append(",OpLock=");
            str.append(getOpLock());
        }

        // Near-cache details
        if (getNearCacheTime() != 0L) {
            str.append(" - Near at=");
            str.append(System.currentTimeMillis() - getNearCacheTime());
            str.append("ms,");

            if (getNearCacheLastAccessTime() > 0L) {
                str.append("acc=");
                str.append(System.currentTimeMillis() - getNearCacheLastAccessTime());
                str.append("ms,");
            }

            if (getNearRemoteUpdateTime() > 0L) {
                str.append("upd=");
                str.append(System.currentTimeMillis() - getNearRemoteUpdateTime());
                str.append("ms,");
            }

            str.append("hits=");
            str.append(getNearCacheHitCount());
            if (isStateValid() == false)
                str.append(",NotValid");
        }

        str.append("]");

        return str.toString();
    }
}
