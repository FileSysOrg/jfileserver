/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
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

package org.filesys.server.filesys.cache.cluster;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.HashMap;

import org.filesys.server.filesys.ExistingOpLockException;
import org.filesys.server.filesys.FileStatus;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.hazelcast.RemoteOpLockDetails;
import org.filesys.server.filesys.pseudo.PseudoFileList;
import org.filesys.server.locking.LocalOpLockDetails;
import org.filesys.server.locking.OpLockDetails;

/**
 * Cluster File State Class
 *
 * @author gkspencer
 */
public abstract class ClusterFileState extends FileState implements Serializable {

    // Serialization id
    private static final long serialVersionUID = 2L;

    // Update mask flags
    public enum UpdateFlag {
        Oplock,
        SharingMode,
        ByteLock,
        FileStatus,
        ChangeDate,
        ModifyDate,
        FileSize,
        AllocSize,
        RetentionExpire
    };

    // Bit mask of pending state updates
    private transient EnumSet<UpdateFlag> m_stateUpdates = EnumSet.noneOf( UpdateFlag.class);

    // State cache that this file state belongs to
    private transient ClusterFileStateCache m_stateCache;

    // Data update in progress on the specified cluster node
    private Object m_dataUpdateNode;

    // File status change reason code
    private transient ChangeReason m_fileStsReason;

    /**
     * Default constructor
     */
    public ClusterFileState() {
    }

    /**
     * Class constructor
     *
     * @param fname         String
     * @param caseSensitive boolean
     */
    public ClusterFileState(String fname, boolean caseSensitive) {
        super(fname, caseSensitive);
    }

    /**
     * Return the oplock details
     *
     * @return OpLockDetails
     */
    public OpLockDetails getOpLock() {
        OpLockDetails oplock = super.getOpLock();
        if ( oplock instanceof RemoteOpLockDetails) {

            // Need to fill in the oplock state cache details
            RemoteOpLockDetails remoteOplock = (RemoteOpLockDetails) oplock;
            remoteOplock.setStateCache(getStateCache());
        }
        return oplock;
    }

    /**
     * Set the path using an already normalized path string
     *
     * @param path String
     */
    public final void setNormalizedPath(String path) {
        setPathInternal(path);
    }

    /**
     * Get the state cache that this state belongs to
     *
     * @return ClusterFileStateCache
     */
    protected final ClusterFileStateCache getStateCache() {
        return m_stateCache;
    }

    /**
     * Set the state cache that this state belongs to
     *
     * @param stateCache ClusterFileStateCache
     */
    public void setStateCache(ClusterFileStateCache stateCache) {
        m_stateCache = stateCache;
    }

    /**
     * Get the file id
     *
     * @return int
     */
    public int getFileId() {
        int fileId = UnknownFileId;

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(this, false);
            if (perNode != null)
                fileId = perNode.getFileId();
        }

        return fileId;
    }

    /**
     * Set the file identifier
     *
     * @param id int
     */
    public void setFileId(int id) {
        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(this, true);
            if (perNode != null)
                perNode.setFileId(id);
        }
    }

    /**
     * Return the file data status
     *
     * @return DataStatus
     */
    public DataStatus getDataStatus() {
        DataStatus dataSts = DataStatus.Available;

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(this, false);
            if (perNode != null)
                dataSts = perNode.getDataStatus();
        }

        return dataSts;
    }

    /**
     * Set the file data status
     *
     * @param sts DataStatus
     */
    public void setDataStatus(DataStatus sts) {
        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(this, true);
            if (perNode != null)
                perNode.setDataStatus(sts);
        }
    }

    /**
     * Return the map of additional attribute objects attached to this file state, and
     * optionally create the map if it does not exist
     *
     * @param createMap boolean
     * @return HashMap
     */
    protected HashMap<String, Object> getAttributeMap(boolean createMap) {
        HashMap<String, Object> attrMap = null;

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(this, createMap);
            if (perNode != null)
                attrMap = perNode.getAttributeMap(createMap);
        }

        return attrMap;
    }

    /**
     * Return the pseudo file list, optionally create a new list
     *
     * @param createList boolean
     * @return PseudoFileList
     */
    protected PseudoFileList getPseudoFileList(boolean createList) {
        PseudoFileList pseudoList = null;

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(this, createList);
            if (perNode != null)
                pseudoList = perNode.getPseudoFileList(createList);
        }

        return pseudoList;
    }

    /**
     * Return the filesystem object
     *
     * @return Object
     */
    public Object getFilesystemObject() {
        Object fileSysObj = null;

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(this, false);
            if (perNode != null)
                fileSysObj = perNode.getFilesystemObject();
        }

        return fileSysObj;
    }

    /**
     * Set the filesystem object
     *
     * @param filesysObj Object
     */
    public void setFilesystemObject(Object filesysObj) {
        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(this, true);
            if (perNode != null)
                perNode.setFilesystemObject(filesysObj);
        }
    }

    /**
     * Check if the file has an active local oplock
     *
     * @return boolean
     */
    public boolean hasLocalOpLock() {
        boolean oplockSts = false;

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(this, false);
            if (perNode != null && perNode.hasOpLock())
                oplockSts = true;
        }

        return oplockSts;
    }

    /**
     * Return the local oplock details
     *
     * @return LocalOpLockDetails
     */
    public LocalOpLockDetails getLocalOpLock() {
        LocalOpLockDetails localOpLock = null;

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(this, false);
            if (perNode != null && perNode.hasOpLock())
                localOpLock = perNode.getOpLock();
        }

        return localOpLock;
    }

    /**
     * Set the oplock for this file
     *
     * @param oplock LocalOpLockDetails
     * @throws ExistingOpLockException If there is an active oplock on this file
     */
    public synchronized void setLocalOpLock(LocalOpLockDetails oplock)
            throws ExistingOpLockException {

        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(this, true);
            if (perNode != null)
                perNode.setOpLock(oplock);
        }
    }

    /**
     * Clear the oplock
     */
    public synchronized void clearLocalOpLock() {
        if (getStateCache() != null) {
            PerNodeState perNode = getStateCache().getPerNodeState(this, false);
            if (perNode != null)
                perNode.clearOpLock();
        }
    }

    /**
     * Check if the file is readable for the specified section of the file and process id
     *
     * @param offset long
     * @param len    long
     * @param pid    int
     * @return boolean
     */
    public boolean canReadFile(long offset, long len, int pid) {
        if (getStateCache() != null)
            return getStateCache().canReadFile(this, offset, len, pid);
        throw new RuntimeException("State cache not set for cluster state, path=" + getPath() + " (canRead)");
    }

    /**
     * Check if the file is writeable for the specified section of the file and process id
     *
     * @param offset long
     * @param len    long
     * @param pid    int
     * @return boolean
     */
    public boolean canWriteFile(long offset, long len, int pid) {
        if (getStateCache() != null)
            return getStateCache().canWriteFile(this, offset, len, pid);
        throw new RuntimeException("State cache not set for cluster state, path=" + getPath() + " (canWrite)");
    }

    /**
     * Return the pending updates mask
     *
     * @return EnumSet&lt;UpdateFlag&gt;
     */
    public final EnumSet<UpdateFlag> getPendingUpdates() {
        return m_stateUpdates;
    }

    /**
     * Clear the pending updates flags and return the current update mask value
     *
     * @return EnumSet&lt;UpdateFlag&gt;
     */
    public final EnumSet<UpdateFlag> clearPendingUpdates() {
        EnumSet<UpdateFlag> updMask = m_stateUpdates;
        m_stateUpdates.clear();
        return updMask;
    }

    /**
     * Set the update mask
     *
     * @param updFlag UpdateFlag
     */
    public final void setUpdateMask(UpdateFlag updFlag) {
        m_stateUpdates.add( updFlag);
    }

    /**
     * Set the file status value, internal method
     *
     * @param fSts   FileStatus
     * @param reason ChangeReason
     */
    public final void setFileStatusInternal(FileStatus fSts, ChangeReason reason) {
        super.setFileStatus(fSts, reason);

        m_fileStsReason = reason;
    }

    /**
     * Return the file status change reason code
     *
     * @return ChangeReason
     */
    public final ChangeReason getStatusChangeReason() {
        return m_fileStsReason;
    }

    /**
     * Set the file status change reason code
     *
     * @param reason ChangeReason
     */
    public final void setStatusChangeReason(ChangeReason reason) {
        m_fileStsReason = reason;
    }

    /**
     * Check if there is a data update in progress for this file
     *
     * @return boolean
     */
    public boolean hasDataUpdateInProgress() {

        // Check if there is a data update in progress for this file
        if (m_dataUpdateNode != null && m_dataUpdateNode instanceof ClusterNode) {

            // If the update is by the local node we ignore it, only return true if
            // the data update is on a remote node
            if (getStateCache() != null) {

                // Check if the data update is on the local node
                ClusterNode updateNode = (ClusterNode) m_dataUpdateNode;

                if (getStateCache().getCluster().getLocalNode().equals(updateNode))
                    return false;
            }

            // Remote data update in progress for this file, or cannot determine if the local
            // node is updating the data
            return true;
        }

        // No data update in progress
        return false;
    }

    /**
     * Return the data update node details, or null if no data update is in progress
     *
     * @return Object
     */
    public Object getDataUpdateNode() {
        return m_dataUpdateNode;
    }

    /**
     * Set the data update node details
     *
     * @param updateNode Object
     */
    public void setDataUpdateNode(Object updateNode) {
        m_dataUpdateNode = updateNode;
    }

    /**
     * Return the update mask as a string
     *
     * @param updMask EnumSet&lt;UpdateFlag&gt;
     * @return String
     */
    public static String getUpdateMaskAsString(EnumSet<UpdateFlag> updMask) {
        if (updMask.isEmpty())
            return "[Empty]";

        StringBuilder str = new StringBuilder(32);

        str.append("[");
        str.append( updMask);
        str.append("]");

        return str.toString();
    }

    /**
     * Return the file state as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(getPath());
        str.append(",");
        str.append(getFileStatus().name());
        if (getStatusChangeReason() != ChangeReason.None) {
            str.append("(");
            str.append(getStatusChangeReason().name());
            str.append(")");
        }

        str.append(":Opn=");

        str.append(super.getOpenCount());    // Local open count only
        if (getOpenCount() > 0) {
            str.append("(shr=");
            str.append(getSharedAccess().name());
            str.append(",owner=");
            str.append(getProcessId());
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

        if (hasDataUpdateInProgress()) {
            str.append(",DataUpd=");
            str.append(getDataUpdateNode());
        }
        str.append("]");

        return str.toString();
    }
}
