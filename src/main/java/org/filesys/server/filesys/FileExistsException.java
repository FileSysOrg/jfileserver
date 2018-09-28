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
 * <p>This exception may be thrown by a disk interface when an attempt to create a new file fails because
 * the file already exists.
 *
 * @author gkspencer
 */
public class FileExistsException extends java.io.IOException {

    private static final long serialVersionUID = 6314398441511723019L;

    /**
     * FileExistsException constructor.
     */
    public FileExistsException() {
        super();
    }

    /**
     * FileExistsException constructor.
     *
     * @param s java.lang.String
     */
    public FileExistsException(String s) {
        super(s);
    }
}
