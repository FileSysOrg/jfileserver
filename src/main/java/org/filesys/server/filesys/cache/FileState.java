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

package org.filesys.server.filesys.cache;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;

import org.filesys.debug.Debug;
import org.filesys.locking.FileLock;
import org.filesys.locking.FileLockList;
import org.filesys.locking.LockConflictException;
import org.filesys.locking.NotLockedException;
import org.filesys.server.filesys.ExistingOpLockException;
import org.filesys.server.filesys.FileName;
import org.filesys.server.filesys.FileOpenParams;
import org.filesys.server.filesys.FileStatus;
import org.filesys.server.filesys.pseudo.PseudoFile;
import org.filesys.server.filesys.pseudo.PseudoFileList;
import org.filesys.server.locking.OpLockDetails;
import org.filesys.smb.SharingMode;

/**
 * File State Class
 *
 * <p>Caches information about a file/directory so that the core server does not need
 * to make calls to the shared device driver.
 *
 * @author gkspencer
 */
public abstract class FileState implements Serializable {

    // Serialization id
    private static final long serialVersionUID = 1L;

    //	File state constants
    public final static long NoTimeout      = -1L;
    public final static long DefTimeout     = 2 * 60000L;    // 2 minutes
    public final static long RenameTimeout  = 1 * 60000L;   // 1 minute
    public final static long DeleteTimeout  = 15000L;        // 15 seconds

    public final static int UnknownFileId   = -1;

    //	File data status codes
    public enum DataStatus {
        Unknown,
        LoadWait,
        Loading,
        Available,
        Updated,
        SaveWait,
        Saving,
        Saved,
        Deleted,
        Renamed,
        DeleteOnClose
    }

    //	Standard file information keys
    public static final String FileInformation = "FileInfo";
    public static final String StreamsList = "StreamsList";

    // File status change reason codes
    public enum ChangeReason {
        None,
        FileCreated,
        FolderCreated,
        FileDeleted,
        FolderDeleted
    }

    //	File name/path
    private String m_path;

    //	File state timeout, -1 indicates no timeout
    private long m_tmo;

    //	File status, indicates if the file/folder exists and if it is a file or folder.
    private FileStatus m_fileStatus;

    //	Open file count
    private int m_openCount;

    // Sharing mode and PID of first process to open the file
    private SharingMode m_sharedAccess = SharingMode.ALL;
    private int m_pid = -1;

    //	File lock list, allocated once there are active locks on this file
    private FileLockList m_lockList;

    // Oplock details
    private OpLockDetails m_oplock;

    //	Retention period expiry date/time
    private long m_retainUntil = -1L;

    // File timestamps updated only whilst file is open
    private long m_accessDate;
    private long m_modifyDate;
    private long m_changeDate;

    // File size and allocation size
    private long m_fileSize = -1;
    private long m_allocSize;

    /**
     * Default constructor
     */
    public FileState() {
    }

    /**
     * Class constructor
     *
     * @param fname         String
     * @param caseSensitive boolean
     */
    public FileState(String fname, boolean caseSensitive) {

        //	Normalize the file path
        setPath(fname, caseSensitive);
        setExpiryTime(System.currentTimeMillis() + DefTimeout);

        //	Set the file/folder status
        m_fileStatus = FileStatus.Unknown;
    }

    /**
     * Class constructor
     *
     * @param fname         String
     * @param status        FileStatus
     * @param caseSensitive boolean
     */
    public FileState(String fname, FileStatus status, boolean caseSensitive) {

        //	Normalize the file path
        setPath(fname, caseSensitive);
        setExpiryTime(System.currentTimeMillis() + DefTimeout);

        //	Set the file/folder status
        m_fileStatus = status;
    }

    /**
     * Return the file name/path
     *
     * @return String
     */
    public final String getPath() {
        return m_path;
    }

    /**
     * Return the file exists state
     *
     * @return boolean
     */
    public final boolean fileExists() {
        if (m_fileStatus == FileStatus.FileExists || m_fileStatus == FileStatus.DirectoryExists)
            return true;
        return false;
    }

    /**
     * Return the file status
     *
     * @return FileStatus
     */
    public final FileStatus getFileStatus() {
        return m_fileStatus;
    }

    /**
     * Return the directory state
     *
     * @return boolean
     */
    public final boolean isDirectory() {
        return m_fileStatus == FileStatus.DirectoryExists ? true : false;
    }

    /**
     * Return the file open count
     *
     * @return int
     */
    public int getOpenCount() {
        return m_openCount;
    }

    /**
     * Get the file id
     *
     * @return int
     */
    public abstract int getFileId();

    /**
     * Return the shared access mode
     *
     * @return SharingMode
     */
    public final SharingMode getSharedAccess() {
        return m_sharedAccess;
    }

    /**
     * Return the PID of the first process to open the file, or -1 if the file is not open
     *
     * @return int
     */
    public final int getProcessId() {
        return m_pid;
    }

    /**
     * Return the file data status
     *
     * @return DataStatus
     */
    public abstract DataStatus getDataStatus();

    /**
     * Check if there are active locks on this file
     *
     * @return boolean
     */
    public final boolean hasActiveLocks() {
        if (m_lockList != null && m_lockList.numberOfLocks() > 0)
            return true;
        return false;
    }

    /**
     * Return the active file locks list
     *
     * @return FileLockList
     */
    public final FileLockList getLockList() {
        return m_lockList;
    }

    /**
     * Check if this file state does not expire
     *
     * @return boolean
     */
    public final boolean isPermanentState() {
        return m_tmo == NoTimeout ? true : false;
    }

    /**
     * Check if the file/folder is under retention
     *
     * @return boolean
     */
    public final boolean hasActiveRetentionPeriod() {
        if (m_retainUntil == -1L)
            return false;
        return System.currentTimeMillis() < m_retainUntil ? true : false;
    }

    /**
     * Get the retention period expiry date/time for the file/folder
     *
     * @return long
     */
    public final long getRetentionExpiryDateTime() {
        return m_retainUntil;
    }

    /**
     * Determine if the file/folder exists
     *
     * @return boolen
     */
    public final boolean exists() {
        if (m_fileStatus == FileStatus.FileExists ||
                m_fileStatus == FileStatus.DirectoryExists)
            return true;
        return false;
    }

    /**
     * Check if the file can be opened depending on any current file opens and the sharing mode of the
     * first file open
     *
     * @param params FileOpenParams
     * @return boolean
     */
    public final boolean allowsOpen(FileOpenParams params) {

        //	If the file is not currently open then allow the file open
        if (getOpenCount() == 0)
            return true;

        //	Check the shared access mode
        if (getSharedAccess() == SharingMode.READ_WRITE &&
                params.getAccessMode() == SharingMode.READ_WRITE.intValue())
            return true;
        else if (getSharedAccess().hasRead() && params.isReadOnlyAccess())
            return true;
        else if (getSharedAccess().hasWrite() && params.isWriteOnlyAccess())
            return true;

        //	Sharing violation, do not allow the file open
        return false;
    }

    /**
     * Increment the file open count
     *
     * @return int
     */
    public synchronized int incrementOpenCount() {
        return ++m_openCount;
    }

    /**
     * Decrement the file open count
     *
     * @return int
     */
    public synchronized int decrementOpenCount() {

        if (m_openCount > 0)
            m_openCount--;

        return m_openCount;
    }

    /**
     * Check if the file state has expired
     *
     * @param curTime long
     * @return boolean
     */
    public final boolean hasExpired(long curTime) {
        if (m_tmo == NoTimeout)
            return false;
        if (curTime > m_tmo)
            return true;
        return false;
    }

    /**
     * Return the number of seconds left before the file state expires
     *
     * @param curTime long
     * @return long
     */
    public final long getSecondsToExpire(long curTime) {
        if (m_tmo == NoTimeout)
            return -1;
        return (m_tmo - curTime) / 1000L;
    }

    /**
     * Set the file status
     *
     * @param status FileStatus
     */
    public void setFileStatus(FileStatus status) {
        setFileStatus(status, ChangeReason.None);
    }

    /**
     * Set the file status
     *
     * @param status FileStatus
     * @param reason ChangeReason
     */
    public void setFileStatus(FileStatus status, ChangeReason reason) {
        m_fileStatus = status;
    }

    /**
     * Set the file identifier
     *
     * @param id int
     */
    public abstract void setFileId(int id);

    /**
     * Set the file state expiry time
     *
     * @param expire long
     */
    public void setExpiryTime(long expire) {
        m_tmo = expire;
    }

    /**
     * Set the retention period expiry date/time
     *
     * @param expires long
     */
    public void setRetentionExpiryDateTime(long expires) {
        m_retainUntil = expires;
    }

    /**
     * Set the shared access mode, from the first file open
     *
     * @param mode SharingMode
     */
    public void setSharedAccess(SharingMode mode) {
        if (getOpenCount() == 0)
            m_sharedAccess = mode;
    }

    /**
     * Set the file data status
     *
     * @param sts DataStatus
     */
    public abstract void setDataStatus(DataStatus sts);

    /**
     * Add an attribute to the file state
     *
     * @param name String
     * @param attr Object
     */
    public final void addAttribute(String name, Object attr) {
        HashMap<String, Object> stateAttrs = getAttributeMap(true);
        if (stateAttrs != null)
            stateAttrs.put(name, attr);
    }

    /**
     * Find an attribute
     *
     * @param name String
     * @return Object
     */
    public final Object findAttribute(String name) {
        HashMap<String, Object> stateAttrs = getAttributeMap(true);
        Object attrObj = null;

        if (stateAttrs != null)
            attrObj = stateAttrs.get(name);

        return attrObj;
    }

    /**
     * Return the count of attributes on this file state
     *
     * @return int
     */
    public final int numberOfAttributes() {
        HashMap<String, Object> stateAttrs = getAttributeMap(false);
        if (stateAttrs != null)
            return stateAttrs.size();
        return 0;
    }

    /**
     * Remove an attribute from the file state
     *
     * @param name String
     * @return Object
     */
    public final Object removeAttribute(String name) {
        HashMap<String, Object> stateAttrs = getAttributeMap(true);
        Object attrObj = null;

        if (stateAttrs != null)
            attrObj = stateAttrs.remove(name);

        return attrObj;
    }

    /**
     * Remove all attributes from the file state
     */
    public final void removeAllAttributes() {
        HashMap<String, Object> stateAttrs = getAttributeMap(false);
        if (stateAttrs != null)
            stateAttrs.clear();
    }

    /**
     * Return the map of additional attribute objects attached to this file state, and
     * optionally create the map if it does not exist
     *
     * @param createMap boolean
     * @return HashMap
     */
    protected abstract HashMap<String, Object> getAttributeMap(boolean createMap);

    /**
     * Set the file path
     *
     * @param path          String
     * @param caseSensitive boolean
     */
    public final void setPath(String path, boolean caseSensitive) {

        //	Split the path into directories and file name, only uppercase the directories to normalize
        //	the path.
        m_path = normalizePath(path, caseSensitive);
    }

    /**
     * Set the file path, using a normalized path, no need to normalize
     *
     * @param path String
     */
    public final void setPathInternal(String path) {
        m_path = path;
    }

    /**
     * Set the PID of the process opening the file
     *
     * @param pid int
     */
    public void setProcessId(int pid) {
        if (getOpenCount() == 0)
            m_pid = pid;
    }

    /**
     * Return the count of active locks on this file
     *
     * @return int
     */
    public final int numberOfLocks() {
        if (m_lockList != null)
            return m_lockList.numberOfLocks();
        return 0;
    }

    /**
     * Add a lock to this file
     *
     * @param lock FileLock
     * @throws LockConflictException Lock conflicts with an existing lock
     */
    public void addLock(FileLock lock)
            throws LockConflictException {

        //	Check if the lock list has been allocated
        if (m_lockList == null) {

            synchronized (this) {

                //	Allocate the lock list, check if the lock list has been allocated elsewhere
                //	as we may have been waiting for the lock
                if (m_lockList == null)
                    m_lockList = new FileLockList();
            }
        }

        //	Add the lock to the list, check if there are any lock conflicts
        synchronized (m_lockList) {

            //	Check if the new lock overlaps with any existing locks
            if (m_lockList.allowsLock(lock)) {

                //	Add the new lock to the list
                m_lockList.addLock(lock);
            } else
                throw new LockConflictException();
        }
    }

    /**
     * Remove a lock on this file
     *
     * @param lock FileLock
     * @throws NotLockedException Lock does not exist
     */
    public void removeLock(FileLock lock)
            throws NotLockedException {

        //	Check if the lock list has been allocated
        if (m_lockList == null)
            throw new NotLockedException();

        //	Remove the lock from the active list
        synchronized (m_lockList) {

            //	Remove the lock, check if we found the matching lock
            if (m_lockList.removeLock(lock) == null)
                throw new NotLockedException();
        }
    }

    /**
     * Check if the file is readable for the specified section of the file and process id
     *
     * @param offset long
     * @param len    long
     * @param pid    int
     * @return boolean
     */
    public boolean canReadFile(long offset, long len, int pid) {

        //	Check if the lock list is valid
        if (m_lockList == null)
            return true;

        //	Check if the file section is readable by the specified process
        boolean readOK = false;

        synchronized (m_lockList) {

            //	Check if the file section is readable
            readOK = m_lockList.canReadFile(offset, len, pid);
        }

        //	Return the read status
        return readOK;
    }

    /**
     * Check if the file is writeable for the specified section of the file and process id
     *
     * @param offset long
     * @param len    long
     * @param pid    int
     * @return boolean
     */
    public boolean canWriteFile(long offset, long len, int pid) {

        //	Check if the lock list is valid
        if (m_lockList == null)
            return true;

        //	Check if the file section is writeable by the specified process
        boolean writeOK = false;

        synchronized (m_lockList) {

            //	Check if the file section is writeable
            writeOK = m_lockList.canWriteFile(offset, len, pid);
        }

        //	Return the write status
        return writeOK;
    }

    /**
     * Check if the file has an active oplock
     *
     * @return boolean
     */
    public boolean hasOpLock() {
        return m_oplock != null ? true : false;
    }

    /**
     * Return the oplock details
     *
     * @return OpLockDetails
     */
    public OpLockDetails getOpLock() {
        return m_oplock;
    }

    /**
     * Set the oplock for this file
     *
     * @param oplock OpLockDetails
     * @throws ExistingOpLockException If there is an active oplock on this file
     */
    public synchronized void setOpLock(OpLockDetails oplock)
            throws ExistingOpLockException {

        if (m_oplock == null)
            m_oplock = oplock;
        else
            throw new ExistingOpLockException();
    }

    /**
     * Clear the oplock
     */
    public synchronized void clearOpLock() {
        m_oplock = null;
    }

    /**
     * Determine if a folder has pseudo files associated with it
     *
     * @return boolean
     */
    public boolean hasPseudoFiles() {
        PseudoFileList pseudoList = getPseudoFileList(false);
        if (pseudoList != null)
            return pseudoList.numberOfFiles() > 0;
        return false;
    }

    /**
     * Return the pseudo file list
     *
     * @return PseudoFileList
     */
    public PseudoFileList getPseudoFileList() {
        return getPseudoFileList(false);
    }

    /**
     * Return the pseudo file list, optionally create a new list
     *
     * @param createList boolean
     * @return PseudoFileList
     */
    protected abstract PseudoFileList getPseudoFileList(boolean createList);

    /**
     * Add a pseudo file to this folder
     *
     * @param pfile PseudoFile
     */
    public final void addPseudoFile(PseudoFile pfile) {
        PseudoFileList pseudoList = getPseudoFileList(true);
        if (pseudoList != null)
            pseudoList.addFile(pfile);
    }

    /**
     * Check if the access date/time has been set
     *
     * @return boolean
     */
    public final boolean hasAccessDateTime() {
        return m_accessDate != 0L ? true : false;
    }

    /**
     * Return the access date/time
     *
     * @return long
     */
    public final long getAccessDateTime() {
        return m_accessDate;
    }

    /**
     * Update the access date/time
     */
    public void updateAccessDateTime() {
        m_accessDate = System.currentTimeMillis();
    }

    /**
     * Check if the change date/time has been set
     *
     * @return boolean
     */
    public final boolean hasChangeDateTime() {
        return m_changeDate != 0L ? true : false;
    }

    /**
     * Return the change date/time
     *
     * @return long
     */
    public final long getChangeDateTime() {
        return m_changeDate;
    }

    /**
     * Update the change date/time
     */
    public void updateChangeDateTime() {
        updateChangeDateTime(System.currentTimeMillis());
    }

    /**
     * Update the change date/time
     *
     * @param changeTime long
     */
    public void updateChangeDateTime(long changeTime) {
        m_changeDate = changeTime;
    }

    /**
     * Check if the modification date/time has been set
     *
     * @return boolean
     */
    public final boolean hasModifyDateTime() {
        return m_modifyDate != 0L ? true : false;
    }

    /**
     * Return the modify date/time
     *
     * @return long
     */
    public final long getModifyDateTime() {
        return m_modifyDate;
    }

    /**
     * Update the modify date/time
     */
    public void updateModifyDateTime() {
        long timeNow = System.currentTimeMillis();
        updateModifyDateTime(timeNow);
        m_accessDate = timeNow;
    }

    /**
     * Update the modify date/time
     *
     * @param modTime long
     */
    public void updateModifyDateTime(long modTime) {
        m_modifyDate = modTime;
    }

    /**
     * Check if there is a filesystem object
     *
     * @return boolean
     */
    public final boolean hasFilesystemObject() {
        return getFilesystemObject() != null ? true : false;
    }

    /**
     * Return the filesystem object
     *
     * @return Object
     */
    public abstract Object getFilesystemObject();

    /**
     * Set the filesystem object
     *
     * @param filesysObj Object
     */
    public abstract void setFilesystemObject(Object filesysObj);

    /**
     * Check if this is a copy file state, or the master file state object
     *
     * @return boolean
     */
    public abstract boolean isCopyState();

    /**
     * Check if the allocation size has been set
     *
     * @return boolean
     */
    public final boolean hasFileSize() {
        return m_fileSize != -1L ? true : false;
    }

    /**
     * Return the file size
     *
     * @return long
     */
    public final long getFileSize() {
        return m_fileSize;
    }

    /**
     * Set the file size
     *
     * @param fileSize long
     */
    public void setFileSize(long fileSize) {
        m_fileSize = fileSize;
    }

    /**
     * Check if the allocation size has been set
     *
     * @return boolean
     */
    public final boolean hasAllocationSize() {
        return m_allocSize > 0 ? true : false;
    }

    /**
     * Return the allocation size
     *
     * @return long
     */
    public final long getAllocationSize() {
        return m_allocSize;
    }

    /**
     * Set the allocation size
     *
     * @param allocSize long
     */
    public void setAllocationSize(long allocSize) {
        m_allocSize = allocSize;
    }

    /**
     * Set the file open count
     *
     * @param count int
     */
    public void setOpenCount(int count) {
        m_openCount = count;
    }

    /**
     * Check if there is a data update in progress for this file
     *
     * @return boolean
     */
    public boolean hasDataUpdateInProgress() {

        // No update in progress, not required for the default implementation
        return false;
    }

    /**
     * Normalize the path to uppercase the directory names and keep the case of the file name.
     *
     * @param path String
     * @return String
     */
    public final static String normalizePath(String path) {
        return normalizePath(path, true);
    }

    /**
     * Normalize the path to uppercase the directory names and keep the case of the file name.
     *
     * @param path          String
     * @param caseSensitive boolean
     * @return String
     */
    public final static String normalizePath(String path, boolean caseSensitive) {

        // Check if the file state names should be case sensitive, if not then just uppercase the whole
        // path
        String normPath = path;

        if (caseSensitive == true) {

            //	Split the path into directories and file name, only uppercase the directories to normalize
            //	the path.
            if (path.length() > 3) {

                //	Split the path to seperate the folders/file name
                int pos = path.lastIndexOf(FileName.DOS_SEPERATOR);
                if (pos != -1) {

                    //	Get the path and file name parts, normalize the path
                    String pathPart = upperCaseAToZ(path.substring(0, pos));
                    String namePart = path.substring(pos);

                    //	Rebuild the path string
                    normPath = pathPart + namePart;
                }
            }
        } else {

            // Uppercase the whole path
            normPath = upperCaseAToZ(path);
        }

        //	Return the normalized path
        return normPath;
    }

    /**
     * Uppercase a-z characters only, leave any multi-national characters as is
     *
     * @param path String
     * @return String
     */
    protected static final String upperCaseAToZ(String path) {
        StringBuilder pathStr = new StringBuilder(path);

        for (int i = 0; i < pathStr.length(); i++) {
            char curChar = pathStr.charAt(i);

            if (Character.isLowerCase(curChar))
                pathStr.setCharAt(i, Character.toUpperCase(curChar));
        }

        return pathStr.toString();
    }

    /**
     * Dump the attributes that are attached to the file state
     */
    public final void DumpAttributes() {

        //	Check if there are any attributes
        HashMap<String, Object> stateAttrs = getAttributeMap(false);

        if (stateAttrs != null) {

            //	Enumerate the available attribute objects
            Iterator<String> names = stateAttrs.keySet().iterator();

            while (names.hasNext()) {

                //	Get the current attribute name
                String name = names.next();

                //	Get the associated attribute object
                Object attrib = stateAttrs.get(name);

                //	Output the attribute details
                Debug.println("++    " + name + " : " + attrib);
            }
        } else
            Debug.println("++    No Attributes");
    }

    /**
     * Return the file state as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(getPath());
        str.append(",");
        str.append(getFileStatus().name());
        str.append(":Opn=");
        str.append(getOpenCount());

        if (getOpenCount() > 0) {
            str.append("(shr=");
            str.append(getSharedAccess().name());
            str.append(",pid=");
            str.append(getProcessId());
            str.append(")");
        }
        str.append(",Fid=");
        str.append(getFileId());

        str.append(",Expire=");
        str.append(getSecondsToExpire(System.currentTimeMillis()));

        str.append(",Sts=");
        str.append(getDataStatus().name());

        str.append(",Locks=");
        str.append(numberOfLocks());

        if (hasOpLock()) {
            str.append(",OpLock=");
            str.append(getOpLock());
        }

        str.append("]");

        return str.toString();
    }
}

