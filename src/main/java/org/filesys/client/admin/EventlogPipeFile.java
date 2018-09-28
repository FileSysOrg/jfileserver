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
import org.filesys.smb.dcerpc.client.DCEPacket;
import org.filesys.smb.dcerpc.client.Eventlog;
import org.filesys.smb.dcerpc.info.EventlogRecordList;

/**
 * Eventlog Pipe File Class
 * 
 * <p>
 * Contains methods to access a remote eventlog service.
 * 
 * @author gkspencer
 */
public class EventlogPipeFile extends IPCPipeFile {

	// Constants

	private final static int DefaultBufferSize = 65000;

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
	public EventlogPipeFile(IPCSession sess, DCEPacket pkt, int handle, String name, int maxTx, int maxRx) {
		super(sess, pkt, handle, name, maxTx, maxRx);
	}

	/**
	 * Open the specified event log
	 * 
	 * @param name String
	 * @return EventlogHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final EventlogHandle openEventLog(String name)
		throws IOException, SMBException {

		// Build the open eventlog request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putPointer(true);
		buf.putShort(0x67);
		buf.putShort(0x01);

		buf.putUnicodeHeader(name, false);
		buf.putString(name, DCEBuffer.ALIGN_INT);

		buf.putUnicodeHeader(null, true);

		buf.putInt(1);
		buf.putInt(1);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Eventlog.OpenEventLog, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the open eventlog request

		doDCERequest(pkt);

		// Retrieve the handle from the response

		buf = getRxBuffer();
		EventlogHandle handle = new EventlogHandle(name);

		try {
			checkStatus(buf.getStatusCode());
			buf.getHandle(handle);
		}
		catch (DCEBufferException ex) {
		}

		return handle;
	}

	/**
	 * Return the number of available event log records for the specified log
	 * 
	 * @param handle EventlogHandle
	 * @return int
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final int getNumberOfRecords(EventlogHandle handle)
		throws IOException, SMBException {

		// Build the get record count request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(handle);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Eventlog.GetNumberOfRecords, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the get record count request

		doDCERequest(pkt);

		// Retrieve the record count from the response

		int recCnt = -1;
		try {
			buf = getRxBuffer();
			recCnt = buf.getInt();
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Return the record count

		return recCnt;
	}

	/**
	 * Return the oldest record number for the specified log
	 * 
	 * @param handle EventlogHandle
	 * @return int
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final int getOldestRecordNumber(EventlogHandle handle)
		throws IOException, SMBException {

		// Build the get oldest record number request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(handle);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Eventlog.GetOldestEventRecord, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the get oldest record number request

		doDCERequest(pkt);

		// Retrieve the record number from the response

		int recNo = -1;
		try {
			buf = getRxBuffer();
			recNo = buf.getInt();
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Return the record number

		return recNo;
	}

	/**
	 * Continue reading event log records
	 * 
	 * @param handle EventlogHandle
	 * @return EventLogRecordList
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final EventlogRecordList readContinueEventLog(EventlogHandle handle)
		throws IOException, SMBException {

		// Continue to read the event log from the last read point

		return readEventLog(handle, Eventlog.SequentialRead + Eventlog.BackwardsRead, 0);
	}

	/**
	 * Read event log records
	 * 
	 * @param handle EventlogHandle
	 * @param flags int
	 * @param recoff int
	 * @return EventLogRecordList
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final EventlogRecordList readEventLog(EventlogHandle handle, int flags, int recoff)
		throws IOException, SMBException {

		// Build the read event records request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(handle);
		buf.putInt(flags);
		buf.putInt(recoff); // record offset
		buf.putInt(DefaultBufferSize); // buffer size

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Eventlog.ReadEventLog, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the get record count request

		doDCERequest(pkt);

		// Retrieve the event record list

		EventlogRecordList recordList = null;

		try {
			recordList = new EventlogRecordList();
			recordList.readRecords(getRxBuffer(), 100);
		}
		catch (DCEBufferException ex) {
		}

		// Return the record list

		return recordList;
	}

	/**
	 * Close the event log
	 * 
	 * @param handle EventlogHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void closeEventlog(EventlogHandle handle)
		throws IOException, SMBException {

		// Build the close handle request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(handle);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Eventlog.CloseEventLog, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the close handle request

		doDCERequest(pkt);
	}
}
