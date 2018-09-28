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

import org.filesys.server.filesys.cache.cluster.ClusterNode;

/**
 * File Data Update Message Class
 *
 * <p>Contains the details of a file data update start or completion.
 *
 * @author gkspencer
 */
public class DataUpdateMessage extends ClusterMessage {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Updated path and start/completion flag
    private String m_path;
    private boolean m_startUpdate;

    /**
     * Default constructor
     */
    public DataUpdateMessage() {
    }

    /**
     * Class constructor
     *
     * @param targetNode  String
     * @param fromNode    ClusterNode
     * @param path        String
     * @param startUpdate boolean
     */
    public DataUpdateMessage(String targetNode, ClusterNode fromNode, String path, boolean startUpdate) {
        super(targetNode, fromNode, ClusterMessageType.DataUpdate);
        m_path = path;
        m_startUpdate = startUpdate;
    }

    /**
     * Return the normalized path of the file/folder
     *
     * @return String
     */
    public final String getPath() {
        return m_path;
    }

    /**
     * Return the start of update flag
     *
     * @return boolean
     */
    public final boolean isStartOfUpdate() {
        return m_startUpdate;
    }

    /**
     * Return the file status message as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(super.toString());
        str.append(",path=");
        str.append(getPath());
        str.append(",startUpdate=");
        str.append(isStartOfUpdate());
        str.append("]");

        return str.toString();
    }

}
