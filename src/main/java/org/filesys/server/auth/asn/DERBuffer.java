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

package org.filesys.server.auth.asn;

import java.io.IOException;
import java.util.List;

import org.filesys.debug.Debug;
import org.filesys.util.DataPacker;

/**
 * DER Buffer Class
 *
 * <p>Pack/unpack objects from a ASN.1 DER encoded blob.
 *
 * @author gkspencer
 */
public class DERBuffer {

    //  Constants
    private static final int DefaultBufferSize = 256;

    // Debug enable
    private static final boolean DebugEnable = false;

    //  Data buffer, current position and offset
    private byte[] m_data;
    private int m_pos;
    private int m_endpos;
    private int m_offset;

    /**
     * Default constructor
     */
    public DERBuffer() {
        m_data = new byte[DefaultBufferSize];
        m_pos = 0;
        m_offset = 0;
    }

    /**
     * Create a data buffer to write data to
     *
     * @param siz int
     */
    public DERBuffer(int siz) {
        m_data = new byte[siz];
        m_pos = 0;
        m_offset = 0;
    }

    /**
     * Create a data buffer to read data from
     *
     * @param buf byte[]
     * @param off int
     * @param len int
     */
    public DERBuffer(byte[] buf, int off, int len) {
        m_data = buf;
        m_offset = off;
        m_pos = off;
        m_endpos = off + len;
    }

    /**
     * Create a data buffer to read data from
     *
     * @param buf byte[]
     */
    public DERBuffer(byte[] buf) {
        m_data = buf;
        m_offset = 0;
        m_pos = 0;
        m_endpos = buf.length;
    }

    /**
     * Return the data buffer
     *
     * @return byte[]
     */
    public final byte[] getBuffer() {
        return m_data;
    }

    /**
     * Return the data length
     *
     * @return int
     */
    public final int getLength() {
        if (m_endpos != 0)
            return m_endpos - m_offset;
        return m_pos - m_offset;
    }

    /**
     * Return the data length in words
     *
     * @return int
     */
    public final int getLengthInWords() {
        return getLength() / 2;
    }

    /**
     * Return the available data length
     *
     * @return int
     */
    public final int getAvailableLength() {
        if (m_endpos == 0)
            return -1;
        return m_endpos - m_pos;
    }

    /**
     * Return the data position
     *
     * @return int
     */
    public final int getPosition() {
        return m_pos;
    }

    /**
     * Return the used buffer as a byte array
     *
     * @return byte[]
     */
    public final byte[] getBytes() {
        if (getLength() == 0)
            return null;

        byte[] byts = new byte[getLength()];
        System.arraycopy(m_data, m_offset, byts, 0, getLength());
        return byts;
    }

    /**
     * Unpack a data byte from the buffer
     *
     * @return int
     */
    public final int unpackByte() {

        //  Check if there is enough data in the buffer
        if (m_data.length - m_pos < 1)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        //  Unpack the data byte value
        return (m_data[m_pos++] & 0xFF);
    }

    /**
     * Unpack a block of bytes
     *
     * @param len int
     * @return byte[]
     */
    public final byte[] unpackBytes(int len) {

        //  Check if there is enough data in the buffer
        if (m_data.length - m_pos < len)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        // Create the return buffer and copy the data
        byte[] byts = new byte[len];
        System.arraycopy(m_data, m_pos, byts, 0, len);

        // Update the buffer position
        m_pos += len;

        // Return the bytes
        return byts;
    }

    /**
     * Peek at the next type in the buffer
     *
     * @return int
     */
    public final int peekType() {

        //  Check if there is enough data in the buffer
        if (m_data.length - m_pos < 1)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        //  Unpack the type byte value
        return (m_data[m_pos] & 0xFF);
    }

    /**
     * Unpack a data type from the buffer
     *
     * @return int
     */
    public final int unpackType() {
        return unpackByte();
    }

    /**
     * Unpack a data length value
     *
     * @return int
     */
    public final int unpackLength() {

        //  Check if there is enough data in the buffer
        if (m_data.length - m_pos < 1)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        //  Unpack the data length byte(s)
        int dlen = (m_data[m_pos++] & 0xFF);

        // Check if this is a multi-byte length
        if ((dlen & 0x80) != 0) {

            // Unpack the length bytes
            int numByts = dlen & 0x7F;
            dlen = 0;

            while (numByts-- > 0) {
                dlen = (dlen << 8) + (m_data[m_pos++] & 0xFF);
            }
        }

        // Return the length
        return dlen;
    }

    /**
     * Unpack an object from the buffer
     *
     * @return DERObject
     * @exception IOException Error unpacking the DER object
     */
    public final DERObject unpackObject()
            throws IOException {

        // Check the object type
        int objTyp = peekType();
        int tagNo = -1;

        if ((objTyp & DER.Tagged) != 0) {

            // Save the tag number
            tagNo = objTyp & 0x1F;

            // Skip the type and outer length
            unpackType();
            unpackLength();

            // Get the main object type
            objTyp = peekType();
        }

        // Create the required object type
        DERObject derObj = null;
        int objPos = getPosition();

        if ((objTyp & DER.Application) != 0) {

            // Create an application specific object
            derObj = new DERApplicationSpecific();

            // Set the position of the object data within the buffer
            if ( derObj != null)
                derObj.setBufferPosition( objPos);

            // DEBUG
            if (DebugEnable)
                Debug.println("DER unpackObject typ=Applicationspecific objTyp=0x" + Integer.toHexString(objTyp) + ", pos=" + objPos);
        }
        else {

            // DEBUG
            if (DebugEnable)
                Debug.println("DER unpackObject typ=" + DER.isTypeString(DER.isType(objTyp)) + " objTyp=0x" + Integer.toHexString(objTyp) + ", pos=" + objPos);

            // Create the appropriate object type
            switch (DER.isType(objTyp)) {
                case DER.BitString:
                    derObj = new DERBitString();
                    break;
                case DER.Boolean:
                    derObj = new DERBoolean();
                    break;
                case DER.Enumerated:
                    derObj = new DEREnumerated();
                    break;
                case DER.GeneralString:
                    derObj = new DERGeneralString();
                    break;
                case DER.Integer:
                    derObj = new DERInteger();
                    break;
                case DER.NumericString:
                    break;
                case DER.ObjectIdentifier:
                    derObj = new DEROid();
                    break;
                case DER.OctetString:
                    derObj = new DEROctetString();
                    break;
                case DER.PrintableString:
                    break;
                case DER.Sequence:
                    derObj = new DERSequence();
                    break;
                case DER.UniversalString:
                    break;
                case DER.UTF8String:
                    break;
                case DER.GeneralizedTime:
                    derObj = new DERGeneralizedTime();
                    break;
            }
        }

        // Decode the object, if valid
        if (derObj != null)
            derObj.derDecode(this);
        else
            throw new IOException("ASN.1 type 0x" + Integer.toHexString(objTyp) + " decode not supported");

        // Set the tag number, if tagged
        if (tagNo != -1)
            derObj.setTagNo(tagNo);

        // Set the position of the object data within the buffer
        derObj.setBufferPosition( objPos);

        // Return the new object
        return derObj;
    }

    /**
     * Unpack an integer value
     *
     * @param len int
     * @return int
     */
    public final int unpackInt(int len) {

        //  Check if there is enough data in the buffer
        if (m_data.length - m_pos < len)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        //  Unpack the integer value
        int ival = 0;

        for (int i = 0; i < len; i++) {
            ival <<= 8;
            ival += unpackByte();
        }

        return ival;
    }

    /**
     * Unpack a long (64 bit) value
     *
     * @param len int
     * @return long
     */
    public final long unpackLong(int len) {

        //  Check if there is enough data in the buffer
        if (m_data.length - m_pos < len)
            throw new ArrayIndexOutOfBoundsException("End of data buffer");

        //  Unpack the integer value
        int lval = 0;

        for (int i = 0; i < len; i++) {
            lval <<= 8;
            lval += unpackByte();
        }

        return lval;
    }

    /**
     * Append a byte value to the buffer
     *
     * @param bval int
     */
    public final void packByte(int bval) {

        //  Check if there is enough space in the buffer
        if (m_data.length - m_pos < 1)
            extendBuffer();

        //  Pack the byte value
        m_data[m_pos++] = (byte) (bval & 0xFF);
    }

    /**
     * Pack a byte at the specified position
     *
     * @param pos  int
     * @param bval int
     */
    public final void packByteAt(int pos, int bval) {

        //  Pack the byte value
        m_data[pos] = (byte) (bval & 0xFF);
    }

    /**
     * Pack bytes into the buffer
     *
     * @param buf byte[]
     * @param off int
     * @param len int
     */
    public final void packBytes(byte[] buf, int off, int len) {

        //  Check if there is enough space in the buffer
        if (m_data.length - m_pos < len)
            extendBuffer(len);

        //  Copy the data to the buffer and update the current write position
        System.arraycopy(buf, off, m_data, m_pos, len);
        m_pos += len;
    }

    /**
     * Pack bytes from the specified DER buffer into this buffer
     *
     * @param buf DERBuffer
     */
    public final void packBytes(DERBuffer buf) {

        //  Check if there is enough space in the buffer
        if (m_data.length - m_pos < buf.getLength())
            extendBuffer(buf.getLength());

        //  Copy the data to the buffer and update the current write position
        System.arraycopy(buf.getBuffer(), 0, m_data, m_pos, buf.getLength());
        m_pos += buf.getLength();
    }

    /**
     * Pack a data length
     *
     * @param len int
     */
    public final void packLength(int len) {

        //  Check if there is enough space in the buffer
        if (m_data.length - m_pos < 4)
            extendBuffer();

        // Check if the length will fit in a single byte
        if (len < 128) {

            // Pack a short length
            packByte(len);
        } else {

            // Calculate the number of bytes required to store the length and pack
            int sizByts = calculateLengthBytes(len);
            packByte(sizByts + 0x80);

            // Pack the length bytes
            int shift = (sizByts - 1) * 8;

            while (shift >= 0) {
                packByte(len >> shift);
                shift -= 8;
            }
        }
    }

    /**
     * Pack an integer value
     *
     * @param ival int
     */
    public final void packInt(int ival) {

        //  Check if there is enough space in the buffer
        if (m_data.length - m_pos < 4)
            extendBuffer();

        //  Pack the integer value
        DataPacker.putIntelInt(ival, m_data, m_pos);
        m_pos += 4;
    }

    /**
     * Pack a long value
     *
     * @param lval long
     */
    public final void packLong(long lval) {

        //  Check if there is enough space in the buffer
        if (m_data.length - m_pos < 8)
            extendBuffer();

        //  Pack the long value
        DataPacker.putIntelLong(lval, m_data, m_pos);
        m_pos += 8;
    }

    /**
     * Pack an object
     *
     * @param derObj DERObject
     * @exception IOException Error packing the DER object
     */
    public final void packObject(DERObject derObj)
            throws IOException {

        // If the object is tagged we need to pack to a seperate buffer first to get the object length
        if (derObj.isTagged()) {

            //  Encode the object to a seperate buffer
            DERBuffer derBuf = new DERBuffer();
            derObj.derEncode(derBuf);

            //  Pack the tagged type
            packByte(DER.Tagged + DER.Constructed + derObj.getTagNo());
            packLength(derBuf.getLength());
            packBytes(derBuf);
        } else {

            // Encode the object into this DER buffer
            derObj.derEncode(this);
        }
    }

    /**
     * Pack an application specific object
     *
     * @param derObj DERObject
     * @exception IOException Error packing the DER object
     */
    public final void packApplicationSpecific(DERObject derObj)
            throws IOException {

        // Pack the object
        packApplicationSpecific(0, derObj);
    }

    /**
     * Pack an application specific object
     *
     * @param tagId  int
     * @param derObj DERObject
     * @exception IOException Error packing the DER object
     */
    public final void packApplicationSpecific(int tagId, DERObject derObj)
            throws IOException {

        // Pack the header
        packByte(DER.Application + DER.Constructed + (tagId & 0x1F));

        // Pack the object to a seperate buffer to get the length
        DERBuffer derBuf = new DERBuffer();
        derBuf.packObject(derObj);

        // Pack the length and object
        packLength(derBuf.getLength());
        packBytes(derBuf);
    }

    /**
     * Pack an application specific list of objects
     *
     * @param derList List
     * @exception IOException Error packing the DER object
     */
    public final void packApplicationSpecific(List derList)
            throws IOException {

        // Pack the list of objects
        packApplicationSpecific(0, derList);
    }

    /**
     * Pack an application specific list of objects
     *
     * @param tagId   int
     * @param derList List
     * @exception IOException Error packing the DER object
     */
    public final void packApplicationSpecific(int tagId, List derList)
            throws IOException {

        // Pack the header
        packByte(DER.Application + DER.Constructed + (tagId & 0x1F));

        // Pack the objects to a seperate buffer to get the length
        DERBuffer derBuf = null;

        if (derList != null && derList.size() > 0) {

            // Allocate the buffer
            derBuf = new DERBuffer();

            // Pack the objects
            for (int i = 0; i < derList.size(); i++) {

                // Pack the current object
                DERObject derObj = (DERObject) derList.get(i);
                derBuf.packObject(derObj);
            }

            // Pack the length and objects
            packLength(derBuf.getLength());
            packBytes(derBuf);
        } else
            packLength(0);
    }

    /**
     * Pack an application specific object
     *
     * @param tagId int
     * @param byts  byte[]
     * @exception IOException Error packing the DER object
     */
    public final void packApplicationSpecific(int tagId, byte[] byts)
            throws IOException {

        // Pack the header
        packByte(DER.Application + DER.Constructed + (tagId & 0x1F));

        // Pack the length and object
        packLength(byts.length);
        packBytes(byts, 0, byts.length);
    }

    /**
     * Pack an application specific object
     *
     * @param byts byte[]
     * @exception IOException Error packing the DER object
     */
    public final void packApplicationSpecific(byte[] byts)
            throws IOException {

        // Pack the bytes
        packApplicationSpecific(0, byts);
    }

    /**
     * Unpack an application specific object
     *
     * @return DERObject
     * @exception IOException Error unpacking the DER object
     */
    public final DERObject unpackApplicationSpecific()
            throws IOException {

        // Get the first entry from the stream
        DERObject derObj = null;

        int typ = unpackType();
        if (DER.isApplicationSpecific(typ) || DER.isContextSpecific(typ)) {

            // Get the object length
            unpackLength();

            // Unpack the top level object, this will cause all objects to be loaded
            derObj = unpackObject();
        }
        else
            throw new IOException("Wrong DER type, expected Application or Context");

        // Return the decoded object
        return derObj;
    }

    /**
     * Unpack an application specific blob
     *
     * @return byte[]
     * @exception IOException Error unpacking the DER object
     */
    public final byte[] unpackApplicationSpecificBytes()
            throws IOException {

        // Get the first entry from the stream
        int typ = unpackType();
        byte[] objByts = null;

        if (DER.isApplicationSpecific(typ)) {

            // Get the object length
            int len = unpackLength();

            // Unpack the top level object bytes
            objByts = unpackBytes(len);
        } else
            throw new IOException("Wrong DER type, expected Application or Context");

        // Return the object bytes
        return objByts;
    }

    /**
     * Calculate the number of length bytes required for a data length
     *
     * @param len int
     * @return int
     */
    public final int calculateLengthBytes(int len) {

        // Check if the length will fit in a single byte
        int sizByts = 1;

        if (len > 0x00FF) {

            // Calculate the number of bytes required to store the length
            sizByts = 2;

            if (len > 0x00FFFFFF)
                sizByts = 4;
            else if (len > 0x0000FFFF)
                sizByts = 3;
        }

        return sizByts;
    }

    /**
     * Set the read/write buffer position
     *
     * @param pos int
     */
    public final void setPosition(int pos) {
        m_pos = pos;
    }

    /**
     * Set the end of buffer position, and reset the read position to the beginning of the buffer
     */
    public final void setEndOfBuffer() {
        m_endpos = m_pos;
        m_pos = m_offset;
    }

    /**
     * Set the data length
     *
     * @param len int
     */
    public final void setLength(int len) {
        m_pos = m_offset + len;
    }

    /**
     * Extend the data buffer by the specified amount
     *
     * @param ext int
     */
    private final void extendBuffer(int ext) {

        //  Create a new buffer of the required size
        byte[] newBuf = new byte[m_data.length + ext];

        //  Copy the data from the current buffer to the new buffer
        System.arraycopy(m_data, 0, newBuf, 0, m_data.length);

        //  Set the new buffer to be the main buffer
        m_data = newBuf;
    }

    /**
     * Extend the data buffer, double the currently allocated buffer size
     */
    private final void extendBuffer() {
        extendBuffer(m_data.length * 2);
    }
}
