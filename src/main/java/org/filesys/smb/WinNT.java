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
 * Windows NT Constants Class
 * 
 * <p>Constants mainly used by the CIFSDiskSession.NTCreate() method.
 *
 * @author gkspencer
 */
public class WinNT {

	//	NTCreateAndX flags (oplocks/target)
	public static final int RequestExclusiveOplock		= 0x0002;
	public static final int RequestBatchOplock			= 0x0004;
	public static final int TargetDirectory				= 0x0008;
	public static final int ExtendedResponse			= 0x0010;

	public static final int RequestOplockMask			= (RequestBatchOplock + RequestExclusiveOplock);

	//	NTCreateAndX create options flags
	public static final int CreateFile			= 0x00000000;
	public static final int CreateDirectory		= 0x00000001;
	public static final int CreateWriteThrough	= 0x00000002;
	public static final int CreateSequential	= 0x00000004;
	
	public static final int CreateNonDirectory	= 0x00000040;
	public static final int CreateRandomAccess	= 0x00000800;
	public static final int CreateDeleteOnClose	= 0x00001000;
	
	public static final int CreateReparsePoint  = 0x00200000;
	
	// Granted oplock type (NTCreateAndX response)
	public static final int GrantedOplockNone		= 0;
	public static final int GrantedOplockExclusive	= 1;
	public static final int GrantedOplockBatch		= 2;
	public static final int GrantedOplockLevelII	= 3;
}
