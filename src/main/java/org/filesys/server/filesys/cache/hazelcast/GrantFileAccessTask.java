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
import org.filesys.server.filesys.*;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;
import org.filesys.smb.ImpersonationLevel;
import org.filesys.smb.OpLockType;
import org.filesys.smb.SharingMode;

import com.hazelcast.core.IMap;

/**
 * Grant File Access Task Class
 *
 * <p>Check if the specified file can be accessed using the requested sharing mode, access mode. Return
 * a file sharing exception if the access cannot be granted.
 *
 * @author gkspencer
 */
public class GrantFileAccessTask extends RemoteStateTask<FileAccessToken> {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // File open parameters
    private GrantAccessParams m_params;

    /**
     * Default constructor
     */
    public GrantFileAccessTask() {
    }

    /**
     * Class constructor
     *
     * @param mapName     String
     * @param key         String
     * @param params      GrantAccessParams
     * @param debug       boolean
     * @param timingDebug boolean
     */
    public GrantFileAccessTask(String mapName, String key, GrantAccessParams params, boolean debug, boolean timingDebug) {
        super(mapName, key, true, false, debug, timingDebug);

        m_params = params;
    }

    /**
     * Run a remote task against a file state
     *
     * @param stateCache Map of paths/cluster file states
     * @param fState     HazelCastFileState
     * @return FileAccessToken
     * @throws Exception Error running remote task
     */
    protected FileAccessToken runRemoteTaskAgainstState(IMap<String, ClusterFileState> stateCache, ClusterFileState fState)
            throws Exception {

        // DEBUG
        if (hasDebug())
            Debug.println("GrantFileAccessTask: Open params=" + m_params + " path " + fState);

        // Check if the current file open allows the required shared access
        boolean nosharing = false;
        OpLockType grantedOplock = OpLockType.LEVEL_NONE;
        boolean oplockNotAvailable = false;
        String noshrReason = null;
        boolean attribsOnly = false;

        if (m_params.isAttributesOnlyAccess()) {

            // File attributes/metadata access only
            attribsOnly = true;

            // DEBUG
            if (hasDebug())
                Debug.println("Attributes only access for " + fState);
        } else if (fState.getOpenCount() > 0) {

            // Get the current primary owner details, the owner node name/port
            String curPrimaryOwner = (String) fState.getPrimaryOwner();

            // DEBUG
            if (hasDebug())
                Debug.println("File already open by " + curPrimaryOwner + ", pid=" + fState.getProcessId() +
                        ", sharingMode=" + fState.getSharedAccess().name());

            // Check if the open action indicates a new file create
            if (m_params.getOpenAction() == CreateDisposition.CREATE)
                throw new FileExistsException();

            // Check for impersonation security level from the original process that opened the file
            if (m_params.getSecurityLevel() == ImpersonationLevel.IMPERSONATION && m_params.getProcessId() == fState.getProcessId() &&
                    curPrimaryOwner.equalsIgnoreCase(m_params.getOwnerName()))
                nosharing = false;

                // Check if the caller wants read access, check the sharing mode
            else if (m_params.isReadOnlyAccess() && fState.getSharedAccess().hasRead())
                nosharing = false;

                // Check if the caller wants write access, check the sharing mode
            else if ((m_params.isReadWriteAccess() || m_params.isWriteOnlyAccess()) && fState.getSharedAccess().hasWrite()) {
                nosharing = true;
                noshrReason = "Sharing mode disallows write";

                // DEBUG
                if (Debug.EnableDbg && hasDebug())
                    Debug.println("Sharing mode disallows write access path=" + fState.getPath());
            }

            // Check if the file has been opened for exclusive access
            else if (fState.getSharedAccess() == SharingMode.NOSHARING) {
                nosharing = true;
                noshrReason = "Sharing mode exclusive";
            }

            // Check if the required sharing mode is allowed by the current file open
            else if ((fState.getSharedAccess().intValue() & m_params.getSharedAccess().intValue()) != m_params.getSharedAccess().intValue()) {
                nosharing = true;
                noshrReason = "Sharing mode mismatch";

                // DEBUG
                if (Debug.EnableDbg && hasDebug())
                    Debug.println("Local share mode=" + fState.getSharedAccess().name() + ", params share mode=" + m_params.getSharedAccess().name());
            }

            // Check if the caller wants exclusive access to the file
            else if (m_params.getSharedAccess() == SharingMode.NOSHARING) {
                nosharing = true;
                noshrReason = "Requestor wants exclusive mode";
            }

            // Indicate that an oplock is not available, file already open by another client
            oplockNotAvailable = true;
            ;
        } else if (m_params.hasOpLockRequest() && m_params.isDirectory() == false) {

            // Grant the requested oplock, file is not open by any other users
            grantedOplock = m_params.getOpLockType();

            // DEBUG
            if (Debug.EnableDbg && hasDebug())
                Debug.println("Granted oplock type=" + grantedOplock.name());
        }

        // Check if there is a sharing mode mismatch
        if (nosharing == true)
            throw new FileSharingException("File sharing violation, reason " + noshrReason);
        else if (attribsOnly == false) {

            // Update the file sharing mode, process id and primary owner details, if this is the first file open
            fState.setSharedAccess(m_params.getSharedAccess());
            fState.setProcessId(m_params.getProcessId());
            fState.setPrimaryOwner(m_params.getOwnerName());

            // Add oplock details
            if (grantedOplock != OpLockType.LEVEL_NONE) {

                try {

                    // Create the remote oplock details
                    RemoteOpLockDetails remoteOplock = new RemoteOpLockDetails(m_params.getOwnerName(), grantedOplock, fState.getPath(), null);
                    fState.setOpLock(remoteOplock);
                }
                catch (ExistingOpLockException ex) {

                    // DEBUG
                    if (hasDebug())
                        Debug.println("Failed to set oplock on " + fState + ", existing oplock=" + fState.getOpLock());

                    // Reset the oplock to not granted
                    grantedOplock = OpLockType.LEVEL_NONE;
                    oplockNotAvailable = true;
                }
            }

            // Increment the file open count
            fState.incrementOpenCount();

            // Set the file status
            if (m_params.getFileStatus() != FileStatus.Unknown)
                fState.setFileStatusInternal(m_params.getFileStatus(), FileState.ChangeReason.None);
        }

        // Return an access token, mark the local copy as released
        HazelCastAccessToken hcToken = new HazelCastAccessToken(m_params.getOwnerName(), m_params.getProcessId(), grantedOplock, oplockNotAvailable);
        hcToken.setReleased(true);

        // Check if the file open is attributes only, mark the token so that the file open count
        // is not decremented when the file is closed
        hcToken.setAttributesOnly(attribsOnly);

        // Return the file access token
        return hcToken;
    }
}
