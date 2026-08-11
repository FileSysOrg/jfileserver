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
import org.filesys.netbios.NetworkSettings;
import org.filesys.server.SessionHandlerInterface;
import org.filesys.server.SessionHandlerList;
import org.filesys.server.SocketSessionHandler;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.smb.mailslot.HostAnnouncer;
import org.filesys.smb.mailslot.TcpipNetBIOSHostAnnouncer;
import org.filesys.util.PlatformType;

/**
 * Threaded SMB Connectins Handler Class
 * 
 * <p>Create a seperate thread for each enabled SMB session handler.
 * 
 * @author gkspencer
 */
public class ThreadedSMBConnectionsHandler implements SMBConnectionsHandler {

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
		if ( config.getSessionDebugFlags().contains( SMBSrvSession.Dbg.SOCKET))
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
						NetworkSettings.setBroadcastMask(config.getBroadcastMask());
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

		// Create the Win32 NetBIOS session handler, if enabled and available on the classpath
		if ( config.hasWin32NetBIOS()) {

			// Only enable if running under Windows
			if ( PlatformType.isWindowsNTOnwards()) {

			    // Check if the Win32 NetBIOS based connections handler is available
                SMBChildConnectionHandler win32Handler = null;

                try {
                    win32Handler = (SMBChildConnectionHandler) Class.forName( "org.filesys.smb.server.win32.Win32NetBIOSSMBConnectionsHandler").newInstance();
                }
                catch ( IllegalAccessException ex) {
                }
                catch ( InstantiationException ex) {
                }
                catch ( ClassNotFoundException ex) {
                }

                // If the Win32 NetBIOS handler is valid then add the Win32 NetBIOS handlers, announcers, LANA monitors
                if ( win32Handler != null) {

                    // Add the Win32 NetBIOS handler
                    win32Handler.initializeHandler( srv, config, this);
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
	 * Add a host announcer to the connections handler
	 *
	 * @param announcer HostAnnouncer
	 */
	public void addHostAnnouncer(HostAnnouncer announcer) {
		m_hostAnnouncers.add( announcer);
	}

	/**
	 * Add a session handler to the connections handler
	 *
	 * @param sessHandler SessionHandlerInterface
	 */
	public void addSessionHandler(SessionHandlerInterface sessHandler) {
		m_handlerList.addHandler( sessHandler);
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
}
