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
import java.util.*;

import org.filesys.smb.*;
import org.filesys.client.*;
import org.filesys.client.admin.*;
import org.filesys.smb.dcerpc.*;
import org.filesys.smb.dcerpc.info.*;
import org.filesys.smb.nt.*;
import org.filesys.util.*;

/**
 * srvUser Application
 * 
 * @author gkspencer
 */
public class srvUser extends BaseApp {

	//	Account flag names
	private static String[] _accFlagStr = { "Account Disabled",
      									  "Home Directory Required",
      									  "Password Not Required",
      									  "Temporary Duplicate",
      									  "Normal Account",
      									  "MNSUser",
      									  "Domain Trust",
      									  "Workstation Trust",
      									  "Server Trust",
      									  "Password Does Not Expire",
      									  "Auto Locked"
	};

	//	Domain and user name
	private String m_domain;
	private String m_userName;
  
	//	Date/time formatter
	private SimpleDateFormat m_formatter;
  
	/**
	 * Class constructor
	 */
	public srvUser() {
		super("srvUser", "User account detailed information");
	}

	/**
	 * Output the command help
	 * 
	 * @param out PrintStream
	 */
	protected void outputCommandHelp(PrintStream out) {

		// Output the srvUser command help
		out.println("Usage: srvUser <host>|<ip_address> <account_name> -username=<username> -password=<password>");
		out.println("  <host>|<ip_address>	The target server to retrieve user account information from");
		out.println("  <account_name>       Account name to return information for. May be specified as");
		out.println("                       <domain>\\<username> or");
		out.println("                       <domain>/<username>  or");
		out.println("                       <username>");
	}

	/**
	 * Perform the main command processing
	 * 
	 * @param out PrintStream
	 * @exception Exception Error running the command
	 */
	protected void doCommand(PrintStream out)
		throws Exception {

		// Open an admin session to the remote server
		AdminSession sess = SessionFactory.OpenAdminSession(getShare());
		setSession(sess.getSession());

		// Open the SAMR service
		SamrPipeFile samr = sess.openSecurityAccountsManagerPipe();

		// Check if the domain name has been specified
		if ( m_domain == null) {

			// Get a list of the available domain names
			StringList domains = samr.enumerateDomains();
			if ( domains != null && domains.numberOfStrings() > 0)
				m_domain = domains.getStringAt(0);
			else {

				// Output an error message, cannot find a default domain
				out.println("%% Cannot find default domain");
				return;
			}
		}

		// Get a handle to the user object and get the user account details
		PolicyHandle userHandle = samr.openUser(m_domain, m_userName);
		UserInfo userInfo = samr.queryUserInformation(userHandle, UserInfo.InfoLevel21);

		// Display the user account details
		out.println("User : " + m_domain + "\\" + userInfo.getUserName());
		out.println("Full name   : " + formatString(userInfo.getFullName()));
		out.println("Comment     : " + formatString(userInfo.getComment()));
		out.println("Description : " + formatString(userInfo.getDescription()));
		out.println();

		out.println("Profile        : " + formatString(userInfo.getProfile()));
		out.println("Home drive     : " + formatString(userInfo.getHomeDirectoryDrive()));
		out.println("Home directory : " + formatString(userInfo.getHomeDirectory()));
		out.println("Script path    : " + formatString(userInfo.getLogonScriptPath()));
		out.println();

		out.println("Last logon   : " + formatDate(userInfo.getLastLogon()));
		out.println("Last logoff  : " + formatDate(userInfo.getLastLogoff()));
		out.println("Logon server : " + formatString(userInfo.getLogonServer()));
		out.println("Logon count  : " + userInfo.numberOfLogons());
		out.println("Bad passwords: " + userInfo.getBadPasswordCount());
		out.println();
		out.println("Password change         : " + formatDate(userInfo.getLastPasswordChange()));
		out.println("Password must change by : " + formatDate(userInfo.getPasswordMustChangeBy()));
		out.println("Account expires         : " + formatDate(userInfo.getAccountExpires()));
		out.println();

		out.println("Flags : ");

		StringList flagStrings = buildAccountControlStringList(userInfo.getFlags());

		if ( flagStrings != null && flagStrings.numberOfStrings() > 0) {

			// Display the enabled account flags
			for (int i = 0; i < flagStrings.numberOfStrings(); i++) {
				out.print("  ");
				out.println(flagStrings.getStringAt(i));
			}
		}
		else
			out.println("  <None>");
		out.println();

		// Get a list of groups that the user belongs to
		RIDList groups = samr.getGroupsForUser(m_domain, m_userName);

		out.println("Groups:");

		if ( groups != null && groups.numberOfRIDs() > 0) {

			// Output the list of groups this user is a member of
			for (int i = 0; i < groups.numberOfRIDs(); i++) {

				// Get the current group details
				RID group = groups.getRIDAt(i);
				out.println("  " + group.getName());
			}
			out.println();
		}
		else
			out.println("  <None>");

		// Get a list of the aliases for the user
		RIDList aliases = samr.getAliasesForUser(m_domain, m_userName);

		out.println("Aliases:");

		if ( aliases != null && aliases.numberOfRIDs() > 0) {

			// Output the list of aliases
			for (int i = 0; i < aliases.numberOfRIDs(); i++) {

				// Get the current group details
				RID alias = aliases.getRIDAt(i);
				out.println("  " + alias.getName());
			}
			out.println();
		}
		else
			out.println("  <None>");

		// Close the user handle
		samr.closeHandle(userHandle);

		// Close the session
		sess.CloseSession();
		setSession(null);
	}

	/**
	 * Validate the command line arguments
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

		// Get the domain/user account name
		NameValue arg2 = cmdLine.findItem(CmdLineArg2);
		if ( arg2 == null || arg2.getValue() == null || arg2.getValue().length() == 0) {
			out.println("%% invalid account name specified, " + arg2 != null ? arg2.getValue() : "");
			return false;
		}

		// Parse the account name string
		String account = arg2.getValue();

		int pos = account.indexOf("\\");
		if ( pos == -1)
			pos = account.indexOf("/");

		if ( pos != -1) {

			// Split the account name into domain and user name strings
			m_domain = account.substring(0, pos);
			m_userName = account.substring(pos + 1);
		}
		else {

			// Only a user name specified
			m_userName = account;
		}

		// Initialize the share details
		PCShare share = new PCShare(arg1.getValue(), "IPC$", getUserName(), getPassword());

		// Save the share for the main processing
		setShare(share);

		// Return a success status
		return true;
	}

	/**
	 * Format a date/time string
	 * 
	 * @param date long
	 * @return String
	 */
	private final String formatDate(long date) {

		// Check if the date formatter has been initialized
		if ( m_formatter == null)
			m_formatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");

		// Check if the date value is valid
		if ( date == NTTime.InfiniteTime)
			return "<No Limit>";
		else if ( date == 0L)
			return "<Not Specified>";

		// Return the formatted date/time string
		return m_formatter.format(new Date(date));
	}

	/**
	 * Format a string for output
	 * 
	 * @param str String
	 * @return String
	 */
	private final String formatString(String str) {
		if ( str == null)
			return "<Not Specified>";
		return str;
	}

	/**
	 * Build a list of the enabled account control flag names
	 * 
	 * @param flags int
	 * @return StringList
	 */
	private final StringList buildAccountControlStringList(int flags) {

		// Allocate the list to hold the enabled flag strings
		StringList list = new StringList();

		// Check if any flags are set
		if ( flags == 0)
			return list;

		// Add the enabled flag names to the list
		int mask = 1;
		int idx = 0;

		while (idx < _accFlagStr.length) {

			// Check if the current flag is enabled
			if ( (flags & mask) != 0)
				list.addString(_accFlagStr[idx]);

			// Update the index and mask
			mask = mask << 1;
			idx++;
		}

		// Return the list of strings
		return list;
	}

	/**
	 * Run the srvUser command
	 * 
	 * @param args String[]
	 */
	public static void main(String[] args) {

		// Create the command object and run the command
		srvUser cmd = new srvUser();
		cmd.runCommand(args);
	}
}
