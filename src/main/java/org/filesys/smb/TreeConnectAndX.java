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

package org.filesys.smb;

/**
 * Tree Connect AndX Class
 * 
 * @author gkspencer
 *
 */
public class TreeConnectAndX {

	// TreeConnectAndX request flags
	public static final int DisconnectTID		= 0x01;
	public static final int ExtendedSignature	= 0x04;
	public static final int ExtendedResponse	= 0x08;
	
	// TreeConnectAndX response support flags
	public static final int SearchBits			= 0x01;
	public static final int DFSShare			= 0x02;
	public static final int CSCMask				= 0x0C;
	public static final int UniqueFileName		= 0x10;
	public static final int HasExtendedSignature= 0x20;

	/**
	 * Check if the extended response flag is set
	 *
	 * @param flags int
	 * @return boolean
	 */
	public static boolean hasExtendedResponse(int flags) {
		if ((flags & ExtendedResponse) != 0)
			return true;
		return false;
	}

	/**
	 * Check if the extended signature flag is set
	 *
	 * @param flags int
	 * @return boolean
	 */
	public static boolean hasExtendedSignature(int flags) {
		if ((flags & ExtendedSignature) != 0)
			return true;
		return false;
	}

	/**
	 * Check if the disconnect TID flag is set
	 *
	 * @param flags int
	 * @return boolean
	 */
	public static boolean hasDisconnectTID(int flags) {
		if ((flags & DisconnectTID) != 0)
			return true;
		return false;
	}

	/**
	 * Return the TreeConnectAndX request flags value as a string
	 *
	 * @param reqFlags int
	 * @return String
	 */
	public static String asStringRequest(int reqFlags) {
		StringBuilder flagStr = new StringBuilder();

		if ((reqFlags & DisconnectTID) != 0)
			flagStr.append("DisconnectTID,");
		if ((reqFlags & ExtendedSignature) != 0)
			flagStr.append("ExtSignature,");
		if ((reqFlags & ExtendedResponse) != 0)
			flagStr.append("ExtResponse");

		return flagStr.toString();
	}
}
