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

package org.filesys.util.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import org.filesys.debug.Debug;


/**
 * Database Connection Pool Class
 *
 * @author gkspencer
 */
public class DBConnectionPool {

    //	Permanent lease time
    public final static long PermanentLease = -1L;

    //	Maximum/minimum connections
    public final static int MinimumConnections = 5;
    public final static int MaximumConnections = 500;

    //	Default pool size
    public final static int DefaultMinSize = 5;
    public final static int DefaultMaxSize = 10;

    //	Default connection lease time
    private final static long DefaultLease = 30000;    //	30 seconds

    //  Period that the database reaper checks the free connections to see if they have timed out
    private final static int ConnectionCheck = 20;   // x lease timeout

    //	Database connection details
    private String m_dbDriver;
    private String m_dsn;

    private String m_user;
    private String m_password;

    //	Minimum/maximum number of connections to pool
    private int m_minPoolSize = DefaultMinSize;
    private int m_maxPoolSize = DefaultMaxSize;

    //	Connection lease time, in milliseconds
    private long m_leaseTime = DefaultLease;

    //	Free/in use connection pools
    private List<Connection> m_freePool;
    private Hashtable<Connection, Long> m_allocPool;

    //	Connection reaper thread
    private DBConnectionReaper m_reaper;
    private Thread m_reaperThread;

    // Database connection status
    private boolean m_online;

    // Database connection pool event listener
    private DBConnectionPoolListener m_dbListener;

    /**
     * Database Connection Reaper Thread Class
     *
     * <p>Check for connections whose lease has expired and return them to the free connection
     * pool.
     */
    protected class DBConnectionReaper implements Runnable {

        //	Reaper wakeup interval
        private long m_wakeup;

        //	Shutdown request flag
        private boolean m_shutdown = false;

        // Database online check interval, as number of thread wakeups
        private int m_onlineCheckInterval = ConnectionCheck;

        /**
         * Class constructor
         *
         * @param intvl long
         */
        public DBConnectionReaper(long intvl) {
            m_wakeup = intvl;
        }

        /**
         * Shutdown the connection reaper
         */
        public final void shutdownRequest() {
            m_shutdown = true;
            m_reaperThread.interrupt();
        }

        /**
         * Set the database online check interval
         *
         * @param interval int
         */
        public final void setOnlineCheckInterval(int interval) {
            m_onlineCheckInterval = interval;
        }

        /**
         * Connection reaper thread
         */
        public void run() {

            //	Load the initial free connection pool
            synchronized (m_freePool) {

                try {

                    //	Allocate a pool of connections
                    while (m_freePool.size() < getMinimumPoolSize()) {
                        Connection conn = createConnection();
                        if (conn != null)
                            m_freePool.add(conn);
                    }

                    // Indicate that the connection pool is online
                    m_online = true;
                    notifyConnectionPoolState();
                }
                catch (SQLException ex) {
                }
            }

            //	Loop forever, or until shutdown
            int loopCnt = 1;

            while (m_shutdown == false) {

                //	Sleep for a while
                try {
                    Thread.sleep(m_wakeup);
                }
                catch (InterruptedException ex) {
                }

                //	Check if there is a shutdown pending
                if (m_shutdown == true)
                    break;

                //  Update the loop counter
                loopCnt++;

                //	Check for expired connection leases
                synchronized (m_allocPool) {

                    //	DEBUG
//					Debug.println("DBConnectionReaper allocPool=" + m_allocPool.size() + ", freePool=" + m_freePool.size());

                    //	Get the current time
                    long timeNow = System.currentTimeMillis();

                    //	Enumerate the allocated connection pool
                    Enumeration<Connection> enm = m_allocPool.keys();
                    boolean removeConn = false;

                    while (enm.hasMoreElements()) {

                        //	Get the connection

                        Connection conn = enm.nextElement();
                        Long expire = m_allocPool.get(conn);

                        //	Check if the connection lease has expired
                        if (expire.longValue() != PermanentLease && expire.longValue() < timeNow)
                            removeConn = true;
                        else if (expire.longValue() == PermanentLease) {

                            try {

                                // Check if the connection has been closed or the server is down
                                if (conn.isClosed())
                                    removeConn = true;
                                else {

                                    // Toggle the auto-commit flag on the connection, this will check the connection on most
                                    // databases
                                    boolean autoCommit = conn.getAutoCommit();
                                    conn.setAutoCommit(true);
                                    conn.setAutoCommit(autoCommit);
                                }
                            }
                            catch (SQLException ex) {
                                removeConn = true;
                            }

                            // DEBUG
                            if (removeConn == true)
                                Debug.println("DBConnectionReaper Permanent lease connection error");
                        }

                        // Check if the connection should be closed
                        if (removeConn == true) {

                            //	Connection lease has expired, remove from the allocated list and close the connection
                            m_allocPool.remove(conn);
                            try {
                                conn.close();
                                Debug.println("DBConnectionReaper closed expired connection, conn=" + conn);
                            }
                            catch (SQLException ex) {
                            }
                        }
                    }
                }

                //	Check if the free pool has grown too far
                synchronized (m_freePool) {

                    //	Release connections from the free pool until below the maximum connection limit
                    while (m_freePool.size() > getMaximumPoolSize()) {

                        //	DEBUG
                        Debug.println("DBConnectionReaper trimming free pool, " + m_freePool.size() + "/" + getMaximumPoolSize());

                        //	Remove a connection from the free pool and close the connection
                        Connection conn = m_freePool.remove(0);

                        if (conn != null) {
                            try {
                                conn.close();
                            }
                            catch (SQLException ex) {
                            }
                        }
                    }
                }

                // Check the connections in the free pool to see if they have been timed out by the server
                if (loopCnt % m_onlineCheckInterval == 0 || isOnline() == false) {

                    synchronized (m_freePool) {

                        // DEBUG
//            Debug.println( "DBConnectionReaper Checking free pool connection status ...");

                        //  Check if the connections in the free pool are still connected/valid
                        int idx = 0;

                        while (idx < m_freePool.size()) {

                            // Get the current connection
                            Connection conn = m_freePool.get(idx);

                            try {

                                // Check if the connection is closed
                                if (conn.isClosed()) {

                                    // Remove the connection from the free pool
                                    m_freePool.remove(idx);

                                    // DEBUG
//                  Debug.println( "DBConnectionReaper Removed closed connection from free pool");
                                }
                                else {

                                    // Toggle the auto-commit flag on the connection, this will check the connection on most
                                    // databases
                                    boolean autoCommit = conn.getAutoCommit();
                                    conn.setAutoCommit(true);
                                    conn.setAutoCommit(autoCommit);

                                    // Update the connection index
                                    idx++;
                                }
                            }
                            catch (SQLException ex) {

                                // Remove the current connection from the free pool
                                try {
                                    m_freePool.remove(idx);
                                    conn.close();
                                }
                                catch (Exception ex2) {
                                }

                                // DEBUG
//                Debug.println( "DBConnectionReaper Removed closed connection from free pool (exception)");
                            }
                        }

                        if (isOnline()) {

                            // Check if the free and allocated pools are empty, this indicates that the database server
                            // is offline
                            if (m_freePool.size() == 0 && m_allocPool.size() == 0) {

                                // Database server appears to be offline
                                m_online = false;
                                notifyConnectionPoolState();
                            }
                        }
                        else {

                            // Try and get a connection from the pool, this will check if the database server is back online
                            Connection conn = getConnection();
                            if (conn != null)
                                releaseConnection(conn);
                        }

                        // DEBUG
//            Debug.println( "DBConnectionReaper Free pool check done.");
                    }
                }
            }
        }
    }

    ;

    /**
     * Class constructor
     *
     * @param driver String
     * @param dsn    String
     * @param user   String
     * @param pwd    String
     * @exception Exception Failed to initialize the connection pool
     */
    public DBConnectionPool(String driver, String dsn, String user, String pwd)
            throws Exception {

        //	Set the JDBC connection details
        m_dbDriver = driver;
        m_dsn = dsn;
        m_user = user;
        m_password = pwd;

        // Call the common constructor code
        commonConstructor();
    }

    /**
     * Class constructor
     *
     * @param driver    String
     * @param dsn       String
     * @param user      String
     * @param pwd       String
     * @param initConns int
     * @param maxConns  int
     * @exception Exception Failed to initialize the connection pool
     */
    public DBConnectionPool(String driver, String dsn, String user, String pwd, int initConns, int maxConns)
            throws Exception {

        //	Set the JDBC connection details
        m_dbDriver = driver;
        m_dsn = dsn;
        m_user = user;
        m_password = pwd;

        //	Set the pool size
        if (initConns > 0)
            m_minPoolSize = initConns;

        if (maxConns > 0)
            m_maxPoolSize = maxConns;

        // Call the common constructor code
        commonConstructor();
    }

    /**
     * Common constructor code
     *
     * @exception Exception Failed to initialize the connection pool
     */
    private void commonConstructor()
            throws Exception {

        //  Load the database driver class
        Class.forName(m_dbDriver).newInstance();

        //  Allocate the free and in use connection pools
        m_freePool = new ArrayList<Connection>();
        m_allocPool = new Hashtable<Connection, Long>();

        //  Start the connection reaper thread
        m_reaper = new DBConnectionReaper(getLeaseTime());
        m_reaperThread = new Thread(m_reaper);
        m_reaperThread.setDaemon(true);
        m_reaperThread.setName("DBConnectionReaper");
        m_reaperThread.start();
    }

    /**
     * Get the JDBC driver details
     *
     * @return String
     */
    public final String getDriver() {
        return m_dbDriver;
    }

    /**
     * Get the connection details
     *
     * @return String
     */
    public final String getDSN() {
        return m_dsn;
    }

    /**
     * Get the user name
     *
     * @return String
     */
    public final String getUserName() {
        return m_user;
    }

    /**
     * Get the password
     *
     * @return String
     */
    public final String getPassword() {
        return m_password;
    }

    /**
     * Get the minimum pool size
     *
     * @return int
     */
    public final int getMinimumPoolSize() {
        return m_minPoolSize;
    }

    /**
     * Get the maximum pool size
     *
     * @return int
     */
    public final int getMaximumPoolSize() {
        return m_maxPoolSize;
    }

    /**
     * Get the connection lease time, in milliseconds
     *
     * @return long
     */
    public final long getLeaseTime() {
        return m_leaseTime;
    }

    /**
     * Get the available connection count
     *
     * @return int
     */
    public final synchronized int getAvailableConnections() {
        return m_freePool.size();
    }

    /**
     * Get the in use connection count
     *
     * @return int
     */
    public final synchronized int getAllocatedConnections() {
        return m_allocPool.size();
    }

    /**
     * Get a connection from the pool
     *
     * @return Connection
     */
    public final Connection getConnection() {

        //	Get a timestamp for the connection
        long expireTime = System.currentTimeMillis() + getLeaseTime();
        return getConnection(expireTime);
    }

    /**
     * Check if the connection pool is online
     *
     * @return boolean
     */
    public final boolean isOnline() {
        return m_online;
    }

    /**
     * Get a connection from the pool with the specified lease time. A lease time of -1 indicates that
     * the connection lease is permanent.
     *
     * @param expireTime long
     * @return Connection
     */
    public final Connection getConnection(long expireTime) {

        //	Check for a connection in the free pool
        Connection conn = null;

        synchronized (m_freePool) {

            //	Check if the free pool has a connection
            if (m_freePool.size() > 0) {

                //	Get the connection
                conn = m_freePool.remove(0);

                try {
                    if (conn.isClosed())
                        conn = null;
                }
                catch (SQLException ex) {
                    conn = null;
                    Debug.println("%%%%% SQL Connection Error: " + ex.toString());
                }
            }
            else if (isOnline() == false) {

                // Try and create a new connection, if we succeed then the database server is back online
                try {

                    // Create a new database connection
                    conn = createConnection();
                }
                catch (SQLException ex) {
                }
            }
        }

        //  Check if the database server is back online
        if (isOnline() == false) {
            if (conn != null) {
                m_online = true;
                notifyConnectionPoolState();
            }
            else
                return null;
        }

        //	Allocate a new connection if there are spare slots available
        synchronized (m_allocPool) {

            //	If the connection is valid add it to the allocated pool
            if (conn != null)
                m_allocPool.put(conn, new Long(expireTime));
            else {

                //	If a connection has not been allocated and there are spare slots available then
                //	create a new connection.
                if (m_allocPool.size() < getMaximumPoolSize()) {

                    try {

                        //	Create a new connection
                        conn = createConnection();

                        //	Add the connection to the in use list
                        m_allocPool.put(conn, new Long(expireTime));
                    }
                    catch (SQLException ex) {
                        conn = null;
                        Debug.println("%%%%% SQL Connection Error: " + ex.toString());
                    }
                }
            }
        }

        //	Return the connection
        return conn;
    }

    /**
     * Release a connection back to the free pool
     *
     * @param conn Connection
     */
    public final void releaseConnection(Connection conn) {

        //	Remove the connection from the in use pool and put it back in the free list
        synchronized (m_allocPool) {
            Object curConn = m_allocPool.remove(conn);
            if (curConn == null)
                return;
        }

        //	Add the connection to the free pool
        synchronized (m_freePool) {

            //	Add the connection back to the free pool, if not closed
            try {
                if (conn.isClosed() == false) {
                    m_freePool.add(conn);
                }
                else
                    Debug.println("***** Connection closed *****");
            }
            catch (Exception ex) {
            }
        }
    }

    /**
     * Renew a lease on a connection for the default lease time
     *
     * @param conn Connection
     */
    public final void renewLease(Connection conn) {
        renewLease(conn, System.currentTimeMillis() + getLeaseTime());
    }

    /**
     * Renew a lease on a connection to hold onto the connection for longer
     *
     * @param conn       Connection
     * @param expireTime long
     */
    public final void renewLease(Connection conn, long expireTime) {

        synchronized (m_allocPool) {

            //	Check that the connection is in the allocated pool
            if (m_allocPool.remove(conn) == null)
                return;

            //	Update the expire time for the lease and add back to the allocated pool
            m_allocPool.put(conn, new Long(expireTime));
        }
    }

    /**
     * Close the connection pool
     */
    public final void closePool() {

        //	Shutdown the connection reaper thread
        m_reaper.shutdownRequest();

        //	Close all allocated database connections
        if (m_allocPool.size() > 0) {

            //	Close all allocated connections
            Enumeration<Connection> enm = m_allocPool.keys();

            while (enm.hasMoreElements()) {

                //	Close the connection
                Connection conn = enm.nextElement();
                try {
                    conn.close();
                }
                catch (SQLException ex) {
                }
            }

            //	Clear the allocated pool
            m_allocPool.clear();
        }

        //	Close all free database connections
        if (m_freePool.size() > 0) {

            //	Close the free pool connections
            for (int i = 0; i < m_freePool.size(); i++) {

                //	Get the current connection
                Connection conn = m_freePool.get(i);
                try {
                    conn.close();
                }
                catch (SQLException ex) {
                }
            }

            //	Clear the free pool
            m_freePool.clear();
        }
    }

    /**
     * Set the connection pool initial and maximum sizes
     *
     * @param initSize int
     * @param maxSize  int
     */
    public final void setPoolSize(int initSize, int maxSize) {
        m_minPoolSize = initSize;
        m_maxPoolSize = maxSize;
    }

    /**
     * Set the default connection lease time
     *
     * @param leaseTime long
     */
    public final void setDefaultLeaseTime(long leaseTime) {
        m_leaseTime = leaseTime;
    }

    /**
     * Set the online check interval, in seconds
     *
     * @param interval int
     */
    public final void setOnlineCheckInterval(int interval) {

        // Set the reaper thread online check interval, convert seconds to thread wakeups
        int wakeups = interval / (int) DefaultLease;
        if (wakeups < 1)
            wakeups = 1;

        m_reaper.setOnlineCheckInterval(wakeups);
    }

    /**
     * Check if there is a connection pool listener
     *
     * @return boolean
     */
    public final boolean hasConnectionPoolListener() {
        return m_dbListener != null ? true : false;
    }

    /**
     * Add a database connection pool listener
     *
     * @param l DBConnectionPoolListener
     */
    public final void addConnectionPoolListener(DBConnectionPoolListener l) {
        m_dbListener = l;
    }

    /**
     * Remove a database connection pool listener
     *
     * @return DBConnectionPoolListener
     */
    public final DBConnectionPoolListener removeConnectionPoolListener() {
        DBConnectionPoolListener l = m_dbListener;
        m_dbListener = null;
        return l;
    }

    /**
     * Create a new database connection
     *
     * @return Connection
     * @throws SQLException Error creating the connection
     */
    protected final Connection createConnection()
            throws SQLException {

        //	Create a new database connection
        return DriverManager.getConnection(getDSN(), getUserName(), getPassword());
    }

    /**
     * Notify the connection pool listener of an online/offline state change
     */
    protected final void notifyConnectionPoolState() {

        // DEBUG
        Debug.println("DBConnectionPool: Database server is " + (isOnline() ? "OnLine" : "OffLine"));

        // Inform the connection pool listener
        if (hasConnectionPoolListener())
            m_dbListener.databaseOnlineStatus(isOnline());
        else
            Debug.println("DBConnectionPool: No listener");
    }

    /**
     * Wait for a valid database connection, up to the specified number of seconds
     *
     * @param waitSecs int
     * @return boolean
     */
    public final boolean waitForConnection(int waitSecs) {

        Connection dbConn = null;
        long timeEnd = System.currentTimeMillis() + (waitSecs * 1000L);

        while ( dbConn == null && System.currentTimeMillis() < timeEnd) {

            try {

                // Get a database connection
                dbConn = createConnection();
            }
            catch ( SQLException ex) {
            }
            catch ( Exception ex) {
            }

            // Check if we got a valid database connection
            if ( dbConn == null) {

                // Wait a while before trying to get the database connection
                try {
                    Thread.sleep(1000);
                }
                catch ( InterruptedException ex) {
                    return false;
                }
            }
        }

        // Return the database connection status
        return dbConn != null ? true : false;
    }
}
