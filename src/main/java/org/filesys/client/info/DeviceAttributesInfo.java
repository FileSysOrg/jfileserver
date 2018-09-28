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
 * Device Attributes Information Class
 * 
 * @author gkspencer
 */
public class DeviceAttributesInfo {

	//	File system attributes
	
	public final static int CaseSensitiveSearch		= 0x0001;
	public final static int CasePreservedNames		= 0x0002;
	public final static int PersistentACLs			= 0x0004;
	public final static int FileCompression			= 0x0008;
	public final static int VolumeQuotas			= 0x0010;
	public final static int DeviceMounted			= 0x0020;
	public final static int VolumeIsCompressed		= 0x8000;
	
	//	File system attributes
	
	private int m_attrib;
	
	//	Maximum file name component length
	
	private int m_maxNameLen;
	
	//	File system name
	
	private String m_fileSysName;
	
	/**
	 * Class constructor
	 * 
	 * @param attr int
	 * @param nameLen int
	 * @param name String
	 */
	public DeviceAttributesInfo(int attr, int nameLen, String name) {
		m_attrib = attr;
		m_maxNameLen = nameLen;
		m_fileSysName = name;
	}

	/**
	 * Return the file system name
	 * 
	 * @return String
	 */
	public final String getFileSystemName() {
		return m_fileSysName;
	}

	/**
	 * Return the maximum file name component length
	 * 
	 * @return int
	 */
	public final int getMaximumNameComponentLength() {
		return m_maxNameLen;
	}

	/**
	 * Return the file system attributes
	 * 
	 * @return int
	 */
	public final int getAttributes() {
		return m_attrib;
	}

	/**
	 * Determine if the file system has case sensistive searches
	 * 
	 * @return boolean
	 */
	public final boolean hasCaseSensitiveSearches() {
		return hasFlag(CaseSensitiveSearch);
	}

	/**
	 * Determine if the file system preserves file name case
	 * 
	 * @return boolean
	 */
	public final boolean hasPreserveNameCase() {
		return hasFlag(CasePreservedNames);
	}

	/**
	 * Determine if the file system has persistent ACLs
	 * 
	 * @return boolean
	 */
	public final boolean hasPersistentACLs() {
		return hasFlag(PersistentACLs);
	}

	/**
	 * Determine if the file system has file compression
	 * 
	 * @return boolean
	 */
	public final boolean hasFileCompression() {
		return hasFlag(FileCompression);
	}

	/**
	 * Determine if the file system has volume quotas
	 * 
	 * @return boolean
	 */
	public final boolean hasVolumeQuotas() {
		return hasFlag(VolumeQuotas);
	}

	/**
	 * Determine if the file system is mounted
	 * 
	 * @return boolean
	 */
	public final boolean isMounted() {
		return hasFlag(DeviceMounted);
	}

	/**
	 * Determine if the file system is compressed
	 * 
	 * @return boolean
	 */
	public final boolean isCompressed() {
		return hasFlag(VolumeIsCompressed);
	}

	/**
	 * Test the specified attribute flag
	 * 
	 * @param flg int
	 * @return boolean
	 */
	private final boolean hasFlag(int flg) {
		return (m_attrib & flg) != 0 ? true : false;
	}

	/**
	 * Return the device attributes as a string
	 * 
	 * @return String
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();

		str.append("[");
		str.append(getFileSystemName());
		str.append(",");
		str.append(getMaximumNameComponentLength());
		str.append(",0x");
		str.append(Integer.toHexString(getAttributes()));
		str.append("]");

		return str.toString();
	}
}
