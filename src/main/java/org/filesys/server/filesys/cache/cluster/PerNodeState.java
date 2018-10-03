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

package org.filesys.server.filesys.cache.cluster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.DeferFailedException;
import org.filesys.server.filesys.ExistingOpLockException;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.pseudo.PseudoFileList;
import org.filesys.server.locking.DeferredRequest;
import org.filesys.server.locking.LocalOpLockDetails;
import org.filesys.smb.SMBStatus;
import org.filesys.smb.server.SMBPacketPool;
import org.filesys.smb.server.SMBThreadRequest;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;

/**
 * Per Node File State Data Class
 *
 * <p>Contains per node values for a file state that cannot be stored within the cluster cache or have per node
 * values which cannot be shared.
 *
 * @author gkspencer
 */
public class PerNodeState {

    // Maximum number of deferred requests allowed
    public static final int MaxDeferredRequests = 3;

    //	File identifier
    private int m_fileId = FileState.UnknownFileId;

    //	File data status
    private FileState.DataStatus m_dataStatus = FileState.DataStatus.LoadWait;

    //	Cache of various file information
    private HashMap<String, Object> m_cache;

    // Pseudo file list
    private PseudoFileList m_pseudoFiles;

    // Filesystem specific object
    private Object m_filesysObj;

    // Local oplock details
    private LocalOpLockDetails m_localOpLock;

    // List of deferred requests waiting for an oplock break
    private ArrayList<DeferredRequest> m_deferredRequests;

    // Time that an oplock break was requested
    private long m_oplockBreakTime;

    /**
     * Default constructor
     */
    public PerNodeState() {
    }

    /**
     * Get the file id
     *
     * @return int
     */
    public int getFileId() {
        return m_fileId;
    }

    /**
     * Return the file data status
     *
     * @return DataStatus
     */
    public FileState.DataStatus getDataStatus() {
        return m_dataStatus;
    }

    /**
     * Set the file identifier
     *
     * @param id int
     */
    public void setFileId(int id) {
        m_fileId = id;
    }

    /**
     * Set the file data status
     *
     * @param sts DataStatus
     */
    public void setDataStatus(FileState.DataStatus sts) {
        m_dataStatus = sts;
    }

    /**
     * Determine if a folder has pseudo files associated with it
     *
     * @return boolean
     */
    public boolean hasPseudoFiles() {
        if (m_pseudoFiles != null)
            return m_pseudoFiles.numberOfFiles() > 0;
        return false;
    }

    /**
     * Return the pseudo file list
     *
     * @param createList boolean
     * @return PseudoFileList
     */
    protected PseudoFileList getPseudoFileList(boolean createList) {
        if (m_pseudoFiles == null && createList == true)
            m_pseudoFiles = new PseudoFileList();
        return m_pseudoFiles;
    }

    /**
     * Return the filesystem object
     *
     * @return Object
     */
    public Object getFilesystemObject() {
        return m_filesysObj;
    }

    /**
     * Set the filesystem object
     *
     * @param filesysObj Object
     */
    public void setFilesystemObject(Object filesysObj) {
        m_filesysObj = filesysObj;
    }

    /**
     * Return the map of additional attribute objects attached to this file state, and
     * optionally create the map if it does not exist
     *
     * @param createMap boolean
     * @return HashMap
     */
    protected HashMap<String, Object> getAttributeMap(boolean createMap) {
        if (m_cache == null && createMap == true)
            m_cache = new HashMap<String, Object>();
        return m_cache;
    }

    /**
     * Clear the attributes
     */
    public final void remoteAllAttributes() {
        if (m_cache != null) {
            m_cache.clear();
            m_cache = null;
        }
    }

    /**
     * Check if the file has an active oplock
     *
     * @return boolean
     */
    public boolean hasOpLock() {
        return m_localOpLock != null ? true : false;
    }

    /**
     * Return the oplock details
     *
     * @return LocalOpLockDetails
     */
    public LocalOpLockDetails getOpLock() {
        return m_localOpLock;
    }

    /**
     * Set the oplock for this file
     *
     * @param oplock LocalOpLockDetails
     * @throws ExistingOpLockException If there is an active oplock on this file
     */
    public synchronized void setOpLock(LocalOpLockDetails oplock)
            throws ExistingOpLockException {

        if (m_localOpLock == null)
            m_localOpLock = oplock;
        else
            throw new ExistingOpLockException();
    }

    /**
     * Clear the oplock
     */
    public synchronized void clearOpLock() {
        m_localOpLock = null;
    }

    /**
     * Check if there is a deferred session attached to the oplock, this indicates an oplock break is
     * in progress for this oplock.
     *
     * @return boolean
     */
    public boolean hasDeferredSessions() {
        if (m_deferredRequests == null)
            return false;
        return m_deferredRequests.size() > 0 ? true : false;
    }

    /**
     * Return the count of deferred requests
     *
     * @return int
     */
    public int numberOfDeferredSessions() {
        if (m_deferredRequests == null)
            return 0;
        return m_deferredRequests.size();
    }

    /**
     * Requeue deferred requests to the thread pool for processing, oplock has been released
     *
     * @return int Number of deferred requests requeued
     */
    public int requeueDeferredRequests() {

        // Check if there are any deferred requests
        if (m_deferredRequests == null)
            return 0;

        int requeueCnt = 0;

        synchronized (m_deferredRequests) {

            for (DeferredRequest deferReq : m_deferredRequests) {

                // Get the deferred session/packet details
                SMBSrvSession sess = deferReq.getDeferredSession();
                SMBSrvPacket pkt = deferReq.getDeferredPacket();

                // DEBUG
                if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                    Debug.println("Release oplock, queued deferred request to thread pool sess=" + sess.getUniqueId() + ", pkt=" + pkt);

                try {

                    // Queue the deferred request to the thread pool for processing
                    sess.getThreadPool().queueRequest(new SMBThreadRequest(sess, pkt));
                }
                catch (Throwable ex) {

                    // Failed to queue the request to the thread pool, release the deferred packet back to the
                    // memory pool
                    sess.getPacketPool().releasePacket(pkt);
                }
            }

            // Clear the deferred request list
            m_deferredRequests.clear();
        }

        // Return the count of requeued requests
        return requeueCnt;
    }

    /**
     * Fail any deferred requests that are attached to this oplock, and clear the deferred list
     *
     * @return int Number of deferred requests that were failed
     */
    public int failDeferredRequests() {

        // Check if there are any deferred requests
        if (m_deferredRequests == null)
            return 0;

        int failCnt = 0;

        synchronized (m_deferredRequests) {

            for (DeferredRequest deferReq : m_deferredRequests) {

                // Get the deferred session/packet details
                SMBSrvSession sess = deferReq.getDeferredSession();
                SMBSrvPacket pkt = deferReq.getDeferredPacket();

                try {

                    // Return an error for the deferred file open request
                    if (sess.sendAsyncErrorResponseSMB(pkt, SMBStatus.NTAccessDenied, SMBStatus.NTErr) == true) {

                        // Update the failed request count
                        failCnt++;

                        // DEBUG
                        if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                            Debug.println("Oplock break timeout, oplock=" + this);
                    } else if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                        Debug.println("Failed to send open reject, oplock break timed out, oplock=" + this);
                }
                catch (IOException ex) {

                }
                finally {

                    // Make sure the packet is released back to the memory pool
                    if (pkt != null)
                        sess.getPacketPool().releasePacket(pkt);
                }
            }

            // Clear the deferred request list
            m_deferredRequests.clear();
        }

        // Return the count of failed requests
        return failCnt;
    }

    /**
     * Add a deferred session/packet, whilst an oplock break is in progress
     *
     * @param deferredSess SMBSrvSession
     * @param deferredPkt  SMBSrvPacket
     * @throws DeferFailedException If the session/packet cannot be deferred
     */
    public void addDeferredSession(SMBSrvSession deferredSess, SMBSrvPacket deferredPkt)
            throws DeferFailedException {

        // Allocate the deferred request list, if required
        if (m_deferredRequests == null) {
            synchronized (this) {
                if (m_deferredRequests == null)
                    m_deferredRequests = new ArrayList<DeferredRequest>(MaxDeferredRequests);
            }
        }

        // Add the request to the list if there are spare slots
        synchronized (m_deferredRequests) {

            if (m_deferredRequests.size() < MaxDeferredRequests) {

                // Add the deferred request to the list
                m_deferredRequests.add(new DeferredRequest(deferredSess, deferredPkt));

                // Update the deferred processing count for the CIFS packet
                deferredPkt.incrementDeferredCount();

                // Set the time that the oplock break was sent to the client, if this is the first deferred request
                if (m_deferredRequests.size() == 1)
                    m_oplockBreakTime = System.currentTimeMillis();

                // DEBUG
                if (Debug.EnableDbg && deferredSess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                    Debug.println("Added deferred request, list=" + m_deferredRequests.size() + ", oplock=" + this);
            } else
                throw new DeferFailedException("No more deferred slots available on oplock");
        }
    }

    /**
     * Update the deferred packet lease time(s) as we wait for an oplock break or timeout
     */
    public void updateDeferredPacketLease() {

        // Check if there are deferred requests
        if (m_deferredRequests != null) {

            synchronized (m_deferredRequests) {

                // Update the packet lease time for all deferred packets to prevent them timing out
                long newLeaseTime = System.currentTimeMillis() + SMBPacketPool.SMBLeaseTime;

                for (DeferredRequest deferReq : m_deferredRequests) {
                    deferReq.getDeferredPacket().setLeaseTime(newLeaseTime);
                }
            }
        }
    }

    /**
     * Return the oplock break time
     *
     * @return long
     */
    public final long getOplockBreakTime() {
        return m_oplockBreakTime;
    }

    /**
     * Finalize, check if there are any deferred requests in the list
     */
    public void finalize() {
        if (m_deferredRequests != null && m_deferredRequests.size() > 0) {

            // Dump out the list of leaked deferred requests
            Debug.println("** Deferred requests found during per node finalize, perNode=" + this);

            for (DeferredRequest deferReq : m_deferredRequests)
                Debug.println("**  Leaked deferred request=" + deferReq);
        }
    }

    /**
     * Return the per node state as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[FID=");
        str.append(getFileId());
        str.append(",data=");
        str.append(getDataStatus());
        str.append(",filesysObj=");
        str.append(getFilesystemObject());
        str.append(",oplock=");
        str.append(getOpLock());

        if (hasDeferredSessions()) {
            str.append(",DeferList=");
            str.append(numberOfDeferredSessions());
        }

        str.append("]");

        return str.toString();
    }
}
