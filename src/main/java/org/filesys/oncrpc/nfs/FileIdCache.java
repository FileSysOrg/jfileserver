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

package org.filesys.oncrpc.nfs;

import java.util.Hashtable;

/**
 * File Id Cache Class
 *
 * <p>Converts a file/directory id to a share relative path.
 *
 * @author gkspencer
 */
public class FileIdCache {

    //	File id to path cache
    private Hashtable<Long, String> m_idCache;

    /**
     * Default constructor
     */
    public FileIdCache() {
        m_idCache = new Hashtable<Long, String>();
    }

    /**
     * Add an entry to the cache
     *
     * @param fid  long
     * @param path String
     */
    public final void addPath(long fid, String path) {
        m_idCache.put(new Long(fid), path);
    }

    /**
     * Convert a file id to a path
     *
     * @param fid long
     * @return String
     */
    public final String findPath(long fid) {
        return m_idCache.get(new Long(fid));
    }

    /**
     * Delete an entry from the cache
     *
     * @param fid long
     */
    public final void deletePath(long fid) {
        m_idCache.remove(new Long(fid));
    }
}
