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

package org.filesys.util;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.filesys.debug.DebugInterface;


/**
 * Hex dump class.
 *
 * @author gkspencer
 */
public final class HexDump {

    private static String _hexChars = "0123456789ABCDEF";

    /**
     * Hex dump a byte array
     *
     * @param byt   Byte array to dump
     */
    public static final void Dump( byte[] byt) {
        Dump( byt, byt.length, 0, System.out);
    }

    /**
     * Hex dump a byte array
     *
     * @param byt    Byte array to dump
     * @param len    Length of data to dump
     * @param offset Offset to start data dump
     */
    public static final void Dump(byte[] byt, int len, int offset) {
        Dump(byt, len, offset, System.out);
    }

    /**
     * Hex dump a byte array
     *
     * @param byt    Byte array to dump
     * @param len    Length of data to dump
     * @param offset Offset to start data dump
     * @param stream Output stream to dump the output to.
     */
    public static final void Dump(byte[] byt, int len, int offset, PrintStream stream) {

        //	Create buffers for the ASCII and Hex output
        StringBuilder ascBuf = new StringBuilder();
        StringBuilder hexBuf = new StringBuilder();

        //  Dump 16 byte blocks from the array until the length has been reached
        int dlen = 0;
        int doff = offset;
        int endoff = offset + len;
        String posStr = null;

        while (dlen < len) {

            //	Reset the ASCII/Hex buffers
            ascBuf.setLength(0);
            hexBuf.setLength(0);

            posStr = generatePositionString(doff);

            //  Dump a block of data, update the data offset
            doff = generateLine(byt, doff, ascBuf, hexBuf, endoff);

            //	Output the current record
            stream.print(posStr);
            stream.print(hexBuf.toString());
            stream.println(ascBuf.toString());

            //  Update the dump length
            dlen += 16;
        }
    }

    /**
     * Hex dump a byte array to a debug output device
     *
     * @param byt    Byte array to dump
     * @param len    Length of data to dump
     * @param offset Offset to start data dump
     * @param dbgDev Debug device for output
     */
    public static final void Dump(byte[] byt, int len, int offset, DebugInterface dbgDev) {

        //	Create buffers for the ASCII and Hex output
        StringBuilder ascBuf = new StringBuilder();
        StringBuilder hexBuf = new StringBuilder();

        //  Dump 16 byte blocks from the array until the length has been reached
        int dlen = 0;
        int doff = offset;
        int endoff = offset + len;
        String posStr = null;

        while (dlen < len) {

            //	Reset the ASCII/Hex buffers
            ascBuf.setLength(0);
            hexBuf.setLength(0);

            posStr = generatePositionString(doff);

            //  Dump a block of data, update the data offset
            doff = generateLine(byt, doff, ascBuf, hexBuf, endoff);

            //	Output the current record
            dbgDev.debugPrintln(posStr + hexBuf.toString() + ascBuf.toString());

            //  Update the dump length
            dlen += 16;
        }
    }

    /**
     * Generate a hex string for the specified string
     *
     * @param str String
     * @return String
     */
    public static final String hexString(String str) {
        if (str != null)
            return hexString(str.getBytes());
        return "";
    }

    /**
     * Generate a hex string for the specified string
     *
     * @param str String
     * @param gap String
     * @return String
     */
    public static final String hexString(String str, String gap) {
        if (str != null)
            return hexString(str.getBytes(), gap);
        return "";
    }

    /**
     * Generate a hex string for the specified bytes
     *
     * @param buf byte[]
     * @return String
     */
    public static final String hexString(byte[] buf) {
        return hexString(buf, buf.length, null);
    }

    /**
     * Generate a hex string for the specified bytes
     *
     * @param buf byte[]
     * @param gap String
     * @return String
     */
    public static final String hexString(byte[] buf, String gap) {
        return hexString(buf, buf.length, gap);
    }

    /**
     * Generate a hex string for the specified bytes
     *
     * @param buf byte[]
     * @param len int
     * @param gap String
     * @return String
     */
    public static final String hexString(byte[] buf, int len, String gap) {

        //	Check if the buffer is valid
        if (buf == null)
            return "";

        //	Create a string buffer for the hex string
        int buflen = buf.length * 2;
        if (gap != null)
            buflen += buf.length * gap.length();

        StringBuilder hex = new StringBuilder(buflen);

        //	Convert the bytes to hex-ASCII
        for (int i = 0; i < len; i++) {

            //  Get the current byte
            int curbyt = (int) (buf[i] & 0x00FF);

            //  Output the hex string
            hex.append(Integer.toHexString((curbyt & 0xF0) >> 4));
            hex.append(Integer.toHexString(curbyt & 0x0F));

            //	Add the gap string, if specified
            if (gap != null && i < (len - 1))
                hex.append(gap);
        }

        //	Return the hex-ASCII string
        return hex.toString();
    }

    /**
     * Generate a hex string for the specified bytes
     *
     * @param buf byte[]
     * @param off int
     * @param len int
     * @param gap String
     * @return String
     */
    public static final String hexString(byte[] buf, int off, int len, String gap) {

        // Check if the buffer is valid
        if (buf == null)
            return "";

        // Create a string buffer for the hex string
        int buflen = (buf.length - off) * 2;
        if (gap != null)
            buflen += buf.length * gap.length();

        StringBuilder hex = new StringBuilder(buflen);

        // Convert the bytes to hex-ASCII
        for (int i = 0; i < len; i++) {

            // Get the current byte
            int curbyt = (int) (buf[off + i] & 0x00FF);

            // Output the hex string
            hex.append(Integer.toHexString((curbyt & 0xF0) >> 4));
            hex.append(Integer.toHexString(curbyt & 0x0F));

            // Add the gap string, if specified
            if (gap != null && i < (len - 1))
                hex.append(gap);
        }

        // Return the hex-ASCII string
        return hex.toString();
    }

    /**
     * Load bytes from a Wireshark hex dump in the format :-
     *
     * 0000 00 01 02 03 04 05 06 07 08  09 0A 0B 0C 0D 0E 0F 10   ................
     *
     * @param hexStr String[]
     * @return byte[]
     * @exception BadHexFormatException Invalid hex data format
     */
    public static final byte[] loadFromHexDump( String[] hexStr)
        throws BadHexFormatException {

        // Allocate the buffer, assuming all records are full length
        byte[] byts = new byte[hexStr.length * 16];

        // Process the hex strings
        int idx = 0;

        for(String hex : hexStr) {

            // Should have at least 8 characters, header plus one byte as hex
            if ( hex.length() < 8)
                throw new BadHexFormatException();

            // Parse hex pairs
            hex = hex.toUpperCase();
            int hexIdx = 6;
            boolean lineDone = false;

            while ( hexIdx < 55 && lineDone == false) {

                // Check if there are any more hex pairs to convert, line will be space padded to the
                // byte dump area
                if ( hex.charAt( hexIdx) == ' ' && hex.charAt( hexIdx + 1) == ' ')
                    lineDone = true;
                else {

                    // Check for a pair of hex characters followed by a space
                    int hex1 = _hexChars.indexOf(hex.charAt(hexIdx));
                    int hex2 = _hexChars.indexOf(hex.charAt(hexIdx + 1));

                    if (hex1 == -1 || hex2 == -1)
                        throw new BadHexFormatException();

                    // Set the byte value
                    byts[idx++] = (byte) (((hex1 << 4) + hex2) & 0xFF);

                    // Update the string index
                    hexIdx += 3;

                    // Extra space in the middle of the hex pairs
                    if ( hex.charAt( hexIdx) == ' ')
                        hexIdx++;
                }
            }
        }

        // Truncate the byte array if we did not get a full last line
        if ( idx < byts.length)
            byts = Arrays.copyOf( byts, idx);

        return byts;
    }

    /**
     * Load bytes from a Wireshark hex dump file in the format :-
     *
     * 0000 00 01 02 03 04 05 06 07 08  09 0A 0B 0C 0D 0E 0F 10   ................
     *
     * @param hexFileName String
     * @return byte[]
     * @exception BadHexFormatException Invalid hex data format
     * @exception FileNotFoundException File not found
     * @exception IOException I/O error
     */
    public static final byte[] loadFromHexDumpFile( String hexFileName)
            throws BadHexFormatException, FileNotFoundException, IOException {

        // Open the file and setup a reader
        File hexFile = new File( hexFileName);
        FileReader reader = new FileReader( hexFile);
        BufferedReader lineReader = new BufferedReader( reader);

        // Read the hex dump lines
        List<String> lines = new ArrayList<>();
        String line;

        while(( line = lineReader.readLine()) != null)
            lines.add( line);

        // Convert the lines list to an array
        String[] hexStrs = lines.toArray(new String[0]);

        // Convert the hex strings to bytes
        return loadFromHexDump( hexStrs);
    }

    /**
     * Load bytes from a hex string
     *
     * @param hex String
     * @return byte[]
     */
    public static final byte[] loadHexString( String hex) {

        if ( hex == null || hex.length() == 0 || hex.length() % 2 == 1)
            return null;

        // Check if the string contains any whitespace
        if ( hex.indexOf(' ') != -1) {
            hex = hex.replaceAll("\\s","");
        }

        // Build the byte array from the hex pairs
        byte[] byts = new byte[hex.length() / 2];

        // Parse hex pairs
        hex = hex.toUpperCase();
        int idx = 0;
        int bytIdx = 0;

        while ( idx < hex.length()) {

            // Check for a pair of hex characters followed by a space
            int hex1 = _hexChars.indexOf(hex.charAt(idx));
            int hex2 = _hexChars.indexOf(hex.charAt(idx + 1));

            // Set the byte value
            byts[bytIdx++] = (byte) (((hex1 << 4) + hex2) & 0xFF);

            // Update the string index
            idx += 2;
        }

        return byts;
    }

    /**
     * Generate a buffer position string
     *
     * @param off int
     * @return String
     */
    private static final String generatePositionString(int off) {

        //  Create a buffer position string
        StringBuilder posStr = new StringBuilder( 8);
        posStr.append( Integer.toString( off));
        posStr.append(" - ");
        
        while (posStr.length() < 8)
            posStr.insert(0, " ");

        //	Return the string
        return posStr.toString();
    }

    /**
     * Output a single line of the hex dump to a debug device
     *
     * @param byt    Byte array to dump
     * @param off    Offset to start data dump
     * @param ascBuf Buffer for ASCII output
     * @param hexBuf Buffer for Hex output
     * @param endOff Position of the end of the used buffer
     * @return New offset value
     */
    private static final int generateLine(byte[] byt, int off, StringBuilder ascBuf, StringBuilder hexBuf, int endOff) {

        //  Check if there is enough buffer space to dump 16 bytes
        int dumplen = byt.length - off;
        if (dumplen > 16)
            dumplen = 16;

        //  Dump a 16 byte block of data
        for (int i = 0; i < dumplen; i++) {

            //  Get the current byte
            int curbyt = (int) (byt[off++] & 0x00FF);

            //  Output the hex string
            hexBuf.append(Integer.toHexString((curbyt & 0xF0) >> 4));
            hexBuf.append(Integer.toHexString(curbyt & 0x0F));
            hexBuf.append(off == endOff ? "]" : " ");

            //  Output the character equivalent, if printable
            if (Character.isLetterOrDigit((char) curbyt) || Character.getType((char) curbyt) != Character.CONTROL)
                ascBuf.append((char) curbyt);
            else
                ascBuf.append(".");
        }

        //  Output the hex dump line
        hexBuf.append("  - ");

        //  Return the new data offset
        return off;
    }
}
