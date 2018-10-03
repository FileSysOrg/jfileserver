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

package org.filesys.smb.nt;

import org.filesys.util.DataBuffer;
import org.filesys.util.DataPacker;

/**
 * Security Id Class
 *
 * @author gkspencer
 */
public class SID {

    //	Constants
    //
    //	Predefined identifier authorities
    public static final int IdentAuthNull       = 0;
    public static final int IdentAuthWorld      = 1;
    public static final int IdentAuthLocal      = 2;
    public static final int IdentAuthCreator    = 3;
    public static final int IdentAuthNonUnique  = 4;
    public static final int IdentAuthNT         = 5;

    //	Predefined sub-authority RIDs
    public static final int SubAuthNull         = 0;
    public static final int SubAuthWorld        = 0;
    public static final int SubAuthLocal        = 0;
    public static final int SubAuthCreatorOwner = 0;
    public static final int SubAuthCreatorGroup = 1;

    public static final int SubAuthNTDialup         = 1;
    public static final int SubAuthNTNetwork        = 2;
    public static final int SubAuthNTBatch          = 3;
    public static final int SubAuthNTInteractive    = 4;
    public static final int SubAuthNTService        = 6;
    public static final int SubAuthNTAnonymous      = 7;
    public static final int SubAuthNTProxy          = 8;
    public static final int SubAuthNTEnterpriseCtrl = 9;
    public static final int SubAuthNTPrincipalSelf  = 10;
    public static final int SubAuthNTAuthenticated  = 11;
    public static final int SubAuthNTRestrictedCode = 12;
    public static final int SubAuthNTTerminalServer = 13;
    public static final int SubAuthNTLocalSystem    = 18;
    public static final int SubAuthNTNonUnique      = 21;
    public static final int SubAuthNTBuiltinDomain  = 32;

    //	Minimum binary size for a SID
    private static final int MinimumBinarySize = 12;

    //	Revision
    private int m_revision;

    //	Identifier authority
    private byte[] m_identAuth;

    //	Subauthority count and values
    private int m_subAuthCnt;
    private int[] m_subAuth;

    //	Relative id
    private int m_rid = -1;

    //	Object name
    private String m_name;

    /**
     * Default constructor
     */
    public SID() {
    }

    /**
     * Class constructor
     *
     * @param name    String
     * @param rev     int
     * @param auth    int
     * @param subauth int
     */
    public SID(String name, int rev, int auth, int subauth) {
        m_name = name;
        m_revision = rev;

        m_identAuth = new byte[6];
        m_identAuth[5] = (byte) (auth & 0xFF);

        m_subAuthCnt = 1;
        m_subAuth = new int[1];
        m_subAuth[0] = subauth;

        m_rid = -1;
    }

    /**
     * Class constructor
     *
     * @param rev     int
     * @param auth    int
     * @param subauth int
     * @param rid     int
     */
    public SID(int rev, int auth, int subauth, int rid) {
        m_revision = rev;

        m_identAuth = new byte[6];
        m_identAuth[5] = (byte) (auth & 0xFF);

        m_subAuthCnt = 1;
        m_subAuth = new int[1];
        m_subAuth[0] = subauth;

        m_rid = rid;
    }

    /**
     * Class constructor
     *
     * @param rev      int
     * @param auth     int
     * @param subauth1 int
     * @param subauth2 int
     * @param rid      int
     */
    public SID(int rev, int auth, int subauth1, int subauth2, int rid) {
        m_revision = rev;

        m_identAuth = new byte[6];
        m_identAuth[5] = (byte) (auth & 0xFF);

        m_subAuthCnt = 2;
        m_subAuth = new int[2];
        m_subAuth[0] = subauth1;
        m_subAuth[1] = subauth2;

        m_rid = rid;
    }

    /**
     * Copy constructor
     *
     * @param sid SID
     */
    public SID(SID sid) {
        m_revision = sid.getRevision();
        m_identAuth = sid.getIdentifierAuthority();

        m_subAuthCnt = sid.getSubauthorityCount();
        if (sid.hasRID())
            m_subAuthCnt--;

        m_subAuth = new int[m_subAuthCnt];

        for (int i = 0; i < m_subAuth.length; i++)
            m_subAuth[i] = sid.getSubauthority(i);

        m_rid = sid.getRID();
    }

    /**
     * Get the revision
     *
     * @return int
     */
    public final int getRevision() {
        return m_revision;
    }

    /**
     * Get the identifier authority
     *
     * @return byte[]
     */
    public final byte[] getIdentifierAuthority() {
        return m_identAuth;
    }

    /**
     * Get the sub-authority count
     *
     * @return int
     */
    public final int getSubauthorityCount() {
        if (m_rid != -1)
            return m_subAuthCnt + 1;
        return m_subAuthCnt;
    }

    /**
     * Get the specified sub-authority
     *
     * @param idx int
     * @return int
     */
    public final int getSubauthority(int idx) {

        //	Range check the index
        if (idx < 0 || idx >= m_subAuthCnt)
            return -1;

        //	Return the sub-authority value
        return m_subAuth[idx];
    }

    /**
     * Check if the relative id has been set
     *
     * @return boolean
     */
    public final boolean hasRID() {
        return m_rid != -1 ? true : false;
    }

    /**
     * Get the relative id
     *
     * @return int
     */
    public final int getRID() {
        return m_rid;
    }

    /**
     * Check if the obect name has been set
     *
     * @return boolean
     */
    public final boolean hasName() {
        return m_name != null ? true : false;
    }

    /**
     * Return the object name
     *
     * @return String
     */
    public final String getName() {
        return m_name;
    }

    /**
     * Check if the SID matches this SID
     *
     * @param sid SID
     * @return boolean
     */
    public final boolean equalsSID(SID sid) {

        //	Compare the SIDs
        if (getRevision() == sid.getRevision() &&
                getIdentifierAuthority() != null &&
                sid.getIdentifierAuthority() != null) {

            //	Compare the identifer authority bytes
            byte[] sidIdent = sid.getIdentifierAuthority();
            if (sidIdent.length != m_identAuth.length)
                return false;

            for (int i = 0; i < m_identAuth.length; i++) {
                if (m_identAuth[i] != sidIdent[i])
                    return false;
            }

            //	Compare the sub-authorities
            for (int i = 0; i < getSubauthorityCount(); i++) {
                if (getSubauthority(i) != sid.getSubauthority(i))
                    return false;
            }

            //	SIDs match
            return true;
        }

        //	SIDs do not match
        return false;
    }

    /**
     * Set the relative id value
     *
     * @param id int
     */
    public final void setRID(int id) {
        m_rid = id;
    }

    /**
     * Set the object name
     *
     * @param name String
     */
    public final void setName(String name) {
        m_name = name;
    }

    /**
     * Load the SID from the specified buffer
     *
     * @param buf    byte[]
     * @param off    int
     * @param domain boolean
     * @return int
     * @throws LoadException Failed to load the SID
     */
    public final int loadSID(byte[] buf, int off, boolean domain)
            throws LoadException {

        //	Check if the buffer is long enough to contain a valid SID
        if ((buf.length - off) < MinimumBinarySize)
            throw new LoadException("Buffer too short for SID");

        //	Unpack the SID
        m_revision = (int) (buf[off++] & 0xFF);
        m_subAuthCnt = (int) (buf[off++] & 0xFF);

        if (m_identAuth == null)
            m_identAuth = new byte[6];

        for (int i = 0; i < 6; i++)
            m_identAuth[i] = buf[off++];

        //	Unpack the subauthorities
        boolean hasRID = false;
        if (domain == false && m_subAuthCnt > 1) {
            m_subAuthCnt--;
            hasRID = true;
        }
        m_subAuth = new int[m_subAuthCnt];

        for (int i = 0; i < m_subAuthCnt; i++) {
            m_subAuth[i] = DataPacker.getIntelInt(buf, off);
            off += 4;
        }

        if (hasRID) {
            m_rid = DataPacker.getIntelInt(buf, off);
            off += 4;
        }

        //	Return the end of SID offset
        return off;
    }

    /**
     * Load the SID from the specified buffer
     *
     * @param buf    DataBuffer
     * @param domain boolean
     * @return int
     * @throws LoadException Failed to load the SID
     */
    public final int loadSID(DataBuffer buf, boolean domain)
            throws LoadException {

        //	Check if the buffer is long enough to contain a valid SID
        if (buf.getAvailableLength() < MinimumBinarySize)
            throw new LoadException("Buffer too short for SID");

        //	Unpack the SID
        m_revision = buf.getByte();
        m_subAuthCnt = buf.getByte();

        if (m_identAuth == null)
            m_identAuth = new byte[6];

        for (int i = 0; i < 6; i++)
            m_identAuth[i] = (byte) (buf.getByte() & 0xFF);

        //	Unpack the subauthorities
        boolean hasRID = false;
        if (domain == false && m_subAuthCnt > 1) {
            m_subAuthCnt--;
            hasRID = true;
        }
        m_subAuth = new int[m_subAuthCnt];

        for (int i = 0; i < m_subAuthCnt; i++)
            m_subAuth[i] = buf.getInt();

        if (hasRID)
            m_rid = buf.getInt();

        //	Return the end of SID offset
        return buf.getPosition();
    }

    /**
     * Save the SID to the specified buffer
     *
     * @param buf byte[]
     * @param off int
     * @return int
     * @throws SaveException Failed to save the SID
     */
    public final int saveSID(byte[] buf, int off)
            throws SaveException {

        //	Pack the SID
        buf[off++] = (byte) (m_revision & 0xFF);
        buf[off++] = (byte) (m_rid != -1 ? m_subAuthCnt + 1 : m_subAuthCnt);

        for (int i = 0; i < 6; i++)
            buf[off++] = m_identAuth[i];

        if (m_subAuth != null) {
            for (int i = 0; i < m_subAuth.length; i++) {
                DataPacker.putIntelInt(m_subAuth[i], buf, off);
                off += 4;
            }
        }

        if (m_rid != -1) {
            DataPacker.putIntelInt(m_rid, buf, off);
            off += 4;
        }

        return off;
    }

    /**
     * Save the SID to the specified buffer
     *
     * @param buf DataBuffer
     * @return int
     * @throws SaveException Failed to save the SID
     */
    public final int saveSID(DataBuffer buf)
            throws SaveException {

        //	Pack the SID
        buf.putByte(m_revision);
        buf.putByte(m_rid != -1 ? m_subAuthCnt + 1 : m_subAuthCnt);

        for (int i = 0; i < 6; i++)
            buf.putByte(m_identAuth[i]);

        if (m_subAuth != null) {
            for (int i = 0; i < m_subAuth.length; i++)
                buf.putInt(m_subAuth[i]);
        }

        if (m_rid != -1)
            buf.putInt(m_rid);

        return buf.getPosition();
    }

    /**
     * Return the SID as a string, in the standard 'S-r-a-s-s-s-r' format
     *
     * @return String
     */
    public String toString() {

        //	Check if the name has been set
        if (hasName())
            return getName();

        //	Build the SID string
        StringBuffer str = new StringBuffer();

        str.append("S-");
        str.append(getRevision());
        str.append("-");
        str.append((int) m_identAuth[5]);

        for (int i = 0; i < m_subAuth.length; i++) {
            str.append("-");
            str.append(getSubauthority(i));
        }

        if (getRID() != -1) {
            str.append("-");
            str.append(getRID());
        }

        return str.toString();
    }
}
