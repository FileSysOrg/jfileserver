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

import java.io.Serializable;
import java.util.ArrayList;

/**
 * File Lock List Class
 *
 * <p>Contains a list of the current locks on a file.
 *
 * @author gkspencer
 */
public class FileLockList implements Serializable {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // List of file locks
    private ArrayList<FileLock> m_lockList;

    /**
     * Construct an empty file lock list.
     */
    public FileLockList() {
        m_lockList = new ArrayList<FileLock>();
    }

    /**
     * Add a lock to the list
     *
     * @param lock Lock to be added to the list.
     */
    public final void addLock(FileLock lock) {
        m_lockList.add(lock);
    }

    /**
     * Find the matching lock
     *
     * @param lock FileLock
     * @return FileLock
     */
    public final FileLock findLock(FileLock lock) {
        return findLock(lock.getOffset(), lock.getLength(), lock.getProcessId());
    }

    /**
     * Find the matching lock
     *
     * @param offset long
     * @param len    long
     * @param pid    int
     * @return FileLock
     */
    public final FileLock findLock(long offset, long len, int pid) {

        // Check if there are any locks in the list
        if (numberOfLocks() == 0)
            return null;

        // Search for the required lock
        FileLock fLock = null;

        for (int i = 0; i < numberOfLocks(); i++) {

            // Get the current lock details
            fLock = getLockAt(i);
            if (fLock.getOffset() == offset && fLock.getLength() == len) {

                // Return the matching lock
                return fLock;
            }
        }

        // Lock not found
        return null;
    }

    /**
     * Remove a lock from the list
     *
     * @param lock FileLock
     * @return FileLock
     */
    public final FileLock removeLock(FileLock lock) {
        return removeLock(lock.getOffset(), lock.getLength(), lock.getProcessId());
    }

    /**
     * Remove a lock from the list
     *
     * @param offset Starting offset of the lock
     * @param len    Locked section length
     * @param pid    Owner process id
     * @return FileLock
     */
    public final FileLock removeLock(long offset, long len, int pid) {

        // Check if there are any locks in the list
        if (numberOfLocks() == 0)
            return null;

        // Search for the required lock
        for (int i = 0; i < numberOfLocks(); i++) {

            // Get the current lock details
            FileLock curLock = getLockAt(i);
            if (curLock.getOffset() == offset && curLock.getLength() == len) {

                // Remove the lock from the list
                m_lockList.remove(i);
                return curLock;
            }
        }

        // Lock not found
        return null;
    }

    /**
     * Remove all locks from the list
     */
    public final void removeAllLocks() {
        m_lockList.clear();
    }

    /**
     * Return the specified lock details
     *
     * @param idx Lock index
     * @return FileLock
     */
    public final FileLock getLockAt(int idx) {
        if (idx < m_lockList.size())
            return m_lockList.get(idx);
        return null;
    }

    /**
     * Remove the lock at the specified index in the list
     *
     * @param idx Lock index
     * @return FileLock
     */
    public final FileLock removeLockAt(int idx) {
        if (idx < m_lockList.size())
            return m_lockList.remove(idx);
        return null;
    }

    /**
     * Check if the new lock should be allowed by comparing with the locks in the list.
     *
     * @param lock FileLock
     * @return boolean true if the lock can be granted, else false.
     */
    public final boolean allowsLock(FileLock lock) {

        // If the list is empty we can allow the lock request
        if (numberOfLocks() == 0)
            return true;

        // Search for any overlapping locks
        for (int i = 0; i < numberOfLocks(); i++) {

            // Get the current lock details
            FileLock curLock = getLockAt(i);
            if (curLock.hasOverlap(lock))
                return false;
        }

        // The lock does not overlap with any existing locks
        return true;
    }

    /**
     * Check if the file is readable for the specified section of the file and process id
     *
     * @param lock FileLock
     * @return boolean
     */
    public final boolean canReadFile(FileLock lock) {
        return canReadFile(lock.getOffset(), lock.getLength(), lock.getProcessId());
    }

    /**
     * Check if the file is readable for the specified section of the file and process id
     *
     * @param offset long
     * @param len    long
     * @param pid    int
     * @return boolean
     */
    public final boolean canReadFile(long offset, long len, int pid) {

        // If the list is empty we can allow the read request
        if (numberOfLocks() == 0)
            return true;

        // Search for a lock that prevents the read
        for (int i = 0; i < numberOfLocks(); i++) {

            // Get the current lock details
            FileLock curLock = getLockAt(i);

            // Check if the process owns the lock, if not then check if there is an overlap
            if (curLock.getProcessId() != pid) {

                // Check if the read overlaps with the locked area
                if (curLock.hasOverlap(offset, len) == true)
                    return false;
            }
        }

        // The lock does not overlap with any existing locks
        return true;
    }

    /**
     * Check if the file is writeable for the specified section of the file and process id
     *
     * @param lock FileLock
     * @return boolean
     */
    public final boolean canWriteFile(FileLock lock) {
        return canWriteFile(lock.getOffset(), lock.getLength(), lock.getProcessId());
    }

    /**
     * Check if the file is writeable for the specified section of the file and process id
     *
     * @param offset long
     * @param len    long
     * @param pid    int
     * @return boolean
     */
    public final boolean canWriteFile(long offset, long len, int pid) {

        // If the list is empty we can allow the read request
        if (numberOfLocks() == 0)
            return true;

        // Search for a lock that prevents the read
        for (int i = 0; i < numberOfLocks(); i++) {

            // Get the current lock details
            FileLock curLock = getLockAt(i);

            // Check if the process owns the lock, if not then check if there is an overlap
            if (curLock.getProcessId() != pid) {

                // Check if the read overlaps with the locked area
                if (curLock.hasOverlap(offset, len) == true)
                    return false;
            }
        }

        // The lock does not overlap with any existing locks
        return true;
    }

    /**
     * Return the count of locks in the list.
     *
     * @return int Number of locks in the list.
     */
    public final int numberOfLocks() {
        return m_lockList.size();
    }

    /**
     * Return the lock list as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Locklist:");
        str.append(numberOfLocks());

        if (numberOfLocks() > 0) {
            for (int i = 0; i < numberOfLocks(); i++) {
                str.append(getLockAt(i));
                str.append(",");
            }
        }

        str.append("]");

        return str.toString();
    }
}
