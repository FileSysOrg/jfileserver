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

package org.filesys.netbios.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.*;

import org.filesys.debug.Debug;
import org.filesys.netbios.NetBIOSName;
import org.filesys.netbios.NetBIOSNameList;
import org.filesys.netbios.NetBIOSPacket;
import org.filesys.netbios.NetworkSettings;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.server.NetworkServer;
import org.filesys.server.ServerListener;
import org.filesys.server.Version;
import org.filesys.server.config.ConfigId;
import org.filesys.server.config.ConfigurationListener;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.smb.server.SMBConfigSection;
import org.filesys.util.HexDump;
import org.filesys.util.StringList;

/**
 * NetBIOS name server class.
 *
 * @author gkspencer
 */
public class NetBIOSNameServer extends NetworkServer implements Runnable, ConfigurationListener {

    //	Server version
    private static final String ServerVersion = Version.NetBIOSServerVersion;

    //	Various NetBIOS packet sizes
    public static final int AddNameSize     = 256;
    public static final int DeleteNameSize  = 256;
    public static final int RefreshNameSize = 256;

    //	Add name thread broadcast interval and retry count
    private static final int AddNameInterval    = 2000; // ms between transmits
    private static final int AddNameRetries     = 5;    // number of broadcasts

    private static final int AddNameWINSInterval = 250; // ms between requests when using WINS

    //	Delete name interval and retry count
    private static final int DeleteNameInterval = 200;  // ms between transmits
    private static final int DeleteNameRetries  = 1;    // number of broadcasts

    //	Refresh name retry count
    public static final int RefreshNameRetries = 2;     // number of broadcasts

    // NetBIOS flags
    public static final int GroupName = 0x8000;

    //	Default time to live value for names registered by this server, in seconds
    public static final int DefaultTTL = 10800;    // 3 hours

    //	Name refresh thread wakeup interval
    public static final long NameRefreshWakeupInterval = 180000L;    //	3 minutes

    //  SMB server configuration section
    private SMBConfigSection m_smbConfig;

    //	Name transaction id
    private static int m_tranId;

    //	NetBIOS name service datagram socket
    private DatagramSocket m_socket;

    //	Shutdown flag
    private boolean m_shutdown;

    //	Local address to bind the name server to
    private InetAddress m_bindAddress;

    //	Broadcast address, if not using WINS
    private InetAddress m_bcastAddr;

    //	Port/socket to bind to
    private int m_port = RFCNetBIOSProtocol.NAMING;

    //	WINS server addresses
    private InetAddress m_winsPrimary;
    private InetAddress m_winsSecondary;

    //	Local add name listener list
    private List<AddNameListener> m_addListeners;

    //	Local name query listener list
    private List<QueryNameListener> m_queryListeners;

    //	Remote name add listener list
    private List<RemoteNameListener> m_remoteListeners;

    //	Local NetBIOS name table
    private List<NetBIOSName> m_localNames;

    //	Remote NetBIOS name table
    private Hashtable<NetBIOSName, byte[]> m_remoteNames;

    //	List of active add name requests
    private List<NetBIOSRequest> m_reqList;

    //	NetBIOS request handler and name refresh threads
    private NetBIOSRequestHandler m_reqHandler;
    private NetBIOSNameRefresh m_refreshThread;

    //	Server thread
    private Thread m_srvThread;

    //	NetBIOS request handler thread inner class
    class NetBIOSRequestHandler extends Thread {

        //	Shutdown request flag
        private boolean m_hshutdown = false;

        /**
         * Default constructor
         */
        public NetBIOSRequestHandler() {
            setDaemon(true);
            setName("NetBIOSRequest");
        }

        /**
         * Shutdown the request handler thread
         */
        public final void shutdownRequest() {
            m_hshutdown = true;

            synchronized (m_reqList) {
                m_reqList.notify();
            }
        }

        /**
         * Main thread code
         */
        public void run() {

            //	Loop until shutdown requested
            while (m_hshutdown == false) {

                try {

                    //	Wait for something to do
                    NetBIOSRequest req = null;

                    synchronized (m_reqList) {

                        //	Check if there are any requests in the queue
                        if (m_reqList.size() == 0) {

                            //	Debug
                            if (Debug.EnableInfo && hasDebug())
                                Debug.println("NetBIOS handler waiting for request ...");

                            //	Wait for some work ...
                            m_reqList.wait();
                        }

                        //	Remove a request from the queue
                        if (m_reqList.size() > 0)
                            req = m_reqList.get(0);
                        else if (m_hshutdown == true)
                            break;
                    }

                    //	Get the request retry count, for WINS only send one request
                    int reqRetry = req.getRetryCount();
                    if (hasPrimaryWINSServer())
                        reqRetry = 1;

                    //	Process the request
                    boolean txsts = true;
                    int retry = 0;

                    while (req.hasErrorStatus() == false && retry++ < reqRetry) {

                        //	Debug
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("NetBIOS handler, processing " + req);

                        //	Process the request
                        switch (req.isType()) {

                            //	Add name request
                            case ADD_NAME:

                                //	Check if a WINS server is configured
                                if (hasPrimaryWINSServer())
                                    txsts = sendAddName(req, getPrimaryWINSServer(), false);
                                else
                                    txsts = sendAddName(req, getBroadcastAddress(), true);
                                break;

                            //	Delete name request
                            case DELETE_NAME:

                                //	Check if a WINS server is configured
                                if (hasPrimaryWINSServer())
                                    txsts = sendDeleteName(req, getPrimaryWINSServer(), false);
                                else
                                    txsts = sendDeleteName(req, getBroadcastAddress(), true);
                                break;

                            //	Refresh name request
                            case REFRESH_NAME:

                                //	Check if a WINS server is configured
                                if (hasPrimaryWINSServer())
                                    txsts = sendRefreshName(req, getPrimaryWINSServer(), false);
                                else
                                    txsts = sendRefreshName(req, getBroadcastAddress(), true);
                                break;
                        }

                        //	Check if the request was successful
                        if (txsts == true && req.getRetryInterval() > 0) {

                            //	Sleep for a while
                            sleep(req.getRetryInterval());
                        }
                    }

                    //	Check if the request was successful
                    if (req.hasErrorStatus() == false) {

                        //	Debug
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("NetBIOS handler successful, " + req);

                        //	Update the name record
                        NetBIOSName nbName = req.getNetBIOSName();

                        switch (req.isType()) {

                            //	Add name request
                            case ADD_NAME:

                                //	Add the name to the list of local names
                                if (m_localNames.contains(nbName) == false)
                                    m_localNames.add(nbName);

                                //	Update the expiry time for the name
                                nbName.setExpiryTime(System.currentTimeMillis() + (nbName.getTimeToLive() * 1000L));

                                //	Inform listeners that the request was successful
                                fireAddNameEvent(nbName, NetBIOSNameEvent.Status.ADD_SUCCESS);
                                break;

                            //	Delete name request
                            case DELETE_NAME:

                                //	Remove the name from the list of local names
                                m_localNames.remove(req.getNetBIOSName());
                                break;

                            //	Refresh name registration request
                            case REFRESH_NAME:

                                //	Update the expiry time for the name
                                nbName.setExpiryTime(System.currentTimeMillis() + (nbName.getTimeToLive() * 1000L));
                                break;
                        }
                    } else {

                        //	Error occurred
                        switch (req.isType()) {

                            //	Add name request
                            case ADD_NAME:

                                //	Remove the name from the local name list
                                m_localNames.remove(req.getNetBIOSName());
                                break;
                        }
                    }

                    //	Remove the request from the queue
                    synchronized (m_reqList) {
                        m_reqList.remove(0);
                    }
                }
                catch (InterruptedException ex) {
                }

                //	Check if the request handler has been shutdown
                if (m_hshutdown == true)
                    break;
            }
        }

        /**
         * Send an add name request
         *
         * @param req   NetBIOSRequest
         * @param dest  InetAddress
         * @param bcast boolean
         * @return boolean
         */
        private final boolean sendAddName(NetBIOSRequest req, InetAddress dest, boolean bcast) {

            try {

                //  Allocate a buffer for the add name NetBIOS packet
                byte[] buf = new byte[AddNameSize];
                NetBIOSPacket addPkt = new NetBIOSPacket(buf);

                //  Build an add name packet for each IP address
                for (int i = 0; i < req.getNetBIOSName().numberOfAddresses(); i++) {

                    //	Build an add name request for the current IP address
                    int len = addPkt.buildAddNameRequest(req.getNetBIOSName(), i, req.getTransactionId());
                    if (bcast == false)
                        addPkt.setFlags(0);

                    //  Allocate the datagram packet, using the add name buffer
                    DatagramPacket pkt = new DatagramPacket(buf, len, dest, RFCNetBIOSProtocol.NAMING);

                    //	Send the add name request
                    if (m_socket != null)
                        m_socket.send(pkt);

                    //	Debug
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("  Add name " + (bcast ? "broadcast" : "WINS") + ", " + req);
                }
            }
            catch (IOException ex) {
                fireAddNameEvent(req.getNetBIOSName(), NetBIOSNameEvent.Status.ADD_IOERROR);
                req.setErrorStatus(true);
                return false;
            }

            //	Add name broadcast successful
            return true;
        }

        /**
         * Send a refresh name request
         *
         * @param req   NetBIOSRequest
         * @param dest  InetAddress
         * @param bcast boolean
         * @return boolean
         */
        private final boolean sendRefreshName(NetBIOSRequest req, InetAddress dest, boolean bcast) {

            try {

                //  Allocate a buffer for the refresh name NetBIOS packet
                byte[] buf = new byte[RefreshNameSize];
                NetBIOSPacket refreshPkt = new NetBIOSPacket(buf);

                //  Build a refresh name packet for each IP address
                for (int i = 0; i < req.getNetBIOSName().numberOfAddresses(); i++) {

                    //	Build a refresh name request for the current IP address
                    int len = refreshPkt.buildRefreshNameRequest(req.getNetBIOSName(), i, req.getTransactionId());
                    if (bcast == false)
                        refreshPkt.setFlags(0);

                    //  Allocate the datagram packet, using the refresh name buffer
                    DatagramPacket pkt = new DatagramPacket(buf, len, dest, RFCNetBIOSProtocol.NAMING);

                    //	Send the refresh name request
                    if (m_socket != null)
                        m_socket.send(pkt);

                    //	Debug
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("  Refresh name " + (bcast ? "broadcast" : "WINS") + ", " + req);
                }
            }
            catch (IOException ex) {
                req.setErrorStatus(true);
                return false;
            }

            //	Add name broadcast successful
            return true;
        }

        /**
         * Send a delete name request via a network broadcast
         *
         * @param req   NetBIOSRequest
         * @param dest  InetAddress
         * @param bcast boolean
         * @return boolean
         */
        private final boolean sendDeleteName(NetBIOSRequest req, InetAddress dest, boolean bcast) {

            try {

                //  Allocate a buffer for the delete name NetBIOS packet
                byte[] buf = new byte[DeleteNameSize];
                NetBIOSPacket delPkt = new NetBIOSPacket(buf);

                //  Build a delete name packet for each IP address
                for (int i = 0; i < req.getNetBIOSName().numberOfAddresses(); i++) {

                    //	Build an add name request for the current IP address
                    int len = delPkt.buildDeleteNameRequest(req.getNetBIOSName(), i, req.getTransactionId());
                    if (bcast == false)
                        delPkt.setFlags(0);

                    //  Allocate the datagram packet, using the add name buffer
                    DatagramPacket pkt = new DatagramPacket(buf, len, dest, RFCNetBIOSProtocol.NAMING);

                    //	Send the add name request
                    if (m_socket != null)
                        m_socket.send(pkt);

                    //	Debug
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("  Delete name " + (bcast ? "broadcast" : "WINS") + ", " + req);
                }
            }
            catch (IOException ex) {
                req.setErrorStatus(true);
                return false;
            }

            //	Delete name broadcast successful
            return true;
        }
    }

    ;

    //	NetBIOS name refresh thread inner class
    class NetBIOSNameRefresh extends Thread {

        //	Shutdown request flag
        private boolean m_hshutdown = false;

        /**
         * Default constructor
         */
        public NetBIOSNameRefresh() {
            setDaemon(true);
            setName("NetBIOSRefresh");
        }

        /**
         * Shutdown the name refresh thread
         */
        public final void shutdownRequest() {
            m_hshutdown = true;

            //	Wakeup the thread
            this.interrupt();
        }

        /**
         * Main thread code
         */
        public void run() {

            //	Loop for ever
            while (m_hshutdown == false) {

                try {

                    //	Sleep for a while
                    sleep(NameRefreshWakeupInterval);

                    //	Check if there is a shutdown pending
                    if (m_hshutdown == true)
                        break;

                    //	Debug
                    if (Debug.EnableInfo && hasDebug())
                        Debug.println("NetBIOS name refresh wakeup ...");

                    //	Check if there are any registered names that will expire in the next interval
                    synchronized (m_localNames) {

                        //	Get the current time plus the wakeup interval
                        long expireTime = System.currentTimeMillis() + NameRefreshWakeupInterval;

                        //	Loop through the local name list
                        for (int i = 0; i < m_localNames.size(); i++) {

                            //	Get a name from the list
                            NetBIOSName nbName = (NetBIOSName) m_localNames.get(i);

                            //	Check if the name has expired, or will expire before the next wakeup event
                            if (nbName.getExpiryTime() < expireTime) {

                                //	Debug
                                if (Debug.EnableInfo && hasDebug())
                                    Debug.println("Queuing name refresh for " + nbName);

                                //	Queue a refresh request for the NetBIOS name
                                NetBIOSRequest nbReq = new NetBIOSRequest(NetBIOSRequest.Type.REFRESH_NAME, nbName, getNextTransactionId());
                                nbReq.setRetryCount(RefreshNameRetries);

                                //	Queue the request
                                synchronized (m_reqList) {

                                    //	Add the request to the list
                                    m_reqList.add(nbReq);

                                    //	Wakeup the processing thread
                                    m_reqList.notify();
                                }
                            }
                        }
                    }
                }
                catch (Exception ex) {

                    //	Debug
                    if (Debug.EnableError && hasDebug()) {
                        Debug.println("NetBIOS Name refresh thread exception");
                        Debug.println(ex);
                    }
                }
            }
        }
    }

    ;

    /**
     * Default constructor
     *
     * @param config ServerConfiguration
     * @throws SocketException If a network setup error occurs
     */
    public NetBIOSNameServer(ServerConfiguration config)
            throws SocketException {

        super("NetBIOS", config);

        //	Perform common constructor code
        commonConstructor();
    }

    /**
     * Common constructor code
     *
     * @throws SocketException If a network setup error occurs
     */
    private final void commonConstructor()
            throws SocketException {

        //	Add the NetBIOS name server as a configuration change listener of the server configuration
        getConfiguration().addListener(this);

        //	Set the server version
        setVersion(ServerVersion);

        //  Find the SMB server configuration
        m_smbConfig = (SMBConfigSection) getConfiguration().getConfigSection(SMBConfigSection.SectionName);
        if (m_smbConfig != null) {

            //  Allocate the local and remote name tables
            m_localNames = new ArrayList<NetBIOSName>();
            m_remoteNames = new Hashtable<NetBIOSName, byte[]>();

            //	Check if NetBIOS name server debug output is enabled
            if (getSMBConfiguration().hasNetBIOSDebug())
                setDebug(true);

            //	Set the local address to bind the server to, and server port
            setBindAddress(getSMBConfiguration().getNetBIOSBindAddress());
            setServerPort(getSMBConfiguration().getNameServerPort());

            //	Copy the WINS server addresses, if set
            setPrimaryWINSServer(getSMBConfiguration().getPrimaryWINSServer());
            setSecondaryWINSServer(getSMBConfiguration().getSecondaryWINSServer());

            //	Check if WINS is not enabled, use broadcasts instead
            if (hasPrimaryWINSServer() == false) {

                try {
                    m_bcastAddr = InetAddress.getByName(getSMBConfiguration().getBroadcastMask());
                }
                catch (Exception ex) {
                }
            }
        } else
            setEnabled(false);
    }

    /**
     * Return the local address the server binds to, or null if all local addresses
     * are used.
     *
     * @return java.net.InetAddress
     */
    public final InetAddress getBindAddress() {
        return m_bindAddress;
    }

    /**
     * Return the next available transaction id for outgoing NetBIOS packets.
     *
     * @return int
     */
    protected final synchronized int getNextTransactionId() {
        return m_tranId++;
    }

    /**
     * Return the port/socket that the server is bound to.
     *
     * @return int
     */
    public final int getPort() {
        return m_port;
    }

    /**
     * Determine if the server binds to a particulat local address, or all addresses
     *
     * @return boolean
     */
    public final boolean hasBindAddress() {
        return m_bindAddress != null ? true : false;
    }

    /**
     * Return the SMB server configuration
     *
     * @return SMBConfigSection
     */
    private final SMBConfigSection getSMBConfiguration() {
        return m_smbConfig;
    }

    /**
     * Return the remote name table
     *
     * @return Hashtable
     */
    public final Hashtable getNameTable() {
        return m_remoteNames;
    }

    /**
     * Return the broadcast address, if WINS is disabled
     *
     * @return InetAddress
     */
    public final InetAddress getBroadcastAddress() {
        return m_bcastAddr;
    }

    /**
     * Determine if the primary WINS server address has been set
     *
     * @return boolean
     */
    public final boolean hasPrimaryWINSServer() {
        return m_winsPrimary != null ? true : false;
    }

    /**
     * Return the primary WINS server address
     *
     * @return InetAddress
     */
    public final InetAddress getPrimaryWINSServer() {
        return m_winsPrimary;
    }

    /**
     * Determine if the secondary WINS server address has been set
     *
     * @return boolean
     */
    public final boolean hasSecondaryWINSServer() {
        return m_winsSecondary != null ? true : false;
    }

    /**
     * Return the secondary WINS server address
     *
     * @return InetAddress
     */
    public final InetAddress getSecondaryWINSServer() {
        return m_winsSecondary;
    }

    /**
     * Add a NetBIOS name.
     *
     * @param name NetBIOS name to be added
     * @throws java.io.IOException I/O error occurred.
     */
    public final synchronized void AddName(NetBIOSName name)
            throws IOException {

        //  Check if the NetBIOS name socket has been initialized
        if (m_socket == null)
            throw new IOException("NetBIOS name socket not initialized");

        //	Create an add name request and add to the request list
        NetBIOSRequest nbReq = new NetBIOSRequest(NetBIOSRequest.Type.ADD_NAME, name, getNextTransactionId());

        //	Set the retry interval
        if (hasPrimaryWINSServer())
            nbReq.setRetryInterval(AddNameWINSInterval);
        else
            nbReq.setRetryInterval(AddNameInterval);

        //	Add the name to the local name list
        m_localNames.add(name);

        //	Queue the request
        synchronized (m_reqList) {

            //	Add the request to the list
            m_reqList.add(nbReq);

            //	Wakeup the processing thread
            m_reqList.notify();
        }
    }

    /**
     * Delete a NetBIOS name.
     *
     * @param name NetBIOS name to be deleted
     * @throws java.io.IOException I/O error occurred.
     */
    public final synchronized void DeleteName(NetBIOSName name)
            throws IOException {

        //  Check if the NetBIOS name socket has been initialized
        if (m_socket == null)
            throw new IOException("NetBIOS name socket not initialized");

        //	Create a delete name request and add to the request list
        NetBIOSRequest nbReq = new NetBIOSRequest(NetBIOSRequest.Type.DELETE_NAME, name, getNextTransactionId(), DeleteNameRetries);
        nbReq.setRetryInterval(DeleteNameInterval);

        synchronized (m_reqList) {

            //	Add the request to the list
            m_reqList.add(nbReq);

            //	Wakeup the processing thread
            m_reqList.notify();
        }
    }

    /**
     * Add a local add name listener to the NetBIOS name server.
     *
     * @param l AddNameListener
     */
    public final synchronized void addAddNameListener(AddNameListener l) {

        //  Check if the add name listener list is allocated
        if (m_addListeners == null)
            m_addListeners = new ArrayList<AddNameListener>();
        m_addListeners.add(l);
    }

    /**
     * Add a query name listener to the NetBIOS name server.
     *
     * @param l QueryNameListener
     */
    public final synchronized void addQueryListener(QueryNameListener l) {

        //  Check if the query name listener list is allocated
        if (m_queryListeners == null)
            m_queryListeners = new ArrayList<QueryNameListener>();
        m_queryListeners.add(l);
    }

    /**
     * Add a remote name listener to the NetBIOS name server.
     *
     * @param l RemoteNameListener
     */
    public final synchronized void addRemoteListener(RemoteNameListener l) {

        //  Check if the remote name listener list is allocated
        if (m_remoteListeners == null)
            m_remoteListeners = new ArrayList<RemoteNameListener>();
        m_remoteListeners.add(l);
    }

    /**
     * Trigger an add name event to all registered listeners.
     *
     * @param name NetBIOSName
     * @param sts  NetBIOSNameEvent.Status
     */
    protected final synchronized void fireAddNameEvent(NetBIOSName name, NetBIOSNameEvent.Status sts) {

        //  Check if there are any listeners
        if (m_addListeners == null || m_addListeners.size() == 0)
            return;

        //  Create a NetBIOS name event
        NetBIOSNameEvent evt = new NetBIOSNameEvent(name, sts);

        //  Inform all registered listeners
        for (int i = 0; i < m_addListeners.size(); i++) {
            AddNameListener addListener = m_addListeners.get(i);
            addListener.netbiosNameAdded(evt);
        }
    }

    /**
     * Trigger an query name event to all registered listeners.
     *
     * @param name NetBIOSName
     * @param addr InetAddress
     */
    protected final synchronized void fireQueryNameEvent(NetBIOSName name, InetAddress addr) {

        //  Check if there are any listeners
        if (m_queryListeners == null || m_queryListeners.size() == 0)
            return;

        //  Create a NetBIOS name event
        NetBIOSNameEvent evt = new NetBIOSNameEvent(name, NetBIOSNameEvent.Status.QUERY_NAME);

        //  Inform all registered listeners
        for (int i = 0; i < m_queryListeners.size(); i++) {
            QueryNameListener queryListener = m_queryListeners.get(i);
            queryListener.netbiosNameQuery(evt, addr);
        }
    }

    /**
     * Trigger a name register event to all registered listeners.
     *
     * @param name NetBIOSName
     * @param addr InetAddress
     */
    protected final synchronized void fireNameRegisterEvent(NetBIOSName name, InetAddress addr) {

        //  Check if there are any listeners
        if (m_remoteListeners == null || m_remoteListeners.size() == 0)
            return;

        //  Create a NetBIOS name event
        NetBIOSNameEvent evt = new NetBIOSNameEvent(name, NetBIOSNameEvent.Status.REGISTER_NAME);

        //  Inform all registered listeners
        for (int i = 0; i < m_remoteListeners.size(); i++) {
            RemoteNameListener nameListener = m_remoteListeners.get(i);
            nameListener.netbiosAddRemoteName(evt, addr);
        }
    }

    /**
     * Trigger a name release event to all registered listeners.
     *
     * @param name NetBIOSName
     * @param addr InetAddress
     */
    protected final synchronized void fireNameReleaseEvent(NetBIOSName name, InetAddress addr) {

        //  Check if there are any listeners
        if (m_remoteListeners == null || m_remoteListeners.size() == 0)
            return;

        //  Create a NetBIOS name event
        NetBIOSNameEvent evt = new NetBIOSNameEvent(name, NetBIOSNameEvent.Status.REGISTER_NAME);

        //  Inform all registered listeners
        for (int i = 0; i < m_remoteListeners.size(); i++) {
            RemoteNameListener nameListener = m_remoteListeners.get(i);
            nameListener.netbiosReleaseRemoteName(evt, addr);
        }
    }

    /**
     * Open the server socket
     *
     * @exception SocketException Socket error
     */
    private void openSocket()
            throws java.net.SocketException {

        //	Check if the server should bind to a particular local address, or all addresses
        if (hasBindAddress())
            m_socket = new DatagramSocket(getPort(), m_bindAddress);
        else
            m_socket = new DatagramSocket(getPort());
    }

    /**
     * Process a NetBIOS name query.
     *
     * @param pkt      NetBIOSPacket
     * @param fromAddr InetAddress
     * @param fromPort int
     */
    protected final void processNameQuery(NetBIOSPacket pkt, InetAddress fromAddr, int fromPort) {

        //  Check that the name query packet is valid
        if (pkt.getQuestionCount() != 1)
            return;

        //  Get the name that is being queried
        String searchName = pkt.getQuestionName();
        char nameType = searchName.charAt(15);

        int len = 0;
        while (len <= 14 && searchName.charAt(len) != ' ' && searchName.charAt(len) != 0)
            len++;
        searchName = searchName.substring(0, len);

        //  Check if this is an adapter status request
        if (searchName.equals(NetBIOSName.AdapterStatusName)) {

            // Process the adapter status request
            processAdapterStatus(pkt, fromAddr, fromPort);
            return;
        }

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("%% Query name=" + searchName + ", type=" + NetBIOSName.TypeAsString(nameType) + ", len=" + len);

        //  Search for the name in the local name table
        Iterator<NetBIOSName> enm = m_localNames.iterator();
        NetBIOSName nbName = null;
        boolean foundName = false;

        while (enm.hasNext() && foundName == false) {

            //  Get the current NetBIOS name item from the local name table
            nbName = enm.next();

            //  Debug
            if (Debug.EnableInfo && hasDebug())
                Debug.println("NetBIOS Name - " + nbName.getName() + ", len=" + nbName.getName().length() + ",type=" + NetBIOSName.TypeAsString(nbName.getType()));

            //  Check if the name matches the query name
            if (nbName.getType() == nameType && nbName.getName().compareTo(searchName) == 0)
                foundName = true;
        }

        //  Check if we found a matching name
        if (foundName == true) {

            //  Debug
            if (Debug.EnableInfo && hasDebug())
                Debug.println("%% Found name " + searchName + " in local name table : " + nbName.toString());

            //  Build the name query response
            int pktLen = pkt.buildNameQueryResponse(nbName);

            //  Debug
            if (Debug.EnableInfo && hasDebug()) {
                Debug.println("%% NetBIOS Reply to " + fromAddr.getHostAddress() + " :-");
                pkt.DumpPacket(false);
            }

            //  Send the reply packet
            try {

                //  Send the name query reply
                sendPacket(pkt, pktLen, fromAddr, fromPort);
            }
            catch (java.io.IOException ex) {
                Debug.println(ex);
            }

            //  Inform listeners of the name query
            fireQueryNameEvent(nbName, fromAddr);
        } else {

            //  Debug
            if (Debug.EnableInfo && hasDebug())
                Debug.println("%% Failed to find match for name " + searchName);
        }
    }

    /**
     * Process a NetBIOS name register request.
     *
     * @param pkt      NetBIOSPacket
     * @param fromAddr InetAddress
     * @param fromPort int
     */
    protected final void processNameRegister(NetBIOSPacket pkt, InetAddress fromAddr, int fromPort) {

        //  Check that the name register packet is valid
        if (pkt.getQuestionCount() != 1)
            return;

        //  Get the name that is being registered
        String regName = pkt.getQuestionName();
        char nameType = regName.charAt(15);

        int len = 0;
        while (len <= 14 && regName.charAt(len) != ' ')
            len++;
        regName = regName.substring(0, len);

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("%% Register name=" + regName + ", type=" + NetBIOSName.TypeAsString(nameType) + ", len=" + len);

        //	Create a NetBIOS name for the host
        byte[] hostIP = fromAddr.getAddress();
        NetBIOSName nbName = new NetBIOSName(regName, nameType, false, hostIP);

        //	Add the name to the remote host name table
        m_remoteNames.put(nbName, hostIP);

        //	Inform listeners that a new remote name has been added
        fireNameRegisterEvent(nbName, fromAddr);

        //	Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("%% Added remote name " + nbName.toString() + " to remote names table");
    }

    /**
     * Process a NetBIOS name release.
     *
     * @param pkt      NetBIOSPacket
     * @param fromAddr InetAddress
     * @param fromPort int
     */
    protected final void processNameRelease(NetBIOSPacket pkt, InetAddress fromAddr, int fromPort) {

        //  Check that the name release packet is valid
        if (pkt.getQuestionCount() != 1)
            return;

        //  Get the name that is being released
        String regName = pkt.getQuestionName();
        char nameType = regName.charAt(15);

        int len = 0;
        while (len <= 14 && regName.charAt(len) != ' ')
            len++;
        regName = regName.substring(0, len);

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("%% Release name=" + regName + ", type=" + NetBIOSName.TypeAsString(nameType) + ", len=" + len);

        //	Create a NetBIOS name for the host
        byte[] hostIP = fromAddr.getAddress();
        NetBIOSName nbName = new NetBIOSName(regName, nameType, false, hostIP);

        //	Remove the name from the remote host name table
        m_remoteNames.remove(nbName);

        //	Inform listeners that a remote name has been released
        fireNameReleaseEvent(nbName, fromAddr);

        //	Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("%% Released remote name " + nbName.toString() + " from remote names table");
    }

    /**
     * Process a NetBIOS query response.
     *
     * @param pkt      NetBIOSPacket
     * @param fromAddr InetAddress
     * @param fromPort int
     */
    protected final void processQueryResponse(NetBIOSPacket pkt, InetAddress fromAddr, int fromPort) {
    }

    /**
     * Process a NetBIOS name register response.
     *
     * @param pkt      NetBIOSPacket
     * @param fromAddr InetAddress
     * @param fromPort int
     */
    protected final void processRegisterResponse(NetBIOSPacket pkt, InetAddress fromAddr, int fromPort) {

        //	Check if there are any reply name details
        if (pkt.getAnswerCount() == 0)
            return;

        //	Get the details from the response packet
        int tranId = pkt.getTransactionId();

        //	Find the matching request
        NetBIOSRequest req = findRequest(tranId);
        if (req == null)
            return;

        //	Get the error code from the response
        int errCode = pkt.getResultCode();

        if (errCode != 0) {

            //	Mark the request error
            req.setErrorStatus(true);

            //	Get the name details
            String regName = pkt.getAnswerName();
            char nameType = regName.charAt(15);

            int len = 0;
            while (len <= 14 && regName.charAt(len) != ' ')
                len++;
            regName = regName.substring(0, len);

            //	Create a NetBIOS name for the host
            byte[] hostIP = fromAddr.getAddress();
            NetBIOSName nbName = new NetBIOSName(regName, nameType, false, hostIP);

            //  Debug
            if (Debug.EnableInfo && hasDebug())
                Debug.println("%% Negative Name Registration name=" + nbName);

            //	Inform listeners of the add name failure
            fireAddNameEvent(req.getNetBIOSName(), NetBIOSNameEvent.Status.ADD_FAILED);
        } else {

            //  Debug
            if (Debug.EnableInfo && hasDebug())
                Debug.println("%% Name Registration Successful name=" + req.getNetBIOSName().getName());

            //	Inform listeners that the add name was successful
            fireAddNameEvent(req.getNetBIOSName(), NetBIOSNameEvent.Status.ADD_SUCCESS);
        }
    }

    /**
     * Process a NetBIOS name release response.
     *
     * @param pkt      NetBIOSPacket
     * @param fromAddr InetAddress
     * @param fromPort int
     */
    protected final void processReleaseResponse(NetBIOSPacket pkt, InetAddress fromAddr, int fromPort) {
    }

    /**
     * Process a NetBIOS WACK.
     *
     * @param pkt      NetBIOSPacket
     * @param fromAddr InetAddress
     * @param fromPort int
     */
    protected final void processWack(NetBIOSPacket pkt, InetAddress fromAddr, int fromPort) {
    }

    /**
     * Process an adapter status request
     *
     * @param pkt      NetBIOSPacket
     * @param fromAddr InetAddress
     * @param fromPort int
     */
    protected final void processAdapterStatus(NetBIOSPacket pkt, InetAddress fromAddr, int fromPort) {

        //  Debug
        if (Debug.EnableInfo && hasDebug())
            Debug.println("%% Adapter status request");

        //  Build the local name list
        NetBIOSNameList nameList = new NetBIOSNameList();
        for (int i = 0; i < m_localNames.size(); i++)
            nameList.addName(m_localNames.get(i));

        //  Build the name query response
        int pktLen = pkt.buildAdapterStatusResponse(nameList, hasPrimaryWINSServer() ? NetBIOSPacket.NAME_TYPE_PNODE : NetBIOSPacket.NAME_TYPE_BNODE);

        //  Debug
        if (Debug.EnableInfo && hasDebug()) {
            Debug.println("%% NetBIOS Reply to " + fromAddr.getHostAddress() + " :-");
            pkt.DumpPacket(false);
        }

        //  Send the reply packet
        try {

            //  Send the name query reply
            sendPacket(pkt, pktLen, fromAddr, fromPort);
        }
        catch (java.io.IOException ex) {
            Debug.println(ex);
        }
    }

    /**
     * Remove a local add name listener from the NetBIOS name server.
     *
     * @param l AddNameListener
     */
    public final synchronized void removeAddNameListener(AddNameListener l) {

        //  Check if the listener list is valid
        if (m_addListeners == null)
            return;
        m_addListeners.remove(l);
    }

    /**
     * Remove a query name listner from the NetBIOS name server.
     *
     * @param l QueryNameListener
     */
    public final synchronized void removeQueryNameListener(QueryNameListener l) {

        //  Check if the listener list is valid
        if (m_queryListeners == null)
            return;
        m_queryListeners.remove(l);
    }

    /**
     * Remove a remote name listener from the NetBIOS name server.
     *
     * @param l RemoteNameListener
     */
    public final synchronized void removeRemoteListener(RemoteNameListener l) {

        //  Check if the listener list is valid
        if (m_remoteListeners == null)
            return;
        m_remoteListeners.remove(l);
    }

    /**
     * Run the NetBIOS name server.
     */
    public void run() {

        //  Initialize the NetBIOS name socket
        NetBIOSPacket nbPkt = null;
        DatagramPacket pkt = null;
        byte[] buf = null;

        try {

            //  Get a list of the local IP addresses
            Vector ipList = new Vector();

            if (hasBindAddress()) {

                //	Use the specified bind address
                ipList.addElement(getBindAddress().getAddress());
            } else {

                //	Get a list of all the local addresses
                InetAddress[] addrs = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());

                for (int i = 0; i < addrs.length; i++) {

                    //	Check for a valid address, filter out '127.0.0.1' and '0.0.0.0' addresses
                    if (addrs[i].getHostAddress().equals("127.0.0.1") == false &&
                            addrs[i].getHostAddress().equals("0.0.0.0") == false)
                        ipList.addElement(addrs[i].getAddress());
                }

                // If no valid addresses were found use the network interface list to find the local server address(es)
                if (ipList.size() == 0) {

                    // Enumerate the network adapter list
                    Enumeration niEnum = NetworkInterface.getNetworkInterfaces();

                    if (niEnum != null) {

                        while (niEnum.hasMoreElements()) {

                            // Get the current network interface
                            NetworkInterface ni = (NetworkInterface) niEnum.nextElement();

                            // Enumerate the addresses for the network adapter
                            Enumeration niAddrs = ni.getInetAddresses();
                            if (niAddrs != null) {
                                // Check for any valid addresses
                                while (niAddrs.hasMoreElements()) {
                                    InetAddress curAddr = (InetAddress) niAddrs.nextElement();

                                    if (curAddr.getHostAddress().equals("127.0.0.1") == false &&
                                            curAddr.getHostAddress().equals("0.0.0.0") == false)
                                        ipList.add(curAddr.getAddress());
                                }
                            }
                        }

                        // DEBUG
                        if (Debug.EnableInfo && ipList.size() > 0 && hasDebug())
                            Debug.println("Found " + ipList.size() + " addresses using interface list");
                    }
                }
            }

            //  Initialize the NetBIOS name socket
            if (m_socket == null)
                openSocket();

            //	Allocate the NetBIOS request queue, and add the server name/alias name requests
            m_reqList = new Vector<NetBIOSRequest>();

            //	Add the server name requests to the queue
            AddName(new NetBIOSName(m_smbConfig.getServerName(), NetBIOSName.FileServer, false, ipList, DefaultTTL));
            AddName(new NetBIOSName(m_smbConfig.getServerName(), NetBIOSName.WorkStation, false, ipList, DefaultTTL));

            if (getSMBConfiguration().getDomainName() != null)
                AddName(new NetBIOSName(m_smbConfig.getDomainName(), NetBIOSName.Domain, true, ipList, DefaultTTL));

            //	Check if the server has alias names configured, if so then also add those names
            if (getSMBConfiguration().hasAliasNames()) {

                //	Add the alias names to the network
                StringList names = getSMBConfiguration().getAliasNames();

                for (int i = 0; i < names.numberOfStrings(); i++) {

                    //	Get the current alias name
                    String alias = (String) names.getStringAt(i);

                    //	Add the name to the network
                    AddName(new NetBIOSName(alias, NetBIOSName.FileServer, false, ipList, DefaultTTL));
                    AddName(new NetBIOSName(alias, NetBIOSName.WorkStation, false, ipList, DefaultTTL));
                }
            }

            //	Create the request handler thread
            m_reqHandler = new NetBIOSRequestHandler();
            m_reqHandler.start();

            //	Create the name refresh thread
            m_refreshThread = new NetBIOSNameRefresh();
            m_refreshThread.start();

            //  Allocate a receive buffer, NetBIOS packet and datagram packet
            buf = new byte[1024];
            nbPkt = new NetBIOSPacket(buf);
            pkt = new DatagramPacket(buf, buf.length);
        }
        catch (Exception ex) {
            if (Debug.EnableError && hasDebug())
                Debug.println("NetBIOSNameServer setup error:" + ex.toString());

            //	Save the exception and inform listeners of the error
            setException(ex);
            fireServerEvent(ServerListener.ServerError);
        }

        //	If there are any pending requests in the queue then wakeup the request handler thread
        if (m_reqList != null && m_reqList.size() > 0) {
            synchronized (m_reqList) {
                m_reqList.notify();
            }
        }

        //	Indicate that the server is active
        setActive(true);
        fireServerEvent(ServerListener.ServerActive);

        //  Loop
        if (hasException() == false) {

            //	Clear the shutdown request flag
            m_shutdown = false;

            while (m_shutdown == false) {

                try {

                    //  Wait for an incoming packet ....
                    m_socket.receive(pkt);

                    //  Debug
                    if (Debug.EnableInfo && hasDebug()) {

                        //  Dump the received packet
                        Debug.println("%% NetBIOS Name Server Rx Datagram from " + pkt.getAddress().getHostAddress() + ", opCode=" + nbPkt.getOpcode());

                        //  Dump the raw packet details
                        HexDump.Dump(buf, pkt.getLength(), 0, Debug.getDebugInterface());
                    }

                    //	Check for a zero length datagram
                    if (pkt.getLength() == 0)
                        continue;

                    //  Get the incoming NetBIOS packet opcode
                    InetAddress fromAddr = pkt.getAddress();
                    int fromPort = pkt.getPort();

                    switch (nbPkt.getOpcode()) {

                        //  Name query
                        case NetBIOSPacket.NAME_QUERY:
                            processNameQuery(nbPkt, fromAddr, fromPort);
                            break;

                        //  Name register
                        case NetBIOSPacket.NAME_REGISTER:
                            processNameRegister(nbPkt, fromAddr, fromPort);
                            break;

                        //  Name release
                        case NetBIOSPacket.NAME_RELEASE:
                            processNameRelease(nbPkt, fromAddr, fromPort);
                            break;

                        //  Name register response
                        case NetBIOSPacket.RESP_REGISTER:
                            processRegisterResponse(nbPkt, fromAddr, fromPort);
                            break;

                        //  Name query response
                        case NetBIOSPacket.RESP_QUERY:
                            processQueryResponse(nbPkt, fromAddr, fromPort);
                            break;

                        //  Name release response
                        case NetBIOSPacket.RESP_RELEASE:
                            processReleaseResponse(nbPkt, fromAddr, fromPort);
                            break;

                        //  WACK
                        case NetBIOSPacket.WACK:
                            processWack(nbPkt, fromAddr, fromPort);
                            break;

                        // Refresh
                        case NetBIOSPacket.REFRESH:
                            processNameRegister(nbPkt, fromAddr, fromPort);
                            break;

                        // Multi-homed name registration
                        case NetBIOSPacket.NAME_REGISTER_MULTI:
                            processNameRegister(nbPkt, fromAddr, fromPort);
                            break;

                        //	Unknown opcode
                        default:
                            if (Debug.EnableError && hasDebug()) {
                                int opCode = nbPkt.getOpcode();
                                Debug.println("Unknown OpCode 0x" + Integer.toHexString(opCode) + ", " + opCode);
                            }
                            break;
                    }
                }
                catch (Exception ex) {
                    if (hasDebug()) {
                        Debug.println("NetBIOSNameServer error");
                        Debug.println(ex);
                    }

                    //	Store the error and inform listeners of the server error. If the server is shutting down we expect a
                    //	socket error as the socket is closed by the shutdown thread and the pending read request generates an
                    //	exception.
                    if (m_shutdown == false) {
                        setException(ex);
                        fireServerEvent(ServerListener.ServerError);
                    }
                }
            }
        }

        //	Indicate that the server is closed
        setActive(false);
        fireServerEvent(ServerListener.ServerShutdown);
    }

    /**
     * Send a packet via the NetBIOS naming datagram socket.
     *
     * @param nbpkt NetBIOSPacket
     * @param len   int
     * @throws java.io.IOException The exception description.
     */
    protected final void sendPacket(NetBIOSPacket nbpkt, int len)
            throws java.io.IOException {

        //  Allocate the datagram packet, using the add name buffer
        DatagramPacket pkt = new DatagramPacket(nbpkt.getBuffer(), len, NetworkSettings.getBroadcastAddress(), RFCNetBIOSProtocol.NAMING);

        //  Send the datagram packet
        m_socket.send(pkt);
    }

    /**
     * Send a packet via the NetBIOS naming datagram socket.
     *
     * @param nbpkt     NetBIOSPacket
     * @param len       int
     * @param replyAddr InetAddress
     * @param replyPort int
     * @throws java.io.IOException The exception description.
     */
    protected final void sendPacket(NetBIOSPacket nbpkt, int len, InetAddress replyAddr, int replyPort)
            throws java.io.IOException {

        //  Allocate the datagram packet, using the add name buffer
        DatagramPacket pkt = new DatagramPacket(nbpkt.getBuffer(), len, replyAddr, replyPort);

        //  Send the datagram packet
        m_socket.send(pkt);
    }

    /**
     * Set the local address that the server should bind to
     *
     * @param addr java.net.InetAddress
     */
    public final void setBindAddress(InetAddress addr) {
        m_bindAddress = addr;
    }

    /**
     * Set the server port
     *
     * @param port int
     */
    public final void setServerPort(int port) {
        m_port = port;
    }

    /**
     * Set the primary WINS server address
     *
     * @param addr InetAddress
     */
    public final void setPrimaryWINSServer(InetAddress addr) {
        m_winsPrimary = addr;
    }

    /**
     * Set the secondary WINS server address
     *
     * @param addr InetAddress
     */
    public final void setSecondaryWINSServer(InetAddress addr) {
        m_winsSecondary = addr;
    }

    /**
     * Find the NetBIOS request with the specified transation id
     *
     * @param id int
     * @return NetBIOSRequest
     */
    private final NetBIOSRequest findRequest(int id) {

        //	Check if the request list is valid
        if (m_reqList == null)
            return null;

        //	Need to lock access to the request list
        NetBIOSRequest req = null;

        synchronized (m_reqList) {

            //	Search for the required request
            int idx = 0;

            while (req == null && idx < m_reqList.size()) {

                //	Get the current request and check if it is the required request
                NetBIOSRequest curReq = (NetBIOSRequest) m_reqList.get(idx++);
                if (curReq.getTransactionId() == id)
                    req = curReq;
            }
        }

        //	Return the request, or null if not found
        return req;
    }

    /**
     * Shutdown the NetBIOS name server
     *
     * @param immediate boolean
     */
    public void shutdownServer(boolean immediate) {

        //	Close the name refresh thread
        try {

            if (m_refreshThread != null) {
                m_refreshThread.shutdownRequest();
            }
        }
        catch (Exception ex) {

            //	Debug
            if (Debug.EnableError)
                Debug.println(ex);
        }

        //	If the shutdown is not immediate then release all of the names registered by this server
        if (isActive() && immediate == false) {

            //	Release all local names
            for (int i = 0; i < m_localNames.size(); i++) {

                //	Get the current name details
                NetBIOSName nbName = (NetBIOSName) m_localNames.get(i);

                //	Queue a delete name request
                try {
                    DeleteName(nbName);
                }
                catch (IOException ex) {
                    Debug.println(ex);
                }
            }

            //	Wait for the request handler thread to process the delete name requests
            while (m_reqList.size() > 0) {
                try {
                    Thread.sleep(100);
                }
                catch (InterruptedException ex) {
                }
            }
        }

        //	Close the request handler thread
        try {

            //	Close the request handler thread
            if (m_reqHandler != null) {
                m_reqHandler.shutdownRequest();
                m_reqHandler.join(1000);
                m_reqHandler = null;
            }
        }
        catch (Exception ex) {

            //	Debug

            if (Debug.EnableError)
                Debug.println(ex);
        }

        //	Indicate that the server is closing
        m_shutdown = true;

        try {

            //	Close the server socket so that any pending receive is cancelled
            if (m_socket != null) {

                //	Send a dummy packet to release the receive on the datagram socket, then close the socket
//				m_socket.send(new DatagramPacket(new byte[0], 0, m_socket.getLocalAddress(), getPort()));
                try {
                    m_socket.close();
                }
                catch (Exception ex) {
                }
                m_socket = null;
            }
        }
        catch (Exception ex) {
            Debug.println(ex);
        }

        //	Fire a shutdown notification event
        fireServerEvent(ServerListener.ServerShutdown);
    }

    /**
     * Start the NetBIOS name server is a seperate thread
     */
    public void startServer() {

        //	Create a seperate thread to run the NetBIOS name server
        m_srvThread = new Thread(this);
        m_srvThread.setName("NetBIOS Name Server");
        m_srvThread.setDaemon(true);

        m_srvThread.start();

        //	Fire a server startup event
        fireServerEvent(ServerListener.ServerStartup);
    }


    /**
     * Validate configuration changes that are relevant to the NetBIOS name server
     *
     * @param id     int
     * @param config ServerConfiguration
     * @param newVal Object
     * @return int
     * @exception InvalidConfigurationException Error changing the configuration
     */
    public int configurationChanged(int id, ServerConfiguration config, Object newVal)
            throws InvalidConfigurationException {

        int sts = StsIgnored;

        try {

            //	Check if the configuration change affects the NetBIOS name server
            switch (id) {

                //	Server enable/disable
                case ConfigId.SMBNetBIOSEnable:

                    //	Check if the server is active
                    Boolean enaSMB = (Boolean) newVal;

                    if (isActive() && enaSMB.booleanValue() == false) {

                        //	Shutdown the server
                        shutdownServer(false);
                    } else if (isActive() == false && enaSMB.booleanValue() == true) {

                        //	Start the server
                        startServer();
                    }

                    //	Indicate that the setting was accepted
                    sts = StsAccepted;
                    break;

                //	Debug enable
                case ConfigId.NetBIOSDebugEnable:

                    //	Toggle the debug setting
                    Boolean b = (Boolean) newVal;
                    setDebug(b.booleanValue());

                    sts = StsAccepted;
                    break;

                //	Settings that require a restart
                case ConfigId.NetBIOSNamePort:
                case ConfigId.NetBIOSSessionPort:
                case ConfigId.NetBIOSBindAddress:
                case ConfigId.SMBBroadcastMask:
                    sts = StsRestartRequired;
                    break;
            }
        }
        catch (Exception ex) {
            throw new InvalidConfigurationException("NetBIOS Server configuration error", ex);
        }

        //	Return the status
        return sts;
    }
}
