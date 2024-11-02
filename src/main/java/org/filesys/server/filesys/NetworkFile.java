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

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

import org.filesys.debug.Debug;
import org.filesys.locking.FileLock;
import org.filesys.locking.FileLockList;
import org.filesys.server.locking.OpLockDetails;
import org.filesys.server.locking.OplockOwner;
import org.filesys.smb.OpLockType;

/**
 * <p>
 * The network file represents a file or directory on a filesystem. The server keeps track of the
 * open files on a per session basis.
 *
 * <p>
 * This class may be extended as required by your own disk driver class.
 *
 * @author gkspencer
 */
public abstract class NetworkFile {

    // Granted file access types
    public enum Access {
        ATTRIBUTES_ONLY (0),
        READ_ONLY       (1),
        WRITE_ONLY      (2),
        READ_WRITE      (3);

        private final int accessType;

        /**
         * Enum constructor
         *
         * @param typ int
         */
        Access(int typ) { accessType = typ; }

        /**
         * Return the access type as an integer
         *
         * @return int
         */
        public final int intValue() { return accessType; }
    }

    // File status flags
    public enum Flags {
        IO_PENDING,
        DELETE_ON_CLOSE,
        DELAYED_WRITE_ERROR,
        CREATED,
        DELAYED_CLOSE,
        CLOSED,
        FORCE_CLOSE,
        PREVIOUS_VERSION,
        POST_CLOSE_FILE,            // close the file using the same worker thread that processes the client close request but after the
                                    // protocol layer has responded to the client
        END_OF_FILE,                // read/write is at end of file
        CREATE_FILE,                // File should be created if it does not exist
        DISALLOW_SET_CREATETIME,    // do not allow setting of creation date/time via this file handle
        DISALLOW_SET_ACCESSTIME,    // do not allow setting of access date/time via this file handle
        DISALLOW_SET_MODIFYTIME,    // do not allow setting of the modify date/time via this file handle
        CLIENT_API                  // file is a special client API file
    };

    // File identifier and parent directory identifier
    protected int m_fid;
    protected int m_dirId;

    // Unique file identifier
    protected long m_uniqueId;

    // File handle id (protocol level id/handle)
    private int m_protocolId = -1;

    // File/directory name
    protected String m_name;

    // Stream name and id
    protected String m_streamName;
    protected int m_streamId;

    // Full name, relative to the share
    protected String m_fullName;

    // File attributes
    protected int m_attrib;

    // File size
    protected long m_fileSize;

    // File creation/modify/last access date/time
    protected long m_createDate;
    protected long m_modifyDate;
    protected long m_accessDate;

    // Track whether file date needs updating on file close
    protected boolean m_modifyDateDirty;

    // Granted file access type
    protected Access m_grantedAccess;

    // Access mask from the open/create request, NT access mode flags
    protected int m_accessMask;

    // Allowed file access (can be different to granted file access if read-only access was requested)
    protected Access m_allowedAccess = Access.READ_WRITE;

    // Count of read/write requests to the file
    protected int m_writeCount;
    protected int m_readCount;

    // List of locks on this file by this session. The lock object will almost certainly be
    // referenced elsewhere depending upon the LockManager implementation used. If locking support is not
    // enabled for the DiskInterface implementation the lock list will not be allocated.
    //
    // This lock list is used to release locks on the file if the session abnormally terminates or
    // closes the file without releasing all locks.
    private FileLockList m_lockList;

    // File status flags
    private Set<Flags> m_flags = EnumSet.of( Flags.CLOSED);

    // Oplock details and owner
    private OpLockDetails m_oplock;
    private OplockOwner m_oplockOwner;

    // Access token object
    private FileAccessToken m_accessToken;

    // Map of handle based directory searches
    private SearchMap m_searchMap;

    // Requested id that opened this file
    private long m_requestId;

    /**
     * Create a network file object with the specified file identifier.
     *
     * @param fid int
     */
    public NetworkFile(int fid) {
        m_fid = fid;
    }

    /**
     * Create a network file with the specified file and parent directory ids
     *
     * @param fid int
     * @param did int
     */
    public NetworkFile(int fid, int did) {
        m_fid = fid;
        m_dirId = did;
    }

    /**
     * Create a network file with the specified file id, stream id and parent directory id
     *
     * @param fid  int
     * @param stid int
     * @param did  int
     */
    public NetworkFile(int fid, int stid, int did) {
        m_fid = fid;
        m_streamId = stid;
        m_dirId = did;
    }

    /**
     * Copy constructor
     *
     * <p>Copy the main details of the existing network file to this file</p>
     *
     * @param netFile NetworkFile
     */
    public NetworkFile( NetworkFile netFile) {
        m_fid = netFile.getFileId();
        m_dirId = netFile.getDirectoryId();
        m_streamId = netFile.getStreamId();

        setName( netFile.getName());
        setFullName( netFile.getFullName());
        setStreamName( getStreamName());

        setAttributes( netFile.getFileAttributes());
        setFileSize( netFile.getFileSize());


        m_createDate = netFile.getCreationDate();
        m_modifyDate = netFile.getModifyDate();
        m_accessDate = netFile.getAccessDate();
    }

    /**
     * Create a network file object with the specified file/directory name.
     *
     * @param name File name string.
     */
    public NetworkFile(String name) {
        m_name = name;
    }

    /**
     * Return the parent directory identifier
     *
     * @return int
     */
    public final int getDirectoryId() {
        return m_dirId;
    }

    /**
     * Return the file attributes.
     *
     * @return int
     */
    public final int getFileAttributes() {
        return m_attrib;
    }

    /**
     * Return the file identifier.
     *
     * @return int
     */
    public final int getFileId() {
        return m_fid;
    }

    /**
     * Get the file size, in bytes.
     *
     * @return long
     */
    public final long getFileSize() {
        return m_fileSize;
    }

    /**
     * Get the file size, in bytes.
     *
     * @return int
     */
    public final int getFileSizeInt() {
        return (int) (m_fileSize & 0x0FFFFFFFFL);
    }

    /**
     * Return the full name, relative to the share.
     *
     * @return String
     */
    public final String getFullName() {
        return m_fullName;
    }

    /**
     * Return the full name including the stream name, relative to the share.
     *
     * @return String
     */
    public final String getFullNameStream() {
        if (isStream())
            return m_fullName + m_streamName;
        else
            return m_fullName;
    }

    /**
     * Return the granted file access mode.
     *
     * @return Access
     */
    public final Access getGrantedAccess() {
        return m_grantedAccess;
    }

    /**
     * Return the access mask, from the open/create request
     *
     * @return int
     */
    public final int getAccessMask() {
        return m_accessMask;
    }

    /**
     * Return the allowed file access mode
     *
     * @return Access
     */
    public final Access getAllowedAccess() {
        return m_allowedAccess;
    }

    /**
     * Return the file/directory name.
     *
     * @return String
     */
    public String getName() {
        return m_name;
    }

    /**
     * Return the stream id, zero indicates the main file stream
     *
     * @return int
     */
    public final int getStreamId() {
        return m_streamId;
    }

    /**
     * Return the stream name, if this is a stream
     *
     * @return String
     */
    public final String getStreamName() {
        return m_streamName;
    }

    /**
     * Return the unique file identifier
     *
     * @return long
     */
    public final long getUniqueId() {
        return m_uniqueId;
    }

    /**
     * Determine if the file has been closed.
     *
     * @return boolean
     */
    public final boolean isClosed() {
        return m_flags.contains( Flags.CLOSED);
    }

    /**
     * Return the directory file attribute status.
     *
     * @return true if the file is a directory, else false.
     */

    public final boolean isDirectory() {
        return (m_attrib & FileAttribute.Directory) != 0;
    }

    /**
     * Return the hidden file attribute status.
     *
     * @return true if the file is hidden, else false.
     */

    public final boolean isHidden() {
        return (m_attrib & FileAttribute.Hidden) != 0;
    }

    /**
     * Return the read-only file attribute status.
     *
     * @return true if the file is read-only, else false.
     */

    public final boolean isReadOnly() {
        return (m_attrib & FileAttribute.ReadOnly) != 0;
    }

    /**
     * Return the system file attribute status.
     *
     * @return true if the file is a system file, else false.
     */

    public final boolean isSystem() {
        return (m_attrib & FileAttribute.System) != 0;
    }

    /**
     * Return the archived attribute status
     *
     * @return boolean
     */
    public final boolean isArchived() {
        return (m_attrib & FileAttribute.Archive) != 0;
    }

    /**
     * Check if this is a stream file
     *
     * @return boolean
     */
    public final boolean isStream() {
        return m_streamName != null;
    }

    /**
     * Check if there are active locks on this file by this session
     *
     * @return boolean
     */
    public final boolean hasLocks() {
        if (m_lockList != null && m_lockList.numberOfLocks() > 0)
            return true;
        return false;
    }

    /**
     * Check for NT attributes
     *
     * @param attr int
     * @return boolean
     */
    public final boolean hasNTAttribute(int attr) {
        return (m_attrib & attr) == attr;
    }

    /**
     * Determine if the file access date/time is valid
     *
     * @return boolean
     */
    public final boolean hasAccessDate() {
        return m_accessDate != 0L;
    }

    /**
     * Return the file access date/time
     *
     * @return long
     */
    public final long getAccessDate() {
        return m_accessDate;
    }

    /**
     * Determine if the file creation date/time is valid
     *
     * @return boolean
     */
    public final boolean hasCreationDate() {
        return m_createDate != 0L;
    }

    /**
     * Return the file creation date/time
     *
     * @return long
     */
    public final long getCreationDate() {
        return m_createDate;
    }

    /**
     * Check if a delayed write error has occurred on this file
     *
     * @return boolean
     */
    public final boolean hasDelayedWriteError() {
        return m_flags.contains( Flags.DELAYED_WRITE_ERROR);
    }

    /**
     * Check if the delete on close flag has been set for this file
     *
     * @return boolean
     */
    public final boolean hasDeleteOnClose() {
        return m_flags.contains( Flags.DELETE_ON_CLOSE);
    }

    /**
     * Check if the file has an I/O request pending
     *
     * @return boolean
     */
    public final boolean hasIOPending() {
        return m_flags.contains( Flags.IO_PENDING);
    }

    /**
     * Check if the delayed close is set
     *
     * @return boolean
     */
    public final boolean hasDelayedClose() {
        return m_flags.contains( Flags.DELAYED_CLOSE);
    }

    /**
     * Check if the end of file flag is set
     *
     * @return boolean
     */
    public final boolean isAtEndOfFile() { return m_flags.contains( Flags.END_OF_FILE); }

    /**
     * Check if the file create required flag is set
     *
     * @return boolean
     */
    public final boolean hasCreateRequired() { return m_flags.contains( Flags.CREATE_FILE); }

    /**
     * Check if the file was created during the open
     *
     * @return boolean
     */
    public final boolean wasCreated() {
        return m_flags.contains( Flags.CREATED);
    }

    /**
     * Check if the file is a previous version
     *
     * @return boolean
     */
    public final boolean isPreviousVersion() { return m_flags.contains( Flags.PREVIOUS_VERSION); }

    /**
     * Check if the file requires close file post processing
     *
     * @return boolean
     */
    public final boolean requiresPostCloseProcessing() { return m_flags.contains( Flags.POST_CLOSE_FILE); }

    /**
     * Determine if the file modification date/time is valid
     *
     * @return boolean
     */
    public boolean hasModifyDate() {
        return m_modifyDate != 0L;
    }

    /**
     * Return the file modify date/time
     *
     * @return long
     */
    public final long getModifyDate() {
        return m_modifyDate;
    }

    /**
     * Return whether the file modify date/time is dirty due to file writes. Calling
     * {@link incrementWriteCount} marks the modify date/time as dirty, setting the
     * date/time via {@link setModifyDate} resets it back to clean.
     *
     * @return boolean
     */
    public final boolean isModifyDateDirty() {
    	return m_modifyDateDirty;
    }

    /**
     * Get the write count for the file
     *
     * @return int
     */
    public final int getWriteCount() {
        return m_writeCount;
    }

    /**
     * Increment the write count
     */
    public final void incrementWriteCount() {
        m_writeCount++;
        m_modifyDateDirty = true;
    }

    /**
     * Get the read count for the file
     *
     * @return int
     */
    public final int getReadCount() {
        return m_readCount;
    }

    /**
     * Increment the read count
     */
    public final void incrementReadCount() {
        m_readCount++;
    }

    /**
     * Return the protocol file id/handle
     *
     * @return int
     */
    public final int getProtocolId() {
        return m_protocolId;
    }

    /**
     * Return the request id that opened this file
     *
     * @return long
     */
    public final long getRequestId() { return m_requestId; }

    /**
     * Check if the file is a special client API file
     *
     * @return boolean
     */
    public final boolean isClientAPIFile() { return m_flags.contains( Flags.CLIENT_API); }

    /**
     * Set the file attributes, as specified by the SMBFileAttribute class.
     *
     * @param attrib int
     */
    public final void setAttributes(int attrib) {
        m_attrib = attrib;
    }

    /**
     * Set, or clear, the delete on close flag
     *
     * @param del boolean
     */
    public final void setDeleteOnClose(boolean del) {
        setStatusFlag(Flags.DELETE_ON_CLOSE, del);
    }

    /**
     * Set the parent directory identifier
     *
     * @param dirId int
     */
    public final void setDirectoryId(int dirId) {
        m_dirId = dirId;
    }

    /**
     * Set the file identifier.
     *
     * @param fid int
     */
    public final void setFileId(int fid) {
        m_fid = fid;
    }

    /**
     * Set the file size.
     *
     * @param siz long
     */
    public final void setFileSize(long siz) {
        m_fileSize = siz;
    }

    /**
     * Set the file size.
     *
     * @param siz int
     */
    public final void setFileSize(int siz) {
        m_fileSize = siz;
    }

    /**
     * Set the full file name, relative to the share.
     *
     * @param name String
     */
    public final void setFullName(String name) {
        m_fullName = name;
    }

    /**
     * Set the granted file access mode.
     *
     * @param mode Access
     */
    public final void setGrantedAccess(Access mode) {
        m_grantedAccess = mode;
    }

    /**
     * Set the access mask, from the open/create request
     *
     * @param accessMask int
     */
    public final void setAccessMask(int accessMask) {
        m_accessMask = accessMask;
    }

    /**
     * Set the allowed access mode
     *
     * @param mode Access
     */
    public final void setAllowedAccess(Access mode) {
        m_allowedAccess = mode;
    }

    /**
     * Set the file name.
     *
     * @param name String
     */
    public final void setName(String name) {
        m_name = name;
    }

    /**
     * Set/clear the I/O pending flag
     *
     * @param pending boolean
     */
    public final void setIOPending(boolean pending) {
        setStatusFlag(Flags.IO_PENDING, pending);
    }

    /**
     * Set the stream id
     *
     * @param id int
     */
    public final void setStreamId(int id) {
        m_streamId = id;
    }

    /**
     * Set the stream name
     *
     * @param name String
     */
    public final void setStreamName(String name) {
        m_streamName = name;
    }

    /**
     * Set the file closed state.
     *
     * @param b boolean
     */
    public final synchronized void setClosed(boolean b) {
        setStatusFlag(Flags.CLOSED, b);
    }

    /**
     * Set the file access date/time
     *
     * @param dattim long
     */
    public final void setAccessDate(long dattim) {
        m_accessDate = dattim;
    }

    /**
     * Set the file creation date/time
     *
     * @param dattim long
     */
    public final void setCreationDate(long dattim) {
        m_createDate = dattim;
    }

    /**
     * Set or clear the delayed write error flag
     *
     * @param err boolean
     */
    public final void setDelayedWriteError(boolean err) {
        setStatusFlag(Flags.DELAYED_WRITE_ERROR, err);
    }

    /**
     * Set or clear the delayed close flag
     *
     * @param delayClose boolean
     */
    public final void setDelayedClose(boolean delayClose) {
        setStatusFlag(Flags.DELAYED_CLOSE, delayClose);
    }

    /**
     * Set the file modification date/time
     *
     * @param dattim long
     */
    public final void setModifyDate(long dattim) {
        m_modifyDate = dattim;
        m_modifyDateDirty = false;
    }

    /**
     * Set or clear the previous version flag
     *
     * @param prevVer boolean
     */
    public final void setPreviousVersion(boolean prevVer) { setStatusFlag(Flags.PREVIOUS_VERSION, prevVer); }

    /**
     * Set the end of file flag
     *
     * @param eof boolean
     */
    public final void setEndOfFile(boolean eof) { setStatusFlag(Flags.END_OF_FILE, eof); }

    /**
     * Set the file create required flag
     *
     * @param create boolean
     */
    public final void setCreateRequired(boolean create) { setStatusFlag(Flags.CREATE_FILE, create); }

    /**
     * Check if the specific status flag is set
     *
     * @param flg Flags
     * @return boolean
     */
    public final boolean hasStatusFlag(Flags flg) {
        return m_flags.contains( flg);
    }

    /**
     * Set/clear a file status flag
     *
     * @param flag Flags
     * @param sts  boolean
     */
    public final synchronized void setStatusFlag(Flags flag, boolean sts) {
        if ( sts)
            m_flags.add( flag);
        else
            m_flags.remove( flag);
    }

    /**
     * Set the request id that opened this file
     *
     * @param reqId long
     */
    public final void setRequestId(long reqId) { m_requestId = reqId; }

    /**
     * Add a lock to the active lock list
     *
     * @param lock FileLock
     */
    public final synchronized void addLock(FileLock lock) {

        // Check if the lock list has been allocated
        if (m_lockList == null)
            m_lockList = new FileLockList();

        // Add the lock
        m_lockList.addLock(lock);
    }

    /**
     * Remove a lock from the active lock list
     *
     * @param lock FileLock
     */
    public final synchronized void removeLock(FileLock lock) {

        // Check if the lock list is allocated
        if (m_lockList == null)
            return;

        // Remove the lock
        m_lockList.removeLock(lock);
    }

    /**
     * Remove all locks from the lock list
     */
    public final synchronized void removeAllLocks() {

        // Check if the lock list is valid
        if (m_lockList != null)
            m_lockList.removeAllLocks();
    }

    /**
     * Return the count of active locks
     *
     * @return int
     */
    public final int numberOfLocks() {

        // Check if the lock list is allocated
        if (m_lockList == null)
            return 0;
        return m_lockList.numberOfLocks();
    }

    /**
     * Get the details of an active lock from the list
     *
     * @param idx int
     * @return FileLock
     */
    public final FileLock getLockAt(int idx) {

        // Check if the lock list is allocated and the index is valid
        if (m_lockList != null)
            return m_lockList.getLockAt(idx);

        // Invalid index or lock list not valid
        return null;
    }

    /**
     * Return the lock list
     *
     * @return FileLockList
     */
    public final FileLockList getLockList() {
        return m_lockList;
    }

    /**
     * Check if there is an oplock on this file/handle
     *
     * @return boolean
     */
    public final boolean hasOpLock() {
        return m_oplock != null;
    }

    /**
     * Return the oplock type, or type none if there is no oplock on the file/folder
     *
     * @return OplockType
     */
    public final OpLockType hasOplockType() {
        if ( m_oplock != null)
            return m_oplock.getLockType();
        return OpLockType.LEVEL_NONE;
    }

    /**
     * Return the oplock details
     *
     * @return OpLockDetails
     */
    public final OpLockDetails getOpLock() {
        return m_oplock;
    }

    /**
     * Check if there is an oplock owner
     *
     * @return boolean
     */
    public final boolean hasOplockOwner() { return m_oplockOwner != null; }

    /**
     * Return the oplock owner
     *
     * @return OplockOwner
     */
    public final OplockOwner getOplockOwner() { return m_oplockOwner; }

    /**
     * Set/clear the oplock on this file
     *
     * @param oplock OpLockDetails
     * @param owner OplockOwner
     */
    public final void setOpLock(OpLockDetails oplock, OplockOwner owner) {
        m_oplock = oplock;
        m_oplockOwner = owner;
    }

    /**
     * Set the unique file identifier
     *
     * @param id long
     */
    protected final void setUniqueId(long id) {
        m_uniqueId = id;
    }

    /**
     * Set the unique id using the file and directory id
     *
     * @param fid int
     * @param did int
     */
    protected final void setUniqueId(int fid, int did) {
        m_uniqueId = ((long) did << 32) + (long) fid;
    }

    /**
     * Set the unique id using the full path string
     *
     * @param path String
     */
    protected final void setUniqueId(String path) {
        m_uniqueId = path.toUpperCase().hashCode();
    }

    /**
     * Set the protocol level file id/handle
     *
     * @param id int
     */
    public final void setProtocolId(int id) {
        m_protocolId = id;
    }

    /**
     * Check if the file has an access token
     *
     * @return boolean
     */
    public final boolean hasAccessToken() {
        return m_accessToken != null;
    }

    /**
     * Return the access token
     *
     * @return FileAccessToken
     */
    public final FileAccessToken getAccessToken() {
        return m_accessToken;
    }

    /**
     * Set, or clear, the access token
     *
     * @param token FileAccessToken
     */
    public final void setAccessToken(FileAccessToken token) {
        m_accessToken = token;
    }

    /**
     * Allocate a slot in the active searches list for a new search.
     *
     * @param searchId int
     * @return boolean  true if the searchId is unique and a slot has been allocated, false if there are no more
     *                  search slots or the searchId is already in use
     * @exception TooManySearchesException Too many active searches
     */
    public synchronized final boolean allocateSearchSlotWithId(int searchId)
            throws TooManySearchesException {

        if ( m_searchMap == null)
            m_searchMap = new HashedSearchMap();

        return m_searchMap.allocateSearchSlotWithId( searchId);
    }

    /**
     * Deallocate the specified search context/slot.
     *
     * @param ctxId int
     */
    public synchronized final void deallocateSearchSlot(int ctxId) {
        if ( m_searchMap != null)
            m_searchMap.deallocateSearchSlot( ctxId);
    }

    /**
     * Return the search context for the specified search id.
     *
     * @param srchId int
     * @return SearchContext
     */
    public synchronized final SearchContext getSearchContext(int srchId) {
        if ( m_searchMap == null)
            return null;
        return m_searchMap.findSearchContext( srchId);
    }

    /**
     * Store the seach context in the specified slot.
     *
     * @param slot Slot to store the search context.
     * @param srch SearchContext
     */
    public synchronized final void setSearchContext(int slot, SearchContext srch) {
        if ( m_searchMap != null)
            m_searchMap.setSearchContext( slot, srch);
    }

    /**
     * Return the number of active tree searches.
     *
     * @return int
     */
    public synchronized final int getSearchCount() {
        if ( m_searchMap == null)
            return 0;
        return m_searchMap.numberOfSearches();
    }

    /**
     * Get file information from the open file details
     *
     * @return FileInfo
     */
    public final FileInfo getFileInformation() {
        FileInfo finfo = new FileInfo( getName(), getFileSize(), getFileAttributes());

        finfo.setCreationDateTime( getCreationDate());
        finfo.setModifyDateTime( getModifyDate());
        finfo.setChangeDateTime( getModifyDate());
        finfo.setAccessDateTime( getAccessDate());

        finfo.setFileId( getFileId());
        finfo.setFileType( isDirectory() ? FileType.Directory : FileType.RegularFile);

        return finfo;
    }

    /**
     * Open the file
     *
     * @param createFlag boolean
     * @throws IOException Failed to open the file
     */
    public abstract void openFile(boolean createFlag)
            throws IOException;

    /**
     * Read from the file.
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @return Length of data read.
     * @throws IOException Failed to read from the file
     */
    public abstract int readFile(byte[] buf, int len, int pos, long fileOff)
            throws java.io.IOException;

    /**
     * Write a block of data to the specified offset within file.
     *
     * @param buf     byte[] buffer to write
     * @param len     number of bytes to write from the buffer
     * @param pos     offset within the buffer to write
     * @param fileOff long offset within the file to write.
     * @throws IOException Failed to write to the file
     */
    public abstract void writeFile(byte[] buf, int len, int pos, long fileOff)
            throws java.io.IOException;

    /**
     * Seek to the specified file position.
     *
     * @param pos long
     * @param typ int
     * @return int
     * @throws IOException Failed to move the file pointer
     */
    public abstract long seekFile(long pos, int typ)
            throws IOException;

    /**
     * Flush any buffered output to the file
     *
     * @throws IOException Failed to flush the file
     */
    public abstract void flushFile()
            throws IOException;

    /**
     * Truncate the file to the specified file size
     *
     * @param siz long
     * @throws IOException Failed to truncate the file
     */
    public abstract void truncateFile(long siz)
            throws IOException;

    /**
     * Implementation of close
     *
     * @exception IOException I/O error
     */
    public abstract void closeFile()
            throws IOException;

    /**
     * Close the file
     *
     * @exception IOException I/O error
     */
    public void close()
            throws IOException {
        closeFile();
    }

    /**
     * Indicate whether the file can be opened/closed via the NetworkFile methods rather than the DiskInterface.
     * <p>
     * This is primarily for use by the NFS server code when caching open files. If possible the server will
     * close the network file to free up file handles, but keep it in the NFS file cache for a short while
     * in case the file is used again.
     * <p>
     * Override this method to prevent the NFS server from closing the file until the file is removed from
     * the NFS file cache, by returning false.
     *
     * @return boolean
     */
    public boolean allowsOpenCloseViaNetworkFile() {
        return true;
    }

    /**
     * Check if the file close is a forced close
     *
     * @return boolean
     */
    public boolean isForce() {
        return m_flags.contains( Flags.FORCE_CLOSE);
    }

    /**
     * Indicate this is a forced close
     *
     * @param force boolean
     */
    public void setForce(boolean force) {
        setStatusFlag( Flags.FORCE_CLOSE, force);
    }

    /**
     * Close any active handle based directory searches that are attached to this directory file
     */
    public synchronized final void closeSearches() {
        if ( m_searchMap != null)
            m_searchMap.closeAllSearches();
    }

    /**
     * Return the file details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(getFullName());

        str.append(" ");
        str.append(isDirectory() ? "D" : "-");
        str.append(isArchived() ? "A" : "-");
        str.append(isSystem() ? "S" : "-");
        str.append(isHidden() ? "H" : "-");

        str.append(" ");
        str.append(getFileSize());

        str.append(":");
        str.append(getFileId());
        str.append("/");
        str.append(getDirectoryId());

        if ( isPreviousVersion())
            str.append( " Ver");

        if ( isClientAPIFile())
            str.append( " ClientAPI");

        if ( hasAccessToken()) {
            str.append(",Token=");
            str.append( getAccessToken());
        }

        if ( getUniqueId() != 0) {
            str.append(",UniqueId=");
            str.append( getUniqueId());
        }

        if ( isClosed())
            str.append( ",Closed");

        str.append(", ReqId=");
        str.append( getRequestId());

        str.append("]");

        return str.toString();
    }
}
