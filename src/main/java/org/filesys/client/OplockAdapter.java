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

package org.filesys.client;

import org.filesys.smb.OpLockType;

/**
 * Oplock Break Callback Adapter Class
 *
 * @author gkspencer
 */
public class OplockAdapter implements OplockInterface {

	/**
	 * Oplock break requested on a file
	 * 
	 * @param file CIFSFile
	 * @return int
	 */
	public int oplockBreak(CIFSFile file) {
		return OpLockType.LEVEL_NONE.intValue();
	}
	
	/**
	 * Send an automatic oplock break response, or not
	 * 
	 * @return boolean
	 */
	public boolean sendAutomaticBreakResponse() {
		return true;
	}
}
