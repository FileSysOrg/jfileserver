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

package org.filesys.ftp;

import java.util.Hashtable;

/**
 * FTP Data Session Table Class
 *
 * <p>Keeps track of FTP data session objects using the local port number as the index.
 *
 * @author gkspencer
 */
public class FTPDataSessionTable {

    //	Data session table
    private Hashtable<Integer, FTPDataSession> m_sessTable;

    /**
     * Default constructor
     */
    public FTPDataSessionTable() {
        m_sessTable = new Hashtable<Integer, FTPDataSession>();
    }

    /**
     * Add a session to the table
     *
     * @param port int
     * @param sess FTPDataSession
     */
    public final void addSession(int port, FTPDataSession sess) {
        m_sessTable.put(new Integer(port), sess);
    }

    /**
     * Find the session using the specified local port
     *
     * @param port int
     * @return FTPDataSession
     */
    public final FTPDataSession findSession(int port) {
        return m_sessTable.get(new Integer(port));
    }

    /**
     * Return the number of sessions in the table
     *
     * @return int
     */
    public final int numberOfSessions() {
        return m_sessTable.size();
    }

    /**
     * Remove a session from the table
     *
     * @param sess FTPDataSession
     * @return FTPDataSession
     */
    public final FTPDataSession removeSession(FTPDataSession sess) {
        return m_sessTable.remove(new Integer(sess.getAllocatedPort()));
    }

    /**
     * Remove all sessions from the table
     */
    public final void removeAllSessions() {
        m_sessTable.clear();
    }
}
