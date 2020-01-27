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

package org.filesys.server.filesys;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Hashed Search Map Class
 *
 * <p>Uses a hashed value as the unique search id</p>
 *
 * @author gkspencer
 */
public class HashedSearchMap extends SearchMap {

    // List of active searches
    private Map<Integer, SearchContext> m_search;

    /**
     * Default constructor
     */
    public HashedSearchMap() {
    }

    /**
     * Class constructor
     *
     * @param maxSearches int
     */
    public HashedSearchMap( int maxSearches) {
        setMaximumNumberOfSearches( maxSearches >= DefaultSearches ? maxSearches : DefaultSearches);
    }

    /**
     * Allocate a slot in the active searches list for a new search.
     *
     * @return int  Search slot id, or -1 if there are no more search slots available
     * @exception TooManySearchesException Too many active searches
     */
    public int allocateSearchSlot()
            throws TooManySearchesException {

        // Not supported
        return InvalidSearchId;
    }

    /**
     * Allocate a slot in the active searches list for a new search.
     *
     * @param searchId int
     * @return boolean  true if the searchId is unique and a slot has been allocated, false if the searchId is already
     *                  in use
     * @exception TooManySearchesException Too many active searches
     */
    public synchronized boolean allocateSearchSlotWithId(int searchId)
            throws TooManySearchesException {

        //  Check if the search array has been allocated
        if (m_search == null) {
            m_search = new HashMap<Integer, SearchContext>(DefaultSearches);
        }

        // Check if there are any free search slots available
        else if ( m_search.size() >= maximumNumberOfSearches()) {

            // No more active search slots available
            return false;
        }

        // Check if the search id is not currently used
        else if ( m_search.containsKey( searchId)) {

            // Id already in use
            return false;
        }

        // Add a marker search context for the allocated id
        m_search.put( searchId, SearchSlotMarker);

        // Slot allocated for the search id
        return true;
    }

    /**
     * Deallocate the specified search context/slot.
     *
     * @param ctxId int
     * @return SearchContext
     */
    public synchronized SearchContext deallocateSearchSlot(int ctxId) {

        //  Check if the search array has been allocated and that the index is valid
        if (m_search == null)
            return null;

        // Remove the search context from the active search list
        SearchContext searchCtx = m_search.remove( ctxId);

        if ( searchCtx != null) {

            // Make sure the search is closed
            if ( searchCtx.isClosed() == false) {
                searchCtx.closeSearch();
                searchCtx.setClosed();
            }
        }

        // Return the search context that was closed
        return searchCtx;
    }

    /**
     * Return the search context for the specified search id.
     *
     * @param srchId int
     * @return SearchContext
     */
    public synchronized SearchContext findSearchContext(int srchId) {

        //  Check if the search array is valid
        if (m_search == null)
            return null;

        //  Return the required search context
        return m_search.get( srchId);
    }

    /**
     * Store the seach context in the specified slot.
     *
     * @param slot Slot to store the search context.
     * @param srch SearchContext
     */
    public synchronized void setSearchContext(int slot, SearchContext srch) {

        //  Check if the search slot id is valid
        if (m_search == null)
            return;

        //  Store the context
        m_search.put( slot, srch);
    }

    /**
     * Return the number of active searches
     *
     * @return int
     */
    public synchronized int numberOfSearches() {
        if ( m_search == null)
            return 0;
        return m_search.size();
    }

    /**
     * Close all active searches
     */
    public synchronized void closeAllSearches() {

        //  Close all active searches
        Iterator<Integer> searchKeyIter = m_search.keySet().iterator();

        while ( searchKeyIter.hasNext()) {

            // Remove the search context from the active search list
            Integer searchKey = searchKeyIter.next();
            SearchContext searchCtx = m_search.get( searchKey);

            if ( searchCtx != null) {

                // Make sure the search is closed
                if ( searchCtx.isClosed() == false) {
                    searchCtx.closeSearch();
                    searchCtx.isClosed();
                }
            }
        }

        // Clear the search list
        m_search.clear();
    }
}
