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

package org.filesys.smb;

/**
 * Invalid UNC path exception class
 *
 * <p>The InvalidUNCPathException indicates that a UNC path has an invalid format.
 *
 * @author gkspencer
 * @see PCShare
 */
public class InvalidUNCPathException extends Exception {

    private static final long serialVersionUID = -5286647687687183134L;

    /**
     * Default invalid UNC path exception constructor.
     */

    public InvalidUNCPathException() {
    }

    /**
     * Invalid UNC path exception constructor, with additional details string.
     *
     * @param msg String
     */

    public InvalidUNCPathException(String msg) {
        super(msg);
    }
}
