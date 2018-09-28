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

package org.filesys.oncrpc.nfs;

import java.util.Hashtable;

/**
 * Share Details Hash Class
 *
 * <p>Hashtable of ShareDetails for the available disk shared devices. ShareDetails are indexed using the
 * hash of the share name to allow mounts to be persistent across server restarts.
 *
 * @author gkspencer
 */
public class ShareDetailsHash {

    //	Share name hash to share details
    private Hashtable<Integer, ShareDetails> m_details;

    /**
     * Class constructor
     */
    public ShareDetailsHash() {
        m_details = new Hashtable<Integer, ShareDetails>();
    }

    /**
     * Add share details to the list of available shares
     *
     * @param details ShareDetails
     */
    public final void addDetails(ShareDetails details) {
        m_details.put(new Integer(details.getName().hashCode()), details);
    }

    /**
     * Delete share details from the list
     *
     * @param shareName String
     * @return ShareDetails
     */
    public final ShareDetails deleteDetails(String shareName) {
        return m_details.get(new Integer(shareName.hashCode()));
    }

    /**
     * Find share details for the specified share name
     *
     * @param shareName String
     * @return ShareDetails
     */
    public final ShareDetails findDetails(String shareName) {

        //	Get the share details for the associated share name
        ShareDetails details = m_details.get(new Integer(shareName.hashCode()));

        //	Return the share details
        return details;
    }

    /**
     * Find share details for the specified share name hash code
     *
     * @param hashCode int
     * @return ShareDetails
     */
    public final ShareDetails findDetails(int hashCode) {

        //	Get the share details for the associated share name
        ShareDetails details = m_details.get(new Integer(hashCode));

        //	Return the share details
        return details;
    }

    /**
     * Return the share details
     *
     * @return Share details hash table
     */
    public final Hashtable<Integer, ShareDetails> getShareDetails() {
        return m_details;
    }
}
