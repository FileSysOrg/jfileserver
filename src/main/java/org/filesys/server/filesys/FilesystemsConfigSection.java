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

import java.util.Enumeration;

import org.filesys.server.config.ConfigSection;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.server.core.DeviceContext;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.core.SharedDeviceList;
import org.filesys.server.filesys.cache.FileStateCache;
import org.filesys.server.filesys.cache.FileStateReaper;

/**
 * Filesystems Configuration Section Class
 *
 * @author gkspencer
 */
public class FilesystemsConfigSection extends ConfigSection {

    // Filesystems configuration section name
    public static final String SectionName = "Filesystems";

    //  List of shared devices
    private SharedDeviceList m_shareList;

    // File state reaper, enabled if one or more filesystems uses a file state cache
    private FileStateReaper m_stateReaper;

    /**
     * Class constructor
     *
     * @param config ServerConfiguration
     */
    public FilesystemsConfigSection(ServerConfiguration config) {
        super(SectionName, config);

        // Allocate the share list
        m_shareList = new SharedDeviceList();
    }

    /**
     * Return the shared device list.
     *
     * @return SharedDeviceList
     */
    public final SharedDeviceList getShares() {
        return m_shareList;
    }

    /**
     * Add a shared device to the server configuration.
     *
     * @param shr SharedDevice
     * @return boolean
     */
    public final boolean addShare(SharedDevice shr) {
        return m_shareList.addShare(shr);
    }

    /**
     * Add a file state cache to the file state reaper thread, to scan for expired file states
     *
     * @param filesysName String
     * @param stateCache  FileStateCache
     */
    public synchronized final void addFileStateCache(String filesysName, FileStateCache stateCache) {

        // Check if the file state reaper has been allocated
        if (m_stateReaper == null)
            m_stateReaper = new FileStateReaper();

        // Add the state cache to the reaper thread
        m_stateReaper.addStateCache(filesysName, stateCache);
    }

    /**
     * Close the configuration section
     */
    public final void closeConfig() {

        // Close the shared filesystems
        if (getShares() != null && getShares().numberOfShares() > 0) {

            // Close the shared filesystems
            Enumeration<SharedDevice> shareEnum = getShares().enumerateShares();

            while (shareEnum.hasMoreElements()) {

                SharedDevice share = shareEnum.nextElement();
                DeviceContext devCtx = share.getContext();

                if (devCtx != null) {

                    // Remove the filesystem from the file state cache, if enabled
                    if (m_stateReaper != null)
                        m_stateReaper.removeStateCache(share.getName());

                    // Close the device context
                    devCtx.CloseContext();
                }
            }

            // Close the file state reaper thread
            if (m_stateReaper != null) {
                m_stateReaper.shutdownRequest();
                m_stateReaper = null;
            }
        }
    }
}
