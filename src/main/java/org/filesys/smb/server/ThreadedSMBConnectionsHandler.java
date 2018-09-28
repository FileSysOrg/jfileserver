/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
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
import java.util.ArrayList;
import java.util.List;

import org.filesys.debug.Debug;
import org.filesys.netbios.win32.Win32NetBIOS;
import org.filesys.server.SessionHandlerInterface;
import org.filesys.server.SessionHandlerList;
import org.filesys.server.SocketSessionHandler;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.smb.mailslot.HostAnnouncer;
import org.filesys.smb.mailslot.TcpipNetBIOSHostAnnouncer;
import org.filesys.smb.mailslot.win32.Win32NetBIOSHostAnnouncer;
import org.filesys.smb.server.win32.LanaListener;
import org.filesys.smb.server.win32.Win32NetBIOSLanaMonitor;
import org.filesys.smb.server.win32.Win32NetBIOSSessionSocketHandler;

/**
 * Threaded SMB Connectins Handler Class
 * 
 * <p>Create a seperate thread for each enabled SMB session handler.
 * 
 * @author gkspencer
 */
public class ThreadedSMBConnectionsHandler implements SMBConnectionsHandler {

	// Constants
	//
	// Default LANA offline polling interval
	public static final long LANAPollingInterval = 5000; // 5 seconds
	
	// List of session handlers that are waiting for incoming requests
	private SessionHandlerList m_handlerList;
	
	// List of host announcers
	private List<HostAnnouncer> m_hostAnnouncers;
	
	// SMB server
	private SMBServer m_server;
	
	// Debug output
	private boolean m_debug;
	
	/**
	 * Class constructor
	 */
	public ThreadedSMBConnectionsHandler() {
		m_handlerList    = new SessionHandlerList();
		m_hostAnnouncers = new ArrayList<HostAnnouncer>();
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
	 * Initialize the connections handler
	 * 
	 * @param srv SMBServer
	 * @param config SMBConfigSection
	 * @exception InvalidConfigurationException Failed to initialize the connection handler
	 */
	public void initializeHandler( SMBServer srv, SMBConfigSection config)
		throws InvalidConfigurationException {

		// Save the server
		m_server = srv;
		
		// Check if the socket connection debug flag is enabled
		if ( (config.getSessionDebugFlags() & SMBSrvSession.DBG_SOCKET) != 0)
			m_debug = true;

		// Create the NetBIOS session socket handler, if enabled
		if ( config.hasNetBIOSSMB()) {

			// Create the TCP/IP NetBIOS SMB session handler(s), and host announcer(s)
			SocketSessionHandler sessHandler = new NetBIOSSessionSocketHandler( srv, config.getSessionPort(), config.getSMBBindAddress(), hasDebug());
			sessHandler.setSocketTimeout( config.getSocketTimeout());
			
			try {
				
				// Initialize the NetBIOS session handler
				sessHandler.initializeSessionHandler( srv);
				m_handlerList.addHandler( sessHandler);

				// DEBUG
				if ( Debug.EnableError && hasDebug())
					Debug.println("[SMB] TCP NetBIOS session handler created");

				// Check if a host announcer should be created
				if ( config.hasEnableAnnouncer()) {

					// Create the TCP NetBIOS host announcer
					TcpipNetBIOSHostAnnouncer announcer = new TcpipNetBIOSHostAnnouncer();

					// Set the host name to be announced
					announcer.addHostName(config.getServerName());
					announcer.setDomain(config.getDomainName());
					announcer.setComment(config.getComment());
					announcer.setBindAddress(config.getSMBBindAddress());
					if ( config.getHostAnnouncerPort() != 0)
						announcer.setPort(config.getHostAnnouncerPort());

					// Check if there are alias names to be announced
					if ( config.hasAliasNames())
						announcer.addHostNames(config.getAliasNames());

					// Set the announcement interval
					if ( config.getHostAnnounceInterval() > 0)
						announcer.setInterval(config.getHostAnnounceInterval());

					try {
						announcer.setBroadcastAddress(config.getBroadcastMask());
					}
					catch (Exception ex) {
					}

					// Set the server type flags
					announcer.setServerType(config.getServerType());

					// Enable debug output
					if ( config.hasHostAnnounceDebug())
						announcer.setDebug(true);

					// Add the announcer to the list
					m_hostAnnouncers.add( announcer);

					// DEBUG
					if ( Debug.EnableError && hasDebug())
						Debug.println("[SMB] TCP NetBIOS host announcer created");
				}
			}
			catch (IOException ex) {
			}
		}

		// Create the TCP/IP SMB session socket handler, if enabled
		if ( config.hasTcpipSMB()) {

			// Create the TCP/IP native SMB session handler(s)
			SocketSessionHandler sessHandler = new TcpipSMBSessionSocketHandler( srv, config.getTcpipSMBPort(), config.getSMBBindAddress(), hasDebug());
			sessHandler.setSocketTimeout( config.getSocketTimeout());

			try {
				
				// Initialize the native SMB handler
				sessHandler.initializeSessionHandler( srv);
				m_handlerList.addHandler( sessHandler);

				// DEBUG
				if ( Debug.EnableError && hasDebug())
					Debug.println("[SMB] Native SMB TCP session handler created");
			}
			catch ( IOException ex) {
				throw new InvalidConfigurationException( "Error initializing session handler, " + ex.getMessage());
			}
		}

		// Create the Win32 NetBIOS session handler, if enabled
		if ( config.hasWin32NetBIOS()) {

			// Only enable if running under Windows
			if ( isWindowsNTOnwards()) {

				// DEBUG
				if ( Debug.EnableInfo && hasDebug()) {
					List<Integer> lanas = Win32NetBIOS.LanaEnumerate();

					StringBuffer lanaStr = new StringBuffer();
					if ( lanas != null && lanas.size() > 0) {
						for (int i = 0; i < lanas.size(); i++) {
							lanaStr.append(Integer.toString(lanas.get( i)));
							lanaStr.append(" ");
						}
					}
					Debug.println("[SMB] Win32 NetBIOS Available LANAs: " + lanaStr.toString());
				}

				// Check if the Win32 NetBIOS session handler should use a particular LANA/network adapter
				// or should use all available LANAs/network adapters (that have NetBIOS enabled).
				Win32NetBIOSSessionSocketHandler sessHandler = null;
				List<Win32NetBIOSSessionSocketHandler> lanaListeners = new ArrayList<Win32NetBIOSSessionSocketHandler>();

				if ( config.getWin32LANA() != -1) {

					// Create a single Win32 NetBIOS session handler using the specified LANA
					sessHandler = new Win32NetBIOSSessionSocketHandler(srv, config.getWin32LANA(), hasDebug());

					try {
						
						// Initialize the Win32 JNI session handler
						sessHandler.initializeSessionHandler( srv);
						m_handlerList.addHandler( sessHandler);

						// DEBUG
						if ( Debug.EnableInfo && hasDebug())
							Debug.println("[SMB] Win32 NetBIOS created session handler on LANA " + config.getWin32LANA());

					}
					catch (Exception ex) {

						// DEBUG
						if ( Debug.EnableError && hasDebug()) {
							Debug.println("[SMB] Win32 NetBIOS failed to create session handler for LANA " + config.getWin32LANA());
							Debug.println("      " + ex.getMessage());
						}
					}

					// Check if a host announcer should be enabled
					if ( config.hasWin32EnableAnnouncer()) {

						// Create a host announcer
						Win32NetBIOSHostAnnouncer hostAnnouncer = new Win32NetBIOSHostAnnouncer(sessHandler, config.getDomainName(), config.getWin32HostAnnounceInterval());
						hostAnnouncer.setDebug( hasDebug());

						// Add the host announcer to the SMB server list
						m_hostAnnouncers.add( hostAnnouncer);

						// DEBUG
						if ( Debug.EnableInfo && hasDebug())
							Debug.println("[SMB] Win32 NetBIOS host announcer enabled on LANA " + config.getWin32LANA());
					}

					// Check if the session handler implements the LANA listener interface
					if ( sessHandler instanceof LanaListener)
						lanaListeners.add(sessHandler);
				}
				else {

					// Get a list of the available LANAs
					List<Integer> lanas = Win32NetBIOS.LanaEnumerate();

					if ( lanas != null && lanas.size() > 0) {

						// Create a session handler for each available LANA
						for (int i = 0; i < lanas.size(); i++) {

							// Get the current LANA
							int lana = lanas.get( i);

							// Create a session handler
							sessHandler = new Win32NetBIOSSessionSocketHandler( srv, lana, hasDebug());

							try {
								
								// Initialize the Win32 JNI session handler
								sessHandler.initializeSessionHandler( srv);
								m_handlerList.addHandler( sessHandler);

								// DEBUG
								if ( Debug.EnableError && hasDebug())
									Debug.println("[SMB] Win32 NetBIOS created session handler on LANA " + lana);

							}
							catch (Exception ex) {

								// DEBUG
								if ( Debug.EnableError && hasDebug()) {
									Debug.println("[SMB] Win32 NetBIOS failed to create session handler for LANA " + lana);
									Debug.println("      " + ex.getMessage());
								}
							}

							// Check if a host announcer should be enabled
							if ( config.hasWin32EnableAnnouncer()) {

								// Create a host announcer
								Win32NetBIOSHostAnnouncer hostAnnouncer = new Win32NetBIOSHostAnnouncer(sessHandler, config.getDomainName(), config.getWin32HostAnnounceInterval());
								hostAnnouncer.setDebug( hasDebug());

								// Add the host announcer to the SMB server list
								m_hostAnnouncers.add( hostAnnouncer);

								// DEBUG
								if ( Debug.EnableInfo && hasDebug())
									Debug.println("[SMB] Win32 NetBIOS host announcer enabled on LANA " + lana);
							}

							// Check if the session handler implements the LANA listener interface
							if ( sessHandler instanceof LanaListener)
								lanaListeners.add(sessHandler);
						}
					}

					// Create a LANA monitor to check for new LANAs becoming available
					Win32NetBIOSLanaMonitor lanaMonitor = new Win32NetBIOSLanaMonitor( srv, lanas, LANAPollingInterval, hasDebug());

					// Register any session handlers that are LANA listeners
					if ( lanaListeners.size() > 0) {

						for (int i = 0; i < lanaListeners.size(); i++) {

							// Get the current LANA listener
							Win32NetBIOSSessionSocketHandler handler = lanaListeners.get(i);

							// Register the LANA listener
							lanaMonitor.addLanaListener(handler.getLANANumber(), handler);
						}
					}
				}
			}
		}
	}
	
	/**
	 *  Return the count of active session handlers
	 *  
	 *  @return int
	 */
	public int numberOfSessionHandlers() {
		return m_handlerList.numberOfHandlers();
	}
	
	/**
	 * Start the connection handler thread
	 */
	public void startHandler() {
		
		// Start each session handler in a seperate thread
		for ( int idx = 0; idx < m_handlerList.numberOfHandlers(); idx++) {
			
			SessionHandlerInterface sessHandler = m_handlerList.getHandlerAt( idx);
			
			if ( sessHandler instanceof Runnable) {
				
				// Start the session handler in a seperate thread
				Thread handlerThread = new Thread(( Runnable) sessHandler);
				handlerThread.setName( "SMBSessHandler_" + sessHandler.getHandlerName());
				handlerThread.start();
				
				// DEBUG
				if ( Debug.EnableInfo && hasDebug())
					Debug.println("[SMB] Created session handler thread " + handlerThread.getName());
			}
		}
		
		// Start the host announcer(s)
		if ( m_hostAnnouncers.size() > 0) {
			
			for ( int idx = 0; idx < m_hostAnnouncers.size(); idx++) {

				// Get the current host announcer
				HostAnnouncer hostAnnouncer = (HostAnnouncer) m_hostAnnouncers.get( idx);
				hostAnnouncer.startAnnouncer();
				
				// DEBUG
				if ( Debug.EnableInfo && hasDebug())
					Debug.println( "[SMB] Started host announcer " + hostAnnouncer.getName());
			}
		}
	}

	/**
	 * Stop the connections handler
	 */
	public void stopHandler() {
		
		// Stop each session handler
		for ( int idx = 0; idx < m_handlerList.numberOfHandlers(); idx++) {
			
			SessionHandlerInterface sessHandler = m_handlerList.getHandlerAt( idx);
			sessHandler.closeSessionHandler( m_server);
				
			// DEBUG
			if ( Debug.EnableInfo && hasDebug())
				Debug.println("[SMB] Shutting down session handler thread " + sessHandler.getHandlerName());
		}
		
		// Stop the host announcer(s)
		if ( m_hostAnnouncers.size() > 0) {
			
			for ( int idx = 0; idx < m_hostAnnouncers.size(); idx++) {

				// Get the current host announcer
				HostAnnouncer hostAnnouncer = (HostAnnouncer) m_hostAnnouncers.get( idx);
				hostAnnouncer.shutdownAnnouncer();
				
				// DEBUG
				if ( Debug.EnableInfo && hasDebug())
					Debug.println( "[SMB] Shutting down host announcer " + hostAnnouncer.getName());
			}
		}
	}
	
	/**
	 * Determine if we are running under Windows NT onwards
	 * 
	 * @return boolean
	 */
	private final boolean isWindowsNTOnwards() {

		// Get the operating system name property
		String osName = System.getProperty("os.name");

		if ( osName.startsWith("Windows")) {
			if ( osName.endsWith("95") || osName.endsWith("98") || osName.endsWith("ME")) {

				// Windows 95-ME
				return false;
			}

			// Looks like Windows NT onwards
			return true;
		}

		// Not Windows
		return false;
	}
}
