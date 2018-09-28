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
 * DCE/RPC Readable Interface
 *
 * <p>A class that implements the DCEReadable interface can load itself from a DCE buffer.
 *
 * @author gkspencer
 */
public interface DCEReadable {

    /**
     * Read the object state from the DCE/RPC buffer
     *
     * @param buf DCEBuffer
     * @exception DCEBufferException DCE buffer error
     */
    public void readObject(DCEBuffer buf)
            throws DCEBufferException;

    /**
     * Read the strings for object from the DCE/RPC buffer
     *
     * @param buf DCEBuffer
     * @exception DCEBufferException DCE buffer error
     */
    public void readStrings(DCEBuffer buf)
            throws DCEBufferException;
}
