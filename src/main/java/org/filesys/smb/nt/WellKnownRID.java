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

package org.filesys.smb.nt;

/**
 * Well Known Reltive Ids Class
 *
 * @author gkspencer
 */
public class WellKnownRID {

	//	Well known RIDs
	//
	//	Well known users
	public static final int DomainUserAdmin					= 0x01F4;	//	500
	public static final int DomainUserGuest					= 0x01F5;	//	501
	public static final int DomainUserKrbtgt				= 0x01F6;	//	502
	
	//	Well known groups
	public static final int DomainGroupAdmins				= 0x0200;	//	512
	public static final int DomainGroupUsers				= 0x0201;	//	513
	public static final int DomainGroupGuests				= 0x0202;	//	514
	public static final int DomainGroupComputers			= 0x0203;	//	515
	public static final int DomainGroupControllers			= 0x0204;	//	516
	public static final int DomainGroupCertAdmins			= 0x0205;	//	517
	public static final int DomainGroupSchemaAdmins			= 0x0206;	//	518
	
	//	Well know aliases
	public static final int DomainAliasAdmins				= 0x0220;	//	544
	public static final int DomainAliasUsers				= 0x0221;	//	545
	public static final int DomainAliasGuests				= 0x0222;	//	546
	public static final int DomainAliasPowerUsers			= 0x0223;	//	547
	public static final int DomainAliasAccountOps			= 0x0224;	//	548
	public static final int DomainAliasSystemOps			= 0x0225;	//	549
	public static final int DomainAliasPrintOps				= 0x0226;	//	550
	public static final int DomainAliasBackupOps			= 0x0227;	//	551
	public static final int DomainAliasReplicator			= 0x0228;	//	552

	/**
	 * Check if a RID is a well known user
	 *
	 * @param id int
	 * @return boolean
	 */
	public final static boolean isWellKnownUser(int id) {
		if (id >= DomainUserAdmin && id <= DomainUserKrbtgt)
			return true;
		return false;
	}

	/**
	 * Check if the RID is a well known group
	 *
	 * @param id int
	 * @return boolean
	 */
	public final static boolean isWellKnownGroup(int id) {
		if (id >= DomainGroupAdmins && id <= DomainGroupSchemaAdmins)
			return true;
		return false;
	}

	/**
	 * Check if the RID is a well known alias
	 *
	 * @param id int
	 * @return boolean
	 */
	public final static boolean isWellKnownAlias(int id) {
		if (id >= DomainAliasAdmins && id <= DomainAliasReplicator)
			return true;
		return false;
	}

	/**
	 * Convert a well known user id to a name
	 *
	 * @param id int
	 * @return String
	 */
	public final static String getWellKnownUserName(int id) {
		String ret = null;
		switch (id) {
			case DomainUserAdmin:
				ret = "Administrator";
				break;
			case DomainUserGuest:
				ret = "Guest";
				break;
			case DomainUserKrbtgt:
				ret = "Krbtgt";
				break;
		}
		return ret;
	}

	/**
	 * Convert a well known group id to a name
	 *
	 * @param id int
	 * @return String
	 */
	public final static String getWellKnownGroupName(int id) {
		String ret = null;
		switch (id) {
			case DomainGroupAdmins:
				ret = "Administrators";
				break;
			case DomainGroupUsers:
				ret = "Users";
				break;
			case DomainGroupGuests:
				ret = "Guests";
				break;
			case DomainGroupComputers:
				ret = "Computers";
				break;
			case DomainGroupControllers:
				ret = "Controllers";
				break;
			case DomainGroupCertAdmins:
				ret = "CertificatePublishers";
				break;
			case DomainGroupSchemaAdmins:
				ret = "SchemaAdministrators";
				break;
		}
		return ret;
	}

	/**
	 * Convert a well known alias id to a name
	 *
	 * @param id int
	 * @return String
	 */
	public final static String getWellKnownAliasName(int id) {
		String ret = null;
		switch (id) {
			case DomainAliasAdmins:
				ret = "Administrators";
				break;
			case DomainAliasUsers:
				ret = "Users";
				break;
			case DomainAliasGuests:
				ret = "Guests";
				break;
			case DomainAliasPowerUsers:
				ret = "PowerUsers";
				break;
			case DomainAliasAccountOps:
				ret = "AccountOps";
				break;
			case DomainAliasSystemOps:
				ret = "SystemOps";
				break;
			case DomainAliasPrintOps:
				ret = "PrintOps";
				break;
			case DomainAliasBackupOps:
				ret = "BackupOps";
				break;
			case DomainAliasReplicator:
				ret = "Replicator";
				break;
		}
		return ret;
	}
}
