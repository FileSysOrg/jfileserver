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

package org.filesys.locking;

import org.filesys.server.locking.LockParams;

import java.io.Serializable;

/**
 * File Lock Class
 *
 * <p>
 * Contains the details of a single file lock.
 *
 * @author gkspencer
 */
public class FileLock implements Serializable {

    // Lock states
    public enum State {
        Locked,
        Unlocked
    }

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Constants
    public static final long LockWholeFile = 0xFFFFFFFFFFFFFFFFL;

    // Start lock offset and length
    private long m_offset;
    private long m_length;

    // Owner details
    private FileLockOwner m_owner;

    // Lock flags
    private int m_flags;

    /**
     * Default constructor
     */
    protected FileLock() {
    }

    /**
     * Class constructor
     *
     * @param offset long
     * @param len    long
     * @param owner  FileLockOwner
     */
    public FileLock(long offset, long len, FileLockOwner owner) {
        setOffset(offset);
        setLength(len);
        setOwner(owner);
    }

    /**
     * Class constructor
     *
     * @param params LockParams
     */
    public FileLock(LockParams params) {
        setOffset( params.getOffset());
        setLength( params.getLength());

        setOwner( params.getOwner());
        setFlags( params.getFlags());
    }

    /**
     * Get the starting lock offset
     *
     * @return long Starting lock offset.
     */
    public final long getOffset() {
        return m_offset;
    }

    /**
     * Set the starting lock offset.
     *
     * @param offset Starting lock offset
     */
    public final void setOffset(long offset) {
        m_offset = offset;
    }

    /**
     * Get the locked section length
     *
     * @return long Locked section length
     */
    public final long getLength() {
        return m_length;
    }

    /**
     * Set the locked section length
     *
     * @param len Locked section length
     */
    public final void setLength(long len) {
        if (len < 0)
            m_length = LockWholeFile;
        else
            m_length = len;
    }

    /**
     * Check if the file lock has an owner
     *
     * @return boolean
     */
    public final boolean hasOwner() { return m_owner != null; }

    /**
     * Get the lock owner details
     *
     * @return FileLockOwner
     */
    public final FileLockOwner getOwner() {
        return m_owner;
    }

    /**
     * Return the lock flags
     *
     * @return int
     */
    public final int getFlags() {
        return m_flags;
    }

    /**
     * Deterine if the lock is locking the whole file
     *
     * @return boolean
     */
    public final boolean isWholeFile() {
        return m_length == LockWholeFile ? true : false;
    }

    /**
     * Set the lock owner
     *
     * @param owner FileLockOwner
     */
    public final void setOwner(FileLockOwner owner) {

        // Make sure the lock owner is valid
        if ( owner == null)
            throw new RuntimeException( "Lock owner is null");

        m_owner = owner;
    }

    /**
     * Set the lock flags
     *
     * @param flags int
     */
    public final void setFlags(int flags) {
        m_flags = flags;
    }

    /**
     * Check if the specified locks byte range overlaps this locks byte range.
     *
     * @param lock FileLock
     * @return boolean
     */
    public final boolean hasOverlap(FileLock lock) {
        return hasOverlap(lock.getOffset(), lock.getLength());
    }

    /**
     * Check if the lock equals the specified lock offset and length
     *
     * @param offset long
     * @param len long
     * @return boolean
     */
    public final boolean isEqualTo( long offset, long len) {
        if ( getOffset() == offset && getLength() == len)
            return true;
        return false;
    }

    /**
     * Check if the specified locks byte range overlaps this locks byte range.
     *
     * @param offset long
     * @param len    long
     * @return boolean
     */
    public final boolean hasOverlap(long offset, long len) {

        // Check if the lock is for the whole file
        if (isWholeFile())
            return true;

        // Check if the locks overlap
        long endOff = getOffset() + (getLength() - 1);

        if (getOffset() < offset && endOff < offset)
            return false;

        endOff = offset + (len - 1);

        if (getOffset() > endOff)
            return false;

        // Locks overlap
        return true;
    }

    /**
     * Return the lock details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Owner=");
        str.append(getOwner());
        str.append(",Offset=");
        str.append(getOffset());
        str.append(",Len=");
        str.append(getLength());

        if ( getFlags() != 0) {
            str.append(",flags=0x");
            str.append(Integer.toHexString(getFlags()));
        }

        str.append("]");

        return str.toString();
    }
}
