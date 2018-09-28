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
 * <p>This error is generated when a tree connection has no free file slots. The new file open
 * request will be rejected by the server.
 *
 * @author gkspencer
 */
public class TooManyFilesException extends Exception {

    private static final long serialVersionUID = -5947001587171843393L;

    /**
     * TooManyFilesException constructor.
     */
    public TooManyFilesException() {
        super();
    }

    /**
     * TooManyFilesException constructor.
     *
     * @param s java.lang.String
     */
    public TooManyFilesException(String s) {
        super(s);
    }
}
