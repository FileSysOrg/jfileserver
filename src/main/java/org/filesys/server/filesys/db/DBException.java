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
 * Database Interface Exception Class
 *
 * @author gkspencer
 */
public class DBException extends Exception {

    private static final long serialVersionUID = -570556453282747263L;

    /**
     * Default constructor
     */
    public DBException() {
        super();
    }

    /**
     * Class constructor
     *
     * @param msg String
     */
    public DBException(String msg) {
        super(msg);
    }

    /**
     * Class constructor
     *
     * @param msg String
     * @param cause Throwable
     */
    public DBException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
