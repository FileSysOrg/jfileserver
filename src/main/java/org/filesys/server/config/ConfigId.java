/*
 * Copyright (C) 2006-2015 Alfresco Software Limited.
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

package org.filesys.server.config;

/**
 * Server Configuration Variable Id Class
 * 
 * <p>
 * Contains the unique id and group id for configuration variables for the various Java File Server
 * components.
 * 
 * @author gkspencer
 */
public class ConfigId {

	// Configuration group ids
	public static final int GroupServer 	= 0x00010000;
	public static final int GroupSMB 		= 0x00020000;
	public static final int GroupFTP 		= 0x00030000;
	public static final int GroupNFS 		= 0x00040000;
	public static final int GroupNetBIOS 	= 0x00050000;
	public static final int GroupShares 	= 0x00060000;
	public static final int GroupSecurity 	= 0x00070000;
	public static final int GroupUsers 		= 0x00080000;
	public static final int GroupDebug 		= 0x00090000;
	public static final int GroupConfig 	= 0x10000000;

	// Server configuration variables
	public static final int ServerSMBEnable = GroupServer + 1;
	public static final int ServerFTPEnable = GroupServer + 2;
	public static final int ServerNFSEnable = GroupServer + 3;
	public static final int ServerTimezone 	= GroupServer + 4;
	public static final int ServerTZOffset 	= GroupServer + 5;

	// SMB server variables
	public static final int SMBHostName 		= GroupSMB + 1;
	public static final int SMBAliasNames 		= GroupSMB + 2;
	public static final int SMBServerType 		= GroupSMB + 3;
	public static final int SMBComment 			= GroupSMB + 4;
	public static final int SMBDomain 			= GroupSMB + 5;
	public static final int SMBBroadcastMask 	= GroupSMB + 6;
	public static final int SMBAnnceEnable 		= GroupSMB + 7;
	public static final int SMBAnnceInterval 	= GroupSMB + 8;
	public static final int SMBDialects 		= GroupSMB + 9;
	public static final int SMBTCPPort 			= GroupSMB + 10;
	public static final int SMBNetBIOSEnable 	= GroupSMB + 11;
	public static final int SMBTCPEnable 		= GroupSMB + 12;
	public static final int SMBBindAddress 		= GroupSMB + 13;
	public static final int SMBMacExtEnable 	= GroupSMB + 14;
	public static final int SMBSessionDebug 	= GroupSMB + 15;
	public static final int SMBDebugEnable 		= GroupSMB + 16;
	public static final int SMBAnnceDebug 		= GroupSMB + 17;
	public static final int SMBWin32NetBIOS 	= GroupSMB + 18;
	public static final int SMBWin32NBName 		= GroupSMB + 19;
	public static final int SMBWin32NBAccept 	= GroupSMB + 20;
	public static final int SMBWin32NBAnnounce 	= GroupSMB + 21;
	public static final int SMBWin32NBLana 		= GroupSMB + 22;
	public static final int SMBAnncePort 		= GroupSMB + 23;
	public static final int SMBMappedDrives 	= GroupSMB + 24;
	public static final int SMBWin32NBWinsock 	= GroupSMB + 25;
	public static final int SMBAuthenticator 	= GroupSMB + 26;
	public static final int SMBDisableNIO		= GroupSMB + 27;
	public static final int SMBSocketTimeout	= GroupSMB + 28;
	public static final int SMBMaxVirtualCircuit= GroupSMB + 29;
    public static final int SMBLoadBalancerList = GroupSMB + 30;
    public static final int SMBTerminalServerList= GroupSMB + 31;
    public static final int SMBDisableEncryption = GroupSMB + 32;
    public static final int SMBRequireSigning	= GroupSMB + 33;
    public static final int SMBSocketKeepAlive	= GroupSMB + 34;
	public static final int SMBPacketsPerThreadRun = GroupSMB + 35;
	public static final int SMBDisableHashedOFM = GroupSMB + 36;

	// FTP server variables
	public static final int FTPBindAddress 		= GroupFTP + 1;
	public static final int FTPPort 			= GroupFTP + 2;
	public static final int FTPAllowAnon 		= GroupFTP + 3;
	public static final int FTPAnonAccount 		= GroupFTP + 4;
	public static final int FTPDebugFlags 		= GroupFTP + 5;
	public static final int FTPDebugEnable 		= GroupFTP + 6;
	public static final int FTPRootPath 		= GroupFTP + 7;
	public static final int FTPDataPortLow 		= GroupFTP + 8;
	public static final int FTPDataPortHigh 	= GroupFTP + 9;
	public static final int FTPSiteInterface 	= GroupFTP + 10;
	public static final int FTPAuthenticator 	= GroupFTP + 11;
	public static final int FTPIPv6Enable 		= GroupFTP + 12;
	public static final int FTPKeyStore 		= GroupFTP + 13;
	public static final int FTPTrustStore 		= GroupFTP + 14;
	public static final int FTPKeyPassphrase	= GroupFTP + 15;
	public static final int FTPRequireSecure	= GroupFTP + 16;
	public static final int FTPKeyStoreType		= GroupFTP + 17;
	public static final int FTPTrustStoreType	= GroupFTP + 18;
	public static final int FTPTrustPassphrase  = GroupFTP + 19;
	public static final int FTPKeyProvider		= GroupFTP + 20;
	public static final int FTPTrustProvider	= GroupFTP + 21;
    public static final int FTPSrvSessionTimeout= GroupFTP + 22;

	// NFS server variables
	public static final int NFSPortMapEnable 	= GroupNFS + 1;
	public static final int NFSDebugFlags 		= GroupNFS + 2;
	public static final int NFSThreads 			= GroupNFS + 3;
	public static final int NFSPortMapPort 		= GroupNFS + 4;
	public static final int NFSMountPort 		= GroupNFS + 5;
	public static final int NFSServerPort 		= GroupNFS + 6;
	public static final int NFSPacketPool 		= GroupNFS + 7;
	public static final int NFSPortMapDebug 	= GroupNFS + 8;
	public static final int NFSMountDebug 		= GroupNFS + 9;
	public static final int NFSRpcAuthenticator = GroupNFS + 10;
	public static final int NFSFileCacheIOTimer = GroupNFS + 11;
	public static final int NFSFileCacheCloseTimer = GroupNFS + 12;
	public static final int NFSFileCacheDebug 	= GroupNFS + 13;
	public static final int NFSRPCRegistrationPort = GroupNFS + 14;
	public static final int NFSDisableNIO		= GroupNFS + 15;

	// NetBIOS server variables
	public static final int NetBIOSNamePort 	= GroupNetBIOS + 1;
	public static final int NetBIOSSessionPort 	= GroupNetBIOS + 2;
	public static final int NetBIOSBindAddress 	= GroupNetBIOS + 3;
	public static final int NetBIOSDebugEnable 	= GroupNetBIOS + 4;
	public static final int NetBIOSWINSPrimary 	= GroupNetBIOS + 5;
	public static final int NetBIOSWINSSecondary = GroupNetBIOS + 6;
	public static final int NetBIOSDatagramPort = GroupNetBIOS + 7;

	// Share related variables
	public static final int ShareList 		= GroupShares + 1;
	public static final int ShareMapper 	= GroupShares + 2;

	// Security related variables
	public static final int SecurityAuthenticator 	= GroupSecurity + 1;
	public static final int SecurityACLManager 		= GroupSecurity + 2;
	public static final int SecurityGlobalACLs 		= GroupSecurity + 3;
	public static final int SecurityJCEProvider 	= GroupSecurity + 4;
	public static final int SecurityUsersInterface 	= GroupSecurity + 5;

	// User related variables
	public static final int UsersList = GroupUsers + 1;

	// Debug related variables
	public static final int DebugDevice = GroupDebug + 1;

	// Configuration section added/removed
	public static final int ConfigSection = GroupConfig + 1;

	/**
	 * Extract the group id from a variable id
	 * 
	 * @param id int
	 * @return int
	 */
	public final static int getGroupId(int id) {
		return (id & 0xFFFF0000);
	}
}
