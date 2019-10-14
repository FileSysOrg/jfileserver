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

package org.filesys.ftp;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TimeZone;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.auth.ClientInfo;
import org.filesys.server.auth.ISMBAuthenticator;
import org.filesys.server.auth.acl.AccessControl;
import org.filesys.server.auth.acl.AccessControlManager;
import org.filesys.server.core.ShareType;
import org.filesys.server.core.SharedDevice;
import org.filesys.server.core.SharedDeviceList;
import org.filesys.server.filesys.*;
import org.filesys.util.UTF8Normalizer;
import org.filesys.util.WildCard;

/**
 * FTP Server Session Class
 *
 * @author gkspencer
 */
public class FTPSrvSession extends SrvSession implements Runnable {

    // Constants
    //
    // Debug flag values
    public static final int DBG_STATE       = 0x00000001; // Session state changes
    public static final int DBG_RXDATA      = 0x00000002; // Received data
    public static final int DBG_TXDATA      = 0x00000004; // Transmit data
    public static final int DBG_DUMPDATA    = 0x00000008; // Dump data packets
    public static final int DBG_SEARCH      = 0x00000010; // File/directory search
    public static final int DBG_INFO        = 0x00000020; // Information requests
    public static final int DBG_FILE        = 0x00000040; // File open/close/info
    public static final int DBG_FILEIO      = 0x00000080; // File read/write
    public static final int DBG_ERROR       = 0x00000100; // Errors
    public static final int DBG_PKTTYPE     = 0x00000200; // Received packet type
    public static final int DBG_TIMING      = 0x00000400; // Time packet processing
    public static final int DBG_DATAPORT    = 0x00000800; // Data port
    public static final int DBG_DIRECTORY   = 0x00001000; // Directory commands
    public static final int DBG_SSL         = 0x00002000; // Secure sessions

    // Enabled features
    public static final boolean FeatureUTF8 = true;
    public static final boolean FeatureMFMT = true;
    public static final boolean FeatureSIZE = true;
    public static final boolean FeatureMLST = true;
    public static final boolean FeatureAUTH = true;

    // Root directory and FTP directory seperator
    private static final String ROOT_DIRECTORY      = "/";
    private static final String FTP_SEPERATOR       = "/";
    private static final char FTP_SEPERATOR_CHAR    = '/';

    // Share relative path directory seperator
    private static final String DIR_SEPERATOR       = "\\";
    private static final char DIR_SEPERATOR_CHAR    = '\\';

    // File transfer buffer size
    private static final int DEFAULT_BUFFERSIZE = 64000;

    // Carriage return/line feed combination required for response messages
    protected final static String CRLF = "\r\n";

    // LIST command options
    protected final static String LIST_OPTION_PREFIX    = "-";
    protected final static char LIST_OPTION_HIDDEN      = 'a';

    // Machine listing fact ids
    protected static final int MLST_SIZE        = 0x0001;
    protected static final int MLST_MODIFY      = 0x0002;
    protected static final int MLST_CREATE      = 0x0004;
    protected static final int MLST_TYPE        = 0x0008;
    protected static final int MLST_UNIQUE      = 0x0010;
    protected static final int MLST_PERM        = 0x0020;
    protected static final int MLST_MEDIATYPE   = 0x0040;

    // Default fact list to use for machine listing commands
    protected static final int MLST_DEFAULT = MLST_SIZE + MLST_MODIFY + MLST_CREATE + MLST_TYPE + MLST_UNIQUE + MLST_PERM
            + MLST_MEDIATYPE;

    // Machine listing fact names
    protected static final String _factNames[] = {"size", "modify", "create", "type", "unique", "perm", "media-type"};

    // MLSD buffer size to allocate
    protected static final int MLSD_BUFFER_SIZE = 4096;

    // Modify date/time minimum date/time argument length
    protected static final int MDTM_DATETIME_MINLEN = 14; // YYYYMMDDHHMMSS

    // Network address types, for EPRT and EPSV commands
    protected static final int TypeIPv4 = 1;
    protected static final int TypeIPv6 = 2;

    // Valid protection levels for PROT command
    protected static final String ProtLevels = "CSEP";
    protected static final String ProtLevelClear = "C";

    // Maximum size to extend the command buffer to
    protected static final int DefCommandBufSize = 1024;
    protected static final int MaxCommandBufSize = 0xFFFF;    // 64K

    // Session socket
    private Socket m_sock;

    // Input/output streams to remote client
    private InputStream m_in;
    private byte[] m_inbuf;

    private OutputStreamWriter m_out;

    // List of pending FTP commands
    private List<FTPRequest> m_ftpCmdList;

    // Data connection
    private FTPDataSession m_dataSess;

    // Current working directory details
    //
    // First level is the share name then a path relative to the share root
    private FTPPath m_cwd;

    // Binary mode flag
    private boolean m_binary = false;

    // Restart position for binary file transfer
    private long m_restartPos = 0;

    // Flag to indicate if UTF-8 paths are enabled
    private boolean m_utf8Paths = true;
    private UTF8Normalizer m_normalizer;

    // Machine listing fact list
    private int m_mlstFacts = MLST_DEFAULT;

    // Rename from path details
    private FTPPath m_renameFrom;

    // Filtered list of shared filesystems available to this session
    private SharedDeviceList m_shares;

    // List of shared device connections used by this session
    private TreeConnectionHash m_connections;

    // SSL/TLS testing
    private SSLContext m_sslContext;
    private SSLEngine m_sslEngine;

    private ByteBuffer m_sslIn;
    private ByteBuffer m_sslOut;

    // Protected buffer size and protection level
    private int m_pbSize = -1;
    private String m_protLevel;

    /**
     * Class constructor
     *
     * @param sock Socket
     * @param srv  FTPServer
     */
    public FTPSrvSession(Socket sock, FTPServer srv) {
        super(-1, srv, "FTP", null);

        // Save the local socket
        m_sock = sock;

        // Set the socket linger options, so the socket closes immediately when closed
        try {
            m_sock.setSoLinger(false, 0);
        }
        catch (SocketException ex) {
        }

        // Indicate that the user is not logged in
        setLoggedOn(false);

        // Allocate the FTP path
        m_cwd = new FTPPath();

        // Allocate the tree connection cache
        m_connections = new TreeConnectionHash();

        // Allocate the command list
        m_ftpCmdList = new ArrayList<FTPRequest>();

        // Get the UTF-8 string normalizer, if available
        m_normalizer = srv.getUTF8Normalizer();
    }

    /**
     * Close the FTP session, and associated data socket if active
     */
    public final void closeSession() {

        // Call the base class
        super.closeSession();

        // Close the data connection, if active
        if (m_dataSess != null) {
            getFTPServer().releaseDataSession(m_dataSess);
            m_dataSess = null;
        }

        // Check if there is an active transaction
        if (hasTransaction()) {

            // DEBUG
            if (Debug.EnableError)
                debugPrintln("** Active transaction after packet processing, cleaning up **");

            // Close the active transaction
            endTransaction();
        }

        // Close the socket first, if the client is still connected this should allow the
        // input/output streams to be closed
        if (m_sock != null) {
            try {
                m_sock.close();
            }
            catch (Exception ex) {
            }
            m_sock = null;
        }

        // Close the input/output streams
        if (m_in != null) {
            try {
                m_in.close();
            }
            catch (Exception ex) {
            }
            m_in = null;
        }

        if (m_out != null) {
            try {
                m_out.close();
            }
            catch (Exception ex) {
            }
            m_out = null;
        }

        // Remove session from server session list
        getFTPServer().removeSession(this);

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_STATE))
            debugPrintln("Session closed, " + getSessionId());
    }

    /**
     * Return the current working directory
     *
     * @return String
     */
    public final String getCurrentWorkingDirectory() {
        return m_cwd.getFTPPath();
    }

    /**
     * Return the server that this session is associated with.
     *
     * @return FTPServer
     */
    public final FTPServer getFTPServer() {
        return (FTPServer) getServer();
    }

    /**
     * Return the client network address
     *
     * @return InetAddress
     */
    public final InetAddress getRemoteAddress() {
        return m_sock.getInetAddress();
    }

    /**
     * Check if there is a current working directory
     *
     * @return boolean
     */
    public final boolean hasCurrentWorkingDirectory() {
        return m_cwd != null ? true : false;
    }

    /**
     * Check if UTF-8 filenames are enabled
     *
     * @return boolean
     */
    public final boolean isUTF8Enabled() {
        return (m_utf8Paths == true && m_normalizer != null);
    }

    /**
     * Set the default path for the session
     *
     * @param rootPath FTPPath
     */
    public final void setRootPath(FTPPath rootPath) {

        // Initialize the current working directory using the root path
        m_cwd = new FTPPath(rootPath);
        m_cwd.setSharedDevice(getShareList(), this);
    }

    /**
     * Get the path details for the current request
     *
     * @param req      FTPRequest
     * @param filePath boolean
     * @return FTPPath
     */
    protected final FTPPath generatePathForRequest(FTPRequest req, boolean filePath) {
        return generatePathForRequest(req, filePath, true);
    }

    /**
     * Get the path details for the current request
     *
     * @param req         FTPRequest
     * @param filePath    boolean
     * @param checkExists boolean
     * @return FTPPath
     */
    protected final FTPPath generatePathForRequest(FTPRequest req, boolean filePath, boolean checkExists) {

        // Convert the path from UTF-8, if enabled
        String path = req.getArgument();

        // Convert the path to an FTP format path
        path = convertToFTPSeperators(path);

        // Check if the path is the root directory and there is a default root path configured
        FTPPath ftpPath = null;

        if (path.compareTo(ROOT_DIRECTORY) == 0) {

            // Check if the FTP server has a default root directory configured
            FTPServer ftpSrv = (FTPServer) getServer();
            if (ftpSrv.hasRootPath())
                ftpPath = ftpSrv.getRootPath();
            else {
                try {
                    ftpPath = new FTPPath("/");
                }
                catch (Exception ex) {
                }
                return ftpPath;
            }
        }

        // Check if the path is relative
        else if (FTPPath.isRelativePath(path) == false) {

            // Create a new path for the directory
            try {
                ftpPath = new FTPPath(path);
            }
            catch (InvalidPathException ex) {
                return null;
            }

            // Find the associated shared device
            if (ftpPath.setSharedDevice(getShareList(), this) == false)
                return null;
        } else {

            // Check for the special '.' directory, just return the current working directory
            if (path.equals(".") || path.length() == 0)
                return m_cwd;

            // Check for the special '..' directory, if already at the root directory return an
            // error
            if (path.equals("..")) {

                // Check if we are already at the root path
                if (m_cwd.isRootPath() == false) {

                    // Remove the last directory from the path
                    m_cwd.removeDirectory();
                    m_cwd.setSharedDevice(getShareList(), this);

                    // Return the new path
                    return m_cwd;
                } else
                    return null;
            }

            // Create a copy of the current working directory and append the new file/directory name
            ftpPath = new FTPPath(m_cwd);

            // Check if the root directory/share has been set
            if (ftpPath.isRootPath()) {

                // Path specifies the share name and possibly a subdirectory
                try {
                    ftpPath.setFTPPath(FTP_SEPERATOR + path);
                }
                catch (InvalidPathException ex) {
                    return null;
                }
            } else {
                if (filePath)
                    ftpPath.addFile(path);
                else
                    ftpPath.addDirectory(path);
            }

            // Find the associated shared device, if not already set
            if (ftpPath.hasSharedDevice() == false && ftpPath.setSharedDevice(getShareList(), this) == false)
                return null;
        }

        // Check if the generated path exists, if the share path is not an empty string or the root folder ('\')
        if (checkExists && ftpPath.hasSharePath() && ftpPath.getSharePath().length() > 1) {

            // Check if the new path exists and is a directory
            DiskInterface disk = null;
            TreeConnection tree = null;

            try {

                // Create a temporary tree connection
                tree = getTreeConnection(ftpPath.getSharedDevice());

                // Access the virtual filesystem driver
                disk = (DiskInterface) ftpPath.getSharedDevice().getInterface();

                // Check if the path exists
                FileStatus sts = disk.fileExists(this, tree, ftpPath.getSharePath());

                if (sts == FileStatus.NotExist) {

                    // Get the path string, check if there is a leading seperator
                    String pathStr = req.getArgument();
                    if (pathStr.startsWith(FTP_SEPERATOR) == false)
                        pathStr = FTP_SEPERATOR + pathStr;

                    // Create the root path
                    ftpPath = new FTPPath(pathStr);

                    // Find the associated shared device
                    if (ftpPath.setSharedDevice(getShareList(), this) == false)
                        ftpPath = null;

                    // Check the path again
                    if (disk.fileExists(this, tree, ftpPath.getSharePath()) == FileStatus.NotExist)
                        ftpPath = null;
                } else if ((sts == FileStatus.FileExists && filePath == false)
                        || (sts == FileStatus.DirectoryExists && filePath == true)) {

                    // Path exists but is the wrong type (directory or file)
                    ftpPath = null;
                }
            }
            catch (Exception ex) {
                ftpPath = null;
            }
        }

        // Return the new path
        return ftpPath;
    }

    /**
     * Convert a path string from share path seperators to FTP path seperators
     *
     * @param path String
     * @return String
     */
    protected final String convertToFTPSeperators(String path) {

        // Check if the path is valid
        if (path == null || path.indexOf(DIR_SEPERATOR) == -1)
            return path;

        // Replace the path seperators
        return path.replace(DIR_SEPERATOR_CHAR, FTP_SEPERATOR_CHAR);
    }

    /**
     * Find the required disk shared device
     *
     * @param name String
     * @return DiskSharedDevice
     */
    protected final DiskSharedDevice findShare(String name) {

        // Check if the name is valid
        if (name == null)
            return null;

        // Find the required disk share
        SharedDevice shr = getFTPServer().getShareList().findShare(m_cwd.getShareName(), ShareType.DISK, true);

        if (shr != null && shr instanceof DiskSharedDevice)
            return (DiskSharedDevice) shr;

        // Disk share not found
        return null;
    }

    /**
     * Set the binary mode flag
     *
     * @param bin boolean
     */
    protected final void setBinary(boolean bin) {
        m_binary = bin;
    }

    /**
     * Send an FTP command response
     *
     * @param stsCode int
     * @param msg     String
     * @exception IOException Socket error
     */
    public final void sendFTPResponse(int stsCode, String msg)
            throws IOException {

        // Build the output record
        StringBuffer outbuf = new StringBuffer(10 + (msg != null ? msg.length() : 0));
        outbuf.append(stsCode);
        outbuf.append(" ");

        if (msg != null)
            outbuf.append(msg);

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_TXDATA))
            debugPrintln("Tx msg=" + outbuf.toString());

        if (Debug.EnableError && hasDebug(DBG_ERROR) && stsCode >= 500)
            debugPrintln("Error status=" + stsCode + ", msg=" + msg);

        // Add the CR/LF
        outbuf.append(CRLF);

        // Output the FTP response
        if (m_out != null) {

            // Check if the response should be encrypted
            if (m_sslEngine != null) {

                // Encrypt the response
                sendEncryptedFTPResponse(outbuf.toString());
            } else {

                // Plaintext connection
                m_out.write(outbuf.toString());
                m_out.flush();
            }
        }
    }

    /**
     * Send an unencrypted FTP command response
     *
     * @param stsCode int
     * @param msg     String
     * @exception IOException Socket error
     */
    public final void sendUnencryptedFTPResponse(int stsCode, String msg)
            throws IOException {

        // Build the output record
        StringBuffer outbuf = new StringBuffer(10 + (msg != null ? msg.length() : 0));
        outbuf.append(stsCode);
        outbuf.append(" ");

        if (msg != null)
            outbuf.append(msg);

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_TXDATA))
            debugPrintln("Tx msg=" + outbuf.toString());

        if (Debug.EnableError && hasDebug(DBG_ERROR) && stsCode >= 500)
            debugPrintln("Error status=" + stsCode + ", msg=" + msg);

        // Add the CR/LF
        outbuf.append(CRLF);

        // Output the FTP response
        if (m_out != null) {
            m_out.write(outbuf.toString());
            m_out.flush();
        }
    }

    /**
     * Send an FTP command response
     *
     * @param msg StringBuffer
     * @exception IOException Socket error
     */
    public final void sendFTPResponse(StringBuffer msg)
            throws IOException {

        sendFTPResponse(msg.toString());
    }

    /**
     * Send an FTP command response
     *
     * @param msg String
     * @exception IOException Socket error
     */
    public final void sendFTPResponse(String msg)
            throws IOException {

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_TXDATA))
            debugPrintln("Tx msg=" + msg);

        // Output the FTP response
        if (m_out != null) {

            // Check if the response should be encrypted
            if (m_sslEngine != null) {

                // Encrypt the response
                StringBuilder str = new StringBuilder(msg.length() + CRLF.length());
                str.append(msg);
                str.append(CRLF);

                sendEncryptedFTPResponse(str.toString());
            } else {

                // Plaintext connection
                m_out.write(msg);
                m_out.write(CRLF);
                m_out.flush();
            }
        }
    }

    /**
     * Send an encrypted FTP response
     *
     * @param msg String
     * @exception IOException Socket error
     */
    protected final void sendEncryptedFTPResponse(String msg)
            throws IOException {

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_TXDATA))
            debugPrintln("Tx msg=" + msg);

        // Output the FTP response
        if (m_out != null) {

            // Check if the response should be encrypted
            if (m_sslEngine != null) {

                // Encrypt the response
                byte[] respByts = msg.getBytes();
                ByteBuffer inByts = ByteBuffer.wrap(respByts, 0, respByts.length);

                m_sslOut.position(0);
                m_sslOut.limit(m_sslOut.capacity());

                // Decrypt the received data
                SSLEngineResult sslRes = m_sslEngine.wrap(inByts, m_sslOut);

                if (m_sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {

                    Runnable task;
                    while ((task = m_sslEngine.getDelegatedTask()) != null) {
                        task.run();
                    }
                }

                // Output the encrypted response
                m_sslOut.flip();
                m_sock.getOutputStream().write(m_sslOut.array(), 0, m_sslOut.remaining());
                m_sock.getOutputStream().flush();
            }
        }
    }

    /**
     * Process a user command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procUser(FTPRequest req)
            throws IOException {

        // Clear the current client information
        setClientInformation(null);
        setLoggedOn(false);

        // Check if a user name has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error in parameters or arguments");
            return;
        }

        // Check if secure session logons are required
        if (getFTPServer().getFTPConfiguration().isFTPSEnabled() && getFTPServer().getFTPConfiguration().requireSecureSession() == true && isSecureSession() == false) {
            sendFTPResponse(530, "Only secure logons are allowed, use FTPS");
            return;
        }

        // Check for an anonymous login
        if (getFTPServer().allowAnonymous() == true && req.getArgument().equalsIgnoreCase(getFTPServer().getAnonymousAccount())) {

            // Anonymous login, create guest client information
            ClientInfo cinfo = ClientInfo.createInfo(getFTPServer().getAnonymousAccount(), null);
            cinfo.setGuest(true);
            setClientInformation(cinfo);

            // Return the anonymous login response
            sendFTPResponse(331, "Guest login ok, send your complete e-mail address as password");
            return;
        }

        // Create client information for the user
        setClientInformation(ClientInfo.createInfo(req.getArgument(), null));

        // Valid user, wait for the password
        sendFTPResponse(331, "User name okay, need password for " + req.getArgument());
    }

    /**
     * Process a password command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procPassword(FTPRequest req)
            throws IOException {

        // Check if the client information has been set, this indicates a user command has been
        // received
        if (hasClientInformation() == false) {
            sendFTPResponse(500, "Syntax error, command " + req.isCommand().name() + " unrecognized");
            return;
        }

        // Check for an anonymous login, accept any password string
        if (getClientInformation().isGuest()) {

            // Save the anonymous login password string
            getClientInformation().setPassword(req.getArgument());

            // Accept the login
            setLoggedOn(true);
            sendFTPResponse(230, "User logged in, proceed");

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_STATE))
                debugPrintln("Anonymous login, info=" + req.getArgument());
        }

        // Validate the user
        else {

            // Get the client information and store the received plain text password
            getClientInformation().setPassword(req.getArgument());

            // Authenticate the user
            FTPAuthenticator auth = getFTPServer().getFTPConfiguration().getFTPAuthenticator();

            if (auth.authenticateUser(getClientInformation(), this) == true) {

                // User successfully logged on
                sendFTPResponse(230, "User logged in, proceed");
                setLoggedOn(true);

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_STATE))
                    debugPrintln("User " + getClientInformation().getUserName() + ", logon successful");
            } else {

                // Return an access denied error
                sendFTPResponse(530, "Access denied");

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_STATE))
                    debugPrintln("User " + getClientInformation().getUserName() + ", logon failed");
            }
        }

        // If the user has successfully logged on to the FTP server then inform listeners
        if (isLoggedOn())
            getFTPServer().sessionLoggedOn(this);
    }

    /**
     * Process a port command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procPort(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if the parameter has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Required argument missing");
            return;
        }

        // Parse the address/port string into a IP address and port
        StringTokenizer token = new StringTokenizer(req.getArgument(), ",");
        if (token.countTokens() != 6) {
            sendFTPResponse(501, "Invalid argument");
            return;
        }

        // Parse the client address
        String addrStr = token.nextToken() + "." + token.nextToken() + "." + token.nextToken() + "." + token.nextToken();
        InetAddress addr = null;

        try {
            addr = InetAddress.getByName(addrStr);
        }
        catch (UnknownHostException ex) {
            sendFTPResponse(501, "Invalid argument (address)");
            return;
        }

        // Parse the client port
        int port = -1;

        try {
            port = Integer.parseInt(token.nextToken()) * 256;
            port += Integer.parseInt(token.nextToken());
        }
        catch (NumberFormatException ex) {
            sendFTPResponse(501, "Invalid argument (port)");
            return;
        }

        // Check if there is an existing data session
        if (m_dataSess != null) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_DATAPORT))
                debugPrintln("Releasing existing data session, sess=" + m_dataSess);

            // Release the current data session
            getFTPServer().releaseDataSession(m_dataSess);
            m_dataSess = null;
        }

        // Create an active data session, the actual socket connection will be made later
        m_dataSess = getFTPServer().allocateDataSession(this, addr, port);

        // Return a success response to the client
        sendFTPResponse(200, "Port OK");

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_DATAPORT))
            debugPrintln("Port open addr=" + addr + ", port=" + port);
    }

    /**
     * Process a passive command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procPassive(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if there is an existing data session
        if (m_dataSess != null) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_DATAPORT))
                debugPrintln("Releasing existing data session, sess=" + m_dataSess);

            // Release the current data session
            getFTPServer().releaseDataSession(m_dataSess);
            m_dataSess = null;
        }

        // Create a passive data session
        try {
            m_dataSess = getFTPServer().allocatePassiveDataSession(this, m_sock.getLocalAddress());
        }
        catch (IOException ex) {
            m_dataSess = null;
        }

        // Check if the data session is valid
        if (m_dataSess == null) {
            sendFTPResponse(550, "Requested action not taken");
            return;
        }

        // Get the passive connection address/port and return to the client
        int pasvPort = m_dataSess.getPassivePort();

        StringBuffer msg = new StringBuffer();

        msg.append("227 Entering Passive Mode (");
        msg.append(getLocalFTPAddressString());
        msg.append(",");
        msg.append(pasvPort >> 8);
        msg.append(",");
        msg.append(pasvPort & 0xFF);
        msg.append(")");

        sendFTPResponse(msg);

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_DATAPORT))
            debugPrintln("Passive open addr=" + m_sock.getLocalAddress() + ", port=" + pasvPort);
    }

    /**
     * Process a print working directory command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procPrintWorkDir(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Return the current working directory virtual path
        sendFTPResponse(257, "\"" + m_cwd.getFTPPath() + "\"");

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_DIRECTORY))
            debugPrintln("Pwd ftp=" + m_cwd.getFTPPath() + ", share=" + m_cwd.getShareName() + ", path=" + m_cwd.getSharePath());
    }

    /**
     * Process a change working directory command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procChangeWorkDir(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if the request has a valid argument
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Path not specified");
            return;
        }

        // Create the new working directory path
        FTPPath newPath = generatePathForRequest(req, false, true);
        if (newPath == null) {
            sendFTPResponse(550, "Invalid path " + req.getArgument());
            return;
        }

        // Set the new current working directory
        m_cwd = newPath;

        // Return a success status
        sendFTPResponse(250, "Requested file action OK");

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_DIRECTORY))
            debugPrintln("Cwd ftp=" + m_cwd.getFTPPath() + ", share=" + m_cwd.getShareName() + ", path=" + m_cwd.getSharePath());
    }

    /**
     * Process a change directory up command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procCdup(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if there is a current working directory path
        if (m_cwd.isRootPath()) {

            // Already at the root directory, return an error status
            sendFTPResponse(550, "Already at root directory");
            return;
        } else {

            // Remove the last directory from the path
            m_cwd.removeDirectory();
            if (m_cwd.isRootPath() == false && m_cwd.getSharedDevice() == null)
                m_cwd.setSharedDevice(getShareList(), this);
        }

        // Return a success status
        sendFTPResponse(250, "Requested file action OK");

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_DIRECTORY))
            debugPrintln("Cdup ftp=" + m_cwd.getFTPPath() + ", share=" + m_cwd.getShareName() + ", path=" + m_cwd.getSharePath());
    }

    /**
     * Process a long directory listing command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procList(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if the client has requested hidden files, via the '-a' option
        boolean hidden = false;

        if (req.hasArgument() && req.getArgument().startsWith(LIST_OPTION_PREFIX)) {

            // We only support the hidden files option
            String arg = req.getArgument();
            if (arg.indexOf(LIST_OPTION_HIDDEN) != -1) {

                // Indicate that we want hidden files in the listing
                hidden = true;
            }

            // Remove the option from the command argument, and update the
            // request
            int pos = arg.indexOf(" ");
            if (pos > 0)
                arg = arg.substring(pos + 1);
            else
                arg = null;

            req.updateArgument(arg);
        }

        // Create the path for the file listing
        FTPPath ftpPath = m_cwd;
        if (req.hasArgument())
            ftpPath = generatePathForRequest(req, true, false);

        if (ftpPath == null) {
            sendFTPResponse(500, "Invalid path");
            return;
        }

        // Check if the session has the required access
        if (ftpPath.isRootPath() == false) {

            // Check if the session has access to the filesystem
            TreeConnection tree = getTreeConnection(ftpPath.getSharedDevice());
            if (tree == null || tree.hasReadAccess() == false) {

                // Session does not have access to the filesystem
                sendFTPResponse(550, "Access denied");
                return;
            }
        }

        try {
            if (ftpPath.hasSharePath() && WildCard.containsWildcards(ftpPath.getSharePath()) == false) {
                // Create a temporary tree connection
                TreeConnection tree = getTreeConnection(ftpPath.getSharedDevice());

                // Access the virtual filesystem driver
                DiskInterface disk = (DiskInterface) ftpPath.getSharedDevice().getInterface();

                FileStatus sts = disk.fileExists(this, tree, ftpPath.getSharePath());

                if (sts == FileStatus.NotExist) {
                    sendFTPResponse(500, "Invalid path");
                    return;
                }

                if (sts == FileStatus.DirectoryExists) {
                    ftpPath.setDirectory(true);
                }

            }
        }
        catch (Exception ex) {
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error reading file list, " + ex.toString());
            }

            // Always need logging - this is unexpected
            debugPrintln(ex);

            // Failed to send file listing
            sendFTPResponse(451, "Error reading file list");
        }

        // Send the intermediate response
        sendFTPResponse(150, "File status okay, about to open data connection");

        // Check if there is an active data session
        if (m_dataSess == null) {
            sendFTPResponse(425, "Can't open data connection");
            return;
        }

        // Get the data connection socket
        Socket dataSock = null;

        try {
            dataSock = m_dataSess.getSocket();
        }
        catch (Exception ex) {
            debugPrintln(ex);
        }

        if (dataSock == null) {
            sendFTPResponse(426, "Connection closed; transfer aborted");
            return;
        }

        // Output the directory listing to the client
        Writer dataWrt = null;

        try {

            // Open an output stream to the client
            if (isUTF8Enabled())
                dataWrt = new OutputStreamWriter(dataSock.getOutputStream(), "UTF-8");
            else
                dataWrt = new OutputStreamWriter(dataSock.getOutputStream());

            // Check if a path has been specified to list
            List<FileInfo> files = null;

            if (req.hasArgument()) {
            }

            // Get a list of file information objects for the current directory
            files = listFilesForPath(ftpPath, false, hidden);

            // Output the file list to the client
            if (files != null) {

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_SEARCH))
                    debugPrintln("List found " + files.size() + " files in " + ftpPath.getFTPPath());

                // Output the file information to the client
                StringBuffer str = new StringBuffer(256);

                for (int i = 0; i < files.size(); i++) {

                    // Get the current file information
                    FileInfo finfo = files.get(i);

                    // Build the output record
                    str.setLength(0);

                    str.append(finfo.isDirectory() ? "d" : "-");
                    str.append("rw-rw-rw-   1 user group ");
                    str.append(finfo.getSize());
                    str.append(" ");

                    FTPDate.packUnixDate(str, new Date(finfo.getModifyDateTime()));

                    str.append(" ");

                    str.append(finfo.getFileName());
                    str.append(CRLF);

                    // Output the file information record
                    dataWrt.write(str.toString());
                }
            }

            // End of file list transmission
            sendFTPResponse(226, "Closing data connection");
        }
        catch (Exception ex) {
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {

                debugPrintln(" Error reading file list, " + ex.toString());
            }
            debugPrintln(ex);


            // Failed to send file listing
            sendFTPResponse(451, "Error reading file list");
        }
        finally {

            // Close the data stream to the client
            if (dataWrt != null)
                dataWrt.close();

            // Close the data connection to the client
            if (m_dataSess != null) {
                getFTPServer().releaseDataSession(m_dataSess);
                m_dataSess = null;
            }
        }
    }

    /**
     * Process a short directory listing command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procNList(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Create the path for the file listing
        FTPPath ftpPath = m_cwd;
        if (req.hasArgument())
            ftpPath = generatePathForRequest(req, true);

        if (ftpPath == null) {
            sendFTPResponse(500, "Invalid path");
            return;
        }

        // Check if the session has the required access
        if (ftpPath.isRootPath() == false) {

            // Check if the session has access to the filesystem
            TreeConnection tree = getTreeConnection(ftpPath.getSharedDevice());
            if (tree == null || tree.hasReadAccess() == false) {

                // Session does not have access to the filesystem
                sendFTPResponse(550, "Access denied");
                return;
            }
        }

        // Send the intermediate response
        sendFTPResponse(150, "File status okay, about to open data connection");

        // Check if there is an active data session
        if (m_dataSess == null) {
            sendFTPResponse(425, "Can't open data connection");
            return;
        }

        // Get the data connection socket
        Socket dataSock = null;

        try {
            dataSock = m_dataSess.getSocket();
        }
        catch (Exception ex) {
            debugPrintln(ex);
        }

        if (dataSock == null) {
            sendFTPResponse(426, "Connection closed; transfer aborted");
            return;
        }

        // Output the directory listing to the client
        Writer dataWrt = null;

        try {

            // Open an output stream to the client
            if (isUTF8Enabled())
                dataWrt = new OutputStreamWriter(dataSock.getOutputStream(), "UTF-8");
            else
                dataWrt = new OutputStreamWriter(dataSock.getOutputStream());

            // Check if a path has been specified to list
            List<FileInfo> files = null;

            if (req.hasArgument()) {
            }

            // Get a list of file information objects for the current directory
            files = listFilesForPath(ftpPath, false, false);

            // Output the file list to the client
            if (files != null) {

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_SEARCH))
                    debugPrintln("List found " + files.size() + " files in " + ftpPath.getFTPPath());

                // Output the file information to the client
                for (int i = 0; i < files.size(); i++) {

                    // Get the current file information
                    FileInfo finfo = files.get(i);

                    // Output the file information record
                    dataWrt.write(finfo.getFileName());
                    dataWrt.write(CRLF);
                }
            }

            // End of file list transmission
            sendFTPResponse(226, "Closing data connection");
        }
        catch (Exception ex) {
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error reading file list, " + ex.toString());
            }
            debugPrintln(ex);

            // Failed to send file listing
            sendFTPResponse(451, "Error reading file list");
        }
        finally {

            // Close the data stream to the client
            if (dataWrt != null)
                dataWrt.close();

            // Close the data connection to the client
            if (m_dataSess != null) {
                getFTPServer().releaseDataSession(m_dataSess);
                m_dataSess = null;
            }
        }
    }

    /**
     * Process a system status command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procSystemStatus(FTPRequest req)
            throws IOException {

        // Return the system type
        sendFTPResponse(215, "UNIX Type: Java FTP Server");
    }

    /**
     * Process a server status command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procServerStatus(FTPRequest req)
            throws IOException {

        // Return server status information
        sendFTPResponse(211, "JFileSrv - Java FTP Server");
    }

    /**
     * Process a help command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procHelp(FTPRequest req)
            throws IOException {

        // Return help information
        sendFTPResponse(211, "HELP text");
    }

    /**
     * Process a no-op command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procNoop(FTPRequest req)
            throws IOException {

        // Return a response
        sendFTPResponse(200, "");
    }

    /**
     * Process an options request
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procOptions(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if the parameter has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Required argument missing");
            return;
        }

        // Parse the argument to get the sub-command and arguments
        StringTokenizer token = new StringTokenizer(req.getArgument(), " ");
        if (token.hasMoreTokens() == false) {
            sendFTPResponse(501, "Invalid argument");
            return;
        }

        // Get the sub-command
        String optsCmd = token.nextToken();

        // UTF8 enable/disable command
        if (FeatureUTF8 && optsCmd.equalsIgnoreCase("UTF8")) {

            // Get the next argument
            if (token.hasMoreTokens()) {
                String optsArg = token.nextToken();
                if (optsArg.equalsIgnoreCase("ON")) {

                    // Enable UTF-8 file names
                    m_utf8Paths = true;
                } else if (optsArg.equalsIgnoreCase("OFF")) {

                    // Disable UTF-8 file names
                    m_utf8Paths = false;
                } else {

                    // Invalid argument
                    sendFTPResponse(501, "OPTS UTF8 Invalid argument");
                    return;
                }

                // Report the new setting back to the client
                sendFTPResponse(200, "OPTS UTF8 " + (isUTF8Enabled() ? "ON" : "OFF"));

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_FILE))
                    debugPrintln("UTF8 options utf8=" + (isUTF8Enabled() ? "ON" : "OFF"));
            }
        }

        // MLST/MLSD fact list command
        else if (FeatureMLST && optsCmd.equalsIgnoreCase("MLST")) {

            // Check if the fact list argument is valid
            if (token.hasMoreTokens() == false) {

                // Invalid fact list argument
                sendFTPResponse(501, "OPTS MLST Invalid argument");
                return;
            }

            // Parse the supplied fact names
            int mlstFacts = 0;
            StringTokenizer factTokens = new StringTokenizer(token.nextToken(), ";");
            StringBuffer factStr = new StringBuffer();

            while (factTokens.hasMoreTokens()) {

                // Get the current fact name and validate
                String factName = factTokens.nextToken();
                int factIdx = -1;
                int idx = 0;

                while (idx < _factNames.length && factIdx == -1) {
                    if (_factNames[idx].equalsIgnoreCase(factName))
                        factIdx = idx;
                    else
                        idx++;
                }

                // Check if the fact name is valid, ignore invalid names
                if (factIdx != -1) {

                    // Add the fact name to the reply tring
                    factStr.append(_factNames[factIdx]);
                    factStr.append(";");

                    // Add the fact to the fact bit mask
                    mlstFacts += (1 << factIdx);
                }
            }

            // Check if any valid fact names were found
            if (mlstFacts == 0) {
                sendFTPResponse(501, "OPTS MLST Invalid Argument");
                return;
            }

            // Update the MLST enabled fact list for this session
            m_mlstFacts = mlstFacts;

            // Send the response
            sendFTPResponse(200, "MLST OPTS " + factStr.toString());

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_SEARCH))
                debugPrintln("MLst options facts=" + factStr.toString());
        } else {

            // Unknown options request or feature not enabled
            sendFTPResponse(501, "Invalid argument");
        }
    }

    /**
     * Process a quit command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procQuit(FTPRequest req)
            throws IOException {

        // Return a response
        sendFTPResponse(221, "Bye");

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_STATE))
            debugPrintln("Quit closing connection(s) to client");

        // Close the session(s) to the client
        closeSession();
    }

    /**
     * Process a type command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procType(FTPRequest req)
            throws IOException {

        // Check if an argument has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error, parameter required");
            return;
        }

        // Check if ASCII or binary mode is enabled
        String arg = req.getArgument().toUpperCase();
        if (arg.startsWith("A"))
            setBinary(false);
        else if (arg.startsWith("I") || arg.startsWith("L"))
            setBinary(true);
        else {

            // Invalid argument
            sendFTPResponse(501, "Syntax error, invalid parameter");
            return;
        }

        // Return a success status
        sendFTPResponse(200, "Command OK");

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_STATE))
            debugPrintln("Type arg=" + req.getArgument() + ", binary=" + m_binary);
    }

    /**
     * Process a restart command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procRestart(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if an argument has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error, parameter required");
            return;
        }

        // Validate the restart position
        try {
            m_restartPos = Integer.parseInt(req.getArgument());
        }
        catch (NumberFormatException ex) {
            sendFTPResponse(501, "Invalid restart position");
            return;
        }

        // Return a success status
        sendFTPResponse(350, "Restart OK");

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_FILEIO))
            debugPrintln("Restart pos=" + m_restartPos);
    }

    /**
     * Process a return file command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procReturnFile(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if an argument has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error, parameter required");
            return;
        }

        // Create the path for the file listing
        FTPPath ftpPath = generatePathForRequest(req, true);
        if (ftpPath == null) {
            sendFTPResponse(500, "Invalid path");
            return;
        }

        // Check if the path is the root directory
        if (ftpPath.isRootPath() || ftpPath.isRootSharePath()) {
            sendFTPResponse(550, "That is a directory");
            return;
        }

        // Send the intermediate response
        sendFTPResponse(150, "Connection accepted");

        // Check if there is an active data session
        if (m_dataSess == null) {
            sendFTPResponse(425, "Can't open data connection");
            return;
        }

        // Get the data connection socket
        Socket dataSock = null;

        try {
            dataSock = m_dataSess.getSocket();
        }
        catch (Exception ex) {
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error reading file list, " + ex.toString());
            }
            debugPrintln(ex);
        }

        if (dataSock == null) {
            sendFTPResponse(426, "Connection closed; transfer aborted");
            return;
        }

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_FILE))
            debugPrintln("Returning ftp=" + ftpPath.getFTPPath() + ", share=" + ftpPath.getShareName() + ", path="
                    + ftpPath.getSharePath());

        // Send the file to the client
        OutputStream os = null;
        DiskInterface disk = null;
        TreeConnection tree = null;
        NetworkFile netFile = null;

        try {

            // Open an output stream to the client
            os = dataSock.getOutputStream();

            // Create a temporary tree connection
            tree = getTreeConnection(ftpPath.getSharedDevice());

            // Check if the file exists and it is a file, if so then open the file
            disk = (DiskInterface) ftpPath.getSharedDevice().getInterface();

            // Create the file open parameters
            FileOpenParams params = new FileOpenParams(ftpPath.getSharePath(), FileAction.OpenIfExists, AccessMode.ReadOnly, 0, 0);

            // Check if the file exists and it is a file
            FileStatus sts = disk.fileExists(this, tree, ftpPath.getSharePath());

            if (sts == FileStatus.FileExists) {

                // Open the file
                netFile = disk.openFile(this, tree, params);
            }

            // Check if the file has been opened
            if (netFile == null) {
                sendFTPResponse(550, "File " + req.getArgument() + " not available");
                return;
            }

            // Allocate the buffer for the file data
            byte[] buf = new byte[DEFAULT_BUFFERSIZE];
            long filePos = m_restartPos;

            int len = -1;
            boolean abort = false;

            while (filePos < netFile.getFileSize() && abort == false) {

                // Read another block of data from the file
                len = disk.readFile(this, tree, netFile, buf, 0, buf.length, filePos);

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_FILEIO))
                    debugPrintln(" Write len=" + len + " bytes");

                // Write the current data block to the client, update the file position
                if (len > 0) {

                    // Write the data to the client
                    os.write(buf, 0, len);

                    // Update the file position
                    filePos += len;

                    // Check if the transfer has been aborted
                    abort = checkForAbort();
                }
            }

            // Close the output stream to the client
            os.close();
            os = null;

            // Indicate that the file has been transmitted, or the transfer was aborted
            if (abort == false)
                sendFTPResponse(226, "Closing data connection");
            else
                sendFTPResponse(426, "Transfer aborted by client");

            // Close the data session
            getFTPServer().releaseDataSession(m_dataSess);
            m_dataSess = null;

            // Close the network file
            disk.closeFile(this, tree, netFile);
            netFile = null;

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_FILEIO))
                debugPrintln(" Transfer complete, file closed");
        }
        catch (SocketException ex) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error during transfer, " + ex.toString());
                debugPrintln(ex);
            }

            // Close the data socket to the client
            if (m_dataSess != null) {
                getFTPServer().releaseDataSession(m_dataSess);
                m_dataSess = null;
            }

            // Indicate that there was an error during transmission of the file data
            sendFTPResponse(426, "Data connection closed by client");
        }
        catch (FileOfflineException ex) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_ERROR))
                debugPrintln(" Error during transfer, " + ex.toString());

            // Indicate that there was an error during transmission of the file data
            sendFTPResponse(450, "File data is currently offline");
        }
        catch (Exception ex) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_ERROR))
                debugPrintln(" Error during transfer, " + ex.toString());

            // Indicate that there was an error during transmission of the file data
            sendFTPResponse(426, "Error during transmission");
        }
        finally {

            // Close the network file
            if (netFile != null && disk != null && tree != null)
                disk.closeFile(this, tree, netFile);

            // Close the output stream to the client
            if (os != null)
                os.close();

            // Close the data connection to the client
            if (m_dataSess != null) {
                getFTPServer().releaseDataSession(m_dataSess);
                m_dataSess = null;
            }
        }
    }

    /**
     * Process a store file command
     *
     * @param req    FTPRequest
     * @param append boolean
     * @exception IOException Socket error
     */
    protected final void procStoreFile(FTPRequest req, boolean append)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if an argument has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error, parameter required");
            return;
        }

        // Create the path for the file listing
        FTPPath ftpPath = generatePathForRequest(req, true, false);
        if (ftpPath == null) {
            sendFTPResponse(500, "Invalid path");
            return;
        }

        // Send the file to the client
        InputStream is = null;
        DiskInterface disk = null;
        TreeConnection tree = null;
        NetworkFile netFile = null;
        FileStatus sts = FileStatus.NotExist;

        // Flag to indicate if the file should be deleted on close, used if there is an error during the upload
        // and the file did not exist before the upload
        boolean deleteOnClose = false;

        try {

            // Create a temporary tree connection
            tree = getTreeConnection(ftpPath.getSharedDevice());

            // Check if the session has the required access to the filesystem
            if (tree == null || tree.hasWriteAccess() == false) {

                // Session does not have write access to the filesystem
                sendFTPResponse(550, "Access denied");
                return;
            }

            // Check if the file exists
            disk = (DiskInterface) ftpPath.getSharedDevice().getInterface();
            sts = disk.fileExists(this, tree, ftpPath.getSharePath());

            if (sts == FileStatus.DirectoryExists) {

                // Return an error status
                sendFTPResponse(500, "Invalid path (existing directory)");
                return;
            }

            // Create the file open parameters
            int openAction = FileAction.CreateNotExist;
            if (sts == FileStatus.FileExists)
                openAction = append == false ? FileAction.TruncateExisting : FileAction.OpenIfExists;

            FileOpenParams params = new FileOpenParams(ftpPath.getSharePath(), openAction, AccessMode.ReadWrite, 0, 0);

            // Transaction begins in the innards of 'disk'
            try {
                // Are we opening an existing file or creating a new one?
                if (sts == FileStatus.FileExists) {
                    // Open and truncate the existing file
                    netFile = disk.openFile(this, tree, params);
                } else {
                    // Create a new file
                    netFile = disk.createFile(this, tree, params);
                }

                // Notify change listeners that a new file has been created
                DiskDeviceContext diskCtx = (DiskDeviceContext) tree.getContext();

                if (diskCtx.hasChangeHandler())
                    diskCtx.getChangeHandler().notifyFileChanged(NotifyAction.Added, ftpPath.getSharePath());

                // Send the intermediate response
                sendFTPResponse(150, "File status okay, about to open data connection");

                // Check if there is an active data session
                if (m_dataSess == null) {
                    sendFTPResponse(425, "Can't open data connection");
                    return;
                }

                // Get the data connection socket
                Socket dataSock = null;

                try {
                    dataSock = m_dataSess.getSocket();
                }
                catch (Exception ex) {
                }

                if (dataSock == null) {
                    sendFTPResponse(426, "Connection closed; transfer aborted");
                    return;
                }

                dataSock.setSoTimeout(getFTPServer().getFTPConfiguration().getFTPSrvSessionTimeout());

                // Open an input stream from the client
                is = dataSock.getInputStream();

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_FILE))
                    debugPrintln("Storing ftp=" + ftpPath.getFTPPath() + ", share=" + ftpPath.getShareName() + ", path="
                            + ftpPath.getSharePath() + (append ? " (Append)" : ""));

                // Allocate the buffer for the file data
                byte[] buf = new byte[DEFAULT_BUFFERSIZE];
                long filePos = 0;
                int len = is.read(buf, 0, buf.length);
                boolean abort = false;

                // If the data is to be appended then set the starting file position to the end of the
                // file
                if (append == true)
                    filePos = netFile.getFileSize();

                // Read/write loop
                while (len > 0 && abort == false) {

                    // DEBUG
                    if (Debug.EnableInfo && hasDebug(DBG_FILEIO))
                        debugPrintln(" Receive len=" + len + " bytes");

                    // Write the current data block to the file, update the file position
                    disk.writeFile(this, tree, netFile, buf, 0, len, filePos);
                    filePos += len;

                    // Read another block of data from the client
                    len = is.read(buf, 0, buf.length);

                    // Check if the file transfer has been aborted
                    abort = checkForAbort();
                }

                // Close the input stream from the client
                is.close();
                is = null;

                // Close the network file
                disk.closeFile(this, tree, netFile);
                netFile = null;

                // Indicate that the file has been received, or the transfer was aborted
                if (abort == false)
                    sendFTPResponse(226, "Closing data connection");
                else
                    sendFTPResponse(426, "Transfer aborted by client");

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_FILEIO))
                    debugPrintln(" Transfer complete, file closed");
            }
            finally {
                endTransaction();
            }
        }
        catch (SocketException ex) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_ERROR))
                debugPrintln(" Error during transfer, " + ex.toString());

            // Close the data socket to the client
            if (m_dataSess != null) {
                getFTPServer().releaseDataSession(m_dataSess);
                m_dataSess = null;
            }

            // Indicate that there was an error during transmission of the file data
            sendFTPResponse(426, "Data connection closed by client");
        }
        catch (DiskFullException ex) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_ERROR))
                debugPrintln(" Error during transfer, " + ex.toString());

            // Close the data socket to the client
            if (m_dataSess != null) {
                getFTPServer().releaseDataSession(m_dataSess);
                m_dataSess = null;
            }

            // If the file did not exist before the upload then mark it to delete on close
            if (sts != FileStatus.FileExists) {
                deleteOnClose = true;

                // DEBUG
                if (Debug.EnableDbg && hasDebug(DBG_ERROR))
                    debugPrintln(" Marking file for delete on close (quota exceeded)");
            }

            // Indicate that there was an error during writing of the file
            sendFTPResponse(451, "Disk full or Quota Exceeded");
        }
        catch (AccessDeniedException ex) {

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_ERROR))
                debugPrintln(" Error during transfer, " + ex.toString());

            // Close the data socket to the client
            if (m_dataSess != null) {
                getFTPServer().releaseDataSession(m_dataSess);
                m_dataSess = null;
            }

            // If the file did not exist before the upload then mark it to delete on close
            if (sts != FileStatus.FileExists) {
                deleteOnClose = true;

                // DEBUG
                if (Debug.EnableDbg && hasDebug(DBG_ERROR))
                    debugPrintln(" Marking file for delete on close (access denied)");
            }

            // Indicate that there was an error during writing of the file
            sendFTPResponse(451, "Access denied, file may be in use or locked by another user");
        }
        catch (SocketTimeoutException ex) {
            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error during transmission: session timeout.");
                debugPrintln(" Marking file for delete on close.");
            }
            deleteOnClose = true;

            // Indicate that there was an error during transmission of the file data
            sendFTPResponse(426, "Error during transmission: session timeout");
        }
        catch (Exception ex) {
            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error during transfer, " + ex.toString());
            }
            debugPrintln(ex);

            // Indicate that there was an error during transmission of the file data
            sendFTPResponse(426, "Error during transmission");
        }
        finally {

            // Check if the file should be marked for delete on close, only when an error occurs
            if (netFile != null && deleteOnClose == true)
                netFile.setDeleteOnClose(true);

            // Close the network file
            if (netFile != null && disk != null && tree != null)
                disk.closeFile(this, tree, netFile);

            // Close the input stream to the client
            if (is != null)
                is.close();

            // Close the data connection to the client
            if (m_dataSess != null) {
                getFTPServer().releaseDataSession(m_dataSess);
                m_dataSess = null;
            }
        }
    }

    /**
     * Process a delete file command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procDeleteFile(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if an argument has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error, parameter required");
            return;
        }

        // Create the path for the file
        FTPPath ftpPath = generatePathForRequest(req, true);
        if (ftpPath == null) {
            sendFTPResponse(550, "Invalid path specified");
            return;
        }

        // Delete the specified file
        DiskInterface disk = null;
        TreeConnection tree = null;

        try {

            // Create a temporary tree connection
            tree = getTreeConnection(ftpPath.getSharedDevice());

            // Check if the session has the required access to the filesystem
            if (tree == null || tree.hasWriteAccess() == false) {

                // Session does not have write access to the filesystem
                sendFTPResponse(550, "Access denied");
                return;
            }

            // Check if the file exists and it is a file
            disk = (DiskInterface) ftpPath.getSharedDevice().getInterface();
            FileStatus sts = disk.fileExists(this, tree, ftpPath.getSharePath());

            if (sts == FileStatus.FileExists) {

                // Delete the file
                disk.deleteFile(this, tree, ftpPath.getSharePath());

                // Check if there are any file/directory change notify requests active
                DiskDeviceContext diskCtx = (DiskDeviceContext) tree.getContext();
                if (diskCtx.hasChangeHandler())
                    diskCtx.getChangeHandler().notifyFileChanged(NotifyAction.Removed, ftpPath.getSharePath());

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_FILE))
                    debugPrintln("Deleted ftp=" + ftpPath.getFTPPath() + ", share=" + ftpPath.getShareName() + ", path="
                            + ftpPath.getSharePath());
            } else {

                // File does not exist or is a directory
                sendFTPResponse(550, "File " + req.getArgument()
                        + (sts == FileStatus.NotExist ? " not available" : " is a directory"));
                return;
            }
        }
        catch (Exception ex) {
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error deleting file, " + ex.toString());
            }
            debugPrintln(ex);
            sendFTPResponse(450, "File action not taken");
            return;
        }

        // Return a success status
        sendFTPResponse(250, "File " + req.getArgument() + " deleted");
    }

    /**
     * Process a rename from command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procRenameFrom(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if an argument has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error, parameter required");
            return;
        }

        // Clear the current rename from path details, if any
        m_renameFrom = null;

        // Create the path for the file/directory
        FTPPath ftpPath = generatePathForRequest(req, false, false);
        if (ftpPath == null) {
            sendFTPResponse(550, "Invalid path specified");
            return;
        }

        // Check that the file exists, and it is a file
        DiskInterface disk = null;
        TreeConnection tree = null;

        try {

            // Create a temporary tree connection
            tree = getTreeConnection(ftpPath.getSharedDevice());

            // Check if the session has the required access to the filesystem
            if (tree == null || tree.hasWriteAccess() == false) {

                // Session does not have write access to the filesystem
                sendFTPResponse(550, "Access denied");
                return;
            }

            // Check if the file exists and it is a file
            disk = (DiskInterface) ftpPath.getSharedDevice().getInterface();
            FileStatus sts = disk.fileExists(this, tree, ftpPath.getSharePath());

            if (sts != FileStatus.NotExist) {

                // Save the rename from file details, rename to command should follow
                m_renameFrom = ftpPath;

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_FILE))
                    debugPrintln("RenameFrom ftp=" + ftpPath.getFTPPath() + ", share=" + ftpPath.getShareName() + ", path="
                            + ftpPath.getSharePath());
            } else {

                // File/directory does not exist
                sendFTPResponse(550, "File " + req.getArgument()
                        + (sts == FileStatus.NotExist ? " not available" : " is a directory"));
                return;
            }
        }
        catch (Exception ex) {
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error renaming file from, " + ex.toString());
            }
            debugPrintln(ex);
            sendFTPResponse(450, "File action not taken");
            return;
        }

        // Return a success status
        sendFTPResponse(350, "File " + req.getArgument() + " OK");
    }

    /**
     * Process a rename to command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procRenameTo(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if an argument has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error, parameter required");
            return;
        }

        // Check if the rename from has already been set
        if (m_renameFrom == null) {
            sendFTPResponse(550, "Rename from not set");
            return;
        }

        // Create the path for the new file name
        FTPPath ftpPath = generatePathForRequest(req, true, false);
        if (ftpPath == null) {
            sendFTPResponse(550, "Invalid path specified");
            return;
        }

        // Check that the rename is on the same share
        if (m_renameFrom.getShareName().compareTo(ftpPath.getShareName()) != 0) {
            sendFTPResponse(550, "Cannot rename across shares");
            return;
        }

        // Rename the file
        DiskInterface disk = null;
        TreeConnection tree = null;

        try {

            // Create a temporary tree connection
            tree = getTreeConnection(ftpPath.getSharedDevice());

            // Check if the session has the required access to the filesystem
            if (tree == null || tree.hasWriteAccess() == false) {

                // Session does not have write access to the filesystem
                sendFTPResponse(550, "Access denied");
                return;
            }

            // Check if the file exists and it is a file
            disk = (DiskInterface) ftpPath.getSharedDevice().getInterface();
            FileStatus sts = disk.fileExists(this, tree, ftpPath.getSharePath());

            if ((sts == FileStatus.NotExist) ||

                    // Special condition where we are changing case of file name but the search above is case insensitive
                    ((sts == FileStatus.FileExists) & m_renameFrom.getSharePath().equalsIgnoreCase(ftpPath.getSharePath()))
                    ) {

                // Rename the file/directory
                disk.renameFile(this, tree, m_renameFrom.getSharePath(), ftpPath.getSharePath());

                // Check if there are any file/directory change notify requests active
                DiskDeviceContext diskCtx = (DiskDeviceContext) tree.getContext();
                if (diskCtx.hasChangeHandler())
                    diskCtx.getChangeHandler().notifyRename(m_renameFrom.getSharePath(), ftpPath.getSharePath());

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_FILE))
                    debugPrintln("RenameTo ftp=" + ftpPath.getFTPPath() + ", share=" + ftpPath.getShareName() + ", path="
                            + ftpPath.getSharePath());
            } else {

                // Destination file already exists or is a directory
                sendFTPResponse(550, "File " + req.getArgument()
                        + (sts == FileStatus.FileExists ? " already exists" : " is a directory"));
                return;
            }
        }
        catch (Exception ex) {
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error rename to, " + ex.toString());
            }
            debugPrintln(ex);
            sendFTPResponse(450, "File action not taken");
            return;
        }
        finally {

            // Clear the rename details
            m_renameFrom = null;
        }

        // Return a success status
        sendFTPResponse(250, "File renamed OK");
    }

    /**
     * Process a create directory command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procCreateDirectory(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if an argument has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error, parameter required");
            return;
        }

        // Check if the new directory contains multiple directories
        FTPPath ftpPath = generatePathForRequest(req, false, false);
        if (ftpPath == null) {
            sendFTPResponse(550, "Invalid path " + req.getArgument());
            return;
        }

        // Create the new directory
        DiskInterface disk = null;
        TreeConnection tree = null;

        try {

            // Create a temporary tree connection
            tree = getTreeConnection(ftpPath.getSharedDevice());

            // Check if the session has the required access to the filesystem
            if (tree == null || tree.hasWriteAccess() == false) {

                // Session does not have write access to the filesystem
                sendFTPResponse(550, "Access denied");
                return;
            }

            // Check if the directory exists
            disk = (DiskInterface) ftpPath.getSharedDevice().getInterface();
            FileStatus sts = disk.fileExists(this, tree, ftpPath.getSharePath());

            if (sts == FileStatus.NotExist) {

                // Create the new directory
                FileOpenParams params = new FileOpenParams(ftpPath.getSharePath(), FileAction.CreateNotExist,
                        AccessMode.ReadWrite, FileAttribute.NTDirectory, 0);

                disk.createDirectory(this, tree, params);

                // Notify change listeners that a new directory has been created
                DiskDeviceContext diskCtx = (DiskDeviceContext) tree.getContext();

                if (diskCtx.hasChangeHandler())
                    diskCtx.getChangeHandler().notifyFileChanged(NotifyAction.Added, ftpPath.getSharePath());

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_DIRECTORY))
                    debugPrintln("CreateDir ftp=" + ftpPath.getFTPPath() + ", share=" + ftpPath.getShareName() + ", path="
                            + ftpPath.getSharePath());
            } else {

                // File/directory already exists with that name, return an error
                sendFTPResponse(450, sts == FileStatus.FileExists ? "File exists with that name" : "Directory already exists");
                return;
            }
        }
        catch (Exception ex) {
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error creating directory, " + ex.toString());
            }
            debugPrintln(ex);
            sendFTPResponse(450, "Failed to create directory");
            return;
        }

        // Return the FTP path to the client
        sendFTPResponse(257, ftpPath.getFTPPath());
    }

    /**
     * Process a delete directory command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procRemoveDirectory(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if an argument has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error, parameter required");
            return;
        }

        // Check if the directory path contains multiple directories
        FTPPath ftpPath = generatePathForRequest(req, false);
        if (ftpPath == null) {
            sendFTPResponse(550, "Invalid path " + req.getArgument());
            return;
        }

        // Check if the path is the root directory, cannot delete directories from the root
        // directory as it maps to the list of available disk shares.
        if (ftpPath.isRootPath() || ftpPath.isRootSharePath()) {
            sendFTPResponse(550, "Access denied, cannot delete directory in root");
            return;
        }

        // Delete the directory
        DiskInterface disk = null;
        TreeConnection tree = null;

        try {

            // Create a temporary tree connection
            tree = getTreeConnection(ftpPath.getSharedDevice());

            // Check if the session has the required access to the filesystem
            if (tree == null || tree.hasWriteAccess() == false) {

                // Session does not have write access to the filesystem
                sendFTPResponse(550, "Access denied");
                return;
            }

            // Check if the directory exists
            disk = (DiskInterface) ftpPath.getSharedDevice().getInterface();
            FileStatus sts = disk.fileExists(this, tree, ftpPath.getSharePath());

            if (sts == FileStatus.DirectoryExists) {

                // Delete the new directory
                disk.deleteDirectory(this, tree, ftpPath.getSharePath());

                // Check if there are any file/directory change notify requests active
                DiskDeviceContext diskCtx = (DiskDeviceContext) tree.getContext();
                if (diskCtx.hasChangeHandler())
                    diskCtx.getChangeHandler().notifyFileChanged(NotifyAction.Removed, ftpPath.getSharePath());

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_DIRECTORY))
                    debugPrintln("DeleteDir ftp=" + ftpPath.getFTPPath() + ", share=" + ftpPath.getShareName() + ", path="
                            + ftpPath.getSharePath());
            } else {

                // File already exists with that name or directory does not exist return an error
                sendFTPResponse(550, sts == FileStatus.FileExists ? "File exists with that name" : "Directory does not exist");
                return;
            }
        }
        catch (Exception ex) {
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error deleting directory, " + ex.toString());
            }
            debugPrintln(ex);
            sendFTPResponse(550, "Failed to delete directory");
            return;
        }

        // Return a success status
        sendFTPResponse(250, "Directory deleted OK");
    }

    /**
     * Process a machine listing request, single folder
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procMachineListing(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPResponse(530, "Not logged in");
            return;
        }

        // Check if an argument has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error, parameter required");
            return;
        }

        // Create the path to be listed
        FTPPath ftpPath = generatePathForRequest(req, false, true);
        if (ftpPath == null) {
            sendFTPResponse(500, "Invalid path");
            return;
        }

        // Get the file information
        DiskInterface disk = null;
        TreeConnection tree = null;

        try {

            // Create a temporary tree connection
            tree = getTreeConnection(ftpPath.getSharedDevice());

            // Access the virtual filesystem driver
            disk = (DiskInterface) ftpPath.getSharedDevice().getInterface();

            // Get the file information
            FileInfo finfo = disk.getFileInformation(this, tree, ftpPath.getSharePath());

            if (finfo == null) {
                sendFTPResponse(550, "Path " + req.getArgument() + " not available");
                return;
            } else if (finfo.isDirectory() == false) {
                sendFTPResponse(501, "Path " + req.getArgument() + " is not a directory");
                return;
            }

            // Return the folder details
            sendFTPResponse("250- Listing " + req.getArgument());

            StringBuffer mlstStr = new StringBuffer(80);
            mlstStr.append(" ");

            generateMlstString(finfo, m_mlstFacts, mlstStr, true);
            mlstStr.append(CRLF);

            sendFTPResponse(mlstStr.toString());
            sendFTPResponse("250 End");

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_FILE))
                debugPrintln("Mlst ftp=" + ftpPath.getFTPPath() + ", share=" + ftpPath.getShareName() + ", info=" + finfo);
        }
        catch (Exception ex) {
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error retrieving file information, " + ex.toString());
            }
            debugPrintln(ex);
            sendFTPResponse(550, "Error retrieving file information");
        }
    }

    /**
     * Process a machine listing request, folder contents
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procMachineListingContents(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if the request has an argument, if not then use the current working directory
        if (req.hasArgument() == false)
            req.updateArgument(".");

        // Create the path for the file listing
        FTPPath ftpPath = m_cwd;
        if (req.hasArgument())
            ftpPath = generatePathForRequest(req, true);

        if (ftpPath == null) {
            sendFTPResponse(500, "Invalid path");
            return;
        }

        // Check if the session has the required access
        if (ftpPath.isRootPath() == false) {

            // Check if the session has access to the filesystem
            TreeConnection tree = getTreeConnection(ftpPath.getSharedDevice());
            if (tree == null || tree.hasReadAccess() == false) {

                // Session does not have access to the filesystem
                sendFTPResponse(550, "Access denied");
                return;
            }
        }

        // Send the intermediate response
        sendFTPResponse(150, "File status okay, about to open data connection");

        // Check if there is an active data session
        if (m_dataSess == null) {
            sendFTPResponse(425, "Can't open data connection");
            return;
        }

        // Get the data connection socket
        Socket dataSock = null;

        try {
            dataSock = m_dataSess.getSocket();
        }
        catch (Exception ex) {
            debugPrintln(ex);
        }

        if (dataSock == null) {
            sendFTPResponse(426, "Connection closed; transfer aborted");
            return;
        }

        // Output the directory listing to the client
        Writer dataWrt = null;

        try {

            // Open an output stream to the client
            if (isUTF8Enabled())
                dataWrt = new OutputStreamWriter(dataSock.getOutputStream(), "UTF-8");
            else
                dataWrt = new OutputStreamWriter(dataSock.getOutputStream());

            // Get a list of file information objects for the current directory
            List<FileInfo> files = null;

            files = listFilesForPath(ftpPath, false, false);

            // Output the file list to the client
            if (files != null) {

                // DEBUG
                if (Debug.EnableInfo && hasDebug(DBG_SEARCH))
                    debugPrintln("MLsd found " + files.size() + " files in " + ftpPath.getFTPPath());

                // Output the file information to the client
                StringBuffer str = new StringBuffer(MLSD_BUFFER_SIZE);

                for (int i = 0; i < files.size(); i++) {

                    // Get the current file information
                    FileInfo finfo = files.get(i);

                    generateMlstString(finfo, m_mlstFacts, str, false);
                    str.append(CRLF);

                    // Output the file information record when the buffer is full
                    if (str.length() >= MLSD_BUFFER_SIZE) {

                        // Output the file data records
                        dataWrt.write(str.toString());

                        // Reset the buffer
                        str.setLength(0);
                    }
                }

                // Flush any remaining file record data
                if (str.length() > 0)
                    dataWrt.write(str.toString());
            }

            // End of file list transmission
            sendFTPResponse(226, "Closing data connection");
        }
        catch (Exception ex) {

            // Failed to send file listing
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error reading file list, " + ex.toString());
            }
            debugPrintln(ex);

            sendFTPResponse(451, "Error reading file list");
        }
        finally {

            // Close the data stream to the client
            if (dataWrt != null)
                dataWrt.close();

            // Close the data connection to the client
            if (m_dataSess != null) {
                getFTPServer().releaseDataSession(m_dataSess);
                m_dataSess = null;
            }
        }
    }

    /**
     * Process a get modification date/time command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procGetModifyDateTime(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if an argument has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error, parameter required");
            return;
        }

        // Create the path for the file request
        FTPPath ftpPath = generatePathForRequest(req, true);
        if (ftpPath == null) {
            sendFTPResponse(550, "Invalid path");
            return;
        }

        // Get the file information
        DiskInterface disk = null;
        TreeConnection tree = null;

        try {

            // Create a temporary tree connection
            tree = getTreeConnection(ftpPath.getSharedDevice());

            // Access the virtual filesystem driver
            disk = (DiskInterface) ftpPath.getSharedDevice().getInterface();

            // Get the file information
            FileInfo finfo = disk.getFileInformation(this, tree, ftpPath.getSharePath());

            if (finfo == null) {
                sendFTPResponse(550, "File " + req.getArgument() + " not available");
                return;
            }

            // Return the file modification date/time
            if (finfo.hasModifyDateTime())
                sendFTPResponse(213, FTPDate.packMlstDateTime(finfo.getModifyDateTime()));
            else
                sendFTPResponse(550, "Modification date/time not available for " + finfo.getFileName());

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_FILE))
                debugPrintln("File modify date/time ftp=" + ftpPath.getFTPPath() + ", share=" + ftpPath.getShareName()
                        + ", modified=" + finfo.getModifyDateTime());
        }
        catch (Exception ex) {
            sendFTPResponse(550, "Error retrieving file modification date/time");
        }
    }

    /**
     * Process a modify date/time command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procModifyDateTime(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if an argument has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error, parameter required");
            return;
        }

        // Check the format of the argument to detemine if this is a get or set modify date/time
        // request
        //
        // Get format is just the filename/path
        // Set format is YYYYMMDDHHMMSS <path>
        String path = req.getArgument();
        long modifyDateTime = 0L;

        if (path.length() > MDTM_DATETIME_MINLEN && path.indexOf(' ') != -1) {

            // Check if the first argument looks like a date/time value
            boolean settime = true;
            for (int i = 0; i < MDTM_DATETIME_MINLEN; i++) {
                if (Character.isDigit(path.charAt(i)) == false)
                    settime = false;
            }

            // Looks like a date/time value
            if (settime == true) {

                try {

                    // Parse the various fields
                    int year = Integer.valueOf(path.substring(0, 4)).intValue();
                    int month = Integer.valueOf(path.substring(4, 6)).intValue();
                    int day = Integer.valueOf(path.substring(6, 8)).intValue();

                    int hours = Integer.valueOf(path.substring(8, 10)).intValue();
                    int mins = Integer.valueOf(path.substring(10, 12)).intValue();
                    int secs = Integer.valueOf(path.substring(12, 14)).intValue();

                    // Check if the date/time includes milliseconds
                    int millis = 0;
                    int sep = path.indexOf(' ', MDTM_DATETIME_MINLEN);

                    if (path.charAt(MDTM_DATETIME_MINLEN) == '.') {

                        // Find the seperator between the date/time and path
                        millis = Integer.valueOf(path.substring(MDTM_DATETIME_MINLEN + 1, sep)).intValue();
                    }

                    // Create the modify date/time, month is zero based
                    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

                    cal.set(year, month - 1, day, hours, mins, secs);
                    if (millis != 0)
                        cal.set(Calendar.MILLISECOND, millis);

                    // Get the modify date/time
                    modifyDateTime = cal.getTimeInMillis();

                    // Remove the date/time from the request argument
                    path = path.substring(sep + 1);
                    req.updateArgument(path);

                    // DEBUG
                    if (Debug.EnableInfo && hasDebug(DBG_FILE))
                        debugPrintln("Modify date/time arg=" + path + ", utcTime=" + modifyDateTime);
                }
                catch (NumberFormatException ex) {
                }
            }
        }

        // Create the path for the file request
        FTPPath ftpPath = generatePathForRequest(req, true);
        if (ftpPath == null) {
            sendFTPResponse(550, "Invalid path");
            return;
        }

        // Get the file information
        DiskInterface disk = null;
        TreeConnection tree = null;

        try {

            // Create a temporary tree connection
            tree = getTreeConnection(ftpPath.getSharedDevice());

            // Access the virtual filesystem driver
            disk = (DiskInterface) ftpPath.getSharedDevice().getInterface();

            // Check if the modify date/time should be set
            if (modifyDateTime != 0L) {

                // Set the file/folder modification date/time
                FileInfo finfo = new FileInfo();
                finfo.setModifyDateTime(modifyDateTime);
                finfo.setFileInformationFlags(FileInfo.SetModifyDate);

                disk.setFileInformation(this, tree, ftpPath.getSharePath(), finfo);
            }

            // Get the file information
            FileInfo finfo = disk.getFileInformation(this, tree, ftpPath.getSharePath());

            if (finfo == null) {
                sendFTPResponse(550, "File " + req.getArgument() + " not available");
                return;
            }

            // Return the file modification date/time
            if (finfo.hasModifyDateTime())
                sendFTPResponse(213, FTPDate.packMlstDateTime(finfo.getModifyDateTime()));
            else
                sendFTPResponse(550, "Modification date/time not available for " + finfo.getFileName());

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_FILE))
                debugPrintln("File modify date/time ftp=" + ftpPath.getFTPPath() + ", share=" + ftpPath.getShareName()
                        + ", modified=" + finfo.getModifyDateTime());
        }
        catch (Exception ex) {
            sendFTPResponse(550, "Error retrieving file modification date/time");
        }
    }

    /**
     * Process a server features request
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procFeatures(FTPRequest req)
            throws IOException {

        // Return the list of supported server features
        sendFTPResponse("211-Features supported");

        // Modify date/time and size commands supported
        if (FeatureMFMT)
            sendFTPResponse(" MFMT");

        if (FeatureSIZE)
            sendFTPResponse(" SIZE");

        if (FeatureUTF8)
            sendFTPResponse(" UTF8");

        // Machine listing supported, build the fact list
        if (FeatureMLST) {
            StringBuffer mlstStr = new StringBuffer();

            mlstStr.append(" MLST ");

            for (int i = 0; i < _factNames.length; i++) {

                // Output the fact name
                mlstStr.append(_factNames[i]);

                // Check if the fact is enabled by default
                if ((MLST_DEFAULT & (1 << i)) != 0)
                    mlstStr.append("*");
                mlstStr.append(";");
            }

            sendFTPResponse(mlstStr.toString());
            sendFTPResponse(" MLSD");
        }

        if (FeatureAUTH)
            sendFTPResponse(" AUTH TLS");

        sendFTPResponse(211, "END");
    }

    /**
     * Process a file size command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procFileSize(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if an argument has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Syntax error, parameter required");
            return;
        }

        // Create the path for the file listing
        FTPPath ftpPath = generatePathForRequest(req, true);
        if (ftpPath == null) {
            sendFTPResponse(550, "Invalid path");
            return;
        }

        // Get the file information
        DiskInterface disk = null;
        TreeConnection tree = null;

        try {

            // Create a temporary tree connection
            tree = getTreeConnection(ftpPath.getSharedDevice());

            // Access the virtual filesystem driver
            disk = (DiskInterface) ftpPath.getSharedDevice().getInterface();

            // Get the file information
            FileInfo finfo = disk.getFileInformation(this, tree, ftpPath.getSharePath());

            if (finfo == null) {
                sendFTPResponse(550, "File " + req.getArgument() + " not available");
                return;
            }

            // Return the file size
            sendFTPResponse(213, "" + finfo.getSize());

            // DEBUG
            if (Debug.EnableInfo && hasDebug(DBG_FILE))
                debugPrintln("File size ftp=" + ftpPath.getFTPPath() + ", share=" + ftpPath.getShareName() + ", size="
                        + finfo.getSize());
        }
        catch (Exception ex) {
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Error retriving file size, " + ex.toString());
            }
            debugPrintln(ex);

            sendFTPResponse(550, "Error retrieving file size");
        }
    }

    /**
     * Process a site specific command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procSite(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if the FTP server has a site interface
        if (getFTPServer().hasSiteInterface()) {

            // Pass the request to the site interface
            FTPSiteInterface siteInterface = getFTPServer().getSiteInterface();

            siteInterface.processFTPSiteCommand(this, req);
        } else {

            // SITE command not implemented
            sendFTPResponse(501, "SITE commands not implemented");
        }
    }

    /**
     * Process a structure command. This command is obsolete.
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procStructure(FTPRequest req)
            throws IOException {

        // Check for the file structure argument
        if (req.hasArgument() && req.getArgument().equalsIgnoreCase("F")) {

            // Return a success status
            sendFTPResponse(200, "OK");
        } else {

            // Return an error response
            sendFTPResponse(504, "Obsolete");
        }
    }

    /**
     * Process a mode command. This command is obsolete.
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procMode(FTPRequest req)
            throws IOException {

        // Check for the stream transfer mode argument
        if (req.hasArgument() && req.getArgument().equalsIgnoreCase("S")) {

            // Return a success status
            sendFTPResponse(200, "OK");
        } else {

            // Return an error response
            sendFTPResponse(504, "Obsolete");
        }
    }

    /**
     * Process an allocate command. This command is obsolete.
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procAllocate(FTPRequest req)
            throws IOException {

        // Return a response
        sendFTPResponse(202, "Obsolete");
    }

    /**
     * Process an abort command. The main abort processing is done in the store/return file handling
     * during an active file transfer.
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procAbort(FTPRequest req)
            throws IOException {

        // Return a success response
        sendFTPResponse(226, "No active transfer to abort");
    }

    /**
     * Process an authentication command for SSL/TLS.
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procAuth(FTPRequest req)
            throws IOException {

        // Check if SSL/TLS sessions are enabled
        if (getFTPServer().getFTPConfiguration().isFTPSEnabled() == false) {
            sendFTPResponse(534, "SSL/TLS sessions not available");
            return;
        }

        // Switch the control session into SSL/TLS mode
        try {

            // Check for SSL or TLS type
            if (req.hasArgument()) {

                String engineTyp = req.getArgument().toUpperCase();
                if (engineTyp.equals("SSL") || engineTyp.equals("TLS")) {

                    // Initialize the SSL engine
                    setupSSLEngine(engineTyp);

                    // Send a response to indicate the socket is ready to switch to SSL/TLS mode
                    sendUnencryptedFTPResponse(234, "Switching to " + engineTyp + " secure session");
                }
            } else {

                // Type not specified
                sendFTPResponse(421, "Failed to negotiate SSL/TLS, type not specified");
            }
        }
        catch (Exception ex) {
            if (Debug.EnableInfo && hasDebug(DBG_ERROR)) {
                debugPrintln(" Faile to negotiate SSL/TLS, " + ex.toString());
            }
            debugPrintln(ex);

            m_sslEngine = null;
            sendFTPResponse(421, "Failed to negotiate SSL/TLS");
        }
    }

    /**
     * Process a protected buffer size command.
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procProtectedBufferSize(FTPRequest req)
            throws IOException {

        // Check if the control session is using SSL/TLS
        if (m_sslEngine == null) {
            sendFTPResponse(503, "Not using secure connection");
            return;
        }

        // Convert the buffer size argument
        String arg = req.getArgument();
        if (arg == null || arg.length() == 0) {
            sendFTPResponse(501, "Empty buffer size argument");
            return;
        }

        // Parse the buffer size argument
        try {
            m_pbSize = Integer.parseInt(arg);
        }
        catch (NumberFormatException ex) {
            sendFTPResponse(501, "Invalid buffer size argument");
            return;
        }

        // Return a success status
        sendFTPResponse(200, "Buffer size ok");
    }

    /**
     * Process a data channel protection level command.
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procDataChannelProtection(FTPRequest req)
            throws IOException {

        // Check that the protected buffer size has been negotiated
        if (m_pbSize == -1) {
            sendFTPResponse(503, "Protected buffer size not negotiated");
            return;
        }

        // Validate the protection level
        String arg = req.getArgument().toUpperCase();

        if (arg == null || ProtLevels.indexOf(arg) == -1) {
            sendFTPResponse(504, "Invalid protection level, " + arg);
            return;
        }

        // Only accept the 'clear' protection level
        if (arg.equals(ProtLevelClear)) {

            // Accept the clear protection level, data connections sent in clear text
            sendFTPResponse(200, "Protection level accepted");
        } else {

            // Reject the protection level for now, we do not support protected data connections
            sendFTPResponse(534, "Protected data connections not supported");
        }
    }

    /**
     * Process a clear command channel command
     *
     * @param ftpReq FTPRequest
     * @exception IOException Socket error
     */
    protected final void procClearCommandChannel(FTPRequest ftpReq)
            throws IOException {

        // Check if the control session is using SSL/TLS
        if (m_sslEngine == null) {
            sendFTPResponse(533, "Not using secure connection");
            return;
        }

        // Send the response over the protected session
        sendFTPResponse(200, "Secure connection closed");

        // Close the SSL engine
        m_sslEngine.closeOutbound();
        getSSLCommand(m_inbuf, 0);

        // Release resources used by the secure connection
        m_sslEngine = null;
        m_sslContext = null;

        m_sslIn = null;
        m_sslOut = null;
    }

    /**
     * Process an extended port command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procExtendedPort(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Check if the parameter has been specified
        if (req.hasArgument() == false) {
            sendFTPResponse(501, "Required argument missing");
            return;
        }

        // Parse the client address
        InetSocketAddress clientAddr;
        try {
            clientAddr = parseExtendedAddress(req.getArgument());
        }
        catch (Exception ex) {
            sendFTPResponse(501, ex.getMessage());
            return;
        }

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_DATAPORT))
            debugPrintln("Opening data socket addr=" + clientAddr.getAddress() + ", port=" + clientAddr.getPort());

        // Create an active data session, the actual socket connection will be made later
        m_dataSess = getFTPServer().allocateDataSession(this, clientAddr.getAddress(), clientAddr.getPort());

        // Return a success response to the client
        sendFTPResponse(200, "Port OK");

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_DATAPORT))
            debugPrintln("Extended port open addr=" + clientAddr.getAddress() + ", port=" + clientAddr.getPort());
    }

    /**
     * Process an extended passive command
     *
     * @param req FTPRequest
     * @exception IOException Socket error
     */
    protected final void procExtendedPassive(FTPRequest req)
            throws IOException {

        // Check if the user is logged in
        if (isLoggedOn() == false) {
            sendFTPNotLoggedOnResponse();
            return;
        }

        // Create a passive data session
        try {
            m_dataSess = getFTPServer().allocatePassiveDataSession(this, m_sock.getLocalAddress());
        }
        catch (IOException ex) {
            m_dataSess = null;
        }

        // Check if the data session is valid
        if (m_dataSess == null) {
            sendFTPResponse(550, "Requested action not taken");
            return;
        }

        // Get the passive connection address/port and return to the client
        int pasvPort = m_dataSess.getPassivePort();

        StringBuffer msg = new StringBuffer();

        msg.append("229 Entering Extended Passive Mode (|||");
        msg.append(pasvPort);
        msg.append("|)");

        sendFTPResponse(msg);

        // DEBUG
        if (Debug.EnableInfo && hasDebug(DBG_DATAPORT))
            debugPrintln("Extended passive open addr=" + m_sock.getLocalAddress() + ", port=" + pasvPort);
    }

    /**
     * Parse the extended network address string
     * <p>
     * Comments for ALF-684
     * RFC 2428:
     * <p>
     * AF Number   Protocol
     * ---------   --------
     * 1           Internet Protocol, Version 4 [Pos81a]
     * 2           Internet Protocol, Version 6 [DH96]
     * <p>
     * AF Number   Address Format      Example
     * ---------   --------------      -------
     * 1           dotted decimal      132.235.1.2
     * 2           IPv6 string         1080::8:800:200C:417A
     * representations
     * defined in [HD96]
     * <p>
     * The following are sample EPRT commands:
     * <p>
     * EPRT |1|132.235.1.2|6275|
     * EPRT |2|1080::8:800:200C:417A|5282|
     * <p>
     * So, we need to parse the InternetAddress according to AF Number.
     * That means we don't need to do any 'instanceof', just check the address
     * validity and raise the error, e.g. 501 (IP Address is not valid) if the
     * address is not convenient to AF Number.
     *
     * @param extAddr String
     * @return InetSocketAddress
     */
    private final InetSocketAddress parseExtendedAddress(String extAddr) {

        // Make sure the string is valid
        if (extAddr == null || extAddr.length() < 7)
            throw new IllegalArgumentException("Invalid argument");

        // Split the string into network type, network address and port strings
        StringTokenizer tokens = new StringTokenizer(extAddr, extAddr.substring(0, 1));
        if (tokens.countTokens() < 3)
            throw new IllegalArgumentException("Invalid argument");
        String netType = tokens.nextToken();
        String netAddr = tokens.nextToken();
        String netPort = tokens.nextToken();

        int afNumber = 0;
        try {
            afNumber = Integer.parseInt(netType);
        }
        catch (NumberFormatException ex) {
        }

        if (afNumber == TypeIPv4 || afNumber == TypeIPv6) {

            InetSocketAddress sockAddr = null;

            // Since Java handles IPv4/IPv6 addresses transparently as it is saying in
            // http://download.oracle.com/javase/1.5.0/docs/guide/net/ipv6_guide/index.html
            // We don't care about AF Number and Address Format, but just checking a validity
            // of the IP.
            InetAddress addr = null;

            try {
                addr = InetAddress.getByName(netAddr);
            }
            catch (UnknownHostException ex) {
            }

            InetAddress remoteAddr = m_sock.getInetAddress();
            if (!addr.equals(remoteAddr)) {
                if (Debug.EnableWarn && hasDebug(DBG_DATAPORT))
                    debugPrintln("EPRT address [" + addr + "] is not equal to client address [" + remoteAddr + "]. For security purposes client address is used for data transmission.");
                addr = remoteAddr;
            }

            // Avoid route connection problems with addresses where an interface is specified.
            // I.e. fe80:0:0:0:a00:27ff:fe42:94f7%eth1
            if (addr != null && addr instanceof Inet6Address) {
                try {
                    addr = InetAddress.getByAddress(addr.getAddress());
                }
                catch (UnknownHostException ex) {
                    throw new IllegalArgumentException("Unknown host");
                }
            }

            int port = -1;

            try {
                port = Integer.parseInt(netPort);
            }
            catch (NumberFormatException ex) {
            }

            if (port != -1)
                sockAddr = new InetSocketAddress(addr, port);

            return sockAddr;
        }

        throw new IllegalArgumentException("Invalid address/port argument");
    }

    /**
     * Build a list of file name or file information objects for the specified server path
     *
     * @param path     FTPPath
     * @param nameOnly boolean
     * @param hidden   boolean
     * @return List of file information objects
     */
    protected final List<FileInfo> listFilesForPath(FTPPath path, boolean nameOnly, boolean hidden) {

        // Check if the path is valid
        if (path == null)
            return null;

        // Check if the path is the root path
        List<FileInfo> files = new ArrayList<FileInfo>();

        if (path.hasSharedDevice() == false) {

            // The first level of directories are mapped to the available shares
            SharedDeviceList shares = getShareList();
            if (shares != null) {

                // Search for disk shares
                Enumeration<SharedDevice> enm = shares.enumerateShares();

                while (enm.hasMoreElements()) {

                    // Get the current shared device
                    SharedDevice shr = enm.nextElement();

                    // Create a file information object for the top level directory details
                    files.add(new FileInfo(shr.getName(), 0L, FileAttribute.Directory));
                }
            }
        } else {

            // Append a wildcard to the search path
            String searchPath = path.getSharePath();

            if (path.isDirectory())
                searchPath = path.makeSharePathToFile("*.*");

            // Create a temporary tree connection
            TreeConnection tree = new TreeConnection(path.getSharedDevice());

            // Start a search on the specified disk share
            DiskInterface disk = null;
            SearchContext ctx = null;

            int searchAttr = FileAttribute.Directory + FileAttribute.Normal;
            if (hidden)
                searchAttr += FileAttribute.Hidden;

            try {
                disk = (DiskInterface) path.getSharedDevice().getInterface();
                ctx = disk.startSearch(this, tree, searchPath, searchAttr);
            }
            catch (Exception ex) {
            }

            // Add the files to the list
            if (ctx != null) {

                // Get the file names/information
                while (ctx.hasMoreFiles()) {

                    // Check if a file name or file information is required
                    if (nameOnly) {

                        // Add a file name to the list
                        files.add( new FileInfo( ctx.nextFileName(), 0, FileAttribute.NTNormal));
                    } else {

                        // Create a file information object
                        FileInfo finfo = new FileInfo();

                        if (ctx.nextFileInfo(finfo) == false)
                            break;
                        if (finfo.getFileName() != null)
                            files.add(finfo);
                    }
                }
            }
        }

        // Return the list of file names/information
        return files;
    }

    /**
     * Get the list of filtered shares that are available to this session
     *
     * @return SharedDeviceList
     */
    protected final SharedDeviceList getShareList() {

        // Check if the filtered share list has been initialized
        if (m_shares == null) {

            // Get a list of shared filesystems
            SharedDeviceList shares = getFTPServer().getShareMapper().getShareList(getFTPServer().getServerName(), this, false);

            // Search for disk shares
            m_shares = new SharedDeviceList();
            Enumeration<SharedDevice> enm = shares.enumerateShares();

            while (enm.hasMoreElements()) {

                // Get the current shared device
                SharedDevice shr = enm.nextElement();

                // Check if the share is a disk share
                if (shr instanceof DiskSharedDevice)
                    m_shares.addShare(shr);
            }

            // Check if there is an access control manager available, if so then filter the list of
            // shared filesystems
            if (getServer().hasAccessControlManager()) {

                // Get the access control manager
                AccessControlManager aclMgr = getServer().getAccessControlManager();

                // Filter the list of shared filesystems
                m_shares = aclMgr.filterShareList(this, m_shares);
            }
        }

        // Return the filtered shared filesystem list
        return m_shares;
    }

    /**
     * Get a tree connection for the specified shared device. Creates and caches a new tree
     * connection if required.
     *
     * @param share SharedDevice
     * @return TreeConnection
     */
    protected final TreeConnection getTreeConnection(SharedDevice share) {

        // Check if the share is valid
        if (share == null)
            return null;

        // Check if there is a tree connection in the cache
        TreeConnection tree = m_connections.findConnection(share.getName());
        if (tree == null) {

            // Create a new tree connection
            tree = new TreeConnection(share);
            m_connections.addConnection(tree);

            // Set the access permission for the shared filesystem
            if (getServer().hasAccessControlManager()) {

                // Set the access permission to the shared filesystem
                AccessControlManager aclMgr = getServer().getAccessControlManager();

                int access = aclMgr.checkAccessControl(this, share);
                if (access != AccessControl.Default)
                    tree.setPermission(access);
            } else {

                // Allow full access to the filesystem
                tree.setPermission(ISMBAuthenticator.ShareStatus.WRITEABLE);
            }
        }

        // Return the connection
        return tree;
    }

    /**
     * Check if an abort command has been sent by the client
     *
     * @return boolean
     */
    private final boolean checkForAbort() {

        try {

            // Check if there is any pending data on the command socket
            if (m_in.available() > 0) {

                // Read the next request
                FTPRequest ftpReq = getNextCommand(false);
                if (ftpReq != null) {

                    // Check for an abort command
                    if (ftpReq.isCommand() == FTPCommand.ABOR) {

                        // DEBUG
                        if (Debug.EnableDbg && hasDebug(DBG_FILEIO))
                            debugPrintln("Transfer aborted by client");

                        // Indicate an abort has been received
                        return true;
                    } else {

                        // Queue the request for processing later
                        m_ftpCmdList.add(ftpReq);
                    }
                }
            }
        }
        catch (IOException ex) {

            // DEBUG
            if (Debug.EnableError && hasDebug(DBG_ERROR))
                debugPrintln("Error during check for abort, " + ex.toString());
        }

        // No command, or not an abort command
        return false;
    }

    /**
     * Read the next FTP command from the command socket, or get a command from the list of queued
     * requests
     *
     * @param checkQueue boolean
     * @return FTPRequest
     * @exception SocketException Socket error
     * @exception IOException Socket error
     */
    private final FTPRequest getNextCommand(boolean checkQueue)
            throws SocketException, IOException {

        // Check if there are any queued requests
        FTPRequest nextReq = null;

        if (checkQueue == true && m_ftpCmdList.size() > 0) {

            // Get the next queued request
            nextReq = m_ftpCmdList.remove(0);
        } else {

            // Loop until a valid request is received, or the connection is closed
            while (nextReq == null) {

                // Wait for an incoming request
                int rdlen = m_in.read(m_inbuf);

                // Check if there is no more data, the other side has dropped the connection
                if (rdlen == -1) {
                    closeSession();
                    return null;
                } else if (rdlen == m_inbuf.length) {

                    // Looks like there is more data to be read for the current command, we need to extend the buffer
                    //
                    // Check if the command buffer has already been extended to the maximum size
                    if (m_inbuf.length < MaxCommandBufSize) {

                        // Extend the command buffer
                        int curLen = m_inbuf.length;
                        int availLen = m_in.available();
                        int newLen = Math.max(m_inbuf.length * 2, curLen + availLen + 50);

                        if (newLen > MaxCommandBufSize)
                            newLen = MaxCommandBufSize;

                        // Check if the new buffer size is large enough for the current command
                        if (newLen > (curLen + availLen)) {

                            // Allocate a new buffer and copy the existing data over to it
                            byte[] newbuf = new byte[newLen];
                            System.arraycopy(m_inbuf, 0, newbuf, 0, m_inbuf.length);

                            // Move the new command buffer into place
                            m_inbuf = newbuf;

                            // DEBUG
                            if (Debug.EnableInfo && hasDebug(DBG_RXDATA))
                                debugPrintln("Extended command buffer to " + m_inbuf.length + " bytes");

                            // Read the remaining data
                            int rdlen2 = m_in.read(m_inbuf, curLen, m_inbuf.length - curLen);
                            if (rdlen2 == -1) {
                                closeSession();
                                return null;
                            } else {

                                // Calculate the total read length
                                rdlen = rdlen + rdlen2;

                                // DEBUG
                                if (Debug.EnableInfo && hasDebug(DBG_RXDATA))
                                    debugPrintln("Secondary read " + rdlen2 + " bytes, total bytes read " + rdlen);
                            }
                        }
                    } else {

                        // Command is too large, clear any pending data on the command socket and ignore it
                        clearCommandSocket();

                        // DEBUG
                        if (Debug.EnableInfo && hasDebug(DBG_RXDATA))
                            debugPrintln("Received command too large, ignored");

                        return null;
                    }

                }

                // If there is an SSL engine associated with this session then decrypt the received data
                if (m_sslEngine != null)
                    rdlen = getSSLCommand(m_inbuf, rdlen);

                // Trim the trailing <CR><LF>

                if (rdlen > 0) {
                    while (rdlen > 0 && m_inbuf[rdlen - 1] == '\r' || m_inbuf[rdlen - 1] == '\n')
                        rdlen--;

                    // Get the command string, create the new request
                    String cmd = null;

                    if (isUTF8Enabled()) {
                        cmd = m_normalizer.normalize(new String(m_inbuf, 0, rdlen, "UTF8"));
                    } else
                        cmd = new String(m_inbuf, 0, rdlen);

                    nextReq = new FTPRequest(cmd);
                }
            }
        }

        // Return the request
        return nextReq;
    }

    /**
     * Clear the command socket of pending data
     *
     * @exception IOException Socket error
     */
    protected void clearCommandSocket()
            throws IOException {

        // Loop until all data has been cleared from the command socket or the socket is closed
        int rdlen = 0;

        while (m_in.available() > 0 && rdlen >= 0) {

            // Read a block of data from the command socket
            rdlen = m_in.read(m_inbuf);
            if (rdlen == -1)
                closeSession();
        }
    }

    /**
     * Get the next command data on an SSL/TLS encrypted connection
     *
     * @param buf byte[]
     * @param len int
     * @return int
     * @exception SocketException Socket error
     * @exception IOException Socket error
     */
    protected final int getSSLCommand(byte[] buf, int len)
            throws SocketException, IOException {

        m_sslIn.limit(m_sslIn.capacity());
        m_sslIn.position(len);
        m_sslIn.flip();

        m_sslOut.clear();

        // Get the SSL engine status
        SSLEngineResult sslRes = m_sslEngine.unwrap(m_sslIn, m_sslOut);
        while (m_sslIn.position() < len) {
            sslRes = m_sslEngine.unwrap(m_sslIn, m_sslOut);
        }

        // DEBUG
        if (Debug.EnableDbg && hasDebug(DBG_SSL))
            debugPrintln("SSL unwrap() len=" + len + ", returned " + sslRes.bytesProduced() + " bytes, res=" + sslRes);

        int unwrapLen = sslRes.bytesProduced();
        boolean loopDone = false;
        Runnable task = null;

        while (loopDone == false && m_sslEngine.getHandshakeStatus() != HandshakeStatus.NOT_HANDSHAKING &&
                sslRes.getStatus() != SSLEngineResult.Status.CLOSED) {

            switch (m_sslEngine.getHandshakeStatus()) {
                case NEED_TASK:

                    // DEBUG
                    if (Debug.EnableDbg && hasDebug(DBG_SSL))
                        debugPrintln("SSL engine status=NEED_TASK");

                    // Run the SSL engine task in the current thread
                    while ((task = m_sslEngine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    break;
                case NEED_WRAP:

                    // DEBUG
                    if (Debug.EnableDbg && hasDebug(DBG_SSL))
                        debugPrintln("SSL engine status=NEED_WRAP");

                    m_sslIn.limit(m_sslIn.capacity());
                    m_sslIn.flip();

                    m_sslOut.clear();

                    while (m_sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
                        sslRes = m_sslEngine.wrap(m_sslIn, m_sslOut);

                        // DEBUG
                        if (Debug.EnableDbg && hasDebug(DBG_SSL))
                            debugPrintln("  wrap() returned " + sslRes.bytesProduced() + " bytes, res=" + sslRes);
                    }

                    // Send the output to the client
                    m_sslOut.flip();

                    if (m_sslOut.remaining() > 0) {

                        // DEBUG
                        if (Debug.EnableDbg && hasDebug(DBG_SSL))
                            debugPrintln("  Send data to client = " + m_sslOut.remaining());

                        // Send the encrypted data to the client
                        m_sock.getOutputStream().write(m_sslOut.array(), 0, m_sslOut.remaining());
                        m_sock.getOutputStream().flush();
                    }
                    break;
                case NEED_UNWRAP:

                    // DEBUG
                    if (Debug.EnableDbg && hasDebug(DBG_SSL))
                        debugPrintln("SSL engine status=NEED_UNWRAP");

                    // Read more data from the socket
                    int rdlen = m_in.read(m_inbuf);

                    // Check if there is no more data, the other side has dropped the connection
                    if (rdlen == -1) {

                        // DEBUG
                        if (Debug.EnableDbg && hasDebug(DBG_SSL))
                            debugPrintln("  Socket read returned -1, closing session");

                        // Close the FTP session
                        closeSession();
                        return 0;
                    }

                    m_sslIn.limit(m_sslIn.capacity());
                    m_sslIn.position(rdlen);
                    m_sslIn.flip();

                    while (m_sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_UNWRAP && m_sslIn.remaining() > 0) {
                        m_sslOut.limit(m_sslOut.capacity());
                        sslRes = m_sslEngine.unwrap(m_sslIn, m_sslOut);

                        // DEBUG
                        if (Debug.EnableDbg && hasDebug(DBG_SSL))
                            debugPrintln("  unwrap() len=" + rdlen + ",returned " + sslRes.bytesProduced() + " bytes, res=" + sslRes);

                        // Run the SSL engine task in the current thread
                        if (m_sslEngine.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                            while ((task = m_sslEngine.getDelegatedTask()) != null) {
                                task.run();

                                // DEBUG
                                if (Debug.EnableDbg && hasDebug(DBG_SSL))
                                    debugPrintln("  task during unwrap");
                            }
                        }
                    }

                    m_sslOut.flip();
                    if (m_sslOut.remaining() > 0) {
                        if (m_sslEngine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
                            unwrapLen = m_sslOut.remaining();
                            loopDone = true;
                        } else {

                            // DEBUG
                            if (Debug.EnableDbg && hasDebug(DBG_SSL))
                                debugPrintln("  Send data to client = " + m_sslOut.remaining());

                            // Send the encrypted data to the client
                            m_sock.getOutputStream().write(m_sslOut.array(), 0, m_sslOut.remaining());
                            m_sock.getOutputStream().flush();
                        }
                    }
                    break;
                case NOT_HANDSHAKING:

                    // DEBUG
                    if (Debug.EnableDbg && hasDebug(DBG_SSL))
                        debugPrintln("SSL engine status=NOT_HANDSHAKING");
                    loopDone = true;
                    break;
                case FINISHED:

                    // DEBUG
                    if (Debug.EnableDbg && hasDebug(DBG_SSL))
                        debugPrintln("SSL engine status=FINISHED");
                    loopDone = true;
                    break;
            }
        }

        // Move decrypted data to the input buffer
        if (unwrapLen > 0)
            System.arraycopy(m_sslOut.array(), 0, m_inbuf, 0, unwrapLen);

        // Return the decrypted data length
        return unwrapLen;
    }

    /**
     * Initialize the SSL engine when SSL mode is enabled on the command socket
     *
     * @param engineTyp String
     * @exception IOException Socket error
     * @throws NoSuchAlgorithmException No such SSL algorithm error
     * @throws CertificateException Certificate error
     * @throws KeyStoreException Key store error
     * @throws UnrecoverableKeyException Unrecoverable key error
     * @throws KeyManagementException Key management error
     */
    protected final void setupSSLEngine(String engineTyp)
            throws IOException, NoSuchAlgorithmException, CertificateException,
            KeyStoreException, UnrecoverableKeyException, KeyManagementException {

        // Get the FTP configuration
        FTPConfigSection ftpConfig = getFTPServer().getFTPConfiguration();

        // Load the key store and trust store
        KeyStore keyStore = KeyStore.getInstance(ftpConfig.getKeyStoreType());
        keyStore.load(new FileInputStream(ftpConfig.getKeyStorePath()), ftpConfig.getKeyStorePassphrase());

        String defaultAlgorithm = KeyManagerFactory.getDefaultAlgorithm();

        KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(defaultAlgorithm);
        keyFactory.init(keyStore, ftpConfig.getKeyStorePassphrase());

        defaultAlgorithm = TrustManagerFactory.getDefaultAlgorithm();

        m_sslContext = SSLContext.getInstance(engineTyp);

        // MNT-7301 FTPS server requires unnecessarly to have a trustStore while a keyStore should be sufficient
        TrustManager[] trManager = null;

        if (ftpConfig.getTrustStorePath() != null) {
            KeyStore trustStore = KeyStore.getInstance(ftpConfig.getTrustStoreType());
            trustStore.load(new FileInputStream(ftpConfig.getTrustStorePath()), ftpConfig.getTrustStorePassphrase());
            TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(defaultAlgorithm);
            trustFactory.init(trustStore);
            trManager = trustFactory.getTrustManagers();
        }

        m_sslContext.init(keyFactory.getKeyManagers(), trManager, null);

        m_sslEngine = m_sslContext.createSSLEngine();
        m_sslEngine.setUseClientMode(false);
        m_sslEngine.setWantClientAuth(true);

        SSLSession sslSess = m_sslEngine.getSession();
        m_sslOut = ByteBuffer.allocate(sslSess.getApplicationBufferSize() + 50);

        if (m_inbuf.length < sslSess.getApplicationBufferSize())
            m_inbuf = new byte[sslSess.getApplicationBufferSize()];
        m_sslIn = ByteBuffer.wrap(m_inbuf);
    }

    /**
     * Check if the session is in SSL/TLS mode
     *
     * @return boolean
     */
    protected final boolean isSecureSession() {
        return m_sslEngine != null ? true : false;
    }

    /**
     * Generate a machine listing string for the specified file/folder information
     *
     * @param finfo     FileInfo
     * @param mlstFlags int
     * @param buf       StringBuffer
     * @param isMlsd    boolean
     */
    protected final void generateMlstString(FileInfo finfo, int mlstFlags, StringBuffer buf, boolean isMlsd) {

        // Create the machine listing record

        for (int i = 0; i < _factNames.length; i++) {

            // Check if the current fact is enabled
            int curFact = 1 << i;

            if ((mlstFlags & curFact) != 0) {

                // Output the fact value
                switch (curFact) {

                    // File size
                    case MLST_SIZE:
                        buf.append(_factNames[i]);
                        buf.append("=");
                        buf.append(finfo.getSize());
                        buf.append(";");
                        break;

                    // Modify date/time
                    case MLST_MODIFY:
                        if (finfo.hasModifyDateTime()) {
                            buf.append(_factNames[i]);
                            buf.append("=");
                            buf.append(FTPDate.packMlstDateTime(finfo.getModifyDateTime()));
                            buf.append(";");
                        }
                        break;

                    // Creation date/time
                    case MLST_CREATE:
                        if (finfo.hasCreationDateTime()) {
                            buf.append(_factNames[i]);
                            buf.append("=");
                            buf.append(FTPDate.packMlstDateTime(finfo.getCreationDateTime()));
                            buf.append(";");
                        }
                        break;

                    // Type
                    case MLST_TYPE:
                        buf.append(_factNames[i]);

                        if (finfo.isDirectory() == false) {
                            buf.append("=file;");
                        } else {
                            buf.append("=dir;");
                        }
                        break;

                    // Unique identifier
                    case MLST_UNIQUE:
                        if (finfo.getFileId() != -1) {
                            buf.append(_factNames[i]);
                            buf.append("=");
                            buf.append(finfo.getFileId());
                            buf.append(";");
                        }
                        break;

                    // Permissions
                    case MLST_PERM:
                        buf.append(_factNames[i]);
                        buf.append("=");
                        if (finfo.isDirectory()) {
                            buf.append(finfo.isReadOnly() ? "el" : "ceflmp");
                        } else {
                            buf.append(finfo.isReadOnly() ? "r" : "rwadf");
                        }
                        buf.append(";");
                        break;

                    // Media-type
                    case MLST_MEDIATYPE:
                        break;
                }
            }
        }

        // Add the file name
        buf.append(" ");
        buf.append(finfo.getFileName());
    }

    /**
     * Return the local IP address as a string in 'n,n,n,n' format
     *
     * @return String
     */
    private final String getLocalFTPAddressString() {
        return m_sock.getLocalAddress().getHostAddress().replace('.', ',');
    }

    /**
     * Return a not logged status
     *
     * @exception IOException Socket error
     */
    protected final void sendFTPNotLoggedOnResponse()
            throws IOException {
        sendFTPResponse(530, "Not logged on");
    }

    /**
     * Indicate that FTP filesystem searches are case sensitive
     *
     * @return boolean
     */
    public boolean useCaseSensitiveSearch() {
        return true;
    }

    /**
     * Start the FTP session in a seperate thread
     */
    public void run() {

        try {

            // Debug
            if (Debug.EnableInfo && hasDebug(DBG_STATE))
                debugPrintln("FTP session started");

            // Create the input/output streams
            m_in = m_sock.getInputStream();
            m_out = new OutputStreamWriter(m_sock.getOutputStream());

            m_inbuf = new byte[DefCommandBufSize];

            // Return the initial response
            sendFTPResponse(220, "FTP server ready");

            // Start/end times if timing debug is enabled
            long startTime = 0L;
            long endTime = 0L;

            // The server session loops until the NetBIOS hangup state is set.
            FTPRequest ftpReq = null;

            while (m_sock != null) {

                // Wait for a request
                ftpReq = getNextCommand(true);
                if (ftpReq == null)
                    continue;

                // Debug
                if (Debug.EnableInfo && hasDebug(DBG_TIMING))
                    startTime = System.currentTimeMillis();

                if (Debug.EnableInfo && hasDebug(DBG_RXDATA))
                    debugPrintln("Rx cmd=" + ftpReq);

                // Parse the received command, and validate
                switch (ftpReq.isCommand()) {

                    // User command
                    case USER:
                        procUser(ftpReq);
                        break;

                    // Password command
                    case PASS:
                        procPassword(ftpReq);
                        break;

                    // Quit command
                    case QUIT:
                        procQuit(ftpReq);
                        break;

                    // Type command
                    case TYPE:
                        procType(ftpReq);
                        break;

                    // Port command
                    case PORT:
                        procPort(ftpReq);
                        break;

                    // Passive command
                    case PASV:
                        procPassive(ftpReq);
                        break;

                    // Restart position command
                    case REST:
                        procRestart(ftpReq);
                        break;

                    // Return file command
                    case RETR:
                        procReturnFile(ftpReq);

                        // Reset the restart position
                        m_restartPos = 0;
                        break;

                    // Store file command
                    case STOR:
                        procStoreFile(ftpReq, false);
                        break;

                    // Append file command
                    case APPE:
                        procStoreFile(ftpReq, true);
                        break;

                    // Print working directory command
                    case PWD:
                    case XPWD:
                        procPrintWorkDir(ftpReq);
                        break;

                    // Change working directory command
                    case CWD:
                    case XCWD:
                        procChangeWorkDir(ftpReq);
                        break;

                    // Change to previous directory command
                    case CDUP:
                    case XCUP:
                        procCdup(ftpReq);
                        break;

                    // Full directory listing command
                    case LIST:
                        procList(ftpReq);
                        break;

                    // Short directory listing command
                    case NLST:
                        procNList(ftpReq);
                        break;

                    // Delete file command
                    case DELE:
                        procDeleteFile(ftpReq);
                        break;

                    // Rename file from command
                    case RNFR:
                        procRenameFrom(ftpReq);
                        break;

                    // Rename file to comand
                    case RNTO:
                        procRenameTo(ftpReq);
                        break;

                    // Create new directory command
                    case MKD:
                    case XMKD:
                        procCreateDirectory(ftpReq);
                        break;

                    // Delete directory command
                    case RMD:
                    case XRMD:
                        procRemoveDirectory(ftpReq);
                        break;

                    // Return file size command
                    case SIZE:
                        procFileSize(ftpReq);
                        break;

                    // Return the modification date/time
                    case MDTM:
                        procGetModifyDateTime(ftpReq);
                        break;

                    // Set modify date/time command
                    case MFMT:
                        procModifyDateTime(ftpReq);
                        break;

                    // System status command
                    case SYST:
                        procSystemStatus(ftpReq);
                        break;

                    // Server status command
                    case STAT:
                        procServerStatus(ftpReq);
                        break;

                    // Help command
                    case HELP:
                        procHelp(ftpReq);
                        break;

                    // No-op command
                    case NOOP:
                        procNoop(ftpReq);
                        break;

                    // Abort command
                    case ABOR:
                        procAbort(ftpReq);
                        break;

                    // Server features command
                    case FEAT:
                        procFeatures(ftpReq);
                        break;

                    // Options command
                    case OPTS:
                        procOptions(ftpReq);
                        break;

                    // Machine listing, single folder
                    case MLST:
                        procMachineListing(ftpReq);
                        break;

                    // Machine listing, folder contents
                    case MLSD:
                        procMachineListingContents(ftpReq);
                        break;

                    // Site specific commands
                    case SITE:
                        procSite(ftpReq);
                        break;

                    // Structure command (obsolete)
                    case STRU:
                        procStructure(ftpReq);
                        break;

                    // Mode command (obsolete)
                    case MODE:
                        procMode(ftpReq);
                        break;

                    // Allocate command (obsolete)
                    case ALLO:
                        procAllocate(ftpReq);
                        break;

                    // Extended Port command
                    case EPRT:
                        procExtendedPort(ftpReq);
                        break;

                    // Extended Passive command
                    case EPSV:
                        procExtendedPassive(ftpReq);
                        break;

                    // SSL/TLS authentication
                    case AUTH:
                        procAuth(ftpReq);
                        break;

                    // Protected buffer size
                    case PBSZ:
                        procProtectedBufferSize(ftpReq);
                        break;

                    // Data channel protection level
                    case PROT:
                        procDataChannelProtection(ftpReq);
                        break;

                    // Clear command channel
                    case CCC:
                        procClearCommandChannel(ftpReq);
                        break;

                    // Unknown/unimplemented command
                    default:
                        if (ftpReq.isCommand() != FTPCommand.INVALID_CMD)
                            sendFTPResponse(502, "Command " + ftpReq.isCommand().name() + " not implemented");
                        else
                            sendFTPResponse(502, "Command not implemented");
                        break;
                }

                // Debug
                if (Debug.EnableInfo && hasDebug(DBG_TIMING)) {
                    endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;
                    if (duration > 20)
                        debugPrintln("Processed cmd " + ftpReq.isCommand().name() + " in " + duration + "ms");
                }

                // Commit/rollback a transaction that the filesystem driver may have stored in the
                // session
                endTransaction();

            } // end while state
        }
        catch (SocketException ex) {

            // DEBUG
            if (Debug.EnableWarn && hasDebug(DBG_STATE))
                debugPrintln("Socket closed by remote client");
        }
        catch (Exception ex) {

            // Output the exception details
            if (isShutdown() == false) {
                debugPrintln(ex);
            }
        }

        // Cleanup the session, make sure all resources are released
        closeSession();

        // Debug
        if (hasDebug(DBG_STATE))
            debugPrintln("Server session closed");
    }
}
