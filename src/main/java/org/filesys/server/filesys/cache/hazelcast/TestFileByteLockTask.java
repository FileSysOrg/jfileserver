/*
 * Copyright (C) 2020 GK Spencer
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

import com.hazelcast.core.IMap;
import org.filesys.debug.Debug;
import org.filesys.locking.FileLock;
import org.filesys.server.filesys.cache.cluster.ClusterFileLock;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;

/**
 * Test File Byte Range Lock Remote Task Class
 *
 * <p>Used to synchronize checking if an lock exists on a file by executing on the remote node that owns
 * the file state/key.
 *
 * @author gkspencer
 */
public class TestFileByteLockTask extends RemoteStateTask<FileLock> {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // File lock to test
    private ClusterFileLock m_lockTest;

    /**
     * Default constructor
     */
    public TestFileByteLockTask() {
    }

    /**
     * Class constructor
     *
     * @param mapName     String
     * @param key         String
     * @param lockTest    ClusterFileLock
     * @param debug       boolean
     * @param timingDebug boolean
     */
    public TestFileByteLockTask(String mapName, String key, ClusterFileLock lockTest, boolean debug, boolean timingDebug) {
        super(mapName, key, true, false, debug, timingDebug);

        m_lockTest = lockTest;
    }

    /**
     * Run a remote task against a file state
     *
     * @param stateCache Map of paths/cluster file states
     * @param fState     ClusterFileState
     * @return FileLock
     * @throws Exception Error running remote task
     */
    protected FileLock runRemoteTaskAgainstState(IMap<String, ClusterFileState> stateCache, ClusterFileState fState)
            throws Exception {

        // DEBUG
        if (hasDebug())
            Debug.println("TestFileByteLockTask: testLock=" + m_lockTest + " on " + fState);

        // Check if there are any locks on the file that overlap with the specified lock
        return fState.testLock( m_lockTest);
    }
}
