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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

/**
 * Cluster Node List Class
 *
 * @author gkspencer
 */
public class ClusterNodeList {

    // List of nodes within a cluster
    private Hashtable<String, ClusterNode> m_nodes;

    /**
     * Comparator for ordering using the node priority
     */
    static class ClusterNodePriorityComparator implements Comparator<ClusterNode> {

        /**
         * Compare cluster nodes by priority
         *
         * @param node1 ClusterNode
         * @param node2 ClusterNode
         * @return int
         */
        public final int compare(ClusterNode node1, ClusterNode node2) {
            if (node1.getPriority() < node2.getPriority())
                return -1;
            else if (node1.getPriority() > node2.getPriority())
                return 1;
            return 0;
        }
    }

    /**
     * Default constructor
     */
    public ClusterNodeList() {
        m_nodes = new Hashtable<String, ClusterNode>();
    }

    /**
     * Class constructor
     *
     * @param initCapac int
     */
    public ClusterNodeList(int initCapac) {
        m_nodes = new Hashtable<String, ClusterNode>(initCapac);
    }

    /**
     * Return the count of nodes in the set
     *
     * @return int
     */
    public final int numberOfNodes() {
        return m_nodes.size();
    }

    /**
     * Find a node
     *
     * @param name String
     * @return ClusterNode
     */
    public final ClusterNode findNode(String name) {

        // Do a simple lookup
        ClusterNode clNode = m_nodes.get(name);
        if (clNode != null)
            return clNode;

        // Strip any node name from the node address string
        String nodeName = name;
        int idx = nodeName.indexOf("/");

        if (idx != -1)
            nodeName = nodeName.substring(idx);

        // If the name does not include the host name then search for a match using the host/port
        // part of the address
        if (nodeName.startsWith("/")) {

            // Search for a node with the required address
            Enumeration<String> enumNodes = enumerateNodes();

            while (enumNodes.hasMoreElements()) {

                // Get the current node name, check if the address part matches
                String curNodeName = enumNodes.nextElement();
                if (curNodeName.endsWith(nodeName)) {

                    // Get the associated node details
                    return m_nodes.get(curNodeName);
                }
            }
        }

        // Node not found
        return null;
    }

    /**
     * Add a node to the set
     *
     * @param node ClusterNode
     */
    public final void addNode(ClusterNode node) {
        m_nodes.put(node.getName(), node);
    }

    /**
     * Remove a node from the list
     *
     * @param name String
     * @return ClusterNode
     */
    public final ClusterNode removeNode(String name) {
        return m_nodes.remove(name);
    }

    /**
     * Return an enumeration for the node names
     *
     * @return Enumeration of node names
     */
    public final Enumeration<String> enumerateNodes() {
        return m_nodes.keys();
    }

    /**
     * Return the list of nodes ordered by priority
     *
     * @return List of cluster nodes
     */
    public List<ClusterNode> asPriorityOrderedList() {

        // Enumerate the nodes and create a list
        ArrayList<ClusterNode> priList = new ArrayList<ClusterNode>(m_nodes.size());

        Enumeration<String> keys = m_nodes.keys();

        while (keys.hasMoreElements()) {

            // Add the current node to the list
            priList.add(m_nodes.get(keys.nextElement()));
        }

        // Sort the list
        Collections.sort(priList, new ClusterNodePriorityComparator());

        // Return the node list
        return priList;
    }

    /**
     * Return the cluster node set as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder(256);

        str.append("[");

        int nodeCnt = numberOfNodes();
        str.append(nodeCnt);
        str.append(":");

        if (nodeCnt > 0) {

            // Dump up to the first ten node names

            if (nodeCnt > 10)
                nodeCnt = 10;

            Enumeration<String> nodeNames = enumerateNodes();
            while (nodeNames.hasMoreElements() && nodeCnt-- > 0) {
                ClusterNode curNode = m_nodes.get(nodeNames.nextElement());
                str.append(curNode);
                str.append(",");
            }

            str.setLength(str.length() - 1);
            if (numberOfNodes() > 10)
                str.append("...");
        }

        str.append("]");

        return str.toString();
    }
}
