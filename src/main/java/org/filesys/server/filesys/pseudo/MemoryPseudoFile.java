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

package org.filesys.server.filesys.pseudo;

import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.NetworkFile;

/**
 * In Memory Pseudo File Class
 *
 * <p>Pseudo file class that uses an in memory buffer for the file data.
 *
 * @author gkspencer
 */
public class MemoryPseudoFile extends PseudoFile {

    // File data buffer
    private byte[] m_data;

    /**
     * Class constructor
     *
     * @param name String
     * @param data byte[]
     */
    public MemoryPseudoFile(String name, byte[] data) {
        super(name);

        m_data = data;
    }

    /**
     * Return the file information for the pseudo file
     *
     * @return FileInfo
     */
    public FileInfo getFileInfo() {

        // Check if the file information is valid
        if (getInfo() == null) {

            // Create the file information
            FileInfo fInfo = new PseudoFileInfo(getFileName(), m_data != null ? m_data.length : 0, getAttributes());

            // Set the file creation/modification times
            fInfo.setCreationDateTime(_creationDateTime);
            fInfo.setModifyDateTime(_creationDateTime);
            fInfo.setChangeDateTime(_creationDateTime);

            // Set the allocation size, round up the actual length
            fInfo.setAllocationSize((fInfo.getSize() + 512L) & 0xFFFFFFFFFFFFFE00L);

            setFileInfo(fInfo);
        }

        // Return the file information
        return getInfo();
    }

    /**
     * Return a network file for reading/writing the pseudo file
     *
     * @param netPath String
     * @return NetworkFile
     */
    public NetworkFile getFile(String netPath) {

        // Create a pseudo file mapped to the in memory file data
        FileInfo finfo = getFileInfo();
        finfo.setPath(netPath);

        return new MemoryNetworkFile(getFileName(), m_data, finfo);
    }
}
