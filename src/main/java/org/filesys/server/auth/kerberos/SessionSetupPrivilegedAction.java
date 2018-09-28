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

package org.filesys.server.auth.kerberos;

import java.security.PrivilegedAction;

import org.filesys.debug.Debug;
import org.filesys.server.auth.spnego.OID;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;


/**
 * Session Setup Privileged Action Class
 * 
 * <p>
 * Handle the processing of a received SPNEGO packet in the context of the SMB server.
 *
 * @author gkspencer
 */
public class SessionSetupPrivilegedAction implements PrivilegedAction {

  // Received security blob details
  private byte[] m_secBlob;
  private int m_secOffset;
  private int m_secLen;

  // SMB server account name
  private String m_accountName;

  /**
   * Class constructor
   * 
   * @param accountName String
   * @param secBlob byte[]
   */
  public SessionSetupPrivilegedAction(String accountName, byte[] secBlob) {

    m_accountName = accountName;

    m_secBlob = secBlob;
    m_secOffset = 0;
    m_secLen = secBlob.length;
  }

  /**
   * Class constructor
   * 
   * @param accountName String
   * @param secBlob byte[]
   * @param secOffset int
   * @param secLen int
   */
  public SessionSetupPrivilegedAction(String accountName, byte[] secBlob, int secOffset, int secLen) {

    m_accountName = accountName;

    m_secBlob = secBlob;
    m_secOffset = secOffset;
    m_secLen = secLen;
  }

  /**
   * Run the privileged action
   */
  public Object run() {

    KerberosDetails krbDetails = null;

    try {
      GSSManager gssManager = GSSManager.getInstance();
      GSSName serverGSSName = gssManager.createName(m_accountName, GSSName.NT_USER_NAME);
      GSSCredential serverGSSCreds = gssManager.createCredential(serverGSSName, GSSCredential.INDEFINITE_LIFETIME, OID.KERBEROS5,
          GSSCredential.ACCEPT_ONLY);

      GSSContext serverGSSContext = gssManager.createContext(serverGSSCreds);

      // Accept the incoming security blob and generate the response blob
      byte[] respBlob = serverGSSContext.acceptSecContext(m_secBlob, m_secOffset, m_secLen);

      // Create the Kerberos response details
      krbDetails = new KerberosDetails(serverGSSContext.getSrcName(), serverGSSContext.getTargName(), respBlob);
    }
    catch (GSSException ex) {
      Debug.println(ex);
    }

    // Return the Kerberos response
    return krbDetails;
  }
}
