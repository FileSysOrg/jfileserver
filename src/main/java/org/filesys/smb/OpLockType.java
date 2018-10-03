/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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
 * OpLock Types Class
 * 
 * <p>Contains oplock type constants
 * 
 * @author gkspencer
 */
public enum OpLockType {
	LEVEL_NONE		(0x00),
	LEVEL_II		(0x01),
	LEVEL_EXCLUSIVE	(0x08),
	LEVEL_BATCH		(0x09),
	LEVEL_LEASE		(0xFF),

	INVALID			(-1);

	private final int oplockTyp;

	/**
	 * Enum constructor
	 *
	 * @param typ int
	 */
	OpLockType(int typ) { oplockTyp = typ; }

	/**
	 * Return the oplock type as an int
	 *
	 * @return int
	 */
	public final int intValue() { return oplockTyp; }

	/**
	 * Create an oplock type from an int
	 *
	 * @param typ int
	 * @return OpLock
	 */
	public static final OpLockType fromInt(int typ) {
		OpLockType oplock = INVALID;

		switch ( typ) {
			case 0x00:
				oplock = LEVEL_NONE;
				break;
			case 0x01:
				oplock = LEVEL_II;
				break;
			case 0x08:
				oplock = LEVEL_EXCLUSIVE;
				break;
			case 0x09:
				oplock = LEVEL_BATCH;
				break;
			case 0xFF:
				oplock = LEVEL_LEASE;
				break;
		}

		return oplock;
	}
}
