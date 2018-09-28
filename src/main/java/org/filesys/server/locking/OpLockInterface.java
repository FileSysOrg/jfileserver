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

package org.filesys.server.locking;

import org.filesys.server.SrvSession;
import org.filesys.server.filesys.TreeConnection;

/**
 * OpLock Interface
 *
 * <p>Optional interface that a DiskInterface driver can implement to provide SMB oplock support.
 *
 * @author gkspencer
 */
public interface OpLockInterface {

    /**
     * Return the oplock manager implementation associated with this virtual filesystem
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return OpLockManager
     */
    public OpLockManager getOpLockManager(SrvSession sess, TreeConnection tree);

    /**
     * Enable/disable oplock support
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return boolean
     */
    public boolean isOpLocksEnabled(SrvSession sess, TreeConnection tree);
}
