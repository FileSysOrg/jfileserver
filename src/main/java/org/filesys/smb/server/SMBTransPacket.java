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

package org.filesys.smb.server;

import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.smb.PacketTypeV1;
import org.filesys.util.DataPacker;

/**
 * SMB transact packet class
 *
 * @author gkspencer
 */
public class SMBTransPacket extends SMBSrvPacket {

    // Define the number of standard parameters
    protected static final int STD_PARAMS = 14;

    // Transaction status that indicates that this transaction has more data
    // to be returned.
    public static final int IsContinued = 234;

    // Transact name, not used for transact 2
    protected String m_transName;

    // Parameter count for this transaction
    protected int m_paramCnt;

    // Multiplex identifier, to identify each transaction request
    private static int m_nextMID = 1;

    /**
     * Construct an SMB transaction packet
     *
     * @param buf Buffer that contains the SMB transaction packet.
     */
    public SMBTransPacket(byte[] buf) {
        super(buf);

        setParser( Version.V1);
    }

    /**
     * Construct from the specified SMB parser
     *
     * @param parser SMBV1Parser
     */
    public SMBTransPacket( SMBV1Parser parser) {
        super( parser.getBuffer());

        m_parser = parser;
    }

    /**
     * Construct an SMB transaction packet
     *
     * @param siz Size of packet to allocate.
     */
    public SMBTransPacket(int siz) {
        super(siz);

        setParser( Version.V1);

        //  Set the multiplex id for this transaction
        getV1Parser().setMultiplexId(getNextMultiplexId());
    }

    /**
     * Return the SMB V1 parser
     *
     * @return SMBV1Parser
     */
    public final SMBV1Parser getV1Parser() {
        return (SMBV1Parser) m_parser;
    }

    /**
     * Get the next multiplex id to uniquely identify this transaction
     *
     * @return Unique multiplex id for this transaction
     */
    public final static int getNextMultiplexId() {
        return m_nextMID++;
    }

    /**
     * Return the total parameter byte count
     *
     * @return int
     */
    public final int getTotalParameterCount() {
        return getV1Parser().getParameter(0);
    }

    /**
     * Return the total data byte count
     *
     * @return int
     */
    public final int getTotalDataCount() {
        return getV1Parser().getParameter(1);
    }

    /**
     * Return the parameter count size in bytes for this section
     *
     * @return int
     */
    public final int getParameterBlockCount() {
        return getV1Parser().getParameter(9);
    }

    /**
     * Return the parameter block offset
     *
     * @return Paramter block offset within the SMB packet
     */
    public final int getParameterBlockOffset() {
        return getV1Parser().getParameter(10) + RFCNetBIOSProtocol.HEADER_LEN;
    }

    /**
     * Return the data block size in bytes for this section
     *
     * @return int
     */
    public final int getDataBlockCount() {
        return getV1Parser().getParameter(11);
    }

    /**
     * Return the data block offset
     *
     * @return Data block offset within the SMB packet.
     */
    public final int getDataBlockOffset() {
        return getV1Parser().getParameter(12) + RFCNetBIOSProtocol.HEADER_LEN;
    }

    /**
     * Return the secondary parameter block size in bytes
     *
     * @return int
     */
    public final int getSecondaryParameterBlockCount() {
        return getV1Parser().getParameter(2);
    }

    /**
     * Return the secondary parameter block offset
     *
     * @return int
     */
    public final int getSecondaryParameterBlockOffset() {
        return getV1Parser().getParameter(3) + RFCNetBIOSProtocol.HEADER_LEN;
    }

    /**
     * Return the secondary parameter block displacement
     *
     * @return int
     */
    public final int getParameterBlockDisplacement() {
        return getV1Parser().getParameter(4);
    }

    /**
     * Return the secondary data block size in bytes
     *
     * @return int
     */
    public final int getSecondaryDataBlockCount() {
        return getV1Parser().getParameter(5);
    }

    /**
     * Return the secondary data block offset
     *
     * @return int
     */
    public final int getSecondaryDataBlockOffset() {
        return getV1Parser().getParameter(6) + RFCNetBIOSProtocol.HEADER_LEN;
    }

    /**
     * Return the secondary data block displacement
     *
     * @return int
     */
    public final int getDataBlockDisplacement() {
        return getV1Parser().getParameter(7);
    }

    /**
     * Return the transaction sub-command
     *
     * @return int
     */
    public final int getSubFunction() {
        return getV1Parser().getParameter(14);
    }

    /**
     * Unpack the parameter block into the supplied array.
     *
     * @param prmblk Array to unpack the parameter block words into.
     * @exception ArrayIndexOutOfBoundsException Parameter block too short
     */
    public final void getParameterBlock(short[] prmblk)
            throws ArrayIndexOutOfBoundsException {

        //  Determine how many parameters are to be unpacked, check if the user
        //  buffer is long enough
        int prmcnt = getV1Parser().getParameter(3) / 2; // convert to number of words
        if (prmblk.length < prmcnt)
            throw new ArrayIndexOutOfBoundsException();

        //  Get the offset to the parameter words, add the NetBIOS header length
        //  to the offset.
        int pos = getV1Parser().getParameter(4) + RFCNetBIOSProtocol.HEADER_LEN;

        //  Unpack the parameter words
        byte[] buf = getBuffer();

        for (int idx = 0; idx < prmcnt; idx++) {

            //  Unpack the current parameter word
            prmblk[idx] = (short) DataPacker.getIntelShort(buf, pos);
            pos += 2;
        }
    }

    /**
     * Initialize the transact SMB packet
     *
     * @param pcnt     Total parameter count for this transaction
     * @param paramblk Parameter block data bytes
     * @param plen     Parameter block data length
     * @param datablk  Data block data bytes
     * @param dlen     Data block data length
     */
    public final void InitializeTransact(int pcnt, byte[] paramblk, int plen, byte[] datablk, int dlen) {

        // Get the associated parser
        SMBV1Parser parser = getV1Parser();

        //  Set the SMB command code
        if (m_transName == null)
            parser.setCommand(PacketTypeV1.Transaction2);
        else
            parser.setCommand(PacketTypeV1.Transaction);

        //  Set the parameter count
        parser.setParameterCount(pcnt);

        //  Save the parameter count, add an extra parameter for the data byte count
        m_paramCnt = pcnt;

        //  Initialize the parameters
        parser.setParameter(0, plen); //  total parameter bytes being sent
        parser.setParameter(1, dlen); //  total data bytes being sent

        for (int i = 2; i < 9; parser.setParameter(i++, 0));

        parser.setParameter(9, plen);     //  parameter bytes sent in this packet
        parser.setParameter(11, dlen);    //  data bytes sent in this packet

        parser.setParameter(13, pcnt - STD_PARAMS);    //  number of setup words

        //  Get the data byte offset
        int pos = parser.getByteOffset();
        int startPos = pos;

        //  Check if this is a named transaction, if so then store the name
        int idx;
        byte[] buf = getBuffer();

        if (m_transName != null) {

            //  Store the transaction name
            byte[] nam = m_transName.getBytes();

            for (idx = 0; idx < nam.length; idx++)
                buf[pos++] = nam[idx];
        }

        //  Word align the buffer offset
        if ((pos % 2) > 0)
            pos++;

        //  Store the parameter block
        if (paramblk != null) {

            //  Set the parameter block offset
            parser.setParameter(10, pos - RFCNetBIOSProtocol.HEADER_LEN);

            //  Store the parameter block
            for (idx = 0; idx < plen; idx++)
                buf[pos++] = paramblk[idx];
        }
        else {

            //  Clear the parameter block offset
            parser.setParameter(10, 0);
        }

        //  Word align the data block
        if ((pos % 2) > 0)
            pos++;

        //  Store the data block
        if (datablk != null) {

            //  Set the data block offset
            parser.setParameter(12, pos - RFCNetBIOSProtocol.HEADER_LEN);

            //  Store the data block
            for (idx = 0; idx < dlen; idx++)
                buf[pos++] = datablk[idx];
        }
        else {

            //  Zero the data block offset
            parser.setParameter(12, 0);
        }

        //  Set the byte count for the SMB packet
        parser.setByteCount(pos - startPos);
    }

    /**
     * Set the specifiec setup parameter within the SMB packet.
     *
     * @param idx Setup parameter index.
     * @param val Setup parameter value.
     */
    public final void setSetupParameter(int idx, int val) {
        getV1Parser().setParameter(STD_PARAMS + idx, val);
    }

    /**
     * Set the transaction name for normal transactions
     *
     * @param tname Transaction name string
     */
    public final void setTransactionName(String tname) {
        m_transName = tname;
    }
}
