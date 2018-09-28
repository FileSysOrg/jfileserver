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

package org.filesys.smb.server.nio;

/**
 * Asynchronous Writes Handler Interface
 *
 * @author gkspencer
 */
public interface AsynchronousWritesHandler {

    /**
     * Return the count of queued writes
     *
     * @return int
     */
    public int getQueuedWriteCount();

    /**
     * Process the write queue and send pending data until outgoing buffers are full
     *
     * @return int Number of requests that were removed from the queue
     */
    public int processQueuedWrites();
}
