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

package org.filesys.server.filesys.db;

/**
 * Database Object Id Interface
 *
 * <p>Provides methods for loading, saving and deleting file id to object id mappings in a database table.
 *
 * @author gkspencer
 */
public interface DBObjectIdInterface {

    /**
     * Create a file id to object id mapping
     *
     * @param fileId   int
     * @param streamId int
     * @param objectId String
     * @exception DBException Database error
     */
    public void saveObjectId(int fileId, int streamId, String objectId)
            throws DBException;

    /**
     * Load the object id for the specified file id
     *
     * @param fileId   int
     * @param streamId int
     * @return String
     * @exception DBException Database error
     */
    public String loadObjectId(int fileId, int streamId)
            throws DBException;

    /**
     * Delete a file id/object id mapping
     *
     * @param fileId   int
     * @param streamId int
     * @param objectId String
     * @exception DBException Database error
     */
    public void deleteObjectId(int fileId, int streamId, String objectId)
            throws DBException;
}
