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

package org.filesys.server.filesys.db;

import java.io.File;

import org.filesys.server.filesys.loader.FileRequest;
import org.filesys.server.filesys.loader.FileRequestQueue;
import org.filesys.server.filesys.loader.MultipleFileRequest;

/**
 * Database Queue Interface
 *
 * <p>The database queue interface provides methods for the queueing of file load and save requests for the
 * multi-threaded background load/save class.
 *
 * @author gkspencer
 */
public interface DBQueueInterface {

    /**
     * Queue a file request.
     *
     * @param fileReq FileRequest
     * @exception DBException Database error
     */
    public void queueFileRequest(FileRequest fileReq)
            throws DBException;

    /**
     * Perform a queue cleanup deleting temporary cache files that do not have an associated save or transaction
     * request.
     *
     * @param tempDir        File
     * @param tempDirPrefix  String
     * @param tempFilePrefix String
     * @param jarFilePrefix  String
     * @return FileRequestQueue
     * @exception DBException Database error
     */
    public FileRequestQueue performQueueCleanup(File tempDir, String tempDirPrefix, String tempFilePrefix, String jarFilePrefix)
            throws DBException;

    /**
     * Delete a file request from the pending queue.
     *
     * @param fileReq FileRequest
     * @exception DBException Database error
     */
    public void deleteFileRequest(FileRequest fileReq)
            throws DBException;

    /**
     * Load a block of file requests from the database into the specified queue.
     *
     * @param seqNo    int
     * @param reqType  int
     * @param reqQueue FileRequestQueue
     * @param recLimit int
     * @return int
     * @exception DBException Database error
     */
    public int loadFileRequests(int seqNo, int reqType, FileRequestQueue reqQueue, int recLimit)
            throws DBException;

    /**
     * Load a transaction request from the queue.
     *
     * @param tranReq MultipleFileRequest
     * @return MultipleFileRequest
     * @exception DBException Database error
     */
    public MultipleFileRequest loadTransactionRequest(MultipleFileRequest tranReq)
            throws DBException;
}
