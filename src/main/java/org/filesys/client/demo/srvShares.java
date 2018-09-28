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
import org.filesys.smb.PCShare;
import org.filesys.smb.dcerpc.info.ShareInfo;
import org.filesys.smb.dcerpc.info.ShareInfoList;
import org.filesys.util.NameValue;
import org.filesys.util.NameValueList;

/**
 * srvShares Application
 * 
 * <p>
 * Display a list of the shares available on a remote server.
 * 
 * @author gkspencer
 */
public class srvShares extends BaseApp {

	/**
	 * Class constructor
	 */
	public srvShares() {
		super("srvShares", "Display remote server share list");
	}

	/**
	 * Display the usage information for the command
	 * 
	 * @param out PrintStream
	 */
	protected void outputCommandHelp(PrintStream out) {

		// Output the srvShares command help
		out.println("Usage: srvShares <host>|<ip_address> [-username=<username>] [-password=<password>]");
		out.println("  If not specified <username> defaults to the logged in user name.");
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

		// Get the list of remote shares
		ShareInfoList shareList = sess.getShareList();
		if ( shareList != null && shareList.numberOfEntries() > 0) {

			// Output column headers
			formatOutput(System.out, "Name", 30, "Type", 8, "Comment", 40, "Users", RightAlign + 10, "Hidden", -1);
			formatOutput(System.out, "----", 30, "----", 8, "-------", 40, "-----", RightAlign + 10, "------", -1);

			// Output the share details
			for (int i = 0; i < shareList.numberOfEntries(); i++) {

				// Get the current share details
				ShareInfo shareInfo = shareList.getShare(i);

				// Output the share details
				formatOutput(System.out, shareInfo.getName(), 30, shareInfo.getTypeAsString(), 8, shareInfo.getComment(), 40, ""
						+ shareInfo.getCurrentUsers() + "/" + shareInfo.getMaximumUsers(), RightAlign + 10,
						(shareInfo.isHidden() ? "  Yes" : ""), -1);
			}
		}
		else
			out.println("No shares to display");

		// Close the session
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
		else if ( cmdLine.numberOfItems() > 3) {
			out.println("%% Too many command line arguments");
			return false;
		}

		// Get the host name/address argument
		NameValue arg1 = cmdLine.findItem(CmdLineArg1);
		if ( arg1 == null || arg1.getValue() == null || arg1.getValue().length() == 0) {
			out.println("%% Invalid host name specified, " + arg1 != null ? arg1.getValue() : "");
			return false;
		}

		// Initialize the share details
		PCShare share = new PCShare(arg1.getValue(), "IPC$", getUserName(), getPassword());

		// Save the share for the main processing
		setShare(share);

		// Return a success status
		return true;
	}

	/**
	 * Run the srvShares command
	 * 
	 * @param args String[]
	 */
	public static void main(String[] args) {

		// Create the command object and run the command
		srvShares cmd = new srvShares();
		cmd.runCommand(args);
	}
}
