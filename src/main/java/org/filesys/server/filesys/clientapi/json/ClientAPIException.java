/*
 * Copyright (C) 2024 GK Spencer
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
package org.filesys.server.filesys.clientapi.json;

/**
 * Client API Exception Class
 *
 * <p>Contains details of an error during processing of a client API request</p>
 *
 * @author gkspencer
 */
public class ClientAPIException extends Exception {

    private static final long serialVersionUID = 1L;

    // Client API error details
    private String m_path;
    private boolean m_warning;

    /**
     * Class constructor
     *
     * @param msg String
     */
    public ClientAPIException( String msg) {
        super( msg);

        m_warning = false;
    }

    /**
     * Class constructor
     *
     * @param msg String
     * @param warning boolean
     */
    public ClientAPIException(String msg, boolean warning) {
        super( msg);

        m_warning = warning;
    }

    /**
     * Class constructor
     *
     * @param msg String
     * @param path String
     * 2param warning boolean
     */
    public ClientAPIException(String msg, String path, boolean warning) {
        super( msg);

        m_path = path;
        m_warning = false;
    }

    /**
     * Check if the exception is a warning or error
     *
     * @return boolean
     */
    public final boolean isWarning() { return m_warning; }

    /**
     * Check if there is an associated path for the error
     *
     * @return boolean
     */
    public final boolean hasPath() { return m_path != null; }

    /**
     * Return the associated path
     *
     * @return String
     */
    public final String getPath() { return m_path; }

    /**
     * Return the client API error as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append("Msg=");
        str.append( getMessage());

        if ( hasPath()) {
            str.append(", path=");
            str.append(getPath());
        }

        str.append( isWarning() ? ", Warning" : ", Error");

        return str.toString();
    }
}
