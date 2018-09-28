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
import org.filesys.util.NameValue;
import org.filesys.util.NameValueList;

/**
 * srvMessage Application
 * 
 * <p>
 * Send a message to a remote host, that is running the Messenger service or equivalent.
 * 
 * @author gkspencer
 */
public class srvMessage extends BaseApp {

	// Name or address of host to send message to
	private String m_hostName;

	// Message text
	private String m_message;

	/**
	 * Class constructor
	 */
	public srvMessage() {
		super("srvMessage", "Send message to remote host", false);
	}

	/**
	 * Display the command usage
	 * 
	 * @param out PrintStream
	 */
	protected void outputCommandHelp(PrintStream out) {

		// Output the srvMessage command help
		out.println("Usage: srvMessage <host>|<ip_address> <message>");
	}

	/**
	 * Perform the main command processing
	 * 
	 * @param out PrintStream
	 * @throws Exception Error running the command
	 */
	protected void doCommand(PrintStream out)
		throws Exception {

		// Send the message to the remote host
		SessionFactory.SendMessage(getHost(), getMessage());

		// Output a success message
		out.println("Message sent to " + getHost());
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
		if ( cmdLine.numberOfItems() < 2) {
			out.println("%% Host name or address must be specified");
			return false;
		}

		// Get the host name/address argument
		NameValue arg1 = cmdLine.findItem(CmdLineArg1);
		if ( arg1 == null || arg1.getValue() == null || arg1.getValue().length() == 0) {
			out.println("%% Invalid host name specified, " + arg1 != null ? arg1.getValue() : "");
			return false;
		}
		else
			setHost(arg1.getValue());

		// The message may have been split if it contains spaces so we must build the message string
		StringBuffer msgBuf = new StringBuffer(256);
		int cmdArg = 2;
		NameValue msgArg = cmdLine.findItem(CmdLineArg + cmdArg++);

		while (msgArg != null) {

			// Append the message text
			msgBuf.append(msgArg.getValue());
			msgBuf.append(" ");

			// Get the next message fragment
			msgArg = cmdLine.findItem(CmdLineArg + cmdArg++);
		}

		// Strip the last space from the message string
		if ( msgBuf.length() > 0)
			msgBuf.setLength(msgBuf.length() - 1);
		setMessage(msgBuf.toString());

		// Return a success status
		return true;
	}

	/**
	 * Get the destination host name/address
	 * 
	 * @return String
	 */
	protected final String getHost() {
		return m_hostName;
	}

	/**
	 * Get the message
	 * 
	 * @return String
	 */
	protected final String getMessage() {
		return m_message;
	}

	/**
	 * Set the destination host
	 * 
	 * @param host String
	 */
	protected final void setHost(String host) {
		m_hostName = host;
	}

	/**
	 * Set the message to be sent
	 * 
	 * @param msg String
	 */
	protected final void setMessage(String msg) {
		m_message = msg;
	}

	/**
	 * Run the srvMessage command
	 * 
	 * @param args String[]
	 */
	public static void main(String[] args) {

		// Create the command object and run the command
		srvMessage cmd = new srvMessage();
		cmd.runCommand(args);
	}
}
