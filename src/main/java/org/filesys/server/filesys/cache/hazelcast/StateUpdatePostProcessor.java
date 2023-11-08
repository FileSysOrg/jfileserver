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

import java.util.EnumSet;

/**
 * File State Update Post Processor Class
 *
 * <p>Low priority file state updates are sent out at the end of request processing.
 *
 * @author gkspencer
 */
public class StateUpdatePostProcessor extends FileStatePostProcessor {

    // Update mask
    private EnumSet<ClusterFileState.UpdateFlag> m_updateMask;

    /**
     * Class constructor
     *
     * @param stateCache HazelCastClusterFileStateCache
     * @param state      HazelCastClusterFileState
     * @param updateMask EnumSet&lt;UpdateFlag&gt;
     */
    public StateUpdatePostProcessor(ClusterFileStateCache stateCache, HazelCastClusterFileState state, EnumSet<ClusterFileState.UpdateFlag> updateMask) {
        super(stateCache, state);

        m_updateMask = updateMask;
    }

    /**
     * Return the update mask
     *
     * @return EnumSet&lt;UpdateFlag&gt;
     */
    public final EnumSet<ClusterFileState.UpdateFlag> getUpdateMask() {
        return m_updateMask;
    }

    /**
     * Add another state update to the existing update mask
     *
     * @param updateFlag UpdateFlag
     */
    public final void addToUpdateMask(ClusterFileState.UpdateFlag updateFlag) {
        m_updateMask.add( updateFlag);
    }

    /**
     * Remove updates from the mask
     *
     * @param updateMask EnumSet&lt;UpdateFlag&gt;
     */
    public final void removeFromUpdateMask(EnumSet<ClusterFileState.UpdateFlag> updateMask) {
        m_updateMask.removeAll( updateMask);
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
