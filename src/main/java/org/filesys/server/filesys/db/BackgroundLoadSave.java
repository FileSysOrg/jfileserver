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

import java.util.ArrayList;
import java.util.List;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.FileStatus;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateCache;
import org.filesys.server.filesys.loader.BackgroundFileLoader;
import org.filesys.server.filesys.loader.CachedFileInfo;
import org.filesys.server.filesys.loader.FileRequest;
import org.filesys.server.filesys.loader.FileRequestQueue;
import org.filesys.server.filesys.loader.FileSegment;
import org.filesys.server.filesys.loader.FileSegmentInfo;
import org.filesys.server.filesys.loader.MultipleFileRequest;
import org.filesys.server.filesys.loader.SingleFileRequest;

/**
 * Background Load Save Class
 *
 * <p>
 * Utility class that can be used by FileLoader or DBInterface implementations to provide a worker
 * thread pool to load/save the file data using a queue of file load/save requests.
 *
 * @author gkspencer
 */
public class BackgroundLoadSave {

    // Status codes returned from the load/save worker thread processing
    public final static int StsSuccess  = 0;
    public final static int StsRequeue  = 1;
    public final static int StsError    = 2;

    // Default/minimum/maximum number of worker threads to use
    public static final int DefaultWorkerThreads = 4;
    public static final int MinimumWorkerThreads = 1;
    public static final int MaximumWorkerThreads = 50;

    // Maximum in-memory request queue size and low water mark
    public static final int RequestQueueMaxSize = 5000;
    public static final int RequestQueueMinSize = 50;

    public static final int RequestQueueDefaultSize     = 200;
    public static final int RequestQueueLowWaterMark    = 50;

    // Minimum number of requests that must be in the in-memory queue when a requeue occurs to
    // continue
    // without sleeping
    public static final int RequeueMinSize      = 20;
    public static final long RequeueWaitTime    = 500; // milliseconds

    // Default worker thread prefix
    private static final String DefaultThreadName = "LoadSave_";

    // Attributes attached to the file state
    public static final String DBFileSegmentInfo = "DBFileSegmentInfo";

    // File state timeout values
    public static final long SequentialFileExpire       = 3000L; // milliseconds
    public static final long RequestProcessedExpire     = 3000L; // "
    public static final long RequestQueuedExpire        = 10000L; // "

    // Transaction timeout default, minimum and maximum values
    public static final long DefaultTransactionTimeout = 5000L; // milliseconds
    public static final long MinimumTransactionTimeout = 2000L; // "
    public static final long MaximumTransactionTimeout = 60000L; // "

    // Name, used to prefix worker thread names
    private String m_name;

    // Queue of file requests
    private FileRequestQueue m_readQueue;
    private FileRequestQueue m_writeQueue;

    // Queue loader threads and lock object
    private QueueLoader m_writeLoader;
    private Object m_writeLoaderLock = new Object();

    private QueueLoader m_readLoader;
    private Object m_readLoaderLock = new Object();

    private TransactionQueueLoader m_tranLoader;

    // Maximum in-memory file request size and low water mark
    private int m_maxQueueSize;
    private int m_lowQueueSize;

    // File request worker thread pools
    private ThreadWorker[] m_readThreads;
    private ThreadWorker[] m_writeThreads;

    // Number of worker threads to create for read/write requests
    private int m_readWorkers;
    private int m_writeWorkers;

    // Enable debug output
    private boolean m_debug;

    // Database queue interface used to load/save the file requests
    private DBQueueInterface m_dbQueueInterface;

    // File state cache and default expiry timeout
    private FileStateCache m_stateCache;
    private long m_stateTimeout;

    // Associated file loader called by the worker threads to do the actual file load/save
    private BackgroundFileLoader m_fileLoader;

    /**
     * Thread Worker Inner Class
     */
    protected class ThreadWorker implements Runnable {

        // Worker thread
        private Thread mi_thread;

        // Worker unique id
        private int mi_id;

        // Associated request queue and queue loader
        private FileRequestQueue mi_queue;
        private QueueLoader mi_loader;

        // Shutdown flag
        private boolean mi_shutdown = false;

        /**
         * Class constructor
         *
         * @param name   String
         * @param id     int
         * @param queue  FileRequestQueue
         * @param loader QueueLoader
         */
        public ThreadWorker(String name, int id, FileRequestQueue queue, QueueLoader loader) {
            mi_id = id;
            mi_queue = queue;
            mi_loader = loader;

            mi_thread = new Thread(this);
            mi_thread.setName(name);
            mi_thread.setDaemon(true);
            mi_thread.start();
        }

        /**
         * Request the worker thread to shutdown
         */
        public final void shutdownRequest() {
            mi_shutdown = true;
            try {
                mi_thread.interrupt();
            }
            catch (Exception ex) {
            }
        }

        /**
         * Run the thread
         */
        public void run() {

            // Loop until shutdown
            FileRequest fileReq = null;

            while (mi_shutdown == false) {

                try {

                    // Wait for a file request to be queued
                    fileReq = mi_queue.removeRequest();
                }
                catch (InterruptedException ex) {

                    // Check for shutdown
                    if (mi_shutdown == true)
                        break;
                }

                // If the file request is valid process it
                if (fileReq != null) {

                    // DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("BackgroundLoadSave loader=" + getName() + ", fileReq=" + fileReq + ", queued="
                                + mi_queue.numberOfRequests());

                    // Check if the in-memory file request queue has reached the low water mark, if
                    // so then wakeup the queue loader
                    // thread to load more file request records from the database
                    mi_loader.checkRequestQueue();

                    // Process the file request
                    int reqSts = StsRequeue;

                    try {

                        // Set the thread id of the worker processing the request
                        fileReq.setThreadId(mi_id);

                        // File data load
                        if (fileReq.isType() == FileRequest.LOAD) {

                            // Load the file
                            reqSts = getFileLoader().loadFile(fileReq);
                        } else if (fileReq.isType() == FileRequest.SAVE || fileReq.isType() == FileRequest.TRANSSAVE) {

                            // Save the file
                            reqSts = getFileLoader().storeFile(fileReq);
                        }
                    }
                    catch (Exception ex) {

                        // DEBUG
                        if (Debug.EnableError && hasDebug()) {
                            Debug.println("BackgroundLoadSave exception=" + ex.toString());
                            Debug.println(ex);
                        }
                    }

                    // Check if the request was processed successfully
                    if (reqSts == StsSuccess || reqSts == StsError) {

                        try {

                            // Delete the file request from the queue
                            getDBQueueInterface().deleteFileRequest(fileReq);
                        }
                        catch (DBException ex) {

                            // DEBUG
                            if (Debug.EnableError && hasDebug())
                                Debug.println("BackgroundLoadSave Error: " + ex.toString());
                        }

                        // Reset the associated file state(s) to expire in a short while
                        if (fileReq instanceof MultipleFileRequest) {

                            // Get the multiple file request details
                            MultipleFileRequest multiReq = (MultipleFileRequest) fileReq;

                            // Calculate the expiry time
                            long expireAt = System.currentTimeMillis() + RequestProcessedExpire;

                            // Reset all the associated file states to expire in a short while
                            for (int i = 0; i < multiReq.getNumberOfFiles(); i++) {
                                CachedFileInfo finfo = multiReq.getFileInfo(i);
                                if (finfo.hasFileState())
                                    finfo.getFileState().setExpiryTime(expireAt);
                            }
                        } else {

                            // Reset the associated file state to expire in a short while
                            SingleFileRequest singleReq = (SingleFileRequest) fileReq;
                            if (singleReq.hasFileState())
                                singleReq.getFileState().setExpiryTime(System.currentTimeMillis() + RequestProcessedExpire);
                        }

                        // DEBUG
                        if (Debug.EnableInfo && reqSts == StsError && hasDebug())
                            Debug.println("BackgroundLoadSave Error request=" + fileReq);
                    }

                    // If the file request was not processed requeue it
                    else if (reqSts == StsRequeue) {

                        // DEBUG
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("BackgroundLoadSave ReQueue request=" + fileReq);

                        // Check if we need to pause, if there are only a few requests in the in-memory queue then we will
                        // sleep for a short while so as not to process the requeued request again too quickly
                        if (mi_queue.numberOfRequests() < RequeueMinSize) {

                            // Sleep for a while before requeueing the request
                            try {
                                Thread.sleep(RequeueWaitTime);
                            }
                            catch (Exception ex) {
                            }
                        }

                        // Add the request to the end of the in-memory queue
                        mi_queue.addRequest(fileReq);
                    }
                }
            }

            // DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("BackgroundLoadSave thread=" + mi_thread.getName() + " shutdown");
        }
    }

    ;

    /**
     * Queue Loader Thread Inner Class
     */
    protected class QueueLoader implements Runnable {

        // Queue loader thread
        private Thread mi_thread;

        // Request type to load
        private int mi_loadType;

        // Shutdown flag
        private boolean mi_shutdown = false;

        // Sequence number of the last request loaded
        private int mi_lastSeqNo;

        // File request load in progress flag
        private boolean mi_loading;

        // Associated queue and lock object
        private FileRequestQueue mi_queue;
        private Object mi_lockObj;

        // Flag to indicate new records have been added to the database since last load
        private boolean mi_newRecords = true;
        private int mi_lastQueuedSeq;

        /**
         * Class constructor
         *
         * @param name  String
         * @param type  int
         * @param queue FileRequestQueue
         * @param lock  Object
         */
        public QueueLoader(String name, int type, FileRequestQueue queue, Object lock) {
            mi_loadType = type;
            mi_queue = queue;
            mi_lockObj = lock;

            mi_thread = new Thread(this);
            mi_thread.setName(name);
            mi_thread.setDaemon(true);
            mi_thread.start();
        }

        /**
         * Request the worker thread to shutdown
         */
        public final void shutdownRequest() {
            mi_shutdown = true;
            try {
                mi_thread.interrupt();
            }
            catch (Exception ex) {
            }
        }

        /**
         * Check if there is a file request record load in progress
         *
         * @return boolean
         */
        public final boolean isLoading() {
            return mi_loading;
        }

        /**
         * Notify the queue loader that a new record has been added to the database
         *
         * @param seqNo int
         */
        public final synchronized void notifyNewRecord(int seqNo) {

            // Update the new record flag
            mi_newRecords = true;

            // Update the last queue request sequence number, if higher than the current value
            if (seqNo > mi_lastQueuedSeq)
                mi_lastQueuedSeq = seqNo;

            // Check if the queue loader thread should be woken up to load new records
            synchronized (mi_lockObj) {
                mi_lockObj.notify();
            }
        }

        /**
         * Check the request queue status and wakeup the loader thread if required
         */
        public final void checkRequestQueue() {

            // Check if there is no load active, the queue is below the low water mark and there are
            // new records to load.
            int qSize = mi_queue.numberOfRequests();

            if (isLoading() == false && qSize < getLowQueueSize() && (mi_newRecords == true || qSize == 0)) {
                synchronized (mi_lockObj) {
                    mi_lockObj.notify();
                }
            }
        }

        /**
         * Run the thread
         */
        public void run() {

            // Create a local queue for loading the file request records before passing to the main
            // queue
            FileRequestQueue tempQueue = new FileRequestQueue();

            // Loop until shutdown
            while (mi_shutdown == false) {

                // Wait for a queue load request
                try {

                    // Wait for a queue load request by waiting on the lock object
                    if (mi_queue.numberOfRequests() > 0 || mi_lastQueuedSeq == 0 || mi_lastSeqNo >= mi_lastQueuedSeq) {
                        synchronized (mi_lockObj) {
                            mi_lockObj.wait();
                        }
                    }
                }
                catch (InterruptedException ex) {

                    // Check for shutdown
                    if (mi_shutdown == true)
                        break;
                }

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("BackgroundLoadSave Queue load requested, seqNo=" + mi_lastSeqNo);

                // Load a block of file requests from the queue database
                int loadCnt = 0;

                try {

                    // Indicate that there is a load in progress, clear the temporary queue
                    mi_loading = true;
                    tempQueue.removeAllRequests();

                    // Calculate the number of records to load
                    int recCnt = getMaximumQueueSize() - mi_queue.numberOfRequests();

                    // Load a block of file request records
                    loadCnt = getDBQueueInterface().loadFileRequests(mi_lastSeqNo, mi_loadType, tempQueue, recCnt);

                    // Check if any records were loaded
                    if (loadCnt > 0) {

                        // Post process the loaded file request records
                        while (tempQueue.numberOfRequests() > 0) {

                            // Remove a request from the temporary queue
                            SingleFileRequest fileReq = (SingleFileRequest) tempQueue.removeRequestNoWait();

                            // Set the last loaded sequence number
                            mi_lastSeqNo = fileReq.getSequenceNumber();

                            // Determine the initial status for the file state
                            int fsts = fileReq.isType() == FileRequest.LOAD ? FileSegmentInfo.LoadWait : FileSegmentInfo.SaveWait;

                            // Recreate the file state and associated data for the file
                            FileState fstate = null;

                            try {
                                fstate = createFileStateForRequest(fileReq.getFileId(), fileReq.getTemporaryFile(), fileReq.getVirtualPath(), fsts);
                                fileReq.setFileState(fstate);
                            }
                            catch (Exception ex) {
                                Debug.println(ex);
                            }

                            // Add the file request to the main in-memory queue
                            mi_queue.addRequest(fileReq);
                        }

                        // If we loaded less records than expected clear the new record flag
                        if (loadCnt < recCnt)
                            mi_newRecords = false;

                        // DEBUG
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("BackgroundLoadSave Loaded " + loadCnt + " records from queue db, type "
                                    + (mi_loadType == FileRequest.LOAD ? "Read" : "Write"));
                    }
                }
                catch (DBException ex) {

                    // DEBUG
                    if (hasDebug()) {
                        Debug.println("BackgroundLoadSave Error " + ex.toString());
                        Debug.println("  Last SeqNo=" + mi_lastSeqNo);
                    }

                    // Reset the last loaded sequence number
                    mi_lastSeqNo = 0;
                }

                // Indicate that records are not being loaded
                mi_loading = false;
            }

            // DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("BackgroundLoadSave Queue loader shutdown");
        }
    }

    ;

    /**
     * Transaction Queue Loader Thread Inner Class
     */
    protected class TransactionQueueLoader implements Runnable {

        // Transaction queue loader thread
        private Thread mi_thread;

        // Shutdown flag
        private boolean mi_shutdown = false;

        // File request load in progress flag
        private boolean mi_loading;

        // List of transactions that are ready to load from the database
        private List mi_transList;

        // Associated request queue
        private FileRequestQueue mi_queue;

        /**
         * Class constructor
         *
         * @param name  String
         * @param queue FileRequestQueue
         */
        public TransactionQueueLoader(String name, FileRequestQueue queue) {

            // Save the associated request queue
            mi_queue = queue;

            // Create the transaction name queue
            mi_transList = new ArrayList();

            // Create the thread and start it
            mi_thread = new Thread(this);
            mi_thread.setName(name);
            mi_thread.setDaemon(true);
            mi_thread.start();
        }

        /**
         * Request the worker thread to shutdown
         */
        public final void shutdownRequest() {
            mi_shutdown = true;
            try {
                mi_thread.interrupt();
            }
            catch (Exception ex) {
            }
        }

        /**
         * Add a transaction to the queue, wakeup the transaction loader if required
         *
         * @param name String
         */
        public final void addTransaction(String name) {
            synchronized (mi_transList) {

                // Add the transaction to the queue
                mi_transList.add(name);

                // Wakeup the loader if this is the only request
                if (mi_transList.size() == 1)
                    mi_transList.notify();
            }
        }

        /**
         * Check if there is a transaction file request record load in progress
         *
         * @return boolean
         */
        public final boolean isLoading() {
            return mi_loading;
        }

        /**
         * Run the thread
         */
        public void run() {

            // Loop until shutdown
            while (mi_shutdown == false) {

                // Wait for a queue load request
                try {

                    // Wait for a transaction load request by waiting on the transaction name queue
                    synchronized (mi_transList) {
                        if (mi_transList.size() == 0) {
                            // Debug.println("TransQueueLoader Waiting ...");
                            mi_transList.wait();
                        }
                    }
                }
                catch (InterruptedException ex) {

                    // Check for shutdown
                    if (mi_shutdown == true)
                        break;
                }

                // Loop until all pending transactions have been loaded
                while (mi_transList.size() > 0) {

                    // Get a transaction name from the queue
                    String tranName = null;
                    int tranId = -1;

                    synchronized (mi_transList) {
                        tranName = (String) mi_transList.remove(0);
                    }

                    if (Debug.EnableInfo && mi_transList.size() > 0)
                        Debug.println("TransQueueLoader Processing tranName=" + tranName + ", queued=" + mi_transList.size());

                    // Convert the transaction id
                    try {
                        tranId = Integer.parseInt(tranName);
                    }
                    catch (NumberFormatException ex) {
                        Debug.println(ex);
                        break;
                    }

                    // DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("BackgroundLoadSave Transaction load requested, tran=" + tranId);

                    try {

                        // Indicate that there is a load in progress
                        mi_loading = true;

                        // Create a multi-file request to hold the details of all the files in the
                        // transaction
                        MultipleFileRequest fileReq = new MultipleFileRequest(FileRequest.TRANSSAVE, tranId);

                        // Load the file list for the transaction
                        CachedFileInfo finfo = null;

                        if (getDBQueueInterface().loadTransactionRequest(fileReq) != null) {

                            // Recreate the file state and associated data for each of the files in
                            // the transaction
                            FileState fstate = null;

                            for (int i = 0; i < fileReq.getNumberOfFiles(); i++) {

                                // Get the current file information from the transaction
                                finfo = fileReq.getFileInfo(i);

                                try {
                                    fstate = createFileStateForRequest(finfo.getFileId(), finfo.getTemporaryPath(), finfo
                                            .getVirtualPath(), FileSegmentInfo.SaveWait);
                                    finfo.setFileState(fstate);
                                }
                                catch (Exception ex) {
                                    Debug.println(ex);
                                }
                            }

                            // Add the request to the file request queue
                            mi_queue.addRequest(fileReq);

                            // DEBUG
                            if (Debug.EnableInfo && hasDebug())
                                Debug.println("BackgroundLoadSave Loaded transaction " + fileReq);
                        }
                    }
                    catch (DBException ex) {

                        // DEBUG
                        if (Debug.EnableError && hasDebug())
                            Debug.println(ex);
                    }

                    // Indicate request loading completed
                    mi_loading = false;
                }
            }

            // DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("BackgroundLoadSave Transaction queue loader shutdown");
        }
    }

    ;

    /**
     * Class constructor
     *
     * @param dbQueue    DBQueueInterface
     * @param stateCache FileStateCache
     * @param bgLoader   BackgroundFileLoader
     */
    public BackgroundLoadSave(DBQueueInterface dbQueue, FileStateCache stateCache, BackgroundFileLoader bgLoader) {

        // Save the database queue, state cache and background loader details
        m_dbQueueInterface = dbQueue;
        m_stateCache = stateCache;
        m_fileLoader = bgLoader;

        // Create the file request queues
        m_readQueue = new FileRequestQueue();
        m_writeQueue = new FileRequestQueue();

        // Set the in-memory queue size and low water mark
        m_maxQueueSize = RequestQueueDefaultSize;
        m_lowQueueSize = RequestQueueLowWaterMark;

        // Set the default worker thread prefix
        setName(DefaultThreadName);
    }

    /**
     * Class constructor
     *
     * @param name       String
     * @param dbQueue    DBQueueInterface
     * @param stateCache FileStateCache
     * @param bgLoader   BackgroundFileLoader
     */
    public BackgroundLoadSave(String name, DBQueueInterface dbQueue, FileStateCache stateCache, BackgroundFileLoader bgLoader) {

        // Save the database queue, state cache and background loader details
        m_dbQueueInterface = dbQueue;
        m_stateCache = stateCache;
        m_fileLoader = bgLoader;

        // Create the file request queues
        m_readQueue = new FileRequestQueue();
        m_writeQueue = new FileRequestQueue();

        // Set the in-memory queue size and low water mark
        m_maxQueueSize = RequestQueueDefaultSize;
        m_lowQueueSize = RequestQueueLowWaterMark;

        // Set the worker thread prefix
        setName(name);
    }

    /**
     * Start the background load/save thread pool
     *
     * @param recCnt int
     */
    public final void startThreads(int recCnt) {

        // Create the read/write queue loaders
        m_readLoader = new QueueLoader("ReadQueueLoader", FileRequest.LOAD, m_readQueue, m_readLoaderLock);
        m_writeLoader = new QueueLoader("WriteQueueLoader", FileRequest.SAVE, m_writeQueue, m_writeLoaderLock);

        // Create the read thread pool
        m_readThreads = new ThreadWorker[m_readWorkers];

        for (int i = 0; i < m_readWorkers; i++)
            m_readThreads[i] = new ThreadWorker(getName() + "_RD_" + (i + 1), i, m_readQueue, m_readLoader);

        // Create the write thread pool
        m_writeThreads = new ThreadWorker[m_writeWorkers];

        for (int i = 0; i < m_writeWorkers; i++)
            m_writeThreads[i] = new ThreadWorker(getName() + "_WR_" + (i + 1), i, m_writeQueue, m_writeLoader);

        // DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("FileLoader threadPool read=" + m_readWorkers + ", write=" + m_writeWorkers);

        // If there are recovered files queued for saving then kick the queue loader
        if (recCnt > 0)
            m_writeLoader.notifyNewRecord(1);
    }

    /**
     * Enable transactions, start the transaction loader thread
     */
    public final void enableTransactions() {

        // Check if the transaction loader thread is valid
        if (m_tranLoader == null)
            m_tranLoader = new TransactionQueueLoader(getName() + "_TranLdr", m_writeQueue);
    }

    /**
     * Shutdown the background load/save thread pool
     */
    public final void shutdownThreads() {

        // Shutdown the worker threads
        if (m_readThreads != null) {
            for (int i = 0; i < m_readThreads.length; i++)
                m_readThreads[i].shutdownRequest();
        }

        if (m_writeThreads != null) {
            for (int i = 0; i < m_writeThreads.length; i++)
                m_writeThreads[i].shutdownRequest();
        }

        // Shutdown the queue loaders
        m_readLoader.shutdownRequest();
        m_readLoader = null;

        m_writeLoader.shutdownRequest();
        m_writeLoader = null;

        // Shutdown the transaction loader, if active
        if (m_tranLoader != null) {
            m_tranLoader.shutdownRequest();
            m_tranLoader = null;
        }
    }

    /**
     * Request file data to be loaded/saved
     *
     * @param req FileRequest
     */
    public void queueFileRequest(FileRequest req) {

        // Make sure the associated file state stays in memory for a short time, if the queue is
        // small the request may get processed soon.
        if (req instanceof SingleFileRequest) {

            // Get the request details
            SingleFileRequest fileReq = (SingleFileRequest) req;
            if (fileReq.hasFileState())
                fileReq.getFileState().setExpiryTime(FileState.NoTimeout);

            try {

                // Write a file request record to the queue database
                getDBQueueInterface().queueFileRequest(fileReq);

                // Check if the request is part of a transaction, or a standalone request
                if (fileReq.isTransaction() == false) {

                    // Check if the in-memory queue is empty, if so then wakeup the queue loader to
                    // load the new request
                    if (fileReq.isType() == FileRequest.LOAD)
                        m_readLoader.notifyNewRecord(fileReq.getSequenceNumber());
                    else
                        m_writeLoader.notifyNewRecord(fileReq.getSequenceNumber());
                } else {

                    //
                    // Check if this request is the last file in the current transaction, if so then
                    // the transaction is ready to be processed
                    if (fileReq.isLastTransactionFile())
                        m_tranLoader.addTransaction("" + fileReq.getTransactionId());
                }
            }
            catch (DBException ex) {

                // DEBUG
                if (Debug.EnableError && hasDebug())
                    Debug.println(ex);
            }
        }
    }

    /**
     * Flush the current pending transaction request
     *
     * @param tranId int
     */
    public final void flushTransaction(int tranId) {

        // Queue the specified transaction for loading/processing
        m_tranLoader.addTransaction("" + tranId);
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Return the database queue interface
     *
     * @return DBQueueInterface
     */
    public final DBQueueInterface getDBQueueInterface() {
        return m_dbQueueInterface;
    }

    /**
     * Return the file loader interface
     *
     * @return BackgroundFileLoader
     */
    public final BackgroundFileLoader getFileLoader() {
        return m_fileLoader;
    }

    /**
     * Return the default file state timeout
     *
     * @return int
     */
    public final long getFileStateTimeout() {
        return m_stateTimeout;
    }

    /**
     * Return the maximum in-memory file request queue size
     *
     * @return int
     */
    public final int getMaximumQueueSize() {
        return m_maxQueueSize;
    }

    /**
     * Return the in-memory file request queue low water mark level
     *
     * @return int
     */
    public final int getLowQueueSize() {
        return m_lowQueueSize;
    }

    /**
     * Return the worker thread prefix
     *
     * @return String
     */
    public final String getName() {
        return m_name;
    }

    /**
     * Return the read request queue
     *
     * @return FileRequestQueue
     */
    protected final FileRequestQueue getReadQueue() {
        return m_readQueue;
    }

    /**
     * Return the read loader
     *
     * @return QueueLoader
     */
    public final QueueLoader getReadLoader() {
        return m_readLoader;
    }

    /**
     * Return the write request queue
     *
     * @return FileRequestQueue
     */
    protected final FileRequestQueue getWriteQueue() {
        return m_writeQueue;
    }

    /**
     * Return the write loader
     *
     * @return QueueLoader
     */
    public final QueueLoader getWriteLoader() {
        return m_writeLoader;
    }

    /**
     * Return the number of read worker threads
     *
     * @return int
     */
    public final int getReadWorkers() {
        return m_readWorkers;
    }

    /**
     * Return the number of write worker threads
     *
     * @return int
     */
    public final int getWriteWorkers() {
        return m_writeWorkers;
    }

    /**
     * Return the file state cache
     *
     * @return FileStateCache
     */
    protected final FileStateCache getStateCache() {
        return m_stateCache;
    }

    /**
     * Set the worker thread name prefix
     *
     * @param name String
     */
    public final void setName(String name) {
        m_name = name;
    }

    /**
     * Enable/disable debug output
     *
     * @param dbg boolean
     */
    public final void setDebug(boolean dbg) {
        m_debug = dbg;
    }

    /**
     * Set the maximum in-memory file request queue size
     *
     * @param qsize int
     */
    public final void setMaximumQueueSize(int qsize) {
        m_maxQueueSize = qsize;
    }

    /**
     * Set the in-memory file request queue low water mark level
     *
     * @param lowqSize int
     */
    public final void setLowQueueSize(int lowqSize) {
        m_lowQueueSize = lowqSize;
    }

    /**
     * Set the number of read worker threads
     *
     * @param rdWorkers int
     */
    public final void setReadWorkers(int rdWorkers) {
        m_readWorkers = rdWorkers;
    }

    /**
     * Set the number of write worker threads
     *
     * @param wrWorkers int
     */
    public final void setWriteWorkers(int wrWorkers) {
        m_writeWorkers = wrWorkers;
    }

    /**
     * Re-create, or attach, a file request to the file state.
     *
     * @param fid      int
     * @param tempPath String
     * @param virtPath String
     * @param sts      int
     * @return FileState
     */
    protected final FileState createFileStateForRequest(int fid, String tempPath, String virtPath, int sts) {

        // Find, or create, the file state for the file/directory
        FileState state = m_stateCache.findFileState(virtPath, false);

        if (state == null) {

            // Create a new file state for the temporary file
            state = m_stateCache.findFileState(virtPath, true);

            synchronized (state) {

                // Prevent the file state from expiring whilst the request is queued against it
                state.setExpiryTime(FileState.NoTimeout);

                // Indicate that the file exists, set the unique file id
                state.setFileStatus(FileStatus.FileExists);
                state.setFileId(fid);

                // Check if the file segment has been attached to the file state
                FileSegmentInfo fileSegInfo = (FileSegmentInfo) state.findAttribute(DBFileSegmentInfo);
                FileSegment fileSeg = null;

                if (fileSegInfo == null) {

                    // Create a new file segment
                    fileSegInfo = new FileSegmentInfo();
                    fileSegInfo.setTemporaryFile(tempPath);

                    fileSeg = new FileSegment(fileSegInfo, true);
                    fileSeg.setStatus(sts, true);

                    // Add the segment to the file state cache
                    state.addAttribute(DBFileSegmentInfo, fileSegInfo);
                } else {

                    // Make sure the file segment indicates its part of a queued request
                    fileSeg = new FileSegment(fileSegInfo, true);
                    fileSeg.setStatus(sts, true);
                }
            }
        }

        // Return the file state
        return state;
    }
}
