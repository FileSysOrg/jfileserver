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

package org.filesys.server.locking;

/**
 * Lock Parameters Class
 *
 * @author gkspencer
 */
public class LockParams {

    // Lock offset and length
    private long m_offset;
    private long m_length;

    // Lock owner
    private int m_owner;

    // Lock flags
    private int m_flags;

    /**
     * Class constructor
     *
     * @param offset long
     * @param len long
     * @param owner int
     */
    public LockParams(long offset, long len, int owner) {
        m_offset = offset;
        m_length = len;

        m_owner = owner;
    }

    /**
     * Class constructor
     *
     * @param offset long
     * @param len long
     * @param owner int
     * @param flags int
     */
    public LockParams(long offset, long len, int owner, int flags) {
        m_offset = offset;
        m_length = len;

        m_owner = owner;
        m_flags = flags;
    }

    /**
     * Return the lock offset
     *
     * @return long
     */
    public final long getOffset() {
        return m_offset;
    }

    /**
     * Return the lock length
     *
     * @return long
     */
    public final long getLength() {
        return m_length;
    }

    /**
     * Return the lock owner id
     *
     * @return int
     */
    public final int getOwner() {
        return m_owner;
    }

    /**
     * Return the lock flags
     *
     * @return int
     */
    public final int getFlags() {
        return m_flags;
    }

    /**
     * Return the lock parameters as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[Lock offset=");
        str.append(getOffset());
        str.append(", len=");
        str.append(getLength());
        str.append(", owner=0x");
        str.append(Integer.toHexString( getOwner()));
        str.append(", flags=0x");
        str.append(Integer.toHexString( getFlags()));
        str.append("]");

        return str.toString();
    }
}
