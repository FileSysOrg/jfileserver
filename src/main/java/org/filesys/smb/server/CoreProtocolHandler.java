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
import java.util.EnumSet;

import org.filesys.debug.Debug;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.server.auth.ISMBAuthenticator;
import org.filesys.server.auth.InvalidUserException;
import org.filesys.server.core.InvalidDeviceInterfaceException;
import org.filesys.server.core.ShareType;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.filesys.*;
import org.filesys.smb.*;
import org.filesys.smb.PacketTypeV1;
import org.filesys.util.DataPacker;
import org.filesys.util.WildCard;

/**
 * Core SMB protocol handler class.
 *
 * @author gkspencer
 */
class CoreProtocolHandler extends ProtocolHandler {

    // Special resume ids for '.' and '..' pseudo directories
    private static final int RESUME_START   = 0x00008003;
    private static final int RESUME_DOT     = 0x00008002;
    private static final int RESUME_DOTDOT  = 0x00008001;

    // Maximum value that can be stored in a parameter word
    private static final int MaxWordValue = 0x0000FFFF;

    // File attribute mask, for standard attributes only
    protected static final int StandardAttributes = 0x3F;

    // Search information per file length
    protected static final int SearchInfoLen = 43;

    // Default SMB flags and flags2, ORed with the SMB packet flags/flags2 before sending a
    // response to the client.
    private int m_defFlags;
    private int m_defFlags2;

    /**
     * Create a new core SMB protocol handler.
     */
    protected CoreProtocolHandler() {
    }

    /**
     * Class constructor
     *
     * @param sess SMBSrvSession
     */
    protected CoreProtocolHandler(SMBSrvSession sess) {
        super(sess);
    }

    /**
     * Return the protocol name
     *
     * @return String
     */
    public String getName() {
        return "Core Protocol";
    }

    /**
     * Map a Java exception class to an SMB error code, and return an error response to the caller.
     *
     * @param ex java.lang.Exception
     */
    protected final void MapExceptionToSMBError(Exception ex) {

    }

    /**
     * Return the default flags SMB header value
     *
     * @return int
     */
    public final int getDefaultFlags() {
        return m_defFlags;
    }

    /**
     * Return the default flags2 SMB header value
     *
     * @return int
     */
    public final int getDefaultFlags2() {
        return m_defFlags2;
    }

    /**
     * Set the default flags value to be ORed with outgoing response packet flags
     *
     * @param flags int
     */
    public final void setDefaultFlags(int flags) {
        m_defFlags = flags;
    }

    /**
     * Set the default flags2 value to be ORed with outgoing response packet flags2 field
     *
     * @param flags int
     */
    public final void setDefaultFlags2(int flags) {
        m_defFlags2 = flags;
    }

    /**
     * Pack file information for a search into the specified buffer.
     *
     * @param buf       byte[] Buffer to store data.
     * @param bufPos    int Position to start storing data.
     * @param searchStr Search context string.
     * @param resumeId  int Resume id
     * @param searchId  Search context id
     * @param info      File data to be packed.
     * @return int Next available buffer position.
     */
    protected final int packSearchInfo(byte[] buf, int bufPos, String searchStr, int resumeId, int searchId, FileInfo info) {

        // Pack the resume key
        CoreResumeKey.putResumeKey(buf, bufPos, searchStr, resumeId + (searchId << 16));
        bufPos += CoreResumeKey.LENGTH;

        // Pack the file information
        buf[bufPos++] = (byte) (info.getFileAttributes() & StandardAttributes);

        SMBDate dateTime = new SMBDate(info.getModifyDateTime());
        if (dateTime != null) {
            DataPacker.putIntelShort(dateTime.asSMBTime(), buf, bufPos);
            DataPacker.putIntelShort(dateTime.asSMBDate(), buf, bufPos + 2);
        }
        else {
            DataPacker.putIntelShort(0, buf, bufPos);
            DataPacker.putIntelShort(0, buf, bufPos + 2);
        }
        bufPos += 4;

        DataPacker.putIntelInt((int) info.getSize(), buf, bufPos);
        bufPos += 4;

        StringBuilder str = new StringBuilder();

        str.append(info.getFileName());

        while (str.length() < 13)
            str.append('\0');

        if (str.length() > 12)
            str.setLength(12);

        DataPacker.putString(str.toString().toUpperCase(), buf, bufPos, true);
        bufPos += 13;

        // Return the new buffer position
        return bufPos;
    }

    /**
     * Check if the specified path exists, and is a directory.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procCheckDirectory(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid check directory request
        if ( parser.checkPacketIsValid(0, 2) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit( parser.getUserId());
        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid connection id.
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the data bytes position and length
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the directory name
        String dirName = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, parser.isUnicode());
        if (dirName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("Directory Check [" + treeId + "] name=" + dirName);

        // Access the disk interface and check for the directory
        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Check that the specified path exists, and it is a directory
            if (disk.fileExists(m_sess, conn, dirName) == FileStatus.DirectoryExists) {

                // The path exists and is a directory, build the valid path response.
                parser.setParameterCount(0);
                parser.setByteCount(0);

                // Send the response packet
                m_sess.sendResponseSMB(smbPkt);
            }
            else {

                // The path does not exist, or is not a directory.
                //
                // DOS clients depend on the 'Directory Invalid' (SMB_ERR_BAD_PATH) message being
                // returned.
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSDirectoryInvalid, SMBStatus.ErrDos);
            }
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {

            // Failed to delete the directory
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSDirectoryInvalid, SMBStatus.ErrDos);
            return;
        }
    }

    /**
     * Close a file that has been opened on the server.
     *
     * @param smbPkt Response SMB packet.
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procCloseFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid file close request
        if ( parser.checkPacketIsValid(3, 0) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit( parser.getUserId());
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
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
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
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("File close [" + treeId + "] fid=" + fid);

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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {
        }

        // Remove the file from the connections list of open files
        conn.removeFile(fid, getSession());

        // Build the close file response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Create a new directory.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procCreateDirectory(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid create directory request
        if ( parser.checkPacketIsValid(0, 2) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit( parser.getUserId());
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

        // Get the data bytes position and length
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the directory name
        String dirName = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, parser.isUnicode());
        if (dirName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Check if the file name is valid
        if (FileName.isValidPath(dirName) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameInvalid, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("Directory Create [" + treeId + "] name=" + dirName);

        // Access the disk interface and create the new directory
        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Directory creation parameters
            FileOpenParams params = new FileOpenParams(dirName, FileAction.CreateNotExist, AccessMode.ReadWrite,
                    FileAttribute.NTDirectory, parser.getProcessIdFull());

            // Create the new directory
            disk.createDirectory(m_sess, conn, params);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (FileExistsException ex) {

            // Failed to create the directory
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameCollision, SMBStatus.DOSFileAlreadyExists, SMBStatus.ErrDos);
            return;
        }
        catch (AccessDeniedException ex) {

            // Not allowed to create directory
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTAccessDenied, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {

            // Failed to create the directory
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSDirectoryInvalid, SMBStatus.ErrDos);
            return;
        }

        // Build the create directory response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Create a new file on the server.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procCreateFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid file create request
        if ( parser.checkPacketIsValid(3, 2) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit( parser.getUserId());
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

        // Get the data bytes position and length
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the file name
        String fileName = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, parser.isUnicode());
        if (fileName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Check if the file name is valid
        if (FileName.isValidPath(fileName) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameInvalid, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Get the required file attributes for the new file
        int attr = parser.getParameter(0);

        // Create the file parameters to be passed to the disk interface
        FileOpenParams params = new FileOpenParams(fileName, FileAction.CreateNotExist, AccessMode.ReadWrite, attr, parser.getProcessIdFull());

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("File Create [" + treeId + "] params=" + params);

        // Access the disk interface and create the new file
        int fid;
        NetworkFile netFile = null;

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Create the new file
            netFile = disk.createFile(m_sess, conn, params);

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
        catch (FileExistsException ex) {

            // File with the requested name already exists
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileAlreadyExists, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {

            // Failed to open the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }

        // Build the create file response
        parser.setParameterCount(1);
        parser.setParameter(0, fid);
        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Create a temporary file.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procCreateTemporaryFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {
    }

    /**
     * Delete a directory.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procDeleteDirectory(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid delete directory request
        if ( parser.checkPacketIsValid(0, 2) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit( parser.getUserId());
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

        // Get the data bytes position and length
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the directory name
        String dirName = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, parser.isUnicode());

        if (dirName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Check if the file name is valid
        if (FileName.isValidPath(dirName) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameInvalid, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSDirectoryNotEmpty, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {

            // Failed to delete the directory
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSDirectoryInvalid, SMBStatus.ErrDos);
            return;
        }

        // Build the delete directory response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Delete a file.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procDeleteFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid file delete request
        if ( parser.checkPacketIsValid(1, 2) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit( parser.getUserId());
        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid connection id.
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

        // Get the data bytes position and length
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the file name
        String fileName = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, parser.isUnicode());
        if (fileName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Check if the file name is valid
        if (FileName.isValidPath(fileName) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTObjectNameInvalid, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("File Delete [" + treeId + "] name=" + fileName);

        // Access the disk interface and delete the file(s)
        int fid;
        NetworkFile netFile = null;

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Delete file(s)
            disk.deleteFile(m_sess, conn, fileName);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {

            // Failed to open the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }

        // Build the delete file response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Get disk attributes processing.
     *
     * @param smbPkt Response SMB packet.
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procDiskAttributes(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.INFO))
            m_sess.debugPrintln("Get disk attributes");

        // Parameter and byte count should be zero
        if ( parser.getParameterCount() != 0 && parser.getByteCount() != 0) {

            // Send an error response
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the disk interface from the shared device
        DiskInterface disk = null;
        DiskDeviceContext diskCtx = null;

        try {
            disk = (DiskInterface) conn.getSharedDevice().getInterface();
            diskCtx = (DiskDeviceContext) conn.getContext();
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Create a disk information object and ask the disk interface to fill in the details
        SrvDiskInfo diskInfo = getDiskInformation(disk, diskCtx);

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.INFO))
            m_sess.debugPrintln("  Disk info - total=" + diskInfo.getTotalUnits() + ", free=" + diskInfo.getFreeUnits()
                    + ", blocksPerUnit=" + diskInfo.getBlocksPerAllocationUnit() + ", blockSize=" + diskInfo.getBlockSize());

        // Check if the disk size information needs scaling to fit into 16bit values
        long totUnits = diskInfo.getTotalUnits();
        long freeUnits = diskInfo.getFreeUnits();
        int blocksUnit = diskInfo.getBlocksPerAllocationUnit();

        while (totUnits > MaxWordValue && blocksUnit <= MaxWordValue) {

            // Increase the blocks per unit and decrease the total/free units
            blocksUnit *= 2;

            totUnits = totUnits / 2L;
            freeUnits = freeUnits / 2L;
        }

        // Check if the total/free units fit into a 16bit value
        if (totUnits > MaxWordValue || blocksUnit > MaxWordValue) {

            // Just use dummy values, cannot fit the disk size into 16bits
            totUnits = MaxWordValue;

            if (freeUnits > MaxWordValue)
                freeUnits = MaxWordValue / 2;

            if (blocksUnit > MaxWordValue)
                blocksUnit = MaxWordValue;
        }

        // Build the reply SMB
        parser.setParameterCount(5);

        parser.setParameter(0, (int) totUnits);
        parser.setParameter(1, blocksUnit);
        parser.setParameter(2, diskInfo.getBlockSize());
        parser.setParameter(3, (int) freeUnits);
        parser.setParameter(4, 0);

        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Echo packet request.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procEcho(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid echo request
        if ( parser.checkPacketIsValid(1, 0) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the echo count from the request
        int echoCnt = parser.getParameter(0);

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.ECHO))
            m_sess.debugPrintln("Echo - Count = " + echoCnt);

        // Loop until all echo packets have been sent
        int echoSeq = 1;

        while (echoCnt > 0) {

            // Set the echo response sequence number
            parser.setParameter(0, echoSeq++);

            // Echo the received packet
            m_sess.sendResponseSMB(smbPkt);
            echoCnt--;

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.ECHO))
                m_sess.debugPrintln("Echo Packet, Seq = " + echoSeq);
        }
    }

    /**
     * Flush the specified file.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procFlushFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid file flush request
        if (parser.checkPacketIsValid(1, 0) == false) {
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

        // Get the file id from the request
        int fid = parser.getParameter(0);

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("File Flush [" + netFile.getFileId() + "]");

        // Flush the file
        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Flush the file
            disk.flushFile(m_sess, conn, netFile);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {

            // Debug
            if (Debug.EnableError && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
                m_sess.debugPrintln("File Flush Error [" + netFile.getFileId() + "] : " + ex.toString());

            // Failed to flush the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.HRDWriteFault, SMBStatus.ErrHrd);
            return;
        }

        // Send the flush response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Get the file attributes for the specified file.
     *
     * @param smbPkt Response SMB packet.
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procGetFileAttributes(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid query file information request
        if (parser.checkPacketIsValid(0, 2) == false) {
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
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the data bytes position and length
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the file name
        String fileName = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, parser.isUnicode());
        if (fileName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("Get File Information [" + treeId + "] name=" + fileName);

        // Access the disk interface and get the file information
        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Get the file information for the specified file/directory
            FileInfo finfo = disk.getFileInformation(m_sess, conn, fileName);
            if (finfo != null) {

                // Mask the file attributes
                finfo.setFileAttributes(finfo.getFileAttributes() & StandardAttributes);

                // Check if the share is read-only, if so then force the read-only flag for the file
                if (conn.getSharedDevice().isReadOnly() && finfo.isReadOnly() == false) {

                    // Make sure the read-only attribute is set
                    finfo.setFileAttributes(finfo.getFileAttributes() + FileAttribute.ReadOnly);
                }

                // Return the file information
                parser.setParameterCount(10);
                parser.setParameter(0, finfo.getFileAttributes());
                if (finfo.getModifyDateTime() != 0L) {
                    SMBDate dateTime = new SMBDate(finfo.getModifyDateTime());
                    parser.setParameter(1, dateTime.asSMBTime());
                    parser.setParameter(2, dateTime.asSMBDate());
                }
                else {
                    parser.setParameter(1, 0);
                    parser.setParameter(2, 0);
                }
                parser.setParameter(3, (int) finfo.getSize() & 0x0000FFFF);
                parser.setParameter(4, (int) (finfo.getSize() & 0xFFFF0000) >> 16);

                for (int i = 5; i < 10; i++)
                    parser.setParameter(i, 0);

                parser.setByteCount(0);

                // Send the response packet
                m_sess.sendResponseSMB(smbPkt);
                return;
            }
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {
        }

        // Failed to get the file information
        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
    }

    /**
     * Get file information.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procGetFileInformation(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid query file information2 request
        if (parser.checkPacketIsValid(1, 0) == false) {
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
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the file id from the request
        int fid = parser.getParameter(0);
        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("Get File Information 2 [" + netFile.getFileId() + "]");

        // Access the disk interface and get the file information
        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Get the file information for the specified file/directory
            FileInfo finfo = disk.getFileInformation(m_sess, conn, netFile.getFullName());
            if (finfo != null) {

                // Mask the file attributes
                finfo.setFileAttributes(finfo.getFileAttributes() & StandardAttributes);

                // Check if the share is read-only, if so then force the read-only flag for the file
                if (conn.getSharedDevice().isReadOnly() && finfo.isReadOnly() == false) {

                    // Make sure the read-only attribute is set
                    finfo.setFileAttributes(finfo.getFileAttributes() + FileAttribute.ReadOnly);
                }

                // Initialize the return packet, no data bytes
                parser.setParameterCount(11);
                parser.setByteCount(0);

                // Return the file information
                //
                // Creation date/time
                SMBDate dateTime = new SMBDate(0);

                if (finfo.getCreationDateTime() != 0L) {
                    dateTime.setTime(finfo.getCreationDateTime());
                    parser.setParameter(0, dateTime.asSMBDate());
                    parser.setParameter(1, dateTime.asSMBTime());
                }
                else {
                    parser.setParameter(0, 0);
                    parser.setParameter(1, 0);
                }

                // Access date/time
                if (finfo.getAccessDateTime() != 0L) {
                    dateTime.setTime(finfo.getAccessDateTime());
                    parser.setParameter(2, dateTime.asSMBDate());
                    parser.setParameter(3, dateTime.asSMBTime());
                }
                else {
                    parser.setParameter(2, 0);
                    parser.setParameter(3, 0);
                }

                // Modify date/time
                if (finfo.getModifyDateTime() != 0L) {
                    dateTime.setTime(finfo.getModifyDateTime());
                    parser.setParameter(4, dateTime.asSMBDate());
                    parser.setParameter(5, dateTime.asSMBTime());
                }
                else {
                    parser.setParameter(4, 0);
                    parser.setParameter(5, 0);
                }

                // File data size
                parser.setParameter(6, (int) finfo.getSize() & 0x0000FFFF);
                parser.setParameter(7, (int) (finfo.getSize() & 0xFFFF0000) >> 16);

                // File allocation size
                parser.setParameter(8, (int) finfo.getSize() & 0x0000FFFF);
                parser.setParameter(9, (int) (finfo.getSize() & 0xFFFF0000) >> 16);

                // File attributes
                parser.setParameter(10, finfo.getFileAttributes());

                // Send the response packet
                m_sess.sendResponseSMB(smbPkt);
                return;
            }
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {
        }

        // Failed to get the file information
        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
    }

    /**
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procLockFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid lock file request
        if (parser.checkPacketIsValid(5, 0) == false) {
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
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the file id from the request
        int fid = parser.getParameter(0);
        long lockcnt = parser.getParameterLong(1);
        long lockoff = parser.getParameterLong(3);

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILEIO))
            m_sess.debugPrintln("File Lock [" + netFile.getFileId() + "] : Offset=" + lockoff + " ,Count=" + lockcnt);

        // ***** Always return a success status, simulated locking ****
        //
        // Build the lock file response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Open a file on the server.
     *
     * @param smbPkt Response SMB packet.
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procOpenFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws IOException, SMBSrvException {

        // Check that the received packet looks like a valid file open request
        if (parser.checkPacketIsValid(2, 2) == false) {
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
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the data bytes position and length
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the file name
        String fileName = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, parser.isUnicode());
        if (fileName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Get the required access mode and the file attributes
        int mode = parser.getParameter(0);
        int attr = parser.getParameter(1);

        // Create the file open parameters to be passed to the disk interface
        FileOpenParams params = new FileOpenParams(fileName, mode, AccessMode.ReadWrite, attr, parser.getProcessIdFull());

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("File Open [" + treeId + "] params=" + params);

        // Access the disk interface and open the requested file
        int fid;
        NetworkFile netFile = null;

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Open the requested file
            netFile = disk.openFile(m_sess, conn, params);

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

            // File is not accessible, or file is actually a directory
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }
        catch (FileSharingException ex) {

            // Return a sharing violation error
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileSharingConflict, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {

            // Failed to open the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSFileNotFound, SMBStatus.ErrDos);
            return;
        }

        // Build the open file response
        parser.setParameterCount(7);

        parser.setParameter(0, fid);
        parser.setParameter(1, 0); // file attributes

        if (netFile.hasModifyDate()) {
            parser.setParameterLong(2, (int) (netFile.getModifyDate() / 1000L));
        }
        else
            parser.setParameterLong(2, 0);

        parser.setParameterLong(4, netFile.getFileSizeInt()); // file size
        parser.setParameter(6, netFile.getGrantedAccess().intValue());

        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Process exit, close all open files.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procProcessExit(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid process exit request
        if (parser.checkPacketIsValid(0, 0) == false) {
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

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("Process Exit - Open files = " + conn.openFileCount());

        // Close all open files
        if (conn.openFileCount() > 0) {

            // Close all files on the connection
            conn.closeConnection(getSession());
        }

        // Build the process exit response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Read from a file that has been opened on the server.
     *
     * @param smbPkt Response SMB packet.
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procReadFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid file read request
        if (parser.checkPacketIsValid(5, 0) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());
        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid connection id.
        int treeId = parser.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Check if the user has the required access permission
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the file id from the request
        int fid = parser.getParameter(0);
        int reqcnt = parser.getParameter(1);
        int reqoff = parser.getParameterLong(2);

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILEIO))
            m_sess.debugPrintln("File Read [" + netFile.getFileId() + "] : Size=" + reqcnt + " ,Pos=" + reqoff);

        // Read data from the file
        SMBSrvPacket respPkt = smbPkt;

        byte[] buf = respPkt.getBuffer();
        int rdlen = 0;

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Calculate the buffer size required for the response
            int dataOff = parser.getByteOffset() + 3;

            if (m_sess.hasClientCapability(Capability.V1LargeRead) == false) {

                // Make sure the requested count is not larger than the client maximum buffer size
                int maxClientCnt = m_sess.getClientMaximumBufferSize() - dataOff;

                if (reqcnt > maxClientCnt)
                    reqcnt = maxClientCnt;

                // Debug
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILEIO))
                    m_sess.debugPrintln("File Read [" + netFile.getFileId() + "] Limited to " + reqcnt);
            }

            // Check if the requested data will fit into the current packet
            if (reqcnt > (buf.length - dataOff)) {

                // Allocate a larger packet for the response
                respPkt = m_sess.getPacketPool().allocatePacket(reqcnt + dataOff, smbPkt);

                // Switch to the response buffer
                buf = respPkt.getBuffer();

                // Setup the response SMB parser
                respPkt.setParser( SMBSrvPacket.Version.V1);
                parser = (SMBV1Parser) respPkt.getParser();
            }

            // Read from the file
            rdlen = disk.readFile(m_sess, conn, netFile, buf, parser.getByteOffset() + 3, reqcnt, reqoff);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {

            // Debug
            if (Debug.EnableError && m_sess.hasDebug(SMBSrvSession.Dbg.FILEIO))
                m_sess.debugPrintln("File Read Error [" + netFile.getFileId() + "] : " + ex.toString());

            // Failed to read the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.HRDReadFault, SMBStatus.ErrHrd);
            return;
        }

        // Return the data block
        int bytOff = parser.getByteOffset();
        buf[bytOff] = (byte) DataType.DataBlock;
        DataPacker.putIntelShort(rdlen, buf, bytOff + 1);
        parser.setByteCount(rdlen + 3); // data type + 16bit length

        parser.setParameter(0, rdlen);
        parser.setParameter(1, 0);
        parser.setParameter(2, 0);
        parser.setParameter(3, 0);
        parser.setParameter(4, 0);

        // Send the read response
        m_sess.sendResponseSMB(respPkt);
    }

    /**
     * Rename a file.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procRenameFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

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

        // Get the data bytes position and length
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the old file name
        boolean isUni = parser.isUnicode();
        String oldName = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, isUni);
        if (oldName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Update the data position
        if (isUni) {
            int len = (oldName.length() * 2) + 2;
            dataPos = DataPacker.wordAlign(dataPos + 1) + len;
            dataLen -= len;
        }
        else {
            dataPos += oldName.length() + 2; // string length + null + data type
            dataLen -= oldName.length() + 2;
        }

        // Extract the new file name
        String newName = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, isUni);
        if (newName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("File Rename [" + treeId + "] old name=" + oldName + ", new name=" + newName);

        // Access the disk interface and rename the requested file
        int fid;
        NetworkFile netFile = null;

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Rename the requested file
            disk.renameFile(m_sess, conn, oldName, newName, null);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {

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
     * Start/continue a directory search operation.
     *
     * @param smbPkt Response SMB packet.
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected final void procSearch(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid search request
        if (parser.checkPacketIsValid(2, 5) == false) {
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

        // Get the maximum number of entries to return and the search file attributes
        int maxFiles = parser.getParameter(0);
        int srchAttr = parser.getParameter(1);

        // Check if this is a volume label request
        if ((srchAttr & FileAttribute.Volume) != 0) {

            // Process the volume label request
            procSearchVolumeLabel(smbPkt, parser);
            return;
        }

        // Get the data bytes position and length
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the search file name
        String srchPath = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, parser.isUnicode());

        if (srchPath == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidFunc, SMBStatus.ErrDos);
            return;
        }

        // Update the received data position
        dataPos += srchPath.length() + 2;
        dataLen -= srchPath.length() + 2;

        int resumeLen = 0;

        if (buf[dataPos++] == DataType.VariableBlock) {

            // Extract the resume key length
            resumeLen = DataPacker.getIntelShort(buf, dataPos);

            // Adjust remaining the data length and position
            dataLen -= 3; // block type + resume key length short
            dataPos += 2; // resume key length short

            // Check that we received enough data
            if (resumeLen > dataLen) {
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
                return;
            }
        }

        // Access the shared devices disk interface
        SearchContext ctx = null;
        DiskInterface disk = null;

        try {
            disk = (DiskInterface) conn.getSharedDevice().getInterface();
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Check if this is the start of a new search
        byte[] resumeKey = null;
        int searchId = -1;

        // Default resume point is at the start of the directory, at the '.' directory if
        // directories are being returned.
        int resumeId = RESUME_START;

        if (resumeLen == 0 && srchPath.length() > 0) {

            try {
                // Allocate a search slot for the new search
                searchId = vc.allocateSearchSlot();
            }
            catch ( TooManySearchesException ex) {

                // Try and find any 'leaked' searches, ie. searches that have been started but not closed.
                //
                // Windows Explorer seems to leak searches after a new folder has been created, a
                // search for '????????.???' is started but never continued.
                int idx = 0;
                ctx = vc.getSearchContext(idx);

                while (ctx != null && searchId == -1) {

                    // Check if the current search context looks like a leaked search.
                    if (ctx.getSearchString().compareTo("????????.???") == 0) {

                        // Debug
                        if (Debug.EnableWarn && m_sess.hasDebug(SMBSrvSession.Dbg.SEARCH))
                            m_sess.debugPrintln("Release leaked search [" + idx + "]");

                        // Deallocate the search context
                        vc.deallocateSearchSlot(idx);

                        try {
                            // Allocate the slot for the new search
                            searchId = vc.allocateSearchSlot();
                        }
                        catch ( TooManySearchesException ex2) {

                            // Failed to allocate a slot for the new search
                            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNoResourcesAvailable, SMBStatus.ErrSrv);
                            return;
                        }
                    }
                    else {

                        // Update the search index and get the next search context
                        ctx = vc.getSearchContext(++idx);
                    }
                }
            }

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.SEARCH))
                m_sess.debugPrintln("Start search [" + searchId + "] - " + srchPath + ", attr=0x" + Integer.toHexString(srchAttr)
                        + ", maxFiles=" + maxFiles);

            // Start a new search
            ctx = disk.startSearch(m_sess, conn, srchPath, srchAttr, EnumSet.noneOf( SearchFlags.class));
            if (ctx != null) {

                // Store details of the search in the context
                ctx.setTreeId(treeId);
                ctx.setMaximumFiles(maxFiles);
            }

            // Save the search context
            vc.setSearchContext(searchId, ctx);
        }
        else {

            // Take a copy of the resume key
            resumeKey = new byte[CoreResumeKey.LENGTH];
            CoreResumeKey.getResumeKey(buf, dataPos, resumeKey);

            // Get the search context slot id from the resume key, and get the search context.
            int id = CoreResumeKey.getServerArea(resumeKey, 0);
            searchId = (id & 0xFFFF0000) >> 16;
            ctx = vc.getSearchContext(searchId);

            // Check if the search context is valid
            if (ctx == null) {
                m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
                return;
            }

            // Get the resume id from the resume key
            resumeId = id & 0x0000FFFF;

            // Restart the search at the resume point, check if the resume point is already set, ie.
            // we are just continuing the search.
            if (resumeId < RESUME_DOTDOT && ctx.getResumeId() != resumeId) {

                // Debug
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.SEARCH))
                    m_sess.debugPrintln("Search resume at " + resumeId);

                // Restart the search at the specified point
                if (ctx.restartAt(resumeId) == false) {

                    // Debug
                    if (Debug.EnableError && m_sess.hasDebug(SMBSrvSession.Dbg.SEARCH))
                        m_sess.debugPrintln("Search restart failed");

                    // Failed to restart the search
                    m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSNoMoreFiles, SMBStatus.ErrDos);

                    // Release the search context
                    vc.deallocateSearchSlot(searchId);
                    return;
                }
            }
        }

        // Check if the search context is valid
        if (ctx == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Check that the search context and tree connection match
        if (ctx.getTreeId() != treeId) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVInvalidTID, SMBStatus.ErrSrv);
            return;
        }

        // Check if this is a wildcard search
        boolean wildcardSearch = WildCard.containsWildcards(srchPath);

        // Calculate the response packet length required
        SMBSrvPacket respPkt = smbPkt;

        if (wildcardSearch == true) {

            // Allocate a response packet large enough to hold the maximum requested file info records
            int pktSize = parser.calculateHeaderLength(1) + (maxFiles * SearchInfoLen);
            respPkt = m_sess.getPacketPool().allocatePacket(pktSize, smbPkt);

            // Switch to using the new packet buffer
            buf = respPkt.getBuffer();

            // Setup a parser for the response
            respPkt.setParser( SMBSrvPacket.Version.V1);
            parser = (SMBV1Parser) respPkt.getParser();
        }

        // Start building the search response packet
        parser.setParameterCount(1);
        int bufPos = parser.getByteOffset();
        buf[bufPos] = (byte) DataType.VariableBlock;
        bufPos += 3; // save two bytes for the actual block length
        int fileCnt = 0;

        // Check if this is the start of a wildcard search and includes directories
        if ((srchAttr & FileAttribute.Directory) != 0 && resumeId >= RESUME_DOTDOT && wildcardSearch == true) {

            // The first entries in the search should be the '.' and '..' entries for the
            // current/parent directories.
            //
            // Remove the file name from the search path, and get the file information for the
            // search directory.
            String workDir = FileName.removeFileName(srchPath);
            FileInfo dirInfo = disk.getFileInformation(m_sess, conn, workDir);

            // Check if we have valid information for the working directory
            if (dirInfo != null)
                dirInfo = new FileInfo(".", 0, FileAttribute.Directory);

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.SEARCH))
                m_sess.debugPrintln("Search adding . and .. entries:  " + dirInfo.toString());

            // Reset the file name to '.' and pack the directory information
            if (resumeId == RESUME_START) {

                // Pack the '.' file information
                dirInfo.setFileName(".");
                resumeId = RESUME_DOT;
                bufPos = packSearchInfo(buf, bufPos, ctx.getSearchString(), RESUME_DOT, searchId, dirInfo);

                // Update the file count
                fileCnt++;
            }

            // Reset the file name to '..' and pack the directory information
            if (resumeId == RESUME_DOT) {

                // Pack the '..' file information
                dirInfo.setFileName("..");
                bufPos = packSearchInfo(buf, bufPos, ctx.getSearchString(), RESUME_DOTDOT, searchId, dirInfo);

                // Update the file count
                fileCnt++;
            }
        }

        // Get files from the search and pack into the return packet
        FileInfo fileInfo = new FileInfo();

        while (fileCnt < ctx.getMaximumFiles() && ctx.nextFileInfo(fileInfo) == true) {

            // Check for . files, ignore them.
            //
            // ** Should check for . and .. file names **
            if (fileInfo.getFileName().startsWith("."))
                continue;

            // Get the resume id for the current file/directory
            resumeId = ctx.getResumeId();

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.SEARCH))
                m_sess.debugPrintln("Search return file " + fileInfo.toString() + ", resumeId=" + resumeId);

            // Check if the share is read-only, if so then force the read-only flag for the file
            if (conn.getSharedDevice().isReadOnly() && fileInfo.isReadOnly() == false) {

                // Make sure the read-only attribute is set
                fileInfo.setFileAttributes(fileInfo.getFileAttributes() + FileAttribute.ReadOnly);
            }

            // Pack the file information
            bufPos = packSearchInfo(buf, bufPos, ctx.getSearchString(), resumeId, searchId, fileInfo);

            // Update the file count, reset the current file information
            fileCnt++;
            fileInfo.resetInfo();
        }

        // Check if any files were found
        if (fileCnt == 0) {

            // Send a repsonse that indicates that the search has finished
            parser.setParameterCount(1);
            parser.setParameter(0, 0);
            parser.setByteCount(0);

            parser.setErrorClass(SMBStatus.ErrDos);
            parser.setErrorCode(SMBStatus.DOSNoMoreFiles);

            m_sess.sendResponseSMB(respPkt);

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.SEARCH))
                m_sess.debugPrintln("End search [" + searchId + "]");

            // Release the search context
            vc.deallocateSearchSlot(searchId);
        }
        else {

            // Set the actual data length
            dataLen = bufPos - parser.getByteOffset();
            parser.setByteCount(dataLen);

            // Set the variable data block length and returned file count parameter
            bufPos = parser.getByteOffset() + 1;
            DataPacker.putIntelShort(dataLen - 3, buf, bufPos);
            parser.setParameter(0, fileCnt);

            // Send the search response packet
            m_sess.sendResponseSMB(respPkt);

            // Check if the search string contains wildcards and this is the start of a new search,
            // if not then release the search context now as the client will not continue the search.
            if (fileCnt == 1 && resumeLen == 0 && WildCard.containsWildcards(srchPath) == false) {

                // Debug
                if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.SEARCH))
                    m_sess.debugPrintln("End search [" + searchId + "] (Not wildcard)");

                // Release the search context
                vc.deallocateSearchSlot(searchId);
            }
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.TXDATA))
            m_sess.debugPrintln("Tx " + parser.getLength() + " bytes");
    }

    /**
     * Process a search request that is for the volume label.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procSearchVolumeLabel(SMBSrvPacket smbPkt, SMBV1Parser parser)
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

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.SEARCH))
            m_sess.debugPrintln("Start Search - Volume Label");

        // Access the shared devices disk interface
        DiskInterface disk = null;
        DiskDeviceContext diskCtx = null;

        try {
            disk = (DiskInterface) conn.getSharedDevice().getInterface();
            diskCtx = (DiskDeviceContext) conn.getContext();
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Get the volume label
        VolumeInfo volInfo = diskCtx.getVolumeInformation();
        String volLabel = "";
        if (volInfo != null)
            volLabel = volInfo.getVolumeLabel();

        // Start building the search response packet
        parser.setParameterCount(1);
        int bufPos = parser.getByteOffset();
        byte[] buf = parser.getBuffer();
        buf[bufPos++] = (byte) DataType.VariableBlock;

        // Calculate the data length
        int dataLen = CoreResumeKey.LENGTH + 22;
        DataPacker.putIntelShort(dataLen, buf, bufPos);
        bufPos += 2;

        // Pack the resume key
        CoreResumeKey.putResumeKey(buf, bufPos, volLabel, -1);
        bufPos += CoreResumeKey.LENGTH;

        // Pack the file information
        buf[bufPos++] = (byte) (FileAttribute.Volume & 0x00FF);

        // Zero the date/time and file length fields
        for (int i = 0; i < 8; i++)
            buf[bufPos++] = (byte) 0;

        StringBuffer volBuf = new StringBuffer();
        volBuf.append(volLabel);

        while (volBuf.length() < 13)
            volBuf.append(" ");

        if (volBuf.length() > 12)
            volBuf.setLength(12);

        bufPos = DataPacker.putString(volBuf.toString().toUpperCase(), buf, bufPos, true);

        // Set the actual data length
        dataLen = bufPos - parser.getByteOffset();
        parser.setByteCount(dataLen);

        // Send the search response packet
        m_sess.sendResponseSMB(smbPkt);

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.SEARCH))
            m_sess.debugPrintln("Volume label for " + conn.toString() + " is " + volLabel);
    }

    /**
     * Seek to the specified file position within the open file.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final void procSeekFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid file seek request
        if (parser.checkPacketIsValid(4, 0) == false) {
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
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the file id from the request
        int fid = parser.getParameter(0);
        int seekMode = parser.getParameter(1);
        long seekPos = (long) parser.getParameterLong(2);

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("File Seek [" + netFile.getFileId() + "] : Mode = " + seekMode + ", Pos = " + seekPos);

        // Seek to the specified position within the file
        byte[] buf = parser.getBuffer();
        long pos = 0;

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Seek to the file position
            pos = disk.seekFile(m_sess, conn, netFile, seekPos, seekMode);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (IOException ex) {

            // Debug
            if (Debug.EnableError && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
                m_sess.debugPrintln("File Seek Error [" + netFile.getFileId() + "] : " + ex.toString());

            // Failed to seek the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.HRDReadFault, SMBStatus.ErrHrd);
            return;
        }

        // Return the new file position
        parser.setParameterCount(2);
        parser.setParameterLong(0, (int) (pos & 0x0FFFFFFFFL));
        parser.setByteCount(0);

        // Send the seek response
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Process the SMB session setup request.
     *
     * @param smbPkt Response SMB packet.
     * @param parser SMBV1Parser
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     * @exception TooManyConnectionsException No more free connections available
     */
    protected void procSessionSetup(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws SMBSrvException, IOException, TooManyConnectionsException {

        // Return an access denied error, require a logon
        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
    }

    /**
     * Set the file attributes for a file.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procSetFileAttributes(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid set file attributes request
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

        // Get the data bytes position and length
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the file name
        String fileName = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, parser.isUnicode());
        if (fileName == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Get the file attributes
        int fattr = parser.getParameter(0);
        int setFlags = FileInfo.SetAttributes;

        FileInfo finfo = new FileInfo(fileName, 0, fattr);

        int fdate = parser.getParameter(1);
        int ftime = parser.getParameter(2);

        if (fdate != 0 && ftime != 0) {
            finfo.setModifyDateTime(new SMBDate(fdate, ftime).getTime());
            setFlags += FileInfo.SetModifyDate;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("Set File Attributes [" + treeId + "] name=" + fileName + ", attr=0x"
                    + Integer.toHexString(fattr) + ", fdate=" + fdate + ", ftime=" + ftime);

        // Access the disk interface and set the file attributes
        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Get the file information for the specified file/directory
            finfo.setFileInformationFlags(setFlags);
            disk.setFileInformation(m_sess, conn, fileName, finfo);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {
        }

        // Return the set file attributes response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Set file information.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procSetFileInformation(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid set file information2 request
        if (parser.checkPacketIsValid(7, 0) == false) {
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

        // Get the file id from the request, and get the network file details.
        int fid = parser.getParameter(0);
        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Get the creation date/time from the request
        int setFlags = 0;
        FileInfo finfo = new FileInfo(netFile.getName(), 0, 0);

        int fdate = parser.getParameter(1);
        int ftime = parser.getParameter(2);

        if (fdate != 0 && ftime != 0) {
            finfo.setCreationDateTime(new SMBDate(fdate, ftime).getTime());
            setFlags += FileInfo.SetCreationDate;
        }

        // Get the last access date/time from the request
        fdate = parser.getParameter(3);
        ftime = parser.getParameter(4);

        if (fdate != 0 && ftime != 0) {
            finfo.setAccessDateTime(new SMBDate(fdate, ftime).getTime());
            setFlags += FileInfo.SetAccessDate;
        }

        // Get the last write date/time from the request
        fdate = parser.getParameter(5);
        ftime = parser.getParameter(6);

        if (fdate != 0 && ftime != 0) {
            finfo.setModifyDateTime(new SMBDate(fdate, ftime).getTime());
            setFlags += FileInfo.SetModifyDate;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILE))
            m_sess.debugPrintln("Set File Information 2 [" + netFile.getFileId() + "] " + finfo.toString());

        // Access the disk interface and set the file information
        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Store the associated network file in the file information object
            finfo.setNetworkFile(netFile);

            // Get the file information for the specified file/directory
            finfo.setFileInformationFlags(setFlags);
            disk.setFileInformation(m_sess, conn, netFile.getFullName(), finfo);
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {
        }

        // Return the set file information response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Process the SMB tree connect request.
     *
     * @param smbPkt Response SMB packet.
     * @param parser SMBV1Parser
     * @throws java.io.IOException         The exception description.
     * @throws SMBSrvException             The exception description.
     * @throws TooManyConnectionsException Too many concurrent connections
     *                                     on this session.
     */
    protected void procTreeConnect(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws SMBSrvException, TooManyConnectionsException, java.io.IOException {

        // Check that the received packet looks like a valid tree connect request
        if (parser.checkPacketIsValid(0, 4) == false) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the virtual circuit for the request
        VirtualCircuit vc = m_sess.findVirtualCircuit(parser.getUserId());
        if (vc == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
            return;
        }

        // Get the data bytes position and length
        int dataPos = parser.getByteOffset();
        int dataLen = parser.getByteCount();
        byte[] buf = parser.getBuffer();

        // Extract the requested share name, as a UNC path
        boolean isUni = parser.isUnicode();
        String uncPath = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, isUni);
        if (uncPath == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Extract the password string
        if (isUni) {
            dataPos = DataPacker.wordAlign(dataPos + 1) + (uncPath.length() * 2) + 2;
            dataLen -= (uncPath.length() * 2) + 2;
        }
        else {
            dataPos += uncPath.length() + 2;
            dataLen -= uncPath.length() + 2;
        }

        String pwd = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, isUni);
        if (pwd == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Extract the service type string
        if (isUni) {
            dataPos = DataPacker.wordAlign(dataPos + 1) + (pwd.length() * 2) + 2;
            dataLen -= (pwd.length() * 2) + 2;
        }
        else {
            dataPos += pwd.length() + 2;
            dataLen -= pwd.length() + 2;
        }

        String service = DataPacker.getDataString(DataType.ASCII, buf, dataPos, dataLen, isUni);
        if (service == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Convert the service type to a shared device type
        ShareType servType = ShareType.ServiceAsType(service);
        if (servType == ShareType.UNKNOWN) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.TREE))
            m_sess.debugPrintln("Tree connect - " + uncPath + ", " + service);

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
        if (shareDev == null || shareDev.getType() != servType) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Allocate a tree id for the new connection
        int treeId = vc.addConnection(shareDev);

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

        // Set the file permission that this user has been granted for this share
        TreeConnection tree = vc.findConnection(treeId);
        tree.setPermission(sharePerm);

        // Build the tree connect response
        parser.setParameterCount(2);

        parser.setParameter(0, buf.length - RFCNetBIOSProtocol.HEADER_LEN);
        parser.setParameter(1, treeId);
        parser.setByteCount(0);

        // Clear any chained request
        parser.setAndXCommand(0xFF);
        m_sess.sendResponseSMB(smbPkt);

        // Inform the driver that a connection has been opened
        if (tree.getInterface() != null)
            tree.getInterface().treeOpened(m_sess, tree);
    }

    /**
     * Process the SMB tree disconnect request.
     *
     * @param smbPkt Response SMB packet.
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procTreeDisconnect(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid tree disconnect request
        if (parser.checkPacketIsValid(0, 0) == false) {
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

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.TREE))
            m_sess.debugPrintln("Tree disconnect - " + treeId + ", " + conn.toString());

        // Remove the specified connection from the session
        vc.removeConnection(treeId, m_sess);

        // Check if this is the last tree connection on the virtual circuit and the virtual circuit is logged off
        if (vc.getConnectionCount() == 0 && vc.isLoggedOn() == false) {

            // Remove the virtual circuit
            m_sess.removeVirtualCircuit(vc.getId());

            // Debug
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.TREE))
                m_sess.debugPrintln("  Removed virtual circuit " + vc);
        }

        // Build the tree disconnect response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        m_sess.sendResponseSMB(smbPkt);

        // If there are no active virtual circuits then close the session/socket
        if (m_sess.numberOfVirtualCircuits() == 0) {

            // DEBUG
            if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.NEGOTIATE))
                Debug.println("  Closing session, no more virtual circuits");

            // Close the session/socket
            m_sess.hangupSession("Tree disconnect");
        }

        // Inform the driver that a connection has been closed
        if (conn.getInterface() != null)
            conn.getInterface().treeClosed(m_sess, conn);
    }

    /**
     * Unlock a byte range in the specified file.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procUnLockFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid unlock file request
        if (parser.checkPacketIsValid(5, 0) == false) {
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
        if (conn.hasReadAccess() == false) {

            // User does not have the required access rights
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSAccessDenied, SMBStatus.ErrDos);
            return;
        }

        // Get the file id from the request
        int fid = parser.getParameter(0);
        long lockcnt = parser.getParameterLong(1);
        long lockoff = parser.getParameterLong(3);

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILEIO))
            m_sess.debugPrintln("File UnLock [" + netFile.getFileId() + "] : Offset=" + lockoff + " ,Count=" + lockcnt);

        // ***** Always return a success status, simulated locking ****
        //
        // Build the unlock file response
        parser.setParameterCount(0);
        parser.setByteCount(0);

        // Send the response packet
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Unsupported SMB procesing.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected final void procUnsupported(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Send an unsupported error response
        m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
    }

    /**
     * Write to a file.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procWriteFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid file write request
        if (parser.checkPacketIsValid(5, 0) == false) {
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

        // Get the file id from the request
        int fid = parser.getParameter(0);
        int wrtcnt = parser.getParameter(1);
        long wrtoff = parser.getParameter(2) + ((parser.getParameter(3) << 16) & 0xFFFFFFFFL);

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILEIO))
            m_sess.debugPrintln("File Write [" + netFile.getFileId() + "] : Size=" + wrtcnt + " ,Pos=" + wrtoff);

        // Write data to the file
        byte[] buf = parser.getBuffer();
        int pos = parser.getByteOffset();
        int wrtlen = 0;

        // Check that the data block is valid
        if (buf[pos] != DataType.DataBlock) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Update the buffer position to the start of the data to be written
            pos += 3;

            // Check for a zero length write, this should truncate/extend the file to the write
            // offset position
            if (wrtcnt == 0) {

                // Truncate/extend the file to the write offset
                disk.truncateFile(m_sess, conn, netFile, wrtoff);
            }
            else {

                // Write to the file
                wrtlen = disk.writeFile(m_sess, conn, netFile, buf, pos, wrtcnt, wrtoff);
            }
        }
        catch (InvalidDeviceInterfaceException ex) {

            // Failed to get/initialize the disk interface
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {

            // Debug
            if (Debug.EnableError && m_sess.hasDebug(SMBSrvSession.Dbg.FILEIO))
                m_sess.debugPrintln("File Write Error [" + netFile.getFileId() + "] : " + ex.toString());

            // Failed to read the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.HRDWriteFault, SMBStatus.ErrHrd);
            return;
        }

        // Return the count of bytes actually written
        parser.setParameterCount(1);
        parser.setParameter(0, wrtlen);
        parser.setByteCount(0);

        // Send the write response
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Write to a file then close the file.
     *
     * @param smbPkt SMBSrvPacket
     * @param parser SMBV1Parser
     * @throws java.io.IOException The exception description.
     * @throws SMBSrvException     The exception description.
     */
    protected void procWriteAndCloseFile(SMBSrvPacket smbPkt, SMBV1Parser parser)
            throws java.io.IOException, SMBSrvException {

        // Check that the received packet looks like a valid file write and close request
        if (parser.checkPacketIsValid(6, 0) == false) {
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

        // Get the file id from the request
        int fid = parser.getParameter(0);
        int wrtcnt = parser.getParameter(1);
        int wrtoff = parser.getParameterLong(2);

        NetworkFile netFile = conn.findFile(fid);

        if (netFile == null) {
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && m_sess.hasDebug(SMBSrvSession.Dbg.FILEIO))
            m_sess.debugPrintln("File Write And Close [" + netFile.getFileId() + "] : Size=" + wrtcnt + " ,Pos=" + wrtoff);

        // Write data to the file
        byte[] buf = parser.getBuffer();
        int pos = parser.getByteOffset() + 1; // word align
        int wrtlen = 0;

        try {

            // Access the disk interface that is associated with the shared device
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Write to the file
            wrtlen = disk.writeFile(m_sess, conn, netFile, buf, pos, wrtcnt, wrtoff);

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
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
            return;
        }
        catch (java.io.IOException ex) {

            // Debug
            if (Debug.EnableError && m_sess.hasDebug(SMBSrvSession.Dbg.FILEIO))
                m_sess.debugPrintln("File Write Error [" + netFile.getFileId() + "] : " + ex.toString());

            // Failed to read the file
            m_sess.sendErrorResponseSMB(smbPkt, SMBStatus.HRDWriteFault, SMBStatus.ErrHrd);
            return;
        }

        // Return the count of bytes actually written
        parser.setParameterCount(1);
        parser.setParameter(0, wrtlen);
        parser.setByteCount(0);

        parser.setError(0, 0);

        // Send the write response
        m_sess.sendResponseSMB(smbPkt);
    }

    /**
     * Run the core SMB protocol handler.
     *
     * @param smbPkt SMBSrvPacket
     * @return boolean true if the packet was processed, else false
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     * @exception TooManyConnectionsException No more free connections available
     */
    public boolean runProtocol(SMBSrvPacket smbPkt)
            throws java.io.IOException, SMBSrvException, TooManyConnectionsException {

        // Get the SMB parser
        if ( smbPkt.hasParser() == false)
            throw new IOException( "SMB packet does not have a parser");

        SMBV1Parser parser = (SMBV1Parser) smbPkt.getParser();

        // Determine the SMB command type
        boolean handledOK = true;

        switch ( parser.getCommand()) {

            // Session setup
            case PacketTypeV1.SessionSetupAndX:
                procSessionSetup(smbPkt, parser);
                break;

            // Tree connect
            case PacketTypeV1.TreeConnect:
                procTreeConnect(smbPkt, parser);
                break;

            // Tree disconnect
            case PacketTypeV1.TreeDisconnect:
                procTreeDisconnect(smbPkt, parser);
                break;

            // Search
            case PacketTypeV1.Search:
                procSearch(smbPkt, parser);
                break;

            // Get disk attributes
            case PacketTypeV1.DiskInformation:
                procDiskAttributes(smbPkt, parser);
                break;

            // Get file attributes
            case PacketTypeV1.GetFileAttributes:
                procGetFileAttributes(smbPkt, parser);
                break;

            // Set file attributes
            case PacketTypeV1.SetFileAttributes:
                procSetFileAttributes(smbPkt, parser);
                break;

            // Get file information
            case PacketTypeV1.QueryInformation2:
                procGetFileInformation(smbPkt, parser);
                break;

            // Set file information
            case PacketTypeV1.SetInformation2:
                procSetFileInformation(smbPkt, parser);
                break;

            // Open a file
            case PacketTypeV1.OpenFile:
                procOpenFile(smbPkt, parser);
                break;

            // Read from a file
            case PacketTypeV1.ReadFile:
                procReadFile(smbPkt, parser);
                break;

            // Seek file
            case PacketTypeV1.SeekFile:
                procSeekFile(smbPkt, parser);
                break;

            // Close a file
            case PacketTypeV1.CloseFile:
                procCloseFile(smbPkt, parser);
                break;

            // Create a new file
            case PacketTypeV1.CreateFile:
            case PacketTypeV1.CreateNew:
                procCreateFile(smbPkt, parser);
                break;

            // Write to a file
            case PacketTypeV1.WriteFile:
                procWriteFile(smbPkt, parser);
                break;

            // Write to a file, then close the file
            case PacketTypeV1.WriteAndClose:
                procWriteAndCloseFile(smbPkt, parser);
                break;

            // Flush file
            case PacketTypeV1.FlushFile:
                procFlushFile(smbPkt, parser);
                break;

            // Rename a file
            case PacketTypeV1.RenameFile:
                procRenameFile(smbPkt, parser);
                break;

            // Delete a file
            case PacketTypeV1.DeleteFile:
                procDeleteFile(smbPkt, parser);
                break;

            // Create a new directory
            case PacketTypeV1.CreateDirectory:
                procCreateDirectory(smbPkt, parser);
                break;

            // Delete a directory
            case PacketTypeV1.DeleteDirectory:
                procDeleteDirectory(smbPkt, parser);
                break;

            // Check if a directory exists
            case PacketTypeV1.CheckDirectory:
                procCheckDirectory(smbPkt, parser);
                break;

            // Unsupported requests
            case PacketTypeV1.IOCtl:
                procUnsupported(smbPkt, parser);
                break;

            // Echo request
            case PacketTypeV1.Echo:
                procEcho(smbPkt, parser);
                break;

            // Process exit request
            case PacketTypeV1.ProcessExit:
                procProcessExit(smbPkt, parser);
                break;

            // Create temoporary file request
            case PacketTypeV1.CreateTemporary:
                procCreateTemporaryFile(smbPkt, parser);
                break;

            // Lock file request
            case PacketTypeV1.LockFile:
                procLockFile(smbPkt, parser);
                break;

            // Unlock file request
            case PacketTypeV1.UnLockFile:
                procUnLockFile(smbPkt, parser);
                break;

            // Default
            default:

                // Indicate that the protocol handler did not process the SMB request
                if (Debug.EnableError)
                    Debug.println("<<<<< Unknown SMB type : 0x" + Integer.toHexString( parser.getCommand()) + " >>>>>");
                handledOK = false;
                break;
        }

        // Run any request post processors
        runRequestPostProcessors(m_sess);

        // Return the handled status
        return handledOK;
    }
}
