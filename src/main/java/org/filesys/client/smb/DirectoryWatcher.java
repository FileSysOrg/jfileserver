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

package org.filesys.client.smb;

/**
 * Directory Watcher Interface
 * 
 * <p>This interface is used to receive change notifications from a file server after registering to receive notifications
 * using the CIFSDiskSession.NTNotifyChange() method.
 * 
 * @author gkspencer
 */
public interface DirectoryWatcher {
	
	//	Notification event types
	
	public final static int FileActionUnknown	= -1;
	public final static int FileNoAction		= 0;
	public final static int FileAdded			= 1;
	public final static int FileRemoved			= 2;
	public final static int FileModified		= 3;
	public final static int FileRenamedOld		= 4;
	public final static int FileRenamedNew		= 5;

	/**
	 * Directory change occurred
	 * 
	 * @param typ int
	 * @param fname String
	 */
	public void directoryChanged(int typ, String fname);
}
