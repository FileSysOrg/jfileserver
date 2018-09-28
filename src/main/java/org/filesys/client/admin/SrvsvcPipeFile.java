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

import java.io.*;

import org.filesys.client.IPCSession;
import org.filesys.smb.SMBException;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.Srvsvc;
import org.filesys.smb.dcerpc.client.DCEPacket;
import org.filesys.smb.dcerpc.info.ConnectionInfoList;
import org.filesys.smb.dcerpc.info.ServerFileInfoList;
import org.filesys.smb.dcerpc.info.ServerInfo;
import org.filesys.smb.dcerpc.info.SessionInfoList;
import org.filesys.smb.dcerpc.info.ShareInfo;
import org.filesys.smb.dcerpc.info.ShareInfoList;

/**
 * Server Service Pipe File Class
 * 
 * <p>
 * Pipe file connected to a remote file server DCE/RPC service that can be used to receive
 * information about the remote server such as the list of available shares, and active sessions.
 * 
 * @author gkspencer
 */
public class SrvsvcPipeFile extends IPCPipeFile {

	/**
	 * Class constructor
	 * 
	 * @param sess SMBIPCSession
	 * @param pkt DCEPacket
	 * @param handle int
	 * @param name String
	 * @param maxTx int
	 * @param maxRx int
	 */
	public SrvsvcPipeFile(IPCSession sess, DCEPacket pkt, int handle, String name, int maxTx, int maxRx) {
		super(sess, pkt, handle, name, maxTx, maxRx);
	}

	/**
	 * Return the servier information
	 * 
	 * @return ServerInfo
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ServerInfo getServerInformation()
		throws IOException, SMBException {

		// Build the remote server name string

		String remName = "\\\\" + getSession().getPCShare().getNodeName();

		// Build the get server information request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putPointer(true);
		buf.putString(remName, DCEBuffer.ALIGN_INT, true);
		buf.putInt(101); // information level

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Srvsvc.NetrServerGetInfo, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the get server information request

		doDCERequest(pkt);

		// Retrieve the server information from the response

		DCEBuffer rxBuf = getRxBuffer();
		ServerInfo srvInfo = new ServerInfo();

		try {
			checkStatus(rxBuf.getStatusCode());
			srvInfo.readObject(rxBuf);
		}
		catch (DCEBufferException ex) {
		}

		// Return the server information

		return srvInfo;
	}

	/**
	 * Return a list of normal shares available on the server, ie. not admin shares that end with
	 * '$'
	 * 
	 * @return ShareInfoList
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ShareInfoList getShareList()
		throws IOException, SMBException {

		// Return the share list

		return getShareList(false);
	}

	/**
	 * Return a list of the shares available on the server
	 * 
	 * @param wantAdmin boolean
	 * @return ServerShareInfoList
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ShareInfoList getShareList(boolean wantAdmin)
		throws IOException, SMBException {

		// Build the remote server name string

		String remName = "\\\\" + getSession().getPCShare().getNodeName();

		// Build the get share list request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putPointer(true);
		buf.putString(remName, DCEBuffer.ALIGN_INT, true);
		buf.putInt(1); // information level
		buf.putInt(1); // information level (again)

		// Empty share container

		buf.putPointer(true);
		buf.putInt(0); // number of entries
		buf.putPointer(false); // shares array
		buf.putInt(-1); // preferred length
		buf.putInt(0); // enum handle

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), wantAdmin ? Srvsvc.NetrShareEnum : Srvsvc.NetrShareEnumSticky, buf,
					getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the get share list request

		doDCERequest(pkt);

		// Retrieve the share list from the response

		DCEBuffer rxBuf = getRxBuffer();
		ShareInfoList shrList = null;

		try {
			checkStatus(buf.getStatusCode());
			shrList = new ShareInfoList(rxBuf);
			shrList.readList(rxBuf);
		}
		catch (DCEBufferException ex) {
		}

		// Return the share list

		return shrList;
	}

	/**
	 * Return detailed information for a share
	 * 
	 * @param shareName String
	 * @return ShareInfo
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ShareInfo getShareInformation(String shareName)
		throws IOException, SMBException {

		// Return the default information level

		return getShareInformation(shareName, ShareInfo.InfoLevel2);
	}

	/**
	 * Return detailed information for a share
	 * 
	 * @param shareName String
	 * @param infoLevel int
	 * @return ShareInfo
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ShareInfo getShareInformation(String shareName, int infoLevel)
		throws IOException, SMBException {

		// Build the remote server name string

		String remName = "\\\\" + getSession().getPCShare().getNodeName();

		// Build the get share information request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putPointer(true);
		buf.putString(remName, DCEBuffer.ALIGN_INT, true);
		buf.putString(shareName, DCEBuffer.ALIGN_INT, true);
		buf.putInt(infoLevel);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Srvsvc.NetrShareGetInfo, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the get share information request

		doDCERequest(pkt);

		// Retrieve the share information from the response

		DCEBuffer rxBuf = getRxBuffer();
		ShareInfo shrInfo = new ShareInfo(infoLevel);

		try {
			checkStatus(rxBuf.getStatusCode());

			// Read the returned information level and pointer

			rxBuf.getInt();
			if ( rxBuf.getPointer() != 0) {
				shrInfo.readObject(rxBuf);
				shrInfo.readStrings(rxBuf);
			}
		}
		catch (DCEBufferException ex) {
		}

		// Return the share information

		return shrInfo;
	}

	/**
	 * Return a list of the active sessions
	 * 
	 * @param clientName String
	 * @param userName String
	 * @return SessionList
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final SessionInfoList getSessionList(String clientName, String userName)
		throws IOException, SMBException {

		// Build the remote server name string

		String remName = "\\\\" + getSession().getPCShare().getNodeName();

		// Build the get open file information request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putPointer(true);
		buf.putString(remName, DCEBuffer.ALIGN_INT, true);

		// Check if only sessions for a particular client should be returned

		if ( clientName != null) {
			buf.putPointer(true);
			buf.putString(clientName, DCEBuffer.ALIGN_INT, true);
		}
		else
			buf.putPointer(false);

		// Check if only sessions for a particular user should be returned

		if ( userName != null) {
			buf.putPointer(true);
			buf.putString(userName, DCEBuffer.ALIGN_INT, true);
		}
		else
			buf.putPointer(false);

		buf.putInt(2); // information level
		buf.putInt(2); // "        "

		buf.putPointer(true); // session info container
		buf.putInt(0);
		buf.putPointer(false);

		buf.putInt(-1); // preferred size

		buf.putPointer(false); // enum handle

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Srvsvc.NetrSessionEnum, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the get session list request

		doDCERequest(pkt);

		// Retrieve the session list from the response

		DCEBuffer rxBuf = getRxBuffer();
		SessionInfoList sessList = null;

		try {
			checkStatus(rxBuf.getStatusCode());
			sessList = new SessionInfoList(rxBuf);
			sessList.readList(rxBuf);
		}
		catch (DCEBufferException ex) {
		}

		// Return the session list

		return sessList;
	}

	/**
	 * Return a list of the active connections
	 * 
	 * @param clientOrShare String
	 * @return ConnectionList
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ConnectionInfoList getConnectionList(String clientOrShare)
		throws IOException, SMBException {

		// Build the remote server name string

		String remName = "\\\\" + getSession().getPCShare().getNodeName();

		// Build the get connection list request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putPointer(true);
		buf.putString(remName, DCEBuffer.ALIGN_INT, true);

		buf.putPointer(true); // share name or client name if it begins with '\\'
		buf.putString(clientOrShare, DCEBuffer.ALIGN_INT, true);

		buf.putInt(1); // information level
		buf.putInt(1); // "        "

		buf.putPointer(true); // connection info container
		buf.putInt(0);
		buf.putPointer(false);

		buf.putInt(-1); // preferred size

		buf.putPointer(false); // enum handle

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Srvsvc.NetrConnectionEnum, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the get open file information request

		doDCERequest(pkt);

		// Retrieve the open file information from the response

		DCEBuffer rxBuf = getRxBuffer();
		ConnectionInfoList connList = null;

		try {
			checkStatus(rxBuf.getStatusCode());
			connList = new ConnectionInfoList(rxBuf);
			connList.readList(rxBuf);
		}
		catch (DCEBufferException ex) {
		}

		// Return the connectio information list

		return connList;
	}

	/**
	 * Return a list of the open files
	 * 
	 * @return ServerFileInfoList
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ServerFileInfoList getOpenFileList()
		throws IOException, SMBException {

		// Build the remote server name string

		String remName = "\\\\" + getSession().getPCShare().getNodeName();

		// Build the get open file information request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putPointer(true);
		buf.putString(remName, DCEBuffer.ALIGN_INT, true);

		buf.putPointer(false); // path
		buf.putPointer(false); // user

		buf.putInt(3); // information level
		buf.putInt(3); // "        "

		buf.putPointer(true); // file info container
		buf.putInt(0);
		buf.putPointer(false);

		buf.putInt(-1); // preferred size

		buf.putPointer(false); // enum handle

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Srvsvc.NetrFileEnum, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the get open file information request

		doDCERequest(pkt);

		// Retrieve the open file information from the response

		DCEBuffer rxBuf = getRxBuffer();
		ServerFileInfoList fileList = null;

		try {
			checkStatus(rxBuf.getStatusCode());
			fileList = new ServerFileInfoList(rxBuf);
			fileList.readList(rxBuf);
		}
		catch (DCEBufferException ex) {
		}

		// Return the open file information

		return fileList;
	}
}
