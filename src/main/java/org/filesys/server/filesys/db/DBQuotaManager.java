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

import java.io.IOException;

import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.filesys.DiskDeviceContext;
import org.filesys.server.filesys.DiskFullException;
import org.filesys.server.filesys.DiskInterface;
import org.filesys.server.filesys.DiskSizeInterface;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.SrvDiskInfo;
import org.filesys.server.filesys.TreeConnection;
import org.filesys.server.filesys.quota.QuotaManager;
import org.filesys.server.filesys.quota.QuotaManagerException;
import org.filesys.util.MemorySize;

/**
 * JDBC Quota Manager Class
 *
 * <p>Filesystem quota management implementation that uses the JDBC database.
 *
 * @author gkspencer
 */
public class DBQuotaManager implements QuotaManager {

    //	Filesystem disk size information
    private SrvDiskInfo m_diskInfo;

    //	Total and free space available
    private long m_totalSpace;
    private long m_freeSpace;

    //	Debug enable flag
    private boolean m_debug;

    /**
     * Class constructor
     *
     * @param ctx DBDeviceContext
     * @param dbg boolean
     */
    public DBQuotaManager(DBDeviceContext ctx, boolean dbg) {

        //	Enable/disable debug output
        setDebug(dbg);
    }

    /**
     * Allocate space from the filesystem free space.
     *
     * @param sess  SrvSession
     * @param tree  TreeConnection
     * @param file  NetworkFile
     * @param alloc long
     * @return long
     * @throws IOException I/O error
     */
    public synchronized long allocateSpace(SrvSession sess, TreeConnection tree, NetworkFile file, long alloc)
            throws IOException {

        //	Check if there is enough free space to satisfy the allocation request
        if (m_freeSpace > alloc && alloc > 0) {

            //	Allocate the free space
            m_freeSpace -= alloc;

            //	DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("JDBCQuotaManager: Allocate=" + alloc + ", free=" + MemorySize.asScaledString(m_freeSpace));
        } else
            throw new DiskFullException();

        //	Return the allocation size
        return alloc;
    }

    /**
     * Release space back to the filesystem.
     *
     * @param sess  SrvSession
     * @param tree  TreeConnection
     * @param fid   int
     * @param path  String
     * @param alloc long
     * @throws IOException I/O error
     */
    public synchronized void releaseSpace(SrvSession sess, TreeConnection tree, int fid, String path, long alloc)
            throws IOException {

        //	Check if the allocation is valid
        if (alloc > 0) {

            //	Release the space back to the free space
            m_freeSpace += alloc;

            //	Check if the free space is valid
            if (m_freeSpace > m_totalSpace)
                m_freeSpace = m_totalSpace;

            //	DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("JDBCQuotaManager: Release=" + alloc + ", free=" + MemorySize.asScaledString(m_freeSpace));
        }
    }

    /**
     * Start the quota manager
     *
     * @param disk DiskInterface
     * @param ctx  DiskDeviceContext
     * @throws QuotaManagerException Failed to start the quota manager
     */
    public void startManager(DiskInterface disk, DiskDeviceContext ctx)
            throws QuotaManagerException {

        //	Access the JDBC context
        DBDeviceContext dbCtx = (DBDeviceContext) ctx;

        //	Get the disk size information for the filesystem
        if (disk instanceof DiskSizeInterface) {

            //	Get the disk size information from the driver
            DiskSizeInterface sizeInterface = (DiskSizeInterface) disk;
            m_diskInfo = new SrvDiskInfo();

            try {
                sizeInterface.getDiskInformation(ctx, m_diskInfo);
            }
            catch (IOException ex) {
                m_diskInfo = null;
            }
        } else if (dbCtx.hasDiskInformation()) {

            //	Use the static disk size information
            m_diskInfo = dbCtx.getDiskInformation();
        }

        //	Check if the disk information is valid
        if (m_diskInfo == null)
            throw new QuotaManagerException("Disk size information not available");

        //	Set the total space for the filesystem
        m_totalSpace = m_diskInfo.getDiskSizeKb() * 1024L;

        if (m_totalSpace == 0)
            throw new QuotaManagerException("Disk size not set");

        //	Get the used space from the database interface
        long usedSpace = dbCtx.getDBInterface().getUsedFileSpace();

        //	Check if the currently used space was returned
        if (usedSpace == -1L)
            throw new QuotaManagerException("Failed to calculate used space");

        //	Set the available free space
        m_freeSpace = m_totalSpace - usedSpace;

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("JDBCQuotaManager: Startup totalSpace=" + MemorySize.asScaledString(m_totalSpace) +
                    ", freeSpace=" + MemorySize.asScaledString(m_freeSpace));
    }

    /**
     * Stop the quota manager
     *
     * @param disk DiskInterface
     * @param ctx  DiskDeviceContext
     * @throws QuotaManagerException Failed to stop the quota manager
     */
    public void stopManager(DiskInterface disk, DiskDeviceContext ctx)
            throws QuotaManagerException {
    }

    /**
     * Return the available free space in bytes
     *
     * @return long
     */
    public synchronized long getAvailableFreeSpace() {
        return m_freeSpace;
    }

    /**
     * Return the free space available to the specified user/session
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return long
     */
    public long getUserFreeSpace(SrvSession sess, TreeConnection tree) {

        //	All free space is available to all users with this quota manager implementation
        return m_freeSpace;
    }

    /**
     * Return total space available to the specified user/session
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return long
     */
    public long getUserTotalSpace(SrvSession sess, TreeConnection tree) {

        return m_totalSpace;
    }

    /**
     * Determine if debug output is enabled
     *
     * @return boolean
     */
    public boolean hasDebug() {
        return m_debug;
    }

    /**
     * Enable/disable debug output
     *
     * @param dbg boolean
     */
    public void setDebug(boolean dbg) {
        m_debug = dbg;
    }
}
