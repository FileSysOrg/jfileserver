/*
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.smb.server;

import org.filesys.server.locking.OplockOwner;
import org.filesys.server.locking.OplockOwnerAdapter;
import org.filesys.smb.OpLockType;

import java.io.Serializable;

/**
 * SMB V1 Oplock Owner Class
 *
 * <p>Contains the oplock owner details required by an SMB v1 oplock break</p>
 *
 * @author gkspencer
 */
public class SMBV1OplockOwner extends OplockOwnerAdapter implements Serializable {

    // Serialization id
    private static final long serialVersionUID = 2L;

    // Oplock owner details
    private int m_fileId;

    /**
     * Class constructor
     *
     * @param sess SMBSrvSession
     * @param treeId int
     * @param procId int
     * @param userId int
     */
    public SMBV1OplockOwner(SMBSrvSession sess, int treeId, int procId, int userId) {
        super( sess, treeId, procId, userId);

        m_fileId = -1;
    }

    /**
     * Return the file id
     *
     * @return int
     */
    public final int getFileId() {
        return m_fileId;
    }

    /**
     * Set the file id
     *
     * @param fileId int
     */
    public final void setFileId(int fileId) {
        m_fileId = fileId;
    }

    /**
     * Compare oplock owners for equality
     *
     * @param obj Object
     * @return boolean
     */
    public boolean equals(Object obj) {

        // Make sure the object is valid, and is the same type
        if ( !(obj instanceof SMBV1OplockOwner))
            return false;

        // Compare the SMB V1 oplock owner details
        SMBV1OplockOwner v1Owner = (SMBV1OplockOwner) obj;

        return getTreeId() == v1Owner.getTreeId() && getUserId() == v1Owner.getUserId() && getProcessId() == v1Owner.getProcessId() &&
                getFileId() == v1Owner.getFileId();
    }

    /**
     * Check if the oplock owner matches this oplock owner for the type of oplock
     *
     * @param opType    OplockType
     * @param opOwner   OplockOwner
     * @return boolean
     */
    public boolean isOwner(OpLockType opType, OplockOwner opOwner) {

        // Make sure the object is valid, and is the same type
        if ( !(opOwner instanceof SMBV1OplockOwner))
            return false;

        // Compare the SMB V1 oplock owner details
        SMBV1OplockOwner v1Owner = (SMBV1OplockOwner) opOwner;

        // For a batch oplock do not check the file id
        if ( opType == OpLockType.LEVEL_BATCH) {

            // Check for the same user/process
            if ( getTreeId() == v1Owner.getTreeId() && getUserId() == v1Owner.getUserId() && getProcessId() == v1Owner.getProcessId())
                return true;
            else
                return false;
        }

        // Check for the same user/process/file id
        return getTreeId() == v1Owner.getTreeId() && getUserId() == v1Owner.getUserId() && getProcessId() == v1Owner.getProcessId() &&
                getFileId() == v1Owner.getFileId();
    }

    /**
     * Return a short unique id string for the oplock owner
     *
     * @return String
     */
    public String getUniqueId() {
        return getSession().getUniqueId() + "-" + getFileId();
    }

    /**
     * Return the oplock owner details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[SMB V1 tree=");
        str.append( getTreeId());
        str.append(", pid=");
        str.append( getProcessId());
        str.append( ", user=");
        str.append( getUserId());
        str.append(", fid=");
        str.append( getFileId());
        str.append("]");

        return str.toString();
    }
}
