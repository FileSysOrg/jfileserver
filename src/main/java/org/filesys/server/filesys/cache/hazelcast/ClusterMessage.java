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

import java.io.Serializable;

import org.filesys.server.filesys.cache.cluster.ClusterNode;

/**
 * Cluster Message Class
 *
 * <p>Base object for messages passed between cluster nodes using the message topic.
 *
 * @author gkspencer
 */
public class ClusterMessage implements Serializable {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Target to indicate message is for all nodes
    public static final String AllNodes = "*";

    // Target node name, or '*' for all nodes
    private String m_targetNode;

    // Node the message was sent from
    private String m_fromNode;

    // Message type
    private int m_msgType;

    /**
     * Default constructor
     */
    ClusterMessage() {
    }

    /**
     * Class constructor
     *
     * @param targetNode String
     * @param msgType    int
     */
    public ClusterMessage(String targetNode, int msgType) {
        m_targetNode = targetNode;
        m_msgType = msgType;
    }

    /**
     * Class constructor
     *
     * @param targetNode String
     * @param fromNode   String
     * @param msgType    int
     */
    public ClusterMessage(String targetNode, String fromNode, int msgType) {
        m_targetNode = targetNode;
        m_fromNode = fromNode;
        m_msgType = msgType;
    }

    /**
     * Class constructor
     *
     * @param targetNode String
     * @param fromNode   ClusterNode
     * @param msgType    int
     */
    public ClusterMessage(String targetNode, ClusterNode fromNode, int msgType) {
        m_targetNode = targetNode;
        if (fromNode != null)
            m_fromNode = fromNode.getName();
        m_msgType = msgType;
    }

    /**
     * Return the target node name
     *
     * @return String
     */
    public final String getTargetNode() {
        return m_targetNode;
    }

    /**
     * Check if the target is all nodes
     *
     * @return boolean
     */
    public final boolean isAllNodes() {
        return m_targetNode.equals(AllNodes) ? true : false;
    }

    /**
     * Return the message type
     *
     * @return int
     */
    public final int isType() {
        return m_msgType;
    }

    /**
     * Check if the from node is valid
     *
     * @return boolean
     */
    public final boolean hasFromNode() {
        return (m_fromNode != null && m_fromNode.length() > 0) ? true : false;
    }

    /**
     * Return the from node
     *
     * @return String
     */
    public final String getFromNode() {
        return m_fromNode;
    }

    /**
     * Check if the message was sent by the local node
     *
     * @param localNode ClusterNode
     * @return boolean
     */
    public final boolean isFromLocalNode(ClusterNode localNode) {
        if (m_fromNode != null && m_fromNode.equals(localNode.getName()))
            return true;
        return false;
    }

    /**
     * Return the cluster message as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Target=");
        if (isAllNodes())
            str.append("All");
        else
            str.append(getTargetNode());
        if (hasFromNode()) {
            str.append(",from=");
            str.append(getFromNode());
        }
        str.append(",type=");
        str.append(ClusterMessageType.getTypeAsString(isType()));
        str.append("]");

        return str.toString();
    }
}
