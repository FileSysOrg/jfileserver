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
 * Security Accounts Manager Ids Class
 * 
 * <p>Contains constants for requests to the SAMR DCE/RPC service on a remote server.
 * 
 * @author gkspencer
 */
public class Samr {

	//	Samr opcodes
	public static final int SamrCloseHandle			= 0x01;
	public static final int SamrLookupDomain		= 0x05;
	public static final int SamrEnumDomains			= 0x06;
	public static final int SamrOpenDomain			= 0x07;
	public static final int SamrEnumGroups			= 0x0B;
	public static final int SamrEnumUsers			= 0x0D;
	public static final int SamrEnumAliases			= 0x0F;
	public static final int SamrGetAliasMembership	= 0x10;
	public static final int SamrLookupNames			= 0x11;
	public static final int SamrLookupRIDs			= 0x12;
	public static final int SamrOpenUser			= 0x22;
	public static final int SamrQueryUserInfo		= 0x24;
	public static final int SamrGetGroupsForUser	= 0x27;
	public static final int SamrConnect2			= 0x39;
	public static final int SamrConnect5			= 0x40;

	/**
	 * Convert an opcode to a function name
	 * 
	 * @param opCode int
	 * @return String
	 */
	public final static String getOpcodeName(int opCode) {
	  
		String ret = "";
		switch ( opCode) {
			case SamrCloseHandle:
				ret = "SamrCloseHandle";
				break;
			case SamrLookupDomain:
				ret = "SamrLookupDomain";
				break;
			case SamrEnumDomains:
				ret = "SamrEnumDomains";
				break;
			case SamrOpenDomain:
				ret = "SamrOpenDomain";
				break;
			case SamrEnumUsers:
			  ret = "SamrEnumUsers";
			  break;
			case SamrEnumAliases:
			  ret = "SamrEnumAliases";
			  break;
			case SamrLookupNames:
				ret = "SamrLookupNames";
				break;
			case SamrLookupRIDs:
				ret = "SamrLookupRIDs";
				break;
			case SamrOpenUser:
				ret = "SamrOpenUser";
				break;
			case SamrQueryUserInfo:
				ret = "SamrQueryUserInfo";
				break;
			case SamrConnect2:
				ret = "SamrConnect2";
				break;
			case SamrConnect5:
				ret = "SamrConnect5";
				break;
		}
		return ret;
	}
}
