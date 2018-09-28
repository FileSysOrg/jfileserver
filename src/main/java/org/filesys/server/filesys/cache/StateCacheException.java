/*
 * Copyright (C) 2005-2013 Alfresco Software Limited.
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
package org.filesys.server.filesys.cache;

/**
 * Exceptions thrown for invalid cache state
 */
public class StateCacheException extends RuntimeException {

    /**
     * Serial version UUID
     */
    private static final long serialVersionUID = 1L;

    /**
     * Constructor
     *
     * @param msgId the message id
     */
    public StateCacheException(String msgId) {
        super(msgId);
    }

    /**
     * Constructor
     *
     * @param msgId the message id
     * @param cause the exception cause
     */
    public StateCacheException(String msgId, Throwable cause) {
        super(msgId, cause);
    }
}
