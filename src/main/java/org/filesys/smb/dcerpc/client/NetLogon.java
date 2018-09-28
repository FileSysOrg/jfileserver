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
 * NetLogon Operation Ids Class
 *
 * <p>Contains constants for requests to the netlogon DCE/RPC service on a remote server.
 * 
 * @author gkspencer
 */
public class NetLogon {

	//	NetLogon opcodes
	public static final int NetrSamLogon				= 0x02;
	public static final int NetrSamLogoff				= 0x03;
	public static final int NetrServerRequestChallenge	= 0x04;
	public static final int NetrServerAuthenticate		= 0x05;
	public static final int NetrServerPasswordSet		= 0x06;
	public static final int NetrServerAuthenticate2		= 0x0F;
	
	/**
	 * Convert an opcode to a function name
	 * 
	 * @param opCode int
	 * @return String
	 */
	public final static String getOpcodeName(int opCode) {
	  
		String ret = "";
		switch ( opCode) {
			case NetrSamLogon:
				ret = "NetrSamLogon";
				break;
			case NetrSamLogoff:
				ret = "NetrSamLogoff";
				break;
			case NetrServerRequestChallenge:
				ret = "NetrServerRequestChallenge";
				break;
			case NetrServerAuthenticate:
				ret = "NetrServerAuthenticate";
				break;
			case NetrServerPasswordSet:
				ret = "NetrServerPasswordSet";
				break;
			case NetrServerAuthenticate2:
				ret = "NetrServerAuthenticate2";
				break;
		}
		
		return ret;
	}
}
