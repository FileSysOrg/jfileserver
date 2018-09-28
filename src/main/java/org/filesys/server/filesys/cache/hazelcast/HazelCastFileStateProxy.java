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

package org.filesys.server.filesys.cache.hazelcast;

import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateProxy;

/**
 * HazelCast File State Proxy Class
 *
 * <p>Store the key path and state cache details so the file state can be retrieved on demand, to avoid
 * saving a reference to a copy of the file state that will not be updated.
 *
 * @author gkspencer
 */
public class HazelCastFileStateProxy implements FileStateProxy {

    // Key path
    private String m_keyPath;

    // State cache
    private HazelCastClusterFileStateCache m_stateCache;

    /**
     * Class constructor
     *
     * @param keyPath    String
     * @param stateCache HazelCastClusterFileStateCache
     */
    public HazelCastFileStateProxy(String keyPath, HazelCastClusterFileStateCache stateCache) {
        m_keyPath = keyPath;
        m_stateCache = stateCache;
    }

    /**
     * Return the file state
     *
     * @return FileState
     */
    public FileState getFileState() {

        // Retrieve the current file state from the cache
        return m_stateCache.findFileState(m_keyPath);
    }
}
