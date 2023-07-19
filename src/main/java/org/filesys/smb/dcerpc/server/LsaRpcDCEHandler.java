/*
 * Copyright (C) 2023 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.smb.dcerpc.server;

import org.filesys.debug.Debug;
import org.filesys.smb.Dialect;
import org.filesys.smb.SMBStatus;
import org.filesys.smb.dcerpc.*;
import org.filesys.smb.dcerpc.info.DsRoleMachineRole;
import org.filesys.smb.dcerpc.info.DsRolePrimaryDomainInfoBasic;
import org.filesys.smb.dcerpc.info.ServerInfo;
import org.filesys.smb.dcerpc.info.WorkstationInfo;
import org.filesys.smb.server.*;

import java.io.IOException;

/**
 * Local Security Authority DCE/RPC Handelr Class
 *
 * @author gkspencer
 */
public class LsaRpcDCEHandler implements DCEHandler {

    @Override
    public void processRequest(SMBSrvSession sess, DCEBuffer inBuf, DCEPipeFile pipeFile, SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException {

        // Get the operation code and move the buffer pointer to the start of the request data
        int opNum = inBuf.getHeaderValue(DCEBuffer.HDR_OPCODE);
        try {
            inBuf.skipBytes(DCEBuffer.OPERATIONDATA);
        } catch (DCEBufferException ex) {
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.Dbg.DCERPC))
            sess.debugPrintln("DCE/RPC LsaRpc request=" + Wkssvc.getOpcodeName(opNum));

        // Create the output DCE buffer and add the response header
        DCEBuffer outBuf = new DCEBuffer();
        outBuf.putResponseHeader(inBuf.getHeaderValue(DCEBuffer.HDR_CALLID), 0);

        // Process the request
        boolean processed = false;

        switch (opNum) {

            // Get workstation information
            case LsaRpc.DsRoleGetPrimaryDomainInformation:
                processed = dsRoleGetPrimaryDomainInformation(sess, inBuf, outBuf);
                break;

            // Unsupported function
            default:
                break;
        }

        // Return an error status if the request was not processed
        if (!processed)
            throw new SMBSrvException(SMBStatus.NTNotSupported);

        // Set the allocation hint for the response
        outBuf.setHeaderValue(DCEBuffer.HDR_ALLOCHINT, outBuf.getLength());

        // Attach the output buffer to the pipe file
        pipeFile.setBufferedData(outBuf);
    }

    /**
     * Get domain role information
     *
     * @param sess   SMBSrvSession
     * @param inBuf  DCEPacket
     * @param outBuf DCEPacket
     * @return boolean
     */
    protected final boolean dsRoleGetPrimaryDomainInformation(SMBSrvSession sess, DCEBuffer inBuf, DCEBuffer outBuf) {

        // Decode the request
        int infoLevel = 0;

        try {
            infoLevel = inBuf.getShort();
        }
        catch (DCEBufferException ex) {
            return false;
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.Dbg.DCERPC))
            sess.debugPrintln("DsRoleGetPrimaryDomainInformation infoLevel=" + DsRolePrimaryDomainInfoLevel.getInfoLevelAsString(infoLevel));

        // Create the required information object
        DCEWriteable dceObject = null;

        switch ( infoLevel) {

            // Primary domain information
            case DsRolePrimaryDomainInfoLevel.DsRolePrimaryDomainInfoBasic:
                SMBServer srv = sess.getSMBServer();
                SMBConfigSection smbConfig = srv.getSMBConfiguration();

                dceObject = new DsRolePrimaryDomainInfoBasic(DsRoleMachineRole.StandaloneServer,
                        smbConfig.getDomainName(), smbConfig.getDNSName(), smbConfig.getForestName(), null, 0);
                break;

            // Server upgrade status
            case DsRolePrimaryDomainInfoLevel.DsRoleUpgradeStatus:
                break;

            // Operation state
            case DsRolePrimaryDomainInfoLevel.DsRoleOperationState:
                break;
        }

        // Write the requested information to the DCE response, if valid
        if ( dceObject != null) {

            try {

                // Pack the object pointer
                outBuf.putPointer( dceObject);

                // Pack the information object
                dceObject.writeObject(outBuf, outBuf);
                outBuf.putInt(0);   // status code

                return true;
            }
            catch ( DCEBufferException ex) {
                if ( Debug.hasDumpStackTraces())
                    Debug.println( ex);
            }
        }

        // Indicate that the request was not processed
        return false;
    }
}
