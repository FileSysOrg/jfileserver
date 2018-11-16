/*
 * Copyright (C) 2018 GK Spencer
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
package org.filesys.netbios.server;

/**
 * NetBIOS LANA Mapper Interface
 *
 * <p>For converting IP address or network adapter name into a NetBIOS LANA id</p>
 */
public interface LANAMapper {

    /**
     * Find the adapter name for a LANA
     *
     * @param lana int
     * @return String
     */
    public String getAdapterNameForLANA(int lana);

    /**
     * Find the TCP/IP address for a LANA
     *
     * @param lana int
     * @return String
     */
    public String getIPAddressForLANA(int lana);

    /**
     * Find the LANA for a network adapter
     *
     * @param name String
     * @return int
     */
    public int getLANAForAdapterName(String name);

    /**
     * Find the LANA for a TCP/IP address
     *
     * @param addr String
     * @return int
     */
    public int getLANAForIPAddress(String addr);
}
