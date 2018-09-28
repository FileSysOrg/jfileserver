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

package org.filesys.smb.dcerpc;

import java.util.*;

/**
 * Policy Handle Cache Class
 *
 * @author gkspencer
 */
public class PolicyHandleCache {

    //	Policy handles
    private Hashtable<String, PolicyHandle> m_cache;

    /**
     * Default constructor
     */
    public PolicyHandleCache() {
        m_cache = new Hashtable<String, PolicyHandle>();
    }

    /**
     * Return the number of handles in the cache
     *
     * @return int
     */
    public final int numberOfHandles() {
        return m_cache.size();
    }

    /**
     * Add a handle to the cache
     *
     * @param name   String
     * @param handle PolicyHandle
     */
    public final void addHandle(String name, PolicyHandle handle) {
        m_cache.put(name, handle);
    }

    /**
     * Return the handle for the specified index
     *
     * @param index String
     * @return PolicyHandle
     */
    public final PolicyHandle findHandle(String index) {
        return m_cache.get(index);
    }

    /**
     * Delete a handle from the cache
     *
     * @param index String
     * @return PolicyHandle
     */
    public final PolicyHandle removeHandle(String index) {
        return m_cache.remove(index);
    }

    /**
     * Enumerate the handles in the cache
     *
     * @return Enumeration of PolicyHandles
     */
    public final Enumeration<PolicyHandle> enumerateHandles() {
        return m_cache.elements();
    }

    /**
     * Clear all handles from the cache
     */
    public final void removeAllHandles() {
        m_cache.clear();
    }
}
