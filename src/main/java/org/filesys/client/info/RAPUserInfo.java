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
import org.filesys.smb.dcerpc.info.UserInfo;

/**
 * User Information Class
 * 
 * <p>
 * The UserInfo class contains the details of a user account on a remote server.
 * 
 * <p>
 * The AdminSession.getUserInfo () method returns an UserInfo object for the specified remote user.
 * 
 * @author gkspencer
 */
public final class RAPUserInfo extends UserInfo implements RAPReadable, Serializable {

	/**
	 * Default constructor
	 */
	public RAPUserInfo() {
	}

	/**
	 * Class constructor
	 * 
	 * @param infoLevel int
	 * @param objs Vector
	 */
	public RAPUserInfo(int infoLevel, Vector objs) {
		readRAPObject(infoLevel, objs);
	}

	/**
	 * @param infoLevel int
	 * @param objs Vector
	 */
	public void readRAPObject(int infoLevel, Vector objs) {
		// TODO Auto-generated method stub

	}
}