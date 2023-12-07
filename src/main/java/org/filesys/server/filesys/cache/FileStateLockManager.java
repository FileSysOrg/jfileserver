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

package org.filesys.server.filesys.cache;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;

import org.filesys.debug.Debug;
import org.filesys.locking.FileLock;
import org.filesys.locking.LockConflictException;
import org.filesys.locking.NotLockedException;
import org.filesys.server.SrvSession;
import org.filesys.server.filesys.*;
import org.filesys.server.locking.*;
import org.filesys.server.thread.ThreadRequestPool;
import org.filesys.server.thread.TimedThreadRequest;
import org.filesys.smb.OpLockType;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;

/**
 * File State Lock Manager Class
 *
 * <p>Lock manager implementation for byte range locking and oplocks that uses the file state cache to
 * track locks/oplocks.
 *
 * @author gkspencer
 */
public class FileStateLockManager implements LockManager, OpLockManager, Runnable {

    // Oplock break timeout
    private static final long OpLockBreakTimeout        = 15000L;    // 15 seconds
    private static final long OpLockBreakTimeoutSecs    = OpLockBreakTimeout / 1000L;

    // File state cache used for byte range locks/oplocks
    private final FileStateCache m_stateCache;

    // Oplock breaks in progress
    private final Hashtable<String, OpLockDetails> m_oplockQueue;

    // Oplock break timeout thread
    private Thread m_expiryThread;
    private boolean m_shutdown;

    // Thread pool and timed thread request
    private ThreadRequestPool m_threadPool;
    private OplockExpiryTimedRequest m_threadReq;

    /**
     * Oplock Expiry Checker Timed Thread Request Class
     */
    private class OplockExpiryTimedRequest extends TimedThreadRequest {

        /**
         * Constructor
         *
         * @param name     String
         * @param interval long
         */
        public OplockExpiryTimedRequest(String name, long interval) {
            super(name, TimedRequestPaused, interval);
        }

        /**
         * Expiry checker method
         */
        protected void runTimedRequest() {

            // Check for expired oplock break requests
            checkExpiredOplockBreaks();

            // If the shutdown flag is set then clear the repeat interval so the timed request
            // does not get re-queued
            if ( m_shutdown) {

                // Clear the repeat interval so the request does not get re-queued
                setRepeatInterval(0L);
            } else if ( m_oplockQueue.isEmpty()) {

                // Pause the timed checker request
                setRunAtTime(TimedRequestPaused);
            }
        }
    }

    /**
     * Class constructor
     *
     * @param stateCache FileStateCache
     */
    public FileStateLockManager(FileStateCache stateCache) {

        // Save the associated state cache
        m_stateCache = stateCache;

        // Create the oplock break queue
        m_oplockQueue = new Hashtable<String, OpLockDetails>();
    }

    /**
     * Lock a byte range within a file, or the whole file.
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param file NetworkFile
     * @param lock FileLock
     * @throws LockConflictException Lock conflicts with an existing lock
     * @throws IOException I/O error
     */
    public void lockFile(SrvSession sess, TreeConnection tree, NetworkFile file, FileLock lock)
            throws LockConflictException, IOException {

        //	Make sure the file implements the file state interface
        if ( !(file instanceof NetworkFileStateInterface))
            throw new IllegalArgumentException("NetworkFile does not implement NetworkFileStateInterface, path=" + file.getFullName());

        //	Get the file state associated with the file
        NetworkFileStateInterface fstateIface = (NetworkFileStateInterface) file;
        FileState fstate = fstateIface.getFileState();

        if (fstate == null)
            throw new IOException("Open file without state (lock)");

        //	Add the lock to the active lock list for the file, check if the new lock conflicts with
        //	any existing locks. Add the lock to the file instance so that locks can be removed if the
        //	file is closed/session abnormally terminates.
        m_stateCache.addLock(fstate, lock);
        file.addLock(lock);
    }

    /**
     * Unlock a byte range within a file, or the whole file
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param file NetworkFile
     * @param lock FileLock
     * @throws NotLockedException File is not locked
     * @throws IOException I/O error
     */
    public void unlockFile(SrvSession sess, TreeConnection tree, NetworkFile file, FileLock lock)
            throws NotLockedException, IOException {

        //	Make sure the file implements the file state interface
        if ( !(file instanceof NetworkFileStateInterface))
            throw new IllegalArgumentException("NetworkFile does not implement NetworkFileStateInterface");

        //	Get the file state associated with the file
        NetworkFileStateInterface fstateIface = (NetworkFileStateInterface) file;
        FileState fstate = fstateIface.getFileState();

        if (fstate == null)
            throw new IOException("Open file without state (unlock)");

        //	Remove the lock from the active lock list for the file, and the file instance
        try {
            m_stateCache.removeLock(fstate, lock);
        }
        finally {
            file.removeLock(lock);
        }
    }

    /**
     * Create a file lock object.
     *
     * @param sess   SrvSession
     * @param tree   TreeConnection
     * @param file   NetworkFile
     * @param params LockParams
     */
    public FileLock createLockObject(SrvSession sess, TreeConnection tree, NetworkFile file, LockParams params) {

        //	Create a lock object to represent the file lock
        return m_stateCache.createFileLockObject(file, params);
    }

    /**
     * Release all locks that a session has on a file. This method is called to perform cleanup if a file
     * is closed that has active locks or if a session abnormally terminates.
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param file NetworkFile
     */
    public void releaseLocksForFile(SrvSession sess, TreeConnection tree, NetworkFile file) {

        //	Check if the file has active locks
        if (file != null && file.hasLocks()) {

            synchronized (file) {

                //	Enumerate the locks and remove
                while (file.numberOfLocks() > 0) {

                    //	Get the current file lock
                    FileLock curLock = file.getLockAt(0);

                    //	Remove the lock, ignore errors
                    try {

                        //	Unlock will remove the lock from the global list and the local files list
                        unlockFile(sess, tree, file, curLock);
                    }
                    catch (Exception ex) {
                        if ( Debug.hasDumpStackTraces())
                            Debug.println( ex);
                    }
                }
            }
        }
    }

    /**
     * Check if there is an oplock for the specified path, return the oplock type.
     *
     * @param path String
     * @return OpLockType
     */
    public OpLockType hasOpLock(String path) {

        // Get the file state
        FileState fstate = m_stateCache.findFileState(path);

        if (fstate != null && fstate.hasOpLock()) {

            // Return the oplock type
            OpLockDetails oplock = fstate.getOpLock();

            if (oplock != null)
                return oplock.getLockType();
        }

        // No oplock
        return OpLockType.LEVEL_NONE;
    }

    /**
     * Return the oplock details for a path, or null if there is no oplock on the path
     *
     * @param path String
     * @return OpLockDetails
     */
    public OpLockDetails getOpLockDetails(String path) {

        // Get the file state
        FileState fstate = m_stateCache.findFileState(path);
        if (fstate != null)
            return m_stateCache.getOpLock(fstate);

        // No oplock
        return null;
    }

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
            throws ExistingOpLockException, InvalidOplockStateException {

        // Get, or create, a file state
        FileState fstate = m_stateCache.findFileState(path, true);

        // Set the oplock
        return m_stateCache.addOpLock(fstate, oplock, netFile);
    }

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
            throws IOException, DeferFailedException {

        // Request an oplock break
        m_stateCache.requestOplockBreak(path, oplock, sess, pkt);

        // For a batch oplock add the oplock to the break in progress queue
        if ( oplock.getLockType() == OpLockType.LEVEL_BATCH) {
            synchronized (m_oplockQueue) {
                m_oplockQueue.put(path, oplock);

                // Inform the checker thread or restart the timed request
                if (m_threadPool == null)
                    m_oplockQueue.notify();
                else
                    m_threadReq.restartRequest();
            }
        }
    }

    /**
     * Release an oplock
     *
     * @param path String
     * @param owner OplockOwner
     */
    public void releaseOpLock(String path, OplockOwner owner) {

        // Get the file state and oplock type
        FileState fstate = m_stateCache.findFileState(path);
        OpLockType opType = OpLockType.INVALID;

        if ( fstate != null && fstate.hasOpLock())
            opType = fstate.getOpLock().getLockType();

        // Remove the oplock owner
        if ( m_stateCache.removeOplockOwner( fstate, owner)) {

            // For a batch oplock clear any pending oplock break requests
            if  (opType == OpLockType.LEVEL_BATCH) {

                // Remove from the pending oplock break queue
                synchronized (m_oplockQueue) {

                    // Remove any active oplock break from the queue
                    OpLockDetails remOplock = m_oplockQueue.remove(path);

                    // Check if there is a deferred CIFS request pending for this oplock
                    if (remOplock != null && remOplock.hasDeferredSessions()) {

                        // Requeue the deferred requests to the thread pool for processing
                        remOplock.requeueDeferredRequests();
                    }
                }
            }
        }
    }

    /**
     * Add a new owner to an oplock
     *
     * @param path String
     * @param oplock OplockDetails
     * @param owner OplockOwner
     * @throws InvalidOplockStateException If there is an existing owner on a batch oplock
     */
    public void addOplockOwner(String path, OpLockDetails oplock, OplockOwner owner)
        throws InvalidOplockStateException {

        // Get the file state and oplock type
        FileState fstate = m_stateCache.findFileState(path);
        OpLockType opType = OpLockType.INVALID;

        // Add the new oplock owner
        if ( fstate != null && fstate.hasOpLock())
            m_stateCache.addOplockOwner( fstate, owner);
    }

    /**
     * Remove an oplock owner
     *
     * @param path String
     * @param oplock OplockDetails
     * @param owner OplockOwner
     * @return int Remaining number of oplock owners
     */
    public int removeOplockOwner(String path, OpLockDetails oplock, OplockOwner owner) {

        // Get the file state and oplock type
        FileState fstate = m_stateCache.findFileState(path);

        // Remove the oplock owner
        int numOwners = 0;

        if ( fstate != null && fstate.hasOpLock()) {

            // Remove the oplock owner
            m_stateCache.removeOplockOwner(fstate, owner);

            // Get the remaining count of oplock owners
            numOwners = fstate.getOpLock().numberOfOwners();
        }

        // Return the remaining number of oplock owners
        return numOwners;
    }


    /**
     * Change an oplock type
     *
     * @param oplock OpLockDetails
     * @param newTyp OpLockType
     * @param requeue boolean
     */
    public void changeOpLockType(OpLockDetails oplock, OpLockType newTyp, boolean requeue) {

        // Change the oplock type via the state cache
        m_stateCache.changeOpLockType(oplock, newTyp);

        // Remove from the pending oplock break queue
        synchronized (m_oplockQueue) {

            // Remove any active oplock break from the queue
            if (m_oplockQueue.remove(oplock.getPath()) != null) {

                // Check if deferred requests should be re-queued for processing
                if ( requeue) {

                    // Check if there are deferred CIFS request(s) pending for this oplock
                    if (oplock.hasDeferredSessions()) {

                        // Requeue the deferred request(s) to the thread pool for processing
                        oplock.requeueDeferredRequests();
                    }
                }
            }
        }
    }

    /**
     * Cancel an oplock break timer
     *
     * @param path String
     */
    public void cancelOplockTimer(String path) {

        // Remove from the pending oplock break queue
        synchronized (m_oplockQueue) {

            // Remove any active oplock break from the queue
            m_oplockQueue.remove(path);
        }
    }

    /**
     * Check for expired oplock break requests
     *
     * @return int
     */
    public int checkExpiredOplockBreaks() {

        // Check if the oplock queue is valid
        if (m_oplockQueue == null)
            return 0;

        int expireCnt = 0;

        synchronized (m_oplockQueue) {

            // Check if there are any oplock breaks in progress
            if ( m_oplockQueue.isEmpty())
                return 0;

            // Check for oplock break requests that have expired
            long timeNow = System.currentTimeMillis();
            Enumeration<String> opBreakKeys = m_oplockQueue.keys();

            while (opBreakKeys.hasMoreElements()) {

                // Check the current oplock break
                String path = opBreakKeys.nextElement();
                OpLockDetails opLock = m_oplockQueue.get(path);
                if (opLock != null) {

                    // Check if the oplock break has timed out
                    if (opLock.hasDeferredSessions()) {

                        // Check if the oplock break has timed out
                        if ((opLock.getOplockBreakTime() + OpLockBreakTimeout) <= timeNow) {

                            // Fail the deferred requests
                            opLock.failDeferredRequests();

                            // Remove the oplock break from the queue
                            m_oplockQueue.remove(path);

                            // Mark the oplock has having a failed oplock break
                            opLock.setOplockBreakFailed();

                            // Update the expired oplock break count
                            expireCnt++;
                        } else {

                            // Update the deferred packet(s) lease timeout
                            opLock.updateDeferredPacketLease();
                        }
                    }
                }
            }
        }

        // Return the count of expired oplock breaks
        return expireCnt;
    }

    /**
     * Check access to a path, the file open parameters may not allow the path to be opened
     *
     * @param path String
     * @param params FileOpenParams
     * @return boolean
     */
    public boolean checkAccess(String path, FileOpenParams params) {

        // Get the file state
        FileState fstate = m_stateCache.findFileState(path);

        if ( fstate == null)
            return true;

        // Check if the caller wants write access, check the sharing mode
        boolean accessAllowed = true;

        if ((params.isReadWriteAccess() || params.isWriteOnlyAccess()) && !fstate.getSharedAccess().hasWrite()) {
            accessAllowed = false;
        }

        // Check if the caller wants delete access, check the sharing mode
        else if (params.isDeleteAccess() && !fstate.getSharedAccess().hasDelete()) {
            accessAllowed = false;
        }

        return accessAllowed;
    }

    /**
     * Run the oplock break expiry
     */
    public void run() {

        // Loop forever
        m_shutdown = false;

        while ( !m_shutdown) {
            // Wait for an oplock break or sleep for a while if there are active oplock break requests
            try {
                synchronized (m_oplockQueue) {
                    if ( m_oplockQueue.isEmpty())
                        m_oplockQueue.wait();
                }

                // Oplock break added to the queue, wait a while before checking the queue
                if ( !m_oplockQueue.isEmpty())
                    Thread.sleep(OpLockBreakTimeout);
            }
            catch (InterruptedException ex) {
            }

            //	Check for shutdown
            if ( m_shutdown)
                return;

            // Check for expired oplock break requests
            checkExpiredOplockBreaks();
        }
    }

    /**
     * Request the oplock break expiry thread to shutdown
     */
    public final void shutdownRequest() {
        m_shutdown = true;

        if (m_expiryThread != null) {
            try {
                m_expiryThread.interrupt();
            }
            catch (Exception ex) {
            }
        }
    }

    /**
     * Start the lock manager
     *
     * @param threadName String
     * @param threadPool ThreadRequestPool
     */
    public final void startLockManager(String threadName, ThreadRequestPool threadPool) {

        // Save the thread pool, if specified
        m_threadPool = threadPool;

        // If the thread pool has not been specified then use a seperate thread for the oplock expiry checker
        if (m_threadPool == null) {

            // Start the oplock break expiry thread
            m_expiryThread = new Thread(this);
            m_expiryThread.setDaemon(true);
            m_expiryThread.setName(threadName);
            m_expiryThread.start();
        } else {

            // Queue a timed request to the thread pool to run the oplock expiry check
            m_threadReq = new OplockExpiryTimedRequest(threadName, OpLockBreakTimeoutSecs / 2);
            m_threadPool.queueTimedRequest(m_threadReq);
        }

    }
}
