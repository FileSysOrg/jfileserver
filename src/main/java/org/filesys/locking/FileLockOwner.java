/*
 * Copyright (C) 2020 GK Spencer
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

package org.filesys.locking;

import org.filesys.server.SrvSession;

import java.io.Serializable;

/**
 * File Lock Owner Base Class
 *
 * <p>Provides the base for all file lock owner types</p>
 *
 * @author gkspencer
 */
public abstract class FileLockOwner implements Serializable {

    // Serialization id
    private static final long serialVersionUID = 1L;

    /**
     * Protocol types enum class
     */
    public enum Protocol {
        SMB,    // Server Message Block
        NFS,    // Network File System
        FTP,    // File Transfer Protocol

        Unknown
    }

    // Owner protocol and version
    private Protocol m_protocol;
    private int m_version;

    // Owner session id
    private int m_sessId;

    /**
     * Class constructor
     *
     * @param proto Protocol
     * @param sess SrvSession
     */
    public FileLockOwner( Protocol proto, SrvSession sess) {
        m_protocol = proto;
        m_sessId = sess.getSessionId();
    }

    /**
     * Class constructor
     *
     * @param proto Protocol
     * @param version int
     * @param sess SrvSession
     */
    public FileLockOwner( Protocol proto, int version, SrvSession sess) {
        m_protocol = proto;
        m_version  = version;
        m_sessId = sess.getSessionId();
    }

    /**
     * Return the owner protocol
     *
     * @return Protocol
     */
    public final Protocol isProtocol() { return m_protocol; }

    /**
     * Return the protocol version
     *
     * @return int
     */
    public final int isProtocolVersion() { return m_version; }

    /**
     * Return the session id
     *
     * @return int
     */
    public final int getSessionId() { return m_sessId; }

    /**
     * Check if this lock owner owns the specified lock
     *
     * @param fLock FileLock
     * @return boolean
     */
    public abstract boolean isLockOwner( FileLock fLock);

    /**
     * Build the file lock owner details string
     *
     * @param str StringBuilder
     */
    protected void buildDetailsString( StringBuilder str) {
        str.append( "protocol=");
        str.append( isProtocol().name());

        str.append( ",sessId=");
        str.append( getSessionId());

        if ( isProtocolVersion() != 0) {
            str.append( ", version=");
            if ( isProtocolVersion() > 255) {
                str.append("0x");
                str.append(Integer.toHexString(isProtocolVersion()));
            }
            else
                str.append( isProtocolVersion());
        }
    }

    /**
     * Return the file lock owner details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append( "[");
        buildDetailsString( str);
        str.append( "]");

        return str.toString();
    }
}
