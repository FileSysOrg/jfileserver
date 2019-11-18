/*
 * Copyright (C) 2006-2013 Alfresco Software Limited.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Time;
import java.util.Set;

import org.filesys.debug.Debug;
import org.filesys.locking.FileLock;
import org.filesys.locking.LockConflictException;
import org.filesys.locking.NotLockedException;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.server.auth.ISMBAuthenticator;
import org.filesys.server.auth.InvalidUserException;
import org.filesys.server.auth.acl.AccessControl;
import org.filesys.server.auth.acl.AccessControlManager;
import org.filesys.server.core.InvalidDeviceInterfaceException;
import org.filesys.server.core.ShareType;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.filesys.*;
import org.filesys.server.locking.*;
import org.filesys.smb.*;
import org.filesys.smb.nt.LoadException;
import org.filesys.smb.nt.NTIOCtl;
import org.filesys.smb.nt.SaveException;
import org.filesys.smb.nt.SecurityDescriptor;
import org.filesys.smb.server.notify.NotifyChangeEvent;
import org.filesys.smb.server.notify.NotifyChangeEventList;
import org.filesys.smb.server.notify.NotifyChangeHandler;
import org.filesys.smb.server.notify.NotifyRequest;
import org.filesys.smb.server.ntfs.NTFSStreamsInterface;
import org.filesys.smb.server.ntfs.StreamInfoList;
import org.filesys.util.DataBuffer;
import org.filesys.util.DataPacker;
import org.filesys.util.MemorySize;
import org.filesys.util.WildCard;

/**
 * NT SMB Protocol Handler Class
 *
 * <p>
 * The NT protocol handler processes the additional SMBs that were added to the protocol in the NT
 * SMB dialect.
 *
 * @author gkspencer
 */
public class NTProtocolHandler extends CoreProtocolHandler {

    // Constants
    //
    // Flag to enable returning of '.' and '..' directory information in FindFirst request
    public static final boolean ReturnDotFiles = true;

    // Dummy date/time for dot files
    public static final long DotFileDateTime = System.currentTimeMillis();

    // Flag to enable faking of oplock requests when opening files
    public static final boolean FakeOpLocks = false;

    // Number of write requests per file to report file size change notifications
    public static final int FileSizeChangeRate = 10;

    // Maximum path size that the filesystem accepts
    public static final int MaxPathLength = 255;

    // NTFS streams information buffer size
    public static final int NTFSStreamsInfoBufsize = 4096;    // 4K buffer

    // Security descriptor to allow Everyone access, returned by the QuerySecurityDescrptor NT
    // transaction when NTFS streams are enabled for a virtual filesystem.
    private static byte[] _sdEveryOne = {0x01, 0x00, 0x04, (byte) 0x80, 0x14, 0x00, 0x00, 0x00, 0x20, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x2c, 0x00, 0x00, 0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x1c, 0x00, 0x01, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x14, 0x00, (byte) 0xff, 0x01, 0x1f, 0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00,
            0x00, 0x00, 0x00};

    /**
     * Class constructor.
     */
    protected NTProtocolHandler() {
        super();
    }

    /**
     * Class constructor
     *
     * @param sess SMBSrvSession
     */
    protected NTProtocolHandler(SMBSrvSession sess) {
        super(sess);
    }

    /**
     * Return the protocol name
     *
     * @return String
     */
    public String getName() {
        return "NT";
    }


    /**
     * Process the NT SMB session setup request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     * @exception TooManyConnectionsException No more connections available
     */
    protected void procSessionSetup(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws SMBSrvException, IOException, TooManyConnectionsException {

        // Call the authenticator to process the session setup
        ISMBAuthenticator smbAuthenticator = m_sess.getSMBServer().getSMBAuthenticator();

        try {

            // Process the session setup request, build the response
            smbAuthenticator.processSessionSetup(m_sess, smbPkt);
        }
        catch (SMBSrvException ex) {

            // Return an error response to the client
            m_sess.sendErrorResponseSMB(smbPkt, ex.getNTErrorCode(), ex.getErrorCode(), ex.getErrorClass());
            return;
        }

        // Check if a new packet was allocated for the response
        SMBSrvPacket outPkt = smbPkt;
        if (smbPkt.hasAssociatedPacket() && parser.hasAndXCommand() == false)
            outPkt = outPkt.getAssociatedPacket();

        // Check if there is a chained command, or commands
        SMBV1Parser respParser = (SMBV1Parser) outPkt.getParser();
        int pos = respParser.getLength();

        if (parser.hasAndXCommand() && parser.getPosition() < smbPkt.getReceivedLength()) {

            // Process any chained commands, AndX
            pos = procAndXCommands(outPkt, parser, null);
            pos -= RFCNetBIOSProtocol.HEADER_LEN;

            // Switch to the response packet
            outPkt = smbPkt.getAssociatedPacket();

            // Get the associated parser
            respParser = (SMBV1Parser) outPkt.getParser();
        }
        else {

            // Indicate that there are no chained replies
            respParser.setAndXCommand(SMBV1.NO_ANDX_CMD);
        }

        // Make sure the packet is a response
        respParser.setResponse();

        // Send the session setup response
        m_sess.sendResponseSMB(outPkt, pos);

        // Update the session state if the response indicates a success status. A multi stage
        // session setup response returns a warning status.
        if (respParser.getLongErrorCode() == SMBStatus.NTSuccess) {

            // Update the session state
            m_sess.setState(SessionState.SMB_SESSION);

            // Find the virtual circuit allocated, this will set the per-thread ClientInfo on the session
            m_sess.findVirtualCircuit(respParser.getUserId());

            // Notify listeners that a user has logged onto the session
            m_sess.getSMBServer().sessionLoggedOn(m_sess);
        }
    }

    /**
     * Process the chained SMB commands (AndX).
     *
     * @param smbPkt Request packet.
     * @param parser SMBV1Parser
     * @param file   Current file , or null if no file context in chain
     * @return New offset to the end of the reply packet
     */
    protected final int procAndXCommands(SMBSrvPacket smbPkt, SMBV1Parser parser, NetworkFile file) {

        // Get the response packet
        SMBSrvPacket respPkt = smbPkt.getAssociatedPacket();
        if (respPkt == null)
            throw new RuntimeException("No response packet allocated for AndX request");

        // Get the chained command and command block offset
        int andxCmd = parser.getAndXCommand();
        int andxOff = parser.getParameter(1) + RFCNetBIOSProtocol.HEADER_LEN;

        // Set the parser for the response
        respPkt.setParser( SMBSrvPacket.Version.V1);
        SMBV1Parser respParser = (SMBV1Parser) respPkt.getParser();

        // Set the initial chained command and offset
        respParser.setAndXCommand(andxCmd);

        int endOfPkt = respParser.getByteOffset() + respParser.getByteCount();
        respParser.setParameter(1, endOfPkt - RFCNetBIOSProtocol.HEADER_LEN);

        // Pointer to the last parameter block, starts with the main command parameter block
        int paramBlk = SMBV1.WORDCNT;

        // Get the current end of the reply packet offset
        boolean andxErr = false;

        while (andxCmd != SMBV1.NO_ANDX_CMD && andxErr == false) {

            // Determine the chained command type
            int prevEndOfPkt = endOfPkt;
            boolean endOfChain = false;

            switch (andxCmd) {

                // Tree connect
                case PacketTypeV1.TreeConnectAndX:
                    endOfPkt = procChainedTreeConnectAndX(andxOff, smbPkt, respPkt, endOfPkt);
                    break;

                // Close file
                case PacketTypeV1.CloseFile:
                    endOfPkt = procChainedClose(andxOff, smbPkt, respPkt, endOfPkt);
                    endOfChain = true;
                    break;

                // Read file
                case PacketTypeV1.ReadAndX:
                    endOfPkt = procChainedReadAndX(andxOff, smbPkt, respPkt, endOfPkt, file);
                    break;

                // Chained command was not handled
                default:
                    if (Debug.EnableError)
                        Debug.println("<<<<< Chained command : 0x" + Integer.toHexString(andxCmd) + " Not Processed >>>>>");
                    break;
            }

            // Set the next chained command details in the current parameter block
            respParser.setAndXCommand(paramBlk, andxCmd);
            respParser.setAndXParameter(paramBlk, 1, prevEndOfPkt - RFCNetBIOSProtocol.HEADER_LEN);

            // Check if the end of chain has been reached, if not then look for the next chained command in the request.
            // End of chain might be set if the current command is not an AndX SMB command.
            if (endOfChain == false) {

                // Advance to the next chained command block
                andxCmd = parser.getAndXParameter(andxOff, 0) & 0x00FF;
                andxOff = parser.getAndXParameter(andxOff, 1);

                // Advance the current parameter block
                paramBlk = prevEndOfPkt;
            }
            else {

                // Indicate that the end of the command chain has been reached
                andxCmd = SMBV1.NO_ANDX_CMD;
            }

            // Check if the chained command has generated an error status
            if (respParser.getErrorCode() != SMBStatus.Success)
                andxErr = true;
        }

        // Return the offset to the end of the reply packet
        return endOfPkt;
    }

    /**
     * Process a chained tree connect request.
     *
     * @param cmdOff  int Offset to the chained command within the request packet.
     * @param smbPkt  Request packet.
     * @param respPkt Response packet
     * @param endOff  int Offset to the current end of the reply packet.
     * @return New end of reply offset.
     */
    protected final int procChainedTreeConnectAndX(int cmdOff, SMBSrvPacket smbPkt, SMBSrvPacket respPkt, int endOff) {

        // Get the request and response parsers
        SMBV1Parser reqParser  = (SMBV1Parser) smbPkt.getParser();
        SMBV1Parser respParser = (SMBV1Parser) respPkt.getParser();

        // Extract the parameters
        int flags = reqParser.getAndXParameter(cmdOff, 2);
        int pwdLen = reqParser.getAndXParameter(cmdOff, 3);

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(respParser.getUserId());

        if (vc == null) {
            respParser.setError(reqParser.isLongErrorCode(), SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return endOff;
        }

        // Reset the byte pointer for data unpacking
        reqParser.setBytePointer(reqParser.getAndXByteOffset(cmdOff), reqParser.getAndXByteCount(cmdOff));

        // Extract the password string
        String pwd = null;

        if (pwdLen > 0) {
            byte[] pwdByt = reqParser.unpackBytes(pwdLen);
            pwd = new String(pwdByt);
        }

        // Extract the requested share name, as a UNC path
        boolean unicode = reqParser.isUnicode();

        String uncPath = reqParser.unpackString(unicode);
        if (uncPath == null) {
            respParser.setError(reqParser.isLongErrorCode(), SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return endOff;
        }

        // Extract the service type string
        String service = reqParser.unpackString(false);
        if (service == null) {
            respParser.setError(reqParser.isLongErrorCode(), SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return endOff;
        }

        // Convert the service type to a shared device type, client may specify '?????' in which case we ignore the error.
        ShareType servType = ShareType.ServiceAsType(service);

        if (servType == ShareType.UNKNOWN && service.compareTo("?????") != 0) {
            respParser.setError(reqParser.isLongErrorCode(), SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return endOff;
        }

        // Debug
        if (m_sess.hasDebug(SMBSrvSession.DBG_TREE))
            m_sess.debugPrintln("NT ANDX Tree Connect AndX - " + uncPath + ", " + service);

        // Parse the requested share name
        PCShare share = null;

        try {
            share = new PCShare(uncPath);
        }
        catch (InvalidUNCPathException ex) {
            respParser.setError(reqParser.isLongErrorCode(), SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return endOff;
        }

        // Map the IPC$ share to the admin pipe type
        if (share.getShareName().compareTo("IPC$") == 0)
            servType = ShareType.ADMINPIPE;

        // Check if the session is a null session, only allow access to the IPC$ named pipe share
        if (m_sess.hasClientInformation() && m_sess.getClientInformation().isNullSession() && servType != ShareType.ADMINPIPE) {

            // Return an error status
            respParser.setError(reqParser.isLongErrorCode(), SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return endOff;
        }

        // Find the requested shared device
        SharedDevice shareDev = null;

        try {

            // Get/create the shared device
            shareDev = m_sess.getSMBServer().findShare(share.getNodeName(), share.getShareName(), servType, m_sess, true);
        }
        catch (InvalidUserException ex) {

            // Return a logon failure status
            respParser.setError(reqParser.isLongErrorCode(), SMBStatus.NTLogonFailure, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return endOff;
        }
        catch (Exception ex) {

            // Return a general status, bad network name
            respParser.setError(reqParser.isLongErrorCode(), SMBStatus.NTBadNetName, SMBStatus.SRVInvalidNetworkName, SMBStatus.ErrSrv);
            return endOff;
        }

        // Check if the share is valid
        if (shareDev == null || (servType != ShareType.UNKNOWN && shareDev.getType() != servType)) {

            // Set the error status
            respParser.setError(reqParser.isLongErrorCode(), SMBStatus.NTBadNetName, SMBStatus.SRVInvalidNetworkName, SMBStatus.ErrSrv);
            return endOff;
        }

        // Authenticate the share connect, if the server is using share mode security
        ISMBAuthenticator auth = getSession().getSMBServer().getSMBAuthenticator();
        ISMBAuthenticator.ShareStatus sharePerm = ISMBAuthenticator.ShareStatus.WRITEABLE;

        if (auth != null && auth.getAccessMode() == ISMBAuthenticator.AuthMode.SHARE) {

            // Validate the share connection
            sharePerm = auth.authenticateShareConnect(m_sess.getClientInformation(), shareDev, pwd, m_sess);

            if (sharePerm == ISMBAuthenticator.ShareStatus.NO_ACCESS) {

                // Invalid share connection request
                respParser.setError(reqParser.isLongErrorCode(), SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
                return endOff;
            }
        }

        // Check if there is an access control manager, if so then run any access controls to
        // determine the sessions access to the share.
        if (getSession().getServer().hasAccessControlManager() && shareDev.hasAccessControls()) {

            // Get the access control manager
            AccessControlManager aclMgr = getSession().getServer().getAccessControlManager();

            // Update the access permission for this session by processing the access control list
            // for the shared device
            int aclPerm = aclMgr.checkAccessControl(getSession(), shareDev);

            if (aclPerm == FileAccess.NoAccess) {

                // Invalid share connection request
                respParser.setError(reqParser.isLongErrorCode(), SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
                return endOff;
            }

            // If the access controls returned a new access type update the main permission
            if (aclPerm != AccessControl.Default)
                sharePerm = asShareStatus( aclPerm);
        }

        // Allocate a tree id for the new connection
        TreeConnection tree = null;

        try {

            // Allocate the tree id for this connection
            int treeId = vc.addConnection(shareDev);
            respParser.setTreeId(treeId);

            // Set the file permission that this user has been granted for this share
            tree = vc.findConnection(treeId);
            tree.setPermission(sharePerm);

            // Inform the driver that a connection has been opened
            if (tree.getInterface() != null)
                tree.getInterface().treeOpened(m_sess, tree);

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TREE))
                m_sess.debugPrintln("ANDX Tree Connect AndX - Allocated Tree Id = " + treeId);
        }
        catch (TooManyConnectionsException ex) {

            // Too many connections open at the moment
            respParser.setError(SMBStatus.SRVNoResourcesAvailable, SMBStatus.ErrSrv);
            return endOff;
        }

        // Build the tree connect response
        respParser.setAndXParameterCount(endOff, 2);
        respParser.setAndXParameter(endOff, 0, SMBV1.NO_ANDX_CMD);
        respParser.setAndXParameter(endOff, 1, 0);

        // Pack the service type
        int pos = respParser.getAndXByteOffset(endOff);
        byte[] outBuf = respParser.getBuffer();
        pos = DataPacker.putString(ShareType.TypeAsService(shareDev.getType()), outBuf, pos, true);

        // Determine the filesystem type, for disk shares
        String devType = "";

        try {

            // Check if this is a disk shared device
            if (shareDev.getType() == ShareType.DISK) {

                // Check if the filesystem driver implements the NTFS streams interface, and streams
                // are enabled
                if (shareDev.getInterface() instanceof NTFSStreamsInterface) {

                    // Check if NTFS streams are enabled
                    NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) shareDev.getInterface();
                    if (ntfsStreams.hasStreamsEnabled(m_sess, tree))
                        devType = FileSystem.TypeNTFS;
                }
                else {

                    // Get the filesystem type from the context
                    DiskDeviceContext diskCtx = (DiskDeviceContext) tree.getContext();
                    devType = diskCtx.getFilesystemType();
                }
            }
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Debug
            if (Debug.EnableError && m_sess.hasDebug(SMBSrvSession.DBG_TREE))
                Debug.println("ANDX TreeConnectAndX error " + ex.getMessage());
        }

        // Pack the filesystem type
        pos = DataPacker.putString(devType, outBuf, pos, true, respParser.isUnicode());

        int bytLen = pos - respParser.getAndXByteOffset(endOff);
        respParser.setAndXByteCount(endOff, bytLen);

        // Return the new end of packet offset
        return pos;
    }

    /**
     * Process a chained read file request
     *
     * @param cmdOff  Offset to the chained command within the request packet.
     * @param smbPkt  Request packet.
     * @param respPkt Response packet
     * @param endOff  Offset to the current end of the reply packet.
     * @param netFile File to be read, passed down the chained requests
     * @return New end of reply offset.
     */
    protected final int procChainedReadAndX(int cmdOff, SMBSrvPacket smbPkt, SMBSrvPacket respPkt, int endOff, NetworkFile netFile) {

        // Get the request and response parsers
        SMBV1Parser reqParser  = (SMBV1Parser) smbPkt.getParser();
        SMBV1Parser respParser = (SMBV1Parser) respPkt.getParser();

        // Get the tree id from the received packet and validate that it is a valid connection id.
        TreeConnection conn = m_sess.findTreeConnection(smbPkt);

        if (conn == null) {
            respParser.setError(SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return endOff;
        }

        // Extract the read file parameters
        long offset = (long) reqParser.getAndXParameterLong(cmdOff, 3); // bottom 32bits of read offset
        offset &= 0xFFFFFFFFL;
        int maxCount = reqParser.getAndXParameter(cmdOff, 5);

        // Check for the NT format request that has the top 32bits of the file offset
        if (reqParser.getAndXParameterCount(cmdOff) == 12) {
            long topOff = (long) reqParser.getAndXParameterLong(cmdOff, 10);
            offset += topOff << 32;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
            m_sess.debugPrintln("Chained File Read AndX : Size=" + maxCount + " ,Pos=" + offset);

        // Read data from the file
        byte[] buf = respPkt.getBuffer();
        int dataPos = 0;
        int rdlen = 0;

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Set the returned parameter count so that the byte offset can be calculated
            respParser.setAndXParameterCount(endOff, 12);
            dataPos = respParser.getAndXByteOffset(endOff);
            dataPos = DataPacker.wordAlign(dataPos); // align the data buffer

            // Check if the requested data length will fit into the buffer
            int dataLen = buf.length - dataPos;
            if (dataLen < maxCount)
                maxCount = dataLen;

            // Read from the file
            // Synchronize reads using the network file
            synchronized (netFile) {
                rdlen = disk.readFile(m_sess, conn, netFile, buf, dataPos, maxCount, offset);
            }

            // Return the data block
            respParser.setAndXParameter(endOff, 0, SMBV1.NO_ANDX_CMD);
            respParser.setAndXParameter(endOff, 1, 0);

            respParser.setAndXParameter(endOff, 2, 0); // bytes remaining, for pipes only
            respParser.setAndXParameter(endOff, 3, 0); // data compaction mode
            respParser.setAndXParameter(endOff, 4, 0); // reserved
            respParser.setAndXParameter(endOff, 5, rdlen); // data length
            respParser.setAndXParameter(endOff, 6, dataPos - RFCNetBIOSProtocol.HEADER_LEN); // offset to data

            // Clear the reserved parameters
            for (int i = 7; i < 12; i++)
                respParser.setAndXParameter(endOff, i, 0);

            // Set the byte count
            respParser.setAndXByteCount(endOff, (dataPos + rdlen) - respParser.getAndXByteOffset(endOff));

            // Update the end offset for the new end of packet
            endOff = dataPos + rdlen;
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            respParser.setError(SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return endOff;
        }
        catch (java.io.IOException ex) {
        }

        // Return the new end of packet offset
        return endOff;
    }

    /**
     * Process a chained close file request
     *
     * @param cmdOff  int Offset to the chained command within the request packet.
     * @param smbPkt  Request packet.
     * @param respPkt Response packet
     * @param endOff  int Offset to the current end of the reply packet.
     * @return New end of reply offset.
     */
    protected final int procChainedClose(int cmdOff, SMBSrvPacket smbPkt, SMBSrvPacket respPkt, int endOff) {

        // Get the request and response parsers
        SMBV1Parser reqParser  = (SMBV1Parser) smbPkt.getParser();
        SMBV1Parser respParser = (SMBV1Parser) respPkt.getParser();

        // Get the tree id from the received packet and validate that it is a valid connection id.
        TreeConnection conn = m_sess.findTreeConnection(smbPkt);

        if (conn == null) {
            respParser.setError(SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return endOff;
        }

        // Get the file id from the request
        int fid = reqParser.getAndXParameter(cmdOff, 0);
        int ftime = reqParser.getAndXParameter(cmdOff, 1);
        int fdate = reqParser.getAndXParameter(cmdOff, 2);

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            respParser.setError(SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return endOff;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
            m_sess.debugPrintln("Chained File Close [" + reqParser.getTreeId() + "] fid=" + fid);

        // Close the file
        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Close the file
            //
            // The disk interface may be null if the file is a named pipe file
            if (disk != null)
                disk.closeFile(m_sess, conn, netFile);

            // Indicate that the file has been closed
            netFile.setClosed(true);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            respParser.setError(SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return endOff;
        }
        catch (java.io.IOException ex) {
        }

        // Clear the returned parameter count and byte count
        respParser.setAndXParameterCount(endOff, 0);
        respParser.setAndXByteCount(endOff, 0);

        endOff = respParser.getAndXByteOffset(endOff) - RFCNetBIOSProtocol.HEADER_LEN;

        // Remove the file from the connections list of open files
        conn.removeFile(fid, getSession());

        // Return the new end of packet offset
        return endOff;
    }

    /**
     * Process the SMB tree connect request.
     *
     * @param smbPkt Request packet.
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     * @exception TooManyConnectionsException No more connections available
     */
    protected void procTreeConnectAndX(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws SMBSrvException, TooManyConnectionsException, java.io.IOException {

        // Check that the received packet looks like a valid tree connect request
        if (parser.checkPacketIsValid(4, 3) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());
        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Extract the parameters
        int flags = parser.getParameter(2);
        int pwdLen = parser.getParameter(3);

        // Initialize the byte area pointer
        parser.resetBytePointer();

        // Determine if ASCII or unicode strings are being used
        boolean unicode = parser.isUnicode();

        // Extract the password string
        String pwd = null;

        if (pwdLen > 0) {
            byte[] pwdByts = parser.unpackBytes(pwdLen);
            pwd = new String(pwdByts);
        }

        // Extract the requested share name, as a UNC path
        String uncPath = parser.unpackString(unicode);
        if (uncPath == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Extract the service type string, always seems to be ASCII
        String service = parser.unpackString(false);
        if (service == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Convert the service type to a shared device type, client may specify '?????' in which case we ignore the error.
        ShareType servType = ShareType.ServiceAsType(service);

        if (servType == ShareType.UNKNOWN && service.compareTo("?????") != 0) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TREE))
            m_sess.debugPrintln("NT Tree Connect AndX - " + uncPath + ", " + service + ", flags=" + TreeConnectAndX.asStringRequest(flags) + "/0x" + Integer.toHexString(flags));

        // Parse the requested share name
        String shareName = null;
        String hostName = null;

        if (uncPath.startsWith("\\")) {

            try {
                PCShare share = new PCShare(uncPath);
                shareName = share.getShareName();
                hostName = share.getNodeName();
            }
            catch (InvalidUNCPathException ex) {
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
                return;
            }
        }
        else
            shareName = uncPath;

        // Map the IPC$ share to the admin pipe type
        if (shareName.compareTo("IPC$") == 0)
            servType = ShareType.ADMINPIPE;

        // Check if the session is a null session, only allow access to the IPC$ named pipe share
        if (m_sess.hasClientInformation() && m_sess.getClientInformation().isNullSession() && servType != ShareType.ADMINPIPE) {

            // Return an error status
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Find the requested shared device
        SharedDevice shareDev = null;

        try {

            // Get/create the shared device
            shareDev = m_sess.getSMBServer().findShare(hostName, shareName, servType, m_sess, true);
        }
        catch (InvalidUserException ex) {

            // Return a logon failure status
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTLogonFailure, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (Exception ex) {

            // Return a general status, bad network name
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTBadNetName, SMBStatus.SRVInvalidNetworkName, SMBStatus.ErrSrv);
            return;
        }

        // Check if the share is valid
        if (shareDev == null || (servType != ShareType.UNKNOWN && shareDev.getType() != servType)) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTBadNetName, SMBStatus.SRVInvalidNetworkName, SMBStatus.ErrSrv);
            return;
        }

        // Authenticate the share connection depending upon the security mode the server is running under
        ISMBAuthenticator auth = getSession().getSMBServer().getSMBAuthenticator();
        ISMBAuthenticator.ShareStatus sharePerm = ISMBAuthenticator.ShareStatus.WRITEABLE;

        if (auth != null) {

            // Validate the share connection
            sharePerm = auth.authenticateShareConnect(m_sess.getClientInformation(), shareDev, pwd, m_sess);

            if (sharePerm == ISMBAuthenticator.ShareStatus.NO_ACCESS) {

                // DEBUG
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TREE))
                    m_sess.debugPrint("Tree connect to " + shareName + ", access denied");

                // Invalid share connection request
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
                return;
            }
        }

        // Check if there is an access control manager, if so then run any access controls to determine the sessions
        // access to the share.
        if (getSession().getServer().hasAccessControlManager() && shareDev.hasAccessControls()) {

            // Get the access control manager
            AccessControlManager aclMgr = getSession().getServer().getAccessControlManager();

            // Update the access permission for this session by processing the access control list
            // for the shared device
            int aclPerm = aclMgr.checkAccessControl(getSession(), shareDev);

            if (aclPerm == FileAccess.NoAccess) {

                // Invalid share connection request
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
                return;
            }

            // If the access controls returned a new access type update the main permission
            if (aclPerm != AccessControl.Default)
                sharePerm = asShareStatus( aclPerm);
        }

        // Allocate a tree id for the new connection
        int treeId = vc.addConnection(shareDev);
        parser.setTreeId(treeId);

        // Set the file permission that this user has been granted for this share
        TreeConnection tree = vc.findConnection(treeId);
        tree.setPermission(sharePerm);

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TREE))
            m_sess.debugPrintln("Tree Connect AndX - Allocated Tree Id = " + treeId + ", Permission = "
                    + sharePerm.name() + ", extendedResponse=" + TreeConnectAndX.hasExtendedResponse(flags));

        // Check if an extended format response is required, only return for filesystem shares
        if (TreeConnectAndX.hasExtendedResponse(flags) && servType != ShareType.ADMINPIPE) {

            // Build the extended tree connect response
            parser.setParameterCount(7);
            parser.setAndXCommand(0xFF); // no chained reply
            parser.setParameter(1, 0);
            parser.setParameter(2, 0);    // response flags

            // Maximal user access rights
            if (sharePerm == ISMBAuthenticator.ShareStatus.WRITEABLE)
                parser.setParameterLong(3, AccessMode.NTFileGenericAll);
            else
                parser.setParameterLong(3, AccessMode.NTFileGenericRead);

            // Guest maximal access rights
            parser.setParameterLong(5, 0);
        }
        else {

            // Build the standard tree connect response
            parser.setParameterCount(3);
            parser.setAndXCommand(0xFF); // no chained reply
            parser.setParameter(1, 0);
            parser.setParameter(2, 0);    // response flags
        }

        // Pack the service type
        int pos = parser.getByteOffset();
        pos = DataPacker.putString(ShareType.TypeAsService(shareDev.getType()), smbPkt.getBuffer(), pos, true);

        // Determine the filesystem type, for disk shares
        String devType = "";

        try {

            // Check if this is a disk shared device
            if (shareDev.getType() == ShareType.DISK) {

                // Check if the filesystem driver implements the NTFS streams interface, and streams are enabled
                if (shareDev.getInterface() instanceof NTFSStreamsInterface) {

                    // Check if NTFS streams are enabled
                    NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) shareDev.getInterface();
                    if (ntfsStreams.hasStreamsEnabled(m_sess, tree))
                        devType = "NTFS";
                }
                else {

                    // Get the filesystem type from the context
                    DiskDeviceContext diskCtx = (DiskDeviceContext) tree.getContext();
                    devType = diskCtx.getFilesystemType();
                }
            }
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Log the error
            if (Debug.EnableError && m_sess.hasDebug(SMBSrvSession.DBG_TREE))
                Debug.println("TreeConnectAndX error " + ex.getMessage());
        }

        // Pack the filesystem type
        pos = DataPacker.wordAlign(pos);
        pos = DataPacker.putString(devType, smbPkt.getBuffer(), pos, true, parser.isUnicode());
        parser.setByteCount(pos - parser.getByteOffset());

        // Send the response
        m_sess.sendResponseSMB(smbPkt);

        // Inform the driver that a connection has been opened
        if (tree.getInterface() != null)
            tree.getInterface().treeOpened(m_sess, tree);
    }

    /**
     * Close a file that has been opened on the server.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected void procCloseFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid file close request
        if (parser.checkPacketIsValid(3, 0) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid connection id
        TreeConnection conn = m_sess.findTreeConnection(smbPkt);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNoAccessRights, SMBStatus.ErrSrv);
            return;
        }

        // Get the file id from the request
        int fid = parser.getParameter(0);
        int ftime = parser.getParameter(1);
        int fdate = parser.getParameter(2);

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
            m_sess.debugPrintln("File close [" + parser.getTreeId() + "] fid=" + fid + ", fileId=" + netFile.getFileId());

        // Close the file
        boolean delayedClose = false;

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Close the file
            //
            // The disk interface may be null if the file is a named pipe file
            if (disk != null) {

                // DEBUG
                long startTime = 0L;

                if (netFile.hasDeleteOnClose() && Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_BENCHMARK))
                    startTime = System.currentTimeMillis();

                // Check if the file has an oplock
                if (netFile.hasOpLock())
                    OpLockHelper.releaseOpLock(m_sess, smbPkt, disk, conn, netFile);

                // Close the file
                disk.closeFile(m_sess, conn, netFile);

                // Release any byte range locks that are on the file
                if (netFile.hasLocks() && disk instanceof FileLockingInterface) {

                    //  Get the lock manager
                    FileLockingInterface flIface = (FileLockingInterface) disk;
                    LockManager lockMgr = flIface.getLockManager(m_sess, conn);

                    //  DEBUG
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_LOCK))
                        Debug.println("Releasing locks for closed file, file=" + netFile.getFullName() + ", locks=" + netFile.numberOfLocks());

                    //  Release all locks on the file owned by this session
                    lockMgr.releaseLocksForFile(m_sess, conn, netFile);
                }

                // Check if the file close has been delayed by the filesystem driver
                if (netFile.hasDelayedClose()) {
                    delayedClose = true;

                    // Reset the delayed close status
                    netFile.setDelayedClose(false);

                    // DEBUG
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
                        m_sess.debugPrintln("File close delayed [" + parser.getTreeId() + "] fid=" + fid + ", path=" + netFile.getFullName());
                }

                // DEBUG
                if (startTime != 0L && Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_BENCHMARK))
                    Debug.println("Benchmark: Delete on close " + netFile.getName() + " took " + (System.currentTimeMillis() - startTime) + "ms");
            }

            // Indicate that the file has been closed
            if (delayedClose == false)
                netFile.setClosed(true);

            // DEBUG
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_BENCHMARK)) {
                if (netFile.isDirectory() == false) {
                    if (netFile.wasCreated() && netFile.getWriteCount() > 0)
                        m_sess.debugPrintln("Benchmark: File=" + netFile.getFullName() + ", Size=" + MemorySize.asScaledString(netFile.getFileSize()) +
                                ", Write Time=" + (System.currentTimeMillis() - netFile.getCreationDate()) + "ms" +
                                ", ClosedAt=" + new Time(System.currentTimeMillis()));
                }
                else if (netFile.getCreationDate() != 0L)
                    m_sess.debugPrintln("Benchmark: Dir=" + netFile.getFullName() +
                            ", Write Time=" + (System.currentTimeMillis() - netFile.getCreationDate()) + "ms, CreatedAt=" + new Time(netFile.getCreationDate()));
                else
                    m_sess.debugPrintln("Benchmark: Dir=" + netFile.getFullName() + ", ClosedAt=" + new Time(System.currentTimeMillis()));
            }
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (AccessDeniedException ex) {

            // Not allowed to delete the file, when the delete on close flag has been set
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (Throwable t) {
        }

        // Remove the file from the connections list of open files
        if (delayedClose == false)
            conn.removeFile(fid, getSession());

        // Build the close file response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);

        // Check if there are any file/directory change notify requests active
        DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();

        if ( diskCtx != null && diskCtx.hasFileServerNotifications()) {
            if (netFile.getWriteCount() > 0)
                diskCtx.getChangeHandler().notifyFileSizeChanged(netFile.getFullName());

            if (netFile.hasDeleteOnClose())
                diskCtx.getChangeHandler().notifyFileChanged(NotifyAction.Removed, netFile.getFullName());
        }
    }

    /**
     * Process a transact2 request. The transact2 can contain many different sub-requests.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected void procTransact2(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that we received enough parameters for a transact2 request
        if (parser.checkPacketIsValid(14, 0) == false) {

            // Not enough parameters for a valid transact2 request
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid connection id
        TreeConnection conn = vc.findConnection(parser.getTreeId());

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNoAccessRights, SMBStatus.ErrSrv);
            return;
        }

        // Create a transact packet using the received SMB packet
        SMBSrvTransPacket tranPkt = new SMBSrvTransPacket(parser);

        // Create a transact buffer to hold the transaction setup, parameter and data blocks
        SrvTransactBuffer transBuf = null;
        int subCmd = tranPkt.getSubFunction();

        if (tranPkt.getTotalParameterCount() == tranPkt.getRxParameterBlockLength()
                && tranPkt.getTotalDataCount() == tranPkt.getRxDataBlockLength()) {

            // Create a transact buffer using the packet buffer, the entire request is contained in
            // a single packet
            transBuf = new SrvTransactBuffer(tranPkt);
        }
        else {

            // Create a transact buffer to hold the multiple transact request parameter/data blocks
            transBuf = new SrvTransactBuffer(tranPkt.getSetupCount(), tranPkt.getTotalParameterCount(), tranPkt.getTotalDataCount());
            transBuf.setType(parser.getCommand());
            transBuf.setFunction(subCmd);

            // Append the setup, parameter and data blocks to the transaction data
            byte[] buf = tranPkt.getBuffer();

            transBuf.appendSetup(buf, tranPkt.getSetupOffset(), tranPkt.getSetupCount() * 2);
            transBuf.appendParameter(buf, tranPkt.getRxParameterBlock(), tranPkt.getRxParameterBlockLength());
            transBuf.appendData(buf, tranPkt.getRxDataBlock(), tranPkt.getRxDataBlockLength());
        }

        // Set the return data limits for the transaction
        transBuf.setReturnLimits(tranPkt.getMaximumReturnSetupCount(), tranPkt.getMaximumReturnParameterCount(), tranPkt.getMaximumReturnDataCount());

        // Clear the transaction packet buffer, as it is owned by the original packet
        tranPkt.setBuffer(null);

        // Check for a multi-packet transaction, for a multi-packet transaction we just acknowledge
        // the receive with an empty response SMB
        if (transBuf.isMultiPacket()) {

            // Save the partial transaction data
            vc.setTransaction(transBuf);

            // Send an intermediate acknowedgement response
            m_sess.sendSuccessResponseSMB(smbPkt);
            return;
        }

        // Check if the transaction is on the IPC$ named pipe, the request requires special processing
        if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {
            IPCHandler.procTransaction(vc, transBuf, m_sess, smbPkt);
            return;
        }

        // DEBUG
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
            m_sess.debugPrintln("Transaction [" + parser.getTreeId() + "] tbuf=" + transBuf);

        // Process the transaction buffer
        processTransactionBuffer(transBuf, smbPkt, parser);
    }

    /**
     * Process a transact2 secondary request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected void procTransact2Secondary(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that we received enough parameters for a transact2 request
        if (parser.checkPacketIsValid(8, 0) == false) {

            // Not enough parameters for a valid transact2 request
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid
        // connection id.
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNoAccessRights, SMBStatus.ErrSrv);
            return;
        }

        // Check if there is an active transaction, and it is an NT transaction
        if (vc.hasTransaction() == false
                || (vc.getTransaction().isType() == PacketTypeV1.Transaction && parser.getCommand() != PacketTypeV1.TransactionSecond)
                || (vc.getTransaction().isType() == PacketTypeV1.Transaction2 && parser.getCommand() != PacketTypeV1.Transaction2Second)) {

            // No transaction to continue, or packet does not match the existing transaction, return
            // an error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Create an NT transaction using the received packet
        SMBSrvTransPacket tpkt = new SMBSrvTransPacket(parser.getBuffer());
        byte[] buf = tpkt.getBuffer();
        SrvTransactBuffer transBuf = vc.getTransaction();

        // Append the parameter data to the transaction buffer, if any
        int plen = tpkt.getSecondaryParameterBlockCount();
        if (plen > 0) {

            // Append the data to the parameter buffer
            DataBuffer paramBuf = transBuf.getParameterBuffer();
            paramBuf.appendData(buf, tpkt.getSecondaryParameterBlockOffset(), plen);
        }

        // Append the data block to the transaction buffer, if any
        int dlen = tpkt.getSecondaryDataBlockCount();
        if (dlen > 0) {

            // Append the data to the data buffer
            DataBuffer dataBuf = transBuf.getDataBuffer();
            dataBuf.appendData(buf, tpkt.getSecondaryDataBlockOffset(), dlen);
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
            m_sess.debugPrintln("Transaction Secondary [" + treeId + "] paramLen=" + plen + ", dataLen=" + dlen);

        // Check if the transaction has been received or there are more sections to be received
        int totParam = tpkt.getTotalParameterCount();
        int totData = tpkt.getTotalDataCount();

        int paramDisp = tpkt.getParameterBlockDisplacement();
        int dataDisp = tpkt.getDataBlockDisplacement();

        if ((paramDisp + plen) == totParam && (dataDisp + dlen) == totData) {

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
                m_sess.debugPrintln("Transaction complete, processing ...");

            // Clear the in progress transaction
            vc.setTransaction(null);

            // Check if the transaction is on the IPC$ named pipe, the request requires special processing
            if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {
                IPCHandler.procTransaction(vc, transBuf, m_sess, smbPkt);
                return;
            }

            // DEBUG
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
                m_sess.debugPrintln("Transaction second [" + treeId + "] tbuf=" + transBuf);

            // Process the transaction
            processTransactionBuffer(transBuf, smbPkt, parser);
        }
        else {

            // There are more transaction parameter/data sections to be received, return an
            // intermediate response
            m_sess.sendSuccessResponseSMB(smbPkt);
        }
    }

    /**
     * Process a transaction buffer
     *
     * @param tbuf   TransactBuffer
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws IOException     If a network error occurs
     * @throws SMBSrvException If an SMB error occurs
     */
    private final void processTransactionBuffer(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the transact2 sub-command code and process the request
        switch (tbuf.getFunction()) {

            // Start a file search
            case PacketTypeV1.Trans2FindFirst:
                procTrans2FindFirst(tbuf, smbPkt, parser);
                break;

            // Continue a file search
            case PacketTypeV1.Trans2FindNext:
                procTrans2FindNext(tbuf, smbPkt, parser);
                break;

            // Query file system information
            case PacketTypeV1.Trans2QueryFileSys:
                procTrans2QueryFileSys(tbuf, smbPkt, parser);
                break;

            // Query path
            case PacketTypeV1.Trans2QueryPath:
                procTrans2QueryPath(tbuf, smbPkt, parser);
                break;

            // Query file information via handle
            case PacketTypeV1.Trans2QueryFile:
                procTrans2QueryFile(tbuf, smbPkt, parser);
                break;

            // Set file information via handle
            case PacketTypeV1.Trans2SetFile:
                procTrans2SetFile(tbuf, smbPkt, parser);
                break;

            // Set file information via path
            case PacketTypeV1.Trans2SetPath:
                procTrans2SetPath(tbuf, smbPkt, parser);
                break;

            // Unknown transact2 command
            default:

                // Return an unrecognized command error
                if (Debug.EnableError)
                    m_sess.debugPrintln("NT Error Transact2 Command = 0x" + Integer.toHexString(tbuf.getFunction()));
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
                break;
        }
    }

    /**
     * Close a search started via the transact2 find first/next command.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procFindClose(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid find close request
        if (parser.checkPacketIsValid(1, 0) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNoAccessRights, SMBStatus.ErrSrv);
            return;
        }

        // Get the search id
        int searchId = parser.getParameter(0);

        // Get the search context
        SearchContext ctx = vc.getSearchContext(searchId);

        if (ctx == null) {

            // Invalid search handle
            m_sess.sendSuccessResponseSMB(smbPkt);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
            m_sess.debugPrintln("Close trans search [" + searchId + "]");

        // Deallocate the search slot, close the search.
        vc.deallocateSearchSlot(searchId);

        // Return a success status SMB
        m_sess.sendSuccessResponseSMB(smbPkt);
    }

    /**
     * Process the file lock/unlock request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procLockingAndX(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid locking andX request
        if (parser.checkPacketIsValid(8, 0) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNoAccessRights, SMBStatus.ErrSrv);
            return;
        }

        // Extract the file lock/unlock parameters
        int fid = parser.getParameter(2);
        int lockType = parser.getParameter(3);
        long lockTmo = parser.getParameterLong(4);
        int unlockCnt = parser.getParameter(6);
        int lockCnt = parser.getParameter(7);

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.Win32InvalidHandle, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_LOCK))
            m_sess.debugPrintln("File Lock [" + netFile.getFileId() + "] : type=0x" + Integer.toHexString(lockType) + ", tmo="
                    + lockTmo + ", locks=" + lockCnt + ", unlocks=" + unlockCnt);

        DiskInterface disk = null;
        try {

            // Get the disk interface for the share
            disk = (DiskInterface) conn.getSharedDevice().getInterface();
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Check for an oplock break
        if (LockingAndX.hasOplockBreak(lockType)) {

            // Debug
            if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                Debug.println("Oplock break, flags=0x" + Integer.toHexString(lockType) + " file=" + netFile);

            // Access the oplock manager via the filesystem
            if (disk instanceof OpLockInterface) {

                // Get the oplock manager
                OpLockInterface oplockIface = (OpLockInterface) disk;
                OpLockManager oplockMgr = oplockIface.getOpLockManager(m_sess, conn);

                if (oplockMgr == null) {

                    // DEBUG
                    if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                        Debug.print("  OpLock manager is null, tree=" + conn);

                    // Return a not supported error
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
                    return;
                }

                // Get the oplock details for the file
                OpLockDetails oplock = oplockMgr.getOpLockDetails(netFile.getFullName());
                if (oplock == null) {

                    // Return a not locked error
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTRangeNotLocked, SMBStatus.DOSNotLocked, SMBStatus.ErrDos);
                    return;
                }

                // Check if the oplock should be released or converted to a shared Level II oplock
                if (LockingAndX.hasLevelIIOplock(lockType) == false) {

                    // Release the oplock
                    oplockMgr.releaseOpLock(oplock.getPath());

                    // DEBUG
                    if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                        Debug.println("  Oplock released, oplock=" + oplock);
                }
                else {

                    // Change the oplock type to a LevelII
                    oplockMgr.changeOpLockType(oplock, OpLockType.LEVEL_II);

                    // DEBUG
                    if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_OPLOCK))
                        Debug.println("  Oplock converted to LevelII, oplock=" + oplock);
                }
            }
            else {

                // Return a not supported error
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
                return;
            }
        }

        // Check for byte range locks/unlocks
        if (unlockCnt > 0 || lockCnt > 0) {

            // Check if the virtual filesystem supports file locking
            if (disk instanceof FileLockingInterface) {

                // Get the lock manager
                FileLockingInterface lockInterface = (FileLockingInterface) disk;
                LockManager lockMgr = lockInterface.getLockManager(m_sess, conn);

                // Unpack the lock/unlock structures
                parser.resetBytePointer();
                boolean largeFileLock = LockingAndX.hasLargeFiles(lockType);

                int lockIdx = 0;

                while (lockIdx < (unlockCnt + lockCnt)) {

                    // Get the unlock/lock structure
                    int pid = parser.unpackWord();
                    long offset = -1;
                    long length = -1;

                    if (largeFileLock == false) {

                        // Get the lock offset and length, short format
                        offset = parser.unpackInt();
                        length = parser.unpackInt();
                    }
                    else {

                        // Get the lock offset and length, large format
                        parser.skipBytes(2);

                        offset = ((long) parser.unpackInt()) << 32;
                        offset += (long) parser.unpackInt();

                        length = ((long) parser.unpackInt()) << 32;
                        length += (long) parser.unpackInt();
                    }

                    // Create the lock/unlock details
                    FileLock fLock = lockMgr.createLockObject(m_sess, conn, netFile, new LockParams(offset, length, pid));
                    boolean isLock = lockIdx++ < lockCnt;

                    // Debug
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_LOCK))
                        m_sess.debugPrintln("  " + (isLock ? "Lock" : "UnLock") + " lock=" + fLock);

                    // Perform the lock/unlock request
                    try {

                        // Check if the request is an unlock
                        if (isLock == false) {

                            // Unlock the file
                            lockMgr.unlockFile(m_sess, conn, netFile, fLock);
                        }
                        else {

                            // Lock the file
                            lockMgr.lockFile(m_sess, conn, netFile, fLock);
                        }
                    }
                    catch (NotLockedException ex) {

                        // Return an error status
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTRangeNotLocked, SMBStatus.DOSNotLocked, SMBStatus.ErrDos);
                        return;
                    }
                    catch (LockConflictException ex) {

                        // Return an error status
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTLockNotGranted, SMBStatus.DOSLockConflict, SMBStatus.ErrDos);
                        return;
                    }
                    catch (IOException ex) {

                        // Return an error status
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVInternalServerError, SMBStatus.ErrSrv);
                        return;
                    }
                }
            }
            else {

                // Filesystem does not support byte range locking
                //
                // Return a 'not locked' status if there are unlocks in the request else return a
                // success status
                if (unlockCnt > 0) {

                    // Return an error status
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTRangeNotLocked, SMBStatus.DOSNotLocked, SMBStatus.ErrDos);
                    return;
                }
            }

            // Return a success response
            parser.setParameterCount(2);
            parser.setAndXCommand(0xFF);
            parser.setParameter(1, 0);
            parser.setByteCount(0);

            // Send the lock request response
            m_sess.sendResponseSMB(smbPkt);
        }
    }

    /**
     * Process the logoff request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procLogoffAndX(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid logoff andX request
        if (parser.checkPacketIsValid(2, 0) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        int uid = parser.getUserId();
        VirtualCircuit vc = m_sess.findVirtualCircuit(uid);

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // DEBUG
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
            Debug.println("[SMB] LogoffAndX vc=" + vc);

        // Mark the virtual circuit as logged off
        vc.setLoggedOn(false);

        // Check if there are no tree connections on this virtual circuit
        if (vc.getConnectionCount() == 0) {

            // Remove the virtual circuit
            m_sess.removeVirtualCircuit(vc.getId());

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
                m_sess.debugPrintln("  Removed virtual circuit " + vc);
        }

        // Return a success status SMB
        m_sess.sendSuccessResponseSMB(smbPkt);

        // If there are no active virtual circuits then close the session/socket
        if (m_sess.numberOfVirtualCircuits() == 0) {

            // DEBUG
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
                Debug.println("  Closing session, no more virtual circuits");

            // Close the session/socket
            m_sess.hangupSession("Client logoff");
        }
    }

    /**
     * Process the file open request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procOpenAndX(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid open andX request
        if (parser.checkPacketIsValid(15, 1) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // If the connection is to the IPC$ remote admin named pipe pass the request to the IPC
        // handler. If the device is not a disk type device then return an error.
        if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {

            // Use the IPC$ handler to process the request
            IPCHandler.processIPCRequest(m_sess, smbPkt);
            return;
        }
        else if (conn.getSharedDevice().getType() != ShareType.DISK) {

            // Return an access denied error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Extract the open file parameters
        int flags = parser.getParameter(2);
        int access = parser.getParameter(3);
        int srchAttr = parser.getParameter(4);
        int fileAttr = parser.getParameter(5);
        int crTime = parser.getParameter(6);
        int crDate = parser.getParameter(7);
        int openFunc = parser.getParameter(8);
        int allocSiz = parser.getParameterLong(9);

        // Extract the filename string
        String fileName = parser.unpackString(parser.isUnicode());
        if (fileName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Create the file open parameters
        long crDateTime = 0L;
        if (crTime > 0 && crDate > 0)
            crDateTime = new SMBDate(crDate, crTime).getTime();

        FileOpenParams params = new FileOpenParams(fileName, openFunc, access, srchAttr, fileAttr, allocSiz, crDateTime, parser.getProcessIdFull());

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
            m_sess.debugPrintln("File Open AndX [" + treeId + "] params=" + params);

        // Check if the file name is valid
        if (FileName.isValidPath(params.getPath()) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameInvalid, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Access the disk interface and open the requested file
        int fid;
        NetworkFile netFile = null;
        int respAction = 0;

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Check if the requested file already exists
            FileStatus fileSts = disk.fileExists(m_sess, conn, fileName);

            if (fileSts == FileStatus.NotExist) {

                // Check if the file should be created if it does not exist
                if (FileAction.createNotExists(openFunc)) {

                    // Check if the session has write access to the filesystem
                    if (conn.hasWriteAccess() == false) {

                        // User does not have the required access rights
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
                        return;
                    }

                    // Create a new file
                    netFile = disk.createFile(m_sess, conn, params);

                    // Indicate that the file did not exist and was created
                    respAction = FileAction.FileCreated;
                }
                else {

                    // Check if the path is a directory
                    if (fileSts == FileStatus.DirectoryExists) {

                        // Return an access denied error
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
                    }
                    else {

                        // Return a file not found error
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
                    }
                    return;
                }
            }
            else {

                // Open the requested file
                netFile = disk.openFile(m_sess, conn, params);

                // Set the file action response
                if (FileAction.truncateExistingFile(openFunc)) {

                    // Truncate the existing file
                    disk.truncateFile(m_sess, conn, netFile, 0L);

                    // Set the response
                    respAction = FileAction.FileTruncated;
                }
                else
                    respAction = FileAction.FileExisted;
            }

            // Add the file to the list of open files for this tree connection
            fid = conn.addFile(netFile, getSession());

        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (TooManyFilesException ex) {

            // Too many files are open on this connection, cannot open any more files.
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSTooManyOpenFiles, SMBStatus.ErrDos);
            return;
        }
        catch (AccessDeniedException ex) {

            // Return an access denied error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (FileSharingException ex) {

            // Return a sharing violation error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTSharingViolation, SMBStatus.DOSFileSharingConflict, SMBStatus.ErrDos);
            return;
        }
        catch (FileOfflineException ex) {

            // File data is unavailable
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTFileOffline, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
            return;
        }
        catch (DiskOfflineException ex) {

            // Filesystem is offline
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
            return;
        }
        catch (IOException ex) {

            // Failed to open the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }

        // Build the open file response
        parser.setParameterCount(15);

        parser.setAndXCommand(0xFF);
        parser.setParameter(1, 0); // AndX offset

        parser.setParameter(2, fid);
        parser.setParameter(3, netFile.getFileAttributes()); // file attributes

        SMBDate modDate = null;

        if (netFile.hasModifyDate())
            modDate = new SMBDate(netFile.getModifyDate());

        parser.setParameter(4, modDate != null ? modDate.asSMBTime() : 0); // last write time
        parser.setParameter(5, modDate != null ? modDate.asSMBDate() : 0); // last write date
        parser.setParameterLong(6, netFile.getFileSizeInt()); // file size
        parser.setParameter(8, netFile.getGrantedAccess().intValue());
        parser.setParameter(9, OpenAndX.FileTypeDisk);
        parser.setParameter(10, 0); // named pipe state
        parser.setParameter(11, respAction);
        parser.setParameter(12, 0); // server FID (long)
        parser.setParameter(13, 0);
        parser.setParameter(14, 0);

        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Process the file read request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procReadAndX(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid read andX request
        if (parser.checkPacketIsValid(10, 0) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // If the connection is to the IPC$ remote admin named pipe pass the request to the IPC handler.
        if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {

            // Use the IPC$ handler to process the request
            IPCHandler.processIPCRequest(m_sess, smbPkt);
            return;
        }

        // Extract the read file parameters
        int fid = parser.getParameter(2);
        long offset = parser.getParameterLong(3); // bottom 32bits of read offset
        offset &= 0xFFFFFFFFL;
        int maxCount = parser.getParameter(5);

        // Check for the NT format request that has the top 32bits of the file offset
        if (parser.getParameterCount() == 12) {
            long topOff = parser.getParameterLong(10);
            offset += topOff << 32;
        }

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
            m_sess.debugPrintln("File Read AndX [" + netFile.getFileId() + "] : Size=" + maxCount + " ,Pos=" + offset);

        // Read data from the file
        SMBSrvPacket respPkt = smbPkt;
        byte[] buf = respPkt.getBuffer();
        int dataPos = 0;
        int rdlen = 0;

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Set the returned parameter count so that the byte offset can be calculated
            parser.setParameterCount(12);
            dataPos = parser.getByteOffset();
            dataPos = DataPacker.wordAlign(dataPos); // align the data buffer

            // Check if the requested data will fit into the current packet
            if (maxCount > (buf.length - dataPos)) {

                // Allocate a larger packet for the response
                respPkt = m_sess.getPacketPool().allocatePacket(maxCount + dataPos, smbPkt);

                // Set the response parser
                respPkt.setParser( SMBSrvPacket.Version.V1);
                parser = (SMBV1Parser) respPkt.getParser();

                // Switch to the response buffer
                buf = respPkt.getBuffer();
                parser.setParameterCount(12);
            }

            // Check if the requested data length will fit into the buffer
            int dataLen = buf.length - dataPos;
            if (dataLen < maxCount)
                maxCount = dataLen;

            // Read from the file
            // Synchronize reads using the network file
            synchronized (netFile) {
                rdlen = disk.readFile(m_sess, conn, netFile, buf, dataPos, maxCount, offset);
            }
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (FileOfflineException ex) {

            // File data is unavailable
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTFileOffline, SMBStatus.HRDReadFault, SMBStatus.ErrHrd);
            return;
        }
        catch (LockConflictException ex) {

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_LOCK))
                m_sess.debugPrintln("Read Lock Error [" + netFile.getFileId() + "] : Size=" + maxCount + " ,Pos=" + offset);

            // File is locked
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTLockConflict, SMBStatus.DOSLockConflict, SMBStatus.ErrDos);
            return;
        }
        catch (AccessDeniedException ex) {

            // User does not have the required access rights or file is not accessible
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (DiskOfflineException ex) {

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
                m_sess.debugPrintln("Filesystem Offline Error [" + netFile.getFileId() + "] Read File");

            // Filesystem is offline
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
            return;
        }
        catch (IOException ex) {

            // Debug
            if (Debug.EnableError && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO)) {
                m_sess.debugPrintln("File Read Error [" + netFile.getFileId() + "] : " + ex.toString());
                m_sess.debugPrintln(ex);

                // Dump the network file details
                m_sess.debugPrintln("  NetworkFile name=" + netFile.getName() + "/" + netFile.getFullName());
                m_sess.debugPrintln("  attr=0x" + Integer.toHexString(netFile.getFileAttributes()) + ", size=" + netFile.getFileSize());
                m_sess.debugPrintln("  fid=" + netFile.getFileId() + ", cdate=" + netFile.getCreationDate() + ", mdate=" + netFile.getModifyDate());
                m_sess.debugPrintln("Offset = " + offset + " (0x" + Long.toHexString(offset) + ")");
            }

            // Failed to read the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTFileOffline, SMBStatus.HRDReadFault, SMBStatus.ErrHrd);
            return;
        }

        // Return the data block
        parser.setAndXCommand(0xFF); // no chained command
        parser.setParameter(1, 0);
        parser.setParameter(2, 0); // bytes remaining, for pipes only
        parser.setParameter(3, 0); // data compaction mode
        parser.setParameter(4, 0); // reserved
        parser.setParameter(5, rdlen); // data length
        parser.setParameter(6, dataPos - RFCNetBIOSProtocol.HEADER_LEN); // offset to data

        // Clear the reserved parameters
        for (int i = 7; i < 12; i++)
            parser.setParameter(i, 0);

        // Set the byte count
        parser.setByteCount((dataPos + rdlen) - parser.getByteOffset());

        // Check if there is a chained command, or commands
        if (parser.hasAndXCommand()) {

            // Process any chained commands, AndX
            int pos = procAndXCommands(smbPkt, parser, netFile);

            // Send the read andX response
            m_sess.sendResponseSMB(smbPkt.getAssociatedPacket(), pos);
        }
        else {

            // Send the normal read andX response
            m_sess.sendResponseSMB(respPkt);
        }
    }

    /**
     * Rename a file.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected void procRenameFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid rename file request
        if (parser.checkPacketIsValid(1, 4) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid
        // connection id.
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasWriteAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNetworkAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the Unicode flag
        boolean isUni = parser.isUnicode();

        // Read the data block
        parser.resetBytePointer();

        // Extract the old file name
        if (parser.unpackByte() != DataType.ASCII) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        String oldName = parser.unpackString(isUni);
        if (oldName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Extract the new file name
        if (parser.unpackByte() != DataType.ASCII) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        String newName = parser.unpackString(isUni);
        if (newName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
            m_sess.debugPrintln("File Rename [" + treeId + "] old name=" + oldName + ", new name=" + newName);

        // Check if the from/to paths are valid
        if (FileName.isValidPath(oldName) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameInvalid, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        if (FileName.isValidPath(newName) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameInvalid, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Access the disk interface and rename the requested file
        int fid;
        NetworkFile netFile = null;

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Rename the requested file
            disk.renameFile(m_sess, conn, oldName, newName);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (FileNotFoundException ex) {

            // Source file/directory does not exist
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }
        catch (FileExistsException ex) {

            // Destination file/directory already exists
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameCollision, SMBStatus.DOSFileAlreadyExists, SMBStatus.ErrDos);
            return;
        }
        catch (AccessDeniedException ex) {

            // Target file/directory exists
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (PermissionDeniedException ex) {

            // Not allowed to rename the file/directory
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNetworkAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (FileSharingException ex) {

            // Return a sharing violation error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTSharingViolation, SMBStatus.DOSFileSharingConflict, SMBStatus.ErrDos);
            return;
        }
        catch (DiskOfflineException ex) {

            // Filesystem is offline
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
        }
        catch (IOException ex) {

            // I/O exception
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Build the rename file response
        parser.setParameterCount(0);
        parser.setByteCount(0);
        parser.setSuccessStatus();

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);

        // Check if there are any file/directory change notify requests active
        DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();
        if (diskCtx.hasFileServerNotifications())
            diskCtx.getChangeHandler().notifyRename(oldName, newName);
    }

    /**
     * Delete a file.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected void procDeleteFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid file delete request
        if (parser.checkPacketIsValid(1, 2) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid
        // connection id.
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasWriteAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the Unicode flag
        boolean isUni = parser.isUnicode();

        // Read the data block
        parser.resetBytePointer();

        // Extract the old file name
        if (parser.unpackByte() != DataType.ASCII) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        String fileName = parser.unpackString(isUni);
        if (fileName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
            m_sess.debugPrintln("File Delete [" + treeId + "] name=" + fileName);

        // Access the disk interface and delete the file(s)
        int fid;
        NetworkFile netFile = null;
        long startTime = 0L;

        try {

            // DEBUG
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_BENCHMARK))
                startTime = System.currentTimeMillis();

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Delete file(s)
            disk.deleteFile(m_sess, conn, fileName);

            // DEBUG
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_BENCHMARK))
                Debug.println("Benchmark: Delete file " + fileName + " took " + (System.currentTimeMillis() - startTime) + "ms");
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (AccessDeniedException ex) {

            // Not allowed to delete the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (DiskOfflineException ex) {

            // Filesystem is offline
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
            return;
        }
        catch (IOException ex) {

            // Failed to open the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }

        // Build the delete file response
        parser.setParameterCount(0);
        parser.setByteCount(0);
        parser.setSuccessStatus();

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);

        // Check if there are any file/directory change notify requests active
        DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();
        if (diskCtx.hasFileServerNotifications())
            diskCtx.getChangeHandler().notifyFileChanged(NotifyAction.Removed, fileName);
    }

    /**
     * Delete a directory.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected void procDeleteDirectory(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid delete directory request
        if (parser.checkPacketIsValid(0, 2) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid
        // connection id.
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasWriteAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the Unicode flag
        boolean isUni = parser.isUnicode();

        // Read the data block
        parser.resetBytePointer();

        // Extract the old file name
        if (parser.unpackByte() != DataType.ASCII) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        String dirName = parser.unpackString(isUni);
        if (dirName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
            m_sess.debugPrintln("Directory Delete [" + treeId + "] name=" + dirName);

        // Access the disk interface and delete the directory
        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Delete the directory
            disk.deleteDirectory(m_sess, conn, dirName);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (AccessDeniedException ex) {

            // Not allowed to delete the directory
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (DirectoryNotEmptyException ex) {

            // Directory not empty
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTDirectoryNotEmpty, SMBStatus.DOSAccessDenied, SMBStatus.NTErr);
            return;
        }
        catch (DiskOfflineException ex) {

            // Filesystem is offline
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
            return;
        }
        catch (IOException ex) {

            // Failed to delete the directory
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSDirectoryInvalid, SMBStatus.ErrDos);
            return;
        }

        // Build the delete directory response
        parser.setParameterCount(0);
        parser.setByteCount(0);
        parser.setSuccessStatus();

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);

        // Check if there are any file/directory change notify requests active
        DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();
        if (diskCtx.hasFileServerNotifications())
            diskCtx.getChangeHandler().notifyDirectoryChanged(NotifyAction.Removed, dirName);
    }

    /**
     * Process a transact2 file search request.
     *
     * @param tbuf   Transaction request details
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procTrans2FindFirst(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNoAccessRights, SMBStatus.ErrSrv);
            return;
        }

        // Get the search parameters
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int srchAttr = paramBuf.getShort();
        int maxFiles = paramBuf.getShort();
        int srchFlag = paramBuf.getShort();
        int infoLevl = paramBuf.getShort();
        paramBuf.skipBytes(4);

        String srchPath = paramBuf.getString(tbuf.isUnicode());

        // Check if the search contains Unicode wildcards
        if (tbuf.isUnicode() && WildCard.containsUnicodeWildcard(srchPath)) {

            // Translate the Unicode wildcards to standard DOS wildcards
            srchPath = WildCard.convertUnicodeWildcardToDOS(srchPath);

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                m_sess.debugPrintln("Converted Unicode wildcards to:" + srchPath);
        }

        // Check if the search path is valid
        if (FileName.isValidSearchPath(srchPath) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameInvalid, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Check if the search path is valid
        if (srchPath == null || srchPath.length() == 0) {

            // Invalid search request
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        else if (srchPath.endsWith(FileName.DOS_SEPERATOR_STR)) {

            // Make the search a wildcard search
            srchPath = srchPath + "*.*";
        }
        else if (srchPath.startsWith(FileName.DOS_SEPERATOR_STR) == false) {

            // Prefix the search path to make it a relative path
            srchPath = FileName.DOS_SEPERATOR_STR + srchPath;

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                m_sess.debugPrintln("Search path missing leading slash, converted to relative path");
        }

        // Check for the Macintosh information level, if the Macintosh extensions are not enabled return an error
        if (infoLevl == FindInfoPacker.InfoMacHfsInfo && getSession().hasMacintoshExtensions() == false) {

            // Return an error status, Macintosh extensions are not enabled
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
            return;
        }

        // Access the shared device disk interface
        SearchContext ctx = null;
        DiskInterface disk = null;
        int searchId = -1;
        boolean wildcardSearch = false;

        try {

            // Access the disk interface
            disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Allocate a search slot for the new search
            searchId = vc.allocateSearchSlot();

            // Check if this is a wildcard search or single file search
            if (WildCard.containsWildcards(srchPath))
                wildcardSearch = true;

            // Start a new search
            ctx = disk.startSearch(m_sess, conn, srchPath, srchAttr);

            if (ctx != null) {

                // Store details of the search in the context
                ctx.setTreeId(treeId);
                ctx.setMaximumFiles(maxFiles);
            }
            else {

                // Deallocate the search
                if (searchId != -1)
                    vc.deallocateSearchSlot(searchId);

                // Failed to start the search, return a no more files error
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNoSuchFile, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
                return;
            }

            // Save the search context
            vc.setSearchContext(searchId, ctx);

            // Create the reply transact buffer
            SrvTransactBuffer replyBuf = new SrvTransactBuffer(tbuf);
            DataBuffer dataBuf = replyBuf.getDataBuffer();

            // Determine the maximum return data length
            int maxLen = replyBuf.getReturnDataLimit();

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                m_sess.debugPrintln("Start trans search [" + searchId + "] - " + srchPath + ", attr=0x"
                        + Integer.toHexString(srchAttr) + ", maxFiles=" + maxFiles + ", maxLen=" + maxLen + ", infoLevel="
                        + infoLevl + ", flags=0x" + Integer.toHexString(srchFlag) + ",dotFiles=" + ctx.hasDotFiles());

            // Loop until we have filled the return buffer or there are no more files to return
            int fileCnt = 0;
            int packLen = 0;
            int lastNameOff = 0;

            // Flag to indicate if resume ids should be returned
            boolean resumeIds = false;
            if (infoLevl == FindInfoPacker.InfoStandard && (srchFlag & FindFirstNext.ReturnResumeKey) != 0) {

                // Windows servers only seem to return resume keys for the standard information level
                resumeIds = true;
            }

            // If this is a wildcard search then add the '.' and '..' entries
            if (wildcardSearch == true && WildCard.isWildcardAll(srchPath) && ReturnDotFiles == true) {

                // Pack the '.' file information
                if (resumeIds == true) {
                    dataBuf.putInt(-1);
                    maxLen -= 4;
                }

                lastNameOff = dataBuf.getPosition();

                // Check if the search has the '.' file entry details
                FileInfo dotInfo = new FileInfo(".", 0, FileAttribute.Directory);
                dotInfo.setFileId(dotInfo.getFileName().hashCode());

                if (ctx.hasDotFiles())
                    ctx.getDotInfo(dotInfo);

                packLen = FindInfoPacker.packInfo(dotInfo, dataBuf, infoLevl, tbuf.isUnicode());

                // Update the file count for this packet, update the remaining buffer length
                fileCnt++;
                maxLen -= packLen;

                // Pack the '..' file information
                if (resumeIds == true) {
                    dataBuf.putInt(-2);
                    maxLen -= 4;
                }

                lastNameOff = dataBuf.getPosition();

                // Check if the search has the '..' file entry details
                if (ctx.hasDotFiles())
                    ctx.getDotDotInfo(dotInfo);
                else {

                    // Set dummy details for the '..' file entry
                    dotInfo.setFileName("..");
                    dotInfo.setFileId(dotInfo.getFileName().hashCode());
                    dotInfo.setCreationDateTime(DotFileDateTime);
                    dotInfo.setModifyDateTime(DotFileDateTime);
                    dotInfo.setAccessDateTime(DotFileDateTime);
                }

                packLen = FindInfoPacker.packInfo(dotInfo, dataBuf, infoLevl, tbuf.isUnicode());

                // Update the file count for this packet, update the remaining buffer length
                fileCnt++;
                maxLen -= packLen;
            }

            boolean pktDone = false;
            boolean searchDone = false;

            FileInfo info = new FileInfo();

            while (pktDone == false && fileCnt < maxFiles) {

                // Get file information from the search
                if (ctx.nextFileInfo(info) == false) {

                    // No more files
                    pktDone = true;
                    searchDone = true;
                }

                // Check if the file information will fit into the return buffer
                else if (FindInfoPacker.calcInfoSize(info, infoLevl, false, true) <= maxLen) {

                    // Pack the resume id, if required
                    if (resumeIds == true) {
                        dataBuf.putInt(ctx.getResumeId());
                        maxLen -= 4;
                    }

                    // Save the offset to the last file information structure
                    lastNameOff = dataBuf.getPosition();

                    // Pack the file information
                    packLen = FindInfoPacker.packInfo(info, dataBuf, infoLevl, tbuf.isUnicode());

                    // Update the file count for this packet
                    fileCnt++;

                    // Recalculate the remaining buffer space
                    maxLen -= packLen;
                }
                else {

                    // Set the search restart point
                    ctx.restartAt(info);

                    // No more buffer space
                    pktDone = true;

                    // Debug
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                        m_sess.debugPrintln("Find first response full, restart at " + info.getFileName());
                }
            }

            // Check for a single file search and the file was not found, in this case return an error status
            if (fileCnt == 0)
                throw new FileNotFoundException(srchPath);

            // Check for a search where the maximum files is set to one, close the search immediately.
            if (maxFiles == 1 && fileCnt == 1)
                searchDone = true;

            // Clear the next structure offset, if applicable
            FindInfoPacker.clearNextOffset(dataBuf, infoLevl, lastNameOff);

            // Pack the parameter block
            paramBuf = replyBuf.getParameterBuffer();

            paramBuf.putShort(searchId);
            paramBuf.putShort(fileCnt);
            paramBuf.putShort(ctx.hasMoreFiles() ? 0 : 1);
            paramBuf.putShort(0);
            paramBuf.putShort(lastNameOff);

            // Send the transaction response
            SMBSrvTransPacket tpkt = new SMBSrvTransPacket(smbPkt);
            tpkt.doTransactionResponse(m_sess, replyBuf, smbPkt);

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                m_sess.debugPrintln("Search [" + searchId + "] Returned " + fileCnt + " files, dataLen=" + dataBuf.getLength()
                        + ", moreFiles=" + ctx.hasMoreFiles());

            // Check if the search is complete
            if (searchDone == true || ctx.hasMoreFiles() == false) {

                // Debug
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                    m_sess.debugPrintln("End start search [" + searchId + "] (Search complete)");

                // Release the search context
                vc.deallocateSearchSlot(searchId);
            }
            else if ((srchFlag & FindFirstNext.CloseSearch) != 0) {

                // Debug
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                    m_sess.debugPrintln("End start search [" + searchId + "] (Close)");

                // Release the search context
                vc.deallocateSearchSlot(searchId);
            }
        }
        catch ( TooManySearchesException ex) {

            // Failed to allocate a slot for the new search
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNoResourcesAvailable, SMBStatus.ErrSrv);
        }
        catch (FileNotFoundException ex) {

            // Deallocate the search
            if (searchId != -1)
                vc.deallocateSearchSlot(searchId);

            // Search path does not exist
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNoSuchFile, SMBStatus.DOSNoMoreFiles, SMBStatus.ErrDos);
        }
        catch (PathNotFoundException ex) {

            // Deallocate the search
            if (searchId != -1)
                vc.deallocateSearchSlot(searchId);

            // Requested path does not exist
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Deallocate the search
            if (searchId != -1)
                vc.deallocateSearchSlot(searchId);

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
        }
        catch (UnsupportedInfoLevelException ex) {

            // Deallocate the search
            if (searchId != -1)
                vc.deallocateSearchSlot(searchId);

            // Requested information level is not supported
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidLevel, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
        }
        catch (DiskOfflineException ex) {

            // Deallocate the search
            if (searchId != -1)
                vc.deallocateSearchSlot(searchId);

            // Filesystem is offline
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
        }
    }

    /**
     * Process a transact2 file search continue request.
     *
     * @param tbuf   Transaction request details
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procTrans2FindNext(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the search parameters
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int searchId = paramBuf.getShort();
        int maxFiles = paramBuf.getShort();
        int infoLevl = paramBuf.getShort();
        int reskey = paramBuf.getInt();
        int srchFlag = paramBuf.getShort();

        String resumeName = paramBuf.getString(tbuf.isUnicode());

        // Access the shared device disk interface
        SearchContext ctx = null;
        DiskInterface disk = null;

        try {

            // Access the disk interface
            disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Retrieve the search context
            ctx = vc.getSearchContext(searchId);
            if (ctx == null) {

                // DEBUG
                if (Debug.EnableError)
                    m_sess.debugPrintln("Search context null - [" + searchId + "]");

                // Invalid search handle
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSNoMoreFiles, SMBStatus.ErrDos);
                return;
            }

            // Create the reply transaction buffer
            SrvTransactBuffer replyBuf = new SrvTransactBuffer(tbuf);
            DataBuffer dataBuf = replyBuf.getDataBuffer();

            // Determine the maximum return data length
            int maxLen = replyBuf.getReturnDataLimit();

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                m_sess.debugPrintln("Continue search [" + searchId + "] - " + resumeName + ", maxFiles=" + maxFiles + ", maxLen="
                        + maxLen + ", infoLevel=" + infoLevl + ", flags=0x" + Integer.toHexString(srchFlag));

            // Loop until we have filled the return buffer or there are no more files to return
            int fileCnt = 0;
            int packLen = 0;
            int lastNameOff = 0;

            // Flag to indicate if resume ids should be returned
            boolean resumeIds = false;
            if (infoLevl == FindInfoPacker.InfoStandard && (srchFlag & FindFirstNext.ReturnResumeKey) != 0) {

                // Windows servers only seem to return resume keys for the standard information level
                resumeIds = true;
            }

            // Flags to indicate packet full or search complete
            boolean pktDone = false;
            boolean searchDone = false;

            FileInfo info = new FileInfo();

            while (pktDone == false && fileCnt < maxFiles) {

                // Get file information from the search
                if (ctx.nextFileInfo(info) == false) {

                    // No more files
                    pktDone = true;
                    searchDone = true;
                }

                // Check if the file information will fit into the return buffer
                else if (FindInfoPacker.calcInfoSize(info, infoLevl, false, true) <= maxLen) {

                    // Pack the resume id, if required
                    if (resumeIds == true) {
                        dataBuf.putInt(ctx.getResumeId());
                        maxLen -= 4;
                    }

                    // Save the offset to the last file information structure
                    lastNameOff = dataBuf.getPosition();

                    // Pack the file information
                    packLen = FindInfoPacker.packInfo(info, dataBuf, infoLevl, tbuf.isUnicode());

                    // Update the file count for this packet
                    fileCnt++;

                    // Recalculate the remaining buffer space
                    maxLen -= packLen;
                }
                else {

                    // Set the search restart point
                    ctx.restartAt(info);

                    // No more buffer space
                    pktDone = true;

                    // Debug
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                        m_sess.debugPrintln("Find next response full, restart at " + info.getFileName());
                }
            }

            // Pack the parameter block
            paramBuf = replyBuf.getParameterBuffer();

            paramBuf.putShort(fileCnt);
            paramBuf.putShort(ctx.hasMoreFiles() ? 0 : 1);
            paramBuf.putShort(0);
            paramBuf.putShort(lastNameOff);

            // Send the transaction response
            SMBSrvTransPacket tpkt = new SMBSrvTransPacket(smbPkt);
            tpkt.doTransactionResponse(m_sess, replyBuf, smbPkt);

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                m_sess.debugPrintln("Search [" + searchId + "] Returned " + fileCnt + " files, dataLen=" + dataBuf.getLength()
                        + ", moreFiles=" + ctx.hasMoreFiles());

            // Check if the search is complete
            if (searchDone == true || ctx.hasMoreFiles() == false) {

                // Debug
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                    m_sess.debugPrintln("End start search [" + searchId + "] (Search complete)");

                // Release the search context
                vc.deallocateSearchSlot(searchId);
            }
            else if ((srchFlag & FindFirstNext.CloseSearch) != 0) {

                // Debug
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                    m_sess.debugPrintln("End start search [" + searchId + "] (Close)");

                // Release the search context
                vc.deallocateSearchSlot(searchId);
            }
        }
        catch (FileNotFoundException ex) {

            // Deallocate the search
            if (searchId != -1)
                vc.deallocateSearchSlot(searchId);

            // Search path does not exist
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSNoMoreFiles, SMBStatus.ErrDos);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Deallocate the search
            if (searchId != -1)
                vc.deallocateSearchSlot(searchId);

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
        }
        catch (UnsupportedInfoLevelException ex) {

            // Deallocate the search
            if (searchId != -1)
                vc.deallocateSearchSlot(searchId);

            // Requested information level is not supported
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
        }
    }

    /**
     * Process a transact2 file system query request.
     *
     * @param tbuf   Transaction request details
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procTrans2QueryFileSys(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the query file system required information level
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int infoLevl = paramBuf.getShort();

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_INFO))
            m_sess.debugPrintln("Query File System Info - level = 0x" + Integer.toHexString(infoLevl));

        // Access the shared device disk interface
        try {

            // Access the disk interface and context
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();
            DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();

            // Set the return parameter count, so that the data area position can be calculated.
            parser.setParameterCount(10);

            // Pack the disk information into the data area of the transaction reply
            byte[] buf = parser.getBuffer();
            int prmPos = DataPacker.longwordAlign(parser.getByteOffset());
            int dataPos = prmPos; // no parameters returned

            // Create a data buffer using the SMB packet. The response should always fit into a
            // single reply packet.
            DataBuffer replyBuf = new DataBuffer(buf, dataPos, buf.length - dataPos);

            // Determine the information level requested
            SrvDiskInfo diskInfo = null;
            VolumeInfo volInfo = null;

            switch (infoLevl) {

                // Standard disk information
                case DiskInfoPacker.InfoStandard:

                    // Get the disk information
                    diskInfo = getDiskInformation(disk, diskCtx);

                    // Pack the disk information into the return data packet
                    DiskInfoPacker.packStandardInfo(diskInfo, replyBuf);
                    break;

                // Volume label information
                case DiskInfoPacker.InfoVolume:

                    // Get the volume label information
                    volInfo = getVolumeInformation(disk, diskCtx);

                    // Pack the volume label information
                    DiskInfoPacker.packVolumeInfo(volInfo, replyBuf, tbuf.isUnicode());
                    break;

                // Full volume information
                case DiskInfoPacker.InfoFsVolume:

                    // Get the volume information
                    volInfo = getVolumeInformation(disk, diskCtx);

                    // Pack the volume information
                    DiskInfoPacker.packFsVolumeInformation(volInfo, replyBuf, tbuf.isUnicode());
                    break;

                // Filesystem size information
                case DiskInfoPacker.InfoFsSize:

                    // Get the disk information
                    diskInfo = getDiskInformation(disk, diskCtx);

                    // Pack the disk information into the return data packet
                    DiskInfoPacker.packFsSizeInformation(diskInfo, replyBuf);
                    break;

                // Filesystem device information
                case DiskInfoPacker.InfoFsDevice:
                    DiskInfoPacker.packFsDevice(NTIOCtl.DeviceDisk, diskCtx.getDeviceAttributes(), replyBuf);
                    break;

                // Filesystem attribute information
                case DiskInfoPacker.InfoFsAttribute:
                    String fsType = diskCtx.getFilesystemType();

                    if (disk instanceof NTFSStreamsInterface) {

                        // Check if NTFS streams are enabled
                        NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) disk;
                        if (ntfsStreams.hasStreamsEnabled(m_sess, conn))
                            fsType = "NTFS";
                    }

                    // Pack the filesystem type
                    DiskInfoPacker.packFsAttribute(diskCtx.getFilesystemAttributes(), MaxPathLength, fsType, tbuf.isUnicode(),
                            replyBuf);
                    break;

                // Mac filesystem information
                case DiskInfoPacker.InfoMacFsInfo:

                    // Check if the filesystem supports NTFS streams
                    //
                    // We should only return a valid response to the Macintosh information level if the
                    // filesystem does NOT support NTFS streams. By returning an error status the Thursby DAVE
                    // software will treat the filesystem as a WinXP/2K filesystem with full streams support.
                    boolean ntfs = false;

                    if (disk instanceof NTFSStreamsInterface) {

                        // Check if streams are enabled
                        NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) disk;
                        ntfs = ntfsStreams.hasStreamsEnabled(m_sess, conn);
                    }

                    // If the filesystem does not support NTFS streams then send a valid response.
                    if (ntfs == false) {

                        // Get the disk and volume information
                        diskInfo = getDiskInformation(disk, diskCtx);
                        volInfo = getVolumeInformation(disk, diskCtx);

                        // Pack the disk information into the return data packet
                        DiskInfoPacker.packMacFsInformation(diskInfo, volInfo, ntfs, replyBuf);
                    }
                    break;

                // Filesystem size information, including per user allocation limit
                case DiskInfoPacker.InfoFullFsSize:

                    // Get the disk information
                    diskInfo = getDiskInformation(disk, diskCtx);

                    // Check if there is a quota manager configured, if so then get the per user free
                    // space from the quota manager
                    long userLimit = -1L;
                    long userTotalSpace = -1L;

                    if (diskCtx.hasQuotaManager()) {

                        // Get the per user quota and free space from the quota manager
                        userTotalSpace = diskCtx.getQuotaManager().getUserTotalSpace(m_sess, conn);
                        userLimit = diskCtx.getQuotaManager().getUserFreeSpace(m_sess, conn);
                    }

                    // If the per user free space is not valid then use the total available free space,
                    // else convert to allocation units.
                    if (userTotalSpace > 0)
                        userTotalSpace = userTotalSpace / diskInfo.getUnitSize();
                    else
                        userTotalSpace = diskInfo.getTotalUnits();

                    if (userLimit != -1L)
                        userLimit = userLimit / diskInfo.getUnitSize();
                    else
                        userLimit = diskInfo.getFreeUnits();

                    // Pack the disk information into the return data packet
                    DiskInfoPacker.packFullFsSizeInformation(userTotalSpace, userLimit, diskInfo, replyBuf);
                    break;
            }

            // Check if any data was packed, if not then the information level is not supported
            if (replyBuf.getPosition() == dataPos) {
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
                return;
            }

            int bytCnt = replyBuf.getPosition() - parser.getByteOffset();
            replyBuf.setEndOfBuffer();
            int dataLen = replyBuf.getLength();
            SMBSrvTransPacket.initTransactReply(smbPkt, 0, prmPos, dataLen, dataPos);
            parser.setByteCount(bytCnt);

            // Send the transact reply
            m_sess.sendResponseSMB(smbPkt);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
    }

    /**
     * Process a transact2 query path information request.
     *
     * @param tbuf   Transaction request details
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procTrans2QueryPath(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the query path information level and file/directory name
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int infoLevl = paramBuf.getShort();
        paramBuf.skipBytes(4);

        String path = paramBuf.getString(tbuf.isUnicode());
        if (path.length() == 0)
            path = FileName.DOS_SEPERATOR_STR;

        // Normalize paths that end with the NTFS data stream name
        if (path.endsWith(FileName.DataStreamName))
            path = path.substring(0, path.length() - FileName.DataStreamName.length());

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_INFO))
            m_sess.debugPrintln("Query Path - level = 0x" + Integer.toHexString(infoLevl) + ", path = " + path);

        // Access the shared device disk interface
        try {

            // Access the disk interface
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Set the return parameter count, so that the data area position can be calculated.
            parser.setParameterCount(10);

            // Pack the file information into the data area of the transaction reply
            byte[] buf = parser.getBuffer();
            int prmPos = DataPacker.longwordAlign(parser.getByteOffset());
            int dataPos = prmPos + 4;

            // Pack the return parametes, EA error offset
            parser.setPosition(prmPos);
            parser.packWord(0);

            // Create a data buffer for the file information
            DataBuffer replyBuf = new DataBuffer(256);

            // Check if the virtual filesystem supports streams, and streams are enabled
            boolean streams = false;

            if (disk instanceof NTFSStreamsInterface) {

                // Check if NTFS streams are enabled
                NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) disk;
                streams = ntfsStreams.hasStreamsEnabled(m_sess, conn);
            }

            // Check if the path is for an NTFS stream, return an error if streams are not supported
            // or not enabled
            if (streams == false && path.indexOf(FileOpenParams.StreamSeparator) != -1) {

                // NTFS streams not supported, return an error status
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameInvalid, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
                return;
            }

            // Check for the file streams information level
            int dataLen = 0;

            if (streams == true && (infoLevl == FileInfoLevel.PathFileStreamInfo || infoLevl == FileInfoLevel.NTFileStreamInfo)) {

                // Debug
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_STREAMS))
                    m_sess.debugPrintln("Get NTFS streams list path=" + path);

                // Get the list of streams from the share driver
                NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) disk;
                StreamInfoList streamList = ntfsStreams.getStreamList(m_sess, conn, path);

                if (streamList == null) {
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNoSuchFile, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
                    return;
                }

                // Pack the file streams information into the return data packet
                dataLen = QueryInfoPacker.packStreamFileInfo(streamList, replyBuf, true);
            }
            else {

                // Get the file information
                FileInfo fileInfo = disk.getFileInformation(m_sess, conn, path);

                if (fileInfo == null) {
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNoSuchFile, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
                    return;
                }

                // Pack the file information into the return data packet
                dataLen = QueryInfoPacker.packInfo(fileInfo, replyBuf, infoLevl, true);
            }

            // Check if any data was packed, if not then the information level is not supported
            if (dataLen == 0) {
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
                return;
            }

            // Check if the file information response will fit into the current packet
            SMBSrvPacket respPkt = smbPkt;
            SMBSrvTransPacket.initTransactReply(respPkt, 2, prmPos, dataLen, dataPos);

            if (parser.getAvailableLength() < (dataLen + 4)) {

                // Allocate a new buffer for the response
                respPkt = m_sess.getPacketPool().allocatePacket(parser.getByteOffset() + dataLen + 4, smbPkt, parser.getByteOffset());

                // Set the parser for the response packet
                respPkt.setParser( SMBSrvPacket.Version.V1);
                parser = (SMBV1Parser) respPkt.getParser();
            }

            // Copy the file information to the response packet
            replyBuf.setEndOfBuffer();
            replyBuf.copyData(respPkt.getBuffer(), dataPos);

            // Set the byte count
            parser.setByteCount((dataPos + dataLen) - parser.getByteOffset());

            // Send the transact reply
            m_sess.sendResponseSMB(respPkt);
        }
        catch (FileNotFoundException ex) {

            // Requested file does not exist
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNoSuchFile, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }
        catch (PathNotFoundException ex) {

            // Requested path does not exist
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }
        catch (UnsupportedInfoLevelException ex) {

            // Requested information level is not supported
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }
        catch (DiskOfflineException ex) {

            // Filesystem is offline
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
            return;
        }
        catch (AccessDeniedException ex) {

            // access denied
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
    }

    /**
     * Process a transact2 query file information (via handle) request.
     *
     * @param tbuf   Transaction request details
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procTrans2QueryFile(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the file id and query path information level
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int fid = paramBuf.getShort();
        int infoLevl = paramBuf.getShort();

        // Get the file details via the file id
        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_INFO))
            m_sess.debugPrintln("Query File - level=0x" + Integer.toHexString(infoLevl) + ", fid=" + fid + ", stream="
                    + netFile.getStreamId() + ", name=" + netFile.getFullName());

        // Access the shared device disk interface
        try {

            // Access the disk interface
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Set the return parameter count, so that the data area position can be calculated.
            parser.setParameterCount(10);

            // Pack the file information into the data area of the transaction reply
            byte[] buf = parser.getBuffer();
            int prmPos = DataPacker.longwordAlign(parser.getByteOffset());
            int dataPos = prmPos + 4;

            // Pack the return parametes, EA error offset
            parser.setPosition(prmPos);
            parser.packWord(0);

            // Check if the virtual filesystem supports streams, and streams are enabled
            boolean streams = false;
            DataBuffer replyBuf = null;

            if (disk instanceof NTFSStreamsInterface) {

                // Check if NTFS streams are enabled
                NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) disk;
                streams = ntfsStreams.hasStreamsEnabled(m_sess, conn);
            }

            // Check for the file streams information level
            int dataLen = 0;

            if (streams == true && (infoLevl == FileInfoLevel.PathFileStreamInfo || infoLevl == FileInfoLevel.NTFileStreamInfo)) {

                // Debug
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_STREAMS))
                    m_sess.debugPrintln("Get NTFS streams list fid=" + fid + ", name=" + netFile.getFullName());

                // Get the list of streams from the share driver
                NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) disk;
                StreamInfoList streamList = ntfsStreams.getStreamList(m_sess, conn, netFile.getFullName());

                if (streamList == null) {
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
                    return;
                }

                // Allocate a larger response buffer if there is more than one stream to return information for
                if (streamList.numberOfStreams() > 1 && buf.length < NTFSStreamsInfoBufsize) {

                    // Allocate a larger packet for the response
                    smbPkt = m_sess.getPacketPool().allocatePacket(NTFSStreamsInfoBufsize, smbPkt, dataPos);

                    // Switch to the response buffer
                    buf = parser.getBuffer();
                }

                // Create a data buffer using the SMB packet. The response should always fit into a single reply packet.
                replyBuf = new DataBuffer(buf, dataPos, buf.length - dataPos);

                // Pack the file streams information into the return data packet
                dataLen = QueryInfoPacker.packStreamFileInfo(streamList, replyBuf, true);
            }
            else {

                // Get the file information
                FileInfo fileInfo = disk.getFileInformation(m_sess, conn, netFile.getFullName());

                if (fileInfo == null) {
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
                    return;
                }

                // Copy current file size and access date/time from the open file
                fileInfo.setFileSize(netFile.getFileSize());
                fileInfo.setAllocationSize( MemorySize.roundupLongSize(fileInfo.getSize()));

                if (netFile.hasAccessDate())
                    fileInfo.setAccessDateTime(netFile.getAccessDate());

                // Create a data buffer using the SMB packet. The response should always fit into a
                // single reply packet.
                replyBuf = new DataBuffer(buf, dataPos, buf.length - dataPos);

                // Pack the file information into the return data packet
                dataLen = QueryInfoPacker.packInfo(fileInfo, replyBuf, infoLevl, true);
            }

            // Check if any data was packed, if not then the information level is not supported
            if (dataLen == 0) {
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
                return;
            }

            SMBSrvTransPacket.initTransactReply(smbPkt, 2, prmPos, dataLen, dataPos);
            parser.setByteCount(replyBuf.getPosition() - parser.getByteOffset());

            // Send the transact reply
            m_sess.sendResponseSMB(smbPkt);
        }
        catch (FileNotFoundException ex) {

            // Requested file does not exist
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }
        catch (PathNotFoundException ex) {

            // Requested path does not exist
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }
        catch (UnsupportedInfoLevelException ex) {

            // Requested information level is not supported
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }
        catch (DiskOfflineException ex) {

            // Filesystem is offline
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
        }
    }

    /**
     * Process a transact2 set file information (via handle) request.
     *
     * @param tbuf   Transaction request details
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procTrans2SetFile(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasWriteAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the file id and information level
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int fid = paramBuf.getShort();
        int infoLevl = paramBuf.getShort();

        // Get the file details via the file id
        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_INFO))
            m_sess.debugPrintln("Set File - level=0x" + Integer.toHexString(infoLevl) + ", fid=" + fid + ", name="
                    + netFile.getFullName());

        // Access the shared device disk interface
        try {

            // Access the disk interface
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Process the set file information request
            DataBuffer dataBuf = tbuf.getDataBuffer();
            FileInfo finfo = null;

            switch (infoLevl) {

                // Set basic file information (dates/attributes)
                case FileInfoLevel.SetBasicInfo:
                case FileInfoLevel.NTFileBasicInfo:

                    // Create the file information template
                    int setFlags = 0;
                    finfo = new FileInfo(netFile.getFullName(), 0, -1);

                    // Set the creation date/time, if specified
                    long timeNow = System.currentTimeMillis();

                    long nttim = dataBuf.getLong();
                    boolean hasSetTime = false;

                    if (nttim != 0L) {
                        if (nttim != -1L) {
                            finfo.setCreationDateTime(NTTime.toJavaDate(nttim));
                            setFlags += FileInfo.SetCreationDate;
                        }
                        hasSetTime = true;
                    }

                    // Set the last access date/time, if specified
                    nttim = dataBuf.getLong();

                    if (nttim != 0L) {
                        if (nttim != -1L) {
                            finfo.setAccessDateTime(NTTime.toJavaDate(nttim));
                            setFlags += FileInfo.SetAccessDate;
                        }
                        else {
                            finfo.setAccessDateTime(timeNow);
                            setFlags += FileInfo.SetAccessDate;
                        }
                        hasSetTime = true;
                    }

                    // Set the last write date/time, if specified
                    nttim = dataBuf.getLong();

                    if (nttim > 0L) {
                        if (nttim != -1L) {
                            finfo.setModifyDateTime(NTTime.toJavaDate(nttim));
                            setFlags += FileInfo.SetModifyDate;
                        }
                        else {
                            finfo.setModifyDateTime(timeNow);
                            setFlags += FileInfo.SetModifyDate;
                        }
                        hasSetTime = true;
                    }

                    // Set the modify date/time, if specified
                    nttim = dataBuf.getLong();

                    if (nttim > 0L) {
                        if (nttim != -1L) {
                            finfo.setChangeDateTime(NTTime.toJavaDate(nttim));
                            setFlags += FileInfo.SetChangeDate;
                        }
                        hasSetTime = true;
                    }

                    // Set the attributes
                    int attr = dataBuf.getInt();
                    int unknown = dataBuf.getInt();

                    if (hasSetTime == false && unknown == 0) {
                        finfo.setFileAttributes(attr);
                        setFlags += FileInfo.SetAttributes;
                    }

                    // Store the associated network file in the file information object
                    finfo.setNetworkFile(netFile);

                    // Set the file information for the specified file/directory
                    finfo.setFileInformationFlags(setFlags);
                    disk.setFileInformation(m_sess, conn, netFile.getFullName(), finfo);

                    // Debug
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_INFO))
                        m_sess.debugPrintln("  Set Basic Info [" + treeId + "] name=" + netFile.getFullName() + ", attr=0x"
                                + Integer.toHexString(attr) + ", setTime=" + hasSetTime + ", setFlags=0x"
                                + Integer.toHexString(setFlags) + ", unknown=" + unknown);
                    break;

                // Set end of file position for a file
                case FileInfoLevel.SetEndOfFileInfo:
                case FileInfoLevel.NTSetEndOfFileInfo:

                    // Get the new end of file position
                    long eofPos = dataBuf.getLong();

                    // Set the new end of file position
                    disk.truncateFile(m_sess, conn, netFile, eofPos);

                    // Debug
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_INFO))
                        m_sess.debugPrintln("  Set end of file position fid=" + fid + ", eof=" + eofPos);
                    break;

                // Set the allocation size for a file
                case FileInfoLevel.SetAllocationInfo:
                case FileInfoLevel.NTSetFileAllocationInfo:

                    // Get the new end of file position
                    long allocSize = dataBuf.getLong();

                    // Set the new end of file position
                    disk.truncateFile(m_sess, conn, netFile, allocSize);

                    // Debug
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_INFO))
                        m_sess.debugPrintln("  Set allocation size fid=" + fid + ", allocSize=" + allocSize);
                    break;

                // Rename a stream
                case FileInfoLevel.NTFileRenameInfo:

                    // Unpack the rename details
                    boolean overwrite = dataBuf.getByte() == 1 ? true : false;
                    dataBuf.skipBytes(3);

                    int rootFid = dataBuf.getInt();
                    int nameLen = dataBuf.getInt();
                    String newName = dataBuf.getString(nameLen, true);

                    // Debug
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_INFO))
                        m_sess.debugPrintln("  Set rename fid=" + fid + ", newName=" + newName + ", overwrite=" + overwrite
                                + ", rootFID=" + rootFid);

                    // Check if the new path contains a directory, only rename of a stream on the same file is supported.
                    // Make sure the network file is not a folder.
                    if (newName.indexOf(FileName.DOS_SEPERATOR_STR) != -1 || netFile.isDirectory()) {

                        // Return a not supported error status
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNotSupported, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
                        return;
                    }

                    // Check if the virtual filesystem supports streams, and streams are enabled
                    boolean streams = false;

                    if (disk instanceof NTFSStreamsInterface) {

                        // Check if NTFS streams are enabled
                        NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) disk;
                        streams = ntfsStreams.hasStreamsEnabled(m_sess, conn);
                    }

                    // If streams are not supported or are not enabled then check for rename of a file/folder, or
                    // return an error status
                    if (streams == false) {

                        // Check if this is a rename of a file rather than a stream
                        if (FileName.containsStreamName(newName) == false) {

                            // Build the target file relative path
                            String[] paths = FileName.splitPath(netFile.getFullName());
                            String newPath = null;
                            if (paths[0] != null)
                                newPath = paths[0] + FileName.DOS_SEPERATOR_STR + newName;
                            else
                                newPath = FileName.DOS_SEPERATOR_STR + newName;

                            // Check if the target file exists
                            FileStatus fileSts = disk.fileExists(m_sess, conn, newPath);

                            if (fileSts == FileStatus.FileExists && overwrite == false) {

                                // Return an error status, rename would overwrite an existing file
                                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
                                return;
                            }
                            else {

                                // Debug
                                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
                                    m_sess.debugPrintln("Transact rename via standard rename from=" + netFile.getFullName() + " to=" + newPath);

                                // Call the standard disk interface rename method to rename the file
                                disk.renameFile(m_sess, conn, netFile.getFullName(), newPath);
                            }
                        }
                        else {

                            // Return a not supported error status
                            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNotSupported, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
                            return;
                        }
                    }
                    else {

                        // Debug
                        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_STREAMS))
                            m_sess.debugPrintln("Rename stream fid=" + fid + ", name=" + netFile.getFullNameStream() + ", newName="
                                    + newName + ", overwrite=" + overwrite);

                        // Rename the stream
                        NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) disk;
                        ntfsStreams.renameStream(m_sess, conn, netFile.getFullNameStream(), newName, overwrite);
                    }
                    break;

                // Mark or unmark a file/directory for delete
                case FileInfoLevel.SetDispositionInfo:
                case FileInfoLevel.NTFileDispositionInfo:

                    // Get the delete flag
                    int flag = dataBuf.getByte();
                    boolean delFlag = flag == 1 ? true : false;

                    // Call the filesystem driver set file information to see if the file can be marked for delete.
                    FileInfo delInfo = new FileInfo();
                    delInfo.setDeleteOnClose(delFlag);
                    delInfo.setFileInformationFlags(FileInfo.SetDeleteOnClose);

                    disk.setFileInformation(m_sess, conn, netFile.getFullName(), delInfo);

                    // Mark/unmark the file/directory for deletion
                    netFile.setDeleteOnClose(delFlag);

                    // Debug
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_INFO))
                        m_sess.debugPrintln("  Set file disposition fid=" + fid + ", name=" + netFile.getName() + ", delete="
                                + delFlag);
                    break;
            }

            // Set the return parameter count, so that the data area position can be calculated.
            parser.setParameterCount(10);

            // Pack the return information into the data area of the transaction reply
            byte[] buf = parser.getBuffer();
            int prmPos = parser.getByteOffset();

            // Longword align the parameters, return an unknown word parameter
            //
            // Note: Make sure the data offset is on a longword boundary, NT has problems if this is
            // not done
            prmPos = DataPacker.longwordAlign(prmPos);
            DataPacker.putIntelShort(0, buf, prmPos);

            SMBSrvTransPacket.initTransactReply(smbPkt, 2, prmPos, 0, prmPos + 4);
            parser.setByteCount((prmPos - parser.getByteOffset()) + 4);

            // Send the transact reply
            m_sess.sendResponseSMB(smbPkt);

            // Check if there are any file/directory change notify requests active
            DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();

            if (diskCtx.hasFileServerNotifications() && netFile.getFullName() != null) {

                // Get the change handler
                NotifyChangeHandler changeHandler = diskCtx.getChangeHandler();

                // Check for file attributes and last write time changes
                if (finfo != null) {

                    // File attributes changed
                    if (finfo.hasSetFlag(FileInfo.SetAttributes))
                        changeHandler.notifyAttributesChanged(netFile.getFullName(), netFile.isDirectory());

                    // Last write time changed
                    if (finfo.hasSetFlag(FileInfo.SetModifyDate))
                        changeHandler.notifyLastWriteTimeChanged(netFile.getFullName(), netFile.isDirectory());
                }
                else if (infoLevl == FileInfoLevel.SetAllocationInfo || infoLevl == FileInfoLevel.SetEndOfFileInfo) {

                    // File size changed
                    changeHandler.notifyFileSizeChanged(netFile.getFullName());
                }
            }
        }
        catch (FileNotFoundException ex) {

            // Requested file does not exist
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
        }
        catch (PermissionDeniedException ex) {

            // Not allowed to rename the file/directory
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNetworkAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (AccessDeniedException ex) {

            // Not allowed to change file attributes/settings
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
        }
        catch (DiskFullException ex) {

            // Disk is full
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTDiskFull, SMBStatus.HRDWriteFault, SMBStatus.ErrHrd);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
        }
        catch (DiskOfflineException ex) {

            // Filesystem is offline
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
        }
        catch (DirectoryNotEmptyException ex) {

            // Directory not empty
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSDirectoryNotEmpty, SMBStatus.ErrDos);
        }
        catch (Exception ex) {

            // Other error during set file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
        }
    }

    /**
     * Process a transact2 set path information request.
     *
     * @param tbuf   Transaction request details
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procTrans2SetPath(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasWriteAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the path and information level
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int infoLevl = paramBuf.getShort();
        paramBuf.skipBytes(4);

        String path = paramBuf.getString(tbuf.isUnicode());
        if (path.length() == 0)
            path = FileName.DOS_SEPERATOR_STR;

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_INFO))
            m_sess.debugPrintln("Set Path - path=" + path + ", level=0x" + Integer.toHexString(infoLevl));

        // Check if the file name is valid
        if (FileName.isValidPath(path) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameInvalid, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Access the shared device disk interface
        try {

            // Access the disk interface
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Process the set file information request
            DataBuffer dataBuf = tbuf.getDataBuffer();
            FileInfo finfo = null;

            int setFlags = 0;
            int attr = 0;

            switch (infoLevl) {

                // Set standard file information (dates/attributes)
                case FileInfoLevel.SetStandard:

                    // Create the file information template
                    finfo = new FileInfo(path, 0, -1);

                    // Set the creation date/time, if specified
                    int smbDate = dataBuf.getShort();
                    int smbTime = dataBuf.getShort();

                    boolean hasSetTime = false;

                    if (smbDate != 0 && smbTime != 0) {
                        finfo.setCreationDateTime(new SMBDate(smbDate, smbTime).getTime());
                        setFlags += FileInfo.SetCreationDate;
                        hasSetTime = true;
                    }

                    // Set the last access date/time, if specified
                    smbDate = dataBuf.getShort();
                    smbTime = dataBuf.getShort();

                    if (smbDate != 0 && smbTime != 0) {
                        finfo.setAccessDateTime(new SMBDate(smbDate, smbTime).getTime());
                        setFlags += FileInfo.SetAccessDate;
                        hasSetTime = true;
                    }

                    // Set the last write date/time, if specified
                    smbDate = dataBuf.getShort();
                    smbTime = dataBuf.getShort();

                    if (smbDate != 0 && smbTime != 0) {
                        finfo.setModifyDateTime(new SMBDate(smbDate, smbTime).getTime());
                        setFlags += FileInfo.SetModifyDate;
                        hasSetTime = true;
                    }

                    // Set the file size/allocation size
                    int fileSize = dataBuf.getInt();
                    if (fileSize != 0) {
                        finfo.setFileSize(fileSize);
                        setFlags += FileInfo.SetFileSize;
                    }

                    fileSize = dataBuf.getInt();
                    if (fileSize != 0) {
                        finfo.setAllocationSize(fileSize);
                        setFlags += FileInfo.SetAllocationSize;
                    }

                    // Set the attributes
                    attr = dataBuf.getInt();
                    int eaListLen = dataBuf.getInt();

                    if (hasSetTime == false && eaListLen == 0) {
                        finfo.setFileAttributes(attr);
                        setFlags += FileInfo.SetAttributes;
                    }

                    // Set the file information for the specified file/directory
                    finfo.setFileInformationFlags(setFlags);
                    disk.setFileInformation(m_sess, conn, path, finfo);

                    // Debug
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_INFO))
                        m_sess.debugPrintln("  Set Standard Info [" + treeId + "] name=" + path + ", attr=0x"
                                + Integer.toHexString(attr) + ", setTime=" + hasSetTime + ", setFlags=0x"
                                + Integer.toHexString(setFlags) + ", eaListLen=" + eaListLen);
                    break;

                // Set basic file information (dates/attributes)
                case FileInfoLevel.SetBasicInfo:

                    // Create the file information template
                    finfo = new FileInfo(path, 0, -1);

                    // Set the creation date/time, if specified
                    long dateTime = NTTime.toJavaDate(dataBuf.getLong());

                    if (dateTime != 0L) {
                        finfo.setCreationDateTime(dateTime);
                        setFlags += FileInfo.SetCreationDate;
                    }

                    // Set the last access date/time, if specified
                    dateTime = NTTime.toJavaDate(dataBuf.getLong());

                    if (dateTime != 0L) {
                        finfo.setAccessDateTime(dateTime);
                        setFlags += FileInfo.SetAccessDate;
                    }

                    // Set the last write date/time, if specified
                    dateTime = NTTime.toJavaDate(dataBuf.getLong());

                    if (dateTime != 0L) {
                        finfo.setModifyDateTime(dateTime);
                        setFlags += FileInfo.SetModifyDate;
                    }

                    // Set the change write date/time, if specified
                    dateTime = NTTime.toJavaDate(dataBuf.getLong());

                    if (dateTime != 0L) {
                        finfo.setChangeDateTime(dateTime);
                        setFlags += FileInfo.SetChangeDate;
                    }

                    // Set the attributes
                    attr = dataBuf.getInt();

                    if (attr != 0) {
                        finfo.setFileAttributes(attr);
                        setFlags += FileInfo.SetAttributes;
                    }

                    // Set the file information for the specified file/directory
                    finfo.setFileInformationFlags(setFlags);
                    disk.setFileInformation(m_sess, conn, path, finfo);

                    // Debug
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_INFO))
                        m_sess.debugPrintln("  Set Basic Info [" + treeId + "] name=" + path + ", attr=0x"
                                + Integer.toHexString(attr) + ", setFlags=0x" + Integer.toHexString(setFlags));
                    break;
            }

            // Set the return parameter count, so that the data area position can be calculated.
            parser.setParameterCount(10);

            // Pack the return information into the data area of the transaction reply
            byte[] buf = parser.getBuffer();
            int prmPos = parser.getByteOffset();

            // Longword align the parameters, return an unknown word parameter
            //
            // Note: Make sure the data offset is on a longword boundary, NT has problems if this is not done
            prmPos = DataPacker.longwordAlign(prmPos);
            DataPacker.putIntelShort(0, buf, prmPos);

            SMBSrvTransPacket.initTransactReply(smbPkt, 2, prmPos, 0, prmPos + 4);
            parser.setByteCount((prmPos - parser.getByteOffset()) + 4);

            // Send the transact reply
            m_sess.sendResponseSMB(smbPkt);

            // Check if there are any file/directory change notify requests active
            DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();

            if (diskCtx.hasFileServerNotifications() && path != null) {

                // Get the change handler
                NotifyChangeHandler changeHandler = diskCtx.getChangeHandler();

                // Check for file attributes and last write time changes
                if (finfo != null) {

                    // Check if the path refers to a file or directory
                    FileStatus fileSts = disk.fileExists(m_sess, conn, path);

                    // File attributes changed
                    if (finfo.hasSetFlag(FileInfo.SetAttributes))
                        changeHandler.notifyAttributesChanged(path, fileSts == FileStatus.DirectoryExists ? true : false);

                    // Last write time changed
                    if (finfo.hasSetFlag(FileInfo.SetModifyDate))
                        changeHandler.notifyLastWriteTimeChanged(path, fileSts == FileStatus.DirectoryExists ? true : false);
                }
            }
        }
        catch (FileNotFoundException ex) {

            // Requested file does not exist
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
        }
        catch (AccessDeniedException ex) {

            // Not allowed to change file attributes/settings
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
        }
        catch (DiskFullException ex) {

            // Disk is full
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTDiskFull, SMBStatus.HRDWriteFault, SMBStatus.ErrHrd);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
        }
        catch (DiskOfflineException ex) {

            // Filesystem is offline
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
        }
        catch (Exception ex) {

            // Other error during set file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
        }
    }

    /**
     * Process the file write request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procWriteAndX(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid write andX request
        if (parser.checkPacketIsValid(12, 0) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasWriteAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // If the connection is to the IPC$ remote admin named pipe pass the request to the IPC handler
        if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {

            // Use the IPC$ handler to process the request
            IPCHandler.processIPCRequest(m_sess, smbPkt);
            return;
        }

        // Extract the write file parameters
        int fid = parser.getParameter(2);

        // Bottom 32bits of file offset
        long offset = (long) (((long) parser.getParameterLong(3)) & 0xFFFFFFFFL);
        int dataPos = parser.getParameter(11) + RFCNetBIOSProtocol.HEADER_LEN;

        int dataLen = parser.getParameter(10);
        int dataLenHigh = 0;

        if (smbPkt.getReceivedLength() > 0xFFFF)
            dataLenHigh = parser.getParameter(9) & 0x0001;

        if (dataLenHigh > 0)
            dataLen += (dataLenHigh << 16);

        // Check for the NT format request that has the top 32bits of the file offset
        if (parser.getParameterCount() == 14) {
            long topOff = (long) (((long) parser.getParameterLong(12)) & 0xFFFFFFFFL);
            offset += topOff << 32;
        }

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
            m_sess.debugPrintln("File Write AndX [" + netFile.getFileId() + "] : Size=" + dataLen + " ,Pos=" + offset);

        // Write data to the file
        byte[] buf = parser.getBuffer();
        int wrtlen = 0;

        // Access the disk interface and write to the file
        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Synchronize writes using the network file
            synchronized (netFile) {

                // Write to the file
                wrtlen = disk.writeFile(m_sess, conn, netFile, buf, dataPos, dataLen, offset);
            }
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (AccessDeniedException ex) {

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
                m_sess.debugPrintln("File Write Error [" + netFile.getFileId() + "] : " + ex.toString());

            // Not allowed to write to the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (LockConflictException ex) {

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_LOCK))
                m_sess.debugPrintln("Write Lock Error [" + netFile.getFileId() + "] : Size=" + dataLen + " ,Pos=" + offset);

            // File is locked
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTLockConflict, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (DiskFullException ex) {

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
                m_sess.debugPrintln("Write Quota Error [" + netFile.getFileId() + "] Disk full : Size=" + dataLen + " ,Pos="
                        + offset);

            // Disk is full
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTDiskFull, SMBStatus.HRDWriteFault, SMBStatus.ErrHrd);
            return;
        }
        catch (DiskOfflineException ex) {

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
                m_sess.debugPrintln("Filesystem Offline Error [" + netFile.getFileId() + "] Write File");

            // Filesystem is offline
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
        }
        catch (IOException ex) {

            // Debug
            if (Debug.EnableError && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
                m_sess.debugPrintln("File Write Error [" + netFile.getFileId() + "] : " + ex.toString());

            // Failed to read the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.HRDWriteFault, SMBStatus.ErrHrd);
            return;
        }

        // Return the count of bytes actually written
        parser.setSuccessStatus();
        parser.setParameterCount(6);
        parser.setAndXCommand(0xFF);
        parser.setParameter(1, 0); // AndX offset
        parser.setParameter(2, wrtlen);
        parser.setParameter(3, 0xFFFF);

        if (dataLenHigh > 0) {
            parser.setParameter(4, dataLen >> 16);
            parser.setParameter(5, 0);
        }
        else {
            parser.setParameterLong(4, 0);
        }

        parser.setByteCount(0);
        parser.setParameter(1, parser.getLength());

        // Send the write response
        m_sess.sendResponseSMB(smbPkt);

        // Report file size change notifications every so often
        //
        // We do not report every write due to the increased overhead of change notifications
        DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();

        if (netFile.getWriteCount() % FileSizeChangeRate == 0 && diskCtx.hasFileServerNotifications() && netFile.getFullName() != null) {

            // Get the change handler
            NotifyChangeHandler changeHandler = diskCtx.getChangeHandler();

            // File size changed
            changeHandler.notifyFileSizeChanged(netFile.getFullName());
        }
    }

    /**
     * Process the file create/open request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procNTCreateAndX(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid NT create andX request
        if (parser.checkPacketIsValid(24, 1) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVInvalidTID, SMBStatus.ErrSrv);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // If the connection is to the IPC$ remote admin named pipe pass the request to the IPC
        // handler. If the device is not a disk type device then return an error.
        if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {

            // Use the IPC$ handler to process the request
            IPCHandler.processIPCRequest(m_sess, smbPkt);
            return;
        }
        else if (conn.getSharedDevice().getType() != ShareType.DISK) {

            // Return an access denied error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Extract the NT create andX parameters
        NTParameterPacker prms = new NTParameterPacker(parser.getBuffer(), SMBV1.PARAMWORDS + 5);

        int nameLen = prms.unpackWord();
        int flags = prms.unpackInt();
        int rootFID = prms.unpackInt();
        int accessMask = prms.unpackInt();
        long allocSize = prms.unpackLong();
        int attrib = prms.unpackInt();
        SharingMode shrAccess = SharingMode.fromInt(prms.unpackInt());
        CreateDisposition createDisp = CreateDisposition.fromInt(prms.unpackInt());
        int createOptn = prms.unpackInt();
        ImpersonationLevel impersonLev = ImpersonationLevel.fromInt(prms.unpackInt());
        int secFlags = prms.unpackByte();

        // Extract the filename string
        String fileName = DataPacker.getUnicodeString(parser.getBuffer(), DataPacker.wordAlign(parser.getByteOffset()),
                nameLen / 2);
        if (fileName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Access the disk interface that is associated with the shared device
        DiskInterface disk = null;
        try {

            // Get the disk interface for the share
            disk = (DiskInterface) conn.getSharedDevice().getInterface();
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Check if the file name contains a file stream name. If the disk interface does not
        // implement the optional NTFS streams interface then return an error status, not supported.
        if (fileName.indexOf(FileOpenParams.StreamSeparator) != -1) {

            // Check if the driver implements the NTFS streams interface and it is enabled
            boolean streams = false;

            if (disk instanceof NTFSStreamsInterface) {

                // Check if streams are enabled
                NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) disk;
                streams = ntfsStreams.hasStreamsEnabled(m_sess, conn);
            }

            // Check if streams are enabled/available
            if (streams == false) {

                // Return a file not found error
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameInvalid, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
                return;
            }
        }

        // Create the file open parameters to be passed to the disk interface
        FileOpenParams params = new FileOpenParams(fileName, createDisp, accessMask, attrib, shrAccess, allocSize, createOptn,
                rootFID, impersonLev, secFlags, parser.getProcessIdFull());

        // Set the create flags, with oplock requests
        params.setNTCreateFlags(flags);
        params.setTreeId(treeId);
        params.setSession(m_sess);

        // If an oplock was requested then set an oplock owner
        SMBV1OplockOwner oplockOwner = null;

        if ( params.requestedOplockType() != OpLockType.LEVEL_NONE) {
            oplockOwner = new SMBV1OplockOwner( treeId, parser.getProcessId(), parser.getUserId());
            params.setOplockOwner( oplockOwner);
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
            m_sess.debugPrintln("NT Create AndX [" + treeId + "] params=" + params);

        // Check if the file name is valid
        if (FileName.isValidPath(params.getPath()) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameInvalid, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Access the disk interface and open the requested file
        int fid;
        NetworkFile netFile = null;
        int respAction = 0;
        OpLockDetails oplock = null;

        try {

            // Check if the requested file already exists
            FileStatus fileSts = disk.fileExists(m_sess, conn, params.getFullPath());

            // Check if the path is to a folder, make sure the Directory flag is set in the open parameters for oplock checking
            if (params.isDirectory() == false && fileSts == FileStatus.DirectoryExists) {
                params.setCreateOption(WinNT.CreateDirectory);
            }

            // Check if the file exists and it is a pseudo file, in which case the file already exists so change a create request to
            // an open request
            if (fileSts == FileStatus.FileExists) {

                // Check for a pseudo file
                FileInfo finfo = disk.getFileInformation(m_sess, conn, params.getFullPath());
                if (finfo != null && finfo.isPseudoFile()) {
                    createDisp = CreateDisposition.OPEN;

                    // Clear any oplock request for pseudo files
                    if (params.requestBatchOpLock() || params.requestExclusiveOpLock()) {
                        if (params.requestExtendedResponse())
                            params.setNTCreateFlags(WinNT.ExtendedResponse);
                        else
                            params.setNTCreateFlags(0);
                    }

                    // Debug
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
                        m_sess.debugPrintln("Converted create to open for pseudo file " + params);
                }
            }

            // Check if the file should be created
            if (fileSts == FileStatus.NotExist) {

                // Check if the file should be created if it does not exist
                if (createDisp == CreateDisposition.CREATE || createDisp == CreateDisposition.OPEN_IF
                        || createDisp == CreateDisposition.OVERWRITE_IF || createDisp == CreateDisposition.SUPERSEDE) {

                    // Check if the user has the required access permission
                    if (conn.hasWriteAccess() == false) {

                        // User does not have the required access rights
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
                        return;
                    }

                    // Check if a new file or directory should be created
                    if ((createOptn & WinNT.CreateDirectory) == 0) {

                        // Create a new file
                        netFile = disk.createFile(m_sess, conn, params);

                        // Indicate the file was created
                        if (netFile != null && m_sess.hasDebug(SMBSrvSession.DBG_BENCHMARK)) {
                            netFile.setStatusFlag(NetworkFile.Flags.CREATED, true);
                            netFile.setCreationDate(System.currentTimeMillis());
                        }

                        // Check if an oplock was requested, grant the oplock if possible and return the granted oplock details, or null
                        // if no oplock granted or requested.
                        oplock = OpLockHelper.grantOpLock(m_sess, smbPkt, disk, conn, params, netFile);
                    }
                    else {

                        // Split the path and walk to see which folder(s) need creating
                        String[] paths = FileName.splitAllPaths(params.getPath());
                        StringBuilder pathStr = new StringBuilder(params.getPath().length());
                        FileStatus fldrSts = FileStatus.Unknown;
                        int idx = 0;

                        while (idx < paths.length) {

                            // Add the current path component and check if it exists, and it is a folder
                            pathStr.append(FileName.DOS_SEPERATOR_STR);
                            pathStr.append(paths[idx++]);

                            fldrSts = disk.fileExists(m_sess, conn, pathStr.toString());

                            // If the current path exists and it is a file then return an error
                            if (fldrSts == FileStatus.FileExists) {
                                if (idx < paths.length)
                                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameCollision, SMBStatus.DOSFileAlreadyExists, SMBStatus.ErrDos);
                                else
                                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.DOSDirectoryInvalid, SMBStatus.ErrDos);
                                return;
                            }
                            else if (fldrSts == FileStatus.NotExist) {

                                // Create the current part of the path
                                FileOpenParams fldrParams = new FileOpenParams(pathStr.toString(), createDisp, accessMask, attrib, shrAccess, allocSize, createOptn,
                                        rootFID, impersonLev, secFlags, parser.getProcessIdFull());
                                disk.createDirectory(m_sess, conn, fldrParams);
                            }
                        }

                        // Open the requested folder, should now exist
                        netFile = disk.openFile(m_sess, conn, params);

                        // Indicate the directory was created
                        if (netFile != null && m_sess.hasDebug(SMBSrvSession.DBG_BENCHMARK)) {
                            netFile.setStatusFlag(NetworkFile.Flags.CREATED, true);
                            netFile.setCreationDate(System.currentTimeMillis());
                        }
                    }

                    // Check if the delete on close option is set
                    if (netFile != null && (createOptn & WinNT.CreateDeleteOnClose) != 0)
                        netFile.setDeleteOnClose(true);

                    // Indicate that the file did not exist and was created
                    respAction = FileAction.FileCreated;
                }
                else {

                    // Check if the path is a directory
                    if (fileSts == FileStatus.DirectoryExists) {

                        // Return an access denied error
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameCollision, SMBStatus.DOSFileAlreadyExists,
                                SMBStatus.ErrDos);
                        return;
                    }
                    else {

                        // Return a file not found error
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
                        return;
                    }
                }
            }
            else if (createDisp == CreateDisposition.CREATE) {

                // Check for a file or directory
                if (fileSts == FileStatus.FileExists || fileSts == FileStatus.DirectoryExists) {

                    // Return a file exists error
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameCollision, SMBStatus.DOSFileAlreadyExists, SMBStatus.ErrDos);
                    return;
                }
                else {

                    // Return an access denied exception
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
                    return;
                }
            }
            else {

                // Check if the open should be a file, not a directory
                if ((createOptn & WinNT.CreateNonDirectory) != 0 && fileSts == FileStatus.DirectoryExists) {

                    // Return a file is a directory error
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTFileIsADirectory, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
                    return;
                }

                // Check if the filesystem supports oplocks, check if there is an oplock on the file
                OpLockHelper.checkOpLock(m_sess, smbPkt, disk, params, conn);

                // Open the requested file/directory
                netFile = disk.openFile(m_sess, conn, params);

                // Check if an oplock was requested, grant the oplock if possible and return the granted oplock details, or null
                // if no oplock granted or requested.
                oplock = OpLockHelper.grantOpLock(m_sess, smbPkt, disk, conn, params, netFile);

                // Check if the file should be truncated
                if (createDisp == CreateDisposition.SUPERSEDE || createDisp == CreateDisposition.OVERWRITE_IF) {

                    // Truncate the file
                    disk.truncateFile(m_sess, conn, netFile, 0L);

                    // Debug
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
                        m_sess.debugPrintln("  [" + treeId + "] name=" + fileName + " truncated");

                    // Treat the file as if it is a newly created file
                    if (netFile != null && m_sess.hasDebug(SMBSrvSession.DBG_BENCHMARK)) {
                        netFile.setStatusFlag(NetworkFile.Flags.CREATED, true);
                        netFile.setCreationDate(System.currentTimeMillis());
                    }
                }

                // Set the file action response
                respAction = FileAction.FileExisted;
            }

            // Add the file to the list of open files for this tree connection
            fid = conn.addFile(netFile, getSession());

            // If the file has been granted an oplock then update the file id, needed for the oplock break
            if (oplock != null && (oplock.getLockType() != OpLockType.LEVEL_NONE && oplock.getLockType() != OpLockType.LEVEL_II)) {
                oplock.setOwnerFileId( fid);
                oplockOwner.setFileId( fid);
            }

            // DEBUG
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
                m_sess.debugPrintln("  [" + treeId + "] name=" + fileName + " fid=" + fid + ", fileId=" + netFile.getFileId() + ", opLock=" + oplock);
        }
        catch (TooManyFilesException ex) {

            // Too many files are open on this connection, cannot open any more files.
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTTooManyOpenFiles, SMBStatus.DOSTooManyOpenFiles, SMBStatus.ErrDos);
            return;
        }
        catch (AccessDeniedException ex) {

            // Return an access denied error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (FileExistsException ex) {

            // File/directory already exists
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameCollision, SMBStatus.DOSFileAlreadyExists, SMBStatus.ErrDos);
            return;
        }
        catch (FileSharingException ex) {

            // Return a sharing violation error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTSharingViolation, SMBStatus.DOSFileSharingConflict, SMBStatus.ErrDos);
            return;
        }
        catch (FileOfflineException ex) {

            // File data is unavailable
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTFileOffline, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
            return;
        }
        catch (FileNameException ex) {

            // File name too long or contains invalid characters
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameInvalid, SMBStatus.DOSInvalidFormat, SMBStatus.ErrDos);
            return;
        }
        catch (DiskOfflineException ex) {

            // Filesystem is offline
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
            return;
        }
        catch (DiskFullException ex) {

            // Disk is full
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTDiskFull, SMBStatus.HRDWriteFault, SMBStatus.ErrHrd);
            return;
        }
        catch (DeferredPacketException ex) {

            // Deferred packet, oplock break in progress, rethrow the exception
            throw ex;
        }
        catch (IOException ex) {

            // Failed to open the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }

        // Build the NT create andX response
        boolean extendedResponse = params.requestExtendedResponse();
        parser.setParameterCount(extendedResponse ? 42 : 34);

        parser.setAndXCommand(0xFF);
        parser.setParameter(1, 0); // AndX offset

        prms.reset(parser.getBuffer(), SMBV1.PARAMWORDS + 4);

        // Pack the oplock type, if granted
        if (oplock != null)
            prms.packByte(oplock.getLockType().intValue());
        else
            prms.packByte(0);

        // Pack the file id
        prms.packWord(fid);
        prms.packInt(respAction);

        // Pack the file/directory dates
        //
        // Creation
        // Access
        // Modify
        // Change
        if (netFile.hasCreationDate())
            prms.packLong(NTTime.toNTTime(netFile.getCreationDate()));
        else
            prms.packLong(0);

        if (netFile.hasAccessDate())
            prms.packLong(NTTime.toNTTime(netFile.getAccessDate()));
        else {

            // Use the modify date/time if access ate/time has not been set
            if (netFile.hasModifyDate())
                prms.packLong(NTTime.toNTTime(netFile.getModifyDate()));
            else
                prms.packLong(0);
        }

        if (netFile.hasModifyDate()) {
            long modDate = NTTime.toNTTime(netFile.getModifyDate());
            prms.packLong(modDate);
            prms.packLong(modDate);
        }
        else {
            prms.packLong(0); // Last write time
            prms.packLong(0); // Change time
        }

        prms.packInt(netFile.getFileAttributes());

        // Pack the file size/allocation size
        long fileSize = netFile.getFileSize();
        if (fileSize > 0L)
            fileSize = (fileSize + 512L) & 0xFFFFFFFFFFFFFE00L;

        prms.packLong(fileSize);    // Allocation size
        prms.packLong(netFile.getFileSize()); // End of file
        prms.packWord(0);            // File type - disk file
        prms.packWord(extendedResponse ? 7 : 0); // Device state
        prms.packByte(netFile.isDirectory() ? 1 : 0);

        prms.packWord(0); // byte count = 0

        // Pack the extra extended response area, if requested
        if (extendedResponse == true) {

            // 22 byte block of zeroes
            prms.packLong(0);
            prms.packLong(0);
            prms.packInt(0);
            prms.packWord(0);

            // Pack the permissions
            if (netFile.isDirectory() || netFile.getAllowedAccess() == NetworkFile.Access.READ_WRITE)
                prms.packInt(AccessMode.NTFileGenericAll);
            else
                prms.packInt(AccessMode.NTFileGenericRead);

            // 8 byte block of zeroes
            prms.packInt(0);
            prms.packInt(0);
        }

        // Set the AndX offset
        int endPos = prms.getPosition();
        parser.setParameter(1, endPos - RFCNetBIOSProtocol.HEADER_LEN);

        // Set the status
        parser.setLongErrorCode(SMBStatus.NTSuccess);

        // Check if there is a chained request
        if (parser.hasAndXCommand()) {

            // Process the chained requests
            endPos = procAndXCommands(smbPkt, parser, netFile);
        }

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt, endPos - RFCNetBIOSProtocol.HEADER_LEN);

        // Check if there are any file/directory change notify requests active
        DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();
        if (diskCtx.hasFileServerNotifications() && respAction == FileAction.FileCreated) {

            // Check if a file or directory has been created
            if (netFile.isDirectory())
                diskCtx.getChangeHandler().notifyDirectoryChanged(NotifyAction.Added, fileName);
            else
                diskCtx.getChangeHandler().notifyFileChanged(NotifyAction.Added, fileName);
        }
    }

    /**
     * Process the cancel request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procNTCancel(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid NT cancel request
        if (parser.checkPacketIsValid(0, 0) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Find the matching notify request and remove it
        NotifyRequest req = m_sess.findNotifyRequest(parser.getMultiplexId(), parser.getTreeId(), parser.getUserId(),
                parser.getProcessId());
        if (req != null) {

            // Remove the request
            m_sess.removeNotifyRequest(req);

            // Return a cancelled status
            parser.setParameterCount(0);
            parser.setByteCount(0);

            // Enable the long error status flag
            if (parser.isLongErrorCode() == false)
                parser.setFlags2(parser.getFlags2() + SMBV1.FLG2_LONGERRORCODE);

            // Set the NT status code
            parser.setLongErrorCode(SMBStatus.NTCancelled);

            // Set the Unicode strings flag
            if (parser.isUnicode() == false)
                parser.setFlags2(parser.getFlags2() + SMBV1.FLG2_UNICODE);

            // Return the error response to the client
            m_sess.sendResponseSMB(smbPkt);

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_NOTIFY)) {
                DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();
                m_sess.debugPrintln("NT Cancel notify mid=" + req.getId() + ", dir=" + req.getWatchPath() + ", queue="
                        + diskCtx.getChangeHandler().getRequestQueueSize());
            }
        }
        else {

            // Nothing to cancel
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
        }
    }

    /**
     * Process an NT transaction
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procNTTransaction(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that we received enough parameters for a transact2 request
        if (parser.checkPacketIsValid(19, 0) == false) {

            // Not enough parameters for a valid transact2 request
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid connection id.
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Check if the transaction request is for the IPC$ pipe
        if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {
            IPCHandler.processIPCRequest(m_sess, smbPkt);
            return;
        }

        // Get the NT transaction sub-command code
        int subCmd = parser.getNTFunction();

        // Check for a notfy change request, this needs special processing
        if (subCmd == PacketTypeV1.NTTransNotifyChange) {

            // Handle the notify change setup request
            procNTTransactNotifyChange(smbPkt, parser);
            return;
        }

        // Create a transact buffer to hold the transaction parameter block and data block
        SrvTransactBuffer transBuf = null;

        if (parser.getTotalParameterCount() == parser.getParameterBlockCount()
                && parser.getTotalDataCount() == parser.getDataBlockCount()) {

            // Create a transact buffer using the packet buffer, the entire request is contained in a single packet
            transBuf = new SrvTransactBuffer(parser);
        }
        else {

            // Create a transact buffer to hold the multiple transact request parameter/data blocks
            transBuf = new SrvTransactBuffer(parser.getSetupCount(), parser.getTotalParameterCount(), parser.getTotalDataCount());
            transBuf.setType(parser.getCommand());
            transBuf.setFunction(subCmd);

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
                m_sess.debugPrintln("NT Transaction [" + treeId + "] transbuf=" + transBuf);

            // Append the setup, parameter and data blocks to the transaction data
            byte[] buf = parser.getBuffer();
            int cnt = parser.getSetupCount();

            if (cnt > 0)
                transBuf.appendSetup(buf, parser.getSetupOffset(), cnt * 2);

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
                m_sess.debugPrintln("NT Transaction [" + treeId + "] pcnt=" + parser.getNTParameter(4) + ", offset="
                        + parser.getNTParameter(5));

            cnt = parser.getParameterBlockCount();

            if (cnt > 0)
                transBuf.appendParameter(buf, parser.getParameterBlockOffset(), cnt);

            cnt = parser.getDataBlockCount();
            if (cnt > 0)
                transBuf.appendData(buf, parser.getDataBlockOffset(), cnt);
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
            m_sess.debugPrintln("NT Transaction [" + treeId + "] cmd=0x" + Integer.toHexString(subCmd) + ", multiPkt="
                    + transBuf.isMultiPacket());

        // Check for a multi-packet transaction, for a multi-packet transaction we just acknowledge the receive with an
        // empty response SMB
        if (transBuf.isMultiPacket()) {

            // Save the partial transaction data
            vc.setTransaction(transBuf);

            // Send an intermediate acknowedgement response
            m_sess.sendSuccessResponseSMB(smbPkt);
            return;
        }

        // Process the transaction buffer
        processNTTransactionBuffer(transBuf, smbPkt, parser);
    }

    /**
     * Process an NT transaction secondary packet
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procNTTransactionSecondary(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that we received enough parameters for a transact2 request
        if (parser.checkPacketIsValid(18, 0) == false) {

            // Not enough parameters for a valid transact2 request
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid connection id.
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Check if the transaction request is for the IPC$ pipe
        if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {
            IPCHandler.processIPCRequest(m_sess, smbPkt);
            return;
        }

        // Check if there is an active transaction, and it is an NT transaction
        if (vc.hasTransaction() == false || vc.getTransaction().isType() != PacketTypeV1.NTTransact) {

            // No NT transaction to continue, return an error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the existing transaction buffer
        byte[] buf = parser.getBuffer();
        SrvTransactBuffer transBuf = vc.getTransaction();

        // Append the parameter data to the transaction buffer, if any
        int plen = parser.getParameterBlockCount();

        if (plen > 0) {

            // Append the data to the parameter buffer
            DataBuffer paramBuf = transBuf.getParameterBuffer();
            paramBuf.appendData(buf, parser.getParameterBlockOffset(), plen);
        }

        // Append the data block to the transaction buffer, if any
        int dlen = parser.getDataBlockCount();

        if (dlen > 0) {

            // Append the data to the data buffer
            DataBuffer dataBuf = transBuf.getDataBuffer();
            dataBuf.appendData(buf, parser.getDataBlockOffset(), dlen);
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
            m_sess.debugPrintln("NT Transaction Secondary [" + treeId + "] paramLen=" + plen + ", dataLen=" + dlen);

        // Check if the transaction has been received or there are more sections to be received
        int totParam = parser.getTotalParameterCount();
        int totData = parser.getTotalDataCount();

        int paramDisp = parser.getParameterBlockDisplacement();
        int dataDisp = parser.getDataBlockDisplacement();

        if ((paramDisp + plen) == totParam && (dataDisp + dlen) == totData) {

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
                m_sess.debugPrintln("NT Transaction complete, processing ...");

            // Clear the in progress transaction
            vc.setTransaction(null);

            // Process the transaction
            processNTTransactionBuffer(transBuf, smbPkt, parser);
        }

        // No response is sent for a transaction secondary
    }

    /**
     * Process an NT transaction buffer
     *
     * @param tbuf   TransactBuffer
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    private final void processNTTransactionBuffer(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Process the NT transaction buffer
        switch (tbuf.getFunction()) {

            // Create file/directory
            case PacketTypeV1.NTTransCreate:
                procNTTransactCreate(tbuf, smbPkt, parser);
                break;

            // I/O control
            case PacketTypeV1.NTTransIOCtl:
                procNTTransactIOCtl(tbuf, smbPkt, parser);
                break;

            // Query security descriptor
            case PacketTypeV1.NTTransQuerySecurityDesc:
                procNTTransactQuerySecurityDesc(tbuf, smbPkt, parser);
                break;

            // Set security descriptor
            case PacketTypeV1.NTTransSetSecurityDesc:
                procNTTransactSetSecurityDesc(tbuf, smbPkt, parser);
                break;

            // Rename file/directory via handle
            case PacketTypeV1.NTTransRename:
                procNTTransactRename(tbuf, smbPkt, parser);
                break;

            // Get user quota
            case PacketTypeV1.NTTransGetUserQuota:

                // DEBUG
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
                    m_sess.debugPrintln("NT GetUserQuota transaction");

                // Return a not implemented error status
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNotImplemented, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
                break;

            // Set user quota
            case PacketTypeV1.NTTransSetUserQuota:

                // DEBUG
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
                    m_sess.debugPrintln("NT SetUserQuota transaction");

                // Return a not implemented error status
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNotImplemented, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
                break;

            // Unknown NT transaction command
            default:

                // Return an unrecognized command error
                if (Debug.EnableError)
                    m_sess.debugPrintln("NT Error unknown NT transact command = 0x" + Integer.toHexString(tbuf.isType()));
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
                break;
        }
    }

    /**
     * Process an NT create file/directory transaction
     *
     * @param tbuf   TransactBuffer
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procNTTransactCreate(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
            m_sess.debugPrintln("NT TransactCreate");

        // Check that the received packet looks like a valid NT create transaction
        if (tbuf.hasParameterBuffer() && tbuf.getParameterBuffer().getLength() < 52) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = tbuf.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasWriteAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // If the connection is not a disk share then return an error.
        if (conn.getSharedDevice().getType() != ShareType.DISK) {

            // Return an access denied error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Extract the file create parameters
        DataBuffer tparams = tbuf.getParameterBuffer();

        int flags = tparams.getInt();
        int rootFID = tparams.getInt();
        int accessMask = tparams.getInt();
        long allocSize = tparams.getLong();
        int attrib = tparams.getInt();
        SharingMode shrAccess = SharingMode.fromInt(tparams.getInt());
        CreateDisposition createDisp = CreateDisposition.fromInt(tparams.getInt());
        int createOptn = tparams.getInt();
        int sdLen = tparams.getInt();
        int eaLen = tparams.getInt();
        int nameLen = tparams.getInt();
        ImpersonationLevel impersonLev = ImpersonationLevel.fromInt(tparams.getInt());
        int secFlags = tparams.getByte();

        // Extract the filename string
        tparams.wordAlign();
        String fileName = tparams.getString(nameLen, true);

        if (fileName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Access the disk interface that is associated with the shared device
        DiskInterface disk = null;
        try {

            // Get the disk interface for the share
            disk = (DiskInterface) conn.getSharedDevice().getInterface();
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Check if the file name contains a file stream name. If the disk interface does not
        // implement the optional NTFS streams interface then return an error status, not supported.
        if (fileName.indexOf(FileOpenParams.StreamSeparator) != -1) {

            // Check if the driver implements the NTFS streams interface and it is enabled
            boolean streams = false;

            if (disk instanceof NTFSStreamsInterface) {

                // Check if streams are enabled
                NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) disk;
                streams = ntfsStreams.hasStreamsEnabled(m_sess, conn);
            }

            // Check if streams are enabled/available
            if (streams == false) {

                // Return a file not found error
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
                return;
            }
        }

        // Create the file open parameters to be passed to the disk interface
        FileOpenParams params = new FileOpenParams(fileName, createDisp, accessMask, attrib, shrAccess, allocSize, createOptn,
                rootFID, impersonLev, secFlags, parser.getProcessIdFull());

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE)) {
            m_sess.debugPrintln("NT TransactCreate [" + treeId + "] params=" + params);
            m_sess.debugPrintln("  secDescLen=" + sdLen + ", extAttribLen=" + eaLen);
        }

        // Access the disk interface and open/create the requested file
        int fid;
        NetworkFile netFile = null;
        int respAction = 0;

        try {

            // Check if the requested file already exists
            FileStatus fileSts = disk.fileExists(m_sess, conn, fileName);

            if (fileSts == FileStatus.NotExist) {

                // Check if the file should be created if it does not exist
                if (createDisp == CreateDisposition.CREATE || createDisp == CreateDisposition.OPEN_IF
                        || createDisp == CreateDisposition.OVERWRITE_IF || createDisp == CreateDisposition.SUPERSEDE) {

                    // Check if a new file or directory should be created
                    if ((createOptn & WinNT.CreateDirectory) == 0) {

                        // Create a new file
                        netFile = disk.createFile(m_sess, conn, params);
                    }
                    else {

                        // Create a new directory and open it
                        disk.createDirectory(m_sess, conn, params);
                        netFile = disk.openFile(m_sess, conn, params);
                    }

                    // Indicate that the file did not exist and was created
                    respAction = FileAction.FileCreated;
                }
                else {

                    // Check if the path is a directory
                    if (fileSts == FileStatus.DirectoryExists) {

                        // Return an access denied error
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameCollision, SMBStatus.DOSFileAlreadyExists,
                                SMBStatus.ErrDos);
                        return;
                    }
                    else {

                        // Return a file not found error
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
                        return;
                    }
                }
            }
            else if (createDisp == CreateDisposition.CREATE) {

                // Check for a file or directory
                if (fileSts == FileStatus.FileExists || fileSts == FileStatus.DirectoryExists) {

                    // Return a file exists error
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameCollision, SMBStatus.DOSFileAlreadyExists, SMBStatus.ErrDos);
                    return;
                }
                else {

                    // Return an access denied exception
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
                    return;
                }
            }
            else {

                // Open the requested file/directory
                netFile = disk.openFile(m_sess, conn, params);

                // Check if the file should be truncated
                if (createDisp == CreateDisposition.SUPERSEDE || createDisp == CreateDisposition.OVERWRITE_IF) {

                    // Truncate the file
                    disk.truncateFile(m_sess, conn, netFile, 0L);

                    // Debug
                    if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
                        m_sess.debugPrintln("  [" + treeId + "] name=" + fileName + " truncated");
                }

                // Set the file action response
                respAction = FileAction.FileExisted;
            }

            // Add the file to the list of open files for this tree connection
            fid = conn.addFile(netFile, getSession());
        }
        catch (TooManyFilesException ex) {

            // Too many files are open on this connection, cannot open any more files.
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTTooManyOpenFiles, SMBStatus.DOSTooManyOpenFiles, SMBStatus.ErrDos);
            return;
        }
        catch (AccessDeniedException ex) {

            // Return an access denied error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (FileExistsException ex) {

            // File/directory already exists
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameCollision, SMBStatus.DOSFileAlreadyExists, SMBStatus.ErrDos);
            return;
        }
        catch (FileSharingException ex) {

            // Return a sharing violation error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTSharingViolation, SMBStatus.DOSFileSharingConflict, SMBStatus.ErrDos);
            return;
        }
        catch (FileOfflineException ex) {

            // File data is unavailable
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTFileOffline, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
            return;
        }
        catch (DiskOfflineException ex) {

            // Filesystem is offline
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
            return;
        }
        catch (IOException ex) {

            // Failed to open the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }

        // Build the NT transaction create response
        DataBuffer prms = new DataBuffer(128);

        // If an oplock was requested indicate it was granted, for now
        if ((flags & WinNT.RequestBatchOplock) != 0) {

            // Batch oplock granted
            prms.putByte(2);
        }
        else if ((flags & WinNT.RequestExclusiveOplock) != 0) {

            // Exclusive oplock granted
            prms.putByte(1);
        }
        else {

            // No oplock granted
            prms.putByte(0);
        }
        prms.putByte(0); // alignment

        // Pack the file id
        prms.putShort(fid);
        prms.putInt(respAction);

        // EA error offset
        prms.putInt(0);

        // Pack the file/directory dates
        if (netFile.hasCreationDate())
            prms.putLong(NTTime.toNTTime(netFile.getCreationDate()));
        else
            prms.putLong(0);

        if (netFile.hasModifyDate()) {
            long modDate = NTTime.toNTTime(netFile.getModifyDate());
            prms.putLong(modDate);
            prms.putLong(modDate);
            prms.putLong(modDate);
        }
        else {
            prms.putLong(0); // Last access time
            prms.putLong(0); // Last write time
            prms.putLong(0); // Change time
        }

        prms.putInt(netFile.getFileAttributes());

        // Pack the file size/allocation size
        prms.putLong(netFile.getFileSize()); // Allocation size
        prms.putLong(netFile.getFileSize()); // End of file
        prms.putShort(0); // File type - disk file
        prms.putShort(0); // Device state
        prms.putByte(netFile.isDirectory() ? 1 : 0);

        // Initialize the transaction response
        parser.initTransactReply(prms.getBuffer(), prms.getLength(), null, 0);

        // Send back the response
        m_sess.sendResponseSMB(smbPkt);

        // Check if there are any file/directory change notify requests active
        DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();
        if (diskCtx.hasFileServerNotifications() && respAction == FileAction.FileCreated) {

            // Check if a file or directory has been created
            if (netFile.isDirectory())
                diskCtx.getChangeHandler().notifyDirectoryChanged(NotifyAction.Added, fileName);
            else
                diskCtx.getChangeHandler().notifyFileChanged(NotifyAction.Added, fileName);
        }
    }

    /**
     * Process an NT I/O control transaction
     *
     * @param tbuf   TransactBuffer
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procNTTransactIOCtl(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = tbuf.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Unpack the request details
        DataBuffer setupBuf = tbuf.getSetupBuffer();

        int ctrlCode = setupBuf.getInt();
        int fid = setupBuf.getShort();
        boolean fsctrl = setupBuf.getByte() == 1 ? true : false;
        int filter = setupBuf.getByte();

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
            m_sess.debugPrintln("NT IOCtl code=" + NTIOCtl.asString(ctrlCode) + ", fid=" + fid + ", fsctrl=" + fsctrl
                    + ", filter=" + filter);

        // Access the disk interface that is associated with the shared device
        DiskInterface disk = null;
        try {

            // Get the disk interface for the share
            disk = (DiskInterface) conn.getSharedDevice().getInterface();
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Check if the disk interface implements the optional IO control interface
        if (disk instanceof IOCtlInterface) {

            // Access the IO control interface
            IOCtlInterface ioControl = (IOCtlInterface) disk;
            SMBSrvPacket respPkt = smbPkt;

            try {

                // Pass the request to the IO control interface for processing
                DataBuffer response = ioControl.processIOControl(m_sess, conn, ctrlCode, fid, tbuf.getDataBuffer(), fsctrl, filter);

                // Pack the response
                if (response != null) {

                    // Check if a larger buffer needs to be allocated for the response packet
                    int respPktLen = parser.calculateResponseLength(0, response.getLength(), 1);

                    if (parser.getBufferLength() < respPktLen) {

                        // Allocate a larger response packet
                        SMBSrvPacket pkt = m_sess.getPacketPool().allocatePacket(respPktLen, smbPkt, parser.getLength());

                        // Create a new packet from the new buffer
                        respPkt = new SMBSrvPacket(pkt.getBuffer());

                        // Create a parser for the response
                        respPkt.setParser( SMBSrvPacket.Version.V1);
                        parser = (SMBV1Parser) respPkt.getParser();
                    }

                    // Pack the response data block
                    parser.initTransactReply(null, 0, response.getBuffer(), response.getLength(), 1);
                    parser.setSetupParameter(0, response.getLength());
                }
                else {

                    // Pack an empty response data block
                    parser.initTransactReply(null, 0, null, 0, 1);
                    parser.setSetupParameter(0, 0);
                }
            }
            catch (IOControlNotImplementedException ex) {

                // Return a not implemented error status
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNotImplemented, SMBStatus.SRVInternalServerError, SMBStatus.ErrSrv);
                return;
            }
            catch (SMBException ex) {

                // Return the specified SMB status, this should be an NT status code
                m_sess.sendErrorResponseSMB(smbPkt, ex.getErrorCode(), SMBStatus.SRVInternalServerError, SMBStatus.ErrSrv);
                return;
            }

            // Send the IOCtl response
            m_sess.sendResponseSMB(respPkt);
        }
        else {

            // Send back an error, IOctl not supported
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNotImplemented, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
        }
    }

    /**
     * Process an NT query security descriptor transaction
     *
     * @param tbuf   TransactBuffer
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procNTTransactQuerySecurityDesc(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = tbuf.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Unpack the request details
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int fid = paramBuf.getShort();
        int flags = paramBuf.getShort();

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
            m_sess.debugPrintln("NT QuerySecurityDesc fid=" + fid + ", flags=" + flags);

        // Get the file details
        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Access the disk interface that is associated with the shared device
        DiskInterface disk = null;
        try {

            // Get the disk interface for the share
            disk = (DiskInterface) conn.getSharedDevice().getInterface();
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Check if the disk interface implements the optional security descriptor interface
        SMBSrvPacket respPkt = smbPkt;

        if (disk instanceof SecurityDescriptorInterface) {

            // Access the security descriptor interface
            SecurityDescriptorInterface secDescInterface = (SecurityDescriptorInterface) disk;

            // Check if this is a buffer length check, if so the maximum returned data count will be zero
            if (tbuf.getReturnDataLimit() == 0) {

                // Get the security descriptor length
                int secDescLen = secDescInterface.getSecurityDescriptorLength(m_sess, conn, netFile);

                // Return the security descriptor length in the parameter block
                byte[] paramblk = new byte[4];
                DataPacker.putIntelInt(secDescLen, paramblk, 0);

                // Initialize the transaction reply
                parser.initTransactReply(paramblk, paramblk.length, null, 0);

                // Set a warning status to indicate the supplied data buffer was too small to return the security descriptor
                parser.setLongErrorCode(SMBStatus.NTBufferTooSmall);
            }
            else {

                // Get the security descriptor for the file
                SecurityDescriptor secDesc = secDescInterface.loadSecurityDescriptor(m_sess, conn, netFile);

                byte[] secBuf = null;
                int secLen = 0;
                byte[] paramblk = new byte[4];

                if (secDesc != null) {

                    // Pack the security descriptor
                    DataBuffer buf = new DataBuffer(4096);

                    try {
                        secLen = secDesc.saveDescriptor(buf);
                        secBuf = buf.getBuffer();
                    }
                    catch (SaveException ex) {
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
                        return;
                    }

                    // Calculate the available space for the security descriptor in the current packet
                    parser.initTransactReply(paramblk, paramblk.length, null, 0);
                    int availLen = respPkt.getBufferLength() - parser.getLength();

                    if (availLen <= (secLen + 8)) {

                        // Allocate a larger packet for the response
                        SMBSrvPacket pkt = m_sess.getPacketPool().allocatePacket(parser.getLength() + secLen + 8, smbPkt);

                        // Create a new packet from the new buffer
                        respPkt = new SMBSrvPacket(pkt.getBuffer());

                        // Create a parser for the response
                        respPkt.setParser( SMBSrvPacket.Version.V1);
                        parser = (SMBV1Parser) respPkt.getParser();
                    }
                }

                // Return the security descriptor length in the parameter block
                DataPacker.putIntelInt(secLen, paramblk, 0);

                // Initialize the transaction reply.
                parser.initTransactReply(paramblk, paramblk.length, secBuf, secLen);
            }
        }
        else {

            // Check if this is a buffer length check, if so the maximum returned data count will be zero
            if (tbuf.getReturnDataLimit() == 0) {

                // Return the security descriptor length in the parameter block
                byte[] paramblk = new byte[4];
                DataPacker.putIntelInt(_sdEveryOne.length, paramblk, 0);

                // Initialize the transaction reply
                parser.initTransactReply(paramblk, paramblk.length, null, 0);

                // Set a warning status to indicate the supplied data buffer was too small to return
                // the security descriptor
                parser.setLongErrorCode(SMBStatus.NTBufferTooSmall);
            }
            else {

                // Return the security descriptor length in the parameter block
                byte[] paramblk = new byte[4];
                DataPacker.putIntelInt(_sdEveryOne.length, paramblk, 0);

                // Initialize the transaction reply. Return the fixed security descriptor that allows anyone to
                // access the file/directory
                parser.initTransactReply(paramblk, paramblk.length, _sdEveryOne, _sdEveryOne.length);
            }
        }

        // Send back the response
        m_sess.sendResponseSMB(respPkt);
    }

    /**
     * Process an NT set security descriptor transaction
     *
     * @param tbuf   TransactBuffer
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procNTTransactSetSecurityDesc(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Unpack the request details
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = tbuf.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasWriteAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the file details
        int fid = paramBuf.getShort();
        paramBuf.skipBytes(2);
        int flags = paramBuf.getInt();

        // Unpack the security descriptor
        DataBuffer dataBuf = tbuf.getDataBuffer();
        SecurityDescriptor secDesc = new SecurityDescriptor();

        try {
            secDesc.loadDescriptor(dataBuf.getBuffer(), dataBuf.getOffset());
        }
        catch (LoadException ex) {

            // Invalid security descriptor
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN)) {
            m_sess.debugPrintln("NT SetSecurityDesc fid=" + fid + ", flags=" + flags);
            m_sess.debugPrintln("   sd=" + secDesc);
        }

        // Access the disk interface that is associated with the shared device
        DiskInterface disk = null;
        try {

            // Get the disk interface for the share
            disk = (DiskInterface) conn.getSharedDevice().getInterface();
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Check if the disk interface implements the optional security descriptor interface
        if (disk instanceof SecurityDescriptorInterface) {

            // Access the security descriptor interface
            SecurityDescriptorInterface secDescInterface = (SecurityDescriptorInterface) disk;

            // Get the file details
            NetworkFile netFile = conn.findFile(fid);

            if (netFile == null) {
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
                return;
            }

            // Save the security descriptor
            secDescInterface.saveSecurityDescriptor(m_sess, conn, netFile, secDesc);

            // Return a success status
            parser.initTransactReply(null, 0, null, 0);
            parser.setError(SMBStatus.Success, SMBStatus.Success);
            m_sess.sendResponseSMB(smbPkt);
        }
        else {

            // Return a success response
            m_sess.sendSuccessResponseSMB(smbPkt);
        }
    }

    /**
     * Process an NT change notification transaction
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procNTTransactNotifyChange(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Make sure the tree connection is for a disk device
        if (conn.getContext() == null || conn.getContext() instanceof DiskDeviceContext == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Check if the device has change notification enabled
        DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();
        if (diskCtx.hasChangeHandler() == false) {

            // Return an error status, share does not have change notification enabled
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNotImplemented, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Unpack the request details
        parser.resetSetupPointer();

        Set<NotifyChange> filter = NotifyChange.setFromInt(parser.unpackInt());
        int fid = parser.unpackWord();
        boolean watchTree = parser.unpackByte() == 1 ? true : false;
        int mid = parser.getMultiplexId();

        // Get the file details
        NetworkFile dir = conn.findFile(fid);
        if (dir == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the maximum notifications to buffer whilst waiting for the request to be reset after
        // a notification has been triggered
        int maxQueue = 0;

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_NOTIFY))
            m_sess.debugPrintln("NT NotifyChange fid=" + fid + ", mid=" + mid + ", filter=" + filter + ", dir=" + dir.getFullName()
                                + ", maxQueue=" + maxQueue);

        // Check if there is an existing request in the notify list that matches the new request and
        // is in a completed state. If so then the client is resetting the notify request so reuse the existing
        // request.
        NotifyRequest req = m_sess.findNotifyRequest(dir, filter, watchTree);

        if (req != null && req.isCompleted()) {

            // Reset the existing request with the new multiplex id
            req.setId(mid);
            req.setCompleted(false);

            // Check if there are any buffered notifications for this session
            if (req.hasBufferedEvents() || req.hasNotifyEnum()) {

                // Get the buffered events from the request, clear the list from the request
                NotifyChangeEventList bufList = req.getBufferedEventList();
                req.clearBufferedEvents();

                // Send the buffered events
                diskCtx.getChangeHandler().sendBufferedNotifications(req, bufList);

                // DEBUG
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_NOTIFY)) {
                    if (bufList == null)
                        m_sess.debugPrintln("   Sent buffered notifications, req=" + req.toString() + ", Enum");
                    else
                        m_sess.debugPrintln("   Sent buffered notifications, req=" + req.toString() + ", count="
                                + bufList.numberOfEvents());
                }
            }
            else {

                // DEBUG
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_NOTIFY))
                    m_sess.debugPrintln("   Reset notify request, " + req.toString());
            }
        }
        else {

            // Create a change notification request
            req = new NotifyRequest(filter, watchTree, m_sess, dir, mid, parser.getTreeId(), parser.getProcessId(), parser.getUserId(), maxQueue);

            // Add the request to the pending notify change lists
            m_sess.addNotifyRequest(req, diskCtx);

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_NOTIFY)) {
                m_sess.debugPrintln("   Added new request, " + req.toString());
                m_sess.debugPrintln("   Global notify mask="
                        + diskCtx.getChangeHandler().getGlobalNotifyMask() + ", reqQueue="
                        + diskCtx.getChangeHandler().getRequestQueueSize());
            }
        }

        // NOTE: If the change notification request is accepted then no reply is sent to the client.
        // A reply will be sent asynchronously if the change notification is triggered.
    }

    /**
     * Process an NT rename via handle transaction
     *
     * @param tbuf   TransactBuffer
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procNTTransactRename(SrvTransactBuffer tbuf, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());

        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        int treeId = tbuf.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasWriteAccess() == false) {

            // User does not have the required access rights

            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
            m_sess.debugPrintln("NT TransactRename");

        // Send back an error, NT rename not supported
        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
    }

    /**
     * Build a change notification response for the specified change event
     *
     * @param evt NotifyChangeEvent
     * @param req NotifyRequest
     * @return SMBSrvPacket
     */
    public SMBSrvPacket buildChangeNotificationResponse(NotifyChangeEvent evt, NotifyRequest req) {

        //	Allocate the NT transaction packet to send the asynchronous notification
        SMBSrvPacket smbPkt = new SMBSrvPacket();

        // Create a parser for the packet
        smbPkt.setParser( SMBSrvPacket.Version.V1);
        SMBV1Parser parser = (SMBV1Parser) smbPkt.getParser();

        //	Build the change notification response SMB
        parser.setParameterCount(18);
        parser.resetBytePointerAlign();

        int pos = parser.getPosition();
        parser.setNTParameter(1, 0);                //	total data count
        parser.setNTParameter(3, pos - 4);        //	offset to parameter block

        parser.setCommand(PacketTypeV1.NTTransact);
        parser.setLongErrorCode(0);

        parser.setFlags(SMBV1.FLG_CANONICAL + SMBV1.FLG_CASELESS);
        parser.setFlags2(SMBV1.FLG2_UNICODE + SMBV1.FLG2_LONGERRORCODE);

        //	Set the response for the current notify request
        parser.setMultiplexId(req.getIdAsInt());
        parser.setTreeId(req.getTreeId());
        parser.setUserId(req.getUserId());
        parser.setProcessId(req.getProcessId());

        // Check if there are notify events or this is a request to the client to enumerate the folder
        if ( req.hasNotifyEnum() == false) {

            //	Get the path for the event
            String relName = evt.getFileName();
            if (relName == null)
                relName = evt.getShortFileName();

            //	DEBUG
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_NOTIFY))
                m_sess.debugPrintln("  Notify evtPath=" + evt.getFileName() + ", MID=" + req.getId() + ", reqPath=" + req.getWatchPath() + ", relative=" + relName);

            //	Pack the notification structure
            parser.packInt(0);                        //	offset to next structure
            parser.packInt(evt.getAction().intValue());            //	action
            parser.packInt(relName.length() * 2);    //	file name length
            parser.packString(relName, true, false);

            //	Check if the event is a file/directory rename, if so then add the old file/directory details
            if (evt.getAction() == NotifyAction.RenamedNewName &&
                    evt.hasOldFileName()) {

                //	Set the offset from the first structure to this structure
                int newPos = DataPacker.longwordAlign(parser.getPosition());
                DataPacker.putIntelInt(newPos - pos, parser.getBuffer(), pos);

                //	Get the old file name
                relName = FileName.makeRelativePath(req.getWatchPath(), evt.getOldFileName());
                if (relName == null)
                    relName = evt.getOldFileName();

                //	Add the old file/directory name details
                parser.packInt(0);                                    //	offset to next structure
                parser.packInt(NotifyAction.RenamedOldName.intValue());
                parser.packInt(relName.length() * 2);                //	file name length
                parser.packString(relName, true, false);
            }

            //	Calculate the parameter block length, longword align the buffer position
            int prmLen = parser.getPosition() - pos;
            parser.alignBytePointer();
            pos = (pos + 3) & 0xFFFFFFFC;

            //	Set the parameter block length
            parser.setNTParameter(0, prmLen);        //	total parameter block count
            parser.setNTParameter(2, prmLen);        //	parameter block count for this packet
            parser.setNTParameter(6, parser.getPosition() - 4); //	data block offset
            parser.setByteCount();
        }
        else {

            // Return a status code to indicate that a folder search is required
            parser.setLongErrorCode(SMBStatus.NTNotifyEnumDir);
        }

        //	DEBUG
//		parser.DumpPacket();

        // Return the change notification response packet
        return smbPkt;
    }

    /**
     * Build an oplock break asynchronous response, sent from the server to the client
     *
     * @param oplock LocalOpLockDetails
     * @return SMBSrvPacket
     */
    public SMBSrvPacket buildOpLockBreakResponse(LocalOpLockDetails oplock) {

        // Get the oplock owner details
        SMBV1OplockOwner oplockOwner = (SMBV1OplockOwner) oplock.getOplockOwner();

        if ( oplockOwner == null)
            return null;

        // Allocate a packet for the oplock break request to be sent on the owner client session
        SMBSrvPacket opBreakPkt = new SMBSrvPacket(128);

        // Set the associated parser
        opBreakPkt.setParser( SMBSrvPacket.Version.V1);
        SMBV1Parser parser = (SMBV1Parser) opBreakPkt.getParser();

        // Build the oplock break request
        opBreakPkt.clearHeader();

        parser.setCommand(PacketTypeV1.LockingAndX);

        parser.setFlags(0);
        parser.setFlags2(0);

        parser.setTreeId(oplockOwner.getTreeId());
        parser.setProcessId(0xFFFF);
        parser.setUserId(0);
        parser.setMultiplexId(0xFFFF);

        parser.setParameterCount(8);
        parser.setAndXCommand(PacketTypeV1.NoChainedCommand);
        parser.setParameter(1, 0);                            // AndX offset
        parser.setParameter(2, oplockOwner.getFileId());           // FID
        parser.setParameter(3, LockingAndX.OplockBreak + LockingAndX.Level2OpLock);
        parser.setParameterLong(4, 0);                        // timeout
        parser.setParameter(6, 0);                            // number of unlocks
        parser.setParameter(7, 0);                            // number of locks

        parser.setByteCount(0);

        // Mark the packet as a request packet
        parser.setRequestPacket(true);

        // Return the oplock break packet
        return opBreakPkt;
    }


    /**
     * Run the NT SMB protocol handler to process the received SMB packet
     *
     * @param smbPkt SMBSrvPacket
     * @return boolean true if the packet was processed, else false
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     * @exception TooManyConnectionsException No more connections available
     */
    public boolean runProtocol(SMBSrvPacket smbPkt)
            throws java.io.IOException, SMBSrvException, TooManyConnectionsException {

        // Check if the received packet has a valid SMB v1 signature
        if (smbPkt.isSMB1() == false)
            throw new IOException("Invalid SMB signature");

        // Get the SMB parser
        SMBV1Parser parser = (SMBV1Parser) smbPkt.getParser();

        if ( parser == null)
            throw new IOException( "SMB packet does not have a parser");

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_STATE) &&
                parser.hasChainedCommand())
            m_sess.debugPrintln("AndX Command = 0x" + Integer.toHexString(parser.getAndXCommand()));

        // Reset the byte unpack offset
        parser.resetBytePointer();

        // Set the process id from the received packet, this can change for the same session and
        // needs to be set for lock ownership checking
        //
        // TODO: Need to remove this
        m_sess.setProcessId(parser.getProcessId());

        // Determine the SMB command type
        boolean handledOK = true;

        switch (parser.getCommand()) {

            // NT Session setup
            case PacketTypeV1.SessionSetupAndX:
                procSessionSetup(smbPkt, parser);
                break;

            // Tree connect
            case PacketTypeV1.TreeConnectAndX:
                procTreeConnectAndX(smbPkt, parser);
                break;

            // Transaction/transaction2
            case PacketTypeV1.Transaction:
            case PacketTypeV1.Transaction2:
                procTransact2(smbPkt, parser);
                break;

            // Transaction/transaction2 secondary
            case PacketTypeV1.TransactionSecond:
            case PacketTypeV1.Transaction2Second:
                procTransact2Secondary(smbPkt, parser);
                break;

            // Close a search started via the FindFirst transaction2 command
            case PacketTypeV1.FindClose2:
                procFindClose(smbPkt, parser);
                break;

            // Open a file
            case PacketTypeV1.OpenAndX:
                procOpenAndX(smbPkt, parser);
                break;

            // Close a file
            case PacketTypeV1.CloseFile:
                procCloseFile(smbPkt, parser);
                break;

            // Read a file
            case PacketTypeV1.ReadAndX:
                procReadAndX(smbPkt, parser);
                break;

            // Write to a file
            case PacketTypeV1.WriteAndX:
                procWriteAndX(smbPkt, parser);
                break;

            // Rename file
            case PacketTypeV1.RenameFile:
                procRenameFile(smbPkt, parser);
                break;

            // Delete file
            case PacketTypeV1.DeleteFile:
                procDeleteFile(smbPkt, parser);
                break;

            // Delete directory
            case PacketTypeV1.DeleteDirectory:
                procDeleteDirectory(smbPkt, parser);
                break;

            // Tree disconnect
            case PacketTypeV1.TreeDisconnect:
                procTreeDisconnect(smbPkt, parser);
                break;

            // Lock/unlock regions of a file
            case PacketTypeV1.LockingAndX:
                procLockingAndX(smbPkt, parser);
                break;

            // Logoff a user
            case PacketTypeV1.LogoffAndX:
                procLogoffAndX(smbPkt, parser);
                break;

            // NT Create/open file
            case PacketTypeV1.NTCreateAndX:
                procNTCreateAndX(smbPkt, parser);
                break;

            // Tree connection (without AndX batching)
            case PacketTypeV1.TreeConnect:
                super.runProtocol(smbPkt);
                break;

            // NT cancel
            case PacketTypeV1.NTCancel:
                procNTCancel(smbPkt, parser);
                break;

            // NT transaction
            case PacketTypeV1.NTTransact:
                procNTTransaction(smbPkt, parser);
                break;

            // NT transaction secondary
            case PacketTypeV1.NTTransactSecond:
                procNTTransactionSecondary(smbPkt, parser);
                break;

            // Echo request
            case PacketTypeV1.Echo:
                super.procEcho(smbPkt, parser);
                break;

            // Default
            default:

                // Get the tree connection details, if it is a disk or printer type connection then pass the request to
                // the core protocol handler
                int treeId = parser.getTreeId();
                TreeConnection conn = null;
                if (treeId != -1)
                    conn = m_sess.findTreeConnection(smbPkt);

                if (conn != null) {

                    // Check if this is a disk or print connection, if so then send the request to the core protocol handler
                    if (conn.getSharedDevice().getType() == ShareType.DISK || conn.getSharedDevice().getType() == ShareType.PRINTER) {

                        // Chain to the core protocol handler
                        handledOK = super.runProtocol(smbPkt);
                    }
                    else if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {

                        // Send the request to IPC$ remote admin handler
                        IPCHandler.processIPCRequest(m_sess, smbPkt);
                        handledOK = true;
                    }
                }
                else {

                    // Need to send a response or the client may hang
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVInvalidTID, SMBStatus.ErrSrv);
                }
                break;
        }

        // Run any request post processors
        runRequestPostProcessors(m_sess);

        // Return the handled status
        return handledOK;
    }
}
