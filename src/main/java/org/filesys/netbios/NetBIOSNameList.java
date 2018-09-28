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

package org.filesys.netbios;

import java.util.ArrayList;
import java.util.List;

/**
 * NetBIOS Name List Class
 *
 * <p>Contains a list of NetBIOSName objects.
 *
 * @author gkspencer
 */
public class NetBIOSNameList {

    //	List of NetBIOS names
    private List<NetBIOSName> m_nameList;

    // MAC address
    private byte[] m_mac = null;

    /**
     * Class constructor
     */
    public NetBIOSNameList() {
        m_nameList = new ArrayList<NetBIOSName>();
    }

    /**
     * Determine if the MAC address is valid
     *
     * @return boolean
     */
    public final boolean hasMACAddress() {
        return m_mac != null ? true : false;
    }

    /**
     * Return the MAC address of the remote server
     *
     * @return byte[]
     */
    public final byte[] getMACAddress() {
        return m_mac;
    }

    /**
     * Return the MAC address as a string in the format nn:nn:nn:nn:nn:nn
     *
     * @return String
     */
    public final String getMACAddressString() {
        String macAddrStr = null;

        if (hasMACAddress()) {
            StringBuffer str = new StringBuffer();

            for (int i = 0; i < m_mac.length; i++) {
                int val = m_mac[i] & 0xFF;
                if (val < 16)
                    str.append("0");
                str.append(Integer.toHexString(val));
                str.append(":");
            }

            if (str.length() > 0)
                str.setLength(str.length() - 1);

            macAddrStr = str.toString();
        }

        return macAddrStr;
    }

    /**
     * Set the MAC address of the remote server
     *
     * @param mac byte[]
     */
    public final void setMACAddress(byte[] mac) {
        m_mac = mac;
    }

    /**
     * Add a name to the list
     *
     * @param name NetBIOSName
     */
    public final void addName(NetBIOSName name) {
        m_nameList.add(name);
    }

    /**
     * Get a name from the list
     *
     * @param idx int
     * @return NetBIOSName
     */
    public final NetBIOSName getName(int idx) {
        if (idx < m_nameList.size())
            return m_nameList.get(idx);
        return null;
    }

    /**
     * Return the number of names in the list
     *
     * @return int
     */
    public final int numberOfNames() {
        return m_nameList.size();
    }

    /**
     * Find names of the specified name of different types and return a subset of the
     * available names.
     *
     * @param name String
     * @return NetBIOSNameList
     */
    public final NetBIOSNameList findNames(String name) {

        //	Allocate the sub list and search for required names
        NetBIOSNameList subList = new NetBIOSNameList();
        for (int i = 0; i < m_nameList.size(); i++) {
            NetBIOSName nbName = getName(i);
            if (nbName.getName().compareTo(name) == 0)
                subList.addName(nbName);
        }

        //	Return the sub list of names
        return subList;
    }

    /**
     * Find the first name of the specified type
     *
     * @param typ   char
     * @param group boolean
     * @return NetBIOSName
     */
    public final NetBIOSName findName(char typ, boolean group) {

        //	Search for the first name of the required type
        for (int i = 0; i < m_nameList.size(); i++) {
            NetBIOSName name = getName(i);
            if (name.getType() == typ && name.isGroupName() == group)
                return name;
        }

        //	Name type not found
        return null;
    }

    /**
     * Find the specified name and type
     *
     * @param name  String
     * @param typ   char
     * @param group boolean
     * @return NetBIOSName
     */
    public final NetBIOSName findName(String name, char typ, boolean group) {

        //	Search for the first name of the required type
        for (int i = 0; i < m_nameList.size(); i++) {
            NetBIOSName nbName = getName(i);
            if (nbName.getName().equals(name) && nbName.getType() == typ && nbName.isGroupName() == group)
                return nbName;
        }

        //	Name/type not found
        return null;
    }

    /**
     * Find names of the specified type and return a subset of the available names
     *
     * @param typ   char
     * @param group boolean
     * @return NetBIOSNameList
     */
    public final NetBIOSNameList findNames(char typ, boolean group) {

        //	Allocate the sub list and search for names of the required type
        NetBIOSNameList subList = new NetBIOSNameList();
        for (int i = 0; i < m_nameList.size(); i++) {
            NetBIOSName name = getName(i);
            if (name.getType() == typ && name.isGroupName() == group)
                subList.addName(name);
        }

        //	Return the sub list of names
        return subList;
    }

    /**
     * Remove a name from the list
     *
     * @param name NetBIOSName
     * @return NetBIOSName
     */
    public final NetBIOSName removeName(NetBIOSName name) {
        for (int i = 0; i < m_nameList.size(); i++) {
            NetBIOSName curName = getName(i);
            if (curName.equals(name)) {
                m_nameList.remove(i);
                return curName;
            }
        }
        return null;
    }

    /**
     * Delete all names from the list
     */
    public final void removeAllNames() {
        m_nameList.clear();
    }
}
