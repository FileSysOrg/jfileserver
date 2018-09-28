/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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

import org.filesys.server.SrvSession;
import org.filesys.server.auth.ISMBAuthenticator;
import org.filesys.server.auth.acl.AccessControl;
import org.filesys.server.core.DeviceContext;
import org.filesys.server.core.DeviceInterface;
import org.filesys.server.core.InvalidDeviceInterfaceException;
import org.filesys.server.core.SharedDevice;

/**
 * The tree connection class holds the details of a single SMB tree connection. A tree connection
 * is a connection to a shared device.
 *
 * @author gkspencer
 */
public class TreeConnection {

    //	Maximum number of open files allowed per connection.
    public static final int MAXFILES = 8192;

    // Number of initial file slots to allocate. Number of allocated slots will be doubled
    // when required until MAXFILES is reached.
    public static final int INITIALFILES = 32;

    //	Shared device that the connection is associated with
    private SharedDevice m_shareDev;

    //	List of open files on this connection. Count of open file slots used.
    private NetworkFile[] m_files;
    private int m_fileCount;

    //	Access permission that the user has been granted
    private ISMBAuthenticator.ShareStatus m_permission;

    // Tree id, unique within a session
    private int m_treeId;

    /**
     * Construct a tree connection using the specified shared device.
     *
     * @param shrDev SharedDevice
     */
    public TreeConnection(SharedDevice shrDev) {
        m_shareDev = shrDev;
        m_shareDev.incrementConnectionCount();
    }

    /**
     * Construct a tree connection using the specified shared device.
     *
     * @param shrDev SharedDevice
     * @param treeId int
     */
    public TreeConnection(SharedDevice shrDev, int treeId) {
        m_shareDev = shrDev;
        m_shareDev.incrementConnectionCount();

        m_treeId = treeId;
    }

    /**
     * Return the tree id
     *
     * @return int
     */
    public final int getId() {
         return m_treeId;
    }

    /**
     * Add a network file to the list of open files for this connection.
     *
     * @param file NetworkFile
     * @param sess SrvSession
     * @return int
     * @exception TooManyFilesException Too many open files
     */
    public synchronized int addFile(NetworkFile file, SrvSession sess)
            throws TooManyFilesException {

        //  Check if the file array has been allocated
        if (m_files == null)
            m_files = new NetworkFile[INITIALFILES];

        //  Find a free slot for the network file
        int idx = 0;

        while (idx < m_files.length && m_files[idx] != null)
            idx++;

        //  Check if we found a free slot
        if (idx == m_files.length) {

            //  The file array needs to be extended, check if we reached the limit.
            if (m_files.length >= MAXFILES)
                throw new TooManyFilesException();

            //  Extend the file array
            NetworkFile[] newFiles = new NetworkFile[m_files.length * 2];
            System.arraycopy(m_files, 0, newFiles, 0, m_files.length);
            m_files = newFiles;
        }

        //	Inform listeners that a file has been opened
        NetworkFileServer fileSrv = (NetworkFileServer) sess.getServer();
        if (fileSrv != null)
            fileSrv.fireOpenFileEvent(sess, file);

        //  Store the network file, update the open file count and return the index
        m_files[idx] = file;
        m_fileCount++;

        // Save the protocol level id
        file.setProtocolId(idx);
        return idx;
    }

    /**
     * Close the tree connection, release resources.
     *
     * @param sess SrvSession
     */
    public synchronized void closeConnection(SrvSession sess) {

        //  Make sure all files are closed
        if (openFileCount() > 0) {

            //  Close all open files
            for (int idx = 0; idx < m_files.length; idx++) {

                //  Make sure the file is closed
                if (m_files[idx] != null) {

                    //  Close the file
                    try {

                        //  Access the disk interface and close the file
                        DiskInterface disk = (DiskInterface) m_shareDev.getInterface();
                        m_files[idx].setForce(true);
                        disk.closeFile(sess, this, m_files[idx]);
                        m_files[idx].setClosed(true);
                    }
                    catch (Exception ex) {
                    }

                    // Remove the file from the open file table
                    removeFile(idx, sess);
                }
            }
        }

        //	Decrement the active connection count for the shared device
        m_shareDev.decrementConnectionCount();
    }

    /**
     * Return the specified network file.
     *
     * @param fid int
     * @return NetworkFile
     */
    public synchronized NetworkFile findFile(int fid) {

        //  Check if the file id and file array are valid
        if (m_files == null || fid >= m_files.length || fid < 0)
            return null;

        //  Get the required file details
        return m_files[fid];
    }

    /**
     * Return the length of the file table
     *
     * @return int
     */
    public synchronized int getFileTableLength() {
        if (m_files == null)
            return 0;
        return m_files.length;
    }

    /**
     * Determine if the shared device has an associated context
     *
     * @return boolean
     */
    public final boolean hasContext() {
        if (m_shareDev != null)
            return m_shareDev.getContext() != null ? true : false;
        return false;
    }

    /**
     * Return the interface specific context object.
     *
     * @return Device interface context object.
     */
    public final DeviceContext getContext() {
        if (m_shareDev == null)
            return null;
        return m_shareDev.getContext();
    }

    /**
     * Return the share access permissions that the user has been granted.
     *
     * @return ShareStatus
     */
    public final ISMBAuthenticator.ShareStatus getPermission() {
        return m_permission;
    }

    /**
     * Deterimine if the access permission for the shared device allows read access
     *
     * @return boolean
     */
    public final boolean hasReadAccess() {
        if (m_permission == ISMBAuthenticator.ShareStatus.READ_ONLY ||
                m_permission == ISMBAuthenticator.ShareStatus.WRITEABLE)
            return true;
        return false;
    }

    /**
     * Determine if the access permission for the shared device allows write access
     *
     * @return boolean
     */
    public final boolean hasWriteAccess() {
        if (m_permission == ISMBAuthenticator.ShareStatus.WRITEABLE)
            return true;
        return false;
    }

    /**
     * Return the shared device that this tree connection is using.
     *
     * @return SharedDevice
     */
    public final SharedDevice getSharedDevice() {
        return m_shareDev;
    }

    /**
     * Return the shared device interface
     *
     * @return DeviceInterface
     */
    public final DeviceInterface getInterface() {
        if (m_shareDev == null)
            return null;
        try {
            return m_shareDev.getInterface();
        }
        catch (InvalidDeviceInterfaceException ex) {
        }
        return null;
    }

    /**
     * Return the count of open files on this tree connection.
     *
     * @return int
     */
    public synchronized int openFileCount() {
        return m_fileCount;
    }

    /**
     * Remove all files from the tree connection.
     */
    public synchronized final void removeAllFiles() {

        //  Check if the file array has been allocated
        if (m_files == null)
            return;

        //  Clear the file list
        for (int idx = 0; idx < m_files.length; m_files[idx++] = null) ;
        m_fileCount = 0;
    }

    /**
     * Remove a network file from the list of open files for this connection.
     *
     * @param idx  int
     * @param sess SrvSession
     */
    public synchronized void removeFile(int idx, SrvSession sess) {

        //  Range check the file index
        if (m_files == null || idx >= m_files.length)
            return;

        //	Inform listeners of the file closure
        NetworkFileServer fileSrv = (NetworkFileServer) sess.getServer();
        if (fileSrv != null)
            fileSrv.fireCloseFileEvent(sess, m_files[idx]);

        //  Remove the file and update the open file count.
        if (m_files[idx] != null)
            m_files[idx].setProtocolId(-1);
        m_files[idx] = null;
        m_fileCount--;
    }

    /**
     * Set the access permission for this share that the user has been granted.
     *
     * @param perm ShareStatus
     */
    public final void setPermission(ISMBAuthenticator.ShareStatus perm) {
        m_permission = perm;
    }

    /**
     * Set the access permission for this share by mapping from an ACL permission
     *
     * @param aclPerm int
     */
    public final void setPermission(int aclPerm) {
        switch ( aclPerm) {
            case AccessControl.NoAccess:
                m_permission = ISMBAuthenticator.ShareStatus.NO_ACCESS;
                break;
            case AccessControl.ReadOnly:
                m_permission = ISMBAuthenticator.ShareStatus.READ_ONLY;
                break;
            case AccessControl.ReadWrite:
                m_permission = ISMBAuthenticator.ShareStatus.WRITEABLE;
                break;
        }
    }

    /**
     * Return the tree connection as a string.
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();
        str.append("[");
        str.append(m_shareDev.toString());
        str.append(", id=");
        str.append(getId());
        str.append(", fileCnt=");
        str.append(openFileCount());
        str.append(", perm=");
        str.append(m_permission.name());
        str.append("]");
        return str.toString();
    }
}
