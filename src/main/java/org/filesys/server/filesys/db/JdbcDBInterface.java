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

package org.filesys.server.filesys.db;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

import org.filesys.debug.Debug;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.loader.DeleteFileRequest;
import org.filesys.server.filesys.loader.FileRequest;
import org.filesys.server.filesys.loader.FileRequestQueue;
import org.filesys.server.filesys.loader.MultipleFileRequest;
import org.filesys.server.filesys.loader.SingleFileRequest;
import org.filesys.util.MemorySize;
import org.filesys.util.db.DBConnectionPool;
import org.filesys.util.db.DBConnectionPoolListener;
import org.springframework.extensions.config.ConfigElement;

/**
 * JDBC Database Interface Abstract Class
 *
 * <p>Provides the standard variables for a JDBC based database interface, including the parsing of the
 * parameters in the initialization method.
 *
 * @author gkspencer
 */
public abstract class JdbcDBInterface implements DBInterface, DBConnectionPoolListener {

    //	Constants
    //
    //	Default database table names
    public final static String FileSysTable         = "JFileSrvFileSys";
    public final static String StreamsTable         = "JFileSrvStreams";
    public final static String RetentionTable       = "JFileSrvRetain";
    public final static String QueueTable           = "JFileSrvQueue";
    public final static String TransactQueueTable   = "JFileSrvTransQueue";
    public final static String DataTable            = "JFileSrvData";
    public final static String JarDataTable         = "JFileSrvJarData";
    public final static String ObjectIdTable        = "JFileSrvObjectIds";
    public final static String SymLinkTable         = "JFileSrvSymLinks";

    //	Number of connections to allocate in the connection pool
    public final static int NumPoolConnections = 5;

    //	Special characters that must be escaped in file/directory names
    protected String m_specialChars = "\'\"\\";

    //	Default file data fragment size
    public final static long DefaultFragSize    = MemorySize.MEGABYTE / 2;      //	1/2Mb
    public final static long MinFragSize        = MemorySize.KILOBYTE * 64L;    // 	64Kb
    public final static long MaxFragSize        = MemorySize.GIGABYTE;          //	1Gb

    //  Database device context
    protected DBDeviceContext m_dbCtx;

    //	Supported/requested database features
    private int m_features;
    private int m_reqFeatures;

    //	JDBC driver class
    protected String m_driver;

    //	JDBC connection string
    protected String m_dsn;

    //	Username and password
    protected String m_userName;
    protected String m_password;

    // Time to wait for a valid database connection during startup
    protected int m_dbWaitSecs = 0;

    //	Database table that contains the file system structure records, streams information
    //	records, data retention information records, file loader queue and file loader transaction
    //	queue and file data tables.
    protected String m_structTable;
    protected String m_streamTable;
    protected String m_retentionTable;
    protected String m_queueTable;
    protected String m_transactTable;
    protected String m_dataTable;
    protected String m_jarDataTable;
    protected String m_objectIdTable;
    protected String m_symLinkTable;

    //	Retention period, milliseconds to add to current date/time value, or -1 if not enabled
    protected long m_retentionPeriod = -1;

    //	Database connection pool
    protected DBConnectionPool m_connPool;

    protected int m_dbInitConns = DBConnectionPool.DefaultMinSize;
    protected int m_dbMaxConns = DBConnectionPool.DefaultMaxSize;

    protected int m_onlineCheckInterval;

    //	Data fragment size to store per BLOB when the file data is stored in the database
    protected long m_dataFragSize = DefaultFragSize;

    //  Pending file save requests, used when the database goes offline
    protected FileRequestQueue m_pendingSaveRequests;

    //	Debug and SQL debug enable flags
    protected boolean m_debug;
    protected boolean m_sqlDebug;

    // Lock file path
    private String m_lockFile;

    // Use crash recovery folder
    private boolean m_crashRecovery;

    /**
     * Default constructor
     */
    public JdbcDBInterface() {
        m_features = getSupportedFeatures();
    }

    /**
     * Determine if the database interface supports the specified feature
     *
     * @param feature int
     * @return boolean
     */
    public final boolean supportsFeature(int feature) {
        return (m_features & feature) != 0 ? true : false;
    }

    /**
     * Request the specified database features be enabled
     *
     * @param featureMask int
     * @throws DBException Database error
     */
    public void requestFeatures(int featureMask)
            throws DBException {

        //	Get the database interface supported features
        int supFeatures = getSupportedFeatures();

        //	Check if there are any unsupported features requested
        if ((featureMask | supFeatures) != supFeatures)
            throw new DBException("Unsupported feature requested");

        //	Set the requested features
        m_reqFeatures = featureMask;
    }

    /**
     * Get the supported database features mask
     *
     * @return int
     */
    protected abstract int getSupportedFeatures();

    /**
     * Check if the crash recovery folder is enabled
     *
     * @return boolean
     */
    public final boolean hasCrashRecovery() {
        return m_crashRecovery;
    }

    /**
     * Check if data retention is enabled
     *
     * @return boolean
     */
    public final boolean isRetentionEnabled() {
        return (m_reqFeatures & FeatureRetention) != 0 ? true : false;
    }

    /**
     * Check if NTFS streams are enabled
     *
     * @return boolean
     */
    public final boolean isNTFSEnabled() {
        return (m_reqFeatures & FeatureNTFS) != 0 ? true : false;
    }

    /**
     * Check if the database queue is enabled
     *
     * @return boolean
     */
    public final boolean isQueueEnabled() {
        return (m_reqFeatures & FeatureQueue) != 0 ? true : false;
    }

    /**
     * Check if database file data load/save is enabled
     *
     * @return boolean
     */
    public final boolean isDataEnabled() {
        return (m_reqFeatures & FeatureData) != 0 ? true : false;
    }

    /**
     * Check if database Jar file data load/save is enabled
     *
     * @return boolean
     */
    public final boolean isJarDataEnabled() {
        return (m_reqFeatures & FeatureJarData) != 0 ? true : false;
    }

    /**
     * Check if the file id/object id mapping feature is enabled
     *
     * @return boolean
     */
    public final boolean isObjectIdEnabled() {
        return (m_reqFeatures & FeatureObjectId) != 0 ? true : false;
    }

    /**
     * Check if the symbolic links feature is enabled
     *
     * @return boolean
     */
    public final boolean isSymbolicLinksEnabled() {
        return (m_reqFeatures & FeatureSymLinks) != 0 ? true : false;
    }

    /**
     * Get the lock file
     *
     * @return String
     */
    protected final String getLockFile() {
        return m_lockFile;
    }

    /**
     * Set the lock file
     *
     * @param lockFile String
     */
    protected final void setLockFile(String lockFile) {
        m_lockFile = lockFile;
    }

    /**
     * Set the data fragment size, used when storing file data to blob fields/oid files
     *
     * @param fragSize long
     * @return long
     */
    protected final long setDataFragmentSize(long fragSize) {
        if (fragSize >= MinFragSize && fragSize <= MaxFragSize)
            m_dataFragSize = fragSize;

        return m_dataFragSize;
    }

    /**
     * Initialize the database interface
     *
     * @param context DBDeviceContext
     * @param params  NameValueList
     * @throws InvalidConfigurationException Error initializing the database interface
     */
    public void initializeDatabase(DBDeviceContext context, ConfigElement params)
            throws InvalidConfigurationException {

        //  Save the context
        m_dbCtx = context;

        //  Parse the standard JDBC database interface parameters
        ConfigElement nameVal = null;

        nameVal = params.getChild("DSN");
        if (nameVal != null)
            m_dsn = nameVal.getValue();

        nameVal = params.getChild("Username");
        if (nameVal != null)
            m_userName = nameVal.getValue();

        nameVal = params.getChild("Password");
        if (nameVal != null)
            m_password = nameVal.getValue();

        nameVal = params.getChild("FileSystemTable");
        if (nameVal != null)
            m_structTable = nameVal.getValue();
        else
            m_structTable = FileSysTable;

        nameVal = params.getChild("StreamsTable");
        if (nameVal != null)
            m_streamTable = nameVal.getValue();
        else
            m_streamTable = StreamsTable;

        nameVal = params.getChild("RetentionTable");
        if (nameVal != null)
            m_retentionTable = nameVal.getValue();
        else
            m_retentionTable = RetentionTable;

        nameVal = params.getChild("QueueTable");
        if (nameVal != null)
            m_queueTable = nameVal.getValue();
        else
            m_queueTable = QueueTable;

        nameVal = params.getChild("TransactQueueTable");
        if (nameVal != null)
            m_transactTable = nameVal.getValue();
        else
            m_transactTable = TransactQueueTable;

        nameVal = params.getChild("DataTable");
        if (nameVal != null)
            m_dataTable = nameVal.getValue();
        else
            m_dataTable = DataTable;

        nameVal = params.getChild("JarDataTable");
        if (nameVal != null)
            m_jarDataTable = nameVal.getValue();
        else
            m_jarDataTable = JarDataTable;

        nameVal = params.getChild("ObjectIdTable");
        if (nameVal != null)
            m_objectIdTable = nameVal.getValue();
        else
            m_objectIdTable = ObjectIdTable;

        nameVal = params.getChild("SymLinksTable");
        if (nameVal != null)
            m_symLinkTable = nameVal.getValue();
        else
            m_symLinkTable = SymLinkTable;

        //  Check if the database connection pool initial and maximum size has been specified
        nameVal = params.getChild("ConnectionPool");

        if (nameVal != null) {
            try {

                //  Check for a single value or split initial/maximum values
                String numVal = nameVal.getValue();
                int pos = numVal.indexOf(':');

                if (pos == -1) {

                    //  Use the same number of read and write worker threads
                    m_dbMaxConns = Integer.parseInt(numVal);
                } else {

                    //  Split the string value into read and write values, and convert to integers
                    String val = numVal.substring(0, pos);
                    m_dbInitConns = Integer.parseInt(val);

                    val = numVal.substring(pos + 1);
                    m_dbMaxConns = Integer.parseInt(val);
                }

                //  Range check the initial/maximum connection pool sizes
                if (m_dbInitConns < DBConnectionPool.MinimumConnections ||
                        m_dbInitConns > m_dbMaxConns)
                    throw new InvalidConfigurationException("Database interface invalid initial connections value");

                if (m_dbMaxConns > DBConnectionPool.MaximumConnections ||
                        m_dbMaxConns < m_dbInitConns)
                    throw new InvalidConfigurationException("Database interface invalid maximum connections value");
            }
            catch (NumberFormatException ex) {
                throw new InvalidConfigurationException("Database interface invalid ConnectionPool value, " + ex.toString());
            }
        }

        //  Check if the database online check interval has been specified
        nameVal = params.getChild("OnlineCheckInterval");
        if (nameVal != null) {
            try {

                // Parse the online check interval value
                m_onlineCheckInterval = Integer.parseInt(nameVal.getValue());
                if (m_onlineCheckInterval < 1 || m_onlineCheckInterval > 30)
                    throw new InvalidConfigurationException("Database online check interval out of valid range (1-30)");
            }
            catch (NumberFormatException ex) {
                throw new InvalidConfigurationException("Database online check interval value invalid, " + nameVal.getValue());
            }
        }

        // Check if the startup database connection wait time has been specified
        nameVal = params.getChild("WaitForDatabase");
        if (nameVal != null) {
            try {

                // Parse the startup wait for database connection interval value
                m_dbWaitSecs = Integer.parseInt(nameVal.getValue());
                if (m_dbWaitSecs < 10 || m_dbWaitSecs > 600)
                    throw new InvalidConfigurationException("Database wait for connection interval out of valid range (10-600");
            }
            catch (NumberFormatException ex) {
                throw new InvalidConfigurationException("Database wait for connection interval value invalid, " + nameVal.getValue());
            }
        }

        //  Check if debug output is enabled
        if (params.getChild("Debug") != null)
            m_debug = true;

        //  Check if SQL debug output is enabled
        if (params.getChild("SQLDebug") != null)
            m_sqlDebug = true;

        //  Copy the retention period from the context, value will be -1 if not enabled
        m_retentionPeriod = context.getRetentionPeriod();

        //  Check if the crash recovery folder is enabled
        if (params.getChild("useCrashRecovery") != null)
            m_crashRecovery = true;
    }

    /**
     * Check if the database is online, return the database connection pool status
     *
     * @return boolean
     */
    public boolean isOnline() {

        //  Check the connection pool status
        if (m_connPool != null) {

            //  Check if the connection pool is online
            if (m_connPool.isOnline())
                return true;

            //  Try and open a connection to the database to see if it's back online
            Connection conn = m_connPool.getConnection();
            if (conn != null)
                m_connPool.releaseConnection(conn);

            // Return the latest connection pool status
            return m_connPool.isOnline();
        }

        //  Connection pool is not valid
        return false;
    }

    /**
     * Shutdown the database interface
     *
     * @param context DBDeviceContext
     */
    public void shutdownDatabase(DBDeviceContext context) {

        //	Close the database connection pool
        if (m_connPool != null)
            m_connPool.closePool();
    }

    /**
     * Get the database connection, open a new connection if required
     *
     * @return Connection
     * @exception SQLException SQL error
     */
    protected final Connection getConnection()
            throws SQLException {

        //	Get a database connection
        Connection conn = m_connPool.getConnection();
        if (conn == null)
            throw new SQLException("Failed to get database connection");

        //	Return the database connection
        return conn;
    }

    /**
     * Get the database connection, open a new connection if required
     *
     * @param leaseTime long
     * @return Connection
     * @exception SQLException SQL error
     */
    protected final Connection getConnection(long leaseTime)
            throws SQLException {

        //	Get a database connection
        Connection conn = m_connPool.getConnection(leaseTime);
        if (conn == null)
            throw new SQLException("Failed to get database connection");

        //	Return the database connection
        return conn;
    }

    /**
     * Release a database connection back to the connection pool
     *
     * @param conn Connection
     */
    protected final void releaseConnection(Connection conn) {

        //	Release the connection to the available pool
        m_connPool.releaseConnection(conn);
    }

    /**
     * Access the database connection pool
     *
     * @return DBConnectionPool
     */
    protected final DBConnectionPool getConnectionPool() {
        return m_connPool;
    }

    /**
     * Get the driver class name
     *
     * @return String
     */
    protected final String getDriverName() {
        return m_driver;
    }

    /**
     * Get the connection string
     *
     * @return String
     */
    protected final String getDSNString() {
        return m_dsn;
    }

    /**
     * Return the database user name
     *
     * @return String
     */
    protected final String getUserName() {
        return m_userName;
    }

    /**
     * Return the database password
     *
     * @return String
     */
    protected final String getPassword() {
        return m_password;
    }

    /**
     * Check if the startup wait for database connection timer has been set
     *
     * @return boolean
     */
    public final boolean hasStartupWaitForConnection() { return m_dbWaitSecs > 0; }

    /**
     * Return the startup wait for connection timer value, in seconds
     *
     * @return int
     */
    public final int getStartupWaitForConnection() { return m_dbWaitSecs; }

    /**
     * Return the file system structure table name
     *
     * @return String
     */
    protected final String getFileSysTableName() {
        return m_structTable;
    }

    /**
     * Check if the NTFS streams table name is valid
     *
     * @return boolean
     */
    protected final boolean hasStreamsTableName() {
        return m_streamTable != null ? true : false;
    }

    /**
     * Return the streams table name
     *
     * @return String
     */
    protected final String getStreamsTableName() {
        return m_streamTable;
    }

    /**
     * Check if the retention table name is valid
     *
     * @return boolean
     */
    protected final boolean hasRetentionTableName() {
        return m_retentionTable != null ? true : false;
    }

    /**
     * Return the retention table name
     *
     * @return String
     */
    protected final String getRetentionTableName() {
        return m_retentionTable;
    }

    /**
     * Determine if the retention period is enabled
     *
     * @return boolean
     */
    protected final boolean hasRetentionPeriod() {
        return m_retentionPeriod != -1L ? true : false;
    }

    /**
     * Return the retention period, in milliseconds
     *
     * @return long
     */
    protected final long getRetentionPeriod() {
        return m_retentionPeriod;
    }

    /**
     * Check if the file loader data table name is valid
     *
     * @return boolean
     */
    protected final boolean hasDataTableName() {
        return m_dataTable != null ? true : false;
    }

    /**
     * Return the file loader data table name
     *
     * @return String
     */
    protected final String getDataTableName() {
        return m_dataTable;
    }

    /**
     * Check if the file loader Jar data table name is valid
     *
     * @return boolean
     */
    protected final boolean hasJarDataTableName() {
        return m_jarDataTable != null ? true : false;
    }

    /**
     * Return the file loader Jar data table name
     *
     * @return String
     */
    protected final String getJarDataTableName() {
        return m_jarDataTable;
    }

    /**
     * Check if the file loader queue table name is valid
     *
     * @return boolean
     */
    protected final boolean hasQueueTableName() {
        return m_queueTable != null ? true : false;
    }

    /**
     * Return the file loader queue table name
     *
     * @return String
     */
    protected final String getQueueTableName() {
        return m_queueTable;
    }

    /**
     * Check if the file loader transaction table name is valid
     *
     * @return boolean
     */
    protected final boolean hasTransactionTableName() {
        return m_transactTable != null ? true : false;
    }

    /**
     * Return the file loader transaction table name
     *
     * @return String
     */
    protected final String getTransactionTableName() {
        return m_transactTable;
    }

    /**
     * Check if the fileid/object id mapping table name is valid
     *
     * @return boolean
     */
    protected final boolean hasObjectIdTableName() {
        return m_objectIdTable != null ? true : false;
    }

    /**
     * Return the fileid/object id mapping table name is valid
     *
     * @return String
     */
    protected final String getObjectIdTableName() {
        return m_objectIdTable;
    }

    /**
     * Check if the symbolic links table name is valid
     *
     * @return boolean
     */
    protected final boolean hasSymLinksTableName() {
        return m_symLinkTable != null ? true : false;
    }

    /**
     * Return the symbolic links table name is valid
     *
     * @return String
     */
    protected final String getSymLinksTableName() {
        return m_symLinkTable;
    }

    /**
     * Return the data fragment size to store per BLOB when using the database to store
     * the file data.
     *
     * @return long
     */
    protected final long getDataFragmentSize() {
        return m_dataFragSize;
    }

    /**
     * Check if database interface debug output is enabled
     *
     * @return boolean
     */
    protected final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Check if database interface SQL debug output is enabled
     *
     * @return boolean
     */
    protected final boolean hasSQLDebug() {
        return m_sqlDebug;
    }

    /**
     * Set the DSN string
     *
     * @param dsn String
     */
    protected final void setDSNString(String dsn) {
        m_dsn = dsn;
    }

    /**
     * Set the JDBC driver class
     *
     * @param driverClass String
     */
    protected final void setDriverName(String driverClass) {
        m_driver = driverClass;
    }

    /**
     * Check for special characters within a file/directory name and escape the characters to return
     * a string which can be used in a SQL statement.
     *
     * @param name String
     * @return String
     */
    protected String checkNameForSpecialChars(String name) {

        //	Check for a null string
        if (name == null || name.length() == 0)
            return name;

        //	Check if the file/directory name contains special characters
        int idx = 0;
        boolean specChars = false;

        while (idx < m_specialChars.length() && specChars == false) {

            //	Check if the name contains the current special character
            if (name.indexOf(m_specialChars.charAt(idx++)) != -1)
                specChars = true;
        }

        //	Check if any special characters were found
        if (specChars == false)
            return name;

        //	Escape any special characters within the file/directory name
        StringBuffer nameBuf = new StringBuffer(name.length() * 2);

        for (int i = 0; i < name.length(); i++) {

            //	Get the current character and check if it needs to be escaped
            char curChar = name.charAt(i);

            //	Append the character to the escape string
            if (m_specialChars.indexOf(curChar) != -1)
                nameBuf.append("\\");
            nameBuf.append(curChar);
        }

        //	Return the escaped file/directory name
        return nameBuf.toString();
    }

    /**
     * Create the database connection pool
     *
     * @throws Exception Error creating the connection pool
     */
    protected final void createConnectionPool()
            throws Exception {

        //	Create the connection pool
        m_connPool = new DBConnectionPool(m_driver, m_dsn, m_userName, m_password, m_dbInitConns, m_dbMaxConns);

        // Set the online check interval, if specified
        if (m_onlineCheckInterval != 0)
            m_connPool.setOnlineCheckInterval(m_onlineCheckInterval * 60);

        // Add the database interface as a connection pool event listener
        m_connPool.addConnectionPoolListener(this);
    }

    /**
     * Default implementation of the delete file request, throws an exception indicating that the
     * feature is not implemented.
     *
     * @param fileReq FileRequest
     * @throws DBException Database error
     */
    public void deleteFileRequest(FileRequest fileReq)
            throws DBException {

        //	Indicate that the feature is not implemented
        throw new DBException("Feature not implemented");
    }

    /**
     * Queue a file save request to the pending save queue as the database is offline
     *
     * @param saveReq FileRequest
     */
    protected synchronized final void queueOfflineSaveRequest(FileRequest saveReq) {

        // Check if the offline queue is allocated
        if (m_pendingSaveRequests == null)
            m_pendingSaveRequests = new FileRequestQueue();

        // DEBUG
        if (hasDebug())
            Debug.println("JDBCInterface: Queueing save request " + saveReq);

        // Add the pending file save request
        m_pendingSaveRequests.addRequest(saveReq);
    }

    /**
     * Database online/offline status event
     *
     * @param dbonline boolean
     */
    public void databaseOnlineStatus(boolean dbonline) {

        // DEBUG
        if (hasDebug())
            Debug.println("JDBCInterface: Database connection event, status=" + (dbonline ? "OnLine" : "OffLine"));

        // Set the shared device availabel status depending on the database state
        m_dbCtx.setAvailable(dbonline);

        // If the database is back online then check if there are queued save/delete requests
        if (dbonline == true) {

            //  Check if there are any queued delete file requests
            if (m_dbCtx.hasOfflineFileDeletes()) {

                // Get the list of files to be deleted
                FileRequestQueue deleteList = m_dbCtx.getOfflineFileDeletes(true);

                for (int i = 0; i < deleteList.numberOfRequests(); i++) {

                    //  Get the current delete file request
                    DeleteFileRequest deleteReq = (DeleteFileRequest) deleteList.removeRequestNoWait();

                    //  Delete the file record from the database
                    try {

                        // Check if the delete is for a file or stream
                        if (deleteReq.getStreamId() == 0)
                            deleteFileRecord(-1, deleteReq.getFileId(), m_dbCtx.isTrashCanEnabled());
                        else
                            deleteStreamRecord(deleteReq.getFileId(), deleteReq.getStreamId(), m_dbCtx.isTrashCanEnabled());

                        // Remove the file state for the file/stream
                        m_dbCtx.getStateCache().removeFileState(deleteReq.getFileState().getPath());

                        // DEBUG
                        if (hasDebug())
                            Debug.println("JDBCInterface: Offline delete of file " + deleteReq.getVirtualPath() + ", fid=" + deleteReq.getFileId());
                    }
                    catch (Exception ex) {

                        //  Requeue the delete file request

                        // TODO:
                    }
                }
            }

            //  Check if there are any queued file save requests
            if (m_pendingSaveRequests != null) {

                // Unlink the pending save request list
                FileRequestQueue saveReqQueue = m_pendingSaveRequests;
                m_pendingSaveRequests = null;

                // DEBUG
                if (hasDebug())
                    Debug.println("JDBCInterface: Requeueing pending save requests, count=" + saveReqQueue.numberOfRequests());

                // Queue the save requests
                FileRequest fileReq = null;

                while (saveReqQueue.numberOfRequests() > 0) {

                    // Get a request from the queue and requeue the request to save the data
                    try {

                        // Get the next save request to be requeued
                        fileReq = saveReqQueue.removeRequestNoWait();
                        queueFileRequest(fileReq);

                        // DEBUG
                        if (hasDebug())
                            Debug.println("JDBCInterface: Requeued save " + fileReq);

                        // Update the file size for the file being saved, the close method may not have updated the final file size
                        // due to the database being offline
                        if (fileReq instanceof SingleFileRequest) {

                            //  Get the single file save request details
                            SingleFileRequest singleReq = (SingleFileRequest) fileReq;

                            if (singleReq.hasFileState()) {

                                //  Get the temporary file size
                                File tempFile = new File(singleReq.getTemporaryFile());

                                //  Create the set file information details to set the file size
                                FileInfo fInfo = new FileInfo("", 0L, 0);
                                fInfo.setFileInformationFlags(FileInfo.SetFileSize);
                                fInfo.setFileId(singleReq.getFileId());
                                fInfo.setFileSize(tempFile.length());

                                //  Update the file size in the database
                                setFileInformation(-1, singleReq.getFileId(), fInfo);

                                // DEBUG
                                if (hasDebug())
                                    Debug.println("JDBCInterface: Updated file size for " + singleReq.getVirtualPath() + " size=" + tempFile.length());
                            }
                        }
                    }
                    catch (DBException ex) {

                        // If there was an error the request will go back onto a new in-memory pending queue
                    }
                }
            }
        }
    }

    /**
     * Check if there are pending offline file requests queued
     *
     * @return boolean
     */
    public final boolean hasOfflineFileRequests() {
        if (m_pendingSaveRequests != null && m_pendingSaveRequests.numberOfRequests() > 0)
            return true;
        return false;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    /////////// Database interface methods required for threaded file loading support	///////////
    /////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Default implementation of the queued request check, throws an exception indicating that the
     * feature is not implemented.
     *
     * @param tempFile String
     * @return boolean
     * @throws DBException Database error
     */
    public boolean hasQueuedRequest(String tempFile)
            throws DBException {

        //	Indicate that the feature is not implemented
        throw new DBException("Feature not implemented");
    }

    /**
     * Default implementation of load file requests, throws an exception indicating that the
     * feature is not implemented.
     *
     * @param seqNo    int
     * @param reqType  int
     * @param reqQueue FileRequestQueue
     * @param recLimit int
     * @return int
     * @throws DBException Database error
     */
    public int loadFileRequests(int seqNo, int reqType, FileRequestQueue reqQueue, int recLimit)
            throws DBException {

        //	Indicate that the feature is not implemented
        throw new DBException("Feature not implemented");
    }

    /**
     * Default implementation of queue file request, throws an exception indicating that the
     * feature is not implemented.
     *
     * @param fileReq FileRequest
     * @throws DBException Database error
     */
    public void queueFileRequest(FileRequest fileReq)
            throws DBException {

        //	Indicate that the feature is not implemented
        throw new DBException("Feature not implemented");
    }

    /////////////////////////////////////////////////////////////////////////////////////////////
    /////////// Database interface methods required for threaded file loading        	///////////
    /////////// transaction support                                                   ///////////
    /////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Default implementation of the queue transaction request, throws an exception indicating that the
     * feature is not implemented.
     *
     * @param fileReq FileRequest
     * @throws DBException Database error
     */
    public void queueTransactionRequest(FileRequest fileReq)
            throws DBException {

        //	Indicate that the feature is not implemented
        throw new DBException("Feature not implemented");
    }

    /**
     * Default implementation of load transaction request, throws an exception indicating that the
     * feature is not implemented.
     *
     * @param tranReq MultipleFileRequest
     * @return MultipleFileRequest
     * @throws DBException Database error
     */
    public MultipleFileRequest loadTransactionRequest(MultipleFileRequest tranReq)
            throws DBException {

        //	Indicate that the feature is not implemented
        throw new DBException("Feature not implemented");
    }
}
