/*
 * Copyright (C) 2006-2011 Alfresco Software Limited.
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

package org.filesys.server.filesys;

import org.filesys.debug.Debug;
import org.filesys.smb.SharingMode;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Objects;

/**
 * File Access Token Interface
 * 
 * <p>A file access token can be granted on a file after various file sharing mode checks have been
 * performed on a file open request.
 *
 * @author gkspencer
 */
public class FileAccessToken implements Serializable {

    // Serialization id
    private static final long serialVersionUID = 1L;

    // Access token flags
    public enum Flags {
        AttributesOnly,     // token for an attributes only file open, data not accessed
        Released            // token has been released
    }

    // Access token flags
    private EnumSet<Flags> m_flags;

    // Token owner id, usually process id or session/virtual circuit id
    private String m_ownerId;

    // File open access mask and sharing mode
    private int m_accessMode;
    private SharingMode m_shareMode;

    /**
     * Default constructor
     */
    protected FileAccessToken() {
        m_flags = EnumSet.noneOf( FileAccessToken.Flags.class);

    }

    /**
     * Class constructor
     *
     * @param params FileOpenParams
     */
    protected FileAccessToken( FileOpenParams params) {
        m_flags = EnumSet.noneOf( FileAccessToken.Flags.class);

        if ( params.hasSession())
            m_ownerId = params.getSession().getUniqueId() + "-" + params.getRequestId();
        else
            m_ownerId = "0x" + Long.toHexString( params.getProcessId());

        m_accessMode = params.getAccessMode();
        m_shareMode  = params.getSharedAccess();

        setAttributesOnly( params.isAttributesOnlyAccess());
    }

    /**
     * Class constructor
     *
     * @param ownerId long
     * @param accessMode int
     * @param sharingMode SharingMode
     * @param attrOnly boolean
     */
    protected FileAccessToken( long ownerId, int accessMode, SharingMode sharingMode, boolean attrOnly) {
        m_flags = EnumSet.noneOf( FileAccessToken.Flags.class);

        m_ownerId = "0x" + Long.toHexString( ownerId);

        m_accessMode = accessMode;
        m_shareMode  = sharingMode;

        setAttributesOnly( attrOnly);
    }

    /**
     * Return the owner id
     *
     * @return String
     */
    public final String getOwnerId() {
        return m_ownerId;
    }

    /**
     * Return the file access mode
     *
     * @return int
     */
    public final int getAccessMode() { return m_accessMode; }

    /**
     * Return the file sharing mode
     *
     * @return SharingMode
     */
    public final SharingMode getSharedAccess() { return m_shareMode; }

    /**
     * Check if the access token has been released
     *
     * @return boolean
     */
    public final boolean isReleased() {
        return m_flags.contains( Flags.Released);
    }

    /**
     * Set the owner id
     *
     * @param id String
     */
    public final void setOwnerId( String id) { m_ownerId = id; }

    /**
     * Set the released state of the access token
     *
     * @param released boolean
     */
    public final void setReleased(boolean released) {
        if ( released)
            m_flags.add( Flags.Released);
        else
            m_flags.remove( Flags.Released);
    }

    /**
     * Check if the access token is on attributes only file open
     *
     * @return boolean
     */
    public final boolean isAttributesOnly() {
        return m_flags.contains( Flags.AttributesOnly);
    }

    /**
     * Set/clear the attributes only flag
     *
     * @param attrOnly boolean
     */
    public final void setAttributesOnly(boolean attrOnly) {
        if (attrOnly)
            m_flags.add(Flags.AttributesOnly);
        else
            m_flags.remove(Flags.AttributesOnly);
    }

    /**
     * Check for equality
     *
     * @return boolean
     */
    public boolean equals(Object obj) {

        boolean eq = false;

        if ( obj instanceof FileAccessToken) {
            FileAccessToken token = (FileAccessToken) obj;

            if (Objects.equals(token.getOwnerId(), getOwnerId()) && token.getAccessMode() == getAccessMode() &&
                 token.getSharedAccess() == getSharedAccess() && token.isAttributesOnly() == isAttributesOnly())
                eq = true;
        }

        return eq;
    }

    /**
     * Return the access token as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Token owner=");
        str.append( getOwnerId());

        str.append(",access=0x");
        str.append( Integer.toHexString( m_accessMode));
        str.append(",sharing=");
        str.append( getSharedAccess().name());

        if (isAttributesOnly())
            str.append(",AttrOnly");

        if (isReleased())
            str.append(",Released");
        str.append("]");

        return str.toString();
    }

    /**
     * Finalize
     */
    public void finalize() {

        // Check if the access token was released
        if ( !isReleased())
            Debug.println("** Access token finalized, not released, " + toString() + " **");
    }
}
