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

package org.filesys.debug.cluster;

import org.filesys.server.filesys.cache.hazelcast.ClusterMessage;

/**
 * Debug Cluster Message Class
 *
 * @author gkspencer
 */
public class DebugClusterMessage extends ClusterMessage {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Debug message
    private String m_debugStr;

    /**
     * Class constructor
     *
     * @param fromNode String
     * @param debugStr String
     */
    public DebugClusterMessage(String fromNode, String debugStr) {
        super(ClusterMessage.AllNodes, fromNode, 0);

        m_debugStr = debugStr;
    }

    /**
     * Return the debug string
     *
     * @return String
     */
    public final String getDebugString() {
        return m_debugStr;
    }
}
