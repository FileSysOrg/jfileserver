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
 * Spoolss Operation Ids Class
 * 
 * <p>Contains constants for requests to the SPOOLSS/printing DCE/RPC service on a remote server.
 * 
 * @author gkspencer
 */
public class Spoolss {

	//	Spoolss opcodes
	public static final int EnumPrinters		= 0x00;
	public static final int OpenPrinter			= 0x01;
	public static final int SetJob				= 0x02;
	public static final int GetJob				= 0x03;
	public static final int EnumJobs			= 0x04;
	public static final int AddPrinter			= 0x05;
	public static final int DeletePrinter		= 0x06;
	public static final int SetPrinter			= 0x07;
	public static final int GetPrinter			= 0x08;
	public static final int AddPrinterDriver	= 0x09;
	public static final int EnumPrinterDrivers	= 0x0A;
	public static final int GetPrinterDriver	= 0x0B;
	public static final int GetPrinterDriverDir	= 0x0C;
	public static final int DeletePrinterDriver	= 0x0D;
	public static final int AddPrintProcessor	= 0x0E;
	public static final int EnumPrintProcessors = 0x0F;
	public static final int GetPrintProcessorDir= 0x10;
	public static final int StartDocPrinter		= 0x11;
	public static final int StartPagePrinter	= 0x12;
	public static final int WritePrinter		= 0x13;
	public static final int EndPagePrinter		= 0x14;
	public static final int AbortPrinter		= 0x15;
	public static final int ReadPrinter			= 0x16;
	public static final int EndDocPrinter		= 0x17;
	public static final int AddJob				= 0x18;
	public static final int ScheduleJob			= 0x19;
	public static final int GetPrinterData		= 0x1A;
	public static final int SetPrinterData		= 0x1B;
	public static final int WaitForPrinterChange= 0x1C;
	public static final int ClosePrinter		= 0x1D;
	public static final int AddForm				= 0x1E;
	public static final int DeleteForm			= 0x1F;
	public static final int GetForm				= 0x20;
	public static final int SetForm				= 0x21;
	public static final int EnumForms			= 0x22;
	public static final int EnumPorts			= 0x23;
	public static final int EnumMonitors		= 0x24;
	public static final int AddPort				= 0x25;
	public static final int ConfigurePort		= 0x26;
	public static final int DeletePort			= 0x27;
	public static final int CreatePrinterIC		= 0x28;
	public static final int PlayGDIScriptOnPrinterIC = 0x29;
	public static final int DeletePrinterIC		= 0x2A;
	public static final int AddPrinterConnection= 0x2B;
	public static final int DeletePrinterConnection = 0x2C;
	public static final int PrinterMessageBox	= 0x2D;
	public static final int AddMonitor			= 0x2E;
	public static final int DeleteMonitor		= 0x2F;
	public static final int DeletePrintProcessor= 0x30;
	public static final int AddPrintProvidor	= 0x31;
	public static final int DeletePrintProvidor	= 0x32;
	public static final int EnumPrintProcDataTypes = 0x33;
	public static final int ResetPrinter		= 0x34;
	public static final int GetPrinterDriver2	= 0x35;
	public static final int FindFirstPrinterChangeNotification = 0x36;
	public static final int FindNextPrinterChangeNotification  = 0x37;
	public static final int FCPN				= 0x38;
	public static final int RouterFindFirstPrinterNotificationOld = 0x39;
	public static final int ReplyOpenPrinter	= 0x3A;
	public static final int RouterReplyPrinter	= 0x3B;
	public static final int ReplyClosePrinter	= 0x3C;
	public static final int AddPortEx			= 0x3D;
	public static final int RemoteFindFirstPrinterChangeNotification = 0x3E;
	public static final int SpoolerInit			= 0x3F;
	public static final int ResetPrinterEx		= 0x40;
	public static final int RFFPCNEx			= 0x41;
	public static final int RRPCN				= 0x42;
	public static final int RFNPCNEx			= 0x43;
	public static final int Unknown_44			= 0x44;
	public static final int OpenPrinterEx		= 0x45;
	public static final int AddPrinterEx		= 0x46;
	public static final int Unknown_47			= 0x47;
	public static final int EnumPrinterData		= 0x48;
	public static final int DeletePrinterData	= 0x49;
	public static final int Unknown_4A			= 0x4A;
	public static final int Unknown_4B			= 0x4B;
	public static final int Unknown_4C			= 0x4C;
	public static final int SetPrinterDataEx	= 0x4D;
	public static final int GetPrinterDataEx	= 0x4E;
	public static final int EnumPrinterDataEx	= 0x4F;
	public static final int EnumPrinterKey		= 0x50;
	public static final int DeletePrinterDataEx	= 0x51;
	public static final int Unknown_52			= 0x52;
	public static final int Unknown_53			= 0x53;
	public static final int DeletePrinterDriverEx = 0x54;
	public static final int Unknown_55			= 0x55;
	public static final int Unknown_56			= 0x56;
	public static final int Unknown_57			= 0x57;
	public static final int Unknown_58			= 0x58;
	public static final int AddPrinterDriverEx  = 0x59;
}
