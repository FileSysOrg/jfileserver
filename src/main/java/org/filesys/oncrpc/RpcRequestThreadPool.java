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

package org.filesys.oncrpc;

import org.filesys.debug.Debug;

/**
 * ONC/RPC Request Thread Pool Class
 *
 * <p>Processes RPC requests using a pool of worker threads.
 *
 * @author gkspencer
 */
public class RpcRequestThreadPool {

    //	Default/minimum/maximum number of worker threads to use
    public static final int DefaultWorkerThreads = 8;
    public static final int MinimumWorkerThreads = 4;
    public static final int MaximumWorkerThreads = 50;

    //	Queue of RPC requests
    private RpcRequestQueue m_queue;

    //	Worker threads
    private ThreadWorker[] m_workers;

    //	RPC dispatcher
    private RpcProcessor m_rpcProcessor;

    //	Debug enable flag
    private static boolean m_debug = true;

    /**
     * Thread Worker Inner Class
     */
    protected class ThreadWorker implements Runnable {

        //	Worker thread
        private Thread mi_thread;

        //	Worker unique id
        private int mi_id;

        //	Shutdown flag
        private boolean mi_shutdown = false;

        /**
         * Class constructor
         *
         * @param name String
         * @param id   int
         */
        public ThreadWorker(String name, int id) {

            //	Save the thread id
            mi_id = id;

            //	Create the worker thread
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
            RpcPacket rpc = null;
            RpcPacket response = null;

            while (mi_shutdown == false) {

                try {

                    //	Wait for an RPC request to be queued
                    rpc = m_queue.removeRequest();
                }
                catch (InterruptedException ex) {

                    //	Check for shutdown
                    if (mi_shutdown == true)
                        break;
                }

                //	If the request is valid process it
                if (rpc != null) {

                    try {

                        //	Process the request
                        response = m_rpcProcessor.processRpc(rpc);
                        if (response != null)
                            response.getPacketHandler().sendRpcResponse(response);
                    }
                    catch (Throwable ex) {

                        //	Do not display errors if shutting down
                        if (mi_shutdown == false) {
                            Debug.println("Worker " + Thread.currentThread().getName() + ":");
                            Debug.println(ex);
                        }
                    }
                    finally {

                        //	Release the RPC packet(s) back to the packet pool
                        if (rpc.getClientProtocol() == Rpc.TCP && rpc.isAllocatedFromPool())
                            rpc.getOwnerPacketPool().releasePacket(rpc);

                        if (response != null && response.getClientProtocol() == Rpc.TCP &&
                                response.getBuffer() != rpc.getBuffer() && response.isAllocatedFromPool())
                            response.getOwnerPacketPool().releasePacket(response);

                    }
                }
            }
        }
    }

    ;

    /**
     * Class constructor
     *
     * @param threadName String
     * @param rpcServer  RpcProcessor
     */
    public RpcRequestThreadPool(String threadName, RpcProcessor rpcServer) {
        this(threadName, DefaultWorkerThreads, rpcServer);
    }

    /**
     * Class constructor
     *
     * @param threadName String
     * @param poolSize   int
     * @param rpcServer  RpcProcessor
     */
    public RpcRequestThreadPool(String threadName, int poolSize, RpcProcessor rpcServer) {

        //	Save the RPC handler
        m_rpcProcessor = rpcServer;

        //	Create the request queue
        m_queue = new RpcRequestQueue();

        //	Check that we have at least minimum worker threads
        if (poolSize < MinimumWorkerThreads)
            poolSize = MinimumWorkerThreads;

        //	Create the worker threads
        m_workers = new ThreadWorker[poolSize];

        for (int i = 0; i < m_workers.length; i++)
            m_workers[i] = new ThreadWorker(threadName + (i + 1), i);
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final static boolean hasDebug() {
        return m_debug;
    }

    /**
     * Return the number of requests in the queue
     *
     * @return int
     */
    public final int getNumberOfRequests() {
        return m_queue.numberOfRequests();
    }

    /**
     * Queue an RPC request to the thread pool for processing
     *
     * @param pkt RpcPacket
     */
    public final void queueRpcRequest(RpcPacket pkt) {
        m_queue.addRequest(pkt);
    }

    /**
     * Shutdown the thread pool and release all resources
     */
    public void shutdownThreadPool() {

        //	Shutdown the worker threads
        if (m_workers != null) {
            for (int i = 0; i < m_workers.length; i++)
                m_workers[i].shutdownRequest();
        }
    }
}
