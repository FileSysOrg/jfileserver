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

import java.util.Enumeration;
import java.util.Hashtable;

/**
 * NFS Server Session Table Class
 *
 * @author gkspencer
 */
public class NFSSessionTable {

    //	Session list
    private Hashtable<Object, NFSSrvSession> m_sessions;

    /**
     * Class constructor
     */
    public NFSSessionTable() {
        m_sessions = new Hashtable<Object, NFSSrvSession>();
    }

    /**
     * Return the number of sessions in the list
     *
     * @return int
     */
    public final int numberOfSessions() {
        return m_sessions.size();
    }

    /**
     * Add a session to the list
     *
     * @param sess NFSSrvSession
     */
    public final void addSession(NFSSrvSession sess) {
        m_sessions.put(sess.getAuthIdentifier(), sess);
    }

    /**
     * Find the session using the authentication identifier
     *
     * @param authIdent Object
     * @return NFSSrvSession
     */
    public final NFSSrvSession findSession(Object authIdent) {
        return m_sessions.get(authIdent);
    }

    /**
     * Remove a session from the list
     *
     * @param sess NFSSrvSession
     * @return NFSSrvSession
     */
    public final NFSSrvSession removeSession(NFSSrvSession sess) {
        return removeSession(sess.getAuthIdentifier());
    }

    /**
     * Remove a session from the list
     *
     * @param authIdent Object
     * @return NFSSrvSession
     */
    public final NFSSrvSession removeSession(Object authIdent) {

        //	Find the required session
        NFSSrvSession sess = findSession(authIdent);

        //	Remove the session and return the removed session
        m_sessions.remove(authIdent);
        return sess;
    }

    /**
     * Enumerate the session ids
     *
     * @return Enumeration
     */
    public final Enumeration enumerate() {
        return m_sessions.keys();
    }
}
