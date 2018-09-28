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

package org.filesys.smb.server.win32;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.filesys.debug.Debug;
import org.filesys.netbios.win32.Win32NetBIOS;
import org.filesys.server.config.ServerConfiguration;
import org.filesys.smb.mailslot.HostAnnouncer;
import org.filesys.smb.mailslot.win32.Win32NetBIOSHostAnnouncer;
import org.filesys.smb.server.SMBConfigSection;
import org.filesys.smb.server.SMBServer;

/**
 * Win32 NetBIOS LANA Monitor Class
 *
 * <p>
 * Monitors the available NetBIOS LANAs to check for new network interfaces coming online. A session
 * socket handler will be created for new LANAs as they appear.
 *
 * @author gkspencer
 */
public class Win32NetBIOSLanaMonitor extends Thread {

    // Constants
    //
    // Initial LANA listener array size
    private static final int LanaListenerArraySize = 16;

    // Global LANA monitor
    private static Win32NetBIOSLanaMonitor _lanaMonitor;

    // Available LANA list and current status
    private BitSet m_lanas;

    private BitSet m_lanaSts;

    // LANA status listeners
    private LanaListener[] m_listeners;

    // List of host announcers
    private List<HostAnnouncer> m_hostAnnouncers;

    // SMB/CIFS server to add new session handlers to
    private SMBServer m_server;

    // Shutdown request flag
    private boolean m_shutdown;

    // Debug output enable
    private boolean m_debug;

    /**
     * Class constructor
     *
     * @param server SMBServer
     * @param lanas  List of Integer
     * @param wakeup long
     * @param debug  boolean
     */
    public Win32NetBIOSLanaMonitor(SMBServer server, List<Integer> lanas, long wakeup, boolean debug) {

        // Set the SMB server and wakeup interval
        m_server = server;
        m_debug = debug;

        // Set the current LANAs in the available LANAs list
        m_lanas = new BitSet();
        m_lanaSts = new BitSet();

        m_hostAnnouncers = new ArrayList<HostAnnouncer>();

        if (lanas != null) {

            // Set the currently available LANAs
            for (int i = 0; i < lanas.size(); i++)
                m_lanas.set(lanas.get( i));
        }

        // Initialize the online LANA status list
        List<Integer> curLanas = Win32NetBIOS.LanaEnumerate();

        if (curLanas != null) {
            for (int i = 0; i < curLanas.size(); i++)
                m_lanaSts.set(curLanas.get( i), true);
        }

        // Set the global LANA monitor, if not already set
        if (_lanaMonitor == null)
            _lanaMonitor = this;

        // Start the LANA monitor thread
        setDaemon(true);
        start();
    }

    /**
     * Return the global LANA monitor
     *
     * @return Win32NetBIOSLanaMonitor
     */
    public static Win32NetBIOSLanaMonitor getLanaMonitor() {
        return _lanaMonitor;
    }

    /**
     * Add a LANA listener
     *
     * @param lana int
     * @param l    LanaListener
     */
    public synchronized final void addLanaListener(int lana, LanaListener l) {

        // Range check the LANA id
        if (lana < 0 || lana > 255)
            return;

        // Check if the listener array has been allocated
        if (m_listeners == null) {
            int len = LanaListenerArraySize;
            if (lana >= len)
                len = (lana + 4) & 0x00FC;

            m_listeners = new LanaListener[len];
        }
        else if (lana >= m_listeners.length) {

            // Extend the LANA listener array
            LanaListener[] newArray = new LanaListener[(lana + 4) & 0x00FC];

            // Copy the existing array to the extended array
            System.arraycopy(m_listeners, 0, newArray, 0, m_listeners.length);
            m_listeners = newArray;
        }

        // Add the LANA listener
        m_listeners[lana] = l;

        // DEBUG
        if (Debug.EnableInfo && hasDebug())
            Debug.println("[SMB] Win32 NetBIOS register listener for LANA " + lana);
    }

    /**
     * Remove a LANA listener
     *
     * @param lana int
     */
    public synchronized final void removeLanaListener(int lana) {

        // Validate the LANA id
        if (m_listeners == null || lana < 0 || lana >= m_listeners.length)
            return;

        m_listeners[lana] = null;
    }

    /**
     * Thread method
     */
    public void run() {

        // Clear the shutdown flag
        m_shutdown = false;

        // Access the CIFS configuration section
        ServerConfiguration config = m_server.getConfiguration();
        SMBConfigSection cifsConfig = (SMBConfigSection) config.getConfigSection(SMBConfigSection.SectionName);

        // Loop until shutdown
        BitSet curLanas = new BitSet();

        while (m_shutdown == false) {

            // Wait for a network address change event
            Win32NetBIOS.waitForNetworkAddressChange();

            // Check if the monitor has been closed
            if (m_shutdown == true)
                continue;

            // Clear the current active LANA bit set
            curLanas.clear();

            // Get the available LANA list
            List<Integer> lanas = Win32NetBIOS.LanaEnumerate();

            if (lanas != null) {

                // Check if there are any new LANAs available
                Win32NetBIOSSessionSocketHandler sessHandler = null;

                for (int i = 0; i < lanas.size(); i++) {

                    // Get the current LANA id, check if it's a known LANA
                    int lana = lanas.get( i);
                    curLanas.set(lana, true);

                    if (m_lanas.get(lana) == false) {

                        // DEBUG
                        if (Debug.EnableInfo && hasDebug())
                            Debug.println("[SMB] Win32 NetBIOS found new LANA, " + lana);

                        // Create a single Win32 NetBIOS session handler using the specified LANA
                        sessHandler = new Win32NetBIOSSessionSocketHandler(m_server, lana, hasDebug());

                        try {
                            sessHandler.initializeSessionHandler(m_server);
                        }
                        catch (Exception ex) {

                            // DEBUG
                            if (Debug.EnableError && hasDebug()) {
                                Debug.println("[SMB] Win32 NetBIOS failed to create session handler for LANA " + lana);
                                Debug.println(ex);
                            }

                            // Clear the session handler
                            sessHandler = null;
                        }

                        // If the session handler was initialized successfully add it to the
                        // SMB/CIFS server
                        if (sessHandler != null) {

                            // Run the NetBIOS session handler in a seperate thread
                            Thread nbThread = new Thread(sessHandler);
                            nbThread.setName("Win32NB_Handler_" + lana);
                            nbThread.start();

                            // DEBUG
                            if (Debug.EnableInfo && hasDebug())
                                Debug.println("[SMB] Win32 NetBIOS created session handler on LANA " + lana);

                            // Check if a host announcer should be enabled
                            if (cifsConfig.hasWin32EnableAnnouncer()) {

                                // Create a host announcer
                                Win32NetBIOSHostAnnouncer hostAnnouncer = new Win32NetBIOSHostAnnouncer(sessHandler, cifsConfig
                                        .getDomainName(), cifsConfig.getWin32HostAnnounceInterval());

                                // Add the host announcer to the SMB/CIFS server list
                                addHostAnnouncer(hostAnnouncer);
                                hostAnnouncer.start();

                                // DEBUG
                                if (Debug.EnableInfo && hasDebug())
                                    Debug.println("[SMB] Win32 NetBIOS host announcer enabled on LANA " + lana);
                            }

                            // Set the LANA in the available LANA list, and set the current status
                            // to online
                            m_lanas.set(lana);
                            m_lanaSts.set(lana, true);
                        }
                    }
                    else {

                        // Check if the LANA has just come back online
                        if (m_lanaSts.get(lana) == false) {

                            // Change the LANA status to indicate the LANA is back online
                            m_lanaSts.set(lana, true);

                            // Inform the listener that the LANA is back online
                            if (m_listeners != null && lana < m_listeners.length && m_listeners[lana] != null)
                                m_listeners[lana].lanaStatusChange(lana, true);

                            // DEBUG
                            if (Debug.EnableError && hasDebug())
                                Debug.println("[SMB] Win32 NetBIOS LANA online - " + lana);
                        }
                    }
                }

                // Check if there are any LANAs that have gone offline
                for (int i = 0; i < m_lanaSts.length(); i++) {

                    if (curLanas.get(i) == false && m_lanaSts.get(i) == true) {

                        // DEBUG
                        if (Debug.EnableError && hasDebug())
                            Debug.println("[SMB] Win32 NetBIOS LANA offline - " + i);

                        // Change the LANA status
                        m_lanaSts.set(i, false);

                        // Check if there is an associated listener for the LANA
                        if (m_listeners != null && m_listeners[i] != null) {

                            // Notify the LANA listener that the LANA is now offline
                            m_listeners[i].lanaStatusChange(i, false);
                        }
                    }
                }
            }
            else if (m_lanaSts.length() == 0) {

                // No network devices, sleep for a while as waitForNetworkAddressChange() does not
                // wait if there are no network devices
                try {
                    Thread.sleep(10000);
                }
                catch (InterruptedException e) {
                }
            }
        }
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
     * Add a host announcer
     *
     * @param hostAnnouncer HostAnnouncer
     */
    protected void addHostAnnouncer(final HostAnnouncer hostAnnouncer) {
        m_hostAnnouncers.add(hostAnnouncer);
    }

    /**
     * Shutdown the host announcers
     */
    protected final void shutdownHostAnnouncers() {
        if (m_hostAnnouncers.size() > 0) {

            for (int idx = 0; idx < m_hostAnnouncers.size(); idx++) {

                // Get the current host announcer
                HostAnnouncer hostAnnouncer = (HostAnnouncer) m_hostAnnouncers.get(idx);
                hostAnnouncer.shutdownAnnouncer();

                // DEBUG
                if (Debug.EnableInfo && hasDebug())
                    Debug.println("[NetBIOS] Shutting down host announcer " + hostAnnouncer.getName());
            }
        }
    }

    /**
     * Request the LANA monitor thread to shutdown
     */
    public final void shutdownRequest() {

        m_shutdown = true;

        // Interrupt the LANA monitor thread
        this.interrupt();

        // Cancel any outstanding wait for network address change
        Win32NetBIOS.cancelWaitForNetworkAddressChange();

        // Clear the global LANA monitor, if this is the global monitor
        if (this == _lanaMonitor)
            _lanaMonitor = null;

        shutdownHostAnnouncers();
    }
}
