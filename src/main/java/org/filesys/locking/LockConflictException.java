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

import java.io.IOException;

/**
 * Lock Conflict Exception Class
 *
 * <p>Thrown when a lock request overlaps with an existing lock on a file.
 *
 * @author gkspencer
 */
public class LockConflictException extends IOException {

    private static final long serialVersionUID = 8287539855625316026L;

    // Details of the existing lock that conflicts with the new lock
    private FileLock m_existingLock;

    /**
     * Class constructor.
     */
    public LockConflictException() {
        super();
    }

    /**
     * Class constructor.
     *
     * @param s String
     */
    public LockConflictException(String s) {
        super(s);
    }

    /**
     * Class constructor.
     *
     * @param s  String
     * @param ex Throwable
     */
    public LockConflictException(String s, Throwable ex) {
        super(s, ex);
    }

    /**
     * Class constructor
     *
     * @param existingLock FileLock
     */
    public LockConflictException( FileLock existingLock) {
        super();

        m_existingLock = existingLock;
    }

    /**
     * Check if the exception has the existing file lock details
     *
     * @return boolean
     */
    public final boolean hasExistingLock() { return m_existingLock != null; }

    /**
     * Return the existing file lock details
     *
     * @return FileLock
     */
    public final FileLock getExistingLock() { return m_existingLock; }
}
