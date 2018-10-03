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
    private static final long serialVersionUID = 1L;

    // File state update masks
    public final static int UpdateOplock        = 0x0001;
    public final static int UpdateSharingMode   = 0x0002;
    public final static int UpdateByteLock      = 0x0004;
    public final static int UpdateFileStatus    = 0x0008;
    public final static int UpdateChangeDate    = 0x0010;
    public final static int UpdateModifyDate    = 0x0020;
    public final static int UpdateFileSize      = 0x0040;
    public final static int UpdateAllocSize     = 0x0080;
    public final static int UpdateOpenCount     = 0x0100;
    public final static int UpdateRetentionExpire = 0x0200;

    // State update strings
    private static final String[] _updateStr = {"OpLock", "SharingMode", "ByteLock", "FileSts", "ChangeDate", "ModDate", "Size", "Alloc", "OpenCount", "Retention"};
    public static final int UpdateMaskCount = _updateStr.length + 1;

    // Bit mask of pending state updates
    private transient int m_stateUpdates;

    // State cache that this file state belongs to
    private transient ClusterFileStateCache m_stateCache;

    // Primary owner of the file, when there are multiple file opens on the same file
    private Object m_primaryOwner;

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
        if (oplock != null && oplock instanceof RemoteOpLockDetails) {

            // Need to fill in the oplock state cache details
            RemoteOpLockDetails remoteOplock = (RemoteOpLockDetails) oplock;
            remoteOplock.setStateCache(getStateCache());
        }
        return oplock;
    }

    /**
     * Return the primary owner
     *
     * @return Object
     */
    public final Object getPrimaryOwner() {
        return m_primaryOwner;
    }

    /**
     * Check if there is a primary owner
     *
     * @return boolean
     */
    public final boolean hasPrimaryOwner() {
        return m_primaryOwner != null ? true : false;
    }

    /**
     * Set the primary owner, only if the file open count is currently zero
     *
     * @param priOwner Object
     */
    public final void setPrimaryOwner(Object priOwner) {
        if (getOpenCount() == 0)
            m_primaryOwner = priOwner;
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
     * @return int
     */
    public final int getPendingUpdates() {
        return m_stateUpdates;
    }

    /**
     * Clear the pending updates flags and return the current update mask value
     *
     * @return int
     */
    public final int clearPendingUpdates() {
        int updMask = m_stateUpdates;
        m_stateUpdates = 0;
        return updMask;
    }

    /**
     * Set the update mask
     *
     * @param updMask int
     */
    public final void setUpdateMask(int updMask) {
        m_stateUpdates = updMask;
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
     * @param updMask int
     * @return String
     */
    public static final String getUpdateMaskAsString(int updMask) {
        if (updMask == 0)
            return "[Empty]";

        StringBuilder str = new StringBuilder(32);

        str.append("[");

        for (int idx = 0; idx < _updateStr.length; idx++) {
            if ((updMask & (1 << idx)) != 0) {
                str.append(_updateStr[idx]);
                str.append(",");
            }
        }

        if (str.length() > 1)
            str.setLength(str.length() - 1);
        str.append("]");

        return str.toString();
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
            str.append(",pid=");
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
