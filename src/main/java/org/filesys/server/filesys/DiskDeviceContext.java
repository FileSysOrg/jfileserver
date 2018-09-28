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

import org.filesys.server.core.DeviceContext;
import org.filesys.server.core.DeviceContextException;
import org.filesys.server.filesys.cache.FileStateCache;
import org.filesys.server.filesys.cache.FileStateCacheListener;
import org.filesys.server.filesys.quota.QuotaManager;
import org.filesys.server.locking.LockManager;
import org.filesys.server.locking.OpLockManager;
import org.filesys.smb.server.notify.NotifyChangeHandler;
import org.filesys.smb.server.notify.NotifyRequest;

/**
 * Disk Device Context Class
 *
 * @author gkspencer
 */
public class DiskDeviceContext extends DeviceContext {

    //	Change notification handler and flag to indicate if file server should generate notifications
    private NotifyChangeHandler m_changeHandler;
    private boolean m_filesysNotifications = true;

    //	Volume information
    private VolumeInfo m_volumeInfo;

    //	Disk sizing information
    private SrvDiskInfo m_diskInfo;

    //	Quota manager
    private QuotaManager m_quotaManager;

    //	Filesystem attributes, required to enable features such as compression and encryption
    private int m_filesysAttribs;

    //	Disk device attributes, can be used to make the device appear as a removeable, read-only,
    //	or write-once device for example.
    private int m_deviceAttribs;

    // File state cache
    private FileStateCache m_stateCache;
    private boolean m_requireStateCache;

    /**
     * Class constructor
     */
    public DiskDeviceContext() {
        super();
    }

    /**
     * Class constructor
     *
     * @param devName String
     */
    public DiskDeviceContext(String devName) {
        super(devName);
    }

    /**
     * Class constructor
     *
     * @param devName   String
     * @param shareName String
     */
    public DiskDeviceContext(String devName, String shareName) {
        super(devName, shareName);
    }

    /**
     * Determine if the volume information is valid
     *
     * @return boolean
     */
    public final boolean hasVolumeInformation() {
        return m_volumeInfo != null ? true : false;
    }

    /**
     * Return the volume information
     *
     * @return VolumeInfo
     */
    public final VolumeInfo getVolumeInformation() {
        return m_volumeInfo;
    }

    /**
     * Determine if the disk sizing information is valid
     *
     * @return boolean
     */
    public final boolean hasDiskInformation() {
        return m_diskInfo != null ? true : false;
    }

    /**
     * Return the disk sizing information
     *
     * @return SMBSrvDiskInfo
     */
    public final SrvDiskInfo getDiskInformation() {
        return m_diskInfo;
    }

    /**
     * Return the filesystem attributes
     *
     * @return int
     */
    public final int getFilesystemAttributes() {
        return m_filesysAttribs;
    }

    /**
     * Return the device attributes
     *
     * @return int
     */
    public final int getDeviceAttributes() {
        return m_deviceAttribs;
    }

    /**
     * Return the filesystem type, either FileSystem.TypeFAT or FileSystem.TypeNTFS.
     * <p>
     * Defaults to FileSystem.FAT but will be overridden if the filesystem driver implements the
     * NTFSStreamsInterface.
     *
     * @return String
     */
    public String getFilesystemType() {
        return FileSystem.TypeFAT;
    }

    /**
     * Determine if the filesystem is case sensitive or not
     *
     * @return boolean
     */
    public final boolean isCaseless() {
        return (m_filesysAttribs & FileSystem.CasePreservedNames) == 0 ? true : false;
    }

    /**
     * Check if the filesystem requires a file state cache
     *
     * @return boolean
     */
    public final boolean requiresStateCache() {
        return m_requireStateCache;
    }

    /**
     * Enable/disable the change notification handler for this device
     *
     * @param ena boolean
     */
    public final void enableChangeHandler(boolean ena) {
        if (ena == true)
            m_changeHandler = new NotifyChangeHandler(this);
        else {

            //	Shutdown the change handler, if valid
            if (m_changeHandler != null)
                m_changeHandler.shutdownRequest();
            m_changeHandler = null;
        }
    }

    /**
     * Close the disk device context. Release the file state cache resources.
     */
    public void CloseContext() {

        //	Call the base class
        super.CloseContext();

        // Close the change notification handler
        if (hasChangeHandler())
            enableChangeHandler(false);
    }

    /**
     * Determine if the disk context has a change notification handler
     *
     * @return boolean
     */
    public final boolean hasChangeHandler() {
        return m_changeHandler != null ? true : false;
    }

    /**
     * Return the change notification handler
     *
     * @return NotifyChangeHandler
     */
    public final NotifyChangeHandler getChangeHandler() {
        return m_changeHandler;
    }

    /**
     * Determine if file server change notifications are enabled
     *
     * @return boolean
     */
    public final boolean hasFileServerNotifications() {
        if (m_changeHandler == null)
            return false;
        return m_filesysNotifications;
    }

    /**
     * Add a request to the change notification list
     *
     * @param req NotifyRequest
     */
    public final void addNotifyRequest(NotifyRequest req) {
        if ( m_changeHandler != null)
            m_changeHandler.addNotifyRequest(req);
    }

    /**
     * Remove a request from the notify change request list
     *
     * @param req NotifyRequest
     */
    public final void removeNotifyRequest(NotifyRequest req) {
        if ( m_changeHandler != null)
            m_changeHandler.removeNotifyRequest(req);
    }

    /**
     * Set the volume information
     *
     * @param vol VolumeInfo
     */
    public final void setVolumeInformation(VolumeInfo vol) {
        m_volumeInfo = vol;
    }

    /**
     * Set the disk information
     *
     * @param disk SMBSrvDiskInfo
     */
    public final void setDiskInformation(SrvDiskInfo disk) {
        m_diskInfo = disk;
    }

    /**
     * Check if there is a quota manager configured for this filesystem.
     *
     * @return boolean
     */
    public final boolean hasQuotaManager() {
        return m_quotaManager != null ? true : false;
    }

    /**
     * Return the quota manager for the filesystem
     *
     * @return QuotaManager
     */
    public final QuotaManager getQuotaManager() {
        return m_quotaManager;
    }

    /**
     * Set the quota manager for this filesystem
     *
     * @param quotaMgr QuotaManager
     */
    public final void setQuotaManager(QuotaManager quotaMgr) {
        m_quotaManager = quotaMgr;
    }

    /**
     * Set the filesystem attributes
     *
     * @param attrib int
     */
    public final void setFilesystemAttributes(int attrib) {
        m_filesysAttribs = attrib;
    }

    /**
     * Set the device attributes
     *
     * @param attrib int
     */
    public final void setDeviceAttributes(int attrib) {
        m_deviceAttribs = attrib;
    }

    /**
     * Enable/disable file server change notifications
     *
     * @param ena boolean
     */
    public final void setFileServerNotifications(boolean ena) {
        m_filesysNotifications = ena;
    }

    /**
     * Context has been initialized and attached to a shared device, do any startup processing in
     * this method.
     *
     * @param share DiskSharedDevice
     * @throws DeviceContextException Error starting the filesystem device
     */
    public void startFilesystem(DiskSharedDevice share)
            throws DeviceContextException {
    }

    /**
     * Determine if the connection has a file state cache
     *
     * @return boolean
     */
    public final boolean hasStateCache() {
        return m_stateCache != null ? true : false;
    }

    /**
     * Return the file state cache
     *
     * @return FileStateCache
     */
    public final FileStateCache getStateCache() {
        return m_stateCache;
    }

    /**
     * Set the file state cache
     *
     * @param stateCache FileStateCache
     */
    public final void setStateCache(FileStateCache stateCache) {
        m_stateCache = stateCache;

        // Check if the disk device context is a cache listener
        if (this instanceof FileStateCacheListener)
            m_stateCache.addStateCacheListener((FileStateCacheListener) this);
    }

    /**
     * Set/clear the requires file state cache flag
     *
     * @param reqStateCache boolean
     */
    public final void setRequiresStateCache(boolean reqStateCache) {
        m_requireStateCache = reqStateCache;
    }

    /**
     * Return the lock manager, if enabled
     *
     * @return LockManager
     */
    public LockManager getLockManager() {
        return null;
    }

    /**
     * Return the oplock manager, if enabled
     *
     * @return OpLockManager
     */
    public OpLockManager getOpLockManager() {
        return null;
    }
}
