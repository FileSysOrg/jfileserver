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

import java.util.*;

import org.filesys.client.admin.RAPReadable;
import org.filesys.smb.dcerpc.info.ServerInfo;

/**
 * SMB server info class
 * 
 * <p>
 * The ServerInfo class contains details of the remote server that an AdminSession is connected to.
 * The class is returned by the AdminSession.getServerInfo () method, or a list of the available
 * servers may be returned as an ServerList by the AdminSession.getServerList () method.
 * 
 * @author gkspencer
 */
public final class RAPServerInfo extends ServerInfo implements RAPReadable, java.io.Serializable {

	// Flag to indicate if the object refers to a domain or server

	private boolean m_domain;

	/**
	 * Default constructor
	 */
	public RAPServerInfo() {
	}

	/**
	 * Class constructor
	 * 
	 * @param infoLevel int
	 * @param objs Vector
	 * @param domain boolean
	 */
	public RAPServerInfo(int infoLevel, Vector objs, boolean domain) {
		readRAPObject(infoLevel, objs);

		m_domain = domain;
	}

	/**
	 * Class constructor
	 * 
	 * @param name String
	 * @param domain boolean
	 */
	public RAPServerInfo(String name, boolean domain) {
		setServerName(name);
		m_domain = domain;
	}

	/**
	 * Determine if this object refers to a domain or server
	 * 
	 * @return boolean
	 */
	public final boolean isDomain() {
		return m_domain;
	}

	/**
	 * Load the server information from the vector of objects
	 * 
	 * @param infoLevel int
	 * @param objs Vector
	 */
	public void readRAPObject(int infoLevel, Vector objs) {

		// Clear the string values

		clearStrings();

		// Load the server information depending on the information level

		Integer ival = null;
		Byte byt1 = null;
		Byte byt2 = null;

		switch (infoLevel) {

			// Information level 1

			case InfoLevel1:
				setServerName((String) objs.elementAt(0));

				byt1 = (Byte) objs.elementAt(1);
				byt2 = (Byte) objs.elementAt(2);
				setVersion(byt1.intValue(), byt2.intValue());

				ival = (Integer) objs.elementAt(3);
				setServerType(ival.intValue());

				setComment((String) objs.elementAt(4));
				break;
		}
	}
}