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

package org.filesys.server.filesys.db;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.auth.ClientInfo;
import org.filesys.server.core.DeviceContext;
import org.filesys.server.filesys.DiskDeviceContext;
import org.filesys.server.filesys.FileName;
import org.filesys.server.filesys.FileOfflineException;
import org.filesys.server.filesys.FileOpenParams;
import org.filesys.server.filesys.FileStatus;
import org.filesys.server.filesys.NetworkFile;
import org.filesys.server.filesys.cache.FileState;
import org.filesys.server.filesys.cache.FileStateCache;
import org.filesys.server.filesys.cache.FileStateListener;
import org.filesys.server.filesys.loader.BackgroundFileLoader;
import org.filesys.server.filesys.loader.DeleteFileRequest;
import org.filesys.server.filesys.loader.FileLoader;
import org.filesys.server.filesys.loader.FileLoaderException;
import org.filesys.server.filesys.loader.FileProcessor;
import org.filesys.server.filesys.loader.FileProcessorList;
import org.filesys.server.filesys.loader.FileRequest;
import org.filesys.server.filesys.loader.FileRequestQueue;
import org.filesys.server.filesys.loader.FileSegment;
import org.filesys.server.filesys.loader.FileSegmentInfo;
import org.filesys.server.filesys.loader.SingleFileRequest;
import org.filesys.util.NameValue;
import org.filesys.util.NameValueList;
import org.filesys.util.StringList;
import org.springframework.extensions.config.ConfigElement;

/**
 * Object Id File Loader Class
 *
 * <p>
 * The object id file loader loads/saves file data to a repository/storage device using an associated object id.
 *
 * <p>
 * This class relies on a seperate DBObjectIdInterface implementation to provide the methods to load and save the file
 * id/object id mappings to the database table.
 *
 * @author gkspencer
 */
public abstract class ObjectIdFileLoader implements FileLoader, BackgroundFileLoader, FileStateListener {

    // Status codes returned from the load/save worker thread processing
    public final static int StsSuccess  = 0;
    public final static int StsRequeue  = 1;
    public final static int StsError    = 2;

    // Temporary sub-directory/file/Jar prefix
    public static final String TempDirPrefix    = "ldr";
    public static final String TempFilePrefix   = "ldr_";
    public static final String JarFilePrefix    = "jar_";

    // Maximum files per temporary sub-directory
    private static final int MaximumFilesPerSubDir = 500;

    // Attributes attached to the file state
    public static final String DBFileSegmentInfo = "DBFileSegmentInfo";

    // Default/minimum/maximum number of worker threads to use
    public static final int DefaultWorkerThreads = 4;
    public static final int MinimumWorkerThreads = 1;
    public static final int MaximumWorkerThreads = 50;

    // File state timeout values
    public static final long SequentialFileExpire   = 3000L;    // milliseconds
    public static final long RequestProcessedExpire = 3000L;    // "
    public static final long RequestQueuedExpire    = 10000L;   // "

    // Default file data fragment size
    public final static long DEFAULT_FRAGSIZE   = 512L * 1024L; // 1/2Mb
    public final static long MIN_FRAGSIZE       = 64L * 1024L;  // 64Kb
    public final static long MAX_FRAGSIZE       = 1024L * 1024L * 1024L;    // 1Gb

    // Memory buffer maximum size
    public final static long MAX_MEMORYBUFFER = 512L * 1024L; // 1/2Mb

    // Name, used to prefix worker thread names
    private String m_name;

    // Maximum in-memory file request size and low water mark
    private int m_maxQueueSize;
    private int m_lowQueueSize;

    // Enable debug output, additional thread level debug output
    private boolean m_debug;
    private boolean m_threadDebug;

    // Number of worker threads to create for read/write requests
    private int m_readWorkers;
    private int m_writeWorkers;

    // Database device context
    private DBDeviceContext m_dbCtx;

    // File state cache, from device context
    private FileStateCache m_stateCache;

    // Database object id interface used to load/save the file id/object id mappings
    private DBObjectIdInterface m_dbObjectIdInterface;

    // Worker thread pool for loading/saving file data
    private BackgroundLoadSave m_backgroundLoader;

    // Temporary file area
    private String m_tempDirName;
    private File m_tempDir;

    // Temporary directory/file prefixes
    private String m_tempDirPrefix = TempDirPrefix;
    private String m_tempFilePrefix = TempFilePrefix;

    // Current temporary sub-directory
    private String m_curTempName;
    private File m_curTempDir;
    private int m_curTempIdx;

    // Maximum/current number of files in a temporary directory
    private int m_tempCount;
    private int m_tempMax;

    // List of file processors that process cached files before storing and after loading.
    private FileProcessorList m_fileProcessors;

    // Required attributes to add to file requests
    private StringList m_reqAttributes;

    /**
     * Class constructor
     */
    public ObjectIdFileLoader() {
    }

    /**
     * Return the database features required by this file loader. Return zero if no database features are required by
     * the loader.
     *
     * @return int
     */
    public int getRequiredDBFeatures() {

        // Return the database features required by the loader
        return DBInterface.FeatureObjectId + DBInterface.FeatureQueue;
    }

    /**
     * Return the database device context
     *
     * @return DBDeviceContext
     */
    public final DBDeviceContext getContext() {
        return m_dbCtx;
    }

    /**
     * Return the file state cache
     *
     * @return FileStateCache
     */
    protected final FileStateCache getStateCache() {
        return m_stateCache;
    }

    /**
     * Return the temporary directory name
     *
     * @return String
     */
    public final String getTemporaryDirectoryPath() {
        return m_tempDirName;
    }

    /**
     * Return the temporary directory
     *
     * @return File
     */
    public final File getTemporaryDirectory() {
        return m_tempDir;
    }

    /**
     * Return the current temporry sub-directory
     *
     * @return File
     */
    public final File getCurrentTempDirectory() {
        return m_curTempDir;
    }

    /**
     * Return the database object id interface
     *
     * @return DBObjectIdInterface
     */
    public final DBObjectIdInterface getDBObjectIdInterface() {
        return m_dbObjectIdInterface;
    }

    /**
     * Add a file processor to process files before storing and after loading.
     *
     * @param fileProc FileProcessor
     * @throws FileLoaderException Failed to add the file processor
     */
    public void addFileProcessor(FileProcessor fileProc)
            throws FileLoaderException {

        // Check if the file processor list has been allocated
        if (m_fileProcessors == null)
            m_fileProcessors = new FileProcessorList();

        // Add the file processor
        m_fileProcessors.addProcessor(fileProc);
    }

    /**
     * Determine if there are any file processors configured
     *
     * @return boolean
     */
    public final boolean hasFileProcessors() {
        return m_fileProcessors != null ? true : false;
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Return the maximum in-memory file request queue size
     *
     * @return int
     */
    public final int getMaximumQueueSize() {
        return m_maxQueueSize;
    }

    /**
     * Return the in-memory file request queue low water mark level
     *
     * @return int
     */
    public final int getLowQueueSize() {
        return m_lowQueueSize;
    }

    /**
     * Return the worker thread prefix
     *
     * @return String
     */
    public final String getName() {
        return m_name;
    }

    /**
     * Return the shared device name that this loader is associated with
     *
     * @return String
     */
    public final String getShareName() {
        return m_dbCtx.getShareName();
    }

    /**
     * Return the temporary sub-directory prefix
     *
     * @return String
     */
    public final String getTempDirectoryPrefix() {
        return m_tempDirPrefix;
    }

    /**
     * Return the temporary file prefix
     *
     * @return String
     */
    public final String getTempFilePrefix() {
        return m_tempFilePrefix;
    }

    /**
     * Set the worker thread name prefix
     *
     * @param name String
     */
    protected final void setName(String name) {
        m_name = name;
    }

    /**
     * Set the list of required attributes to be added to file requests
     *
     * @param attrNames StringList
     */
    protected final void setRequiredAttributes(StringList attrNames) {
        m_reqAttributes = attrNames;
    }

    /**
     * Check if there are any required attributes to be added to file requests
     *
     * @return boolean
     */
    protected final boolean hasRequiredAttributes() {
        return m_reqAttributes != null ? true : false;
    }

    /**
     * Create a network file for the specified file
     *
     * @param params FileOpenParams
     * @param fid    int
     * @param stid   int
     * @param did    int
     * @param create boolean
     * @param dir    boolean
     * @exception IOException I/O error
     * @exception FileNotFoundException File not found
     */
    public NetworkFile openFile(FileOpenParams params, int fid, int stid, int did, boolean create, boolean dir)
            throws IOException, FileNotFoundException {

        // Split the file name to get the name only
        String[] paths = FileName.splitPath(params.getPath());
        String name = paths[1];

        if (params.isStream())
            name = name + params.getStreamName();

        // Find, or create, the file state for the file/directory
        FileState fstate = m_stateCache.findFileState(params.getFullPath(), true);
        fstate.setExpiryTime(System.currentTimeMillis() + m_stateCache.getFileStateExpireInterval());

        // Check if the file is a directory
        DBNetworkFile netFile = null;

        if (dir == false) {

            // Create the network file and associated file segment
            CachedNetworkFile cacheFile = createNetworkFile(fstate, params, name, fid, stid, did);
            netFile = cacheFile;

            // Check if the file is being opened for sequential access and the data has not yet been loaded
            FileSegment fileSeg = cacheFile.getFileSegment();

            if (create == true || params.isOverwrite() == true) {

                // Indicate that the file data is available, this is a new file or the existing file is being
                // overwritten so there is no data to load.
                fileSeg.setStatus(FileSegmentInfo.Available);
            } else if (params.isSequentialAccessOnly() && fileSeg.isDataLoading() == false) {

                synchronized (cacheFile.getFileState()) {

                    // Create the temporary file
                    cacheFile.openFile(create);
                    cacheFile.closeFile();

                    // Queue a file data load request
                    if (fileSeg.isDataLoading() == false)
                        queueFileRequest(new SingleFileRequest(FileRequest.LOAD, cacheFile.getFileId(), cacheFile.getStreamId(),
                                fileSeg.getInfo(), cacheFile.getFullName(), fstate));
                }

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("ObjIdLoader Queued file load, SEQUENTIAL access, file=" + cacheFile.getFullName());
            }
        } else {

            // Create a placeholder network file for the directory
            netFile = new DirectoryNetworkFile(name, fid, did, m_stateCache.getFileStateProxy(fstate));

            // Debug
            if (Debug.EnableInfo && hasDebug())
                Debug.println("ObjIdLoader.openFile() DIR name=" + name + ", state=" + fstate);
        }

        // Return the network file
        return netFile;
    }

    /**
     * Close the network file
     *
     * @param sess    SrvSession
     * @param netFile NetworkFile
     * @exception IOException I/O error
     */
    public void closeFile(SrvSession sess, NetworkFile netFile)
            throws IOException {

        // Close the cached network file
        if (netFile instanceof CachedNetworkFile) {

            // Get the cached network file
            CachedNetworkFile cacheFile = (CachedNetworkFile) netFile;
            cacheFile.closeFile();

            // Get the file segment details
            FileSegment fileSeg = cacheFile.getFileSegment();

            // Check if the file data has been updated, if so then queue a file save
            if (fileSeg.isUpdated() && netFile.hasDeleteOnClose() == false) {

                // Set the modified date/time and file size for the file
                File tempFile = new File(fileSeg.getTemporaryFile());

                netFile.setModifyDate(tempFile.lastModified());
                netFile.setFileSize(tempFile.length());

                // Queue a file save request to save the data back to the repository, if not already queued
                if (fileSeg.isSaveQueued() == false) {

                    // Create a file save request for the updated file segment
                    SingleFileRequest fileReq = new SingleFileRequest(FileRequest.SAVE, cacheFile.getFileId(), cacheFile.getStreamId(),
                            fileSeg.getInfo(), netFile.getFullName(), cacheFile.getFileState());

                    // Check if there are any attributes to be added to the file request
                    if (hasRequiredAttributes() && sess != null) {

                        // Check if the user name is required
                        if (m_reqAttributes.containsString(FileRequest.AttrUserName)) {

                            // Add the user name attribute
                            ClientInfo cInfo = sess.getClientInformation();
                            String userName = "";

                            if (cInfo != null && cInfo.getUserName() != null)
                                userName = cInfo.getUserName();

                            fileReq.addAttribute(new NameValue(FileRequest.AttrUserName, userName));
                        }

                        // Check if the protocol type is required
                        if (m_reqAttributes.containsString(FileRequest.AttrProtocol)) {

                            // Add the protocol type attribute
                            fileReq.addAttribute(new NameValue(FileRequest.AttrProtocol, sess.getProtocolName()));
                        }
                    }

                    // Set the file segment status
                    fileSeg.setStatus(FileSegmentInfo.SaveWait, true);

                    // Queue the file save request
                    queueFileRequest(fileReq);
                } else if (Debug.EnableInfo && hasDebug()) {

                    // DEBUG
                    Debug.println("## FileLoader Save already queued for " + fileSeg);
                }
            }

            // Update the cache timeout for the temporary file if there are no references to the file. If the file was
            // opened for sequential access only it will be expired quicker.
            else if (cacheFile.getFileState().getOpenCount() == 0) {

                // If the file was opened for sequential access only then we can delete it from the temporary area
                // sooner
                long tmo = System.currentTimeMillis();

                if (cacheFile.isSequentialOnly())
                    tmo += SequentialFileExpire;
                else
                    tmo += m_stateCache.getFileStateExpireInterval();

                // Set the file state expiry, the local file data will be deleted when the file state expires (if there
                // are still no references to the file).
                cacheFile.getFileState().setExpiryTime(tmo);
            }

            // If the database is not online and the file is marked for delete then queue a delete file request to do
            // the delete when the database is back online
            if (m_dbCtx.isAvailable() == false && netFile.hasDeleteOnClose()) {

                // Queue an offline delete request for the file
                DeleteFileRequest deleteReq = new DeleteFileRequest(cacheFile.getFileId(), cacheFile.getStreamId(), fileSeg
                        .getTemporaryFile(), cacheFile.getFullNameStream(), cacheFile.getFileState());
                m_dbCtx.addOfflineFileDelete(deleteReq);

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("## FileLoader queued offline delete, " + deleteReq);
            }
        }
    }

    /**
     * Delete the specified file data
     *
     * @param fname String
     * @param fid   int
     * @param stid  int
     * @exception IOException I/O error
     */
    public void deleteFile(String fname, int fid, int stid)
            throws IOException {

        // Delete the file data from the database
        try {

            // Find the associated file state
            FileState fstate = m_stateCache.findFileState(fname, false);

            if (fstate != null) {

                // Get the file segment details
                FileSegmentInfo fileSegInfo = (FileSegmentInfo) fstate.removeAttribute(DBFileSegmentInfo);
                if (fileSegInfo != null) {
                    try {

                        // Change the file segment status
                        fileSegInfo.setQueued(false);
                        fileSegInfo.setStatus(FileSegmentInfo.Initial);

                        // Delete the temporary file
                        fileSegInfo.deleteTemporaryFile();
                    }
                    catch (Exception ex) {

                        // DEBUG
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("## ObjIdLoader failed to delete temp file " + fileSegInfo.getTemporaryFile());
                    }
                }
            }
        }
        catch (Exception ex) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("## ObjIdLoader deleteFile() error, " + ex.toString());
        }
    }

    /**
     * Request file data to be loaded/saved
     *
     * @param req FileRequest
     */
    public void queueFileRequest(FileRequest req) {

        // Pass the request to the background load/save thread pool
        m_backgroundLoader.queueFileRequest(req);
    }

    /**
     * Load a file
     *
     * @param req FileRequest
     * @return int
     * @throws Exception Failed to load the file data
     */
    public int loadFile(FileRequest req)
            throws Exception {

        // DEBUG
        long startTime = 0L;
        SingleFileRequest loadReq = (SingleFileRequest) req;

        if (Debug.EnableInfo && hasDebug()) {
            Debug.println("## ObjIdLoader loadFile() req=" + loadReq.toString() + ", thread=" + Thread.currentThread().getName());
            startTime = System.currentTimeMillis();
        }

        // Check if the temporary file still exists, if not then the file has been deleted from the filesystem
        File tempFile = new File(loadReq.getTemporaryFile());
        FileSegment fileSeg = findFileSegmentForPath(loadReq.getVirtualPath());

        if (tempFile.exists() == false || fileSeg == null) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("  Temporary file deleted");

            // Return an error status
            fileSeg.setStatus(FileSegmentInfo.Error, false);
            return StsError;
        }

        // DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("## ObjIdLoader fileSeg=" + fileSeg.getTemporaryFile() + ", virtPath=" + loadReq.getVirtualPath());

        // Load the file data
        int loadSts = StsRequeue;
        String objectId = null;

        int fileId = loadReq.getFileId();
        int strmId = loadReq.getStreamId();

        try {

            // Update the segment status
            fileSeg.setStatus(FileSegmentInfo.Loading);

            // Get the object id for the file
            objectId = getDBObjectIdInterface().loadObjectId(fileId, strmId);

            if (objectId != null) {

                // Load the file data
                loadFileData(fileId, strmId, objectId, fileSeg);

                // Set the load status
                loadSts = StsSuccess;

                // DEBUG
                if (Debug.EnableInfo && hasDebug()) {
                    long endTime = System.currentTimeMillis();
                    Debug.println("## ObjIdLoader loaded fid=" + loadReq.getFileId() + ", stream=" + loadReq.getStreamId()
                            + ", time=" + (endTime - startTime) + "ms");
                }
            } else {

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("## ObjIdLoader No object id mapping for fid=" + loadReq.getFileId() + ", stream=" + loadReq.getStreamId());

                // Indicate a load success
                loadSts = StsSuccess;
            }
        }
        catch (DBException ex) {

            // DEBUG
            if (Debug.EnableError && hasDebug())
                Debug.println(ex);

            // Indicate the file load failed
            loadSts = StsError;
        }
        catch (FileOfflineException ex) {

            // DEBUG
            if (Debug.EnableError && hasDebug())
                Debug.println(ex);

            // Indicate the file load failed
            loadSts = StsError;
        }
        catch (IOException ex) {

            // DEBUG
            if (Debug.EnableError && hasDebug())
                Debug.println(ex);

            // Indicate the file load failed
            loadSts = StsRequeue;
        }
        catch (Exception ex) {

            // DEBUG
            if (Debug.EnableError && hasDebug())
                Debug.println(ex);

            // Indicate the file load failed
            loadSts = StsError;
        }

        // Clear the last modified date/time of the temporary file to indicate it has not been updated
        tempFile.setLastModified(0L);

        // Check if the file was loaded successfully
        if (loadSts == StsSuccess) {

            // Signal that the file data is available
            fileSeg.signalDataAvailable();

            // Update the file status
            fileSeg.setStatus(FileSegmentInfo.Available, false);

            // Run the file load processors
            runFileLoadedProcessors(getContext(), loadReq.getFileState(), fileSeg);
        } else if (loadSts == StsError) {

            // Set the file status to indicate error to any client reading threads
            fileSeg.setStatus(FileSegmentInfo.Error, false);

            // Wakeup any threads waiting on data for this file
            fileSeg.setReadableLength(0L);
            fileSeg.signalDataAvailable();

            // Delete the temporary file
            fileSeg.deleteTemporaryFile();
        }

        // Return the load file status
        return loadSts;
    }

    /**
     * Store a file
     *
     * @param req FileRequest
     * @return int
     * @throws Exception Failed to save the file data
     */
    public int storeFile(FileRequest req)
            throws Exception {

        // Check for a single file request
        int saveSts = StsError;
        SingleFileRequest saveReq = (SingleFileRequest) req;

        // Check if the temporary file still exists, if not then the file has been deleted from the filesystem
        File tempFile = new File(saveReq.getTemporaryFile());
        FileSegment fileSeg = findFileSegmentForPath(saveReq.getVirtualPath());

        if (tempFile.exists() == false || fileSeg == null) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug())
                Debug.println("  Temporary file deleted");

            // Return an error status
            return StsError;
        }

        // Run any file store processors
        runFileStoreProcessors(m_dbCtx, saveReq.getFileState(), fileSeg);

        // Update the segment status, and clear the updated flag
        fileSeg.setStatus(FileSegmentInfo.Saving);
        fileSeg.getInfo().setUpdated(false);

        // Save the file data
        try {

            // Save the file data and get the assigned object id
            String objectId = saveFileData(saveReq.getFileId(), saveReq.getStreamId(), fileSeg, req.getAttributes());

            // Save the object id to the mapping database
            getDBObjectIdInterface().saveObjectId(saveReq.getFileId(), saveReq.getStreamId(), objectId);

            // Indicate that the save was successful
            saveSts = StsSuccess;
        }
        catch (DBException ex) {

            // DEBUG
            if (Debug.EnableError && hasDebug())
                Debug.println(ex);

            // Indicate the file save failed
            saveSts = StsError;
        }
        catch (IOException ex) {

            // DEBUG
            if (Debug.EnableError && hasDebug())
                Debug.println(ex);

            // Indicate the file save failed
            saveSts = StsError;
        }

        // Update the segment status
        if (saveSts == StsSuccess)
            fileSeg.setStatus(FileSegmentInfo.Saved, false);
        else
            fileSeg.setStatus(FileSegmentInfo.Error, false);

        // Return the data save status
        return saveSts;
    }

    /**
     * Load the file data
     *
     * @param fileId   int
     * @param streamId int
     * @param objectId String
     * @param fileSeg  FileSegment
     * @exception IOException I/O error
     */
    public abstract void loadFileData(int fileId, int streamId, String objectId, FileSegment fileSeg)
            throws IOException;

    /**
     * Save file data
     *
     * @param fileId   int
     * @param streamId int
     * @param fileSeg  FileSegment
     * @param attrs    NameValueList
     * @return String
     * @exception IOException I/O error
     */
    public abstract String saveFileData(int fileId, int streamId, FileSegment fileSeg, NameValueList attrs)
            throws IOException;

    /**
     * Initialize the file loader using the specified parameters
     *
     * @param params ConfigElement
     * @param ctx    DeviceContext
     * @exception FileLoaderException Failed to initialize the file loader
     * @exception IOException I/O error
     */
    public void initializeLoader(ConfigElement params, DeviceContext ctx)
            throws FileLoaderException, IOException {

        // Debug output enable
        if (params.getChild("Debug") != null)
            m_debug = true;

        // Get the count of worker threads to create
        ConfigElement nameVal = params.getChild("ThreadPoolSize");
        if (nameVal != null && (nameVal.getValue() == null || nameVal.getValue().length() == 0))
            throw new FileLoaderException("FileLoader ThreadPoolSize parameter is null");

        // Convert the thread pool size parameter, or use the default value
        m_readWorkers = DefaultWorkerThreads;
        m_writeWorkers = DefaultWorkerThreads;

        if (nameVal != null) {
            try {

                // Check for a single value or split read/write values
                String numVal = nameVal.getValue();
                int rdCnt = -1;
                int wrtCnt = -1;
                int pos = numVal.indexOf(':');

                if (pos == -1) {

                    // Use the same number of read and write worker threads
                    rdCnt = Integer.parseInt(numVal);
                    wrtCnt = rdCnt;
                } else {

                    // Split the string value into read and write values, and convert to integers
                    String val = numVal.substring(0, pos);
                    rdCnt = Integer.parseInt(val);

                    val = numVal.substring(pos + 1);
                    wrtCnt = Integer.parseInt(val);
                }

                // Set the read/write thread pool sizes
                m_readWorkers = rdCnt;
                m_writeWorkers = wrtCnt;
            }
            catch (NumberFormatException ex) {
                throw new FileLoaderException("ObjIdLoader Invalid ThreadPoolSize value, " + ex.toString());
            }
        }

        // Range check the thread pool size
        if (m_readWorkers < MinimumWorkerThreads || m_readWorkers > MaximumWorkerThreads)
            throw new FileLoaderException("ObjIdLoader Invalid ThreadPoolSize (read), valid range is " + MinimumWorkerThreads
                    + "-" + MaximumWorkerThreads);

        if (m_writeWorkers < MinimumWorkerThreads || m_writeWorkers > MaximumWorkerThreads)
            throw new FileLoaderException("ObjIdLoader Invalid ThreadPoolSize (write), valid range is " + MinimumWorkerThreads
                    + "-" + MaximumWorkerThreads);

        // Get the temporary file data directory
        ConfigElement tempArea = params.getChild("TempDirectory");
        if (tempArea == null || tempArea.getValue() == null || tempArea.getValue().length() == 0)
            throw new FileLoaderException("FileLoader TempDirectory not specified or null");

        // Validate the temporary directory
        File tempDir = new File(tempArea.getValue());
        if (tempDir.exists() == false) {

            // Temporary directory does not exist, create the directory
            if (tempDir.mkdir() == false)
                throw new FileLoaderException("Failed to create temporary directory " + tempDir.getAbsolutePath());
        }

        m_tempDirName = tempDir.getAbsolutePath();
        if (m_tempDirName != null && m_tempDirName.endsWith(File.separator) == false)
            m_tempDirName = m_tempDirName + File.separator;

        m_tempDir = new File(m_tempDirName);
        if (m_tempDir.exists() == false || m_tempDir.isDirectory() == false)
            throw new FileLoaderException("FileLoader TempDirectory does not exist, or is not a directory, " + m_tempDirName);

        if (m_tempDir.canWrite() == false)
            throw new FileLoaderException("FileLoader TempDirectory is not writeable, " + m_tempDirName);

        // Create the starting temporary sub-directory
        createNewTempDirectory();

        // Check if the maxmimum files per sub-directory has been specified
        ConfigElement maxFiles = params.getChild("MaximumFilesPerDirectory");
        if (maxFiles != null) {
            try {
                m_tempMax = Integer.parseInt(maxFiles.getValue());

                // Range check the maximum files per sub-directory
                if (m_tempMax < 10 || m_tempMax > 20000)
                    throw new FileLoaderException("FileLoader MaximumFilesPerDirectory out of valid range (10-20000)");
            }
            catch (NumberFormatException ex) {
                throw new FileLoaderException("FileLoader MaximumFilesPerDirectory invalid, " + maxFiles.getValue());
            }
        } else
            m_tempMax = MaximumFilesPerSubDir;

        // Check if there are any file processors configured
        ConfigElement fileProcs = params.getChild("FileProcessors");
        if (fileProcs != null) {

            // Validate the file processor classes and add to the file loader
            List<ConfigElement> elems = fileProcs.getChildren();

            for (ConfigElement className : elems) {

                // Get the current file processor class name
                if (className == null || className.getValue() == null || className.getValue().length() == 0)
                    throw new FileLoaderException("Empty file processor class name");

                // Validate the file processor class name and create an instance of the file processor
                try {

                    // Create the file processor instance
                    Object procObj = Class.forName(className.getValue()).newInstance();

                    // Check that it is a file processor implementation
                    if (procObj instanceof FileProcessor) {

                        // Add to the list of file processors
                        addFileProcessor((FileProcessor) procObj);
                    } else
                        throw new FileLoaderException("Class " + className.getValue() + " is not a FileProcessor implementation");
                }
                catch (ClassNotFoundException ex) {
                    throw new FileLoaderException("File processor class not found, " + className.getValue());
                }
                catch (InstantiationException ex) {
                    throw new FileLoaderException("File processor exception, " + ex.toString());
                }
                catch (IllegalAccessException ex) {
                    throw new FileLoaderException("File processor exception, " + ex.toString());
                }
            }
        }

        // Check if the database interface being used supports the required features
        if (ctx instanceof DBDeviceContext) {

            // Access the database device context
            m_dbCtx = (DBDeviceContext) ctx;

            // Check if the request queue is supported by the database interface
            if (getContext().getDBInterface().supportsFeature(DBInterface.FeatureQueue) == false)
                throw new FileLoaderException("DBLoader requires queue support in database interface");

            if (getContext().getDBInterface() instanceof DBQueueInterface == false)
                throw new FileLoaderException("Database interface does not implement queue interface");

            // Check if the object id feature is supported by the database interface
            if (getContext().getDBInterface().supportsFeature(DBInterface.FeatureObjectId) == false)
                throw new FileLoaderException("DBLoader requires data support in database interface");

            if (getContext().getDBInterface() instanceof DBObjectIdInterface)
                m_dbObjectIdInterface = (DBObjectIdInterface) getContext().getDBInterface();
            else
                throw new FileLoaderException("Database interface does not implement object id interface");
        } else
            throw new FileLoaderException("Requires database device context");

        // Check if background loader debug is enabled
        if (params.getChild("ThreadDebug") != null)
            m_threadDebug = true;
    }

    /**
     * Start the file loader
     *
     * @param ctx DeviceContext
     */
    public void startLoader(DeviceContext ctx) {

        // Get the file state cache from the context
        m_stateCache = getContext().getStateCache();

        // Add the file loader as a file state listener so that we can cleanup temporary data files
        m_stateCache.addStateListener(this);

        // Get the database interface
        DBQueueInterface dbQueue = null;

        if (getContext().getDBInterface() instanceof DBQueueInterface)
            dbQueue = (DBQueueInterface) getContext().getDBInterface();
        else
            throw new RuntimeException("Database interface does not implement queue interface");

        // Perform a queue cleanup before starting the thread pool. This will check the temporary cache area and delete
        // files that are not part of a queued save/transaction save request.
        FileRequestQueue recoveredQueue = null;

        try {

            // Cleanup the temporary cache area and queue
            recoveredQueue = dbQueue.performQueueCleanup(m_tempDir, TempDirPrefix, TempFilePrefix, JarFilePrefix);

            // DEBUG
            if (recoveredQueue != null && Debug.EnableInfo && hasDebug())
                Debug.println("[DBLoader] Cleanup recovered " + recoveredQueue.numberOfRequests() + " pending save files");
        }
        catch (DBException ex) {

            // DEBUG
            if (Debug.EnableError && hasDebug())
                Debug.println(ex);
        }

        // Check if there are any file save requests pending in the queue database
        FileRequestQueue saveQueue = new FileRequestQueue();

        try {
            dbQueue.loadFileRequests(1, FileRequest.SAVE, saveQueue, 1);
        }
        catch (DBException ex) {
        }

        // Create the background load/save thread pool
        m_backgroundLoader = new BackgroundLoadSave("DBLdr", dbQueue, m_stateCache, this);

        m_backgroundLoader.setReadWorkers(m_readWorkers);
        m_backgroundLoader.setWriteWorkers(m_writeWorkers);

        m_backgroundLoader.setDebug(m_threadDebug);

        // Start the file loader threads, start the request loading if there are pending file save requests
        m_backgroundLoader.startThreads(saveQueue.numberOfRequests());

        // Queue the recovered file save requests
        if (recoveredQueue != null) {

            // Queue the file save requests
            while (recoveredQueue.numberOfRequests() > 0)
                queueFileRequest(recoveredQueue.removeRequestNoWait());
        }
    }

    /**
     * Shutdown the file loader and release all resources
     *
     * @param immediate boolean
     */
    public void shutdownLoader(boolean immediate) {

        // Shutdown the background load/save thread pool
        if (m_backgroundLoader != null)
            m_backgroundLoader.shutdownThreads();
    }

    /**
     * Run the file store file processors
     *
     * @param context DiskDeviceContext
     * @param state   FileState
     * @param segment FileSegment
     */
    protected final void runFileStoreProcessors(DiskDeviceContext context, FileState state, FileSegment segment) {

        // Check if there are any file processors configured
        if (m_fileProcessors == null || m_fileProcessors.numberOfProcessors() == 0)
            return;

        try {

            // Run all of the file store processors
            for (int i = 0; i < m_fileProcessors.numberOfProcessors(); i++) {

                // Get the current file processor
                FileProcessor fileProc = m_fileProcessors.getProcessorAt(i);

                // Run the file processor
                fileProc.processStoredFile(context, state, segment);
            }

            // Make sure the file segment is closed after processing
            segment.closeFile();
        }
        catch (Exception ex) {

            // DEBUG
            if (Debug.EnableError && hasDebug()) {
                Debug.println("$$ Store file processor exception");
                Debug.println(ex);
            }
        }
    }

    /**
     * Run the file load file processors
     *
     * @param context DiskDeviceContext
     * @param state   FileState
     * @param segment FileSegment
     */
    protected final void runFileLoadedProcessors(DiskDeviceContext context, FileState state, FileSegment segment) {

        // Check if there are any file processors configured
        if (m_fileProcessors == null || m_fileProcessors.numberOfProcessors() == 0)
            return;

        try {

            // Run all of the file load processors
            for (int i = 0; i < m_fileProcessors.numberOfProcessors(); i++) {

                // Get the current file processor
                FileProcessor fileProc = m_fileProcessors.getProcessorAt(i);

                // Run the file processor
                fileProc.processLoadedFile(context, state, segment);
            }

            // Make sure the file segment is closed after processing
            segment.closeFile();
        }
        catch (Exception ex) {

            // DEBUG
            if (Debug.EnableError && hasDebug()) {
                Debug.println("$$ Load file processor exception");
                Debug.println(ex);
            }
        }
    }

    /**
     * Re-create, or attach, a file request to the file state.
     *
     * @param fid      int
     * @param tempPath String
     * @param virtPath String
     * @param sts      int
     * @return FileState
     */
    protected final FileState createFileStateForRequest(int fid, String tempPath, String virtPath, int sts) {

        // Find, or create, the file state for the file/directory
        FileState state = m_stateCache.findFileState(virtPath, true);

        synchronized (state) {

            // Prevent the file state from expiring whilst the request is queued against it
            state.setExpiryTime(FileState.NoTimeout);

            // Indicate that the file exists, set the unique file id
            state.setFileStatus(FileStatus.FileExists);
            state.setFileId(fid);

            // Check if the file segment has been attached to the file state
            FileSegmentInfo fileSegInfo = (FileSegmentInfo) state.findAttribute(DBFileSegmentInfo);
            FileSegment fileSeg = null;

            if (fileSegInfo == null) {

                // Create a new file segment
                fileSegInfo = new FileSegmentInfo();
                fileSegInfo.setTemporaryFile(tempPath);

                fileSeg = new FileSegment(fileSegInfo, true);
                fileSeg.setStatus(sts, true);

                // Add the segment to the file state cache
                state.addAttribute(DBFileSegmentInfo, fileSegInfo);
            } else {

                // Make sure the file segment indicates its part of a queued request
                fileSeg = new FileSegment(fileSegInfo, true);
                fileSeg.setStatus(sts, true);
            }
        }

        // Return the file state
        return state;
    }

    /**
     * Find the file segment for the specified virtual path
     *
     * @param virtPath String
     * @return FileSegment
     */
    protected final FileSegment findFileSegmentForPath(String virtPath) {

        // Get the file state for the virtual path
        FileState fstate = m_stateCache.findFileState(virtPath, false);
        if (fstate == null)
            return null;

        // Get the file segment
        FileSegmentInfo segInfo = null;
        FileSegment fileSeg = null;

        synchronized (fstate) {

            // Get the associated file segment
            segInfo = (FileSegmentInfo) fstate.findAttribute(DBFileSegmentInfo);
            fileSeg = new FileSegment(segInfo, true);
        }

        // Return the file segment
        return fileSeg;
    }

    /**
     * Determine if the loader supports NTFS streams
     *
     * @return boolean
     */
    public boolean supportsStreams() {

        // Check if the database implementation supports the NTFS streams feature
        if (getContext() != null)
            return getContext().getDBInterface().supportsFeature(DBInterface.FeatureNTFS);
        return true;
    }

    /**
     * Create a new temporary sub-directory
     */
    private final void createNewTempDirectory() {

        // Create the starting temporary sub-directory
        m_curTempName = m_tempDirName + getTempDirectoryPrefix() + m_curTempIdx++;
        m_curTempDir = new File(m_curTempName);

        if (m_curTempDir.exists() == false)
            m_curTempDir.mkdir();

        // Clear the temporary file count
        m_tempCount = 0;

        // DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("ObjIdLoader Created new temp directory - " + m_curTempName);
    }

    /**
     * File state has expired. The listener can control whether the file state is removed from the cache, or not.
     *
     * @param state FileState
     * @return true to remove the file state from the cache, or false to leave the file state in the cache
     */
    public boolean fileStateExpired(FileState state) {

        // Check if the file state has an associated file segment
        FileSegmentInfo segInfo = (FileSegmentInfo) state.findAttribute(DBFileSegmentInfo);
        boolean expire = true;

        if (segInfo != null) {

            // Check if the file has a request queued
            if (segInfo.isQueued() == false) {

                try {

                    // Delete the temporary file and reset the segment status so that the data may be loaded again
                    // if required.
                    if (segInfo.hasStatus() != FileSegmentInfo.Initial) {

                        // Delete the temporary file
                        try {
                            segInfo.deleteTemporaryFile();
                        }
                        catch (IOException ex) {

                            // DEBUG
                            if (Debug.EnableError) {
                                Debug.println("Delete temp file error: " + ex.toString());
                                File tempFile = new File(segInfo.getTemporaryFile());
                                Debug.println("  TempFile file=" + tempFile.getAbsolutePath() + ", exists=" + tempFile.exists());
                                Debug.println("  FileState state=" + state);
                                Debug.println("  FileSegmentInfo segInfo=" + segInfo);
                                Debug.println("  StateCache size=" + m_stateCache.numberOfStates());
                            }
                        }

                        // Remove the file segment, reset the file segment back to the initial state
                        state.removeAttribute(DBFileSegmentInfo);
                        segInfo.setStatus(FileSegmentInfo.Initial);

                        // Reset the file state to indicate file data load required
                        state.setDataStatus(FileState.DataStatus.LoadWait);

                        // Check if the temporary file sub-directory is now empty, and it is not the current temporary
                        // sub-directory
                        if (segInfo.getTemporaryFile().startsWith(m_curTempName) == false) {

                            // Check if the sub-directory is empty
                            File tempFile = new File(segInfo.getTemporaryFile());
                            File subDir = tempFile.getParentFile();
                            String[] files = subDir.list();
                            if (files == null || files.length == 0) {

                                // Delete the sub-directory
                                subDir.delete();

                                // DEBUG
                                if (Debug.EnableInfo && hasDebug())
                                    Debug.println("$$ Deleted temporary directory " + subDir.getPath() + ", curTempName="
                                            + m_curTempName + ", tempFile=" + segInfo.getTemporaryFile());
                            }
                        }

                        // Indicate that the file state should not be deleted
                        expire = false;

                        // Debug
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("$$ Deleted temporary file " + segInfo.getTemporaryFile() + " [EXPIRED] $$");
                    }

                    // If the file state is not to be deleted reset the file state expiration timer
                    if (expire == false)
                        state.setExpiryTime(System.currentTimeMillis() + m_stateCache.getFileStateExpireInterval());
                }
                catch (Exception ex) {

                    // DEBUG
                    if (Debug.EnableError) {
                        Debug.println("$$  " + ex.toString());
                        Debug.println("  state=" + state);
                    }
                }
            } else {

                // DEBUG
                if (hasDebug()) {
                    File tempFile = new File(segInfo.getTemporaryFile());
                    if (tempFile.exists() == false) {
                        Debug.println("== Skipped file state, queued " + state);
                        Debug.println("   File seg=" + segInfo);
                    }
                }

                // File state is queued, do not expire
                expire = false;
            }
        } else if (state.isDirectory()) {

            // Nothing to do when it's a directory, just allow it to expire
            expire = true;
        } else if (Debug.EnableInfo && hasDebug())
            Debug.println("$$ Expiring state=" + state);

        // Return true if the file state can be expired
        return expire;
    }

    /**
     * File state cache is closing down, any resources attached to the file state must be released.
     *
     * @param state FileState
     */
    public void fileStateClosed(FileState state) {

        // DEBUG
        if (state == null) {
            Debug.println("%%%%% FileLoader.fileStateClosed() state=NULL %%%%%");
            return;
        }

        // Check if the file state has an associated file
        FileSegmentInfo segInfo = (FileSegmentInfo) state.findAttribute(DBFileSegmentInfo);

        if (segInfo != null && segInfo.isQueued() == false && segInfo.hasStatus() != FileSegmentInfo.SaveWait) {

            try {

                // Delete the temporary file
                segInfo.deleteTemporaryFile();

                // Debug
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("$$ Deleted temporary file " + segInfo.getTemporaryFile() + " [CLOSED] $$");
            }
            catch (IOException ex) {
            }
        }
    }

    /**
     * Create a file segment to load/save the file data
     *
     * @param state  FileState
     * @param params FileOpenParams
     * @param fname  String
     * @param fid    int
     * @param stid   int
     * @param did    int
     * @return CachedNetworkFile
     * @exception IOException I/O error
     */
    private final CachedNetworkFile createNetworkFile(FileState state, FileOpenParams params, String fname, int fid, int stid,
                                                      int did)
            throws IOException {

        // The file state is used to synchronize the creation of the file segment as there may be other
        // sessions opening the file at the same time. We have to be careful that only one thread creates the
        // file segment.
        FileSegment fileSeg = null;
        CachedNetworkFile netFile = null;

        synchronized (state) {

            // Check if the file segment has been attached to the file state
            FileSegmentInfo fileSegInfo = (FileSegmentInfo) state.findAttribute(DBFileSegmentInfo);
            if (fileSegInfo == null) {

                // Check if we need to create a new temporary sub-drectory
                if (m_tempCount++ >= m_tempMax)
                    createNewTempDirectory();

                // Create a unique temporary file name
                StringBuffer tempName = new StringBuffer();
                tempName.append(getTempFilePrefix());
                tempName.append(fid);

                if (stid > 0) {
                    tempName.append("_");
                    tempName.append(stid);

                    // DEBUG
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("## Temp file for stream ##");
                }

                tempName.append(".tmp");

                // Create a new file segment
                fileSegInfo = new FileSegmentInfo();
                fileSeg = FileSegment.createSegment(fileSegInfo, tempName.toString(), m_curTempDir,
                        params.isReadOnlyAccess() == false);

                // Add the segment to the file state cache
                state.addAttribute(DBFileSegmentInfo, fileSegInfo);

                // Check if the file is zero length, if so then set the file segment state to indicate it is available
                DBFileInfo finfo = (DBFileInfo) state.findAttribute(FileState.FileInformation);
                if (finfo != null && finfo.getSize() == 0)
                    fileSeg.setStatus(FileSegmentInfo.Available);
            } else {

                // Create the file segment to map to the existing temporary file
                fileSeg = new FileSegment(fileSegInfo, params.isReadOnlyAccess() == false);

                // Check if the temporary file exists, if not then create it
                File tempFile = new File(fileSeg.getTemporaryFile());
                if (tempFile.exists() == false) {

                    // Create the temporary file
                    tempFile.createNewFile();

                    // Reset the file segment state to indicate a file load is required
                    fileSeg.setStatus(FileSegmentInfo.Initial);
                }
            }

            // Create the new network file
            netFile = new CachedNetworkFile(fname, fid, stid, did, m_stateCache.getFileStateProxy(state), fileSeg, this);

            netFile.setGrantedAccess(params.isReadOnlyAccess() ? NetworkFile.Access.READ_ONLY : NetworkFile.Access.READ_WRITE);
            netFile.setSequentialOnly(params.isSequentialAccessOnly());
            netFile.setAttributes(params.getAttributes());
            netFile.setFullName(params.getPath());

            if (stid != 0)
                netFile.setStreamName(params.getStreamName());
        }

        // Return the network file
        return netFile;
    }

    /**
     * Set the database context
     *
     * @param dbCtx DBDeviceContext
     */
    public final void setContext(DBDeviceContext dbCtx) {
        m_dbCtx = dbCtx;
    }
}
