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
import org.filesys.client.info.FileInfo;
import org.filesys.server.filesys.FileAttribute;
import org.filesys.client.*;
import org.filesys.smb.InvalidUNCPathException;
import org.filesys.smb.PCShare;
import org.filesys.smb.SMBDate;
import org.filesys.util.MemorySize;
import org.filesys.util.NameValue;
import org.filesys.util.NameValueList;

/**
 * srvDir Application
 * 
 * @author gkspencer
 */
public class srvDir extends BaseApp {

	// Date/time formatter
	private SimpleDateFormat m_formatter;

	/**
	 * Class constructor
	 */
	public srvDir() {
		super("srvDir", "Remote directory listing utility");
	}

	/**
	 * Output the command help
	 * 
	 * @param out PrintStream
	 */
	protected void outputCommandHelp(PrintStream out) {

		// Output the srvDir command help
		out.println("Usage: srvDir \\\\<host>|<ip_address>\\<share_name>[\\<path\\<filespec>]");
		out.println("  The UNC path to the file/folder may use forward or backslash characters.");
		out.println("  Access control may be embedded in the UNC path using:-");
		out.println("    \\\\<host>\\<share>[%username][:<password.]\\<path>\\...");
	}

	/**
	 * Perform the main command processing
	 * 
	 * @param out PrintStream
	 * @exception Exception Error running the command
	 */
	protected void doCommand(PrintStream out)
		throws Exception {

		// Get the remote path details
		PCShare remPath = getShare();

		// Open a session to the remote file server
		DiskSession sess = SessionFactory.OpenDisk(remPath);
		setSession(sess);

		// Start a search of the remote directory
		SearchContext srch = sess.StartSearch(remPath.getRelativePath(), FileAttribute.Directory);

		// Get a file from the search
		int fileCnt = 0;
		FileInfo finfo = srch.nextFileInfo();

		while (finfo != null) {

			// Output the current file information
			formatOutput(System.out, finfo.getFileName(), 30, finfo.getFormattedAttributes(), 6, (finfo.isDirectory() ? ""
					: MemorySize.asScaledString(finfo.getSize())), RightAlign + 10, formatDate(finfo.getModifyDateTime(), finfo
					.getCreationDateTime()), -1);

			// Get the next file from the list
			finfo = srch.nextFileInfo();
			fileCnt++;
		}

		// Output a blnk line if there were some files
		if ( fileCnt > 0)
			System.out.println();

		// Output the total number of files
		System.out.println("Total of " + fileCnt + " files");

		// Close the session to the server
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
		if ( cmdLine.numberOfItems() > 3) {
			out.println("%% Wrong number of command line arguments");
			return false;
		}

		// Get the command line argument, should be the UNC path
		NameValue arg1 = cmdLine.findItem(CmdLineArg1);
		if ( arg1 == null || arg1.getValue() == null || arg1.getValue().length() == 0) {
			out.println("%% Invalid command line argument, " + arg1 != null ? arg1.getValue() : "");
			return false;
		}

		// Initialize the share details
		PCShare share = null;
		try {

			// Parse the UNC path and validate
			share = new PCShare(arg1.getValue());

			// Check if a file mask has been specified
			if ( share.getFileName() == null || share.getFileName().length() == 0)
				share.setFileName("*");

			// Save the share for the main processing
			setShare(share);
		}
		catch (InvalidUNCPathException ex) {
			out.println("%% Invalid UNC path, " + ex.toString());
			return false;
		}

		// Return a success status
		return true;
	}

	/**
	 * Format a date/time string
	 * 
	 * @param modifyDate SMBDate
	 * @param createDate SMBDate
	 * @return String
	 */
	private final String formatDate(SMBDate modifyDate, SMBDate createDate) {

		// Check if the date formatter has been initialized
		if ( m_formatter == null)
			m_formatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss.SSS");

		// Check if the date value is valid
		SMBDate date = modifyDate;

		if ( date == null) {

			// Use the creation date, if valid
			if ( createDate != null)
				date = createDate;
			else
				return "";
		}

		// Return the formatted date/time string
		return m_formatter.format(date);
	}

	/**
	 * Run the srvDir command
	 * 
	 * @param args String[]
	 */
	public static void main(String[] args) {

		// Create the command object and run the command
		srvDir cmd = new srvDir();
		cmd.runCommand(args);
	}
}
