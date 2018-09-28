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

package org.filesys.smb.server;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.filesys.debug.Debug;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.server.filesys.DiskOfflineException;
import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.PathNotFoundException;
import org.filesys.server.filesys.TooManyFilesException;
import org.filesys.server.filesys.TreeConnection;
import org.filesys.server.filesys.UnsupportedInfoLevelException;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.SMBStatus;
import org.filesys.smb.TransactionNames;
import org.filesys.smb.WinNT;
import org.filesys.smb.dcerpc.DCEPipeType;
import org.filesys.smb.dcerpc.server.DCEPipeFile;
import org.filesys.smb.dcerpc.server.DCEPipeHandler;
import org.filesys.util.DataBuffer;
import org.filesys.util.DataPacker;

/**
 * <p>
 * The IPCHandler class processes requests made on the IPC$ remote admin pipe. The code is shared
 * amongst different SMB protocol handlers.
 *
 * @author gkspencer
 */
class IPCHandler {

    /**
     * Process a request made on the IPC$ remote admin named pipe.
     *
     * @param sess   SMBSrvSession
     * @param smbPkt SMBSrvPacket
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    public static void processIPCRequest(SMBSrvSession sess, SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException {

        // Get the tree id from the received packet and validate that it is a valid connection id.
        TreeConnection conn = sess.findTreeConnection(smbPkt);

        if (conn == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the associated parser
        SMBV1Parser parser = (SMBV1Parser) smbPkt.getParser();

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("IPC$ Request [" + parser.getTreeId() + "] - cmd = " + SMBV1.getPacketTypeString( parser.getCommand()));

        // Determine the SMB command
        switch ( parser.getCommand()) {

            // Open file request
            case PacketTypeV1.OpenAndX:
            case PacketTypeV1.OpenFile:
                procIPCFileOpen(sess, smbPkt, parser);
                break;

            // Read file request
            case PacketTypeV1.ReadFile:
                procIPCFileRead(sess, smbPkt, parser);
                break;

            // Read AndX file request
            case PacketTypeV1.ReadAndX:
                procIPCFileReadAndX(sess, smbPkt, parser);
                break;

            // Write file request
            case PacketTypeV1.WriteFile:
                procIPCFileWrite(sess, smbPkt, parser);
                break;

            // Write AndX file request
            case PacketTypeV1.WriteAndX:
                procIPCFileWriteAndX(sess, smbPkt, parser);
                break;

            // Close file request
            case PacketTypeV1.CloseFile:
                procIPCFileClose(sess, smbPkt, parser);
                break;

            // NT create andX request
            case PacketTypeV1.NTCreateAndX:
                procNTCreateAndX(sess, smbPkt, parser);
                break;

            // Default, respond with an unsupported function error.
            default:
                sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
                break;
        }
    }

    /**
     * Process an IPC$ transaction request.
     *
     * @param vc     VirtualCircuit
     * @param tbuf   SrvTransactBuffer
     * @param sess   SMBSrvSession
     * @param smbPkt SMBSrvPacket
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected static void procTransaction(VirtualCircuit vc, SrvTransactBuffer tbuf, SMBSrvSession sess, SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException {

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("IPC$ Transaction  pipe=" + tbuf.getName() + ", subCmd="
                    + NamedPipeTransaction.getSubCommand(tbuf.getFunction()));

        // Call the required transaction handler
        if (tbuf.getName().compareTo(TransactionNames.PipeLanman) == 0) {

            // Call the \PIPE\LANMAN transaction handler to process the request
            if (PipeLanmanHandler.processRequest(tbuf, sess, smbPkt))
                return;
        }

        // Process the pipe command
        switch (tbuf.getFunction()) {

            // Set named pipe handle state
            case NamedPipeTransaction.SetNmPHandState:
                procSetNamedPipeHandleState(sess, vc, tbuf, smbPkt);
                break;

            // Named pipe transaction request, pass the request to the DCE/RPC handler
            case NamedPipeTransaction.TransactNmPipe:
                DCERPCHandler.processDCERPCRequest(sess, vc, tbuf, smbPkt);
                break;

            // Query file information via handle
            case PacketTypeV1.Trans2QueryFile:
                procTrans2QueryFile(sess, vc, tbuf, smbPkt);
                break;

            // Wait for named pipe
            case NamedPipeTransaction.WaitNamedPipe:
                sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTNotSupported, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
                break;

            // Unknown command
            default:
                sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
                break;
        }
    }

    /**
     * Process a special IPC$ file open request.
     *
     * @param sess   SMBSrvSession
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected static void procIPCFileOpen(SMBSrvSession sess, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the data bytes position and length
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the filename string
        String fileName = DataPacker.getString(buf, dataPos, dataLen);

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("IPC$ Open file = " + fileName);

        // Check if the requested IPC$ file is valid
        DCEPipeType pipeType = DCEPipeType.getNameAsType(fileName);
        if (pipeType == DCEPipeType.PIPE_INVALID) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }

        // Get the tree connection details
        TreeConnection conn = sess.findTreeConnection(smbPkt);

        if (conn == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVInvalidTID, SMBStatus.ErrSrv);
            return;
        }

        // Create a network file for the special pipe
        DCEPipeFile pipeFile = new DCEPipeFile(pipeType);
        pipeFile.setGrantedAccess(NetworkFile.Access.READ_WRITE);

        // Add the file to the list of open files for this tree connection
        int fid = -1;

        try {
            fid = conn.addFile(pipeFile, sess);
        }
        catch (TooManyFilesException ex) {

            // Too many files are open on this connection, cannot open any more files.

            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSTooManyOpenFiles, SMBStatus.ErrDos);
            return;
        }

        // Build the open file response
        parser.setParameterCount(15);

        parser.setAndXCommand(0xFF);
        parser.setParameter(1, 0); // AndX offset

        parser.setParameter(2, fid);
        parser.setParameter(3, 0); // file attributes
        parser.setParameter(4, 0); // last write time
        parser.setParameter(5, 0); // last write date
        parser.setParameterLong(6, 0); // file size
        parser.setParameter(8, 0);
        parser.setParameter(9, 0);
        parser.setParameter(10, 0); // named pipe state
        parser.setParameter(11, 0);
        parser.setParameter(12, 0); // server FID (long)
        parser.setParameter(13, 0);
        parser.setParameter(14, 0);

        parser.setByteCount(0);

        // Send the response packet
        sess.sendResponseSMB(smbPkt);
    }

    /**
     * Process an IPC pipe file read request
     *
     * @param sess   SMBSrvSession
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected static void procIPCFileRead(SMBSrvSession sess, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check if the received packet is a valid read file request
        if (parser.checkPacketIsValid(5, 0) == false) {

            // Invalid request
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("IPC$ File Read");

        // Pass the read request the DCE/RPC handler
        DCERPCHandler.processDCERPCRead(sess, smbPkt);
    }

    /**
     * Process an IPC pipe file read andX request
     *
     * @param sess   SMBSrvSession
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected static void procIPCFileReadAndX(SMBSrvSession sess, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check if the received packet is a valid read andX file request
        if (parser.checkPacketIsValid(10, 0) == false) {

            // Invalid request
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("IPC$ File Read AndX");

        // Pass the read request the DCE/RPC handler
        DCERPCHandler.processDCERPCRead(sess, smbPkt);
    }

    /**
     * Process an IPC pipe file write request
     *
     * @param sess   SMBSrvSession
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected static void procIPCFileWrite(SMBSrvSession sess, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check if the received packet is a valid write file request
        if (parser.checkPacketIsValid(5, 0) == false) {

            // Invalid request
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("IPC$ File Write");

        // Pass the write request the DCE/RPC handler
        DCERPCHandler.processDCERPCRequest(sess, smbPkt);
    }

    /**
     * Process an IPC pipe file write andX request
     *
     * @param sess   SMBSrvSession
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected static void procIPCFileWriteAndX(SMBSrvSession sess, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check if the received packet is a valid write andX request
        if (parser.checkPacketIsValid(12, 0) == false) {

            // Invalid request
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("IPC$ File Write AndX");

        // Pass the write request the DCE/RPC handler
        DCERPCHandler.processDCERPCRequest(sess, smbPkt);
    }

    /**
     * Process a special IPC$ file close request.
     *
     * @param sess   SMBSrvSession
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected static void procIPCFileClose(SMBSrvSession sess, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid file close request
        if (parser.checkPacketIsValid(3, 0) == false) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid
        // connection id.
        TreeConnection conn = sess.findTreeConnection(smbPkt);

        if (conn == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the file id from the request
        int fid = parser.getParameter(0);
        DCEPipeFile netFile = (DCEPipeFile) conn.findFile(fid);

        if (netFile == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("IPC$ File close [" + parser.getTreeId() + "] fid=" + fid);

        // Remove the file from the connections list of open files
        conn.removeFile(fid, sess);

        // Build the close file response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        // Send the response packet
        sess.sendResponseSMB(smbPkt);
    }

    /**
     * Process a set named pipe handle state request
     *
     * @param sess   SMBSrvSession
     * @param vc     VirtualCircuit
     * @param tbuf   SrvTransactBuffer
     * @param smbPkt SMBSrvPacket
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected static void procSetNamedPipeHandleState(SMBSrvSession sess, VirtualCircuit vc, SrvTransactBuffer tbuf,
                                                      SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException {

        // Get the request parameters
        DataBuffer setupBuf = tbuf.getSetupBuffer();
        setupBuf.skipBytes(2);
        int fid = setupBuf.getShort();

        DataBuffer paramBuf = tbuf.getParameterBuffer();
        int state = paramBuf.getShort();

        // Get the connection for the request
        TreeConnection conn = vc.findConnection(tbuf.getTreeId());

        // Get the IPC pipe file for the specified file id
        DCEPipeFile netFile = (DCEPipeFile) conn.findFile(fid);
        if (netFile == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("  SetNmPHandState pipe=" + netFile.getName() + ", fid=" + fid + ", state=0x"
                    + Integer.toHexString(state));

        // Store the named pipe state
        netFile.setPipeState(state);

        // Setup the response packet
        SMBSrvTransPacket.initTransactReply(smbPkt, 0, 0, 0, 0);

        // Send the response packet
        sess.sendResponseSMB(smbPkt);
    }

    /**
     * Process an NT create andX request
     *
     * @param sess   SMBSrvSession
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected static void procNTCreateAndX(SMBSrvSession sess, SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Get the tree id from the received packet and validate that it is a valid
        // connection id.
        TreeConnection conn = sess.findTreeConnection(smbPkt);

        if (conn == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.NTErr);
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
        int shrAccess = prms.unpackInt();
        int createDisp = prms.unpackInt();
        int createOptn = prms.unpackInt();
        int impersonLev = prms.unpackInt();
        int secFlags = prms.unpackByte();

        // Extract the filename string
        int pos = DataPacker.wordAlign(parser.getByteOffset());
        String fileName = DataPacker.getUnicodeString(parser.getBuffer(), pos, nameLen);
        if (fileName == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.NTErr);
            return;
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("NT Create AndX [" + parser.getTreeId() + "] name=" + fileName + ", flags=0x"
                    + Integer.toHexString(flags) + ", attr=0x" + Integer.toHexString(attrib) + ", allocSize=" + allocSize);

        // Check if the pipe name is a short or long name
        if (fileName.startsWith("\\PIPE") == false)
            fileName = "\\PIPE" + fileName;

        // Check if the requested IPC$ file is valid
        DCEPipeType pipeType = DCEPipeType.getNameAsType(fileName);
        if (pipeType == DCEPipeType.PIPE_INVALID) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.NTErr);
            return;
        }

        // Check if there is a handler for the pipe file
        if (DCEPipeHandler.getHandlerForType(pipeType) == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.NTErr);
            return;
        }

        // Create a network file for the special pipe
        DCEPipeFile pipeFile = new DCEPipeFile(pipeType);
        pipeFile.setGrantedAccess(NetworkFile.Access.READ_WRITE);

        // Add the file to the list of open files for this tree connection
        int fid = -1;

        try {
            fid = conn.addFile(pipeFile, sess);
        }
        catch (TooManyFilesException ex) {

            // Too many files are open on this connection, cannot open any more files.
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.Win32InvalidHandle, SMBStatus.NTErr);
            return;
        }

        // Build the NT create andX response
        boolean extendedResponse = (flags & WinNT.ExtendedResponse) != 0;
        parser.setParameterCount(extendedResponse ? 42 : 34);

        prms.reset(parser.getBuffer(), SMBV1.PARAMWORDS + 4);

        prms.packByte(0);
        prms.packWord(fid);
        prms.packInt(0x0001); // File existed and was opened

        prms.packLong(0); // Creation time
        prms.packLong(0); // Last access time
        prms.packLong(0); // Last write time
        prms.packLong(0); // Change time

        prms.packInt(0x0080); // File attributes
        prms.packLong(0); // Allocation size
        prms.packLong(0); // End of file
        prms.packWord(2); // File type - named pipe, message mode
        prms.packByte(0xFF); // Pipe instancing count
        prms.packByte(0x05); // IPC state bits

        prms.packByte(0); // directory flag
        prms.packWord(0); // byte count = 0

        // Pack the extra extended response area, if requested
        if (extendedResponse == true) {

            // 22 byte block of zeroes
            prms.packLong(0);
            prms.packLong(0);
            prms.packInt(0);
            prms.packWord(0);

            // Pack the permissions
            prms.packInt(0x1F01FF);

            // 8 byte block, from network trace observed values
            prms.packInt(0x12019B);
            prms.packInt(0);
        }

        // Set the AndX offset
        int endPos = prms.getPosition();
        parser.setParameter(1, endPos - RFCNetBIOSProtocol.HEADER_LEN);

        // Set the status
        parser.setLongErrorCode(SMBStatus.NTSuccess);

        // Send the response packet
        sess.sendResponseSMB(smbPkt, endPos - RFCNetBIOSProtocol.HEADER_LEN);
    }

    /**
     * Process a transact2 query file information (via handle) request.
     *
     * @param sess   SMBSrvSession
     * @param vc     VirtualCircuit
     * @param tbuf   Transaction request details
     * @param smbPkt SMBSrvPacket
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected static final void procTrans2QueryFile(SMBSrvSession sess, VirtualCircuit vc, SrvTransactBuffer tbuf,
                                                    SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException {

        // Get the tree connection details
        int treeId = tbuf.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the file id and query path information level
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int fid = paramBuf.getShort();
        int infoLevl = paramBuf.getShort();

        // Get the file details via the file id
        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("IPC$ Query File - level=0x" + Integer.toHexString(infoLevl) + ", fid=" + fid + ", name="
                    + netFile.getFullName());

        // Access the shared device disk interface
        try {

            // Get the associated parser
            SMBV1Parser parser = (SMBV1Parser) smbPkt.getParser();

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
            // single reply packet.
            DataBuffer replyBuf = new DataBuffer(buf, dataPos, buf.length - dataPos);

            // Build the file information from the network file details
            FileInfo fileInfo = new FileInfo(netFile.getName(), netFile.getFileSize(), netFile.getFileAttributes());

            fileInfo.setAccessDateTime(netFile.getAccessDate());
            fileInfo.setCreationDateTime(netFile.getCreationDate());
            fileInfo.setModifyDateTime(netFile.getModifyDate());
            fileInfo.setChangeDateTime(netFile.getModifyDate());

            fileInfo.setFileId(netFile.getFileId());

            // Set the file allocation size, looks like it is used as the pipe buffer size
            fileInfo.setAllocationSize(4096L);

            // Pack the file information into the return data packet
            int dataLen = QueryInfoPacker.packInfo(fileInfo, replyBuf, infoLevl, true);

            // Check if any data was packed, if not then the information level is not supported
            if (dataLen == 0) {
                sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
                return;
            }

            SMBSrvTransPacket.initTransactReply(smbPkt, 2, prmPos, dataLen, dataPos);
            parser.setByteCount(replyBuf.getPosition() - parser.getByteOffset());

            // Send the transact reply
            sess.sendResponseSMB(smbPkt);
        }
        catch (FileNotFoundException ex) {

            // Requested file does not exist
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }
        catch (PathNotFoundException ex) {

            // Requested path does not exist
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }
        catch (UnsupportedInfoLevelException ex) {

            // Requested information level is not supported
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }
        catch (DiskOfflineException ex) {

            // Filesystem is offline
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectPathNotFound, SMBStatus.HRDDriveNotReady, SMBStatus.ErrHrd);
        }
    }
}
