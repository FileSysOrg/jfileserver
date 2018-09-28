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

import java.io.Serializable;

import org.filesys.locking.FileLock;

/**
 * Cluster File Lock
 *
 * @author gkspencer
 */
public class ClusterFileLock extends FileLock implements Serializable {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Lock owner node address/unique name
    private String m_ownerNode;

    /**
     * Default constructor
     */
    public ClusterFileLock() {
        super(0, 0, -1);
    }

    /**
     * Class constructor
     *
     * @param ownerNode ClusterNode
     * @param offset    long
     * @param len       long
     * @param pid       int
     */
    public ClusterFileLock(ClusterNode ownerNode, long offset, long len, int pid) {
        super(offset, len, pid);

        m_ownerNode = ownerNode.getName();

        // Use the top half of the process id to add the node id, to make the PID unique
        // across the cluster
        pid += ownerNode.getPriority() << 16;
        setProcessId(pid);
    }

    /**
     * Return the owner node address
     *
     * @return String
     */
    public final String getOwnerNode() {
        return m_ownerNode;
    }

    /**
     * Return the lock details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Owner=");
        str.append(getOwnerNode());
        str.append(",PID=");
        str.append(getProcessId());
        str.append("/");
        str.append(getProcessId() & 0x0000FFFF);
        str.append(",Offset=");
        str.append(getOffset());
        str.append(",Len=");
        str.append(getLength());
        str.append("]");

        return str.toString();
    }
}
