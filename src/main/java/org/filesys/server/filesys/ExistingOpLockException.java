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
 * Exiting OpLock Exception Class
 *
 * <p>Thrown when trying to set an oplock on a file that already has an active oplock.
 *
 * @author gkspencer
 */
public class ExistingOpLockException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     */
    public ExistingOpLockException() {
        super();
    }

    /**
     * Class constructor.
     *
     * @param s String
     */
    public ExistingOpLockException(String s) {
        super(s);
    }

    /**
     * Class constructor.
     *
     * @param s  String
     * @param ex Exception
     */
    public ExistingOpLockException(String s, Exception ex) {
        super(s, ex);
    }
}
