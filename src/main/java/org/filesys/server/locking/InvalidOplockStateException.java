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

package org.filesys.server.locking;

/**
 * Invalid Oplock State Exception Class
 *
 * <p>Indicates the oplock state is not as expected when setting oplock details or changing the oplock type</p>
 *
 * @author gkspencer
 */
public class InvalidOplockStateException extends Exception {

    /**
     * Class constructor.
     */
    public InvalidOplockStateException() {
        super();
    }

    /**
     * Class constructor.
     *
     * @param msg String
     */
    public InvalidOplockStateException(String msg) {
        super(msg);
    }
}
