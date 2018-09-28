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

package org.filesys.smb.server;

/**
 * <p>Contains the named pipe transaction codes.
 *
 * @author gkspencer
 */
public class NamedPipeTransaction {

	//	Transaction sub-commands
	public static final int CallNamedPipe	= 0x54;
	public static final int WaitNamedPipe	= 0x53;
	public static final int PeekNmPipe		= 0x23;
	public static final int QNmPHandState	= 0x21;
	public static final int SetNmPHandState	= 0x01;
	public static final int QNmPipeInfo		= 0x22;
	public static final int TransactNmPipe	= 0x26;
	public static final int RawReadNmPipe	= 0x11;
	public static final int RawWriteNmPipe	= 0x31;

	/**
	 * Return the named pipe transaction sub-command as a string
	 *
	 * @param subCmd int
	 * @return String
	 */
	public final static String getSubCommand(int subCmd) {

		//	Determine the sub-command code
		String ret = "";

		switch (subCmd) {
			case CallNamedPipe:
				ret = "CallNamedPipe";
				break;
			case WaitNamedPipe:
				ret = "WaitNamedPipe";
				break;
			case PeekNmPipe:
				ret = "PeekNmPipe";
				break;
			case QNmPHandState:
				ret = "QNmPHandState";
				break;
			case SetNmPHandState:
				ret = "SetNmPHandState";
				break;
			case QNmPipeInfo:
				ret = "QNmPipeInfo";
				break;
			case TransactNmPipe:
				ret = "TransactNmPipe";
				break;
			case RawReadNmPipe:
				ret = "RawReadNmPipe";
				break;
			case RawWriteNmPipe:
				ret = "RawWriteNmPipe";
				break;
		}
		return ret;
	}
}
