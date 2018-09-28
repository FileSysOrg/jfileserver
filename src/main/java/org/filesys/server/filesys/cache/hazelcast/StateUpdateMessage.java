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

import org.filesys.server.filesys.FileStatus;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;
import org.filesys.server.filesys.cache.cluster.ClusterNode;

/**
 * File State Update Message Class
 *
 * <p>Used to send low priority file state update notifications to the cluster.
 *
 * @author gkspencer
 */
public class StateUpdateMessage extends ClusterMessage {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Update path
    private String m_path;

    // Update mask
    private int m_updateMask;

    // Update values
    private FileStatus m_fileStatus;
    private FileState.ChangeReason m_fileStsReason;

    private long m_fileSize;
    private long m_allocSize;

    private long m_changeDate;
    private long m_modifyDate;
    private long m_retentionDate;

    /**
     * Default constructor
     */
    public StateUpdateMessage() {
    }

    /**
     * Class constructor
     *
     * @param targetNode String
     * @param fromNode   ClusterNode
     * @param clState    ClusterFileState
     * @param updateMask int
     */
    public StateUpdateMessage(String targetNode, ClusterNode fromNode, ClusterFileState clState, int updateMask) {
        super(targetNode, fromNode, ClusterMessageType.FileStateUpdate);

        // Set the update mask and path
        m_updateMask = updateMask;
        m_path = clState.getPath();

        // Set the updated values
        if (hasUpdate(ClusterFileState.UpdateFileStatus)) {
            m_fileStatus = clState.getFileStatus();
            m_fileStsReason = clState.getStatusChangeReason();
        }

        if (hasUpdate(ClusterFileState.UpdateFileSize))
            m_fileSize = clState.getFileSize();
        if (hasUpdate(ClusterFileState.UpdateAllocSize))
            m_allocSize = clState.getAllocationSize();

        if (hasUpdate(ClusterFileState.UpdateChangeDate))
            m_changeDate = clState.getChangeDateTime();
        if (hasUpdate(ClusterFileState.UpdateModifyDate))
            m_modifyDate = clState.getModifyDateTime();

        if (hasUpdate(ClusterFileState.UpdateRetentionExpire))
            m_retentionDate = clState.getRetentionExpiryDateTime();
    }

    /**
     * Check if the spceified value has an update
     *
     * @param upd int
     * @return boolean
     */
    public boolean hasUpdate(int upd) {
        return (m_updateMask & upd) != 0 ? true : false;
    }

    /**
     * Return the update mask
     *
     * @return int
     */
    public final int getUpdateMask() {
        return m_updateMask;
    }

    /**
     * Return the path
     *
     * @return String
     */
    public final String getPath() {
        return m_path;
    }

    /**
     * Return the file status
     *
     * @return FileStatus
     */
    public final FileStatus getFileStatus() {
        return m_fileStatus;
    }

    /**
     * Return the file status change reason code
     *
     * @return ChangeReason
     */
    public final FileState.ChangeReason getStatusChangeReason() {
        return m_fileStsReason;
    }

    /**
     * Return the file size
     *
     * @return long
     */
    public final long getFileSize() {
        return m_fileSize;
    }

    /**
     * Return the file allocation size
     *
     * @return long
     */
    public final long getAllocationSize() {
        return m_allocSize;
    }

    /**
     * Return the change date/time
     *
     * @return long
     */
    public final long getChangeDateTime() {
        return m_changeDate;
    }

    /**
     * Return the modification date/time
     *
     * @return long
     */
    public final long getModificationDateTime() {
        return m_modifyDate;
    }

    /**
     * Return the retention expiry date/time
     *
     * @return long
     */
    public final long getRetentionDateTime() {
        return m_retentionDate;
    }

    /**
     * Return the state update message as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(super.toString());
        str.append(",path=");
        str.append(getPath());
        str.append(",updates=");
        str.append(ClusterFileState.getUpdateMaskAsString(getUpdateMask()));

        if (hasUpdate(ClusterFileState.UpdateFileStatus)) {
            str.append(",fileSts=");
            str.append(getFileStatus().name());

            if (getStatusChangeReason() != FileState.ChangeReason.None) {
                str.append(",reason=");
                str.append(getStatusChangeReason().name());
            }
        }

        if (hasUpdate(ClusterFileState.UpdateFileSize)) {
            str.append(",fsize=");
            str.append(getFileSize());
        }
        if (hasUpdate(ClusterFileState.UpdateAllocSize)) {
            str.append(",alloc=");
            str.append(getAllocationSize());
        }

        if (hasUpdate(ClusterFileState.UpdateChangeDate)) {
            str.append(",change=");
            str.append(getChangeDateTime());
        }
        if (hasUpdate(ClusterFileState.UpdateModifyDate)) {
            str.append(",modify=");
            str.append(getModificationDateTime());
        }

        if (hasUpdate(ClusterFileState.UpdateRetentionExpire)) {
            str.append(",retain=");
            str.append(getRetentionDateTime());
        }
        str.append("]");

        return str.toString();
    }
}

