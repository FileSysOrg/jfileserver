/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.auth.ClientInfo;
import org.filesys.server.core.DeviceInterface;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.filesys.*;

/**
 * Virtual Circuit Class
 *
 * <p>Represents an authenticated circuit on an SMB session. There may be multiple virtual circuits opened
 * on a single session/socket connection.
 *
 * @author gkspencer
 */
public class VirtualCircuit {

    //  Default and maximum number of connection slots
    public static final int DefaultConnections = 4;
    public static final int MaxConnections = 16;

    //  Tree ids are 16bit values
    private static final int TreeIdMask = 0x0000FFFF;

    //  Default and maximum number of search slots
    private static final int DefaultSearches = 8;
    private static final int MaxSearches = 256;

    // Invalid id value
    public static final int InvalidID = -1;

    // Search slot marker object, indicates a search slot is in use before the actual search context
    // is stored in the slot
    public static final SearchContextAdapter SearchSlotMarker = new SearchContextAdapter();

    // Virtual circuit id value
    //
    // Allocated by the server and sent by the client to identify the virtual circuit
    private int m_vcId = -1;

    // Virtual circuit number
    private int m_vcNum;

    // Client information for this virtual circuit
    private ClientInfo m_clientInfo;

    // Active tree connections
    private Map<Integer, TreeConnection> m_connections;
    private int m_treeId = 1;

    // List of active searches
    private SearchMap m_search;

    //  Active SMB transaction details
    private SrvTransactBuffer m_transact;

    // Flag to indicate if the virtual circuit is logged on/off
    private boolean m_loggedOn;

    /**
     * Class constructor
     *
     * @param vcNum int
     * @param cInfo ClientInfo
     */
    public VirtualCircuit(int vcNum, ClientInfo cInfo) {
        m_vcNum = vcNum;
        m_clientInfo = cInfo;

        m_loggedOn = true;
    }

    /**
     * Return the virtual circuit id
     *
     * @return int
     */
    public final int getId() {
        return m_vcId;
    }

    /**
     * Return the virtual circuit number
     *
     * @return int
     */
    public final int getVCNumber() {
        return m_vcNum;
    }

    /**
     * Return the client information
     *
     * @return ClientInfo
     */
    public final ClientInfo getClientInformation() {
        return m_clientInfo;
    }

    /**
     * Add a new connection to this virtual circuit. Return the allocated tree id for the new
     * connection.
     *
     * @param shrDev SharedDevice
     * @return int   Allocated tree id (connection id).
     * @exception TooManyConnectionsException No more connections available
     */
    public synchronized int addConnection(SharedDevice shrDev)
            throws TooManyConnectionsException {

        //  Check if the connection array has been allocated
        if (m_connections == null)
            m_connections = new HashMap<Integer, TreeConnection>(DefaultConnections);

        //  Allocate an id for the tree connection
        int treeId = 0;

        //  Check if the tree connection table is full
        if (m_connections.size() == MaxConnections)
            throw new TooManyConnectionsException();

        //  Find a free slot in the connection array
        treeId = (m_treeId++ & TreeIdMask);

        while (m_connections.containsKey(treeId)) {

            //  Try another tree id for the new connection
            treeId = (m_treeId++ & TreeIdMask);
        }

        //  Store the new tree connection
        m_connections.put(treeId, new TreeConnection(shrDev, treeId));

        //  Return the allocated tree id
        return treeId;
    }

    /**
     * Return the tree connection details for the specified tree id.
     *
     * @param treeId int
     * @return TreeConnection
     */
    public synchronized final TreeConnection findConnection(int treeId) {

        //  Check if the tree id and connection array are valid
        if (m_connections == null)
            return null;

        //  Get the required tree connection details
        return m_connections.get(treeId);
    }

    /**
     * Remove the specified tree connection from the active connection list.
     *
     * @param treeId int
     * @param sess   SrvSession
     */
    public synchronized void removeConnection(int treeId, SrvSession sess) {

        //  Check if the tree id is valid
        if (m_connections == null)
            return;

        TreeConnection tree = m_connections.get(treeId);

        //  Close the connection, release resources
        if (tree != null) {

            //  Close the connection
            tree.closeConnection(sess);

            //  Remove the connection from the connection list
            m_connections.remove(treeId);
        }
    }

    /**
     * Remove all tree connections from the active connection list
     *
     * @param sess SrvSession
     */
    public synchronized void removeAllConnections(SrvSession sess) {

        //  Check if the tree id is valid
        if (m_connections == null)
            return;

        // Remove the tree connections
        Iterator<Integer> iterId = m_connections.keySet().iterator();

        while ( iterId.hasNext()) {

            // Get the current tree connection
            Integer treeId = iterId.next();
            TreeConnection tree = m_connections.get( treeId);

            //  Close the connection, release resources
            if (tree != null) {

                //  Close the connection
                tree.closeConnection(sess);

                //  Remove the connection from the connection list
                iterId.remove();
            }
        }
    }

    /**
     * Return the active tree connection count
     *
     * @return int
     */
    public synchronized final int getConnectionCount() {
        return m_connections != null ? m_connections.size() : 0;
    }

    /**
     * Allocate a slot in the active searches list for a new search.
     *
     * @return int  Search slot id, or -1 if there are no more search slots available.
     * @exception TooManySearchesException No more search slots available
     */
    public synchronized final int allocateSearchSlot()
        throws TooManySearchesException {

        if ( m_search == null)
            m_search = new SequentialSearchMap();

        return m_search.allocateSearchSlot();
    }

    /**
     * Allocate a slot in the active searches list for a new search.
     *
     * @param searchId int
     * @return boolean  true if the searchId is unique and a slot has been allocated, false if there are no more
     *                  search slots or the searchId is already in use
     * @exception TooManySearchesException No more search slots available
     */
    public synchronized final boolean allocateSearchSlotWithId(int searchId)
        throws TooManySearchesException {

        if ( m_search == null)
            m_search = new SequentialSearchMap();

        return m_search.allocateSearchSlotWithId( searchId);
    }

    /**
     * Deallocate the specified search context/slot.
     *
     * @param ctxId int
     */
    public synchronized final void deallocateSearchSlot(int ctxId) {
        if ( m_search != null)
            m_search.deallocateSearchSlot( ctxId);
    }

    /**
     * Return the search context for the specified search id.
     *
     * @param srchId int
     * @return SearchContext
     */
    public synchronized final SearchContext getSearchContext(int srchId) {
        if ( m_search == null)
            return null;
        return m_search.findSearchContext( srchId);
    }

    /**
     * Store the seach context in the specified slot.
     *
     * @param slot Slot to store the search context.
     * @param srch SearchContext
     */
    public synchronized final void setSearchContext(int slot, SearchContext srch) {
        if ( m_search != null)
            m_search.setSearchContext( slot, srch);
    }

    /**
     * Return the number of active tree searches.
     *
     * @return int
     */
    protected synchronized final int getSearchCount() {
        if ( m_search == null)
            return 0;
        return m_search.numberOfSearches();
    }

    /**
     * Check if there is an active transaction
     *
     * @return boolean
     */
    public synchronized final boolean hasTransaction() {
        return m_transact != null ? true : false;
    }

    /**
     * Return the active transaction buffer
     *
     * @return TransactBuffer
     */
    public synchronized final SrvTransactBuffer getTransaction() {
        return m_transact;
    }

    /**
     * Set the active transaction buffer
     *
     * @param buf TransactBuffer
     */
    public synchronized final void setTransaction(SrvTransactBuffer buf) {
        m_transact = buf;
    }

    /**
     * Set the id for the circuit
     *
     * @param id int
     */
    public final void setId(int id) {
        m_vcId = id;
    }

    /**
     * Close the virtual circuit, close active tree connections
     *
     * @param sess SrvSession
     */
    public synchronized final void closeCircuit(SrvSession sess) {

        //  Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_STATE))
            sess.debugPrintln("Cleanup vc=" + getVCNumber() + ", id=" + getId() + ", searches=" + getSearchCount() + ", treeConns=" + getConnectionCount());

        //  Check if there are any active searches
        if (m_search != null && m_search.numberOfSearches() > 0) {

            //  Close all active searches
            synchronized (this) {
                m_search.closeAllSearches();
            }
        }

        //  Check if there are open tree connections
        if (m_connections != null) {

            for (TreeConnection tree : m_connections.values()) {

                //  Get the current tree connection
                DeviceInterface devIface = tree.getInterface();

                //  Check if there are open files on the share
                tree.closeConnection(sess);

                //  Inform the driver that the connection has been closed
                if (devIface != null)
                    devIface.treeClosed(sess, tree);
            }

            //  Clear the tree connection list
            m_connections.clear();
        }
    }

    /**
     * Check if the virtual circuit has a valid user logged on
     *
     * @return boolean
     */
    public synchronized final boolean isLoggedOn() {
        return m_loggedOn;
    }

    /**
     * Set the logged on status for the virtual circuit
     *
     * @param loggedOn boolean
     */
    public synchronized final void setLoggedOn(boolean loggedOn) {
        m_loggedOn = loggedOn;
    }

    /**
     * Return the virtual circuit details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(getVCNumber());
        str.append(":");
        str.append(getId());
        str.append(",");
        str.append(getClientInformation());
        str.append(",Tree=");
        str.append(getConnectionCount());
        str.append(",Searches=");
        str.append(getSearchCount());
        str.append("]");

        return str.toString();
    }
}
