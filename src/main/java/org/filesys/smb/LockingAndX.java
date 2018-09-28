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

package org.filesys.smb;

/**
 * LockingAndX SMB Constants Class
 * 
 * <p>Contains constants used by the LockingAndX SMB request, plus methods for decoding the flags values.
 *
 * @author gkspencer
 */
public class LockingAndX {

	//	Lock type flags
	public static final int SharedLock		= 0x0001;
	public static final int OplockBreak		= 0x0002;
	public static final int ChangeType		= 0x0004;
	public static final int Cancel			= 0x0008;
	public static final int LargeFiles		= 0x0010;
	
	public static final int Level2OpLock	= 0x0100;

	/**
	 * Check if this is a normal lock/unlock, ie. no flags except the V1LargeFiles flag may
	 * be set
	 *
	 * @param flags int
	 * @return boolean
	 */
	public final static boolean isNormalLockUnlock(int flags) {
		return (flags & 0x000F) == 0 ? true : false;
	}

	/**
	 * Check if the large files flag is set
	 *
	 * @param flags int
	 * @return boolean
	 */
	public final static boolean hasLargeFiles(int flags) {
		return (flags & LargeFiles) != 0 ? true : false;
	}

	/**
	 * Check if the shared lock flag is set
	 *
	 * @param flags int
	 * @return boolean
	 */
	public final static boolean hasSharedLock(int flags) {
		return (flags & SharedLock) != 0 ? true : false;
	}

	/**
	 * Check if the oplock break flag is set
	 *
	 * @param flags int
	 * @return boolean
	 */
	public final static boolean hasOplockBreak(int flags) {
		return (flags & OplockBreak) != 0 ? true : false;
	}

	/**
	 * Check if the LevelII oplock flag is set
	 *
	 * @param flags int
	 * @return boolean
	 */
	public final static boolean hasLevelIIOplock(int flags) {
		return (flags & Level2OpLock) != 0 ? true : false;
	}

	/**
	 * Check if the change type flag is set
	 *
	 * @param flags int
	 * @return boolean
	 */
	public final static boolean hasChangeType(int flags) {
		return (flags & ChangeType) != 0 ? true : false;
	}

	/**
	 * Check if the cancel flag is set
	 *
	 * @param flags int
	 * @return boolean
	 */
	public final static boolean hasCancel(int flags) {
		return (flags & Cancel) != 0 ? true : false;
	}
}
