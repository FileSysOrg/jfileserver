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

package org.filesys.server.filesys;

import org.filesys.server.SrvSession;
import org.filesys.smb.SMBException;
import org.filesys.util.DataBuffer;

/**
 * IO Control Interface
 *
 * <p>Optional interface that a DiskInterface driver can implement to enable NT I/O control function processing.
 *
 * @author gkspencer
 */
public interface IOCtlInterface {

    /**
     * Process a filesystem I/O control request
     *
     * @param sess     Server session
     * @param tree     Tree connection.
     * @param ctrlCode I/O control code
     * @param fid      File id
     * @param dataBuf  I/O control specific input data
     * @param isFSCtrl true if this is a filesystem control, or false for a device control
     * @param filter   if bit0 is set indicates that the control applies to the share root handle
     * @return DataBuffer
     * @throws IOControlNotImplementedException I/O control code not implemented
     * @throws SMBException SMB error
     */
    public DataBuffer processIOControl(SrvSession sess, TreeConnection tree, int ctrlCode, int fid, DataBuffer dataBuf,
                                       boolean isFSCtrl, int filter)
            throws IOControlNotImplementedException, SMBException;
}
