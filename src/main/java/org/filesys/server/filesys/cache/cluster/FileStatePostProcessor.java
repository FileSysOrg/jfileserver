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

package org.filesys.server.filesys.cache.cluster;

import org.filesys.server.RequestPostProcessor;

/**
 * File State Post Processor Abstract Class
 *
 * @author gkspencer
 */
public abstract class FileStatePostProcessor extends RequestPostProcessor {

    // File state and state cache
    private ClusterFileStateCache m_stateCache;
    private ClusterFileState m_state;

    // Post processor debug enable
    private static boolean m_debug = true;

    /**
     * Class constructor
     *
     * @param stateCache ClusterFileStateCache
     * @param state      ClusterFileState
     */
    public FileStatePostProcessor(ClusterFileStateCache stateCache, ClusterFileState state) {
        m_stateCache = stateCache;
        m_state = state;
    }

    /**
     * Return the file state cache
     *
     * @return ClusterFileStateCache
     */
    protected final ClusterFileStateCache getStateCache() {
        return m_stateCache;
    }

    /**
     * Return the file state
     *
     * @return ClusterFileState
     */
    protected final ClusterFileState getState() {
        return m_state;
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public static final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Enable/disable debug output
     *
     * @param dbg boolean
     */
    public static final void setDebug(boolean dbg) {
        m_debug = dbg;
    }
}
