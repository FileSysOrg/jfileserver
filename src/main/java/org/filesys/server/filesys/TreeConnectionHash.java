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

package org.filesys.server.filesys;

import java.util.Enumeration;
import java.util.Hashtable;

/**
 * Tree Connection Hash Class
 *
 * <p>Hashtable of TreeConnections for the available disk shared devices. TreeConnections are indexed using the
 * hash of the share name to allow mounts to be persistent across server restarts.
 *
 * @author gkspencer
 */
public class TreeConnectionHash {

    //	Share name hash to tree connection
    private Hashtable<Integer, TreeConnection> m_connections;

    /**
     * Class constructor
     */
    public TreeConnectionHash() {
        m_connections = new Hashtable<Integer, TreeConnection>();
    }

    /**
     * Return the number of tree connections in the hash table
     *
     * @return int
     */
    public final int numberOfEntries() {
        return m_connections.size();
    }

    /**
     * Add a connection to the list of available connections
     *
     * @param tree TreeConnection
     */
    public final void addConnection(TreeConnection tree) {
        m_connections.put(new Integer(tree.getSharedDevice().getName().hashCode()), tree);
    }

    /**
     * Delete a connection from the list
     *
     * @param shareName String
     * @return TreeConnection
     */
    public final TreeConnection deleteConnection(String shareName) {
        return m_connections.get(new Integer(shareName.hashCode()));
    }

    /**
     * Find a connection for the specified share name
     *
     * @param shareName String
     * @return TreeConnection
     */
    public final TreeConnection findConnection(String shareName) {

        //	Get the tree connection for the associated share name
        TreeConnection tree = m_connections.get(new Integer(shareName.hashCode()));

        //	Return the tree connection
        return tree;
    }

    /**
     * Find a connection for the specified share name hash code
     *
     * @param hashCode int
     * @return TreeConnection
     */
    public final TreeConnection findConnection(int hashCode) {

        //	Get the tree connection for the associated share name
        TreeConnection tree = (TreeConnection) m_connections.get(new Integer(hashCode));

        //	Return the tree connection
        return tree;
    }

    /**
     * Enumerate the connections
     *
     * @return Enumeration of tree connections
     */
    public final Enumeration<TreeConnection> enumerateConnections() {
        return m_connections.elements();
    }
}
