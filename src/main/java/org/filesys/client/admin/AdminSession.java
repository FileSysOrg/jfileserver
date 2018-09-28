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

package org.filesys.client.admin;
 
import java.util.*;
import java.io.*;

import org.filesys.client.SMBPacket;
import org.filesys.client.SessionFactory;
import org.filesys.client.info.PrintQueueInfo;
import org.filesys.client.info.RAPServerInfo;
import org.filesys.client.info.RAPShareInfo;
import org.filesys.client.info.ServerList;
import org.filesys.client.IPCSession;
import org.filesys.client.Session;
import org.filesys.client.info.PrintJob;
import org.filesys.client.info.PrintJobList;
import org.filesys.client.info.PrinterList;
import org.filesys.client.info.RAPServiceInfo;
import org.filesys.client.info.RAPSessionInfo;
import org.filesys.client.info.RAPUserInfo;
import org.filesys.client.info.RAPWorkstationInfo;
import org.filesys.smb.*;
import org.filesys.smb.PacketTypeV1;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCEException;
import org.filesys.smb.dcerpc.DCEPipeType;
import org.filesys.smb.dcerpc.PolicyHandle;
import org.filesys.smb.dcerpc.client.DCEPacket;
import org.filesys.smb.dcerpc.info.ServerInfo;
import org.filesys.smb.dcerpc.info.SessionInfo;
import org.filesys.smb.dcerpc.info.SessionInfoList;
import org.filesys.smb.dcerpc.info.ShareInfo;
import org.filesys.smb.dcerpc.info.ShareInfoList;
import org.filesys.smb.dcerpc.info.UserInfo;
import org.filesys.smb.dcerpc.info.WorkstationInfo;
import org.filesys.smb.nt.RID;
import org.filesys.smb.nt.RIDList;
import org.filesys.smb.nt.SID;
import org.filesys.smb.nt.WellKnownRID;
import org.filesys.smb.nt.WellKnownSID;
import org.filesys.util.DataPacker;
import org.filesys.util.StringList;

/**
 * SMB admin session class
 *
 * <p>The AdminSession class implements the Remote Administration Protocol (RAP)
 * as defined in the draft protocol specification.
 *
 * <p>The class can return the list of nodes on the network, get remote user information,
 * get the list of shares on the remote server, list of printer queues on the remote server,
 * and can manipulate individual print jobs.
 *
 * <p>An AdminSession is created, as with all SMB sessions, via the SessionFactory
 * static class. The SessionFactory.OpenAdminSession() method requires a PCShare
 * object that provides the remote server node name, the share name will be ignored as
 * an admin session is always made to the IPC$ named pipe. User name and/or a password
 * may be required depending upon the requests being made, and whether the 'GUEST'
 * account is available on the remote server.
 * 
 * @see SessionFactory
 * 
 * @author gkspencer
 */
public final class AdminSession {

	// User information levels that may be requested

	private static final int UserInfo0 = 0;
	private static final int UserInfo1 = 1;
	private static final int UserInfo11 = 11;

	// Printer informatoin levels that may be requested

	private static final int PrintQInfo1 = 1;
	private static final int PrintQInfo2 = 2;
	private static final int PrintQInfo3 = 3;
	private static final int PrintQInfo4 = 4;
	private static final int PrintQInfo5 = 5;

	// Print job information levels that may be requested

	private static final int PrintJobInfo0 = 0;
	private static final int PrintJobInfo1 = 1;
	private static final int PrintJobInfo2 = 2;
	private static final int PrintJobInfo3 = 3;

	//	Service information levels that may be requested

	private static final int ServiceInfo0 = 0;
  	private static final int ServiceInfo2 = 2;

  	//	Disk information levels that may be requested

  	private static final int DiskInfo0 = 0;

  	//	Group information levels that may be requested

  	private static final int GroupInfo0 = 0;
  	private static final int GroupInfo1 = 1;

  	//	Workstation information levels

  	private static final int WorkStation10 = 10;

  	// IPC session attached to the remote server IPC$ pipe

  	private IPCSession m_sess;

  	//	Default buffer size to use

  	private int m_defBufSize = 8192;

  	//	DCE/RPC pipe cache
  
  	private Hashtable m_pipeCache;
  
	//	SAMR policy handle
	
	private SamrPolicyHandle m_samrHandle;
	
	//	Local domain SID
	
	private SID m_localDomain;

	//	Use DCE/RPC calls rather than RAP calls if the server supports them
	
	private boolean m_useDCERPC = true;
	
	/**
	 * Class constructor
	 * 
	 * @param sess IPCSession
	 */
	public AdminSession(IPCSession sess) {

		// Save the session

		m_sess = sess;

		// Allocate the DCE/RPC pipe cache

		m_pipeCache = new Hashtable();

		// Set the maximum buffer size using the session negotiated buffer size

		if ( m_sess.getMaximumPacketSize() < m_defBufSize)
			m_defBufSize = m_sess.getMaximumPacketSize();
	}

	/**
	 * Return the use DCE/RPC flag
	 * 
	 * @return boolean
	 */
	public final boolean useDceRpc() {
		return m_useDCERPC;
	}

	/**
	 * Set the use DCE/RPC flag to enable/disable the use of the newer DCE/RPC calls if the server
	 * supports them
	 * 
	 * @param ena boolean
	 */
	public final void setUseDceRpc(boolean ena) {
		m_useDCERPC = ena;
	}

	/**
	 * Close the session to the remote server
	 * 
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB level error occurs
	 */
	public void CloseSession()
		throws java.io.IOException, SMBException {

		// Close the cached DCE/RPC pipes

		closePipes();

		// Close the associated IPC pipe session

		m_sess.CloseSession();
	}

	/**
	 * Continue, unpause, the specified print job
	 * 
	 * @param job PrintJob containing the details of the print job to continue
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB exception occurs
	 */

	public final void ContinuePrintJob(PrintJob job)
		throws java.io.IOException, SMBException {

		// Continue, unpause, the specified print job

		ManagePrintJob(job, PacketTypeV1.NetPrintJobContinue);
	}

	/**
	 * Start, unpause, the specified remote printer queue.
	 * 
	 * @param qname Remote print queue to continue
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB error occurs
	 */

	public final void ContinuePrintQueue(String qname)
		throws java.io.IOException, SMBException {

		// Continue, unpause, the specified print queue

		ManagePrintQueue(qname, PacketTypeV1.NetPrintQContinue);
	}

	/**
	 * Delete the specified print job
	 * 
	 * @param job PrintJob containing the details of the print job to pause
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB exception occurs
	 */

	public final void DeletePrintJob(PrintJob job)
		throws java.io.IOException, SMBException {

		// Delete the specified print job

		ManagePrintJob(job, PacketTypeV1.NetPrintJobDelete);
	}

	/**
	 * Return the transaction buffer size.
	 * 
	 * @return int
	 */
	public int getBufferSize() {
		return m_defBufSize;
	}

	/**
	 * Return a list of the disk devices available on the remote server.
	 * 
	 * @return Disk name strings.
	 * @exception SMBException If an SMB error occurs.
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public final StringList getDiskList()
		throws SMBException, java.io.IOException {

		// Create an SMB transaction packet for the disk enum request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetServerDiskEnum parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.NetServerDiskEnum, params, 0);
		int pos = DataPacker.putString("WrLeh", params, 2, true);
		pos = DataPacker.putString("B3", params, pos, true);
		DataPacker.putIntelShort(DiskInfo0, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 8); // maximum parameter bytes to return, 4 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[4];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter

		// Unpack the disk information structure

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the disk information structure, padding bytes are indicated by
		// '.'s and will not return objects

		StringList diskList = new StringList();

		int cnt = (int) prms[2];

		while (cnt-- > 0) {

			// Unpack the disk information structure

			Vector objs = new Vector();
			pos = DataDecoder.DecodeData(buf, pos, "B3", objs, conv);

			// Copy the values to a disk information object

			String name = (String) objs.elementAt(0);
			diskList.addString(name);
		}

		// Return the disk information

		return diskList;
	}

	/**
	 * Return a list of the groups on the remote server.
	 * 
	 * @return List of groups on the remote server.
	 * @exception SMBException If an SMB error occurs.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception DCEException If a DCE/RPC exception occurs
	 */
	public final StringList getGroupList()
		throws SMBException, java.io.IOException, DCEException {

		// Check if the server supports DCE/RPC requests

		StringList groupList = null;

		if ( useDceRpc() && getSession().supportsRPCAPIs()) {

			// Open the SAMR DCE/RPC pipe

			SamrPipeFile samrPipe = openSecurityAccountsManagerPipe();

			// Get a list of the available domains

			StringList domains = samrPipe.enumerateDomains();

			// Get the groups list

			if ( domains != null && domains.numberOfStrings() > 0)
				groupList = samrPipe.enumerateGroups(domains.getStringAt(0));
		}
		else {

			// Use the older RAP call to get the groups list

			groupList = getRAPGroupList();
		}

		// Return the groups list

		return groupList;
	}

	/**
	 * Get a list of users that are in the specified group.
	 * 
	 * @param grpName java.lang.String Group name to return user list for.
	 * @return List of user names.
	 * @exception SMBException If an SMB error occurs.
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public final StringList getGroupUsers(String grpName)
		throws SMBException, java.io.IOException {

		// Use the RAP call for now

		return getRAPGroupUsers(grpName);
	}

	/**
	 * Get a list of groups for the specified user.
	 * 
	 * @param userName java.lang.String USer name to return group list for.
	 * @return List of group names.
	 * @exception SMBException If an SMB error occurs.
	 * @exception java.io.IOException If an I/O error occurs.
	 */
	public final StringList getUserGroups(String userName)
		throws SMBException, java.io.IOException {

		// Use the RAP call for now

		return getRAPUserGroups(userName);
	}

	/**
	 * Return printer queue information for the specified printer queue.
	 * 
	 * @param printerName Name of the remote printer to return information for.
	 * @return PrintQueueInfo
	 * @exception SMBException If an SMB error occurs
	 * @exception java.io.IOException If an I/O error occurs
	 */
	public final PrintQueueInfo getPrinterInfo(String printerName)
		throws java.io.IOException, SMBException {

		// Create an SMB transaction packet for the get printer information request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetPrintQGetInfo parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.NetPrintQGetInfo, params, 0);
		int pos = DataPacker.putString("zWrLh", params, 2, true);
		// pos = DataPacker.putString ( "B13BWWWzzzzzWW", params, pos, true);
		pos = DataPacker.putString("zWWWWzzzzWWzzl", params, pos, true);
		pos = DataPacker.putString(printerName, params, pos, true);
		// DataPacker.putIntelShort ( PrintQInfo2, params, pos);
		DataPacker.putIntelShort(PrintQInfo3, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 6); // maximum parameter bytes to return, 3 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[3];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter

		// Unpack the printer list

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the printer information structure, padding bytes are indicated by
		// '.'s and will not return objects

		Vector objs = new Vector();
		// pos = SMBDataDecoder.DecodeData ( buf, pos, "B13.WWWzzzzzWW", objs, conv);
		pos = DataDecoder.DecodeData(buf, pos, "zWWWWzzzzWWzzl", objs, conv);

		// Create a printer information object to hold the current printer details

		String str = (String) objs.elementAt(0);
		if ( str == null || str.length() == 0)
			str = printerName;
		PrintQueueInfo qinfo = new PrintQueueInfo(str);

		// Set the print queue parameters

		Short sval = (Short) objs.elementAt(1);
		qinfo.setPriority(sval.intValue());
		qinfo.setSeperatorPage((String) objs.elementAt(5));
		qinfo.setPreProcessor((String) objs.elementAt(6));
		qinfo.setPrinterList((String) objs.elementAt(11));
		qinfo.setParameterString((String) objs.elementAt(7));
		qinfo.setComment((String) objs.elementAt(8));

		sval = (Short) objs.elementAt(9);
		qinfo.setStatus(sval.intValue());

		sval = (Short) objs.elementAt(10);
		qinfo.setJobCount(sval.intValue());

		sval = (Short) objs.elementAt(2);
		qinfo.setStartTime(sval.intValue());

		sval = (Short) objs.elementAt(3);
		qinfo.setStopTime(sval.intValue());

		// Return the printer information

		return qinfo;
	}

	/**
	 * Return the list of printer queues available on this server.
	 * 
	 * @return PrinterList containing the list of available printers
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */

	public final PrinterList getPrinterList()
		throws java.io.IOException, SMBException {

		// Create an SMB transaction packet for the get printer list request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetUserGetInfo parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.NetPrintQEnum, params, 0);
		int pos = DataPacker.putString("WrLeh", params, 2, true);
		pos = DataPacker.putString("B13BWWWzzzzzWW", params, pos, true);
		DataPacker.putIntelShort(PrintQInfo1, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 8); // maximum parameter bytes to return, 4 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[4];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter
		int cnt = (int) prms[2]; // number of entries returned
		int tot = (int) prms[3]; // total number of entries available

		// Unpack the printer list

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the printer information structures, padding bytes are indicated by
		// '.'s and will not return objects

		PrinterList prnList = new PrinterList();

		while (cnt-- > 0) {

			// Get the current printer information

			Vector objs = new Vector();
			pos = DataDecoder.DecodeData(buf, pos, "B13.WWWzzzzzWW", objs, conv);

			// Create a printer information object to hold the current printer details

			String str = (String) objs.elementAt(0);
			PrintQueueInfo qinfo = new PrintQueueInfo(str);

			// Set the print queue parameters

			Short sval = (Short) objs.elementAt(1);
			qinfo.setPriority(sval.intValue());
			qinfo.setSeperatorPage((String) objs.elementAt(4));
			qinfo.setPreProcessor((String) objs.elementAt(5));
			qinfo.setPrinterList((String) objs.elementAt(6));
			qinfo.setParameterString((String) objs.elementAt(7));

			sval = (Short) objs.elementAt(9);
			qinfo.setStatus(sval.intValue());
			sval = (Short) objs.elementAt(10);
			qinfo.setJobCount(sval.intValue());

			// Add the current printer queue to the list

			prnList.addPrinterInfo(qinfo);

		} // end while printers

		// Return the printer list

		return prnList;
	}

	/**
	 * Return information for the specified print job.
	 * 
	 * @param id Id of the print job to return information for
	 * @return PrintJob
	 * @exception SMBException The exception description.
	 * @exception java.io.IOException The exception description.
	 */
	public final PrintJob getPrintJobInfo(int id)
		throws SMBException, java.io.IOException {

		// Create an SMB transaction packet for the get printer list request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the DosPrintJobEnum parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.NetPrintJobGetInfo, params, 0);
		int pos = DataPacker.putString("WWrLh", params, 2, true);
		pos = DataPacker.putString("WWzWWDDzz", params, pos, true);

		DataPacker.putIntelShort(id, params, pos);
		pos += 2;
		DataPacker.putIntelShort(PrintJobInfo2, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 6); // maximum parameter bytes to return, 3 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[3];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter

		// Unpack the print job list

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the print job information, padding bytes are indicated by
		// '.'s and will not return objects

		Vector objs = new Vector();
		pos = DataDecoder.DecodeData(buf, pos, "WWzWWDDzz", objs, conv);

		// Create a print job information object to hold the current job details

		Short sval = (Short) objs.elementAt(0);
		PrintJob prnJob = new PrintJob(sval.intValue());

		sval = (Short) objs.elementAt(1);
		prnJob.setPriority(sval.intValue());
		prnJob.setUserName((String) objs.elementAt(2));

		sval = (Short) objs.elementAt(3);
		prnJob.setPrintPosition(sval.intValue());

		sval = (Short) objs.elementAt(4);
		prnJob.setStatus(sval.intValue());

		Integer ival = (Integer) objs.elementAt(5);
		prnJob.setQueuedDateTime(new Date(ival.longValue() * 1000));

		ival = (Integer) objs.elementAt(6);
		prnJob.setSpoolFileSize(ival.intValue());

		prnJob.setComment((String) objs.elementAt(7));
		prnJob.setDocument((String) objs.elementAt(8));

		// Return the print job information

		return prnJob;
	}

	/**
	 * Return the list of print jobs in the specified printer queue.
	 * 
	 * @param qnam Name of the queue to return jobs for
	 * @return PrintJobList containing the list of jobs in the queue
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	public final PrintJobList getPrintJobs(String qnam)
		throws java.io.IOException, SMBException {

		// Create an SMB transaction packet for the get printer list request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the DosPrintJobEnum parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.NetPrintQGetInfo, params, 0);
		int pos = DataPacker.putString("zWrLh", params, 2, true);
		pos = DataPacker.putString("B13BWWWzzzzzWN", params, pos, true);
		pos = DataPacker.putString(qnam, params, pos, true);
		DataPacker.putIntelShort(PrintJobInfo2, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;
		pos = DataPacker.putString("WB21BB16B10zWWzDDz", params, pos, true);

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 6); // maximum parameter bytes to return, 4 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[3];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		if ( prms[1] == 0)
			return null;

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter

		// Unpack the print job list

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the print job list, padding bytes are indicated by
		// '.'s and will not return objects

		PrintJobList jobList = new PrintJobList();

		Vector qobj = new Vector();
		pos = DataDecoder.DecodeData(buf, pos, "B13BWWWzzzzzWN", qobj, conv);

		// Get the count for the second block of data, the queue list

		int cnt = DataPacker.getIntelShort(buf, pos);
		pos += 2;

		while (cnt-- > 0) {

			// Get the current print job information

			Vector objs = new Vector();
			pos = DataDecoder.DecodeData(buf, pos, "WB21BB16B10zWWzDDz", objs, conv);

			// Create a print job information object to hold the current job details

			Short sval = (Short) objs.elementAt(0);
			PrintJob prnJob = new PrintJob(sval.intValue());

			sval = (Short) objs.elementAt(6);
			prnJob.setPriority(sval.intValue());
			prnJob.setUserName((String) objs.elementAt(1));

			Byte bval = (Byte) objs.elementAt(2);
			prnJob.setPrintPosition(sval.intValue());

			sval = (Short) objs.elementAt(7);
			prnJob.setStatus(sval.intValue());

			Integer ival = (Integer) objs.elementAt(9);
			prnJob.setQueuedDateTime(new Date(ival.longValue() * 1000));

			ival = (Integer) objs.elementAt(10);
			prnJob.setSpoolFileSize(ival.intValue());

			prnJob.setComment((String) objs.elementAt(8));
			prnJob.setDocument((String) objs.elementAt(11));

			// Add the print job to the job list

			jobList.addPrintJob(prnJob);

		} // end while printers

		// Return the print job list

		return jobList;
	}

	/**
	 * Return the list of print jobs in the specified print queue.
	 * 
	 * @param qnam Name of the print queue
	 * @return PrintJobList
	 * @exception SMBException The exception description.
	 * @exception java.io.IOException The exception description.
	 */
	private PrintJobList getPrintJobsOld(String qnam)
		throws SMBException, IOException {

		// Create an SMB transaction packet for the get printer list request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the DosPrintJobEnum parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.NetPrintJobEnum, params, 0);
		int pos = DataPacker.putString("zWrLeh", params, 2, true);
		pos = DataPacker.putString("WWzWWDDzz", params, pos, true);
		pos = DataPacker.putString(qnam, params, pos, true);
		DataPacker.putIntelShort(PrintJobInfo2, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 6); // maximum parameter bytes to return, 4 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[4];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter
		int cnt = (int) prms[2]; // number of entries returned
		int tot = (int) prms[3]; // total number of entries available

		// Unpack the print job list

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the print job list, padding bytes are indicated by
		// '.'s and will not return objects

		PrintJobList jobList = new PrintJobList();

		while (cnt-- > 0) {

			// Get the current print job information

			Vector objs = new Vector();
			pos = DataDecoder.DecodeData(buf, pos, "WWzWWDDzz", objs, conv);

			// Create a print job information object to hold the current job details

			Short sval = (Short) objs.elementAt(0);
			PrintJob prnJob = new PrintJob(sval.intValue());

			sval = (Short) objs.elementAt(1);
			prnJob.setPriority(sval.intValue());
			prnJob.setUserName((String) objs.elementAt(2));

			sval = (Short) objs.elementAt(3);
			prnJob.setPrintPosition(sval.intValue());

			sval = (Short) objs.elementAt(4);
			prnJob.setStatus(sval.intValue());

			Integer ival = (Integer) objs.elementAt(5);
			prnJob.setQueuedDateTime(new Date(ival.longValue() * 1000));

			ival = (Integer) objs.elementAt(6);
			prnJob.setSpoolFileSize(ival.intValue());

			prnJob.setComment((String) objs.elementAt(7));
			prnJob.setDocument((String) objs.elementAt(8));

			// Add the print job to the job list

			jobList.addPrintJob(prnJob);

		} // end while printers

		// Return the print job list

		return jobList;
	}

	/**
	 * Return the server information for the server we are connected to
	 * 
	 * @return ServerInfo containing the server information
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	public final ServerInfo getServerInfo()
		throws java.io.IOException, SMBException {

		// Use the RAP call for now

		return getRAPServerInfo();
	}

	/**
	 * Return the server information for the specified server.
	 * 
	 * @param node Node name of the server to return information for.
	 * @return ServerInfo
	 * @exception SMBException An SMB exception has occurred.
	 * @exception java.io.IOException An I/O exception has occurred.
	 */
	public final ServerInfo getServerInfo(String node)
		throws SMBException, java.io.IOException {

		// Use the RAP call for now

		return getRAPServerInfo(node);
	}

	/**
	 * Return the list of available servers on the network
	 * 
	 * @param flags Server enumerate flags.
	 * @return List of available servers, as a ServerList, else null if there are no servers
	 *         available.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB exception occurs
	 */
	public final ServerList getServerList(int flags)
		throws java.io.IOException, SMBException {

		// Use the RAP call for now

		return getRAPServerList(flags);
	}

	/**
	 * Return the list of available servers on the network
	 * 
	 * @param flags Server enumerate flags.
	 * @return List of available servers, as a Vector of Strings, else null if there are no servers
	 *         available.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB exception occurs
	 */
	public final StringList getServerNames(int flags)
		throws java.io.IOException, SMBException {

		// Use the RAP call for now

		return getRAPServerNames(flags);
	}

	/**
	 * Return a list of services installed on the remote node.
	 * 
	 * @return List of service name strings.
	 * @exception SMBException SMB error occurred.
	 * @exception java.io.IOException I/O exception.
	 */
	public final StringList getServiceList()
		throws SMBException, java.io.IOException {

		// Use the RAP call for now

		return getRAPServiceList();
	}

	/**
	 * Return the associated session.
	 * 
	 * @return Session
	 */
	public Session getSession() {
		return m_sess;
	}

	/**
	 * Return a list of open sessions on the remote server.
	 * 
	 * @return SessionInfoList
	 * @exception SMBException SMB level error
	 * @exception DCEException DCE/RPC level error
	 * @exception IOException Socket error
	 */
	public final SessionInfoList getSessionList()
		throws SMBException, DCEException, java.io.IOException {

		// Check if the server supports DCE/RPC requests

		SessionInfoList sessList = null;

		if ( useDceRpc() && getSession().supportsRPCAPIs()) {

			// Open the server DCE/RPC pipe

			SrvsvcPipeFile srvPipe = openServerPipe();

			// Get the session list

			sessList = srvPipe.getSessionList(null, null);
		}
		else {

			// Use the older RAP call to get the session list

			sessList = getRAPSessionList();
		}

		// Return the session list

		return sessList;
	}

	/**
	 * Return the full share information for the specified share
	 * 
	 * @param shr Remote share name to return information for.
	 * @return ShareInfo containing the full share details
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 * @exception DCEException DCE/RPC error
	 */

	public final ShareInfo getShareInfo(String shr)
		throws java.io.IOException, SMBException, DCEException {

		// Check if the server supports DCE/RPC requests

		ShareInfo shrInfo = null;

		if ( useDceRpc() && getSession().supportsRPCAPIs()) {

			// Open the server DCE/RPC pipe

			SrvsvcPipeFile srvPipe = openServerPipe();

			// Get the share list

			shrInfo = srvPipe.getShareInformation(shr);
		}
		else {

			// Use the RAP call for now

			shrInfo = getRAPShareInfo(shr);
		}

		// Return the share information

		return shrInfo;
	}

	/**
	 * Return the list of available shares on the remote server
	 * 
	 * @return List of available shares, as a ShareList, else null if there are no shares available.
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException SMB error
	 * @exception DCEException DCE/RPC error
	 */
	public final ShareInfoList getShareList()
		throws java.io.IOException, SMBException, DCEException {

		// Check if the server supports DCE/RPC requests

		ShareInfoList shareList = null;

		if ( useDceRpc() && getSession().supportsRPCAPIs()) {

			// Open the server DCE/RPC pipe

			SrvsvcPipeFile srvPipe = openServerPipe();

			// Get the share list

			shareList = srvPipe.getShareList(true);
		}
		else {

			// Use the older RAP call to get the share list

			shareList = getRAPShareList();
		}

		// Return the share list

		return shareList;
	}

	/**
	 * Return the user information for the specified user
	 * 
	 * @param usr User name of the user to return information for.
	 * @return UserInfo containing the user details
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	public final UserInfo getUserInfo(String usr)
		throws java.io.IOException, SMBException {

		// Use the RAP call for now

		return getRAPUserInfo(usr);
	}

	/**
	 * Return the list of users on the remote server.
	 * 
	 * @return Vector of user name strings.
	 * @exception java.io.IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 * @exception DCEException If a DCE/RPC error occurs
	 */
	public final StringList getUserList()
		throws java.io.IOException, SMBException, DCEException {

		// Check if the server supports DCE/RPC requests

		StringList userList = null;

		if ( useDceRpc() && getSession().supportsRPCAPIs()) {

			// Open the SAMR DCE/RPC pipe

			SamrPipeFile samrPipe = openSecurityAccountsManagerPipe();

			// Get a list of the available domains

			StringList domains = samrPipe.enumerateDomains();

			// Get the user list

			if ( domains != null && domains.numberOfStrings() > 0)
				userList = samrPipe.enumerateUsers(domains.getStringAt(0));
		}
		else {

			// Use the older RAP call to get the user list

			userList = getRAPUserList();
		}

		// Reutrn the user list

		return userList;
	}

	/**
	 * Return the server type/information for the server we are connected to
	 * 
	 * @return WorkStationInfo containing the information
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 * @exception DCEException If a DCE/RPC error occurs
	 */
	public final WorkstationInfo getWorkstationInfo()
		throws java.io.IOException, SMBException, DCEException {

		// Check if the server supports DCE/RPC requests

		WorkstationInfo wksInfo = null;

		if ( useDceRpc() && getSession().supportsRPCAPIs()) {

			// Open the Workstation service DCE/RPC pipe

			WkssvcPipeFile wksPipe = openWorkstationPipe();

			// Get the workstation information

			wksInfo = wksPipe.getWorkstationInformation();
		}
		else {

			// Use the older RAP call

			wksInfo = getRAPWorkstationInfo();
		}

		// Return the workstation information

		return wksInfo;
	}

	/**
	 * Return the account type for the specified user name
	 * 
	 * @param userName String
	 * @return int
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 * @throws DCEException DCE/RPC error
	 */
	public final int getAccountType(String userName)
		throws IOException, SMBException, DCEException {

		// Open the SAMR DCE/RPC pipe, if not already open

		SamrPipeFile samrPipe = (SamrPipeFile) openDCERPCPipe(DCEPipeType.PIPE_SAMR);

		if ( m_samrHandle == null) {

			// Get a handle to the service

			m_samrHandle = samrPipe.openService();
		}

		// Get the user account details and determine if the user is a guest, normal user or
		// administrator

		PolicyHandle domainHandle = null;
		PolicyHandle builtinHandle = null;
		PolicyHandle userHandle = null;

		int userType = -1;

		try {

			// Get the local domain SID

			if ( m_localDomain == null) {

				// Get a list of the domains

				StringList domains = samrPipe.enumerateDomains();
				if ( domains != null && domains.numberOfStrings() > 0) {

					// Get a handle to the domain

					m_localDomain = samrPipe.lookupDomain(domains.getStringAt(0));
				}
			}

			// Open the domain

			domainHandle = samrPipe.openDomain(m_localDomain);

			// Open the builtin domain

			builtinHandle = samrPipe.openDomain(WellKnownSID.SIDBuiltinDomain);

			// Find the user relative-id

			RIDList rids = samrPipe.lookupName(domainHandle, userName);
			RID userRID = rids.findRID(userName, RID.TypeUser);

			if ( userRID != null) {

				// Get a handle to the user

				userHandle = samrPipe.openUser(domainHandle, userRID);

				// Get a list of groups that the user is a member of

				RIDList groups = samrPipe.getGroupsForUser(userHandle);
				if ( groups != null) {

					// Check if the user is a member of the administrators, users or guests group

					if ( groups.findRID(WellKnownRID.DomainGroupAdmins, RID.TypeWellKnownGroup) != null) {

						// User in an administrator

						userType = UserInfo.PrivAdmin;
					}
					else if ( groups.findRID(WellKnownRID.DomainGroupUsers, RID.TypeWellKnownGroup) != null) {

						// User is a normal user

						userType = UserInfo.PrivUser;
					}
					else if ( groups.findRID(WellKnownRID.DomainGroupGuests, RID.TypeWellKnownGroup) != null) {

						// User is a guest

						userType = UserInfo.PrivGuest;
					}
				}

				// Make a SID for the user

				SID userSID = new SID(m_localDomain);
				userSID.setRID(userRID.getRID());

				// Get a list of aliases that the user is a member of

				RIDList aliases = samrPipe.getAliasesForUser(builtinHandle, userSID);
				if ( aliases != null) {

					// Check if the user is a member of the administrators alias group

					if ( aliases.findRID(WellKnownRID.DomainAliasAdmins, RID.TypeAlias) != null) {

						// User is an administrator

						userType = UserInfo.PrivAdmin;
					}
					else if ( aliases.findRID(WellKnownRID.DomainAliasUsers, RID.TypeAlias) != null) {

						// User is a normal user

						userType = UserInfo.PrivUser;
					}
					else if ( aliases.findRID(WellKnownRID.DomainAliasGuests, RID.TypeAlias) != null) {

						// User is a guest

						userType = UserInfo.PrivGuest;
					}
				}
			}
		}
		finally {

			// Make sure all handles are released

			if ( samrPipe != null) {

				// Close the domain handle

				if ( domainHandle != null)
					samrPipe.closeHandle(domainHandle);

				// Close the builtin domain handle

				if ( builtinHandle != null)
					samrPipe.closeHandle(builtinHandle);

				// Close the user handle

				if ( userHandle != null)
					samrPipe.closeHandle(userHandle);
			}
		}

		// Return the user account type

		return userType;
	}

	/**
	 * Pause/continue or delete the specified print job
	 * 
	 * @param job Print job to manage
	 * @param func Print job function
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	private final void ManagePrintJob(PrintJob job, int func)
		throws java.io.IOException, SMBException {

		// Create an SMB transaction packet for the print management job request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the DosPrintJobEnum parameter block

		byte[] params = new byte[64];
		DataPacker.putIntelShort(func, params, 0);
		int pos = DataPacker.putString("W", params, 2, true);
		pos = DataPacker.putString("", params, pos, true);
		DataPacker.putIntelShort(job.getJobNumber(), params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 2); // maximum parameter bytes to return, 1 short
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[2];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);
	}

	/**
	 * Pause, continue or delete the specified printer queue.
	 * 
	 * @param qname Print queue to manage
	 * @param func int Function to perform (pause/continue/delete)
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs.
	 */
	private void ManagePrintQueue(String qname, int func)
		throws java.io.IOException, SMBException {

		// Create an SMB transaction packet for the print queue management request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the transact parameter block

		byte[] params = new byte[64];
		DataPacker.putIntelShort(func, params, 0);
		int pos = DataPacker.putString("z", params, 2, true);
		pos = DataPacker.putString("", params, pos, true);
		pos = DataPacker.putString(qname, params, pos, true);

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 2); // maximum parameter bytes to return, 1 short
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[2];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);
	}

	/**
	 * Pause the specified print job
	 * 
	 * @param job PrintJob containing the details of the print job to pause
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB exception occurs
	 */
	public final void PausePrintJob(PrintJob job)
		throws java.io.IOException, SMBException {

		// Pause the specified print job

		ManagePrintJob(job, PacketTypeV1.NetPrintJobPause);
	}

	/**
	 * Pause the specified print queue.
	 * 
	 * @param qname Remote print queue to be paused
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	public final void PausePrintQueue(String qname)
		throws java.io.IOException, SMBException {

		// Pause the specified print queue

		ManagePrintQueue(qname, PacketTypeV1.NetPrintQPause);
	}

	/**
	 * Open the Windows registry DCE/RPC pipe
	 * 
	 * @return WinregPipeFile
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 * @exception DCEException DCE/RPC error
	 */
	public final WinregPipeFile openWindowsRegistryPipe()
		throws IOException, SMBException, DCEException {

		// Open the remote Windows registry DCE/RPC service

		return (WinregPipeFile) openDCERPCPipe(DCEPipeType.PIPE_WINREG);
	}

	/**
	 * Open the server DCE/RPC pipe
	 * 
	 * @return SrvsvcPipeFile
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 * @exception DCEException DCE/RPC error
	 */
	public final SrvsvcPipeFile openServerPipe()
		throws IOException, SMBException, DCEException {

		// Open the DCE/RPC service

		return (SrvsvcPipeFile) openDCERPCPipe(DCEPipeType.PIPE_SRVSVC);
	}

	/**
	 * Open the workstation DCE/RPC pipe
	 * 
	 * @return WkssvcPipeFile
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 * @exception DCEException DCE/RPC error
	 */
	public final WkssvcPipeFile openWorkstationPipe()
		throws IOException, SMBException, DCEException {

		// Open the DCE/RPC service

		return (WkssvcPipeFile) openDCERPCPipe(DCEPipeType.PIPE_WKSSVC);
	}

	/**
	 * Open the event log DCE/RPC pipe
	 * 
	 * @return EventlogPipeFile
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 * @exception DCEException DCE/RPC error
	 */
	public final EventlogPipeFile openEventLogPipe()
		throws IOException, SMBException, DCEException {

		// Open the DCE/RPC service

		return (EventlogPipeFile) openDCERPCPipe(DCEPipeType.PIPE_EVENTLOG);
	}

	/**
	 * Open the service manager DCE/RPC pipe
	 * 
	 * @return SvcctlPipeFile
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 * @exception DCEException DCE/RPC error
	 */
	public final SvcctlPipeFile openServiceManagerPipe()
		throws IOException, SMBException, DCEException {

		// Open the DCE/RPC service

		return (SvcctlPipeFile) openDCERPCPipe(DCEPipeType.PIPE_SVCCTL);
	}

	/**
	 * Open the security accounts manager DCE/RPC pipe (SAMR)
	 * 
	 * @return SamrPipeFile
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 * @exception DCEException DCE/RPC error
	 */
	public final SamrPipeFile openSecurityAccountsManagerPipe()
		throws IOException, SMBException, DCEException {

		// Open the DCE/RPC service

		return (SamrPipeFile) openDCERPCPipe(DCEPipeType.PIPE_SAMR);
	}

	/**
	 * Open the shutdown service DCE/RPC pipe
	 * 
	 * @return InitShutPipeFile
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 * @exception DCEException DCE/RPC error
	 */
	public final InitShutPipeFile openInitShutdownPipe()
		throws IOException, SMBException, DCEException {

		// Open the DCE/RPC service

		return (InitShutPipeFile) openDCERPCPipe(DCEPipeType.PIPE_INITSHUT);
	}

	/**
	 * Set the buffer size to use for transactions. The minimum is 8K, and maximum is 64K.
	 * 
	 * @param siz int
	 */
	public void setBufferSize(int siz) {

		// Check the buffer size for a valid range

		if ( siz >= 8192 && siz <= 65535)
			m_defBufSize = siz;
	}

	/**
	 * Open the specifed named pipe file and setup the pipe.
	 * 
	 * @param pipeId DCE/RPC pipe type
	 * @return IPCPipeFile
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 * @exception DCEException DCE/RPC error
	 */
	public final IPCPipeFile openPipe(DCEPipeType pipeId)
		throws IOException, SMBException, DCEException {

		// Check if the server supports DCE/RPC

		if ( getSession().supportsRPCAPIs() == false)
			throw new DCEException("Server does not support DCE/RPC");

		// Check if the pipe id is valid

		String pipeName = DCEPipeType.getTypeAsStringShort(pipeId);
		if ( pipeName == null)
			throw new DCEException("Invalid pipe id " + pipeId);

		// Initialize the SMB request to open the required named pipe file

		SMBPacket pkt = new SMBPacket();

		// Build the NTCreateAndX SMB packet

		pkt.setFlags(m_sess.getDefaultFlags());
		pkt.setFlags2(m_sess.getDefaultFlags2());

		pkt.setCommand(PacketTypeV1.NTCreateAndX);
		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		pkt.setParameterCount(24);
		pkt.resetParameterPointer();

		pkt.packByte(0xFF); // no chained command
		pkt.packByte(0); // reserved
		pkt.packWord(0); // AndX offset
		pkt.packByte(0); // reserved

		int nameLen = pipeName.length();
		if ( pkt.isUnicode())
			nameLen = (nameLen * 2) + 2;

		pkt.packWord(nameLen);// name length in bytes, inc null
		pkt.packInt(0); // flags
		pkt.packInt(0); // root FID
		pkt.packInt(0x2019F); // desired access mode
		pkt.packLong(0); // allocation size
		pkt.packInt(0); // file attributes
		pkt.packInt(SharingMode.READ_WRITE.intValue()); // share access mode
		pkt.packInt(0x01); // action to take if file exists
		pkt.packInt(0); // file create options
		pkt.packInt(2); // impersonation level, 0=anonymous, 2=impersonation
		pkt.packByte(0); // security flags

		pkt.resetBytePointer();
		pkt.packString(pipeName, pkt.isUnicode());

		pkt.setByteCount();

		// Send/receive the NT create andX request

		pkt.ExchangeSMB(m_sess, pkt, true);

		// Unpack the file/directory details

		pkt.resetParameterPointer();
		pkt.skipBytes(5);

		int pipeHandle = pkt.unpackWord();

		// Bind the pipe file

		DCEPacket dcePkt = new DCEPacket(65535, pkt);
		dcePkt.initializeDCEBind(pipeHandle, 4280, 4280, pipeId, 1);

		// Set the user id and tree id

		dcePkt.setUserId(m_sess.getUserId());
		dcePkt.setTreeId(m_sess.getTreeId());

		// Set the header flags

		// dcePkt.setFlags(m_sess.getDefaultFlags());
		// dcePkt.setFlags2(m_sess.getDefaultFlags2());

		// Exchanged the SMB packet

		dcePkt.ExchangeSMB(m_sess, dcePkt);

		// Check if we received a valid response

		if ( dcePkt.isValidResponse() == false)
			throw new SMBException(dcePkt.getErrorClass(), dcePkt.getErrorCode());

		// Get the maximum transmit/receive buffer sizes from the bind acknowledge

		DCEBuffer ackBuf = new DCEBuffer(dcePkt.getBuffer(), dcePkt.getDCEDataOffset());
		int maxTxSize = -1;
		int maxRxSize = -1;

		try {
			maxTxSize = ackBuf.getShort();
			maxRxSize = ackBuf.getShort();
		}
		catch (DCEBufferException ex) {
		}

		// Create a pipe file for the new pipe connection

		IPCPipeFile pipeFile = null;

		switch (pipeId) {

			// Server service

			case PIPE_SRVSVC:
				pipeFile = new SrvsvcPipeFile(m_sess, dcePkt, pipeHandle, pipeName, maxTxSize, maxRxSize);
				break;

			// Windows registry

			case PIPE_WINREG:
				pipeFile = new WinregPipeFile(m_sess, dcePkt, pipeHandle, pipeName, maxTxSize, maxRxSize);
				break;

			// Service control

			case PIPE_SVCCTL:
				pipeFile = new SvcctlPipeFile(m_sess, dcePkt, pipeHandle, pipeName, maxTxSize, maxRxSize);
				break;

			// Eventlog

			case PIPE_EVENTLOG:
				pipeFile = new EventlogPipeFile(m_sess, dcePkt, pipeHandle, pipeName, maxTxSize, maxRxSize);
				break;

			// Local Security Authority

			case PIPE_LSARPC:
				pipeFile = new LsarpcPipeFile(m_sess, dcePkt, pipeHandle, pipeName, maxTxSize, maxRxSize);
				break;

			// Security Accounts Manager

			case PIPE_SAMR:
				pipeFile = new SamrPipeFile(m_sess, dcePkt, pipeHandle, pipeName, maxTxSize, maxRxSize);
				break;

			// NetLogon

			case PIPE_NETLOGON:
				pipeFile = new NetLogonPipeFile(m_sess, dcePkt, pipeHandle, pipeName, maxTxSize, maxRxSize);
				break;

			// InitShutdown

			case PIPE_INITSHUT:
				pipeFile = new InitShutPipeFile(m_sess, dcePkt, pipeHandle, pipeName, maxTxSize, maxRxSize);
				break;

			// Workstation service

			case PIPE_WKSSVC:
				pipeFile = new WkssvcPipeFile(m_sess, dcePkt, pipeHandle, pipeName, maxTxSize, maxRxSize);
				break;
		}

		// Return the named pipe file
		return pipeFile;
	}

	/**
	 * Open a DCE/RPC pipe, or re-use a pipe file cached in the pipe cache.
	 * 
	 * @param pipeType DCEPipeType
	 * @return IPCPipeFile
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 * @exception DCEException DCE/RPC error
	 */
	private final IPCPipeFile openDCERPCPipe(DCEPipeType pipeType)
		throws IOException, SMBException, DCEException {

		// Check if the server supports DCE/RPC

		if ( getSession().supportsRPCAPIs() == false)
			throw new DCEException("Server does not support DCE/RPC");

		// Get the pipe type as a string, the name is used to index the cached pipe files

		String pipeName = DCEPipeType.getTypeAsString(pipeType);

		// Check if there is a pipe file already in the cache

		IPCPipeFile pipeFile = (IPCPipeFile) m_pipeCache.get(pipeName);
		if ( pipeFile != null && pipeFile.isClosed()) {

			// Remove the pipe file from the cache as the file has been closed

			m_pipeCache.remove(pipeName);
			pipeFile = null;
		}

		// Check if the pipe file is valid

		if ( pipeFile != null)
			return pipeFile;

		// Open the required DCE/RPC pipe

		pipeFile = openPipe(pipeType);
		if ( pipeFile != null) {

			// Cache the pipe file

			m_pipeCache.put(pipeName, pipeFile);
		}

		// Return the DCE/RPC pipe file

		return pipeFile;
	}

	/**
	 * Close the cached pipe files
	 */
	public final void closePipes() {

		// If the SAMR handle is valid then close the handle before closing the pipes

		if ( m_samrHandle != null) {

			// Get the SAMR pipe, if cached

			SamrPipeFile samrPipe = (SamrPipeFile) m_pipeCache.get(DCEPipeType.getTypeAsString(DCEPipeType.PIPE_SAMR));
			if ( samrPipe != null && samrPipe.isClosed() == false) {
				try {
					samrPipe.closeHandle(m_samrHandle);
				}
				catch (Exception ex) {
				}

				// Clear the handle and domain SID

				m_samrHandle = null;
				m_localDomain = null;
			}
		}
		// Enumerate the pipe files in the cache

		Enumeration enm = m_pipeCache.elements();

		while (enm.hasMoreElements()) {

			// Get a pipe file from the cache

			IPCPipeFile pipeFile = (IPCPipeFile) enm.nextElement();

			// Close the pipe file

			if ( pipeFile.isClosed() == false) {
				try {
					pipeFile.ClosePipe();
				}
				catch (Exception ex) {
				}
			}
		}

		// Clear the pipe cache of all objects

		m_pipeCache.clear();
	}

	/**
	 * Get a list of the available groups using the older RAP call.
	 * 
	 * @return List of groups on the remote server.
	 * @exception SMBException If an SMB error occurs.
	 * @exception IOException If an I/O error occurs.
	 */
	private final StringList getRAPGroupList()
		throws SMBException, java.io.IOException {

		// Create an SMB transaction packet for the get group info request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetGroupEnum parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.NetGroupEnum, params, 0);
		int pos = DataPacker.putString("WrLeh", params, 2, true);
		pos = DataPacker.putString("B21", params, pos, true);
		DataPacker.putIntelShort(GroupInfo0, params, pos);
		// pos = DataPacker.putString ( "B21z", params, pos, true);
		// DataPacker.putIntelShort ( GroupInfo1, params, pos);
		// pos = DataPacker.putString ( "B21BzWW", params, pos, true);
		// DataPacker.putIntelShort ( 2, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 8); // maximum parameter bytes to return, 4 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[4];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter

		// Unpack the group information structure

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the group information structure, padding bytes are indicated by
		// '.'s and will not return objects

		Vector grpList = new Vector();

		int cnt = (int) prms[3];

		while (cnt-- > 0) {

			// Decode a group name string from the return buffer

			pos = DataDecoder.DecodeData(buf, pos, "B21", grpList, conv);
		}

		// Return the group information

		return new StringList(grpList);
	}

	/**
	 * Get a list of users that are in the specified group, using the older RAP call.
	 * 
	 * @param grpName java.lang.String Group name to return user list for.
	 * @return List of user names.
	 * @exception SMBException If an SMB error occurs.
	 * @exception IOException If an I/O error occurs.
	 */
	private final StringList getRAPGroupUsers(String grpName)
		throws SMBException, java.io.IOException {

		// Create an SMB transaction packet for the get group users info request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetGroupGetUser parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.NetGroupGetUsers, params, 0);
		int pos = DataPacker.putString("zWrLeh", params, 2, true);
		pos = DataPacker.putString("B21", params, pos, true);
		pos = DataPacker.putString(grpName, params, pos, true);
		DataPacker.putIntelShort(UserInfo0, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 8); // maximum parameter bytes to return, 4 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[4];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter

		// Unpack the user information structure

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the user information structure, padding bytes are indicated by
		// '.'s and will not return objects

		Vector usrList = new Vector();

		int cnt = (int) prms[3];

		while (cnt-- > 0) {

			// Decode a user name string from the return buffer

			pos = DataDecoder.DecodeData(buf, pos, "B21", usrList, conv);
		}

		// Return the user information

		return new StringList(usrList);
	}

	/**
	 * Return the server information for the server we are connected to, using the older RAP call.
	 * 
	 * @return ServerInfo containing the server information
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	private final ServerInfo getRAPServerInfo()
		throws java.io.IOException, SMBException {

		// Create an SMB transaction packet for the get server info request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetServerGetInfo parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.RAPServerGetInfo, params, 0);
		int pos = DataPacker.putString("WrLh", params, 2, true);
		pos = DataPacker.putString("B16BBDz", params, pos, true);
		DataPacker.putIntelShort(ServerInfo.InfoLevel1, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 6); // maximum parameter bytes to return, 3 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[3];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter

		// Unpack the share information structure

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the server information structure, padding bytes are indicated by
		// '.'s and will not return objects

		Vector objs = new Vector();
		DataDecoder.DecodeData(buf, pos, "B16BBDz", objs, conv);

		// Copy the values to a full server information object

		RAPServerInfo srvinfo = new RAPServerInfo(ServerInfo.InfoLevel1, objs, false);

		// Return the server information

		return srvinfo;
	}

	/**
	 * Return the server information for the specified server, using the older RAP call.
	 * 
	 * @param node Node name of the server to return information for.
	 * @return ServerInfo
	 * @exception SMBException An SMB exception has occurred.
	 * @exception IOException An I/O exception has occurred.
	 */
	private final ServerInfo getRAPServerInfo(String node)
		throws SMBException, java.io.IOException {

		// Check if we want server information for the local node

		if ( node.equalsIgnoreCase(m_sess.getServer()))
			return getRAPServerInfo();

		// Open an admin session to the required node

		PCShare srvShr = new PCShare(node, "", "", "");
		AdminSession admSess = SessionFactory.OpenAdminSession(srvShr);

		// Get the server information

		ServerInfo srvInfo = admSess.getRAPServerInfo();

		// Close the session

		admSess.CloseSession();

		// Return the server information

		return srvInfo;
	}

	/**
	 * Return the list of available servers on the network, using the older RAP call.
	 * 
	 * @param flags Server enumerate flags.
	 * @return List of available servers, as a ServerList, else null if there are no servers
	 *         available.
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB exception occurs
	 */
	private final ServerList getRAPServerList(int flags)
		throws java.io.IOException, SMBException {

		// Create an SMB transaction packet for the server enum request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetServerEnum parameter block

		byte[] params = new byte[128];
		DataPacker.putIntelShort(PacketTypeV1.RAPServerEnum2, params, 0);

		int pos = 0;

		pos = DataPacker.putString("WrLehDz", params, 2, true);
		pos = DataPacker.putString("B16BBDz", params, pos, true);

		DataPacker.putIntelShort(ServerInfo.InfoLevel1, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize - SMBPacket.TRANS_HEADERLEN, params, pos);
		pos += 2;
		DataPacker.putIntelInt(flags, params, pos);
		pos += 4;

		// Set the domain enumeration flag

		boolean domainEnum = false;

		if ( (flags & ServerType.DomainEnum) != 0)
			domainEnum = true;

		// Check for the domain enum flag, if set then specify a null domain
		// name string.

		if ( m_sess.getDomain() == null || domainEnum == true)
			pos = DataPacker.putString("", params, pos, true);
		else
			pos = DataPacker.putString(m_sess.getDomain(), params, pos, true);

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 8); // maximum parameter bytes to return, 4 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new java.io.IOException("Transaction failed");

		// Unpack the parameter block

		short[] prms = new short[4];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter
		int nsrv = (int) prms[2]; // number of server infos in this packet
		int totsrv = (int) prms[3]; // total server infos

		// Create a server list to return the server information

		ServerList srvList = new ServerList();

		// Unpack the server information structures

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		while (nsrv-- > 0) {

			// Unpack the server information structure

			Vector objs = new Vector();
			pos = DataDecoder.DecodeData(buf, pos, "B16BBDz", objs, conv);

			// Copy the values to a full server information object

			RAPServerInfo srvinfo = new RAPServerInfo(ServerInfo.InfoLevel1, objs, domainEnum);

			// Add the server info to the list

			srvList.addServerInfo(srvinfo);
		}

		// Return the server information list

		return srvList;
	}

	/**
	 * Return the list of available servers on the network, using the older RAP call.
	 * 
	 * @param flags Server enumerate flags.
	 * @return List of available servers, as a Vector of Strings, else null if there are no servers
	 *         available.
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB exception occurs
	 */
	private final StringList getRAPServerNames(int flags)
		throws java.io.IOException, SMBException {

		// Create an SMB transaction packet for the server enum request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetServerEnum parameter block

		byte[] params = new byte[128];
		DataPacker.putIntelShort(PacketTypeV1.RAPServerEnum2, params, 0);
		int pos = 0;

		if ( m_sess.getDialectString().equalsIgnoreCase("NT LM 0.12")
				|| (m_sess.getOperatingSystem() != null && m_sess.getOperatingSystem().startsWith("Windows NT 4")))
			pos = DataPacker.putString("WrLehDz", params, 2, true);
		else
			// pos = DataPacker.putString ( "WrLehDz", params, 2, true);
			pos = DataPacker.putString("WrLehD0", params, 2, true);

		pos = DataPacker.putString("B16", params, pos, true);

		DataPacker.putIntelShort(ServerInfo.InfoLevel0, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize - SMBPacket.TRANS_HEADERLEN, params, pos);
		pos += 2;
		DataPacker.putIntelInt(flags, params, pos);
		pos += 4;

		// Set the domain enumeration flag

		boolean domainEnum = false;

		if ( (flags & ServerType.DomainEnum) != 0)
			domainEnum = true;

		// Check for the domain enum flag, if set then specify a null domain
		// name string.

		if ( m_sess.getDomain() == null || domainEnum == true)
			pos = DataPacker.putString("", params, pos, true);
		else
			pos = DataPacker.putString(m_sess.getDomain(), params, pos, true);

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 8); // maximum parameter bytes to return, 4 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new java.io.IOException("Transaction failed");

		// Unpack the parameter block

		short[] prms = new short[4];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter
		int nsrv = (int) prms[2]; // number of server infos in this packet
		int totsrv = (int) prms[3]; // total server infos

		// Create a vector to return the server names

		Vector srvList = new Vector();

		// Unpack the server information structures

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		while (nsrv-- > 0) {

			// Unpack the server information structure

			Vector objs = new Vector();
			pos = DataDecoder.DecodeData(buf, pos, "B16", objs, conv);

			// Get the server name string, and add it to the list

			String name = (String) objs.elementAt(0);
			srvList.addElement(name);
		}

		// Return the server name list

		return new StringList(srvList);
	}

	/**
	 * Return a list of services installed on the remote node, using the older RAP call.
	 * 
	 * @return List of service name strings.
	 * @exception SMBException SMB error occurred.
	 * @exception IOException I/O exception.
	 */
	private final StringList getRAPServiceList()
		throws SMBException, java.io.IOException {

		// Create an SMB transaction packet for the service enum request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetUserGetInfo parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.NetServiceEnum, params, 0);
		int pos = DataPacker.putString("WrLeh", params, 2, true);
		pos = DataPacker.putString("B16WDWB64", params, pos, true);
		DataPacker.putIntelShort(ServiceInfo2, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 8); // maximum parameter bytes to return, 4 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[4];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter

		// Unpack the user information structure

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the service information structure, padding bytes are indicated by
		// '.'s and will not return objects

		Vector srvList = new Vector();

		int cnt = (int) prms[2];

		while (cnt-- > 0) {

			// Unpack the service information structure

			Vector objs = new Vector();
			pos = DataDecoder.DecodeData(buf, pos, "B16WDWB64", objs, conv);

			// Copy the values to a full service information object

			RAPServiceInfo srvInfo = new RAPServiceInfo(ServiceInfo2, objs);

			// Add the service info to the list

			srvList.addElement(srvInfo);
		}

		// Return the service information

		return new StringList(srvList);
	}

	/**
	 * Return a list of open sessions on the remote server, using the older RAP call.
	 * 
	 * @return SessionInfoList
	 * @exception SMBException If an SMB exception occurs
	 * @exception IOException If an I/O error occurs
	 */
	private final SessionInfoList getRAPSessionList()
		throws SMBException, java.io.IOException {

		// Create an SMB transaction packet for the session enum request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetSessionEnum parameter block

		byte[] params = new byte[128];
		DataPacker.putIntelShort(PacketTypeV1.RAPSessionEnum, params, 0);
		int pos = DataPacker.putString("WrLeh", params, 2, true);
		pos = DataPacker.putString("zzWWWDDD", params, pos, true);

		DataPacker.putIntelShort(SessionInfo.InfoLevel1, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 8); // maximum parameter bytes to return, 4 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new java.io.IOException("Transaction failed");

		// Unpack the parameter block

		short[] prms = new short[4];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter
		int nsess = (int) prms[2]; // number of session infos in this packet
		int totsess = (int) prms[3]; // total session infos

		// Create a vector to return the session info objects

		SessionInfoList sessList = new SessionInfoList();

		// Unpack the session information structures

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		while (nsess-- > 0) {

			// Unpack the session information structure

			Vector objs = new Vector();
			pos = DataDecoder.DecodeData(buf, pos, "zzWWWDDD", objs, conv);

			// Copy the values to a full session information object

			RAPSessionInfo sessInfo = new RAPSessionInfo(SessionInfo.InfoLevel1, objs);

			// Add the session info to the list

			sessList.addSession(sessInfo);
		}

		// Return the session information list

		return sessList;
	}

	/**
	 * Return the full share information for the specified share, using the older RAP call.
	 * 
	 * @param shr Remote share name to return information for.
	 * @return ShareInfo containing the full share details
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	private final ShareInfo getRAPShareInfo(String shr)
		throws java.io.IOException, SMBException {

		// Create an SMB transaction packet for the get share info request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetShareGetInfo parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.RAPShareGetInfo, params, 0);
		int pos = DataPacker.putString("zWrLh", params, 2, true);
		pos = DataPacker.putString("B13BWzWWWzB9B", params, pos, true);
		pos = DataPacker.putString(shr, params, pos, true);
		DataPacker.putIntelShort(ShareInfo.InfoLevel2, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 6); // maximum parameter bytes to return, 3 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[3];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter

		// Unpack the share information structure

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the share information structure, padding bytes are indicated by
		// '.'s and will not return objects

		Vector objs = new Vector();
		DataDecoder.DecodeData(buf, pos, "B13.WzWWWzB9.", objs, conv);

		// Create the share information object

		return new RAPShareInfo(ShareInfo.InfoLevel2, objs);
	}

	/**
	 * Return the list of available shares on the remote server, using the older RAP call.
	 * 
	 * @return List of available shares, as a ShareList, else null if there are no shares available.
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException SMB error
	 */
	private final ShareInfoList getRAPShareList()
		throws java.io.IOException, SMBException {

		// Create an SMB transaction packet for the share enum request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetShareEnum parameter block

		byte[] params = new byte[64];
		DataPacker.putIntelShort(PacketTypeV1.RAPShareEnum, params, 0);
		int pos = DataPacker.putString("WrLeh", params, 2, true);
		pos = DataPacker.putString("B13BWz", params, pos, true);
		DataPacker.putIntelShort(ShareInfo.InfoLevel1, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 8); // maximum parameter bytes to return, 4 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[4];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter
		int nshr = (int) prms[2]; // number of share infos in this packet
		int totshr = (int) prms[3]; // total share infos

		// Check if the status indicates more data, if so then ping the server so
		// that the remaining data is discarded (?)

		if ( prms[0] == SMBStatus.Win32MoreData)
			m_sess.pingServer(1);

		// Unpack the share information structures

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();
		Vector objs = new Vector();
		ShareInfoList shrList = new ShareInfoList();

		while (nshr-- > 0) {

			// Unpack a share information structure, padding bytes are indicated by
			// '.'s and will not return objects

			pos = DataDecoder.DecodeData(buf, pos, "B13.Wz", objs, conv);

			// Create the share information object and add to the list

			ShareInfo shrinfo = new RAPShareInfo(ShareInfo.InfoLevel1, objs);
			shrList.addShare(shrinfo);

			// Clear the current share data

			objs.removeAllElements();
		}

		// Return the share list

		return shrList;
	}

	/**
	 * Get a list of groups for the specified user, using the RAP call.
	 * 
	 * @param userName java.lang.String USer name to return group list for.
	 * @return List of group names.
	 * @exception SMBException If an SMB error occurs.
	 * @exception IOException If an I/O error occurs.
	 */
	public final StringList getRAPUserGroups(String userName)
		throws SMBException, java.io.IOException {

		// Create an SMB transaction packet for the get group users info request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetUserGetGroups parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.NetUserGetGroups, params, 0);
		int pos = DataPacker.putString("zWrLeh", params, 2, true);
		pos = DataPacker.putString("B21", params, pos, true);
		pos = DataPacker.putString(userName, params, pos, true);
		DataPacker.putIntelShort(GroupInfo0, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 8); // maximum parameter bytes to return, 4 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[4];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter

		// Unpack the user information structure

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the user information structure, padding bytes are indicated by
		// '.'s and will not return objects

		Vector usrList = new Vector();

		int cnt = (int) prms[3];

		while (cnt-- > 0) {

			// Decode a user name string from the return buffer

			pos = DataDecoder.DecodeData(buf, pos, "B21", usrList, conv);
		}

		// Return the user information

		return new StringList(usrList);
	}

	/**
	 * Return the user information for the specified user, using the older RAP call.
	 * 
	 * @param usr User name of the user to return information for.
	 * @return UserInfo containing the user details
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	private final UserInfo getRAPUserInfo(String usr)
		throws java.io.IOException, SMBException {

		// Create an SMB transaction packet for the get user info request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetUserGetInfo parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.RAPUserGetInfo, params, 0);
		int pos = DataPacker.putString("zWrLh", params, 2, true);
		pos = DataPacker.putString("B21BzzzWDDzzDDWWzWzDWB21W", params, pos, true);
		pos = DataPacker.putString(usr, params, pos, true);
		DataPacker.putIntelShort(UserInfo11, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 6); // maximum parameter bytes to return, 3 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[3];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = prms[1] - pkt.getDataOffset(); // offset converter

		// Unpack the user information structure

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the user information structure, padding bytes are indicated by
		// '.'s and will not return objects

		Vector objs = new Vector();
		DataDecoder.DecodeData(buf, pos, "B21.zzzWDDz.4TTWWzWzDWB21W", objs, conv);

		// Copy the values to a user information object

		return new RAPUserInfo(UserInfo11, objs);
	}

	/**
	 * Return the list of users on the remote server, using the older RAP call.
	 * 
	 * @return Vector of user name strings.
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	private final StringList getRAPUserList()
		throws java.io.IOException, SMBException {

		// Create an SMB transaction packet for the get user info request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetUserGetInfo parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.NetUserEnum, params, 0);
		int pos = DataPacker.putString("WrLeh", params, 2, true);
		pos = DataPacker.putString("B21", params, pos, true);
		DataPacker.putIntelShort(UserInfo0, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 8); // maximum parameter bytes to return, 4 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[4];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter

		// Unpack the user information structure

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the user information structure, padding bytes are indicated by
		// '.'s and will not return objects

		Vector usrList = new Vector();

		int cnt = (int) prms[3];

		while (cnt-- > 0) {

			// Decode a user name string from the return buffer

			pos = DataDecoder.DecodeData(buf, pos, "B21", usrList, conv);
		}

		// Return the user information

		return new StringList(usrList);
	}

	/**
	 * Return the server type/information for the server we are connected to, using the older RAP
	 * call.
	 * 
	 * @return WorkStationInfo containing the information
	 * @exception IOException If an I/O error occurs
	 * @exception SMBException If an SMB error occurs
	 */
	private final WorkstationInfo getRAPWorkstationInfo()
		throws java.io.IOException, SMBException {

		// Create an SMB transaction packet for the get server info request

		ClientTransPacket pkt = new ClientTransPacket(m_defBufSize);
		pkt.setTransactionName("\\PIPE\\LANMAN");

		// Build the NetServerGetInfo parameter block

		byte[] params = new byte[256];
		DataPacker.putIntelShort(PacketTypeV1.RAPWkstaGetInfo, params, 0);
		int pos = DataPacker.putString("WrLh", params, 2, true);
		pos = DataPacker.putString("zzzBBzz", params, pos, true);
		DataPacker.putIntelShort(WorkStation10, params, pos);
		pos += 2;
		DataPacker.putIntelShort(m_defBufSize, params, pos);
		pos += 2;

		// Initialize the transaction packet

		pkt.InitializeTransact(m_sess, 14, params, pos, null, 0);

		// Set various transaction parameters

		pkt.setParameter(2, 6); // maximum parameter bytes to return, 3 shorts
		pkt.setParameter(3, m_defBufSize - SMBPacket.TRANS_HEADERLEN);
		// maximum data bytes to return

		// Set the user id and tree id

		pkt.setUserId(m_sess.getUserId());
		pkt.setTreeId(m_sess.getTreeId());

		// Exchanged the SMB packet

		pkt.ExchangeSMB(m_sess, pkt);

		// Check if we received a valid response

		if ( pkt.isValidResponse() == false)
			throw new SMBException(pkt.getErrorClass(), pkt.getErrorCode());

		// Unpack the parameter block

		short[] prms = new short[3];
		pkt.getParameterBlock(prms);

		if ( prms[0] != 0 && prms[0] != SMBStatus.Win32MoreData)
			throw new SMBException(SMBStatus.NetErr, prms[0]);

		int conv = (int) prms[1] - pkt.getDataOffset(); // offset converter

		// Unpack the share information structure

		byte[] buf = pkt.getBuffer();
		pos = pkt.getDataOffset();

		// Unpack the server information structure, padding bytes are indicated by
		// '.'s and will not return objects

		Vector objs = new Vector();
		DataDecoder.DecodeData(buf, pos, "zzzBBzz", objs, conv);

		// Create the workstation information object to return

		return new RAPWorkstationInfo(WorkStation10, objs);
	}
}