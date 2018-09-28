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
 * Eventlog Operation Ids Class
 * 
 * <p>Contains constants for requests to the event log DCE/RPC service on a remote server.
 * 
 * @author gkspencer
 */
public class Eventlog {
  
	//	Eventlog opcodes
	public static final int OpenEventLog			= 0x07;
	public static final int GetNumberOfRecords		= 0x04;
	public static final int GetOldestEventRecord	= 0x05;
	public static final int ReadEventLog			= 0x0A;
	public static final int CloseEventLog			= 0x02;
  
	//	Event log names
	public static final String EVTLOG_SYSTEM			= "System";
	public static final String EVTLOG_SECURITY			= "Security";
	public static final String EVTLOG_APPLICATION		= "Application";
	public static final String EVTLOG_DIRECTORYSERVICE 	= "Directory Service";
	public static final String EVTLOG_DNSSERVER			= "DNS Server";
	public static final String EVTLOG_FILEREPLSERVICE	= "File Replication Service";
  
	//	Event log read flags
	public static final int SequentialRead				= 0x0001;
	public static final int SeekRead					= 0x0002;
	public static final int ForwardsRead				= 0x0004;
	public static final int BackwardsRead				= 0x0008;
}
