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

package org.filesys.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.Netapi32Util;
import org.filesys.netbios.NetBIOSName;
import org.filesys.netbios.NetBIOSNameList;
import org.filesys.netbios.NetBIOSSession;


/**
 * Local Server Class
 *
 * @author gkspencer
 */
public class LocalServer {

    // Local server name and domain
    private static String m_localName;
    private static String m_localDomain;

    /**
     * Get the local server name and optionally trim the domain name
     *
     * @param trimDomain boolean
     * @return String
     */
    public static final String getLocalServerName(boolean trimDomain) {

        // Check if the name has already been set
        if (m_localName != null)
            return m_localName;

        // Find the local server name
        String srvName = null;

        if (PlatformType.isPlatformType() == PlatformType.Type.WINDOWS) {

            // Get the local name via JNA
            srvName = Kernel32Util.getComputerName();
        }
        else {

            // Get the DNS name of the local system
            try {
                srvName = InetAddress.getLocalHost().getHostName();
            }
            catch (UnknownHostException ex) {
            }
        }

        // Strip the domain name
        if (trimDomain && srvName != null) {
            int pos = srvName.indexOf(".");
            if (pos != -1)
                srvName = srvName.substring(0, pos);
        }

        // Save the local server name
        m_localName = srvName;

        // Return the local server name
        return srvName;
    }

    /**
     * Get the local domain/workgroup name
     *
     * @return String
     */
    public static final String getLocalDomainName() {

        // Check if the local domain has been set
        if (m_localDomain != null)
            return m_localDomain;

        // Find the local domain name
        String domainName = null;

        if (PlatformType.isPlatformType() == PlatformType.Type.WINDOWS) {

            // Get the local domain/workgroup name via JNA
            String computerName = Kernel32Util.getComputerName();
            domainName = Netapi32Util.getDomainName( computerName);
        }
        else {

            NetBIOSName nbName = null;

            try {

                // Try and find the browse master on the local network
                nbName = NetBIOSSession.FindName(NetBIOSName.BrowseMasterName, NetBIOSName.BrowseMasterGroup, 5000);

                // Get the NetBIOS name list from the browse master
                NetBIOSNameList nbNameList = NetBIOSSession.FindNamesForAddress(nbName.getIPAddressString(0));
                if (nbNameList != null) {
                    nbName = nbNameList.findName(NetBIOSName.MasterBrowser, false);

                    // Set the domain/workgroup name
                    if (nbName != null)
                        domainName = nbName.getName();
                }
            }
            catch (IOException ex) {
            }
        }

        // Save the local domain name
        m_localDomain = domainName;

        // Return the local domain/workgroup name
        return domainName;
    }
}
