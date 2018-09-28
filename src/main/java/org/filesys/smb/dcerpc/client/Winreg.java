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
 * Windows Registry Operation Ids Class
 * 
 * <p>Contains constants for requests to the remote registry DCE/RPC service on a remote server.
 * 
 * @author gkspencer
 */
public class Winreg {

	//	Winreg opcodes
	public static final int RegOpenHKR			= 0x00;	//	HKEY_ROOT
	public static final int RegOpenHKCU			= 0x01;	//	HKEY_CURRENT_USER
	public static final int RegOpenHKLM			= 0x02;	//	HKEY_LOCAL_MACHINE
	public static final int RegOpenHKPD			= 0x03;	//	HKEY_PERFORMANCE_DATA ?
	public static final int RegOpenHKU			= 0x04;	//	HKEY_USERS
	public static final int RegClose			= 0x05;
	public static final int RegCreateKey		= 0x06;
	public static final int RegDeleteKey		= 0x07;
	public static final int RegDeleteValue		= 0x08;
	public static final int RegEnumKey			= 0x09;
	public static final int RegEnumValue		= 0x0A;
	public static final int RegFlushKey			= 0x0B;
	public static final int RegGetKeySecurity	= 0x0C;
	public static final int RegOpenKey			= 0x0F;
	public static final int RegQueryInfoKey		= 0x10;
	public static final int RegQueryValue		= 0x11;
	public static final int RegSetKeySecurity	= 0x15;
	public static final int RegCreateValue		= 0x16;
	public static final int RegShutdown			= 0x18;
	public static final int RegShutdownAbort	= 0x19;
	public static final int RegGetVersion		= 0x9999;
	
	//	Root key ids
	public static final int HKEYLocalMachine	= 0;
	public static final int HKEYClassesRoot		= 1;
	public static final int HKEYCurrentUser		= 2;
	public static final int HKEYUsers			= 3;
	public static final int HKEYPerformanceData	= 4;
	public static final int HKEYCurrentConfig	= 5;
	public static final int HKEYDynData			= 6;

	//	Root key long names
	private static final String[] _rootLongNames = { "HKEY_LOCAL_MACHINE",
	    											 "HKEY_CLASSES_ROOT",
	    											 "HKEY_CURRENT_USER",
	    											 "HKEY_USERS",
	    											 "HKEY_PERFORMANCE_DATA",
	    											 "HKEY_CURRENT_CONFIG",
	    											 "HKEY_DYN_DATA"
	};
	
	//	Root key short names
	private static final String[] _rootShortNames = { "HKLM", "HKCR", "HKCU", "HKU", "HKPD", "HKCC", "HKDD" };
	
	/**
	 * Return a root key id as a long name
	 * 
	 * @param id int
	 * @return String
	 */
	public final static String getRootIdAsLongName(int id) {
	  
	  //	Validate the id
	  if ( id < 0 || id >= _rootLongNames.length)
	    return null;
	  return _rootLongNames[id];
	}

	/**
	 * Return a root key id as a short name
	 * 
	 * @param id int
	 * @return String
	 */
	public final static String getRootIdAsShortName(int id) {
	  
	  //	Validate the id
	  if ( id < 0 || id >= _rootShortNames.length)
	    return null;
	  return _rootShortNames[id];
	}
}
