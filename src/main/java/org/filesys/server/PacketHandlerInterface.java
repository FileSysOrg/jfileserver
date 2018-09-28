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

package org.filesys.server;

import java.io.IOException;

/**
 * Packet Handler Interface
 *
 * <p>Implemented by classes that read/write request packets to a network connection.
 *
 * @author gkspencer
 */
public interface PacketHandlerInterface {

    /**
     * Return the protocol name
     *
     * @return String
     */
    public String getProtocolName();

    /**
     * Return the number of bytes available for reading without blocking
     *
     * @return int
     * @exception IOException Socket error
     */
    public int availableBytes()
            throws IOException;

    /**
     * Read a packet of data
     *
     * @param pkt    byte[]
     * @param offset int
     * @param maxLen int
     * @return int
     * @exception IOException Socket error
     */
    public int readPacket(byte[] pkt, int offset, int maxLen)
            throws IOException;

    /**
     * Write a packet of data
     *
     * @param pkt    byte[]
     * @param offset int
     * @param len    int
     * @exception IOException Socket error
     */
    public void writePacket(byte[] pkt, int offset, int len)
            throws IOException;

    /**
     * Close the packet handler
     */
    public void closePacketHandler();
}
