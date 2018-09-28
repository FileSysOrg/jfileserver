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

package org.filesys.netbios.win32;

/**
 * Winsock2 Class
 * 
 * @author gkspencer
 */
public class Winsock2 {

	// Event select bit values
	public static final int FD_READ			= 0x0001;
	public static final int FD_WRITE  		= 0x0002;
	public static final int FD_OOB    		= 0x0004;
	public static final int FD_ACCEPT 		= 0x0008;
	public static final int FD_CONNECT		= 0x0010;
	public static final int FD_CLOSE  		= 0x0020;
	public static final int FD_QOS    		= 0x0040;
	public static final int FD_GROUPQOS		= 0x0080;
	public static final int FD_ROUTECHANGE	= 0x0100;
	public static final int FD_ADDRCHANGE 	= 0x0200;
	
	// Infinite timeout for WinsockWaitForMultipleEvents
	public static final int WSA_INFINITE	= 0xFFFFFFFF;
}
