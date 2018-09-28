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

package org.filesys.smb.server;

import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.TransactBuffer;
import org.filesys.util.DataBuffer;
import org.filesys.util.DataPacker;

/**
 * Transact Buffer Class
 *
 * <p>Contains the parameters and data for a transaction, transaction2 or NT transaction request.
 *
 * @author gkspencer
 */
class SrvTransactBuffer extends TransactBuffer {

    /**
     * Class constructor
     *
     * @param slen int
     * @param plen int
     * @param dlen int
     */
    public SrvTransactBuffer(int slen, int plen, int dlen) {
        super(slen, plen, dlen);
    }

    /**
     * Class constructor
     *
     * @param parser SMBV1Parser
     */
    public SrvTransactBuffer( SMBV1Parser parser) {

        // Call the base constructor so that it does not allocate any buffers
        super(0, 0, 0);

        // Set the tree id
        setTreeId(parser.getTreeId());

        // Set the setup block and size
        int slen = parser.getSetupCount() * 2;
        if (slen > 0)
            m_setupBuf = new DataBuffer(parser.getBuffer(), parser.getSetupOffset(), slen);

        // Set the parameter block and size
        int plen = parser.getTotalParameterCount();
        if (plen > 0)
            m_paramBuf = new DataBuffer(parser.getBuffer(), parser.getParameterBlockOffset(), plen);

        // Set the data block and size
        int dlen = parser.getDataBlockCount();
        if (dlen > 0)
            m_dataBuf = new DataBuffer(parser.getBuffer(), parser.getDataBlockOffset(), dlen);

        // Set the transaction type and sub-function
        setType(parser.getCommand());
        setFunction(parser.getNTFunction());

        // Set the maximum parameter and data block lengths to be returned
        setReturnParameterLimit(parser.getMaximumParameterReturn());
        setReturnDataLimit(parser.getMaximumDataReturn());

        // Set the Unicode flag
        setUnicode(parser.isUnicode());

        // Indicate that this is a not a multi-packet transaction
        m_multi = false;
    }

    /**
     * Class constructor
     *
     * <p>Construct a TransactBuffer using the maximum size settings from the specified transaction
     * buffer
     *
     * @param tbuf SrvTransactBuffer
     */
    public SrvTransactBuffer(SrvTransactBuffer tbuf) {
        super(tbuf.getReturnSetupLimit(), tbuf.getReturnParameterLimit(), tbuf.getReturnDataLimit());

        // Save the return limits for this transaction buffer
        setReturnLimits(tbuf.getReturnSetupLimit(), tbuf.getReturnParameterLimit(), tbuf.getReturnDataLimit());

        // Set the transaction reply type
        setType(tbuf.isType());

        // Copy the tree id
        setTreeId(tbuf.getTreeId());
    }

    /**
     * Class constructor
     *
     * @param tpkt SMBSrvTransPacket
     */
    public SrvTransactBuffer(SMBSrvTransPacket tpkt) {

        // Call the base constructor so that it does not allocate any buffers
        super(0, 0, 0);

        // Get the associated parser
        SMBV1Parser parser = (SMBV1Parser) tpkt.getParser();

        // Set the tree id
        setTreeId(parser.getTreeId());

        // Set the setup block and size
        int slen = tpkt.getSetupCount() * 2;
        if (slen > 0)
            m_setupBuf = new DataBuffer(tpkt.getBuffer(), tpkt.getSetupOffset(), slen);

        // Set the parameter block and size
        int plen = tpkt.getTotalParameterCount();
        if (plen > 0)
            m_paramBuf = new DataBuffer(tpkt.getBuffer(), tpkt.getRxParameterBlock(), plen);

        // Set the data block and size
        int dlen = tpkt.getRxDataBlockLength();
        if (dlen > 0)
            m_dataBuf = new DataBuffer(tpkt.getBuffer(), tpkt.getRxDataBlock(), dlen);

        // Set the transaction type and sub-function
        setType(parser.getCommand());

        if (tpkt.getSetupCount() > 0)
            setFunction(tpkt.getSetupParameter(0));

        // Set the Unicode flag
        setUnicode(parser.isUnicode());

        // Get the transaction name, if used
        if (isType() == PacketTypeV1.Transaction) {

            // Unpack the transaction name string
            int pos = parser.getByteOffset();
            byte[] buf = tpkt.getBuffer();

            if (isUnicode())
                pos = DataPacker.wordAlign(pos);

            setName(DataPacker.getString(buf, pos, 64, isUnicode()));
        }
        else
            setName("");

        // Indicate that this is a not a multi-packet transaction
        m_multi = false;
    }
}
