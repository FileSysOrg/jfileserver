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
 * LSA Policy Handle Class
 * 
 * <p>
 * Local Security Authority (LSA) policy handle, required to access LSA functions.
 * 
 * @author gkspencer
 */
public class LSAPolicyHandle extends PolicyHandle {

	/**
	 * Class constructor
	 */
	public LSAPolicyHandle() {
		super();
		setName("LSA");
	}

	/**
	 * Class constructor
	 * 
	 * @param buf byte[]
	 * @param off int
	 */
	public LSAPolicyHandle(byte[] buf, int off) {
		super("LSA", buf, off);
	}
}
