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

package org.filesys.smb.server;

import org.filesys.smb.DialectSelector;

/**
 * Negotiate Context class
 *
 * <p>Contains the list of SMB dialects requested by the client. Can be extended to add other negotiate specific values
 * that need to be passed between the parsing of the incoming negotiate request and the packing of the negotiate response</p>
 * @author gkspencer
 */
public class NegotiateContext {

    // List of SMB dialects requested by the client
    private DialectSelector m_dialects;

    // Client/server capabilities
    private int m_capabilities;

    /**
     * Class constructor
     */
    public NegotiateContext() {
    }

    /**
     * Class constructor
     *
     * @param dialects DialectSelector
     */
    public NegotiateContext( DialectSelector dialects) {
        setDialects( dialects);
    }

    /**
     * Get the list of requested dialects
     *
     * @return DialectSelector
     */
    public DialectSelector getDialects() {
        return m_dialects;
    }

    /**
     * Set the list of requested dialects
     *
     * @param dialects DialectSelector
     */
    public void setDialects( DialectSelector dialects) {
        m_dialects = dialects;
    }

    /**
     * Get the client/server capabilties
     *
     * @return int
     */
    public final int getCapabilities() {
        return m_capabilities;
    }

    /**
     * Set the client/server capabilities
     *
     * @param capab int
     */
    public final void setCapabilities( int capab) {
        m_capabilities = capab;
    }

    /**
     * Return the negotiate context as a string
     *
     * @return String
     */
    public String toString() {

        StringBuffer str = new StringBuffer();

        str.append( "[NegCtx dialects=");
        if ( m_dialects != null)
            str.append( m_dialects.toString());
        else
            str.append( "null");
        str.append( "]");

        return str.toString();
    }
}
