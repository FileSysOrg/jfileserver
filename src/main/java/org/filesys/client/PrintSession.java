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

package org.filesys.client;

import org.filesys.smb.PCShare;
import org.filesys.smb.SMBDeviceType;
import org.filesys.smb.SMBException;

/**
 *  SMB print session class
 *
 *  <p>The print session allows a new print job to be created, using the SMBFile
 *  class or as an SMBOutputStream.
 *
 *  <p>When the SMBFile/SMBOutputStream is closed the print job will be queued to
 *  the remote printer.
 *
 *  <p>A print session is created using the SessionFactory.OpenPrinter() method. The
 *  SessionFactory negotiates the appropriate SMB dialect and creates the appropriate
 *  PrintSession derived object.
 * 
 * @see SessionFactory
 * 
 * @author gkspencer
 */
public abstract class PrintSession extends Session {

	//	Print modes

	public static final int TextMode 		= 0;
	public static final int GraphicsMode 	= 1;

	//	Default number of print queue entries to return

	public static final int DefaultEntryCount = 20;
  
	/**
	 * Construct an SMB print session
	 * 
	 * @param shr Remote server details
	 * @param dialect SMB dialect that this session is using
	 */
	protected PrintSession(PCShare shr, int dialect) {
		super(shr, dialect, null);

		// Set the device type

		this.setDeviceType(SMBDeviceType.Printer);
	}

	/**
	 * Determine if the print session has been closed.
	 * 
	 * @return true if the print session has been closed, else false.
	 */
	protected final boolean isClosed() {
		return m_treeid == Closed ? true : false;
	}

	/**
	 * Open a spool file on the remote print server.
	 * 
	 * @param id Identifier string for this print request.
	 * @param mode Print mode, either TextMode or GraphicsMode.
	 * @param setuplen Length of data in the start of the spool file that is printer setup code.
	 * @return SMBFile for the new spool file, else null.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public abstract SMBFile OpenSpoolFile(String id, int mode, int setuplen)
		throws java.io.IOException, SMBException;

	/**
	 * Open a spool file as an output stream.
	 * 
	 * @param id Identifier string for this print request.
	 * @param mode Print mode, either TextMode or GraphicsMode.
	 * @param setuplen Length of data in the start of the spool file that is printer setup code.
	 * @return SMBOutputStream for the spool file, else null.
	 * @exception java.io.IOException If an I/O error occurs.
	 * @exception SMBException If an SMB level error occurs
	 */
	public SMBOutputStream OpenSpoolStream(String id, int mode, int setuplen)
		throws java.io.IOException, SMBException {

		// Open an SMBFile first

		SMBFile sfile = OpenSpoolFile(id, mode, setuplen);
		if ( sfile == null)
			return null;

		// Create an output stream attached to the SMBFile

		return new SMBOutputStream(sfile);
	}
}