/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.server.locking;

import java.io.IOException;

import org.filesys.locking.FileLock;
import org.filesys.locking.LockConflictException;
import org.filesys.locking.NotLockedException;
import org.filesys.server.SrvSession;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.TreeConnection;

/**
 * Lock Manager Interface
 *
 * <p>A lock manager implementation provides file locking support for a virtual filesystem.
 *
 * @author gkspencer
 */
public interface LockManager {

    /**
     * Lock a byte range within a file, or the whole file.
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param file NetworkFile
     * @param lock FileLock
     * @throws LockConflictException Lock conflicts with an existing lock
     * @throws IOException I/O error
     */
    public void lockFile(SrvSession sess, TreeConnection tree, NetworkFile file, FileLock lock)
            throws LockConflictException, IOException;

    /**
     * Unlock a byte range within a file, or the whole file
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param file NetworkFile
     * @param lock FileLock
     * @throws NotLockedException File is not locked
     * @throws IOException I/O error
     */
    public void unlockFile(SrvSession sess, TreeConnection tree, NetworkFile file, FileLock lock)
            throws NotLockedException, IOException;

    /**
     * Create a lock object, allows the FileLock object to be extended
     *
     * @param sess   SrvSession
     * @param tree   TreeConnection
     * @param file   NetworkFile
     * @param params LockParams
     * @return FileLock
     */
    public FileLock createLockObject(SrvSession sess, TreeConnection tree, NetworkFile file, LockParams params);

    /**
     * Release all locks that a session has on a file. This method is called to perform cleanup if a file
     * is closed that has active locks or if a session abnormally terminates.
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param file NetworkFile
     */
    public void releaseLocksForFile(SrvSession sess, TreeConnection tree, NetworkFile file);
}
