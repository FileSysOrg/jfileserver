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
import org.filesys.server.locking.OplockOwner;
import org.filesys.smb.*;

/**
 * File Open Parameters Class
 *
 * <p>Contains the details of a file open request.
 *
 * @author gkspencer
 */
public class FileOpenParams {

    //	Constants
    public final static String StreamSeparator = ":";

    //	Conversion array for Core/LanMan open actions to NT open action codes
    private static int[] _NTToLMOpenCode = {
            FileAction.TruncateExisting + FileAction.CreateNotExist,
            FileAction.OpenIfExists,
            FileAction.CreateNotExist,
            FileAction.OpenIfExists + FileAction.CreateNotExist,
            FileAction.TruncateExisting,
            FileAction.TruncateExisting + FileAction.CreateNotExist
    };

    //	File/directory to be opened
    private String m_path;

    //	Stream name
    private String m_stream;

    //	File open action
    private CreateDisposition m_openAction;

    //	Desired access mode
    private int m_accessMode;

    //	File attributes
    private int m_attr;

    //	Allocation size
    private long m_allocSize;

    //	Shared access flags
    private SharingMode m_sharedAccess = SharingMode.READ_WRITE;

    //	Creation date/time
    private long m_createDate;

    //	Root directory file id, zero if not specified
    private int m_rootFID;

    //	Create options
    private int m_createOptions;

    //	Security impersonation level, -1 if not set
    private ImpersonationLevel m_secLevel;

    //	Security flags
    private int m_secFlags;

    //	Owner group and user id
    private int m_gid = -1;
    private int m_uid = -1;

    //	Unix mode
    private int m_mode = -1;

    //  File type and symbolic name
    private FileType m_fileType;
    private String m_symName;

    // Process id
    private int m_pid;

    // 	NTCreateAndX createFlags field (extended response and oplock flags)
    private int m_createFlags;

    //  Session making the open/create request
    private SrvSession m_sess;

    // Tree id
    private int m_treeId = -1;

    // Oplock owner details, if an oplock as been requested
    private OplockOwner m_oplockOwner;

    // Timestamp of a previous version
    private long m_prevVersion;

    /**
     * Class constructor for Core SMB dialect Open SMB requests
     *
     * @param path       String
     * @param openAction int
     * @param accessMode int Access Mode
     * @param fileAttr   int
     * @param pid        int
     */
    public FileOpenParams(String path, int openAction, int accessMode, int fileAttr, int pid) {

        //	Parse the file path, split into file name and stream if specified
        parseFileName(path);

        m_openAction = convertToNTOpenAction(openAction);
        m_accessMode = convertToNTAccessMode(accessMode);
        m_attr = fileAttr;
        m_pid = pid;

        //	Check if the diectory attribute is set
        if (FileAttribute.isDirectory(m_attr))
            m_createOptions = WinNT.CreateDirectory;

        //	No security settings
        m_secLevel = ImpersonationLevel.INVALID;
    }

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
     * @param pid        int
     */
    public FileOpenParams(String path, int openAction, int accessMode, int fileAttr, int gid, int uid, int mode, int pid) {

        //	Parse the file path, split into file name and stream if specified
        parseFileName(path);

        m_openAction = convertToNTOpenAction(openAction);
        m_accessMode = convertToNTAccessMode(accessMode);
        m_attr = fileAttr;
        m_pid = pid;

        //	Check if the diectory attribute is set
        if (FileAttribute.isDirectory(m_attr))
            m_createOptions = WinNT.CreateDirectory;

        //	No security settings
        m_secLevel = ImpersonationLevel.INVALID;

        m_gid = gid;
        m_uid = uid;
        m_mode = mode;
    }

    /**
     * Class constructor for LanMan SMB dialect OpenAndX requests
     *
     * @param path       String
     * @param openAction int
     * @param accessMode int
     * @param searchAttr int
     * @param fileAttr   int
     * @param allocSize  int
     * @param createDate long
     * @param pid        int
     */
    public FileOpenParams(String path, int openAction, int accessMode, int searchAttr, int fileAttr,
                          int allocSize, long createDate, int pid) {

        //	Parse the file path, split into file name and stream if specified
        parseFileName(path);

        m_openAction = convertToNTOpenAction(openAction);
        m_accessMode = convertToNTAccessMode(accessMode);
        m_attr = fileAttr;
        m_sharedAccess = convertToNTSharedMode(accessMode);
        m_allocSize = allocSize;
        m_createDate = createDate;
        m_pid = pid;

        //	Check if the directory attribute is set
        if (FileAttribute.isDirectory(m_attr))
            m_createOptions = WinNT.CreateDirectory;

        //	No security settings
        m_secLevel = ImpersonationLevel.INVALID;
    }

    /**
     * Class constructor for NT SMB dialect NTCreateAndX requests
     *
     * @param path         String
     * @param openAction   CreateDisposition
     * @param accessMode   int NT Access Mode
     * @param attr         int
     * @param sharedAccess SharingMode
     * @param allocSize    long
     * @param createOption int
     * @param rootFID      int
     * @param secLevel     ImpersonationLevel
     * @param secFlags     int
     * @param pid          int
     */
    public FileOpenParams(String path, CreateDisposition openAction, int accessMode, int attr, SharingMode sharedAccess, long allocSize,
                          int createOption, int rootFID, ImpersonationLevel secLevel, int secFlags, int pid) {

        //	Parse the file path, split into file name and stream if specified
        parseFileName(path);

        m_openAction = openAction;
        m_accessMode = accessMode;
        m_attr = attr;
        m_sharedAccess = sharedAccess;
        m_allocSize = allocSize;
        m_createOptions = createOption;
        m_rootFID = rootFID;
        m_secLevel = secLevel;
        m_secFlags = secFlags;
        m_pid = pid;

        //	Make sure the directory attribute is set if the create directory option is set
        if ((createOption & WinNT.CreateDirectory) != 0 &&
                (m_attr & FileAttribute.Directory) == 0)
            m_attr += FileAttribute.Directory;
    }

    /**
     * Class constructor for SMB v2 dialect Create requests
     *
     * @param path         String
     * @param openAction   CreateDisposition
     * @param accessMode   int NT Access Mode
     * @param attr         int
     * @param sharedAccess SharingMode
     * @param createOption int
     * @param secLevel     ImpersonationLevel
     * @param pid          int
     */
    public FileOpenParams(String path, CreateDisposition openAction, int accessMode, int attr, SharingMode sharedAccess, int createOption,
                          ImpersonationLevel secLevel, int pid) {

        //	Parse the file path, split into file name and stream if specified
        parseFileName(path);

        m_openAction = openAction;
        m_accessMode = accessMode;
        m_attr = attr;
        m_sharedAccess = sharedAccess;
        m_createOptions = createOption;
        m_secLevel = secLevel;
        m_secFlags = 0;
        m_pid = pid;

        //	Make sure the directory attribute is set if the create directory option is set
        if ((createOption & WinNT.CreateDirectory) != 0 &&
                (m_attr & FileAttribute.Directory) == 0)
            m_attr += FileAttribute.Directory;
    }

    /**
     * Return the path to be opened/created
     *
     * @return String
     */
    public final String getPath() {
        return m_path;
    }

    /**
     * Return the full path to be opened/created, including the stream
     *
     * @return String
     */
    public final String getFullPath() {
        if (isStream())
            return m_path + m_stream;
        else
            return m_path;
    }

    /**
     * Return the file attributes
     *
     * @return int
     */
    public final int getAttributes() {
        return m_attr;
    }

    /**
     * Return the allocation size, or zero if not specified
     *
     * @return long
     */
    public final long getAllocationSize() {
        return m_allocSize;
    }

    /**
     * Determine if a creation date/time has been specified
     *
     * @return boolean
     */
    public final boolean hasCreationDateTime() {
        return m_createDate != 0L ? true : false;
    }

    /**
     * Return the file creation date/time
     *
     * @return long
     */
    public final long getCreationDateTime() {
        return m_createDate;
    }

    /**
     * Return the open/create file/directory action
     *
     * @return CreateDisposition
     */
    public final CreateDisposition getOpenAction() {
        return m_openAction;
    }

    /**
     * Return the process id
     *
     * @return int
     */
    public final int getProcessId() {
        return m_pid;
    }

    /**
     * Return the root directory file id, or zero if not specified
     *
     * @return int
     */
    public final int getRootDirectoryFID() {
        return m_rootFID;
    }

    /**
     * Return the stream name
     *
     * @return String
     */
    public final String getStreamName() {
        return m_stream;
    }

    /**
     * Check if the specified create option is enabled, specified in the WinNT class.
     *
     * @param flag int
     * @return boolean
     */
    public final boolean hasCreateOption(int flag) {
        return (m_createOptions & flag) != 0 ? true : false;
    }

    /**
     * Return the create options flags
     *
     * @return int
     */
    public final int getCreateOptions() {
        return m_createOptions;
    }

    /**
     * Check if a file stream has been specified in the path to be created/opened
     *
     * @return boolean
     */
    public final boolean isStream() {
        return m_stream != null ? true : false;
    }

    /**
     * Determine if the file is to be opened read-only
     *
     * @return boolean
     */
    public final boolean isReadOnlyAccess() {
        if ((m_accessMode & AccessMode.NTReadWrite) == AccessMode.NTRead ||
                m_accessMode == AccessMode.NTGenericRead)
            return true;
        return false;
    }

    /**
     * Determine if the file is to be opened write-only
     *
     * @return boolean
     */
    public final boolean isWriteOnlyAccess() {
        if ((m_accessMode & AccessMode.NTReadWrite) == AccessMode.NTWrite ||
                m_accessMode == AccessMode.NTGenericWrite)
            return true;
        return false;
    }

    /**
     * Determine if the file is to be opened read/write
     *
     * @return boolean
     */
    public final boolean isReadWriteAccess() {
        if ((m_accessMode & AccessMode.NTReadWrite) == AccessMode.NTReadWrite ||
                (m_accessMode & AccessMode.NTGenericReadWrite) == AccessMode.NTGenericReadWrite ||
                m_accessMode == AccessMode.NTGenericAll)
            return true;
        return false;
    }

    /**
     * Determine if the file open is to access the file attributes/metadata only
     *
     * @return boolean
     */
    public final boolean isAttributesOnlyAccess() {
        if ((m_accessMode & (AccessMode.NTReadWrite + AccessMode.NTAppend)) == 0 &&
                (m_accessMode & (AccessMode.NTReadAttrib + AccessMode.NTWriteAttrib)) != 0)
            return true;
        return false;
    }

    /**
     * Return the access mode flags
     *
     * @return int
     */
    public final int getAccessMode() {
        return m_accessMode;
    }

    /**
     * Determine if the target of the create/open is a directory
     *
     * @return boolean
     */
    public final boolean isDirectory() {
        return hasCreateOption(WinNT.CreateDirectory);
    }

    /**
     * Return the file type
     *
     * @return FileType
     */
    public final FileType isFileType() {
        return m_fileType;
    }

    /**
     * determine if the target of the create/open is a symbolic link
     *
     * @return boolean
     */
    public final boolean isSymbolicLink() {
        return isFileType() == FileType.SymbolicLink;
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
     * Determine if the file will be accessed sequentially only
     *
     * @return boolean
     */
    public final boolean isSequentialAccessOnly() {
        return hasCreateOption(WinNT.CreateSequential);
    }

    /**
     * Determine if the file should be deleted when closed
     *
     * @return boolean
     */
    public final boolean isDeleteOnClose() {
        return hasCreateOption(WinNT.CreateDeleteOnClose);
    }

    /**
     * Determine if write-through mode is enabled (buffering is not allowed if enabled)
     *
     * @return boolean
     */
    public final boolean isWriteThrough() {
        return hasCreateOption(WinNT.CreateWriteThrough);
    }

    /**
     * Determine if the open mode should overwrite/truncate an existing file
     *
     * @return boolean
     */
    public final boolean isOverwrite() {
        if (getOpenAction() == CreateDisposition.SUPERSEDE ||
                getOpenAction() == CreateDisposition.OVERWRITE ||
                getOpenAction() == CreateDisposition.OVERWRITE_IF)
            return true;
        return false;
    }

    /**
     * Return the shared access mode, zero equals allow any shared access
     *
     * @return SharingMode
     */
    public final SharingMode getSharedAccess() {
        return m_sharedAccess;
    }

    /**
     * Determine if security impersonation is enabled
     *
     * @return boolean
     */
    public final boolean hasSecurityLevel() {
        return m_secLevel != ImpersonationLevel.INVALID ? true : false;
    }

    /**
     * Return the security impersonation level. Levels are defined in the WinNT class.
     *
     * @return ImpersonationLevel
     */
    public final ImpersonationLevel getSecurityLevel() {
        return m_secLevel;
    }

    /**
     * Determine if the security context tracking flag is enabled
     *
     * @return boolean
     */
    public final boolean hasSecurityContextTracking() {
        return (m_secFlags & NTSecurity.ContextTracking) != 0 ? true : false;
    }

    /**
     * Determine if the security effective only flag is enabled
     *
     * @return boolean
     */
    public final boolean hasSecurityEffectiveOnly() {
        return (m_secFlags & NTSecurity.EffectiveOnly) != 0 ? true : false;
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
     * Check if the open is for a previous version of a file
     *
     * @return boolean
     */
    public final boolean isPreviousVersion() {
        return m_prevVersion != 0L ? true : false;
    }

    /**
     * Return the previous version timestamp
     *
     * @return long
     */
    public final long getPreviousVersionDateTime() {
        return m_prevVersion;
    }

    /**
     * Return the requested oplock type
     *
     * @return OplockType
     */
    public final OpLockType requestedOplockType() {
        OpLockType lockTyp = OpLockType.LEVEL_NONE;

        if ( requestBatchOpLock())
            lockTyp = OpLockType.LEVEL_BATCH;
        else if ( requestExclusiveOpLock())
            lockTyp = OpLockType.LEVEL_EXCLUSIVE;
        else if ( requestLevelIIOplock())
            lockTyp = OpLockType.LEVEL_II;

        return lockTyp;
    }

    /**
     * Check if a batch oplock was requested
     *
     * @return boolean
     */
    public final boolean requestBatchOpLock() {
        return (m_createFlags & WinNT.RequestBatchOplock) != 0 ? true : false;
    }

    /**
     * Check if an exclusive oplock was requested
     *
     * @return boolean
     */
    public final boolean requestExclusiveOpLock() {
        return (m_createFlags & WinNT.RequestExclusiveOplock) != 0 ? true : false;
    }

    /**
     * Check if a level II oplock was requested
     *
     * @return boolean
     */
    public boolean requestLevelIIOplock() {
        return false;
    }

    /**
     * Check if there is an oplock owner
     *
     * @return boolean
     */
    public final boolean hasOplockOwner() {
        return m_oplockOwner != null ? true : false;
    }

    /**
     * Return the oplock owner
     *
     * @return OplockOwner
     */
    public final OplockOwner getOplockOwner() {
        return m_oplockOwner;
    }

    /**
     * Set the oplock owner
     *
     * @param owner OplockOwner
     */
    public final void setOplockOwner(OplockOwner owner) {
        m_oplockOwner = owner;
    }

    /**
     * Check if an extended response was requested
     *
     * @return boolean
     */
    public final boolean requestExtendedResponse() {
        return (m_createFlags & WinNT.ExtendedResponse) != 0 ? true : false;
    }

    /**
     * Determine if the session has been set
     *
     * @return boolean
     */
    public final boolean hasSession() {
        return m_sess != null ? true : false;
    }

    /**
     * Return the session
     *
     * @return SrvSession
     */
    public final SrvSession getSession() {
        return m_sess;
    }

    /**
     * Check for a particular access mode
     *
     * @param mode int
     * @return boolean
     */
    public final boolean hasAccessMode(int mode) {
        return (m_accessMode & mode) == mode ? true : false;
    }

    /**
     * Check if the tree id has been set
     *
     * @return boolean
     */
    public final boolean hasTreeId() {
        return m_treeId != -1 ? true : false;
    }

    /**
     * Return the tree id
     *
     * @return int
     */
    public final int getTreeId() {
        return m_treeId;
    }

    /**
     * Set the tree id the file open is on
     *
     * @param treeId int
     */
    public final void setTreeId(int treeId) {
        m_treeId = treeId;
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
     * Set a create option flag
     *
     * @param flag int
     */
    public final void setCreateOption(int flag) {
        m_createOptions = m_createOptions | flag;
    }

    /**
     * Set the NTCreateAndX createFlags
     *
     * @param createFlags int
     */
    public final void setNTCreateFlags(int createFlags) {
        m_createFlags = createFlags;
    }

    /**
     * Return the NT create flags
     *
     * @return int
     */
    protected final int getNTCreateFlags() {
        return m_createFlags;
    }

    /**
     * Set the session that is making the open/create request
     *
     * @param sess SrvSession
     */
    public final void setSession(SrvSession sess) {
        m_sess = sess;
    }

    /**
     * Set the file type
     *
     * @param typ FileType
     */
    public final void setFileType(FileType typ) {
        m_fileType = typ;
    }

    /**
     * Set the symbolic link name
     *
     * @param name String
     */
    public final void setSymbolicLink(String name) {
        m_symName = name;
        m_fileType = FileType.SymbolicLink;
    }

    /**
     * Set the previous version timestamp
     *
     * @param tstamp long
     */
    public final void setPreviousVersionDateTime(long tstamp) {
        m_prevVersion = tstamp;
    }

    /**
     * Convert a Core/LanMan access mode to an NT access mode
     *
     * @param accessMode int
     * @return int
     */
    private final int convertToNTAccessMode(int accessMode) {

        //	Convert the Core/LanMan SMB dialect format access mode value to an NT access mode
        int mode = 0;

        switch (AccessMode.getAccessMode(accessMode)) {
            case AccessMode.ReadOnly:
                mode = AccessMode.NTRead;
                break;
            case AccessMode.WriteOnly:
                mode = AccessMode.NTWrite;
                break;
            case AccessMode.ReadWrite:
                mode = AccessMode.NTReadWrite;
                break;
        }
        return mode;
    }

    /**
     * Convert a Core/LanMan open action to a file open action
     *
     * @param openAction int
     * @return CreateDisposition
     */
    private final CreateDisposition convertToNTOpenAction(int openAction) {

        //	Convert the Core/LanMan SMB dialect open action to an NT open action
        CreateDisposition action = CreateDisposition.OPEN;

        for (int i = 0; i < _NTToLMOpenCode.length; i++) {
            if (_NTToLMOpenCode[i] == openAction)
                action = CreateDisposition.fromInt(i);
        }

        return action;
    }

    /**
     * Convert a Core/LanMan shared access to NT sharing flags
     *
     * @param sharedAccess int
     * @return SharingMode
     */
    private final SharingMode convertToNTSharedMode(int sharedAccess) {

        //	Get the shared access value from the access mask
        int shr = AccessMode.getSharingMode(sharedAccess);
        SharingMode ret = SharingMode.READ_WRITE;

        switch (shr) {
            case AccessMode.Exclusive:
                ret = SharingMode.NOSHARING;
                break;
            case AccessMode.DenyRead:
                ret = SharingMode.WRITE;
                break;
            case AccessMode.DenyWrite:
                ret = SharingMode.READ;
                break;
        }
        return ret;
    }

    /**
     * Parse a file name to split the main file name/path and stream name
     *
     * @param fileName String
     */
    private final void parseFileName(String fileName) {

        // Make sure path is relative to the root
        if (fileName != null && fileName.startsWith(FileName.DOS_SEPERATOR_STR) == false)
            fileName = FileName.DOS_SEPERATOR_STR + fileName;

        //	Check if the file name contains a stream name
        int pos = fileName.indexOf(StreamSeparator);
        if (pos == -1) {
            m_path = fileName;

            // Convert empty path to root path
            if (m_path.length() == 0)
                m_path = FileName.DOS_SEPERATOR_STR;
            return;
        }

        //	Split the main file name and stream name
        m_path = fileName.substring(0, pos);
        m_stream = fileName.substring(pos);

        // Check for the data stream name
        if (m_stream.equals(FileName.MainDataStreamName))
            m_stream = null;
        else if (m_stream.endsWith(FileName.DataStreamName))
            m_stream = m_stream.substring(0, m_stream.length() - FileName.DataStreamName.length());
    }

    /**
     * Return the file open parameters as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("[");

        str.append(getPath());

        str.append(",");
        str.append(getOpenAction().name());
        str.append(",acc=0x");
        str.append(Integer.toHexString(m_accessMode));
        str.append(",attr=0x");
        str.append(Integer.toHexString(getAttributes()));
        str.append(",alloc=");
        str.append(getAllocationSize());
        str.append(",share=");
        str.append(getSharedAccess().name());
        str.append(",pid=");
        str.append(getProcessId());

        if (getRootDirectoryFID() != 0) {
            str.append(",fid=");
            str.append(getRootDirectoryFID());
        }

        if (hasCreationDateTime()) {
            str.append(",cdate=");
            str.append(getCreationDateTime());
        }

        if (m_createOptions != 0) {
            str.append(",copt=0x");
            str.append(Integer.toHexString(m_createOptions));
        }

        if (hasSecurityLevel()) {
            str.append(",seclev=");
            str.append(getSecurityLevel().name());
            str.append(",secflg=0x");
            str.append(Integer.toHexString(m_secFlags));
        }

        if (hasGid() || hasUid()) {
            str.append(",gid=");
            str.append(getGid());
            str.append(",uid=");
            str.append(getUid());
        }

        if (getMode() != -1) {
            str.append(",mode=0");
            str.append(Integer.toOctalString(getMode()));
        }

        if (m_createFlags != 0) {
            if (requestBatchOpLock())
                str.append(",BatchOpLck");
            if (requestExclusiveOpLock())
                str.append(",ExOpLck");
            if (requestExtendedResponse())
                str.append(",ExtResp");
        }

        // Previous version open
        if ( isPreviousVersion()) {
            str.append(",PrevVer=");
            str.append( getPreviousVersionDateTime());
        }

        str.append("]");

        return str.toString();
    }
}
