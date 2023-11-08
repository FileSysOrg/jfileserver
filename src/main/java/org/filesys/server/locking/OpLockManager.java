/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
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

import org.filesys.server.filesys.*;
import org.filesys.smb.OpLockType;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;

/**
 * OpLock Manager Interface
 *
 * <p>An oplock manager implementationis used to store oplock details for the SMB protocol handler.
 *
 * @author gkspencer
 */
public interface OpLockManager {

    /**
     * Check if there is an oplock for the specified path, return the oplock type.
     *
     * @param path String
     * @return OpLockType
     */
    public OpLockType hasOpLock(String path);

    /**
     * Return the oplock details for a path, or null if there is no oplock on the path
     *
     * @param path String
     * @return OpLockDetails
     */
    public OpLockDetails getOpLockDetails(String path);

    /**
     * Grant an oplock, store the oplock details
     *
     * @param path    String
     * @param oplock  OpLockDetails
     * @param netFile NetworkFile
     * @return boolean
     * @throws ExistingOpLockException If the file already has an oplock
     * @throws InvalidOplockStateException Invalid oplock state, usually due to an existing batch oplock
     */
    public boolean grantOpLock(String path, OpLockDetails oplock, NetworkFile netFile)
            throws ExistingOpLockException, InvalidOplockStateException;

    /**
     * Request an oplock break on the specified oplock
     *
     * @param path   String
     * @param oplock OpLockDetails
     * @param sess   SMBSrvSession
     * @param pkt    SMBSrvPacket
     * @throws IOException I/O error
     * @throws DeferFailedException Failed to defer session processing
     */
    public void requestOpLockBreak(String path, OpLockDetails oplock, SMBSrvSession sess, SMBSrvPacket pkt)
            throws IOException, DeferFailedException;

    /**
     * Release an oplock
     *
     * @param path String
     * @param owner OplockOwner
     */
    public void releaseOpLock(String path, OplockOwner owner);

    /**
     * Add a new owner to an oplock
     *
     * @param path String
     * @param oplock OplockDetails
     * @param owner OplockOwner
     * @throws InvalidOplockStateException If the oplock type is OplockType.BATCH and there is already an owner
     */
    public void addOplockOwner(String path, OpLockDetails oplock, OplockOwner owner)
        throws InvalidOplockStateException;

    /**
     * Remove an oplock owner
     *
     * @param path String
     * @param oplock OplockDetails
     * @param owner OplockOwner
     * @return int Remaining number of oplock owners
     */
    public int removeOplockOwner(String path, OpLockDetails oplock, OplockOwner owner);

    /**
     * Change an oplock type
     *
     * @param oplock OpLockDetails
     * @param newTyp OpLockType
     * @param requeue boolean
     */
    public void changeOpLockType(OpLockDetails oplock, OpLockType newTyp, boolean requeue);

    /**
     * Cancel an oplock break timer
     *
     * @param path String
     */
    public void cancelOplockTimer(String path);

    /**
     * Check for expired oplock break requests
     *
     * @return int
     */
    public int checkExpiredOplockBreaks();

    /**
     * Check access to a path, the file open parameters may not allow the path to be opened
     *
     * @param path String
     * @param params FileOpenParams
     * @return boolean
     */
    public boolean checkAccess(String path, FileOpenParams params);
}
