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

import org.filesys.server.SrvSession;


/**
 * Transactional Filesystem Interface
 *
 * <p>Optional interface that a filesystem driver can implement to add support for transactions around filesystem calls.
 *
 * @author gkspencer
 */
public interface TransactionalFilesystemInterface {

    /**
     * Begin a read-only transaction
     *
     * @param sess SrvSession
     */
    public void beginReadTransaction(SrvSession sess);

    /**
     * Begin a writeable transaction
     *
     * @param sess SrvSession
     */
    public void beginWriteTransaction(SrvSession sess);

    /**
     * End an active transaction
     *
     * @param sess SrvSession
     * @param tx   Object
     */
    public void endTransaction(SrvSession sess, Object tx);
}
