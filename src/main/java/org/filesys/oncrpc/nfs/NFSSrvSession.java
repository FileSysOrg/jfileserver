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

package org.filesys.oncrpc.nfs;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.EnumSet;
import java.util.Enumeration;

import org.filesys.debug.Debug;
import org.filesys.oncrpc.Rpc;
import org.filesys.oncrpc.RpcPacketHandler;
import org.filesys.server.NetworkServer;
import org.filesys.server.SrvSession;
import org.filesys.server.auth.ClientInfo;
import org.filesys.server.core.DeviceInterface;
import org.filesys.server.filesys.SearchContext;
import org.filesys.server.filesys.TreeConnection;
import org.filesys.server.filesys.TreeConnectionHash;

/**
 * NFS Server Session Class
 *
 * @author gkspencer
 */
public class NFSSrvSession extends SrvSession {

    //	Debug flags
    public static final int DBG_RXDATA      = 0x00000001; //	Received data
    public static final int DBG_TXDATA      = 0x00000002; //	Transmit data
    public static final int DBG_DUMPDATA    = 0x00000004; //	Dump data packets
    public static final int DBG_SEARCH      = 0x00000008; //	File/directory search
    public static final int DBG_INFO        = 0x00000010; //	Information requests
    public static final int DBG_FILE        = 0x00000020; //	File open/close/info
    public static final int DBG_FILEIO      = 0x00000040; // 	File read/write
    public static final int DBG_ERROR       = 0x00000080; //	Errors
    public static final int DBG_TIMING      = 0x00000100; //	Time packet processing
    public static final int DBG_DIRECTORY   = 0x00000200; //	Directory commands
    public static final int DBG_SESSION     = 0x00000400; //	Session creation/deletion
    public static final int DBG_SOCKET      = 0x00000800; //    Socket handling
    public static final int DBG_THREADPOOL  = 0x00001000; //    Thread pool

    //	Default and maximum number of search slots
    private static final int DefaultSearches    = 32;
    private static final int MaxSearches        = 256;

    //	Remote address and port
    private InetAddress m_remAddr;
    private int m_remPort;

    //	Session type (TCP or UDP)
    private Rpc.ProtocolId m_type;

    //	Authentication identifier
    //
    //	Identifies this session uniquely within the authentication type being used by the client
    private Object m_authIdentifier;

    //	Active tree connections
    private TreeConnectionHash m_connections;

    //	Cache of currently open files
    private NetworkFileCache m_fileCache;

    //	Last time the session was accessed. Used to determine when to expire UDP sessions.
    private long m_lastAccess;

    //	Active search list for this session
    private SearchContext[] m_search;
    private int m_searchCount;

    // NFS client information
    private ClientInfo m_nfsClientInfo;

    // RPC processor for this session
    private RpcSessionProcessor m_rpcProcessor;

    // RPC packet handler for this session
    private RpcPacketHandler m_pktHandler;

    /**
     * Create a new NFS session
     *
     * @param pktHandler RpcPacketHandler
     * @param nfsServer NFSServer
     * @param sessId int
     * @param protocolType Rpc.ProtocolId
     * @param remAddr SocketAddress
     * @return NFSSrvSession
     */
    public static NFSSrvSession createSession(RpcPacketHandler pktHandler, NFSServer nfsServer, int sessId, Rpc.ProtocolId protocolType,
                                              SocketAddress remAddr) {

        // Make sure the socket address is the expected type
        if ( remAddr instanceof InetSocketAddress == false)
            throw new RuntimeException( "NFS session, socket address is not an InetSocketAddress");

        // Create a new NFS session
        return new NFSSrvSession( sessId, nfsServer, pktHandler, (InetSocketAddress) remAddr, protocolType);
    }

    /**
     * Class constructor
     *
     * @param sessId int
     * @param srv  NFSServer
     * @param pktHandler RpcPacketHandler
     * @param addr InetSocketAddress
     * @param type Rpc.ProtocolId
     */
    public NFSSrvSession(int sessId, NFSServer srv, RpcPacketHandler pktHandler, InetSocketAddress addr, Rpc.ProtocolId type) {
        super(sessId, srv, "NFS", null);

        //	Save the remote address/port and type
        if ( addr != null) {

            // Set the remote address and port
            m_remAddr = addr.getAddress();
            m_remPort = addr.getPort();

            // Set the remote host name
            setRemoteName( addr.getHostName());
        }
        m_type = type;

        // Save the associated  packet handler
        m_pktHandler = pktHandler;

        //	Create a unique id for the session from the remote address, port and type
        StringBuilder str = new StringBuilder();

        str.append(type == Rpc.ProtocolId.TCP ? "T" : "U");
        str.append(m_remAddr.getHostAddress());
        str.append(":");
        str.append(m_remPort);

        setUniqueId(str.toString());

        //	Initialize the last access date/time
        setLastAccess(System.currentTimeMillis());
    }

    /**
     * Class constructor
     *
     * @param srv  NetworkServer
     * @param addr InetAddress
     * @param port int
     * @param type Rpc.ProtocolId
     */
    public NFSSrvSession(NetworkServer srv, InetAddress addr, int port, Rpc.ProtocolId type) {
        super(-1, srv, "NFS", null);

        //	Save the remote address/port and type
        m_remAddr = addr;
        m_remPort = port;
        m_type = type;

        //	Create a unique id for the session from the remote address, port and type
        StringBuilder str = new StringBuilder();

        str.append(type == Rpc.ProtocolId.TCP ? "T" : "U");
        str.append(m_remAddr.getHostAddress());
        str.append(":");
        str.append(m_remPort);

        setUniqueId(str.toString());

        //	Set the remote name
        setRemoteName(m_remAddr.getHostAddress());

        //	Initialize the last access date/time
        setLastAccess(System.currentTimeMillis());
    }

    /**
     * Return the session type
     *
     * @return Rpc.ProtocolId
     */
    public final Rpc.ProtocolId isType() {
        return m_type;
    }

    /**
     * Return the open file cache
     *
     * @return NetworkFileCache
     */
    public final NetworkFileCache getFileCache() {

        // Check if the file cache has been created

        if (m_fileCache == null) {
            m_fileCache = new NetworkFileCache(getUniqueId());

            // Copy settings to the file cache
            NFSConfigSection config = getNFSServer().getNFSConfiguration();

            m_fileCache.setDebug(hasDebug(NFSSrvSession.DBG_FILE));

            if (config.getNFSFileCacheIOTimer() > 0)
                m_fileCache.setIOTimer(config.getNFSFileCacheIOTimer());
            if (config.getNFSFileCacheCloseTimer() > 0)
                m_fileCache.setCloseTimer(config.getNFSFileCacheCloseTimer());

            m_fileCache.setRpcAuthenticator(config.getRpcAuthenticator());
        }

        // Return the file cache
        return m_fileCache;
    }

    /**
     * Determine if the session has an associated packet handler
     *
     * @return boolean
     */
    public final boolean hasPacketHandler() {
        return m_pktHandler != null ? true : false;
    }

    /**
     * Return the associated packet handler
     *
     * @return RpcPacketHandler
     */
    public final RpcPacketHandler getPacketHandler() {
        return m_pktHandler;
    }

    /**
     * Determine if the session has an authentication identifier
     *
     * @return boolean
     */
    public final boolean hasAuthIdentifier() {
        return m_authIdentifier != null ? true : false;
    }

    /**
     * Return the authentication identifier
     *
     * @return Object
     */
    public final Object getAuthIdentifier() {
        return m_authIdentifier;
    }

    /**
     * Return the client network address
     *
     * @return InetAddress
     */
    public InetAddress getRemoteAddress() {
        return m_remAddr;
    }

    /**
     * Return the remote port
     *
     * @return int
     */
    public final int getRemotePort() {
        return m_remPort;
    }

    /**
     * Get the last access date/time for the session
     *
     * @return long
     */
    public final long getLastAccess() {
        return m_lastAccess;
    }

    /**
     * return the NFS client information
     *
     * @return ClientInfo
     */
    public final ClientInfo getNFSClientInformation() {
        return m_nfsClientInfo;
    }

    /**
     * Set the NFS client information
     *
     * @param cInfo ClientInfo
     */
    public final void setNFSClientInformation(ClientInfo cInfo) {
        m_nfsClientInfo = cInfo;
    }

    /**
     * Check if the session has an associated RPC processor
     *
     * @return boolean
     */
    public final boolean hasRpcProcessor() {
        return m_rpcProcessor != null ? true : false;
    }

    /**
     * Get the associated RPC processor
     *
     * @return RpcSessionProcessor
     */
    public final RpcSessionProcessor getRpcProcessor() {
        return m_rpcProcessor;
    }

    /**
     * Set the associated RPC processor
     *
     * @param rpcProc RpcSessionProcessor
     */
    public final void setRpcProcessor(RpcSessionProcessor rpcProc) {
        m_rpcProcessor = rpcProc;
    }

    /**
     * Find the tree connection for the specified share hash
     *
     * @param shareHash int
     * @return TreeConnection
     */
    public final TreeConnection findConnection(int shareHash) {
        if (m_connections == null)
            return null;
        return m_connections.findConnection(shareHash);
    }

    /**
     * Add a new connection to the list of active tree connections for this session
     *
     * @param tree TreeConnection
     */
    public final void addConnection(TreeConnection tree) {
        if (m_connections == null)
            m_connections = new TreeConnectionHash();
        m_connections.addConnection(tree);
    }

    /**
     * Remove a connection from the list of active tree connections for this session
     *
     * @param tree TreeConnection
     */
    public final void removeConnection(TreeConnection tree) {
        if (m_connections == null)
            return;
        m_connections.deleteConnection(tree.getSharedDevice().getName());
    }

    /**
     * Set the authentication identifier
     *
     * @param authIdent Object
     */
    public final void setAuthIdentifier(Object authIdent) {
        m_authIdentifier = authIdent;
    }

    /**
     * Set the last access date/time for the session
     *
     * @param dateTime long
     */
    public final void setLastAccess(long dateTime) {
        m_lastAccess = dateTime;
    }

    /**
     * Set the last access date/time for the session
     */
    public final void setLastAccess() {
        m_lastAccess = System.currentTimeMillis();
    }

    /**
     * Close the session, cleanup any resources.
     */
    public void closeSession() {

        //	Cleanup open files, tree connections and searches
        cleanupSession();

        //  Inform listeners that the session has closed
        getNFSServer().fireSessionClosed(this);

        //	Call the base class
        super.closeSession();
    }

    /**
     * Allocate a slot in the active searches list for a new search.
     *
     * @param search SearchContext
     * @return int  Search slot index, or -1 if there are no more search slots available.
     */
    public synchronized final int allocateSearchSlot(SearchContext search) {

        //  Check if the search array has been allocated
        if (m_search == null)
            m_search = new SearchContext[DefaultSearches];

        //  Find a free slot for the new search
        int idx = 0;

        while (idx < m_search.length && m_search[idx] != null)
            idx++;

        //  Check if we found a free slot
        if (idx == m_search.length) {

            //  The search array needs to be extended, check if we reached the limit.
            if (m_search.length >= MaxSearches)
                return -1;

            //  Extend the search array
            SearchContext[] newSearch = new SearchContext[m_search.length * 2];
            System.arraycopy(m_search, 0, newSearch, 0, m_search.length);
            m_search = newSearch;
        }

        //	If the search context is valid then store in the allocated slot
        if (search != null)
            m_search[idx] = search;

        //  Return the allocated search slot index
        m_searchCount++;
        return idx;
    }

    /**
     * Deallocate the specified search context/slot.
     *
     * @param ctxId int
     */
    public synchronized final void deallocateSearchSlot(int ctxId) {

        //  Check if the search array has been allocated and that the index is valid
        if (m_search == null || ctxId >= m_search.length)
            return;

        //  Close the search
        if (m_search[ctxId] != null)
            m_search[ctxId].closeSearch();

        //  Free the specified search context slot
        m_searchCount--;
        m_search[ctxId] = null;
    }

    /**
     * Return the NFS server that the session is associated with
     *
     * @return NFSServer
     */
    public final NFSServer getNFSServer() {
        return (NFSServer) getServer();
    }

    /**
     * Return the search context for the specified search id.
     *
     * @param srchId int
     * @return SearchContext
     */
    public final SearchContext getSearchContext(int srchId) {

        //  Check if the search array is valid and the search index is valid
        if (m_search == null || srchId >= m_search.length)
            return null;

        //  Return the required search context
        return m_search[srchId];
    }

    /**
     * Return the number of active tree searches.
     *
     * @return int
     */
    public final int getSearchCount() {
        return m_searchCount;
    }

    /**
     * Store the seach context in the specified slot.
     *
     * @param slot Slot to store the search context.
     * @param srch SearchContext
     */
    protected final void setSearchContext(int slot, SearchContext srch) {

        //  Check if the search slot id is valid
        if (m_search == null || slot > m_search.length)
            return;

        //  Store the context
        m_search[slot] = srch;
    }

    /**
     * Cleanup any resources owned by this session, close files, searches and change notification requests.
     */
    protected final void cleanupSession() {

        //  Debug
        if (Debug.EnableInfo && hasDebug(DBG_SESSION))
            debugPrintln("NFS Cleanup session, searches=" + getSearchCount() +
                    ", files=" + (m_fileCache != null ? m_fileCache.numberOfEntries() : 0) +
                    ", treeConns=" + (m_connections != null ? m_connections.numberOfEntries() : 0));

        //  Check if there are any active searches
        if (m_search != null) {

            //  Close all active searches
            for (int idx = 0; idx < m_search.length; idx++) {

                //  Check if the current search slot is active
                if (m_search[idx] != null)
                    deallocateSearchSlot(idx);
            }

            //  Release the search context list, clear the search count
            m_search = null;
            m_searchCount = 0;
        }

        //	Close any open files
        if (m_fileCache != null)
            m_fileCache.closeAllFiles();

        //  Check if there are open tree connections
        if (m_connections != null && m_connections.numberOfEntries() > 0) {

            //	Enumerate the active connections
            Enumeration<TreeConnection> conns = m_connections.enumerateConnections();

            while (conns.hasMoreElements()) {

                //	Get the current tree connection
                TreeConnection tree = conns.nextElement();

                tree.closeConnection(this);

                //	Inform the driver that the connection has been closed
                DeviceInterface devIface = tree.getInterface();
                if (devIface != null)
                    devIface.treeClosed(this, tree);

                //  Release the connection list
                m_connections = null;
            }
        }
    }

    /**
     * Indicate that NFS filesystem searches are case sensitive
     *
     * @return boolean
     */
    public boolean useCaseSensitiveSearch() {
        return true;
    }
}
