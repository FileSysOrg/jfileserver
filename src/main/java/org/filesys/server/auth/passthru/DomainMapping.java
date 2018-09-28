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

/**
 * Domain Mapping Class
 *
 * @author gkspencer
 */
public abstract class DomainMapping {

    // Domain name
    private String m_domain;

    /**
     * Class consructor
     *
     * @param domain String
     */
    public DomainMapping(String domain) {
        m_domain = domain;
    }

    /**
     * Return the domain name
     *
     * @return String
     */
    public final String getDomain() {
        return m_domain;
    }

    /**
     * Check if the client address is a member of this domain
     *
     * @param clientIP int
     * @return boolean
     */
    public abstract boolean isMemberOfDomain(int clientIP);
}
