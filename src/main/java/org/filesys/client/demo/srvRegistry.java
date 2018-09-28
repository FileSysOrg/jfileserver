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
import java.util.*;

import org.filesys.client.SessionFactory;
import org.filesys.client.admin.AdminSession;
import org.filesys.client.admin.WinregPipeFile;
import org.filesys.smb.PCShare;
import org.filesys.smb.dcerpc.client.RegistryType;
import org.filesys.smb.dcerpc.client.Winreg;
import org.filesys.smb.dcerpc.info.RegistryKey;
import org.filesys.smb.dcerpc.info.RegistryValue;
import org.filesys.util.HexDump;
import org.filesys.util.NameValue;
import org.filesys.util.NameValueList;

/**
 * srvRegistry Application
 * 
 * <p>
 * Display remote registry keys/values.
 * 
 * @author gkspencer
 */
public class srvRegistry extends BaseApp {

	// Root key id
	private int m_rootKey;

	// Sub key path
	private String m_subKey;

	/**
	 * Class constructor
	 */
	public srvRegistry() {
		super("srvRegistry", "Display remote registry keys/values");
	}

	/**
	 * Display the usage information for the command
	 * 
	 * @param out PrintStream
	 */
	protected void outputCommandHelp(PrintStream out) {

		// Output the srvRegistry command help
		out.println("Usage: srvRegistry <host>|<ip_address> <key_name> -username=<username> -password=<password>");
		out.println("The <key_name> must start with one of the following root key names :-");
		out.println("  HKEY_LOCAL_MACHINE or HKLM");
		out.println("  HKEY_USERS or HKU");
		out.println("  HKEY_CLASSES_ROOT or HKCR");
		out.println("  HKEY_CURRENT_USER or HKCU");
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

		WinregPipeFile regPipe = sess.openWindowsRegistryPipe();
		if ( regPipe == null)
			throw new Exception("Failed to open service control pipe, WinReg");

		// Open the root key
		RegistryKey rootKey = regPipe.openRootKey(getRootKey());
		if ( rootKey == null)
			throw new IOException("Failed to open root registry key");

		// Open the subkey, if specified
		RegistryKey subKey = null;

		if ( hasSubkeyPath()) {

			// Open the subkey
			subKey = regPipe.openKey(rootKey, getSubkeyPath());
		}
		else {

			// Use the root key as the subkey
			subKey = rootKey;
		}

		// Output the key name
		out.print("Registry key ");
		out.print(Winreg.getRootIdAsLongName(getRootKey()));
		out.print("\\");
		out.println(getSubkeyPath());
		out.println();

		// Enumerate the keys and values for the specified registry key
		List<RegistryKey> keys = regPipe.getKeysForKey(subKey);
		out.println("Keys:");

		if ( keys != null && keys.size() > 0) {

			// Display the sub keys
			for (int i = 0; i < keys.size(); i++) {
				RegistryKey key = (RegistryKey) keys.get(i);
				out.println("  " + key.getFullName());
			}
		}
		else
			out.println("  None");
		out.println();

		List<RegistryValue> values = regPipe.getValuesForKey(subKey);
		out.println("Values:");
		out.println();

		if ( values != null && values.size() > 0) {

			// Output the header
			formatOutput(out, "Name", 30, "Type", 15, "Value", -1);
			formatOutput(out, "----", 30, "----", 15, "-----", -1);

			// Display the values

			for (int i = 0; i < values.size(); i++) {

				// Get the registry value
				RegistryValue value = values.get(i);

				// Output the value details
				if ( value.getDataType() == RegistryType.REG_SZ || value.getDataType() == RegistryType.REG_DWORD
						|| value.getDataType() == RegistryType.REG_MULTI_SZ) {

					// Output the standard value
					formatOutput(out, value.getName(), 30, value.getDataTypeString(), 15, value.getValue().toString(), 55);
				}
				else {

					// Output as hex-ASCII
					byte[] val = (byte[]) value.getValue();
					formatOutput(out, value.getName(), 30, value.getDataTypeString(), 15, HexDump.hexString(val,
							val.length < 20 ? val.length : 20, " "), -1);
				}
			}
		}
		else
			out.println("  None");

		// Close the registry keys
		regPipe.closeHandle(subKey);
		if ( rootKey.isOpen() == true)
			regPipe.closeHandle(rootKey);

		// Close the pipe/session
		regPipe.ClosePipe();
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

		// Get the registry key path, and validate
		NameValue arg2 = cmdLine.findItem(CmdLineArg2);
		if ( arg2 == null || arg2.getValue() == null || arg2.getValue().length() == 0) {
			out.println("%% Invalid key name specified, " + (arg2 != null ? arg2.getValue() : ""));
			return false;
		}

		// Validate the registry key path
		String keyName = arg2.getValue();
		keyName = keyName.replace('/', '\\');

		String keyPath = "";

		int pos = keyName.indexOf("\\");

		if ( pos != -1) {

			// Split the registry path into root name and path
			keyPath = keyName.substring(pos + 1);
			keyName = keyName.substring(0, pos);
		}

		// Initialize the share details
		PCShare share = new PCShare(arg1.getValue(), "IPC$", getUserName(), getPassword());

		// Validate the root key id
		int rootId = -1;

		if ( keyName.equalsIgnoreCase("HKEY_LOCAL_MACHINE") || keyName.equalsIgnoreCase("HKLM"))
			rootId = Winreg.HKEYLocalMachine;
		else if ( keyName.equalsIgnoreCase("HKEY_USERS") || keyName.equalsIgnoreCase("HKU"))
			rootId = Winreg.HKEYUsers;
		else if ( keyName.equalsIgnoreCase("HKEY_CLASSES_ROOT") || keyName.equalsIgnoreCase("HKCR"))
			rootId = Winreg.HKEYClassesRoot;
		else if ( keyName.equalsIgnoreCase("HKEY_CURRENT_USER") || keyName.equalsIgnoreCase("HKCU"))
			rootId = Winreg.HKEYCurrentUser;

		if ( rootId == -1) {
			out.println("%% Invalid root key name, " + keyName);
			return false;
		}

		// Save the registry key details
		setRootKey(rootId);
		setSubkeyPath(keyPath);

		// Save the share for the main processing
		setShare(share);

		// Return a success status
		return true;
	}

	/**
	 * Return the root key id
	 * 
	 * @return int
	 */
	protected final int getRootKey() {
		return m_rootKey;
	}

	/**
	 * Check if the subkey path is valid
	 * 
	 * @return boolean
	 */
	protected final boolean hasSubkeyPath() {
		if ( m_subKey != null && m_subKey.length() > 0)
			return true;
		return false;
	}

	/**
	 * Return the sub key path
	 * 
	 * @return String
	 */
	protected final String getSubkeyPath() {
		return m_subKey;
	}

	/**
	 * Set the root key id
	 * 
	 * @param root int
	 */
	protected final void setRootKey(int root) {
		m_rootKey = root;
	}

	/**
	 * Set the sub key path
	 * 
	 * @param subkey String
	 */
	protected final void setSubkeyPath(String subkey) {
		m_subKey = subkey;
	}

	/**
	 * Run the srvRegistry command
	 * 
	 * @param args String[]
	 */
	public static void main(String[] args) {

		// Create the command object and run the command
		srvRegistry cmd = new srvRegistry();
		cmd.runCommand(args);
	}
}
