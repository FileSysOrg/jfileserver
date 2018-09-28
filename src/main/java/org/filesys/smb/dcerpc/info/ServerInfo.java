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

package org.filesys.smb.dcerpc.info;

import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCEReadable;
import org.filesys.smb.dcerpc.DCEWriteable;

/**
 * Server Information Class
 *
 * @author gkspencer
 */
public class ServerInfo implements DCEWriteable, DCEReadable {

	//	Information levels supported
	public static final int InfoLevel0		= 0;
	public static final int InfoLevel1		= 1;
	public static final int InfoLevel101	= 101;
	public static final int InfoLevel102	= 102;
  
	//	Server platform ids
	public final static int PLATFORM_OS2	= 400;
	public final static int PLATFORM_NT		= 500;
	
	//	Information level
	private int m_infoLevel;
	
	//	Server information
	private int m_platformId;
	private String m_name;
	private int m_verMajor;
	private int m_verMinor;
	private int m_srvType;
	private String m_comment;

	/**
	 * Default constructor
	 */
	public ServerInfo() {
	}

	/**
	 * Class constructor
	 *
	 * @param lev int
	 */
	public ServerInfo(int lev) {
		m_infoLevel = lev;
	}

	/**
	 * Get the information level
	 *
	 * @return int
	 */
	public final int getInformationLevel() {
		return m_infoLevel;
	}

	/**
	 * Get the server name
	 *
	 * @return String
	 */
	public final String getServerName() {
		return m_name;
	}

	/**
	 * Get the server comment
	 *
	 * @return String
	 */
	public final String getComment() {
		return m_comment;
	}

	/**
	 * Get the server platform id
	 *
	 * @return int
	 */
	public final int getPlatformId() {
		return m_platformId;
	}

	/**
	 * Get the servev major version
	 *
	 * @return int
	 */
	public final int getMajorVersion() {
		return m_verMajor;
	}

	/**
	 * Get the server minor version
	 *
	 * @return int
	 */
	public final int getMinorVersion() {
		return m_verMinor;
	}

	/**
	 * Get the server type flags
	 *
	 * @return int
	 */
	public final int getServerType() {
		return m_srvType;
	}

	/**
	 * Set the server name
	 *
	 * @param name String
	 */
	public final void setServerName(String name) {
		m_name = name;
	}

	/**
	 * Set the server comment
	 *
	 * @param comment String
	 */
	public final void setComment(String comment) {
		m_comment = comment;
	}

	/**
	 * Set the information level
	 *
	 * @param lev int
	 */
	public final void setInformationLevel(int lev) {
		m_infoLevel = lev;
	}

	/**
	 * Set the server platform id
	 *
	 * @param id int
	 */
	public final void setPlatformId(int id) {
		m_platformId = id;
	}

	/**
	 * Set the server type flags
	 *
	 * @param typ int
	 */
	public final void setServerType(int typ) {
		m_srvType = typ;
	}

	/**
	 * Set the server version
	 *
	 * @param verMajor int
	 * @param verMinor int
	 */
	public final void setVersion(int verMajor, int verMinor) {
		m_verMajor = verMajor;
		m_verMinor = verMinor;
	}

	/**
	 * Clear the string values
	 */
	protected final void clearStrings() {

		//	Clear the string values

		m_name = null;
		m_comment = null;
	}

	/**
	 * Read the server information from the DCE/RPC buffer
	 *
	 * @param buf DCEBuffer
	 * @exception DCEBufferException DCE buffer error
	 */
	public void readObject(DCEBuffer buf)
			throws DCEBufferException {

		//	Clear the string values
		clearStrings();

		//	Read the server information details
		m_infoLevel = buf.getInt();
		buf.skipPointer();

		//	Unpack the server information
		switch (getInformationLevel()) {

			//	Information level 0
			case InfoLevel0:
				if (buf.getPointer() != 0)
					m_name = buf.getString(DCEBuffer.ALIGN_INT);
				break;

			//	Information level 101/1
			case InfoLevel1:
			case InfoLevel101:
				m_platformId = buf.getInt();
				buf.skipPointer();
				m_verMajor = buf.getInt();
				m_verMinor = buf.getInt();
				m_srvType = buf.getInt();
				buf.skipPointer();

				m_name = buf.getString(DCEBuffer.ALIGN_INT);
				m_comment = buf.getString();
				break;

			//	Level 102
			case InfoLevel102:
				break;
		}
	}

	/**
	 * Read the strings for this object from the DCE/RPC buffer
	 *
	 * @param buf DCEBuffer
	 * @exception DCEBufferException DCE buffer exception
	 */
	public void readStrings(DCEBuffer buf)
			throws DCEBufferException {

		//	Not required
	}

	/**
	 * Write a server information structure
	 *
	 * @param buf    DCEBuffer
	 * @param strBuf DCEBuffer
	 */
	public void writeObject(DCEBuffer buf, DCEBuffer strBuf) {

		//	Output the server information structure
		buf.putInt(getInformationLevel());
		buf.putPointer(true);

		//	Output the required information level
		switch (getInformationLevel()) {

			//	Information level 0
			case InfoLevel0:
				buf.putPointer(getServerName() != null);
				if (getServerName() != null)
					strBuf.putString(getServerName(), DCEBuffer.ALIGN_INT, true);
				break;

			//	Information level 101/1
			case InfoLevel1:
			case InfoLevel101:
				buf.putInt(getPlatformId());
				buf.putPointer(true);
				buf.putInt(getMajorVersion());
				buf.putInt(getMinorVersion());
				buf.putInt(getServerType());
				buf.putPointer(true);

				strBuf.putString(getServerName(), DCEBuffer.ALIGN_INT, true);
				strBuf.putString(getComment() != null ? getComment() : "", DCEBuffer.ALIGN_INT, true);
				break;

			//	Level 102
			case InfoLevel102:
				break;
		}
	}

	/**
	 * Return the server information as a string
	 *
	 * @return String
	 */
	public String toString() {
		return "";
	}
}
