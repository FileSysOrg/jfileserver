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

package org.filesys.smb;

/**
 * PC share class.
 *
 * <p>The PC share class holds the details of a network share, including the
 * required username and password access control.
 *
 * @author gkspencer
 */
public final class PCShare {

    // Flag to control whether the user name is parsed into domain and user name values when
    // specified as 'user@domain' or 'domain\\user'
    private static boolean _parseUserName = false;

    //	Domain name
    private String m_domain = null;

    // 	Node name string.
    private String m_nodename = null;

    // 	Remote share name string.
    private String m_shrname = null;

    // 	User name access control string.
    private String m_username = null;

    // 	Password access control string.
    private String m_password = null;

    // 	Remote path, relative to the share.
    private String m_path = null;

    // 	File name string.
    private String m_fname = null;

    //	Primary and secondary protocols to try connection on
    private int m_primaryProto = Protocol.UseDefault;
    private int m_secondaryProto = Protocol.UseDefault;

    // Flag to indicate if this is a null logon
    private boolean m_nullLogon;

    // Extended security negotiation flags
    private int m_extendedSecFlags;

    /**
     * Construct an empty PCShare object.
     */
    public PCShare() {
    }

    /**
     * Construct a PCShare using the supplied UNC path.
     *
     * @param netpath Network path of the remote server, in UNC format ie.
     *                \\node\\share.
     * @throws InvalidUNCPathException If the network path is invalid.
     */
    public PCShare(String netpath) throws InvalidUNCPathException {
        setNetworkPath(netpath);

        //  If the user name has not been set, use the guest account
        if (m_username == null)
            setUserName("GUEST");
    }

    /**
     * Construct a PCShare using the specified remote server and access control
     * details.
     *
     * @param nname Node name of the remote server.
     * @param shr   Share name on the remote server.
     * @param uname User name used to access the remote share.
     * @param pwd   Password used to access the remote share.
     */
    public PCShare(String nname, String shr, String uname, String pwd) {
        setNodeName(nname);
        setShareName(shr);
        setUserName(uname);
        setPassword(pwd);
    }

    /**
     * Build a share relative path using the supplied working directory and
     * file name.
     *
     * @param workdir Working directory string, relative to the root of the share.
     * @param fname   File name string.
     * @return Share relative path string.
     */
    public static String makePath(String workdir, String fname) {

        //  Create a string buffer to build the share relative path
        StringBuffer pathStr = new StringBuffer();

        //  Make sure there is a leading '\' on the path string
        if (!workdir.startsWith("\\"))
            pathStr.append("\\");
        pathStr.append(workdir);

        //  Make sure the path ends with '\'
        if (pathStr.charAt(pathStr.length() - 1) != '\\')
            pathStr.append("\\");

        //  Add the file name to the path string
        pathStr.append(fname);

        //  Return share relative the path string
        return pathStr.toString();
    }

    /**
     * Return the domain for the share.
     *
     * @return String
     */
    public final String getDomain() {
        return m_domain;
    }

    /**
     * Get the remote file name string.
     *
     * @return Remote file name string.
     */
    public final String getFileName() {
        return m_fname;
    }

    /**
     * Return the full UNC path for this PC share object.
     *
     * @return Path string of the remote share/path/file in UNC format, ie.
     * \\node\share\path\file.
     */
    public final String getNetworkPath() {

        //  Create a string buffer to build up the full network path
        StringBuffer strBuf = new StringBuffer(128);

        //  Add the node name and share name
        strBuf.append("\\\\");
        strBuf.append(getNodeName());
        strBuf.append("\\");
        strBuf.append(getShareName());

        //  Add the path, if there is one
        if (getPath() != null && getPath().length() > 0) {
            if (getPath().charAt(0) != '\\') {
                strBuf.append("\\");
            }
            strBuf.append(getPath());
        }

        //  Add the file name if there is one
        if (getFileName() != null && getFileName().length() > 0) {
            if (strBuf.charAt(strBuf.length() - 1) != '\\') {
                strBuf.append("\\");
            }
            strBuf.append(getFileName());
        }

        //  Return the network path
        return strBuf.toString();
    }

    /**
     * Get the remote node name string.
     *
     * @return Node name string.
     */
    public final String getNodeName() {
        return m_nodename;
    }

    /**
     * Get the remote password required to access the remote share.
     *
     * @return Remote password string.
     */
    public final String getPassword() {
        return m_password;
    }

    /**
     * Get the share relative path string.
     *
     * @return Share relative path string.
     */
    public final String getPath() {
        return m_path != null ? m_path : "\\";
    }

    /**
     * Return the share relative path for this PC share object.
     *
     * @return Path string of the remote share/path/file relative to the
     * share, ie. \path\file.
     */
    public final String getRelativePath() {

        //  Create a string buffer to build up the full network path
        StringBuffer strBuf = new StringBuffer(128);

        //  Add the path, if there is one
        if (getPath().length() > 0) {
            if (getPath().charAt(0) != '\\') {
                strBuf.append("\\");
            }
            strBuf.append(getPath());
        }

        //  Add the file name if there is one
        if (getFileName().length() > 0) {
            if (strBuf.charAt(strBuf.length() - 1) != '\\') {
                strBuf.append("\\");
            }
            strBuf.append(getFileName());
        }

        //  Return the network path
        return strBuf.toString();
    }

    /**
     * Get the remote share name string.
     *
     * @return Remote share name string.
     */
    public final String getShareName() {
        return m_shrname;
    }

    /**
     * Get the remote user name string.
     *
     * @return Remote user name string required to access the remote share.
     */
    public final String getUserName() {
        return m_username != null ? m_username : "";
    }

    /**
     * Get the primary protocol to connect with
     *
     * @return int
     */
    public final int getPrimaryProtocol() {
        return m_primaryProto;
    }

    /**
     * Get the secondary protocol to connect with
     *
     * @return int
     */
    public final int getSecondaryProtocol() {
        return m_secondaryProto;
    }

    /**
     * Determine if the share has a domain specified.
     *
     * @return boolean
     */
    public final boolean hasDomain() {
        return m_domain == null ? false : true;
    }

    /**
     * Check if this a null logon should be used
     *
     * @return boolean
     */
    public final boolean isNullLogon() {
        return m_nullLogon;
    }

    /**
     * Determine if extended security flags have been set
     *
     * @return boolean
     */
    public final boolean hasExtendedSecurityFlags() {
        return m_extendedSecFlags != 0 ? true : false;
    }

    /**
     * Return the extended security flags
     *
     * @return int
     */
    public final int getExtendedSecurityFlags() {
        return m_extendedSecFlags;
    }

    /**
     * Set the domain to be used during the session setup.
     *
     * @param domain String
     */
    public final void setDomain(String domain) {
        m_domain = domain;
        if (m_domain != null)
            m_domain = m_domain.toUpperCase();
    }

    /**
     * Set the extended security negotiation flags
     *
     * @param extFlags int
     */
    public final void setExtendedSecurityFlags(int extFlags) {
        m_extendedSecFlags = extFlags;
    }

    /**
     * Set the remote file name string.
     *
     * @param fn Remote file name string.
     */
    public final void setFileName(String fn) {
        m_fname = fn;
    }

    /**
     * Set the PC share from the supplied UNC path string.
     *
     * @param netpath UNC format remote file path.
     * @exception InvalidUNCPathException Invalid UNC path
     */

    public final void setNetworkPath(String netpath)
            throws InvalidUNCPathException {

        //  Take a copy of the network path
        StringBuffer path = new StringBuffer(netpath);
        for (int i = 0; i < path.length(); i++) {

            //  Convert forward slashes to back slashes
            if (path.charAt(i) == '/')
                path.setCharAt(i, '\\');
        }
        String npath = path.toString();

        //  UNC path starts with '\\'
        if (!npath.startsWith("\\\\") || npath.length() < 5)
            throw new InvalidUNCPathException(npath);

        //  Extract the node name from the network path
        int pos = 2;
        int endpos = npath.indexOf("\\", pos);

        if (endpos == -1)
            throw new InvalidUNCPathException(npath);

        setNodeName(npath.substring(pos, endpos));
        pos = endpos + 1;

        //  Extract the share name from the network path
        endpos = npath.indexOf("\\", pos);

        if (endpos == -1) {

            //  Share name is the last part of the UNC path
            setShareName(npath.substring(pos));

            //  Set the root path and clear the file name
            setPath("\\");
            setFileName("");
        }
        else {
            setShareName(npath.substring(pos, endpos));

            pos = endpos + 1;

            //  Extract the share relative path from the network path
            endpos = npath.lastIndexOf("\\");

            if (endpos != -1 && endpos > pos) {

                //  Set the share relative path, and update the current position index
                setPath(npath.substring(pos, endpos));

                //  File name is the rest of the UNC path
                setFileName(npath.substring(endpos + 1));
            }
            else {

                //  Set the share relative path to the root path
                setPath("\\");

                //  Set the file name string
                if (npath.length() > pos)
                    setFileName(npath.substring(pos));
                else
                    setFileName("");
            }
        }

        //  Check if the share name contains embedded access control
        pos = m_shrname.indexOf("%");
        if (pos != -1) {

            //  Find the end of the user name
            endpos = m_shrname.indexOf(":", pos);
            if (endpos != -1) {

                //  Extract the user name and password strings
                setUserName(m_shrname.substring(pos + 1, endpos));
                setPassword(m_shrname.substring(endpos + 1));
            }
            else {

                //  Extract the user name string
                setUserName(m_shrname.substring(pos + 1));
            }

            //  Reset the share name string, to remove the access control
            setShareName(m_shrname.substring(0, pos));
        }

        //  Check if the path has been set, if not then use the root path
        if (m_path == null || m_path.length() == 0)
            m_path = "\\";
    }

    /**
     * Set the remote node name string.
     *
     * @param nname Remote node name string.
     */

    public final void setNodeName(String nname) {
        m_nodename = nname;
    }

    /**
     * Set the null logon flag
     *
     * @param nullLogon boolean
     */
    public final void setNullLogon(boolean nullLogon) {
        m_nullLogon = nullLogon;
    }

    /**
     * Set the remote password string.
     *
     * @param pwd Remote password string, required to access the remote share.
     */

    public final void setPassword(String pwd) {
        m_password = pwd;
    }

    /**
     * Set the share relative path string.
     *
     * @param pth Share relative path string.
     */

    public final void setPath(String pth) {
        m_path = pth;
    }

    /**
     * Set the remote share name string.
     *
     * @param shr Remote share name string.
     */

    public final void setShareName(String shr) {
        m_shrname = shr;
    }

    /**
     * Set the remote user name string. The name may be in the format 'user', 'user@domain' or 'domain\\user'.
     *
     * @param uname Remote user name string.
     */

    public final void setUserName(String uname) {

        // Check if user name parsing is enabled
        if (_parseUserName == false)
            m_username = uname;
        else {

            // Check if the username contains a domain
            int pos = uname.indexOf('\\');

            if (pos != -1) {

                // Set the user name and domain, 'domain\\username' format
                m_username = uname.substring(pos + 1);
                setDomain(uname.substring(0, pos));
            }
            else if (uname.indexOf('@') != -1) {

                // Set the user name and domain, 'username@domain' format
                pos = uname.indexOf('@');
                m_username = uname.substring(0, pos);
                setDomain(uname.substring(pos + 1));
            }
            else {

                // Set the user name
                m_username = uname;
            }
        }
    }

    /**
     * Set the primary and secondary protocol order that is used to connect to the remote
     * host.
     *
     * @param pri int
     * @param sec int
     */
    public final void setProtocolOrder(int pri, int sec) {
        m_primaryProto = pri;
        m_secondaryProto = sec;
    }

    /**
     * Return the PCShare object as a string
     *
     * @return PCShare string.
     */

    public final String toString() {
        return getNetworkPath();
    }

    /**
     * Enable/disbale parsing of the user name value
     *
     * @param ena boolean
     */
    public static final void setUserNameParsing(boolean ena) {
        _parseUserName = ena;
    }
}
