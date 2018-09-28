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

import org.filesys.smb.dcerpc.PolicyHandle;

/**
 * Service Manager Handle Class
 * 
 * <p>
 * Contains the handle to a remote service manager.
 * 
 * @author gkspencer
 */
public class ServiceManagerHandle extends PolicyHandle {

	/**
	 * Class constructor
	 * 
	 * @param srvName String
	 */
	public ServiceManagerHandle(String srvName) {
		super();
		setName(srvName);
	}

	/**
	 * Class constructor
	 * 
	 * @param srvName String
	 * @param buf byte[]
	 * @param off int
	 */
	public ServiceManagerHandle(String srvName, byte[] buf, int off) {
		super(srvName, buf, off);
	}
}
