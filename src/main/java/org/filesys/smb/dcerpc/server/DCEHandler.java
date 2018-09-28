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

import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.server.SMBSrvException;
import org.filesys.smb.server.SMBSrvPacket;
import org.filesys.smb.server.SMBSrvSession;

/**
 * DCE Request Handler Interface
 *
 * @author gkspencer
 */
public interface DCEHandler {

    /**
     * Process a DCE/RPC request
     *
     * @param sess     SMBSrvSession
     * @param inBuf    DCEBuffer
     * @param pipeFile DCEPipeFile
     * @param smbPkt   Request packet
     * @throws IOException I/O error
     * @throws SMBSrvException SMB error
     */
    public void processRequest(SMBSrvSession sess, DCEBuffer inBuf, DCEPipeFile pipeFile, SMBSrvPacket smbPkt)
            throws IOException, SMBSrvException;

    // TODO: Remove the smbPkt parameter
}
