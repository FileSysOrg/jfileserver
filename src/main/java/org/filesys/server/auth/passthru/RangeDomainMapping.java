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
 * Address Range Domain Mapping Class
 *
 * @author gkspencer
 */
public class RangeDomainMapping extends DomainMapping {

    // Range from/to addresses
    private int m_rangeFrom;
    private int m_rangeTo;

    /**
     * class constructor
     *
     * @param domain    String
     * @param rangeFrom int
     * @param rangeTo   int
     */
    public RangeDomainMapping(String domain, int rangeFrom, int rangeTo) {
        super(domain);

        m_rangeFrom = rangeFrom;
        m_rangeTo = rangeTo;
    }

    /**
     * Return the from range address
     *
     * @return int
     */
    public final int getRangeFrom() {
        return m_rangeFrom;
    }

    /**
     * Return the to range address
     *
     * @return int
     */
    public final int getRangeTo() {
        return m_rangeTo;
    }

    /**
     * Check if the client address is a member of this domain
     *
     * @param clientIP int
     * @return boolean
     */
    public boolean isMemberOfDomain(int clientIP) {
        if (clientIP >= m_rangeFrom && clientIP <= m_rangeTo)
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
        str.append(IPAddress.asString(getRangeFrom()));
        str.append(":");
        str.append(IPAddress.asString(getRangeTo()));
        str.append("]");

        return str.toString();
    }
}
