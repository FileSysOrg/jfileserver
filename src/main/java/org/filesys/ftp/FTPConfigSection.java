/*
 * Copyright (C) 2005-2015 Alfresco Software Limited.
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

import java.net.InetAddress;

import org.springframework.extensions.config.ConfigElement;
import org.springframework.extensions.config.element.GenericConfigElement;
import org.filesys.server.config.ConfigId;
import org.filesys.server.config.ConfigSection;
import org.filesys.server.config.ConfigurationListener;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;

/**
 * FTP Server Configuration Section Class
 *
 * @author gkspencer
 */
public class FTPConfigSection extends ConfigSection {

    // FTP server configuration section name
    public static final String SectionName = "FTP";

    // Default key store/trust store types
    public static final String DefaultKeyStoreType = "JKS";
    public static final String DefaultTrustStoreType = "JKS";

    //  Bind address and FTP server port. A port of -1 indicates do not start FTP server.
    private InetAddress m_ftpBindAddress;
    private int m_ftpPort = -1;
    private int m_ftpSrvSessionTimeout = 0;

    //  Allow anonymous FTP access and anonymous FTP account name
    private boolean m_ftpAllowAnonymous;
    private String m_ftpAnonymousAccount;

    //  FTP root path, if not specified defaults to listing all shares as the root
    private String m_ftpRootPath;

    //  FTP data socket range
    private int m_ftpDataPortLow;
    private int m_ftpDataPortHigh;

    //  FTP authenticator interface
    private FTPAuthenticator m_ftpAuthenticator;

    // Is the authenticator instance owned by this object
    private boolean m_localFtpAuthenticator;

    //  FTP server debug flags
    private int m_ftpDebug;

    // FTP SITE interface
    private FTPSiteInterface m_ftpSiteInterface;

    // FTP character set
    private String m_ftpCharSet;

    // FTPS configuration
    //
    // Keystore/truststore details
    private String m_keyStorePath;
    private String m_keyStoreType = DefaultKeyStoreType;
    private char[] m_keyStorePass;

    private String m_trustStorePath;
    private String m_trustStoreType = DefaultTrustStoreType;
    private char[] m_trustStorePass;

    // Only allow FTPS/encrypted session logons
    private boolean m_requireSecureSess;

    /**
     * Class constructor
     *
     * @param config ServerConfiguration
     */
    public FTPConfigSection(ServerConfiguration config) {
        super(SectionName, config);

        //  Set the default FTP authenticator
        m_ftpAuthenticator = new LocalAuthenticator();
        m_localFtpAuthenticator = true;

        try {
            m_ftpAuthenticator.initialize(config, new GenericConfigElement("ftpAuthenticator"));
        }
        catch (InvalidConfigurationException ex) {
        }
    }

    /**
     * Return the FTP server bind address, may be null to indicate bind to all available addresses
     *
     * @return InetAddress
     */
    public final InetAddress getFTPBindAddress() {
        return m_ftpBindAddress;
    }

    /**
     * Return the FTP server port to use for incoming connections
     *
     * @return int
     */
    public final int getFTPPort() {
        return m_ftpPort;
    }

    public final int getFTPSrvSessionTimeout() {
        return m_ftpSrvSessionTimeout;
    }

    /**
     * Return the FTP authenticator interface
     *
     * @return FTPAuthenticator
     */
    public final FTPAuthenticator getFTPAuthenticator() {
        return m_ftpAuthenticator;
    }

    /**
     * Determine if anonymous FTP access is allowed
     *
     * @return boolean
     */
    public final boolean allowAnonymousFTP() {
        return m_ftpAllowAnonymous;
    }

    /**
     * Return the anonymous FTP account name
     *
     * @return String
     */
    public final String getAnonymousFTPAccount() {
        return m_ftpAnonymousAccount;
    }

    /**
     * Return the FTP debug flags
     *
     * @return int
     */
    public final int getFTPDebug() {
        return m_ftpDebug;
    }

    /**
     * Check if an FTP root path has been configured
     *
     * @return boolean
     */
    public final boolean hasFTPRootPath() {
        return m_ftpRootPath != null ? true : false;
    }

    /**
     * Return the FTP root path
     *
     * @return String
     */
    public final String getFTPRootPath() {
        return m_ftpRootPath;
    }

    /**
     * Determine if a port range is set for FTP data sockets
     *
     * @return boolean
     */
    public final boolean hasFTPDataPortRange() {
        if (m_ftpDataPortLow > 0 && m_ftpDataPortHigh > 0)
            return true;
        return false;
    }

    /**
     * Return the FTP data socket range low value
     *
     * @return int
     */
    public final int getFTPDataPortLow() {
        return m_ftpDataPortLow;
    }

    /**
     * Return the FTP data socket range high value
     *
     * @return int
     */
    public final int getFTPDataPortHigh() {
        return m_ftpDataPortHigh;
    }

    /**
     * Determine if the FTP SITE interface is enabled
     *
     * @return boolean
     */
    public final boolean hasFTPSiteInterface() {
        return m_ftpSiteInterface != null ? true : false;
    }

    /**
     * Return the FTP SITE interface
     *
     * @return FTPSiteInterface
     */
    public final FTPSiteInterface getFTPSiteInterface() {
        return m_ftpSiteInterface;
    }

    /**
     * Return the FTP character set
     *
     * @return String
     */
    public final String getFTPCharacterSet() {
        return m_ftpCharSet;
    }

    /**
     * Check if FTPS support is enabled
     *
     * @return boolean
     */
    public final boolean isFTPSEnabled() {
        // MNT-7301 FTPS server requires unnecessarly to have a trustStore while a keyStore should be sufficient
        if (getKeyStorePath() != null)
            return true;
        return false;
    }

    /**
     * Return the key store path
     *
     * @return String
     */
    public final String getKeyStorePath() {
        return m_keyStorePath;
    }

    /**
     * Return the key store type
     *
     * @return String
     */
    public final String getKeyStoreType() {
        return m_keyStoreType;
    }

    /**
     * Return the trust store path
     *
     * @return String
     */
    public final String getTrustStorePath() {
        return m_trustStorePath;
    }

    /**
     * Return the trust store type
     *
     * @return String
     */
    public final String getTrustStoreType() {
        return m_trustStoreType;
    }

    /**
     * Return the passphrase for the key store
     *
     * @return char[]
     */
    public final char[] getKeyStorePassphrase() {
        return m_keyStorePass;
    }

    /**
     * Return the passphrase for the trust store
     *
     * @return char[]
     */
    public final char[] getTrustStorePassphrase() {
        return m_trustStorePass;
    }

    /**
     * Detemrine if only secure sessions will be allowed to logon
     *
     * @return boolean
     */
    public final boolean requireSecureSession() {
        return m_requireSecureSess;
    }

    /**
     * Set the FTP character set
     *
     * @param charSet String
     */
    public final void setFTPCharacterSet(String charSet) {
        m_ftpCharSet = charSet;
    }

    /**
     * Set the FTP server bind address, may be null to indicate bind to all available addresses
     *
     * @param addr InetAddress
     * @return int
     * @exception InvalidConfigurationException Error setting the bind address
     */
    public final int setFTPBindAddress(InetAddress addr)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPBindAddress, addr);
        m_ftpBindAddress = addr;

        //  Return the change status
        return sts;
    }

    /**
     * Set the FTP server port to use for incoming connections, -1 indicates disable the FTP server
     *
     * @param port int
     * @return int
     * @exception InvalidConfigurationException Error setting the port
     */
    public final int setFTPPort(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPPort, new Integer(port));
        m_ftpPort = port;

        //  Return the change status
        return sts;
    }

    /**
     * Set the FTP server session timeout
     *
     * @param timeout int
     * @return int
     * @exception InvalidConfigurationException Error setting the timeout
     */
    public final int setFTPSrvSessionTimeout(int timeout)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPSrvSessionTimeout, new Integer(timeout));
        m_ftpSrvSessionTimeout = timeout;

        //  Return the change status
        return sts;
    }

    /**
     * Set the FTP server data port range low value
     *
     * @param port int
     * @return int
     * @exception InvalidConfigurationException Error setting the data port range
     */
    public final int setFTPDataPortLow(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPDataPortLow, new Integer(port));
        m_ftpDataPortLow = port;

        //  Return the change status
        return sts;
    }

    /**
     * Set the FTP server data port range high value
     *
     * @param port int
     * @return int
     * @exception InvalidConfigurationException Error setting the data port range
     */
    public final int setFTPDataPortHigh(int port)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPDataPortHigh, new Integer(port));
        m_ftpDataPortHigh = port;

        //  Return the change status
        return sts;
    }

    /**
     * Set the FTP root path
     *
     * @param path String
     * @return int
     * @exception InvalidConfigurationException Error setting the root path
     */
    public final int setFTPRootPath(String path)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPRootPath, path);
        m_ftpRootPath = path;

        //  Return the change status
        return sts;
    }

    /**
     * Enable/disable anonymous FTP access
     *
     * @param ena boolean
     * @return int
     * @exception InvalidConfigurationException Error setting the allow anonymous access flag
     */
    public final int setAllowAnonymousFTP(boolean ena)
            throws InvalidConfigurationException {

        //  Check if the value has changed
        int sts = ConfigurationListener.StsIgnored;

        if (m_ftpAllowAnonymous != ena) {

            //  Inform listeners, validate the configuration change
            sts = fireConfigurationChange(ConfigId.FTPAllowAnon, new Boolean(ena));
            m_ftpAllowAnonymous = ena;
        }

        //  Return the change status
        return sts;
    }

    /**
     * Set the anonymous FTP account name
     *
     * @param acc String
     * @return int
     * @exception InvalidConfigurationException Error setting the anonymous account name
     */
    public final int setAnonymousFTPAccount(String acc)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPAnonAccount, acc);
        m_ftpAnonymousAccount = acc;

        //  Return the change status
        return sts;
    }

    /**
     * Set the FTP debug flags
     *
     * @param dbg int
     * @return int
     * @exception InvalidConfigurationException Error setting the debug flags
     */
    public final int setFTPDebug(int dbg)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPDebugFlags, new Integer(dbg));
        m_ftpDebug = dbg;

        //  Return the change status
        return sts;
    }

    /**
     * Set the FTP SITE interface to handle custom FTP commands
     *
     * @param siteInterface FTPSiteInterface
     * @return int
     * @exception InvalidConfigurationException Error setting the site interface
     */
    public final int setFTPSiteInterface(FTPSiteInterface siteInterface)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPSiteInterface, siteInterface);
        m_ftpSiteInterface = siteInterface;

        //  Return the change status
        return sts;
    }

    /**
     * Set the authenticator to be used to authenticate FTP users.
     *
     * @param authClass String
     * @param params    ConfigElement
     * @return int
     * @exception InvalidConfigurationException Error setting the authenticator
     */
    public final int setAuthenticator(String authClass, ConfigElement params)
            throws InvalidConfigurationException {

        //  Validate the authenticator class
        int sts = ConfigurationListener.StsIgnored;
        FTPAuthenticator auth = null;

        try {

            //  Load the authenticator class
            Object authObj = Class.forName(authClass).newInstance();
            if (authObj instanceof FTPAuthenticator) {

                //  Set the server authenticator
                auth = (FTPAuthenticator) authObj;
            } else
                throw new InvalidConfigurationException("Authenticator is not derived from required base class");
        }
        catch (ClassNotFoundException ex) {
            throw new InvalidConfigurationException("Authenticator class " + authClass + " not found");
        }
        catch (Exception ex) {
            throw new InvalidConfigurationException("Authenticator class error");
        }

        //  Initialize the authenticator using the parameter values
        auth.initialize(getServerConfiguration(), params);

        //  Inform listeners, validate the configuration change
        sts = setAuthenticator(auth);

        // Remember that the authenticator instance will need destroying
        m_localFtpAuthenticator = true;

        //  Return the change status
        return sts;
    }

    /**
     * Set the authenticator to be used to authenticate FTP users.
     *
     * @param auth the authenticator
     * @return int
     * @exception InvalidConfigurationException Error setting the authenticator
     */
    public final int setAuthenticator(FTPAuthenticator auth)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPAuthenticator, auth);

        //  Set the FTP authenticator interface
        m_ftpAuthenticator = auth;
        m_localFtpAuthenticator = false;

        //  Return the change status
        return sts;
    }

    /**
     * Set the key store path
     *
     * @param path String
     * @return int
     * @exception InvalidConfigurationException Error setting the key store path
     */
    public final int setKeyStorePath(String path)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPKeyStore, path);

        //  Set the key store path
        m_keyStorePath = path;

        //  Return the change status
        return sts;
    }

    /**
     * Set the key store type
     *
     * @param typ String
     * @return int
     * @exception InvalidConfigurationException Error setting the key store type
     */
    public final int setKeyStoreType(String typ)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPKeyStoreType, typ);

        //  Set the key store type
        m_keyStoreType = typ;

        //  Return the change status
        return sts;
    }

    /**
     * Set the trust store path
     *
     * @param path String
     * @return int
     * @exception InvalidConfigurationException Error setting the trust store path
     */
    public final int setTrustStorePath(String path)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPTrustStore, path);

        //  Set the trust store path
        m_trustStorePath = path;

        //  Return the change status
        return sts;
    }

    /**
     * Set the trust store type
     *
     * @param typ String
     * @return int
     * @exception InvalidConfigurationException Error setting the trust store type
     */
    public final int setTrustStoreType(String typ)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPTrustStoreType, typ);

        //  Set the trust store type
        m_trustStoreType = typ;

        //  Return the change status
        return sts;
    }

    /**
     * Set the key store passphrase
     *
     * @param passphrase String
     * @return int
     * @exception InvalidConfigurationException Error setting the key store pass phrase
     */
    public final int setKeyStorePassphrase(String passphrase)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPKeyPassphrase, passphrase);

        //  Set the key store passphrase
        m_keyStorePass = passphrase.toCharArray();

        //  Return the change status
        return sts;
    }

    /**
     * Set the trust store passphrase
     *
     * @param passphrase String
     * @return int
     * @exception InvalidConfigurationException Error setting the trust store pass phrase
     */
    public final int setTrustStorePassphrase(String passphrase)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPTrustPassphrase, passphrase);

        //  Set the trust store passphrase
        m_trustStorePass = passphrase.toCharArray();

        //  Return the change status
        return sts;
    }

    /**
     * Set the require secure session flag
     *
     * @param reqSecureSess boolean
     * @return int
     * @exception InvalidConfigurationException Error setting the secure session flag
     */
    public final int setRequireSecureSession(boolean reqSecureSess)
            throws InvalidConfigurationException {

        //  Inform listeners, validate the configuration change
        int sts = fireConfigurationChange(ConfigId.FTPRequireSecure, new Boolean(reqSecureSess));

        //  Set the require secure session flag
        m_requireSecureSess = reqSecureSess;

        //  Return the change status
        return sts;
    }

    /**
     * Close the configuration section
     */
    public final void closeConfig() {
        if (m_localFtpAuthenticator && m_ftpAuthenticator != null) {
            m_ftpAuthenticator.closeAuthenticator();
        }
        m_ftpAuthenticator = null;
    }
}
