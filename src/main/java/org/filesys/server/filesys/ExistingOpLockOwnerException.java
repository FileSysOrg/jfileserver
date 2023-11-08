/*
 * Copyright (C) 2023 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */
package org.filesys.server.filesys;

import java.io.IOException;

/**
 * Existing Oplock Owner Exception Class
 *
 * @author gkspencer
 */
public class ExistingOpLockOwnerException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     */
    public ExistingOpLockOwnerException() {
        super();
    }

    /**
     * Class constructor.
     *
     * @param s String
     */
    public ExistingOpLockOwnerException(String s) {
        super(s);
    }

    /**
     * Class constructor.
     *
     * @param s  String
     * @param ex Exception
     */
    public ExistingOpLockOwnerException(String s, Exception ex) {
        super(s, ex);
    }
}
