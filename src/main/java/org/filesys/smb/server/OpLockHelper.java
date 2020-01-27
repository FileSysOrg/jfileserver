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

import org.filesys.debug.Debug;
import org.filesys.server.filesys.*;
import org.filesys.server.locking.LocalOpLockDetails;
import org.filesys.server.locking.OpLockDetails;
import org.filesys.server.locking.OpLockInterface;
import org.filesys.server.locking.OpLockManager;
import org.filesys.smb.OpLockType;

import java.io.IOException;

/**
 * OpLock Helper class
 *
 * @author gkspencer
 */
public class OpLockHelper {

    /**
     * Grant an oplock, check if the filesystem supports oplocks, grant the requested oplock and return the
     * oplock details, or null if no oplock granted or requested.
     *
     * @param sess    SMBSrvSession
     * @param pkt     SMBSrvPacket
     * @param disk    DiskInterface
     * @param tree    TreeConnection
     * @param params  FileOpenParams
     * @param netFile NetworkFile
     * @return LocalOpLockDetails
     */
    public static final OpLockDetails grantOpLock(SMBSrvSession sess, SMBSrvPacket pkt, DiskInterface disk, TreeConnection tree, FileOpenParams params, NetworkFile netFile) {

        // Check if the file open is on a folder
        if (netFile.isDirectory())
            return null;

        // Check if the filesystem supports oplocks
        OpLockDetails oplock = null;

        if (disk instanceof OpLockInterface) {

            // Get the oplock interfcae, check if oplocks are enabled
            OpLockInterface oplockIface = (OpLockInterface) disk;
            if (oplockIface.isOpLocksEnabled(sess, tree) == false)
                return null;

            OpLockManager oplockMgr = oplockIface.getOpLockManager(sess, tree);

            if (oplockMgr != null) {

                // Check if there is a shared level II oplock on the file
                oplock = oplockMgr.getOpLockDetails(params.getPath());

                if (oplock != null && oplock.getLockType() == OpLockType.LEVEL_II)
                    return oplock;

                // Get the oplock type
                OpLockType oplockTyp = params.requestedOplockType();

                if ( oplockTyp == OpLockType.LEVEL_NONE)
                    return null;

                // Create the oplock details
                if ( params.hasOplockOwner())
                    oplock = new LocalOpLockDetails(oplockTyp, params.getPath(), sess, params.getOplockOwner(), netFile.isDirectory());
                else
                    oplock = new LocalOpLockDetails(oplockTyp, params.getPath(), sess, pkt, netFile.isDirectory());

                try {

                    // Store the oplock via the oplock manager, check if the oplock grant was allowed
                    if (oplockMgr.grantOpLock(params.getPath(), oplock, netFile)) {

                        // Save the oplock details with the opened file
                        netFile.setOpLock(oplock);

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                            sess.debugPrintln("Granted oplock sess=" + sess.getUniqueId() + " oplock=" + oplock);
                    }
                    else {

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                            sess.debugPrintln("Oplock not granted sess=" + sess.getUniqueId() + " oplock=" + oplock + " (Open count)");

                        // Clear the oplock, not granted
                        oplock = null;
                    }
                }
                catch (ExistingOpLockException ex) {

                    // DEBUG
                    if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                        sess.debugPrintln("Failed to grant oplock sess=" + sess.getUniqueId() + ", file=" + params.getPath() + " (Oplock exists)");

                    // Indicate no oplock was granted
                    oplock = null;
                }
            }
            else {

                // DEBUG
                if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                    sess.debugPrintln("OpLock manager is null, tree=" + tree);
            }
        }

        // Return the oplock details, or null if no oplock granted/not requested/not supported
        return oplock;
    }

    /**
     * Check if a file has an oplock, start the oplock break and defer the packet until the oplock
     * break has finished processing.
     *
     * @param sess   SMBSrvSession
     * @param pkt    SMBSrvPacket
     * @param disk   DiskInterface
     * @param params FileOpenParams
     * @param tree   TreeConnection
     * @throws DeferredPacketException If an oplock break has been started
     * @throws AccessDeniedException   If the oplock break send fails
     */
    public static final void checkOpLock(SMBSrvSession sess, SMBSrvPacket pkt, DiskInterface disk, FileOpenParams params, TreeConnection tree)
            throws DeferredPacketException, AccessDeniedException {

        // Check if the filesystem supports oplocks
        if (disk instanceof OpLockInterface) {

            // Get the oplock interface, check if oplocks are enabled
            OpLockInterface oplockIface = (OpLockInterface) disk;
            if (oplockIface.isOpLocksEnabled(sess, tree) == false)
                return;

            OpLockManager oplockMgr = oplockIface.getOpLockManager(sess, tree);

            if (oplockMgr == null) {

                // DEBUG
                if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                    sess.debugPrintln("OpLock manager is null, tree=" + tree);

                // Nothing to do
                return;
            }

            // Check if the file has an oplock, and it is not a shared level II oplock
            OpLockDetails oplock = oplockMgr.getOpLockDetails(params.getFullPath());

            if (oplock != null && oplock.getLockType() != OpLockType.LEVEL_II) {

                // DEBUG
                if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                    sess.debugPrintln("Check oplock on file " + params.getPath() + ", oplock=" + oplock);

                // Check if the oplock is local
                boolean deferredPkt = false;

                if (oplock instanceof LocalOpLockDetails) {

                    // Access the local oplock details
                    LocalOpLockDetails localOpLock = (LocalOpLockDetails) oplock;

                    // Check if the session that owns the oplock is still valid
                    SMBSrvSession opSess = localOpLock.getOwnerSession();

                    if (opSess.isShutdown() == false) {

                        // Check if the file open is for attributes/metadata only
                        if ((params.getAccessMode() & (AccessMode.NTRead + AccessMode.NTWrite + AccessMode.NTAppend)) == 0 &&
                                (params.getAccessMode() & (AccessMode.NTGenericRead + AccessMode.NTGenericWrite + AccessMode.NTGenericExecute)) == 0) {

                            // DEBUG
                            if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                                sess.debugPrintln("No oplock break, access attributes only, params=" + params + ", oplock=" + oplock);

                            // Oplock break not required
                            return;
                        }

                        // Check for a batch oplock, is the owner the same
                        if ( oplock.getLockType() == OpLockType.LEVEL_BATCH && params.requestBatchOpLock()) {

                            // Check if the current oplock owner is the same as the requestor
                            if ( localOpLock.getOplockOwner().isOwner( OpLockType.LEVEL_BATCH, params.getOplockOwner())) {

                                // DEBUG
                                if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                                    sess.debugPrintln("No oplock break, oplock owner, params=" + params + ", oplock=" + oplock);

                                // Oplock break not required
                                return;
                            }
                        }

                        // Check if the oplock has a failed break timeout, do not send another break request to the client, fail the open
                        // request with an access denied error
                        if (oplock.hasOplockBreakFailed()) {

                            // DEBUG
                            if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                                sess.debugPrintln("Oplock has failed break attempt, failing open request params=" + params);

                            // Fail the open request with an access denied error
                            throw new AccessDeniedException("Oplock has failed break");
                        }

                        // Need to send an oplock break to the oplock owner before we can continue processing the current file open request
                        try {

                            // DEBUG
                            if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                                sess.debugPrintln("Oplock break required, owner=" + oplock + ", open=" + sess.getUniqueId());

                            // Request the owner session break the oplock
                            oplockMgr.requestOpLockBreak(oplock.getPath(), oplock, sess, pkt);

                            // Indicate that the current SMB request packet processing should be deferred, until the oplock break is received
                            // from the owner
                            deferredPkt = true;
                        }
                        catch (DeferFailedException ex) {

                            // Log the error
                            if (Debug.EnableError)
                                Debug.println("Failed to defer request for local oplock break, oplock=" + oplock, Debug.Error);

                            // Throw an access denied exception so that the file open is rejected
                            throw new AccessDeniedException("Oplock break defer failed");
                        }
                        catch (IOException ex) {

                            // Log the error
                            if (Debug.EnableError) {
                                Debug.println("Failed to send local oplock break:", Debug.Error);
                                Debug.println(ex, Debug.Error);
                            }

                            // Throw an access denied exception so that the file open is rejected
                            throw new AccessDeniedException("Oplock break send failed");
                        }
                    }
                    else {

                        //	Oplock owner session is no longer valid, release the oplock
                        oplockMgr.releaseOpLock(oplock.getPath());

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                            sess.debugPrintln("Oplock released, session invalid sess=" + opSess.getUniqueId());
                    }
                }
                else if (oplock.isRemoteLock()) {

                    // Check if the open is not accessing the file data, ie. accessing attributes only
                    if ((params.getAccessMode() & (AccessMode.NTRead + AccessMode.NTWrite + AccessMode.NTAppend)) == 0 &&
                            (params.getAccessMode() & (AccessMode.NTGenericRead + AccessMode.NTGenericWrite + AccessMode.NTGenericExecute)) == 0)
                        return;

                    // Check if the oplock has a failed break timeout, do not send another break request to the client, fail the open
                    // request with an access denied error
                    if (oplock.hasOplockBreakFailed()) {

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                            sess.debugPrintln("Oplock has failed break attempt, failing open request params=" + params);

                        // Fail the open request with an access denied error
                        throw new AccessDeniedException("Oplock has failed break");
                    }

                    try {

                        // Send a remote oplock break request to the owner node
                        oplockMgr.requestOpLockBreak(oplock.getPath(), oplock, sess, pkt);

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                            sess.debugPrintln("Remote oplock break sent, oplock=" + oplock);

                        // Indicate that the current SMB request packet processing should be deferred, until the oplock break is received
                        // from the owner
                        deferredPkt = true;
                    }
                    catch (DeferFailedException ex) {

                        // Log the error
                        if (Debug.EnableError)
                            Debug.println("Failed to defer request for remote oplock break, oplock=" + oplock, Debug.Error);

                        // Throw an access denied exception so that the file open is rejected
                        throw new AccessDeniedException("Oplock break defer failed");
                    }
                    catch (IOException ex) {

                        // Log the error
                        if (Debug.EnableError) {
                            Debug.println("Failed to send remote oplock break:", Debug.Error);
                            Debug.println(ex, Debug.Error);
                        }

                        // Throw an access denied exception so that the file open is rejected
                        throw new AccessDeniedException("Oplock break send failed");
                    }
                }

                // Check if the SMB file open request processing should be deferred until the oplock break has completed
                if (deferredPkt == true)
                    throw new DeferredPacketException("Waiting for oplock break");
            }
        }

        // Returning without an exception indicates that there is no oplock on the file, or a shared oplock, so the
        // file open request can continue
    }

    /**
     * Release an oplock
     *
     * @param sess    SMBSrvSession
     * @param pkt     SMBSrvPacket
     * @param disk    DiskInterface
     * @param tree    TreeConnection
     * @param netFile NetworkFile
     */
    public static final void releaseOpLock(SMBSrvSession sess, SMBSrvPacket pkt, DiskInterface disk, TreeConnection tree, NetworkFile netFile) {

        // Check if the filesystem supports oplocks
        if (disk instanceof OpLockInterface) {

            // Get the oplock manager
            OpLockInterface oplockIface = (OpLockInterface) disk;
            OpLockManager oplockMgr = oplockIface.getOpLockManager(sess, tree);

            if (oplockMgr != null) {

                // Get the oplock details
                OpLockDetails oplock = netFile.getOpLock();

                if (oplock != null) {

                    // Release the oplock
                    oplockMgr.releaseOpLock(oplock.getPath());

                    // Clear the network file oplock
                    netFile.setOpLock(null);

                    // DEBUG
                    if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                        sess.debugPrintln("Released oplock sess=" + sess.getUniqueId() + " oplock=" + oplock);
                }
            }
        }
    }

}
