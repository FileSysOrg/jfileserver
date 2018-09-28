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

package org.filesys.smb.server.ntfs;

import java.io.IOException;

import org.filesys.server.SrvSession;
import org.filesys.server.filesys.TreeConnection;

/**
 * NTFS Streams Interface
 *
 * <p>Optional interface that a DiskInterface driver can implement to provide file streams support.
 *
 * @author gkspencer
 */
public interface NTFSStreamsInterface {

    /**
     * Determine if NTFS streams are enabled
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return boolean
     */
    public boolean hasStreamsEnabled(SrvSession sess, TreeConnection tree);

    /**
     * Return stream information for the specified stream
     *
     * @param sess       SrvSession
     * @param tree       TreeConnection
     * @param streamInfo StreamInfo
     * @return StreamInfo
     * @throws IOException I/O error occurred
     */
    public StreamInfo getStreamInformation(SrvSession sess, TreeConnection tree, StreamInfo streamInfo)
            throws IOException;

    /**
     * Return a list of the streams for the specified file
     *
     * @param sess     SrvSession
     * @param tree     TreeConnection
     * @param fileName String
     * @return StreamInfoList
     * @throws IOException I/O error occurred
     */
    public StreamInfoList getStreamList(SrvSession sess, TreeConnection tree, String fileName)
            throws IOException;

    /**
     * Rename a stream
     *
     * @param sess      SrvSession
     * @param tree      TreeConnection
     * @param oldName   String
     * @param newName   String
     * @param overWrite boolean
     * @throws IOException I/O error occurred
     */
    public void renameStream(SrvSession sess, TreeConnection tree, String oldName, String newName, boolean overWrite)
            throws IOException;
}
