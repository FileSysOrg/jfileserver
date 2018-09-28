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

package org.filesys.util.db;

/**
 * Connection Pool Listener Interface
 *
 * <p>Used to communicate database connection events such as the database server online/offline status.
 *
 * @author gkspencer
 */
public interface DBConnectionPoolListener {

    /**
     * Database online/offline status event
     *
     * @param dbonline boolean
     */
    public void databaseOnlineStatus(boolean dbonline);
}
