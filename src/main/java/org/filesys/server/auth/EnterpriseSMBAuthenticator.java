/*
 * Copyright (C) 2006-2012 Alfresco Software Limited.
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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.sasl.RealmCallback;

import org.filesys.debug.Debug;
import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.server.auth.kerberos.KerberosDetails;
import org.filesys.server.auth.kerberos.SessionSetupPrivilegedAction;
import org.filesys.server.auth.ntlm.*;
import org.filesys.server.auth.spnego.NegTokenInit;
import org.filesys.server.auth.spnego.NegTokenTarg;
import org.filesys.server.auth.spnego.OID;
import org.filesys.server.auth.spnego.SPNEGO;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.server.core.NoPooledMemoryException;
import org.filesys.smb.Capability;
import org.filesys.smb.SMBStatus;
import org.filesys.smb.server.*;
import org.filesys.util.DataBuffer;
import org.filesys.util.DataPacker;
import org.filesys.util.HexDump;
import org.springframework.extensions.config.ConfigElement;
import org.ietf.jgss.Oid;

/**
 * Enterprise SMB Authenticator Class
 *
 * <p>
 * SMB authenticator that supports NTLMSSP and Kerberos logins.
 *
 * @author gkspencer
 */
public class EnterpriseSMBAuthenticator extends SMBAuthenticator implements CallbackHandler {

    // Constants
    //
    // Default login configuration entry name
    private static final String LoginConfigEntry = "FileServerSMB";

    //	Line seperator used for exception stack traces
    private static final String LineSeperator = System.getProperty("line.separator");

    // NTLM flags mask, used to mask out features that are not supported
    private static final int NTLM_UNSUPPORTED_FLAGS = NTLM.Flag56Bit + NTLM.Flag128Bit + NTLM.FlagLanManKey + NTLM.FlagNegotiateNTLM +
                                                      NTLM.FlagNegotiateUnicode;

    // NTLM flags to be sent back to the client
    private static final int NTLM_SERVER_FLAGS = NTLM.FlagChallengeAccept + NTLM.FlagRequestTarget + NTLM.Flag128Bit + NTLM.FlagNegotiateOEM +
                                                 NTLM.FlagNegotiateUnicode + NTLM.FlagKeyExchange + NTLM.FlagTargetInfo + NTLM.FlagRequestVersion +
                                                 NTLM.FlagAlwaysSign + NTLM.FlagNegotiateSign + NTLM.FlagNegotiateExtSecurity + NTLM.FlagNegotiateSeal;

    // MIC token
    private static final int MIC_TOKEN_LENGTH   = 16;

    private static final int MIC_TOKEN_VERSION  = 0;
    private static final int MIC_TOKEN_DIGEST   = 4;
    private static final int MIC_TOKEN_SEQNO    = 12;

    private static final int MIC_TOKEN_VER_NTLMSSP  = 0x00000001;

    // Use NTLMSSP or SPNEGO
    protected boolean m_useRawNTLMSSP;

    // Accept NTLM logons
    protected boolean m_allowNTLM = true;

    // Flag to control whether NTLMv1 is accepted
    protected boolean m_acceptNTLMv1;

    // Kerberos settings
    //
    // Account name and password for server ticket
    //
    // The account name must be built from the SMB server name, in the format :-
    //
    // cifs/<server_name>@<realm>
    protected String m_accountName;
    protected String m_password;

    // Kerberos realm and KDC address
    protected String m_krbRealm;

    // Login configuration entry name
    protected String m_loginEntryName = LoginConfigEntry;

    // Server login context
    protected LoginContext m_loginContext;

    // SPNEGO NegTokenInit blob, sent to the client in the SMB negotiate response
    protected byte[] m_negTokenInit;

    /**
     * Class constructor
     */
    public EnterpriseSMBAuthenticator() {
        setExtendedSecurity(true);
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

        // Call the base initialization
        super.initialize(config, params);

        // Check if session cleanup should be disabled, when a session setup request is received
        // on virtual circuit zero
        if (params.getChild("disableSessionCleanup") != null) {

            // Disable session cleanup
            setSessionCleanup(false);

            // Debug
            if (hasDebugOutput())
                debugOutput("[SMB] Disabled session cleanup (for virtual circuit zero logons)");
        }

        // Check if Java API Kerberos debug output should be enabled
        if (params.getChild("kerberosDebug") != null) {

            // Enable Kerberos API debug output
            System.setProperty("sun.security.jgss.debug", "true");
            System.setProperty("sun.security.krb5.debug", "true");

            System.setProperty("com.ibm.security.jgss.debug", "all");
        }

        // Access the SMB server configuration
        SMBConfigSection cifsConfig = (SMBConfigSection) config.getConfigSection(SMBConfigSection.SectionName);

        // Check if Kerberos is enabled, get the Kerberos realm
        ConfigElement krbRealm = params.getChild("Realm");

        if (krbRealm != null && krbRealm.getValue() != null && krbRealm.getValue().length() > 0) {

            // Set the Kerberos realm
            m_krbRealm = krbRealm.getValue();

            // Get the SMB service account password
            ConfigElement srvPassword = params.getChild("Password");
            if (srvPassword != null && srvPassword.getValue() != null && srvPassword.getValue().length() > 0) {

                // Set the SMB service account password
                m_password = srvPassword.getValue();
            }

            // Check if a custom Kerberos configuration file has been specified
            ConfigElement krb5ConfPath = params.getChild("KerberosConfig");

            if ( krb5ConfPath != null) {

                // Get the Kerberos configuration path
                String krb5Path = krb5ConfPath.getValue();

                // Make sure the Kerberos configuration file exists
                if (Files.exists( Paths.get( krb5Path), LinkOption.NOFOLLOW_LINKS)) {

                    // Set the Kerberos configuration path
                    System.setProperty( "java.security.krb5.conf", krb5Path);
                }
                else {

                    // Configuration file does not exist
                    throw new InvalidConfigurationException("Kerberos configuration file does not exist - " + krb5Path);
                }
            }

            // Check if a custom login configuration file has been specified
            ConfigElement loginConfPath = params.getChild("LoginConfig");

            if ( loginConfPath != null) {

                // Get the login configuration path
                String loginPath = loginConfPath.getValue();

                // Make sure the login configuration file exists
                if (Files.exists( Paths.get( loginPath), LinkOption.NOFOLLOW_LINKS)) {

                    // Set the login configuration path
                    System.setProperty( "java.security.auth.login.config", loginPath);
                }
                else {

                    // Configuration file does not exist
                    throw new InvalidConfigurationException("Login configuration file does not exist - " + loginPath);
                }
            }

            // Get the login configuration entry name
            ConfigElement loginEntry = params.getChild("LoginEntry");

            if (loginEntry != null) {
                if (loginEntry.getValue() != null && loginEntry.getValue().length() > 0) {

                    // Set the login configuration entry name to use
                    m_loginEntryName = loginEntry.getValue();
                }
                else
                    throw new InvalidConfigurationException("Invalid login entry specified");
            }

            // Create a login context for the SMB server service
            try {

                // Login the SMB server service
                m_loginContext = new LoginContext(m_loginEntryName, this);
                m_loginContext.login();
            }
            catch (LoginException ex) {

                // Debug
                if (hasDebugOutput())
                    debugOutput("[SMB] Kerberos authenticator error - " + ex.getMessage());

                throw new InvalidConfigurationException("Failed to login SMB server service");
            }

            // Get the SMB service account name from the subject
            Subject subj = m_loginContext.getSubject();
            Principal princ = subj.getPrincipals().iterator().next();

            m_accountName = princ.getName();

            if (hasDebugOutput())
                debugOutput("[SMB] Logged on using principal " + m_accountName);

            // DEBUG
            if (hasDebugOutput()) {
                debugOutput("[SMB] Enabling mechTypes :-");
                debugOutput("       Kerberos5");
                debugOutput("       MS-Kerberos5");
            }

            // Create the Oid list for the SPNEGO NegTokenInit, include NTLMSSP for fallback
            List<Oid> mechTypes = new ArrayList<Oid>();

            mechTypes.add(OID.MSKERBEROS5);
            mechTypes.add(OID.KERBEROS5);

            if (params.getChild("disableNTLM") == null) {
                mechTypes.add(OID.NTLMSSP);

                // DEBUG
                if (Debug.EnableDbg && hasDebug())
                    debugOutput("       NTLMSSP");

                // Set the NTLM logons allowed flag
                m_allowNTLM = true;
            }
            else {

                // NTLM logons not allowed
                m_allowNTLM = false;
            }

            // Build the SPNEGO NegTokenInit blob
            try {

                // Build the mechListMIC principle
                //
                // Note: This field is not as specified, only seems to be used by Samba clients (Linux/Mac/Unix)
                String mecListMIC = null;

                StringBuilder mic = new StringBuilder();

                mic.append("cifs/");
                mic.append(cifsConfig.getServerName().toLowerCase());
                mic.append("@");
                mic.append(m_krbRealm);

                mecListMIC = mic.toString();

                // Build the SPNEGO NegTokenInit that contains the authentication types that the
                // SMB server accepts
                NegTokenInit negTokenInit = new NegTokenInit(mechTypes, mecListMIC);

                // Encode the NegTokenInit blob
                m_negTokenInit = negTokenInit.encode();
            }
            catch (IOException ex) {

                // Debug
                if (hasDebugOutput())
                    debugOutput("[SMB] Error creating SPNEGO NegTokenInit blob - " + ex.getMessage());

                throw new InvalidConfigurationException("Failed to create SPNEGO NegTokenInit blob");
            }

            // Indicate that SPNEGO security blobs are being used
            m_useRawNTLMSSP = false;
        }
        else {

            // Check if raw NTLMSSP or SPNEGO/NTLMSSP should be used
            ConfigElement useSpnego = params.getChild("useSPNEGO");

            if (useSpnego != null) {

                // Create the Oid list for the SPNEGO NegTokenInit
                List<Oid> mechTypes = new ArrayList<Oid>();

                mechTypes.add(OID.NTLMSSP);

                // Build the SPNEGO NegTokenInit blob
                try {

                    // Build the SPNEGO NegTokenInit that contains the authentication types that the
                    // SMB server accepts
                    NegTokenInit negTokenInit = new NegTokenInit(mechTypes, null);

                    // Encode the NegTokenInit blob
                    m_negTokenInit = negTokenInit.encode();
                }
                catch (IOException ex) {

                    // Debug
                    if (hasDebugOutput())
                        debugOutput("[SMB] Error creating SPNEGO NegTokenInit blob - " + ex.getMessage());

                    throw new InvalidConfigurationException("Failed to create SPNEGO NegTokenInit blob");
                }

                // Indicate that SPNEGO security blobs are being used
                m_useRawNTLMSSP = false;
            }
            else {

                // Use raw NTLMSSP security blobs
                m_useRawNTLMSSP = true;
            }
        }

        // Check if NTLM logons are disabled
        if (params.getChild("disableNTLM") != null) {

            // Indicate NTLM logons are not allowed
            m_allowNTLM = false;

            // Debug
            if (hasDebugOutput())
                debugOutput("[SMB] NTLM logons disabled");
        }

        // Check if NTLMv1 logons are accepted
        ConfigElement disallowNTLMv1 = params.getChild("disallowNTLMv1");

        m_acceptNTLMv1 = disallowNTLMv1 != null ? false : true;

        // Make sure either NTLMSSP or SPNEGO authentication is enabled
        if ( allowNTLMLogon() == false && m_loginContext == null) {

            // Debug
            if (hasDebugOutput())
                debugOutput("[SMB] No authentication methods enabled");

            throw new InvalidConfigurationException("No authentication methods enabled, require NTLMSSP or SPNEGO");
        }
    }

    @Override
    public boolean usingSPNEGO() {
        return m_useRawNTLMSSP ? false : true;
    }

    @Override
    public byte[] getNegTokenInit() {
        return m_negTokenInit;
    }

    /**
     * Determine if NTLM logons are allowed
     *
     * @return boolean
     */
    private final boolean allowNTLMLogon() {
        return m_allowNTLM;
    }

    /**
     * Determine if NTLMv1 logons are accepted
     *
     * @return boolean
     */
    private final boolean acceptNTLMv1Logon() {
        return m_acceptNTLMv1;
    }

    /**
     * JAAS callback handler
     *
     * @param callbacks Callback[]
     * @exception IOException I/O error
     * @exception UnsupportedCallbackException Unsupported callback error
     */
    public void handle(Callback[] callbacks)
            throws IOException, UnsupportedCallbackException {

        // Process the callback list
        for (int i = 0; i < callbacks.length; i++) {

            // Request for user name
            if (callbacks[i] instanceof NameCallback) {
                NameCallback cb = (NameCallback) callbacks[i];
                cb.setName(m_accountName);
            }

            // Request for password
            else if (callbacks[i] instanceof PasswordCallback) {
                PasswordCallback cb = (PasswordCallback) callbacks[i];
                if ( m_password != null)
                    cb.setPassword(m_password.toCharArray());
                else
                    cb.setPassword("".toCharArray());
            }

            // Request for realm
            else if (callbacks[i] instanceof RealmCallback) {
                RealmCallback cb = (RealmCallback) callbacks[i];
                cb.setText(m_krbRealm);
            } else {
                throw new UnsupportedCallbackException(callbacks[i]);
            }
        }
    }

    @Override
    public int getEncryptionKeyLength() {
        return 8;
    }

    @Override
    public int getServerCapabilities() {
        return Capability.V1Unicode + Capability.V1RemoteAPIs + Capability.V1NTSMBs + Capability.V1NTFind + Capability.V1NTStatus
                + Capability.V1LargeFiles + Capability.V1LargeRead + Capability.V1LargeWrite + Capability.V1ExtendedSecurity
                + Capability.V1InfoPassthru + Capability.V1Level2Oplocks;
    }

    @Override
    public void processSessionSetup(SMBSrvSession sess, SMBSrvPacket reqPkt)
            throws SMBSrvException {

        // Get the associated parser
        SMBV1Parser parser = (SMBV1Parser) reqPkt.getParser();

        // Check that the received packet looks like a valid NT session setup andX request
        if (parser.checkPacketIsValid(12, 0) == false)
            throw new SMBSrvException(SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);

        // Check if the request is using security blobs or the older hashed password format
        if (parser.getParameterCount() == 13) {

            try {

                // Process the hashed password session setup
                doHashedPasswordLogon(sess, reqPkt);
                return;
            }
            catch (SMBSrvException ex) {

                // Rethrow the exception
                throw ex;
            }
        }

        // Extract the session details
        int maxBufSize = parser.getParameter(2);
        int maxMpx = parser.getParameter(3);
        int vcNum = parser.getParameter(4);
        int secBlobLen = parser.getParameter(7);
        int capabs = parser.getParameterLong(10);

        // Extract the client details from the session setup request
        int dataPos = parser.getByteOffset();
        byte[] buf = parser.getBuffer();

        // Determine if ASCII or unicode strings are being used
        boolean isUni = parser.isUnicode();

        // Make a note of the security blob position
        int secBlobPos = dataPos;

        // Extract the clients primary domain name string
        dataPos += secBlobLen;
        parser.setPosition(dataPos);

        String domain = "";

        if (parser.hasMoreData()) {

            // Extract the callers domain name
            domain = parser.unpackString(isUni);

            if (domain == null)
                throw new SMBSrvException(SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
        }

        // Extract the clients native operating system
        String clientOS = "";

        if (parser.hasMoreData()) {

            // Extract the callers operating system name
            clientOS = parser.unpackString(isUni);

            if (clientOS == null)
                throw new SMBSrvException(SMBStatus.NTInvalidParameter, SMBStatus.SRVNonSpecificError, SMBStatus.ErrSrv);
        }

        // DEBUG
        if (hasDebugOutput())
            debugOutput("[SMB] NT Session setup " + (usingSPNEGO() ? "SPNEGO" : "NTLMSSP") + ", MID="
                    + parser.getMultiplexId() + ", UID=" + parser.getUserId() + ", PID=" + parser.getProcessId());

        // Store the client maximum buffer size, maximum multiplexed requests count and client
        // capability flags
        sess.setClientMaximumBufferSize(maxBufSize != 0 ? maxBufSize : SMBSrvSession.DefaultBufferSize);
        sess.setClientMaximumMultiplex(maxMpx);
        sess.setClientCapabilities(capabs);

        // Create the client information and store in the session
        ClientInfo client = ClientInfo.createInfo(null, null);
        client.setDomain(domain);
        client.setOperatingSystem(clientOS);

        client.setLogonType(ClientInfo.LogonType.Normal);

        // Set the remote address, if available
        if (sess.hasRemoteAddress())
            client.setClientAddress(sess.getRemoteAddress().getHostAddress());

        // Set the process id for this client, for multi-stage logons
        client.setProcessId(parser.getProcessId());

        // Get the current session setup object, or null
        Object setupObj = sess.getSetupObject(client.getProcessId(), SetupObjectType.Type2Message);

        // Process the security blob
        boolean isNTLMSSP = false;
        SecurityBlob secBlob = null;
        AuthStatus authSts = AuthStatus.DISALLOW;

        try {

            // Check if the blob has the NTLMSSP signature
            if (secBlobLen >= NTLM.Signature.length) {

                // Check for the NTLMSSP signature
                int idx = 0;
                while (idx < NTLM.Signature.length && buf[secBlobPos + idx] == NTLM.Signature[idx])
                    idx++;

                if (idx == NTLM.Signature.length)
                    isNTLMSSP = true;
            }

            // Create the security blob
            secBlob = new SecurityBlob(isNTLMSSP ? SecurityBlob.SecurityBlobType.NTLMSSP : SecurityBlob.SecurityBlobType.SPNEGO,
                    buf, secBlobPos, secBlobLen, isUni);

            // Process the security blob
            authSts = processSecurityBlob(sess, client, secBlob);
        }
        catch (SMBSrvException ex) {

            // Remove the session setup object for this logon attempt
            sess.removeAllSetupObjects(client.getProcessId());

            // Rethrow the exception
            throw ex;
        }

        // Check if the authentication was successful
        if ( authSts == AuthStatus.DISALLOW)
            throw new SMBSrvException( SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE)) {
            if (secBlob.hasResponseBlob() == false)
                debugOutput("[SMB] User " + client.getUserName() + " logged on "
                        + (client != null ? " (type " + client.getLogonTypeString() + ")" : ""));
            else
                debugOutput("[SMB] Two stage logon (" + (isNTLMSSP ? "NTLMSSP" : "SPNEGO") + ")");
        }

        // Get the response blob length, it can be null
        int respLen = secBlob.hasResponseBlob() ? secBlob.getResponseLength() : 0;

        // Use the original packet for the response
        SMBSrvPacket respPkt = reqPkt;

        // Check if there is/was a session setup object stored in the session, this indicates a
        // multi-stage session setup so set the status code accordingly
        boolean loggedOn = false;

        if (secBlob.hasResponseBlob() || sess.hasSetupObject(client.getProcessId(), SetupObjectType.Type2Message) || setupObj != null) {

            // NTLMSSP has two stages, if there is a stored setup object then indicate more processing required
            if (sess.hasSetupObject(client.getProcessId(), SetupObjectType.Type2Message))
                parser.setLongErrorCode(SMBStatus.NTMoreProcessingRequired);
            else {

                // Indicate a successful logon, the method can still throw an exception to stop the logon
                onSuccessfulLogon( client);

                // Indicate session setup success
                parser.setLongErrorCode(SMBStatus.NTSuccess);

                // Indicate that the user is logged on
                loggedOn = true;
            }

            // Set the parameter count then check if the security blob will fit into the current packet buffer
            parser.setParameterCount(4);
            int reqLen = respLen + 100; // allow for strings

            if (reqLen > parser.getAvailableLength()) {

                try {

                    // Allocate a new buffer for the response
                    respPkt = sess.getPacketPool().allocatePacket(parser.getByteOffset() + reqLen, reqPkt);

                    // Create a parser for the new response
                    respPkt.setParser( SMBSrvPacket.Version.V1);
                    parser = (SMBV1Parser) respPkt.getParser();
                }
                catch (NoPooledMemoryException ex) {

                    // DEBUG
                    if (Debug.EnableDbg && hasDebug())
                        debugOutput("Authenticator failed to allocate packet from pool, reqSiz="
                                + (parser.getByteOffset() + respLen));

                    // Return a server error to the client
                    throw new SMBSrvException(SMBStatus.NTInvalidParameter, SMBStatus.SRVNoBuffers, SMBStatus.ErrSrv);
                }
            }

            // Fill in the rest of the packet header
            parser.setParameter(0, 0xFF); // No chained response
            parser.setParameter(1, 0); // Offset to chained response

            parser.setParameter(2, 0); // Action
            parser.setParameter(3, respLen);
        }
        else {

            // Indicate a successful logon, the method can still throw an exception to stop the logon
            onSuccessfulLogon( client);

            // Build a completed session setup response
            parser.setLongErrorCode(SMBStatus.NTSuccess);

            // Build the session setup response SMB
            parser.setParameterCount(12);
            parser.setParameter(0, 0xFF); // No chained response
            parser.setParameter(1, 0); // Offset to chained response

            parser.setParameter(2, SMBSrvSession.DefaultBufferSize);
            parser.setParameter(3, SMBSrvSession.NTMaxMultiplexed);
            parser.setParameter(4, vcNum); // virtual circuit number
            parser.setParameterLong(5, 0); // session key
            parser.setParameter(7, respLen);    // security blob length
            parser.setParameterLong(8, 0); // reserved
            parser.setParameterLong(10, getServerCapabilities());

            // Indicate that the user is logged on
            loggedOn = true;
        }

        // If the user is logged on then allocate a virtual circuit
        int uid = 0xFFFF;

        if (loggedOn == true) {

            // Check for virtual circuit zero, disconnect any other sessions from this client
            if (vcNum == 0 && hasSessionCleanup()) {

                // Disconnect other sessions from this client, cleanup any open files/locks/oplocks
                int discCnt = sess.disconnectClientSessions();

                // DEBUG
                if (discCnt > 0 && Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
                    debugOutput("[SMB] Disconnected " + discCnt + " existing sessions from client, sess=" + sess);
            }

            // Clear any stored session setup object for the logon
            sess.removeAllSetupObjects(client.getProcessId());

            // Create a virtual circuit for the new logon
            VirtualCircuit vc = new VirtualCircuit(vcNum, client);
            uid = sess.addVirtualCircuit(vc);

            if (uid == VirtualCircuit.InvalidID) {

                // DEBUG
                if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
                    debugOutput("[SMB] Failed to allocate UID for virtual circuit, " + vc);

                // Failed to allocate a UID
                throw new SMBSrvException(SMBStatus.NTTooManySessions, SMBStatus.SRVTooManyUIDs, SMBStatus.ErrSrv);
            }
            else if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE)) {

                // DEBUG
                debugOutput("[SMB] Allocated UID=" + uid + " for VC=" + vc);
            }
        }

        // Common session setup response
        parser.setCommand(parser.getCommand());
        parser.setByteCount(0);

        parser.setTreeId(loggedOn ? 0 : 0xFFFF);
        parser.setUserId(uid);

        // Set the various flags
        int flags = parser.getFlags();
        flags &= ~SMBV1.FLG_CASELESS;
        parser.setFlags(flags);

        parser.setResponse();

        int flags2 = SMBV1.FLG2_LONGFILENAMES + SMBV1.FLG2_EXTENDEDSECURITY + SMBV1.FLG2_LONGERRORCODE;
        if (isUni)
            flags2 += SMBV1.FLG2_UNICODE;
        parser.setFlags2(flags2);

        // Pack the security blob
        int pos = parser.getByteOffset();
        buf = parser.getBuffer();

        if (secBlob.hasResponseBlob()) {
            System.arraycopy(secBlob.getResponseBlob(), 0, buf, pos, secBlob.getResponseLength());
            pos += secBlob.getResponseLength();
        }

        // Pack the OS, dialect and domain name strings
        if (isUni)
            pos = DataPacker.wordAlign(pos);

        pos = DataPacker.putString("Java", buf, pos, true, isUni);
        pos = DataPacker.putString("Java File Server " + sess.getServer().isVersion(), buf, pos, true, isUni);

        if (secBlob.hasResponseBlob() == false)
            pos = DataPacker.putString(sess.getSMBServer().getSMBConfiguration().getDomainName(), buf, pos, true, isUni);

        parser.setByteCount(pos - parser.getByteOffset());
        parser.setParameter(1, pos - RFCNetBIOSProtocol.HEADER_LEN);
    }

    @Override
    public AuthStatus processSecurityBlob(SMBSrvSession sess, ClientInfo client, SecurityBlob secBlob)
            throws SMBSrvException {

        // Check if this is a transactional authenticator
        AuthStatus authSts = AuthStatus.DISALLOW;

        if ( this instanceof TransactionalSMBAuthenticator) {

            // Wrap the authentication processing in a transaction
            TransactionalSMBAuthenticator transAuth = (TransactionalSMBAuthenticator) this;
            authSts = transAuth.processSecurityBlobInTransaction(sess, client, secBlob);
        }
        else {

            // Call the security blob processing directly
            authSts = processSecurityBlobInternal(sess, client, secBlob);
        }

        // Return the authentication status
        return authSts;
    }

    /**
     * Process the security blob, non-transactional, can be wrapped by a transactional call
     *
     * @param sess SMBSrvSession
     * @param client ClientInfo
     * @param secBlob SecurityBlob
     * @return AuthStatus
     * @exception SMBSrvException SMB error
     */
    public final AuthStatus processSecurityBlobInternal(SMBSrvSession sess, ClientInfo client, SecurityBlob secBlob)
            throws SMBSrvException {

        // Get the current session setup object, or null
        Object setupObj = sess.getSetupObject(client.getProcessId(), SetupObjectType.Type2Message);

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
            debugOutput("[SMB] Process security blob=" + secBlob);

        // Process the security blob
        AuthStatus authSts = AuthStatus.DISALLOW;

        try {

            // Process the security blob
            if (secBlob.isNTLMSSP()) {

                // Process an NTLMSSP security blob
                authSts = doNtlmsspSessionSetup(sess, client, secBlob, false);
            } else {

                // Process an SPNEGO security blob
                authSts = doSpnegoSessionSetup(sess, client, secBlob);
            }
        }
        catch (SMBSrvException ex) {

            // Remove the session setup object for this logon attempt
            sess.removeAllSetupObjects(client.getProcessId());

            // Rethrow the exception
            throw ex;
        }

        // Debug
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE)) {
            if (authSts == AuthStatus.AUTHENTICATED)
                debugOutput("[SMB] User " + client.getUserName() + " logged on "
                        + (client != null ? " (type " + client.getLogonTypeString() + ")" : ""));
            else
                debugOutput("[SMB] Two stage logon (" + secBlob.isType().name() + ")");
        }

        // Get the response blob length, it can be null
        int respLen = secBlob.hasResponseBlob() ? secBlob.getResponseLength() : 0;

        // Check if the user has been authenticated, or there are more round trips to complete the authentication
        if ( authSts == AuthStatus.AUTHENTICATED) {

            // Clear any stored session setup object for the logon
            sess.removeAllSetupObjects(client.getProcessId());

            // Indicate a successful logon, the method can throw an exception to stop the logon
            onSuccessfulLogon( client);
        }

        // Return the authentication status
        return authSts;
    }

    /**
     * Process an NTLMSSP security blob
     *
     * @param sess    SMBSrvSession
     * @param client  ClientInfo
     * @param secBlob SecurityBlob
     * @param spnego boolean
     * @return AuthStatus
     * @exception SMBSrvException SMB error
     */
    private final AuthStatus doNtlmsspSessionSetup(SMBSrvSession sess, ClientInfo client, SecurityBlob secBlob, boolean spnego)
            throws SMBSrvException {

        // Make sure NTLM logons are enabled
        if ( allowNTLMLogon() == false) {

            // Client has sent an NTLM logon
            if (hasDebugOutput())
                debugOutput("[SMB] NTLM disabled, received NTLM logon from client (NTLMSSP)");

            // Return a logon failure status
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }

        // Determine the NTLMSSP message type
        AuthStatus authSts = AuthStatus.DISALLOW;
        NTLMMessage.Type msgType = NTLMMessage.isNTLMType(secBlob.getSecurityBlob(), secBlob.getSecurityOffset());
        byte[] respBlob = null;

        if (msgType == NTLMMessage.Type.Invalid) {

            // DEBUG
            if (hasDebugOutput()) {
                debugOutput("[SMB] Invalid NTLMSSP token received");
                debugOutput("[SMB]   Token=" + HexDump.hexString(secBlob.getSecurityBlob(), secBlob.getSecurityOffset(), secBlob.getSecurityLength(), " "));
            }

            // Return a logon failure status
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }

        // Check for a type 1 NTLMSSP message
        else if (msgType == NTLMMessage.Type.Negotiate) {

            // Create the type 1 NTLM message from the token
            Type1NTLMMessage type1Msg = new Type1NTLMMessage(secBlob.getSecurityBlob(), secBlob.getSecurityOffset(), secBlob.getSecurityLength());

            // DEBUG
            if ( hasDebugOutput())
                debugOutput("[SMB] Received NTLM Type1/Negotiate - " + type1Msg.toString());

            // Check if signing is required or the NTLMSSP security blob is wrapped by an SPNEGO message,
            // if so then we need to keep a copy of the Type 1 message
            if ( spnego || type1Msg.hasFlag( NTLM.FlagNegotiateSign) || type1Msg.hasFlag( NTLM.FlagAlwaysSign)) {

                // Clone the Type 1 message and save on the session
                type1Msg = type1Msg.clone();
                sess.setSetupObject( client.getProcessId(), type1Msg, SetupObjectType.Type1Message);

                // Debug
                if (hasDebugOutput())
                    debugOutput("[SMB] Signing required/SPNEGO, saved Type1");
            }

            // Build the type 2 NTLM response message
            //
            // Get the flags from the client request and mask out unsupported features
            int ntlmFlags = type1Msg.getFlags() & NTLM_UNSUPPORTED_FLAGS;

            // Generate a challenge for the response
            NTLanManAuthContext ntlmCtx = new NTLanManAuthContext();

            // Build a type2 message to send back to the client, containing the challenge
            String domain = sess.getSMBServer().getServerName();

            List<TargetInfo> tList = new ArrayList<TargetInfo>();

            tList.add(new StringTargetInfo(TargetInfo.Type.DOMAIN, domain));
            tList.add(new StringTargetInfo(TargetInfo.Type.SERVER, sess.getServerName()));
            tList.add(new StringTargetInfo(TargetInfo.Type.DNS_DOMAIN, domain.toLowerCase()));
            tList.add(new StringTargetInfo(TargetInfo.Type.FULL_DNS, domain.toLowerCase()));

            tList.add(new TimestampTargetInfo());

            ntlmFlags = NTLM_SERVER_FLAGS;

            if (acceptNTLMv1Logon())
                ntlmFlags += NTLM.Flag56Bit;

            Type2NTLMMessage type2Msg = new Type2NTLMMessage();

            type2Msg.buildType2(ntlmFlags, domain, ntlmCtx.getChallenge(), null, tList);

            // Store the type 2 message in the session until the session setup is complete
            sess.setSetupObject(client.getProcessId(), type2Msg, SetupObjectType.Type2Message);

            // Set the response blob using the type 2 message
            respBlob = type2Msg.getBytes();
            secBlob.setResponseBlob( respBlob);

            authSts = AuthStatus.MORE_PROCESSING;
        }
        else if (msgType == NTLMMessage.Type.Authenticate) {

            // Create the type 3 NTLM message from the token
            Type3NTLMMessage type3Msg = new Type3NTLMMessage(secBlob.getSecurityBlob(), secBlob.getSecurityOffset(), secBlob.getSecurityLength(),
                                                             secBlob.isUnicode());

            // DEBUG
            if ( hasDebugOutput())
                debugOutput("[SMB] Received NTLM Type3/Authenticate - " + type3Msg.toString());

            // Make sure a type 2 message was stored in the first stage of the session setup
            if (sess.hasSetupObject(client.getProcessId(), SetupObjectType.Type2Message) == false) {

                // Clear the setup object
                sess.removeAllSetupObjects(client.getProcessId());

                // Return a logon failure
                throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
            }

            // Check if signing is required or the NTLMSSP security blob is wrapped by an SPNEGO message,
            // if so then we need to keep a copy of the Type 3 message
            if (spnego || type3Msg.hasFlag( NTLM.FlagNegotiateSign) || type3Msg.hasFlag( NTLM.FlagAlwaysSign)) {

                // Clone the Type 3 message and save on the session
                type3Msg = type3Msg.clone();
                sess.setSetupObject( client.getProcessId(), type3Msg, SetupObjectType.Type3Message);

                // Debug
                if (hasDebugOutput())
                    debugOutput("[SMB] Signing required/SPNEGO, saved Type3");
            }

            // Determine if the client sent us NTLMv1 or NTLMv2
            if (type3Msg.hasFlag(NTLM.Flag128Bit)) { // && type3Msg.hasFlag(NTLM.FlagNTLM2Key)) {

                // Determine if the client sent us an NTLMv2 blob or an NTLMv2 session key
                if (type3Msg.getNTLMHashLength() > 24) {

                    // Looks like an NTLMv2 blob
                    doNTLMv2Logon(sess, client, type3Msg);

                    // Debug
                    if (hasDebugOutput())
                        debugOutput("[SMB] Logged on using NTLMSSP/NTLMv2");
                }
                else {

                    // Looks like an NTLMv2 session key
                    doNTLMv2SessionKeyLogon(sess, client, type3Msg);

                    // Debug
                    if (hasDebugOutput())
                        debugOutput("[SMB] Logged on using NTLMSSP/NTLMv2SessKey");
                }
            }
            else {

                // Looks like an NTLMv1 blob
                doNTLMv1Logon(sess, client, type3Msg);

                // Debug
                if (hasDebugOutput())
                    debugOutput("[SMB] Logged on using NTLMSSP/NTLMv1");
            }

            // Indicate user authenticated
            authSts = AuthStatus.AUTHENTICATED;
        }

        // Return the authentication status
        return authSts;
    }

    /**
     * Process an SPNEGO security blob
     *
     * @param sess    SMBSrvSession
     * @param client  ClientInfo
     * @param secBlob SecurityBlob
     * @return AuthStatus
     * @exception SMBSrvException SMB error
     */
    private final AuthStatus doSpnegoSessionSetup(SMBSrvSession sess, ClientInfo client, SecurityBlob secBlob)
            throws SMBSrvException {

        // Check the received token type, if it is a target token and there is a stored session setup object, this is
        // the second stage of an NTLMSSP session setup that is wrapped with SPNEGO
        AuthStatus authSts = AuthStatus.DISALLOW;
        int tokType = -1;

        try {
            tokType = SPNEGO.checkTokenType(secBlob);
        }
        catch (IOException ex) {
        }

        // Check for the second stage of an NTLMSSP logon
        NegTokenTarg negTarg = null;

        if (tokType == SPNEGO.NegTokenTarg && sess.hasSetupObject(client.getProcessId(), SetupObjectType.Type2Message)) {

            // Get the NTLMSSP blob from the NegTokenTarg blob
            NegTokenTarg negToken = new NegTokenTarg();

            try {

                // Decode the security blob
                negToken.decode(secBlob);
            }
            catch (IOException ex) {

                // Log the error
                if (Debug.EnableError && hasDebug())
                    debugOutput(ex);

                // Return a logon failure status
                throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
            }

            // Get the second stage NTLMSSP blob
            byte[] ntlmsspBlob = negToken.getResponseToken();

            // Create a seperate security blob for the NTLMSSP blob
            SecurityBlob ntlmBlob = new SecurityBlob(SecurityBlob.SecurityBlobType.NTLMSSP, ntlmsspBlob, secBlob.isUnicode());

            // Perform an NTLMSSP session setup
            authSts = doNtlmsspSessionSetup(sess, client, ntlmBlob, true);

            // NTLMSSP is a two stage process, set the SPNEGO status
            SPNEGO.Result spnegoSts = SPNEGO.Result.AcceptCompleted;
            byte[] mechListMIC = null;

            if  ( authSts == AuthStatus.MORE_PROCESSING) {

                // Require another round trip
                spnegoSts = SPNEGO.Result.AcceptIncomplete;
            }
            else {

                // Check if the response needs a message integrity code
                if ( negToken.hasMechListMIC() && sess.hasSessionKey( KeyType.SessionKey) &&
                        sess.hasSetupObject( client.getProcessId(), SetupObjectType.NegTokenInit)) {

                    // Verify the received MIC token
                    NegTokenInit negInit = (NegTokenInit) sess.getSetupObject(client.getProcessId(), SetupObjectType.NegTokenInit);

                    if ( negInit.hasMechListBytes()) {
                        byte[] mechList = negInit.getMechListBytes();

                        if (verifyMIC(sess, mechList, 0, mechList.length, negToken.getMechListMIC(), client.getProcessId()) == false) {

                            // Return a logon failure status
                            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
                        }
                    }

                    // Get the original NegTokenInit that started the authentication
                    NegTokenInit initToken = (NegTokenInit) sess.getSetupObject( client.getProcessId(), SetupObjectType.NegTokenInit);

                    if ( initToken.hasMechListBytes()) {

                        // Generate the MIC token for the response
                        byte[] initTokenByts = initToken.getMechListBytes();
                        mechListMIC = generateMIC(sess, initTokenByts, 0, initTokenByts.length, 0);
                    }

                    // No longer need the NTLM messages
                    sess.removeAllSetupObjects( client.getProcessId());
                }
            }

            // Package the NTLMSSP response in an SPNEGO response
            negTarg = new NegTokenTarg(spnegoSts, null, ntlmBlob.getResponseBlob());
            negTarg.setMechListMIC( mechListMIC);
        }
        else if (tokType == SPNEGO.NegTokenInit) {

            // Parse the SPNEGO security blob to get the Kerberos ticket
            NegTokenInit negToken = new NegTokenInit();

            try {

                // Decode the security blob
                negToken.decode(secBlob);
            }
            catch (IOException ex) {

                // Log the error
                if (Debug.EnableError && hasDebug())
                    debugOutput(ex);

                // Return a logon failure status
                throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
            }

            // Save the NegTokenInit details as they may be required to generate a MIC
            sess.setSetupObject( client.getProcessId(), negToken, SetupObjectType.NegTokenInit);

            // Determine the authentication mechanism the client is using and logon
            String oidStr = null;
            if (negToken.numberOfOids() > 0)
                oidStr = negToken.getOidAt(0).toString();

            if (oidStr != null && oidStr.equals(OID.ID_NTLMSSP)) {

                // Check if NTLM logons are enabled
                if ( allowNTLMLogon() == false) {

                    // Client has sent an NTLM logon
                    if (hasDebugOutput())
                        debugOutput("[SMB] NTLM disabled, received NTLM logon from client (SPNEGO)");

                    // Return a logon failure status
                    throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
                }

                // NTLMSSP logon, get the NTLMSSP security blob that is inside the SPNEGO blob
                byte[] ntlmsspBlob = negToken.getMechtoken();

                // Create a seperate security blob for the NTLMSSP blob
                SecurityBlob ntlmBlob = new SecurityBlob(SecurityBlob.SecurityBlobType.NTLMSSP, ntlmsspBlob, secBlob.isUnicode());

                // Perform an NTLMSSP session setup
                authSts = doNtlmsspSessionSetup(sess, client, ntlmBlob, true);

                // NTLMSSP is a two stage process, set the SPNEGO status
                SPNEGO.Result spnegoSts = SPNEGO.Result.AcceptCompleted;

                if ( authSts == AuthStatus.MORE_PROCESSING) {

                    // Require another round trip
                    spnegoSts = SPNEGO.Result.AcceptIncomplete;
                }

                // Package the NTLMSSP response in an SPNEGO response
                negTarg = new NegTokenTarg(spnegoSts, OID.NTLMSSP, ntlmBlob.getResponseBlob());
            }
            else if (oidStr != null && (oidStr.equals(OID.ID_MSKERBEROS5) || oidStr.equals(OID.ID_KERBEROS5))) {

                // Kerberos logon
                negTarg = doKerberosLogon(sess, negToken, client);

                // Indicate authentication successful
                authSts = AuthStatus.AUTHENTICATED;
            }
            else {

                // Debug
                if (hasDebugOutput()) {
                    debugOutput("[SMB] No matching authentication OID found");
                    debugOutput("[SMB]   " + negToken.toString());
                }

                // Clear any session setup state
                sess.removeAllSetupObjects( client.getProcessId());

                // No valid authentication mechanism
                throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
            }
        }
        else {

            // Unknown SPNEGO token type
            if (hasDebugOutput())
                debugOutput("[SMB] Unknown SPNEGO token type=" + tokType);

            // Clear any session setup state
            sess.removeAllSetupObjects( client.getProcessId());

            // Return a logon failure status
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }

        // Generate the NegTokenTarg blob
        try {

            // Generate the response blob
            secBlob.setResponseBlob( negTarg.encode());
        }
        catch (IOException ex) {

            // Debug
            if (hasDebugOutput())
                debugOutput("[SMB] Failed to encode NegTokenTarg - " + ex.getMessage());

            // Clear any session setup state
            sess.removeAllSetupObjects( client.getProcessId());

            // Failed to build response blob
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }

        // Return the authentication status
        return authSts;
    }

    /**
     * Perform a Kerberos login and return an SPNEGO response
     *
     * @param sess     SMBSrvSession
     * @param negToken NegTokenInit
     * @param client   ClientInfo
     * @return NegTokenTarg
     * @exception SMBSrvException SMB error
     */
    private final NegTokenTarg doKerberosLogon(SMBSrvSession sess, NegTokenInit negToken, ClientInfo client)
            throws SMBSrvException {

        //  Authenticate the user
        KerberosDetails krbDetails = null;
        NegTokenTarg negTokenTarg = null;

        try {

            //  Run the session setup as a privileged action
            PrivilegedAction sessSetupAction = getKerberosPrivilegedAction( negToken);
            Object result = Subject.doAs(m_loginContext.getSubject(), sessSetupAction);

            if (result != null) {

                // Access the Kerberos response
                krbDetails = (KerberosDetails) result;

                // Determine the response OID
                Oid respOid = null;

                if (negToken.hasOid(OID.MSKERBEROS5)) {
                    respOid = OID.MSKERBEROS5;

                    // DEBUG
                    if (Debug.EnableDbg && hasDebug())
                        debugOutput("[SMB] Using OID MS Kerberos5 for NegTokenTarg");
                } else {
                    respOid = OID.KERBEROS5;

                    // DEBUG
                    if (Debug.EnableDbg && hasDebug())
                        debugOutput("[SMB] Using OID Kerberos5 for NegTokenTarg");
                }

                // Create the NegTokenTarg response blob
                negTokenTarg = new NegTokenTarg(SPNEGO.Result.AcceptCompleted, respOid, krbDetails.getResponseToken());

                // DEBUG
                if (Debug.EnableDbg && hasDebug())
                    debugOutput("[SMB] Created NegTokenTarg using standard Krb5 API response");

                // Check if this is a null logon
                String userName = krbDetails.getUserName();

                if (userName != null) {

                    // Check for the machine account name
                    if (userName.endsWith("$") && userName.equals(userName.toUpperCase())) {

                        // Null logon
                        client.setLogonType(ClientInfo.LogonType.Null);

                        //  Debug
                        if (Debug.EnableDbg && hasDebug())
                            debugOutput("[SMB] Machine account logon, " + userName + ", as null logon");
                    } else {

                        // Store the user name and full logon name in the client information, indicate that this is not a guest logon
                        client.setUserName( userName);
                        client.setLoggedOnName(krbDetails.getSourceName());
                        client.setGuest(false);

                        // Indicate that the session is logged on
                        sess.setLoggedOn(true);
                    }
                } else {

                    // Null logon
                    client.setLogonType(ClientInfo.LogonType.Null);
                }

                // Post Kerberos logon hook, logon can still be stopped if an exception is thrown
                postKerberosLogon( sess, krbDetails, client);

                // Indicate that the session is logged on
                sess.setLoggedOn(true);

                //  Debug
                if (Debug.EnableDbg && hasDebug())
                    debugOutput("[SMB] Logged on using Kerberos, user " + userName);
            }
            else {

                // Debug
                if (Debug.EnableDbg && hasDebug())
                    debugOutput("[SMB] No SPNEGO response, Kerberos logon failed");

                // Return a logon failure status
                throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
            }
        }
        catch (Exception ex) {

            // Log the error
            if (Debug.EnableError && hasDebug()) {
                debugOutput("[SMB] Kerberos logon error");
                debugOutput(ex);
            }

            // Return a logon failure status
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }

        // Return the response SPNEGO blob
        return negTokenTarg;
    }

    /**
     * Perform an NTLMv1 logon using the NTLMSSP type3 message
     *
     * @param sess     SMBSrvSession
     * @param client   ClientInfo
     * @param type3Msg Type3NTLMMessage
     * @exception SMBSrvException SMB error
     */
    private final void doNTLMv1Logon(SMBSrvSession sess, ClientInfo client, Type3NTLMMessage type3Msg)
            throws SMBSrvException {

        // Check if NTLMv1 logons are allowed
        if (acceptNTLMv1Logon() == false) {

            // NTLMv1 password hashes not accepted
            if (Debug.EnableWarn && hasDebug())
                debugOutput("[SMB] NTLMv1 not accepted, client " + sess.getRemoteName());

            // Return a logon failure
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }

        // Get the type 2 message that contains the challenge sent to the client
        Type2NTLMMessage type2Msg = (Type2NTLMMessage) sess.removeSetupObject(client.getProcessId(), SetupObjectType.Type2Message);

        // Get the NTLM logon details
        String userName = type3Msg.getUserName();

        // Check for a null logon
        if (userName.length() == 0) {

            // DEBUG
            if (hasDebugOutput())
                debugOutput("[SMB] Null logon");

            // Indicate a null logon in the client information
            client.setLogonType(ClientInfo.LogonType.Null);
            return;
        }

        // Get the user details
        UserAccount user = getUserDetails(userName);

        if (user != null) {

            // Authenticate the user
            AuthStatus sts = authenticateUser(client, sess, PasswordAlgorithm.NTLM1);

            if (sts.isError()) {

                // Logon failed
                throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
            }

            // Store the full user name in the client information, indicate that this is not a guest logon
            client.setUserName(userName);
            client.setGuest(sts == AuthStatus.GUEST_LOGON ? true : false);

            // Indicate that the session is logged on
            sess.setLoggedOn(true);
        } else {

            // Log a warning, user does not exist
            if (Debug.EnableError && hasDebug())
                debugOutput("[SMB] User does not exist, " + userName);

            // Return a logon failure
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }
    }

    /**
     * Perform an NTLMv1 logon using the NTLMSSP type3 message
     *
     * @param sess   SMBSrvSession
     * @param client ClientInfo
     * @exception SMBSrvException SMB error
     */
    private final void doNTLMv1Logon(SMBSrvSession sess, ClientInfo client)
            throws SMBSrvException {

        // Check if NTLMv1 logons are allowed
        if (acceptNTLMv1Logon() == false) {

            // NTLMv1 password hashes not accepted
            if (Debug.EnableWarn && hasDebug())
                debugOutput("[SMB] NTLMv1 not accepted, client " + sess.getRemoteName());

            // Return a logon failure
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }

        // Get the user details
        String userName = client.getUserName();
        UserAccount user = getUserDetails(userName);

        if (user != null) {

            // Authenticate the user
            AuthStatus sts = authenticateUser(client, sess, PasswordAlgorithm.NTLM1);

            if (sts.isError()) {

                // Logon failed
                throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
            }

            // Store the full user name in the client information, indicate that this is not a guest logon
            client.setUserName(userName);
            client.setGuest(sts == AuthStatus.GUEST_LOGON ? true : false);

            // Indicate that the session is logged on
            sess.setLoggedOn(true);
        } else {

            // Log a warning, user does not exist
            if (Debug.EnableError && hasDebug())
                debugOutput("[SMB] User does not exist, " + userName);

            // Return a logon failure
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }
    }

    /**
     * Perform an NTLMv2 logon using the NTLMSSP type3 message
     *
     * @param sess     SMBSrvSession
     * @param client   ClientInfo
     * @param type3Msg Type3NTLMMessage
     * @exception SMBSrvException SMB error
     */
    private final void doNTLMv2Logon(SMBSrvSession sess, ClientInfo client, Type3NTLMMessage type3Msg)
            throws SMBSrvException {

        // Get the type 2 message that contains the challenge sent to the client
        Type2NTLMMessage type2Msg = (Type2NTLMMessage) sess.getSetupObject(client.getProcessId(), SetupObjectType.Type2Message);

        // Get the NTLM logon details
        String userName = type3Msg.getUserName();

        // Check for a null logon
        if (userName.length() == 0) {

            // DEBUG
            if (hasDebugOutput())
                debugOutput("[SMB] Null logon");

            // Indicate a null logon in the client information
            client.setLogonType(ClientInfo.LogonType.Null);
            return;
        }

        // Get the user details
        UserAccount user = getUserDetails(userName);

        if (user != null) {

            try {

                // Calculate the MD4 of the user password
                byte[] md4Pwd = null;

                if (user.hasMD4Password())
                    md4Pwd = user.getMD4Password();
                else {
                    md4Pwd = getEncryptor().generateEncryptedPassword(user.getPassword(), type2Msg.getChallenge(),
                            PasswordEncryptor.MD4, null, null);
                    user.setMD4Password(md4Pwd);
                }

                // Generate the v2 hash using the challenge that was sent to the client
                byte[] v2hash = getEncryptor().doNTLM2Encryption(md4Pwd, type3Msg.getUserName(), type3Msg.getDomain());

                // Get the NTLMv2 blob sent by the client and the challenge that was sent by the server
                NTLMv2Blob v2blob = new NTLMv2Blob(type3Msg.getNTLMHash());
                byte[] srvChallenge = type2Msg.getChallenge();

                // Calculate the HMAC of the received blob and compare
                byte[] srvHmac = v2blob.calculateHMAC(srvChallenge, v2hash);
                byte[] clientHmac = v2blob.getHMAC();

                if (clientHmac != null && srvHmac != null && clientHmac.length == srvHmac.length) {
                    int i = 0;

                    while (i < clientHmac.length && clientHmac[i] == srvHmac[i])
                        i++;

                    if (i != clientHmac.length) {

                        // Return a logon failure
                        throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
                    }
                }

                // Check if a session key has been returned by the client
                if ( NTLM.hasFlag(type3Msg.getFlags(), NTLM.FlagKeyExchange)) {

                    // Make sure we got an encrypted session key from the client
                    if ( type3Msg.hasSessionKey()) {

                        // Generate the various session keys/ciphers
                        generateSessionKeys(sess, type3Msg, v2hash, srvHmac, clientHmac);
                    }
                    else
                        throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
                }

                // Store the full user name in the client information, indicate that this is not a guest logon
                client.setUserName(userName);
                client.setGuest(false);

                // Indicate that the session is logged on
                sess.setLoggedOn(true);
            }
            catch (Exception ex) {

                // Log the error
                if (Debug.EnableError && hasDebug())
                    debugOutput(ex);

                // Return a logon failure
                throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
            }
        } else {

            // Log a warning, user does not exist
            if (hasDebugOutput())
                debugOutput("[SMB] User does not exist, " + userName);

            // Return a logon failure
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }
    }

    /**
     * Perform an NTLMv2 logon
     *
     * @param sess   SMBSrvSession
     * @param client ClientInfo
     * @exception SMBSrvException SMB error
     */
    private final void doNTLMv2Logon(SMBSrvSession sess, ClientInfo client)
            throws SMBSrvException {

        // Check for a null logon
        if (client.getUserName().length() == 0) {

            // DEBUG
            if (hasDebugOutput())
                debugOutput("[SMB] Null logon");

            // Indicate a null logon in the client information
            client.setLogonType(ClientInfo.LogonType.Null);
            return;
        }

        // Get the user details
        UserAccount user = getUserDetails(client.getUserName());

        if (user != null) {

            try {

                // Get the challenge that was sent to the client during negotiation
                byte[] srvChallenge = null;
                if (sess.hasAuthenticationContext()) {

                    // Get the challenge from the authentication context
                    NTLanManAuthContext ntlmCtx = (NTLanManAuthContext) sess.getAuthenticationContext();
                    srvChallenge = ntlmCtx.getChallenge();
                }

                // Calculate the MD4 of the user password
                byte[] md4Pwd = null;

                if (user.hasMD4Password())
                    md4Pwd = user.getMD4Password();
                else {
                    md4Pwd = getEncryptor().generateEncryptedPassword(user.getPassword(), srvChallenge, PasswordEncryptor.MD4,
                            null, null);
                    user.setMD4Password(md4Pwd);
                }

                // Create the NTLMv2 blob from the received hashed password bytes
                NTLMv2Blob v2blob = new NTLMv2Blob(client.getPassword());

                // Generate the v2 hash using the challenge that was sent to the client
                byte[] v2hash = getEncryptor().doNTLM2Encryption(md4Pwd, client.getUserName(), client.getDomain());

                // Calculate the HMAC of the received blob and compare
                byte[] srvHmac = v2blob.calculateHMAC(srvChallenge, v2hash);
                byte[] clientHmac = v2blob.getHMAC();

                if (clientHmac != null && srvHmac != null && clientHmac.length == srvHmac.length) {

                    int i = 0;

                    while (i < clientHmac.length && clientHmac[i] == srvHmac[i])
                        i++;

                    if (i != clientHmac.length) {

                        // Return a logon failure
                        throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
                    }
                }

                // Store the full user name in the client information, indicate that this is not a guest logon
                client.setGuest(false);

                // Indicate that the session is logged on
                sess.setLoggedOn(true);
            }
            catch (Exception ex) {

                // Log the error
                if (Debug.EnableError && hasDebug())
                    debugOutput(ex);

                // Return a logon failure
                throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
            }
        } else {

            // Log a warning, user does not exist
            if (hasDebugOutput())
                debugOutput("[SMB] User does not exist, " + client.getUserName());

            // Return a logon failure
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }
    }

    /**
     * Perform an NTLMv2 session key logon
     *
     * @param sess     SMBSrvSession
     * @param client   ClientInfo
     * @param type3Msg Type3NTLMMessage
     * @exception SMBSrvException SMB error
     */
    private final void doNTLMv2SessionKeyLogon(SMBSrvSession sess, ClientInfo client, Type3NTLMMessage type3Msg)
            throws SMBSrvException {

        // Get the type 2 message that contains the challenge sent to the client
        Type2NTLMMessage type2Msg = (Type2NTLMMessage) sess.removeSetupObject(client.getProcessId(), SetupObjectType.Type2Message);

        // Get the NTLM logon details
        String userName = type3Msg.getUserName();

        // Check for a null logon
        if (userName.length() == 0) {

            // DEBUG
            if (hasDebugOutput())
                debugOutput("[SMB] Null logon");

            // Indicate a null logon in the client information
            client.setLogonType(ClientInfo.LogonType.Null);
            return;
        }

        // Get the user details
        UserAccount user = getUserDetails(userName);

        if (user != null) {

            // Create the value to be encrypted by appending the server challenge and client
            // challenge and applying an MD5 digest
            byte[] nonce = new byte[16];
            System.arraycopy(type2Msg.getChallenge(), 0, nonce, 0, 8);
            System.arraycopy(type3Msg.getLMHash(), 0, nonce, 8, 8);

            MessageDigest md5 = null;
            byte[] v2challenge = new byte[8];

            try {

                // Create the MD5 digest
                md5 = MessageDigest.getInstance("MD5");

                // Apply the MD5 digest to the nonce
                md5.update(nonce);
                byte[] md5nonce = md5.digest();

                // We only want the first 8 bytes
                System.arraycopy(md5nonce, 0, v2challenge, 0, 8);
            }
            catch (NoSuchAlgorithmException ex) {

                // Log the error
                if (Debug.EnableError && hasDebug())
                    debugOutput(ex);

                // Return a logon failure
                throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
            }

            // Generate the local encrypted password using the MD5 generated challenge
            byte[] p21 = new byte[21];
            byte[] md4byts = null;

            if (user.hasMD4Password())
                md4byts = user.getMD4Password();
            else {
                try {
                    md4byts = getEncryptor().generateEncryptedPassword(user.getPassword(), null, PasswordEncryptor.MD4, null,
                            null);
                }
                catch (Exception ex) {

                    // DEBUG
                    if (Debug.EnableError && hasDebug())
                        debugOutput(ex);

                    // Return a logon failure
                    throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
                }
            }

            System.arraycopy(md4byts, 0, p21, 0, 16);

            // Generate the local hash of the password
            byte[] localHash = null;

            try {
                localHash = getEncryptor().doNTLM1Encryption(p21, v2challenge);
            }
            catch (NoSuchAlgorithmException ex) {

                // Log the error
                if (Debug.EnableError && hasDebug())
                    debugOutput(ex);
            }

            // Validate the password
            byte[] clientHash = type3Msg.getNTLMHash();

            if (clientHash != null && localHash != null && clientHash.length == localHash.length) {
                int i = 0;

                while (i < clientHash.length && clientHash[i] == localHash[i])
                    i++;

                if (i != clientHash.length) {

                    // Return a logon failure
                    throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
                }
            }

            // Store the full user name in the client information, indicate that this is not a guest logon
            client.setUserName(userName);
            client.setGuest(false);

            // Indicate that the session is logged on
            sess.setLoggedOn(true);
        } else {

            // Log a warning, user does not exist
            if (hasDebugOutput())
                debugOutput("[SMB] User does not exist, " + userName);

            // Return a logon failure
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        }
    }

    /**
     * Perform a hashed password logon using either NTLMv1 or NTLMv2
     *
     * @param sess   SMBSrvSession
     * @param reqPkt SMBSrvPacket
     * @exception SMBSrvException SMB error
     */
    private final void doHashedPasswordLogon(SMBSrvSession sess, SMBSrvPacket reqPkt)
            throws SMBSrvException {

        // Get the associated parser
        SMBV1Parser parser = (SMBV1Parser) reqPkt.getParser();

        // Check that the received packet looks like a valid NT session setup andX request
        if (parser.checkPacketIsValid(13, 0) == false)
            throw new SMBSrvException(SMBStatus.NTInvalidParameter, SMBStatus.ErrSrv, SMBStatus.SRVNonSpecificError);

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
            debugOutput("[SMB] NT Session setup from user=" + user + ", password="
                    + (uniPwd != null ? HexDump.hexString(uniPwd) : "none") + ", ANSIpwd="
                    + (ascPwd != null ? HexDump.hexString(ascPwd) : "none") + ", domain=" + domain + ", os=" + clientOS + ", VC="
                    + vcNum + ", maxBuf=" + maxBufSize + ", maxMpx=" + maxMpx + ", authCtx=" + sess.getAuthenticationContext());
            debugOutput("[SMB]   MID=" + parser.getMultiplexId() + ", UID=" + parser.getUserId() + ", PID="
                    + parser.getProcessId());
        }

        // Store the client maximum buffer size, maximum multiplexed requests count and client capability flags
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
        if (user.length() == 0 && domain.length() == 0 && uniPwdLen == 0)
            client.setLogonType(ClientInfo.LogonType.Null);

        // Authenticate the user using the Unicode password hash, this is either NTLMv1 or NTLMv2 encoded
        boolean isGuest = false;

        if (uniPwd != null) {
            if (uniPwd.length == 24) {

                // NTLMv1 hashed password
                doNTLMv1Logon(sess, client);

                // Debug
                if (hasDebugOutput())
                    debugOutput("[SMB] Logged on using Hashed/NTLMv1");
            } else if (uniPwd.length > 0) {

                // NTLMv2 blob
                doNTLMv2Logon(sess, client);

                // Debug
                if (hasDebugOutput())
                    debugOutput("[SMB] Logged on using Hashed/NTLMv2");
            }
        } else {

            // Mark the session as a null logon
            client.setLogonType(ClientInfo.LogonType.Null);
        }

        // Check if the user was logged on as guest
        if (client.isGuest()) {

            // Guest logon
            isGuest = true;

            // DEBUG
            if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
                debugOutput("[SMB] User " + user + ", logged on as guest");
        }

        // Create a virtual circuit and allocate a UID to the new circuit
        VirtualCircuit vc = new VirtualCircuit(vcNum, client);
        int uid = sess.addVirtualCircuit(vc);

        if (uid == VirtualCircuit.InvalidID) {

            // DEBUG
            if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE))
                debugOutput("[SMB] Failed to allocate UID for virtual circuit, " + vc);

            // Failed to allocate a UID
            throw new SMBSrvException(SMBStatus.NTLogonFailure, SMBStatus.ErrDos, SMBStatus.DOSAccessDenied);
        } else if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_NEGOTIATE)) {

            // DEBUG
            debugOutput("[SMB] Allocated UID=" + uid + " for VC=" + vc);
        }

        // Set the guest flag for the client, indicate that the session is logged on
        if (isGuest == true)
            client.setLogonType(ClientInfo.LogonType.Guest);
        sess.setLoggedOn(true);

        // Indicate a successful logon, the method can throw an exception to stop the logon
        onSuccessfulLogon( client);

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
    }

    /**
     * Generate the session keys/ciphers
     *
     * @param sess SMBSrvSession
     * @param type3 Type3NTLMMessage
     * @param v2hash byte[]
     * @param srvHMAC byte[]
     * @param clientHMAC byte[]
     * @exception Exception Error initializing the keys
     */
    private void generateSessionKeys(SMBSrvSession sess, Type3NTLMMessage type3, byte[] v2hash, byte[] srvHMAC, byte[] clientHMAC)
        throws Exception {

        // Calculate the session base key
        Mac hmacMd5 = Mac.getInstance("HMACMD5");
        SecretKeySpec blobKey = new SecretKeySpec(v2hash, "MD5");

        hmacMd5.init(blobKey);
        byte[] sessionBaseKey = hmacMd5.doFinal(clientHMAC);

        // Decrypt the random session key
        byte[] encSessKey = type3.getSessionKey();

        Cipher rc4 = Cipher.getInstance("RC4");
        rc4.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sessionBaseKey, "RC4"));
        byte[] randSessKey = rc4.doFinal(encSessKey);

        // Store the session key
        sess.addSessionKey(KeyType.SessionKey, randSessKey);

        // DEBUG
        if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.DBG_SIGNING))
            sess.debugPrintln("Set session key=" + HexDump.hexString(randSessKey));

        // Check if extended security has been negotiated
        if ( type3.hasFlag(NTLM.FlagNegotiateExtSecurity)) {

            // Generate the NTLM server signing key
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            md5.update(randSessKey);
            md5.update(NTLM.SERVER_SIGNING_KEY_CONST);

            byte[] srvSigningKey = md5.digest();

            // Store the server signing key
            sess.addSessionKey(KeyType.NTLMServerSigningKey, srvSigningKey);

            // Generate the NTLM client signing key
            md5.reset();

            md5.update(randSessKey);
            md5.update(NTLM.CLIENT_SIGNING_KEY_CONST);

            byte[] clientSigningKey = md5.digest();

            // Store the client signing key
            sess.addSessionKey(KeyType.NTLMClientSigningKey, clientSigningKey);

            // Generate the NTLM server sealing key
            md5.reset();

            md5.update(randSessKey);
            md5.update(NTLM.SERVER_SEALING_KEY_CONST);

            byte[] srvSealingKey = md5.digest();

            // Store the server sealing key
            sess.addSessionKey(KeyType.NTLMServerSealingKey, srvSealingKey);

            // Generate the NTLM client sealing key
            md5.reset();

            md5.update(randSessKey);
            md5.update(NTLM.CLIENT_SEALING_KEY_CONST);

            byte[] clientSealingKey = md5.digest();

            // Store the client sealing key
            sess.addSessionKey(KeyType.NTLMClientSealingKey, clientSealingKey);

            // Create the server RC4 sealing context
            Cipher srvRC4 = Cipher.getInstance("RC4");
            SecretKeySpec srvKey = new SecretKeySpec(srvSealingKey, "RC4");
            srvRC4.init(Cipher.ENCRYPT_MODE, srvKey);

            // Store the server RC4 context
            sess.addSessionKey(KeyType.NTLMServerRC4Context, srvRC4);

            // Create the client RC4 sealing context
            Cipher clientRC4 = Cipher.getInstance("RC4");
            SecretKeySpec clientKey = new SecretKeySpec(clientSealingKey, "RC4");
            clientRC4.init(Cipher.DECRYPT_MODE, clientKey);

            // Store the client RC4 context
            sess.addSessionKey(KeyType.NTLMClientRC4Context, clientRC4);
        }
    }

    /**
     * Verify a MIC token
     *
     * @param sess SMBSrvSession
     * @param buf byte[]
     * @param offset int
     * @param len int
     * @param rxMIC byte[]
     * @param pid int
     * @return boolean
     */
    protected final boolean verifyMIC(SMBSrvSession sess, byte[] buf, int offset, int len, byte[] rxMIC, int pid) {

        // Make sure the received MIC looks like a valid MIC token
        if ( rxMIC == null || rxMIC.length != MIC_TOKEN_LENGTH)
            return false;

        DataBuffer rxMICBuf = new DataBuffer( rxMIC, 0, rxMIC.length);
        if ( rxMICBuf.getInt() != MIC_TOKEN_VER_NTLMSSP)
            return false;

        boolean micSts = false;

        try {

            // Get the client signing/sealing keys and RC4 context
            byte[] clientSigningKey = (byte[]) sess.getSessionKey(KeyType.NTLMClientSigningKey);
            Cipher clientRC4 = (Cipher) sess.getSessionKey(KeyType.NTLMClientRC4Context);

            // HMAC-MD5 of sequence number+data using the client signing key/RC4
            SecretKeySpec md5Key = new SecretKeySpec( clientSigningKey, "MD5");
            Mac hmacMD5 = Mac.getInstance("HMACMD5");

            byte[] seqNo = new byte[] { 0x00, 0x00, 0x00, 0x00};

            hmacMD5.init( md5Key);
            hmacMD5.update( rxMICBuf.getBuffer(), MIC_TOKEN_SEQNO, 4);
            hmacMD5.update( buf, offset, len);
            byte[] md5Chk = hmacMD5.doFinal();

            // RC4 first 8 bytes of the HMAC-MD5 result
            byte[] chkSum = clientRC4.update(md5Chk, 0, 8);

            // Build the MIC token
            DataBuffer micToken = new DataBuffer(16);

            micToken.putInt( MIC_TOKEN_VER_NTLMSSP);
            micToken.appendData(chkSum);
            micToken.putInt( 0);

            // Check if the MIC token is valid
            if ( Arrays.equals(rxMIC, micToken.getBuffer()))
                micSts = true;

            // DEBUG
            if (micSts == false && hasDebugOutput()) {
                sess.debugPrintln("Verify MIC, generated MIC token=" + HexDump.hexString(micToken.getBuffer()));
                sess.debugPrintln("             Received MIC token=" + HexDump.hexString(rxMIC));
            }
        }
        catch ( Exception ex) {
            ex.printStackTrace();
        }

        // Return the MIC status
        return micSts;
    }

    /**
     * Generate a MIC token
     *
     * @param sess SMBSrvSession
     * @param buf byte[]
     * @param offset int
     * @param len int
     * @param seqNo int
     * @return byte[]
     */
    protected final byte[] generateMIC(SMBSrvSession sess, byte[] buf, int offset, int len, int seqNo) {

        // Get the server signing key
        byte[] srvSigningKey = (byte[]) sess.getSessionKey(KeyType.NTLMServerSigningKey);
        DataBuffer micToken = null;

        try {

            // Check if the server signing key is valid, if not then use the weaker CRC-32 to generate the MIC
            if ( srvSigningKey != null) {

                // Need to generate a mechListMIC for the response
                SecretKeySpec key = new SecretKeySpec(srvSigningKey, "MD5");
                Mac hmacMd5 = Mac.getInstance("HMACMD5");

                // Buffer for the sequence number bytes
                byte[] seqNoBuf = new byte[4];
                DataPacker.putInt(seqNo, seqNoBuf, 0);

                // Generate the message integrity code
                hmacMd5.init(key);
                hmacMd5.update(seqNoBuf);
                hmacMd5.update(buf, offset, len);

                byte[] chkSum = hmacMd5.doFinal();

                Cipher srvRC4 = (Cipher) sess.getSessionKey(KeyType.NTLMServerRC4Context);
                chkSum = srvRC4.update(chkSum, 0, 8);

                micToken = new DataBuffer(16);
                micToken.putInt(MIC_TOKEN_VER_NTLMSSP);
                micToken.appendData(chkSum, 0, 8);
                micToken.putInt(seqNo);
            }

            // Generate the MIC using CRC-32
            else {

                // TODO: Test with NTLM neg ext security switched off

                // Generate the CRC-32
                CRC32 crc32 = new CRC32();
                crc32.update(buf, offset, len);

                // Build the MIC token
                micToken = new DataBuffer(16);
                micToken.putInt(MIC_TOKEN_VER_NTLMSSP);
                micToken.putZeros(4);
                micToken.putInt ((int) (crc32.getValue() & 0xFFFFFFFF));
                micToken.putInt(seqNo);
            }
        }
        catch (Exception ex) {

            // Ignore the error for now
        }

        // Return the MIC token, if valid
        if ( micToken != null) {

            // DEBUG
            if (hasDebugOutput())
                debugOutput("[SMB] Generate micToken (" + (srvSigningKey != null ? "key" : "crc") + ")=" + HexDump.hexString(micToken.getBuffer()));

            return micToken.getBuffer();
        }

        return null;
    }

    /**
     * Build the SPNEGO NegTokenInit with the specified mech types enabled
     *
     * @param srvName String
     * @param mechTypes List of OIDs
     * @return NegTokenInit
     */
    protected NegTokenInit buildNegTokenInit( String srvName, List<Oid> mechTypes) {

        // Build the mechListMIC principle
        //
        // Note: This field is not as specified, only seems to be used by Samba clients (Linux/Mac/Unix)
        String mecListMIC = null;

        StringBuilder mic = new StringBuilder();

        mic.append("cifs/");
        mic.append(srvName.toLowerCase());
        mic.append("@");
        mic.append(m_krbRealm);

        mecListMIC = mic.toString();

        // Build the SPNEGO NegTokenInit that contains the authentication types that the
        // SMB server accepts
        return new NegTokenInit(mechTypes, mecListMIC);
    }

    /**
     * Called after a successful logon
     *
     * @param client ClientInfo
     */
    protected void onSuccessfulLogon( ClientInfo client) {
    }

    /**
     * Normalize a user name from a Kerberos/NTLM logon
     *
     * @param externalUserId String
     * @return String
     * @exception SMBSrvException SMB error
     */
    protected String normalizeUserId(String externalUserId)
        throws SMBSrvException {

        // Default, just return the original user name
        return externalUserId;
    }

    /**
     * Get the privileged action to run for a Kerberos logon
     *
     * @param negToken NegTokenInit
     * @return PrivilegedAction
     */
    protected PrivilegedAction getKerberosPrivilegedAction( NegTokenInit negToken) {
        return new SessionSetupPrivilegedAction(m_accountName, negToken.getMechtoken());
    }

    /**
     * Kerberos authentication hook, can stop the logon even though Kerberos authentication has been successful
     *
     * @param sess SMBSrvSession
     * @param krbDetails KerberosDetails
     * @param client ClientInfo
     * @exception SMBSrvException To prevent the user logon
     */
    protected void postKerberosLogon( SMBSrvSession sess, KerberosDetails krbDetails, ClientInfo client)
        throws SMBSrvException {

    }

    /**
     * NTLM authentication hook, can stop the logon even though the NTLM authentication has been successful
     *
     * @param sess SMBSrvSession
     * @param client ClientInfo
     * @throws SMBSrvException To prevent the user logon
     */
    protected void postNTLMLogon( SMBSrvSession sess, ClientInfo client)
        throws SMBSrvException {

    }

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public boolean hasDebugOutput() {
        return Debug.EnableDbg && hasDebug();
    }

    /**
     * Output debug logging
     *
     * @param dbg String
     */
    public void debugOutput(String dbg) {
        Debug.println( dbg);
    }

    /**
     * Output an exception
     *
     * @param ex    Exception
     */
    public void debugOutput(Exception ex) {

        //	Write the exception stack trace records to an in-memory stream
        StringWriter strWrt = new StringWriter();
        ex.printStackTrace(new PrintWriter(strWrt, true));

        //	Split the resulting string into seperate records and output to the debug device
        StringTokenizer strTok = new StringTokenizer(strWrt.toString(), LineSeperator);

        while (strTok.hasMoreTokens())
            debugOutput(strTok.nextToken());
    }

    @Override
    public String toString() {

        StringBuilder str = new StringBuilder();

        str.append( getClass().getName());
        str.append(" - ");

        if (m_useRawNTLMSSP)
            str.append( "NTLMSSP");
        else
            str.append( "SPNEGO");

        if ( allowNTLMLogon() == false)
            str.append(",NoNTLM");

        if ( acceptNTLMv1Logon() == false)
            str.append(",NoNTLMv1");

        if ( m_loginContext != null && m_krbRealm != null) {
            str.append(",Kerberos=");
            str.append( m_krbRealm);
        }

        return str.toString();
    }
}
