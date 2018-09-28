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

import org.filesys.server.filesys.cache.cluster.ClusterInterface;
import org.filesys.server.filesys.cache.cluster.ClusterNode;

import com.hazelcast.core.Member;

/**
 * HazelCast Cluster Node Class
 *
 * @author gkspencer
 */
public class HazelCastClusterNode extends ClusterNode {

    /**
     * Class constructor
     *
     * @param name     String
     * @param priority int
     * @param cluster  ClusterInterface
     * @param addr     Member
     */
    public HazelCastClusterNode(String name, int priority, ClusterInterface cluster, Member addr) {
        super(name, priority, cluster, addr);

        // Check for the local node
        if (addr.localMember())
            setLocalNode(true);
    }

    /**
     * Return the cluster node state as a string
     *
     * @return String
     */
    public String getStateAsString() {
        return "Running";
    }

    /**
     * Return the HazelCast node details
     *
     * @return Member
     */
    public final Member getHazelCastAddress() {
        return (Member) getAddress();
    }

    /**
     * Check if this cluster node matches the specified name.
     * <p>
     * Need to provide additional checking as names may be in the format 'name/ip-addr:port' or
     * '/ip-addr:port'
     *
     * @param name String
     */
    public boolean nameMatches(String name) {

        // Get the '/ip-addr:port' part from this nodes name
        String thisName = getName();
        int idx = thisName.indexOf('/');

        if (idx > 0)
            thisName = thisName.substring(idx);

        // Make sure the name to check is also in the '/ip-addr:port' format
        String chkName = name;
        idx = chkName.indexOf('/');

        if (idx > 0)
            chkName = chkName.substring(idx);

        // Check if the addresses match
        return thisName.equalsIgnoreCase(chkName);
    }
}
