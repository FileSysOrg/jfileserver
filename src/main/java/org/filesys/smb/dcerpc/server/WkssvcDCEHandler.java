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

package org.filesys.smb.dcerpc.server;

import java.io.IOException;

import org.filesys.debug.Debug;
import org.filesys.smb.Dialect;
import org.filesys.smb.SMBStatus;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.Wkssvc;
import org.filesys.smb.dcerpc.info.ServerInfo;
import org.filesys.smb.dcerpc.info.WorkstationInfo;
import org.filesys.smb.server.SMBConfigSection;
import org.filesys.smb.server.SMBServer;
import org.filesys.smb.server.SMBSrvException;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;

/**
 * Wkssvc DCE/RPC Handler Class
 *
 * @author gkspencer
 */
public class WkssvcDCEHandler implements DCEHandler {

    /**
     * Process a WksSvc DCE/RPC request
     *
     * @param sess     SMBSrvSession
     * @param inBuf    DCEBuffer
     * @param pipeFile DCEPipeFile
     * @param smbPkt Request packet
     * @throws IOException I/O error
     * @throws SMBSrvException SMB error
     */
    public void processRequest(SMBSrvSession sess, DCEBuffer inBuf, DCEPipeFile pipeFile, SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException {

        // Get the operation code and move the buffer pointer to the start of the request data
        int opNum = inBuf.getHeaderValue(DCEBuffer.HDR_OPCODE);
        try {
            inBuf.skipBytes(DCEBuffer.OPERATIONDATA);
        }
        catch (DCEBufferException ex) {
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_DCERPC))
            sess.debugPrintln("DCE/RPC WksSvc request=" + Wkssvc.getOpcodeName(opNum));

        // Create the output DCE buffer and add the response header
        DCEBuffer outBuf = new DCEBuffer();
        outBuf.putResponseHeader(inBuf.getHeaderValue(DCEBuffer.HDR_CALLID), 0);

        // Process the request
        boolean processed = false;

        switch (opNum) {

            // Get workstation information
            case Wkssvc.NetWkstaGetInfo:
                processed = netWkstaGetInfo(sess, inBuf, outBuf);
                break;

            // Unsupported function
            default:
                break;
        }

        // Return an error status if the request was not processed
        if (processed == false)
            throw new SMBSrvException( SMBStatus.NTNotSupported);
//        {
//            sess.sendErrorResponseSMB(smbPkt, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
//            return;
//        }

        // Set the allocation hint for the response
        outBuf.setHeaderValue(DCEBuffer.HDR_ALLOCHINT, outBuf.getLength());

        // Attach the output buffer to the pipe file
        pipeFile.setBufferedData(outBuf);
    }

    /**
     * Get workstation infomation
     *
     * @param sess   SMBSrvSession
     * @param inBuf  DCEPacket
     * @param outBuf DCEPacket
     * @return boolean
     */
    protected final boolean netWkstaGetInfo(SMBSrvSession sess, DCEBuffer inBuf, DCEBuffer outBuf) {

        // Decode the request
        String srvName = null;
        int infoLevel = 0;

        try {
            inBuf.skipPointer();
            srvName = inBuf.getString(DCEBuffer.ALIGN_INT);
            infoLevel = inBuf.getInt();
        }
        catch (DCEBufferException ex) {
            return false;
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_DCERPC))
            sess.debugPrintln("NetWkstaGetInfo srvName=" + srvName + ", infoLevel=" + infoLevel);

        // Create the workstation information and set the common values
        WorkstationInfo wkstaInfo = new WorkstationInfo(infoLevel);

        SMBServer srv = sess.getSMBServer();
        wkstaInfo.setWorkstationName(srv.getServerName());
        wkstaInfo.setDomain(srv.getSMBConfiguration().getDomainName());

        // Determine if the server is using the NT SMB dialect and set the platofmr id accordingly
        SMBConfigSection smbConfig = sess.getSMBServer().getSMBConfiguration();
        if (smbConfig != null && smbConfig.getEnabledDialects().hasDialect(Dialect.NT) == true) {
            wkstaInfo.setPlatformId(ServerInfo.PLATFORM_NT);
            wkstaInfo.setVersion(5, 1);
        } else {
            wkstaInfo.setPlatformId(ServerInfo.PLATFORM_OS2);
            wkstaInfo.setVersion(4, 0);
        }

        // Write the server information to the DCE response
        wkstaInfo.writeObject(outBuf, outBuf);
        outBuf.putInt(0);

        // Indicate that the request was processed successfully
        return true;
    }
}
