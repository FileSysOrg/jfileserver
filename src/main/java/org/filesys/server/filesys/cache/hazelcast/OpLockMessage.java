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

import org.filesys.server.locking.OplockOwner;

/**
 * OpLock Message Class
 *
 * <p>Contains the details of an oplock break request or notification of an oplock break completing.
 *
 * @author gkspencer
 */
public class OpLockMessage extends ClusterMessage {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Oplock path
    private String m_path;

    // Oplock owner to be added/removed
    private OplockOwner m_owner;

    /**
     * Default constructor
     */
    public OpLockMessage() {
    }

    /**
     * Class constructor
     *
     * @param targetNode String
     * @param msgType    ClusterMessageType
     * @param path       String
     */
    public OpLockMessage(String targetNode, ClusterMessageType msgType, String path) {
        super(targetNode, msgType);
        m_path = path;
    }

    /**
     * Class constructor
     *
     * @param targetNode String
     * @param msgType    ClusterMessageType
     * @param path       String
     * @param owner      OplockOwner
     */
    public OpLockMessage(String targetNode, ClusterMessageType msgType, String path, OplockOwner owner) {
        super(targetNode, msgType);
        m_path = path;
        m_owner = owner;
    }

    /**
     * Return the normalized path of the oplocked file/folder
     *
     * @return String
     */
    public final String getPath() {
        return m_path;
    }

    /**
     * Check if the oplock owner has been specified
     *
     * @return boolean
     */
    public final boolean hasOwner() { return m_owner != null; }

    /**
     * Return the oplock owner details
     *
     * @return OplockOwner
     */
    public final OplockOwner getOwner() { return m_owner; }

    /**
     * Return the oplock message as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(super.toString());
        str.append(",path=");
        str.append(getPath());
        str.append(",owner=");
        str.append( getOwner());
        str.append("]");

        return str.toString();
    }
}
