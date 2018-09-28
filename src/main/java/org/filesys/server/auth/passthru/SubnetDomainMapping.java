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

package org.filesys.server.auth.passthru;

import org.filesys.util.IPAddress;

/**
 * Subnet Domain Mapping Class
 *
 * @author gkspencer
 */
public class SubnetDomainMapping extends DomainMapping {

    // Subnet and mask for the domain
    private int m_subnet;
    private int m_mask;

    /**
     * class constructor
     *
     * @param domain String
     * @param subnet int
     * @param mask   int
     */
    public SubnetDomainMapping(String domain, int subnet, int mask) {
        super(domain);

        m_subnet = subnet;
        m_mask = mask;
    }

    /**
     * Return the subnet
     *
     * @return int
     */
    public final int getSubnet() {
        return m_subnet;
    }

    /**
     * Return the subnet mask
     *
     * @return int
     */
    public final int getSubnetMask() {
        return m_mask;
    }

    /**
     * Check if the client address is a member of this domain
     *
     * @param clientIP int
     * @return boolean
     */
    public boolean isMemberOfDomain(int clientIP) {
        if ((clientIP & m_mask) == m_subnet)
            return true;
        return false;
    }

    /**
     * Return the domain mapping as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(getDomain());
        str.append(",");
        str.append(IPAddress.asString(getSubnet()));
        str.append(":");
        str.append(IPAddress.asString(getSubnetMask()));
        str.append("]");

        return str.toString();
    }
}
