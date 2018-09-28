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

package org.filesys.smb.dcerpc.info;

import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCEList;
import org.filesys.smb.dcerpc.DCEReadable;

/**
 * Server File Information List Class
 *
 * @author gkspencer
 */
public class ServerFileInfoList extends DCEList {

    /**
     * Default constructor
     */
    public ServerFileInfoList() {
        super();
    }

    /**
     * Class constructor
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
    public ServerFileInfoList(DCEBuffer buf) throws DCEBufferException {
        super(buf);
    }

    /**
     * Create a new connection information object
     *
     * @return DCEReadable
     */
    protected DCEReadable getNewObject() {
        return new ServerFileInfo(getInformationLevel());
    }

    /**
     * Read a list of file information objects
     *
     * @param buf DCEBuffer
     * @throws DCEBufferException DCE buffer error
     */
/**
    public void readObject(DCEBuffer buf) throws DCEBufferException {

        // Check if the container is valid, if so the object list will be valid

        if (containerIsValid() == false) return;

        // Read the container object count and array pointer

        int numEntries = buf.getInt();
        if (buf.getPointer() != 0) {

            // Get the array element count

            int elemCnt = buf.getInt();

            while (elemCnt-- > 0) {

                // Create a server file information object and add to the list

                ServerFileInfo finfo = new ServerFileInfo(getInformationLevel());
                addObject(finfo);

                // Load the main object details

                finfo.readObject(buf);
            }

            // Load the strings for the file information objects

            for (int i = 0; i < numberOfEntries(); i++) {

                // Get a file information object

                ServerFileInfo finfo = (ServerFileInfo) getList().get(i);

                // Load the strings for the file information

                finfo.readStrings(buf);
            }
        }
    }
**/
}
