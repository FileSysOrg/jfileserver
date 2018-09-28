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

package org.filesys.smb.mailslot.win32;

import org.filesys.netbios.NetBIOSName;
import org.filesys.netbios.win32.Win32NetBIOS;
import org.filesys.smb.mailslot.HostAnnouncer;
import org.filesys.smb.server.win32.Win32NetBIOSSessionSocketHandler;

/**
 * <p>The host announcer class periodically broadcasts a host announcement datagram to inform other
 * Windows networking hosts of the local hosts existence and capabilities.
 *
 * <p>The Win32 NetBIOS host announcer sends out the announcements using datagrams sent via the Win32
 * Netbios() Netapi32 call.
 *
 * @author gkspencer
 */
public class Win32NetBIOSHostAnnouncer extends HostAnnouncer {

    //	Associated session handler
    Win32NetBIOSSessionSocketHandler m_handler;

    /**
     * Create a host announcer.
     *
     * @param handler Win32NetBIOSSessionSocketHandler
     * @param domain  Domain name to announce to
     * @param intval  Announcement interval, in minutes
     */
    public Win32NetBIOSHostAnnouncer(Win32NetBIOSSessionSocketHandler handler, String domain, int intval) {

        //	Save the handler
        m_handler = handler;

        //	Add the host to the list of names to announce
        addHostName(handler.getServerName());
        setDomain(domain);
        setInterval(intval);
    }

    /**
     * Return the LANA
     *
     * @return int
     */
    public final int getLana() {
        return m_handler.getLANANumber();
    }

    /**
     * Return the host name NetBIOS number
     *
     * @return int
     */
    public final int getNameNumber() {
        return m_handler.getNameNumber();
    }

    /**
     * Initialize the host announcer.
     *
     * @throws Exception Failed to initialize the host announcer
     */
    protected void initialize()
            throws Exception {

        //	Set the thread name
        setName("Win32HostAnnouncer_L" + getLana());
    }

    /**
     * Determine if the network connection used for the host announcement is valid
     *
     * @return boolean
     */
    public boolean isNetworkEnabled() {
        return m_handler.isLANAValid();
    }

    /**
     * Send an announcement broadcast.
     *
     * @param hostName Host name being announced
     * @param buf      Buffer containing the host announcement mailslot message.
     * @param offset   Offset to the start of the host announcement message.
     * @param len      Host announcement message length.
     */
    protected void sendAnnouncement(String hostName, byte[] buf, int offset, int len)
            throws Exception {

        //	Build the destination NetBIOS name using the domain/workgroup name
        NetBIOSName destNbName = new NetBIOSName(getDomain(), NetBIOSName.MasterBrowser, false);
        byte[] destName = destNbName.getNetBIOSName();

        //  Send the host announce datagram via the Win32 Netbios() API call
        Win32NetBIOS.SendDatagram(getLana(), getNameNumber(), destName, buf, 0, len);
    }
}
