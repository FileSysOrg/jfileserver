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

/**
 * <p>Thrown when an attempt is made to write to a file that is read-only or the user only has read access to, or
 * open a file that is actually a directory.
 *
 * @author gkspencer
 */
public class AccessDeniedException extends java.io.IOException {

    private static final long serialVersionUID = -7914373730318995028L;

    /**
     * AccessDeniedException constructor
     */
    public AccessDeniedException() {
        super();
    }

    /**
     * AccessDeniedException constructor.
     *
     * @param s String
     */
    public AccessDeniedException(String s) {
        super(s);
    }

    /**
     * AccessDeniedException constructor.
     *
     * @param s  String
     * @param ex Throwable
     */
    public AccessDeniedException(String s, Throwable ex) {
        super(s, ex);
    }
}
