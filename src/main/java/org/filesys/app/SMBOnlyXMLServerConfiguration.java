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

package org.filesys.app;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.filesys.audit.Audit;
import org.filesys.audit.AuditConfigSection;
import org.filesys.audit.AuditGroup;
import org.filesys.debug.Debug;
import org.filesys.debug.DebugConfigSection;
import org.filesys.netbios.server.LANAMapper;
import org.filesys.server.auth.ISMBAuthenticator;
import org.filesys.server.auth.UserAccount;
import org.filesys.server.auth.UserAccountList;
import org.filesys.server.auth.acl.ACLParseException;
import org.filesys.server.auth.acl.AccessControl;
import org.filesys.server.auth.acl.AccessControlList;
import org.filesys.server.auth.acl.AccessControlParser;
import org.filesys.server.auth.acl.InvalidACLTypeException;
import org.filesys.server.config.CoreServerConfigSection;
import org.filesys.server.config.GlobalConfigSection;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.SecurityConfigSection;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.server.core.DeviceContextException;
import org.filesys.server.core.ShareType;
import org.filesys.server.core.SharedDeviceList;
import org.filesys.server.filesys.DiskDeviceContext;
import org.filesys.server.filesys.DiskInterface;
import org.filesys.server.filesys.DiskSharedDevice;
import org.filesys.server.filesys.FilesystemsConfigSection;
import org.filesys.server.filesys.SrvDiskInfo;
import org.filesys.server.filesys.VolumeInfo;
import org.filesys.server.filesys.cache.FileStateCache;
import org.filesys.server.filesys.cache.StandaloneFileStateCache;
import org.filesys.server.thread.ThreadRequestPool;
import org.filesys.smb.Dialect;
import org.filesys.smb.DialectSelector;
import org.filesys.smb.server.SMBConfigSection;
import org.filesys.smb.server.SMBSrvSession;
import org.filesys.smb.server.SMBV1VirtualCircuitList;
import org.filesys.smb.util.DriveMapping;
import org.filesys.smb.util.DriveMappingList;
import org.filesys.util.*;
import org.filesys.util.PlatformType;
import org.springframework.extensions.config.ConfigElement;
import org.springframework.extensions.config.element.GenericConfigElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * SMB Only XML File Server Configuration Class
 * 
 * <p>
 * XML implementation of the SMB server configuration.
 * 
 * @author gkspencer
 */
public class SMBOnlyXMLServerConfiguration extends ServerConfiguration {

	// Constants
	//
	// Node type for an Element
	private static final int ELEMENT_TYPE = 1;

	// Valid drive letter names for mapped drives
	private static final String _driveLetters = "CDEFGHIJKLMNOPQRSTUVWXYZ";

	// Default thread pool size
	private static final int DefaultThreadPoolInit	= 25;
	private static final int DefaultThreadPoolMax	= 50;
	
	// Default memory pool settings
	private static final int[] DefaultMemoryPoolBufSizes  = { 256, 4096, 16384, 66000 };
	private static final int[] DefaultMemoryPoolInitAlloc = {  20,   20,     5,     5 };
	private static final int[] DefaultMemoryPoolMaxAlloc  = { 100,   50,    50,    50 };

	// Memory pool packet size limits
	private static final int MemoryPoolMinimumPacketSize	= 256;
    private static final int MemoryPoolMaximumPacketSize	= 128 * (int) MemorySize.KILOBYTE;

	// Memory pool allocation limits
	private static final int MemoryPoolMinimumAllocation	= 5;
	private static final int MemoryPoolMaximumAllocation    = 500;
	
	// Maximum session timeout
	private static final int MaxSessionTimeout				= 60 * 60;	// 1 hour
	
	// Date formatter
	private SimpleDateFormat m_dateFmt = new SimpleDateFormat("dd-MMM-yyyy hh:mm:ss");

	// Pattern match for environment variable tokens
    private Pattern m_envTokens = Pattern.compile("\\$\\{[a-zA-Z0-9_\\.]+\\}");

	/**
	 * Default constructor
	 */
	public SMBOnlyXMLServerConfiguration() {
		super("");
	}

	/**
	 * Load the configuration from the specified file.
	 * 
	 * @param fname String
	 * @exception IOException Error opening the configuration file
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	public final void loadConfiguration(String fname)
		throws IOException, InvalidConfigurationException {

		// Open the configuration file
		InputStream inFile = new FileInputStream(fname);
		Reader inRead = new InputStreamReader(inFile);

		// Call the main parsing method
		loadConfiguration(inRead);
	}

	/**
	 * Load the configuration from the specified input stream
	 * 
	 * @param in Reader
	 * @exception IOException Error reading the configuration stream
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	public final void loadConfiguration(Reader in)
		throws IOException, InvalidConfigurationException {

		// Reset the current configuration to the default settings
		removeAllConfigSections();

		// Load and parse the XML configuration document
		try {

			// Load the configuration from the XML file
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();

			InputSource xmlSource = new InputSource(in);
			Document doc = builder.parse(xmlSource);

			// Parse the document
			loadConfiguration(doc);
		}
		catch (Exception ex) {

			// Rethrow the exception as a configuration exeception
			throw new InvalidConfigurationException("XML error", ex);
		}
		finally {

			// Close the input file
			in.close();
		}
	}

	/**
	 * Load the configuration from the specified document
	 * 
	 * @param doc Document
	 * @exception IOException Error reading the configuration document
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	public void loadConfiguration(Document doc)
		throws IOException, InvalidConfigurationException {

		// Reset the current configuration to the default settings
		removeAllConfigSections();

		// Parse the XML configuration document
		try {

			// Access the root of the XML document, get a list of the child nodes
			Element root = doc.getDocumentElement();
			NodeList childNodes = root.getChildNodes();

			// Process the debug settings element
			procDebugElement(findChildNode("debug", childNodes));

			// Process the audit configuration section, if available
			procAuditElement( findChildNode( "audit", childNodes));

			// Process the core server configuration settings
			procServerCoreElement(findChildNode("server-core", childNodes));
			
			// Process the global configuration settings
			procGlobalElement(findChildNode("global", childNodes));

			// Process the security element
			procSecurityElement(findChildNode("security", childNodes));

			// Process the shares element
			procSharesElement(findChildNode("shares", childNodes));

			// Process the SMB server specific settings
			procSMBServerElement(findChildNode("SMB", childNodes));

			// Process the drive mappings settings
			procDriveMappingsElement(findChildNode("DriveMappings", childNodes));

			// Process any extension sections that may have been added to the configuration
			procExtensions( childNodes);
		}
		catch (Exception ex) {

			// Rethrow the exception as a configuration exception
			throw new InvalidConfigurationException("XML error", ex);
		}
	}

	/**
	 * Process any extension sections that may have been added to the configuration
	 *
	 * @param nodeList NodeList
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected void procExtensions( NodeList nodeList)
		throws InvalidConfigurationException {

		// To be overridden
	}

	/**
	 * Process the server core settings XML element
	 * 
	 * @param srvCore Element
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected void procServerCoreElement(Element srvCore)
		throws InvalidConfigurationException {

		// Create the core server configuration section
		CoreServerConfigSection coreConfig = new CoreServerConfigSection(this);

		// Check if the server core element has been specified
		if ( srvCore == null) {
			
			// Configure a default memory pool
			coreConfig.setMemoryPool( getMemoryBufferSizes(), getMemoryBufferAllocations(), getMemoryBufferMaximumAllocations());
			
			// Configure a default thread pool size
			coreConfig.setThreadPool( getDefaultThreads(), getMaximumThreads());
			return;
		}

		// Check if the thread pool size has been specified
		Element elem = findChildNode("threadPool", srvCore.getChildNodes());
		if ( elem != null) {
			
			// Get the initial thread pool size
			String initSizeStr = elem.getAttribute("init");
			if ( initSizeStr == null || initSizeStr.length() == 0)
				throw new InvalidConfigurationException("Thread pool initial size not specified");
			
			// Validate the initial thread pool size
			int initSize = 0;
			
			try {
				initSize = Integer.parseInt( initSizeStr);
			}
			catch (NumberFormatException ex) {
				throw new InvalidConfigurationException("Invalid thread pool size value, " + initSizeStr);
			}
			
			// Range check the thread pool size
			if ( initSize < ThreadRequestPool.MinimumWorkerThreads)
				throw new InvalidConfigurationException("Thread pool size below minimum allowed size");
			
			if ( initSize > ThreadRequestPool.MaximumWorkerThreads)
				throw new InvalidConfigurationException("Thread pool size above maximum allowed size");
			
			// Get the maximum thread pool size
			String maxSizeStr = elem.getAttribute("max");
			int maxSize = initSize;
			
			if ( maxSizeStr.length() > 0) {
				
				// Validate the maximum thread pool size
				try {
					maxSize = Integer.parseInt( maxSizeStr);
				}
				catch (NumberFormatException ex) {
					throw new InvalidConfigurationException(" Invalid thread pool maximum size value, " + maxSizeStr);
				}
				
				// Range check the maximum thread pool size
				if ( maxSize < ThreadRequestPool.MinimumWorkerThreads)
					throw new InvalidConfigurationException("Thread pool maximum size below minimum allowed size");
				
				if ( maxSize > ThreadRequestPool.MaximumWorkerThreads)
					throw new InvalidConfigurationException("Thread pool maximum size above maximum allowed size");
				
				if ( maxSize < initSize)
					throw new InvalidConfigurationException("Initial size is larger than maximum size");
			}
			else if ( maxSizeStr != null)
				throw new InvalidConfigurationException("Thread pool maximum size not specified");
			
			// Configure the thread pool
			coreConfig.setThreadPool( initSize, maxSize);
		}
		else {
			
			// Configure a default thread pool size
			coreConfig.setThreadPool( getDefaultThreads(), getMaximumThreads());
		}
		
		// Check if thread pool debug output is enabled
		if ( findChildNode("threadPoolDebug", srvCore.getChildNodes()) != null)
			coreConfig.getThreadPool().setDebug( true);
		
		// Check if the memory pool configuration has been specified
		elem = findChildNode("memoryPool", srvCore.getChildNodes());
		if ( elem != null) {
			
			// Check if the packet sizes/allocations have been specified
			Element pktElem = findChildNode("packetSizes", elem.getChildNodes());
			if ( pktElem != null) {

				// Calculate the array size for the packet size/allocation arrays
				NodeList nodeList = pktElem.getChildNodes();
				int elemCnt = 0;
				
				for ( int i = 0; i < nodeList.getLength(); i++) {
					if ( nodeList.item( i).getNodeType() == ELEMENT_TYPE)
						elemCnt++;
				}
				
				// Create the packet size, initial allocation and maximum allocation arrays
				int[] pktSizes  = new int[elemCnt];
				int[] initSizes = new int[elemCnt];
				int[] maxSizes  = new int[elemCnt];
				
				int elemIdx = 0;
				
				// Process the packet size elements
				for ( int i = 0; i < nodeList.getLength(); i++) {
					
					// Get the current element node
					Node curNode = nodeList.item( i);
					if ( curNode.getNodeType() == ELEMENT_TYPE) {
						
						// Get the element and check if it is a packet size element
						Element curElem = (Element) curNode;
						if ( curElem.getNodeName().equals("packet")) {
							
							// Get the packet size
							int pktSize   = -1;
							int initAlloc = -1;
							int maxAlloc  = -1;
							
							String pktSizeStr = curElem.getAttribute("size");
							if ( pktSizeStr == null || pktSizeStr.length() == 0)
								throw new InvalidConfigurationException("Memory pool packet size not specified");
							
							// Parse the packet size
							try {
								pktSize = MemorySize.getByteValueInt( pktSizeStr);
							}
							catch ( NumberFormatException ex) {
								throw new InvalidConfigurationException("Memory pool packet size, invalid size value, " + pktSizeStr);
							}

							// Make sure the packet sizes have been specified in ascending order
							if ( elemIdx > 0 && pktSizes[elemIdx - 1] >= pktSize)
								throw new InvalidConfigurationException("Invalid packet size specified, less than/equal to previous packet size");
							
							// Get the initial allocation for the current packet size
							String initSizeStr = curElem.getAttribute("init");
							if ( initSizeStr == null || initSizeStr.length() == 0)
								throw new InvalidConfigurationException("Memory pool initial allocation not specified");
							
							// Parse the initial allocation
							try {
								initAlloc = Integer.parseInt( initSizeStr);
							}
							catch (NumberFormatException ex) {
								throw new InvalidConfigurationException("Invalid initial allocation, " + initSizeStr);
							}
							
							// Range check the initial allocation
							if ( initAlloc < MemoryPoolMinimumAllocation)
								throw new InvalidConfigurationException("Initial memory pool allocation below minimum of " + MemoryPoolMinimumAllocation);
							
							if ( initAlloc > MemoryPoolMaximumAllocation)
								throw new InvalidConfigurationException("Initial memory pool allocation above maximum of " + MemoryPoolMaximumAllocation);
							
							// Get the maximum allocation for the current packet size
							String maxSizeStr = curElem.getAttribute("max");
							if ( maxSizeStr == null || maxSizeStr.length() == 0)
								throw new InvalidConfigurationException("Memory pool maximum allocation not specified");
							
							// Parse the maximum allocation
							try {
								maxAlloc = Integer.parseInt( maxSizeStr);
							}
							catch (NumberFormatException ex) {
								throw new InvalidConfigurationException("Invalid maximum allocation, " + maxSizeStr);
							}

							// Range check the maximum allocation
							if ( maxAlloc < MemoryPoolMinimumAllocation)
								throw new InvalidConfigurationException("Maximum memory pool allocation below minimum of " + MemoryPoolMinimumAllocation);
							
							if ( initAlloc > MemoryPoolMaximumAllocation)
								throw new InvalidConfigurationException("Maximum memory pool allocation above maximum of " + MemoryPoolMaximumAllocation);

							// Set the current packet size elements
							pktSizes[elemIdx]  = pktSize;
							initSizes[elemIdx] = initAlloc;
							maxSizes[elemIdx]  = maxAlloc;
							
							elemIdx++;
						}
					}
						
				}
				
				// Check if all elements were used in the packet size/allocation arrays
				if ( elemIdx < pktSizes.length) {
					
					// Re-allocate the packet size/allocation arrays
					int[] newPktSizes  = new int[elemIdx];
					int[] newInitSizes = new int[elemIdx];
					int[] newMaxSizes  = new int[elemIdx];
					
					// Copy the values to the shorter arrays
					System.arraycopy(pktSizes, 0, newPktSizes, 0, elemIdx);
					System.arraycopy(initSizes, 0, newInitSizes, 0, elemIdx);
					System.arraycopy(maxSizes, 0, newMaxSizes, 0, elemIdx);
					
					// Move the new arrays into place
					pktSizes  = newPktSizes;
					initSizes = newInitSizes;
					maxSizes  = newMaxSizes;
				}
				
				// Configure the memory pool
				coreConfig.setMemoryPool( pktSizes, initSizes, maxSizes);
			}
		}
		else {
			
			// Configure a default memory pool
			coreConfig.setMemoryPool( getMemoryBufferSizes(), getMemoryBufferAllocations(), getMemoryBufferMaximumAllocations());
		}
	}

	/**
	 * Process the global settings XML element
	 * 
	 * @param global Element
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected void procGlobalElement(Element global)
		throws InvalidConfigurationException {

		// Create the global configuration section

		GlobalConfigSection globalConfig = new GlobalConfigSection(this);

		// Check if the global element has been specified
		if ( global == null)
			return;

		// Check if the timezone has been specified
		Element elem = findChildNode("timezone", global.getChildNodes());
		if ( elem != null) {

			// Check for the timezone name
			String tzName = elem.getAttribute("name");
			if ( tzName != null && tzName.length() > 0)
				globalConfig.setTimeZone(tzName);

			// Check for the timezone offset value
			String tzOffset = elem.getAttribute("offset");
			if ( tzOffset != null && tzOffset.length() > 0 && tzName != null && tzName.length() > 0)
				throw new InvalidConfigurationException("Specify name or offset for timezone");

			// Validate the timezone offset
			if ( tzOffset != null && tzOffset.length() > 0) {
				int offset = 0;

				try {
					offset = Integer.parseInt(tzOffset);
				}
				catch (NumberFormatException ex) {
					throw new InvalidConfigurationException("Invalid timezone offset value, " + tzOffset);
				}

				// Range check the timezone offset value
				if ( offset < -1440 || offset > 1440)
					throw new InvalidConfigurationException("Invalid timezone offset, value out of valid range, " + tzOffset);

				// Set the timezone offset in minutes from UTC
				globalConfig.setTimeZoneOffset(offset);
			}
		}
	}

	/**
	 * Process the SMB server XML element
	 * 
	 * @param smb Element
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected void procSMBServerElement(Element smb)
		throws InvalidConfigurationException {

		// Check if the SMB element is valid
		if ( smb == null)
			throw new InvalidConfigurationException("SMB section must be specified");

		// Create the SMB server configuration section
		SMBConfigSection smbConfig = new SMBConfigSection(this);

		// Process the main SMB server settings
		procHostElement(findChildNode("host", smb.getChildNodes()), smbConfig);

		// Debug settings are now specified within the SMB server configuration block
		//
		// Check if NetBIOS debug is enabled
		Element elem = findChildNode("netbiosDebug", smb.getChildNodes());
		if ( elem != null)
			smbConfig.setNetBIOSDebug(true);

		// Check if host announcement debug is enabled
		elem = findChildNode("announceDebug", smb.getChildNodes());
		if ( elem != null)
			smbConfig.setHostAnnounceDebug(true);

		// Check if session debug is enabled
        procSessionDebugElement( findChildNode("sessionDebug", smb.getChildNodes()), smbConfig);

		// Check if NIO based code should be disabled
		if ( findChildNode( "disableNIO", smb.getChildNodes()) != null)
			smbConfig.setDisableNIOCode( true);
		
		// Check if the HashedOpenFileMap should be disabled (and ArrayOpenFileMap used
		// instead)
		if (findChildNode("disableHashedOpenFileMap", smb.getChildNodes()) != null)
			smbConfig.setDisableHashedOpenFileMap(true);

		// Check if a maximum virtual circuits per session limit has been specified
		elem = findChildNode("virtualCircuits", smb.getChildNodes());
		if ( elem != null) {
			
			// Parse and validate the maximum virtual circuits value
			String maxVCVal = elem.getAttribute( "maxPerSession");
			
			if ( maxVCVal != null && maxVCVal.length() > 0) {
				try {
					
					// Parse the value, and range check
					int maxVC = Integer.parseInt( maxVCVal);
					
					if ( maxVC < SMBV1VirtualCircuitList.MinCircuits || maxVC > SMBV1VirtualCircuitList.MaxCircuits)
						throw new InvalidConfigurationException("Maximum virtual circuits value out of range, valid range " + SMBV1VirtualCircuitList.MinCircuits + " - " +
								SMBV1VirtualCircuitList.MaxCircuits);
					
					// Set the maximum virtual circuits per session
					smbConfig.setMaximumVirtualCircuits( maxVC);
				}
				catch (NumberFormatException ex) {
					throw new InvalidConfigurationException("Invalid maximum virtual circuits value, " + maxVCVal);
				}
			}
		}

		// Check if an authenticator has been specified
		Element authElem = findChildNode("authenticator", smb.getChildNodes());
		if ( authElem != null) {

			// Get the authenticator class and security mode
			Element classElem = findChildNode("class", authElem.getChildNodes());
			String authClass = null;

			if ( classElem == null) {

				// Check if the authenticator type has been specified
				String authType = authElem.getAttribute("type");

				if ( authType == null)
					throw new InvalidConfigurationException("Authenticator class not specified");

				// Check the authenticator type and set the appropriate authenticator class
				authClass = getSMBAuthenticatorClassForType( authType);
			}
			else {

				// Set the authenticator class
				authClass = getText(classElem);
			}

			Element modeElem = findChildNode("mode", authElem.getChildNodes());
			ISMBAuthenticator.AuthMode accessMode = ISMBAuthenticator.AuthMode.USER;

			if ( modeElem != null) {

				// Validate the authenticator mode
				String mode = getText(modeElem);

				if ( mode.equalsIgnoreCase("user"))
					accessMode = ISMBAuthenticator.AuthMode.USER;
				else if ( mode.equalsIgnoreCase("share"))
					accessMode = ISMBAuthenticator.AuthMode.SHARE;
				else
					throw new InvalidConfigurationException("Invalid authentication mode, must be USER or SHARE");
			}

			// Get the allow guest setting
			Element allowGuest = findChildNode("allowGuest", authElem.getChildNodes());

			// Get the parameters for the authenticator class
			ConfigElement params = buildConfigElement(authElem);
			smbConfig.setAuthenticator(authClass, params, accessMode, allowGuest != null ? true : false);
		}

		// Check if the maximum packets per thread run has been specified
		elem = findChildNode("maxPacketsPerThreadRun", smb.getChildNodes());

		if (elem != null) {

			// Validate the maximum packets per thread run value
			String maxPkts = getTextWithEnvVars(elem);
			if (maxPkts != null && !maxPkts.isEmpty()) {
				try {

					// Convert the maximum packets to an integer value
					int maxPktsInt = Integer.parseInt(maxPkts);

					if (maxPktsInt < SMBConfigSection.MinPacketsPerRun || maxPktsInt > SMBConfigSection.MaxPacketsPerRun)
						throw new InvalidConfigurationException("Maximum packets per run out of range (" + SMBConfigSection.MinPacketsPerRun +
								" - " + SMBConfigSection.MaxPacketsPerRun + ")");

					// Set the SMB configuration value
					smbConfig.setMaximumPacketsPerThreadRun( maxPktsInt);

				} catch (NumberFormatException ex) {
					throw new InvalidConfigurationException("Invalid maximum packets per run value, " + maxPkts);
				}
			} else
				throw new InvalidConfigurationException("Maximum packets per run value not specified");
		}
	}

	/**
	 * Process the host XML element
	 * 
	 * @param host Element
	 * @param smbConfig SMBConfigSection
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected void procHostElement(Element host, SMBConfigSection smbConfig)
		throws InvalidConfigurationException {

		// Check if the host element is valid
		if ( host == null)
			throw new InvalidConfigurationException("Host section must be specified");

		// Get the host name attribute
		String attr = getAttributeWithEnvVars( host, "name");

		if ( attr == null || attr.length() == 0)
			throw new InvalidConfigurationException("Host name not specified or invalid");
		smbConfig.setServerName(attr.toUpperCase());

		// If the global server name has not been set then use the SMB server name
		if ( getServerName() == null)
			setServerName(smbConfig.getServerName());

		// Get the domain name
		attr = getAttributeWithEnvVars(host,"domain");
		if ( attr != null && attr.length() > 0)
			smbConfig.setDomainName(attr.toUpperCase());

		// Get the enabled SMB dialects
        procSMBDialectsElement(findChildNode("smbdialects", host.getChildNodes()), smbConfig);

		// Check for a server comment
		Element elem = findChildNode("comment", host.getChildNodes());

		if ( elem != null)
			smbConfig.setComment(getText(elem));

		// Check for a bind address
		elem = findChildNode("bindto", host.getChildNodes());
		if ( elem != null) {

			// Check if the network adapter name has been specified
			if ( elem.hasAttribute("adapter")) {

				// Get the IP address for the adapter
				InetAddress bindAddr = parseAdapterName(elem.getAttribute("adapter"));

				// Set the bind address for the server
				smbConfig.setSMBBindAddress(bindAddr);
			}
			else {

				// Validate the bind address
				String bindText = getText(elem);

				try {

					// Check the bind address
					InetAddress bindAddr = InetAddress.getByName(bindText);

					// Set the bind address for the server
					smbConfig.setSMBBindAddress(bindAddr);
				}
				catch (UnknownHostException ex) {
					throw new InvalidConfigurationException(ex.toString());
				}
			}
		}

		// Check if the host announcer should be enabled
		elem = findChildNode("hostAnnounce", host.getChildNodes());
		if ( elem != null) {

			// Check for an announcement interval
			attr = elem.getAttribute("interval");
			if ( attr != null && attr.length() > 0) {
				try {
					smbConfig.setHostAnnounceInterval(Integer.parseInt(attr));
				}
				catch (NumberFormatException ex) {
					throw new InvalidConfigurationException("Invalid host announcement interval");
				}
			}

			// Check if the domain name has been set, this is required if the host announcer is enabled
			if ( smbConfig.getDomainName() == null)
				throw new InvalidConfigurationException("Domain name must be specified if host announcement is enabled");

			// Enable host announcement
			smbConfig.setHostAnnouncer(true);
		}

		// Check for a host announcer port
		elem = findChildNode("HostAnnouncerPort", host.getChildNodes());
		if ( elem != null) {
			try {
				smbConfig.setHostAnnouncerPort(Integer.parseInt(getText(elem)));
				if ( smbConfig.getHostAnnouncerPort() <= 0 || smbConfig.getHostAnnouncerPort() >= 65535)
					throw new InvalidConfigurationException("Host announcer port out of valid range");
			}
			catch (NumberFormatException ex) {
				throw new InvalidConfigurationException("Invalid host announcer port");
			}
		}

		// Check if NetBIOS SMB is enabled
		elem = findChildNode("netBIOSSMB", host.getChildNodes());
		if ( elem != null) {

			// Check if NetBIOS over TCP/IP is enabled for the current platform
			boolean platformOK = false;

			if ( elem.hasAttribute("platforms")) {
				
				// Get the list of platforms
				String platformsStr = elem.getAttribute("platforms");

				// Parse the list of platforms that NetBIOS over TCP/IP is to be enabled for and check if the current
				// platform is included
				List<PlatformType.Type> enabledPlatforms = parsePlatformString(platformsStr);
				if ( enabledPlatforms.contains(getPlatformType()))
					platformOK = true;
			}
			else {
				// No restriction on platforms
				platformOK = true;
			}

			// Enable the NetBIOS SMB support
			smbConfig.setNetBIOSSMB(platformOK);

			// Only parse the other settings if NetBIOS based SMB is enabled for the current platform
			if ( platformOK) {

				// Check for the session port
				attr = elem.getAttribute("sessionPort");
				if ( attr != null && attr.length() > 0) {
					try {
						smbConfig.setSessionPort(Integer.parseInt(attr));
						if ( smbConfig.getSessionPort() <= 0 || smbConfig.getSessionPort() >= 65535)
							throw new InvalidConfigurationException("NetBIOS SMB session port out of valid range");
					}
					catch (NumberFormatException ex) {
						throw new InvalidConfigurationException("Invalid NetBIOS SMB session port");
					}
				}

				// Check for the datagram port
				attr = elem.getAttribute("datagramPort");
				if ( attr != null && attr.length() > 0) {
					try {
						smbConfig.setDatagramPort(Integer.parseInt(attr));
						if ( smbConfig.getDatagramPort() <= 0 || smbConfig.getDatagramPort() >= 65535)
							throw new InvalidConfigurationException("NetBIOS SMB datagram port out of valid range");
					}
					catch (NumberFormatException ex) {
						throw new InvalidConfigurationException("Invalid NetBIOS SMB datagram port");
					}
				}

				// Check for the name server port
				attr = elem.getAttribute("namingPort");
				if ( attr != null && attr.length() > 0) {
					try {
						smbConfig.setNameServerPort(Integer.parseInt(attr));
						if ( smbConfig.getNameServerPort() <= 0 || smbConfig.getNameServerPort() >= 65535)
							throw new InvalidConfigurationException("NetBIOS SMB naming port out of valid range");
					}
					catch (NumberFormatException ex) {
						throw new InvalidConfigurationException("Invalid NetBIOS SMB naming port");
					}
				}

				// Check for a bind address
				attr = elem.getAttribute("bindto");
				if ( attr != null && attr.length() > 0) {

					// Validate the bind address
					try {

						// Check the bind address
						InetAddress bindAddr = InetAddress.getByName(attr);

						// Set the bind address for the NetBIOS name server
						smbConfig.setNetBIOSBindAddress(bindAddr);
					}
					catch (UnknownHostException ex) {
						throw new InvalidConfigurationException(ex.toString());
					}
				}

				// Check for a bind address using the adapter name
				else if ( elem.hasAttribute("adapter")) {

					// Get the bind address via the network adapter name
					InetAddress bindAddr = parseAdapterName(elem.getAttribute("adapter"));
					smbConfig.setNetBIOSBindAddress(bindAddr);
				}
				else if ( smbConfig.hasSMBBindAddress()) {

					// Use the SMB bind address for the NetBIOS name server
					smbConfig.setNetBIOSBindAddress(smbConfig.getSMBBindAddress());
				}
			}
		}
		else {

			// Disable NetBIOS SMB support
			smbConfig.setNetBIOSSMB(false);
		}

		// Check if TCP/IP SMB is enabled
		elem = findChildNode("tcpipSMB", host.getChildNodes());
		if ( elem != null) {

			// Check if native SMB is enabled for the current platform
			boolean platformOK = false;

			if ( elem.hasAttribute("platforms")) {

				// Get the list of platforms
				String platformsStr = elem.getAttribute("platforms");

				// Parse the list of platforms that NetBIOS over TCP/IP is to be enabled for and
				// check if the current platform is included
				List<PlatformType.Type> enabledPlatforms = parsePlatformString(platformsStr);
				if ( enabledPlatforms.contains(getPlatformType()))
					platformOK = true;
			}
			else {

				// No restriction on platforms
				platformOK = true;
			}

			// Enable the TCP/IP SMB support
			smbConfig.setTcpipSMB(platformOK);

			// Check if the port has been specified
			attr = elem.getAttribute("port");
			if ( attr != null && attr.length() > 0) {
				try {
					smbConfig.setTcpipSMBPort(Integer.parseInt(attr));
					if ( smbConfig.getTcpipSMBPort() <= 0 || smbConfig.getTcpipSMBPort() >= 65535)
						throw new InvalidConfigurationException("TCP/IP SMB port out of valid range");
				}
				catch (NumberFormatException ex) {
					throw new InvalidConfigurationException("Invalid TCP/IP SMB port");
				}
			}
		}
		else {

			// Disable TCP/IP SMB support
			smbConfig.setTcpipSMB(false);
		}

		// Check that the broadcast mask has been set if TCP/IP NetBIOS and/or the host announcer is
		// enabled
		if ( smbConfig.hasNetBIOSSMB() || smbConfig.hasEnableAnnouncer()) {

			// Parse the broadcast mask
			elem = findChildNode("broadcast", host.getChildNodes());
			if ( elem != null) {

				// Get the broadcast mask string
				String bcastMask = getText( elem);

				// Check if we should determine the broadcast mask automatically
				if ( bcastMask.equalsIgnoreCase( "AUTO")) {

				    // Get the broadcast mask
                    bcastMask = determineBroadcastMask();

                    if ( bcastMask == null)
                        throw new InvalidConfigurationException("Failed to determine broadcast mask automatically");
				}

				// Check if the broadcast mask is a valid numeric IP address
				else if ( IPAddress.isNumericAddress( bcastMask) == false)
					throw new InvalidConfigurationException("Invalid broadcast mask, must be n.n.n.n format");

				// Set the network broadcast mask
				smbConfig.setBroadcastMask( bcastMask);
			}
			else {

                // Get the broadcast mask
                String bcastMask = determineBroadcastMask();

                if ( bcastMask == null)
                    throw new InvalidConfigurationException("Failed to determine broadcast mask automatically");

                // Set the network broadcast mask
                smbConfig.setBroadcastMask( bcastMask);
			}
		}

		// Check if Win32 NetBIOS is enabled
		elem = findChildNode("Win32NetBIOS", host.getChildNodes());

		if ( elem != null) {

            // Check if the Win32 NetBIOS classes are available
            LANAMapper lanaMapper = null;
            boolean win32Available = false;

            try {
                lanaMapper = (LANAMapper) Class.forName("org.filesys.netbios.win32.Win32NetBIOS").newInstance();
                win32Available = true;
            }
            catch (IllegalAccessException ex) {
            }
            catch (InstantiationException ex) {
            }
            catch (ClassNotFoundException ex) {
            }

            if ( win32Available == false || lanaMapper == null) {

                // Disable Win32 NetBIOS
                smbConfig.setWin32NetBIOS(false);

                // Log a warning, Win32 NetBIOS classes not available
                Debug.println("Win32 NetBIOS classes not available, setting ignored");
            }
            else {

                // Check if the Win32 NetBIOS server name has been specified
                attr = elem.getAttribute("name");
                if (attr != null && attr.length() > 0) {

                    // Validate the name
                    if (attr.length() > 16)
                        throw new InvalidConfigurationException("Invalid Win32 NetBIOS name, " + attr);

                    // Set the Win32 NetBIOS file server name
                    smbConfig.setWin32NetBIOSName(attr);
                }

                // Check if the Win32 NetBIOS client accept name has been specified
                attr = elem.getAttribute("accept");
                if (attr != null && attr.length() > 0) {

                    // Validate the client accept name
                    if (attr.length() > 15)
                        throw new InvalidConfigurationException("Invalid Win32 NetBIOS accept name, " + attr);

                    // Set the client accept string
                    smbConfig.setWin32NetBIOSClientAccept(attr);
                }

                // Check if the Win32 NetBIOS LANA has been specified
                attr = elem.getAttribute("lana");
                if (attr != null && attr.length() > 0) {

                    // Check if the LANA has been specified as an IP address or adapter name
                    int lana = -1;

                    if (IPAddress.isNumericAddress(attr)) {

                        // Convert the IP address to a LANA id
                        lana = lanaMapper.getLANAForIPAddress(attr);
                        if (lana == -1)
                            throw new InvalidConfigurationException("Failed to convert IP address " + attr + " to a LANA");
                    } else if (attr.length() > 1 && Character.isLetter(attr.charAt(0))) {

                        // Convert the network adapter to a LANA id
                        lana = lanaMapper.getLANAForAdapterName(attr);
                        if (lana == -1)
                            throw new InvalidConfigurationException("Failed to convert network adapter " + attr + " to a LANA");
                    } else {

                        // Validate the LANA number
                        try {
                            lana = Integer.parseInt(attr);
                        } catch (NumberFormatException ex) {
                            throw new InvalidConfigurationException("Invalid Win32 NetBIOS LANA specified");
                        }

                        // LANA should be in the range 0-255
                        if (lana < 0 || lana > 255)
                            throw new InvalidConfigurationException("Invalid Win32 NetBIOS LANA number, " + lana);
                    }

                    // Set the LANA number
                    smbConfig.setWin32LANA(lana);
                }

                // Check if the native NetBIOS interface has been specified, either 'winsock' or
                // 'netbios'
                attr = elem.getAttribute("api");

                if (attr != null && attr.length() > 0) {

                    // Validate the API type
                    boolean useWinsock = true;

                    if (attr.equalsIgnoreCase("netbios"))
                        useWinsock = false;
                    else if (attr.equalsIgnoreCase("winsock") == false)
                        throw new InvalidConfigurationException("Invalid NetBIOS API type, spefify 'winsock' or 'netbios'");

                    // Set the NetBIOS API to use
                    smbConfig.setWin32WinsockNetBIOS(useWinsock);
                }

                // Force the older NetBIOS API code to be used on 64Bit Windows as Winsock NetBIOS is
                // not available
                if (smbConfig.useWinsockNetBIOS() == true && X64.isWindows64()) {

                    // Log a warning
                    Debug.println("Using older Netbios() API code, Winsock NetBIOS not available on x64");

                    // Use the older NetBIOS API code
                    smbConfig.setWin32WinsockNetBIOS(false);
                }

                // Check if the current operating system is supported by the Win32 NetBIOS handler
                String osName = System.getProperty("os.name");
                if (osName.startsWith("Windows")
                        && (osName.endsWith("95") == false && osName.endsWith("98") == false && osName.endsWith("ME") == false)) {

                    // Enable Win32 NetBIOS
                    smbConfig.setWin32NetBIOS(true);
                } else {

                    // Win32 NetBIOS not supported on the current operating system
                    smbConfig.setWin32NetBIOS(false);
                }
            }

            // Check if the host announcer should be enabled
            elem = findChildNode("Win32Announce", host.getChildNodes());

            if (elem != null) {

                // Check if the Win32 host announcer classes are available
                win32Available = false;

                try {
                    Class.forName("org.filesys.smb.mailslot.win32.Win32NetBIOSHostAnnouncer");
                    win32Available = true;
                }
                catch (ClassNotFoundException ex) {
                }

                if (win32Available == false) {

                    // Log a warning, Win32 host announcer classes not available
                    Debug.println("Win32 host announcer classes not available, setting ignored");
                } else {

                    // Check for an announcement interval
                    attr = elem.getAttribute("interval");
                    if (attr != null && attr.length() > 0) {
                        try {
                            smbConfig.setWin32HostAnnounceInterval(Integer.parseInt(attr));
                        } catch (NumberFormatException ex) {
                            throw new InvalidConfigurationException("Invalid host announcement interval");
                        }
                    }

                    // Check if the domain name has been set, this is required if the host announcer is enabled
                    if (smbConfig.getDomainName() == null)
                        throw new InvalidConfigurationException("Domain name must be specified if host announcement is enabled");

                    // Enable Win32 NetBIOS host announcement
                    smbConfig.setWin32HostAnnouncer(true);
                }
            }
        }
        else {

            // Disable Win32 NetBIOS
            smbConfig.setWin32NetBIOS(false);
        }

		// Check if NetBIOS and/or TCP/IP SMB have been enabled
		if ( smbConfig.hasNetBIOSSMB() == false && smbConfig.hasTcpipSMB() == false && smbConfig.hasWin32NetBIOS() == false)
			throw new InvalidConfigurationException("NetBIOS SMB, TCP/IP SMB or Win32 NetBIOS must be enabled");

		// Check if server alias name(s) have been specified
		elem = findChildNode("alias", host.getChildNodes());
		if ( elem != null) {

			// Get the alias name list
			attr = elem.getAttribute("names");
			if ( attr == null || attr.length() == 0)
				throw new InvalidConfigurationException("Alias name(s) not specified");

			// Split the alias name list
			StringList names = new StringList();
			StringTokenizer nameTokens = new StringTokenizer(attr, ",");

			while (nameTokens.hasMoreTokens()) {

				// Get the current alias name
				String alias = nameTokens.nextToken().trim().toUpperCase();

				// Check if the name already exists in the alias list, or matches the main server
				// name
				if ( alias.equalsIgnoreCase(getServerName()))
					throw new InvalidConfigurationException("Alias is the same as the main server name");
				else if ( names.containsString(alias))
					throw new InvalidConfigurationException("Same alias specified twice, " + alias);
				else
					names.addString(alias);
			}

			// Set the server alias names
			smbConfig.addAliasNames(names);
		}

		// Check if Macintosh extension SMBs should be enabled
		elem = findChildNode("macExtensions", host.getChildNodes());
		if ( elem != null) {

			// Enable Macintosh extension SMBs
			smbConfig.setMacintoshExtensions(true);
		}

		// Check if WINS servers are configured
		elem = findChildNode("WINS", host.getChildNodes());

		if ( elem != null) {

			// Get the primary WINS server
			Element winsSrv = findChildNode("primary", elem.getChildNodes());
			if ( winsSrv == null)
				throw new InvalidConfigurationException("No primary WINS server configured");

			// Validate the WINS server address
			InetAddress primaryWINS = null;

			try {
				primaryWINS = InetAddress.getByName(getText(winsSrv));
			}
			catch (UnknownHostException ex) {
				throw new InvalidConfigurationException("Invalid primary WINS server address, " + winsSrv.getNodeValue());
			}

			// Check if a secondary WINS server has been specified
			winsSrv = findChildNode("secondary", elem.getChildNodes());
			InetAddress secondaryWINS = null;

			if ( winsSrv != null) {

				// Validate the secondary WINS server address
				try {
					secondaryWINS = InetAddress.getByName(getText(winsSrv));
				}
				catch (UnknownHostException ex) {
					throw new InvalidConfigurationException("Invalid secondary WINS server address, " + winsSrv.getNodeValue());
				}
			}

			// Set the WINS server address(es)
			smbConfig.setPrimaryWINSServer(primaryWINS);
			if ( secondaryWINS != null)
				smbConfig.setSecondaryWINSServer(secondaryWINS);
		}
		
		// Check if a session timeout is configured
		elem = findChildNode("sessionTimeout", host.getChildNodes());
		if ( elem != null) {
			
			// Validate the session timeout value
			String sessTmo = getText( elem);
			if ( sessTmo != null && sessTmo.length() > 0) {
				try {
					
					// Convert the timeout value to milliseconds
					int tmo = Integer.parseInt(sessTmo);
					if ( tmo < 0 || tmo > MaxSessionTimeout)
						throw new InvalidConfigurationException("Session timeout out of range (0 - " + MaxSessionTimeout + ")");
					
					// Convert the session timeout to milliseconds
					smbConfig.setSocketTimeout( tmo * 1000);
				}
				catch (NumberFormatException ex) {
					throw new InvalidConfigurationException("Invalid session timeout value, " + sessTmo);
				}
			}
			else
				throw new InvalidConfigurationException("Session timeout value not specified");
		}

		// Check if socket keep-alives should be disabled
		elem = findChildNode( "disableKeepAlive", host.getChildNodes());

		if ( elem != null)
			smbConfig.setSocketKeepAlive( false);

		// Check for the values used by the Local Security Authority DCE/RPC service
		elem = findChildNode( "dnsName", host.getChildNodes());

		if ( elem != null) {
			String dnsName = getText( elem);
			if ( dnsName != null && !dnsName.isEmpty())
				smbConfig.setDNSName( dnsName);
		}

		elem = findChildNode( "forestName", host.getChildNodes());

		if ( elem != null) {
			String forestName = getText( elem);
			if ( forestName != null && !forestName.isEmpty())
				smbConfig.setForestName( forestName);
		}
	}

	/**
	 * Process the debug XML element
	 * 
	 * @param debug Element
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected final void procDebugElement(Element debug)
		throws InvalidConfigurationException {

		// Check if the debug section has been specified
		if ( debug == null)
			return;

		// Create the debug configuration section
		DebugConfigSection debugConfig = new DebugConfigSection(this);

		// Get the debug output class and parameters
		Element elem = findChildNode("output", debug.getChildNodes());
		if ( elem == null)
			throw new InvalidConfigurationException("Output class must be specified to enable debug output");

		// Check if the output type has been specified
        String outType = getAttributeWithEnvVars(elem, "type");
        String dbgClass = null;

        if ( outType != null && outType.isEmpty() == false) {

            // Check for a valid debug output type
            if ( outType.equalsIgnoreCase( "console"))
                dbgClass = "org.filesys.debug.ConsoleDebug";
            else if ( outType.equalsIgnoreCase( "file"))
                dbgClass = "org.filesys.debug.LogFileDebug";
            else if ( outType.equalsIgnoreCase( "jdk"))
                dbgClass = "org.filesys.debug.JdkLoggingDebug";
            else
                throw new InvalidConfigurationException("Invalid debug output type '" + outType + "'");
        }

        // If the debug class has not been set then check for the original class setting
        if ( dbgClass == null) {

            // Get the debug output class
            Element debugClass = findChildNode("class", elem.getChildNodes());
            if (debugClass == null)
                throw new InvalidConfigurationException("Class must be specified for debug output");

            // Get the debug class
            dbgClass = getTextWithEnvVars( debugClass);
        }

		// Get the parameters for the debug class
		ConfigElement params = buildConfigElement(elem);
		debugConfig.setDebug(dbgClass, params);
	}

	/**
	 * Process the shares XML element
	 * 
	 * @param shares Element
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected final void procSharesElement(Element shares)
		throws InvalidConfigurationException {

		// Check if the shares element is valid
		if ( shares == null)
			return;

		// Create the filesystems configuration section
		FilesystemsConfigSection filesysConfig = new FilesystemsConfigSection(this);

		// Iterate the child elements
		NodeList children = shares.getChildNodes();

		if ( children != null) {

			// Iterate the child elements and process the disk/print share elements
			for (int i = 0; i < children.getLength(); i++) {

				// Get the current child node
				Node node = children.item(i);

				if ( node.getNodeType() == ELEMENT_TYPE) {

					// Get the next element from the list
					Element child = (Element) node;

					// Check if this is a disk or print share element
					if ( child.getNodeName().equalsIgnoreCase("diskshare"))
						addDiskShare(child, filesysConfig);
				}
			}
		}
	}

	/**
	 * Process the security XML element
	 * 
	 * @param security Element
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected void procSecurityElement(Element security)
		throws InvalidConfigurationException {

		// Check if the security element is valid
		if ( security == null)
			return;

		// Create the security configuration section
		SecurityConfigSection secConfig = new SecurityConfigSection(this);

		// Check if an access control manager has been specified
		Element aclElem = findChildNode("accessControlManager", security.getChildNodes());
		if ( aclElem != null) {

			// Get the access control manager class and security mode
			Element classElem = findChildNode("class", aclElem.getChildNodes());
			if ( classElem == null)
				throw new InvalidConfigurationException("Access control manager class not specified");

			// Get the parameters for the access control manager class
			ConfigElement params = buildConfigElement(aclElem);
			secConfig.setAccessControlManager(getText(classElem), params);
		}
		else {

			// Use the default access control manager
			secConfig.setAccessControlManager("org.filesys.server.auth.acl.DefaultAccessControlManager",
					new GenericConfigElement("aclManager"));
		}

		// Check if global access controls have been specified
		Element globalACLs = findChildNode("globalAccessControl", security.getChildNodes());
		if ( globalACLs != null) {

			// Parse the access control list
			AccessControlList acls = procAccessControlElement(globalACLs, secConfig);
			if ( acls != null)
				secConfig.setGlobalAccessControls(acls);
		}

		// Check if a JCE provider class has been specified
		Element jceElem = findChildNode("JCEProvider", security.getChildNodes());
		if ( jceElem != null) {

			// Set the JCE provider
			secConfig.setJCEProvider(getText(jceElem));
		}

		// Add the users
		Element usersElem = findChildNode("users", security.getChildNodes());
		if ( usersElem != null) {

			// Get the list of user elements
			NodeList userList = usersElem.getChildNodes();

			for (int i = 0; i < userList.getLength(); i++) {

				// Get the current user node
				Node node = userList.item(i);

				if ( node.getNodeType() == ELEMENT_TYPE) {
					Element userElem = (Element) node;
					addUser(userElem, secConfig);
				}
			}
		}

		// Check if a share mapper has been specified
		Element mapper = findChildNode("shareMapper", security.getChildNodes());

		if ( mapper != null) {

			// Get the share mapper class
			Element classElem = findChildNode("class", mapper.getChildNodes());
			if ( classElem == null)
				throw new InvalidConfigurationException("Share mapper class not specified");

			// Get the parameters for the share mapper class
			ConfigElement params = buildConfigElement(mapper);
			secConfig.setShareMapper(getText(classElem), params);
		}

		// Check if the users interface has been specified
		Element usersIface = findChildNode("usersInterface", security.getChildNodes());

		if ( usersIface != null) {

			// Get the users interface class
			Element classElem = findChildNode("class", usersIface.getChildNodes());
			if ( classElem == null)
				throw new InvalidConfigurationException("Users interface class not specified");

			// Get the parameters for the users interface class
			ConfigElement params = buildConfigElement(usersIface);
			secConfig.setUsersInterface(getText(classElem), params);
		}
	}

	/**
	 * Process the drive mappings XML element
	 * 
	 * @param mappings Element
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected final void procDriveMappingsElement(Element mappings)
		throws InvalidConfigurationException {

		// Check if the drive mappings element is valid
		if ( mappings == null)
			return;

		// Create the drive mappings configuration section
		DriveMappingsConfigSection mapConfig = new DriveMappingsConfigSection(this);

		// Parse each drive mapping element
		NodeList mapElems = mappings.getChildNodes();
		DriveMappingList mapList = null;

		if ( mapElems != null && mapElems.getLength() > 0) {

			// Create the mapped drive list
			mapList = new DriveMappingList();

			// Access the CIFS server configuration
			SMBConfigSection smbConfig = (SMBConfigSection) getConfigSection(SMBConfigSection.SectionName);

			// Get a list of the available shares
			SecurityConfigSection secConfig = (SecurityConfigSection) getConfigSection(SecurityConfigSection.SectionName);
			SharedDeviceList shareList = secConfig.getShareMapper().getShareList(getServerName(), null, false);

			// Process each drive mapping element
			for (int i = 0; i < mapElems.getLength(); i++) {

				// Get the current mapped drive details
				Node node = mapElems.item(i);

				if ( node.getNodeType() == ELEMENT_TYPE) {

					// Access the mapped drive element
					Element elem = (Element) node;

					if ( elem.getNodeName().equals("mapDrive")) {

						// Get the mapped drive local drive and remote path details
						String localPath = elem.getAttribute("drive").toUpperCase();
						String shareName = elem.getAttribute("share");

						// Check the local path string
						if ( localPath.length() != 2)
							throw new InvalidConfigurationException("Invalid local drive specified, " + localPath);

						if ( localPath.charAt(1) != ':' || _driveLetters.indexOf(localPath.charAt(0)) == -1)
							throw new InvalidConfigurationException("Invalid local drive specified, " + localPath);

						// Check if the share name is a valid local disk share
						if ( shareName.length() == 0)
							throw new InvalidConfigurationException("Empty share name for mapped drive, " + localPath);

						if ( shareList.findShare(shareName, ShareType.DISK, true) == null)
							throw new InvalidConfigurationException("Mapped drive share " + shareName + " does not exist");

						// Get the username/password to be used to connect the mapped drive
						String userName = null;
						String password = null;

						if ( elem.hasAttribute("username"))
							userName = elem.getAttribute("username");

						if ( elem.hasAttribute("password"))
							password = elem.getAttribute("password");

						// Get the options flags
						boolean interact = false;
						boolean prompt = false;

						if ( elem.hasAttribute("interactive")) {
							if ( elem.getAttribute("interactive").equalsIgnoreCase("YES"))
								interact = true;
						}

						if ( elem.hasAttribute("prompt")) {
							if ( elem.getAttribute("prompt").equalsIgnoreCase("YES"))
								prompt = true;
						}

						// Build the remote path
						StringBuffer remPath = new StringBuffer();
						remPath.append("\\\\");

						if ( smbConfig.hasWin32NetBIOS() && smbConfig.getWin32ServerName() != null)
							remPath.append(smbConfig.getWin32ServerName());
						else
							remPath.append(getServerName());
						remPath.append("\\");
						remPath.append(shareName.toUpperCase());

						// Add a drive mapping
						mapList.addMapping(new DriveMapping(localPath, remPath.toString(), userName, password, interact, prompt));
					}
				}
			}

			// Set the mapped drive list
			mapConfig.setMappedDrives(mapList);
		}
	}

	/**
	 * Process an access control sub-section and return the access control list
	 * 
	 * @param acl Element
	 * @param secConfig SecutiryConfigSection
     * @return AccessControlList
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected final AccessControlList procAccessControlElement(Element acl, SecurityConfigSection secConfig)
		throws InvalidConfigurationException {

		// Check if there is an access control manager configured
		if ( secConfig.getAccessControlManager() == null)
			throw new InvalidConfigurationException("No access control manager configured");

		// Create the access control list
		AccessControlList acls = new AccessControlList();

		// Check if there is a default access level for the ACL group
		String attrib = acl.getAttribute("default");

		if ( attrib != null && attrib.length() > 0) {

			// Get the access level and validate
			try {

				// Parse the access level name
				int access = AccessControlParser.parseAccessTypeString(attrib);

				// Set the default access level for the access control list
				acls.setDefaultAccessLevel(access);
			}
			catch (InvalidACLTypeException ex) {
				throw new InvalidConfigurationException("Default access level error, " + ex.toString());
			}
			catch (ACLParseException ex) {
				throw new InvalidConfigurationException("Default access level error, " + ex.toString());
			}
		}

		// Parse each access control element and create the required access control
		NodeList aclElems = acl.getChildNodes();

		if ( aclElems != null && aclElems.getLength() > 0) {

			// Create the access controls
			GenericConfigElement params = null;
			String type = null;

			for (int i = 0; i < aclElems.getLength(); i++) {

				// Get the current ACL details
				Node node = aclElems.item(i);

				if ( node.getNodeType() == ELEMENT_TYPE) {

					// Access the ACL element
					Element elem = (Element) node;
					type = elem.getNodeName();

					// Create a new config element
					params = new GenericConfigElement("acl");

					// Convert the element attributes into a list of name value pairs
					NamedNodeMap attrs = elem.getAttributes();

					if ( attrs == null || attrs.getLength() == 0)
						throw new InvalidConfigurationException("Missing attribute(s) for access control " + type);

					for (int j = 0; j < attrs.getLength(); j++) {

						// Create a name/value pair from the current attribute and add to the
						// parameter list
						Node attr = attrs.item(j);
						params.addAttribute( attr.getNodeName(), attr.getNodeValue());
					}

					try {

						// Create the access control and add to the list
						acls.addControl(secConfig.getAccessControlManager().createAccessControl(type, params));
					}
					catch (InvalidACLTypeException ex) {
						throw new InvalidConfigurationException("Invalid access control type - " + type);
					}
					catch (ACLParseException ex) {
						throw new InvalidConfigurationException("Access control parse error (" + type + "), " + ex.toString());
					}
				}
			}
		}

		// Check if there are no access control rules but the default access level is set to 'None',
		// this is not allowed as the share would not be accessible or visible.
		if ( acls.getDefaultAccessLevel() == AccessControl.NoAccess && acls.numberOfControls() == 0)
			throw new InvalidConfigurationException("Empty access control list and default access 'None' not allowed");

		// Return the access control list
		return acls;
	}

	/**
	 * Process the sessionDebug XML element
	 *
	 * @param elem Element
	 * @param smbConfig CIFSConfigSection
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected void procSessionDebugElement(Element elem, SMBConfigSection smbConfig)
			throws InvalidConfigurationException {

        if ( elem != null) {

            // Check for session debug flags
            String flags = getAttributeWithEnvVars(elem,"flags");
            EnumSet<SMBSrvSession.Dbg> smbDbg = EnumSet.<SMBSrvSession.Dbg>noneOf( SMBSrvSession.Dbg.class);

            if ( flags != null) {

                // Parse the flags
                flags = flags.toUpperCase();
                StringTokenizer token = new StringTokenizer(flags, ",");

                while (token.hasMoreTokens()) {

                    // Get the current debug flag token
                    String dbg = token.nextToken().trim();

					// Convert the debug flag name to an enum value
					try {
						smbDbg.add(SMBSrvSession.Dbg.valueOf(dbg));
					}
					catch ( IllegalArgumentException ex) {
						throw new InvalidConfigurationException("Invalid SMB debug flag, " + dbg);
					}
                }
            }

            // Set the session debug flags
            smbConfig.setSessionDebugFlags(smbDbg);
        }
    }

	/**
	 * Process the smbdialects XML element
	 *
	 * @param elem Element
	 * @param smbConfig CIFSConfigSection
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected void procSMBDialectsElement(Element elem, SMBConfigSection smbConfig)
			throws InvalidConfigurationException {

        if ( elem != null) {

            // Clear all configured SMB dialects
            DialectSelector diaSel = smbConfig.getEnabledDialects();
            diaSel.ClearAll();

            // Parse the SMB dilaects list
            StringTokenizer token = new StringTokenizer(getTextWithEnvVars(elem), ",");

            while (token.hasMoreTokens()) {

                // Get the current SMB dialect token
                String dia = token.nextToken().trim();

                // Determine the dialect to be enabled
                if ( dia.equalsIgnoreCase("CORE")) {

                    // Enable core dialects
                    diaSel.AddDialect(Dialect.Core);
                    diaSel.AddDialect(Dialect.CorePlus);
                }
                else if ( dia.equalsIgnoreCase("LANMAN")) {

                    // Enable the LanMan dialects
                    diaSel.AddDialect(Dialect.DOSLanMan1);
                    diaSel.AddDialect(Dialect.DOSLanMan2);
                    diaSel.AddDialect(Dialect.LanMan1);
                    diaSel.AddDialect(Dialect.LanMan2);
                    diaSel.AddDialect(Dialect.LanMan2_1);
                }
                else if ( dia.equalsIgnoreCase("NT")) {

                    // Enable the NT dialect
                    diaSel.AddDialect(Dialect.NT);
                }
                else if ( dia.equalsIgnoreCase( "SMB1")) {

                	// Enable all SMB v1 dialects
					diaSel.enableGroup( DialectSelector.DialectGroup.SMBv1);
				}
                else
                    throw new InvalidConfigurationException("Invalid SMB dialect, " + dia);
            }

            // Set the enabled server SMB dialects
            smbConfig.setEnabledDialects(diaSel);
        }
    }

	/**
	 * Process the audit configuration section
	 *
	 * @param auditElem Element
	 * @exception InvalidConfigurationException Error during parsing of the audit section
	 */
	protected final void procAuditElement( Element auditElem)
			throws InvalidConfigurationException {

		// Check if the audit section has been specified
		if ( auditElem == null)
			return;

		// Create the audit configuration section
		AuditConfigSection auditConfig = new AuditConfigSection(this);

		// Get the audit output class and parameters
		Element elem = findChildNode("output", auditElem.getChildNodes());
		if ( elem == null)
			throw new InvalidConfigurationException("Output class must be specified to enable audit output");

		// Check if the output type has been specified
		String outType = getAttributeWithEnvVars(elem, "type");
		String auditClass = null;

		if ( outType != null) {

			// Check for a valid audit output type
			if ( outType.equalsIgnoreCase( "console"))
				auditClass = "org.filesys.debug.ConsoleDebug";
			else if ( outType.equalsIgnoreCase( "file"))
				auditClass = "org.filesys.debug.LogFileDebug";
			else if ( outType.equalsIgnoreCase( "jdk"))
				auditClass = "org.filesys.debug.JdkLoggingDebug";
			else
				throw new InvalidConfigurationException("Invalid audit output type '" + outType + "'");
		}

		// If the audit class has not been set then check for the original class setting
		if ( auditClass == null) {

			// Get the audit output class
			Element auditClassElem = findChildNode("class", elem.getChildNodes());
			if ( auditClassElem == null)
				throw new InvalidConfigurationException("Class must be specified for audit output");

			// Get the audit class
			auditClass = getTextWithEnvVars( auditClassElem);
		}

		// Get the parameters for the audit class
		ConfigElement params = buildConfigElement(elem);
		auditConfig.setAudit( auditClass, params);

		// Check if the enabled audit groups list has been specified
		elem = findChildNode( "groups", auditElem.getChildNodes());

		if ( elem != null) {

			// Get the list of audit groups to enable
			String groups = getAttributeWithEnvVars( elem, "enable");

			EnumSet<AuditGroup> auditGroups = EnumSet.noneOf( AuditGroup.class);

			if ( groups != null && groups.length() > 0) {

				if ( groups.equals( "*")) {
					auditGroups = EnumSet.allOf( AuditGroup.class);
				}
				else {

					// Parse the groups list
					groups = groups.toUpperCase();
					StringTokenizer token = new StringTokenizer(groups, ",");

					while (token.hasMoreTokens()) {

						// Get the current audit group name
						String groupName = token.nextToken().trim();

						// Convert the group name to an enum value
						try {
							auditGroups.add(AuditGroup.valueOf(groupName));
						}
						catch (IllegalArgumentException ex) {
							throw new InvalidConfigurationException("Invalid audit group name, " + groupName);
						}
					}
				}

				// Set the enabled audit groups
				Audit.setAuditGroups( auditGroups);
			}
			else
				throw new InvalidConfigurationException("Empty audit groups enable list");
		}
	}

	/**
     * Add a user
     *
     * @param user Element
     * @param secConfig SecurityConfigSection
     * @exception InvalidConfigurationException Error parsing the configuration
     */
	protected final void addUser(Element user, SecurityConfigSection secConfig)
		throws InvalidConfigurationException {

		// Get the username
		String attr = getAttributeWithEnvVars( user, "name");
		if ( attr == null || attr.length() == 0)
			throw new InvalidConfigurationException("User name not specified, or zero length");

		// Check if the user already exists
		String userName = attr;

		if ( secConfig.hasUserAccounts() && secConfig.getUserAccounts().findUser(userName) != null)
			throw new InvalidConfigurationException("User " + userName + " already defined");

		// Get the MD4 hashed password
		byte[] md4 = null;
		String password = null;

		Element elem = findChildNode("md4", user.getChildNodes());
		if ( elem != null) {

			// Get the MD4 hashed password string
			String md4Str = getText(elem);
			if ( md4Str == null || md4Str.length() != 32)
				throw new InvalidConfigurationException("Invalid MD4 hashed password for user " + userName);

			// Decode the MD4 string
			md4 = new byte[16];
			for (int i = 0; i < 16; i++) {

				// Get a hex pair and convert
				String hexPair = md4Str.substring(i * 2, (i * 2) + 2);
				md4[i] = (byte) Integer.parseInt(hexPair, 16);
			}
		}
		else {

			// Get the password for the account
			elem = findChildNode("password", user.getChildNodes());
			if ( elem == null)
				throw new InvalidConfigurationException("No password specified for user " + userName);

			// Get the plaintext password
			password = getTextWithEnvVars(elem);
		}

		// Create the user account
		UserAccount userAcc = new UserAccount(userName, password);
		userAcc.setMD4Password(md4);

		// Check if the user in an administrator
		elem = findChildNode("administrator", user.getChildNodes());
		if ( elem != null)
			userAcc.setAdministrator(true);

		// Get the real user name and comment
		elem = findChildNode("realname", user.getChildNodes());
		if ( elem != null)
			userAcc.setRealName(getTextWithEnvVars(elem));

		elem = findChildNode("comment", user.getChildNodes());
		if ( elem != null)
			userAcc.setComment(getTextWithEnvVars(elem));

		// Get the home directory
		elem = findChildNode("home", user.getChildNodes());
		if ( elem != null)
			userAcc.setHomeDirectory(getTextWithEnvVars(elem));

		// Add the user account
		UserAccountList accList = secConfig.getUserAccounts();
		if ( accList == null)
			secConfig.setUserAccounts(new UserAccountList());
		secConfig.getUserAccounts().addUser(userAcc);
	}

	/**
	 * Add a disk share
	 * 
	 * @param disk Element
     * @param filesysConfig FilesystemConfigSection
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected final void addDiskShare(Element disk, FilesystemsConfigSection filesysConfig)
		throws InvalidConfigurationException {

		// Get the share name and comment attributes
		String attr = getAttributeWithEnvVars(disk,"name");
		if ( attr == null || attr.length() == 0)
			throw new InvalidConfigurationException("Disk share name must be specified");

		String name = attr;
		String comment = null;

		attr = disk.getAttribute("comment");
		if ( attr != null && attr.length() > 0)
			comment = attr;

		// Get the disk driver details
		Element driverElem = findChildNode("driver", disk.getChildNodes());
		if ( driverElem == null)
			throw new InvalidConfigurationException("No driver specified for disk share " + name);

		Element classElem = findChildNode("class", driverElem.getChildNodes());
		if ( classElem == null || getText(classElem).length() == 0)
			throw new InvalidConfigurationException("No driver class specified for disk share " + name);

		// Get the security configuration section
		SecurityConfigSection secConfig = (SecurityConfigSection) getConfigSection(SecurityConfigSection.SectionName);

		// Check if an access control list has been specified
		AccessControlList acls = null;
		Element aclElem = findChildNode("accessControl", disk.getChildNodes());

		if ( aclElem != null) {

			// Parse the access control list
			acls = procAccessControlElement(aclElem, secConfig);
		}
		else {

			// Use the global access control list for this disk share
			acls = secConfig.getGlobalAccessControls();
		}

		// Get the parameters for the driver
		ConfigElement params = buildConfigElement(driverElem);

		// Check if change notification should be disabled for this device
		boolean changeNotify = findChildNode("disableChangeNotification", disk.getChildNodes()) != null ? false : true;

		// Check if change notification debug output should be enabled for this device
		boolean changeDebug = findChildNode( "notifyDebug", disk.getChildNodes()) != null ? true : false;

		// Check if the volume information has been specified
		Element volElem = findChildNode("volume", disk.getChildNodes());
		VolumeInfo volInfo = null;

		if ( volElem != null) {

			// Create the volume information
			volInfo = new VolumeInfo("");

			// Get the volume label
			attr = volElem.getAttribute("label");
			if ( attr != null && attr.length() > 0)
				volInfo.setVolumeLabel(attr);

			// Get the serial number
			attr = volElem.getAttribute("serial");
			if ( attr != null && attr.length() > 0) {
				try {
					volInfo.setSerialNumber(Integer.parseInt(attr));
				}
				catch (NumberFormatException ex) {
					throw new InvalidConfigurationException("Volume serial number invalid, " + attr);
				}
			}

			// Get the creation date/time
			attr = volElem.getAttribute("created");
			if ( attr != null && attr.length() > 0) {
				try {
					volInfo.setCreationDateTime(m_dateFmt.parse(attr));
				}
				catch (ParseException ex) {
					throw new InvalidConfigurationException("Volume creation date/time invalid, " + attr);
				}
			}
		}
		else {

			// Create volume information using the share name
			volInfo = new VolumeInfo(name, (int) System.currentTimeMillis(), new Date(System.currentTimeMillis()));
		}

		// Check if the disk sizing information has been specified
		SrvDiskInfo diskInfo = null;
		Element sizeElem = findChildNode("size", disk.getChildNodes());

		if ( sizeElem != null) {

			// Get the total disk size in bytes
			long totSize = -1L;
			long freeSize = 0;

			attr = sizeElem.getAttribute("totalSize");
			if ( attr != null && attr.length() > 0)
				totSize = MemorySize.getByteValue(attr);

			if ( totSize == -1L)
				throw new InvalidConfigurationException("Total disk size invalid or not specified");

			// Get the free size in bytes
			attr = sizeElem.getAttribute("freeSize");
			if ( attr != null && attr.length() > 0)
				freeSize = MemorySize.getByteValue(attr);
			else
				freeSize = (totSize / 10L) * 9L;

			if ( freeSize == -1L)
				throw new InvalidConfigurationException("Free disk size invalid or not specified");

			// Get the block size and blocks per unit values, if specified
			long blockSize = 512L;
			long blocksPerUnit = 64L; // 32Kb units

			attr = sizeElem.getAttribute("blockSize");
			if ( attr != null && attr.length() > 0) {
				try {
					blockSize = Long.parseLong(attr);

					// Check for a multiple of 512 bytes
					if ( blockSize <= 0 || blockSize % 512 != 0)
						throw new InvalidConfigurationException("Block size must be a multiple of 512");
				}
				catch (NumberFormatException ex) {
					throw new InvalidConfigurationException("Invalid block size specified, " + attr);
				}
			}

			attr = sizeElem.getAttribute("blocksPerUnit");
			if ( attr != null && attr.length() > 0) {
				try {
					blocksPerUnit = Long.parseLong(attr);

					// Check for a valid blocks per unit value
					if ( blocksPerUnit <= 0)
						throw new InvalidConfigurationException("Invalid blocks per unit, must be greater than zero");
				}
				catch (NumberFormatException ex) {
					throw new InvalidConfigurationException("Invalid blocks per unit value");
				}
			}

			// Calculate the sizes and set the disk sizing information
			long unitSize = blockSize * blocksPerUnit;
			long totUnits = totSize / unitSize;
			long freeUnits = freeSize / unitSize;

			diskInfo = new SrvDiskInfo(totUnits, blocksPerUnit, blockSize, freeUnits);
		}
		else {

			// Default to a 80Gb sized disk with 90% free space
			diskInfo = new SrvDiskInfo(2560000, 64, 512, 2304000);
		}

		// Check if a state cache is configured
		Element cacheElem = findChildNode("stateCache", disk.getChildNodes());
		FileStateCache stateCache = null;
		
		if ( cacheElem != null) {

			// Convert the state cache configuration
			ConfigElement cacheConfig = buildConfigElement( cacheElem);
			
			// Get the cache type
			attr = cacheElem.getAttribute( "type");
			if ( attr.equalsIgnoreCase( "standalone")) {
				
				// Create a standalone file state cache
				stateCache = new StandaloneFileStateCache();
			}
			else if ( attr.equalsIgnoreCase( "cluster")) {
				
				// Create a clustered file state cache, need to load the class to avoid a reference to it
				try {
					stateCache = (FileStateCache) Class.forName("org.filesys.server.filesys.cache.hazelcast.HazelCastClusterFileStateCacheV5").newInstance();
				}
				catch ( ClassNotFoundException ex) {
					throw new InvalidConfigurationException( "Clustered file state cache not available, check build/Jar");
				}
				catch ( Exception ex) {
					throw new InvalidConfigurationException( "Failed to load clustered file state cache class, " + ex);
				}
			}
			else if ( attr.equalsIgnoreCase( "custom")) {

				// Get the custom state cache class name
				Element cacheClass = findChildNode( "class", cacheElem.getChildNodes());
				if ( cacheClass == null || getText( cacheClass).length() == 0)
					throw new InvalidConfigurationException( "Custom state cache class not specified");
				
				// Create a custom file state cache
				try {
					Object cacheObj = Class.forName( getText( cacheClass)).newInstance();
					if ( cacheObj instanceof FileStateCache == false)
						throw new InvalidConfigurationException( "State cache class is not a FileStateCache based class");
					
					stateCache = (FileStateCache) cacheObj; 
				}
				catch ( ClassNotFoundException ex) {
					throw new InvalidConfigurationException( "Clustered file state cache not available, check build/Jar");
				}
				catch ( Exception ex) {
					throw new InvalidConfigurationException( "Failed to load clustered file state cache class, " + ex);
				}
			}
			
			// Initialize the cache
			if ( stateCache != null)
				stateCache.initializeCache( cacheConfig, this);
			else
				throw new InvalidConfigurationException( "Failed to initialize state cache for filesystem " + name);
		}
		
		// Check if a share with this name already exists
		if ( filesysConfig.getShares().findShare(name) != null)
			throw new InvalidConfigurationException("Share " + name + " already exists");

		// Validate the driver class, create a device context and add the new disk share
		try {

			// Load the driver class
			Object drvObj = Class.forName(getText(classElem)).newInstance();
			if ( drvObj instanceof DiskInterface) {

				// Create the driver
				DiskInterface diskDrv = (DiskInterface) drvObj;

				// Create a context for this share instance, save the configuration parameters as
				// part of the context
				DiskDeviceContext devCtx = (DiskDeviceContext) diskDrv.createContext(name, params);
				devCtx.setConfigurationParameters(params);

				// Enable/disable change notification for this device
				devCtx.enableChangeHandler(changeNotify);

				// Enable/disable change notification debug output
				if ( devCtx.hasChangeHandler())
					devCtx.getChangeHandler().setDebug(changeDebug);

				// Set the volume information, may be null
				devCtx.setVolumeInformation(volInfo);

				// Set the disk sizing information, may be null
				devCtx.setDiskInformation(diskInfo);

				// Set the share name in the context
				devCtx.setShareName(name);

				// Create the default file state cache type if the filesystem requires it, for backwards compatability
				if ( devCtx.requiresStateCache() && stateCache == null) {
					stateCache = new StandaloneFileStateCache();
					stateCache.initializeCache( new GenericConfigElement( "stateCache"), this);
				}
				
				if ( devCtx.requiresStateCache() == false && stateCache != null)
					throw new InvalidConfigurationException( "Filesystem does not use state caching");

				devCtx.setStateCache( stateCache);
				
				// Create the disk shared device and add to the server's list of shares
				DiskSharedDevice diskDev = new DiskSharedDevice(name, diskDrv, devCtx);
				diskDev.setComment(comment);
				diskDev.setConfiguration( this);

				// Add any access controls to the share
				diskDev.setAccessControlList(acls);

                // Check if the filesystem uses the file state cache, if so then add to the file state reaper
                if ( devCtx.hasStateCache()) {
                    
                    // Register the state cache with the reaper thread
                    filesysConfig.addFileStateCache( name, devCtx.getStateCache());
                }
                
				// Start the filesystem
				devCtx.startFilesystem(diskDev);

				// Pass the driver/context details to the state cache
				if ( devCtx.hasStateCache())
					devCtx.getStateCache().setDriverDetails(diskDev);
				
				// Add the new share to the list of available shares
				filesysConfig.addShare(diskDev);
			}
		}
		catch (ClassNotFoundException ex) {
			throw new InvalidConfigurationException("Disk driver class " + getText(classElem) + " not found");
		}
		catch (DeviceContextException ex) {
			throw new InvalidConfigurationException("Driver context error", ex);
		}
		catch (Exception ex) {
			throw new InvalidConfigurationException("Disk share setup error", ex);
		}
	}

	/**
	 * Get the SMB authenticator class name to use for the specified authenticator type
	 *
	 * @param authType String
	 * @return String
	 */
	protected String getSMBAuthenticatorClassForType( String authType) {

		// Check the authenticator type and set the appropriate authenticator class
		String authClass = null;

		if ( authType.equalsIgnoreCase("local"))
			authClass = "org.filesys.server.auth.LocalAuthenticator";
		else if ( authType.equalsIgnoreCase("passthru"))
			authClass = "org.filesys.server.auth.PassthruAuthenticator";
		else if ( authType.equalsIgnoreCase("enterprise"))
			authClass = "org.filesys.server.auth.EnterpriseSMBAuthenticator";

		return authClass;
	}

	/**
	 * Find the specified child node in the node list
	 * 
	 * @param name String
	 * @param list NodeList
	 * @return Element
	 */
	protected final Element findChildNode(String name, NodeList list) {

		// Check if the list is valid
		if ( list == null)
			return null;

		// Search for the required element
		for (int i = 0; i < list.getLength(); i++) {

			// Get the current child node
			Node child = list.item(i);
			if ( child.getNodeName().equals(name) && child.getNodeType() == ELEMENT_TYPE)
				return (Element) child;
		}

		// Element not found
		return null;
	}

	/**
	 * Get the value text for the specified element
	 * 
	 * @param elem Element
	 * @return String
	 */
	protected final String getText(Element elem) {

		// Check if the element has children
		NodeList children = elem.getChildNodes();
		String text = "";

		if ( children != null && children.getLength() > 0 && children.item(0).getNodeType() != ELEMENT_TYPE)
			text = children.item(0).getNodeValue();

		// Return the element text value
		return text;
	}

	/**
	 * Get the value text for the specified element and convert any environment variable tokens
	 *
	 * @param elem Element
	 * @return String
	 */
	protected final String getTextWithEnvVars(Element elem) {

		// Get the element text
        String text = getText( elem);

        return expandEnvVars( text);
	}

    /**
     * Get the attribute text for the specified attribute and convert any environment variable tokens
     *
     * @param elem Element
     * @param attrName String
     * @return String
     */
    protected final String getAttributeWithEnvVars(Element elem, String attrName) {

        // Get the attribute value
        String attr = elem.getAttribute( attrName);

        return expandEnvVars( attr);
    }

	/**
	 * Expand a string by converting any environment variables
	 *
	 * @param inStr String
	 * @return String
	 */
	protected final String expandEnvVars(String inStr) {

		String outStr = inStr;

		if ( inStr != null && inStr.length() > 0) {

			// Convert environment variable tokens
			Matcher matcher = m_envTokens.matcher( inStr);
			StringBuffer attrOut = new StringBuffer( inStr.length());

			while ( matcher.find()) {

				// Get the current match string
				String token = inStr.substring(matcher.start(), matcher.end());
				String envVar = token.substring(2, token.length() - 1);

				// Get the environment variable value
				String envValue = System.getenv( envVar);

				if ( envValue != null) {

					// Replace the occurrence of the environment variable token and write to the new string
					matcher.appendReplacement( attrOut, envValue);
				}
				else {

					// Check for a system property
					envValue = System.getProperty( envVar);

					if ( envValue != null) {

						// Replace the occurrence of the environment variable token and write to the new string
						matcher.appendReplacement(attrOut, envValue);
					}
				}
			}

			// Replace the original attribute string
			if ( attrOut.length() > 0)
				outStr = attrOut.toString();
		}

		// Return the updated string
		return outStr;
	}

	/**
	 * Build a configuration element list from an elements child nodes
	 * 
	 * @param root Element
	 * @return GenericConfigElement
	 */
	protected final GenericConfigElement buildConfigElement(Element root) {
		return buildConfigElement(root, null);
	}

	/**
	 * Build a configuration element list from an elements child nodes
	 * 
	 * @param root Element
	 * @param cfgElem GenericConfigElement
	 * @return GenericConfigElement
	 */
	protected final GenericConfigElement buildConfigElement(Element root, GenericConfigElement cfgElem) {

		// Create the top level element, if not specified
		GenericConfigElement rootElem = cfgElem;

		if ( rootElem == null) {

			// Create the root element
			rootElem = new GenericConfigElement(root.getNodeName());

			// Add any attributes
			NamedNodeMap attribs = root.getAttributes();
			if ( attribs != null) {
				for (int i = 0; i < attribs.getLength(); i++) {
					Node attribNode = attribs.item(i);
					rootElem.addAttribute(attribNode.getNodeName(), expandEnvVars(attribNode.getNodeValue()));
				}
			}
		}

		// Get the child node list
		NodeList nodes = root.getChildNodes();
		if ( nodes == null)
			return rootElem;

		// Process the child node list
		GenericConfigElement childElem = null;

		for (int i = 0; i < nodes.getLength(); i++) {

			// Get the current node
			Node node = nodes.item(i);

			if ( node.getNodeType() == ELEMENT_TYPE) {

				// Access the Element
				Element elem = (Element) node;

				// Check if the element has any child nodes
				NodeList children = elem.getChildNodes();

				if ( children != null && children.getLength() > 1) {

					// Add the child nodes as child configuration elements
					childElem = buildConfigElement(elem, null);
				}
				else {

					// Create a normal name/value
					if ( children.getLength() > 0) {
						childElem = new GenericConfigElement(elem.getNodeName());
						childElem.setValue( expandEnvVars( children.item(0).getNodeValue()));
					}
					else {
						childElem = new GenericConfigElement(elem.getNodeName());
					}

					// Add any attributes
					NamedNodeMap attribs = elem.getAttributes();
					if ( attribs != null) {
						for (int j = 0; j < attribs.getLength(); j++) {
							Node attribNode = attribs.item(j);
							childElem.addAttribute(attribNode.getNodeName(), expandEnvVars( attribNode.getNodeValue()));
						}
					}
				}

				// Add the child configuration element
				rootElem.addChild(childElem);
			}
		}

		// Return the configuration element
		return rootElem;
	}

	/**
	 * Parse a platform type string into a list of platform ids
	 * 
	 * @param platforms String
	 * @return List of platform type ids
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected final List<PlatformType.Type> parsePlatformString(String platforms)
		throws InvalidConfigurationException {

		// Create the list to hold the platform ids

		List<PlatformType.Type> platformIds = new ArrayList<PlatformType.Type>();

		if ( platforms == null)
			return platformIds;

		// Split the platform list
		StringTokenizer tokens = new StringTokenizer(platforms.toUpperCase(Locale.ENGLISH), ",");

		while (tokens.hasMoreTokens()) {

			// Get the current platform token and validate
			String platform = tokens.nextToken().trim();

			// Validate the platform id
			PlatformType.Type id = PlatformType.Type.Unknown;

			if ( platform.equalsIgnoreCase("WINDOWS"))
				id = PlatformType.Type.WINDOWS;
			else if ( platform.equalsIgnoreCase("LINUX"))
				id = PlatformType.Type.LINUX;
			else if ( platform.equalsIgnoreCase("MACOSX"))
				id = PlatformType.Type.MACOSX;
			else if ( platform.equalsIgnoreCase("SOLARIS"))
				id = PlatformType.Type.SOLARIS;

			if ( id == PlatformType.Type.Unknown)
				throw new InvalidConfigurationException("Invalid platform type '" + platform + "'");

			// Add the platform id to the list
			platformIds.add(id);
		}

		// Return the platform id list
		return platformIds;
	}

	/**
	 * Parse an adapter name string and return the matching address
	 * 
	 * @param adapter String
	 * @return InetAddress
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected final InetAddress parseAdapterName(String adapter)
		throws InvalidConfigurationException {

		NetworkInterface ni = null;

		try {
			ni = NetworkInterface.getByName(adapter);
		}
		catch (SocketException ex) {
			throw new InvalidConfigurationException("Invalid adapter name, " + adapter);
		}

		if ( ni == null)
			throw new InvalidConfigurationException("Invalid network adapter name, " + adapter);

		// Get the IP address for the adapter
		InetAddress adapAddr = null;
		Enumeration<InetAddress> addrEnum = ni.getInetAddresses();

		while (addrEnum.hasMoreElements() && adapAddr == null) {

			// Get the current address
			InetAddress addr = addrEnum.nextElement();
			if ( IPAddress.isNumericAddress(addr.getHostAddress()))
				adapAddr = addr;
		}

		// Check if we found the IP address to bind to
		if ( adapAddr == null)
			throw new InvalidConfigurationException("Adapter " + adapter + " does not have a valid IP address");

		// Return the adapter address
		return adapAddr;
	}

    /**
     * Determine the broadcast mask to be used
     *
     * @return String
     */
    protected final String determineBroadcastMask() {


        String bcastMask = null;

        try {

            // Enumerate the available network interfaces
            Enumeration<NetworkInterface> niEnum = NetworkInterface.getNetworkInterfaces();

            while ( bcastMask == null && niEnum.hasMoreElements()) {

                // Get the current network interface
                NetworkInterface curIface = niEnum.nextElement();

                // Ignore the loopback adapter
                if ( curIface.isLoopback())
                    continue;

                // Ignore adapters that are not up and running
                if ( curIface.isUp() == false)
                    continue;

                // Get the interface address(es)
                List<InterfaceAddress> addrList = curIface.getInterfaceAddresses();

                 if ( addrList != null && addrList.size() > 0) {

                     int idx = 0;

                     while ( idx < addrList.size() && bcastMask == null) {

                         // Get the current interface address
                         InterfaceAddress ifAddr = addrList.get( idx++);
                         InetAddress bcastAddr = ifAddr.getBroadcast();

                         if ( bcastAddr != null && bcastAddr instanceof Inet4Address) {
                             bcastMask = bcastAddr.toString();

                             if ( bcastMask != null) {

                                 // Strip leading slash
                                 if (bcastMask.startsWith("/"))
                                     bcastMask = bcastMask.substring(1);

                                 // Check for the auto-configuration address
                                 if (bcastMask.startsWith("169.254."))
                                     bcastMask = null;
                             }
                         }
                     }
                 }
            }
        }
        catch ( Exception ex) {
        }

        // Return the broadcast mask, or null
        return bcastMask;
    }

    /**
     * Get the default number of threads to create in the thread pool
     *
     * @return int
     */
    protected int getDefaultThreads() {
        return DefaultThreadPoolInit;
    }

    /**
     * Get the maximum number of threads to create in the thread pool
     *
     * @return int
     */
    protected int getMaximumThreads() {
        return DefaultThreadPoolMax;
    }

    /**
     * Get the memory pool buffer sizes
     *
     * @return int[]
     */
    protected int[] getMemoryBufferSizes() {
        return DefaultMemoryPoolBufSizes;
    }

    /**
     * Get the memory pool buffer initial allocations for each buffer size
     *
     * @return int[]
     */
    protected int[] getMemoryBufferAllocations() {
        return DefaultMemoryPoolInitAlloc;
    }

    /**
     * Get the memory pool buffer maximum allocations for each buffer size
     *
     * @return int[]
     */
    protected int[] getMemoryBufferMaximumAllocations() {
        return DefaultMemoryPoolMaxAlloc;
    }
}
