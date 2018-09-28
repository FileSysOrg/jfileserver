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
 * Service Control Operation Ids Class
 * 
 * <p>Contains constants for requests to the service control manager DCE/RPC service on a remote server.
 * 
 * @author gkspencer
 */
public class Svcctl {

	//	Svcctl opcodes
	public static final int Close					= 0x00;
	public static final int ControlService			= 0x01;
	public static final int DeleteService			= 0x02;
	public static final int LockServiceDatabase     = 0x03;
	public static final int QueryObjectSecurity     = 0x04;
	public static final int SetObjectSecurity       = 0x05;
	public static final int QueryServiceStatus	    = 0x06;
	public static final int SetServiceStatus        = 0x07;
	public static final int UnlockSeviceDatabase    = 0x08;
	public static final int NotifyBootConfigStatus  = 0x09;
	public static final int SCSetServiceBits        = 0x0A;
	public static final int ChangeServiceConfig     = 0x0B;
	public static final int CreateService			= 0x0C;
	public static final int EnumDependentServices   = 0x0D;
	public static final int EnumServiceStatus		= 0x0E;
	public static final int OpenSCManager			= 0x0F;
	public static final int OpenService				= 0x10;
	public static final int QueryServiceConfig	    = 0x11;
	public static final int QuesyServiceLockStatus  = 0x12;
	public static final int StartService			= 0x13;
	public static final int GetServiceDisplayName   = 0x14;
	public static final int GetServiceKeyName       = 0x15;
	
	public static final int OpenSCManagerA			= 0x1B;
	public static final int OpenServiceA			= 0x1C;
	
	public static final int EnumServiceStatusEx     = 0x2A;
	
	// Service Control Manager access modes
	public static final int ScManagerAllAccess      = 0x0F003F;
	public static final int ScManagerCreateService  = 0x000002;
	public static final int ScManagerConnect        = 0x000001;
	public static final int ScManagerEnumerateSvc   = 0x000004;
	public static final int ScManagerLock           = 0x000008;
	public static final int ScManagerBootConfig     = 0x000020;
	public static final int ScManagerQueryLockStatus= 0x000040;
	
	// Service access modes
	public static final int ServiceQueryConfig      = 0x000001;
	public static final int ServiceChangeConfig     = 0x000002;
	public static final int ServiceQueryStatus      = 0x000004;
	public static final int ServiceEnumDependents   = 0x000008;
	public static final int ServiceStart            = 0x000010;
	public static final int ServiceStop             = 0x000020;
	public static final int ServicePauseContinue    = 0x000040;
	public static final int ServiceInterrogate      = 0x000080;
	public static final int ServiceUserDefinedCtrl  = 0x000100;
	
	public static final int ServiceAllAccess        = 0x0F01FF;
}
