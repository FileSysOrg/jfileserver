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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.filesys.server.auth.ClientInfo;
import org.filesys.server.auth.UserAccount;
import org.filesys.server.config.InvalidConfigurationException;
import org.filesys.server.config.SecurityConfigSection;
import org.filesys.server.config.ServerConfiguration;
import org.springframework.extensions.config.ConfigElement;

/**
 * <p>Local Authenticator Class.
 *
 * <p>Authenticate FTP users using the user accounts defined in the configuration or available via the
 * users interface.
 *
 * @author gkspencer
 */
public class LocalAuthenticator implements FTPAuthenticator {

    // Server configuration and required sections
    protected ServerConfiguration m_config;
    protected SecurityConfigSection m_securityConfig;

    // Debug output enable
    private boolean m_debug;

    /**
     * Authenticate an FTP user
     *
     * @param cInfo ClientInfo
     * @param sess  FTPSrvSession
     * @return boolean
     */
    public boolean authenticateUser(ClientInfo cInfo, FTPSrvSession sess) {

        //  Check if the user exists in the user list
        UserAccount userAcc = getUserDetails(cInfo.getUserName());
        if (userAcc != null) {

            //  Validate the password
            boolean authSts = false;

            if (cInfo.getPassword() != null) {

                //  Check if the user details has the MD4 password
                if (userAcc.hasMD4Password()) {

                    //  Convert the client password to an MD4 hash
                    try {
                        MessageDigest md4 = MessageDigest.getInstance("MD4");

                        md4.update(cInfo.getPassword());
                        byte[] md4Hash = md4.digest();

                        //  Compare the passwords
                        byte[] userMd4 = userAcc.getMD4Password();

                        for (int i = 0; i < userMd4.length; i++)
                            if (userMd4[i] != md4Hash[i])
                                authSts = false;
                    }
                    catch (NoSuchAlgorithmException ex) {
                    }
                } else {

                    //  Compare the plaintext passwords
                    byte[] userPwd = userAcc.getPassword().getBytes();
                    byte[] clientPwd = cInfo.getPassword();

                    if (userPwd.length == clientPwd.length) {

                        //  Compare the passwords
                        authSts = true;

                        for (int i = 0; i < userPwd.length; i++)
                            if (userPwd[i] != clientPwd[i])
                                authSts = false;
                    }
                }
            }

            //  Return the authentication status
            return authSts;
        }

        //  Unknown user
        return false;
    }

    /**
     * Search for the requried user account details
     *
     * @param user String
     * @return UserAccount
     */
    public final UserAccount getUserDetails(String user) {

        // Get the user account details via the users interface
        return m_securityConfig.getUsersInterface().getUserAccount(user);
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
     * Initialize the FTP authenticator
     *
     * @param config ServerConfiguration
     * @param params ConfigElement
     * @exception InvalidConfigurationException Error initializing the authenticator
     */
    public void initialize(ServerConfiguration config, ConfigElement params)
            throws InvalidConfigurationException {

        // Save the server configuration
        m_config = config;

        //  Get the security configuration
        m_securityConfig = (SecurityConfigSection) m_config.getConfigSection(SecurityConfigSection.SectionName);

        // Check if debug output is enabled
        if (params.getChild("Debug") != null)
            m_debug = true;
    }

    /**
     * Close the authenticator
     */
    public void closeAuthenticator() {
    }
}
