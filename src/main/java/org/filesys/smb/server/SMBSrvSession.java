/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) GK Spencer
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

package org.filesys.smb.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;

import org.filesys.debug.Debug;
import org.filesys.netbios.NetBIOSException;
import org.filesys.netbios.NetBIOSName;
import org.filesys.netbios.NetBIOSSession;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.server.SrvSession;
import org.filesys.server.SrvSessionList;
import org.filesys.server.auth.AuthenticatorException;
import org.filesys.server.filesys.*;
import org.filesys.server.thread.ThreadRequestPool;
import org.filesys.smb.*;
import org.filesys.smb.server.notify.NotifyRequest;
import org.filesys.smb.server.notify.NotifyRequestList;
import org.filesys.util.HexDump;
import org.filesys.util.StringList;

/**
 * <p>
 * The SMB server creates a server session object for each incoming session request.
 * 
 * <p>
 * The server session holds the context of a particular session, including the list of open files
 * and active searches.
 * 
 * @author gkspencer
 */
public class SMBSrvSession extends SrvSession implements Runnable {

	// Define the default receive buffer size to allocate.
	public static final int DefaultBufferSize = 0x010000 + RFCNetBIOSProtocol.HEADER_LEN;
	public static final int LanManBufferSize = 8192;

	// Maximum multiplexed packets allowed (client can send up to this many SMBs before waiting for
	// a response)
	//
	// Setting NTMaxMultiplexed to one will disable asynchronous notifications on the client
	public static final int LanManMaxMultiplexed = 1;
	public static final int NTMaxMultiplexed = 4;

	// Maximum number of virtual circuits
	private static final int MaxVirtualCircuits = 0;

	// Debug flag values
	public static final int DBG_PKTTYPE 	= 0x00000001; // Received packet type
	public static final int DBG_STATE 		= 0x00000002; // Session state changes
	public static final int DBG_RXDATA 		= 0x00000004; // Received data
	public static final int DBG_TXDATA 		= 0x00000008; // Transmit data
	public static final int DBG_DUMPDATA 	= 0x00000010; // Dump data packets
	public static final int DBG_NEGOTIATE 	= 0x00000020; // Protocol negotiate phase
	public static final int DBG_TREE 		= 0x00000040; // Tree connection/disconnection
	public static final int DBG_SEARCH 		= 0x00000080; // File/directory search
	public static final int DBG_INFO 		= 0x00000100; // Information requests
	public static final int DBG_FILE 		= 0x00000200; // File open/close/info
	public static final int DBG_FILEIO 		= 0x00000400; // File read/write
	public static final int DBG_TRAN 		= 0x00000800; // Transactions
	public static final int DBG_ECHO 		= 0x00001000; // Echo requests
	public static final int DBG_ERROR 		= 0x00002000; // Errors
	public static final int DBG_IPC 		= 0x00004000; // IPC$ requests
	public static final int DBG_LOCK 		= 0x00008000; // Lock/unlock requests
	public static final int DBG_DCERPC 		= 0x00010000; // DCE/RPC
	public static final int DBG_STATECACHE 	= 0x00020000; // File state cache
	public static final int DBG_TIMING 		= 0x00040000; // Time packet processing
	public static final int DBG_NOTIFY 		= 0x00080000; // Asynchronous change notification
	public static final int DBG_STREAMS 	= 0x00100000; // NTFS streams
	public static final int DBG_SOCKET 		= 0x00200000; // NetBIOS/native SMB socket connections
	public static final int DBG_PKTPOOL     = 0x00400000; // Packet pool allocate/release
	public static final int DBG_PKTSTATS    = 0x00800000; // Packet pool statistics
	public static final int DBG_THREADPOOL  = 0x01000000; // Thread pool
	public static final int DBG_BENCHMARK	= 0x02000000; // Benchmarking
	public static final int DBG_OPLOCK		= 0x04000000; // Opportunistic locks
	public static final int DBG_PKTALLOC	= 0x08000000; // Memory pool allocate/release
	public static final int DBG_COMPOUND	= 0x10000000; // compound request handling
	public static final int DBG_CANCEL		= 0x20000000; // request cancel handling
	public static final int DBG_SIGNING		= 0x40000000; // request/response signing
	public static final int DBG_ENCRYPTION 	= 0x80000000; // Encryption/decryption

	// Server session object factory
	private static SrvSessionFactory m_factory = new DefaultSrvSessionFactory();

	// Packet handler used to send/receive SMB packets over a particular protocol
	private PacketHandler m_pktHandler;

	// Protocol handler for this session, depends upon the negotiated SMB dialect
	private ProtocolHandler m_handler;

	// SMB session state.
	private SessionState m_state = SessionState.NETBIOS_SESS_REQUEST;

	// Notify change requests and notifications pending flag
	private NotifyRequestList m_notifyList;
	private boolean m_notifyPending;

	// Asynchronous response packet queue
	//
	// Contains SMB response packets that could not be sent due to SMB requests being processed. The
	// asynchronous responses must be sent after any pending requests have been processed as the client may
	// disconnect the session.
	private Queue<SMBSrvPacket> m_asynchQueue;

	// Maximum client buffer size and multiplex count
	private int m_maxBufSize;
	private int m_maxMultiplex;

	// Client capabilities
	private int m_clientCaps;

	// Virtual circuit list
	private VirtualCircuitList m_vcircuits;

	// Setup objects used during two stage session setup before the virtual circuit is allocated
	private Hashtable<Integer, EnumMap<SetupObjectType, Object>> m_setupObjects;

	// Flag to indicate an asynchronous read has been queued/is being processed
	private boolean m_asyncRead;
	
    // Session keys/contexts, for signing/encryption
	private HashMap<String, Object> m_sessionKeys;

	/**
	 * Temporary default constructor
     *
     * TODO: Remove this constructor
	 */
	public SMBSrvSession() {
		super(-1, null, "TEST", null);
	}

	/**
	 * Class constructor.
	 *
	 * @param handler Packet handler used to send/receive SMBs
	 * @param srv     Server that this session is associated with.
	 * @param maxVC   Maximum virtual circuits allowed on this session.
	 */
	protected SMBSrvSession(PacketHandler handler, SMBServer srv, int maxVC) {
		super(-1, srv, handler.isProtocolName(), null);

		// Set the packet handler
		m_pktHandler = handler;

		// If this is a TCPIP SMB or Win32 NetBIOS session then bypass the NetBIOS session setup
		// phase.
		if (isProtocol() == Protocol.TCPIP || isProtocol() == Protocol.Win32NetBIOS) {

			// Advance to the SMB negotiate dialect phase
			setState(SessionState.SMB_NEGOTIATE);
		}

		// Initialize the virtual circuit list
		setMaximumVirtualCircuits(maxVC);
	}

	/**
	 * Return the session protocol type
	 *
	 * @return Protocol
	 */
	public final Protocol isProtocol() {
		return m_pktHandler.isProtocol();
	}

	/**
	 * Find the tree connection for the request
	 *
	 * @param smbPkt SMBSrvPacket
	 * @return TreeConnection
	 */
	public final TreeConnection findTreeConnection(SMBSrvPacket smbPkt) {

		// Find the virtual circuit for the request
        TreeConnection tree = null;

        if ( smbPkt.isSMB1()) {
            SMBV1Parser parser = (SMBV1Parser) smbPkt.getParser();
            VirtualCircuit vc = findVirtualCircuit(parser.getUserId());

            if (vc != null) {

                // Find the tree connection
                tree = vc.findConnection(parser.getTreeId());
            }
        }

		// Return the tree connection, or null if invalid UID or TID
		return tree;
	}

	/**
	 * Add a new virtual circuit, return the allocated UID
	 *
	 * @param vc VirtualCircuit
	 * @return int
	 */
	public synchronized final int addVirtualCircuit(VirtualCircuit vc) {

		// Check if the virtual circuit list has been allocated
		if (m_vcircuits == null)
			m_vcircuits = new VirtualCircuitList();

		// Add the new virtual circuit
		return m_vcircuits.addCircuit(vc);
	}

	/**
	 * Find a virtual circuit with the allocated UID
	 *
	 * @param uid int
	 * @return VirtualCircuit
	 */
	public final VirtualCircuit findVirtualCircuit(int uid) {

		// Check if the virtual circuit list has been allocated
		if (m_vcircuits == null)
			m_vcircuits = new VirtualCircuitList();

		// Find the virtual circuit with the specified UID
		VirtualCircuit vc = m_vcircuits.findCircuit(uid);
		if (vc != null) {

			// Set the session client information from the virtual circuit
			setClientInformation(vc.getClientInformation());

			// Setup any authentication context
			getSMBServer().getSMBAuthenticator().setCurrentUser(getClientInformation());
		}

		// Return the virtual circuit
		return vc;
	}

	/**
	 * Remove a virtual circuit
	 *
	 * @param uid int
	 */
	public final void removeVirtualCircuit(int uid) {

		// Remove the virtual circuit with the specified UID
		if (m_vcircuits != null)
			m_vcircuits.removeCircuit(uid, this);
	}

	/**
	 * Return the active virtual circuit count
	 *
	 * @return int
	 */
	public final int numberOfVirtualCircuits() {
		return (m_vcircuits != null ? m_vcircuits.getCircuitCount() : 0);
	}

	/**
	 * Cleanup any resources owned by this session, close virtual circuits and change notificatio requests.
	 */
	protected final void cleanupSession() {

		// Debug
		try {
			// DEBUG
			if (Debug.EnableInfo && hasDebug(DBG_STATE))
				debugPrintln("Cleanup session, vcircuits=" + m_vcircuits.getCircuitCount() + ", changeNotify="
						+ getNotifyChangeCount());

			// Clear the virtual circuit list
			m_vcircuits.clearCircuitList(this);

			// Check if there are active change notification requests
			if (m_notifyList != null && m_notifyList.numberOfRequests() > 0) {

				// Remove the notify requests from the associated device context notify list
				for (int i = 0; i < m_notifyList.numberOfRequests(); i++) {

					// Get the current change notification request and remove from the global notify
					// list
					NotifyRequest curReq = m_notifyList.getRequest(i);
					if (curReq.getDiskContext().hasChangeHandler())
						curReq.getDiskContext().getChangeHandler().removeNotifyRequests(this);
				}
			}

			// Delete any temporary shares that were created for this session
			getSMBServer().deleteTemporaryShares(this);


		}
		finally {
			// Commit any outstanding transaction that may have been started during cleanup
			if (hasTransaction())
				endTransaction();
		}
	}

	/**
	 * Close the session socket
	 */
	protected final void closeSocket() {

		// Indicate that the session is being shutdown
		setShutdown(true);

		// Close the packet handler
		try {
			m_pktHandler.closeHandler();
			if (Debug.EnableInfo && hasDebug(DBG_STATE))
				debugPrintln("Closed packet handler for client: " + m_pktHandler.getClientName());
		}
		catch (Exception ex) {
			Debug.println(ex);
		}
	}

	/**
	 * Close the session
	 */
	public final void closeSession() {

		// Cleanup the session (open files/virtual circuits/searches)
		cleanupSession();

		// Call the base class
		super.closeSession();

		try {

			// Set the session into a hangup state
			setState(SessionState.NETBIOS_HANGUP);

			// Close the socket
			closeSocket();
		}
		catch (Exception ex) {
			if (Debug.EnableInfo && hasDebug(DBG_STATE)) {
				debugPrintln("[SMB] Error during close session, " + getUniqueId()
						+ ", addr=" + getRemoteAddress().getHostAddress());
				debugPrintln(ex);
			}
		}

	}

	/**
	 * Finalize, object is about to be garbage collected. Make sure resources are released.
	 */
	public void finalize() {

		// Check if there are any active resources
		cleanupSession();

		// Make sure the socket is closed and deallocated
		closeSocket();
	}

	/**
	 * Return the count of active change notification requests
	 *
	 * @return int
	 */
	public final int getNotifyChangeCount() {
		if (m_notifyList == null)
			return 0;
		return m_notifyList.numberOfRequests();
	}

	/**
	 * Return the client maximum buffer size
	 *
	 * @return int
	 */
	public final int getClientMaximumBufferSize() {
		return m_maxBufSize;
	}

	/**
	 * Return the client maximum muliplexed requests
	 *
	 * @return int
	 */
	public final int getClientMaximumMultiplex() {
		return m_maxMultiplex;
	}

	/**
	 * Return the client capability flags
	 *
	 * @return int
	 */
	public final int getClientCapabilities() {
		return m_clientCaps;
	}

	/**
	 * Determine if the client has the specified capability enabled
	 *
	 * @param cap int
	 * @return boolean
	 */
	public final boolean hasClientCapability(int cap) {
		if ((m_clientCaps & cap) != 0)
			return true;
		return false;
	}

	/**
	 * Return the packet handler used by the session
	 *
	 * @return PacketHandler
	 */
	public final PacketHandler getPacketHandler() {
		return m_pktHandler;
	}

    /**
     * Return the associated protocol handler used by the session
     *
     * @return ProtocolHandler
     */
    public final ProtocolHandler getProtocolHandler() {
        return m_handler;
    }

	/**
	 * Return the SMB packet pool from the packet handler
	 *
	 * @return SMBPacketPool
	 */
	public final SMBPacketPool getPacketPool() {
		return m_pktHandler.getPacketPool();
	}

	/**
	 * Return the thread pool
	 *
	 * @return ThreadRequestPool
	 */
	public final ThreadRequestPool getThreadPool() {
		return getSMBServer().getThreadPool();
	}

	/**
	 * Cehck if the clients remote address is available
	 *
	 * @return boolean
	 */
	public final boolean hasRemoteAddress() {
		return m_pktHandler.hasRemoteAddress();
	}

	/**
	 * Return the client network address
	 *
	 * @return InetAddress
	 */
	public final InetAddress getRemoteAddress() {
		return m_pktHandler.getRemoteAddress();
	}

	/**
	 * Return the server that this session is associated with.
	 *
	 * @return SMBServer
	 */
	public final SMBServer getSMBServer() {
		return (SMBServer) getServer();
	}

	/**
	 * Return the server name that this session is associated with.
	 *
	 * @return String
	 */
	public final String getServerName() {
		return getSMBServer().getServerName();
	}

	/**
	 * Return the session state
	 *
	 * @return SessionState
	 */
	public final SessionState getState() {
		return m_state;
	}

	/**
	 * Return the maximum virtual circuits allowed on this session
	 *
	 * @return int
	 */
	public final int getMaximumVirtualCircuits() {
		return (m_vcircuits != null) ? m_vcircuits.getMaximumVirtualCircuits() : 0;
	}

    /**
     * Check if the session has any keys
     *
     * @return boolean
     */
    public synchronized final boolean hasSessionKeys() {
        return m_sessionKeys != null ? true : false;
    }

	/**
	 * Check for the specified key type
	 *
	 * @param key String
	 * @return boolean
	 */
	public synchronized final boolean hasSessionKey( String key) {
		if ( m_sessionKeys == null)
			return false;
		return m_sessionKeys.containsKey( key);
	}

	/**
     * Return the required session key
     *
     * @param key String
     * @return Object
     */
    public synchronized final Object getSessionKey( String key) {
        if ( m_sessionKeys == null)
            return null;
        return m_sessionKeys.get( key);
    }

    /**
     * Add a session key of the specified type
     *
     * @param key String
     * @param val Object
     */
    public synchronized final void addSessionKey(String key, Object val) {
        if ( m_sessionKeys == null) {
            synchronized (this) {
                if (m_sessionKeys == null)
                    m_sessionKeys = new HashMap<String, Object>();
            }
        }

        m_sessionKeys.put( key, val);
    }

    /**
     * Remove the specified key type
     *
     * @param key String
     * @return Object
     */
    public synchronized final Object removeSessionKey( String key) {
        if ( m_sessionKeys != null)
            return m_sessionKeys.remove( key);
        return null;
    }

    /**
     * Remove all session keys
     */
    public synchronized void removeAllSessionKeys() {
        if ( m_sessionKeys != null) {
            m_sessionKeys.clear();
            m_sessionKeys = null;
        }
    }

    /**
	 * Hangup the session.
	 *
	 * @param reason String Reason the session is being closed.
	 */
	public void hangupSession(String reason) {

		// Debug
		if (Debug.EnableInfo && hasDebug(DBG_STATE)) {
			debugPrint("## Session closing, reason=" + reason);
			debugPrintln(reason);
		}

		// Inform the protocol handler of the hangup
		if ( getProtocolHandler() != null)
			getProtocolHandler().hangupSession( this, reason);

		// Set the session into a NetBIOS hangup state
		setState(SessionState.NETBIOS_HANGUP);
	}

	/**
	 * Check if the Macintosh extension SMBs are enabled
	 *
	 * @return boolean
	 */
	public final boolean hasMacintoshExtensions() {
		return getSMBServer().getSMBConfiguration().hasMacintoshExtensions();
	}

	/**
	 * Check if there is a change notification update pending
	 *
	 * @return boolean
	 */
	public final boolean hasNotifyPending() {
		return m_notifyPending;
	}

	/**
	 * Determine if the session has a setup object for the specified PID
	 *
	 * @param pid int
     * @param typ SetupObjectType
	 * @return boolean
	 */
	public synchronized final boolean hasSetupObject(int pid, SetupObjectType typ) {
		if (m_setupObjects == null)
			return false;

		// Get the map of setup objects for the particular id
        EnumMap<SetupObjectType, Object> setupMap = m_setupObjects.get(pid);
        if ( setupMap == null)
            return false;

        return setupMap.containsKey( typ);
	}

	/**
	 * Return the session setup object for the specified PID
	 *
	 * @param pid int
	 * @param typ SetupObjectType
	 * @return Object
	 */
	public synchronized final Object getSetupObject(int pid, SetupObjectType typ) {

		if (m_setupObjects == null)
			return null;

        // Get the map of setup objects for the particular id
        EnumMap<SetupObjectType, Object> setupMap = m_setupObjects.get(pid);
        if ( setupMap == null)
            return null;

        return setupMap.get( typ);
	}

	/**
	 * Store the setup object for the specified PID
	 *
	 * @param pid int
     * @param typ SetupObjectType
	 * @param obj Object
	 */
	public synchronized final void setSetupObject(int pid, Object obj, SetupObjectType typ) {

        EnumMap<SetupObjectType, Object> setupMap = null;

        if (m_setupObjects == null)
			m_setupObjects = new Hashtable<Integer, EnumMap<SetupObjectType, Object>>();
        else
            setupMap = m_setupObjects.get( pid);

        if (setupMap == null) {

            // Create the setup map for the specified id
            setupMap = new EnumMap<SetupObjectType, Object>(SetupObjectType.class);
            m_setupObjects.put(pid, setupMap);
        }

        // Add the setup object for the specified id
        setupMap.put( typ, obj);
	}

	/**
	 * Remove the session setup object for the specified PID
	 *
	 * @param pid int
     * @param typ SetupObjectType
	 * @return Object
	 */
	public synchronized final Object removeSetupObject(int pid, SetupObjectType typ) {
		if (m_setupObjects == null)
			return null;

        // Get the map of setup objects for the particular id
        EnumMap<SetupObjectType, Object> setupMap = m_setupObjects.get(pid);
        if ( setupMap == null)
            return null;

        // Remove the setup object
        Object setupObj = setupMap.remove( typ);
        if ( setupMap.isEmpty())
            m_setupObjects.remove( pid);

        return setupObj;
	}

    /**
     * Remove all session setup objects for the specified PID
     *
     * @param pid int
     */
    public synchronized final void removeAllSetupObjects(int pid) {
        if (m_setupObjects == null)
            return;

        m_setupObjects.remove( pid);
    }

    /**
	 * Set the change notify pending flag
	 *
	 * @param pend boolean
	 */
	public final void setNotifyPending(boolean pend) {
		m_notifyPending = pend;
	}

	/**
	 * Set the client maximum buffer size
	 *
	 * @param maxBuf int
	 */
	public final void setClientMaximumBufferSize(int maxBuf) {
		m_maxBufSize = maxBuf;
	}

	/**
	 * Set the client maximum multiplexed
	 *
	 * @param maxMpx int
	 */
	public final void setClientMaximumMultiplex(int maxMpx) {
		m_maxMultiplex = maxMpx;
	}

	/**
	 * Set the client capability flags
	 *
	 * @param flags int
	 */
	public final void setClientCapabilities(int flags) {
		m_clientCaps = flags;
	}

	/**
	 * Set the server capabilities flags
	 *
	 * @param srvCapab int
	 */
	public final void setServerCapabilities(int srvCapab) {
		m_handler.setServerCapabilities( srvCapab);
	}

	/**
	 * Set the session state.
	 *
	 * @param state SessionState
	 */
	public void setState(SessionState state) {

		// Debug
		if (Debug.EnableInfo && hasDebug(DBG_STATE))
			debugPrintln("State changed to " + state.name());

		// Change the session state
		m_state = state;
	}

	/**
	 * Set the maximum virtual circuits allowed for this session
	 *
	 * @param maxVC int
	 */
	public synchronized final void setMaximumVirtualCircuits(int maxVC) {

		// Can only set the virtual circuit limit before the virtual circuit list has been allocated
		// to the session
		if (m_vcircuits != null)
			throw new RuntimeException("Virtual circuit list is already allocated");

		// Create the virtual circuit list with the required limit
		m_vcircuits = new VirtualCircuitList(maxVC);
	}

	/**
	 * Process the NetBIOS session request message, either accept the session request and send back
	 * a NetBIOS accept or reject the session and send back a NetBIOS reject and hangup the session.
	 * <p>
	 * @param smbPkt SMBSrvPacket
	 * @exception IOException I/O error
     * @exception NetBIOSException NetBIOS error
	 */
	protected void procNetBIOSSessionRequest(SMBSrvPacket smbPkt)
			throws IOException, NetBIOSException {

		// Check if the received packet contains enough data for a NetBIOS session request packet.
		if (smbPkt.getReceivedLength() < RFCNetBIOSProtocol.SESSREQ_LEN || smbPkt.getHeaderType() != RFCNetBIOSProtocol.MsgType.REQUEST) {

			// Debug
			if (Debug.EnableInfo && hasDebug(DBG_STATE)) {
				Debug.println("NBREQ invalid packet len=" + smbPkt.getReceivedLength() + ", header=0x" + Integer.toHexString(smbPkt.getHeaderType().intValue()));
				HexDump.Dump(smbPkt.getBuffer(), smbPkt.getReceivedLength(), 0, Debug.getDebugInterface());
			}

			throw new NetBIOSException("NBREQ Invalid packet len=" + smbPkt.getReceivedLength());
		}

		// Do a few sanity checks on the received packet
		byte[] buf = smbPkt.getBuffer();

		if (buf[4] != (byte) 32 || buf[38] != (byte) 32)
			throw new NetBIOSException("NBREQ Invalid NetBIOS name data");

		// Extract the from/to NetBIOS encoded names, and convert to normal strings.
		StringBuffer nbName = new StringBuffer(32);
		for (int i = 0; i < 32; i++)
			nbName.append((char) buf[5 + i]);

		String toName = NetBIOSSession.DecodeName(nbName.toString());
		toName = toName.trim();

		nbName.setLength(0);
		for (int i = 0; i < 32; i++)
			nbName.append((char) buf[39 + i]);

		String fromName = NetBIOSSession.DecodeName(nbName.toString());
		fromName = fromName.trim();

		// Debug
		if (Debug.EnableInfo && hasDebug(DBG_STATE))
			debugPrintln("NetBIOS CALL From " + fromName + " to " + toName);

		// Check that the request is for this server
		boolean forThisServer = false;

		if (toName.compareTo(getServerName()) == 0 || toName.compareTo(NetBIOSName.SMBServer) == 0
				|| toName.compareTo(NetBIOSName.SMBServer2) == 0 || toName.compareTo("*") == 0) {

			// Request is for this server
			forThisServer = true;
		}
		else if (getSMBServer().getSMBConfiguration().hasAliasNames() == true) {

			// Check for a connection to one of the alias server names
			StringList aliasNames = getSMBServer().getSMBConfiguration().getAliasNames();
			if (aliasNames.containsString(toName))
				forThisServer = true;
		}
		else {

			// Check if the caller is using an IP address
			InetAddress[] srvAddr = getSMBServer().getServerAddresses();
			if (srvAddr != null) {

				// Check for an address match
				int idx = 0;

				while (idx < srvAddr.length && forThisServer == false) {

					// Check the current IP address
					if (srvAddr[idx++].getHostAddress().compareTo(toName) == 0)
						forThisServer = true;
				}
			}
		}

		// If we did not find an address match then reject the session request
		if (forThisServer == false)
			throw new NetBIOSException("NBREQ Called name is not this server (" + toName + ")");

		// Debug
		if (Debug.EnableInfo && hasDebug(DBG_STATE))
			debugPrintln("NetBIOS session request from " + fromName);

		// Move the session to the SMB negotiate state
		setState(SessionState.SMB_NEGOTIATE);

		// Set the remote client name
		setRemoteName(fromName);

		// Build a NetBIOS session accept message
		smbPkt.setHeaderType(RFCNetBIOSProtocol.MsgType.ACK);
		smbPkt.setHeaderFlags(0);
		smbPkt.setHeaderLength(0);

		// Output the NetBIOS session accept packet
		m_pktHandler.writePacket(smbPkt, 4, true);
	}

	/**
	 * Process an SMB dialect negotiate request, may be SMB v1 or SMB v2 header
	 *
	 * @param smbPkt SMBSrvPacket
     * @exception SMBSrvException SMB error
     * @exception IOException I/O error
	 */
	protected void procSMBNegotiate(SMBSrvPacket smbPkt)
			throws SMBSrvException, IOException {

		// Initialize the NetBIOS header
		byte[] buf = smbPkt.getBuffer();
		buf[0] = (byte) RFCNetBIOSProtocol.MsgType.MESSAGE.intValue();

        // Negotiate request can be SMB v1 with SMB v2 dialects within the list, or SMB v2 if only SMB v2 dialects are
        // being requested
        //
        // Get the requested dialect list
        NegotiateContext negCtx = null;

        try {

        	// Make sure we can get a parser for the received packet
			if ( smbPkt.getParser() != null) {

				// Parse the negotiate request and get the list of requested dialects
				negCtx = smbPkt.getParser().parseNegotiateRequest(this);

				// Debug
				if (Debug.EnableInfo && hasDebug(DBG_NEGOTIATE))
					debugPrintln("Negotiate context: " + negCtx);
			}
			else {

				// Failed to get a parser for the received packet, drop the connection
				setState(SessionState.NETBIOS_HANGUP);

				// Debug
				if (Debug.EnableInfo && hasDebug(DBG_NEGOTIATE))
					debugPrintln("Failed to get parser for received negotiate");

				// Do not send a reply, just drop the connection
				return;
			}
        }
        catch ( SMBSrvException ex) {

            // Error parsing the negotiate request
            sendErrorResponseSMB(smbPkt, ex.getNTErrorCode(), ex.getErrorCode(), ex.getErrorClass());
            setState(SessionState.NETBIOS_HANGUP);
            return;
        }

        // Find the highest level SMB dialect that the server and client both support
		DialectSelector diaSelector = getSMBServer().getSMBConfiguration().getEnabledDialects();
		int diaIdx = diaSelector.findHighestDialect( negCtx.getDialects());

		// Debug
		if (Debug.EnableInfo && hasDebug(DBG_NEGOTIATE)) {
			if (diaIdx == -1)
				debugPrintln("Failed to negotiate SMB dialect");
			else
				debugPrintln("Negotiated SMB dialect - " + Dialect.DialectTypeString(diaIdx));
		}

		try {

            // Check if we successfully negotiated an SMB dialect with the client
            SMBSrvPacket respPkt = null;

            if (diaIdx != -1) {

                // Allocate a protocol handler for the negotiated dialect, if we cannot get a protocol handler then bounce
                // the request.
                m_handler = ProtocolFactory.getHandler(diaIdx);

                if (m_handler != null) {

                    // Debug
                    if (Debug.EnableInfo && hasDebug(DBG_NEGOTIATE))
                        debugPrintln("Assigned protocol handler - " + m_handler.getClass().getName());

                    // Initialize the protocol handler
					m_handler.initialize( getSMBServer(), this, diaIdx);

                    // Update the session debug prefix to contain the SMB version
                    setDebugPrefix("[" + getPacketHandler().getShortName() + getSessionId() + ":" + Dialect.getMajorSMBVersion( diaIdx) + "] ");

                    // Post processing hook for the protocol handler
                    respPkt = m_handler.postProcessNegotiate(smbPkt, negCtx);
                }
                else {

                    // Could not get a protocol handler for the selected SMB dialect, indicate to the client that no suitable
                    // dialect is available
                    diaIdx = -1;

                    // Debug
                    if (Debug.EnableError && hasDebug(DBG_NEGOTIATE))
                        debugPrintln("No protocol handler for dialect - " + Dialect.DialectTypeString( diaIdx));

                    // Return an error status
                    sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
                    return;
                }
            }
            else {

            	// No common dialect available between the client and server

				// Debug
				if (Debug.EnableError && hasDebug(DBG_NEGOTIATE))
					debugPrintln("No comon dialect between client and server");

				// Return an error status
				sendErrorResponseSMB(smbPkt, SMBStatus.NTInvalidParameter, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);

				// Drop the session
				setState( SessionState.NETBIOS_HANGUP);
				return;

			}

            // Pass the negotiate context back to the parser
            respPkt.getParser().packNegotiateResponse(getSMBServer(), this, respPkt, diaIdx, negCtx);

            // Send the negotiate response
            m_pktHandler.writePacket(respPkt, respPkt.getLength());

            // Check if the negotiated SMB dialect supports the session setup command, if not then bypass the session setup phase
            if ( diaIdx == -1)
                setState(SessionState.NETBIOS_HANGUP);
            else if (smbPkt.getParser().requireSessionSetup( diaIdx))
                setState(SessionState.SMB_SESSSETUP);
            else
                setState(SessionState.SMB_SESSION);

            // If a dialect was selected inform the server that the session has been opened
            if (diaIdx != -1)
                getSMBServer().sessionOpened(this);
        }
        catch (AuthenticatorException ex) {

            // Log the error
            if (Debug.EnableError && hasDebug(DBG_NEGOTIATE))
                debugPrintln("Negotiate error - " + ex.getMessage());

            // Close the session
            setState(SessionState.NETBIOS_HANGUP);
        }
        catch ( SMBSrvException ex) {

		    // Build an error response, use the original request packet
            smbPkt.getParser().buildErrorResponse(SMBStatus.NTErr, ex.getNTErrorCode());
            m_pktHandler.writePacket(smbPkt, smbPkt.getLength());
        }
	}

	/**
	 * Start the SMB server session in a seperate thread.
	 */
	public void run() {

		// Server packet allocated from the pool
		SMBSrvPacket smbPkt = null;

		try {

			// Debug
			if (Debug.EnableInfo && hasDebug(SMBSrvSession.DBG_NEGOTIATE))
				debugPrintln("Server session started");

			// The server session loops until the NetBIOS hangup state is set.
			while (m_state != SessionState.NETBIOS_HANGUP) {

				try {

					// Wait for a request packet
					smbPkt = m_pktHandler.readPacket();
				}
				catch (SocketTimeoutException ex) {

					// Debug
					if (Debug.EnableInfo && hasDebug(SMBSrvSession.DBG_SOCKET))
						debugPrintln("Socket read timed out, closing session");

					// Socket read timed out
					hangupSession("Socket read timeout");

					// Clear the request packet
					smbPkt = null;
				}
				catch (IOException ex) {

					// Check if there is no more data, the other side has dropped the connection
					hangupSession("Remote disconnect: " + ex.toString());

					// Clear the request packet
					smbPkt = null;
				}

				// Check for an empty packet
				if (smbPkt == null)
					continue;

				// Check the packet signature if we are in an SMB state
				if (m_state != SessionState.NETBIOS_SESS_REQUEST) {

					// Check the packet signature (for SMB v1/2/3)
					if (smbPkt.isSMB() == false) {

						// Debug
						if (Debug.EnableInfo && hasDebug(DBG_ERROR))
							debugPrintln("Invalid SMB packet signature received, packet ignored");

						continue;
					}
				}

				// Queue the request to the thread pool for processing
				getThreadPool().queueRequest(new SMBThreadRequest(this, smbPkt));
				smbPkt = null;
			}

			// Cleanup the session, then close the session/socket
			closeSession();

			if (Debug.EnableInfo && hasDebug(DBG_STATE))
				debugPrintln("[SMB] Closed session, " + getUniqueId()
						+ ", addr=" + getRemoteAddress().getHostAddress());

			// Notify the server that the session has closed
			getSMBServer().sessionClosed(this);

			// Clear any user context
			if (hasClientInformation())
				getSMBServer().getSMBAuthenticator().setCurrentUser(null);
		}
		catch (Exception ex) {

			// Output the exception details
			if (isShutdown() == false) {
				debugPrintln("Closing session due to exception");
				debugPrintln(ex);
				Debug.println(ex);
			}
		}
		catch (Throwable ex) {
			debugPrintln("Closing session due to throwable");
			debugPrintln(ex.toString());
			Debug.println(ex);
		}
		finally {

			// Release any allocated request packet back to the pool
			if (smbPkt != null)
				getSMBServer().getPacketPool().releasePacket(smbPkt);
		}
	}

	/**
	 * Handle a session message, receive all data and run the SMB protocol handler.
	 *
	 * @param smbPkt SMBSrvPacket
     * @exception SMBSrvException SMB error
     * @exception IOException I/O error
     * @exception TooManyConnectionsException No more connections available
	 */
	protected final void runHandler(SMBSrvPacket smbPkt)
			throws IOException, SMBSrvException, TooManyConnectionsException {

		// DEBUG
		if (Debug.EnableInfo && hasDebug(DBG_PKTTYPE))
			debugPrintln("Rx packet - " + smbPkt.getParser().toShortString());

		// Call the protocol handler
		if (m_handler.runProtocol(smbPkt) == false) {

			// The sessions protocol handler did not process the request, return an unsupported SMB error status.
			sendErrorResponseSMB(smbPkt, SMBStatus.SRVNotSupported, SMBStatus.ErrSrv);
		}

		// Commit/rollback any active transaction
		if (hasTransaction())
			endTransaction();

		// Check if there are any pending asynchronous response packets
		SMBSrvPacket asynchPkt;

		while ((asynchPkt = removeFirstAsynchResponse()) != null) {

			// Remove the current asynchronous response SMB packet and send to the client
			sendResponseSMB(asynchPkt, asynchPkt.getLength());

			// DEBUG
			if (Debug.EnableInfo && hasDebug(DBG_NOTIFY)) {
				debugPrintln("Sent queued asynch response type=" + smbPkt.getParser().toShortString());
				synchronized (this) {
					debugPrintln("  Async queue len=" + m_asynchQueue.size());
				}
			}
		}
	}

	/**
	 * Process a SMB request packet
	 *
	 * @param smbPkt SMBSrvPacket
	 */
	public final void processPacket(SMBSrvPacket smbPkt) {

		// Process the packet, if valid
		if (smbPkt != null) {

			try {

				// Start/end times if timing debug is enabled
				long startTime = 0L;
				long endTime = 0L;

				// Debug
				if (Debug.EnableInfo && hasDebug(DBG_TIMING))
					startTime = System.currentTimeMillis();

				// Debug
				if (Debug.EnableInfo && hasDebug(DBG_RXDATA)) {
					debugPrintln("Rx Data len=" + smbPkt.getReceivedLength());
					HexDump.Dump(smbPkt.getBuffer(), smbPkt.getReceivedLength(), 0, Debug.getDebugInterface());
				}

				// Process the received packet
				if (smbPkt.getReceivedLength() > 0) {

					switch (m_state) {

						// NetBIOS session request pending
						case NETBIOS_SESS_REQUEST:
							procNetBIOSSessionRequest(smbPkt);
							break;

						// SMB dialect negotiate
						case SMB_NEGOTIATE:
							procSMBNegotiate(smbPkt);
							break;

						// SMB session setup
						case SMB_SESSSETUP:
							m_handler.runProtocol(smbPkt);
							break;

						// SMB session main request processing
						case SMB_SESSION:

							// Run the main protocol handler
							runHandler(smbPkt);

							// Debug
							if (Debug.EnableInfo && hasDebug(DBG_TIMING)) {
								endTime = System.currentTimeMillis();
								long duration = endTime - startTime;
								if (duration > 20)
									debugPrintln("Processed packet " + smbPkt.getParser().toShortString());
							}
							break;
					}
				}

				// Release the current packet back to the pool
				getPacketPool().releasePacket(smbPkt);
				smbPkt = null;

				// DEBUG
				if (Debug.EnableInfo && hasDebug(DBG_PKTSTATS))
					Debug.println("[SMB] Packet pool stats: " + getPacketPool());

			}
			catch (DeferredPacketException ex) {

				// Packet processing has been deferred, waiting on completion of some other processing
				// Make sure the request packet is not released yet
				smbPkt = null;
			}
			catch (SocketException ex) {

				// DEBUG
				if (Debug.EnableInfo && hasDebug(DBG_STATE))
					debugPrintln("Socket closed by remote client");
			}
			catch (Exception ex) {

				// Output the exception details
				if (isShutdown() == false) {
					debugPrintln("Closing session due to exception");
					debugPrintln(ex);
					Debug.println(ex);
				}
			}
			catch (Throwable ex) {
				debugPrintln("Closing session due to throwable");
				debugPrintln(ex.toString());
				Debug.println(ex);
			}
			finally {

				// Release any allocated request packet back to the pool
				if (smbPkt != null)
					getSMBServer().getPacketPool().releasePacket(smbPkt);
			}
		}

		// Check if there is an active transaction
		if (hasTransaction()) {

			// DEBUG
			if (Debug.EnableError)
				debugPrintln("** Active transaction after packet processing, cleaning up **");

			// Close the active transaction
			endTransaction();
		}

		// Check if the session has been closed, either cleanly or due to an exception
		if (m_state == SessionState.NETBIOS_HANGUP) {

			// Cleanup the session, make sure all resources are released
			cleanupSession();

			// Debug
			if (Debug.EnableInfo && hasDebug(DBG_STATE))
				debugPrintln("Server session closed");

			// Close the session
			closeSocket();

			// Notify the server that the session has closed
			getSMBServer().sessionClosed(this);
		}

		// Clear any user context
		if (hasClientInformation())
			getSMBServer().getSMBAuthenticator().setCurrentUser(null);
	}

	/**
	 * Send an SMB response
	 *
	 * @param pkt SMBSrvPacket
	 * @throws IOException I/O error
	 */
	public final void sendResponseSMB(SMBSrvPacket pkt)
			throws IOException {
		sendResponseSMB(pkt, pkt.getLength());
	}

	/**
	 * Send an SMB response
	 *
	 * @param pkt SMBSrvPacket
	 * @param len int
	 * @throws IOException I/O error
	 */
	public synchronized final void sendResponseSMB(SMBSrvPacket pkt, int len)
			throws IOException {

		// Commit/rollback any active transactions before sending the response
		if (hasTransaction()) {

			// DEBUG
			long startTime = 0L;

			if (Debug.EnableInfo && hasDebug(DBG_BENCHMARK))
				startTime = System.currentTimeMillis();

			// Commit or rollback the transaction
			endTransaction();

			// DEBUG
			if (Debug.EnableInfo && hasDebug(DBG_BENCHMARK)) {
				long elapsedTime = System.currentTimeMillis() - startTime;
				if (elapsedTime > 5L)
					Debug.println("Benchmark: End transaction took " + elapsedTime + "ms");
			}
		}

		// Do any final updates before the response is sent
        pkt.getParser().responsePreSend(this, pkt);

		// Send the response packet
		m_pktHandler.writePacket(pkt, len);
		m_pktHandler.flushPacket();

		// Debug
		if (Debug.EnableInfo && hasDebug(DBG_TXDATA)) {
			debugPrintln("Tx Data len=" + len);
			HexDump.Dump(pkt.getBuffer(), len, 0, Debug.getDebugInterface());
		}
	}

	/**
	 * Send a success response SMB
	 *
	 * @param smbPkt SMBSrvPacket
	 * @throws IOException If a network error occurs
	 */
	public final void sendSuccessResponseSMB(SMBSrvPacket smbPkt)
			throws IOException {

        // Check if long error codes are required by the client
        SMBParser parser = smbPkt.getParser();

        // Make sure the response flag is set
        parser.setResponse();

        // Check if long error codes are being used
        if ( parser.isLongErrorCode()) {

            // Build the success response using the NT status code
            parser.buildErrorResponse( SMBStatus.NTErr, SMBStatus.NTSuccess);
        }
        else {

            // Build the success response using the specified error class
            parser.buildErrorResponse( SMBStatus.Success, SMBStatus.Success);
        }

		// Return the success response to the client
		sendResponseSMB(smbPkt, smbPkt.getLength());

		// Debug
		if (Debug.EnableInfo && hasDebug(DBG_TXDATA))
			debugPrintln("Tx Data len=" + smbPkt.getLength() + ", success SMB");
	}

	/**
	 * Send an error response SMB. The returned code depends on the client long error code flag
	 * setting.
	 *
	 * @param smbPkt   SMBSrvPacket
	 * @param ntCode   32bit error code
	 * @param stdCode  Standard error code
	 * @param stdClass Standard error class
     * @exception SMBSrvException SMB error
     * @exception IOException I/O error
	 */
	public final void sendErrorResponseSMB(SMBSrvPacket smbPkt, int ntCode, int stdCode, int stdClass)
			throws java.io.IOException, SMBSrvException {

		// Check if long error codes are required by the client
        SMBParser parser = smbPkt.getParser();

        if (parser.isLongErrorCode()) {

            // Return the long/NT status code
            if (ntCode != -1) {

                // Use the 32bit NT error code
                sendErrorResponseSMB(smbPkt, ntCode, SMBStatus.NTErr);
            }
            else
                throw new SMBSrvException( SMBStatus.InternalErr, SMBStatus.IntNoLongErrorCode);
        }
        else {

            // Return the standard/DOS error code
            sendErrorResponseSMB(smbPkt, stdCode, stdClass);
        }
	}

	/**
	 * Send an error response SMB.
	 *
	 * @param smbPkt   SMBSrvPacket
	 * @param errCode  int Error code.
	 * @param errClass int Error class.
     * @exception IOException I/O error
	 */
	public final void sendErrorResponseSMB(SMBSrvPacket smbPkt, int errCode, int errClass)
			throws java.io.IOException {

        // Check if long error codes are required by the client
        SMBParser parser = smbPkt.getParser();

        // Make sure the response flag is set
        parser.setResponse();

        // Check if long error codes are being used
        if ( parser.isLongErrorCode()) {

            // Build the error response using the NT status code
            parser.buildErrorResponse( SMBStatus.NTErr, errCode);
        }
        else {

            // Build the error response using the specified error class
            parser.buildErrorResponse( errClass, errCode);
        }

		// Return the error response to the client
		sendResponseSMB(smbPkt, smbPkt.getLength());

		// Debug
		if (Debug.EnableInfo && hasDebug(DBG_ERROR))
			debugPrintln("Error : Cmd = " + smbPkt.getParser().toShortString() + " - " + SMBErrorText.ErrorString(errClass, errCode));
	}

	/**
	 * Send an NT error response SMB.
	 *
	 * @param smbPkt   SMBSrvPacket
	 * @param ntErrCode  int NT Error code
     * @exception IOException I/O error
	 */
	public final void sendNTErrorResponseSMB(SMBSrvPacket smbPkt, int ntErrCode)
			throws java.io.IOException {

		// Make sure the response flag is set
		SMBParser parser = smbPkt.getParser();
		parser.setResponse();

		// Build the error response using the NT status code
		parser.buildErrorResponse( SMBStatus.NTErr, ntErrCode);

		// Return the error response to the client
		sendResponseSMB(smbPkt, smbPkt.getLength());

		// Debug
		if (Debug.EnableInfo && hasDebug(DBG_ERROR))
			debugPrintln("Error : Cmd = " + smbPkt.getParser().toShortString() + " - " + SMBErrorText.ErrorString( SMBStatus.NTErr, ntErrCode));
	}

	/**
	 * Send an asynchonous error response SMB.
	 *
	 * @param smbPkt   SMBSrvPacket
	 * @param errCode  int Error code.
	 * @param errClass int Error class.
	 * @return boolean
     * @exception IOException I/O error
	 */
	public final boolean sendAsyncErrorResponseSMB(SMBSrvPacket smbPkt, int errCode, int errClass)
			throws java.io.IOException {

		// Build the error response
		SMBParser parser = smbPkt.getParser();
		parser.buildErrorResponse( errClass, errCode);

		// Return the error response to the client
		boolean sentOK = sendAsynchResponseSMB(smbPkt, smbPkt.getLength());

		// Debug
		if (Debug.EnableInfo && hasDebug(DBG_ERROR))
			debugPrintln("Async Error : Cmd = " + smbPkt.getParser().toShortString() + " - " + SMBErrorText.ErrorString(errClass, errCode) + ", sent=" + sentOK);

		// Return the send status
		return sentOK;
	}

	/**
	 * Send, or queue, an asynchronous response SMB
	 *
	 * @param pkt SMBSrvPacket
	 * @param len int
	 * @return true if the packet was sent, or false if it was queued
	 * @throws IOException If an I/O error occurs
	 */
	public final boolean sendAsynchResponseSMB(SMBSrvPacket pkt, int len)
			throws IOException {

		// Check if there is pending data from the client
		boolean sts = false;

		if (m_pktHandler.availableBytes() == 0) {

			// Send the asynchronous response immediately
			sendResponseSMB(pkt, len);
			m_pktHandler.flushPacket();

			// Indicate that the SMB response has been sent
			sts = true;
		}
		else {

			// Queue the packet to send out when current SMB requests have been processed
			queueAsynchResponseSMB(pkt);
		}

		// Return the sent/queued status
		return sts;
	}

	/**
	 * Queue an asynchronous response SMB for sending when current SMB requests have been processed.
	 *
	 * @param pkt SMBSrvPacket
	 */
	public final synchronized void queueAsynchResponseSMB(SMBSrvPacket pkt) {

		// Check if the asynchronous response queue has been allocated
		if (m_asynchQueue == null) {

			// Allocate the asynchronous response queue
			m_asynchQueue = new LinkedList<SMBSrvPacket>();
		}

		// Add the SMB response packet to the queue
		m_asynchQueue.add(pkt);
	}

	/**
	 * Remove an asynchronous response packet from the head of the list
	 *
	 * @return SMBSrvPacket
	 */
	protected final synchronized SMBSrvPacket removeFirstAsynchResponse() {

		// Check if there are asynchronous response packets queued
		if (m_asynchQueue == null || m_asynchQueue.size() == 0)
			return null;

		// Return the SMB packet from the head of the queue
		SMBSrvPacket pkt = (SMBSrvPacket) m_asynchQueue.poll();
		return pkt;
	}

	/**
	 * Check if this session has any asynchrnous responses queued
	 *
	 * @return boolean
	 */
	public final synchronized boolean hasAsyncResponseQueued() {
		if (m_asynchQueue == null || m_asynchQueue.size() == 0)
			return false;
		return true;
	}

	/**
	 * Send queued asynchronous responses
	 *
	 * @return int
	 */
	public final synchronized int sendQueuedAsyncResponses() {

		// Check if there are any pending asynchronous response packets
		int asyncCnt = 0;
		SMBSrvPacket asyncPkt;

		while ((asyncPkt = removeFirstAsynchResponse()) != null) {

			try {

				// Update the asynchronous packet count
				asyncCnt++;

				// Send the current asynchronous response to the client
				sendResponseSMB(asyncPkt, asyncPkt.getLength());

				// DEBUG
				if (Debug.EnableInfo && (hasDebug(DBG_NOTIFY) || hasDebug(DBG_OPLOCK))) {
					debugPrintln("Sent queued async response type=" + asyncPkt.getParser().toShortString());
					debugPrintln("  Async queue len=" + m_asynchQueue.size());
				}
			}
			catch (Exception ex) {

				// DEBUG
				if (Debug.EnableError && (hasDebug(DBG_NOTIFY) || hasDebug(DBG_OPLOCK)))
					debugPrintln("Failed to send queued asynch response type=" + asyncPkt.getParser().toShortString() + ", ex=" + ex);
			}
		}

		// Return the count of asynchrnous packets processed
		return asyncCnt;
	}

	/**
	 * Find the notify request with the specified ids
	 *
	 * @param mid int
	 * @param tid int
	 * @param uid int
	 * @param pid int
	 * @return NotifyRequest
	 */
	public final NotifyRequest findNotifyRequest(int mid, int tid, int uid, int pid) {

		// Check if the local notify list is valid
		if (m_notifyList == null)
			return null;

		// Find the matching notify request
		return m_notifyList.findRequest(mid, tid, uid, pid);
	}

	/**
	 * Find the notify request with the specified id
	 *
	 * @param mid long
	 * @return NotifyRequest
	 */
	public final NotifyRequest findNotifyRequest(long mid) {

		// Check if the local notify list is valid
		if (m_notifyList == null)
			return null;

		// Find the matching notify request
		return m_notifyList.findRequest(mid);
	}

	/**
	 * Find an existing notify request for the specified directory and filter
	 *
	 * @param dir       NetworkFile
	 * @param filter    Set of NotifyChange
	 * @param watchTree boolean
	 * @return NotifyRequest
	 */
	public final NotifyRequest findNotifyRequest(NetworkFile dir, Set<NotifyChange> filter, boolean watchTree) {

		// Check if the local notify list is valid
		if (m_notifyList == null)
			return null;

		// Find the matching notify request
		return m_notifyList.findRequest(dir, filter, watchTree);
	}

	/**
	 * Add a change notification request
	 *
	 * @param req NotifyRequest
	 * @param ctx DiskDeviceContext
	 */
	public final void addNotifyRequest(NotifyRequest req, DiskDeviceContext ctx) {

		// Check if the local notify list has been allocated
		if (m_notifyList == null)
			m_notifyList = new NotifyRequestList();

		// Add the request to the local list and the shares global list
		m_notifyList.addRequest(req);
		ctx.addNotifyRequest(req);
	}

	/**
	 * Remove a change notification request
	 *
	 * @param req NotifyRequest
	 */
	public final void removeNotifyRequest(NotifyRequest req) {

		// Check if the local notify list has been allocated
		if (m_notifyList == null)
			return;

		// Remove the request from the local list and the shares global list
		m_notifyList.removeRequest(req);

		if (req.getDiskContext() != null)
			req.getDiskContext().removeNotifyRequest(req);
	}

	/**
	 * Return the server session object factory
	 *
	 * @return SrvSessionFactory
	 */
	public static final SrvSessionFactory getFactory() {
		return m_factory;
	}

	/**
	 * Set the server session object factory
	 *
	 * @param factory SrvSessionFactory
	 */
	public static final void setFactory(SrvSessionFactory factory) {
		m_factory = factory;
	}

	/**
	 * Create a new server session instance
	 *
	 * @param handler PacketHandler
	 * @param server  SMBServer
	 * @param sessId  int
	 * @return SMBSrvSession
	 */
	public static final SMBSrvSession createSession(PacketHandler handler, SMBServer server, int sessId) {
		return m_factory.createSession(handler, server, sessId);
	}

	/**
	 * Check if an asynchronous read is queued/being processed by this session
	 *
	 * @return boolean
	 */
	public final boolean hasReadInProgress() {
		return m_asyncRead;
	}

	/**
	 * Set/clear the read in progress flag
	 *
	 * @param inProgress boolean
	 */
	public final void setReadInProgress(boolean inProgress) {
		m_asyncRead = inProgress;
	}

	/**
	 * Indicate that SMB filesystem searches are not case sensitive
	 *
	 * @return boolean
	 */
	public boolean useCaseSensitiveSearch() {
		return false;
	}

	/**
	 * Are pseudo files enabled for this session?
	 *
	 * @return true
	 */
	public boolean isPseudoFilesEnabled() {
		return true;
	}

	/**
	 * Disconnect other client sessions from the same address/client
	 *
	 * @return int
	 */
	public final int disconnectClientSessions() {

		// Check the session list for other sessions from this client address
		SrvSessionList sessList = getSMBServer().getSessions();
		int discCnt = 0;

		if (sessList != null) {

			// Search for sessions matching the clients address/name
			Enumeration<SrvSession> enumSess = sessList.enumerateSessions();
			boolean addrMatch = false;
			String addrStr = null;

			while (enumSess.hasMoreElements()) {

				// Get the current session
				SMBSrvSession curSess = (SMBSrvSession) enumSess.nextElement();
				addrMatch = false;

				// Check for an address/client name match
				InetAddress address = curSess.getRemoteAddress();
				List<String> terminalServerList = getSMBServer().getSMBConfiguration().getTerminalServerList();
				List<String> loadBalancerList = getSMBServer().getSMBConfiguration().getLoadBalancerList();

				boolean disableCheckLoadBalancer = true;
				if (loadBalancerList != null && loadBalancerList.size() > 0) {
					disableCheckLoadBalancer = !loadBalancerList.contains(address.getHostAddress());
				}

				boolean disableCheckTerminalServer = true;
				if (terminalServerList != null && terminalServerList.size() > 0) {
					disableCheckTerminalServer = !terminalServerList.contains(address.getHostAddress());
				}

				if (curSess.getSessionId() != getSessionId() && disableCheckLoadBalancer && disableCheckTerminalServer) {

					// Check the IP address and userName
					boolean userNameIsTheSame = false;
					if (hasClientInformation() && curSess.hasClientInformation()) {
						String userName = getClientInformation().getUserName();
						String userNameSess = curSess.getClientInformation().getUserName();

						if (userName != null && userName.equals(userNameSess)) {
							userNameIsTheSame = true;
						}
					}

					if (hasRemoteAddress() && curSess.hasRemoteAddress()) {

						// Check if the IP addresses match
						if (getRemoteAddress().equals(curSess.getRemoteAddress()) && userNameIsTheSame) {
							addrMatch = true;
							addrStr = getRemoteAddress().getHostAddress();
						}
					}
					else if (hasRemoteName() && curSess.hasRemoteName()) {

						// Check if the remote NetBIOS names match
						if (getRemoteName().equals(curSess.getRemoteName()) && userNameIsTheSame) {
							addrMatch = true;
							addrStr = getRemoteName();
						}
					}
				}

				// Check if the session matches the current session address/client name
				if (addrMatch == true) {

					// DEBUG
					if (Debug.EnableInfo && hasDebug(DBG_NEGOTIATE))
						debugPrintln("Close existing session sess=" + curSess + "addr=" + addrStr);

					// Disconnect the existing session
					curSess.closeSession();

					// Update the disconnected session count
					discCnt++;
				}
			}
		}

		// Return the count of sessions disconnected
		return discCnt;
	}

	/**
	 * Dump the setup objects
	 */
	public final void dumpSetupObjects() {

	    if ( m_setupObjects != null) {

	        // Output the setup object details
            debugPrintln("Setup objects:");
	        Enumeration<Integer> enumPids = m_setupObjects.keys();

	        while ( enumPids.hasMoreElements()) {

	            // Get the current PIDs setup objects
                Integer pid = enumPids.nextElement();
                EnumMap<SetupObjectType, Object> setupObjs = m_setupObjects.get( pid);

                // Output the details
                debugPrintln(" PID=" + pid + ", setupObjs=" + setupObjs);
            }
        }
        else
            debugPrintln("No setup objects");
	}

	/**
	 * Dump the session keys
	 */
	public final void dumpSessionKeys() {

	    if ( m_sessionKeys != null) {

	        // Output the list of session keys
            debugPrintln("Session keys:");
            Iterator<String> keys = m_sessionKeys.keySet().iterator();

            while ( keys.hasNext())
                debugPrintln(" " + keys.next());
        }
        else
            debugPrintln("No session keys");
	}
}
