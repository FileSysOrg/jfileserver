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

package org.filesys.app;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.StringTokenizer;

import org.filesys.ftp.FTPSiteInterface;
import org.filesys.ftp.FTPConfigSection;
import org.filesys.ftp.FTPPath;
import org.filesys.ftp.InvalidPathException;
import org.filesys.oncrpc.nfs.NFSConfigSection;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.filesys.cache.hazelcast.ClusterConfigSection;
import org.springframework.extensions.config.ConfigElement;
import org.springframework.extensions.config.element.ConfigElementAdapter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * XML File Server Configuration Class
 * 
 * <p>
 * XML implementation of the SMB server configuration. Save/load the server configuration to an XML
 * format file using the DOM API.
 * 
 * @author gkspencer
 */
public class XMLServerConfiguration extends SMBOnlyXMLServerConfiguration {

	// Constants
	//
	// Default FTP server port and anonymous account name
	private static final int DEFAULT_FTP_PORT = 21;
	private static final String ANONYMOUS_FTP_ACCOUNT = "anonymous";

	// FTP server debug type strings
	private static final String m_ftpDebugStr[] = { "STATE", "RXDATA", "TXDATA", "DUMPDATA", "SEARCH", "INFO", "FILE", "FILEIO",
			"ERROR", "PKTTYPE", "TIMING", "DATAPORT", "DIRECTORY", "SSL" };

	// NFS server debug type strings
	private static final String m_nfsDebugStr[] = { "RXDATA", "TXDATA", "DUMPDATA", "SEARCH", "INFO", "FILE", "FILEIO", "ERROR",
			"TIMING", "DIRECTORY", "SESSION" };

	// Global server enable flags
	private boolean m_smbEnabled;
	private boolean m_ftpEnabled;
	private boolean m_nfsEnabled;

	/**
	 * Default constructor
	 */
	public XMLServerConfiguration() {
		super();
	}

	/**
	 * Load the configuration from the specified document
	 * 
	 * @param doc Document
	 * @exception IOException Error reading the document
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

			// Process the cluster settings element
			procClusterElement(findChildNode("cluster", childNodes));
			
			// Process the debug settings element
			procDebugElement(findChildNode("debug", childNodes));

			// Process the main server enable element
			procServersElement(findChildNode("servers", childNodes));

			// Process the core server configuration settings
			procServerCoreElement(findChildNode("server-core", childNodes));
			
			// Process the global configuration settings
			procGlobalElement(findChildNode("global", childNodes));

			// Process the security element
			procSecurityElement(findChildNode("security", childNodes));

			// Process the shares element
			procSharesElement(findChildNode("shares", childNodes));

			// Process the SMB server specific settings
			if ( isSMBServerEnabled())
				procSMBServerElement(findChildNode("SMB", childNodes));

			// Process the FTP server configuration
			if ( isFTPServerEnabled())
				procFTPServerElement(findChildNode("FTP", childNodes));

			// Process the NFS server configuration
			if ( isNFSServerEnabled())
				procNFSServerElement(findChildNode("NFS", childNodes));
		}
		catch (Exception ex) {

			// Rethrow the exception as a configuration exeception
			throw new InvalidConfigurationException("XML error", ex);
		}
	}

	/**
	 * Check if the SMB server is enabled
	 * 
	 * @return boolean
	 */
	public final boolean isSMBServerEnabled() {
		return m_smbEnabled;
	}

	/**
	 * Check if the FTP server is enabled
	 * 
	 * @return boolean
	 */
	public final boolean isFTPServerEnabled() {
		return m_ftpEnabled;
	}

	/**
	 * Check if the NFS server is enabled
	 * 
	 * @return boolean
	 */
	public final boolean isNFSServerEnabled() {
		return m_nfsEnabled;
	}

	/**
	 * Process the servers XML element
	 * 
	 * @param servers Element
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected final void procServersElement(Element servers)
		throws InvalidConfigurationException {

		// Check if the servers element has been specified, if not then this is an old format
		// configuration
		if ( servers != null) {

			// Check if the SMB server is enabled
			Element srvElem = findChildNode("SMB", servers.getChildNodes());

			if ( srvElem != null) {
				String srvEnableStr = getAttributeWithEnvVars( srvElem, "enable");
				if ( srvEnableStr != null)
					m_smbEnabled = Boolean.parseBoolean( srvEnableStr);
				else
					m_smbEnabled = true;
			}
			else {

				// No 'SMB' element, server not enabled
				m_smbEnabled = false;
			}

			// Check if the FTP server is enabled
			srvElem = findChildNode("FTP", servers.getChildNodes());

			if ( srvElem != null) {
				String srvEnableStr = getAttributeWithEnvVars( srvElem, "enable");
				if ( srvEnableStr != null)
					m_ftpEnabled = Boolean.parseBoolean( srvEnableStr);
				else
					m_ftpEnabled = true;
			}
			else {

				// No 'FTP' element, server not enabled
				m_ftpEnabled = false;
			}

			// Check if the NFS server is enabled
			srvElem = findChildNode("NFS", servers.getChildNodes());

			if ( srvElem != null) {
				String srvEnableStr = getAttributeWithEnvVars( srvElem, "enable");
				if ( srvEnableStr != null)
					m_nfsEnabled = Boolean.parseBoolean( srvEnableStr);
				else
					m_nfsEnabled = true;
			}
			else {

				// No 'NFS' element, server not enabled
				m_nfsEnabled = false;
			}
		}
	}

	/**
	 * Process the FTP server XML element
	 * 
	 * @param ftp Element
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected final void procFTPServerElement(Element ftp)
		throws InvalidConfigurationException {

		// Check if the FTP element is valid, if not then disable the FTP server
		if ( ftp == null) {

			// Check if the FTP server is enabled, if so then there must be an FTP configuration
			// section
			if ( isFTPServerEnabled())
				throw new InvalidConfigurationException("FTP server enabled, but not configured");
			return;
		}

		// Create the FTP server configuration section
		FTPConfigSection ftpConfig = new FTPConfigSection(this);

		// Check for a bind address
		Element elem = findChildNode("bindto", ftp.getChildNodes());
		if ( elem != null) {

			// Check if the network adapter name has been specified
			if ( elem.hasAttribute("adapter")) {

				// Get the IP address for the adapter
				InetAddress bindAddr = parseAdapterName(elem.getAttribute("adapter"));

				// Set the bind address for the server
				ftpConfig.setFTPBindAddress(bindAddr);
			}
			else {

				// Validate the bind address
				String bindText = getText(elem);

				try {

					// Check the bind address
					InetAddress bindAddr = InetAddress.getByName(bindText);

					// Set the bind address for the FTP server
					ftpConfig.setFTPBindAddress(bindAddr);
				}
				catch (UnknownHostException ex) {
					throw new InvalidConfigurationException(ex.toString());
				}
			}
		}

		// Check for an FTP server port
		elem = findChildNode("port", ftp.getChildNodes());
		if ( elem != null) {
			try {
				ftpConfig.setFTPPort(Integer.parseInt(getText(elem)));
				if ( ftpConfig.getFTPPort() <= 0 || ftpConfig.getFTPPort() >= 65535)
					throw new InvalidConfigurationException("FTP server port out of valid range");
			}
			catch (NumberFormatException ex) {
				throw new InvalidConfigurationException("Invalid FTP server port");
			}
		}
		else {

			// Use the default FTP port
			ftpConfig.setFTPPort(DEFAULT_FTP_PORT);
		}

		// Check if anonymous login is allowed
		elem = findChildNode("allowAnonymous", ftp.getChildNodes());
		if ( elem != null) {

			// Enable anonymous login to the FTP server
			ftpConfig.setAllowAnonymousFTP(true);

			// Check if an anonymous account has been specified
			String anonAcc = elem.getAttribute("user");
			if ( anonAcc != null && anonAcc.length() > 0) {

				// Set the anonymous account name
				ftpConfig.setAnonymousFTPAccount(anonAcc);

				// Check if the anonymous account name is valid
				if ( ftpConfig.getAnonymousFTPAccount() == null || ftpConfig.getAnonymousFTPAccount().length() == 0)
					throw new InvalidConfigurationException("Anonymous FTP account invalid");
			}
			else {

				// Use the default anonymous account name
				ftpConfig.setAnonymousFTPAccount(ANONYMOUS_FTP_ACCOUNT);
			}
		}
		else {

			// Disable anonymous logins
			ftpConfig.setAllowAnonymousFTP(false);
		}

		// Check if a root path has been specified
		elem = findChildNode("rootDirectory", ftp.getChildNodes());
		if ( elem != null) {

			// Get the root path
			String rootPath = getText(elem);

			// Validate the root path
			try {

				// Parse the path
				new FTPPath(rootPath);

				// Set the root path
				ftpConfig.setFTPRootPath(rootPath);
			}
			catch (InvalidPathException ex) {
				throw new InvalidConfigurationException("Invalid FTP root directory, " + rootPath);
			}
		}

		// Check if a data port range has been specified
		elem = findChildNode("dataPorts", ftp.getChildNodes());
		if ( elem != null) {

			// Check for the from port range value
			int rangeFrom = -1;
			int rangeTo = -1;

			String rangeStr = elem.getAttribute("rangeFrom");
			if ( rangeStr != null && rangeStr.length() > 0) {

				// Validate the range string
				try {
					rangeFrom = Integer.parseInt(rangeStr);
				}
				catch (NumberFormatException ex) {
					throw new InvalidConfigurationException("Invalid FTP rangeFrom value, " + rangeStr);
				}
			}

			// Check for the to port range value
			rangeStr = elem.getAttribute("rangeTo");
			if ( rangeStr != null && rangeStr.length() > 0) {

				// Validate the range string
				try {
					rangeTo = Integer.parseInt(rangeStr);
				}
				catch (NumberFormatException ex) {
					throw new InvalidConfigurationException("Invalid FTP rangeTo value, " + rangeStr);
				}
			}

			// Validate the data port range values
			if ( rangeFrom == -1 || rangeTo == -1)
				throw new InvalidConfigurationException("FTP data port range from/to must be specified");

			if ( rangeFrom < 1024 || rangeFrom > 65535)
				throw new InvalidConfigurationException("Invalid FTP data port rangeFrom value, " + rangeFrom);

			if ( rangeTo < 1024 || rangeTo > 65535)
				throw new InvalidConfigurationException("Invalid FTP data port rangeTo value, " + rangeTo);

			if ( rangeFrom >= rangeTo)
				throw new InvalidConfigurationException("Invalid FTP data port range, " + rangeFrom + "-" + rangeTo);

			// Set the FTP data port range
			ftpConfig.setFTPDataPortLow(rangeFrom);
			ftpConfig.setFTPDataPortHigh(rangeTo);
		}

		// Check if FTP debug is enabled
		elem = findChildNode("debug", ftp.getChildNodes());
		if ( elem != null) {

			// Check for FTP debug flags
			String flags = elem.getAttribute("flags");
			int ftpDbg = 0;

			if ( flags != null) {

				// Parse the flags
				flags = flags.toUpperCase();
				StringTokenizer token = new StringTokenizer(flags, ",");

				while (token.hasMoreTokens()) {

					// Get the current debug flag token
					String dbg = token.nextToken().trim();

					// Find the debug flag name
					int idx = 0;

					while (idx < m_ftpDebugStr.length && m_ftpDebugStr[idx].equalsIgnoreCase(dbg) == false)
						idx++;

					if ( idx >= m_ftpDebugStr.length)
						throw new InvalidConfigurationException("Invalid FTP debug flag, " + dbg);

					// Set the debug flag
					ftpDbg += 1 << idx;
				}
			}

			// Set the FTP debug flags
			ftpConfig.setFTPDebug(ftpDbg);
		}

		// Check if a site interface has been specified
		elem = findChildNode("siteInterface", ftp.getChildNodes());
		if ( elem != null) {

			// Get the site interface class name
			Element classElem = findChildNode("class", elem.getChildNodes());
			if ( classElem == null)
				throw new InvalidConfigurationException("Class not specified for FTP site interface");

			String siteClass = getText(classElem);

			// Validate the site interface class
			try {

				// Load the driver class
				Object siteObj = Class.forName(siteClass).newInstance();
				if ( siteObj instanceof FTPSiteInterface) {

					// Initialize the site interface
					ConfigElement params = buildConfigElement(elem);
					FTPSiteInterface ftpSiteInterface = (FTPSiteInterface) siteObj;

					ftpSiteInterface.initializeSiteInterface(this, params);

					// Set the site interface
					ftpConfig.setFTPSiteInterface(ftpSiteInterface);
				}
			}
			catch (ClassNotFoundException ex) {
				throw new InvalidConfigurationException("FTP site interface class " + siteClass + " not found");
			}
			catch (Exception ex) {
				throw new InvalidConfigurationException("FTP site interface setup error, " + ex.toString());
			}
		}

		// Check if an authenticator has been specified
		elem = findChildNode("authenticator", ftp.getChildNodes());
		if ( elem != null) {

			// Get the FTP authenticator class
			Element classElem = findChildNode("class", elem.getChildNodes());
			if ( classElem == null)
				throw new InvalidConfigurationException("FTP Authenticator class not specified");

			// Get the parameters for the FTP authenticator class
			ConfigElement params = buildConfigElement(elem);
			ftpConfig.setAuthenticator(getText(classElem), params);
		}

		// FTPS parameter parsing
		//
		// Check if a key store path has been specified
		elem = findChildNode("keyStore", ftp.getChildNodes());
		if ( elem != null) {

			// Get the path to the key store, check that the file exists
			String keyStorePath = getText( elem);
			File keyStoreFile = new File( keyStorePath);
			
			if ( keyStoreFile.exists() == false)
				throw new InvalidConfigurationException("FTPS key store file does not exist, " + keyStorePath);
			else if ( keyStoreFile.isDirectory())
				throw new InvalidConfigurationException("FTPS key store path is a directory, " + keyStorePath);
			
			// Set the key store path
			ftpConfig.setKeyStorePath( keyStorePath);
		}

		// Check if a key store type has been specified
		elem = findChildNode("keyStoreType", ftp.getChildNodes());
		if ( elem != null) {
			
			// Get the key store type, and validate
			String keyStoreType = getText( elem);
			
			if ( keyStoreType == null || keyStoreType.length() == 0)
				throw new InvalidConfigurationException("FTPS key store type is invalid");
			
			try {
				KeyStore.getInstance( keyStoreType);
			}
			catch ( KeyStoreException ex) {
				throw new InvalidConfigurationException("FTPS key store type is invalid, " + keyStoreType, ex);
			}
			
			// Set the key store type
			ftpConfig.setKeyStoreType( keyStoreType);
		}
		
		// Check if the key store passphrase has been specified
		elem = findChildNode("keyStorePassphrase", ftp.getChildNodes());
		if ( elem != null) {

			// Set the key store passphrase
			ftpConfig.setKeyStorePassphrase( getText( elem));
		}
		
		// Check if the trust store path has been specified
		elem = findChildNode("trustStore", ftp.getChildNodes());
		if ( elem != null) {

			// Get the path to the trust store, check that the file exists
			String trustStorePath = getText( elem);
			File trustStoreFile = new File( trustStorePath);
			
			if ( trustStoreFile.exists() == false)
				throw new InvalidConfigurationException("FTPS trust store file does not exist, " + trustStorePath);
			else if ( trustStoreFile.isDirectory())
				throw new InvalidConfigurationException("FTPS trust store path is a directory, " + trustStorePath);
			
			// Set the trust store path
			ftpConfig.setTrustStorePath( trustStorePath);
		}
		
		// Check if a trust store type has been specified
		elem = findChildNode("trustStoreType", ftp.getChildNodes());
		if ( elem != null) {
			
			// Get the trust store type, and validate
			String trustStoreType = getText( elem);
			
			if ( trustStoreType == null || trustStoreType.length() == 0)
				throw new InvalidConfigurationException("FTPS trust store type is invalid");
			
			try {
				KeyStore.getInstance( trustStoreType);
			}
			catch ( KeyStoreException ex) {
				throw new InvalidConfigurationException("FTPS trust store type is invalid, " + trustStoreType, ex);
			}
			
			// Set the trust store type
			ftpConfig.setTrustStoreType( trustStoreType);
		}
		
		// Check if the trust store passphrase has been specified
		elem = findChildNode("trustStorePassphrase", ftp.getChildNodes());
		if ( elem != null) {

			// Set the key store passphrase
			ftpConfig.setTrustStorePassphrase( getText( elem));
		}
		
		// Check if only secure sessions should be allowed to logon
		elem = findChildNode("requireSecureSession", ftp.getChildNodes());
		if ( elem != null) {

			// Only allow secure sessions to logon to the FTP server
			ftpConfig.setRequireSecureSession( true);
		}
		
		// Check that all the required FTPS parameters have been set
		if ( ftpConfig.getKeyStorePath() != null) {
			
			// Make sure all parameters are set
			if ( ftpConfig.getKeyStorePath() == null)
				throw new InvalidConfigurationException("FTPS configuration requires keyStore to be set");
		}
		
		// Check if SSLEngine debug output should be enabled
		elem = findChildNode("sslEngineDebug", ftp.getChildNodes());
		if ( elem != null) {

			// Enable SSLEngine debug output
			System.setProperty("javax.net.debug", "ssl,handshake");
		}
	}

	/**
	 * Process the NFS server XML element
	 * 
	 * @param nfs Element
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected final void procNFSServerElement(Element nfs)
		throws InvalidConfigurationException {

		// Check if the NFS element is valid
		if ( nfs == null)
			return;

		// Create the NFS server configuration section
		NFSConfigSection nfsConfig = new NFSConfigSection(this);

		// Check if the port mapper is enabled
		if ( findChildNode("enablePortMapper", nfs.getChildNodes()) != null)
			nfsConfig.setNFSPortMapper(true);

		// Check for the thread pool size
		Element elem = findChildNode("ThreadPool", nfs.getChildNodes());

		// Check for the old TCPThreadPool value if the new value is not available
		if ( elem == null)
			elem = findChildNode("TCPThreadPool", nfs.getChildNodes());

		if ( elem != null) {

			try {

				// Convert the pool size value
				int poolSize = Integer.parseInt(getText(elem));

				// Range check the pool size value
				if ( poolSize < 4)
					throw new InvalidConfigurationException("NFS thread pool size is below minimum of 4");

				// Set the thread pool size
				nfsConfig.setNFSThreadPoolSize(poolSize);
			}
			catch (NumberFormatException ex) {
				throw new InvalidConfigurationException("Invalid NFS thread pool size setting, " + getText(elem));
			}
		}

		// NFS packet pool size
		elem = findChildNode("PacketPool", nfs.getChildNodes());

		if ( elem != null) {

			try {

				// Convert the packet pool size value
				int pktPoolSize = Integer.parseInt(getText(elem));

				// Range check the pool size value
				if ( pktPoolSize < 10)
					throw new InvalidConfigurationException("NFS packet pool size is below minimum of 10");

				if ( pktPoolSize < nfsConfig.getNFSThreadPoolSize() + 1)
					throw new InvalidConfigurationException("NFS packet pool must be at least thread pool size plus one");

				// Set the packet pool size
				nfsConfig.setNFSPacketPoolSize(pktPoolSize);
			}
			catch (NumberFormatException ex) {
				throw new InvalidConfigurationException("Invalid NFS packet pool size setting, " + getText(elem));
			}
		}

		// Check for a port mapper server port
		if ( findChildNode("disablePortMapperRegistration", nfs.getChildNodes()) != null) {
			
			// Disable port mapper registration for the mount/NFS servers
			nfsConfig.setPortMapperPort( -1);
		}
		else {
			elem = findChildNode("PortMapperPort", nfs.getChildNodes());
			if ( elem != null) {
				try {
					nfsConfig.setPortMapperPort(Integer.parseInt(getText(elem)));
					if ( nfsConfig.getPortMapperPort() <= 0 || nfsConfig.getPortMapperPort() >= 65535)
						throw new InvalidConfigurationException("Port mapper server port out of valid range");
				}
				catch (NumberFormatException ex) {
					throw new InvalidConfigurationException("Invalid port mapper server port");
				}
			}
		}

		// Check for a mount server port
		elem = findChildNode("MountServerPort", nfs.getChildNodes());
		if ( elem != null) {
			try {
				nfsConfig.setMountServerPort(Integer.parseInt(getText(elem)));
				if ( nfsConfig.getMountServerPort() <= 0 || nfsConfig.getMountServerPort() >= 65535)
					throw new InvalidConfigurationException("Mount server port out of valid range");
			}
			catch (NumberFormatException ex) {
				throw new InvalidConfigurationException("Invalid mount server port");
			}
		}

		// Check for an NFS server port
		elem = findChildNode("NFSServerPort", nfs.getChildNodes());
		if ( elem != null) {
			try {
				nfsConfig.setNFSServerPort(Integer.parseInt(getText(elem)));
				if ( nfsConfig.getNFSServerPort() <= 0 || nfsConfig.getNFSServerPort() >= 65535)
					throw new InvalidConfigurationException("NFS server port out of valid range");
			}
			catch (NumberFormatException ex) {
				throw new InvalidConfigurationException("Invalid NFS server port");
			}
		}

		// Check for an RPC registration port
		elem = findChildNode("RPCRegisterPort", nfs.getChildNodes());
		if ( elem != null) {
			try {
				nfsConfig.setRPCRegistrationPort(Integer.parseInt(getText(elem)));
				if ( nfsConfig.getRPCRegistrationPort() <= 0 || nfsConfig.getRPCRegistrationPort() >= 65535)
					throw new InvalidConfigurationException("RPC registration port out of valid range");
			}
			catch (NumberFormatException ex) {
				throw new InvalidConfigurationException("Invalid RPC registration port");
			}
		}

		// Check if an RPC authenticator has been specified
		elem = findChildNode("rpcAuthenticator", nfs.getChildNodes());
		if ( elem != null) {

			// Get the RPC authenticator class
			Element classElem = findChildNode("class", elem.getChildNodes());
			if ( classElem == null)
				throw new InvalidConfigurationException("RPC Authenticator class not specified");

			// Get the parameters for the RPC authenticator class
			ConfigElement params = buildConfigElement(elem);
			nfsConfig.setRpcAuthenticator(getText(classElem), params);
		}
		else {
			
			// Use the null RPC authenticator as the default
			nfsConfig.setRpcAuthenticator( "DefaultRpcAuthenticator", new ConfigElementAdapter( "", ""));
		}

		// Check if NFS debug is enabled
		elem = findChildNode("debug", nfs.getChildNodes());
		if ( elem != null) {

			// Check for NFS debug flags
			String flags = elem.getAttribute("flags");
			int nfsDbg = 0;

			if ( flags != null) {

				// Parse the flags
				flags = flags.toUpperCase();
				StringTokenizer token = new StringTokenizer(flags, ",");

				while (token.hasMoreTokens()) {

					// Get the current debug flag token
					String dbg = token.nextToken().trim();

					// Find the debug flag name
					int idx = 0;

					while (idx < m_nfsDebugStr.length && m_nfsDebugStr[idx].equalsIgnoreCase(dbg) == false)
						idx++;

					if ( idx >= m_nfsDebugStr.length)
						throw new InvalidConfigurationException("Invalid NFS debug flag, " + dbg);

					// Set the debug flag
					nfsDbg += 1 << idx;
				}
			}

			// Set the NFS debug flags
			nfsConfig.setNFSDebug(nfsDbg);
		}

		// Check if mount server debug output is enabled
		elem = findChildNode("mountServerDebug", nfs.getChildNodes());
		if ( elem != null)
			nfsConfig.setMountServerDebug(true);

		// Check if port mapper debug output is enabled
		elem = findChildNode("portMapperDebug", nfs.getChildNodes());
		if ( elem != null)
			nfsConfig.setPortMapperDebug(true);

		// Check if the file cache timers have been specified
		elem = findChildNode("FileCache", nfs.getChildNodes());

		if ( elem != null) {
			try {

				// Check for a single value or I/O and close timer values
				String numVal = getText(elem);
				long cacheIOTimer = -1;
				long cacheCloseTimer = -1;

				int pos = numVal.indexOf(':');

				if ( pos == -1) {

					// Only change the I/O timer
					cacheIOTimer = Integer.parseInt(numVal);
				}
				else {

					// Split the string value into read and write values, and convert to integers
					String val = numVal.substring(0, pos);
					cacheIOTimer = Integer.parseInt(val);

					val = numVal.substring(pos + 1);
					cacheCloseTimer = Integer.parseInt(val);
				}

				// Range check the I/O timer
				if ( cacheIOTimer < 0 || cacheIOTimer > 30)
					throw new InvalidConfigurationException("Invalid NFS file cache I/O timer value, " + cacheIOTimer);
				else {

					// Convert the timer to milliseconds
					nfsConfig.setNFSFileCacheIOTimer(cacheIOTimer * 1000L);
				}

				// Range check the close timer, if specified
				if ( cacheCloseTimer != -1) {
					if ( cacheCloseTimer < 0 || cacheCloseTimer > 120)
						throw new InvalidConfigurationException("Invalid NFS file cache close timer value, " + cacheCloseTimer);
					else {

						// Convert the timer to milliseconds
						nfsConfig.setNFSFileCacheCloseTimer(cacheCloseTimer * 1000L);
					}
				}
			}
			catch (NumberFormatException ex) {
				throw new InvalidConfigurationException("Invalid NFS file cache timer value, " + ex.toString());
			}
		}

		// Check if NFS file cache debug output is enabled
		if ( findChildNode("fileCacheDebug", nfs.getChildNodes()) != null)
			nfsConfig.setNFSFileCacheDebug(true);
	}
	
	/**
	 * Process the servers XML element
	 * 
	 * @param cluster Element
	 * @exception InvalidConfigurationException Error parsing the configuration
	 */
	protected final void procClusterElement(Element cluster)
		throws InvalidConfigurationException {

		// Check if the cluster element has been specified
		if ( cluster != null) {

			// Check if the Hazelcast classes are available on the classpath
			try {
				
				// Check for various Hazelcast classes
				Class.forName( "com.hazelcast.core.HazelcastInstance");
				Class.forName( "com.hazelcast.core.IMap");
				Class.forName( "com.hazelcast.core.ITopic");
				
				// Check for the cluster configuration section
				Class.forName("org.filesys.server.filesys.cache.hazelcast.ClusterConfigSection");
			}
			catch ( ClassNotFoundException ex) {
				throw new InvalidConfigurationException( "Hazelcast classes not found on the classpath, required for cluster support");
			}

			// Create the cluster configuration section
			ClusterConfigSection clusterConfig = new ClusterConfigSection( this);
			
			// Get the path to the HazelCast configuration file
			Element elem = findChildNode("configFile", cluster.getChildNodes());
			
			if ( elem != null) {
				
				// Check that the configuration file exists, and is a file
				String configPath = getText( elem);
				
				try {
					File confFile = new File( configPath);
					if ( confFile.exists() == false)
						throw new InvalidConfigurationException( "HazelCast configuration file does not exist, " + configPath);
					
					if ( confFile.isDirectory())
						throw new InvalidConfigurationException( "HazelCast configuration file is a folder path, " + configPath);
					
					// Set the Hazelcast cluster configuration file location
					clusterConfig.setConfigFile( configPath);
				}
				catch ( Exception ex) {
					throw new InvalidConfigurationException( "HazelCast configuration file not valid", ex);
				}
			}
			else
				throw new InvalidConfigurationException( "HazelCast configuration file not specified");
		}
	}
}
