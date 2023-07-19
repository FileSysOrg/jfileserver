/*
 * Copyright (C) 2023 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.smb.dcerpc.info;

import org.filesys.smb.dcerpc.*;

/**
 * Ds Role Primary Domain Info Basic Class
 *
 * @author gkspencer
 */
public class DsRolePrimaryDomainInfoBasic implements DCEWriteable {

    // Flags values
    public final int DsRolePrimaryDsRunning         = 0x000000001;
    public final int DsRolePrimaryDsMixedMode       = 0x000000002;
    public final int DsRoleUpgradeInProgress        = 0x000000004;
    public final int DsRolePrimaryDomainGuidPresent = 0x001000000;

    // Machine role
    private DsRoleMachineRole m_machineRole;

    // Flags
    private int m_flags;

    // Domain name, DNS domain name, forest name
    private String m_domain;
    private String m_dnsName;
    private String m_forestName;

    // Domain identifier
    private UUID m_domainGUID;

    /**
     * Default constructor
     */
    public DsRolePrimaryDomainInfoBasic() {
    }

    /**
     * Class constructor
     *
     * @param role DsRoleMachineRole
     * @param domain String
     * @param dnsName String
     * @param forestName String
     * @param domainGuid UUID
     * @param flags int
     */
    public DsRolePrimaryDomainInfoBasic( DsRoleMachineRole role, String domain, String dnsName, String forestName, UUID domainGuid, int flags) {
        m_machineRole = role;

        m_domain = domain;
        m_dnsName = dnsName;
        m_forestName = forestName;

        m_domainGUID = domainGuid;

        m_flags  = flags;

        if ( m_domainGUID != null)
            m_flags += DsRolePrimaryDomainGuidPresent;
    }

    @Override
    public void writeObject(DCEBuffer buf, DCEBuffer strBuf)
        throws DCEBufferException {

        // Output the information level
        buf.putInt( DsRolePrimaryDomainInfoLevel.DsRolePrimaryDomainInfoBasic);

        // Output the primary domain information structure
        buf.putInt( m_machineRole.ordinal());
        buf.putInt( m_flags);

        buf.putPointer( true);      // NetBIOS domain name
        buf.putPointer( m_dnsName != null);
        buf.putPointer( m_forestName != null);

        if ( m_domainGUID != null)
            buf.putUUID( m_domainGUID, false);
        else
            buf.putZeroBytes( 16);

        // Set the strings and object values for the structure
        strBuf.putString( m_domain, DCEBuffer.ALIGN_INT, true);

        if ( m_dnsName != null)
            strBuf.putString( m_dnsName, DCEBuffer.ALIGN_INT, true);

        if ( m_forestName != null)
            strBuf.putString( m_forestName, DCEBuffer.ALIGN_INT, true);
    }
}
