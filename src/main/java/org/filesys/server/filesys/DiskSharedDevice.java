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

import org.filesys.server.core.DeviceInterface;
import org.filesys.server.core.ShareType;
import org.filesys.server.core.SharedDevice;

/**
 * <p>A disk shared device has a name, a driver class and a context for the driver.
 *
 * @author gkspencer
 */
public class DiskSharedDevice extends SharedDevice {

    /**
     * Construct a disk share with the specified name and device interface.
     *
     * @param name  Disk share name.
     * @param iface Disk device interface.
     * @param ctx   Context that will be passed to the device interface.
     */
    public DiskSharedDevice(String name, DeviceInterface iface, DiskDeviceContext ctx) {
        super(name, ShareType.DISK, ctx);
        setInterface(iface);
    }

    /**
     * Construct a disk share with the specified name and device interface.
     *
     * @param name   java.lang.String
     * @param iface  DeviceInterface
     * @param ctx    DeviceContext
     * @param attrib int
     */
    public DiskSharedDevice(String name, DeviceInterface iface, DiskDeviceContext ctx, int attrib) {
        super(name, ShareType.DISK, ctx);
        setInterface(iface);
        setAttributes(attrib);
    }

    /**
     * Return the disk device context
     *
     * @return DiskDeviceContext
     */
    public final DiskDeviceContext getDiskContext() {
        return (DiskDeviceContext) getContext();
    }

    /**
     * Return the disk interface
     *
     * @return DiskInterface
     */
    public final DiskInterface getDiskInterface() {
        try {
            if (getInterface() instanceof DiskInterface)
                return (DiskInterface) getInterface();
        }
        catch (Exception ex) {
        }
        return null;
    }
}
