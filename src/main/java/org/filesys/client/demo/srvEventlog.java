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

package org.filesys.client.demo;

import java.io.*;
import java.text.*;

import org.filesys.client.SessionFactory;
import org.filesys.client.admin.AdminSession;
import org.filesys.client.admin.EventlogHandle;
import org.filesys.client.admin.EventlogPipeFile;
import org.filesys.smb.PCShare;
import org.filesys.smb.dcerpc.client.Eventlog;
import org.filesys.smb.dcerpc.info.EventlogRecord;
import org.filesys.smb.dcerpc.info.EventlogRecordList;
import org.filesys.util.NameValue;
import org.filesys.util.NameValueList;

/**
 * srvEventlog Application
 * 
 * <p>
 * Display the last 50 event log entries on a remote server.
 * 
 * @author gkspencer
 */
public class srvEventlog extends BaseApp {

	// Constants
	//
	// Number of event log records to display
	private static final int DisplayCount = 50;

	// Event log to display (system, application or security)
	private String m_logType = Eventlog.EVTLOG_SYSTEM;

	/**
	 * Class constructor
	 */
	public srvEventlog() {
		super("srvEventlog", "Display the latest event log entries on a remote server");
	}

	/**
	 * Display the usage information for the command
	 * 
	 * @param out PrintStream
	 */
	protected void outputCommandHelp(PrintStream out) {

		// Output the srvEventlog command help
		out.println("Usage: srvEventlog <host>|<ip_address> [<logname>] -username=<username> -password=<password>");
		out.println("  <logname> may be one of the following :-");
		out.println("    System      For the system event log");
		out.println("    Application For the application event log");
		out.println("    Security    For the security event log");
	}

	/**
	 * Perform the main command processing
	 * 
	 * @param out PrintStream
	 * @throws Exception Error running the command
	 */
	protected void doCommand(PrintStream out)
		throws Exception {

		// Open an admin session to the remote server
		AdminSession sess = SessionFactory.OpenAdminSession(getShare());
		setSession(sess.getSession());

		EventlogPipeFile evtPipe = sess.openEventLogPipe();
		if ( evtPipe == null)
			throw new Exception("Failed to open service control pipe, Eventlog");

		// Open the remote event log
		EventlogHandle handle = evtPipe.openEventLog(getEventLogName());

		// Get the record count and oldest record number from the event log
		int recCnt = evtPipe.getNumberOfRecords(handle);
		int recNo = evtPipe.getOldestRecordNumber(handle) + recCnt;

		if ( recCnt > DisplayCount)
			recCnt = DisplayCount;

		// Read the event log
		EventlogRecordList recList = evtPipe
				.readEventLog(handle, Eventlog.SeekRead + Eventlog.ForwardsRead, (recNo - recCnt) - 1);
		if ( recList != null && recList.numberOfRecords() > 0) {

			// Create a formatter for the event date/time
			SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMMyyyy HH:mm:ss");

			// Output the column headers
			formatOutput(out, "Time", 19, "Source", 30, "Type", 15, "Text", 50);
			formatOutput(out, "----", 19, "------", 30, "----", 15, "----", 50);

			// Output the event log records
			for (int i = recList.numberOfRecords() - 1; i >= 0; i--) {

				// Get the current event log record
				EventlogRecord event = recList.getRecordAt(i);

				// Output the record
				formatOutput(out, dateFormat.format(event.getTimeGenerated()), 19, event.getEventSource(), 30, event
						.getEventTypeAsString(), 15, event.getEventString(), 50);
			}
		}
		else
			out.println("No records to display");

		// Close the eventlog
		evtPipe.closeEventlog(handle);

		// Close the pipe/session
		evtPipe.ClosePipe();
		sess.CloseSession();
		setSession(null);
	}

	/**
	 * Validate the command line parameters
	 * 
	 * @param cmdLine NameValueList
	 * @param out PrintStream
	 * @return boolean
	 */
	protected boolean validateCommandLine(NameValueList cmdLine, PrintStream out) {

		// Check if we have the correct number of parameters
		if ( cmdLine.numberOfItems() == 0) {
			out.println("%% Host name or address must be specified");
			return false;
		}
		else if ( cmdLine.numberOfItems() > 4) {
			out.println("%% Too many command line arguments");
			return false;
		}

		// Get the host name/address argument
		NameValue arg1 = cmdLine.findItem(CmdLineArg1);
		if ( arg1 == null || arg1.getValue() == null || arg1.getValue().length() == 0) {
			out.println("%% Invalid host name specified, " + arg1 != null ? arg1.getValue() : "");
			return false;
		}

		// Check if the optional event log name parameter has been specified
		NameValue arg2 = cmdLine.findItem(CmdLineArg2);
		if ( arg2 != null) {

			// Validate the event log name
			String logName = null;

			if ( arg2.getValue().equalsIgnoreCase(Eventlog.EVTLOG_SYSTEM))
				logName = Eventlog.EVTLOG_SYSTEM;
			else if ( arg2.getValue().equalsIgnoreCase(Eventlog.EVTLOG_APPLICATION))
				logName = Eventlog.EVTLOG_APPLICATION;
			else if ( arg2.getValue().equalsIgnoreCase(Eventlog.EVTLOG_SECURITY))
				logName = Eventlog.EVTLOG_SECURITY;
			else {
				out.println("%% Invalid event log name - " + arg2.getValue());
				return false;
			}

			// Save the event log name
			setEventLogName(logName);
		}

		// Initialize the share details
		PCShare share = new PCShare(arg1.getValue(), "IPC$", getUserName(), getPassword());

		// Save the share for the main processing
		setShare(share);

		// Return a success status
		return true;
	}

	/**
	 * Get the event log to be displayed
	 * 
	 * @return String
	 */
	protected final String getEventLogName() {
		return m_logType;
	}

	/**
	 * Set the event log to be displayed
	 * 
	 * @param type String
	 */
	protected final void setEventLogName(String type) {
		m_logType = type;
	}

	/**
	 * Run the srvEventlog command
	 * 
	 * @param args String[]
	 */
	public static void main(String[] args) {

		// Create the command object and run the command
		srvEventlog cmd = new srvEventlog();
		cmd.runCommand(args);
	}
}
