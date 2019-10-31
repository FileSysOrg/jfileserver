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

package org.filesys.smb.server;

import org.filesys.server.SrvSession;
import org.filesys.server.auth.ClientInfo;


/**
 * Virtual Circuit List Class
 *
 * <p>Contains a list of virtual circuits that belong to a session.
 *
 * @author gkspencer
 */
public interface VirtualCircuitList {

    /**
     * Create a virtual circuit object
     *
     * @param vcNum int
     * @param client ClientInfo
     * @return VirtualCircuit
     */
    public VirtualCircuit createVirtualCircuit(int vcNum, ClientInfo client);

    /**
     * Return the maximum virtual circuits allowed
     *
     * @return int
     */
    public int getMaximumVirtualCircuits();

    /**
     * Add a new virtual circuit to this session. Return the allocated id for the new circuit.
     *
     * @param vcircuit VirtualCircuit
     * @return int   Allocated id
     */
    public int addCircuit(VirtualCircuit vcircuit);

    /**
     * Return the virtual circuit details for the specified id.
     *
     * @param id int
     * @return VirtualCircuit
     */
    public VirtualCircuit findCircuit(int id);

    /**
     * Remove the specified virtual circuit from the active circuit list.
     *
     * @param id  int
     * @param sess SrvSession
     */
    public void removeCircuit(int id, SrvSession sess);

    /**
     * Return the active tree connection count
     *
     * @return int
     */
    public int getCircuitCount();

    /**
     * Clear the virtual circuit list
     *
     * @param sess SMBSrvSession
     */
    public void clearCircuitList(SMBSrvSession sess);
}
