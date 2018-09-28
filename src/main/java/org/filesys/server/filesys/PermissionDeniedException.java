/*
 * Copyright (C) 2006-2012 Alfresco Software Limited.
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

import java.io.IOException;

/**
 * <p>
 * Thrown when a file/folder rename is not allowed as the user does not have permission to rename
 * the file/folder.
 *
 * @author gkspencer
 */
public class PermissionDeniedException extends IOException {

    private static final long serialVersionUID = -7328006359992815029L;

    /**
     * Default constructor
     */
    public PermissionDeniedException() {
        super();
    }

    /**
     * PermissionDeniedException constructor.
     *
     * @param s String
     */
    public PermissionDeniedException(String s) {
        super(s);
    }

    /**
     * PermissionDeniedException constructor.
     *
     * @param s  String
     * @param ex Throwable
     */
    public PermissionDeniedException(String s, Throwable ex) {
        super(s, ex);
    }
}
