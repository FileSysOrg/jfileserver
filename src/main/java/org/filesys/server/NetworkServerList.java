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

package org.filesys.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Network Server List Class
 *
 * @author gkspencer
 */
public class NetworkServerList implements Iterable<NetworkServer> {

    //	List of network servers
    private List<NetworkServer> m_servers;

    /**
     * Class constructor
     */
    public NetworkServerList() {
        m_servers = new ArrayList<NetworkServer>();
    }

    /**
     * Iterator over the servers in the list
     */
    @Override
    public final Iterator<NetworkServer> iterator() {
        return m_servers.iterator();
    }

    /**
     * Return the number of servers in the list
     *
     * @return int
     */
    public final int numberOfServers() {
        return m_servers.size();
    }

    /**
     * Add a server to the list
     *
     * @param server NetworkServer
     */
    public final void addServer(NetworkServer server) {
        m_servers.add(server);
    }

    /**
     * Return the specified server
     *
     * @param idx int
     * @return NetworkServer
     */
    public final NetworkServer getServer(int idx) {

        //	Range check the index
        if (idx < 0 || idx >= m_servers.size())
            return null;
        return m_servers.get(idx);
    }

    /**
     * Find a server in the list by name
     *
     * @param name String
     * @return NetworkServer
     */
    public final NetworkServer findServer(String name) {

        //	Search for the required server
        for (int i = 0; i < m_servers.size(); i++) {

            //	Get the current server from the list
            NetworkServer server = m_servers.get(i);

            if (server.getProtocolName().equals(name))
                return server;
        }

        //	Server not found
        return null;
    }

    /**
     * Remove the server at the specified position within the list
     *
     * @param idx int
     * @return NetworkServer
     */
    public final NetworkServer removeServer(int idx) {

        //	Range check the index
        if (idx < 0 || idx >= m_servers.size())
            return null;

        //	Remove the server from the list
        return m_servers.remove(idx);
    }

    /**
     * Remove the server with the specified protocol name
     *
     * @param proto String
     * @return NetworkServer
     */
    public final NetworkServer removeServer(String proto) {

        //	Search for the required server
        for (int i = 0; i < m_servers.size(); i++) {

            //	Get the current server from the list
            NetworkServer server = m_servers.get(i);

            if (server.getProtocolName().equals(proto)) {
                m_servers.remove(i);
                return server;
            }
        }

        //	Server not found
        return null;
    }

    /**
     * Remove all servers from the list
     */
    public final void removeAll() {
        m_servers.clear();
    }
}
