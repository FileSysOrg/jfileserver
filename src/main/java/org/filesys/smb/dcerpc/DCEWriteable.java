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

package org.filesys.smb.dcerpc;

/**
 * DCE/RPC Writeable Interface
 *
 * <p>A class that implements the DCEWriteable interface can save itself to a DCE buffer.
 *
 * @author gkspencer
 */
public interface DCEWriteable {

    /**
     * Write the object state to DCE/RPC buffers.
     *
     * <p>If a list of objects is being written the strings will be written after the objects so the
     * second buffer will be specified.
     *
     * <p>If a single object is being written to the buffer the second buffer may be null or be the same
     * buffer as the main buffer.
     *
     * @param buf    DCEBuffer
     * @param strBuf DCEBuffer
     * @exception DCEBufferException DCE buffer error
     */
    public void writeObject(DCEBuffer buf, DCEBuffer strBuf)
            throws DCEBufferException;
}
