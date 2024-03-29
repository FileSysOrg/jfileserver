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

import java.io.IOException;

/**
 * <p>Thrown when an attempt is made to delete a directory that contains files or directories.
 *
 * @author gkspencer
 */
public class DirectoryNotEmptyException extends IOException {

    private static final long serialVersionUID = -4707262817813283889L;

    /**
     * Default constructor
     */
    public DirectoryNotEmptyException() {
        super();
    }

    /**
     * Class constructor.
     *
     * @param s String
     */
    public DirectoryNotEmptyException(String s) {
        super(s);
    }

    /**
     * Class constructor.
     *
     * @param s String
     * @param cause Throwable
     */
    public DirectoryNotEmptyException(String s, Throwable cause) {
        super(s, cause);
    }
}
