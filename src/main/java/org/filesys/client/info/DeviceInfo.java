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

package org.filesys.client.info;

/**
 * Device Information Class
 * 
 * @author gkspencer
 */
public class DeviceInfo {

	//	Device types
	
	public static final int Beep				= 1;
	public static final int CDROM				= 2;
	public static final int CDROMFileSystem		= 3;
	public static final int Controller			= 4;
	public static final int Datalink			= 5;
	public static final int DFS					= 6;
	public static final int Disk				= 7;
	public static final int DiskFileSystem		= 8;
	public static final int FileSystem			= 9;
	public static final int InportPort			= 10;
	public static final int Keyboard			= 11;
	public static final int Mailslot			= 12;
	public static final int MIDIIn				= 13;
	public static final int MIDIOut				= 14;
	public static final int Mouse				= 15;
	public static final int MultiUNCProvider	= 16;
	public static final int NamedPipe			= 17;
	public static final int Network				= 18;
	public static final int NetworkBrowser		= 19;
	public static final int NetworkFileSystem	= 20;
	public static final int Null				= 21;
	public static final int ParallelPort		= 22;
	public static final int PhysicalNetcard		= 23;
	public static final int Printer				= 24;
	public static final int Scanner				= 25;
	public static final int SerialMousePort		= 26;
	public static final int SerialPort			= 27;
	public static final int Screen				= 28;
	public static final int Sound				= 29;
	public static final int Streams				= 30;
	public static final int Tape				= 31;
	public static final int TapeFileSystem		= 32;
	public static final int Transport			= 33;
	public static final int Unknown				= 34;
	public static final int Video				= 35;
	public static final int VirtualDisk			= 36;
	public static final int WaveIn				= 37;
	public static final int WaveOut				= 38;
	public static final int Port8042			= 39;
	public static final int NetworkRedirector	= 40;
	public static final int Battery				= 41;
	public static final int BusExtended			= 42;
	public static final int Modem				= 43;
	public static final int VDM					= 44;
	
	//	Device characteristics
	
	public static final int RemoveableMedia		= 0x0001;
	public static final int ReadOnlyDevice		= 0x0002;
	public static final int FloppyDisk			= 0x0004;
	public static final int WriteOnceMedia		= 0x0008;
	public static final int RemoteDevice		= 0x0010;
	public static final int DeviceMounted		= 0x0020;
	public static final int VirtualVolume		= 0x0040;
	
	//	Device type
	
	private int m_type;
	
	//	Device characteristics
	
	private int m_chars;
	
	/**
	 * Class constructor
	 * 
	 * @param typ int
	 * @param chr int
	 */
	public DeviceInfo(int typ, int chr) {
		m_type = typ;
		m_chars = chr;
	}

	/**
	 * Get the device type
	 * 
	 * @return int
	 */
	public final int getType() {
		return m_type;
	}

	/**
	 * Return the device characteristics
	 * 
	 * @return int
	 */
	public final int getCharacteristics() {
		return m_chars;
	}

	/**
	 * Determine if the device has removeable media
	 * 
	 * @return boolean
	 */
	public final boolean isRemoveable() {
		return hasFlag(RemoveableMedia);
	}

	/**
	 * Determine if the device is read only
	 * 
	 * @return boolean
	 */
	public final boolean isReadOnly() {
		return hasFlag(ReadOnlyDevice);
	}

	/**
	 * Determine if the device is a floppy disk type device
	 * 
	 * @return boolean
	 */
	public final boolean isFloppyDisk() {
		return hasFlag(FloppyDisk);
	}

	/**
	 * Determine if the device is a write once device
	 * 
	 * @return boolean
	 */
	public final boolean isWriteOnce() {
		return hasFlag(WriteOnceMedia);
	}

	/**
	 * Determine if the device is a remote device
	 * 
	 * @return boolean
	 */
	public final boolean isRemote() {
		return hasFlag(RemoteDevice);
	}

	/**
	 * Determine if the device is mounted
	 * 
	 * @return boolean
	 */
	public final boolean isMounted() {
		return hasFlag(DeviceMounted);
	}

	/**
	 * Determine if the device is a virtual device
	 * 
	 * @return boolean
	 */
	public final boolean isVirtual() {
		return hasFlag(VirtualVolume);
	}

	/**
	 * Check if the device characteristic flag is set
	 * 
	 * @param flg int
	 * @return boolan
	 */
	private final boolean hasFlag(int flg) {
		return (m_chars & flg) != 0 ? true : false;
	}

	/**
	 * Return the device information as a string
	 * 
	 * @return String
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();

		str.append("[");
		str.append(getType());
		str.append(",0x");
		str.append(Integer.toHexString(getCharacteristics()));
		str.append("]");

		return str.toString();
	}
}
