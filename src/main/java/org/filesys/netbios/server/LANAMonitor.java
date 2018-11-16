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

import org.filesys.smb.server.SMBServer;

import java.util.List;

/**
 * LANA Monitor Interface
 *
 * <p>Monitors the available NetBIOS LANAs for online/offline status as network interfaces are added
 * and removed</p>
 */
public interface LANAMonitor {

    /**
     * Initialize the LANA monitor
     *
     *
     * @param server SMBServer
     * @param lanas  List of Integer
     * @param wakeup long
     * @param debug  boolean
     */
    public void initializeLANAMonitor(SMBServer server, List<Integer> lanas, long wakeup, boolean debug);

    /**
     * Request the LANA monitor to shutdown
     */
    public void shutdownRequest();
}
