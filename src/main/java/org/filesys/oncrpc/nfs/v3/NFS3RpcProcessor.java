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

package org.filesys.oncrpc.nfs.v3;

import org.filesys.debug.Debug;
import org.filesys.oncrpc.Rpc;
import org.filesys.oncrpc.RpcPacket;
import org.filesys.oncrpc.nfs.*;
import org.filesys.server.auth.ISMBAuthenticator;
import org.filesys.server.auth.acl.AccessControl;
import org.filesys.server.auth.acl.AccessControlManager;
import org.filesys.server.core.InvalidDeviceInterfaceException;
import org.filesys.server.filesys.*;
import org.filesys.util.HexDump;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.EnumSet;

/**
 * NFS v3 RPC Processor Class
 *
 * @author gkspencer
 */
public class NFS3RpcProcessor implements RpcSessionProcessor {

    //	Constants
    //
    //	Unix file modes
    public static final int MODE_STFILE         = 0100000;
    public static final int MODE_STDIR          = 0040000;
    public static final int MODE_STREAD         = 0000555;
    public static final int MODE_STWRITE        = 0000333;
    public static final int MODE_DIR_DEFAULT    = MODE_STDIR + (MODE_STREAD | MODE_STWRITE);
    public static final int MODE_FILE_DEFAULT   = MODE_STFILE + (MODE_STREAD | MODE_STWRITE);

    //	Readdir/Readdirplus cookie masks/shift
    //
    //	32bit cookies (required by Solaris)
    public static final long COOKIE_RESUMEID_MASK = 0x00FFFFFFL;
    public static final long COOKIE_SEARCHID_MASK = 0xFF000000L;
    public static final int COOKIE_SEARCHID_SHIFT = 24;

    //	Cookie ids for . and .. directory entries
    public static final long COOKIE_DOT_DIRECTORY       = 0x00FFFFFFL;
    public static final long COOKIE_DOTDOT_DIRECTORY    = 0x00FFFFFEL;

    //	ReadDir and ReadDirPlus reply header and per file fixed structure lengths.
    //
    //	Add file name length rounded to 4 byte boundary to the per file structure
    // length to get the actual length.
    public final static int READDIRPLUS_HEADER_LENGTH   = 108;
    public final static int READDIRPLUS_ENTRY_LENGTH    = 200;
    public final static int READDIR_HEADER_LENGTH       = 108;
    public final static int READDIR_ENTRY_LENGTH        = 24;

    //	File id offset
    public static final long FILE_ID_OFFSET = 2L;

    //	Maximum request size to accept
    public final static int MaxRequestSize = 0xFFFF;

    //	Filesystem limits
    public static final int MaxReadSize     = MaxRequestSize;
    public static final int PrefReadSize    = MaxRequestSize;
    public static final int MultReadSize    = 4096;
    public static final int MaxWriteSize    = MaxRequestSize;
    public static final int PrefWriteSize   = MaxRequestSize;
    public static final int MultWriteSize   = 4096;
    public static final int PrefReadDirSize = 8192;
    public static final long MaxFileSize    = 0x01FFFFFFF000L;

    // RPC response packet sizes to allocate
    public static final int MaxResponseSize     = 0xFFFF;
    public static final int RespSizeReadDir     = MaxResponseSize;
    public static final int RespSizeCreate      = 512;
    public static final int RespSizeRename      = 512;
    public static final int RespSizeMkDir       = 512;
    public static final int RespSizeReadLink    = 4096;

    @Override
    public int getProgamId() {
        return NFS3.ProgramId;
    }

    @Override
    public int getVersionId() {
        return NFS3.VersionId;
    }

    @Override
    public RpcPacket processRpc(RpcPacket rpc, NFSSrvSession nfsSess) throws IOException {

        //	Position the RPC buffer pointer at the start of the call parameters
        rpc.positionAtParameters();

        //	Process the RPC request
        RpcPacket response = null;
        NFS3.ProcedureId procId = NFS3.ProcedureId.fromInt( rpc.getProcedureId());

        switch ( procId) {

            //	Null request
            case Null:
                response = procNull(nfsSess, rpc);
                break;

            // Get attributes request
            case GetAttr:
                response = procGetAttr(nfsSess, rpc);
                break;

            //	Set attributes request
            case SetAttr:
                response = procSetAttr(nfsSess, rpc);
                break;

            //	Lookup request
            case Lookup:
                response = procLookup(nfsSess, rpc);
                break;

            //	Access request
            case Access:
                response = procAccess(nfsSess, rpc);
                break;

            //	Read symbolic link request
            case ReadLink:
                response = procReadLink(nfsSess, rpc);
                break;

            //	Read file request
            case Read:
                response = procRead(nfsSess, rpc);
                break;

            //	Write file request
            case Write:
                response = procWrite(nfsSess, rpc);
                break;

            //	Create file request
            case Create:
                response = procCreate(nfsSess, rpc);
                break;

            //	Create directory request
            case MkDir:
                response = procMkDir(nfsSess, rpc);
                break;

            //	Create symbolic link request
            case SymLink:
                response = procSymLink(nfsSess, rpc);
                break;

            //	Create special device request
            case MkNode:
                response = procMkNode(nfsSess, rpc);
                break;

            //	Delete file request
            case Remove:
                response = procRemove(nfsSess, rpc);
                break;

            //	Delete directory request
            case RmDir:
                response = procRmDir(nfsSess, rpc);
                break;

            //	Rename request
            case Rename:
                response = procRename(nfsSess, rpc);
                break;

            //	Create hard link request
            case Link:
                response = procLink(nfsSess, rpc);
                break;

            //	Read directory request
            case ReadDir:
                response = procReadDir(nfsSess, rpc);
                break;

            //	Read directory plus request
            case ReadDirPlus:
                response = procReadDirPlus(nfsSess, rpc);
                break;

            //	Filesystem status request
            case FsStat:
                response = procFsStat(nfsSess, rpc);
                break;

            //	Filesystem information request
            case FsInfo:
                response = procFsInfo(nfsSess, rpc);
                break;

            //	Retrieve POSIX information request
            case PathConf:
                response = procPathConf(nfsSess, rpc);
                break;

            //	Commit request
            case Commit:
                response = procCommit(nfsSess, rpc);
                break;
        }

        // Commit/rollback a transaction that the filesystem driver may have stored in the session
        if (nfsSess != null)
            nfsSess.endTransaction();

        //	Dump the response
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.DUMPDATA)) {
            Debug.println("NFS Resp=" + (rpc != null ? rpc.toString() : "<Null>"));
            HexDump.Dump(rpc.getBuffer(), rpc.getLength(), 0);
        }

        //	Return the RPC response
        return response;
    }

    /**
     * Process the null request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procNull(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Build the response
        rpc.buildResponseHeader();
        return rpc;
    }

    /**
     * Process the get attributes request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procGetAttr(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Get the handle from the request
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.INFO))
            nfsSess.debugPrintln("GetAttr request from " + rpc.getClientDetails() + ", handle=" + NFSHandle.asString(handle));

        //	Check if the handle is valid
        if (NFSHandle.isValid(handle) == false) {

            //	Return an error status
            rpc.buildErrorResponse(NFS3.StatusCode.BadHandle.intValue());
            return rpc;
        }

        //	Build the response header
        rpc.buildResponseHeader();

        //	Check if this is a share handle
        int shareId = -1;
        String path = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        //	Call the disk share driver to get the file information for the path
        try {

            //	Get the share id and path
            shareId = getShareIdFromHandle(handle);
            TreeConnection conn = getTreeConnection(nfsSess, shareId);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasReadAccess() == false)
                throw new AccessDeniedException();

            //	Get the path from the handle
            path = getPathForHandle(nfsSess, handle, conn);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasReadAccess() == false)
                throw new AccessDeniedException();

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Get the network file details, if the file is open
            NetworkFile netFile = getOpenNetworkFileForHandle(nfsSess, handle, conn);

            //	Get the file information for the specified path
            FileInfo finfo = disk.getFileInformation(nfsSess, conn, path);
            if (finfo != null) {

                //  Blend in live file details, if the file is open
                if (netFile != null) {

                    // Update file size from open file
                    finfo.setFileSize(netFile.getFileSize());

                    //  DEBUG
                    if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.INFO))
                        nfsSess.debugPrintln("GetAttr added details from open file");
                }

                //	Pack the file information into the NFS attributes structure
                rpc.packInt(NFS3.StatusCode.Success.intValue());
                packAttributes3(rpc, finfo, shareId);

                //	DEBUG
                if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.INFO))
                    nfsSess.debugPrintln("GetAttr path=" + path + ", info=" + finfo);
            }
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR)) {
                nfsSess.debugPrintln("GetAttr Exception: " + ex.toString());
                nfsSess.debugPrintln(ex);
            }
        }

        //	Error status
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the error response
            rpc.buildErrorResponse(errorSts.intValue());

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("GetAttr error=" + errorSts.getStatusString());
        }

        //	Return the attributes
        rpc.setLength();
        return rpc;
    }

    /**
     * Process the set attributes request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procSetAttr(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Unpack the set attributes parameters
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.INFO))
            nfsSess.debugPrintln("SetAttr request from " + rpc.getClientDetails());

        //	Check if the handle is valid
        if (NFSHandle.isValid(handle) == false) {
            rpc.buildErrorResponse(NFS3.StatusCode.BadHandle.intValue());
            return rpc;
        }

        //	Check if this is a share handle
        int shareId = -1;
        String path = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        //	Call the disk share driver to get the file information for the path
        try {

            //	Get the share id and path
            shareId = getShareIdFromHandle(handle);
            TreeConnection conn = getTreeConnection(nfsSess, shareId);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasWriteAccess() == false)
                throw new AccessDeniedException();

            //	Get the path from the handle
            path = getPathForHandle(nfsSess, handle, conn);

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            //	Get the current file information
            FileInfo oldInfo = disk.getFileInformation(nfsSess, conn, path);

            //	Get the values to be set for the file/folder
            int setFlags = 0;
            int gid = -1;
            int uid = -1;
            int mode = -1;
            long fsize = -1L;
            long atime = -1L;
            long mtime = -1L;

            //	Check if the file mode has been specified
            if (rpc.unpackInt() == Rpc.True) {
                mode = rpc.unpackInt();
                setFlags += FileInfo.SetMode;
            }

            //	Check if the file owner uid has been specified
            if (rpc.unpackInt() == Rpc.True) {
                uid = rpc.unpackInt();
                setFlags += FileInfo.SetUid;
            }

            //	Check if the file group gid has been specified
            if (rpc.unpackInt() == Rpc.True) {
                gid = rpc.unpackInt();
                setFlags += FileInfo.SetGid;
            }

            //	Check if a new file size has been specified
            if (rpc.unpackInt() == Rpc.True) {
                fsize = rpc.unpackLong();
                setFlags += FileInfo.SetFileSize;
            }

            //	Check if the access date/time should be set. It may be set to a client specified time
            //	or using the server time
            NFS3.SetAttrTimestamp setTime = NFS3.SetAttrTimestamp.fromInt(rpc.unpackInt());

            if (setTime == NFS3.SetAttrTimestamp.TimeClient) {
                int timeInt = rpc.unpackInt();
                if (timeInt >= 0)
                    atime = (long) timeInt;
                else {
                    atime = (long) (timeInt & 0x7FFFFFFF);
                    atime += 0x80000000L;
                }
                atime *= 1000L;
                rpc.skipBytes(4);        //	nanoseconds
                setFlags += FileInfo.SetAccessDate;
            } else if (setTime == NFS3.SetAttrTimestamp.TimeServer) {
                atime = System.currentTimeMillis();
                setFlags += FileInfo.SetAccessDate;
            }

            //	Check if the modify date/time should be set. It may be set to a client specified time
            //	or using the server time
            setTime = NFS3.SetAttrTimestamp.fromInt(rpc.unpackInt());

            if (setTime == NFS3.SetAttrTimestamp.TimeClient) {
                int timeInt = rpc.unpackInt();
                if (timeInt >= 0)
                    mtime = (long) timeInt;
                else {
                    mtime = (long) (timeInt & 0x7FFFFFFF);
                    mtime += 0x80000000L;
                }
                mtime = (long) rpc.unpackInt();
                mtime *= 1000L;
                rpc.skipBytes(4);        //	nanoseconds
                setFlags += FileInfo.SetModifyDate;
            } else if (setTime == NFS3.SetAttrTimestamp.TimeServer) {
                mtime = System.currentTimeMillis();
                setFlags += FileInfo.SetModifyDate;
            }

            //	Check if any of the file times should be updated
            if (setFlags != 0) {

                //	Set the file access/modify date/times
                FileInfo finfo = new FileInfo();
                finfo.setFileInformationFlags(setFlags);

                if (atime != -1L)
                    finfo.setAccessDateTime(atime);

                if (mtime != -1L)
                    finfo.setModifyDateTime(mtime);

                //	Check if the group id should be set
                if (gid != -1) {

                    //	Set the group id in the file information
                    finfo.setGid(gid);
                }

                //	Check if the user id should be set
                if (uid != -1) {

                    //	Set the user id in the file information
                    finfo.setUid(uid);
                }

                //	Check if the mode should be set
                if (mode != -1) {

                    //	Set the mode in the file information
                    finfo.setMode(mode);
                }

                //	Set the file information
                disk.setFileInformation(nfsSess, conn, path, finfo);

                //	DEBUG
                if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.INFO))
                    nfsSess.debugPrintln("SetAttr handle=" + NFSHandle.asString(handle) + ", accessTime=" + finfo.getAccessDateTime() +
                            ", modifyTime=" + finfo.getModifyDateTime() + ", mode=" + mode + ", gid/uid=" + gid + "/" + uid);
            }

            //	Check if the file size should be updated
            if (fsize != -1L) {

                //	Open the file, may be cached
                NetworkFile netFile = getNetworkFileForHandle(nfsSess, handle, conn, false);

                synchronized (netFile) {

                    //	Open the network file
                    netFile.openFile(false);

                    //	Change the file size
                    disk.truncateFile(nfsSess, conn, netFile, fsize);

                    //	Close the file
//					netFile.close();
                }

                //	DEBUG
                if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.INFO))
                    nfsSess.debugPrintln("SetAttr handle=" + NFSHandle.asString(handle) + ", newSize=" + fsize);
            }

            //	Get the updated file information
            FileInfo newInfo = disk.getFileInformation(nfsSess, conn, path);

            //  Check if the file size was changed
            if (fsize != -1L)
                newInfo.setFileSize(fsize);
            else {

                // Check if the file is open, use the current size
                NetworkFile netFile = getOpenNetworkFileForHandle(nfsSess, handle, conn);
                if (netFile != null)
                    newInfo.setFileSize(netFile.getFileSize());
            }

            // Report the requested mode back to the client
//			if ( mode != -1)
//				newInfo.setMode(mode);

            // Make sure the access time is set
//			if ( newInfo.hasAccessDateTime() == false)
//				newInfo.setAccessDateTime( System.currentTimeMillis());

            //	Pack the response
            rpc.buildResponseHeader();
            rpc.packInt(NFS3.StatusCode.Success.intValue());

            packWccData(rpc, oldInfo);
            packPostOpAttr(nfsSess, newInfo, shareId, rpc);
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (DiskFullException ex) {
            errorSts = NFS3.StatusCode.DQuot;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("SetAttr Exception: " + ex.toString());
        }

        //	Check for a failure status
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the error response
            rpc.buildErrorResponse(errorSts.intValue());
            packWccData(rpc, null);

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("SetAttr error=" + errorSts.getStatusString());
        }

        //	Return a the set status
        rpc.setLength();
        return rpc;
    }

    /**
     * Process the lookup request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procLookup(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Unpack the lookup arguments
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        String fileName = rpc.unpackUTF8String();

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
            nfsSess.debugPrintln("Lookup request from " + rpc.getClientDetails() + ", handle=" + NFSHandle.asString(handle) +
                    ", name=" + fileName);

        //	Check if the handle is valid
        if (NFSHandle.isValid(handle) == false) {
            rpc.buildErrorResponse(NFS3.StatusCode.BadHandle.intValue());
            return rpc;
        }

        //	Call the disk share driver to get the file information for the path
        int shareId = -1;
        String path = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        try {

            //	Get the share id and path
            shareId = getShareIdFromHandle(handle);
            TreeConnection conn = getTreeConnection(nfsSess, shareId);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasReadAccess() == false)
                throw new AccessDeniedException();

            //	Get the path from the handle
            path = getPathForHandle(nfsSess, handle, conn);

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            //	Build the full path string
            String lookupPath = generatePath(path, fileName);

            //	Check if the file/directory exists
            if (disk.fileExists(nfsSess, conn, lookupPath) != FileStatus.NotExist) {

                //	Get file information for the path
                FileInfo finfo = disk.getFileInformation(nfsSess, conn, lookupPath);

                if (finfo != null) {

                    // Get a handle to the file
                    byte[] fHandle = getHandleForFile(nfsSess, handle, conn, fileName);

                    // Get the network file details, if the file is open
                    NetworkFile netFile = getOpenNetworkFileForHandle(nfsSess, fHandle, conn);
                    if (netFile != null)
                        finfo.setFileSize(netFile.getFileSize());

                    //	Pack the response
                    rpc.buildResponseHeader();
                    rpc.packInt(NFS3.StatusCode.Success.intValue());

                    //	Pack the file handle
                    if (finfo.isDirectory())
                        NFSHandle.packDirectoryHandle(shareId, finfo.getFileId(), rpc, NFS3.FileHandleSize);
                    else
                        NFSHandle.packFileHandle(shareId, getFileIdForHandle(handle), finfo.getFileId(), rpc, NFS3.FileHandleSize);

                    //	Pack the file attributes
                    packPostOpAttr(nfsSess, finfo, shareId, rpc);

                    //	Add a cache entry for the path
                    ShareDetails details = nfsSess.getNFSServer().findShareDetails( shareId);

                    details.getFileIdCache().addPath(finfo.getFileId(), lookupPath);

                    //	Check if the file path is a file name only, if so then get the parent directory details
                    if (pathHasDirectories(fileName) == false || fileName.equals("..")) {

                        //	Get the parent directory file information
                        FileInfo dirInfo = disk.getFileInformation(nfsSess, conn, path);
                        packPostOpAttr(nfsSess, dirInfo, shareId, rpc);

                        //	Add the path to the file id cache, if the filesystem does not support id lookups
                        if (details.hasFileIdSupport() == false)
                            details.getFileIdCache().addPath(dirInfo.getFileId(), path);
                    }

                    //	DEBUG
                    if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
                        nfsSess.debugPrintln("Lookup path=" + lookupPath + ", finfo=" + finfo.toString());
                }
            } else {

                //	File does not exist
                errorSts = NFS3.StatusCode.NoEnt;
            }
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Lookup Exception: " + ex.toString());
        }

        //	Check if an error is being returned
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the response
            rpc.buildErrorResponse(errorSts.intValue());
            packPostOpAttr(nfsSess, null, shareId, rpc);

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                Debug.println("Lookup error=" + errorSts.getStatusString());
        }

        //	Return the response
        rpc.setLength();
        return rpc;
    }

    /**
     * Process the access request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procAccess(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Get the parameters from the request
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        int accessMode = rpc.unpackInt();

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.INFO))
            nfsSess.debugPrintln("Access request from " + rpc.getClientDetails() + ", handle=" + NFSHandle.asString(handle) +
                    ", access=0x" + Integer.toHexString(accessMode));

        //	Check if the handle is valid
        if (NFSHandle.isValid(handle) == false) {

            //	Return an error status
            rpc.buildErrorResponse(NFS3.StatusCode.BadHandle.intValue());
            return rpc;
        }

        //	Check if this is a share handle
        int shareId = -1;
        String path = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        //	Call the disk share driver to get the file information for the path
        try {

            // Check if the access check is on the share handle
            if (NFSHandle.isShareHandle(handle)) {

                // Pack the response
                rpc.buildResponseHeader();
                rpc.packInt(NFS3.StatusCode.Success.intValue());

                packPostOpAttr(nfsSess, null, shareId, rpc);
                rpc.packInt(accessMode & NFS3.AccessAll);

                // DEBUG
                if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.INFO))
                    Debug.println("Access share path=" + path);

            } else {

                // Get the share id and path
                shareId = getShareIdFromHandle(handle);
                TreeConnection conn = getTreeConnection(nfsSess, shareId);
                path = getPathForHandle(nfsSess, handle, conn);

                // Get the disk interface from the disk driver
                DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

                // Get the file information for the specified path
                FileInfo finfo = disk.getFileInformation(nfsSess, conn, path);
                if (finfo != null) {

                    // Check the access that the session has to the filesystem
                    int mask = 0;

                    if (conn.hasWriteAccess()) {

                        // Set the mask to allow all operations
                        mask = NFS3.AccessAll;
                    } else if (conn.hasReadAccess()) {

                        // Set the mask for read-only operations
                        mask = NFS3.AccessRead + NFS3.AccessLookup + NFS3.AccessExecute;
                    }

                    // Check if the file is open, blend in current file details
                    NetworkFile netFile = getOpenNetworkFileForHandle(nfsSess, handle, conn);
                    if (netFile != null)
                        finfo.setFileSize(netFile.getFileSize());

                    // Pack the response
                    rpc.buildResponseHeader();
                    rpc.packInt(NFS3.StatusCode.Success.intValue());

                    packPostOpAttr(nfsSess, finfo, shareId, rpc);
                    rpc.packInt(accessMode & mask);

                    // DEBUG
                    if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.INFO))
                        Debug.println("Access path=" + path + ", info=" + finfo);

                } else {

                    // Return an error status
                    errorSts = NFS3.StatusCode.NoEnt;
                }
            }

        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR)) {
                nfsSess.debugPrintln("Access3 Exception: " + ex.toString());
                nfsSess.debugPrintln(ex);
            }
        }

        //	Check for an error status
        if (errorSts != NFS3.StatusCode.Success) {
            rpc.buildErrorResponse(errorSts.intValue());
            packPostOpAttr(nfsSess, null, shareId, rpc);

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Access error=" + errorSts.getStatusString());
        }

        //	Return the response
        rpc.setLength();
        return rpc;
    }

    /**
     * Process the read link request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procReadLink(NFSSrvSession nfsSess, RpcPacket rpc) {

        //  Unpack the read link arguments
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        //  Check if the handle is valid
        if (NFSHandle.isValid(handle) == false) {
            rpc.buildErrorResponse(NFS3.StatusCode.BadHandle.intValue());
            return rpc;
        }

        //  Build the response header
        rpc.buildResponseHeader();

        //  Call the disk share driver to read the symbolic link data
        int shareId = -1;
        String path = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        RpcPacket respRpc = rpc;

        try {

            //  Get the share id and path
            shareId = getShareIdFromHandle(handle);

            TreeConnection conn = getTreeConnection(nfsSess, shareId);
            path = getPathForHandle(nfsSess, handle, conn);

            //  Check if the filesystem supports symbolic links
            boolean symLinks = false;

            if (conn.getInterface() instanceof SymbolicLinkInterface) {

                // Check if symbolic links are enabled in the filesystem
                SymbolicLinkInterface symLinkIface = (SymbolicLinkInterface) conn.getInterface();
                symLinks = symLinkIface.hasSymbolicLinksEnabled(nfsSess, conn);
            }

            // Check if symbolic links are not supported or not enabled
            if (symLinks == false) {

                // Symbolic links not supported on this filesystem
                rpc.buildErrorResponse(NFS3.StatusCode.NotSupp.intValue());
                packPostOpAttr(nfsSess, null, 0, rpc);
                packWccData(rpc, null);

                rpc.setLength();
                return rpc;
            }

            //  Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Get the file information for the symbolic link
            FileInfo finfo = disk.getFileInformation(nfsSess, conn, path);
            if (finfo != null && finfo.isFileType() == FileType.SymbolicLink) {

                // Get the symbolic link data
                SymbolicLinkInterface symLinkInterface = (SymbolicLinkInterface) disk;
                String linkData = symLinkInterface.readSymbolicLink(nfsSess, conn, path);

                // Allocate a larger buffer for the response
                respRpc = nfsSess.getNFSServer().getPacketPool().allocateAssociatedPacket( RespSizeReadLink, rpc, -1);

                // Pack the read link response
                respRpc.packInt(NFS3.StatusCode.Success.intValue());
                packPostOpAttr(nfsSess, finfo, shareId, respRpc);

                respRpc.packString(linkData);
            } else {

                // Return an error status, not a symbolic link
                errorSts = NFS3.StatusCode.InVal;
            }

        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //  DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("ReadLink Exception: " + ex.toString());
        }

        //  Error status
        if (errorSts != NFS3.StatusCode.Success) {

            //  Pack the error response
            respRpc.buildErrorResponse(errorSts.intValue());

            //  DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("ReadLink error=" + errorSts.getStatusString());
        }

        //  Return the response
        respRpc.setLength();
        return respRpc;
    }

    /**
     * Process the read file request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procRead(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Unpack the read parameters
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        long offset = rpc.unpackLong();
        int count = rpc.unpackInt();

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILEIO))
            nfsSess.debugPrintln("[NFS] Read request " + rpc.getClientDetails() + ", count=" + count + ", pos=" + offset);

        //	Call the disk share driver to read the file
        int shareId = -1;
        NetworkFile netFile = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        RpcPacket respRpc = rpc;

        try {

            //	Get the share id and associated shared device
            shareId = getShareIdFromHandle(handle);
            TreeConnection conn = getTreeConnection(nfsSess, shareId);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasReadAccess() == false)
                throw new AccessDeniedException();

            //	Get the network file, it may be cached
            netFile = getNetworkFileForHandle(nfsSess, handle, conn, true);

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            // Allocate a larger response RPC packet, associate with the request RPC
            int allocLen = rpc.getRequestHeaderLength() + count + NFS3.LenPostOpAttr3;
            respRpc = nfsSess.getNFSServer().getPacketPool().allocateAssociatedPacket( allocLen, rpc, -1);

            //	Pack the start of the response
            respRpc.buildResponseHeader();
            respRpc.packInt(NFS3.StatusCode.Success.intValue());

            //	Get file information for the path and pack into the reply
            FileInfo finfo = disk.getFileInformation(nfsSess, conn, netFile.getFullName());
            finfo.setFileSize(netFile.getFileSize());

            packPostOpAttr(nfsSess, finfo, shareId, respRpc);

            //	Save the current position in the response buffer to fill in the length and end of file flag after
            //	the read.
            int bufPos = respRpc.getPosition();

            //	Read the network file
            int rdlen = -1;

            synchronized (netFile) {

                //	Make sure the network file is open
                if (netFile.isClosed())
                    netFile.openFile(false);

                //	Read a block of data from the file
                rdlen = disk.readFile(nfsSess, conn, netFile, respRpc.getBuffer(), bufPos + 12, count, offset);
            }

            //	Set the read length
            respRpc.packInt(rdlen);

            // Set the end of file flag
            if ( finfo.getSize() > (offset + count)) {

                // Not at end of file
                respRpc.packInt( Rpc.False);
            }
            else {

                // Read is up to end of file
                respRpc.packInt( Rpc.True);
            }
            respRpc.packInt(rdlen);

            //	Set the response length
            respRpc.setLength((bufPos + 12 + ((rdlen + 3) & 0xFFFFFFFC)) - respRpc.getOffset());

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILEIO))
                nfsSess.debugPrintln("Read fid=" + netFile.getFileId() + ", name=" + netFile.getName() + ", rdlen=" + rdlen);
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR)) {
                nfsSess.debugPrintln("Read Exception: netFile=" + netFile + ", cache=" + nfsSess.getFileCache().numberOfEntries());
                nfsSess.debugPrintln(ex);
            }
        }

        //	Check for an error status
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the error response
            respRpc.buildErrorResponse(errorSts.intValue());
            packPostOpAttr(nfsSess, null, shareId, respRpc);

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Read error=" + errorSts.getStatusString());
        }

        //	Return the response
        return respRpc;
    }

    /**
     * Process the write file request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procWrite(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Unpack the read parameters
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        long offset = rpc.unpackLong();
        int count = rpc.unpackInt();
        int stable = rpc.unpackInt();

        //	Skip the second write length, position at the start of the data to write
        rpc.skipBytes(4);

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILEIO))
            nfsSess.debugPrintln("Write request from " + rpc.getClientDetails() + " , count=" + count + ", offset=" + offset);

        //	Call the disk share driver to write to the file
        int shareId = -1;
        String path = null;
        NetworkFile netFile = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        try {

            //	Get the share id and associated shared device
            shareId = getShareIdFromHandle(handle);
            TreeConnection conn = getTreeConnection(nfsSess, shareId);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasWriteAccess() == false)
                throw new AccessDeniedException();

            //	Get the network file, it may be cached
            netFile = getNetworkFileForHandle(nfsSess, handle, conn, false);

            //	Get the file path
            path = getPathForHandle(nfsSess, handle, conn);

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            //	Check if threaded writes should be used
            FileInfo preInfo = null;

            synchronized (netFile) {

                //	Make sure the network file is open
                if (netFile.isClosed())
                    netFile.openFile(false);

                //	Get the pre-operation file details
                preInfo = disk.getFileInformation(nfsSess, conn, path);

                //	Write to the network file
                disk.writeFile(nfsSess, conn, netFile, rpc.getBuffer(), rpc.getPosition(), count, offset);
            }

            //	Get file information for the path and pack the response
            FileInfo finfo = disk.getFileInformation(nfsSess, conn, path);

            // Set the current file size from the open file
            finfo.setFileSize(netFile.getFileSize());

            // Pack the response
            rpc.buildResponseHeader();
            rpc.packInt(NFS3.StatusCode.Success.intValue());

            packPreOpAttr(nfsSess, preInfo, rpc);
            packPostOpAttr(nfsSess, finfo, shareId, rpc);

            rpc.packInt(count);
            rpc.packInt(stable);
            rpc.packLong( nfsSess.getNFSServer().getWriteVerifier());

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILEIO))
                nfsSess.debugPrintln("Write fid=" + netFile.getFileId() + ", name=" + netFile.getName() + ", wrlen=" + count);
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (DiskFullException ex) {
            errorSts = NFS3.StatusCode.NoSpc;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR)) {
                nfsSess.debugPrintln("Write Exception: netFile=" + netFile + ", cache=" + nfsSess.getFileCache().numberOfEntries());
                nfsSess.debugPrintln(ex);
            }
        }

        //	Check for a failure status
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the error response
            rpc.buildErrorResponse(errorSts.intValue());
            packWccData(rpc, null); // before attributes
            packWccData(rpc, null); // after attributes

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Write error=" + errorSts.getStatusString());
        }

        //	Return the write response
        rpc.setLength();
        return rpc;
    }

    /**
     * Process the create file request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procCreate(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Unpack the create arguments
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        String fileName = rpc.unpackUTF8String();

        int createMode = rpc.unpackInt();

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILE))
            nfsSess.debugPrintln("Create request from " + rpc.getClientDetails() + ", name=" + fileName);

        //	Check if the handle is valid
        if (NFSHandle.isValid(handle) == false) {
            rpc.buildErrorResponse(NFS3.StatusCode.BadHandle.intValue());
            return rpc;
        }

        //	Call the disk share driver to create the new file
        int shareId = -1;
        String path = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        RpcPacket respRpc = rpc;

        try {

            //	Get the share id and path
            shareId = getShareIdFromHandle(handle);

            TreeConnection conn = getTreeConnection(nfsSess, shareId);
            path = getPathForHandle(nfsSess, handle, conn);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasWriteAccess() == false)
                throw new AccessDeniedException();

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            //	Get the pre-operation state for the parent directory
            FileInfo preInfo = disk.getFileInformation(nfsSess, conn, path);

            //	Build the full path string
            StringBuffer str = new StringBuffer();
            str.append(path);

            if (path.endsWith("\\") == false)
                str.append("\\");
            str.append(fileName);

            String filePath = str.toString();

            //	Check if the file exists
            FileStatus existSts = disk.fileExists(nfsSess, conn, filePath);

            if (existSts == FileStatus.FileExists) {
                errorSts = NFS3.StatusCode.Exist;
            } else if (existSts == FileStatus.DirectoryExists) {
                errorSts = NFS3.StatusCode.IsDir;
            } else {

                //	Get the file permissions
                int gid = -1;
                int uid = -1;
                int mode = -1;

                if (rpc.unpackInt() == Rpc.True)
                    mode = rpc.unpackInt();

                if (rpc.unpackInt() == Rpc.True)
                    uid = rpc.unpackInt();

                if (rpc.unpackInt() == Rpc.True)
                    gid = rpc.unpackInt();

                //	Create a new file
                FileOpenParams params = new FileOpenParams(filePath, FileAction.CreateNotExist, AccessMode.ReadWrite, 0, gid, uid, mode, 0);
                NetworkFile netFile = disk.createFile(nfsSess, conn, params);

                //  DEBUG
                if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILE))
                    nfsSess.debugPrintln("  Create file params=" + params);

                //	Get file information for the path
                FileInfo finfo = disk.getFileInformation(nfsSess, conn, filePath);

                if (finfo != null) {

                    // Allocate a larger response RPC packet, associate with the request RPC
                    respRpc = nfsSess.getNFSServer().getPacketPool().allocateAssociatedPacket( RespSizeCreate, rpc, -1);

                    //	Pack the response
                    respRpc.buildResponseHeader();
                    respRpc.packInt(NFS3.StatusCode.Success.intValue());

                    if (finfo.isDirectory())
                        packDirectoryHandle(shareId, finfo.getFileId(), respRpc);
                    else
                        packFileHandle(shareId, getFileIdForHandle(handle), finfo.getFileId(), respRpc);

                    //	Pack the file attributes
                    packPostOpAttr(nfsSess, finfo, shareId, respRpc);

                    //	Add a cache entry for the path
                    ShareDetails details = nfsSess.getNFSServer().findShareDetails( shareId);
                    details.getFileIdCache().addPath(finfo.getFileId(), filePath);

                    //	Add a cache entry for the network file
                    nfsSess.getFileCache().addFile(netFile, conn, nfsSess);

                    //	Pack the wcc data structure for the directory
                    packPreOpAttr(nfsSess, preInfo, respRpc);

                    FileInfo postInfo = disk.getFileInformation(nfsSess, conn, path);
                    packPostOpAttr(nfsSess, postInfo, shareId, respRpc);

                    //	DEBUG
                    if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILE))
                        nfsSess.debugPrintln("Create path=" + filePath + ", finfo=" + finfo.toString());

                    //	Notify change listeners that a new file has been created
                    DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();

                    if (diskCtx.hasChangeHandler())
                        diskCtx.getChangeHandler().notifyFileChanged(NotifyAction.Added, filePath);
                }
            }
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR)) {
                nfsSess.debugPrintln("Create Exception: " + ex.toString());
                nfsSess.debugPrintln(ex);
            }
        }

        //	Check for a failure status
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the error response
            respRpc.buildErrorResponse(errorSts.intValue());
            packWccData(respRpc, null); // before attributes
            packWccData(respRpc, null); // after attributes

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Create error=" + errorSts.getStatusString());
        }

        //	Return the response
        respRpc.setLength();
        return respRpc;
    }

    /**
     * Process the create directory request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procMkDir(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Unpack the mkdir arguments
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        String dirName = rpc.unpackUTF8String();

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.DIRECTORY))
            nfsSess.debugPrintln("MkDir request from " + rpc.getClientDetails() + ", name=" + dirName);

        //	Check if the handle is valid
        if (NFSHandle.isValid(handle) == false) {
            rpc.buildErrorResponse(NFS3.StatusCode.BadHandle.intValue());
            return rpc;
        }

        //	Call the disk share driver to create the new directory
        int shareId = -1;
        String path = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        RpcPacket respRpc = rpc;

        try {

            //	Get the share id and path
            shareId = getShareIdFromHandle(handle);
            TreeConnection conn = getTreeConnection(nfsSess, shareId);
            path = getPathForHandle(nfsSess, handle, conn);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasWriteAccess() == false)
                throw new AccessDeniedException();

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            //	Get the pre-operation state for the parent directory

            FileInfo preInfo = disk.getFileInformation(nfsSess, conn, path);

            //	Build the full path string
            StringBuffer str = new StringBuffer();
            str.append(path);
            if (path.endsWith("\\") == false)
                str.append("\\");
            str.append(dirName);
            String dirPath = str.toString();

            //	Check if the file exists
            FileStatus existSts = disk.fileExists(nfsSess, conn, dirPath);

            if (existSts != FileStatus.NotExist) {
                errorSts = NFS3.StatusCode.Exist;
            } else {

                //	Get the user id, group id and mode for the new directory
                int gid = -1;
                int uid = -1;
                int mode = -1;

                if (rpc.unpackInt() == Rpc.True)
                    mode = rpc.unpackInt();

                if (rpc.unpackInt() == Rpc.True)
                    uid = rpc.unpackInt();

                if (rpc.unpackInt() == Rpc.True)
                    gid = rpc.unpackInt();

                //	Directory creation parameters
                FileOpenParams params = new FileOpenParams(dirPath, FileAction.CreateNotExist, AccessMode.ReadWrite,
                        FileAttribute.NTDirectory, gid, uid, mode, 0);

                //	Create a new directory
                disk.createDirectory(nfsSess, conn, params);

                //	Get file information for the new directory
                FileInfo finfo = disk.getFileInformation(nfsSess, conn, dirPath);

                if (finfo != null) {

                    // Allocate a larger response RPC packet, associate with the request RPC
                    respRpc = nfsSess.getNFSServer().getPacketPool().allocateAssociatedPacket( RespSizeMkDir, rpc, -1);

                    //	Pack the response
                    respRpc.buildResponseHeader();
                    respRpc.packInt(NFS3.StatusCode.Success.intValue());

                    packDirectoryHandle(shareId, finfo.getFileId(), respRpc);

                    //	Pack the file attributes
                    packPostOpAttr(nfsSess, finfo, shareId, respRpc);

                    //	Add a cache entry for the path
                    ShareDetails details = nfsSess.getNFSServer().findShareDetails( shareId);

                    details.getFileIdCache().addPath(finfo.getFileId(), dirPath);

                    //	Pack the post operation details for the parent directory
                    packWccData(respRpc, preInfo);
                    packPostOpAttr(nfsSess, conn, handle, respRpc);

                    //	Notify change listeners that a new directory has been created
                    DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();

                    if (diskCtx.hasChangeHandler())
                        diskCtx.getChangeHandler().notifyFileChanged(NotifyAction.Added, dirPath);

                    //	DEBUG
                    if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.DIRECTORY))
                        nfsSess.debugPrintln("Mkdir path=" + dirPath + ", finfo=" + finfo.toString());
                }
            }
        }

        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Mkdir Exception: " + ex.toString());
        }

        //	Check for an error status
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the error response
            respRpc.buildErrorResponse(errorSts.intValue());
            packWccData(respRpc, null);
            packWccData(respRpc, null);

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Mkdir error=" + errorSts.getStatusString());
        }

        //	Return the response
        respRpc.setLength();
        return respRpc;
    }

    /**
     * Process the create symbolic link request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procSymLink(NFSSrvSession nfsSess, RpcPacket rpc) {

        //  Unpack the create symbolic link arguments
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        String fileName = rpc.unpackUTF8String();

        //  Check if the handle is valid
        if (NFSHandle.isValid(handle) == false) {
            rpc.buildErrorResponse(NFS3.StatusCode.BadHandle.intValue());
            return rpc;
        }

        //  Call the disk share driver to create the symbolic link
        int shareId = -1;
        String path = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        try {

            //  Get the share id and path
            shareId = getShareIdFromHandle(handle);

            TreeConnection conn = getTreeConnection(nfsSess, shareId);
            path = getPathForHandle(nfsSess, handle, conn);

            //  Check if the filesystem supports symbolic links
            boolean symLinks = false;

            if (conn.getInterface() instanceof SymbolicLinkInterface) {

                // Check if symbolic links are enabled in the filesystem
                SymbolicLinkInterface symLinkIface = (SymbolicLinkInterface) conn.getInterface();
                symLinks = symLinkIface.hasSymbolicLinksEnabled(nfsSess, conn);
            }

            // Check if symbolic links are not supported or not enabled
            if (symLinks == false) {

                // Symbolic links not supported on this filesystem
                rpc.buildErrorResponse(NFS3.StatusCode.NotSupp.intValue());
                packPostOpAttr(nfsSess, null, 0, rpc);
                packWccData(rpc, null);

                rpc.setLength();
                return rpc;
            }

            //  Check if the session has the required access to the shared filesystem
            if (conn.hasWriteAccess() == false)
                throw new AccessDeniedException();

            //  Get the symbolic link attributes
            int setFlags = 0;
            int gid = -1;
            int uid = -1;
            int mode = -1;
            long fsize = -1L;
            long atime = -1L;
            long mtime = -1L;

            //  Check if the file mode has been specified
            if (rpc.unpackInt() == Rpc.True) {
                mode = rpc.unpackInt();
                setFlags += FileInfo.SetMode;
            }

            //  Check if the file owner uid has been specified
            if (rpc.unpackInt() == Rpc.True) {
                uid = rpc.unpackInt();
                setFlags += FileInfo.SetUid;
            }

            //  Check if the file group gid has been specified
            if (rpc.unpackInt() == Rpc.True) {
                gid = rpc.unpackInt();
                setFlags += FileInfo.SetGid;
            }

            //  Check if a new file size has been specified
            if (rpc.unpackInt() == Rpc.True) {
                fsize = rpc.unpackLong();
                setFlags += FileInfo.SetFileSize;
            }

            //  Check if the access date/time should be set. It may be set to a client specified time
            //  or using the server time
            NFS3.SetAttrTimestamp setTime = NFS3.SetAttrTimestamp.fromInt(rpc.unpackInt());

            if (setTime == NFS3.SetAttrTimestamp.TimeClient) {
                atime = (long) rpc.unpackInt();
                atime *= 1000L;
                rpc.skipBytes(4);   //  nanoseconds
                setFlags += FileInfo.SetAccessDate;
            } else if (setTime == NFS3.SetAttrTimestamp.TimeServer) {
                atime = System.currentTimeMillis();
                setFlags += FileInfo.SetAccessDate;
            }

            //  Check if the modify date/time should be set. It may be set to a client specified time
            //  or using the server time
            setTime = NFS3.SetAttrTimestamp.fromInt(rpc.unpackInt());

            if (setTime == NFS3.SetAttrTimestamp.TimeClient) {
                mtime = (long) rpc.unpackInt();
                mtime *= 1000L;
                rpc.skipBytes(4);   //  nanoseconds
                setFlags += FileInfo.SetModifyDate;
            } else if (setTime == NFS3.SetAttrTimestamp.TimeServer) {
                mtime = System.currentTimeMillis();
                setFlags += FileInfo.SetModifyDate;
            }

            //  Get the symbolic link name
            String linkName = rpc.unpackString();

            //  DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILE))
                nfsSess.debugPrintln("Symbolic link request from " + rpc.getClientDetails() + ", name=" + fileName + ", link=" + linkName);

            //  Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            //  Get the pre-operation state for the parent directory
            FileInfo preInfo = disk.getFileInformation(nfsSess, conn, path);

            //  Build the full path string
            StringBuffer str = new StringBuffer();
            str.append(path);

            if (path.endsWith("\\") == false)
                str.append("\\");
            str.append(fileName);

            String filePath = str.toString();

            //  Check if the file exists
            FileStatus existSts = disk.fileExists(nfsSess, conn, filePath);

            if (existSts == FileStatus.FileExists) {
                errorSts = NFS3.StatusCode.Exist;
            } else if (existSts == FileStatus.DirectoryExists) {
                errorSts = NFS3.StatusCode.IsDir;
            } else {

                //  Create a new symbolic
                FileOpenParams params = new FileOpenParams(filePath, FileAction.CreateNotExist, AccessMode.ReadWrite, 0, gid, uid, mode, 0);
                params.setSymbolicLink(linkName);

                NetworkFile netFile = disk.createFile(nfsSess, conn, params);

                //  DEBUG
                if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILE))
                    nfsSess.debugPrintln("  Symbolic link params=" + params);

                //  Get file information for the path
                FileInfo finfo = disk.getFileInformation(nfsSess, conn, filePath);

                if (finfo != null) {

                    //  Pack the response
                    rpc.buildResponseHeader();
                    rpc.packInt(NFS3.StatusCode.Success.intValue());

                    packFileHandle(shareId, getFileIdForHandle(handle), finfo.getFileId(), rpc);

                    //  Pack the file attributes
                    packPostOpAttr(nfsSess, finfo, shareId, rpc);

                    //  Add a cache entry for the path
                    ShareDetails details = nfsSess.getNFSServer().findShareDetails( shareId);
                    details.getFileIdCache().addPath(finfo.getFileId(), filePath);

                    //  Add a cache entry for the network file
                    nfsSess.getFileCache().addFile(netFile, conn, nfsSess);

                    //  Pack the wcc data structure for the directory
                    packPreOpAttr(nfsSess, preInfo, rpc);

                    FileInfo postInfo = disk.getFileInformation(nfsSess, conn, path);
                    packPostOpAttr(nfsSess, postInfo, shareId, rpc);

                    //  DEBUG
                    if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILE))
                        nfsSess.debugPrintln("Symbolic link path=" + filePath + ", finfo=" + finfo.toString());
                }
            }
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //  DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("SymbolicLink Exception: " + ex.toString());
        }

        //  Error status
        if (errorSts != NFS3.StatusCode.Success) {

            //  Pack the error response
            rpc.buildErrorResponse(errorSts.intValue());

            //  DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("SymLink error=" + errorSts.getStatusString());
        }

        //  Return the response
        rpc.setLength();
        return rpc;
    }

    /**
     * Process the make special device request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procMkNode(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.DIRECTORY))
            nfsSess.debugPrintln("MkNode request from " + rpc.getClientDetails());

        //	Return an error status
        rpc.buildErrorResponse(NFS3.StatusCode.NotSupp.intValue());
        packPostOpAttr(nfsSess, null, 0, rpc);
        packWccData(rpc, null);

        rpc.setLength();
        return rpc;
    }

    /**
     * Process the delete file request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procRemove(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Unpack the remove arguments
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        String fileName = rpc.unpackUTF8String();

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILE))
            nfsSess.debugPrintln("Remove request from " + rpc.getClientDetails() + ", name=" + fileName);

        //	Call the disk share driver to delete the file
        int shareId = -1;
        String path = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        try {

            //	Get the share id and path
            shareId = getShareIdFromHandle(handle);
            ShareDetails details = nfsSess.getNFSServer().findShareDetails( shareId);
            TreeConnection conn = getTreeConnection(nfsSess, shareId);

            path = getPathForHandle(nfsSess, handle, conn);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasWriteAccess() == false)
                throw new AccessDeniedException();

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            //	Get the pre-operation details for the directory
            FileInfo preInfo = disk.getFileInformation(nfsSess, conn, path);

            //	Build the full path string
            StringBuffer str = new StringBuffer();
            str.append(path);
            if (path.endsWith("\\") == false)
                str.append("\\");
            str.append(fileName);
            String delPath = str.toString();

            //	Check if the file exists
            FileStatus existSts = disk.fileExists(nfsSess, conn, delPath);

            if (existSts == FileStatus.NotExist) {
                errorSts = NFS3.StatusCode.NoEnt;
            } else if (existSts == FileStatus.DirectoryExists) {
                errorSts = NFS3.StatusCode.IsDir;
            } else {

                //	Get the file information for the file to be deleted
                FileInfo finfo = disk.getFileInformation(nfsSess, conn, delPath);

                //	Delete the file
                disk.deleteFile(nfsSess, conn, delPath);

                //	Remove the path from the cache
                if (finfo != null) {
                    details.getFileIdCache().deletePath(finfo.getFileId());
                    nfsSess.getFileCache().removeFile(finfo.getFileId());
                }

                //	Get the post-operation details for the directory
                FileInfo postInfo = disk.getFileInformation(nfsSess, conn, path);

                //	Pack the response
                rpc.buildResponseHeader();
                rpc.packInt(NFS3.StatusCode.Success.intValue());

                packPreOpAttr(nfsSess, preInfo, rpc);
                packPostOpAttr(nfsSess, postInfo, shareId, rpc);

                //	Check if there are any file/directory change notify requests active
                DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();
                if (diskCtx.hasChangeHandler())
                    diskCtx.getChangeHandler().notifyFileChanged(NotifyAction.Removed, delPath);
            }
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (SecurityException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Remove Exception: " + ex.toString());
        }

        //	Check for an error status
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the error response
            rpc.buildErrorResponse(errorSts.intValue());
            packWccData(rpc, null);
            packWccData(rpc, null);

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Remove error=" + errorSts.getStatusString());
        }

        //	Return the remove repsonse
        rpc.setLength();
        return rpc;
    }

    /**
     * Process the delete directory request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procRmDir(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Unpack the rmdir arguments
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        String dirName = rpc.unpackUTF8String();

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.DIRECTORY))
            nfsSess.debugPrintln("RmDir request from " + rpc.getClientDetails() + ", name=" + dirName);

        //	Check if the handle is valid
        if (NFSHandle.isValid(handle) == false) {
            rpc.buildErrorResponse(NFS3.StatusCode.BadHandle.intValue());
            return rpc;
        }

        int shareId = -1;
        String path = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        try {

            //	Get the share id and path
            shareId = getShareIdFromHandle(handle);
            ShareDetails details = nfsSess.getNFSServer().findShareDetails( shareId);
            TreeConnection conn = getTreeConnection(nfsSess, shareId);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasWriteAccess() == false)
                throw new AccessDeniedException();

            //	Build the pre-operation part of the response
            rpc.buildResponseHeader();
            rpc.packInt(NFS3.StatusCode.Success.intValue());

            //	Pack the pre operation attributes for the parent directory
            packPreOpAttr(nfsSess, conn, handle, rpc);

            //	Get the path to be removed
            path = getPathForHandle(nfsSess, handle, conn);

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            //	Build the full path string
            StringBuffer str = new StringBuffer();
            str.append(path);

            if (path.endsWith("\\") == false)
                str.append("\\");
            str.append(dirName);

            String delPath = str.toString();

            //	Check if the file exists
            FileStatus existSts = disk.fileExists(nfsSess, conn, delPath);

            if (existSts == FileStatus.NotExist) {
                errorSts = NFS3.StatusCode.NoEnt;
            } else if (existSts == FileStatus.FileExists) {
                errorSts = NFS3.StatusCode.NoEnt;
            } else {

                //	Get the file information for the directory to be deleted
                FileInfo finfo = disk.getFileInformation(nfsSess, conn, delPath);

                //	Delete the directory
                disk.deleteDirectory(nfsSess, conn, delPath);

                //	Remove the path from the cache
                if (finfo != null)
                    details.getFileIdCache().deletePath(finfo.getFileId());

                //	Pack the post operation attributes for the parent directory
                packPostOpAttr(nfsSess, conn, handle, rpc);

                //	Check if there are any file/directory change notify requests active
                DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();
                if (diskCtx.hasChangeHandler())
                    diskCtx.getChangeHandler().notifyFileChanged(NotifyAction.Removed, delPath);
            }
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (SecurityException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (java.nio.file.DirectoryNotEmptyException ex) {
            errorSts = NFS3.StatusCode.NotEmpty;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Rmdir Exception: " + ex.toString());
        }

        //	Check if an error status is being returned
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the error response
            rpc.buildErrorResponse(errorSts.intValue());
            packWccData(rpc, null);
            packWccData(rpc, null);

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Rmdir error=" + errorSts.getStatusString());
        }

        //	Return the response
        rpc.setLength();
        return rpc;
    }

    /**
     * Process the rename file request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procRename(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Unpack the rename arguments
        byte[] fromHandle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(fromHandle);

        String fromName = rpc.unpackUTF8String();

        byte[] toHandle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(toHandle);

        String toName = rpc.unpackUTF8String();

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILE)) {
            nfsSess.debugPrintln("Rename request from " + rpc.getClientDetails() + ", fromHandle=" + NFSHandle.asString(fromHandle) + ", fromname=" + fromName);
            nfsSess.debugPrintln("               tohandle=" + NFSHandle.asString(toHandle) + ", toname=" + toName);
        }

        //	Call the disk share driver to rename the file/directory
        int shareId = -1;
        String fromPath = null;
        String toPath = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        RpcPacket respRpc = null;

        try {

            //	Get the share id and path
            shareId = getShareIdFromHandle(fromHandle);
            ShareDetails details = nfsSess.getNFSServer().findShareDetails( shareId);
            TreeConnection conn = getTreeConnection(nfsSess, shareId);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasWriteAccess() == false)
                throw new AccessDeniedException();

            //	Get paths from the handles
            fromPath = getPathForHandle(nfsSess, fromHandle, conn);
            toPath = getPathForHandle(nfsSess, toHandle, conn);

            //	Build the full path string for the old name
            StringBuffer str = new StringBuffer();
            str.append(fromPath);

            if (fromPath.endsWith("\\") == false)
                str.append("\\");
            str.append(fromName);

            String oldPath = str.toString();

            //	Build the full path string for the new name
            str.setLength(0);
            str.append(toPath);

            if (toPath.endsWith("\\") == false)
                str.append("\\");
            str.append(toName);

            String newPath = str.toString();

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            //	Get the pre-operation details for the parent directories
            FileInfo preFromInfo = disk.getFileInformation(nfsSess, conn, fromPath);
            FileInfo preToInfo = null;

            if (NFSHandle.unpackDirectoryId(fromHandle) == NFSHandle.unpackDirectoryId(toHandle))
                preToInfo = preFromInfo;
            else
                preToInfo = disk.getFileInformation(nfsSess, conn, toPath);

            //	Check if the from path exists
            FileStatus existSts = disk.fileExists(nfsSess, conn, oldPath);

            if (existSts == FileStatus.NotExist) {
                errorSts = NFS3.StatusCode.NoEnt;
            } else {

                //	DEBUG
                if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILE))
                    nfsSess.debugPrintln("Rename from=" + oldPath + ", to=" + newPath);

                //	Get the file details for the file/folder being renamed
                FileInfo finfo = disk.getFileInformation(nfsSess, conn, oldPath);

                // Check if the file being renamed is in the open file cache
                if (finfo != null && finfo.isDirectory() == false) {

                    // Build a handle for the file
                    byte[] fHandle = getHandleForFile(nfsSess, fromHandle, conn, fromName);

                    // Get the open file
                    NetworkFile netFile = getOpenNetworkFileForHandle(nfsSess, fHandle, conn);
                    if (netFile != null) {

                        // DEBUG
                        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILE))
                            nfsSess.debugPrintln("  Closing file " + oldPath + " before rename");

                        // Close the file
                        disk.closeFile(nfsSess, conn, netFile);

                        // Remove the file from the open file cache
                        NetworkFileCache fileCache = nfsSess.getFileCache();

                        synchronized (fileCache) {

                            // Remove the file from the open file cache
                            fileCache.removeFile(netFile.getFileId());
                        }
                    }
                }

                // Check if the target exists and it is a file, if so then delete it
                if (disk.fileExists(nfsSess, conn, newPath) == FileStatus.FileExists) {

                    // DEBUG
                    if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILE))
                        nfsSess.debugPrintln("  Delete existing file before rename, newPath=" + newPath);

                    // Delete the existing target file
                    disk.deleteFile(nfsSess, conn, newPath);
                }

                //	Rename the file/directory
                disk.renameFile(nfsSess, conn, oldPath, newPath);

                //	Remove the original path from the cache
                if (finfo != null && finfo.getFileId() != -1) {
                    details.getFileIdCache().deletePath(finfo.getFileId());

                    // Add an entry with the original file id mapped to the new path
                    //
                    // The file id from the file information for the new path may not be the same
                    // but the client will still be using the original handle to access the file.
                    details.getFileIdCache().addPath(finfo.getFileId(), newPath);
                }

                //	Get the file id for the new file/directory
                finfo = disk.getFileInformation(nfsSess, conn, newPath);
                if (finfo != null)
                    details.getFileIdCache().addPath(finfo.getFileId(), newPath);

                //	Check if there are any file/directory change notify requests active
                DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();
                if (diskCtx.hasChangeHandler())
                    diskCtx.getChangeHandler().notifyRename(oldPath, newPath);

                //	Get the post-operation details for the parent directories
                FileInfo postFromInfo = disk.getFileInformation(nfsSess, conn, fromPath);
                FileInfo postToInfo = null;

                if (NFSHandle.unpackDirectoryId(fromHandle) == NFSHandle.unpackDirectoryId(toHandle))
                    postToInfo = postFromInfo;
                else
                    postToInfo = disk.getFileInformation(nfsSess, conn, toPath);

                // Allocate a larger response RPC packet, associate with the request RPC
                respRpc = nfsSess.getNFSServer().getPacketPool().allocateAssociatedPacket( RespSizeRename, rpc, -1);

                //	Pack the rename response
                respRpc.buildResponseHeader();
                respRpc.packInt(NFS3.StatusCode.Success.intValue());

                packWccData(respRpc, preFromInfo);
                packPostOpAttr(nfsSess, postFromInfo, shareId, respRpc);

                packWccData(respRpc, preToInfo);
                packPostOpAttr(nfsSess, postToInfo, shareId, respRpc);
            }
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (SecurityException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (FileExistsException ex) {
            errorSts = NFS3.StatusCode.Exist;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Rename Exception: " + ex.toString());
        }

        //	Check for an error status
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the error response
            rpc.buildErrorResponse(errorSts.intValue());

            // Pack the from dir WCC data
            packWccData(rpc, null);
            packWccData(rpc, null);

            // Pack the to dir WCC data
            packWccData(rpc, null);
            packWccData(rpc, null);

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Rename error=" + errorSts.getStatusString());
        }

        //	Return the rename response
        respRpc.setLength();
        return respRpc;
    }

    /**
     * Process the create hard link request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procLink(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.RXDATA))
            nfsSess.debugPrintln("Link request from " + rpc.getClientDetails());

        //	Return an error status
        rpc.buildErrorResponse(NFS3.StatusCode.Access.intValue());
        packPostOpAttr(nfsSess, null, 0, rpc);
        packWccData(rpc, null);
        packWccData(rpc, null);

        rpc.setLength();
        return rpc;
    }

    /**
     * Process the read directory request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procReadDir(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Unpack the read directory arguments
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        long cookie = rpc.unpackLong();
        long cookieVerf = rpc.unpackLong();

        int maxCount = rpc.unpackInt();

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
            nfsSess.debugPrintln("ReadDir request from " + rpc.getClientDetails() + " handle=" + NFSHandle.asString(handle) +
                    ", count=" + maxCount);

        //	Check if this is a share handle
        int shareId = -1;
        String path = null;

        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        // Response RPC defaults to using the request buffer but will likley be allocated a seperate buffer
        RpcPacket respRpc = null;

        //	Call the disk share driver to get the file information for the path
        try {

            //	Get the share id and path
            shareId = getShareIdFromHandle(handle);
            ShareDetails details = nfsSess.getNFSServer().findShareDetails(shareId);
            TreeConnection conn = getTreeConnection(nfsSess, shareId);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasReadAccess() == false)
                throw new AccessDeniedException();

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            //	Get the path from the handle
            path = getPathForHandle(nfsSess, handle, conn);

            //	If the filesystem driver cannot convert file ids to relative paths we need to build a relative path for
            //	every file and sub-directory in the search
            StringBuffer pathBuf = null;
            int pathLen = 0;
            FileIdCache fileCache = details.getFileIdCache();

            if (details.hasFileIdSupport() == false) {

                //	Allocate the buffer for building the relative paths
                pathBuf = new StringBuffer(256);
                pathBuf.append(path);
                if (path.endsWith("\\") == false)
                    pathBuf.append("\\");

                //	Set the length of the search path portion of the string
                pathLen = pathBuf.length();
            }

            // Allocate a larger response RPC packet, associate with the request RPC
            respRpc = nfsSess.getNFSServer().getPacketPool().allocateAssociatedPacket( maxCount, rpc, -1);

            //	Build the response header
            respRpc.buildResponseHeader();
            respRpc.packInt(NFS3.StatusCode.Success.intValue());

            //	Get the root directory information
            FileInfo dinfo = disk.getFileInformation(nfsSess, conn, path);
            packPostOpAttr(nfsSess, dinfo, shareId, respRpc);

            //	Generate the search path
            String searchPath = generatePath(path, "*.*");

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
                nfsSess.debugPrintln("ReadDir searchPath=" + searchPath + ", cookie=" + cookie);

            //	Check if this is the start of a search
            SearchContext search = null;
            long searchId = -1;

            if (cookie == 0) {

                //	Start a new search, allocate a search id
                search = disk.startSearch(nfsSess, conn, searchPath, FileAttribute.Directory + FileAttribute.Normal, EnumSet.noneOf( SearchFlags.class));

                //	Allocate a search id for the new search
                searchId = nfsSess.allocateSearchSlot(search);

                //	Set the cookie verifier
                cookieVerf = dinfo.getModifyDateTime();

                //	DEBUG
                if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
                    nfsSess.debugPrintln("ReadDir allocated searchId=" + searchId);
            } else {

                //	Check if the cookie verifier is valid, check reverse byte order
                if (cookieVerf != 0L && cookieVerf != dinfo.getModifyDateTime() &&
                        Long.reverseBytes(cookieVerf) != dinfo.getModifyDateTime())
                    throw new BadCookieException();

                //	Retrieve the search from the active search cache
                searchId = (cookie & COOKIE_SEARCHID_MASK) >> COOKIE_SEARCHID_SHIFT;

                //	Get the active search
                search = nfsSess.getSearchContext((int) searchId);

                //	Check if the search has been closed, if so then restart the search
                if (search == null) {

                    //	Restart the search
                    search = disk.startSearch(nfsSess, conn, searchPath, FileAttribute.Directory + FileAttribute.Normal, EnumSet.noneOf( SearchFlags.class));

                    //	Allocate a search id for the new search
                    searchId = nfsSess.allocateSearchSlot(search);

                    //	Set the cookie verifier
                    cookieVerf = dinfo.getModifyDateTime();

                    //	DEBUG
                    if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
                        nfsSess.debugPrintln("ReadDir restarted search, searchId=" + searchId);
                }

                //	Check if the search is at the required restart point
                int resumeId = (int) (cookie & COOKIE_RESUMEID_MASK);
                if (search.getResumeId() != resumeId)
                    search.restartAt(resumeId);
            }

            //	Pack the cookie verifier
            respRpc.packLong(cookieVerf);

            //	Check if the search id is valid
            if (searchId == -1)
                throw new Exception("Bad search id");

            //	Search id is masked into the top of the file index to make the resume cookie
            long searchMask = ((long) searchId) << COOKIE_SEARCHID_SHIFT;

            //	Build the return file list
            int entCnt = 0;

            //	Loop until the return buffer is full or there are no more files
            FileInfo finfo = new FileInfo();

            //	Check if this is the start of a search, if so then add the '.' and '..' entries
            if (cookie == 0) {

                //	Add the search directory details, the '.' directory
                respRpc.packInt(Rpc.True);
                respRpc.packLong(dinfo.getFileIdLong() + FILE_ID_OFFSET);
                respRpc.packString(".");
                respRpc.packLong(COOKIE_DOT_DIRECTORY);

                //	Get the file information for the parent directory
                String parentPath = generatePath(path, "..");
                FileInfo parentInfo = disk.getFileInformation(nfsSess, conn, parentPath);

                //	Add the parent of the search directory, the '..' directory
                respRpc.packInt(Rpc.True);
                respRpc.packLong(parentInfo.getFileIdLong() + FILE_ID_OFFSET);
                respRpc.packString("..");
                respRpc.packLong(COOKIE_DOTDOT_DIRECTORY);

                //	Update the entry count and current used reply buffer count
                entCnt = 2;
            }

            //	Add file/sub-directory entries until there are no more entries or the buffer is full
            boolean replyFull = false;

            while (entCnt++ < maxCount && replyFull == false && search.nextFileInfo(finfo)) {

                //	Check if the new file entry will fit into the reply buffer without exceeding the clients maximum
                //	reply size
                int entryLen = READDIR_ENTRY_LENGTH + ((finfo.getFileName().length() + 3) & 0xFFFFFFFC);

                if (entryLen > respRpc.getAvailableLength() ||
                        (respRpc.getPosition() + entryLen > maxCount)) {
                    replyFull = true;

                    //  DEBUG
                    if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
                        nfsSess.debugPrintln("ReadDir response full, restart at=" + finfo.getFileName() + ", resumeId=" + search.getResumeId());

                    search.restartAt(finfo);
                    break;
                }

                //	Fill in the entry details
                respRpc.packInt(Rpc.True);
                respRpc.packLong(finfo.getFileIdLong() + FILE_ID_OFFSET);
                respRpc.packUTF8String(finfo.getFileName());
                respRpc.packLong(search.getResumeId() + searchMask);

                //	Check if the relative path should be added to the file id cache
                if (details.hasFileIdSupport() == false && fileCache.findPath(finfo.getFileId()) == null) {

                    //	Create a relative path for the current file/sub-directory and add to the file id cache
                    pathBuf.setLength(pathLen);
                    pathBuf.append(finfo.getFileName());

                    fileCache.addPath(finfo.getFileId(), pathBuf.toString());
                }
            }

            //	Indicate no more file entries in this response
            respRpc.packInt(Rpc.False);

            //	Check if the search is complete
            if (search.hasMoreFiles()) {

                //	Indicate that there are more files to be returned
                respRpc.packInt(Rpc.False);
            } else {

                //	Set the end of search flag
                respRpc.packInt(Rpc.True);

                //	Close the search, release the search slot
                search.closeSearch();
                nfsSess.deallocateSearchSlot((int) searchId);

                //	DEBUG
                if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
                    nfsSess.debugPrintln("ReadDir released searchId=" + searchId);
            }

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
                nfsSess.debugPrintln("ReadDir return entries=" + (entCnt - 1) + ", eof=" + search.hasMoreFiles());
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (BadCookieException ex) {
            errorSts = NFS3.StatusCode.BadCookie;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR)) {
                nfsSess.debugPrintln("ReadDir Exception: " + ex.toString());
                nfsSess.debugPrintln(ex);
            }
        }

        //	Check for an error status
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the error response
            respRpc.buildErrorResponse(errorSts.intValue());
            packPostOpAttr(nfsSess, null, shareId, respRpc);

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("ReadDir error=" + errorSts.getStatusString());
        }

        //	Return the read directory response
        respRpc.setLength();
        return respRpc;
    }

    /**
     * Process the read directory plus request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procReadDirPlus(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Unpack the read directory arguments
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        long cookie = rpc.unpackLong();
        long cookieVerf = rpc.unpackLong();

        int maxDir = rpc.unpackInt();
        int maxCount = rpc.unpackInt();

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
            nfsSess.debugPrintln("ReadDir request from " + rpc.getClientDetails() + " handle=" + NFSHandle.asString(handle) +
                    ", dir=" + maxDir + ", count=" + maxCount);

        //	Check if this is a share handle
        int shareId = -1;
        String path = null;

        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        // Response RPC defaults to using the request buffer but will likley be allocated a seperate buffer
        RpcPacket respRpc = null;

        //	Call the disk share driver to get the file information for the path
        try {

            //	Get the share id and path
            shareId = getShareIdFromHandle(handle);
            ShareDetails details = nfsSess.getNFSServer().findShareDetails( shareId);
            TreeConnection conn = getTreeConnection(nfsSess, shareId);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasReadAccess() == false)
                throw new AccessDeniedException();

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            //	Get the path from the handle
            path = getPathForHandle(nfsSess, handle, conn);

            //	If the filesystem driver cannot convert file ids to relative paths we need to build a relative path for
            //	every file and sub-directory in the search
            StringBuffer pathBuf = null;
            int pathLen = 0;
            FileIdCache fileCache = details.getFileIdCache();

            if (details.hasFileIdSupport() == false) {

                //	Allocate the buffer for building the relative paths
                pathBuf = new StringBuffer(256);
                pathBuf.append(path);
                if (path.endsWith("\\") == false)
                    pathBuf.append("\\");

                //	Set the length of the search path portion of the string
                pathLen = pathBuf.length();
            }

            // Allocate a larger response RPC packet, associate with the request RPC
            respRpc = nfsSess.getNFSServer().getPacketPool().allocateAssociatedPacket( maxCount, rpc, -1);

            //	Build the response header
            respRpc.buildResponseHeader();
            respRpc.packInt(NFS3.StatusCode.Success.intValue());

            //	Get the root directory information
            FileInfo dinfo = disk.getFileInformation(nfsSess, conn, path);
            packPostOpAttr(nfsSess, dinfo, shareId, respRpc);

            //	Generate the search path
            String searchPath = generatePath(path, "*.*");

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
                nfsSess.debugPrintln("ReadDirPlus searchPath=" + searchPath + ", cookie=" + cookie);

            //	Check if this is the start of a search
            SearchContext search = null;
            long searchId = -1;

            if (cookie == 0L) {

                //	Start a new search, allocate a search id
                search = disk.startSearch(nfsSess, conn, searchPath, FileAttribute.Directory + FileAttribute.Normal, EnumSet.noneOf( SearchFlags.class));

                //	Allocate a search id for the new search
                searchId = nfsSess.allocateSearchSlot(search);

                //	Set the cookie verifier
                cookieVerf = dinfo.getModifyDateTime();

                //	DEBUG
                if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
                    nfsSess.debugPrintln("ReadDirPlus allocated searchId=" + searchId);
            } else {

                //	Check if the cookie verifier is valid, check reverse byte order
                if (cookieVerf != 0L && cookieVerf != dinfo.getModifyDateTime() &&
                        Long.reverseBytes(cookieVerf) != dinfo.getModifyDateTime()) {
                    nfsSess.debugPrintln("Bad cookie verifier, verf=0x" + Long.toHexString(cookieVerf) + ", modTime=0x" + Long.toHexString(dinfo.getModifyDateTime()));
                    throw new BadCookieException();
                }

                //	Retrieve the search from the active search cache
                searchId = (cookie & COOKIE_SEARCHID_MASK) >> COOKIE_SEARCHID_SHIFT;

                //	Get the active search
                search = nfsSess.getSearchContext((int) searchId);

                //	Check if the search has been closed, if so then restart the search
                if (search == null) {

                    //	Restart the search
                    search = disk.startSearch(nfsSess, conn, searchPath, FileAttribute.Directory + FileAttribute.Normal, EnumSet.noneOf( SearchFlags.class));

                    //	Allocate a search id for the new search
                    searchId = nfsSess.allocateSearchSlot(search);

                    //	Set the cookie verifier
                    cookieVerf = dinfo.getModifyDateTime();

                    //	DEBUG
                    if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
                        nfsSess.debugPrintln("ReadDirPlus restarted search, searchId=" + searchId);
                }

                //	Get the search resume id from the cookie
                int resumeId = (int) (cookie & COOKIE_RESUMEID_MASK);
                if (search != null && search.getResumeId() != resumeId)
                    search.restartAt(resumeId);
            }

            //	Pack the cookie verifier
            respRpc.packLong(cookieVerf);

            //	Check if the search id is valid
            if (searchId == -1)
                throw new Exception("Bad search id");

            //	Search id is masked into the top of the file index to make the resume cookie
            long searchMask = ((long) searchId) << COOKIE_SEARCHID_SHIFT;

            //	Build the return file list
            int entCnt = 0;

            //	Loop until the return buffer is full or there are no more files
            FileInfo finfo = new FileInfo();

            //	Check if this is the start of a search, if so then add the '.' and '..' entries
            if (cookie == 0) {

                //	Add the search directory details, the '.' directory
                respRpc.packInt(Rpc.True);
                respRpc.packLong(dinfo.getFileIdLong() + FILE_ID_OFFSET);
                respRpc.packString(".");
                respRpc.packLong(COOKIE_DOT_DIRECTORY);

                //	Fill in the file attributes
                respRpc.packInt(Rpc.True);
                packAttributes3(respRpc, dinfo, shareId);

                //	Fill in the file handle
                packDirectoryHandle(shareId, dinfo.getFileId(), rpc);

                //	Get the file information for the parent directory
                String parentPath = generatePath(path, "..");
                FileInfo parentInfo = disk.getFileInformation(nfsSess, conn, parentPath);

                //	Add the parent of the search directory, the '..' directory
                respRpc.packInt(Rpc.True);
                respRpc.packLong(parentInfo.getFileIdLong() + FILE_ID_OFFSET);
                respRpc.packString("..");
                respRpc.packLong(COOKIE_DOTDOT_DIRECTORY);

                //	Fill in the file attributes
                respRpc.packInt(Rpc.True);
                packAttributes3(respRpc, parentInfo, shareId);

                //	Fill in the file handle
                packDirectoryHandle(shareId, parentInfo.getFileId(), respRpc);

                //	Update the entry count and current used reply buffer count
                entCnt = 2;
            }

            //	Pack the file entries
            boolean replyFull = false;

            while (entCnt++ < maxDir && replyFull == false && search.nextFileInfo(finfo)) {

                //	Check if the new file entry will fit into the reply buffer without exceeding the clients maximum
                //	reply size
                int entryLen = READDIRPLUS_ENTRY_LENGTH + ((finfo.getFileName().length() + 3) & 0xFFFFFFFC);

                if (entryLen > respRpc.getAvailableLength() ||
                        (respRpc.getPosition() + entryLen > maxCount)) {
                    replyFull = true;

                    //  DEBUG
                    if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
                        nfsSess.debugPrintln("ReadDirPlus response full, restart at=" + finfo.getFileName() + ", resumeId=" + search.getResumeId());

                    search.restartAt(finfo);
                    break;
                }

                //	Fill in the entry details
                respRpc.packInt(Rpc.True);
                respRpc.packLong(finfo.getFileIdLong() + FILE_ID_OFFSET);
                respRpc.packUTF8String(finfo.getFileName());
                respRpc.packLong(search.getResumeId() + searchMask);

                //	Fill in the file attributes
                respRpc.packInt(Rpc.True);
                packAttributes3(respRpc, finfo, shareId);

                //	Fill in the file or directory handle
                if (finfo.isDirectory())
                    packDirectoryHandle(shareId, finfo.getFileId(), respRpc);
                else
                    packFileHandle(shareId, dinfo.getFileId(), finfo.getFileId(), respRpc);

                //	Check if the relative path should be added to the file id cache
                if (details.hasFileIdSupport() == false && fileCache.findPath(finfo.getFileId()) == null) {

                    //	Create a relative path for the current file/sub-directory and add to the file id cache
                    pathBuf.setLength(pathLen);
                    pathBuf.append(finfo.getFileName());

                    fileCache.addPath(finfo.getFileId(), pathBuf.toString());
                }

                // Reset the file type
                finfo.setFileType(FileType.RegularFile);
            }

            //	Indicate that there are no more file entries in this response
            respRpc.packInt(Rpc.False);

            //	Check if the search is complete
            if (search.hasMoreFiles()) {

                //	Indicate that there are more files to be returned
                respRpc.packInt(Rpc.False);
            } else {

                //	Set the end of search flag
                respRpc.packInt(Rpc.True);

                //	Close the search, release the search slot
                search.closeSearch();
                nfsSess.deallocateSearchSlot((int) searchId);
            }

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
                nfsSess.debugPrintln("ReadDirPlus return entries=" + (entCnt - 1) + ", eof=" + (search.hasMoreFiles() ? false : true));
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (BadCookieException ex) {
            errorSts = NFS3.StatusCode.BadCookie;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR)) {
                nfsSess.debugPrintln("ReadDirPlus Exception: " + ex.toString());
                nfsSess.debugPrintln(ex);
            }
        }

        //	Check for an error status
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the error response
            respRpc.buildErrorResponse(errorSts.intValue());
            packPostOpAttr(nfsSess, null, shareId, respRpc);

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("ReadDir error=" + errorSts.getStatusString());
        }

        //	Return the read directory plus response
        respRpc.setLength();
        return respRpc;
    }

    /**
     * Process the filesystem status request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procFsStat(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Get the handle from the request
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.INFO))
            nfsSess.debugPrintln("FsInfo request from " + rpc.getClientDetails());

        //	Call the disk share driver to get the disk size information
        int shareId = -1;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        try {

            //	Get the share id
            shareId = getShareIdFromHandle(handle);

            //	Get the required disk driver/tree connection
            TreeConnection conn = getTreeConnection(nfsSess, shareId);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasReadAccess() == false)
                throw new AccessDeniedException();

            //	Get the static disk information from the context, if available
            DiskDeviceContext diskCtx = (DiskDeviceContext) conn.getContext();
            SrvDiskInfo diskInfo = diskCtx.getDiskInformation();

            //	If we did not get valid disk information from the device context check
            // if the driver implements the
            //	disk sizing interface
            if (diskInfo == null)
                diskInfo = new SrvDiskInfo();

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            //	Check if the driver implements the dynamic sizing interface to get
            // realtime disk size information
            if (disk instanceof DiskSizeInterface) {

                //	Get the dynamic disk sizing information
                DiskSizeInterface sizeInterface = (DiskSizeInterface) disk;
                sizeInterface.getDiskInformation(diskCtx, diskInfo);
            }

            //	Calculate the disk size information
            //int unitSize = diskInfo.getBlockSize() * diskInfo.getBlocksPerAllocationUnit();

            //	Get the file details for the root directory
            String rootPath = getPathForHandle(nfsSess, handle, conn);
            FileInfo rootInfo = disk.getFileInformation(nfsSess, conn, rootPath);

            //	Pack the response
            rpc.buildResponseHeader();
            rpc.packInt(NFS3.StatusCode.Success.intValue());

            packPostOpAttr(nfsSess, rootInfo, shareId, rpc);

            //	Calculate the total/free disk space in bytes
            long totalSize = diskInfo.getDiskSizeKb() * 1024L;
            long freeSize = diskInfo.getDiskFreeSizeKb() * 1024L;

            //	Pack the total size, free size and space available to the user
            rpc.packLong(totalSize);
            rpc.packLong(freeSize);
            rpc.packLong(freeSize);

            //	Total/free file slots in the file system, assume one file per 1Kb of
            // space
            long totalSlots = diskInfo.getDiskSizeKb();
            long freeSlots = diskInfo.getDiskFreeSizeKb();

            //	Pack the total slots, free slots and user slots available
            rpc.packLong(totalSlots);
            rpc.packLong(freeSlots);
            rpc.packLong(freeSlots);

            //	Pack the number of seconds for which the file system in not expected to
            // change
            rpc.packInt(0);
        }
        catch (SecurityException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;
        }

        //	Check for an error status
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the error response
            rpc.buildErrorResponse(errorSts.intValue());
            packPostOpAttr(nfsSess, null, shareId, rpc);

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("FsStat error=" + errorSts.getStatusString());
        }

        //	Return the response
        rpc.setLength();
        return rpc;
    }

    /**
     * Process the filesystem information request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procFsInfo(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Get the handle from the request
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.INFO))
            nfsSess.debugPrintln("[NFS] FsInfo request from " + rpc.getClientDetails());

        //	Check if the handle is valid
        if (NFSHandle.isValid(handle) == false) {

            //	Return an error status
            rpc.buildErrorResponse(NFS3.StatusCode.BadHandle.intValue());
            return rpc;
        }

        //	Build the response header
        rpc.buildResponseHeader();

        //	Check if this is a share handle
        int shareId = -1;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        //	Pack the filesystem information for the filesystem
        try {

            //	Get the share id and path
            shareId = getShareIdFromHandle(handle);
            TreeConnection conn = getTreeConnection(nfsSess, shareId);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasReadAccess() == false)
                throw new AccessDeniedException();

            //	Pack the status code and post op attributes
            rpc.packInt(NFS3.StatusCode.Success.intValue());
            packPostOpAttr(nfsSess, conn, handle, rpc);

            //	Pack the filesystem information
            //
            //	Maximum/preferred read request supported by the server
            rpc.packInt(MaxReadSize);
            rpc.packInt(PrefReadSize);
            rpc.packInt(MultReadSize);

            //	Maximum/preferred write request supported by the server
            rpc.packInt(MaxWriteSize);
            rpc.packInt(PrefWriteSize);
            rpc.packInt(MultWriteSize);

            //	Preferred READDIR request size
            rpc.packInt(PrefReadDirSize);

            //	Maximum file size supported
            rpc.packLong(MaxFileSize);

            //	Server time resolution, indicate to nearest second
            rpc.packInt(1); //	seconds
            rpc.packInt(0); //	nano-seconds

            //	Server properties, check if the filesystem supports symbolic links
            int fileSysProps = NFS3.FileSysHomogeneuos + NFS3.FileSysCanSetTime;
            if (conn.getInterface() instanceof SymbolicLinkInterface) {

                // Check if symbolic links are enabled
                SymbolicLinkInterface symLinkIface = (SymbolicLinkInterface) conn.getInterface();
                if (symLinkIface.hasSymbolicLinksEnabled(nfsSess, conn))
                    fileSysProps += NFS3.FileSysSymLink;
            }

            rpc.packInt(fileSysProps);
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR)) {
                nfsSess.debugPrintln("FsInfo Exception: " + ex.toString());
                nfsSess.debugPrintln(ex);
            }
        }

        //	Check for an error status
        if (errorSts != NFS3.StatusCode.Success) {
            rpc.buildErrorResponse(errorSts.intValue());
            packPostOpAttr(nfsSess, null, shareId, rpc);

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("FsInfo error=" + errorSts.getStatusString());
        }

        //	Return the response
        rpc.setLength();
        return rpc;
    }

    /**
     * Process the retrieve POSIX information request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procPathConf(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	Unpack the pathconf arguments
        byte[] handle = new byte[NFS3.FileHandleSize];
        rpc.unpackByteArrayWithLength(handle);

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
            nfsSess.debugPrintln("PathConf request from " + rpc.getClientDetails() + " handle=" + NFSHandle.asString(handle));

        //	Call the disk share driver to get the file information for the path
        int shareId = -1;
        String path = null;
        NFS3.StatusCode errorSts = NFS3.StatusCode.Success;

        try {

            //	Get the share id and path
            shareId = getShareIdFromHandle(handle);
            TreeConnection conn = getTreeConnection(nfsSess, shareId);

            //	Check if the session has the required access to the shared filesystem
            if (conn.hasReadAccess() == false)
                throw new AccessDeniedException();

            //	Get the path from the handle
            path = getPathForHandle(nfsSess, handle, conn);

            //	Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

            //	Check if the file/directory exists
            if (disk.fileExists(nfsSess, conn, path) != FileStatus.NotExist) {

                //	Get file information for the path
                FileInfo finfo = disk.getFileInformation(nfsSess, conn, path);

                //	Build the response
                rpc.buildResponseHeader();
                rpc.packInt(NFS3.StatusCode.Success.intValue());

                packPostOpAttr(nfsSess, finfo, shareId, rpc);

                //	Pack the filesystem options
                rpc.packInt(32767);
                rpc.packInt(255);

                rpc.packInt(Rpc.True);        //	truncate over size names
                rpc.packInt(Rpc.True);        //	chown restricted
                rpc.packInt(Rpc.True);        //	case insensitive
                rpc.packInt(Rpc.True);        //	case preserving

                //	DEBUG
                if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.SEARCH))
                    nfsSess.debugPrintln("Pathconf path=" + path + ", finfo=" + (finfo != null ? finfo.toString() : "<null>"));
            } else {

                //	File does not exist
                errorSts = NFS3.StatusCode.NoEnt;
            }
        }
        catch (BadHandleException ex) {
            errorSts = NFS3.StatusCode.BadHandle;
        }
        catch (StaleHandleException ex) {
            errorSts = NFS3.StatusCode.Stale;
        }
        catch (AccessDeniedException ex) {
            errorSts = NFS3.StatusCode.Access;
        }
        catch (Exception ex) {
            errorSts = NFS3.StatusCode.ServerFault;

            //	DEBUG
            if (Debug.EnableError && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Pathconf Exception: " + ex.toString());
        }

        //	Check if an error is being returned
        if (errorSts != NFS3.StatusCode.Success) {

            //	Pack the error response
            rpc.buildErrorResponse(errorSts.intValue());
            packPostOpAttr(nfsSess, null, shareId, rpc);

            //	DEBUG
            if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.ERROR))
                nfsSess.debugPrintln("Pathconf error=" + errorSts.getStatusString());
        }

        //	Return the path information response
        rpc.setLength();
        return rpc;
    }

    /**
     * Commit request
     *
     * @param nfsSess NFSSrvSession
     * @param rpc  RpcPacket
     * @return RpcPacket
     */
    private final RpcPacket procCommit(NFSSrvSession nfsSess, RpcPacket rpc) {

        //	DEBUG
        if (Debug.EnableInfo && nfsSess.hasDebug(NFSSrvSession.Dbg.FILEIO))
            nfsSess.debugPrintln("Commit request from " + rpc.getClientDetails());

        //	Pack the response
        rpc.buildResponseHeader();

        rpc.packInt(NFS3.StatusCode.Success.intValue());
        packWccData(rpc, null);
        packPostOpAttr(nfsSess, null, 0, rpc);

        //	Pack the write verifier, indicates if the server has been restarted since the file write requests
        rpc.packLong( nfsSess.getNFSServer().getWriteVerifier());

        //	Return the response
        rpc.setLength();
        return rpc;
    }

    /**
     * Pack the NFS v3 file attributes structure using the file information
     *
     * @param rpc       RpcPacket
     * @param finfo     FileInfo
     * @param fileSysId int
     */
    protected final void packAttributes3(RpcPacket rpc, FileInfo finfo, int fileSysId) {

        //	Pack the NFS format file attributes
        if (finfo.isDirectory()) {

            //	Pack the directory information
            rpc.packInt(NFS3.FileType.Directory.intValue());
            if (finfo.hasMode())
                rpc.packInt(finfo.getMode());
            else
                rpc.packInt(MODE_DIR_DEFAULT);
        } else {

            //	Pack the file information
            if (finfo.isFileType() == FileType.SymbolicLink)
                rpc.packInt(NFS3.FileType.Link.intValue());
            else
                rpc.packInt(NFS3.FileType.Regular.intValue());

            if (finfo.hasMode())
                rpc.packInt(finfo.getMode());
            else
                rpc.packInt(MODE_FILE_DEFAULT);
        }

        //	Set various Unix fields
        rpc.packInt(1); //	number of links

        rpc.packInt(finfo.hasUid() ? finfo.getUid() : 0);
        rpc.packInt(finfo.hasGid() ? finfo.getGid() : 0);

        //	Set the size for the file
        if (finfo.isDirectory()) {

            //	Pack the directory size/allocation
            rpc.packLong(512L);
            rpc.packLong(1024L);
        } else {

            //	Pack the file size/allocation
            rpc.packLong(finfo.getSize());
            if (finfo.getAllocationSize() != 0)
                rpc.packLong(finfo.getAllocationSize());
            else
                rpc.packLong(finfo.getSize());
        }

        //	Pack the rdev field
        rpc.packInt(0); //	specdata1
        rpc.packInt(0); //	specdata2

        //	Pack the file id
        long fid = ((long) finfo.getFileId()) & 0x0FFFFFFFFL;
        fid += FILE_ID_OFFSET;

        rpc.packLong(fileSysId);
        rpc.packLong(fid); //	fid

        //	Pack the file times
        if (finfo.hasAccessDateTime()) {
            rpc.packInt((int) (finfo.getAccessDateTime() / 1000L));
            rpc.packInt(0);
        } else
            rpc.packLong(0);

        if (finfo.hasModifyDateTime()) {
            rpc.packInt((int) (finfo.getModifyDateTime() / 1000L));
            rpc.packInt(0);
        } else
            rpc.packLong(0);

        if (finfo.hasChangeDateTime()) {
            rpc.packInt((int) (finfo.getChangeDateTime() / 1000L));
            rpc.packInt(0);
        } else
            rpc.packLong(0);
    }

    /**
     * Pack a share handle
     *
     * @param shareName String
     * @param rpc       RpcPacket
     */
    protected final void packShareHandle(String shareName, RpcPacket rpc) {

        //	Indicate that a handle follows, pack the handle
        rpc.packInt(Rpc.True);
        NFSHandle.packShareHandle(shareName, rpc, NFS3.FileHandleSize);
    }

    /**
     * Pack a directory handle
     *
     * @param shareId int
     * @param dirId   int
     * @param rpc     RpcPacket
     */
    protected final void packDirectoryHandle(int shareId, int dirId, RpcPacket rpc) {

        //	Indicate that a handle follows, pack the handle
        rpc.packInt(Rpc.True);
        NFSHandle.packDirectoryHandle(shareId, dirId, rpc, NFS3.FileHandleSize);
    }

    /**
     * Pack a directory handle
     *
     * @param shareId int
     * @param dirId   int
     * @param fileId  int
     * @param rpc     RpcPacket
     */
    protected final void packFileHandle(int shareId, int dirId, int fileId, RpcPacket rpc) {

        //	Indicate that a handle follows, pack the handle
        rpc.packInt(Rpc.True);
        NFSHandle.packFileHandle(shareId, dirId, fileId, rpc, NFS3.FileHandleSize);
    }

    /**
     * Get the share id from the specified handle
     *
     * @param handle byte[]
     * @return int
     * @exception BadHandleException Bad NFS handle
     */
    protected final int getShareIdFromHandle(byte[] handle)
            throws BadHandleException {

        //	Check if this is a share handle
        int shareId = NFSHandle.unpackShareId(handle);

        //	Check if the share id is valid
        if (shareId == -1)
            throw new BadHandleException();

        //	Return the share id
        return shareId;
    }

    /**
     * Get the path for the specified handle
     *
     * @param nfsSess   NFSSrvSession
     * @param handle byte[]
     * @param tree   TreeConnection
     * @return String
     * @exception BadHandleException Bad NFS handle
     * @exception StaleHandleException Stale NFS handle
     */
    protected final String getPathForHandle(NFSSrvSession nfsSess, byte[] handle, TreeConnection tree)
            throws BadHandleException, StaleHandleException {

        //	Get the share details via the share id hash
        ShareDetails details = nfsSess.getNFSServer().findShareDetails(getShareIdFromHandle(handle));

        //	Check if this is a share handle
        String path = null;

        int dirId = -1;
        int fileId = -1;

        if (NFSHandle.isShareHandle(handle)) {

            //	Use the root path
            path = "\\";
        } else if (NFSHandle.isDirectoryHandle(handle)) {

            //	Get the directory id from the handle and get the associated path
            dirId = NFSHandle.unpackDirectoryId(handle);
            path = details.getFileIdCache().findPath(dirId);
        } else if (NFSHandle.isFileHandle(handle)) {

            //	Get the file id from the handle and get the associated path
            fileId = NFSHandle.unpackFileId(handle);
            path = details.getFileIdCache().findPath(fileId);
        } else
            throw new BadHandleException();

        //	Check if the path is valid. The path may not be valid if the server has
        // 	been restarted as the file id cache will not contain the required path.
        if (path == null) {

            //	Check if the filesystem driver supports converting file ids to paths
            if (details.hasFileIdSupport()) {

                //	Get the file and directory ids from the handle
                dirId = NFSHandle.unpackDirectoryId(handle);
                fileId = NFSHandle.unpackFileId(handle);

                //	If the file id is not valid the handle is to a directory, use the
                // 	directory id as the file id
                if (fileId == -1) {
                    fileId = dirId;
                    dirId = -1;
                }

                //	Convert the file id to a path
                FileIdInterface fileIdInterface = (FileIdInterface) tree.getInterface();
                try {

                    //	Convert the file id to a path
                    path = fileIdInterface.buildPathForFileId(nfsSess, tree, dirId, fileId);

                    //	Add the path to the cache
                    details.getFileIdCache().addPath(fileId, path);
                }
                catch (FileNotFoundException ex) {
                }
            } else if (NFSHandle.isDirectoryHandle(handle) && dirId == 0) {

                //	Path is the root directory
                path = "\\";

                //	Add an entry to the cache
                details.getFileIdCache().addPath(dirId, path);
            }
        }

        //	Check if the path is valid, filesystem driver may not support converting
        // 	file ids to paths or the file/directory may have been deleted.
        if (path == null)
            throw new StaleHandleException();

        //	Return the path
        return path;
    }

    /**
     * Get the handle for the specified directory handle and file name
     *
     * @param nfsSess   NFSSrvSession
     * @param handle byte[]
     * @param tree   TreeConnection
     * @param fname  String
     * @return byte[]
     * @exception BadHandleException Bad NFS handle
     * @exception StaleHandleException Stale NFS handle
     */
    protected final byte[] getHandleForFile(NFSSrvSession nfsSess, byte[] handle, TreeConnection tree, String fname)
            throws BadHandleException, StaleHandleException {

        //  Get the share details via the share id hash
        int shareId = getShareIdFromHandle(handle);
        ShareDetails details = nfsSess.getNFSServer().findShareDetails( shareId);

        //  Check if this is a share handle
        String path = null;

        int dirId = -1;
        int fileId = -1;

        if (NFSHandle.isDirectoryHandle(handle)) {

            //    Get the directory id from the handle and get the associated path
            dirId = NFSHandle.unpackDirectoryId(handle);
            path = details.getFileIdCache().findPath(dirId);
        } else if (NFSHandle.isShareHandle(handle)) {

            // Use the root path
            dirId = 0;
            path = "\\";
        } else
            throw new BadHandleException();

        byte[] fHandle = null;

        try {

            //  Get the disk interface from the disk driver
            DiskInterface disk = (DiskInterface) tree.getSharedDevice().getInterface();

            // Build the path to the file
            String filePath = generatePath(path, fname);

            //  Check if the file/directory exists
            FileStatus fsts = disk.fileExists(nfsSess, tree, filePath);

            if (fsts == FileStatus.FileExists || fsts == FileStatus.DirectoryExists) {

                //  Get file information for the path
                FileInfo finfo = disk.getFileInformation(nfsSess, tree, filePath);

                if (finfo != null) {

                    // Get the file id
                    fileId = finfo.getFileId();

                    // Allocate and build the file handle
                    fHandle = new byte[NFS3.FileHandleSize];
                    if (finfo.isDirectory())
                        NFSHandle.packDirectoryHandle(shareId, dirId, fHandle);
                    else
                        NFSHandle.packFileHandle(shareId, dirId, fileId, fHandle);
                }
            }
        }
        catch (Exception ex) {
        }

        // Check if the file handle is valid
        if (fHandle == null)
            throw new BadHandleException();

        // Return the file handle
        return fHandle;
    }

    /**
     * Get the file id from the specified handle
     *
     * @param handle byte[]
     * @return String
     * @exception BadHandleException Bad NFS handle
     */
    protected final int getFileIdForHandle(byte[] handle)
            throws BadHandleException {

        //	Check the handle type
        int fileId = -1;

        if (NFSHandle.isShareHandle(handle)) {

            //	Root file id
            fileId = 0;
        } else if (NFSHandle.isDirectoryHandle(handle)) {

            //	Get the directory id from the handle
            fileId = NFSHandle.unpackDirectoryId(handle);
        } else if (NFSHandle.isFileHandle(handle)) {

            //	Get the file id from the handle
            fileId = NFSHandle.unpackFileId(handle);
        }

        //	Check if the file id is valid
        if (fileId == -1)
            throw new BadHandleException();

        //	Return the file id
        return fileId;
    }

    /**
     * Find, or open, the required network file using the file handle
     *
     * @param nfsSess     NFSSrvSession
     * @param handle   byte[]
     * @param conn     TreeConnection
     * @param readOnly boolean
     * @return NetworkFile
     * @exception BadHandleException   If the handle is not valid
     * @exception StaleHandleException If the file id cannot be converted to a path
     */
    protected final NetworkFile getNetworkFileForHandle(NFSSrvSession nfsSess, byte[] handle, TreeConnection conn, boolean readOnly)
            throws BadHandleException, StaleHandleException {

        //	Check if the handle is a file handle
        if (NFSHandle.isFileHandle(handle) == false)
            throw new BadHandleException("Not a file handle");

        //	Get the file id from the handle
        int fileId = getFileIdForHandle(handle);

        //	Get the per session network file cache, use this to synchronize
        NetworkFileCache fileCache = nfsSess.getFileCache();
        NetworkFile file = null;

        synchronized (fileCache) {

            //  Check the file cache, file may already be open
            file = fileCache.findFile(fileId, nfsSess);

            if (file == null || (file.getGrantedAccess() == NetworkFile.Access.READ_ONLY && readOnly == false)) {

                //	Get the path for the file
                String path = getPathForHandle(nfsSess, handle, conn);
                if (path == null)
                    throw new StaleHandleException();

                try {

                    // Close the existing file, if switching from read-only to writeable access
                    if (file != null)
                        file.closeFile();

                    //	Get the disk interface from the connection
                    DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

                    //	Open the network file
                    FileOpenParams params = new FileOpenParams(path, FileAction.OpenIfExists, ((readOnly) ? (AccessMode.ReadOnly) : (AccessMode.ReadWrite)), 0, 0);
                    file = disk.openFile(nfsSess, conn, params);

                    //	Add the file to the active file cache
                    if (file != null)
                        fileCache.addFile(file, conn, nfsSess);
                }
                catch (AccessDeniedException ex) {
                    if (nfsSess.hasDebug( NFSSrvSession.Dbg.FILE))
                        Debug.println(ex);
                }
                catch (Exception ex) {
                    Debug.println(ex);
                }
            } else if (file.getGrantedAccess() == NetworkFile.Access.READ_ONLY && readOnly == false) {

            }
        }

        //	Return the network file

        return file;
    }

    /**
     * Find the required network file using the file handle, or return null if the file has not been opened
     *
     * @param nfsSess   NFSSrvSession
     * @param handle byte[]
     * @param conn   TreeConnection
     * @return NetworkFile
     * @exception BadHandleException   If the handle is not valid
     * @exception StaleHandleException If the file id cannot be converted to a path
     */
    protected final NetworkFile getOpenNetworkFileForHandle(NFSSrvSession nfsSess, byte[] handle, TreeConnection conn)
            throws BadHandleException, StaleHandleException {

        //  Check if the handle is a file handle
        if (NFSHandle.isFileHandle(handle) == false)
            return null;

        //  Get the file id from the handle
        int fileId = getFileIdForHandle(handle);

        //  Get the per session network file cache, use this to synchronize
        NetworkFileCache fileCache = nfsSess.getFileCache();
        NetworkFile file = null;

        synchronized (fileCache) {

            //    Check the file cache, file may already be open
            file = fileCache.findFile(fileId, nfsSess);
        }

        //  Return the network file
        return file;
    }

    /**
     * Return the tree connection for the specified share index
     *
     * @param nfsSess    NFSSrvSession
     * @param shareId int
     * @return TreeConnection
     * @exception BadHandleException   If the handle is not valid
     */
    protected final TreeConnection getTreeConnection(NFSSrvSession nfsSess, int shareId) throws BadHandleException {

        //	Get the required tree connection from the session
        TreeConnection conn = nfsSess.findConnection(shareId);
        if (conn == null) {

            //	Get a template tree connection from the global list
            TreeConnection template = nfsSess.getNFSServer().findConnection(shareId);
            if (template == null) {

                // Check if any new shares have been added and try to find the required connection again
                if (nfsSess.getNFSServer().checkForNewShares() > 0)
                    template = nfsSess.getNFSServer().findConnection(shareId);
            }

            // Matching tree connection not found, handle is not valid
            if (template == null)
                throw new BadHandleException();

            //	Check if there is an access control manager configured
            if (nfsSess.getNFSServer().hasAccessControlManager()) {

                //	Check if the session has access to the shared filesystem
                AccessControlManager aclMgr = nfsSess.getNFSServer().getAccessControlManager();

                int sharePerm = aclMgr.checkAccessControl(nfsSess, template.getSharedDevice());

                if (sharePerm == AccessControl.NoAccess) {

                    // Session does not have access to the shared filesystem, mount should have failed or permissions
                    // may have changed.
                    throw new BadHandleException();
                }
                else if (sharePerm == AccessControl.Default)
                    sharePerm = AccessControl.ReadWrite;

                //	Create a new tree connection from the template
                conn = new TreeConnection(template.getSharedDevice());
                conn.setPermission(sharePerm);

                //	Add the tree connection to the active list for the session
                nfsSess.addConnection(conn);
            } else {

                // No access control manager, allow full access to the filesystem
                conn = new TreeConnection(template.getSharedDevice());
                conn.setPermission(ISMBAuthenticator.ShareStatus.WRITEABLE);

                //  Add the tree connection to the active list for the session
                nfsSess.addConnection(conn);
            }
        }

        //	Return the tree connection
        return conn;
    }

    /**
     * Pack a weak cache consistency structure
     *
     * @param rpc   RpcPacket
     * @param finfo FileInfo
     */
    protected final void packWccData(RpcPacket rpc, FileInfo finfo) {

        //	Pack the weak cache consistency data
        if (finfo != null) {

            //	Indicate that data follows
            rpc.packInt(Rpc.True);

            //	Pack the file size
            if (finfo.isDirectory())
                rpc.packLong(512L);
            else
                rpc.packLong(finfo.getSize());

            //	Pack the file times
            if (finfo.hasModifyDateTime()) {
                rpc.packInt((int) (finfo.getModifyDateTime() / 1000L));
                rpc.packInt(0);
            } else
                rpc.packLong(0);

            if (finfo.hasChangeDateTime()) {
                rpc.packInt((int) (finfo.getChangeDateTime() / 1000L));
                rpc.packInt(0);
            } else
                rpc.packLong(0);
        } else
            rpc.packInt(Rpc.False);
    }

    /**
     * Check if a file path contains any directory components
     *
     * @param fpath String
     * @return boolean
     */
    protected final boolean pathHasDirectories(String fpath) {

        //	Check if the file path is valid
        if (fpath == null || fpath.length() == 0)
            return false;

        //	Check if the file path starts with a directory component
        if (fpath.startsWith("\\") || fpath.startsWith("/") || fpath.startsWith(".."))
            return true;

        //	Check if the file path contains directory components
        if (fpath.indexOf("\\") != -1 || fpath.indexOf("/") != -1)
            return true;

        //	File path does not have any directory components
        return false;
    }

    /**
     * Pack the pre operation weak cache consistency data for the specified
     * file/directory
     *
     * @param nfsSess  NFSSrvSession
     * @param finfo FileInfo
     * @param rpc   RpcPacket
     */
    protected final void packPreOpAttr(NFSSrvSession nfsSess, FileInfo finfo, RpcPacket rpc) {

        //	Pack the file information
        if (finfo != null)
            packWccData(rpc, finfo);
        else
            rpc.packInt(Rpc.False);
    }

    /**
     * Pack the pre operation weak cache consistency data for the specified
     * file/directory
     *
     * @param nfsSess    NFSSrvSession
     * @param conn    TreeConnection
     * @param fhandle byte[]
     * @param rpc     RpcPacket
     * @exception BadHandleException   If the handle is not valid
     * @exception StaleHandleException If the file id cannot be converted to a path
     * @exception InvalidDeviceInterfaceException Device interface not valid
     * @exception IOException I/O error
     */
    protected final void packPreOpAttr(NFSSrvSession nfsSess, TreeConnection conn, byte[] fhandle, RpcPacket rpc)
            throws BadHandleException, StaleHandleException, InvalidDeviceInterfaceException, IOException {

        //	Get the path
        String path = getPathForHandle(nfsSess, fhandle, conn);

        //	Get the disk interface from the disk driver
        DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

        //	Get the file information for the path
        FileInfo finfo = disk.getFileInformation(nfsSess, conn, path);

        //	Pack the file information
        packWccData(rpc, finfo);
    }

    /**
     * Pack the post operation weak cache consistency data for the specified
     * file/directory
     *
     * @param nfsSess    NFSSrvSession
     * @param conn    TreeConnection
     * @param fhandle byte[]
     * @param rpc     RpcPacket
     * @exception BadHandleException   If the handle is not valid
     * @exception StaleHandleException If the file id cannot be converted to a path
     * @exception InvalidDeviceInterfaceException Device interface not valid
     * @exception IOException I/O error
     */
    protected final void packPostOpAttr(NFSSrvSession nfsSess, TreeConnection conn, byte[] fhandle, RpcPacket rpc)
            throws BadHandleException, StaleHandleException, InvalidDeviceInterfaceException, IOException {

        //	Get the path
        String path = getPathForHandle(nfsSess, fhandle, conn);

        //	Get the disk interface from the disk driver
        DiskInterface disk = (DiskInterface) conn.getSharedDevice().getInterface();

        //	Get the file information for the path
        FileInfo finfo = disk.getFileInformation(nfsSess, conn, path);

        //	Pack the file information
        if (finfo != null) {
            rpc.packInt(Rpc.True);
            packAttributes3(rpc, finfo, getShareIdFromHandle(fhandle));
        } else
            rpc.packInt(Rpc.False);
    }

    /**
     * Pack the post operation weak cache consistency data for the specified
     * file/directory
     *
     * @param nfsSess      NFSSrvSession
     * @param finfo     FileInfo
     * @param fileSysId int
     * @param rpc       RpcPacket
     */
    protected final void packPostOpAttr(NFSSrvSession nfsSess, FileInfo finfo, int fileSysId, RpcPacket rpc) {

        //	Pack the file information
        if (finfo != null) {

            //	Pack the post operation attributes
            rpc.packInt(Rpc.True);
            packAttributes3(rpc, finfo, fileSysId);
        } else
            rpc.packInt(Rpc.False);
    }

    /**
     * Generate a share relative path from the directory path and argument path.
     * The argument path may contain the value '..' in which case the directory
     * path will be stipped back one level.
     *
     * @param dirPath String
     * @param argPath String
     * @return String
     */
    protected final String generatePath(String dirPath, String argPath) {

        //	If the argument path is '..', if so then strip the directory path back a
        // level
        StringBuffer pathBuf = new StringBuffer();

        if (argPath.equals("..")) {

            //	Split the path into component directories
            String[] dirs = FileName.splitAllPaths(dirPath);

            //	Rebuild the path without the last directory
            pathBuf.append("\\");
            int dirCnt = dirs.length - 1;

            if (dirCnt > 0) {

                //	Add the paths
                for (int i = 0; i < dirCnt; i++) {
                    pathBuf.append(dirs[i]);
                    pathBuf.append("\\");
                }
            }

            //	Remove the trailing slash
            if (pathBuf.length() > 1)
                pathBuf.setLength(pathBuf.length() - 1);
        } else {

            //	Add the share relative path
            pathBuf.append(dirPath);
            if (dirPath.endsWith("\\") == false)
                pathBuf.append("\\");

            if (argPath.equals(".") == false)
                pathBuf.append(argPath);
        }

        //	Return the path
        return pathBuf.toString();
    }


}
