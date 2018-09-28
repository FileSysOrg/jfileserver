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
 * Path Not Found Exception Class
 *
 * <p>Indicates that the upper part of a path does not exist, as opposed to the file/folder at the
 * end of the path.
 *
 * @author gkspencer
 */
public class PathNotFoundException extends IOException {

    private static final long serialVersionUID = -537450129331293825L;

    /**
     * Class constructor.
     */
    public PathNotFoundException() {
        super();
    }

    /**
     * Class constructor.
     *
     * @param s java.lang.String
     */
    public PathNotFoundException(String s) {
        super(s);
    }
}
