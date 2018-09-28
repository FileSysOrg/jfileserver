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
 * Access Control Entry Class
 *
 * @author gkspencer
 */
public class ACE {

	//	ACE types
	public final static int Allowed				= 0;
	public final static int Denied				= 1;
	public final static int Audit				= 2;
	public final static int Alarm				= 3;
	public final static int AllowedCompound		= 4;
	public final static int AllowedObject		= 5;
	public final static int DeniedObject		= 6;
	public final static int AuditObject			= 7;
	public final static int AlarmObject			= 8;
	
	//	ACE type strings
	private static final String[] _typeStr = {"Allow", "Deny", "Audit", "Alarm", "AlCm", "AlOb", "DeOb", "AuOb", "AlmO"};
		
	//	ACE flags
	public final static int ObjectInherit		= 0x0001;
	public final static int ContainerInherit	= 0x0002;
	public final static int NoPropagateInherit	= 0x0004;
	public final static int InheritOnly			= 0x0008;
	public final static int Inherited			= 0x0010;
	
	public final static int SuccessfulAccess	= 0x0040;
	public final static int FailedAccess		= 0x0080;
	
	//	ACE full access mask
	public static final int FullAccess			= 0x001F01FF;
	
	//	ACE type and flags
	private int m_type;
	private int m_flags;
	
	//	Access mask
	private int m_accessMask;
	
	//	Security id that the entry applies to
	private SID m_sid;
	
	/**
	 * Default constructor
	 */
	public ACE() {
	}

	/**
	 * Class constructor
	 *
	 * @param type  int
	 * @param flags int
	 * @param mask  int
	 * @param sid   SID
	 */
	public ACE(int type, int flags, int mask, SID sid) {
		m_type = type;
		m_flags = flags;
		m_accessMask = mask;
		m_sid = sid;
	}

	/**
	 * Return the access control entry type
	 *
	 * @return int
	 */
	public final int getType() {
		return m_type;
	}

	/**
	 * Return the access control entry flags
	 *
	 * @return int
	 */
	public final int getFlags() {
		return m_flags;
	}

	/**
	 * Return the access mask
	 *
	 * @return int
	 */
	public final int getAccessMask() {
		return m_accessMask;
	}

	/**
	 * Return the security id that the access control entry applies to
	 *
	 * @return SID
	 */
	public final SID getSID() {
		return m_sid;
	}

	/**
	 * Return object inherit flag status
	 *
	 * @return boolean
	 */
	public final boolean hasObjectInherit() {
		return (m_flags & ObjectInherit) != 0 ? true : false;
	}

	/**
	 * Return the container inherit flag status
	 *
	 * @return boolean
	 */
	public final boolean hasContainerInherit() {
		return (m_flags & ContainerInherit) != 0 ? true : false;
	}

	/**
	 * Return the no propagate inherit flag status
	 *
	 * @return boolean
	 */
	public final boolean hasNoPropagateInherit() {
		return (m_flags & NoPropagateInherit) != 0 ? true : false;
	}

	/**
	 * Return the inherit only flag status
	 *
	 * @return boolean
	 */
	public final boolean hasInheritOnly() {
		return (m_flags & InheritOnly) != 0 ? true : false;
	}

	/**
	 * Return the inherited flag status
	 *
	 * @return boolean
	 */
	public final boolean isInherited() {
		return (m_flags & Inherited) != 0 ? true : false;
	}

	/**
	 * Return the successful access flag status
	 *
	 * @return boolean
	 */
	public final boolean isSuccessfulAccess() {
		return (m_flags & SuccessfulAccess) != 0 ? true : false;
	}

	/**
	 * Return the failed access flag status
	 *
	 * @return boolean
	 */
	public final boolean isFailedAccess() {
		return (m_flags & FailedAccess) != 0 ? true : false;
	}

	/**
	 * Load the access control entry from the specified buffer
	 *
	 * @param buf byte[]
	 * @param off int
	 * @return int
	 * @throws LoadException Failed to load the access control
	 */
	public final int loadACE(byte[] buf, int off)
			throws LoadException {

		//	Get the ACE type and flags
		m_type = (int) (buf[off] & 0xFF);
		m_flags = (int) (buf[off + 1] & 0xFF);

		//	Get the ACE size (includes the type, flags and size)
		int siz = DataPacker.getIntelShort(buf, off + 2);

		//	Read the remaining part of the ACE, the format depends on the ACE type
		if (getType() >= Allowed && getType() <= Alarm) {

			//	Get the access mask
			m_accessMask = DataPacker.getIntelInt(buf, off + 4);

			//	Create a security id and load from the buffer
			m_sid = new SID();
			m_sid.loadSID(buf, off + 8, false);
		}

		//	Return the new offset at the end of this ACE
		return off + siz;
	}

	/**
	 * Load the access control entry from the specified buffer
	 *
	 * @param buf DataBuffer
	 * @return int
	 * @throws LoadException Failed to load the access control
	 */
	public final int loadACE(DataBuffer buf)
			throws LoadException {

		//	Get the ACE type and flags
		m_type = buf.getByte();
		m_flags = buf.getByte();

		//	Get the ACE size (includes the type, flags and size)
		int siz = buf.getShort();

		//	Read the remaining part of the ACE, the format depends on the ACE type
		if (getType() >= Allowed && getType() <= Alarm) {

			//	Get the access mask
			m_accessMask = buf.getInt();

			//	Create a security id and load from the buffer
			m_sid = new SID();
			m_sid.loadSID(buf, false);
		}

		//	Return the new offset at the end of this ACE
		return buf.getPosition();
	}

	/**
	 * Save the access control entry to the specified buffer
	 *
	 * @param buf byte[]
	 * @param off int
	 * @return int
	 * @throws SaveException Failed to save the access control
	 */
	public final int saveACE(byte[] buf, int off)
			throws SaveException {

		//	Pack the ACE into the buffer
		buf[off] = (byte) (m_type & 0xFF);
		buf[off + 1] = (byte) (m_flags & 0xFF);

		int endPos = off + 4;

		if (getType() >= Allowed && getType() <= Alarm) {

			//	Pack the access mask
			DataPacker.putIntelInt(m_accessMask, buf, off + 4);
			endPos += 4;

			//	Save the SID
			endPos = m_sid.saveSID(buf, endPos);
		}

		//	Set the ACE size and return the end offset
		DataPacker.putIntelShort(endPos - off, buf, off + 2);
		return endPos;
	}

	/**
	 * Save the access control entry to the specified buffer
	 *
	 * @param buf DataBuffer
	 * @return int
	 * @throws SaveException Failed to save the access control
	 */
	public final int saveACE(DataBuffer buf)
			throws SaveException {

		//	Pack the ACE into the buffer
		int startPos = buf.getPosition();

		buf.putByte(m_type);
		buf.putByte(m_flags);
		buf.putShort(0);

		if (getType() >= Allowed && getType() <= Alarm) {

			//	Pack the access mask
			buf.putInt(m_accessMask);

			//	Save the SID
			m_sid.saveSID(buf);
		}

		//	Set the ACE size and return the end offset
		int endPos = buf.getPosition();

		buf.setPosition(startPos + 2);
		buf.putShort(endPos - startPos);
		buf.setPosition(endPos);

		return endPos;
	}

	/**
	 * Return the ACe type as a string
	 *
	 * @return String
	 */
	public final String getTypeAsString() {
		return _typeStr[getType()];
	}

	/**
	 * Return the access mask as a string
	 *
	 * @return String
	 */
	public final String getAccessMaskAsString() {
		if (getAccessMask() == FullAccess)
			return "FullAccess";
		return "0x" + Integer.toHexString(getAccessMask());
	}

	/**
	 * Return the access control entry as a string
	 *
	 * @return String
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();

		str.append("[");
		str.append(_typeStr[getType()]);
		str.append(",");

		if (getAccessMask() == FullAccess)
			str.append("FullAccess");
		else {
			str.append("0x");
			str.append(Integer.toHexString(getAccessMask()));
		}

		str.append(",");

		str.append(getSID().toString());
		str.append("]");

		return str.toString();
	}
}
