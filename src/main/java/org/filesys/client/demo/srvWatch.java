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

import org.filesys.client.*;
import org.filesys.client.smb.DirectoryWatcher;
import org.filesys.server.filesys.AccessMode;
import org.filesys.server.filesys.FileAction;
import org.filesys.server.filesys.FileAttribute;
import org.filesys.smb.InvalidUNCPathException;
import org.filesys.smb.PCShare;
import org.filesys.smb.SharingMode;
import org.filesys.util.NameValue;
import org.filesys.util.NameValueList;

/**
 * srvWatch Application
 * 
 * @author gkspencer
 */
public class srvWatch extends BaseApp {

	/**
	 * Class constructor
	 */
	public srvWatch() {
		super("srvWatch", "Monitor file/folder changes on a remote folder");
	}

	/**
	 * Output the command help
	 * 
	 * @param out PrintStream
	 */
	protected void outputCommandHelp(PrintStream out) {

		// Output the srvWatch command help
		out.println("Usage: srvWatch \\\\<host>|<ip_address>\\<share_name>[\\<path\\");
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
		PCShare share = getShare();

		// Open a session to the remote file server
		DiskSession sess = SessionFactory.OpenDisk(share);
		setSession(sess);

		// Build the share relative path
		String path = share.getRelativePath();

		// Check if the path exists
		if ( sess.FileExists(path) == false || sess.isDirectory(path) == false) {

			// File/folder does not exist
			out.println("%% Folder does not exist - " + path);
		}

		// Check if we have a CIFS session, this is required to open the folder using the newer
		// NTCreate call
		else if ( sess instanceof CIFSDiskSession) {

			// Access the CIFS session
			CIFSDiskSession cifsSess = (CIFSDiskSession) sess;

			// Use the NTCreateAndX call to open the folder as we require the file id for the change
			// notification request
			SMBFile watchDir = cifsSess.NTCreate(path, AccessMode.NTRead, FileAttribute.NTNormal, SharingMode.READ_WRITE.intValue(),
					FileAction.NTOpen, 0, 0);

			// Create a directory watcher
			DirectoryWatcher watcher = new DirectoryWatcher() {

				// Directory changed callback

				public void directoryChanged(int typ, String fname) {
					System.out.println("  " + NotifyChange.getActionAsString(typ) + " : " + fname);
				}
			};

			// Add a notify change listener to the directory
			AsynchRequest dirWatch = cifsSess.NTNotifyChange(watchDir.getFileId(), NotifyChange.FileName
					+ NotifyChange.DirectoryName + NotifyChange.Size, true, watcher, true);

			// Output a message and wait for the user to stop the command
			out.println("Watching directory " + path + " on \\\\" + share.getNodeName() + "\\" + share.getShareName()
					+ ", enter 'x' to stop");
			boolean userExit = false;

			while (userExit == false) {

				// Check if the user has requested the command to stop
				if ( System.in.available() > 0) {

					// Read a character, check if the user wants to close the application
					int ch = System.in.read();
					if ( ch == 'x' || ch == 'X') {
						userExit = true;
						break;
					}
				}

				// Wait for an asynchronous response
				try {
					cifsSess.checkForAsynchReceive(-1);
				}
				catch (IOException ex) {
				}

				// Sleep for a short while
				try {
					Thread.sleep(1000);
				}
				catch (InterruptedException ex) {
					userExit = true;
				}
			}

			// Output a close message
			out.println("Watcher closed.");

			// Set the change notification handler to not reset
			dirWatch.setAutoReset(false);

			// Close the remote folder
			watchDir.Close();
		}
		else {

			// Output an error message, notifications not supported
			out.println("%% Server does not support notifications");
		}

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
		if ( cmdLine.numberOfItems() < 1) {
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
	 * Run the srvWatch command
	 * 
	 * @param args String[]
	 */
	public static void main(String[] args) {

		// Create the command object and run the command
		srvWatch cmd = new srvWatch();
		cmd.runCommand(args);
	}
}
