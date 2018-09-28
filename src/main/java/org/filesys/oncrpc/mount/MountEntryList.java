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

package org.filesys.oncrpc.mount;


import java.util.ArrayList;
import java.util.List;

/**
 * Mount Entry List Class
 *
 * <p>Contains a list of active mount entries.
 *
 * @author gkspencer
 */
public class MountEntryList {

    //	Mount entry list
    private List<MountEntry> m_mounts;

    /**
     * Default constructor
     */
    public MountEntryList() {
        m_mounts = new ArrayList<MountEntry>();
    }

    /**
     * Ad an entry to the list
     *
     * @param entry MountEntry
     */
    public synchronized final void addEntry(MountEntry entry) {
        m_mounts.add(entry);
    }

    /**
     * Return the number of entries in the list
     *
     * @return int
     */
    public synchronized final int numberOfEntries() {
        return m_mounts.size();
    }

    /**
     * Return the specified entry
     *
     * @param idx int
     * @return MountEntry
     */
    public synchronized final MountEntry getEntryAt(int idx) {
        if (idx < 0 || idx >= m_mounts.size())
            return null;
        return m_mounts.get(idx);
    }

    /**
     * Find an entry in the list
     *
     * @param path String
     * @param host String
     * @return MountEntry
     */
    public synchronized final MountEntry findEntry(String path, String host) {
        for (int i = 0; i < m_mounts.size(); i++) {
            MountEntry entry = m_mounts.get(i);

            if (host.compareTo(entry.getHost()) == 0 && path.compareTo(entry.getPath()) == 0)
                return entry;
        }
        return null;
    }

    /**
     * Remove an entry from the list
     *
     * @param path String
     * @param host String
     * @return MountEntry
     */
    public synchronized final MountEntry removeEntry(String path, String host) {
        for (int i = 0; i < m_mounts.size(); i++) {
            MountEntry entry = m_mounts.get(i);

            if (host.compareTo(entry.getHost()) == 0 && path.compareTo(entry.getPath()) == 0) {
                m_mounts.remove(i);
                return entry;
            }
        }
        return null;
    }

    /**
     * Remove all entries from the list for the specified host
     *
     * @param host String
     */
    public synchronized final void removeHostEntries(String host) {
        for (int i = 0; i < m_mounts.size(); i++) {
            MountEntry entry = m_mounts.get(i);

            if (host.compareTo(entry.getHost()) == 0)
                m_mounts.remove(i);
        }
    }

    /**
     * Find all items for the specified host and return as a new list
     *
     * @param host String
     * @return MountEntryList
     */
    public synchronized final MountEntryList findSessionEntries(String host) {

        //	Allocate the list to hold the matching entries
        MountEntryList list = new MountEntryList();

        //	Find the matching entries
        for (int i = 0; i < m_mounts.size(); i++) {
            MountEntry entry = m_mounts.get(i);
            if (host.compareTo(entry.getHost()) == 0)
                list.addEntry(entry);
        }

        //	Check if the list is empty, return the list
        if (list.numberOfEntries() == 0)
            list = null;
        return list;
    }

    /**
     * Remote all entries from the list
     */
    public synchronized final void removeAllItems() {
        m_mounts.clear();
    }
}
