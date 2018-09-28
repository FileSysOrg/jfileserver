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

import java.io.IOException;
import java.util.Enumeration;

import org.filesys.debug.Debug;
import org.filesys.server.core.ShareType;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.core.SharedDeviceList;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.SMBStatus;
import org.filesys.smb.TransactBuffer;
import org.filesys.util.DataBuffer;

/**
 * IPC$ Transaction handler for \PIPE\LANMAN requests.
 *
 * @author gkspencer
 */
class PipeLanmanHandler {

    // Server capability flags
    public static final int WorkStation         = 0x00000001;
    public static final int Server              = 0x00000002;
    public static final int SQLServer           = 0x00000004;
    public static final int DomainCtrl          = 0x00000008;
    public static final int DomainBakCtrl       = 0x00000010;
    public static final int TimeSource          = 0x00000020;
    public static final int AFPServer           = 0x00000040;
    public static final int NovellServer        = 0x00000080;
    public static final int DomainMember        = 0x00000100;
    public static final int PrintServer         = 0x00000200;
    public static final int DialinServer        = 0x00000400;
    public static final int UnixServer          = 0x00000800;
    public static final int NTServer            = 0x00001000;
    public static final int WfwServer           = 0x00002000;
    public static final int MFPNServer          = 0x00004000;
    public static final int NTNonDCServer       = 0x00008000;
    public static final int PotentialBrowse     = 0x00010000;
    public static final int BackupBrowser       = 0x00020000;
    public static final int MasterBrowser       = 0x00040000;
    public static final int DomainMaster        = 0x00080000;
    public static final int OSFServer           = 0x00100000;
    public static final int VMSServer           = 0x00200000;
    public static final int Win95Plus           = 0x00400000;
    public static final int DFSRoot             = 0x00800000;
    public static final int NTCluster           = 0x01000000;
    public static final int TerminalServer      = 0x02000000;
    public static final int DCEServer           = 0x10000000;
    public static final int AlternateXport      = 0x20000000;
    public static final int LocalListOnly       = 0x40000000;
    public static final int DomainEnum          = 0x80000000;

    /**
     * Process a \PIPE\LANMAN transaction request.
     *
     * @param tbuf  Transaction setup, parameter and data buffers
     * @param sess  SMB server session that received the transaction.
     * @param trans Packet to use for reply
     * @return true if the transaction has been handled, else false.
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    public final static boolean processRequest(TransactBuffer tbuf, SMBSrvSession sess, SMBSrvPacket trans)
            throws IOException, SMBSrvException {

        // Create a transaction packet
        SMBSrvTransPacket tpkt = new SMBSrvTransPacket(trans.getBuffer());

        // Get the transaction command code, parameter descriptor and data descriptor strings from
        // the parameter block.
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int cmd = paramBuf.getShort();
        String prmDesc = paramBuf.getString(false);
        String dataDesc = paramBuf.getString(false);

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("\\PIPE\\LANMAN\\ transact request, cmd=" + cmd + ", prm=" + prmDesc + ", data=" + dataDesc);

        // Call the required transaction handler
        boolean processed = false;

        switch (cmd) {

            // Share
            case PacketTypeV1.RAPShareEnum:
                processed = procNetShareEnum(sess, tbuf, prmDesc, dataDesc, tpkt);
                break;

            // Get share information
            case PacketTypeV1.RAPShareGetInfo:
                processed = procNetShareGetInfo(sess, tbuf, prmDesc, dataDesc, tpkt);
                break;

            // Workstation information
            case PacketTypeV1.RAPWkstaGetInfo:
                processed = procNetWkstaGetInfo(sess, tbuf, prmDesc, dataDesc, tpkt);
                break;

            // Server information
            case PacketTypeV1.RAPServerGetInfo:
                processed = procNetServerGetInfo(sess, tbuf, prmDesc, dataDesc, tpkt);
                break;

            // Print queue information
            case PacketTypeV1.NetPrintQGetInfo:
                processed = procNetPrintQGetInfo(sess, tbuf, prmDesc, dataDesc, tpkt);
                break;

            // No handler
            default:

                // Debug
                if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
                    sess.debugPrintln("No handler for \\PIPE\\LANMAN\\ request, cmd=" + cmd + ", prm=" + prmDesc + ", data="
                            + dataDesc);
                break;
        }

        // Check if the transaction packet has allocated an associated packet from the pool, we need to copy the associated packet
        // to the outer request packet so that it is released back to the pool.
        if (tpkt.hasAssociatedPacket()) {

            Debug.println("[SMB] PipeLanManHandler allocated associated packet, len=" + tpkt.getAssociatedPacket().getBufferLength());

            // Copy the associated packet to the outer request packet
            trans.setAssociatedPacket(tpkt.getAssociatedPacket());
            tpkt.setAssociatedPacket(null);
        }

        // Return the transaction processed status
        return processed;
    }

    /**
     * Process a NetServerGetInfo transaction request.
     *
     * @param sess     Server session that received the request.
     * @param tbuf     Transaction buffer
     * @param prmDesc  Parameter descriptor string.
     * @param dataDesc Data descriptor string.
     * @param tpkt     Transaction reply packet
     * @return true if the transaction has been processed, else false.
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final static boolean procNetServerGetInfo(SMBSrvSession sess, TransactBuffer tbuf, String prmDesc, String dataDesc,
                                                        SMBSrvTransPacket tpkt)
            throws IOException, SMBSrvException {

        // Validate the parameter string
        if (prmDesc.compareTo("WrLh") != 0)
            throw new SMBSrvException(SMBStatus.SRVInternalServerError, SMBStatus.ErrSrv);

        // Unpack the server get information specific parameters
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int infoLevel = paramBuf.getShort();
        int bufSize = paramBuf.getShort();

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("NetServerGetInfo infoLevel=" + infoLevel);

        // Check if the information level requested and data descriptor string match
        if (infoLevel == 1 && dataDesc.compareTo("B16BBDz") == 0) {

            // Create the transaction reply data buffer
            TransactBuffer replyBuf = new TransactBuffer(tbuf.isType(), 0, 6, 1024);

            // Pack the parameter block
            paramBuf = replyBuf.getParameterBuffer();

            paramBuf.putShort(0); // status code
            paramBuf.putShort(0); // converter for strings
            paramBuf.putShort(1); // number of entries

            // Pack the data block, calculate the size of the fixed data block
            DataBuffer dataBuf = replyBuf.getDataBuffer();
            int strPos = SMBSrvTransPacket.CalculateDataItemSize("B16BBDz");

            // Pack the server name pointer and string
            dataBuf.putStringPointer(strPos);
            strPos = dataBuf.putFixedStringAt(sess.getServerName(), 16, strPos);

            // Pack the major/minor version
            dataBuf.putByte(1);
            dataBuf.putByte(0);

            // Pack the server capability flags
            dataBuf.putInt(sess.getSMBServer().getServerType());

            // Pack the server comment string
            String srvComment = sess.getSMBServer().getComment();
            if (srvComment == null)
                srvComment = "";

            dataBuf.putStringPointer(strPos);
            strPos = dataBuf.putStringAt(srvComment, strPos, false, true);

            // Set the data block length
            dataBuf.setLength(strPos);

            // Send the transaction response
            tpkt.doTransactionResponse(sess, replyBuf);
        }
        else
            throw new SMBSrvException(SMBStatus.SRVInternalServerError, SMBStatus.ErrSrv);

        // We processed the request
        return true;
    }

    /**
     * Process a NetShareEnum transaction request.
     *
     * @param sess     Server session that received the request.
     * @param tbuf     Transaction buffer
     * @param prmDesc  Parameter descriptor string.
     * @param dataDesc Data descriptor string.
     * @param tpkt     Transaction reply packet
     * @return true if the transaction has been processed, else false.
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final static boolean procNetShareEnum(SMBSrvSession sess, TransactBuffer tbuf, String prmDesc, String dataDesc,
                                                    SMBSrvTransPacket tpkt)
            throws IOException, SMBSrvException {

        // Validate the parameter string
        if (prmDesc.compareTo("WrLeh") != 0)
            throw new SMBSrvException(SMBStatus.SRVInternalServerError, SMBStatus.ErrSrv);

        // Unpack the server get information specific parameters
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int infoLevel = paramBuf.getShort();
        int bufSize = paramBuf.getShort();

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("NetShareEnum infoLevel=" + infoLevel);

        // Check if the information level requested and data descriptor string match
        if (infoLevel == 1 && dataDesc.compareTo("B13BWz") == 0) {

            // Get the share list from the server
            SharedDeviceList shrList = sess.getSMBServer().getShareList(null, sess);
            int shrCount = 0;
            int strPos = 0;

            if (shrList != null) {

                // Calculate the fixed data length
                shrCount = shrList.numberOfShares();
                strPos = SMBSrvTransPacket.CalculateDataItemSize("B13BWz") * shrCount;
            }

            // Create the transaction reply data buffer
            TransactBuffer replyBuf = new TransactBuffer(tbuf.isType(), 0, 6, bufSize);

            // Pack the parameter block
            paramBuf = replyBuf.getParameterBuffer();

            paramBuf.putShort(0); // status code
            paramBuf.putShort(0); // converter for strings
            paramBuf.putShort(shrCount); // number of entries
            paramBuf.putShort(shrCount); // total number of entries

            // Pack the data block
            DataBuffer dataBuf = replyBuf.getDataBuffer();
            Enumeration<SharedDevice> enm = shrList.enumerateShares();

            while (enm.hasMoreElements()) {

                // Get the current share
                SharedDevice shrDev = enm.nextElement();

                // Pack the share name, share type and comment pointer
                dataBuf.putFixedString(shrDev.getName(), 13);
                dataBuf.putByte(0);
                dataBuf.putShort(ShareType.asShareInfoType(shrDev.getType()));
                dataBuf.putStringPointer(strPos);

                if (shrDev.getComment() != null)
                    strPos = dataBuf.putStringAt(shrDev.getComment(), strPos, false, true);
                else
                    strPos = dataBuf.putStringAt("", strPos, false, true);
            }

            // Set the data block length
            dataBuf.setLength(strPos);

            // Send the transaction response
            tpkt.doTransactionResponse(sess, replyBuf);
        }
        else
            throw new SMBSrvException(SMBStatus.SRVInternalServerError, SMBStatus.ErrSrv);

        // We processed the request
        return true;
    }

    /**
     * Process a NetShareGetInfo transaction request.
     *
     * @param sess     Server session that received the request.
     * @param tbuf     Transaction buffer
     * @param prmDesc  Parameter descriptor string.
     * @param dataDesc Data descriptor string.
     * @param tpkt     Transaction reply packet
     * @return true if the transaction has been processed, else false.
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final static boolean procNetShareGetInfo(SMBSrvSession sess, TransactBuffer tbuf, String prmDesc, String dataDesc,
                                                       SMBSrvTransPacket tpkt)
            throws IOException, SMBSrvException {

        // Validate the parameter string
        if (prmDesc.compareTo("zWrLh") != 0)
            throw new SMBSrvException(SMBStatus.SRVInternalServerError, SMBStatus.ErrSrv);

        // Unpack the share get information specific parameters
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        String shareName = paramBuf.getString(32, false);
        int infoLevel = paramBuf.getShort();
        int bufSize = paramBuf.getShort();

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("NetShareGetInfo - " + shareName + ", infoLevel=" + infoLevel);

        // Check if the information level requested and data descriptor string match
        if (infoLevel == 1 && dataDesc.compareTo("B13BWz") == 0) {

            // Find the required share information
            SharedDevice share = null;

            try {

                // Get the shared device details
                share = sess.getSMBServer().findShare(null, shareName, ShareType.UNKNOWN, sess, false);
            }
            catch (Exception ex) {
            }

            if (share == null) {
                sess.sendErrorResponseSMB(tpkt, SMBStatus.SRVInternalServerError, SMBStatus.ErrSrv);
                return true;
            }

            // Create the transaction reply data buffer
            TransactBuffer replyBuf = new TransactBuffer(tbuf.isType(), 0, 6, 1024);

            // Pack the parameter block
            paramBuf = replyBuf.getParameterBuffer();

            paramBuf.putShort(0); // status code
            paramBuf.putShort(0); // converter for strings
            paramBuf.putShort(1); // number of entries

            // Pack the data block, calculate the size of the fixed data block
            DataBuffer dataBuf = replyBuf.getDataBuffer();
            int strPos = SMBSrvTransPacket.CalculateDataItemSize("B13BWz");

            // Pack the share name
            dataBuf.putStringPointer(strPos);
            strPos = dataBuf.putFixedStringAt(share.getName(), 13, strPos);

            // Pack unknown byte, alignment ?
            dataBuf.putByte(0);

            // Pack the share type flags
            dataBuf.putShort(share.getType().intValue());

            // Pack the share comment
            dataBuf.putStringPointer(strPos);

            if (share.getComment() != null)
                strPos = dataBuf.putStringAt(share.getComment(), strPos, false, true);
            else
                strPos = dataBuf.putStringAt("", strPos, false, true);

            // Set the data block length
            dataBuf.setLength(strPos);

            // Send the transaction response
            tpkt.doTransactionResponse(sess, replyBuf);
        }
        else {

            // Debug
            if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
                sess.debugPrintln("NetShareGetInfo - UNSUPPORTED " + shareName + ", infoLevel=" + infoLevel + ", dataDesc="
                        + dataDesc);

            // Server error
            throw new SMBSrvException(SMBStatus.SRVInternalServerError, SMBStatus.ErrSrv);
        }

        // We processed the request
        return true;
    }

    /**
     * Process a NetWkstaGetInfo transaction request.
     *
     * @param sess     Server session that received the request.
     * @param tbuf     Transaction buffer
     * @param prmDesc  Parameter descriptor string.
     * @param dataDesc Data descriptor string.
     * @param tpkt     Transaction reply packet
     * @return true if the transaction has been processed, else false.
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final static boolean procNetWkstaGetInfo(SMBSrvSession sess, TransactBuffer tbuf, String prmDesc, String dataDesc,
                                                       SMBSrvTransPacket tpkt)
            throws IOException, SMBSrvException {

        // Validate the parameter string
        if (prmDesc.compareTo("WrLh") != 0)
            throw new SMBSrvException(SMBStatus.SRVInternalServerError, SMBStatus.ErrSrv);

        // Unpack the share get information specific parameters
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        int infoLevel = paramBuf.getShort();
        int bufSize = paramBuf.getShort();

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("NetWkstaGetInfo infoLevel=" + infoLevel);

        // Check if the information level requested and data descriptor string match
        if ((infoLevel == 1 && dataDesc.compareTo("zzzBBzzz") == 0) || (infoLevel == 10 && dataDesc.compareTo("zzzBBzz") == 0)) {

            // Create the transaction reply data buffer
            TransactBuffer replyBuf = new TransactBuffer(tbuf.isType(), 0, 6, 1024);

            // Pack the data block, calculate the size of the fixed data block
            DataBuffer dataBuf = replyBuf.getDataBuffer();
            int strPos = SMBSrvTransPacket.CalculateDataItemSize(dataDesc);

            // Pack the server name
            dataBuf.putStringPointer(strPos);
            strPos = dataBuf.putStringAt(sess.getServerName(), strPos, false, true);

            // Pack the user name
            dataBuf.putStringPointer(strPos);
            strPos = dataBuf.putStringAt("", strPos, false, true);

            // Pack the domain name
            dataBuf.putStringPointer(strPos);

            String domain = sess.getSMBServer().getSMBConfiguration().getDomainName();
            if (domain == null)
                domain = "";
            strPos = dataBuf.putStringAt(domain, strPos, false, true);

            // Pack the major/minor version number
            dataBuf.putByte(4);
            dataBuf.putByte(2);

            // Pack the logon domain
            dataBuf.putStringPointer(strPos);
            strPos = dataBuf.putStringAt("", strPos, false, true);

            // Check if the other domains should be packed
            if (infoLevel == 1 && dataDesc.compareTo("zzzBBzzz") == 0) {

                // Pack the other domains
                dataBuf.putStringPointer(strPos);
                strPos = dataBuf.putStringAt("", strPos, false, true);
            }

            // Set the data block length
            dataBuf.setLength(strPos);

            // Pack the parameter block
            paramBuf = replyBuf.getParameterBuffer();

            paramBuf.putShort(0); // status code
            paramBuf.putShort(0); // converter for strings
            paramBuf.putShort(dataBuf.getLength());
            paramBuf.putShort(0); // number of entries

            // Send the transaction response
            tpkt.doTransactionResponse(sess, replyBuf);
        }
        else {

            // Debug
            if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
                sess.debugPrintln("NetWkstaGetInfo UNSUPPORTED infoLevel=" + infoLevel + ", dataDesc=" + dataDesc);

            // Unsupported request
            throw new SMBSrvException(SMBStatus.SRVInternalServerError, SMBStatus.ErrSrv);
        }

        // We processed the request
        return true;
    }

    /**
     * Process a NetPrintQGetInfo transaction request.
     *
     * @param sess     Server session that received the request.
     * @param tbuf     Transaction buffer
     * @param prmDesc  Parameter descriptor string.
     * @param dataDesc Data descriptor string.
     * @param tpkt     Transaction reply packet
     * @return true if the transaction has been processed, else false.
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    protected final static boolean procNetPrintQGetInfo(SMBSrvSession sess, TransactBuffer tbuf, String prmDesc, String dataDesc,
                                                        SMBSrvTransPacket tpkt)
            throws IOException, SMBSrvException {

        // Validate the parameter string
        if (prmDesc.compareTo("zWrLh") != 0)
            throw new SMBSrvException(SMBStatus.SRVInternalServerError, SMBStatus.ErrSrv);

        // Unpack the share get information specific parameters
        DataBuffer paramBuf = tbuf.getParameterBuffer();

        String shareName = paramBuf.getString(32, false);
        int infoLevel = paramBuf.getShort();
        int bufSize = paramBuf.getShort();

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("NetPrintQGetInfo - " + shareName + ", infoLevel=" + infoLevel);

        // We did not process the request
        return false;
    }
}
