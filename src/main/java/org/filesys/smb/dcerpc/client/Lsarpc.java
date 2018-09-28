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

package org.filesys.smb.dcerpc.client;

/**
 * LSA RPC Operation Ids
 * 
 * <p>Contains constants for requests to the LSA DCE/RPC service on a remote server.
 *
 * @author gkspencer
 */
public class Lsarpc {

	//	Lsarpc opcodes
	public static final int CloseRequest		= 0x00;
	public static final int QueryInfoPolicy		= 0x07;
	public static final int OpenPolicy2			= 0x2C;
	public static final int LookupNames			= 0x0E;
	public static final int LookupSIDs			= 0x0F;

	/**
	 * Convert an opcode to a function name
	 * 
	 * @param opCode int
	 * @return String
	 */
	public final static String getOpcodeName(int opCode) {

		String ret = "";
		switch (opCode) {
			case CloseRequest:
				ret = "CloseRequest";
				break;
			case QueryInfoPolicy:
				ret = "QueryInfoPolicy";
				break;
			case OpenPolicy2:
				ret = "OpenPolicy2";
				break;
			case LookupNames:
				ret = "LookupNames";
				break;
			case LookupSIDs:
				ret = "LookupSIDs";
				break;
		}

		return ret;
	}
}
