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
import org.filesys.smb.dcerpc.info.ServiceStatusInfo;

/**
 * Service Information Class.
 * 
 * <p>
 * The service information class contains the details of a service on a remote server.
 * 
 * @author gkspencer
 */
public class RAPServiceInfo extends ServiceStatusInfo implements RAPReadable, Serializable {

	/**
	 * Default constructor
	 */
	public RAPServiceInfo() {
		super();
	}

	/**
	 * Class constructor
	 * 
	 * @param infoLevel int
	 * @param objs Vector
	 */
	public RAPServiceInfo(int infoLevel, Vector objs) {
		readRAPObject(infoLevel, objs);
	}

	/**
	 * Load the service information from the vector of objects
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

			// Information level 2

			case 2:
				setName((String) objs.elementAt(0));
				break;
		}
	}
}