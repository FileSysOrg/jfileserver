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

/**
 * Server Session Information Class
 * 
 * @author gkspencer
 */
public class SessionInfo implements DCEReadable {

	//	Information levels supported
	public static final int InfoLevel0		= 0;
	public static final int InfoLevel1		= 1;
	public static final int InfoLevel2		= 2;
	public static final int InfoLevel10		= 10;
	public static final int InfoLevel502	= 502;
  
	//	Session flags
	public final static int Guest				= 0x0001;
	public final static int EncryptedPassword	= 0x0002;
  
	//	Information level
	private int m_infoLevel;

	//	Session details
	private String m_client;
	private String m_user;
	
	private int m_openFiles;
	private int m_sessTime;
	private int m_idleTime;
	private int m_userFlags;
	
	private String m_clientType;
	private String m_transport;

	/**
	 * Default constructor
	 */
	public SessionInfo() {
	}

	/**
	 * Class constructor
	 *
	 * @param infoLevel int
	 */
	public SessionInfo(int infoLevel) {
		m_infoLevel = infoLevel;
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
	 * Return the client name
	 *
	 * @return String
	 */
	public final String getClientName() {
		return m_client;
	}

	/**
	 * Return the user name
	 *
	 * @return String
	 */
	public final String getUserName() {
		return m_user;
	}

	/**
	 * Return the number of open files on this session
	 *
	 * @return int
	 */
	public final int getNumberOfOpenFiles() {
		return m_openFiles;
	}

	/**
	 * Return the session time in seconds
	 *
	 * @return int
	 */
	public final int getSessionTime() {
		return m_sessTime;
	}

	/**
	 * Return the session idle time in seconds
	 *
	 * @return int
	 */
	public final int getIdleTime() {
		return m_idleTime;
	}

	/**
	 * Return the user flags
	 *
	 * @return int
	 */
	public final int getUserFlags() {
		return m_userFlags;
	}

	/**
	 * Check if the session is using the guest account
	 *
	 * @return boolean
	 */
	public final boolean isGuest() {
		return (m_userFlags & Guest) != 0 ? true : false;
	}

	/**
	 * Check if the session used an encrypted password
	 *
	 * @return boolean
	 */
	public final boolean usedEncryptedPassword() {
		return (m_userFlags & EncryptedPassword) != 0 ? true : false;
	}

	/**
	 * Return the client type
	 *
	 * @return String
	 */
	public final String getClientType() {
		return m_clientType;
	}

	/**
	 * Return the transport
	 *
	 * @return String
	 */
	public final String getTransport() {
		return m_transport;
	}

	/**
	 * Clear all string values
	 */
	protected final void clearStrings() {

		// Clear the string values
		m_client = null;
		m_user = null;
		m_clientType = null;
		m_transport = null;
	}

	/**
	 * Read the session information from the DCE buffer
	 *
	 * @param buf DCEBuffer
	 * @exception DCEBufferException DCE buffer error
	 */
	public void readObject(DCEBuffer buf)
			throws DCEBufferException {

		// Clear the strings
		clearStrings();

		// Unpack the session information
		switch (getInformationLevel()) {

			// Information level 0
			case InfoLevel0:
				m_client = buf.getPointer() != 0 ? "" : null;
				break;

			// Information level 1
			case InfoLevel1:
				m_client = buf.getPointer() != 0 ? "" : null;
				m_user = buf.getPointer() != 0 ? "" : null;

				m_openFiles = buf.getInt();
				m_sessTime = buf.getInt();
				m_idleTime = buf.getInt();
				m_userFlags = buf.getInt();
				break;

			// Information level 2
			case InfoLevel2:
				m_client = buf.getPointer() != 0 ? "" : null;
				m_user = buf.getPointer() != 0 ? "" : null;

				m_openFiles = buf.getInt();
				m_sessTime = buf.getInt();
				m_idleTime = buf.getInt();
				m_userFlags = buf.getInt();

				m_clientType = buf.getPointer() != 0 ? "" : null;
				break;

			// Information level 10
			case InfoLevel10:
				m_client = buf.getPointer() != 0 ? "" : null;
				m_user = buf.getPointer() != 0 ? "" : null;

				m_sessTime = buf.getInt();
				m_idleTime = buf.getInt();
				break;

			// Information level 502
			case InfoLevel502:
				m_client = buf.getPointer() != 0 ? "" : null;
				m_user = buf.getPointer() != 0 ? "" : null;

				m_openFiles = buf.getInt();
				m_sessTime = buf.getInt();
				m_idleTime = buf.getInt();
				m_userFlags = buf.getInt();

				m_clientType = buf.getPointer() != 0 ? "" : null;
				m_transport = buf.getPointer() != 0 ? "" : null;
				break;
		}
	}

	/**
	 * Read the strings for this session information from the DCE/RPC buffer
	 *
	 * @param buf DCEBuffer
	 * @exception DCEBufferException DCE buffer error
	 */
	public void readStrings(DCEBuffer buf)
			throws DCEBufferException {

		// Read the strings for this session information
		if (getClientName() != null)
			m_client = buf.getString(DCEBuffer.ALIGN_INT);

		if (getUserName() != null)
			m_user = buf.getString(DCEBuffer.ALIGN_INT);

		if (getClientType() != null)
			m_clientType = buf.getString(DCEBuffer.ALIGN_INT);

		if (getTransport() != null)
			m_transport = buf.getString(DCEBuffer.ALIGN_INT);
	}

	/**
	 * Set the client name
	 *
	 * @param client String
	 */
	public final void setClientName(String client) {
		m_client = client;
	}

	/**
	 * Set the user name
	 *
	 * @param userName String
	 */
	public final void setUserName(String userName) {
		m_user = userName;
	}

	/**
	 * Set the number of open files on this session
	 *
	 * @param files int
	 */
	public final void setNumberOfOpenFiles(int files) {
		m_openFiles = files;
	}

	/**
	 * Set the session time in seconds
	 *
	 * @param sessTime int
	 */
	public final void setSessionTime(int sessTime) {
		m_sessTime = sessTime;
	}

	/**
	 * Set the session idle time in seconds
	 *
	 * @param idleTime int
	 */
	public final void setIdleTime(int idleTime) {
		m_idleTime = idleTime;
	}

	/**
	 * Set the user flags
	 *
	 * @param flags int
	 */
	public final void setUserFlags(int flags) {
		m_userFlags = flags;
	}

	/**
	 * Return the session information as a string
	 *
	 * @return String
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();

		str.append("[Client=");
		str.append(getClientName());
		str.append(":Level=");
		str.append(getInformationLevel());
		str.append(":");

		if (getInformationLevel() >= InfoLevel1) {
			str.append("User=");
			str.append(getUserName());
			str.append(",OpenFiles=");
			str.append(getNumberOfOpenFiles());
			str.append(",Connected=");
			str.append(getSessionTime());
			str.append(",Idle=");
			str.append(getIdleTime());
			str.append(",UserFlags=");
			str.append(getUserFlags());
		}

		if (getInformationLevel() >= InfoLevel2) {
			str.append(",ClientType=");
			str.append(getClientType());
		}

		if (getInformationLevel() == InfoLevel502) {
			str.append(",Transport=");
			str.append(getTransport());
		}

		str.append("]");
		return str.toString();
	}
}
