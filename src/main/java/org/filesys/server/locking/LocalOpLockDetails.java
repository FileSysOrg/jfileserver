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

package org.filesys.server.locking;

import java.io.IOException;
import java.util.ArrayList;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.DeferFailedException;
import org.filesys.smb.OpLockType;
import org.filesys.smb.SMBStatus;
import org.filesys.smb.server.*;

/**
 * Local OpLock Details Class
 *
 * <p>Contains the details of an oplock that is owned by a session on the local node.
 *
 * @author gkspencer
 */
public class LocalOpLockDetails extends OpLockDetailsAdapter {

    // Maximum number of deferred requests allowed
    public static final int MaxDeferredRequests = 3;

    // Oplock type
    private OpLockType m_type;

    // Relative path of oplocked file/folder
    private String m_path;
    private boolean m_folder;

    // List of deferred requests waiting for an oplock break
    private ArrayList<DeferredRequest> m_deferredRequests = new ArrayList<DeferredRequest>(MaxDeferredRequests);

    // Time that the oplock break was sent to the client
    private long m_opBreakTime;

    // Flag to indicate the oplock break timed out
    private boolean m_failedBreak;

    // Oplock owner session
    private SMBSrvSession m_ownerSess;

    // Oplock owner detals
    private OplockOwner m_oplockOwner;

    /**
     * Class constructor
     *
     * @param lockTyp OpLockType
     * @param path    String
     * @param sess    SMBSrvSession
     * @param pkt     SMBSrvPacket
     * @param folder  boolean
     */
    public LocalOpLockDetails(OpLockType lockTyp, String path, SMBSrvSession sess, SMBSrvPacket pkt, boolean folder) {
        m_type = lockTyp;
        m_path = path;

        m_folder = folder;

        m_ownerSess = sess;

        // Get the associated parser
        SMBParser parser = pkt.getParser();
        parser.setOplockOwner(sess, this);
    }

    /**
     * Class constructor
     *
     * @param lockTyp OpLockType
     * @param path    String
     * @param sess    SMBSrvSession
     * @param owner OplockOwner
     * @param folder  boolean
     */
    public LocalOpLockDetails(OpLockType lockTyp, String path, SMBSrvSession sess, OplockOwner owner, boolean folder) {
        m_type = lockTyp;
        m_path = path;

        m_folder = folder;

        m_ownerSess = sess;
        m_oplockOwner = owner;
    }

    /**
     * Return the oplock type
     *
     * @return OpLockType
     */
    public OpLockType getLockType() {
        return m_type;
    }

    /**
     * Return the lock owner session
     *
     * @return SMBSrvSession
     */
    public SMBSrvSession getOwnerSession() {
        return m_ownerSess;
    }

    /**
     * Check if there is an oplock owner
     *
     * @return boolean
     */
    public final boolean hasOplockOwner() {
        return m_oplockOwner != null ? true : false;
    }

    /**
     * Return the oplock owner details
     *
     * @return OplockOwner
     */
    public final OplockOwner getOplockOwner() {
        return m_oplockOwner;
    }

    /**
     * Return the share relative path of the locked file
     *
     * @return String
     */
    public String getPath() {
        return m_path;
    }

    /**
     * Check if the oplock is on a file or folder
     *
     * @return boolean
     */
    public boolean isFolder() {
        return m_folder;
    }

    /**
     * Return the time that the oplock break was sent to the client
     *
     * @return long
     */
    public long getOplockBreakTime() {
        return m_opBreakTime;
    }

    /**
     * Check if this oplock is still valid, or an oplock break has failed
     *
     * @return boolean
     */
    public boolean hasOplockBreakFailed() {
        return m_failedBreak;
    }

    /**
     * Check if this is a remote oplock
     *
     * @return boolean
     */
    public boolean isRemoteLock() {

        // Always local
        return false;
    }

    /**
     * Set the oplock owner details
     *
     * @param owner OplockOwner
     */
    public void setOplockOwner(OplockOwner owner) {
        m_oplockOwner = owner;
    }

    /**
     * Set the failed oplock break flag, to indicate the client did not respond to the oplock break
     * request within a reasonable time.
     */
    public final void setOplockBreakFailed() {

        // Mark the oplock break as failed, timed out
        m_failedBreak = true;

        // DEBUG
        if ( m_ownerSess != null && m_ownerSess.hasDebug( SMBSrvSession.DBG_OPLOCK))
            m_ownerSess.debugPrintln("*** Oplock break failed, timed out");
    }

    /**
     * Set the lock type
     *
     * @param lockTyp OpLockType
     */
    public void setLockType(OpLockType lockTyp) {
        m_type = lockTyp;
    }

    /**
     * Check if there is a deferred session attached to the oplock, this indicates an oplock break is
     * in progress for this oplock.
     *
     * @return boolean
     */
    public boolean hasDeferredSessions() {
        return m_deferredRequests.size() > 0 ? true : false;
    }

    /**
     * Return the count of deferred requests
     *
     * @return int
     */
    public int numberOfDeferredSessions() {
        return m_deferredRequests.size();
    }

    /**
     * Requeue deferred requests to the thread pool for processing, oplock has been released
     *
     * @return int Number of deferred requests requeued
     */
    public int requeueDeferredRequests() {

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

        synchronized (m_deferredRequests) {

            if (m_deferredRequests.size() < MaxDeferredRequests) {

                // Add the deferred request to the list
                m_deferredRequests.add(new DeferredRequest(deferredSess, deferredPkt));

                // Update the deferred processing count for the SMB packet
                deferredPkt.incrementDeferredCount();

                // Set the time that the oplock break was sent to the client, if this is the first deferred request
                if (m_deferredRequests.size() == 1)
                    m_opBreakTime = System.currentTimeMillis();

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

        synchronized (m_deferredRequests) {

            // Update the packet lease time for all deferred packets to prevent them timing out
            long newLeaseTime = System.currentTimeMillis() + SMBPacketPool.SMBLeaseTime;

            for (DeferredRequest deferReq : m_deferredRequests) {
                deferReq.getDeferredPacket().setLeaseTime(newLeaseTime);
            }
        }
    }

    /**
     * Request an oplock break
     */
    public void requestOpLockBreak()
            throws IOException {

        // Check that the session is valid
        if (getOwnerSession() == null || hasOplockBreakFailed())
            return;

        // Build the oplock break packet
        SMBSrvPacket opBreakPkt = getOwnerSession().getProtocolHandler().buildOpLockBreakResponse( this);

        if ( opBreakPkt == null) {

            // DEBUG
            if (Debug.EnableDbg && getOwnerSession().hasDebug(SMBSrvSession.DBG_OPLOCK))
                getOwnerSession().debugPrintln("buildOpLockBreakResponse() returned null");

            return;
        }

        // Send the oplock break to the session that owns the oplock
        boolean breakSent = getOwnerSession().sendAsynchResponseSMB(opBreakPkt, opBreakPkt.getLength());

        // Set the time the oplock break was sent
        m_opBreakTime = System.currentTimeMillis();

        // DEBUG
        if (Debug.EnableDbg && getOwnerSession().hasDebug(SMBSrvSession.DBG_OPLOCK))
            getOwnerSession().debugPrintln("Oplock break sent to " + getOwnerSession().getUniqueId() + " async=" + (breakSent ? "Sent" : "Queued"));
    }

    /**
     * Check if there is an oplock break in progress for this oplock
     *
     * @return boolean
     */
    public boolean hasBreakInProgress() {

        // Check if the oplock break time has been set but the failed oplock flag is clear
        if (m_opBreakTime != 0L && hasOplockBreakFailed() == false)
            return true;
        return false;
    }

    /**
     * Finalize, check if there are any deferred requests in the list
     */
    public void finalize() {
        if (m_deferredRequests != null && m_deferredRequests.size() > 0) {

            // Dump out the list of leaked deferred requests
            Debug.println("** Deferred requests found during oplock finalize, oplock=" + this);

            for (DeferredRequest deferReq : m_deferredRequests)
                Debug.println("**  Leaked deferred request=" + deferReq);
        }
    }

    /**
     * Return the oplock details as a string
     *
     * @return String
     */
    public String toString() {

        StringBuilder str = new StringBuilder();

        str.append("[Local Type=");
        str.append(getLockType().name());
        str.append(",");
        str.append(getPath());
        str.append(",Sess=");
        if (getOwnerSession() != null)
            str.append(getOwnerSession().getUniqueId());
        else
            str.append("NULL");

        str.append(", Owner=");
        if ( hasOplockOwner())
            str.append( getOplockOwner().toString());
        else
            str.append("NULL");

        if (hasDeferredSessions()) {
            str.append(",DeferList=");
            str.append(numberOfDeferredSessions());
        }

        if (hasOplockBreakFailed())
            str.append(" BreakFailed");
        else if (hasBreakInProgress())
            str.append(" BreakInProgress");

        str.append("]");

        return str.toString();
    }
}
