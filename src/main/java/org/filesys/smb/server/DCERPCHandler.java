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

import org.filesys.debug.Debug;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.server.filesys.TreeConnection;
import org.filesys.smb.DataType;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.SMBStatus;
import org.filesys.smb.TransactBuffer;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCECommand;
import org.filesys.smb.dcerpc.DCEDataPacker;
import org.filesys.smb.dcerpc.DCEPipeType;
import org.filesys.smb.dcerpc.UUID;
import org.filesys.smb.dcerpc.server.DCEPipeFile;
import org.filesys.smb.dcerpc.server.DCESrvPacket;
import org.filesys.util.DataBuffer;
import org.filesys.util.DataPacker;

/**
 * DCE/RPC Protocol Handler Class
 *
 * @author gkspencer
 */
public class DCERPCHandler {

    /**
     * Process a DCE/RPC request
     *
     * @param sess     SMBSrvSession
     * @param srvTrans SMBSrvTransPacket
     * @param smbPkt   SMBSrvPacket
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    public static final void processDCERPCRequest(SMBSrvSession sess, SMBSrvTransPacket srvTrans, SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException {

        // Get the tree id from the received packet and validate that it is a valid connection id.
        TreeConnection conn = sess.findTreeConnection(srvTrans);

        if (conn == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the associated parser
        SMBV1Parser parser = (SMBV1Parser) srvTrans.getParser();

        // Get the file id and validate
        int fid = srvTrans.getSetupParameter(1);
        int maxData = parser.getParameter(3) - DCEBuffer.OPERATIONDATA;

        // Get the IPC pipe file for the specified file id
        DCEPipeFile pipeFile = (DCEPipeFile) conn.findFile(fid);
        if (pipeFile == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Create a DCE/RPC buffer from the received data
        DCEBuffer dceBuf = new DCEBuffer(srvTrans.getBuffer(), parser.getParameter(10) + RFCNetBIOSProtocol.HEADER_LEN);

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_DCERPC))
            sess.debugPrintln("TransactNmPipe pipeFile=" + pipeFile.getName() + ", fid=" + fid + ", dceCmd=0x"
                    + Integer.toHexString(dceBuf.getHeaderValue(DCEBuffer.HDR_PDUTYPE)));

        // Process the received DCE buffer
        processDCEBuffer(sess, dceBuf, pipeFile, smbPkt);

        // Check if there is a reply buffer to return to the caller
        if (pipeFile.hasBufferedData() == false)
            return;

        DCEBuffer txBuf = pipeFile.getBufferedData();

        // Initialize the reply
        DCESrvPacket dcePkt = new DCESrvPacket(smbPkt.getBuffer());

        // Get the response parser
        SMBV1Parser respParser = (SMBV1Parser) smbPkt.getParser();

        // Always only one fragment as the data either fits into the first reply fragment or the
        // client will read the remaining data by issuing read requests on the pipe
        int flags = DCESrvPacket.FLG_ONLYFRAG;

        dcePkt.initializeDCEReply();
        txBuf.setHeaderValue(DCEBuffer.HDR_FLAGS, flags);

        // Build the reply data
        byte[] buf = dcePkt.getBuffer();
        int pos = DCEDataPacker.longwordAlign(respParser.getByteOffset());

        // Set the DCE fragment size and send the reply DCE/RPC SMB
        int dataLen = txBuf.getLength();
        txBuf.setHeaderValue(DCEBuffer.HDR_FRAGLEN, dataLen);

        // Copy the data from the DCE output buffer to the reply SMB packet
        int len = txBuf.getLength();
        int sts = SMBStatus.NTSuccess;

        if (len > maxData) {

            // Write the maximum transmit fragment to the reply
            len = maxData + DCEBuffer.OPERATIONDATA;
            dataLen = maxData + DCEBuffer.OPERATIONDATA;

            // Indicate a buffer overflow status
            sts = SMBStatus.NTBufferOverflow;
        }
        else {

            // Clear the DCE/RPC pipe buffered data, the reply will fit into a single response packet
            pipeFile.setBufferedData(null);
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_DCERPC))
            sess.debugPrintln("Reply DCEbuf flags=0x" + Integer.toHexString(flags) + ", len=" + len + ", status=0x"
                    + Integer.toHexString(sts));

        // Copy the reply data to the reply packet
        try {
            pos += txBuf.copyData(buf, pos, len);
        }
        catch (DCEBufferException ex) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
            return;
        }

        // Set the SMB transaction data length
        int byteLen = pos - respParser.getByteOffset();
        respParser.setParameter(1, dataLen);
        respParser.setParameter(6, dataLen);
        respParser.setByteCount(byteLen);
        respParser.setFlags2(SMBPacket.FLG2_LONGERRORCODE);
        respParser.setLongErrorCode(sts);
        respParser.setResponse();

        sess.sendResponseSMB(dcePkt);

        // Check if the transaction packet has allocated an associated packet from the pool, we need to copy the associated packet
        // to the outer request packet so that it is released back to the pool.
        if (dcePkt.hasAssociatedPacket()) {

            // DEBUG
            if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_PKTPOOL))
                Debug.println("[SMB] DCERPCHandler allocated associated packet, len=" + dcePkt.getAssociatedPacket().getBufferLength());

            // Copy the associated packet to the outer request packet
            smbPkt.setAssociatedPacket(dcePkt.getAssociatedPacket());
            dcePkt.setAssociatedPacket(null);
        }
    }

    /**
     * Process a DCE/RPC request
     *
     * @param sess   SMBSrvSession
     * @param vc     VirtualCircuit
     * @param tbuf   TransactBuffer
     * @param smbPkt SMBSrvPacket
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    public static final void processDCERPCRequest(SMBSrvSession sess, VirtualCircuit vc, TransactBuffer tbuf, SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException {

        // Check if the transaction buffer has setup and data buffers
        if (tbuf.hasSetupBuffer() == false || tbuf.hasDataBuffer() == false) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVUnrecognizedCommand, SMBStatus.ErrSrv);
            return;
        }

        // Get the tree id from the received packet and validate that it is a valid connection id.
        int treeId = tbuf.getTreeId();
        TreeConnection conn = vc.findConnection(treeId);

        if (conn == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the file id and validate
        DataBuffer setupBuf = tbuf.getSetupBuffer();

        setupBuf.skipBytes(2);
        int fid = setupBuf.getShort();
        int maxData = tbuf.getReturnDataLimit() - DCEBuffer.OPERATIONDATA;

        // Get the IPC pipe file for the specified file id
        DCEPipeFile pipeFile = (DCEPipeFile) conn.findFile(fid);
        if (pipeFile == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Create a DCE/RPC buffer from the received transaction data
        DCEBuffer dceBuf = new DCEBuffer(tbuf);

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_DCERPC))
            sess.debugPrintln("TransactNmPipe pipeFile=" + pipeFile.getName() + ", fid=" + fid + ", dceCmd=0x"
                    + Integer.toHexString(dceBuf.getHeaderValue(DCEBuffer.HDR_PDUTYPE)));

        // Process the received DCE buffer
        processDCEBuffer(sess, dceBuf, pipeFile, smbPkt);

        // Check if there is a reply buffer to return to the caller
        if (pipeFile.hasBufferedData() == false)
            return;

        DCEBuffer txBuf = pipeFile.getBufferedData();

        // Initialize the reply
        DCESrvPacket dcePkt = new DCESrvPacket(smbPkt.getBuffer());

        // Get the associated parser
        SMBV1Parser parser = (SMBV1Parser) smbPkt.getParser();

        // Always only one fragment as the data either fits into the first reply fragment or the
        // client will read the remaining data by issuing read requests on the pipe
        int flags = DCESrvPacket.FLG_ONLYFRAG;

        dcePkt.initializeDCEReply();
        txBuf.setHeaderValue(DCEBuffer.HDR_FLAGS, flags);

        // Build the reply data
        byte[] buf = dcePkt.getBuffer();
        int pos = DCEDataPacker.longwordAlign(parser.getByteOffset());

        // Set the DCE fragment size and send the reply DCE/RPC SMB
        int dataLen = txBuf.getLength();
        txBuf.setHeaderValue(DCEBuffer.HDR_FRAGLEN, dataLen);

        // Copy the data from the DCE output buffer to the reply SMB packet
        int len = txBuf.getLength();
        int sts = SMBStatus.NTSuccess;

        if (len > maxData) {

            // Write the maximum transmit fragment to the reply
            len = maxData + DCEBuffer.OPERATIONDATA;
            dataLen = maxData + DCEBuffer.OPERATIONDATA;

            // Indicate a buffer overflow status
            sts = SMBStatus.NTBufferOverflow;
        }
        else {

            // Clear the DCE/RPC pipe buffered data, the reply will fit into a single response packet
            pipeFile.setBufferedData(null);
        }

        // Check if a new buffer needs to be allocated for the response
        int pktLen = parser.getByteOffset() + len + 4;    // allow for alignment

        if (smbPkt.getBufferLength() < pktLen) {

            // Allocate a new buffer for the response
            SMBSrvPacket respPkt = sess.getPacketPool().allocatePacket(pktLen, smbPkt, parser.getByteOffset());

            // Switch the response to the new buffer
            buf = respPkt.getBuffer();
            dcePkt.setBuffer(buf);

            // Create a parser for the new response
            dcePkt.setParser( SMBSrvPacket.Version.V1);
            parser = (SMBV1Parser) dcePkt.getParser();
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_DCERPC))
            sess.debugPrintln("Reply DCEbuf flags=0x" + Integer.toHexString(flags) + ", len=" + len + ", status=0x"
                    + Integer.toHexString(sts));

        // Copy the reply data to the reply packet
        try {
            pos += txBuf.copyData(buf, pos, len);
        }
        catch (DCEBufferException ex) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
            return;
        }

        // Set the SMB transaction data length
        int byteLen = pos - parser.getByteOffset();
        parser.setParameter(1, dataLen);
        parser.setParameter(6, dataLen);
        parser.setByteCount(byteLen);
        parser.setFlags2(SMBPacket.FLG2_LONGERRORCODE);
        parser.setLongErrorCode(sts);
        parser.setResponse();

        sess.sendResponseSMB(dcePkt);

        // Check if the transaction packet has allocated an associated packet from the pool, we need to copy the associated packet
        // to the outer request packet so that it is released back to the pool.
        if (dcePkt.hasAssociatedPacket()) {

            // DEBUG
            if (Debug.EnableDbg && sess.hasDebug(SMBSrvSession.DBG_PKTPOOL))
                Debug.println("[SMB] DCERPCHandler allocated associated packet, len=" + dcePkt.getAssociatedPacket().getBufferLength());

            // Copy the associated packet to the outer request packet
            smbPkt.setAssociatedPacket(dcePkt.getAssociatedPacket());
            dcePkt.setAssociatedPacket(null);
        }
    }

    /**
     * Process a DCE/RPC write request to the named pipe file
     *
     * @param sess   SMBSrvSession
     * @param smbPkt SMBSrvPacket
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    public static final void processDCERPCRequest(SMBSrvSession sess, SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException {

        // Get the tree id from the received packet and validate that it is a valid connection id.
        TreeConnection conn = sess.findTreeConnection(smbPkt);

        if (conn == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the associated parser
        SMBV1Parser parser = (SMBV1Parser) smbPkt.getParser();

        // Determine if this is a write or write andX request
        int cmd = parser.getCommand();

        // Get the file id and validate
        int fid = -1;
        if (cmd == PacketTypeV1.WriteFile)
            fid = parser.getParameter(0);
        else
            fid = parser.getParameter(2);

        // Get the IPC pipe file for the specified file id
        DCEPipeFile pipeFile = (DCEPipeFile) conn.findFile(fid);
        if (pipeFile == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Create a DCE buffer for the received data
        DCEBuffer dceBuf = null;
        byte[] buf = parser.getBuffer();
        int pos = 0;
        int len = 0;

        if (cmd == PacketTypeV1.WriteFile) {

            // Get the data offset
            pos = parser.getByteOffset();

            // Check that the received data is valid
            if (buf[pos++] != DataType.DataBlock) {
                sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidData, SMBStatus.ErrDos);
                return;
            }

            len = DataPacker.getIntelShort(buf, pos);
            pos += 2;
        }
        else {

            // Get the data offset and length
            len = parser.getParameter(10);
            pos = parser.getParameter(11) + RFCNetBIOSProtocol.HEADER_LEN;
        }

        // Create a DCE buffer mapped to the received packet
        dceBuf = new DCEBuffer(buf, pos);

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("Write pipeFile=" + pipeFile.getName() + ", fid=" + fid + ", dceCmd=0x"
                    + Integer.toHexString(dceBuf.getHeaderValue(DCEBuffer.HDR_PDUTYPE)));

        // Process the DCE buffer
        processDCEBuffer(sess, dceBuf, pipeFile, smbPkt);

        // Check if there is a valid reply buffered
        int bufLen = 0;
        if (pipeFile.hasBufferedData())
            bufLen = pipeFile.getBufferedData().getLength();

        // Send the write/write andX reply
        if (cmd == PacketTypeV1.WriteFile) {

            // Build the write file reply
            parser.setParameterCount(1);
            parser.setParameter(0, len);
            parser.setByteCount(0);
        }
        else {

            // Build the write andX reply
            parser.setParameterCount(6);

            parser.setAndXCommand(0xFF);
            parser.setParameter(1, 0);
            parser.setParameter(2, len);
            parser.setParameter(3, bufLen);
            parser.setParameter(4, 0);
            parser.setParameter(5, 0);
            parser.setByteCount(0);
        }

        // Send the write reply
        parser.setFlags2(SMBPacket.FLG2_LONGERRORCODE);
        sess.sendResponseSMB(smbPkt);
    }

    /**
     * Process a DCE/RPC pipe read request
     *
     * @param sess   SMBSrvSession
     * @param smbPkt SMBSrvPacket
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    public static final void processDCERPCRead(SMBSrvSession sess, SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException {

        // Get the tree id from the received packet and validate that it is a valid connection id
        TreeConnection conn = sess.findTreeConnection(smbPkt);

        if (conn == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidDrive, SMBStatus.ErrDos);
            return;
        }

        // Get the associated parser
        SMBV1Parser parser = (SMBV1Parser) smbPkt.getParser();

        // Determine if this is a read or read andX request
        int cmd = parser.getCommand();

        // Get the file id and read length, and validate
        int fid = -1;
        int rdLen = -1;

        if (cmd == PacketTypeV1.ReadFile) {
            fid = parser.getParameter(0);
            rdLen = parser.getParameter(1);
        }
        else {
            fid = parser.getParameter(2);
            rdLen = parser.getParameter(5);
        }

        // Get the IPC pipe file for the specified file id
        DCEPipeFile pipeFile = (DCEPipeFile) conn.findFile(fid);

        if (pipeFile == null) {
            sess.sendErrorResponseSMB(smbPkt, SMBStatus.DOSInvalidHandle, SMBStatus.ErrDos);
            return;
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
            sess.debugPrintln("Read pipeFile=" + pipeFile.getName() + ", fid=" + fid + ", rdLen=" + rdLen);

        // Check if there is a valid reply buffered
        SMBSrvPacket respPkt = smbPkt;

        if (pipeFile.hasBufferedData()) {

            // Get the buffered data
            DCEBuffer bufData = pipeFile.getBufferedData();
            int bufLen = bufData.getAvailableLength();

            // Debug
            if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
                sess.debugPrintln("  Buffered data available=" + bufLen);

            // Check if there is less data than the read size
            if (rdLen > bufLen)
                rdLen = bufLen;

            // Check if the requested data will fit into the current packet
            byte[] buf = parser.getBuffer();
            int pos = parser.getByteOffset();

            if (cmd == PacketTypeV1.ReadFile)
                pos += 2;

            if (rdLen > (buf.length - pos)) {

                // Allocate a larger packet for the response
                respPkt = sess.getPacketPool().allocatePacket(rdLen + pos, smbPkt, pos);

                // Switch to the response buffer
                buf = respPkt.getBuffer();

                // Create a parser for the new response
                respPkt.setParser( SMBSrvPacket.Version.V1);
                parser = (SMBV1Parser) respPkt.getParser();

                // Debug
                if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
                    sess.debugPrintln("  Allocated larger reply packet, pktLen=" + respPkt.getBuffer().length);
            }

            // Set the DCE response buffer flags and fragment length
            bufData.setHeaderValue(DCEBuffer.HDR_FLAGS, DCEBuffer.FLG_ONLYFRAG);
            bufData.setHeaderValue(DCEBuffer.HDR_FRAGLEN, bufData.getLength());

            // Build the read response
            if (cmd == PacketTypeV1.ReadFile) {

                // Build the read response
                parser.setParameterCount(5);
                parser.setParameter(0, rdLen);
                for (int i = 1; i < 5; i++)
                    parser.setParameter(i, 0);
                parser.setByteCount(rdLen + 3);

                // Copy the data to the response
                pos = parser.getByteOffset();

                buf[pos++] = (byte) DataType.DataBlock;
                DataPacker.putIntelShort(rdLen, buf, pos);
                pos += 2;

                try {
                    bufData.copyData(buf, pos, rdLen);
                }
                catch (DCEBufferException ex) {
                    sess.debugPrintln(ex);
                }
            }
            else {

                // Build the read andX response
                parser.setParameterCount(12);
                parser.setAndXCommand(0xFF);
                for (int i = 1; i < 12; i++)
                    parser.setParameter(i, 0);

                // Copy the data to the response
                pos = DCEDataPacker.longwordAlign(parser.getByteOffset());

                parser.setParameter(5, rdLen);
                parser.setParameter(6, pos - RFCNetBIOSProtocol.HEADER_LEN);
                parser.setByteCount((pos + rdLen) - parser.getByteOffset());

                try {
                    bufData.copyData(buf, pos, rdLen);
                }
                catch (DCEBufferException ex) {
                    Debug.println(ex);
                }
            }

            // TODO: Free the DCEBuffer if no more data available
        }
        else {

            // Debug
            if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_IPC))
                sess.debugPrintln("  No buffered data available");

            // Return a zero length read response
            if (cmd == PacketTypeV1.ReadFile) {

                // Initialize the read response
                parser.setParameterCount(5);
                for (int i = 0; i < 5; i++)
                    parser.setParameter(i, 0);
                parser.setByteCount(0);
            }
            else {

                // Return a zero length read andX response
                parser.setParameterCount(12);

                parser.setAndXCommand(0xFF);
                for (int i = 1; i < 12; i++)
                    parser.setParameter(i, 0);
                parser.setByteCount(0);
            }
        }

        // Clear the status code
        parser.setLongErrorCode(SMBStatus.NTSuccess);

        // Send the read reply
        parser.setFlags2(SMBPacket.FLG2_LONGERRORCODE);
        sess.sendResponseSMB(respPkt);
    }

    /**
     * Process the DCE/RPC request buffer
     *
     * @param sess     SMBSrvSession
     * @param dceBuf   DCEBuffer
     * @param pipeFile DCEPipeFile
     * @param smbPkt   SMBSrvPacket
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    public static final void processDCEBuffer(SMBSrvSession sess, DCEBuffer dceBuf, DCEPipeFile pipeFile, SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException {

        // Process the DCE/RPC request
        switch (dceBuf.getHeaderValue(DCEBuffer.HDR_PDUTYPE)) {

            // DCE Bind
            case DCECommand.BIND:
                procDCEBind(sess, dceBuf, pipeFile, smbPkt);
                break;

            // DCE Request
            case DCECommand.REQUEST:
                procDCERequest(sess, dceBuf, pipeFile, smbPkt);
                break;

            default:
                throw new SMBSrvException(SMBStatus.NTAccessDenied, SMBStatus.SRVNoAccessRights, SMBStatus.ErrSrv);
        }
    }

    /**
     * Process a DCE bind request
     *
     * @param sess     SMBSrvSession
     * @param dceBuf   DCEBuffer
     * @param pipeFile DCEPipeFile
     * @param smbPkt   SMBSrvPacket
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    public static final void procDCEBind(SMBSrvSession sess, DCEBuffer dceBuf, DCEPipeFile pipeFile, SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException {

        try {

            // DEBUG
            if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_DCERPC))
                sess.debugPrintln("DCE Bind");

            // Get the call id and skip the DCE header
            int callId = dceBuf.getHeaderValue(DCEBuffer.HDR_CALLID);
            dceBuf.skipBytes(DCEBuffer.DCEDATA);

            // Unpack the bind request
            int maxTxSize = dceBuf.getShort();
            int maxRxSize = dceBuf.getShort();
            int groupId = dceBuf.getInt();

            int ctxElems = dceBuf.getByte(DCEBuffer.ALIGN_SHORT);
            dceBuf.skipBytes(2);

            int presCtxId = dceBuf.getShort();
            int trfSyntax = dceBuf.getShort();

            UUID uuid1 = dceBuf.getUUID(true);
            UUID uuid2 = dceBuf.getUUID(true);

            // Debug
            if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_DCERPC)) {
                sess.debugPrintln("Bind: maxTx=" + maxTxSize + ", maxRx=" + maxRxSize + ", groupId=" + groupId + ", ctxElems="
                        + ctxElems + ", presCtxId=" + presCtxId + ", trfSyntax=" + trfSyntax);
                sess.debugPrintln("      uuid1=" + uuid1.toString());
                sess.debugPrintln("      uuid2=" + uuid2.toString());
            }

            // Update the IPC pipe file
            pipeFile.setMaxTransmitFragmentSize(maxTxSize);
            pipeFile.setMaxReceiveFragmentSize(maxRxSize);

            // Create an output DCE buffer for the reply and add the bind acknowledge header
            DCEBuffer txBuf = new DCEBuffer();

            txBuf.putBindAckHeader(dceBuf.getHeaderValue(DCEBuffer.HDR_CALLID));
            txBuf.setHeaderValue(DCEBuffer.HDR_FLAGS, DCEBuffer.FLG_ONLYFRAG);

            // Pack the bind acknowledge DCE reply
            txBuf.putShort(maxTxSize);
            txBuf.putShort(maxRxSize);
            txBuf.putInt(0x53F0);

            String srvPipeName = DCEPipeType.getServerPipeName(pipeFile.getPipeId());
            txBuf.putShort(srvPipeName.length() + 1);
            txBuf.putASCIIString(srvPipeName, true, DCEBuffer.ALIGN_INT);
            txBuf.putInt(1);
            txBuf.putShort(0);
            txBuf.putShort(0);
            txBuf.putUUID(uuid2, true);

            txBuf.setHeaderValue(DCEBuffer.HDR_FRAGLEN, txBuf.getLength());

            // Attach the reply buffer to the pipe file
            pipeFile.setBufferedData(txBuf);
        }
        catch (DCEBufferException ex) {
            throw new SMBSrvException(SMBStatus.NTNotSupported, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
        }
    }

    /**
     * Process a DCE request
     *
     * @param sess     SMBSrvSession
     * @param inBuf    DCEBuffer
     * @param pipeFile DCEPipeFile
     * @param smbPkt   SMBSrvPacket
     * @exception IOException I/O error
     * @exception SMBSrvException SMB error
     */
    public static final void procDCERequest(SMBSrvSession sess, DCEBuffer inBuf, DCEPipeFile pipeFile, SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException {

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_DCERPC))
            sess.debugPrintln("DCE Request opNum=0x" + Integer.toHexString(inBuf.getHeaderValue(DCEBuffer.HDR_OPCODE)));

        // Pass the request to the DCE pipe request handler
        if (pipeFile.hasRequestHandler())
            pipeFile.getRequestHandler().processRequest(sess, inBuf, pipeFile, smbPkt);
        else
            throw new SMBSrvException(SMBStatus.NTAccessDenied, SMBStatus.SRVNoAccessRights, SMBStatus.ErrSrv);
    }
}
