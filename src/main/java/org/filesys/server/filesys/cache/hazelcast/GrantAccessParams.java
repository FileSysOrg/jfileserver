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

import org.filesys.server.filesys.*;
import org.filesys.server.filesys.cache.cluster.ClusterNode;
import org.filesys.smb.ImpersonationLevel;
import org.filesys.smb.OpLockType;
import org.filesys.smb.SharingMode;
import org.filesys.smb.WinNT;

/**
 * Grant Access Params Class
 *
 * <p>Contains a subset of the parameters from a FileOpenParams object that are sent to a grant file access
 * remote task on the cluster.
 *
 * @author gkspencer
 */
public class GrantAccessParams implements Serializable {

    // Serialization id
    private static final long serialVersionUID = 2L;

    //	Cluster node that owns the token
    private String m_ownerName;

    // Process id that owns the file
    private int m_pid;

    // File status, if FileStatus.Unknown then do not set on file state
    private FileStatus m_fileSts;

    // File open parameter value required by the access check
    private int m_accessMode;
    private SharingMode m_sharedAccess;
    private ImpersonationLevel m_secLevel;
    private int m_createOptions;
    private CreateDisposition m_openAction;

    // Oplock requested/type
    private OpLockType m_oplock = OpLockType.LEVEL_NONE;

    /**
     * Default constructor
     */
    public GrantAccessParams() {
    }

    /**
     * Class constructor
     *
     * @param clNode     ClusterNode
     * @param openParams FileOpenParams
     * @param fileSts    FileStatus
     */
    public GrantAccessParams(ClusterNode clNode, FileOpenParams openParams, FileStatus fileSts) {
        m_ownerName = clNode.getName();

        // New file status, or unknown to not set
        m_fileSts = fileSts;

        // Copy required file open params
        m_pid = openParams.getProcessId();
        m_accessMode = openParams.getAccessMode();
        m_sharedAccess = openParams.getSharedAccess();
        m_secLevel = openParams.getSecurityLevel();
        m_createOptions = openParams.getCreateOptions();
        m_openAction = openParams.getOpenAction();

        // Check if an oplock has been requested
        if (openParams.requestBatchOpLock())
            m_oplock = OpLockType.LEVEL_BATCH;
        else if (openParams.requestExclusiveOpLock())
            m_oplock = OpLockType.LEVEL_EXCLUSIVE;
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
     * Return the file status
     *
     * @return FileStatus
     */
    public final FileStatus getFileStatus() {
        return m_fileSts;
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
     * Return the shared access mode, zero equals allow any shared access
     *
     * @return SharingMode
     */
    public final SharingMode getSharedAccess() {
        return m_sharedAccess;
    }

    /**
     * Return the open action
     *
     * @return CreateDisposition
     */
    public final CreateDisposition getOpenAction() {
        return m_openAction;
    }

    /**
     * Determine if security impersonation is enabled
     *
     * @return boolean
     */
    public final boolean hasSecurityLevel() {
        return m_secLevel != ImpersonationLevel.INVALID ? true : false;
    }

    /**
     * Return the security impersonation level. Levels are defined in the WinNT class.
     *
     * @return ImpersonationLevel
     */
    public final ImpersonationLevel getSecurityLevel() {
        return m_secLevel;
    }

    /**
     * Determine if the file is to be opened read-only
     *
     * @return boolean
     */
    public final boolean isReadOnlyAccess() {
        if ((m_accessMode & AccessMode.NTReadWrite) == AccessMode.NTRead)
            return true;
        return false;
    }

    /**
     * Determine if the file is to be opened write-only
     *
     * @return boolean
     */
    public final boolean isWriteOnlyAccess() {
        if ((m_accessMode & AccessMode.NTReadWrite) == AccessMode.NTWrite)
            return true;
        return false;
    }

    /**
     * Determine if the file is to be opened read/write
     *
     * @return boolean
     */
    public final boolean isReadWriteAccess() {
        if ((m_accessMode & AccessMode.NTReadWrite) == AccessMode.NTReadWrite)
            return true;
        return false;
    }

    /**
     * Determine if the file open is to access the file attributes/metadata only
     *
     * @return boolean
     */
    public final boolean isAttributesOnlyAccess() {
        if ((m_accessMode & (AccessMode.NTReadWrite + AccessMode.NTAppend)) == 0 &&
                (m_accessMode & (AccessMode.NTReadAttrib + AccessMode.NTWriteAttrib)) != 0)
            return true;
        return false;
    }

    /**
     * Return the access mode flags
     *
     * @return int
     */
    public final int getAccessMode() {
        return m_accessMode;
    }

    /**
     * Check if an oplock has been requested
     *
     * @return boolean
     */
    public final boolean hasOpLockRequest() {
        return m_oplock != OpLockType.LEVEL_NONE ? true : false;
    }

    /**
     * Return the oplock type requested (batch or exclusive)
     *
     * @return OpLockType
     */
    public final OpLockType getOpLockType() {
        return m_oplock;
    }

    /**
     * Check if the file being creasted/opened must be a directory
     *
     * @return boolean
     */
    public final boolean isDirectory() {
        return hasCreateOption(WinNT.CreateDirectory) || getFileStatus() == FileStatus.DirectoryExists;
    }

    /**
     * Check if the specified create option is enabled, specified in the WinNT class.
     *
     * @param flag int
     * @return boolean
     */
    protected final boolean hasCreateOption(int flag) {
        return (m_createOptions & flag) != 0 ? true : false;
    }

    /**
     * Return the access parameters as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Owner=");
        str.append(getOwnerName());
        str.append(",pid=");
        str.append(getProcessId());
        str.append(",fileSts=");
        str.append(getFileStatus().name());
        str.append(",openAction=");
        str.append(getOpenAction().name());
        str.append(",create=0x");
        str.append(Integer.toHexString(m_createOptions));
        str.append(",access=0x");
        str.append(Integer.toHexString(getAccessMode()));
        str.append(",sharing=");
        str.append(getSharedAccess().name());
        str.append(",secLevel=");
        str.append(getSecurityLevel());
        str.append(",oplock=");
        str.append(getOpLockType().name());

        if (isDirectory())
            str.append(" DIR");
        str.append("]");

        return str.toString();
    }
}
