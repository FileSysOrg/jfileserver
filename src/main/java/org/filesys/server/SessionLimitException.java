/*
 * Copyright (C) 2020 GK Spencer
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
package org.filesys.server;

/**
 * Session Limit Exception Class
 *
 * <p>Server has reached the maximum allowed sessions</p>
 *
 * @author gkspencer
 */
public class SessionLimitException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Class constructor
     *
     * @param str String
     */
    public SessionLimitException(String str) {
        super(str);
    }
}
