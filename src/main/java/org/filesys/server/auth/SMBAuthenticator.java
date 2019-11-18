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

package org.filesys.server.auth;

import java.net.InetAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.SecurityConfigSection;
import org.filesys.server.config.ServerConfigurationAccessor;
import org.filesys.smb.DialectSelector;
import org.filesys.smb.server.*;
import org.filesys.util.DataPacker;
import org.springframework.extensions.config.ConfigElement;
import org.filesys.debug.Debug;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.server.SrvSession;
import org.filesys.server.auth.passthru.DomainMapping;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.server.core.NoPooledMemoryException;
import org.filesys.server.core.SharedDevice;
import org.filesys.smb.Capability;
import org.filesys.smb.Dialect;
import org.filesys.smb.SMBStatus;
import org.filesys.util.HexDump;
import org.filesys.util.IPAddress;

/**
 * SMB Authenticator Class
 *
 * <p>
 * An authenticator is used by the SMB server to authenticate users when in user level access mode
 * and authenticate requests to connect to a share when in share level access.
 *
 * @author gkspencer
 */
public abstract class SMBAuthenticator implements ISMBAuthenticator {

    // Server access mode
    protected static final String GUEST_USERNAME = "guest";

    // Default SMB dialects to enable
    private DialectSelector m_dialects;

    // Security mode flags
    private int m_securityMode = SecurityMode.UserMode + SecurityMode.EncryptedPasswords;

    // Password encryption algorithms
    private PasswordEncryptor m_encryptor = new PasswordEncryptor();

    // Server access mode
    private AuthMode m_accessMode = AuthMode.USER;

    // Enable extended security mode
    private boolean m_extendedSecurity;

    // Flag to enable/disable the guest account, and control mapping of unknown users to the guest account
    private boolean m_allowGuest;
    private boolean m_mapToGuest;

    // Default guest user name
    private String m_guestUserName = GUEST_USERNAME;

    // Random number generator used to generate challenge keys
    protected Random m_random = new Random(System.currentTimeMillis());

    // Server configuration and required sections
    protected ServerConfigurationAccessor m_config;

    // Cleanup sessions from the same client address/name if a session setup using virtual circuit zero is received
    private boolean m_sessCleanup = true;

    // Debug output enable
    private boolean m_debug;

    /**
     * @param debug activate debug mode?
     */
    public void setDebug(boolean debug) {

        this.m_debug = debug;
    }

    /**
     * @param config an accessor for the file server configuration sections
     */
    public void setConfig(ServerConfigurationAccessor config) {
        this.m_config = config;
    }

    @Override
    public ShareStatus authenticateShareConnect(ClientInfo client, SharedDevice share, String sharePwd, SrvSession sess) {

        // Allow write access
        //
        // Main authentication is handled by authenticateUser()
        return ShareStatus.WRITEABLE;
    }

    @Override
    public AuthStatus authenticateUser(ClientInfo client, SrvSession sess, PasswordAlgorithm alg) {

        // Check if the user exists in the user list
        UserAccount userAcc = getUserDetails(client.getUserName());

        if (userAcc != null) {

            // Validate the password
            boolean authSts = false;

            if (client.getPassword() != null) {

                // Validate using the Unicode password
                authSts = validatePassword(userAcc, client, sess.getAuthenticationContext(), alg);
            }
            else if (client.hasANSIPassword()) {

                // Validate using the ANSI password with the LanMan encryption
                authSts = validatePassword(userAcc, client, sess.getAuthenticationContext(), PasswordAlgorithm.LANMAN);
            }

            // Return the authentication status
            return authSts == true ? AuthStatus.AUTHENTICATED : AuthStatus.BAD_PASSWORD;
        }

        // Check if this is an SMB null session logon.
        //
        // The null session will only be allowed to connect to the IPC$ named pipe share.
        if (client.isNullSession() && sess instanceof SMBSrvSession)
            return AuthStatus.AUTHENTICATED;

        // Unknown user
        return allowGuest() ? AuthStatus.GUEST_LOGON : AuthStatus.DISALLOW;
    }

    /**
     * Initialize the authenticator, after properties have been set
     *
     * @exception InvalidConfigurationException Error initializing the authenticator
     */
    public void initialize()
            throws InvalidConfigurationException {

        // Check all required properties have been set
        if (m_config == null)
            throw new InvalidConfigurationException("server configuration accessor not set");

        // Allocate the SMB dialect selector, and initialize using the default list of dialects
        m_dialects = new DialectSelector();
        m_dialects.enableUpTo( Dialect.UpToSMBv1);
    }

    /**
     * Initialize the authenticator
     *
     * @param config ServerConfiguration
     * @param params ConfigElement
     * @exception InvalidConfigurationException Error initializing the authenticator
     */
    public void initialize(ServerConfiguration config, ConfigElement params)
            throws InvalidConfigurationException {

        if (params.getChild("Debug") != null)
            setDebug(true);

        // Save the server configuration so we can access the authentication component
        setConfig(config);

        initialize();
    }

    /**
     * Encrypt the plain text password with the specified encryption key using the specified
     * encryption algorithm.
     *
     * @param plainPwd   String
     * @param encryptKey byte[]
     * @param alg        PasswordAlgorithm
     * @param userName   String
     * @param domain     String
     * @return byte[]
     */
    protected final byte[] generateEncryptedPassword(String plainPwd, byte[] encryptKey, PasswordAlgorithm alg, String userName, String domain) {

        // Map to the password encryption algorithm
        int passAlg = 0;

        switch ( alg) {
            case LANMAN:
                passAlg = org.filesys.client.PasswordEncryptor.LANMAN;
                break;
            case NTLM1:
                passAlg = org.filesys.client.PasswordEncryptor.NTLM1;
                break;
            case NTLM2:
                passAlg = org.filesys.client.PasswordEncryptor.NTLM2;
                break;
        }

        // Use the password encryptor
        byte[] encPwd = null;

        try {
            // Encrypt the password
            encPwd = m_encryptor.generateEncryptedPassword(plainPwd, encryptKey, passAlg, userName, domain);
        }
        catch (NoSuchAlgorithmException ex) {
        }
        catch (InvalidKeyException ex) {
        }

        // Return the encrypted password
        return encPwd;
    }

    @Override
    public final AuthMode getAccessMode() {
        return m_accessMode;
    }

    /* (non-Javadoc)
     * @see org.filesys.server.auth.ICifsAuthenticator#hasExtendedSecurity()
     */
    public final boolean hasExtendedSecurity() {
        return m_extendedSecurity;
    }

    /* (non-Javadoc)
     * @see org.filesys.server.auth.ICifsAuthenticator#getAuthContext(SMBSrvSession)
     */
    public AuthContext getAuthContext(SMBSrvSession sess) {

        AuthContext authCtx = null;

        if (sess.hasAuthenticationContext() && sess.getAuthenticationContext() instanceof NTLanManAuthContext) {

            // Use the existing authentication context
            authCtx = sess.getAuthenticationContext();
        } else {

            // Create a new authentication context for the session
            authCtx = new NTLanManAuthContext();
            sess.setAuthenticationContext(authCtx);
        }

        // Return the authentication context
        return authCtx;
    }

    /* (non-Javadoc)
     * @see org.filesys.server.auth.ICifsAuthenticator#getEnabledDialects()
     */
    public final DialectSelector getEnabledDialects() {
        return m_dialects;
    }

    /* (non-Javadoc)
     * @see org.filesys.server.auth.ICifsAuthenticator#getSecurityMode()
     */
    public final int getSecurityMode() {
        return m_securityMode;
    }

    /* (non-Javadoc)
     * @see org.filesys.server.auth.ICifsAuthenticator#getSMBConfig()
     */
    public final SMBConfigSection getSMBConfig() {
        return (SMBConfigSection) m_config.getConfigSection(SMBConfigSection.SectionName);
    }

    /**
     * Return the security configuration section
     *
     * @return SecurityConfigSection
     */
    public final SecurityConfigSection getSecurityConfig() {
        return (SecurityConfigSection) m_config.getConfigSection(SecurityConfigSection.SectionName);
    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    @Override
    public void processSessionSetup(SMBSrvSession sess, SMBSrvPacket reqPkt)
            throws SMBSrvException {

        // Get the associated parser for the packet
        SMBV1Parser parser = (SMBV1Parser) reqPkt.getParser();

        // Check that the received packet looks like a valid NT session setup andX request
        if (parser.checkPacketIsValid(13, 0) == false) {
            throw new SMBSrvException(SMBStatus.NTInvalidParameter, SMBStatus.ErrSrv, SMBStatus.SRVNonSpecificError);
        }

        // Extract the session details
        int maxBufSize = parser.getParameter(2);
        int maxMpx = parser.getParameter(3);
        int vcNum = parser.getParameter(4);
        int ascPwdLen = parser.getParameter(7);
        int uniPwdLen = parser.getParameter(8);
        int capabs = parser.getParameterLong(11);

        // Extract the client details from the session setup request
        byte[] buf = parser.getBuffer();

        // Determine if ASCII or unicode strings are being used
        boolean isUni = parser.isUnicode();

        // Extract the password strings
        byte[] ascPwd = parser.unpackBytes(ascPwdLen);
        byte[] uniPwd = parser.unpackBytes(uniPwdLen);

        // Extract the user name string
        String user = parser.unpackString(isUni);

        if (user == null)
            throw new SMBSrvException(SMBStatus.NTInvalidParameter, SMBStatus.ErrSrv, SMBStatus.SRVNonSpecificError);

        // Extract the clients primary domain name string
        String domain = "";

        if (parser.hasMoreData()) {

            // Extract the callers domain name
            domain = parser.unpackString(isUni);

            if (domain == null)
                throw new SMBSrvException(SMBStatus.NTInvalidParameter, SMBStatus.ErrSrv, SMBStatus.SRVNonSpecificError);
        }

        // Extract the clients native operating system
        String clientOS = "";

        if (parser.hasMoreData()) {

            // Extract the callers operating system name
            clientOS = parser.unpackString(isUni);

            if (clientOS == null)
                throw new SMBSrvException(SMBStatus.NTInvalidParameter, SMBStatus.ErrSrv, SMBStatus.SRVNonSpecificError);
        }

        // DEBUG
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE)) {
            Debug.println("[SMB] NT Session setup from user=" + user + ", password="
                    + (uniPwd != null ? HexDump.hexString(uniPwd) : "none") + ", ANSIpwd="
                    + (ascPwd != null ? HexDump.hexString(ascPwd) : "none") + ", domain=" + domain + ", os=" + clientOS + ", VC="
                    + vcNum + ", maxBuf=" + maxBufSize + ", maxMpx=" + maxMpx + ", authCtx=" + sess.getAuthenticationContext());
            Debug.println("[SMB]  MID=" + parser.getMultiplexId() + ", UID=" + parser.getUserId() + ", PID="
                    + parser.getProcessId());
        }

        // Store the client maximum buffer size, maximum multiplexed requests count and client
        // capability flags
        sess.setClientMaximumBufferSize(maxBufSize != 0 ? maxBufSize : SMBSrvSession.DefaultBufferSize);
        sess.setClientMaximumMultiplex(maxMpx);
        sess.setClientCapabilities(capabs);

        // Create the client information and store in the session
        ClientInfo client = ClientInfo.getFactory().createInfo(user, uniPwd);
        client.setANSIPassword(ascPwd);
        client.setDomain(domain);
        client.setOperatingSystem(clientOS);

        if (sess.hasRemoteAddress())
            client.setClientAddress(sess.getRemoteAddress().getHostAddress());

        // Check if this is a null session logon
        if (user.length() == 0 && domain.length() == 0 && uniPwdLen == 0 && ascPwdLen == 1)
            client.setLogonType(ClientInfo.LogonType.Null);

        // Authenticate the user
        boolean isGuest = false;

        AuthStatus sts = authenticateUser(client, sess, PasswordAlgorithm.NTLM1);

        if (sts.intValue() > 0 && sts == AuthStatus.GUEST_LOGON) {

            // Guest logon
            isGuest = true;

            // DEBUG
            if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
                Debug.println("[SMB] User " + user + ", logged on as guest");
        }
        else if (sts != AuthStatus.AUTHENTICATED) {

            // DEBUG
            if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
                Debug.println("[SMB] User " + user + ", access denied");

            // Invalid user, reject the session setup request
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }
        else if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE)) {

            // DEBUG
            Debug.println("[SMB] User " + user + " logged on "
                    + (client != null ? " (type " + client.getLogonTypeString() + ")" : ""));
        }

        // Create a virtual circuit and allocate a UID to the new circuit
        VirtualCircuit vc = new VirtualCircuit(vcNum, client);
        int uid = sess.addVirtualCircuit(vc);

        if (uid == VirtualCircuit.InvalidID) {

            // DEBUG
            if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
                Debug.println("[SMB] Failed to allocate UID for virtual circuit, " + vc);

            // Failed to allocate a UID
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }
        else if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE)) {

            // DEBUG
            Debug.println("[SMB] Allocated UID=" + uid + " for VC=" + vc);
        }

        // Set the guest flag for the client, indicate that the session is logged on
        if (client.isNullSession() == false)
            client.setGuest(isGuest);
        sess.setLoggedOn(true);

        // Check for virtual circuit zero, disconnect any other sessions from this client
        if (vcNum == 0 && hasSessionCleanup()) {

            // Disconnect other sessions from this client, cleanup any open files/locks/oplocks
            int discCnt = sess.disconnectClientSessions();

            // DEBUG
            if (discCnt > 0 && Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
                Debug.println("[SMB] Disconnected " + discCnt + " existing sessions from client, sess=" + sess);
        }

        // Check if there is a chained commmand with the session setup request (usually a TreeConnect)
        SMBSrvPacket respPkt = reqPkt;

        if (parser.hasAndXCommand()) {

            try {

                // Allocate a new packet for the response
                respPkt = sess.getPacketPool().allocatePacket(reqPkt.getLength(), reqPkt);

                // Create a parser for the new response
                respPkt.setParser( SMBSrvPacket.Version.V1);
                parser = (SMBV1Parser) respPkt.getParser();
            }
            catch (NoPooledMemoryException ex) {

                // No memory, return a server error
                throw new SMBSrvException(SMBStatus.ErrSrv, SMBStatus.SRVNoBuffers);
            }
        }

        // Build the session setup response SMB
        parser.setParameterCount(3);
        parser.setParameter(0, 0); // No chained response
        parser.setParameter(1, 0); // Offset to chained response
        parser.setParameter(2, isGuest ? 1 : 0);
        parser.setByteCount(0);

        parser.setTreeId(0);
        parser.setUserId(uid);

        // Set the various flags
        int flags = parser.getFlags();
        flags &= ~SMBV1.FLG_CASELESS;
        parser.setFlags(flags);

        int flags2 = SMBV1.FLG2_LONGFILENAMES;
        if (isUni)
            flags2 += SMBV1.FLG2_UNICODE;

        if (hasExtendedSecurity() == false)
            flags2 &= ~SMBV1.FLG2_EXTENDEDSECURITY;

        parser.setFlags2(flags2);

        // Pack the OS, dialect and domain name strings.
        int pos = parser.getByteOffset();
        buf = parser.getBuffer();

        if (isUni)
            pos = DataPacker.wordAlign(pos);

        pos = DataPacker.putString("Java", buf, pos, true, isUni);
        pos = DataPacker.putString("Java File Server " + sess.getServer().isVersion(), buf, pos, true, isUni);
        pos = DataPacker.putString(sess.getSMBServer().getSMBConfiguration().getDomainName(), buf, pos, true, isUni);

        parser.setByteCount(pos - parser.getByteOffset());
        parser.setParameter(1, pos - RFCNetBIOSProtocol.HEADER_LEN);
    }

    @Override
    public AuthStatus processSecurityBlob(SMBSrvSession sess, ClientInfo client, SecurityBlob secBlob)
            throws SMBSrvException {
        return AuthStatus.UNSUPPORTED;
    }

    @Override
    public int getEncryptionKeyLength() {
        return STANDARD_CHALLENGE_LEN;
    }

    /* (non-Javadoc)
     * @see org.filesys.server.auth.ICifsAuthenticator#getServerCapabilities()
     */
    public int getServerCapabilities() {
        return Capability.V1Unicode + Capability.V1RemoteAPIs + Capability.V1NTSMBs + Capability.V1NTFind + Capability.V1NTStatus
                + Capability.V1LargeFiles + Capability.V1LargeRead + Capability.V1LargeWrite;
    }

    /**
     * Check if the guest account is enabled
     *
     * @return boolean
     */
    public final boolean allowGuest() {
        return m_allowGuest;
    }

    /**
     * Return the guest user name
     *
     * @return String
     */
    public final String getGuestUserName() {
        return m_guestUserName;
    }

    /**
     * Allow mapping of unknown users to the guest account
     *
     * @return boolean
     */
    public final boolean mapUnknownUserToGuest() {
        return m_mapToGuest;
    }

    /**
     * Enable/disable the guest account
     *
     * @param ena Enable the guest account if true, only allow defined user accounts access if false
     */
    public final void setAllowGuest(boolean ena) {
        m_allowGuest = ena;
    }

    /**
     * Set the guest user name
     *
     * @param guest String
     */
    public final void setGuestUserName(String guest) {
        m_guestUserName = guest;
    }

    /**
     * Enable/disable mapping of unknown users to the guest account
     *
     * @param ena Enable mapping of unknown users to the guest if true
     */
    public final void setMapToGuest(boolean ena) {
        m_mapToGuest = ena;
    }

    /**
     * Set the security mode flags
     *
     * @param flg int
     */
    protected final void setSecurityMode(int flg) {
        m_securityMode = flg;
    }

    /**
     * Set the extended security flag
     *
     * @param extSec boolean
     */
    protected final void setExtendedSecurity(boolean extSec) {
        m_extendedSecurity = extSec;
    }

    /**
     * Cleanup existing sessions from the same client address/name
     *
     * @return boolean
     */
    public final boolean hasSessionCleanup() {
        return m_sessCleanup;
    }

    /**
     * Enable/disable session cleanup when a new logon is received using virtual circuit zero
     *
     * @param ena boolean
     */
    public void setSessionCleanup(boolean ena) {
        m_sessCleanup = ena;
    }

    @Override
    public void closeAuthenticator() {

        // Override if cleanup required
    }

    /**
     * Validate a password by encrypting the plain text password using the specified encryption key
     * and encryption algorithm.
     *
     * @param user    UserAccount
     * @param client  ClientInfo
     * @param authCtx AuthContext
     * @param alg     PasswordAlgorithm
     * @return boolean
     */
    protected final boolean validatePassword(UserAccount user, ClientInfo client, AuthContext authCtx, PasswordAlgorithm alg) {

        // Get the challenge
        byte[] encryptKey = null;

        if (authCtx != null && authCtx instanceof NTLanManAuthContext) {

            // Get the NT/LanMan challenge
            NTLanManAuthContext ntlmCtx = (NTLanManAuthContext) authCtx;
            encryptKey = ntlmCtx.getChallenge();
        } else
            return false;

        // Get the encrypted password
        byte[] encryptedPwd = null;

        if (alg == PasswordAlgorithm.LANMAN)
            encryptedPwd = client.getANSIPassword();
        else
            encryptedPwd = client.getPassword();

        // Check if the user account has the MD4 password hash
        byte[] encPwd = null;

        if (user.hasMD4Password() && alg != PasswordAlgorithm.LANMAN) {

            try {

                // Generate the encrpyted password
                if (alg == PasswordAlgorithm.NTLM1) {

                    // Get the MD4 hashed password
                    byte[] p21 = new byte[21];
                    System.arraycopy(user.getMD4Password(), 0, p21, 0, user.getMD4Password().length);

                    // Generate an NTLMv1 encrypted password
                    encPwd = getEncryptor().doNTLM1Encryption(p21, encryptKey);
                }
                else if (alg == PasswordAlgorithm.NTLM2) {

                    // Generate an NTLMv2 encrypted password
                    encPwd = getEncryptor().doNTLM2Encryption(user.getMD4Password(), client.getUserName(), client.getDomain());
                }
            }
            catch (NoSuchAlgorithmException ex) {
            }
            catch (InvalidKeyException ex) {
            }
        } else {

            // Generate an encrypted version of the plain text password
            encPwd = generateEncryptedPassword(user.getPassword() != null ? user.getPassword() : "", encryptKey, alg, client
                    .getUserName(), client.getDomain());
        }

        // Compare the generated password with the received password
        if (encPwd != null && encryptedPwd != null && encPwd.length == STANDARD_PASSWORD_LEN
                && encryptedPwd.length == STANDARD_PASSWORD_LEN) {

            // Compare the password arrays
            for (int i = 0; i < STANDARD_PASSWORD_LEN; i++)
                if (encPwd[i] != encryptedPwd[i])
                    return false;

            // Password is valid
            return true;
        }

        // User or password is invalid
        return false;
    }

    /**
     * Convert the password string to a byte array
     *
     * @param pwd String
     * @return byte[]
     */
    protected final byte[] convertPassword(String pwd) {

        // Create a padded/truncated 14 character string
        StringBuffer p14str = new StringBuffer();
        p14str.append(pwd);
        if (p14str.length() > 14)
            p14str.setLength(14);
        else {
            while (p14str.length() < 14)
                p14str.append((char) 0x00);
        }

        // Convert the P14 string to an array of bytes. Allocate the return 16 byte array.
        return p14str.toString().getBytes();
    }

    /**
     * Return the password encryptor
     *
     * @return PasswordEncryptor
     */
    protected final PasswordEncryptor getEncryptor() {
        return m_encryptor;
    }

    /**
     * Set the access mode of the server.
     *
     * @param mode Either AuthMode.SHARE or AuthMode.USER
     */
    public final void setAccessMode(AuthMode mode) {
        m_accessMode = mode;
    }

    /**
     * Logon using the guest user account
     *
     * @param client ClientInfo
     * @param sess   SrvSession
     */
    protected void doGuestLogon(ClientInfo client, SrvSession sess) {

        // Set the home folder for the guest user
        client.setUserName(getGuestUserName());

        // Mark the client as being a guest logon
        client.setGuest(true);
    }

    /* (non-Javadoc)
     * @see org.filesys.server.auth.ICifsAuthenticator#getUserDetails(java.lang.String)
     */
    public final UserAccount getUserDetails(String user) {

        // Get the user account details via the users interface
        return getSecurityConfig().getUsersInterface().getUserAccount(user);
    }

    @Override
    public void setCurrentUser(ClientInfo client) {
    }

    /**
     * Map a client IP address to a domain
     *
     * @param clientIP InetAddress
     * @return String
     */
    protected final String mapClientAddressToDomain(InetAddress clientIP) {

        // Check if there are any domain mappings
        SecurityConfigSection securityConfig = getSecurityConfig();
        if (securityConfig.hasDomainMappings() == false)
            return null;

        // Convert the client IP address to an integer value
        int clientAddr = IPAddress.asInteger(clientIP);

        for (DomainMapping domainMap : securityConfig.getDomainMappings()) {

            if (domainMap.isMemberOfDomain(clientAddr)) {

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("Mapped client IP " + clientIP + " to domain " + domainMap.getDomain());

                return domainMap.getDomain();
            }
        }

        // DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("Failed to map client IP " + clientIP + " to a domain");

        // No domain mapping for the client address
        return null;
    }

    @Override
    public boolean usingSPNEGO() {

        // SPNEGO not supported
        return false;
    }

    @Override
    public byte[] getNegTokenInit() {

        // SPNEGO not supported
        return null;
    }

    @Override
    public String toString() {
        return getClass().getName() + ", mode=" + getAccessMode().name();
    }
}
