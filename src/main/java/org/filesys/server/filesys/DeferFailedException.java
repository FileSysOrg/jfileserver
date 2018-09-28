/*
 * Copyright (C) 2006-2011 Alfresco Software Limited.
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
 * Defer Failed Exception Class
 *
 * <p>Indicates that a request could not be deferred for later processing, and and appropriate error
 * should be returned.
 *
 * @author gkspencer
 */
public class DeferFailedException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     */
    public DeferFailedException() {
        super();
    }

    /**
     * Class constructor.
     *
     * @param s String
     */
    public DeferFailedException(String s) {
        super(s);
    }
}
