/*
 * Copyright (C) 2012 GK Spencer
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

package org.filesys.server.filesys;

/**
 * Unix File Open Parameters Class
 *
 * <p>Extends the base FileOpenParams class with unix specific values</p>
 *
 * @author gkspencer
 */
public class UnixFileOpenParams extends FileOpenParams {

    //	Owner group and user id
    private int m_gid = -1;
    private int m_uid = -1;

    //	Unix mode
    private int m_mode = -1;

    //  Symbolic link name
    private String m_symName;

    /**
     * Class constructor for Core SMB dialect Open SMB requests
     *
     * @param path       String
     * @param openAction int
     * @param accessMode int Access Mode
     * @param fileAttr   int
     * @param gid        int
     * @param uid        int
     * @param mode       int
     * @param pid        long
     */
    public UnixFileOpenParams(String path, int openAction, int accessMode, int fileAttr, int gid, int uid, int mode, long pid) {
        super(path, openAction, accessMode, fileAttr, pid);

        // Set the Unix group id, user id and mode
        m_gid = gid;
        m_uid = uid;
        m_mode = mode;
    }

    /**
     * Determine if the group id has been set
     *
     * @return boolean
     */
    public final boolean hasGid() {
        return m_gid != -1 ? true : false;
    }

    /**
     * Return the owner group id
     *
     * @return int
     */
    public final int getGid() {
        return m_gid;
    }

    /**
     * Determine if the user id has been set
     *
     * @return boolean
     */
    public final boolean hasUid() {
        return m_uid != -1 ? true : false;
    }

    /**
     * Return the owner user id
     *
     * @return int
     */
    public final int getUid() {
        return m_uid;
    }

    /**
     * Determine if the mode has been set
     *
     * @return boolean
     */
    public final boolean hasMode() {
        return m_mode != -1 ? true : false;
    }

    /**
     * Return the Unix mode
     *
     * @return int
     */
    public final int getMode() {
        return m_mode;
    }

    /**
     * Return the symbolic link name
     *
     * @return String
     */
    public final String getSymbolicLinkName() {
        return m_symName;
    }

    /**
     * Set the Unix mode
     *
     * @param mode int
     */
    public final void setMode(int mode) {
        m_mode = mode;
    }

    /**
     * Set the symbolic link name
     *
     * @param name String
     */
    public final void setSymbolicLink(String name) {
        m_symName = name;
        setFileType( FileType.SymbolicLink);
    }

}
