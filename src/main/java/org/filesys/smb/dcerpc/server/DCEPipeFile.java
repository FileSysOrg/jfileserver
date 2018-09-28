/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.smb.dcerpc.server;

import java.io.IOException;

import org.filesys.server.filesys.NetworkFile;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEPipeType;

/**
 * DCE/RPC Pipe File Class
 *
 * <p>Contains the details and state of a DCE/RPC special named pipe.
 *
 * @author gkspencer
 */
public class DCEPipeFile extends NetworkFile {

    //	Maximum receive/transmit DCE fragment size
    private int m_maxRxFragSize;
    private int m_maxTxFragSize;

    //	Named pipe state flags
    private int m_state;

    //	DCE/RPC handler for this named pipe
    private DCEHandler m_handler;

    //	Current DCE buffered data
    private DCEBuffer m_dceData;

    /**
     * Class constructor
     *
     * @param pipeType DCEPipeType
     */
    public DCEPipeFile(DCEPipeType pipeType) {
        super(pipeType.intValue());
        setName(DCEPipeType.getTypeAsString(pipeType));

        //	Set the DCE/RPC request handler for the pipe
        setRequestHandler(DCEPipeHandler.getHandlerForType(pipeType));
    }

    /**
     * Return the maximum receive fragment size
     *
     * @return int
     */
    public final int getMaxReceiveFragmentSize() {
        return m_maxRxFragSize;
    }

    /**
     * Return the maximum transmit fragment size
     *
     * @return int
     */
    public final int getMaxTransmitFragmentSize() {
        return m_maxTxFragSize;
    }

    /**
     * Return the named pipe state
     *
     * @return int
     */
    public final int getPipeState() {
        return m_state;
    }

    /**
     * Return the pipe type id
     *
     * @return DCEPipeType
     */
    public final DCEPipeType getPipeId() {
        return DCEPipeType.fromInt(getFileId());
    }

    /**
     * Determine if the pipe has a request handler
     *
     * @return boolean
     */
    public final boolean hasRequestHandler() {
        return m_handler != null ? true : false;
    }

    /**
     * Return the pipes DCE/RPC handler
     *
     * @return DCEHandler
     */
    public final DCEHandler getRequestHandler() {
        return m_handler;
    }

    /**
     * Determine if the pipe has any buffered data
     *
     * @return boolean
     */
    public final boolean hasBufferedData() {
        return m_dceData != null ? true : false;
    }

    /**
     * Get the buffered data for the pipe
     *
     * @return DCEBuffer
     */
    public final DCEBuffer getBufferedData() {
        return m_dceData;
    }

    /**
     * Set buffered data for the pipe
     *
     * @param buf DCEBuffer
     */
    public final void setBufferedData(DCEBuffer buf) {
        m_dceData = buf;
    }

    /**
     * Set the maximum receive fragment size
     *
     * @param siz int
     */
    public final void setMaxReceiveFragmentSize(int siz) {
        m_maxRxFragSize = siz;
    }

    /**
     * Set the maximum transmit fragment size
     *
     * @param siz int
     */
    public final void setMaxTransmitFragmentSize(int siz) {
        m_maxTxFragSize = siz;
    }

    /**
     * Set the named pipe state flags
     *
     * @param state int
     */
    public final void setPipeState(int state) {
        m_state = state;
    }

    /**
     * Set the pipes DCE/RPC handler
     *
     * @param handler DCEHandler
     */
    public final void setRequestHandler(DCEHandler handler) {
        m_handler = handler;
    }

    /**
     * Dump the file details
     */
    public final void DumpFile() {
        System.out.println("** DCE/RPC Named Pipe: " + getName());
        System.out.println("  File ID : " + getFileId());
        System.out.println("  State   : 0x" + Integer.toHexString(getPipeState()));
        System.out.println("  Max Rx  : " + getMaxReceiveFragmentSize());
        System.out.println("  Max Tx  : " + getMaxTransmitFragmentSize());
        System.out.println("  Handler : " + getRequestHandler());
    }

    /**
     * Close the file
     *
     * @throws IOException Failed to close the pipe file
     */
    public void closeFile() throws IOException {
    }

    /**
     * Open the file
     *
     * @param createFlag boolean
     * @throws IOException Failed to open the pipe file
     */
    public void openFile(boolean createFlag) throws IOException {
    }

    /**
     * Read from the file
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @return int
     * @throws IOException Failed to read from the pipe file
     */
    public int readFile(byte[] buf, int len, int pos, long fileOff) throws IOException {
        return 0;
    }

    /**
     * Flush any buffered output to the file
     *
     * @throws IOException Failed to flush the pipe file
     */
    public void flushFile()
            throws IOException {
    }

    /**
     * Move the file pointer
     *
     * @param pos long
     * @param typ int
     * @return long
     * @throws IOException Failed to set the pipe file position
     */
    public long seekFile(long pos, int typ) throws IOException {
        return 0;
    }

    /**
     * Truncate, or extend, the file to the specified size
     *
     * @param siz long
     * @throws IOException Failed to truncate the pipe file
     */
    public void truncateFile(long siz) throws IOException {
    }

    /**
     * Write to the file
     *
     * @param buf     byte[]
     * @param len     int
     * @param pos     int
     * @param fileOff long
     * @throws IOException Failed to write to the pipe file
     */
    public void writeFile(byte[] buf, int len, int pos, long fileOff) throws IOException {
    }
}
