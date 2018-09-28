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

import org.filesys.debug.Debug;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateCache;
import org.filesys.server.filesys.loader.BackgroundFileLoader;
import org.filesys.server.filesys.loader.CachedFileInfo;
import org.filesys.server.filesys.loader.FileRequest;
import org.filesys.server.filesys.loader.FileRequestQueue;
import org.filesys.server.filesys.loader.MultipleFileRequest;
import org.filesys.server.filesys.loader.SingleFileRequest;


/**
 * Background Load Save Class
 *
 * <p>Utility class that can be used by FileLoader or DBInterface implementations to provide a worker thread pool
 * to load/save the file data using a queue of file load/save requests.
 *
 * @author gkspencer
 */
public class MemoryBackgroundLoadSave {

    //	Status codes returned from the load/save worker thread processing
    public final static int StsSuccess  = 0;
    public final static int StsRequeue  = 1;
    public final static int StsError    = 2;

    //	Default/minimum/maximum number of worker threads to use
    public static final int DefaultWorkerThreads = 4;
    public static final int MinimumWorkerThreads = 1;
    public static final int MaximumWorkerThreads = 50;

    //	Maximum in-memory request queue size and low water mark
    public static final int RequestQueueMaxSize = 5000;
    public static final int RequestQueueMinSize = 50;

    public static final int RequestQueueDefaultSize     = 200;
    public static final int RequestQueueLowWaterMark    = 50;

    //	Minimum number of requests that must be in the in-memory queue when a requeue occurs to continue
    //	without sleeping
    public static final int RequeueMinSize      = 20;
    public static final long RequeueWaitTime    = 500;    //	milliseconds

    //	Default worker thread prefix
    private static final String DefaultThreadName = "MemLoadSave_";

    //	Attributes attached to the file state
    public static final String DBFileSegmentInfo = "DBFileSegmentInfo";

    //	File state timeout values
    public static final long SequentialFileExpire   = 3000L;        //	milliseconds
    public static final long RequestProcessedExpire = 3000L;        //	     "
    public static final long RequestQueuedExpire    = 10000L;       //	     "

    //	Transaction timeout default, minimum and maximum values
    public static final long DefaultTransactionTimeout = 5000L;     //	milliseconds
    public static final long MinimumTransactionTimeout = 2000L;     //	     "
    public static final long MaximumTransactionTimeout = 60000L;    //	     "

    //	Name, used to prefix worker thread names
    private String m_name;

    //	Queue of file requests
    private FileRequestQueue m_readQueue;
    private FileRequestQueue m_writeQueue;

    //	Maximum in-memory file request size and low water mark
    private int m_maxQueueSize;
    private int m_lowQueueSize;

    //	File request worker thread pools
    private ThreadWorker[] m_readThreads;
    private ThreadWorker[] m_writeThreads;

    //	Number of worker threads to create for read/write requests
    private int m_readWorkers;
    private int m_writeWorkers;

    //	Enable debug output
    private boolean m_debug;

    //	File state cache and default expiry timeout
    private FileStateCache m_stateCache;
    private long m_stateTimeout;

    //	Associated file loader called by the worker threads to do the actual file load/save
    private BackgroundFileLoader m_fileLoader;

    /**
     * Thread Worker Inner Class
     */
    protected class ThreadWorker implements Runnable {

        //	Worker thread
        private Thread mi_thread;

        //	Worker unique id
        private int mi_id;

        //	Associated request queue
        private FileRequestQueue mi_queue;

        //	Shutdown flag
        private boolean mi_shutdown = false;

        /**
         * Class constructor
         *
         * @param name  String
         * @param id    int
         * @param queue FileRequestQueue
         */
        public ThreadWorker(String name, int id, FileRequestQueue queue) {
            mi_id = id;
            mi_queue = queue;

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

            //	Loop until shutdown
            FileRequest fileReq = null;

            while (mi_shutdown == false) {

                try {

                    //	Wait for a file request to be queued
                    fileReq = mi_queue.removeRequest();
                }
                catch (InterruptedException ex) {

                    //	Check for shutdown
                    if (mi_shutdown == true)
                        break;
                }

                //	If the file request is valid process it
                if (fileReq != null) {

                    //	DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("BackgroundLoadSave loader=" + getName() + ", fileReq=" + fileReq + ", queued=" + mi_queue.numberOfRequests());

                    //	Process the file request
                    int reqSts = StsRequeue;

                    try {

                        //	Set the thread id of the worker processing the request
                        fileReq.setThreadId(mi_id);

                        //	File data load
                        if (fileReq.isType() == FileRequest.LOAD) {

                            //	Load the file
                            reqSts = getFileLoader().loadFile(fileReq);
                        } else if (fileReq.isType() == FileRequest.SAVE || fileReq.isType() == FileRequest.TRANSSAVE) {

                            //	Save the file
                            reqSts = getFileLoader().storeFile(fileReq);
                        }
                    }
                    catch (Exception ex) {

                        //	DEBUG
                        if (Debug.EnableError && hasDebug()) {
                            Debug.println("BackgroundLoadSave exception=" + ex.toString());
                            Debug.println(ex);
                        }
                    }

                    //	Check if the request was processed successfully
                    if (reqSts == StsSuccess || reqSts == StsError) {

                        //	Reset the associated file state(s) to expire in a short while
                        if (fileReq instanceof MultipleFileRequest) {

                            //	Get the multiple file request details
                            MultipleFileRequest multiReq = (MultipleFileRequest) fileReq;

                            //	Calculate the expiry time
                            long expireAt = System.currentTimeMillis() + RequestProcessedExpire;

                            //	Reset all the associated file states to expire in a short while
                            for (int i = 0; i < multiReq.getNumberOfFiles(); i++) {
                                CachedFileInfo finfo = multiReq.getFileInfo(i);
                                if (finfo.hasFileState())
                                    finfo.getFileState().setExpiryTime(expireAt);
                            }
                        } else {

                            //	Reset the associated file state to expire in a short while
                            SingleFileRequest singleReq = (SingleFileRequest) fileReq;
                            if (singleReq.hasFileState())
                                singleReq.getFileState().setExpiryTime(System.currentTimeMillis() + RequestProcessedExpire);
                        }

                        //	DEBUG
                        if (Debug.EnableInfo && reqSts == StsError && hasDebug())
                            Debug.println("BackgroundLoadSave Error request=" + fileReq);
                    }

                    //	If the file request was not processed requeue it
                    else if (reqSts == StsRequeue) {

                        //	DEBUG
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("BackgroundLoadSave ReQueue request=" + fileReq);

                        //	Check if we need to pause, if there are only a few requests in the in-memory queue then we will
                        //	sleep for a short while so as not to process the requeued request again too quickly
                        if (mi_queue.numberOfRequests() < RequeueMinSize) {

                            //	Sleep for a while before requeueing the request
                            try {
                                Thread.sleep(RequeueWaitTime);
                            }
                            catch (Exception ex) {
                            }
                        }

                        //	Add the request to the end of the in-memory queue
                        mi_queue.addRequest(fileReq);
                    }
                }
            }

            //	DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("BackgroundLoadSave thread=" + mi_thread.getName() + " shutdown");
        }
    }

    ;

    /**
     * Class constructor
     *
     * @param stateCache FileStateCache
     * @param bgLoader   BackgroundFileLoader
     */
    public MemoryBackgroundLoadSave(FileStateCache stateCache, BackgroundFileLoader bgLoader) {

        //	Save the state cache and background loader details
        m_stateCache = stateCache;
        m_fileLoader = bgLoader;

        //	Create the file request queues
        m_readQueue = new FileRequestQueue();
        m_writeQueue = new FileRequestQueue();

        //	Set the in-memory queue size and low water mark
        m_maxQueueSize = RequestQueueDefaultSize;
        m_lowQueueSize = RequestQueueLowWaterMark;

        //	Set the default worker thread prefix
        setName(DefaultThreadName);
    }

    /**
     * Class constructor
     *
     * @param name       String
     * @param stateCache FileStateCache
     * @param bgLoader   BackgroundFileLoader
     */
    public MemoryBackgroundLoadSave(String name, FileStateCache stateCache, BackgroundFileLoader bgLoader) {

        //	Save the state cache and background loader details
        m_stateCache = stateCache;
        m_fileLoader = bgLoader;

        //	Create the file request queues
        m_readQueue = new FileRequestQueue();
        m_writeQueue = new FileRequestQueue();

        //	Set the in-memory queue size and low water mark
        m_maxQueueSize = RequestQueueDefaultSize;
        m_lowQueueSize = RequestQueueLowWaterMark;

        //	Set the worker thread prefix
        setName(name);
    }

    /**
     * Start the background load/save thread pool
     */
    public final void startThreads() {

        //	Create the read thread pool
        m_readThreads = new ThreadWorker[m_readWorkers];

        for (int i = 0; i < m_readWorkers; i++)
            m_readThreads[i] = new ThreadWorker(getName() + "_RD_" + (i + 1), i, m_readQueue);

        //	Create the write thread pool
        m_writeThreads = new ThreadWorker[m_writeWorkers];

        for (int i = 0; i < m_writeWorkers; i++)
            m_writeThreads[i] = new ThreadWorker(getName() + "_WR_" + (i + 1), i, m_writeQueue);

        //	DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("FileLoader threadPool read=" + m_readWorkers + ", write=" + m_writeWorkers);
    }

    /**
     * Shutdown the background load/save thread pool
     */
    public final void shutdownThreads() {

        //	Shutdown the worker threads
        if (m_readThreads != null) {
            for (int i = 0; i < m_readThreads.length; i++)
                m_readThreads[i].shutdownRequest();
        }

        if (m_writeThreads != null) {
            for (int i = 0; i < m_writeThreads.length; i++)
                m_writeThreads[i].shutdownRequest();
        }
    }

    /**
     * Request file data to be loaded/saved
     *
     * @param req FileRequest
     */
    public void queueFileRequest(FileRequest req) {

        //	Make sure the associated file state stays in memory for a short time, if the queue is small
        //	the request may get processed soon.
        if (req instanceof SingleFileRequest) {

            //	Get the request details
            SingleFileRequest fileReq = (SingleFileRequest) req;
            if (fileReq.hasFileState()) {

                //	Lock the file state so it does not get expired during the load/save
                fileReq.getFileState().setExpiryTime(FileState.NoTimeout);
            }

            //	Check if the request is a load or save
            if (fileReq.isType() == FileRequest.LOAD)
                m_readQueue.addRequest(fileReq);
            else
                m_writeQueue.addRequest(fileReq);
        }
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
     * Return the write request queue
     *
     * @return FileRequestQueue
     */
    protected final FileRequestQueue getWriteQueue() {
        return m_writeQueue;
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
}
