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

import org.filesys.util.DataBuffer;
import org.filesys.util.DataPacker;

/**
 * Security Descriptor Class
 *
 * @author gkspencer
 */
public class SecurityDescriptor {

	//	Get/set security descriptor flags
	public final static int Owner				= 0x0001;
	public final static int Group				= 0x0002;
	public final static int DACL				= 0x0004;
	public final static int SACL				= 0x0008;

	//	Security descriptor control flags
	public final static int OwnerDefaulted		= 0x0001;
	public final static int GroupDefaulted		= 0x0002;
	public final static int DACLPresent			= 0x0004;
	public final static int DACLDefaulted		= 0x0008;
	public final static int SACLPresent			= 0x0010;
	public final static int SACLDefaulted		= 0x0020;
	public final static int DACLAutoInheritReq	= 0x0100;
	public final static int SACLAutoInheritReq	= 0x0200;
	public final static int DACLAutoInherited	= 0x0400;
	public final static int SACLAutoInherited	= 0x0800;
	public final static int DACLProtected		= 0x1000;
	public final static int SACLProtected		= 0x2000;
	public final static int SelfRelative		= 0x8000;

	//	Object types
	public final static int	File				= 0;
	public final static int	Directory			= 1;
	public final static int	NamedPipe			= 2;
	public final static int	RegistryKey			= 3;
	public final static int	User				= 4;
	public final static int	Kernel				= 5;

	//	Object name and type
	private String m_name;
	private int m_type;
	
	//	Revision and control flags
	private int m_revision = 1;
	private int m_control;
	
	//	Owner/group SIDs
	private SID m_owner;
	private SID m_group;
	
	//	Discretionary ACL
	private ACL m_dacl;
	
	//	System ACL
	private ACL m_sacl;

	/**
	 * Default constructor
	 */
	public SecurityDescriptor() {
	}

	/**
	 * Class constructor
	 *
	 * @param name String
	 * @param type int
	 */
	public SecurityDescriptor(String name, int type) {
		m_name = name;
		m_type = type;
	}

	/**
	 * Class constructor
	 *
	 * @param name  String
	 * @param type  int
	 * @param owner SID
	 * @param group SID
	 * @param dacl  ACL
	 * @param sacl  ACL
	 */
	public SecurityDescriptor(String name, int type, SID owner, SID group, ACL dacl, ACL sacl) {
		m_name = name;
		m_type = type;

		m_owner = owner;
		m_group = group;

		m_dacl = dacl;
		m_sacl = sacl;
	}

	/**
	 * Check if the descriptor is self-relative
	 *
	 * @return boolean
	 */
	public final boolean isSelfRelative() {
		return hasControlFlag(SelfRelative);
	}

	/**
	 * Return the revision
	 *
	 * @return int
	 */
	public final int getRevision() {
		return m_revision;
	}

	/**
	 * Check if the security descriptor has an owner SID
	 *
	 * @return boolean
	 */
	public final boolean hasOwner() {
		return m_owner != null ? true : false;
	}

	/**
	 * Return the owner SID
	 *
	 * @return SID
	 */
	public final SID getOwner() {
		return m_owner;
	}

	/**
	 * Check if the security descriptor has a group SID
	 *
	 * @return boolean
	 */
	public final boolean hasGroup() {
		return m_group != null ? true : false;
	}

	/**
	 * Return the group SID
	 *
	 * @return SID
	 */
	public final SID getGroup() {
		return m_group;
	}

	/**
	 * Check if the security descriptor has a discretionary ACL
	 *
	 * @return boolean
	 */
	public final boolean hasDACL() {
		return m_dacl != null ? true : false;
	}

	/**
	 * Return the discretionary ACL
	 *
	 * @return ACL
	 */
	public final ACL getDACL() {
		return m_dacl;
	}

	/**
	 * Check if the security descriptor has a system ACL
	 *
	 * @return boolean
	 */
	public final boolean hasSACL() {
		return m_sacl != null ? true : false;
	}

	/**
	 * Return the system ACL
	 *
	 * @return ACL
	 */
	public final ACL getSACL() {
		return m_sacl;
	}

	/**
	 * Set the owner SID
	 *
	 * @param sid SID
	 */
	public final void setOwner(SID sid) {
		m_owner = sid;
	}

	/**
	 * Set the group SID
	 *
	 * @param sid SID
	 */
	public final void setGroup(SID sid) {
		m_group = sid;
	}

	/**
	 * Set the discretionary ACL
	 *
	 * @param acl ACL
	 */
	public final void setDACL(ACL acl) {
		m_dacl = acl;
	}

	/**
	 * Set the system ACL
	 *
	 * @param acl ACL
	 */
	public final void setSACL(ACL acl) {
		m_sacl = acl;
	}

	/**
	 * Set the control flags
	 *
	 * @param flg int
	 */
	public final void setControlFlags(int flg) {
		m_control = flg;
	}

	/**
	 * Load the security descriptor from the specified buffer
	 *
	 * @param buf byte[]
	 * @param off int
	 * @return int
	 * @throws LoadException Failed to load the security descriptor
	 */
	public final int loadDescriptor(byte[] buf, int off)
			throws LoadException {

		//	Get the revision and control flags
		m_revision = DataPacker.getIntelShort(buf, off);
		m_control = DataPacker.getIntelShort(buf, off + 2);

		//	Make sure the security descriptor is self-raltive, if not then abort the load
		if (isSelfRelative() == false)
			throw new LoadException("Security descriptor not self-relative, cannot load");

		//	Clear any current settings
		m_owner = null;
		m_group = null;
		m_dacl = null;
		m_sacl = null;

		//	Get the offset to the owner SID, and load if available
		int pos = DataPacker.getIntelInt(buf, off + 4);
		int endPos = off + 20;

		if (pos != 0) {

			//	Load the owner SID
			m_owner = new SID();
			endPos = m_owner.loadSID(buf, off + pos, false);
		}

		//	Get the offset to the group SID, and load if available
		pos = DataPacker.getIntelInt(buf, off + 8);
		if (pos != 0) {

			//	Load the group SID
			m_group = new SID();
			endPos = m_group.loadSID(buf, off + pos, false);
		}

		//	Get the offset to the system ACL, and load if available
		pos = DataPacker.getIntelInt(buf, off + 12);

		if (pos != 0) {

			//	Load the system ACL
			m_sacl = new ACL();
			endPos = m_sacl.loadACL(buf, off + pos);
		}

		//	Get the offset to the discretionary ACL, and load if available
		pos = DataPacker.getIntelInt(buf, off + 16);

		if (pos != 0) {

			//	Load the discretionary ACL
			m_dacl = new ACL();
			endPos = m_dacl.loadACL(buf, off + pos);
		}

		//	Return the end position of the security descriptor
		return endPos;
	}

	/**
	 * Load the security descriptor from the specified buffer
	 *
	 * @param buf DataBuffer
	 * @return int
	 * @throws LoadException Failed to load the security descriptor
	 */
	public final int loadDescriptor(DataBuffer buf)
			throws LoadException {

		//	Get the revision and control flags
		int startPos = buf.getPosition();

		m_revision = buf.getByte();
		buf.skipBytes(1);
		m_control = buf.getShort();

		//	Make sure the security descriptor is self-raltive, if not then abort the load
		if (isSelfRelative() == false)
			throw new LoadException("Security descriptor not self-relative, cannot load");

		//	Clear any current settings
		m_owner = null;
		m_group = null;
		m_dacl = null;
		m_sacl = null;

		//	Get the offset to the owner SID, and load if available
		int pos = buf.getInt();

		if (pos != 0) {

			//	Load the owner SID
			m_owner = new SID();

			buf.setPosition(startPos + pos);
			m_owner.loadSID(buf, false);
		}

		//	Get the offset to the group SID, and load if available
		buf.setPosition(startPos + 8);
		pos = buf.getInt();

		if (pos != 0) {

			//	Load the group SID
			m_group = new SID();

			buf.setPosition(startPos + pos);
			m_group.loadSID(buf, false);
		}

		//	Get the offset to the system ACL, and load if available
		buf.setPosition(startPos + 12);
		pos = buf.getInt();

		if (pos != 0) {

			//	Load the system ACL
			m_sacl = new ACL();

			buf.setPosition(startPos + pos);
			m_sacl.loadACL(buf);
		}

		//	Get the offset to the discretionary ACL, and load if available
		buf.setPosition(startPos + 16);
		pos = buf.getInt();

		if (pos != 0) {

			//	Load the discretionary ACL
			m_dacl = new ACL();

			buf.setPosition(startPos + pos);
			m_dacl.loadACL(buf);
		}

		//	Return the end position of the security descriptor
		return buf.getPosition();
	}

	/**
	 * Save the security descriptor to the specified buffer
	 *
	 * @param buf byte[]
	 * @param off int
	 * @return int
	 * @throws SaveException Failed to save the security descriptor
	 */
	public final int saveDescriptor(byte[] buf, int off)
			throws SaveException {

		//	Pack the security descriptor
		DataPacker.putIntelShort(m_revision, buf, off);

		//	Make sure the self-relative flag is set
		if (isSelfRelative() == false)
			m_control += SelfRelative;

		//	Check if the SACL flag is set, if present
		if (hasSACL() && hasControlFlag(SACLPresent) == false)
			m_control += SACLPresent;

		//	Check if the DACL flag is set, if present
		if (hasDACL() && hasControlFlag(DACLPresent) == false)
			m_control += DACLPresent;

		//	Pack the control flags
		DataPacker.putIntelShort(m_control, buf, off + 2);

		//	Clear the section offsets
		DataPacker.putZeros(buf, off + 4, 16);

		//	Pack the owner SID, if available
		int pos = off + 20;

		if (hasOwner()) {

			//	Pack the owner SID
			DataPacker.putIntelInt(pos - off, buf, off + 4);            //	offset to owner SID
			pos = getOwner().saveSID(buf, pos);
		}

		//	Pack the group SID, if available
		if (hasGroup()) {

			//	Pack the group SID
			DataPacker.putIntelInt(pos - off, buf, off + 8);            //	offset to group SID
			pos = getGroup().saveSID(buf, pos);
		}

		//	Pack the system ACL, if available
		if (hasSACL()) {

			//	Pack the SACL
			DataPacker.putIntelInt(pos - off, buf, off + 12);        //	offset to SACL
			pos = getSACL().saveACL(buf, pos);
		}

		//	Pack the discretionary ACL, if available
		if (hasDACL()) {

			//	Pack the DACL
			DataPacker.putIntelInt(pos - off, buf, off + 16);        //	offset to DACL
			pos = getDACL().saveACL(buf, pos);
		}

		//	Return the end offset
		return pos;
	}

	/**
	 * Save the security descriptor to the specified buffer
	 *
	 * @param buf DataBuffer
	 * @return int
	 * @throws SaveException Failed to save the security descriptor
	 */
	public final int saveDescriptor(DataBuffer buf)
			throws SaveException {

		// Save the current buffer position
		int startPos = buf.getPosition();

		//	Pack the security descriptor
		buf.putShort(m_revision);

		//	Make sure the self-relative flag is set
		if (isSelfRelative() == false)
			m_control += SelfRelative;

		//	Check if the SACL flag is set, if present
		if (hasSACL() && hasControlFlag(SACLPresent) == false)
			m_control += SACLPresent;

		//	Check if the DACL flag is set, if present
		if (hasDACL() && hasControlFlag(DACLPresent) == false)
			m_control += DACLPresent;

		//	Pack the control flags
		buf.putShort(m_control);

		//	Clear the section offsets
		buf.putZeros(16);

		//	Pack the owner SID, if available
		int pos = startPos + 20;

		if (hasOwner()) {

			//	Pack the owner SID
			buf.setPosition(startPos + 4);
			buf.putInt(pos - startPos);

			buf.setPosition(pos);
			pos = getOwner().saveSID(buf);
		}

		//	Pack the group SID, if available
		if (hasGroup()) {

			//	Pack the group SID
			buf.setPosition(startPos + 8);
			buf.putInt(pos - startPos);

			buf.setPosition(pos);
			pos = getGroup().saveSID(buf);
		}

		//	Pack the system ACL, if available
		if (hasSACL()) {

			//	Pack the SACL
			buf.setPosition(startPos + 12);
			buf.putInt(pos - startPos);

			buf.setPosition(pos);
			pos = getSACL().saveACL(buf);
		}

		//	Pack the discretionary ACL, if available
		if (hasDACL()) {

			//	Pack the DACL
			buf.setPosition(startPos + 16);
			buf.putInt(pos - startPos);

			buf.setPosition(pos);
			pos = getDACL().saveACL(buf);
		}

		//	Return the end offset
		return pos;
	}

	/**
	 * Check if the specified control flag is set
	 *
	 * @param flg int
	 * @return boolean
	 */
	protected final boolean hasControlFlag(int flg) {
		if ((m_control & flg) != 0)
			return true;
		return false;
	}

	/**
	 * Return the security descriptor as a string
	 *
	 * @return String
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();

		str.append("[");

		if (hasOwner()) {
			str.append("Owner:");
			str.append(getOwner().toString());
			str.append(",");
		}

		if (hasGroup()) {
			str.append("Group:");
			str.append(getGroup().toString());
			str.append(",");
		}

		if (hasDACL()) {
			str.append("DACL:");
			str.append(getDACL().toString());
			str.append(",");
		}

		if (hasSACL()) {
			str.append("SACL:");
			str.append(getSACL().toString());
		}

		str.append("]");

		return str.toString();
	}
}
