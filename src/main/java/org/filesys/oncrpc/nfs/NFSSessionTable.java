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
     * Remove a session from the list using the unique session id
     *
     * @param sess NFSSrvSession
     * @return NFSSrvSession
     */
    public final NFSSrvSession removeSessionById(NFSSrvSession sess) {

        Enumeration<Object> enumSess = m_sessions.keys();

        while ( enumSess.hasMoreElements()) {

            // Get the current session and check for a matching session id
            Object curKey = enumSess.nextElement();
            NFSSrvSession curSess = m_sessions.get( curKey);

            if ( curSess.getSessionId() == sess.getSessionId()) {

                // Remove the session
                m_sessions.remove( curKey);

                // Return the removed session
                return curSess;
            }
        }

        // Session not found
        return null;
    }

    /**
     * Remove one ro more sessions from the list using the client address
     *
     * @param sess NFSSrvSession
     * @return int
     */
    public final int removeSessionsByAddress(NFSSrvSession sess) {

        int remCnt = 0;
        Enumeration<Object> enumSess = m_sessions.keys();

        while ( enumSess.hasMoreElements()) {

            // Get the current session and check for a matching client address
            Object curKey = enumSess.nextElement();
            NFSSrvSession curSess = m_sessions.get( curKey);

            if ( curSess.getRemoteSocketAddress().equals( sess.getRemoteSocketAddress())) {

                // Remove the session
                m_sessions.remove( curKey);

                // Update the count of removed sessions
                remCnt++;
            }
        }

        // Return the count of sessions removed
        return remCnt;
    }

    /**
     * Enumerate the session ids
     *
     * @return Enumeration&lt;Object&gt;
     */
    public final Enumeration<Object> enumerate() {
        return m_sessions.keys();
    }
}
