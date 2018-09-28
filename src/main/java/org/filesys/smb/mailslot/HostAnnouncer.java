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

package org.filesys.smb.mailslot;

import org.filesys.debug.Debug;
import org.filesys.netbios.NetBIOSName;
import org.filesys.smb.ServerType;
import org.filesys.smb.TransactionNames;
import org.filesys.util.StringList;

/**
 * <p>
 * The host announcer class periodically broadcasts a host announcement datagram to inform other
 * Windows networking hosts of the local hosts existence and capabilities.
 *
 * @author gkspencer
 */
public abstract class HostAnnouncer extends Thread {

    // Shutdown announcement interval and message count
    public static final int SHUTDOWN_WAIT   = 2000; // 2 seconds
    public static final int SHUTDOWN_COUNT  = 3;

    // Starting announcement interval, doubles until it reaches the configured interval
    public static final long STARTING_INTERVAL = 5000; // 5 seconds

    // Local host name(s) to announce
    private StringList m_names;

    // Domain to announce to
    private String m_domain;

    // Server comment string
    private String m_comment;

    // Announcement interval in minutes
    private int m_interval;

    // Server type flags
    private int m_srvtype = ServerType.WorkStation + ServerType.Server;

    // SMB mailslot packet
    private SMBMailslotPacket m_smbPkt;

    // Update count for the host announcement packet
    private byte m_updateCount;

    // Shutdown flag, host announcer should remove the announced name as it shuts down
    private boolean m_shutdown = false;

    // Debug output enable
    private boolean m_debug;

    /**
     * HostAnnouncer constructor.
     */
    public HostAnnouncer() {

        // Common constructor
        commonConstructor();
    }

    /**
     * Create a host announcer.
     *
     * @param name   Host name to announce
     * @param domain Domain name to announce to
     * @param intval Announcement interval, in minutes
     */
    public HostAnnouncer(String name, String domain, int intval) {

        // Common constructor
        commonConstructor();

        // Add the host to the list of names to announce
        addHostName(name);
        setDomain(domain);
        setInterval(intval);
    }

    /**
     * Common constructor code
     */
    private final void commonConstructor() {

        // Allocate the host name list
        m_names = new StringList();
    }

    /**
     * Return the server comment string.
     *
     * @return String
     */
    public final String getComment() {
        return m_comment;
    }

    /**
     * Return the domain name that the host announcement is directed to.
     *
     * @return String
     */
    public final String getDomain() {
        return m_domain;
    }

    /**
     * Return the number of names being announced
     *
     * @return int
     */
    public final int numberOfNames() {
        return m_names.numberOfStrings();
    }

    /**
     * Return the specified host name being announced.
     *
     * @param idx int
     * @return String
     */
    public final String getHostName(int idx) {
        return m_names.getStringAt(idx);
    }

    /**
     * Return the announcement interval, in minutes.
     *
     * @return int
     */
    public final int getInterval() {
        return m_interval;
    }

    /**
     * Return the server type flags.
     *
     * @return int
     */
    public final int getServerType() {
        return m_srvtype;
    }

    /**
     * Determine if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() {
        return m_debug;
    }

    /**
     * Enable/disable debug output
     *
     * @param dbg true or false
     */
    public final void setDebug(boolean dbg) {
        m_debug = dbg;
    }

    /**
     * Initialize the host announcement SMB.
     *
     * @param name String
     */
    protected final void initHostAnnounceSMB(String name) {

        // Allocate the transact SMB
        if (m_smbPkt == null)
            m_smbPkt = new SMBMailslotPacket();

        // Create the host announcement structure
        byte[] data = new byte[256];
        int pos = MailSlot.createHostAnnouncement(data, 0, name, m_comment, m_srvtype, m_interval, m_updateCount++);

        // Create the mailslot SMB
        m_smbPkt.initializeMailslotSMB(TransactionNames.MailslotBrowse, data, pos);
    }

    /**
     * Start the host announcer
     */
    public final void startAnnouncer() {

        // Initialize the host announcer
        try {

            // Initialize the host announcer datagram socket
            initialize();
        }
        catch (Exception ex) {
            if (Debug.EnableError && hasDebug())
                Debug.println("HostAnnouncer: " + ex.toString());
            return;
        }

        // Start the host announcer thread
        start();
    }

    /**
     * Start the host announcer thread.
     */
    public void run() {

        // Clear the shutdown flag
        m_shutdown = false;

        // Send the host announcement datagram
        long sleepTime = STARTING_INTERVAL;
        long sleepNormal = getInterval() * 60 * 1000;

        while (m_shutdown == false) {

            try {

                // Check if the network connection is valid
                if (isNetworkEnabled()) {

                    // Loop through the host names to be announced
                    for (int i = 0; i < m_names.numberOfStrings(); i++) {

                        // Create a host announcement transact SMB
                        String hostName = getHostName(i);
                        initHostAnnounceSMB(hostName);

                        // Send the host announce datagram
                        sendAnnouncement(hostName, m_smbPkt.getBuffer(), 0, m_smbPkt.getLength());

                        // DEBUG
                        if (Debug.EnableError && hasDebug())
                            Debug.println("HostAnnouncer: Announced host " + hostName);
                    }
                } else {

                    // Reset the sleep interval to the starting interval as the network connection
                    // is not available
                    sleepTime = STARTING_INTERVAL;
                }

                // Sleep for a while
                sleep(sleepTime);

                // Update the sleep interval, if the network connection is enabled
                if (isNetworkEnabled() && sleepTime < sleepNormal) {

                    // Double the sleep interval until it exceeds the configured announcement
                    // interval.
                    // This is to send out more broadcasts when the server first starts.
                    sleepTime *= 2;
                    if (sleepTime > sleepNormal)
                        sleepTime = sleepNormal;
                }
            }
            catch (Exception ex) {
                if (Debug.EnableError && m_shutdown == false && hasDebug())
                    Debug.println("HostAnnouncer: " + ex.toString());
                m_shutdown = true;
            }
        }

        // Set the announcement interval to zero to indicate that the host is leaving Network
        // Neighborhood
        setInterval(0);

        // Clear the server flag in the announced host type
        if ((m_srvtype & ServerType.Server) != 0)
            m_srvtype -= ServerType.Server;

        // Send out a number of host announcement to remove the host name(s) from Network
        // Neighborhood
        for (int j = 0; j < SHUTDOWN_COUNT; j++) {

            // Loop through the host names to be announced
            for (int i = 0; i < m_names.numberOfStrings(); i++) {

                // Create a host announcement transact SMB
                String hostName = getHostName(i);
                initHostAnnounceSMB(hostName);

                // Send the host announce datagram
                try {

                    // Send the host announcement
                    sendAnnouncement(hostName, m_smbPkt.getBuffer(), 0, m_smbPkt.getLength());
                }
                catch (Exception ex) {
                }
            }

            // Sleep for a while
            try {
                sleep(SHUTDOWN_WAIT);
            }
            catch (InterruptedException ex) {
            }
        }
    }

    /**
     * Initialize the host announcer.
     *
     * @throws Exception Failed to initialize the host announcer
     */
    protected abstract void initialize()
            throws Exception;

    /**
     * Determine if the network connection used for the host announcement is valid
     *
     * @return boolean
     */
    public abstract boolean isNetworkEnabled();

    /**
     * Send an announcement broadcast.
     *
     * @param hostName Host name being announced
     * @param buf      Buffer containing the host announcement mailslot message.
     * @param offset   Offset to the start of the host announcement message.
     * @param len      Host announcement message length.
     * @exception Exception Failed to send the host announcement
     */
    protected abstract void sendAnnouncement(String hostName, byte[] buf, int offset, int len)
            throws Exception;

    /**
     * Set the server comment string.
     *
     * @param comment String
     */
    public final void setComment(String comment) {
        m_comment = comment;
        if (m_comment != null && m_comment.length() > 80)
            m_comment = m_comment.substring(0, 80);
    }

    /**
     * Set the domain name that the host announcement are directed to.
     *
     * @param name String
     */
    public final void setDomain(String name) {
        m_domain = name.toUpperCase();
    }

    /**
     * Add a host name to the list of names to announce
     *
     * @param name String
     */
    public final void addHostName(String name) {
        m_names.addString(NetBIOSName.toUpperCaseName(name));
    }

    /**
     * Add a list of names to the announcement list
     *
     * @param names StringList
     */
    public final void addHostNames(StringList names) {

        // Add the names, check for duplicates
        for (int i = 0; i < names.numberOfStrings(); i++) {

            // Get the current host name
            String name = names.getStringAt(i);

            // Check if the name exists in the announcement list, if not then add to the list
            if (m_names.containsString(name) == false)
                m_names.addString(name);
        }
    }

    /**
     * Set the announcement interval, in minutes.
     *
     * @param intval int
     */
    public final void setInterval(int intval) {
        m_interval = intval;
    }

    /**
     * Set the server type flags.
     *
     * @param typ int
     */
    public final void setServerType(int typ) {
        m_srvtype = typ;
    }

    /**
     * Shutdown the host announcer and remove the announced name from Network Neighborhood.
     */
    public final synchronized void shutdownAnnouncer() {

        // Set the shutdown flag and wakeup the main host announcer thread
        m_shutdown = true;
        interrupt();

        try {
            join(2000);
        }
        catch (InterruptedException ex) {
        }
    }
}
