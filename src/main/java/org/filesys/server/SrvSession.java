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

import java.net.InetAddress;

import org.filesys.debug.Debug;
import org.filesys.server.auth.AuthContext;
import org.filesys.server.auth.ClientInfo;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.core.SharedDeviceList;
import org.filesys.server.filesys.TransactionalFilesystemInterface;

/**
 * Server Session Base Class
 *
 * <p>
 * Base class for server session implementations for different protocols.
 *
 * @author gkspencer
 */
public abstract class SrvSession {

    // Network server this session is associated with
    private NetworkServer m_server;

    // Session id/slot number
    private int m_sessId;

    // Unique session id string
    private String m_uniqueId;

    // Process id
    private int m_processId = -1;

    // Session/user is logged on/validated
    private boolean m_loggedOn;

    // Indicate if this is a persistent session that will survive passed a socket disconnection
    private boolean m_persistentSess;

    // Time the session was disconnected
    private long m_disconnectTime;

    // Client details
    private static ThreadLocal<ClientInfo> m_clientInfo = new ThreadLocal<ClientInfo>();

    // Authentication context, used during the initial session setup phase
    private AuthContext m_authContext;

    // Debug flags for this session, and debug output interface.
    private int m_debug;
    private String m_dbgPrefix;

    // List of dynamic/temporary shares created for this session
    private SharedDeviceList m_dynamicShares;

    // Session shutdown flag
    private boolean m_shutdown;

    // Protocol type
    private String m_protocol;

    // Remote client/host name
    private String m_remoteName;

    // Transaction object, for filesystems that implement the TransactionalFilesystemInterface
    private static ThreadLocal<Object> m_tx = new ThreadLocal<Object>();
    private static ThreadLocal<TransactionalFilesystemInterface> m_txInterface = new ThreadLocal<TransactionalFilesystemInterface>();

    // Time of last I/O on this session
    private long m_lastIO;

    // Request post-processing hook
    private RequestPostProcessor m_reqPostProcessor;

    // Place for the driver to store state
    private Object driverState;

    /**
     * Class constructor
     *
     * @param sessId     int
     * @param srv        NetworkServer
     * @param proto      String
     * @param remoteName String
     */
    public SrvSession(int sessId, NetworkServer srv, String proto, String remoteName) {
        m_sessId = sessId;
        m_server = srv;

        setProtocolName(proto);
        setRemoteName(remoteName);
    }

    /**
     * Output a string to the debug device
     *
     * @param str String
     */
    public final void debugPrint(String str) {
        Debug.print(str);
    }

    /**
     * Output a string and a newline to the debug device
     *
     * @param str String
     */
    public final void debugPrintln(String str) {
        Debug.print(m_dbgPrefix);
        Debug.println(str);
    }

    /**
     * Output an exception stack trace to the debug device
     *
     * @param ex Exception
     */
    public final void debugPrintln(Exception ex) {
        Debug.println(ex);
    }

    /**
     * Check if the session has an authentication context
     *
     * @return boolean
     */
    public final boolean hasAuthenticationContext() {
        return m_authContext != null ? true : false;
    }

    /**
     * Return the authentication context for this sesion
     *
     * @return AuthContext
     */
    public final AuthContext getAuthenticationContext() {
        return m_authContext;
    }

    /**
     * Add a dynamic share to the list of shares created for this session
     *
     * @param shrDev SharedDevice
     */
    public final void addDynamicShare(SharedDevice shrDev) {

        // Check if the dynamic share list must be allocated
        if (m_dynamicShares == null)
            m_dynamicShares = new SharedDeviceList();

        // Add the new share to the list
        m_dynamicShares.addShare(shrDev);
    }

    /**
     * Determine if the session has any dynamic shares
     *
     * @return boolean
     */
    public final boolean hasDynamicShares() {
        return m_dynamicShares != null ? true : false;
    }

    /**
     * Return the list of dynamic shares created for this session
     *
     * @return SharedDeviceList
     */
    public final SharedDeviceList getDynamicShareList() {
        return m_dynamicShares;
    }

    /**
     * Return the process id
     *
     * @return int
     */
    public final int getProcessId() {
        return m_processId;
    }

    /**
     * Return the remote client network address
     *
     * @return InetAddress
     */
    public abstract InetAddress getRemoteAddress();

    /**
     * Return the session id for this session.
     *
     * @return int
     */
    public final int getSessionId() {
        return m_sessId;
    }

    /**
     * Return the server this session is associated with
     *
     * @return NetworkServer
     */
    public final NetworkServer getServer() {
        return m_server;
    }

    /**
     * Check if the session has valid client information
     *
     * @return boolean
     */
    public final boolean hasClientInformation() {
        return m_clientInfo.get() != null ? true : false;
    }

    /**
     * Return the client information
     *
     * @return ClientInfo
     */
    public final ClientInfo getClientInformation() {
        return m_clientInfo.get();
    }

    /**
     * Determine if the protocol type has been set
     *
     * @return boolean
     */
    public final boolean hasProtocolName() {
        return m_protocol != null ? true : false;
    }

    /**
     * Return the protocol name
     *
     * @return String
     */
    public final String getProtocolName() {
        return m_protocol;
    }

    /**
     * Determine if the remote client name has been set
     *
     * @return boolean
     */
    public final boolean hasRemoteName() {
        return m_remoteName != null ? true : false;
    }

    /**
     * Return the remote client name
     *
     * @return String
     */
    public final String getRemoteName() {
        return m_remoteName;
    }

    /**
     * Determine if the session is logged on/validated
     *
     * @return boolean
     */
    public final boolean isLoggedOn() {
        return m_loggedOn;
    }

    /**
     * Determine if this is a persistent session
     *
     * @return boolean
     */
    public final boolean isPersistentSession() { return m_persistentSess; }

    /**
     * Determine if the session has been shut down
     *
     * @return boolean
     */
    public final boolean isShutdown() {
        return m_shutdown;
    }

    /**
     * Return the unique session id
     *
     * @return String
     */
    public final String getUniqueId() {
        return m_uniqueId;
    }

    /**
     * Determine if the specified debug flag is enabled.
     *
     * @param dbgFlag int
     * @return boolean
     */
    public final boolean hasDebug(int dbgFlag) {
        if ((m_debug & dbgFlag) != 0)
            return true;
        return false;
    }

    /**
     * Return the time of the last I/o on this session
     *
     * @return long
     */
    public final long getLastIOTime() {
        return m_lastIO;
    }

    /**
     * Check if there are post processor requests queued
     *
     * @return boolean
     */
    public final boolean hasPostProcessorRequests() {
        return RequestPostProcessor.hasPostProcessor();
    }

    /**
     * Return the post processor at the head of the queue, or null if there are no more post processors
     * queued.
     *
     * @return RequestPostProcessor
     */
    public final RequestPostProcessor getNextPostProcessor() {
        return RequestPostProcessor.dequeuePostProcessor();
    }

    /**
     * Determine if the protocol uses case sensitive filesystem searches
     *
     * @return boolean
     */
    public abstract boolean useCaseSensitiveSearch();

    /**
     * Set the authentication context, used during the initial session setup phase
     *
     * @param ctx AuthContext
     */
    public final void setAuthenticationContext(AuthContext ctx) {
        m_authContext = ctx;
    }

    /**
     * Set the client information
     *
     * @param client ClientInfo
     */
    public final void setClientInformation(ClientInfo client) {
        m_clientInfo.set(client);
    }

    /**
     * Set the debug output interface.
     *
     * @param flgs int
     */
    public final void setDebug(int flgs) {
        m_debug = flgs;
    }

    /**
     * Set the debug output prefix for this session
     *
     * @param prefix String
     */
    public final void setDebugPrefix(String prefix) {
        m_dbgPrefix = prefix;
    }

    /**
     * Set the logged on/validated status for the session
     *
     * @param loggedOn boolean
     */
    public final void setLoggedOn(boolean loggedOn) {
        m_loggedOn = loggedOn;
    }

    /**
     * Set the persistent session setting for this session
     *
     * @param persistSess Boolean
     */
    public final void setPersistentSession(boolean persistSess) { m_persistentSess = persistSess; }

    /**
     * Check if the session has a disconnected system timestamp
     *
     * @return boolean
     */
    public final boolean isDisconnectedSession() {
        return m_disconnectTime != 0L ? true : false;
    }

    /**
     * Get the system time that the session was disconnected
     *
     * @return long
     */
    public final long getDisconnectedAt() {
        return m_disconnectTime;
    }

    /**
     * Set the session disconnected system time
     *
     * @param disconnectTime long
     */
    public final void setDisconnectedAt(long disconnectTime) { m_disconnectTime = disconnectTime; }

    /**
     * Clear the session disconnected system time
     */
    public final void clearDisconnectedAt() { m_disconnectTime = 0L; }

    /**
     * Set the process id
     *
     * @param id int
     */
    public final void setProcessId(int id) {
        m_processId = id;
    }

    /**
     * Set the protocol name
     *
     * @param name String
     */
    public final void setProtocolName(String name) {
        m_protocol = name;
    }

    /**
     * Set the remote client name
     *
     * @param name String
     */
    public final void setRemoteName(String name) {
        m_remoteName = name;
    }

    /**
     * Set the session id for this session.
     *
     * @param id int
     */
    public final void setSessionId(int id) {
        m_sessId = id;
    }

    /**
     * Set the unique session id
     *
     * @param unid String
     */
    public final void setUniqueId(String unid) {
        m_uniqueId = unid;
    }

    /**
     * Set the time of hte last I/O on this session
     *
     * @param ioTime long
     */
    public final void setLastIOTime(long ioTime) {
        m_lastIO = ioTime;
    }

    /**
     * Set the shutdown flag
     *
     * @param flg boolean
     */
    protected final void setShutdown(boolean flg) {
        m_shutdown = flg;
    }

    /**
     * Close the network session
     */
    public void closeSession() {

        // Release any dynamic shares owned by this session
        if (hasDynamicShares()) {

            // Close the dynamic shares
            getServer().getShareMapper().deleteShares(this);
        }
    }

    /**
     * Initialize the thread local transaction objects
     */
    public final void initializeTransactionObject() {
        if (m_tx == null)
            m_tx = new ThreadLocal<Object>();
        if (m_txInterface == null)
            m_txInterface = new ThreadLocal<TransactionalFilesystemInterface>();
    }

    /**
     * Return the transaction context
     *
     * @return Transaction context thread local object
     */
    public final ThreadLocal<Object> getTransactionObject() {
        return m_tx;
    }

    /**
     * Set the active transaction and transaction interface
     *
     * @param tx      Object
     * @param txIface TransactionalFilesystemInterface
     */
    public final void setTransaction(Object tx, TransactionalFilesystemInterface txIface) {
        m_tx.set(tx);
        m_txInterface.set(txIface);
    }

    /**
     * Set the active transaction interface
     *
     * @param txIface TransactionalFilesystemInterface
     */
    public final void setTransaction(TransactionalFilesystemInterface txIface) {
        m_txInterface.set(txIface);
    }

    /**
     * Clear the stored transaction
     */
    public final void clearTransaction() {
        m_tx.set(null);
        m_txInterface.set(null);
    }

    /**
     * End a transaction
     */
    public final void endTransaction() {

        // Check if there is an active transaction
        if (m_txInterface != null && m_txInterface.get() != null && m_tx != null) {

            // Use the transaction interface to end the transaction
            m_txInterface.get().endTransaction(this, m_tx.get());
        }
    }

    /**
     * Check if there is an active transaction
     *
     * @return boolean
     */
    public final boolean hasTransaction() {
        if (m_tx == null)
            return false;
        return m_tx.get() != null ? true : false;
    }

    /**
     * Are pseudo files enabled for this session?
     *
     * @return boolean
     */
    public boolean isPseudoFilesEnabled() {
        return false;
    }

    /**
     * Get the Driver State.  A place for the content driver to
     * store state in the session.
     *
     * @return the driver state.
     */
    public Object getDriverState() {
        return driverState;
    }

    /**
     * Set the Driver State.   A place for the content driver to
     * store state in the session.
     *
     * @param driverState Object
     */
    public void setDriverState(Object driverState) {
        this.driverState = driverState;
    }

    /**
     * Return the session details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Session id=");
        str.append(getSessionId());
        str.append(",unique=");
        str.append(getUniqueId());
        str.append(",proto=");
        str.append(getProtocolName());

        if ( isPersistentSession())
            str.append(" Persistent");
        if ( isDisconnectedSession())
            str.append(" Disconnected");

        str.append("]");

        return str.toString();
    }
}
