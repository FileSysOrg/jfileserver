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
import org.filesys.server.locking.*;
import org.filesys.smb.OpLockType;
import org.filesys.smb.SMBStatus;

import java.io.IOException;

/**
 * OpLock Helper class
 *
 * @author gkspencer
 */
public class OpLockHelper {

    // File access modes
    private static final int FileAccessRead         = 0x00120089;
    private static final int FileAccessReadWrite    = 0x0012019F;

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
    public static OpLockDetails grantOpLock(SMBSrvSession sess, SMBSrvPacket pkt, DiskInterface disk, TreeConnection tree, FileOpenParams params, NetworkFile netFile) {

        // Check if the file open is on a folder, or an attributes only open of a file
        if (netFile.isDirectory() || params.isAttributesOnlyAccess())
            return null;

        // Check if the filesystem supports oplocks
        OpLockDetails oplock = null;

        if (disk instanceof OpLockInterface) {

            // Get the oplock interface, check if oplocks are enabled
            OpLockInterface oplockIface = (OpLockInterface) disk;
            if ( !oplockIface.isOpLocksEnabled(sess, tree))
                return null;

            OpLockManager oplockMgr = oplockIface.getOpLockManager(sess, tree);

            if (oplockMgr != null) {

                // Check if there is a shared level II oplock on the file
                oplock = oplockMgr.getOpLockDetails(params.getPath());

                if (oplock != null && oplock.getLockType() == OpLockType.LEVEL_II) {

                    try {

                        // Need to add an owner for the new file open
                        oplockMgr.addOplockOwner(params.getPath(), oplock, params.getOplockOwner());

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                            sess.debugPrintln("Grant oplock returning existing level II oplock=" + oplock);

                        return oplock;
                    }
                    catch ( InvalidOplockStateException ex) {

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                            sess.debugPrintln("Error adding new owner to oplock=" + oplock + ", ex=" + ex);

                        if ( Debug.hasDumpStackTraces())
                            Debug.println( ex);
                    }
                }

                // Get the oplock type
                OpLockType oplockTyp = params.requestedOplockType();

                if (oplockTyp == OpLockType.LEVEL_NONE)
                    return null;

                // Create the oplock details
                oplock = new LocalOpLockDetails(oplockTyp, params.getPath(), sess, params.getOplockOwner(), netFile.isDirectory());

                try {

                    // Store the oplock via the oplock manager, check if the oplock grant was allowed
                    if (oplockMgr.grantOpLock(params.getPath(), oplock, netFile)) {

                        // Save the oplock details with the opened file
                        netFile.setOpLock(oplock, params.getOplockOwner());

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                            sess.debugPrintln("Granted oplock sess=" + sess.getUniqueId() + " oplock=" + oplock);
                    }
                    else {

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                            sess.debugPrintln("Oplock not granted sess=" + sess.getUniqueId() + " oplock=" + oplock + " (Open count)");

                        // Clear the oplock, not granted
                        oplock = null;
                    }
                }
                catch (ExistingOpLockException | InvalidOplockStateException ex) {

                    // DEBUG
                    if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                        sess.debugPrintln("Failed to grant oplock sess=" + sess.getUniqueId() + ", file=" + params.getPath() + " (Oplock exists)");

                    // Indicate no oplock was granted
                    oplock = null;
                }
            }
            else {

                // DEBUG
                if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
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
     * @throws SMBSrvException         If the requested oplock cannot be granted
     */
    public static void checkOpLock(SMBSrvSession sess, SMBSrvPacket pkt, DiskInterface disk, FileOpenParams params, TreeConnection tree)
            throws DeferredPacketException, AccessDeniedException, SMBSrvException {

        // Check if the filesystem supports oplocks
        if (disk instanceof OpLockInterface) {

            // Get the oplock interface, check if oplocks are enabled
            OpLockInterface oplockIface = (OpLockInterface) disk;
            if ( !oplockIface.isOpLocksEnabled(sess, tree))
                return;

            OpLockManager oplockMgr = oplockIface.getOpLockManager(sess, tree);

            if (oplockMgr == null) {

                // DEBUG
                if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                    sess.debugPrintln("OpLock manager is null, tree=" + tree);

                // Nothing to do
                return;
            }

            // Check if the file has an oplock, and it is not a shared level II oplock
            OpLockDetails oplock = oplockMgr.getOpLockDetails(params.getFullPath());

            if (oplock != null && oplock.isBatchOplock()) {

                // DEBUG
                if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                    sess.debugPrintln("Check oplock on file " + params.getPath() + ", oplock=" + oplock);

                // Check if the oplock is local
                boolean deferredPkt = false;

                if (oplock instanceof LocalOpLockDetails) {

                    // Access the local oplock details
                    LocalOpLockDetails localOpLock = (LocalOpLockDetails) oplock;

                    // Check if the session that owns the oplock is still valid
                    SMBSrvSession opSess = localOpLock.getOwnerSession();

                    if ( opSess != null && !opSess.isShutdown()) {

                        // Check if the file open is for attributes/metadata only
                        if ( params.isAttributesOnlyAccess()) {

                            // DEBUG
                            if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                                sess.debugPrintln("No oplock break, access attributes only, params=" + params + ", oplock=" + oplock);

                            // Oplock break not required
                            return;
                        }

                        // Check if the new file open is allowed access to the file/folder, do not trigger an oplock break if
                        // access to the file would not be allowed
                        if ( !oplockMgr.checkAccess(params.getFullPath(), params)) {

                            // DEBUG
                            if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                                sess.debugPrintln("No oplock break, failed access check, params=" + params + ", oplock=" + oplock);

                            return;
                        }

                        // Check if the oplock has a failed break timeout, do not send another break request to the client, fail the open
                        // request with an access denied error
                        if (oplock.hasOplockBreakFailed()) {

                            // DEBUG
                            if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                                sess.debugPrintln("Oplock has failed break attempt, failing open request params=" + params);

                            // Fail the open request with an access denied error
                            throw new AccessDeniedException("Oplock has failed break");
                        }

                        // Need to send an oplock break to the oplock owner before we can continue processing the current file open request
                        try {

                            // DEBUG
                            if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
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

                                if ( Debug.hasDumpStackTraces())
                                    Debug.println(ex, Debug.Error);
                            }

                            // Throw an access denied exception so that the file open is rejected
                            throw new AccessDeniedException("Oplock break send failed");
                        }
                    }
                    else {

                        //	Oplock owner session is no longer valid, release the oplock
                        oplockMgr.releaseOpLock(oplock.getPath(), params.getOplockOwner());

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                            sess.debugPrintln("Oplock released, session invalid, oplock=" + localOpLock);
                    }
                }
                else if (oplock.isRemoteLock()) {

                    // Check if the open is not accessing the file data, ie. accessing attributes only
                    if ( params.isAttributesOnlyAccess())
                        return;

                    // Check if the oplock is a shared level II oplock, no break required
                    if ( oplock.isLevelIIOplock())
                        return;

                    // Check if the new file open is allowed access to the file/folder, do not trigger an oplock break if
                    // access to the file would not be allowed
                    if ( !oplockMgr.checkAccess(params.getFullPath(), params)) {

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                            sess.debugPrintln("No oplock break, failed access check (remote), params=" + params + ", oplock=" + oplock);

                        return;
                    }

                    // Check if the oplock has a failed break timeout, do not send another break request to the client, fail the open
                    // request with an access denied error
                    if (oplock.hasOplockBreakFailed()) {

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                            sess.debugPrintln("Oplock has failed break attempt, failing open request params=" + params);

                        // Fail the open request with an access denied error
                        throw new AccessDeniedException("Oplock has failed break");
                    }

                    try {

                        // Send a remote oplock break request to the owner node
                        oplockMgr.requestOpLockBreak(oplock.getPath(), oplock, sess, pkt);

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
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
                if ( deferredPkt)
                    throw new DeferredPacketException("Waiting for oplock break on " + params.getPath());
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
    public static void releaseOpLock(SMBSrvSession sess, SMBSrvPacket pkt, DiskInterface disk, TreeConnection tree, NetworkFile netFile) {

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
                    oplockMgr.releaseOpLock(oplock.getPath(), netFile.getOplockOwner());

                    // Clear the network file oplock and owner
                    netFile.setOpLock(null, null);

                    // DEBUG
                    if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.Dbg.OPLOCK))
                        sess.debugPrintln("Released oplock sess=" + sess.getUniqueId() + " oplock=" + oplock);
                }
            }
        }
    }

}
