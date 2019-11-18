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

package org.filesys.smb.server;

import java.io.IOException;

import org.filesys.server.RequestPostProcessor;
import org.filesys.server.SrvSession;
import org.filesys.server.auth.ISMBAuthenticator;
import org.filesys.server.core.NoPooledMemoryException;
import org.filesys.server.filesys.*;
import org.filesys.server.locking.LocalOpLockDetails;
import org.filesys.smb.Dialect;
import org.filesys.smb.server.notify.NotifyChangeEvent;
import org.filesys.smb.server.notify.NotifyRequest;

/**
 * Protocol handler abstract base class.
 *
 * <p>The protocol handler class is the base of all SMB protocol/dialect handler classes.
 *
 * @author gkspencer
 */
public abstract class ProtocolHandler {

    // Negotiate response packet size
    private static final int NegotiateResponseLength    = 4096;

    // Server session that this protocol handler is associated with.
    protected SMBSrvSession m_sess;

    // SMB dialect that has been negotiated with the client
    protected int m_dialect = Dialect.Unknown;

    // Capabilities that are enabled
    protected int m_srvCapabilites;

    /**
     * Create a protocol handler for the specified session.
     */
    protected ProtocolHandler() {
    }

    /**
     * Create a protocol handler for the specified session.
     *
     * @param sess SMBSrvSession
     */
    protected ProtocolHandler(SMBSrvSession sess) {
        m_sess = sess;
    }

    /**
     * Initialize the protocol handler
     *
     * @param smbServer SMBServer
     * @param smbSession SMBSrvSession
     * @param dialect int
     */
    public void initialize( SMBServer smbServer, SMBSrvSession smbSession, int dialect) {
        setSession( smbSession);
        setDialect( dialect);
    }

    /**
     * Return the protocol handler name.
     *
     * @return String
     */
    public abstract String getName();

    /**
     * Run the SMB protocol handler for this server session.
     *
     * @param smbPkt SMBSrvPacket
     * @return boolean
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     * @exception TooManyConnectionsException No more connections available
     */
    public abstract boolean runProtocol(SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException, TooManyConnectionsException;

    /**
     * Get the server session that this protocol handler is associated with.
     *
     * @return SMBSrvSession
     */
    protected final SMBSrvSession getSession() {
        return m_sess;
    }

    /**
     * Set the server session that this protocol handler is associated with.
     *
     * @param sess SMBSrvSession
     */
    protected final void setSession(SMBSrvSession sess) {
        m_sess = sess;
    }

    /**
     * Get the negotiated dialect for this session
     *
     * @return int
     */
    public final int getDialect() {
        return m_dialect;
    }

    /**
     * Set the negotiated dialect for this session
     *
     * @param dialect int
     */
    protected final void setDialect( int dialect) {
        m_dialect = dialect;
    }

    /**
     * Get the server capabilities, flag values vary depending on the SMB version negotiated
     *
     * @return int
     */
    public final int getServerCapabilities() {
        return m_srvCapabilites;
    }

    /**
     * Build a change notification response for the specified change event
     *
     * @param evt NotifyChangeEvent
     * @param req NotifyRequest
     * @return SMBSrvPacket
     */
    public SMBSrvPacket buildChangeNotificationResponse(NotifyChangeEvent evt, NotifyRequest req) {
        return null;
    }

    /**
     * Build an oplock break asynchronous response, sent from the server to the client
     *
     * @param oplock LocalOpLockDetails
     * @return SMBSrvPacket
     */
    public SMBSrvPacket buildOpLockBreakResponse(LocalOpLockDetails oplock) {
        return null;
    }

    /**
     * Set the server capabilities
     *
     * @param srvCapab int
     */
    protected final void setServerCapabilities( int srvCapab) {
        m_srvCapabilites = srvCapab;
    }

    /**
     * Post processing of a negotiate request, after the dialect has been chosen
     *
     * @param smbPkt SMBSrvPacket
     * @param negCtx NegotiateContext
     * @return SMBSrvPacket
     * @exception SMBSrvException SMB error
     */
    public SMBSrvPacket postProcessNegotiate(SMBSrvPacket smbPkt, NegotiateContext negCtx)
        throws SMBSrvException {

        SMBSrvPacket respPkt = smbPkt;

        if ( respPkt.getBufferLength() < NegotiateResponseLength) {

            try {

                // Allocate a larger packet for the negotiate response
                respPkt = m_sess.getPacketPool().allocatePacket( NegotiateResponseLength, smbPkt);

            } catch (NoPooledMemoryException ex) {

            }
        }

        // Return the response packet
        return respPkt;
    }

    /**
     * Get disk sizing information from the specified driver and context.
     *
     * @param disk DiskInterface
     * @param ctx  DiskDeviceContext
     * @return SrvDiskInfo
     * @throws IOException I/O error
     */
    protected final SrvDiskInfo getDiskInformation(DiskInterface disk, DiskDeviceContext ctx)
            throws IOException {

        //	Get the static disk information from the context, if available
        SrvDiskInfo diskInfo = ctx.getDiskInformation();

        //	If we did not get valid disk information from the device context check if the driver implements the
        //	disk sizing interface
        if (diskInfo == null)
            diskInfo = new SrvDiskInfo();

        //	Check if the driver implements the dynamic sizing interface to get realtime disk size information
        if (disk instanceof DiskSizeInterface) {

            //	Get the dynamic disk sizing information
            DiskSizeInterface sizeInterface = (DiskSizeInterface) disk;
            sizeInterface.getDiskInformation(ctx, diskInfo);
        }

        //	Return the disk information
        return diskInfo;
    }

    /**
     * Get disk volume information from the specified driver and context
     *
     * @param disk DiskInterface
     * @param ctx  DiskDeviceContext
     * @return VolumeInfo
     */
    protected final VolumeInfo getVolumeInformation(DiskInterface disk, DiskDeviceContext ctx) {

        //	Get the static volume information from the context, if available
        VolumeInfo volInfo = ctx.getVolumeInformation();

        //	If we did not get valid volume information from the device context check if the driver implements the
        //	disk volume interface
        if (disk instanceof DiskVolumeInterface) {

            //	Get the dynamic disk volume information
            DiskVolumeInterface volInterface = (DiskVolumeInterface) disk;
            volInfo = volInterface.getVolumeInformation(ctx);
        }

        //	If we still have not got  valid volume information then create empty volume information
        if (volInfo == null)
            volInfo = new VolumeInfo("");

        //	Return the volume information
        return volInfo;
    }

    /**
     * Run any request post processors that are queued for a session
     *
     * @param sess SrvSession
     */
    protected final void runRequestPostProcessors(SrvSession sess) {

        // Run the request post processor(s)
        while (sess.hasPostProcessorRequests()) {

            try {

                // Dequeue the current request post processor and run it
                RequestPostProcessor postProc = sess.getNextPostProcessor();
                postProc.runProcessor();
            }
            catch (Throwable ex) {

                // Sink any errors, no a lot that can be done
            }
        }
    }

    /**
     * Convert an ACL permission to a share status
     *
     * @param aclPerm int
     * @return ShareStatus
     */
    protected final ISMBAuthenticator.ShareStatus asShareStatus(int aclPerm) {

        ISMBAuthenticator.ShareStatus sharePerm = ISMBAuthenticator.ShareStatus.NO_ACCESS;

        switch ( aclPerm) {
            case FileAccess.ReadOnly:
                sharePerm = ISMBAuthenticator.ShareStatus.READ_ONLY;
                break;
            case FileAccess.Writeable:
                sharePerm = ISMBAuthenticator.ShareStatus.WRITEABLE;
                break;
        }

        return sharePerm;
    }

    /**
     * Hangup session callback from the session
     *
     * @param sess SMBSrvSession
     * @param reason String
     */
    public void hangupSession( SMBSrvSession sess, String reason) {
    }

    /**
     * Get a virtual circuit list
     *
     * @param maxVC int
     * @return VirtualCircuitList
     */
    public VirtualCircuitList createVirtualCircuitList( int maxVC) {

        // Default is to return an SMB v1 virtual circuit list
        return new SMBV1VirtualCircuitList( maxVC);
    }
}