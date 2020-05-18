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
package org.filesys.server.filesys.loader;

import org.filesys.server.filesys.db.MemCachedNetworkFile;

/**
 * In-Memory File Loader Interface
 *
 * <p>The InMemoryLoader interface adds methods required by the caching of file data in-memory</p>
 *
 * @author gkspencer
 */
public interface InMemoryLoader {

    /**
     * Convert an in-memory cached file to a streamed file
     *
     * @param netFile MemCachedNetworkFile
     * @return boolean
     */
    public boolean convertInMemoryToStreamedFile(MemCachedNetworkFile netFile);
}
