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
 * Registry Data Types Class
 * 
 * <p>Defines constants for the different data types that may be read/written to/from a remote registry.
 * 
 * @author gkspencer
 */
public class RegistryType {

	//	Registry data types
	public final static int REG_SZ					= 1;
	public final static int REG_EXPAND_SZ			= 2;
	public final static int REG_BINARY				= 3;
	public final static int REG_DWORD				= 4;
	public final static int REG_DWORD_BIGENDIAN		= 5;
	public final static int REG_LINK				= 6;
	public final static int REG_MULTI_SZ			= 7;
	public final static int REG_RSCLIST				= 8;
	public final static int REG_FULL_RSCDESC		= 9;
	public final static int REG_RSCREQLIST			= 10;

	//	Type names
	private static final String[] _typeNames = {	"",
													"REG_SZ",
													"REG_EXPAND_SZ",
													"REG_BINARY",
													"REG_DWORD",
													"REG_DWORD_BIGENDIAN",
													"REG_LINK",
													"REG_MULTI_SZ",
													"REG_RSCLIST",
													"REG_FULL_RSCDESC",
													"REG_RSCREQLIST"
	};
	
	/**
	 * Return a registry value type as a string
	 * 
	 * @param typ int
	 * @return String
	 */
	public final static String getTypeAsString(int typ) {
		
		//	Range check the value
		if ( typ < 0 || typ >= _typeNames.length)
			return "<Unknown>";
			
		//	Return the registry value type
		return _typeNames[typ];
	}
}
