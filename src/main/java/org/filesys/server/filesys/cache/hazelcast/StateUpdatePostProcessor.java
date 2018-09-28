/*
 * Copyright (C) 2006-2011 Alfresco Software Limited.
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

package org.filesys.server.filesys.cache.hazelcast;

import org.filesys.debug.Debug;
import org.filesys.server.filesys.cache.cluster.ClusterFileState;
import org.filesys.server.filesys.cache.cluster.ClusterFileStateCache;
import org.filesys.server.filesys.cache.cluster.FileStatePostProcessor;

/**
 * File State Update Post Processor Class
 *
 * <p>Low priority file state updates are sent out at the end of request processing.
 *
 * @author gkspencer
 */
public class StateUpdatePostProcessor extends FileStatePostProcessor {

    // Update mask
    private int m_updateMask;

    /**
     * Class constructor
     *
     * @param stateCache HazelCastClusterFileStateCache
     * @param state      HazelCastClusterFileState
     * @param updateMask int
     */
    public StateUpdatePostProcessor(ClusterFileStateCache stateCache, HazelCastClusterFileState state, int updateMask) {
        super(stateCache, state);

        m_updateMask = updateMask;
    }

    /**
     * Return the update mask
     *
     * @return int
     */
    public final int getUpdateMask() {
        return m_updateMask;
    }

    /**
     * Add another state update to the existing update mask
     *
     * @param updateMask int
     */
    public final void addToUpdateMask(int updateMask) {
        m_updateMask |= updateMask;
    }

    /**
     * Remove updates from the mask
     *
     * @param updateMask int
     */
    public final void removeFromUpdateMask(int updateMask) {
        m_updateMask &= ~updateMask;
    }

    /**
     * Run the post processor
     */
    public void runProcessor() {

        try {

            // Send the state updated message to the cluster
            getStateCache().updateFileState(getState(), m_updateMask);
        }
        catch (Exception ex) {

            // DEBUG
            if (hasDebug()) {
                Debug.println("State update post processor failed to update state=" + getState() + ", updates=" + ClusterFileState.getUpdateMaskAsString(m_updateMask));
                Debug.println(ex);
            }
        }
    }
}
