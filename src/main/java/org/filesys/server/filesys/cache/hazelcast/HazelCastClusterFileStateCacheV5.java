/*
 * Copyright (C) 2018 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.server.filesys.cache.hazelcast;

import com.hazelcast.core.*;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryEvictedListener;
import com.hazelcast.map.listener.EntryRemovedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;
import com.hazelcast.topic.MessageListener;
import org.filesys.server.filesys.*;
import org.filesys.server.filesys.cache.cluster.*;
import org.filesys.server.locking.OplockOwner;
import org.filesys.smb.OpLockType;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * HazelCast Clustered File State Cache Class
 *
 * @author gkspencer
 */
public class HazelCastClusterFileStateCacheV5 extends HazelCastClusterFileStateCache implements
        EntryAddedListener<String, HazelCastClusterFileState>, EntryUpdatedListener<String, HazelCastClusterFileState>,
        EntryRemovedListener<String, HazelCastClusterFileState>, EntryEvictedListener<String, HazelCastClusterFileState>,
        MessageListener<ClusterMessage> {

    /**
     * Class constructor
     */
    public HazelCastClusterFileStateCacheV5() {

    }

    /**
     * Start the cluster
     *
     * @throws Exception Failed to start the cluster
     */
    public void startCluster()
            throws Exception {

        super.startCluster();

        // Signal that the cluster cache is running, this will mark the filesystem as available
        if (m_stateCache != null && m_clusterTopic != null) {

            // Add a listener to receive cluster cache entry events
            m_stateCache.addEntryListener(this, false);

            // Add a listener to receive cluster messages via the topic
            m_clusterTopic.addMessageListener(this);

            // Indicate that the cluster is running
            getStateCache().clusterRunning();
        }
    }

    /**
     * Execute a rename file state
     *
     * @param oldPath String
     * @param newPath String
     * @param isDir boolean
     * @return boolean
     * @exception InterruptedException Execution interrupted
     * @exception ExecutionException Execution error
     */
    public boolean executeRenameFileState( String oldPath, String newPath, boolean isDir)
        throws InterruptedException, ExecutionException {

        // Rename the state via a remote call to the node that owns the file state
        IExecutorService execService = m_hazelCastInstance.getExecutorService( ExecutorName);
        Callable<Boolean> callable = new RenameStateTask(getMapName(), oldPath, newPath, isDir, hasTaskDebug(), hasTaskTiming());

        Future<Boolean> renameStateTask = execService.submitToKeyOwner( callable, oldPath);

        return renameStateTask.get();
    }

    /**
     * Execute adding an oplock
     *
     * @param path String
     * @param remoteOpLock RemoteOpLockDetails
     * @return boolean
     * @exception InterruptedException Execution interrupted
     * @exception ExecutionException Execution error
     */
    public boolean executeAddOpLock( String path, RemoteOpLockDetails remoteOpLock)
        throws InterruptedException, ExecutionException {

        // Add the oplock via a remote call to the node that owns the file state
        IExecutorService execService = m_hazelCastInstance.getExecutorService( ExecutorName);
        Callable<Boolean> callable = new AddOpLockTask(getMapName(), path, remoteOpLock, hasTaskDebug(), hasTaskTiming());

        Future<Boolean> addOpLockTask = execService.submitToKeyOwner( callable, path);

        return addOpLockTask.get();
    }

    /**
     * Execute removing an oplock owner
     * 
     * @param path String
     * @param owner OplockOwner
     * @return ClusterFileState
     * @exception InterruptedException Execution interrupted
     * @exception ExecutionException Execution error
     */
    public ClusterFileState executeRemoveOplockOwner( String path, OplockOwner owner)
        throws InterruptedException, ExecutionException {

        // Remove the oplock owner using a remote call to the node that owns the file state
        IExecutorService execService = m_hazelCastInstance.getExecutorService( ExecutorName);
        Callable<HazelCastClusterFileState> callable = new RemoveOplockOwnerTask(getMapName(), path, owner, hasTaskDebug(), hasTaskTiming());

        Future<HazelCastClusterFileState> removeOpLockOwnerTask = execService.submitToKeyOwner( callable, path);

        return removeOpLockOwnerTask.get();
    }

    /**
     * Execute clear oplock
     *
     * @param path String
     * @exception InterruptedException Execution interrupted
     * @exception ExecutionException Execution error
     */
    public void executeClearOpLock( String path)
        throws InterruptedException, ExecutionException {

        // Remove the oplock using a remote call to the node that owns the file state
        IExecutorService execService = m_hazelCastInstance.getExecutorService( ExecutorName);
        Callable<Boolean> callable = new RemoveOpLockTask(getMapName(), path, hasTaskDebug(), hasTaskTiming());

        Future<Boolean> removeOpLockTask = execService.submitToKeyOwner( callable, path);

        removeOpLockTask.get();
    }

    /**
     * Execute an add lock
     *
     * @param path String
     * @param lock ClusterFileLock
     * @return ClusterFileState
     * @exception InterruptedException Execution interrupted
     * @exception ExecutionException Execution error
     */
    public ClusterFileState executeAddLock( String path, ClusterFileLock lock)
        throws InterruptedException, ExecutionException {

        // Add the oplock via a remote call to the node that owns the file state
        IExecutorService execService = m_hazelCastInstance.getExecutorService( ExecutorName);
        Callable<HazelCastClusterFileState> callable = new AddFileByteLockTask(getMapName(), path, lock,
                hasDebugLevel(Dbg.BYTELOCK), hasTaskTiming());

        Future<HazelCastClusterFileState> addLockTask = execService.submitToKeyOwner( callable, path);

        return addLockTask.get();
    }

    /**
     * Execute a remove lock
     *
     * @param path String
     * @param lock ClusterFileLock
     * @return ClusterFileState
     * @exception InterruptedException Execution interrupted
     * @exception ExecutionException Execution error
     */
    public ClusterFileState executeRemoveLock( String path, ClusterFileLock lock)
        throws InterruptedException, ExecutionException {

        // Remove the oplock via a remote call to the node that owns the file state
        IExecutorService execService = m_hazelCastInstance.getExecutorService( ExecutorName);
        Callable<HazelCastClusterFileState> callable = new RemoveFileByteLockTask(getMapName(), path, lock,
                hasDebugLevel(Dbg.BYTELOCK), hasTaskTiming());

        Future<HazelCastClusterFileState> removeLockTask = execService.submitToKeyOwner( callable, path);

        return removeLockTask.get();
    }

    /**
     * Execute an oplock change type
     *
     * @param path String
     * @param newTyp OpLockType
     * @return OpLockType
     * @exception InterruptedException Execution interrupted
     * @exception ExecutionException Execution error
     */
    public OpLockType executeChangeOpLockType( String path, OpLockType newTyp)
        throws InterruptedException, ExecutionException {

        // Run the file access checks via the node that owns the file state
        IExecutorService execService = m_hazelCastInstance.getExecutorService( ExecutorName);
        Callable<Integer> callable = new ChangeOpLockTypeTask(getMapName(), path, newTyp, hasTaskDebug(), hasTaskTiming());

        Future<Integer> changeOpLockTask = execService.submitToKeyOwner( callable, path);

        return OpLockType.fromInt(changeOpLockTask.get());
    }

    /**
     * Execute a grant file access
     *
     * @param path String
     * @param params GrantAccessParams
     * @return GrantAccessResponse
     * @exception InterruptedException Execution interrupted
     * @exception ExecutionException Execution error
     */
    public GrantAccessResponse executeGrantFileAccess( String path, GrantAccessParams params)
        throws InterruptedException, ExecutionException {

        // Run the file access checks via the node that owns the file state
        IExecutorService execService = m_hazelCastInstance.getExecutorService( ExecutorName);
        Callable<GrantAccessResponse> callable = new GrantFileAccessTask(getMapName(), path, params, hasTaskDebug(), hasTaskTiming());

        Future<GrantAccessResponse> grantAccessTask = execService.submitToKeyOwner( callable, path);

        return (GrantAccessResponse) grantAccessTask.get();
    }

    /**
     * Execute release file access
     *
     * @param path String
     * @param token HazelCastAccessToken
     * @return int
     * @exception InterruptedException Execution interrupted
     * @exception ExecutionException Execution error
     */
    public int executeReleaseFileAccess( String path, HazelCastAccessToken token)
        throws InterruptedException, ExecutionException {

        // Run the file access checks via the node that owns the file state
        IExecutorService execService = m_hazelCastInstance.getExecutorService( ExecutorName);
        Callable<Integer> callable = new ReleaseFileAccessTask(getMapName(), path, token, getTopicName(),
                hasDebugLevel(Dbg.FILEACCESS), hasTaskTiming());

        Future<Integer> releaseAccessTask = execService.submitToKeyOwner( callable, path);

        return releaseAccessTask.get();
    }

    /**
     * Execute check file access
     *
     * @param path String
     * @param chkLock ClusterFileLock
     * @param writeChk boolean
     * @return boolean
     * @exception InterruptedException Execution interrupted
     * @exception ExecutionException Execution error
     */
    public boolean executeCheckFileAccess( String path, ClusterFileLock chkLock, boolean writeChk)
        throws InterruptedException, ExecutionException {

        // Check the file access via a remote call to the node that owns the file state
        IExecutorService execService = m_hazelCastInstance.getExecutorService( ExecutorName);
        Callable<Boolean> callable = new CheckFileByteLockTask(getMapName(), path, chkLock, writeChk,
                hasDebugLevel(Dbg.FILEACCESS), hasTaskTiming());

        Future<Boolean> checkLockTask = execService.submitToKeyOwner( callable, path);

        return checkLockTask.get();
    }

    /**
     * Exceute a remote update state
     *
     * @param path String
     * @param fileSts FileStatus
     * @return boolean
     */
    public boolean executeRemoteUpdateState( String path, FileStatus fileSts)
        throws InterruptedException, ExecutionException {

        // Update the file status via a remote call to the node that owns the file state
        IExecutorService execService = m_hazelCastInstance.getExecutorService( ExecutorName);
        Callable<Boolean> callable = new UpdateStateTask(getMapName(), path, fileSts,
                hasDebugLevelOr(Dbg.REMOTETASK, Dbg.FILESTATUS), hasTaskTiming());

        Future<Boolean> updateStateTask = execService.submitToKeyOwner( callable, path);

        return updateStateTask.get();
    }

    /**
     * Execute update file data status
     *
     * @param path String
     * @param startUpdate boolean
     * @return boolean
     */
    public boolean executeUpdateFileDataStatus( String path, boolean startUpdate)
        throws InterruptedException, ExecutionException {

        // Set the file data update status via a remote call to the node that owns the file state
        IExecutorService execService = m_hazelCastInstance.getExecutorService( ExecutorName);
        Callable<Boolean> callable = new FileDataUpdateTask(getMapName(), path, getLocalNode(), startUpdate,
                hasDebugLevel(Dbg.FILEDATAUPDATE), hasTaskTiming());

        Future<Boolean> fileDataUpdateTask = execService.submitToKeyOwner( callable, path);

        return fileDataUpdateTask.get();
    }
}
