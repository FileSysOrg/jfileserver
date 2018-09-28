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
import org.filesys.smb.SMBStatus;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.PolicyHandle;
import org.filesys.smb.dcerpc.client.DCEPacket;
import org.filesys.smb.dcerpc.client.Svcctl;
import org.filesys.smb.dcerpc.info.NTService;
import org.filesys.smb.dcerpc.info.ServiceConfigInfo;
import org.filesys.smb.dcerpc.info.ServiceStatusExList;
import org.filesys.smb.dcerpc.info.ServiceStatusInfo;
import org.filesys.smb.dcerpc.info.ServiceStatusList;
import org.filesys.util.StringList;

/**
 * Service Control Pipe File Class
 * 
 * <p>
 * Pipe file connected to a remote service manager DCE/RPC service that can be used to retrieve
 * information about remote NT services, start and stop services, and perform other service
 * requests.
 * 
 * @author gkspencer
 */
public class SvcctlPipeFile extends IPCPipeFile {

	// Service manager handle

	private ServiceManagerHandle m_handle;

	/**
	 * Class constructor
	 * 
	 * @param sess SMBIPCSession
	 * @param pkt DCEPacket
	 * @param handle int
	 * @param name String
	 * @param maxTx int
	 * @param maxRx int
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public SvcctlPipeFile(IPCSession sess, DCEPacket pkt, int handle, String name, int maxTx, int maxRx) throws IOException,
			SMBException {
		super(sess, pkt, handle, name, maxTx, maxRx);

		// Open the service manager

		m_handle = openServiceControlManager();
	}

	/**
	 * Open the service control manager on the remote server
	 * 
	 * @return ServiceManagerHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	protected final ServiceManagerHandle openServiceControlManager()
		throws IOException, SMBException {

		// Open the service control manager with default access rights

		return openServiceControlManager(Svcctl.ScManagerAllAccess);
	}

	/**
	 * Open the service control manager on the remote server
	 * 
	 * @param accessMode int
	 * @return ServiceManagerHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	protected final ServiceManagerHandle openServiceControlManager(int accessMode)
		throws IOException, SMBException {

		// Build the remote server name string

		String remName = getSession().getPCShare().getNodeName();

		// Build the open service control manager request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putPointer(true);
		buf.putString(remName, DCEBuffer.ALIGN_INT, true); // Does not work unless we count the null
															// for the string
		buf.putPointer(false);
		buf.putInt(accessMode);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Svcctl.OpenSCManager, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the open service manager request

		doDCERequest(pkt);

		// Retrieve the policy handle from the response

		DCEBuffer rxBuf = getRxBuffer();
		ServiceManagerHandle handle = new ServiceManagerHandle(remName);

		try {
			checkStatus(rxBuf.getStatusCode());
			rxBuf.getHandle(handle);
		}
		catch (DCEBufferException ex) {
		}

		// Return the service manager handle

		return handle;
	}

	/**
	 * Create a new service with no dependencies and using the default LocalSystem account
	 * 
	 * @param serviceName String
	 * @param displayName String
	 * @param svcType From NTService.Type...
	 * @param startType From NTService.Start...
	 * @param errCtrl From NTService.Error...
	 * @param binPath String
	 * @return ServiceHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ServiceHandle createService(String serviceName, String displayName, int svcType, int startType, int errCtrl,
			String binPath)
		throws IOException, SMBException {

		// Call the main create service method

		return createService(serviceName, displayName, svcType, startType, errCtrl, binPath, null, null, null, null);
	}

	/**
	 * Create a new service
	 * 
	 * @param serviceName String
	 * @param displayName String
	 * @param svcType From NTService.Type...
	 * @param startType From NTService.Start...
	 * @param errCtrl From NTService.Error...
	 * @param binPath String
	 * @param loadGrp String
	 * @param depend StringList
	 * @param account String
	 * @param password byte[]
	 * @return ServiceHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ServiceHandle createService(String serviceName, String displayName, int svcType, int startType, int errCtrl,
											 String binPath, String loadGrp, StringList depend, String account, byte[] password)
		throws IOException, SMBException {

		// Build the create service request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		// Pack the service manager handle

		buf.putHandle(getManagerHandle());

		// Service name

		buf.putString(serviceName, DCEBuffer.ALIGN_INT, true);

		// Display name, if specified

		if ( displayName != null) {
			buf.putPointer(true);
			buf.putString(displayName, DCEBuffer.ALIGN_INT, true);
		}
		else
			buf.putPointer(false);

		// Various flags/controls

		// buf.putInt(AccessMode.NTGenericAll);
		// buf.putInt(0x0F01FF);
		buf.putInt(Svcctl.ScManagerAllAccess);
		buf.putInt(svcType);
		buf.putInt(startType);
		buf.putInt(errCtrl);

		// Service executable path

		buf.putString(binPath, DCEBuffer.ALIGN_INT, true);

		// Load group

		if ( loadGrp != null) {
			buf.putPointer(true);
			buf.putString(loadGrp, DCEBuffer.ALIGN_INT, true);
		}
		else
			buf.putPointer(false);

		// Tag id return, not specified

		buf.putPointer(false);

		// Dependencies, packed as a block of null terminated strings with a double null terminator

		if ( depend != null) {

			// Build the dependency list string

			StringBuffer depList = new StringBuffer();

			for (int i = 0; i < depend.numberOfStrings(); i++) {
				String curDep = (String) depend.getStringAt(i);
				depList.append(curDep);
				depList.append('\0');
			}

			// Add the terminating nulls

			depList.append('\0');

			// Pack the dependency list

			buf.putPointer(true);

			int bytLen = depList.length() * 2;
			buf.putInt(bytLen);
			buf.putUnicodeBytes(depList.toString(), DCEBuffer.ALIGN_INT);
			buf.putInt(bytLen);
		}
		else {
			buf.putPointer(false);
			buf.putPointer(false);
		}

		// Account name to run service under

		if ( account != null) {
			buf.putPointer(true);
			buf.putString(account, DCEBuffer.ALIGN_INT, true);
		}
		else
			buf.putPointer(false);

		// Password

		if ( password != null) {
			buf.putPointer(true);
			buf.putInt(password.length); // password length
		}
		else
			buf.putPointer(false);

		// Not sure what these are yet

		buf.putPointer(false);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Svcctl.CreateService, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the create service request

		doDCERequest(pkt);

		// Retrieve the policy handle from the response

		buf = getRxBuffer();
		ServiceHandle srvHandle = new ServiceHandle(serviceName);

		try {

			// Check the status

			checkStatus(buf.getStatusCode());

			// Skip unknown value

			buf.getInt();

			// Get the returned service handle

			buf.getHandle(srvHandle);
		}
		catch (DCEBufferException ex) {
		}

		// Return the service handle

		return srvHandle;
	}

	/**
	 * Delete a remote service
	 * 
	 * @param handle ServiceHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void deleteService(ServiceHandle handle)
		throws IOException, SMBException {

		// Build the delete service request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		// Pack the service manager handle

		buf.putHandle(handle);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Svcctl.DeleteService, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the delete service request

		doDCERequest(pkt);

		// Check the returned status

		buf = getRxBuffer();

		try {
			checkStatus(buf.getInt());
		}
		catch (DCEBufferException ex) {
		}
	}

	/**
	 * Open a service on the remote server
	 * 
	 * @param serviceName String
	 * @return ServiceHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ServiceHandle openService(String serviceName)
		throws IOException, SMBException {

		// Open the service with default access rights

		return openService(serviceName, Svcctl.ServiceQueryConfig + Svcctl.ServiceQueryStatus);
	}

	/**
	 * Open a service on the remote server
	 * 
	 * @param serviceName String
	 * @param accessMode int
	 * @return ServiceHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ServiceHandle openService(String serviceName, int accessMode)
		throws IOException, SMBException {

		// Build the open service request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(getManagerHandle());
		buf.putString(serviceName, DCEBuffer.ALIGN_INT, true);
		buf.putInt(accessMode);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Svcctl.OpenService, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the open service request

		doDCERequest(pkt);

		// Retrieve the policy handle from the response

		buf = getRxBuffer();
		ServiceHandle srvHandle = new ServiceHandle(serviceName);

		try {
			checkStatus(buf.getStatusCode());
			buf.getHandle(srvHandle);
		}
		catch (DCEBufferException ex) {
		}

		// Return the service handle

		return srvHandle;
	}

	/**
	 * Return the remote service configuration details
	 * 
	 * @param handle ServiceHandle
	 * @return ServiceConfigInfo
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ServiceConfigInfo getServiceConfiguration(ServiceHandle handle)
		throws IOException, SMBException {

		// Build the query service configuration request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(handle);
		buf.putInt(2048);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Svcctl.QueryServiceConfig, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the query service configuration request

		doDCERequest(pkt);

		// Retrieve the service configuration details

		DCEBuffer rxBuf = getRxBuffer();

		ServiceConfigInfo info = new ServiceConfigInfo();
		try {
			checkStatus(rxBuf.getStatusCode());
			info.readObject(rxBuf);
		}
		catch (DCEBufferException ex) {
		}

		return info;
	}

	/**
	 * Set the remote service configuration details
	 * 
	 * @param handle ServiceHandle
	 * @param config ServiceConfigInfo
	 * @return int
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 * @exception DCEBufferException DCE buffer error
	 */
	public final int setServiceConfiguration(ServiceHandle handle, ServiceConfigInfo config)
		throws IOException, SMBException, DCEBufferException {

		// Build the set service configuration request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(handle);
		config.writeObject(buf, null);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Svcctl.ChangeServiceConfig, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the query service configuration request

		doDCERequest(pkt);

		// Check if the tag id was returned

		DCEBuffer rxBuf = getRxBuffer();
		checkStatus(rxBuf.getStatusCode());

		int tagId = -1;

		if ( rxBuf.getPointer() != 0) {

			// Read the allocated tag id

			tagId = rxBuf.getInt();
		}

		// Return the tag id

		return tagId;
	}

	/**
	 * Return the service status list for services matching the specified type and state on the
	 * remote server.
	 * 
	 * @param typ int
	 * @param state int
	 * @return ServiceStatusList
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ServiceStatusList getServiceList(int typ, int state)
		throws IOException, SMBException {

		// Loop until all services have been returned, this may require multiple requests

		ServiceStatusList stsList = new ServiceStatusList();
		int sts = -1;

		do {

			// Build the enumerate service request

			DCEBuffer buf = getBuffer();
			buf.resetBuffer();

			buf.putHandle(getManagerHandle());
			buf.putInt(typ);
			buf.putInt(state);
			buf.putInt(16384);
			buf.putPointer(true);
			buf.putInt(stsList.getMultiPartHandle());

			// Initialize the DCE request

			DCEPacket pkt = getPacket();
			try {
				pkt.initializeDCERequest(getHandle(), Svcctl.EnumServiceStatus, buf, getMaximumTransmitSize(), getNextCallId());
			}
			catch (DCEBufferException ex) {
				ex.printStackTrace();
			}

			// Send the enumerate service status request and receive the response data

			doDCERequest(pkt);

			// Get the request status

			sts = getRxBuffer().getStatusCode();

			// Create a service status list and populate from the received data

			try {
				stsList.readObject(getRxBuffer());
			}
			catch (DCEBufferException ex) {
			}

		} while (sts == SMBStatus.NETContinued);

		// Return the service status list

		return stsList;
	}

	/**
	 * Return the service status for the specified service
	 * 
	 * @param handle ServiceHandle
	 * @return ServiceStatusInfo
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ServiceStatusInfo getServiceStatus(ServiceHandle handle)
		throws IOException, SMBException {

		// Build the get service status request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(handle);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Svcctl.QueryServiceStatus, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the get service status request and receive the response data

		doDCERequest(pkt);

		// Create a service status list and populate from the received data

		ServiceStatusInfo srvInfo = new ServiceStatusInfo();
		buf = getRxBuffer();

		try {
			checkStatus(buf.getStatusCode());
			srvInfo.readObject(buf);
		}
		catch (DCEBufferException ex) {
		}

		// Return the service status info

		return srvInfo;
	}

	/**
	 * Start a service
	 * 
	 * @param handle ServiceHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void startService(ServiceHandle handle)
		throws IOException, SMBException {

		// Build the start service request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(handle);
		buf.putInt(0); // Number of arguments (?)
		buf.putPointer(false); // Argument pointer (?)

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Svcctl.StartService, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the get service status request and receive the response data

		doDCERequest(pkt);
	}

	/**
	 * Stop a service
	 * 
	 * @param handle ServiceHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void stopService(ServiceHandle handle)
		throws IOException, SMBException {

		// Stop the service

		controlService(handle, NTService.ServiceCtrlStop);
	}

	/**
	 * Pause a service
	 * 
	 * @param handle ServiceHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void pauseService(ServiceHandle handle)
		throws IOException, SMBException {

		// Pause the service

		controlService(handle, NTService.ServiceCtrlPause);
	}

	/**
	 * Resume a service
	 * 
	 * @param handle ServiceHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void resumeService(ServiceHandle handle)
		throws IOException, SMBException {

		// Resume the service

		controlService(handle, NTService.ServiceCtrlResume);
	}

	/**
	 * Service control
	 * 
	 * @param handle ServiceHandle
	 * @param ctrl int
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void controlService(ServiceHandle handle, int ctrl)
		throws IOException, SMBException {

		// Build the stop service request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(handle);
		buf.putInt(ctrl);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Svcctl.ControlService, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the get service status request and receive the response data

		doDCERequest(pkt);

		// Check the status code

		checkStatus(getRxBuffer().getStatusCode());
	}

	/**
	 * Close a remote service
	 * 
	 * @param handle ServiceHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void closeService(ServiceHandle handle)
		throws IOException, SMBException {

		// Close the service

		closeHandle(handle);
	}

	/**
	 * Close the remote service manager
	 * 
	 * @param handle ServiceManagerHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	protected final void closeServiceManager(ServiceManagerHandle handle)
		throws IOException, SMBException {

		// Close the service manager

		closeHandle(handle);
	}

	/**
	 * Return the service manager handle
	 * 
	 * @return ServiceManagerHandle
	 */
	protected final ServiceManagerHandle getManagerHandle() {
		return m_handle;
	}

	/**
	 * Close the pipe
	 *
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public void ClosePipe()
		throws IOException, SMBException {

		// Close the service manager handle

		if ( getManagerHandle() != null) {

			// Close the service manager handle

			closeServiceManager(m_handle);
			m_handle = null;
		}

		// Call the base class

		super.ClosePipe();
	}

	/**
	 * Close a remote handle
	 * 
	 * @param handle PolicyHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	private final void closeHandle(PolicyHandle handle)
		throws IOException, SMBException {

		// Build the close handle request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(handle);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Svcctl.Close, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the close handle request

		getSession().SendTransaction(pkt, pkt);
		if ( pkt.isValidResponse() == false)
			throw new SMBException(SMBStatus.NTErr, pkt.getLongErrorCode());
	}

	/**
	 * Return the service status list for services matching the specified type and state on the
	 * remote server.
	 * 
	 * @param typ int
	 * @param state int
	 * @return ServiceStatusExList
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final ServiceStatusExList getServiceListEx(int typ, int state)
		throws IOException, SMBException {

		// Loop until all services have been returned, this may require multiple requests

		ServiceStatusExList stsList = new ServiceStatusExList();
		int sts = -1;

		do {

			// Build the enumerate service request

			DCEBuffer buf = getBuffer();
			buf.resetBuffer();

			buf.putHandle(getManagerHandle());
			buf.putInt(0); // info level
			buf.putInt(typ);
			buf.putInt(state);
			buf.putInt(16384);
			buf.putPointer(true);
			buf.putInt(stsList.getMultiPartHandle());
			buf.putPointer(false);

			// Initialize the DCE request

			DCEPacket pkt = getPacket();
			try {
				pkt.initializeDCERequest(getHandle(), Svcctl.EnumServiceStatusEx, buf, getMaximumTransmitSize(), getNextCallId());
			}
			catch (DCEBufferException ex) {
				ex.printStackTrace();
			}

			// Send the enumerate service status request and receive the response data

			doDCERequest(pkt);

			// Get the request status

			sts = getRxBuffer().getStatusCode();

			// Create a service status list and populate from the received data

			try {
				stsList.readObject(getRxBuffer());
			}
			catch (DCEBufferException ex) {
			}

		} while (sts == SMBStatus.NETContinued);

		// Return the service status list

		return stsList;
	}
}
