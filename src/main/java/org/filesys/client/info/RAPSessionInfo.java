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

import java.io.Serializable;
import java.util.*;

import org.filesys.client.admin.RAPReadable;
import org.filesys.smb.dcerpc.info.SessionInfo;

/**
 * Session Information Class.
 * 
 * <p>
 * The session information class contains the details of a connection to a remote server.
 * 
 * @author gkspencer
 */
public class RAPSessionInfo extends SessionInfo implements RAPReadable, Serializable {

	/**
	 * Default constructor.
	 */
	public RAPSessionInfo() {
		super();
	}

	/**
	 * Class constructor
	 * 
	 * @param infoLevel int
	 * @param objs Vector
	 */
	public RAPSessionInfo(int infoLevel, Vector objs) {
		readRAPObject(infoLevel, objs);
	}

	/**
	 * Load the session information from the vector of objects
	 * 
	 * @param infoLevel int
	 * @param objs Vector
	 */
	public void readRAPObject(int infoLevel, Vector objs) {

		// Clear the string values in the current object

		clearStrings();

		// Load the session information depending on the information level

		Short val = null;
		Integer ival = null;

		switch (infoLevel) {

			// Information level 1

			case 1:
				setClientName((String) objs.elementAt(0));
				setUserName((String) objs.elementAt(1));

				val = (Short) objs.elementAt(3);
				setNumberOfOpenFiles(val.intValue());

				ival = (Integer) objs.elementAt(5);
				setSessionTime(ival.intValue());

				ival = (Integer) objs.elementAt(6);
				setIdleTime(ival.intValue());

				ival = (Integer) objs.elementAt(7);
				setUserFlags(ival.intValue());
				break;
		}
	}
}