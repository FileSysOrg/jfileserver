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

import org.filesys.client.DiskSession;
import org.filesys.client.SessionFactory;
import org.filesys.client.admin.SamrPipeFile;
import org.filesys.client.info.DiskInfo;
import org.filesys.client.info.FileInfo;
import org.filesys.server.filesys.AccessMode;
import org.filesys.server.filesys.FileAction;
import org.filesys.server.filesys.FileAttribute;
import org.filesys.client.*;
import org.filesys.client.admin.*;
import org.filesys.client.info.*;
import org.filesys.smb.*;
import org.filesys.smb.nt.ACE;
import org.filesys.smb.nt.ACL;
import org.filesys.smb.nt.SecurityDescriptor;
import org.filesys.util.MemorySize;
import org.filesys.util.NameValue;
import org.filesys.util.NameValueList;

/**
 * srvStat Application
 * 
 * @author gkspencer
 */
public class srvStat extends BaseApp {

	// Flag to indicate only basic information should be displayed
	private boolean m_basicInfo = false;

	// Date formatter
	private SimpleDateFormat m_formatter;

	/**
	 * Class constructor
	 */
	public srvStat() {
		super("srvStat", "Remote file/folder detailed information");
	}

	/**
	 * Output the command help
	 * 
	 * @param out PrintStream
	 */
	protected void outputCommandHelp(PrintStream out) {

		// Output the srvStat command help
		out.println("Usage: srvStat \\\\<host>|<ip_address>\\<share_name>[\\<path\\<file>");
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
		if ( sess.FileExists(path) == false && sess.isDirectory(path) == false) {

			// File/folder does not exist
			out.println("%% File/folder does not exist - " + path);
		}

		// Check if we have a CIFS session, if so we can get more detailed file information
		else if ( m_basicInfo == false && sess instanceof CIFSDiskSession) {

			// Access the CIFS session
			CIFSDiskSession cifsSess = (CIFSDiskSession) sess;

			// Get information about the remote filesystem
			DeviceAttributesInfo fileSys = cifsSess.NTGetDeviceAttributes();
			DiskInfo diskInfo = cifsSess.getDiskInformation();

			// Use the NTCreateAndX call to open the file with extra options, these are required in
			// order to read the security descriptor
			SMBFile statFile = cifsSess.NTCreate(path, AccessMode.NTRead + AccessMode.NTReadControl + AccessMode.NTReadAttrib
					+ AccessMode.NTReadEA, FileAttribute.NTNormal, SharingMode.READ_WRITE.intValue(), FileAction.NTOpen, 0, 0);

			// Get the main file/folder information
			FileInfo fInfo = cifsSess.NTGetFileInformation(statFile.getFileId(), FileInfoLevel.PathFileAllInfo);

			// Output the file information
			out.println("File name : " + path);
			out.println();
			out.println("Creation date : " + formatDate(fInfo.getCreationDateTime()));
			out.println("Last access   : " + formatDate(fInfo.getAccessDateTime()));
			out.println("Last write    : " + formatDate(fInfo.getModifyDateTime()));
			out.println();

			out.println("File size       : " + MemorySize.asScaledString(fInfo.getSize()) + " (" + fInfo.getSize() + " bytes)");
			out.println("Allocation size : " + MemorySize.asScaledString(fInfo.getAllocationSize()) + " ("
					+ fInfo.getAllocationSize() + " bytes)");
			out.println();

			out.println("Attributes : " + FileAttribute.getNTAttributesAsString(fInfo.getFileAttributes()));
			out.println("Filesystem : " + fileSys.getFileSystemName() + ", Free space "
					+ MemorySize.asScaledString(diskInfo.getDiskFreeSizeBytes()) + ", Total space "
					+ MemorySize.asScaledString(diskInfo.getDiskSizeBytes()));

			// If the filesystem is NTFS check if the file has any streams
			if ( fileSys.getFileSystemName().equals("NTFS")) {

				// Get the streams list for the file
				ExtendedFileInfo streamInfo = (ExtendedFileInfo) cifsSess.NTGetFileInformation(statFile.getFileId(),
						FileInfoLevel.PathFileStreamInfo);
				if ( streamInfo != null && streamInfo.hasNTFSStreams()) {

					// Output the stream details
					out.println();
					out.println("NTFS Streams :");

					for (int i = 0; i < streamInfo.numberOfNTFSStreams(); i++) {

						// Get the current stream details
						StreamInfo sInfo = streamInfo.getNTFSStreams().getStreamAt(i);

						// Output the stream details
						out.println("  Name : " + sInfo.getName());
						out.println("    Size       : " + MemorySize.asScaledString(sInfo.getSize()) + " (" + sInfo.getSize()
								+ " bytes)");
						out.println("    Allocation : " + MemorySize.asScaledString(sInfo.getAllocationSize()) + " ("
								+ sInfo.getAllocationSize() + " bytes)");
					}
					out.println();
				}

				// Get the security descriptor
				int flags = NTSecurity.DACL + NTSecurity.Owner + NTSecurity.Group;
				SecurityDescriptor secDesc = cifsSess.NTQuerySecurityDescriptor(statFile.getFileId(), flags);

				if ( secDesc != null) {

					// Convert the owner/group SIDs to names
					AdminSession admSess = SessionFactory.OpenAdminSession(getShare());
					SamrPipeFile samr = admSess.openSecurityAccountsManagerPipe();

					samr.lookupName(secDesc.getOwner());
					samr.lookupName(secDesc.getGroup());

					// Display the security descriptor details
					out.println("Security descriptor:");
					out.println("  Owner : " + secDesc.getOwner());
					out.println("  Group : " + secDesc.getGroup());

					out.println("  DACL:");
					ACL acl = secDesc.getDACL();
					for (int i = 0; i < acl.numberOfEntries(); i++) {

						// Get the current access control entry
						ACE ace = acl.getACE(i);
						samr.lookupName(ace.getSID());

						// Output the details
						out.print("     ");
						formatOutput(out, ace.getTypeAsString(), 10, ace.getAccessMaskAsString(), 14, ace.getSID().toString(), -1);
					}

					// Close the admin session
					admSess.CloseSession();
				}
			}

			// Close the remote file/folder
			statFile.Close();
		}
		else {

			// Output standard file information
			//
			// Get information about the remote filesystem
			DiskInfo diskInfo = sess.getDiskInformation();

			// Get the main file/folder information
			FileInfo fInfo = sess.getFileInformation(path);

			// Output the file information
			out.println("File name : " + path);
			out.println();
			out.println("Creation date : " + formatDate(fInfo.getCreationDateTime()));
			out.println("Last access   : " + formatDate(fInfo.getAccessDateTime()));
			out.println("Last write    : " + formatDate(fInfo.getModifyDateTime()));
			out.println();

			out.println("File size       : " + MemorySize.asScaledString(fInfo.getSize()) + " (" + fInfo.getSize() + " bytes)");
			out.println("Allocation size : " + MemorySize.asScaledString(fInfo.getAllocationSize()) + " ("
					+ fInfo.getAllocationSize() + " bytes)");
			out.println();

			out.println("Attributes : " + FileAttribute.getNTAttributesAsString(fInfo.getFileAttributes()));
			out.println("Filesystem : Free space " + MemorySize.asScaledString(diskInfo.getDiskFreeSizeBytes())
					+ ", Total space " + MemorySize.asScaledString(diskInfo.getDiskSizeBytes()));
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

		// Check if the basic information switch has been specified
		if ( cmdLine.findItemCaseless("basic") != null)
			m_basicInfo = true;

		// Return a success status
		return true;
	}

	/**
	 * Format a date/time string
	 * 
	 * @param date SMBDate
	 * @return String
	 */
	private final String formatDate(SMBDate date) {

		// Check if the date formatter has been initialized
		if ( m_formatter == null)
			m_formatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss.SSS");

		// Check if the date value is valid
		if ( date == null)
			return "<Not Specified>";

		// Return the formatted date/time string
		return m_formatter.format(date);
	}

	/**
	 * Run the srvStat command
	 * 
	 * @param args String[]
	 */
	public static void main(String[] args) {

		// Create the command object and run the command
		srvStat cmd = new srvStat();
		cmd.runCommand(args);
	}
}
