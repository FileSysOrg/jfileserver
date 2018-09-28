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
import org.filesys.smb.dcerpc.info.WorkstationInfo;

/**
 * Workstation information class
 * 
 * <p>
 * The WorkStationInfo class contains the basic details of a remote workstation.
 * 
 * @author gkspencer
 */
public class RAPWorkstationInfo extends WorkstationInfo implements RAPReadable, Serializable {

	/**
	 * Default constructor
	 */
	public RAPWorkstationInfo() {
	}

	/**
	 * Class constructor
	 * 
	 * @param infoLevel int
	 * @param objs Vector
	 */
	public RAPWorkstationInfo(int infoLevel, Vector objs) {
		readRAPObject(infoLevel, objs);
	}

	/**
	 * Load the workstation information from the vector of objects
	 * 
	 * @param infoLevel int
	 * @param objs Vector
	 */
	public void readRAPObject(int infoLevel, Vector objs) {

		// Clear the string values in the current object

		clearStrings();

		// Load the share information depending on the information level

		Byte byt1 = null;
		Byte byt2 = null;

		switch (infoLevel) {

			// Information level 10

			case 10:
				setWorkstationName((String) objs.elementAt(0));
				setUserName((String) objs.elementAt(1));
				setDomain((String) objs.elementAt(2));

				byt1 = (Byte) objs.elementAt(3);
				byt2 = (Byte) objs.elementAt(4);
				setVersion(byt1.intValue(), byt2.intValue());

				setLogonDomain((String) objs.elementAt(5));
				setOtherDomains((String) objs.elementAt(6));
				break;
		}
	}
}