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

/**
 * Disk Size Interface
 *
 * <p>Optional interface that a DiskInterface driver can implement to provide disk sizing information. The disk size
 * information may also be specified via the configuration.
 *
 * @author gkspencer
 */
public interface DiskSizeInterface {

    /**
     * Get the disk information for this shared disk device.
     *
     * @param ctx     DiskDeviceContext
     * @param diskDev SrvDiskInfo
     * @throws java.io.IOException The exception description.
     */
    public void getDiskInformation(DiskDeviceContext ctx, SrvDiskInfo diskDev)
            throws java.io.IOException;
}
