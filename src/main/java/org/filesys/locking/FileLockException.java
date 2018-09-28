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

package org.filesys.locking;

import java.io.IOException;

/**
 * File Lock Exception Class
 *
 * @author gkspencer
 */
public class FileLockException extends IOException {

    private static final long serialVersionUID = 2722104928152336050L;

    /**
     * Class constructor.
     */
    public FileLockException() {
        super();
    }

    /**
     * Class constructor.
     *
     * @param s java.lang.String
     */
    public FileLockException(String s) {
        super(s);
    }
}
