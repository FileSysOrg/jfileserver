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
 * Search Map Base Class
 *
 * <p>A search map holds search contexts for active searches on a session, virtual circuit or file</p>
 *
 * @author gkspencer
 */
public abstract class SearchMap {

    //  Default and maximum number of search slots
    protected static final int DefaultSearches = 8;
    protected static final int MaxSearches = 256;

    // Invalid search id
    public static final int InvalidSearchId = -1;

    // Search slot marker object, indicates a search slot is in use before the actual search context
    // is stored in the slot
    protected static final SearchContextAdapter SearchSlotMarker = new SearchContextAdapter();

    // Maximum number of concurrent searches to allow
    private int m_maxSearches = MaxSearches;

    /**
     * Default constructor
     */
    public SearchMap() {
    }

    /**
     * Class constructor
     *
     * @param maxSearches int
     */
    public SearchMap( int maxSearches) {
        m_maxSearches = maxSearches;
    }

    /**
     * Allocate a slot in the active searches list for a new search.
     *
     * @return int  Search slot id, or -1 if there are no more search slots available
     * @exception TooManySearchesException Too many active searches
     */
    public abstract int allocateSearchSlot()
            throws TooManySearchesException;

    /**
     * Allocate a slot in the active searches list for a new search.
     *
     * @param searchId int
     * @return boolean  true if the searchId is unique and a slot has been allocated, false if the searchId is already
     *                  in use
     * @exception TooManySearchesException Too many active searches
     */
    public abstract boolean allocateSearchSlotWithId(int searchId)
            throws TooManySearchesException;

    /**
     * Deallocate the specified search context/slot.
     *
     * @param ctxId int
     * @return SearchContext
     */
    public abstract SearchContext deallocateSearchSlot(int ctxId);

    /**
     * Return the search context for the specified search id.
     *
     * @param srchId int
     * @return SearchContext
     */
    public abstract SearchContext findSearchContext(int srchId);

    /**
     * Store the seach context in the specified slot.
     *
     * @param slot Slot to store the search context.
     * @param srch SearchContext
     */
    public abstract void setSearchContext(int slot, SearchContext srch);

    /**
     * Return the number of active searches
     *
     * @return int
     */
    public abstract int numberOfSearches();

    /**
     * Return the maximum number of concurrent searches allowed
     *
     * @return int
     */
    public final int maximumNumberOfSearches() {
         return m_maxSearches;
    }

    /**
     * Close all active searches
     */
    public abstract void closeAllSearches();

    /**
     * set the maximum number of concurrent searches allowed
     *
     * @param maxSearches int
     */
    protected final void setMaximumNumberOfSearches(int maxSearches) {
        m_maxSearches = maxSearches;
    }
}
