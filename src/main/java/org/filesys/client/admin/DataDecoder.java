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

package org.filesys.client.admin;

import java.util.*;

import org.filesys.util.DataPacker;

/**
 * SMB data decoder class
 * 
 * @author gkspencer
 */
final class DataDecoder {

	/**
	 * Decode a block of data according to the data descriptor string and return a vector of the
	 * values converted to Java object types.
	 * 
	 * @param buf Byte array containing the data to be unpacked
	 * @param off Offset within the buffer that the data begins
	 * @param desc Data descriptor string
	 * @param objs Vector to store the decoded objects into
	 * @param conv Converter used to locate strings within a data block.
	 * @return Offset within the data buffer at the end of this data block
	 */
	protected static final int DecodeData(byte[] buf, int off, String desc, Vector objs, int conv) {

		// Scan the data descriptor string and convert each data item in the data
		// block

		int bufpos = off;
		int pos = 0;

		while (pos < desc.length()) {

			// Get the current data item type

			char dtype = desc.charAt(pos++);
			int dlen = 1;

			// Check if a data length has been specified

			if ( pos < desc.length() && Character.isDigit(desc.charAt(pos))) {

				// Convert the data length string

				int numlen = 1;
				int numpos = pos + 1;
				while (numpos < desc.length() && Character.isDigit(desc.charAt(numpos++)))
					numlen++;

				// Set the data length

				dlen = Integer.parseInt(desc.substring(pos, pos + numlen));

				// Update the descriptor string position

				pos = numpos - 1;
			}

			// Convert the current data item

			switch (dtype) {

				// Word (16 bit) data type

				case 'W':

					// Unpack words from the data block

					int sval;

					while (dlen-- > 0) {

						// Unpack the current word value

						sval = DataPacker.getIntelShort(buf, bufpos);
						objs.addElement(new Short((short) sval));

						// Update the buffer pointer

						bufpos += 2;
					}
					break;

				// Integer (32 bit) data type

				case 'D':

					// Unpack integer values from the data block

					int ival;

					while (dlen-- > 0) {

						// Unpack the current integer value

						ival = DataPacker.getIntelInt(buf, bufpos);
						objs.addElement(new Integer(ival));

						// Update the buffer pointer

						bufpos += 4;
					}
					break;

				// Byte data type

				case 'B':

					// For a single byte return a Byte else return the bytes as a String
					// object.

					if ( dlen == 1) {
						objs.addElement(new Byte(buf[bufpos++]));
					}
					else {
						int endlen = 0;

						while (endlen < dlen && buf[bufpos + endlen] != 0x00)
							endlen++;
						String strval = new String(buf, bufpos, endlen);
						objs.addElement(strval);
						bufpos += dlen;
					}
					break;

				// Null terminated string data type

				case 'z':

					// Find the end of the null terminated string

					short spos = (short) DataPacker.getIntelInt(buf, bufpos);
					spos -= (short) conv;

					if ( spos < buf.length && spos > 0) {

						int endpos = spos;
						while (buf[endpos] != 0)
							endpos++;

						// Add a string to the data vector

						String str = new String(buf, spos, endpos - spos);
						objs.addElement(str);
					}
					else
						objs.addElement("");

					// Add 32-bit value size to buffer position

					bufpos += 4;
					break;

				// Skip 'n' bytes in the buffer

				case '.':
					bufpos += dlen;
					break;

				// Integer (32 bit) data type converted to a date/time value

				case 'T':

					// Unpack integer values from the data block

					int dval;

					while (dlen-- > 0) {

						// Unpack the current integer value

						dval = DataPacker.getIntelInt(buf, bufpos);

						// Build a Date object using the integer value as seconds since the
						// base date/time of 1-Jan-1970 00:00

						Date datetime = new Date((long) dval * 1000);
						objs.addElement(datetime);

						// Update the buffer pointer

						bufpos += 4;
					}
					break;

			} // end switch data type

		} // end while descriptor string

		// Return the new buffer offset

		return bufpos;
	}
}