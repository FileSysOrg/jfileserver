/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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
 * SMB status code class.
 * 
 * <p>
 * The SMBStatus class contains the error class and error code values that a remote server may return.
 * 
 * @author gkspencer
 */
public final class SMBStatus {

	// Error classes
	public static final int Success 	= 0x00;
	public static final int ErrDos 		= 0x01;
	public static final int ErrSrv 		= 0x02;
	public static final int ErrHrd 		= 0x03;
	public static final int NetErr 		= 0x04;
	public static final int InternalErr	= 0x05;
	public static final int NTErr 		= 0x06;
	public static final int DCERPCErr 	= 0x07;
	public static final int Win32Err 	= 0x08;

	public static final int ErrCmd 		= 0xFF;

	// Mask for NT severity
	public static final int NT_SEVERITY_MASK 	= 0xF0000000;
	public static final int NT_ERROR_MASK 		= 0x0FFFFFFF;

	// DOS error codes.
	public static final int DOSInvalidFunc 			= 1;
	public static final int DOSFileNotFound 		= 2;
	public static final int DOSDirectoryInvalid 	= 3;
	public static final int DOSTooManyOpenFiles 	= 4;
	public static final int DOSAccessDenied 		= 5;
	public static final int DOSInvalidHandle 		= 6;
	public static final int DOSMemCtrlBlkDestoyed 	= 7;
	public static final int DOSInsufficientMem 		= 8;
	public static final int DOSInvalidAddress 		= 9;
	public static final int DOSInvalidEnv 			= 10;
	public static final int DOSInvalidFormat 		= 11;
	public static final int DOSInvalidOpenMode 		= 12;
	public static final int DOSInvalidData 			= 13;
	public static final int DOSInvalidDrive 		= 15;
	public static final int DOSDeleteSrvDir 		= 16;
	public static final int DOSNotSameDevice 		= 17;
	public static final int DOSNoMoreFiles 			= 18;
	public static final int DOSFileSharingConflict 	= 32;
	public static final int DOSLockConflict 		= 33;
	public static final int DOSFileAlreadyExists 	= 80;
	public static final int DOSUnknownInfoLevel 	= 124;
	public static final int DOSDirectoryNotEmpty 	= 145;
	public static final int DOSNotLocked 			= 158;

	// Server error codes
	public static final int SRVNonSpecificError 	= 1;
	public static final int SRVBadPassword 			= 2;
	public static final int SRVNoAccessRights 		= 4;
	public static final int SRVInvalidTID 			= 5;
	public static final int SRVInvalidNetworkName 	= 6;
	public static final int SRVInvalidDevice 		= 7;
	public static final int SRVPrintQueueFullFiles 	= 49;
	public static final int SRVPrintQueueFullSpace 	= 50;
	public static final int SRVEOFOnPrintQueueDump 	= 51;
	public static final int SRVInvalidPrintFID 		= 52;
	public static final int SRVUnrecognizedCommand 	= 64;
	public static final int SRVInternalServerError 	= 65;
	public static final int SRVFIDAndPathInvalid 	= 67;
	public static final int SRVInvalidAccessPerm 	= 69;
	public static final int SRVInvalidAttributeMode = 70;
	public static final int SRVServerPaused 		= 81;
	public static final int SRVNotReceivingMessages = 82;
	public static final int SRVNoBuffers 			= 83;
	public static final int SRVTooManyRemoteNames 	= 87;
	public static final int SRVTimedOut 			= 88;
	public static final int SRVNoResourcesAvailable = 89;
	public static final int SRVTooManyUIDs 			= 90;
	public static final int SRVInvalidUID 			= 91;
	public static final int SRVNoRAWUseMPX 			= 250;
	public static final int SRVNoRAWUseStdReadWrite = 251;
	public static final int SRVContinueInMPXMode 	= 252;
	public static final int SRVNotSupported 		= 65535;

	// Hardware error codes.
	public static final int HRDWriteProtected 		= 19;
	public static final int HRDUnknownUnit 			= 20;
	public static final int HRDDriveNotReady 		= 21;
	public static final int HRDUnknownCommand 		= 22;
	public static final int HRDDataError 			= 23;
	public static final int HRDBadRequestLength 	= 24;
	public static final int HRDSeekError 			= 25;
	public static final int HRDUnknownMediaType 	= 26;
	public static final int HRDSectorNotFound 		= 27;
	public static final int HRDPrinterOutOfPaper 	= 28;
	public static final int HRDWriteFault 			= 29;
	public static final int HRDReadFault 			= 30;
	public static final int HRDGeneralFailure 		= 31;
	public static final int HRDOpenConflict 		= 32;
	public static final int HRDLockConflict 		= 33;
	public static final int HRDWrongDiskInDrive 	= 34;
	public static final int HRDNoFCBsAvailable 		= 35;
	public static final int HRDSharingBufferOverrun = 36;

	// Network error codes
	public static final int NETAccessDenied 		= 5;
	public static final int NETInvalidHandle 		= 6;
	public static final int NETUnsupported 			= 50;
	public static final int NETNetAccessDenied 		= 65;
	public static final int NETBadDeviceType 		= 66;
	public static final int NETBadNetworkName 		= 67;
	public static final int NETAlreadyAssigned 		= 85;
	public static final int NETInvalidPassword 		= 86;
	public static final int NETInvParameter 		= 87;
	public static final int NETContinued 			= 234;
	public static final int NETNoMoreItems 			= 259;
	public static final int NETInvalidAddress 		= 487;
	public static final int NETServiceDoesNotExist 	= 1060;
	public static final int NETBadDevice 			= 1200;
	public static final int NETNoNetOrBadPath 		= 1203;
	public static final int NETExtendedError 		= 1208;
	public static final int NETNoNetwork 			= 1222;
	public static final int NETCancelled 			= 1223;
	public static final int NETSrvNotRunning 		= 2114;
	public static final int NETBufferTooSmall 		= 2123;
	public static final int NETNoTransactions 		= 2141;
	public static final int NETInvQueueName 		= 2150;
	public static final int NETNoSuchPrintJob 		= 2151;
	public static final int NETNotResponding 		= 2160;
	public static final int NETSpoolerNotStarted 	= 2161;
	public static final int NETCannotPerformOp 		= 2164;
	public static final int NETErrLoadLogonScript 	= 2212;
	public static final int NETLogonNotValidated 	= 2214;
	public static final int NETLogonSrvOldSoftware 	= 2217;
	public static final int NETUserNameNotFound 	= 2221;
	public static final int NETUserLgnWkNotAllowed 	= 2240;
	public static final int NETUserLgnTimeNotAllowed = 2241;
	public static final int NETUserPasswordExpired 	= 2242;
	public static final int NETPasswordCannotChange = 2243;
	public static final int NETPasswordTooShort 	= 2246;

	// Internal error codes
	public static final int IntUnsupportedDevice 	= 1;
	public static final int IntNoMoreSessions 		= 2;
	public static final int IntSessionNotActive 	= 3;
	public static final int IntInvalidSMBReceived 	= 4;
	public static final int IntLargeFilesNotSupported = 5;
	public static final int IntInvalidFileInfo 		= 6;
	public static final int IntDceRpcNotSupported 	= 7;
	public static final int IntInvalidSMBSignature 	= 8;
	public static final int IntNoLongErrorCode		= 9;
	public static final int IntNoShortErrorCode		= 10;

	// NT 32-bit status code
	public static final int NTSuccess = 0;

	public static final int NTNotImplemented 		= 0xC0000002;
	public static final int NTInvalidInfoClass 		= 0xC0000003;
	public static final int NTInfoLengthMismatch	= 0xC0000004;
	public static final int NTInvalidParameter 		= 0xC000000D;
	public static final int NTNoSuchFile 			= 0xC000000F;
	public static final int NTInvalidDeviceRequest 	= 0xC0000010;
	public static final int NTEndOfFile				= 0xC0000011;
	public static final int NTMoreProcessingRequired = 0xC0000016;
	public static final int NTAccessDenied 			= 0xC0000022;
	public static final int NTBufferTooSmall 		= 0xC0000023;
	public static final int NTObjectTypeMismatch 	= 0xC0000024;
	public static final int NTObjectNameInvalid 	= 0xC0000033;
	public static final int NTObjectNotFound 		= 0xC0000034;
	public static final int NTObjectNameCollision 	= 0xC0000035;
	public static final int NTObjectPathNotFound 	= 0xC000003A;
	public static final int NTDataError				= 0xC000003E;
	public static final int NTSharingViolation 		= 0xC0000043;
	public static final int NTLockConflict 			= 0xC0000054;
	public static final int NTLockNotGranted 		= 0xC0000055;
	public static final int NTLogonFailure 			= 0xC000006D;
	public static final int NTPasswordExpired 		= 0xC0000071;
	public static final int NTAccountDisabled 		= 0xC0000072;
	public static final int NTNoneMapped 			= 0xC0000073;
	public static final int NTInvalidSecDescriptor 	= 0xC0000079;
	public static final int NTRangeNotLocked 		= 0xC000007E;
	public static final int NTDiskFull 				= 0xC000007F;
	public static final int NTServerDisabled		= 0xC0000080;
	public static final int NTPipeBusy 				= 0xC00000AE;
	public static final int NTPipeDisconnected    	= 0xC00000B0;
	public static final int NTFileIsADirectory 		= 0xC00000BA;
	public static final int NTNotSupported 			= 0xC00000BB;
	public static final int NTNetworkAccessDenied	= 0xC00000CA;
	public static final int NTBadDeviceType 		= 0xC00000CB;
	public static final int NTBadNetName 			= 0xC00000CC;
	public static final int NTTooManySessions		= 0xC00000CE;
	public static final int NTRequestNotAccepted 	= 0xC00000D0;
	public static final int NTPipeEmpty           	= 0xC00000D9;
	public static final int NTNoSuchDomain 			= 0xC00000DF;
	public static final int NTDirectoryNotEmpty		= 0xC0000101;
	public static final int NTTooManyOpenFiles 		= 0xC000011F;
	public static final int NTFSDriverRequired		= 0xC000019C;
	public static final int NTCancelled 			= 0xC0000120;
	public static final int NTFileClosed		    = 0xC0000128;
	public static final int NTInvalidLevel 			= 0xC0000148;
	public static final int NTPipeBroken          	= 0xC000014B;
	public static final int NTUserSessionDeleted	= 0xC0000203;
	public static final int NTPasswordChangeReq 	= 0xC0000224;
	public static final int NTNotFound			    = 0xC0000225;
	public static final int NTAccountLocked			= 0xC0000234;
	public static final int NTFileOffline 			= 0xC0000267;
	public static final int NTVolumeNotUpgraded		= 0xC000029C;

	public static final int SMBInvalidSMB				= 0x00010002;
	
	public static final int Win32FileNotFound 			= 2;
	public static final int Win32PathNotFound 			= 3;
	public static final int Win32AccessDenied 			= 5;
	public static final int Win32InvalidHandle 			= 6;
	public static final int Win32BadDeviceType 			= 66;
	public static final int Win32BadNetworkName 		= 67;
	public static final int Win32AlreadyAssigned 		= 85;
	public static final int Win32InvalidPassword 		= 86;
	public static final int Win32MoreData 				= 234;
	public static final int Win32NoMoreItems 			= 259;
	public static final int Win32MoreEntries 			= 261;
	public static final int Win32InvalidAddress 		= 487;
	public static final int Win32ServiceDoesNotExist 	= 1060;
	public static final int Win32ServiceMarkedForDelete = 1072;
	public static final int Win32ServiceExists 			= 1073;
	public static final int Win32ServiceDuplicateName 	= 1077;
	public static final int Win32BadDevice 				= 1200;
	public static final int Win32NoNetOrBadPath 		= 1203;
	public static final int Win32ExtendedError 			= 1208;
	public static final int Win32NoNetwork 				= 1222;

	
	public static final int NTBufferOverflow 	= 0x80000005;
	public static final int NTNoMoreFiles 		= 0x80000006;
	public static final int NTNotifyEnumDir 	= 0x0000010C;
	public static final int NTPending			= 0x00000103;

	// DEC/RPC status codes
	public static final int DCERPC_Fault = 0;
}
