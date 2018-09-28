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

import org.filesys.client.SessionFactory;
import org.filesys.client.admin.AdminSession;
import org.filesys.client.admin.SvcctlPipeFile;
import org.filesys.smb.PCShare;
import org.filesys.smb.dcerpc.info.NTService;
import org.filesys.smb.dcerpc.info.ServiceStatusInfo;
import org.filesys.smb.dcerpc.info.ServiceStatusList;
import org.filesys.util.NameValue;
import org.filesys.util.NameValueList;

/**
 * srvServices Application
 * 
 * <p>Display a list of services on a remote server.
 * 
 * @author gkspencer
 */
public class srvServices extends BaseApp {

	//	Service type filter strings
	private final static String[] _svcTypesStr = { "All", "Driver", "FileSys", "Adapter", "Interactive" };
	private final static int[] _svcTypes       = { NTService.TypeAll,
      											 NTService.TypeDriver,
      											 NTService.TypeFileSystem,
      											 NTService.TypeAdapter,
      											 NTService.TypeInteractive
	};
  
	//	Service type filter, defaults to display all services
	private int m_serviceType = NTService.TypeAll;
 
	/**
	 * Class constructor
	 */
	public srvServices() {
		super("srvServices", "List services on a remote server");
	}

	/**
	 * Display the usage information for the command
	 * 
	 * @param out PrintStream
	 */
	protected void outputCommandHelp(PrintStream out) {

		// Output the srvServices command help
		out.println("Usage: srvServices <host>|<ip_address> [<service_type>] -username=<username> -password=<password>");
		out.println("  <service_type> may be one of the following :-");
		out.println("    All         Display all services. [Default]");
		out.println("    Driver      Device driver services");
		out.println("    FileSys     Filesystem services");
		out.println("    Adapter     Network adapter services");
		out.println("    Interactive Services that interact with the desktop");
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

		SvcctlPipeFile svcPipe = sess.openServiceManagerPipe();
		if ( svcPipe == null)
			throw new Exception("Failed to open service control pipe, SvcCtl");

		// Retrieve the service status list
		ServiceStatusList stsList = svcPipe.getServiceList(getServiceType(), NTService.EnumAll);

		if ( stsList != null && stsList.numberOfServices() > 0) {

			// Output column headers
			formatOutput(System.out, "Display Name", 40, "Name", 20, "Type", 15, "State", -1);
			formatOutput(System.out, "------------", 40, "----", 20, "----", 15, "-----", -1);

			// Output the service status details
			for (int i = 0; i < stsList.numberOfServices(); i++) {

				// Get the current service status details
				ServiceStatusInfo stsInfo = stsList.getInfo(i);

				// Output the service status
				formatOutput(System.out, stsInfo.getDisplayName(), 40, stsInfo.getName(), 20, NTService.getTypeAsString(stsInfo
						.getType()), 15, NTService.getStateAsString(stsInfo.getCurrentState()), 20);
			}
		}
		else
			out.println("No services to display");

		// Close the pipe/session
		svcPipe.ClosePipe();
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
		else if ( cmdLine.numberOfItems() > 5) {
			out.println("%% Too many command line arguments");
			return false;
		}

		// Get the host name/address argument
		NameValue arg1 = cmdLine.findItem(CmdLineArg1);
		if ( arg1 == null || arg1.getValue() == null || arg1.getValue().length() == 0) {
			out.println("%% Invalid host name specified, " + arg1 != null ? arg1.getValue() : "");
			return false;
		}

		// Check if the optional service type parameter has been specified
		NameValue arg2 = cmdLine.findItem(CmdLineArg2);
		if ( arg2 != null) {

			// Validate the service type value
			int svcType = -1;

			for (int i = 0; i < _svcTypesStr.length; i++) {

				// Check if the service type matches
				if ( _svcTypesStr[i].equalsIgnoreCase(arg2.getValue()))
					svcType = _svcTypes[i];
			}

			// Check if the service type is valid
			if ( svcType == -1) {
				out.println("%% Invalid service type - " + arg2.getValue());
				return false;
			}

			// Save the service type
			setServiceType(svcType);
		}

		// Initialize the share details
		PCShare share = new PCShare(arg1.getValue(), "IPC$", getUserName(), getPassword());

		// Save the share for the main processing
		setShare(share);

		// Return a success status
		return true;
	}

	/**
	 * Return the service type filter value
	 * 
	 * @return int
	 */
	protected final int getServiceType() {
		return m_serviceType;
	}

	/**
	 * Set the service type filter
	 * 
	 * @param filter int
	 */
	protected final void setServiceType(int filter) {
		m_serviceType = filter;
	}

	/**
	 * Run the srvServices command
	 * 
	 * @param args String[]
	 */
	public static void main(String[] args) {

		// Create the command object and run the command
		srvServices cmd = new srvServices();
		cmd.runCommand(args);
	}
}
