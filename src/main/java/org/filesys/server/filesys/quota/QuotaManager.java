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

package org.filesys.server.filesys.quota;

import java.io.IOException;

import org.filesys.server.SrvSession;
import org.filesys.server.filesys.DiskDeviceContext;
import org.filesys.server.filesys.DiskInterface;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.TreeConnection;


/**
 * Quota Manager Interface
 *
 * <p>Provides the basic interface for filesystem disk quota management.
 *
 * @author gkspencer
 */
public interface QuotaManager {

    /**
     * Start the quota manager.
     *
     * @param disk DiskInterface
     * @param ctx  DiskDeviceContext
     * @throws QuotaManagerException Error starting the quota manager
     */
    public void startManager(DiskInterface disk, DiskDeviceContext ctx)
            throws QuotaManagerException;

    /**
     * Stop the quota manager
     *
     * @param disk DiskInterface
     * @param ctx  DiskDeviceContext
     * @throws QuotaManagerException Error stopping the quota manager
     */
    public void stopManager(DiskInterface disk, DiskDeviceContext ctx)
            throws QuotaManagerException;

    /**
     * Allocate space on the filesystem.
     *
     * @param sess  SrvSession
     * @param tree  TreeConnection
     * @param file  NetworkFile
     * @param alloc long
     * @return long
     * @throws IOException I/O error
     */
    public long allocateSpace(SrvSession sess, TreeConnection tree, NetworkFile file, long alloc)
            throws IOException;

    /**
     * Release space to the free space for the filesystem.
     *
     * @param sess  SrvSession
     * @param tree  TreeConnection
     * @param fid   int
     * @param path  String
     * @param alloc long
     * @throws IOException I/O error
     */
    public void releaseSpace(SrvSession sess, TreeConnection tree, int fid, String path, long alloc)
            throws IOException;

    /**
     * Return the free space available in bytes
     *
     * @return long
     */
    public long getAvailableFreeSpace();

    /**
     * Return the free space available to the specified user/session
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return long
     */
    public long getUserFreeSpace(SrvSession sess, TreeConnection tree);

    /**
     * Return total space available to the specified user/session
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return long
     */
    public long getUserTotalSpace(SrvSession sess, TreeConnection tree);
}
