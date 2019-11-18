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

import java.io.FileNotFoundException;
import java.io.IOException;

import org.filesys.debug.Debug;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.server.auth.ClientInfo;
import org.filesys.server.auth.ISMBAuthenticator;
import org.filesys.server.auth.InvalidUserException;
import org.filesys.server.config.GlobalConfigSection;
import org.filesys.server.core.InvalidDeviceInterfaceException;
import org.filesys.server.core.ShareType;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.filesys.*;
import org.filesys.smb.*;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.server.ntfs.NTFSStreamsInterface;
import org.filesys.smb.server.ntfs.StreamInfoList;
import org.filesys.util.DataBuffer;
import org.filesys.util.DataPacker;
import org.filesys.util.WildCard;

/**
 * LanMan SMB Protocol Handler Class.
 *
 * <p>
 * The LanMan protocol handler processes the additional SMBs that were added to the protocol in the
 * LanMan1 and LanMan2 SMB dialects.
 *
 * @author gkspencer
 */
class LanManProtocolHandler extends CoreProtocolHandler {

    // Locking type flags
    protected static final int LockShared           = 0x01;
    protected static final int LockOplockRelease    = 0x02;
    protected static final int LockChangeType       = 0x04;
    protected static final int LockCancel           = 0x08;
    protected static final int LockLargeFiles       = 0x10;

    // Dummy date/time for dot files
    public static final long DotFileDateTime = System.currentTimeMillis();

    /**
     * LanManProtocolHandler constructor.
     */
    protected LanManProtocolHandler() {
        super();
    }

    /**
     * LanManProtocolHandler constructor.
     *
     * @param sess SMBSrvSession
     */
    protected LanManProtocolHandler(SMBSrvSession sess) {
        super(sess);
    }

    /**
     * Return the protocol name
     *
     * @return String
     */
    public String getName() {
        return "LanMan";
    }

    /**
     * Process the chained SMB commands (AndX).
     *
     * @param smbPkt SMBSrvPacket
     * @param file   Current file , or null if no file context in chain
     * @return New offset to the end of the reply packet
     */
    protected final int procAndXCommands(SMBSrvPacket smbPkt, NetworkFile file) {

        // Get the response packet
        SMBSrvPacket respPkt = smbPkt.getAssociatedPacket();
        if (respPkt == null)
            throw new RuntimeException("No response packet allocated for AndX request");

        // Get the parser
        SMBV1Parser parser = (SMBV1Parser) smbPkt.getParser();

        // Get the chained command and command block offset
        int andxCmd = parser.getAndXCommand();
        int andxOff = parser.getParameter(1) + RFCNetBIOSProtocol.HEADER_LEN;

        // Set the parser for the response
        respPkt.setParser( SMBSrvPacket.Version.V1);
        SMBV1Parser respParser = (SMBV1Parser) respPkt.getParser();

        // Set the initial chained command and offset
        respParser.setAndXCommand(andxCmd);
        respParser.setParameter(1, andxOff - RFCNetBIOSProtocol.HEADER_LEN);

        // Pointer to the last parameter block, starts with the main command parameter block
        int paramBlk = SMBV1.WORDCNT;

        // Get the current end of the reply packet offset
        int endOfPkt = respParser.getByteOffset() + respParser.getByteCount();
        boolean andxErr = false;

        while (andxCmd != SMBV1.NO_ANDX_CMD && andxErr == false) {

            // Determine the chained command type
            int prevEndOfPkt = endOfPkt;

            switch (andxCmd) {

                // Tree connect
                case PacketTypeV1.TreeConnectAndX:
                    endOfPkt = procChainedTreeConnectAndX(andxOff, smbPkt, respPkt, endOfPkt);
                    break;

                // Close file
                case PacketTypeV1.CloseFile:
                    endOfPkt = procChainedClose(andxOff, smbPkt, respPkt, endOfPkt);
                    break;

                // Read file
                case PacketTypeV1.ReadAndX:
                    endOfPkt = procChainedReadAndX(andxOff, smbPkt, respPkt, endOfPkt, file);
                    break;
            }

            // Advance to the next chained command block
            andxCmd = parser.getAndXParameter(andxOff, 0) & 0x00FF;
            andxOff = parser.getAndXParameter(andxOff, 1);

            // Set the next chained command details in the current parameter block
            respParser.setAndXCommand(prevEndOfPkt, andxCmd);
            respParser.setAndXParameter(paramBlk, 1, prevEndOfPkt - RFCNetBIOSProtocol.HEADER_LEN);

            // Advance the current parameter block
            paramBlk = prevEndOfPkt;

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
     * @param cmdOff  int Offset to the chained command within the request packet
     * @param smbPkt  Request packet
     * @param respPkt SMBSrvPacket Reply packet
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
            respParser.setError( reqParser.isLongErrorCode(), SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError,
                    SMBStatus.ErrSrv);
            return endOff;
        }

        // Get the data bytes position and length
        int dataPos = reqParser.getAndXByteOffset(cmdOff);
        int dataLen = reqParser.getAndXByteCount(cmdOff);
        byte[] buf = reqParser.getBuffer();

        // Extract the password string
        String pwd = null;

        if (pwdLen > 0) {
            pwd = new String(buf, dataPos, pwdLen);
            dataPos += pwdLen;
            dataLen -= pwdLen;
        }

        // Extract the requested share name, as a UNC path
        String uncPath = DataPacker.getString(buf, dataPos, dataLen);
        if (uncPath == null) {
            respParser.setError(SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return endOff;
        }

        // Extract the service type string
        dataPos += uncPath.length() + 1; // null terminated
        dataLen -= uncPath.length() + 1; // null terminated

        String service = DataPacker.getString(buf, dataPos, dataLen);
        if (service == null) {
            respParser.setError(SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return endOff;
        }

        // Convert the service type to a shared device type, client may specify '?????' in which case we ignore the error.
        ShareType servType = ShareType.ServiceAsType(service);

        if (servType == ShareType.UNKNOWN && service.compareTo("?????") != 0) {
            respParser.setError(SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return endOff;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TREE))
            m_sess.debugPrintln("ANDX Tree Connect AndX - " + uncPath + ", " + service);

        // Parse the requested share name
        PCShare share = null;

        try {
            share = new PCShare(uncPath);
        }
        catch (InvalidUNCPathException ex) {
            respParser.setError(SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return endOff;
        }

        // Map the IPC$ share to the admin pipe type
        if (share.getShareName().compareTo("IPC$") == 0)
            servType = ShareType.ADMINPIPE;

        // Find the requested shared device
        SharedDevice shareDev = null;

        try {

            // Get/create the shared device
            shareDev = m_sess.getSMBServer().findShare(share.getNodeName(), share.getShareName(), servType, getSession(), true);
        }
        catch (InvalidUserException ex) {

            // Return a logon failure status
            respParser.setError(SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return endOff;
        }
        catch (Exception ex) {

            // Return a general status, bad network name
            respParser.setError(SMBStatus.SRVInvalidNetworkName, SMBStatus.ErrSrv);
            return endOff;
        }

        // Check if the share is valid
        if (shareDev == null || (servType != ShareType.UNKNOWN && shareDev.getType() != servType)) {
            respParser.setError(SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
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
                respParser.setError(SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
                return endOff;
            }
        }

        // Allocate a tree id for the new connection
        try {

            // Allocate the tree id for this connection
            int treeId = vc.addConnection(shareDev);
            respParser.setTreeId(treeId);

            // Set the file permission that this user has been granted for this share
            TreeConnection tree = vc.findConnection(treeId);
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
        byte[] outBuf = respPkt.getBuffer();
        pos = DataPacker.putString(ShareType.TypeAsService(shareDev.getType()), outBuf, pos, true);
        int bytLen = pos - respParser.getAndXByteOffset(endOff);
        respParser.setAndXByteCount(endOff, bytLen);

        // Return the new end of packet offset
        return pos;
    }

    /**
     * Process a chained read file request
     *
     * @param cmdOff  Offset to the chained command within the request packet
     * @param smbPkt  Request packet
     * @param respPkt Reply packet.
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
        if ( reqParser.getAndXParameterCount(cmdOff) == 12) {
            long topOff = (long) reqParser.getAndXParameterLong(cmdOff, 10);
            offset += topOff << 32;
        }

        // Debug
        if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
            Debug.println("Chained File Read AndX : Size=" + maxCount + " ,Pos=" + offset);

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
            rdlen = disk.readFile(m_sess, conn, netFile, buf, dataPos, maxCount, offset);

            // Return the data block
            respParser.setAndXParameter(endOff, 0, SMBV1.NO_ANDX_CMD);
            respParser.setAndXParameter(endOff, 1, 0);

            respParser.setAndXParameter(endOff, 2, 0xFFFF);
            respParser.setAndXParameter(endOff, 3, 0);
            respParser.setAndXParameter(endOff, 4, 0);
            respParser.setAndXParameter(endOff, 5, rdlen);
            respParser.setAndXParameter(endOff, 6, dataPos - RFCNetBIOSProtocol.HEADER_LEN);

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
     * @param cmdOff  int Offset to the chained command within the request packet
     * @param smbPkt  Request packet
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
        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            respParser.setError(SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return endOff;
        }

        // Debug
        if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
            Debug.println("Chained File Close [" + reqParser.getTreeId() + "] fid=" + fid);

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
     * Close a search started via the transact2 find first/next command.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procFindClose(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid find close request
        if ( parser.checkPacketIsValid(1, 0) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit( parser.getUserId());
        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());
        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
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

        // Extract the file lock/unlock parameters
        int fid = parser.getParameter(2);
        int lockType = parser.getParameter(3);
        long lockTmo = parser.getParameterLong(4);
        int lockCnt = parser.getParameter(6);
        int unlockCnt = parser.getParameter(7);

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_LOCK))
            m_sess.debugPrintln("File Lock [" + netFile.getFileId() + "] : type=0x" + Integer.toHexString(lockType) + ", tmo="
                    + lockTmo + ", locks=" + lockCnt + ", unlocks=" + unlockCnt);

        // Return a success status for now
        parser.setParameterCount(2);
        parser.setAndXCommand(0xFF);
        parser.setParameter(1, 0);
        parser.setByteCount(0);

        // Send the lock request response
        m_sess.sendResponseSMB(smbPkt);
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
            throws java.io.IOException, SMBSrvException {

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
            Debug.println("[SMB] Logoff vc=" + vc);

        // Close the virtual circuit
        m_sess.removeVirtualCircuit(uid);

        // Return a success status SMB
        m_sess.sendSuccessResponseSMB(smbPkt);
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
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid open andX request
        if (parser.checkPacketIsValid(15, 1) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());
        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNoAccessRights, SMBStatus.ErrSrv);
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
        SMBDate crDateTime = null;
        if (crTime > 0 && crDate > 0)
            crDateTime = new SMBDate(crDate, crTime);

        FileOpenParams params = new FileOpenParams(fileName, openFunc, access, srchAttr, fileAttr, allocSiz,
                crDateTime != null ? crDateTime.getTime() : 0L, parser.getProcessIdFull());

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
            m_sess.debugPrintln("File Open AndX [" + treeId + "] params=" + params);

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
                        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
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
                if (FileAction.truncateExistingFile(openFunc))
                    respAction = FileAction.FileTruncated;
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (FileSharingException ex) {

            // Return a sharing violation error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileSharingConflict, SMBStatus.ErrDos);
            return;
        }
        catch (FileOfflineException ex) {

            // File data is unavailable
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTFileOffline, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
            return;
        }
        catch (java.io.IOException ex) {

            // Failed to open the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }

        // Check if there is a chain command to process
        SMBSrvPacket respPkt = smbPkt;
        boolean andX = false;

        if (parser.hasAndXCommand()) {

            // Allocate a new packet for the response
            respPkt = m_sess.getPacketPool().allocatePacket(parser.getLength(), smbPkt);

            // Indicate that there is an AndX chained command to process
            andX = true;

            // Set a parser for the response
            respPkt.setParser( SMBSrvPacket.Version.V1);
            parser = (SMBV1Parser) respPkt.getParser();
        }

        // Build the open file response
        parser.setParameterCount(15);

        parser.setAndXCommand(0xFF);
        parser.setParameter(1, 0); // AndX offset

        parser.setParameter(2, fid);
        parser.setParameter(3, netFile.getFileAttributes() & StandardAttributes);

        long modDate = 0L;

        if (netFile.hasModifyDate()) {
            GlobalConfigSection gblConfig = (GlobalConfigSection) m_sess.getServer().getConfiguration().getConfigSection(
                    GlobalConfigSection.SectionName);
            modDate = (netFile.getModifyDate() / 1000L) + (gblConfig != null ? gblConfig.getTimeZoneOffset() : 0);
        }

        parser.setParameterLong(4, (int) modDate);
        parser.setParameterLong(6, netFile.getFileSizeInt()); // file size
        parser.setParameter(8, netFile.getGrantedAccess().intValue());
        parser.setParameter(9, OpenAndX.FileTypeDisk);
        parser.setParameter(10, 0); // named pipe state
        parser.setParameter(11, respAction);
        parser.setParameter(12, 0); // server FID (long)
        parser.setParameter(13, 0);
        parser.setParameter(14, 0);

        parser.setByteCount(0);

        // Check if there is a chained command, or commands
        if (andX == true) {

            // Process any chained commands, AndX
            int pos = procAndXCommands(smbPkt, netFile);

            // Send the read andX response
            m_sess.sendResponseSMB(respPkt, pos);
        }
        else {

            // Send the normal read AndX response
            m_sess.sendResponseSMB(respPkt);
        }
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());
        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // If the connection is to the IPC$ remote admin named pipe pass the request to the IPC
        // handler.
        if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {

            // Use the IPC$ handler to process the request
            IPCHandler.processIPCRequest(m_sess, smbPkt);
            return;
        }

        // Extract the read file parameters
        int fid = parser.getParameter(2);
        int offset = parser.getParameterLong(3);
        int maxCount = parser.getParameter(5);

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
        int rdlen = 0;

        // Set the returned parameter count so that the byte offset can be calculated
        parser.setParameterCount(12);
        int dataPos = parser.getByteOffset();

        try {

            // Check if the requested data will fit into the current packet
            if (maxCount > (buf.length - dataPos)) {

                // Allocate a larger packet for the response
                respPkt = m_sess.getPacketPool().allocatePacket(maxCount + dataPos, smbPkt);

                // Set the parser for the new response packet
                respPkt.setParser( SMBSrvPacket.Version.V1);
                parser = (SMBV1Parser) respPkt.getParser();

                // Switch to the response buffer
                buf = respPkt.getBuffer();
                parser.setParameterCount(12);
            }

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Check if the requested data length will fit into the buffer
            int dataLen = buf.length - dataPos;
            if (dataLen < maxCount)
                maxCount = dataLen;

            // Read from the file
            rdlen = disk.readFile(m_sess, conn, netFile, buf, dataPos, maxCount, offset);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (AccessDeniedException ex) {

            // No access to file, or file is a directory
            //
            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
                m_sess.debugPrintln("File Read Error [" + netFile.getFileId() + "] : " + ex.toString());

            // Failed to read the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {

            // Debug
            if (Debug.EnableError && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
                m_sess.debugPrintln("File Read Error [" + netFile.getFileId() + "] : " + ex.toString());

            // Failed to read the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.HRDReadFault, SMBStatus.ErrHrd);
            return;
        }

        // Return the data block
        parser.setAndXCommand(0xFF);        // no chained command
        parser.setParameter(1, 0);
        parser.setParameter(2, 0xFFFF);    // bytes remaining, for pipes only
        parser.setParameter(3, 0);        // data compaction mode
        parser.setParameter(4, 0);        // reserved
        parser.setParameter(5, rdlen);    // data length
        parser.setParameter(6, dataPos - RFCNetBIOSProtocol.HEADER_LEN); // offset to data

        // Clear the reserved parameters
        for (int i = 7; i < 12; i++)
            parser.setParameter(i, 0);

        // Set the byte count
        parser.setByteCount((dataPos + rdlen) - parser.getByteOffset());

        // Send the read andX response
        m_sess.sendResponseSMB(respPkt);
    }

    /**
     * Process the file read MPX request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procReadMPX(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid read andX request
        if (parser.checkPacketIsValid(8, 0) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the tree connection details
        TreeConnection conn = m_sess.findTreeConnection(smbPkt);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVInvalidTID, SMBStatus.ErrSrv);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // If the connection is to the IPC$ remote admin named pipe pass the request to the IPC
        // handler.
        if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {

            // Use the IPC$ handler to process the request
            IPCHandler.processIPCRequest(m_sess, smbPkt);
            return;
        }

        // Extract the read file parameters
        int fid = parser.getParameter(0);
        int offset = parser.getParameterLong(1);
        int maxCount = parser.getParameter(3);

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
            Debug.println("File ReadMPX [" + netFile.getFileId() + "] : Size=" + maxCount + " ,Pos=" + offset + ",MaxCount="
                    + maxCount);

        // Get the maximum buffer size the client allows
        int clientMaxSize = m_sess.getClientMaximumBufferSize();

        // Read data from the file
        SMBSrvPacket respPkt = smbPkt;
        byte[] buf = respPkt.getBuffer();
        int dataPos = 0;
        int rdlen = 0;
        int rdRemaining = maxCount;

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Check if the read data will fit into the current packet
            if (parser.getBufferLength() < clientMaxSize) {

                // Allocate a new packet for the responses
                respPkt = m_sess.getPacketPool().allocatePacket(clientMaxSize, smbPkt);

                // Switch to the new buffer
                buf = respPkt.getBuffer();

                // Set the response packet parser
                respPkt.setParser( SMBSrvPacket.Version.V1);
                parser = (SMBV1Parser) respPkt.getParser();
            }

            // Set the returned parameter count so that the byte offset can be calculated
            parser.setParameterCount(8);
            dataPos = parser.getByteOffset();

            // Calculate the maximum read size to return
            clientMaxSize -= dataPos;

            // Loop until all required data has been read
            while (rdRemaining > 0) {

                // Check if the requested data length will fit into the buffer
                rdlen = rdRemaining;
                if (rdlen > clientMaxSize)
                    rdlen = clientMaxSize;

                // Read from the file
                rdlen = disk.readFile(m_sess, conn, netFile, buf, dataPos, rdlen, offset);

                // Build the reply packet
                parser.setParameterLong(0, offset);
                parser.setParameter(2, maxCount);
                parser.setParameter(3, 0xFFFF);
                parser.setParameterLong(4, 0);
                parser.setParameter(6, rdlen);
                parser.setParameter(7, dataPos - RFCNetBIOSProtocol.HEADER_LEN);

                parser.setByteCount(rdlen);

                // Update the read offset and remaining read length
                if (rdlen > 0) {
                    rdRemaining -= rdlen;
                    offset += rdlen;
                }
                else
                    rdRemaining = 0;

                // Set the response command
                parser.setCommand(PacketTypeV1.ReadMpxSecondary);

                // Debug
                if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
                    Debug.println("File ReadMPX Secondary [" + netFile.getFileId() + "] : Size=" + rdlen + " ,Pos=" + offset);

                // Send the packet
                m_sess.sendResponseSMB(smbPkt);
            }
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (AccessDeniedException ex) {

            // No access to file, or file is a directory
            //
            // Debug
            if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
                Debug.println("File ReadMPX Error [" + netFile.getFileId() + "] : " + ex.toString());

            // Failed to read the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (IOException ex) {

            // Debug
            if (Debug.EnableError)
                Debug.println("File ReadMPX Error [" + netFile.getFileId() + "] : " + ex);

            // Failed to read the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.HRDReadFault, SMBStatus.ErrHrd);
            return;
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid
        // connection id.
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasWriteAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
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
        if (oldName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_FILE))
            m_sess.debugPrintln("File Rename [" + treeId + "] old name=" + oldName + ", new name=" + newName);

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
        catch (IOException ex) {

            // Failed to open the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }

        // Build the rename file response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Process the SMB session setup request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected void procSessionSetup(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws SMBSrvException, IOException, TooManyConnectionsException {

        // Extract the client details from the session setup request
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the session details
        int maxBufSize = parser.getParameter(2);
        int maxMpx = parser.getParameter(3);
        int vcNum = parser.getParameter(4);

        // Extract the password string
        byte[] pwd = null;
        int pwdLen = parser.getParameter(7);

        if (pwdLen > 0) {
            pwd = new byte[pwdLen];
            for (int i = 0; i < pwdLen; i++)
                pwd[i] = buf[dataPos + i];
            dataPos += pwdLen;
            dataLen -= pwdLen;
        }

        // Extract the user name string
        String user = DataPacker.getString(buf, dataPos, dataLen);
        if (user == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        else {

            // Update the buffer pointers
            dataLen -= user.length() + 1;
            dataPos += user.length() + 1;
        }

        // Extract the clients primary domain name string
        String domain = "";

        if (dataLen > 0) {

            // Extract the callers domain name
            domain = DataPacker.getString(buf, dataPos, dataLen);
            if (domain == null) {
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
                return;
            }
            else {

                // Update the buffer pointers
                dataLen -= domain.length() + 1;
                dataPos += domain.length() + 1;
            }
        }

        // Extract the clients native operating system
        String clientOS = "";

        if (dataLen > 0) {

            // Extract the callers operating system name
            clientOS = DataPacker.getString(buf, dataPos, dataLen);
            if (clientOS == null) {
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
                return;
            }
        }

        // DEBUG
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
            m_sess.debugPrintln("Session setup from user=" + user + ", password=" + pwd + ", domain=" + domain + ", os="
                    + clientOS + ", VC=" + vcNum + ", maxBuf=" + maxBufSize + ", maxMpx=" + maxMpx);

        // Store the client maximum buffer size and maximum multiplexed requests count
        m_sess.setClientMaximumBufferSize(maxBufSize);
        m_sess.setClientMaximumMultiplex(maxMpx);

        // Create the client information and store in the session
        ClientInfo client = ClientInfo.createInfo(user, pwd);
        client.setDomain(domain);
        client.setOperatingSystem(clientOS);
        if (m_sess.hasRemoteAddress())
            client.setClientAddress(m_sess.getRemoteAddress().getHostAddress());

        if (m_sess.getClientInformation() == null || m_sess.getClientInformation().getUserName().length() == 0) {

            // Set the session client details
            m_sess.setClientInformation(client);
        }
        else {

            // Get the current client details from the session
            ClientInfo curClient = m_sess.getClientInformation();

            if (curClient.getUserName() == null || curClient.getUserName().length() == 0) {

                // Update the client information
                m_sess.setClientInformation(client);
            }
            else {

                // DEBUG
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
                    m_sess.debugPrintln("Session already has client information set");
            }
        }

        // Authenticate the user, if the server is using user mode security
        ISMBAuthenticator auth = getSession().getSMBServer().getSMBAuthenticator();
        boolean isGuest = false;

        if (auth != null && auth.getAccessMode() == ISMBAuthenticator.AuthMode.USER) {

            // Validate the user
            ISMBAuthenticator.AuthStatus sts = auth.authenticateUser(client, m_sess, ISMBAuthenticator.PasswordAlgorithm.LANMAN);

            if (sts == ISMBAuthenticator.AuthStatus.GUEST_LOGON)
                isGuest = true;
            else if (sts != ISMBAuthenticator.AuthStatus.AUTHENTICATED) {

                // Invalid user, reject the session setup request
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
                return;
            }
        }

        // Set the guest flag for the client and logged on status
        client.setGuest(isGuest);
        getSession().setLoggedOn(true);

        // If the user is logged on then allocate a virtual circuit
        int uid = 0;

        // Create a virtual circuit for the new logon
        VirtualCircuit vc = new VirtualCircuit(vcNum, client);
        uid = m_sess.addVirtualCircuit(vc);

        if (uid == VirtualCircuit.InvalidID) {

            // DEBUG
            if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
                Debug.println("Failed to allocate UID for virtual circuit, " + vc);

            // Failed to allocate a UID
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }
        else if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE)) {

            // DEBUG
            Debug.println("Allocated UID=" + uid + " for VC=" + vc);
        }

        // Check if there is a chained commmand with the session setup request (usually a TreeConnect)
        SMBSrvPacket respPkt = smbPkt;
        boolean andX = false;

        if (parser.hasAndXCommand() && dataPos < smbPkt.getReceivedLength()) {

            // Allocate a new packet for the response
            respPkt = m_sess.getPacketPool().allocatePacket(parser.getLength(), smbPkt);

            // Indicate that there is an AndX chained command to process
            andX = true;

            // Set the response packet parser
            respPkt.setParser( SMBSrvPacket.Version.V1);
            parser = (SMBV1Parser) respPkt.getParser();
        }

        // Build the session setup response SMB
        parser.setParameterCount(3);
        parser.setParameter(0, 0); // No chained response
        parser.setParameter(1, 0); // Offset to chained response
        parser.setParameter(2, isGuest ? 1 : 0);
        parser.setByteCount(0);

        parser.setTreeId(0);
        parser.setUserId(uid);

        // Set the various flags
        int flags = parser.getFlags();
        flags &= ~SMBV1.FLG_CASELESS;
        parser.setFlags(flags);
        parser.setFlags2(SMBV1.FLG2_LONGFILENAMES);

        // Pack the OS, dialect and domain name strings.
        int pos = parser.getByteOffset();
        buf = respPkt.getBuffer();

        pos = DataPacker.putString("Java", buf, pos, true);
        pos = DataPacker.putString("Java File Server " + m_sess.getServer().isVersion(), buf, pos, true);
        pos = DataPacker.putString(m_sess.getSMBServer().getSMBConfiguration().getDomainName(), buf, pos, true);

        parser.setByteCount(pos - parser.getByteOffset());

        // Check if there is a chained command, or commands
        if (andX == true) {

            // Process any chained commands, AndX
            pos = procAndXCommands(smbPkt, null);
        }
        else {

            // Indicate that there are no chained replies
            parser.setAndXCommand(SMBV1.NO_ANDX_CMD);
        }

        // Send the negotiate response
        m_sess.sendResponseSMB(respPkt, pos);

        // Update the session state
        m_sess.setState(SessionState.SMB_SESSION);

        // Find the virtual circuit allocated, this will set the per-thread ClientInfo on the session
        m_sess.findVirtualCircuit(parser.getUserId());

        // Notify listeners that a user has logged onto the session
        m_sess.getSMBServer().sessionLoggedOn(m_sess);
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
        if (parser.checkPacketIsValid(15, 0) == false) {

            // Not enough parameters for a valid transact2 request
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.NTErr);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());
        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid
        // connection id.
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.NTErr);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Create a transact packet using the received SMB packet
        SMBSrvTransPacket tranPkt = new SMBSrvTransPacket(parser.getBuffer());

        // Create a transact buffer to hold the transaction setup, parameter and data blocks
        SrvTransactBuffer transBuf = null;
        int subCmd = tranPkt.getSubFunction();

        if (tranPkt.getTotalParameterCount() == tranPkt.getParameterBlockCount()
                && tranPkt.getTotalDataCount() == tranPkt.getDataBlockCount()) {

            // Create a transact buffer using the packet buffer, the entire request is contained in a single packet
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
            transBuf.appendParameter(buf, tranPkt.getParameterBlockOffset(), tranPkt.getParameterBlockCount());
            transBuf.appendData(buf, tranPkt.getDataBlockOffset(), tranPkt.getDataBlockCount());
        }

        // Set the return data limits for the transaction
        transBuf.setReturnLimits(tranPkt.getMaximumReturnSetupCount(), tranPkt.getMaximumReturnParameterCount(), tranPkt.getMaximumReturnDataCount());

        // Check for a multi-packet transaction, for a multi-packet transaction we just acknowledge
        // the receive with an empty response SMB
        if (transBuf.isMultiPacket()) {

            // Save the partial transaction data
            vc.setTransaction(transBuf);

            // Send an intermediate acknowedgement response
            m_sess.sendSuccessResponseSMB(smbPkt);
            return;
        }

        // Check if the transaction is on the IPC$ named pipe, the request requires special
        // processing
        if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {
            IPCHandler.procTransaction(vc, transBuf, m_sess, smbPkt);
            return;
        }

        // DEBUG
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TRAN))
            m_sess.debugPrintln("Transaction [" + treeId + "] tbuf=" + transBuf);

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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.NTErr);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());
        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid
        // connection id.
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.NTErr);
            return;
        }
        // Check if the user has the required access permission

        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
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

            // There are more transaction parameter/data sections to be received, return an intermediate response
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

        // Get the transaction sub-command code and validate
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

            // Unknown transact2 command
            default:

                // Return an unrecognized command error
                if (Debug.EnableError)
                    m_sess.debugPrintln("Error Transact2 Command = 0x" + Integer.toHexString(tbuf.getFunction()));
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
                break;
        }
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
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

        // Check if the search path is valid
        if (srchPath == null || srchPath.length() == 0) {

            // Invalid search request
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Access the shared device disk interface
        SearchContext ctx = null;
        DiskInterface disk = null;
        int searchId = -1;

        try {

            // Access the disk interface
            disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Allocate a search slot for the new search
            searchId = vc.allocateSearchSlot();

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                m_sess.debugPrintln("Start trans search [" + searchId + "] - " + srchPath + ", attr=0x"
                        + Integer.toHexString(srchAttr) + ", maxFiles=" + maxFiles + ", infoLevel=" + infoLevl + ", flags=0x"
                        + Integer.toHexString(srchFlag));

            // Start a new search
            ctx = disk.startSearch(m_sess, conn, srchPath, srchAttr);
            if (ctx != null) {

                // Store details of the search in the context
                ctx.setTreeId(treeId);
                ctx.setMaximumFiles(maxFiles);
            }
            else {

                // Failed to start the search, return a no more files error
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
                return;
            }

            // Save the search context
            vc.setSearchContext(searchId, ctx);

            // Create the reply transact buffer
            SrvTransactBuffer replyBuf = new SrvTransactBuffer(tbuf);
            DataBuffer dataBuf = replyBuf.getDataBuffer();

            // Determine the maximum return data length
            int maxLen = replyBuf.getReturnDataLimit();

            // Check if resume keys are required
            boolean resumeReq = (srchFlag & FindFirstNext.ReturnResumeKey) != 0 ? true : false;

            // Loop until we have filled the return buffer or there are no more files to return
            int fileCnt = 0;
            int packLen = 0;
            int lastNameOff = 0;

            boolean pktDone = false;
            boolean searchDone = false;

            FileInfo info = new FileInfo();

            // If this is a wildcard search then add the '.' and '..' entries
            if (WildCard.containsWildcards(srchPath)) {

                // Pack the '.' file information
                if (resumeReq == true) {
                    dataBuf.putInt(-1);
                    maxLen -= 4;
                }

                lastNameOff = dataBuf.getPosition();
                FileInfo dotInfo = new FileInfo(".", 0, FileAttribute.Directory);
                dotInfo.setFileId(dotInfo.getFileName().hashCode());
                dotInfo.setCreationDateTime(DotFileDateTime);
                dotInfo.setModifyDateTime(DotFileDateTime);
                dotInfo.setAccessDateTime(DotFileDateTime);

                packLen = FindInfoPacker.packInfo(dotInfo, dataBuf, infoLevl, tbuf.isUnicode());

                // Update the file count for this packet, update the remaining buffer length
                fileCnt++;
                maxLen -= packLen;

                // Pack the '..' file information
                if (resumeReq == true) {
                    dataBuf.putInt(-2);
                    maxLen -= 4;
                }

                lastNameOff = dataBuf.getPosition();
                dotInfo.setFileName("..");
                dotInfo.setFileId(dotInfo.getFileName().hashCode());

                packLen = FindInfoPacker.packInfo(dotInfo, dataBuf, infoLevl, tbuf.isUnicode());

                // Update the file count for this packet, update the remaining buffer length
                fileCnt++;
                maxLen -= packLen;
            }

            // Pack the file information records
            while (pktDone == false && fileCnt < maxFiles) {

                // Get file information from the search
                if (ctx.nextFileInfo(info) == false) {

                    // No more files
                    pktDone = true;
                    searchDone = true;
                }

                // Check if the file information will fit into the return buffer
                else if (FindInfoPacker.calcInfoSize(info, infoLevl, false, true) <= maxLen) {

                    // Pack a dummy resume key, if required
                    if (resumeReq) {
                        dataBuf.putZeros(4);
                        maxLen -= 4;
                    }

                    // Save the offset to the last file information structure
                    lastNameOff = dataBuf.getPosition();

                    // Mask the file attributes
                    info.setFileAttributes(info.getFileAttributes() & StandardAttributes);

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
                }
            }

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
                m_sess.debugPrintln("Search [" + searchId + "] Returned " + fileCnt + " files, moreFiles=" + ctx.hasMoreFiles());

            // Check if the search is complete
            if (searchDone == true) {

                // Debug
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                    m_sess.debugPrintln("End start search [" + searchId + "] (Search complete)");

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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
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
                if (Debug.EnableError && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                    m_sess.debugPrintln("Search context null - [" + searchId + "]");

                // Invalid search handle
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSNoMoreFiles, SMBStatus.ErrDos);
                return;
            }

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                m_sess.debugPrintln("Continue search [" + searchId + "] - " + resumeName + ", maxFiles=" + maxFiles
                        + ", infoLevel=" + infoLevl + ", flags=0x" + Integer.toHexString(srchFlag));

            // Create the reply transaction buffer
            SrvTransactBuffer replyBuf = new SrvTransactBuffer(tbuf);
            DataBuffer dataBuf = replyBuf.getDataBuffer();

            // Determine the maximum return data length
            int maxLen = replyBuf.getReturnDataLimit();

            // Check if resume keys are required
            boolean resumeReq = (srchFlag & FindFirstNext.ReturnResumeKey) != 0 ? true : false;

            // Loop until we have filled the return buffer or there are no more files to return
            int fileCnt = 0;
            int packLen = 0;
            int lastNameOff = 0;

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

                    // Pack a dummy resume key, if required
                    if (resumeReq)
                        dataBuf.putZeros(4);

                    // Save the offset to the last file information structure
                    lastNameOff = dataBuf.getPosition();

                    // Mask the file attributes
                    info.setFileAttributes(info.getFileAttributes() & StandardAttributes);

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
                m_sess.debugPrintln("Search [" + searchId + "] Returned " + fileCnt + " files, moreFiles=" + ctx.hasMoreFiles());

            // Check if the search is complete
            if (searchDone == true) {

                // Debug
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_SEARCH))
                    m_sess.debugPrintln("End start search [" + searchId + "] (Search complete)");

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
     * Process a transact2 query file information (via handle) request.
     *
     * @param tbuf   Transaction request details
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws IOException     If an I/O error occurs
     * @throws SMBSrvException SMB protocol exception
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
        if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_INFO))
            Debug.println("Query File - level=0x" + Integer.toHexString(infoLevl) + ", fid=" + fid + ", stream="
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

            // Create a data buffer using the SMB packet. The response should always fit into a
            // single
            // reply packet.
            DataBuffer replyBuf = new DataBuffer(buf, dataPos, buf.length - dataPos);

            // Check if the virtual filesystem supports streams, and streams are enabled
            boolean streams = false;

            if (disk instanceof NTFSStreamsInterface) {

                // Check if NTFS streams are enabled
                NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) disk;
                streams = ntfsStreams.hasStreamsEnabled(m_sess, conn);
            }

            // Check for the file streams information level
            int dataLen = 0;

            if (streams == true && (infoLevl == FileInfoLevel.PathFileStreamInfo || infoLevl == FileInfoLevel.NTFileStreamInfo)) {

                // Debug
                if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_STREAMS))
                    Debug.println("Get NTFS streams list fid=" + fid + ", name=" + netFile.getFullName());

                // Get the list of streams from the share driver
                NTFSStreamsInterface ntfsStreams = (NTFSStreamsInterface) disk;
                StreamInfoList streamList = ntfsStreams.getStreamList(m_sess, conn, netFile.getFullName());

                if (streamList == null) {
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
                    return;
                }

                // Pack the file streams information into the return data packet
                dataLen = QueryInfoPacker.packStreamFileInfo(streamList, replyBuf, true);
            }
            else {

                // Get the file information
                FileInfo fileInfo = disk.getFileInformation(m_sess, conn, netFile.getFullNameStream());

                if (fileInfo == null) {
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
                    return;
                }

                // Mask the file attributes
                fileInfo.setFileAttributes(fileInfo.getFileAttributes() & StandardAttributes);

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
        catch (AccessDeniedException ex) {

            // Not allowed to access the file/folder
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
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
                    DiskInfoPacker.packFsDevice(0, 0, replyBuf);
                    break;

                // Filesystem attribute information
                case DiskInfoPacker.InfoFsAttribute:
                    DiskInfoPacker.packFsAttribute(0, 255, "JFileSrv", tbuf.isUnicode(), replyBuf);
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the tree connection details
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.NTErr);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the query path information level and file/directory name
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int infoLevl = paramBuf.getShort();
        paramBuf.skipBytes(4);

        String path = paramBuf.getString(tbuf.isUnicode());

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
            int dataPos = prmPos; // no parameters returned

            // Create a data buffer using the SMB packet. The response should always fit into a
            // single reply packet.
            DataBuffer replyBuf = new DataBuffer(buf, dataPos, buf.length - dataPos);

            // Get the file information
            FileInfo fileInfo = disk.getFileInformation(m_sess, conn, path);

            if (fileInfo == null) {
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.NTErr);
                return;
            }

            // Mask the file attributes
            fileInfo.setFileAttributes(fileInfo.getFileAttributes() & StandardAttributes);

            // Pack the file information into the return data packet
            int dataLen = QueryInfoPacker.packInfo(fileInfo, replyBuf, infoLevl, true);

            // Check if any data was packed, if not then the information level is not supported
            if (dataLen == 0) {
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.NTErr);
                return;
            }

            SMBSrvTransPacket.initTransactReply(smbPkt, 0, prmPos, dataLen, dataPos);
            parser.setByteCount(replyBuf.getPosition() - parser.getByteOffset());

            // Send the transact reply
            m_sess.sendResponseSMB(smbPkt);
        }
        catch (FileNotFoundException ex) {

            // Requested file does not exist
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.NTErr);
            return;
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.NTErr);
            return;
        }
        catch (UnsupportedInfoLevelException ex) {

            // Requested information level is not supported
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.NTErr);
            return;
        }
    }

    /**
     * Process the SMB tree connect request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     * @exception TooManyConnectionsException No more connections available
     */
    protected void procTreeConnectAndX(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws SMBSrvException, TooManyConnectionsException, IOException {

        // Check that the received packet looks like a valid tree connect request
        if (parser.checkPacketIsValid(4, 3) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
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

        // Get the data bytes position and length
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the password string
        String pwd = null;

        if (pwdLen > 0) {
            pwd = new String(buf, dataPos, pwdLen);
            dataPos += pwdLen;
            dataLen -= pwdLen;
        }

        // Extract the requested share name, as a UNC path
        String uncPath = DataPacker.getString(buf, dataPos, dataLen);
        if (uncPath == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Extract the service type string
        dataPos += uncPath.length() + 1; // null terminated
        dataLen -= uncPath.length() + 1; // null terminated

        String service = DataPacker.getString(buf, dataPos, dataLen);
        if (service == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Convert the service type to a shared device type, client may specify '?????' in which case we ignore the error.
        ShareType servType = ShareType.ServiceAsType(service);

        if (servType == ShareType.UNKNOWN && service.compareTo("?????") != 0) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TREE))
            m_sess.debugPrintln("Tree Connect AndX - " + uncPath + ", " + service);

        // Parse the requested share name
        PCShare share = null;

        try {
            share = new PCShare(uncPath);
        }
        catch (InvalidUNCPathException ex) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Map the IPC$ share to the admin pipe type
        if (servType == ShareType.NAMEDPIPE && share.getShareName().compareTo("IPC$") == 0)
            servType = ShareType.ADMINPIPE;

        // Find the requested shared device
        SharedDevice shareDev = null;

        try {

            // Get/create the shared device
            shareDev = m_sess.getSMBServer().findShare(share.getNodeName(), share.getShareName(), servType, getSession(), true);
        }
        catch (InvalidUserException ex) {

            // Return a logon failure status
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (Exception ex) {

            // Return a general status, bad network name
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVInvalidNetworkName, SMBStatus.ErrSrv);
            return;
        }

        // Check if the share is valid
        if (shareDev == null || (servType != ShareType.UNKNOWN && shareDev.getType() != servType)) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Authenticate the share connection depending upon the security mode the server is running
        // under
        ISMBAuthenticator auth = getSession().getSMBServer().getSMBAuthenticator();
        ISMBAuthenticator.ShareStatus sharePerm = ISMBAuthenticator.ShareStatus.WRITEABLE;

        if (auth != null) {

            // Validate the share connection
            sharePerm = auth.authenticateShareConnect(m_sess.getClientInformation(), shareDev, pwd, m_sess);

            if (sharePerm == ISMBAuthenticator.ShareStatus.NO_ACCESS) {

                // Invalid share connection request
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNoAccessRights, SMBStatus.ErrSrv);
                return;
            }
        }

        // Allocate a tree id for the new connection
        int treeId = vc.addConnection(shareDev);
        parser.setTreeId(treeId);

        // Set the file permission that this user has been granted for this share
        TreeConnection tree = vc.findConnection(treeId);
        tree.setPermission(sharePerm);

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_TREE))
            m_sess.debugPrintln("Tree Connect AndX - Allocated Tree Id = " + treeId + ", Permission = " + sharePerm.name());

        // Build the tree connect response
        parser.setParameterCount(3);
        parser.setAndXCommand(0xFF); // no chained reply
        parser.setParameter(1, 0);
        parser.setParameter(2, 0);

        // Pack the service type
        int pos = parser.getByteOffset();
        pos = DataPacker.putString(ShareType.TypeAsService(shareDev.getType()), buf, pos, true);
        parser.setByteCount(pos - parser.getByteOffset());

        // Send the response
        m_sess.sendResponseSMB(smbPkt);

        // Inform the driver that a connection has been opened
        if (tree.getInterface() != null)
            tree.getInterface().treeOpened(m_sess, tree);
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());
        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
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
        if (conn.hasWriteAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // If the connection is to the IPC$ remote admin named pipe pass the request to the IPC
        // handler.
        if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {

            // Use the IPC$ handler to process the request
            IPCHandler.processIPCRequest(m_sess, smbPkt);
            return;
        }

        // Extract the write file parameters
        int fid = parser.getParameter(2);
        int offset = parser.getParameterLong(3);
        int dataLen = parser.getParameter(10);
        int dataPos = parser.getParameter(11) + RFCNetBIOSProtocol.HEADER_LEN;

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

            // Write to the file
            wrtlen = disk.writeFile(m_sess, conn, netFile, buf, dataPos, dataLen, offset);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
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
        parser.setParameterCount(6);
        parser.setAndXCommand(0xFF);
        parser.setParameter(1, 0);
        parser.setParameter(2, wrtlen);
        parser.setParameter(3, 0); // remaining byte count for pipes only
        parser.setParameter(4, 0); // reserved
        parser.setParameter(5, 0); // "
        parser.setByteCount(0);

        // Send the write response
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Process the file write MPX request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procWriteMPX(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid write andX request
        if (parser.checkPacketIsValid(12, 0) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the tree connection details
        TreeConnection conn = m_sess.findTreeConnection(smbPkt);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVInvalidTID, SMBStatus.ErrSrv);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasWriteAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // If the connection is to the IPC$ remote admin named pipe pass the request to the IPC
        // handler.
        if (conn.getSharedDevice().getType() == ShareType.ADMINPIPE) {

            // Use the IPC$ handler to process the request
            IPCHandler.processIPCRequest(m_sess, smbPkt);
            return;
        }

        // Extract the write file parameters
        int fid = parser.getParameter(0);
        int totLen = parser.getParameter(1);
        int offset = parser.getParameterLong(3);
        int dataLen = parser.getParameter(10);
        int dataPos = parser.getParameter(11) + RFCNetBIOSProtocol.HEADER_LEN;

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
            Debug.println("File WriteMPX [" + netFile.getFileId() + "] : Size=" + dataLen + " ,Pos=" + offset + ", TotLen="
                    + totLen);

        // Write data to the file
        byte[] buf = parser.getBuffer();
        int wrtlen = 0;

        // Access the disk interface and write to the file
        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Write to the file
            wrtlen = disk.writeFile(m_sess, conn, netFile, buf, dataPos, dataLen, offset);

            // Return the initial MPX response
            parser.setParameterCount(1);
            parser.setAndXCommand(0xFF);
            parser.setParameter(1, 0xFFFF);
            parser.setByteCount(0);

            // Send the write response
            m_sess.sendResponseSMB(smbPkt);

            // Update the remaining data length and write offset
            totLen -= wrtlen;
            offset += wrtlen;

            int rxlen = 0;
            SMBSrvPacket curPkt = null;

            while (totLen > 0) {

                // Release the associated packet
                if (smbPkt.hasAssociatedPacket()) {

                    // Release the current associated packet back to the pool, and clear
                    m_sess.getPacketPool().releasePacket(smbPkt.getAssociatedPacket());
                    smbPkt.setAssociatedPacket(null);
                }

                // Receive the next write packet
                curPkt = m_sess.getPacketHandler().readPacket();
                smbPkt.setAssociatedPacket(curPkt);

                // Make sure it is a secondary WriteMPX type packet
                if (parser.getCommand() != PacketTypeV1.WriteMpxSecondary)
                    throw new IOException("Write MPX invalid packet type received");

                // Get the write length and buffer offset
                dataLen = parser.getParameter(6);
                dataPos = parser.getParameter(7) + RFCNetBIOSProtocol.HEADER_LEN;

                // Debug
                if (Debug.EnableDbg && m_sess.hasDebug(SMBSrvSession.DBG_FILEIO))
                    Debug.println("File WriteMPX Secondary [" + netFile.getFileId() + "] : Size=" + dataLen + " ,Pos=" + offset);

                // Write the block of data
                wrtlen = disk.writeFile(m_sess, conn, netFile, buf, dataPos, dataLen, offset);

                // Update the remaining data length and write offset
                totLen -= wrtlen;
                offset += wrtlen;
            }
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (IOException ex) {

            // Debug
            if (Debug.EnableError)
                Debug.println("File WriteMPX Error [" + netFile.getFileId() + "] : " + ex);

            // Failed to read the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.HRDWriteFault, SMBStatus.ErrHrd);
            return;
        }
    }

    /**
     * Run the LanMan protocol handler
     *
     * @param smbPkt SMBSrvPacket
     * @return boolean true if the packet was processed, else false
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     * @exception TooManyConnectionsException No more connections available
     */
    public boolean runProtocol(SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException, TooManyConnectionsException {

        // Check if the received packet has a valid SMB v1 signature
        if (smbPkt.isSMB1() == false)
            throw new IOException("Invalid SMB signature");

        // Get the SMB parser
        if ( smbPkt.hasParser() == false)
            throw new IOException( "SMB packet does not have a parser");

        SMBV1Parser parser = (SMBV1Parser) smbPkt.getParser();

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.DBG_STATE) &&
                parser.hasChainedCommand())
            m_sess.debugPrintln("AndX Command = 0x" + Integer.toHexString( parser.getAndXCommand()));

        // Reset the byte unpack offset
        parser.resetBytePointer();

        // Determine the SMB command type
        boolean handledOK = true;

        switch ( parser.getCommand()) {

            // Session setup
            case PacketTypeV1.SessionSetupAndX:
                procSessionSetup(smbPkt, parser);
                break;

            // Tree connect
            case PacketTypeV1.TreeConnectAndX:
                procTreeConnectAndX(smbPkt, parser);
                break;

            // Transaction2
            case PacketTypeV1.Transaction2:
            case PacketTypeV1.Transaction:
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

            // Read a file
            case PacketTypeV1.ReadAndX:
                procReadAndX(smbPkt, parser);
                break;

            // Read MPX
            case PacketTypeV1.ReadMpx:
                procReadMPX(smbPkt, parser);
                break;

            // Write to a file
            case PacketTypeV1.WriteAndX:
                procWriteAndX(smbPkt, parser);
                break;

            // Write MPX
            case PacketTypeV1.WriteMpx:
                procWriteMPX(smbPkt, parser);
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

            // Tree connection (without AndX batching)
            case PacketTypeV1.TreeConnect:
                super.runProtocol(smbPkt);
                break;

            // Rename file
            case PacketTypeV1.RenameFile:
                procRenameFile(smbPkt, parser);
                break;

            // Echo request
            case PacketTypeV1.Echo:
                super.procEcho(smbPkt, parser);
                break;

            // Default
            default:

                // Get the tree connection details, if it is a disk or printer type connection then pass
                // the request to the core protocol handler
                int treeId = parser.getTreeId();
                TreeConnection conn = null;
                if (treeId != -1)
                    conn = m_sess.findTreeConnection(smbPkt);

                if (conn != null) {

                    // Check if this is a disk or print connection, if so then send the request to the
                    // core protocol handler
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
                break;
        }

        // Run any request post processors
        runRequestPostProcessors(m_sess);

        // Return the handled status
        return handledOK;
    }
}
