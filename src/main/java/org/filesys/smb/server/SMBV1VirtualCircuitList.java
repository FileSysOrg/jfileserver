/*
 * Copyright (C) 2019 GK Spencer
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
package org.filesys.smb.server;

import org.filesys.debug.Debug;
import org.filesys.server.SrvSession;
import org.filesys.server.auth.ClientInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * SMB v1 Virtual Circuit List Class
 *
 * <p>Contains a list of virtual circuits that belong to a session, implementing SMB v1 limits.
 *
 * @author gkspencer
 */
public class SMBV1VirtualCircuitList implements VirtualCircuitList {

    //  Default and maximum number of virtual circuits
    public static final int DefaultCircuits     = 4;
    public static final int DefMaxCircuits      = 16;

    public static final int MinCircuits         = 4;
    public static final int MaxCircuits         = 2000;
    public static final int InitialCircuits     = 16;

    //  UIDs are 16bit values
    private static final int UIDMask            = 0x0000FFFF;

    // Active virtual circuits
    private Map<Integer, VirtualCircuit> m_vcircuits;
    private int m_UID = 1;

    // Maximum allowed virtual circuits
    private int m_maxVC = MaxCircuits;

    /**
     * Default constructor
     */
    public SMBV1VirtualCircuitList() {

        // Save the maxmimum virtual circuits value
        m_maxVC = MaxCircuits;

        // Allocate the virtual circuit table
        m_vcircuits = new HashMap<Integer, VirtualCircuit>( InitialCircuits);
    }


    /**
     * Class constructor
     *
     * @param maxVC int
     */
    public SMBV1VirtualCircuitList(int maxVC) {

        // Save the maxmimum virtual circuits value
        if ( maxVC > 0)
            m_maxVC = maxVC;
        else
            m_maxVC = MaxCircuits;

        // Allocate the virtual circuit table
        m_vcircuits = new HashMap<Integer, VirtualCircuit>(getMaximumVirtualCircuits());
    }

    /**
     * Create a virtual circuit object
     *
     * @param vcNum int
     * @param client ClientInfo
     */
    public VirtualCircuit createVirtualCircuit(int vcNum, ClientInfo client) {
        return new VirtualCircuit( vcNum, client);
    }

    /**
     * Return the maximum virtual circuits allowed
     *
     * @return int
     */
    public final int getMaximumVirtualCircuits() {
        return m_maxVC;
    }

    /**
     * Add a new virtual circuit to this session. Return the allocated UID for the new
     * circuit.
     *
     * @param vcircuit VirtualCircuit
     * @return int   Allocated UID.
     */
    public synchronized int addCircuit(VirtualCircuit vcircuit) {

        //  Check if the circuit table has been allocated
        if (m_vcircuits == null)
            m_vcircuits = new HashMap<Integer, VirtualCircuit>(DefaultCircuits);

        //  Allocate an id for the tree connection
        int uid = 0;

        //  Check if the virtual circuit table is full
        if (m_vcircuits.size() == getMaximumVirtualCircuits())
            return VirtualCircuit.InvalidID;

        //  Find a free slot in the circuit table
        uid = (m_UID++ & UIDMask);

        while (m_vcircuits.containsKey(uid)) {

            //  Try another user id for the new virtual circuit
            uid = (m_UID++ & UIDMask);
        }

        //  Store the new virtual circuit
        vcircuit.setId(uid);
        m_vcircuits.put(uid, vcircuit);

        //  Return the allocated UID
        return uid;
    }


    /**
     * Return the virtual circuit details for the specified UID.
     *
     * @param uid int
     * @return VirtualCircuit
     */
    public synchronized final VirtualCircuit findCircuit(int uid) {

        //  Check if the circuit table is valid
        if (m_vcircuits == null)
            return null;

        //  Get the required tree connection details
        return m_vcircuits.get(uid);
    }

    /**
     * Remove the specified virtual circuit from the active circuit list.
     *
     * @param uid  int
     * @param sess SrvSession
     */
    public synchronized void removeCircuit(int uid, SrvSession sess) {

        //  Check if the circuit table is valid
        if (m_vcircuits == null)
            return;

        //  Close the circuit and remove from the circuit table
        VirtualCircuit vc = m_vcircuits.get(uid);

        //  Close the virtual circuit, release resources
        if (vc != null) {

            //  Close the circuit
            vc.closeCircuit(sess);

            //  Remove the circuit from the circuit table
            m_vcircuits.remove(uid);
        }
    }

    /**
     * Return the active tree connection count
     *
     * @return int
     */
    public synchronized final int getCircuitCount() {
        return m_vcircuits != null ? m_vcircuits.size() : 0;
    }

    /**
     * Clear the virtual circuit list
     *
     * @param sess SMBSrvSession
     */
    public synchronized final void clearCircuitList(SMBSrvSession sess) {
        if (m_vcircuits != null) {

            // Enumerate the virtual circuits and close all circuits
            for (VirtualCircuit vc : m_vcircuits.values()) {

                if (!sess.isShutdown()) {

                    // Set the session client information from the virtual circuit
                    sess.setClientInformation(vc.getClientInformation());

                    // Setup any authentication context
                    sess.getSMBServer().getSMBAuthenticator().setCurrentUser(vc.getClientInformation());
                }

                // DEBUG
                if (Debug.EnableInfo && sess.hasDebug(SMBSrvSession.Dbg.STATE))
                    sess.debugPrintln("  Cleanup vc=" + vc);

                vc.closeCircuit(sess);
            }

            // Clear the virtual circuit list
            m_vcircuits.clear();
        }
    }

    /**
     * Return the virtual circuit list details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[VCs=");
        str.append(getCircuitCount());
        str.append("/");
        str.append(getMaximumVirtualCircuits());
        str.append("]");

        return str.toString();
    }
}
