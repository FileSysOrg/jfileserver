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

/**
 * Sequential Id Search Map Class
 *
 * <p>Allocates search ids using a sequential counter</p>
 *
 * @author gkspencer
 */
public class SequentialSearchMap extends SearchMap {

    // List of active searches
    private SearchContext[] m_search;
    private int m_searchCount;

    /**
     * Default constructor
     */
    public SequentialSearchMap() {
    }

    /**
     * Class constructor
     *
     * @param maxSearches int
     */
    public SequentialSearchMap( int maxSearches) {
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

        //  Check if the search array has been allocated
        if (m_search == null)
            m_search = new SearchContext[DefaultSearches];

        //  Find a free slot for the new search
        int idx = 0;

        while (idx < m_search.length && m_search[idx] != null)
            idx++;

        //  Check if we found a free slot
        if (idx == m_search.length) {

            //  The search array needs to be extended, check if we reached the limit.
            if (m_search.length >= MaxSearches)
                throw new TooManySearchesException();

            //  Extend the search array
            SearchContext[] newSearch = new SearchContext[m_search.length * 2];
            System.arraycopy(m_search, 0, newSearch, 0, m_search.length);
            m_search = newSearch;
        }

        //  Return the allocated search slot index, mark the slot as allocated
        m_searchCount++;
        m_search[idx] = SearchSlotMarker;
        return idx;

    }

    /**
     * Allocate a slot in the active searches list for a new search.
     *
     * @param searchId int
     * @return boolean  true if the searchId is unique and a slot has been allocated, false if the searchId is already
     *                  in use
     * @exception TooManySearchesException Too many active searches
     */
    public boolean allocateSearchSlotWithId(int searchId)
            throws TooManySearchesException {

        // Not supported
        return false;
    }

    /**
     * Deallocate the specified search context/slot.
     *
     * @param ctxId int
     * @return SearchContext
     */
    public SearchContext deallocateSearchSlot(int ctxId) {

        //  Check if the search array has been allocated and that the index is valid
        if (m_search == null || ctxId >= m_search.length)
            return null;

        //  Close the search
        if (m_search[ctxId] != null)
            m_search[ctxId].closeSearch();

        //  Free the specified search context slot
        m_searchCount--;

        SearchContext ctx = m_search[ctxId];
        m_search[ctxId] = null;

        // Return the search context that was closed
        return ctx;
    }

    /**
     * Return the search context for the specified search id.
     *
     * @param srchId int
     * @return SearchContext
     */
    public SearchContext findSearchContext(int srchId) {

        //  Check if the search array is valid and the search index is valid
        if (m_search == null || srchId >= m_search.length)
            return null;

        //  Return the required search context
        return m_search[srchId];
    }

    /**
     * Store the seach context in the specified slot.
     *
     * @param slot Slot to store the search context.
     * @param srch SearchContext
     */
    public void setSearchContext(int slot, SearchContext srch) {

        //  Check if the search slot id is valid
        if (m_search == null || slot > m_search.length)
            return;

        //  Store the context
        m_search[slot] = srch;
    }

    /**
     * Return the number of active searches
     *
     * @return int
     */
    public int numberOfSearches() {
        return m_searchCount;
    }

    /**
     * Close all active searches
     */
    public void closeAllSearches() {

        //  Close all active searches
        for (int idx = 0; idx < m_search.length; idx++) {

            //  Check if the current search slot is active
            if (m_search[idx] != null)
                deallocateSearchSlot(idx);
        }

        //  Release the search context list, clear the search count
        m_search = null;
        m_searchCount = 0;
    }
}
