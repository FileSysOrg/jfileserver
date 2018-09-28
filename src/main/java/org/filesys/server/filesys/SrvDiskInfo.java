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

import org.filesys.smb.PCShare;

/**
 * <p>The class extends the client side version of the disk information class to allow
 * values to be set after construction by a disk interface implementation.
 *
 * <p>The class contains information about the total, free and used blocks on a disk device, and the
 * block size and blocks per allocation unit of the device.
 *
 * @author gkspencer
 */
public class SrvDiskInfo extends DiskInfo {

    /**
     * Create an empty disk information object.
     */
    public SrvDiskInfo() {
    }

    /**
     * Construct a disk information object.
     *
     * @param totunits int
     * @param blkunit  int
     * @param blksiz   int
     * @param freeunit int
     */
    public SrvDiskInfo(int totunits, int blkunit, int blksiz, int freeunit) {
        super(null, (long) totunits, blkunit, blksiz, (long) freeunit);
    }

    /**
     * Construct a disk information object.
     *
     * @param totunits long
     * @param blkunit  long
     * @param blksiz   long
     * @param freeunit long
     */
    public SrvDiskInfo(long totunits, long blkunit, long blksiz, long freeunit) {
        super(null, totunits, (int) blkunit, (int) blksiz, freeunit);
    }

    /**
     * Class constructor
     *
     * @param shr      PCShare
     * @param totunits int
     * @param blkunit  int
     * @param blksiz   int
     * @param freeunit int
     */
    protected SrvDiskInfo(PCShare shr, int totunits, int blkunit, int blksiz, int freeunit) {
        super(shr, totunits, blkunit, blksiz, freeunit);
    }

    /**
     * Set the block size, in bytes.
     *
     * @param siz int
     */
    public final void setBlockSize(int siz) {
        m_blocksize = siz;
    }

    /**
     * Set the number of blocks per filesystem allocation unit.
     *
     * @param blks int
     */
    public final void setBlocksPerAllocationUnit(int blks) {
        m_blockperunit = blks;
    }

    /**
     * Set the number of free units on this shared disk device.
     *
     * @param units int
     */
    public final void setFreeUnits(int units) {
        m_freeunits = units;
    }

    /**
     * Set the total number of units on this shared disk device.
     *
     * @param units int
     */
    public final void setTotalUnits(int units) {
        m_totalunits = units;
    }

    /**
     * Set the block size, in bytes.
     *
     * @param siz long
     */
    public final void setBlockSize(long siz) {
        m_blocksize = siz;
    }

    /**
     * Set the number of blocks per filesystem allocation unit.
     *
     * @param blks long
     */
    public final void setBlocksPerAllocationUnit(long blks) {
        m_blockperunit = blks;
    }

    /**
     * Set the number of free units on this shared disk device.
     *
     * @param units long
     */
    public final void setFreeUnits(long units) {
        m_freeunits = units;
    }

    /**
     * Set the total number of units on this shared disk device.
     *
     * @param units long
     */
    public final void setTotalUnits(long units) {
        m_totalunits = units;
    }

    /**
     * Set the node name.
     *
     * @param name java.lang.String
     */
    protected final void setNodeName(String name) {
        m_nodename = name;
    }

    /**
     * Set the shared device name.
     *
     * @param name java.lang.String
     */
    protected final void setShareName(String name) {
        m_share = name;
    }

    /**
     * Copy the disk information details
     *
     * @param disk SrvDiskInfo
     */
    public final void copyFrom(SrvDiskInfo disk) {

        //	Copy the details to this object
        setBlockSize(disk.getBlockSize());
        setBlocksPerAllocationUnit(disk.getBlocksPerAllocationUnit());

        setFreeUnits(disk.getFreeUnits());
        setTotalUnits(disk.getTotalUnits());
    }
}
