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

package org.filesys.client.admin;

import java.io.*;
import java.util.*;

import org.filesys.client.IPCSession;
import org.filesys.server.filesys.AccessMode;
import org.filesys.smb.SMBException;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.client.DCEPacket;
import org.filesys.smb.dcerpc.client.Winreg;
import org.filesys.smb.dcerpc.info.RegistryKey;
import org.filesys.smb.dcerpc.info.RegistryKeyInfo;
import org.filesys.smb.dcerpc.info.RegistryValue;

/**
 * Windows Registry Pipe File Class
 * 
 * <p>
 * Pipe file connected to a remote Windows registry DCE/RPC service that can be used to retrieve,
 * update and create values and keys in the remote registry.
 * 
 * @author gkspencer
 */
public class WinregPipeFile extends IPCPipeFile {

	// Constants

	private static final int MaxReturnBufferSize = 1048576; // 1Mb

	/**
	 * Class constructor
	 * 
	 * @param sess SMBIPCSession
	 * @param pkt DCEPacket
	 * @param handle int
	 * @param name String
	 * @param maxTx int
	 * @param maxRx int
	 */
	public WinregPipeFile(IPCSession sess, DCEPacket pkt, int handle, String name, int maxTx, int maxRx) {
		super(sess, pkt, handle, name, maxTx, maxRx);
	}

	/**
	 * Open the HIVE_KEY_LOCAL_MACHINE (HKLM) registry key on the remote server. The returned policy
	 * handle is used to access the keys/values.
	 * 
	 * @return RegistryKey
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final RegistryKey openHKLocalMachine()
		throws IOException, SMBException {

		// Open the HKEY_LOCAL_MACHINE registry hive

		return openRootKey(Winreg.HKEYLocalMachine);
	}

	/**
	 * Open the HIVE_KEY_USERS (HKU) registry key on the remote server. The returned policy handle
	 * is used to access the keys/values.
	 * 
	 * @return RegistryKey
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final RegistryKey openHKUsers()
		throws IOException, SMBException {

		// Open the HIVE_KEY_USERS registry hive

		return openRootKey(Winreg.HKEYUsers);
	}

	/**
	 * Open the HKEY_CLASSES_ROOT registry key on the remote server. The returned policy handle is
	 * used to access the keys/values.
	 * 
	 * @return RegistryKey
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final RegistryKey openHKClassesRoot()
		throws IOException, SMBException {

		// Open the HKEY_CLASSES_ROOT registry hive

		return openRootKey(Winreg.HKEYClassesRoot);
	}

	/**
	 * Open the HKEY_CURRENT_USER registry key on the remote server. The returned policy handle is
	 * used to access the keys/values.
	 * 
	 * @return RegistryKey
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final RegistryKey openHKCurrentUser()
		throws IOException, SMBException {

		// Open the HKEY_CLASSES_ROOT registry hive

		return openRootKey(Winreg.HKEYCurrentUser);
	}

	/**
	 * Open the HKEY_PERFORMANCE_DATA registry key on the remote server. The returned policy handle
	 * is used to access the keys/values.
	 * 
	 * @return RegistryKey
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final RegistryKey openHKPerformanceData()
		throws IOException, SMBException {

		// Open the HKEY_PERFORMANCE_DATA registry hive

		return openRootKey(Winreg.HKEYPerformanceData);
	}

	/**
	 * Open a registry key.
	 * 
	 * @param root RegistryKey
	 * @param key String
	 * @return RegistryKey
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final RegistryKey openKey(RegistryKey root, String key)
		throws IOException, SMBException {

		// Build the open key request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(root);
		buf.putUnicodeHeader(key, true);
		buf.putString(key, DCEBuffer.ALIGN_INT, true);
		buf.putZeroInts(1);
		buf.putInt(AccessMode.NTMaximumAllowed);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Winreg.RegOpenKey, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the open registry key DCE request

		doDCERequest(pkt);

		// Retrieve the policy handle from the response

		buf = getRxBuffer();
		RegistryKey regKey = new RegistryKey(key, root);

		try {
			checkStatus(buf.getStatusCode());
			buf.getHandle(regKey);
		}
		catch (DCEBufferException ex) {
		}

		// Return the handle to the registry key

		return regKey;
	}

	/**
	 * Open a registry key.
	 * 
	 * @param root RegistryKey
	 * @param key RegistryKey
	 * @return RegistryKey
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final RegistryKey openKey(RegistryKey root, RegistryKey key)
		throws IOException, SMBException {

		// Build the open key request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(root);
		buf.putUnicodeHeader(key.getName(), true);
		buf.putString(key.getName(), DCEBuffer.ALIGN_INT, true);
		buf.putZeroInts(1);
		buf.putInt(AccessMode.NTMaximumAllowed);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Winreg.RegOpenKey, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the open registry key DCE request

		doDCERequest(pkt);

		// Retrieve the policy handle from the response

		buf = getRxBuffer();

		try {
			checkStatus(buf.getStatusCode());
			buf.getHandle(key);
		}
		catch (DCEBufferException ex) {
		}

		// Return the handle to the registry key

		return key;
	}

	/**
	 * Close a hive or key.
	 *
	 * @param handle RegistryKey
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void closeHandle(RegistryKey handle)
		throws IOException, SMBException {

		// Build the close request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(handle);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Winreg.RegClose, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the close handle DCE request

		doDCERequest(pkt);

		// Retrieve the status from the response

		buf = getRxBuffer();

		try {
			checkStatus(buf.getInt());
		}
		catch (DCEBufferException ex) {
		}

		// Mark the registry key as closed

		handle.markClosed();
	}

	/**
	 * Flush a key so updates are actually in the registry.
	 *
	 * @param handle RegistryKey
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	/**
	 * public final void flushKey(RegistryKey handle) throws IOException, SMBException { }
	 **/

	/**
	 * Return information about a registry key.
	 *
	 * @param handle RegistryKey
	 * @param cls String
	 * @return RegistryKeyInfo
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final RegistryKeyInfo getKeyInfo(RegistryKey handle, String cls)
		throws IOException, SMBException {

		// Build the query key information request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(handle);
		buf.putUnicodeHeader(cls, true);
		buf.putString(cls != null ? cls : "", DCEBuffer.ALIGN_INT, true);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Winreg.RegQueryInfoKey, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the query key information DCE request

		doDCERequest(pkt);

		// Retrieve registry key information

		buf = getRxBuffer();
		RegistryKeyInfo keyInfo = new RegistryKeyInfo();

		try {
			checkStatus(buf.getStatusCode());
			keyInfo.readObject(buf);
		}
		catch (DCEBufferException ex) {
		}

		// Return the registry key information

		return keyInfo;
	}

	/**
	 *
	 * Return the value for the specified key and parameter
	 *
	 * @param key RegistryKey
	 * @param name String
	 * @return RegistryValue
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final RegistryValue getValue(RegistryKey key, String name)
		throws IOException, SMBException {

		// Create a registry value

		RegistryValue regVal = new RegistryValue(name);
		return getValue(key, regVal);
	}

	/**
	 * Return the value for the specified key and parameter
	 *
	 * @param key RegistryKey
	 * @param regval RegistryValue
	 * @return RegistryValue
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final RegistryValue getValue(RegistryKey key, RegistryValue regval)
		throws IOException, SMBException {

		// Build the query value request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		String name = regval.getName();

		buf.putHandle(key);
		buf.putUnicodeHeader(name, true);
		buf.putString(name != null ? name : "", DCEBuffer.ALIGN_INT, true);

		// The following values should not be required as they are output parameters incorrectly
		// specified
		// as input paramters in the IDL

		buf.putPointer(true);
		buf.putPointer(true);

		buf.putPointer(true);
		buf.putInt(MaxReturnBufferSize);

		buf.putPointer(false);
		buf.putInt(0);

		buf.putPointer(true);
		buf.putInt(MaxReturnBufferSize);

		buf.putPointer(true);
		buf.putInt(0);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Winreg.RegQueryValue, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the query value DCE request

		doDCERequest(pkt);

		// Retrieve the registry value information

		buf = getRxBuffer();

		try {
			checkStatus(buf.getStatusCode());
			regval.readValue(buf);
		}
		catch (DCEBufferException ex) {
		}

		// Return the registry value

		return regval;
	}

	/**
	 * Return the default value for the specified key
	 *
	 * @param key RegistryKey
	 * @return RegistryValue
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final RegistryValue getDefaultValue(RegistryKey key)
		throws IOException, SMBException {

		// Build the query value request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(key);

		buf.putShort(2);
		buf.putShort(2);
		buf.putPointer(true);

		buf.putInt(1);
		buf.putInt(0);
		buf.putInt(1);
		buf.putInt(0); // 2 bytes padding

		// The following values should not be required as they are output parameters incorrectly
		// specified
		// as input paramters in the IDL

		buf.putPointer(true);
		buf.putPointer(true);

		buf.putPointer(true);
		buf.putInt(MaxReturnBufferSize);

		buf.putPointer(false);
		buf.putInt(0);

		buf.putPointer(true);
		buf.putInt(MaxReturnBufferSize);

		buf.putPointer(true);
		buf.putInt(0);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Winreg.RegQueryValue, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the query value DCE request

		doDCERequest(pkt);

		// Retrieve the registry value information

		buf = getRxBuffer();
		RegistryValue regVal = new RegistryValue("(Default)");

		try {
			checkStatus(buf.getStatusCode());
			regVal.readValue(buf);
		}
		catch (DCEBufferException ex) {
		}

		// Return the registry value

		return regVal;
	}

	/**
	 * Return the values for the specified registry key
	 * 
	 * @param key RegistryKey
	 * @return List of registry values
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final List<RegistryValue> getValuesForKey(RegistryKey key)
		throws IOException, SMBException {

		// Get the key information to get the count of values

		RegistryKeyInfo keyInfo = getKeyInfo(key, null);

		// Allocate the vector for the registry values

		List<RegistryValue> regValues = new ArrayList<RegistryValue>();

		// Get the registry values

		if ( keyInfo.getNumberOfValues() > 0) {

			// Enumerate the registry values

			int valIdx = 0;

			while (valIdx < keyInfo.getNumberOfValues()) {

				// Build the enumerate value request

				DCEBuffer buf = getBuffer();
				buf.resetBuffer();

				buf.putHandle(key);
				buf.putInt(valIdx++);

				// The following values should not be required as they are output parameters
				// incorrectly specified
				// as input paramters in the IDL

				buf.putUnicodeReturn(0x200);
				buf.putStringReturn(0x100, DCEBuffer.ALIGN_INT); // size MUST be half the Unicode
																	// header length

				buf.putPointer(true);
				buf.putInt(0);

				buf.putPointer(true);
				buf.putInt(keyInfo.getMaximumValueNameLength());

				buf.putPointer(true);
				buf.putInt(keyInfo.getMaximumValueLength());

				// Initialize the DCE request

				DCEPacket pkt = getPacket();
				try {
					pkt.initializeDCERequest(getHandle(), Winreg.RegEnumValue, buf, getMaximumTransmitSize(), getNextCallId());
				}
				catch (DCEBufferException ex) {
					ex.printStackTrace();
				}

				// Send the enumerate value DCE request

				doDCERequest(pkt);

				// Retrieve registry value information

				buf = getRxBuffer();
				RegistryValue regVal = new RegistryValue();

				try {

					// Read the registry value details

					checkStatus(buf.getStatusCode());
					regVal.readObject(buf);

					// Fetch the actual value

					if ( regVal.getName() != null && regVal.getName().length() > 0) {

						// Get the registry value

						getValue(key, regVal);

						// Add the registry value to the list

						regValues.add(regVal);
					}
				}
				catch (DCEBufferException ex) {
					ex.printStackTrace();
				}

			}
		}

		// Return the list of registry values

		return regValues;
	}

	/**
	 * Return the sub-keys for the specified registry key
	 * 
	 * @param parentKey RegistryKey
	 * @return List of registry keys
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final List<RegistryKey> getKeysForKey(RegistryKey parentKey)
		throws IOException, SMBException {

		// Get the key information to get the count of sub-keys

		RegistryKeyInfo keyInfo = getKeyInfo(parentKey, null);

		// Allocate the vector for the registry keys

		List<RegistryKey> regKeys = new ArrayList<RegistryKey>();
		RegistryKey lastChild = null;

		// Get the registry values

		if ( keyInfo.getNumberOfSubkeys() > 0) {

			// Enumerate the registry subkeys

			int keyIdx = 0;

			while (keyIdx < keyInfo.getNumberOfSubkeys()) {

				// Build the enumerate value request

				DCEBuffer buf = getBuffer();
				buf.resetBuffer();

				buf.putHandle(parentKey);
				buf.putInt(keyIdx++);

				// The following values should not be required as they are output parameters
				// incorrectly specified
				// as input paramters in the IDL

				buf.putUnicodeReturn(0x200);
				buf.putStringReturn(0x100, DCEBuffer.ALIGN_INT); // size MUST be half the Unicode
																	// header length

				buf.putPointer(false);
				buf.putPointer(false);
				buf.putPointer(false);

				// Initialize the DCE request

				DCEPacket pkt = getPacket();
				try {
					pkt.initializeDCERequest(getHandle(), Winreg.RegEnumKey, buf, getMaximumTransmitSize(), getNextCallId());
				}
				catch (DCEBufferException ex) {
					ex.printStackTrace();
				}

				// Send the enumerate value DCE request

				doDCERequest(pkt);

				// Retrieve registry value information

				buf = getRxBuffer();
				RegistryKey regKey = new RegistryKey(parentKey);

				try {

					// Read the registry key details

					checkStatus(buf.getStatusCode());
					regKey.readObject(buf);
				}
				catch (DCEBufferException ex) {
					ex.printStackTrace();
				}

				// Link the registry keys

				if ( parentKey.hasChild() == false)
					parentKey.setChild(regKey);

				if ( lastChild != null)
					lastChild.setSibling(regKey);
				lastChild = regKey;

				// Add the registry key to the list

				regKeys.add(regKey);
			}
		}

		// Return the list of registry keys

		return regKeys;
	}

	/**
	 * Create a new key
	 * 
	 * @param parent RegistryKey
	 * @param keyName String
	 * @return RegistryKey
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final RegistryKey createKey(RegistryKey parent, String keyName)
		throws IOException, SMBException {

		// Build the create key request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(parent);
		buf.putUnicodeHeader(keyName, true);
		buf.putString(keyName, DCEBuffer.ALIGN_INT, true);

		buf.putInt(0);
		buf.putInt(0);
		buf.putInt(0);

		buf.putInt(AccessMode.NTGenericAll); // desired access ?

		buf.putInt(0);
		buf.putInt(0);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Winreg.RegCreateKey, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the query key information DCE request

		doDCERequest(pkt);

		// Retrieve registry key information

		buf = getRxBuffer();
		RegistryKey key = new RegistryKey(keyName, parent);

		try {
			checkStatus(buf.getStatusCode());
			buf.getHandle(key);
		}
		catch (DCEBufferException ex) {
		}

		// Return the new registry key handle

		return key;
	}

	/**
	 * Delete a registry key
	 * 
	 * @param parent RegistryKey
	 * @param keyName String
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void deleteKey(RegistryKey parent, String keyName)
		throws IOException, SMBException {

		// Build the delete key request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(parent);
		buf.putUnicodeHeader(keyName, true);
		buf.putString(keyName, DCEBuffer.ALIGN_INT, true);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Winreg.RegDeleteKey, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the query key information DCE request

		doDCERequest(pkt);

		// Check the delete status

		buf = getRxBuffer();

		try {
			checkStatus(buf.getInt());
		}
		catch (DCEBufferException ex) {
		}
	}

	/**
	 * Create a new registry value
	 * 
	 * @param parent RegistryKey
	 * @param value RegistryValue
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void createValue(RegistryKey parent, RegistryValue value)
		throws IOException, SMBException {

		// Build the create value request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		// Pack the registry key handle

		buf.putHandle(parent);

		// Pack the value name

		buf.putUnicodeHeader(value.getName(), true);
		buf.putString(value.getName(), DCEBuffer.ALIGN_INT, true);

		// Pack the value data type

		buf.putInt(value.getDataType());

		// Pack the value data

		if ( value.getRawValue() != null) {

			// Pack the raw data value

			int len = value.getRawValue().length;

			buf.putInt(len);
			buf.putBytes(value.getRawValue(), len, DCEBuffer.ALIGN_INT);
			buf.putInt(len);
		}
		else {

			// Pack a null data block

			buf.putInt(0);
			buf.putInt(0);
		}

		// Send the create key DCE request

		try {
			doDCERequest(Winreg.RegCreateValue, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Check the create value status

		buf = getRxBuffer();

		try {
			checkStatus(buf.getInt());
		}
		catch (DCEBufferException ex) {
		}
	}

	/**
	 * Delete a registry value
	 * 
	 * @param parent RegistryKey
	 * @param valueName String
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void deleteValue(RegistryKey parent, String valueName)
		throws IOException, SMBException {

		// Build the delete value request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(parent);
		buf.putUnicodeHeader(valueName, true);
		buf.putString(valueName, DCEBuffer.ALIGN_INT, true);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Winreg.RegDeleteValue, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the query key information DCE request

		doDCERequest(pkt);

		// Check the delete status

		buf = getRxBuffer();

		try {
			checkStatus(buf.getInt());
		}
		catch (DCEBufferException ex) {
		}
	}

	/**
	 * Shutdown a remote system, and optionally reboot the system
	 * 
	 * @param msg String
	 * @param tmo int
	 * @param reboot boolean
	 * @param force boolean
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void shutdownServer(String msg, int tmo, boolean reboot, boolean force)
		throws IOException, SMBException {

		// Build the shutdown server request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putPointer(true);
		buf.putPointer(true);

		buf.putPointer(true);
		buf.putUnicodeHeader(msg, false);
		buf.putString(msg, DCEBuffer.ALIGN_INT, false);

		buf.putInt(tmo);
		buf.putByte(force ? 1 : 0);
		buf.putByte(reboot ? 1 : 0);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Winreg.RegShutdown, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the open registry key DCE request

		doDCERequest(pkt);

		// Get the reply status

		buf = getRxBuffer();

		try {
			checkStatus(buf.getInt());
		}
		catch (DCEBufferException ex) {
		}
	}

	/**
	 * Open a root key on the remote server
	 * 
	 * @param keyid int
	 * @return RegistryKey
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final RegistryKey openRootKey(int keyid)
		throws IOException, SMBException {

		// Build the open key DCE request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putPointer(true);
		buf.putShort(0x84e0);
		buf.putShort(0);
		buf.putInt(AccessMode.NTMaximumAllowed);

		// Get the required opcode

		int opCode = -1;
		String name = null;

		switch (keyid) {
			case Winreg.HKEYClassesRoot:
				opCode = Winreg.RegOpenHKR;
				name = "HKEY_CLASSES_ROOT";
				break;
			case Winreg.HKEYLocalMachine:
				opCode = Winreg.RegOpenHKLM;
				name = "HKEY_LOCAL_MACHINE";
				break;
			case Winreg.HKEYCurrentUser:
				opCode = Winreg.RegOpenHKCU;
				name = "HKEY_CURRENT_USER";
				break;
			case Winreg.HKEYUsers:
				opCode = Winreg.RegOpenHKU;
				name = "HKEY_USERS";
				break;
			case Winreg.HKEYPerformanceData:
				opCode = Winreg.RegOpenHKPD;
				name = "HKEY_PERFORMANCE_DATA";
				break;
		}

		// Check if there is an opcode for the root key

		if ( opCode == -1)
			return null;

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), opCode, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the open registry hive DCE request

		doDCERequest(pkt);

		// Retrieve the policy handle from the response

		buf = getRxBuffer();
		RegistryKey regKey = new RegistryKey(name);

		try {
			checkStatus(buf.getStatusCode());
			buf.getHandle(regKey);
		}
		catch (DCEBufferException ex) {
		}

		// Return the registry key handle

		return regKey;
	}
}
