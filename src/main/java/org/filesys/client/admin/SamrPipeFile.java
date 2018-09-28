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

import java.io.*;
import java.util.*;

import org.filesys.client.IPCSession;
import org.filesys.smb.SMBException;
import org.filesys.smb.SMBStatus;
import org.filesys.smb.dcerpc.DCEBuffer;
import org.filesys.smb.dcerpc.DCEBufferException;
import org.filesys.smb.dcerpc.DCEException;
import org.filesys.smb.dcerpc.PolicyHandle;
import org.filesys.smb.dcerpc.PolicyHandleCache;
import org.filesys.smb.dcerpc.client.DCEPacket;
import org.filesys.smb.dcerpc.client.SIDCache;
import org.filesys.smb.dcerpc.client.Samr;
import org.filesys.smb.dcerpc.info.UserInfo;
import org.filesys.smb.nt.LoadException;
import org.filesys.smb.nt.RID;
import org.filesys.smb.nt.RIDList;
import org.filesys.smb.nt.SID;
import org.filesys.smb.nt.SaveException;
import org.filesys.smb.nt.WellKnownRID;
import org.filesys.smb.nt.WellKnownSID;
import org.filesys.util.StringList;

/**
 * Security Accounts Manager Pipe File Class
 * 
 * <p>
 * Pipe file connected to a remote SAMR DCE/RPC service that can be used to retrieve information
 * about remote users, groups, aliases, and perform id to name mappings.
 * 
 * @author gkspencer
 */
public class SamrPipeFile extends IPCPipeFile {

	// Default buffer size to use for enumeration requests

	public static final int DefaultBufferSize = 0xFFFF;

	// SAMR service handle

	private SamrPolicyHandle m_handle;

	// Domain SID and policy handle caches

	private SIDCache m_domainSIDs;
	private PolicyHandleCache m_domainHandles;

	/**
	 * Class constructor
	 * 
	 * @param sess SMBIPCSession
	 * @param pkt DCEPacket
	 * @param handle int
	 * @param name String
	 * @param maxTx int
	 * @param maxRx int
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 * @exception DCEException DCE/RPC error
	 */
	public SamrPipeFile(IPCSession sess, DCEPacket pkt, int handle, String name, int maxTx, int maxRx) throws IOException,
			SMBException, DCEException {
		super(sess, pkt, handle, name, maxTx, maxRx);

		// Allocate the domain SID and policy handle caches

		m_domainSIDs = new SIDCache();
		m_domainHandles = new PolicyHandleCache();

		// Open the service

		openService();
	}

	/**
	 * Open the SAM service on the remote server
	 * 
	 * @return SamrPolicyHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	protected final SamrPolicyHandle openService()
		throws IOException, SMBException {

		// Check if we have already opened the SAMR service

		if ( m_handle != null)
			return m_handle;

		// Build the remote server name string

		String remName = getSession().getPCShare().getNodeName();

		// Build the open SAM service request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putPointer(true);
		buf.putString(remName, DCEBuffer.ALIGN_INT, true); // Does not work unless we count the null
															// for the string
		buf.putInt(0x003F); // access mask

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Samr.SamrConnect2, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the open SAM service request

		doDCERequest(pkt);

		// Retrieve the policy handle from the response

		DCEBuffer rxBuf = getRxBuffer();
		m_handle = new SamrPolicyHandle();

		try {
			checkStatus(rxBuf.getStatusCode());
			rxBuf.getHandle(m_handle);
		}
		catch (DCEBufferException ex) {
		}

		// Return the SAM service handle

		return m_handle;
	}

	/**
	 * Enumerate the domains in the server
	 * 
	 * @return StringList
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final StringList enumerateDomains()
		throws IOException, SMBException {

		// Build the lookup domain request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(getSamrHandle());
		buf.putInt(0); // resume handle
		buf.putInt(8192); // preferred maximum size

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Samr.SamrEnumDomains, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the lookup domain request

		doDCERequest(pkt);

		// Retrieve the domain SID from the response

		DCEBuffer rxBuf = getRxBuffer();
		StringList domains = new StringList();

		try {

			// Check the request status

			checkStatus(rxBuf.getStatusCode());

			// Get the resume handle

			int resume = rxBuf.getInt();

			// Load the domain names

			if ( rxBuf.getPointer() != 0) {

				// Get the name count

				int cnt = rxBuf.getInt();

				if ( rxBuf.getPointer() != 0) {

					// Skip the max count value

					rxBuf.skipBytes(4);

					// Skip the name entry pointer/sizes

					rxBuf.skipBytes(cnt * 12);

					// Load the actual name strings

					for (int i = 0; i < cnt; i++) {

						// Load a name string, filter out the 'Builtin' domain name

						String name = rxBuf.getString(DCEBuffer.ALIGN_INT);
						if ( name != null && name.equals("Builtin") == false)
							domains.addString(name);
					}
				}
			}
		}
		catch (DCEBufferException ex) {
		}

		// Return the domain names

		return domains;
	}

	/**
	 * Enumerate the groups in a domain
	 *
     * @param domain String
	 * @return StringList
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final StringList enumerateGroups(String domain)
		throws IOException, SMBException {

		// Get the domain SID and handle

		SID domSID = lookupDomain(domain);
		PolicyHandle domHandle = openDomain(domSID);

		// A large group list may take several requests, loop until the full list has been retrieved

		DCEBuffer buf = getBuffer();
		int resumeId = 0;

		StringList groups = new StringList();
		boolean moreGroups = true;

		while (moreGroups) {

			// Build the enumerate groups request

			buf.resetBuffer();

			buf.putHandle(domHandle);
			buf.putInt(resumeId); // resume handle
			// buf.putInt( DefaultBufferSize); // preferred maximum size
			buf.putInt(0xFFFFFFFF); // account control

			// Initialize the DCE request

			DCEPacket pkt = getPacket();
			try {
				pkt.initializeDCERequest(getHandle(), Samr.SamrEnumGroups, buf, getMaximumTransmitSize(), getNextCallId());
			}
			catch (DCEBufferException ex) {
				ex.printStackTrace();
			}

			// Send the enumerate groups request

			doDCERequest(pkt);

			// Retrieve the group list from the response

			DCEBuffer rxBuf = getRxBuffer();

			try {

				// Check the request status

				if ( rxBuf.hasMoreEntries() == false)
					moreGroups = false;
				else
					checkStatus(rxBuf.getStatusCode());

				// Get the resume handle

				resumeId = rxBuf.getInt();

				// Load the group names

				if ( rxBuf.getPointer() != 0) {

					// Get the name count

					int cnt = rxBuf.getInt();

					if ( rxBuf.getPointer() != 0) {

						// Skip the max count value

						rxBuf.skipBytes(4);

						// Skip the name entry pointer/sizes

						rxBuf.skipBytes(cnt * 12);

						// Load the actual name strings

						for (int i = 0; i < cnt; i++) {

							// Load a name string

							String name = rxBuf.getString(DCEBuffer.ALIGN_INT);
							if ( name != null)
								groups.addString(name);
						}
					}
				}
			}
			catch (DCEBufferException ex) {
			}
		}

		// Return the group names

		return groups;
	}

	/**
	 * Enumerate the users in a domain
	 *
	 * @param domain String
	 * @return StringList
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final StringList enumerateUsers(String domain)
		throws IOException, SMBException {

		// Get the domain SID and handle

		SID domSID = lookupDomain(domain);
		PolicyHandle domHandle = openDomain(domSID);

		// A large user list may take several requests, loop until the full list has been retrieved

		DCEBuffer buf = getBuffer();
		int resumeId = 0;

		StringList users = new StringList();
		boolean moreUsers = true;

		while (moreUsers) {

			// Build the enumerate users request

			buf.resetBuffer();

			buf.putHandle(domHandle);
			buf.putInt(resumeId); // resume handle
			buf.putInt(0x00000010); // account control - normal users
			buf.putInt(DefaultBufferSize); // preferred maximum size

			// Initialize the DCE request

			DCEPacket pkt = getPacket();
			try {
				pkt.initializeDCERequest(getHandle(), Samr.SamrEnumUsers, buf, getMaximumTransmitSize(), getNextCallId());
			}
			catch (DCEBufferException ex) {
				ex.printStackTrace();
			}

			// Send the enumerate users request

			doDCERequest(pkt);

			// Retrieve the user list from the response

			DCEBuffer rxBuf = getRxBuffer();

			try {

				// Check the request status

				if ( rxBuf.hasSuccessStatus())
					moreUsers = false;
				if ( rxBuf.hasMoreEntries())
					moreUsers = true;
				else
					checkStatus(rxBuf.getStatusCode());

				// Get the resume handle

				resumeId = rxBuf.getInt();

				// Load the user names

				if ( rxBuf.getPointer() != 0) {

					// Get the name count

					int cnt = rxBuf.getInt();

					if ( rxBuf.getPointer() != 0) {

						// Skip the max count value

						rxBuf.skipBytes(4);

						// Skip the name entry pointer/sizes

						rxBuf.skipBytes(cnt * 12);

						// Load the actual name strings

						for (int i = 0; i < cnt; i++) {

							// Load a name string

							String name = rxBuf.getString(DCEBuffer.ALIGN_INT);
							if ( name != null)
								users.addString(name);
						}
					}
				}
			}
			catch (DCEBufferException ex) {
			}
		}

		// Return the users names

		return users;
	}

	/**
	 * Enumerate the aliases/local groups in a domain
	 *
     * @param domain String
	 * @return StringList
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final StringList enumerateAliases(String domain)
		throws IOException, SMBException {

		// Get the domain SID and handle

		SID domSID = lookupDomain(domain);
		PolicyHandle domHandle = openDomain(domSID);

		// A large alias list may take several requests, loop until the full list has been retrieved

		DCEBuffer buf = getBuffer();
		int resumeId = 0;

		StringList aliases = new StringList();
		boolean moreAliases = true;

		while (moreAliases) {

			// Build the enumerate aliases request

			buf.resetBuffer();

			buf.putHandle(domHandle);
			buf.putInt(resumeId); // resume handle
			// buf.putInt( DefaultBufferSize); // preferred maximum size
			// Note: Ethereal says this field is account control but trying different
			// smaller values it looks like the buffer size.
			buf.putInt(0xFFFFFFFF); // account control

			// Initialize the DCE request

			DCEPacket pkt = getPacket();
			try {
				pkt.initializeDCERequest(getHandle(), Samr.SamrEnumAliases, buf, getMaximumTransmitSize(), getNextCallId());
			}
			catch (DCEBufferException ex) {
				ex.printStackTrace();
			}

			// Send the enumerate aliases request

			doDCERequest(pkt);

			// Retrieve the alias list from the response

			DCEBuffer rxBuf = getRxBuffer();

			try {

				// Check the request status

				if ( rxBuf.hasSuccessStatus())
					moreAliases = false;
				if ( rxBuf.hasMoreEntries())
					moreAliases = true;
				else
					checkStatus(rxBuf.getStatusCode());

				// Get the resume handle

				resumeId = rxBuf.getInt();

				// Load the alias names

				if ( rxBuf.getPointer() != 0) {

					// Get the name count

					int cnt = rxBuf.getInt();

					if ( rxBuf.getPointer() != 0) {

						// Skip the max count value

						rxBuf.skipBytes(4);

						// Skip the name entry pointer/sizes

						rxBuf.skipBytes(cnt * 12);

						// Load the actual name strings

						for (int i = 0; i < cnt; i++) {

							// Load a name string

							String name = rxBuf.getString(DCEBuffer.ALIGN_INT);
							if ( name != null)
								aliases.addString(name);
						}
					}
				}
			}
			catch (DCEBufferException ex) {
			}
		}

		// Return the alias names

		return aliases;
	}

	/**
	 * Find a domain and return the SID
	 * 
	 * @param domain String
	 * @return SID
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final SID lookupDomain(String domain)
		throws IOException, SMBException {

		// Check if the SID is in the cache

		SID sid = m_domainSIDs.findSID(domain);
		if ( sid != null)
			return sid;

		// Build the lookup domain request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(getSamrHandle());
		buf.putUnicodeHeader(domain.length());
		buf.putString(domain, DCEBuffer.ALIGN_INT, false);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Samr.SamrLookupDomain, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the lookup domain request

		doDCERequest(pkt);

		// Retrieve the domain SID from the response

		DCEBuffer rxBuf = getRxBuffer();
		sid = new SID();

		try {

			// Check the request status

			checkStatus(rxBuf.getStatusCode());

			// Skip the pointer and count values

			rxBuf.skipBytes(8);

			// Load the SID

			try {
				sid.loadSID(rxBuf.getBuffer(), rxBuf.getReadPosition(), true);
			}
			catch (LoadException ex) {
				throw new IOException("Failed to load SID object");
			}

			// Set the SID name

			sid.setName(domain);

			// Add the domain SID to the cache

			m_domainSIDs.addSID(domain, sid);
		}
		catch (DCEBufferException ex) {
		}

		// Return the domain SID

		return sid;
	}

	/**
	 * Open a domain and return the policy handle required to perform other functions on the domain
	 * 
	 * @param domain String
	 * @return PolicyHandle
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final PolicyHandle openDomain(String domain)
		throws IOException, SMBException {

		// Check if the domain handle is in the cache

		PolicyHandle domHandle = m_domainHandles.findHandle(domain);
		if ( domHandle != null)
			return domHandle;

		// Get the SID for the domain and open the domain

		SID domSID = lookupDomain(domain);
		domHandle = openDomain(domSID);

		// Add the handle to the cache

		m_domainHandles.addHandle(domain, domHandle);

		// Return the domain handle

		return domHandle;
	}

	/**
	 * Open the built in domain
	 * 
	 * @return PolicyHandle
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final PolicyHandle openBuiltinDomain()
		throws IOException, SMBException {

		// Open the builtin domain using the well known SID

		return openDomain(WellKnownSID.SIDBuiltinDomain);
	}

	/**
	 * Open a domain and return the policy handle required to perform other functions on the domain
	 * 
	 * @param domainSID SID
	 * @return PolicyHandle
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final PolicyHandle openDomain(SID domainSID)
		throws IOException, SMBException {

		// Build the open domain request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(getSamrHandle());
		buf.putInt(0x0305); // access mask
		buf.putInt(domainSID.getSubauthorityCount());

		int off = -1;

		try {
			off = domainSID.saveSID(buf.getBuffer(), buf.getWritePosition());
		}
		catch (SaveException ex) {
			throw new IOException("Failed to save SID object");
		}

		buf.setWritePosition(off);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Samr.SamrOpenDomain, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the open SAM service request

		doDCERequest(pkt);

		// Retrieve the policy handle from the response

		DCEBuffer rxBuf = getRxBuffer();
		PolicyHandle domHandle = new PolicyHandle();

		try {
			checkStatus(rxBuf.getStatusCode());
			rxBuf.getHandle(domHandle);
		}
		catch (DCEBufferException ex) {
		}

		// Add the domain handle to the cache
		// Return the handle

		return domHandle;
	}

	/**
	 * Lookup an object name within a domain and return the resource ids and types
	 * 
	 * @param domHandle PolicyHandle
	 * @param name String
	 * @return RIDList
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final RIDList lookupName(PolicyHandle domHandle, String name)
		throws IOException, SMBException {

		// Build the lookup names request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(domHandle);
		buf.putInt(1); // count

		buf.putInt(1000); // max count
		buf.putInt(0); // offset
		buf.putInt(1); // actual count

		buf.putUnicodeHeader(name.length());
		buf.putString(name, DCEBuffer.ALIGN_NONE, false);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Samr.SamrLookupNames, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the lookup domain request

		doDCERequest(pkt);

		// Retrieve the list of matching RIDs

		DCEBuffer rxBuf = getRxBuffer();

		// Check the request status, ignore informational status codes

		if ( rxBuf.getStatusCode() != SMBStatus.NTSuccess && rxBuf.getStatusCode() != SMBStatus.NTNoneMapped)
			checkStatus(rxBuf.getStatusCode());
		RIDList rids = unpackRIDList(rxBuf, name);

		// Return the RID list

		return rids;
	}

	/**
	 * Lookup the object name for the specified security id
	 * 
	 * @param sid SID
	 * @return RIDList
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final RIDList lookupName(SID sid)
		throws IOException, SMBException {

		// Check if the domain SID cache has any entries, if not then populate

		if ( m_domainSIDs.numberOfSIDs() == 0)
			populateDomainSIDCache();

		// Check for a well known SID

		if ( WellKnownSID.getSIDName(sid) != null)
			return null;

		// Look for the matching domain SID

		String domainName = m_domainSIDs.findName(sid);
		if ( domainName == null)
			return null;

		// Check if the SID has a relative id, if not then return the domain in the RID list

		if ( sid.hasRID() == false) {

			// Set the SID name

			sid.setName(domainName);

			// Create a RID list with an entry for the domain

			RIDList list = new RIDList();
			list.addRID(new RID(-1, RID.TypeDomain, domainName));

			return list;
		}

		// Get a handle for the domain

		PolicyHandle handle = openDomain(domainName);

		// Get the object name

		int[] ids = new int[1];
		ids[0] = sid.getRID();

		RIDList list = lookupIds(handle, ids);

		// Build the object name string for the SID

		StringBuffer objName = new StringBuffer();

		objName.append(domainName);
		objName.append("\\");
		objName.append(list.getRIDAt(0).getName());

		sid.setName(objName.toString());

		// Return the relative id list

		return list;
	}

	/**
	 * Lookup the domain name for the specified security id
	 * 
	 * @param sid SID
	 * @return String
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final String lookupDomainName(SID sid)
		throws IOException, SMBException {

		// Check if the domain SID cache has any entries, if not then populate

		if ( m_domainSIDs.numberOfSIDs() == 0)
			populateDomainSIDCache();

		// Look for the matching domain SID

		return m_domainSIDs.findName(sid);
	}

	/**
	 * Lookup resource ids within a domain and fill in the resource names and types. The original
	 * RIDList is returned with the names and types set.
	 * 
	 * @param domHandle PolicyHandle
	 * @param rids RIDList
	 * @return RIDList
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final RIDList lookupIds(PolicyHandle domHandle, RIDList rids)
		throws IOException, SMBException {

		// Get the ids and convert to names/types

		int[] ids = rids.getIdList();
		RIDList nameList = lookupIds(domHandle, ids, ids.length);

		// Copy the name/type values to the original list

		for (int i = 0; i < nameList.numberOfRIDs(); i++) {

			// Get the current RID and match up with the original list

			RID nameRID = nameList.getRIDAt(i);
			RID idRID = rids.findRID(nameRID.getRID());

			if ( idRID != null) {

				// Copy the name/type into the original RID

				idRID.setName(nameRID.getName());
				idRID.setType(nameRID.isType());
			}
		}

		// Return the original list

		return rids;
	}

	/**
	 * Lookup resource ids within a domain and return the resource names and types
	 * 
	 * @param domHandle PolicyHandle
	 * @param ids int[]
	 * @return RIDList
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final RIDList lookupIds(PolicyHandle domHandle, int[] ids)
		throws IOException, SMBException {

		// Call the main id lookup

		return lookupIds(domHandle, ids, ids.length);
	}

	/**
	 * Lookup resource ids within a domain and return the resource names and types
	 * 
	 * @param domHandle PolicyHandle
	 * @param ids int[]
	 * @param count int
	 * @return RIDList
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final RIDList lookupIds(PolicyHandle domHandle, int[] ids, int count)
		throws IOException, SMBException {

		// Build the lookup names request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(domHandle);
		buf.putInt(count); // count of RIDs to convert

		buf.putInt(1000); // max count
		buf.putInt(0); // offset
		buf.putInt(count); // actual count

		for (int i = 0; i < count; i++)
			buf.putInt(ids[i]);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Samr.SamrLookupRIDs, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the lookup domain request

		doDCERequest(pkt);

		// Retrieve the list of matching RIDs

		DCEBuffer rxBuf = getRxBuffer();

		// Check the request status

		checkStatus(rxBuf.getStatusCode());
		RIDList rids = unpackRIDNameList(rxBuf, ids, count);

		// Return the RID list

		return rids;
	}

	/**
	 * Open a user and return the policy handle required to perform other functions on the user
	 * 
	 * @param domHandle domHandle PolicyHandle
	 * @param rid RID
	 * @return PolicyHandle
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final PolicyHandle openUser(PolicyHandle domHandle, RID rid)
		throws IOException, SMBException {

		// Open the user

		return openUser(domHandle, rid.getRID());
	}

	/**
	 * Open a user and return the policy handle required to perform other functions on the user
	 * 
	 * @param domain String
	 * @param userName String
	 * @return PolicyHandle
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final PolicyHandle openUser(String domain, String userName)
		throws IOException, SMBException {

		// Open the domain

		PolicyHandle domainHandle = openDomain(domain);

		// Find the user relative-id

		RIDList rids = lookupName(domainHandle, userName);
		RID userRID = rids.findRID(userName, RID.TypeUser);

		PolicyHandle userHandle = null;

		if ( userRID != null) {

			// Get a handle to the user

			userHandle = openUser(domainHandle, userRID);
		}
		else
			throw new SMBException(SMBStatus.NTErr, SMBStatus.NTObjectNotFound);

		// Return the handle to the user object

		return userHandle;
	}

	/**
	 * Open a user and return the policy handle required to perform other functions on the user
	 * 
	 * @param domHandle domHandle PolicyHandle
	 * @param rid int
	 * @return PolicyHandle
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final PolicyHandle openUser(PolicyHandle domHandle, int rid)
		throws IOException, SMBException {

		// Build the open user request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(domHandle);
		buf.putInt(0x031B); // access mask
		buf.putInt(rid);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Samr.SamrOpenUser, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the open user request

		doDCERequest(pkt);

		// Retrieve the policy handle from the response

		DCEBuffer rxBuf = getRxBuffer();
		PolicyHandle usrHandle = new PolicyHandle();

		try {
			checkStatus(rxBuf.getStatusCode());
			rxBuf.getHandle(usrHandle);
		}
		catch (DCEBufferException ex) {
		}

		// Return the handle

		return usrHandle;
	}

	/**
	 * Return the list of groups that the user has membership of
	 * 
	 * @param domain String
	 * @param userName String
	 * @return RIDList
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final RIDList getGroupsForUser(String domain, String userName)
		throws IOException, SMBException {

		// Get a handle to the user

		PolicyHandle userHandle = openUser(domain, userName);

		// Get the groups list for the user

		RIDList groups = null;

		try {

			// Get the group list for the user

			groups = getGroupsForUser(userHandle);

			// Convert RIDs to names

			if ( groups != null)
				groups = lookupIds(openDomain(domain), groups);
		}
		finally {

			// Close the user handle

			if ( userHandle != null)
				closeHandle(userHandle);
		}

		// Return the groups list

		return groups;
	}

	/**
	 * Return the list of groups that the user has membership of
	 * 
	 * @param usrHandle PolicyHandle
	 * @return RIDList
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final RIDList getGroupsForUser(PolicyHandle usrHandle)
		throws IOException, SMBException {

		// Build the get groups for user request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(usrHandle);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Samr.SamrGetGroupsForUser, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the open user request

		doDCERequest(pkt);

		// Retrieve the user information from the response

		DCEBuffer rxBuf = getRxBuffer();
		RIDList rids = new RIDList();

		try {

			// Check the response status

			checkStatus(rxBuf.getStatusCode());

			// Unpack the RID list

			if ( rxBuf.getPointer() != 0) {

				// Get the count of RID records returned

				int cnt = rxBuf.getInt();

				if ( cnt > 0 && rxBuf.getPointer() != 0) {

					// Skip the count

					rxBuf.skipBytes(4);

					// Read the RIDs/attributes

					for (int i = 0; i < cnt; i++) {

						// Get the RID and attributes

						int rid = rxBuf.getInt();
						int attr = rxBuf.getInt();

						// Add the RID to the list

						rids.addRID(new RID(rid, WellKnownRID.isWellKnownGroup(rid) ? RID.TypeWellKnownGroup
								: RID.TypeDomainGroup, WellKnownRID.getWellKnownGroupName(rid)));
					}
				}
			}
		}
		catch (DCEBufferException ex) {
		}

		// Return the group RIDs list

		return rids;
	}

	/**
	 * Return the list of aliases that the user has membership of
	 * 
	 * @param domain String
	 * @param userName String
	 * @return RIDList
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final RIDList getAliasesForUser(String domain, String userName)
		throws IOException, SMBException {

		// Get the domain SID and handle

		SID domSID = lookupDomain(domain);
		PolicyHandle domHandle = openDomain(domSID);
		PolicyHandle builtinHandle = openBuiltinDomain();

		// Find the user relative-id

		RIDList rids = lookupName(domHandle, userName);
		RID userRID = rids.findRID(userName, RID.TypeUser);

		if ( userRID == null)
			throw new SMBException(SMBStatus.NTErr, SMBStatus.NTObjectNotFound);

		// Make a SID for the user

		SID userSID = new SID(domSID);
		userSID.setRID(userRID.getRID());

		// Get the alias list for the user

		RIDList domAliasList = getAliasesForUser(domHandle, userSID);
		if ( domAliasList != null)
			domAliasList = lookupIds(domHandle, domAliasList);
		else
			domAliasList = new RIDList();

		// Get the builtin alias list for the user

		RIDList builtinList = getAliasesForUser(builtinHandle, userSID);
		if ( builtinList != null)
			builtinList = lookupIds(builtinHandle, builtinList);
		domAliasList.addRIDs(builtinList);

		return domAliasList;
	}

	/**
	 * Return the list of aliases that the user has membership of
	 * 
	 * @param domHandle PolicyHandle
	 * @param userSID SID
	 * @return RIDList
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final RIDList getAliasesForUser(PolicyHandle domHandle, SID userSID)
		throws IOException, SMBException {

		// Copy the SID to make a SID with the domain groups users id

		SID usersGroupSID = new SID(userSID);
		usersGroupSID.setRID(WellKnownRID.DomainGroupUsers);

		// Build the get alias membership for user request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(domHandle);

		buf.putInt(1); // number of SIDs
		buf.putPointer(true);

		buf.putInt(1); // number of SIDs
		buf.putPointer(true);

		buf.putInt(userSID.getSubauthorityCount());
		int off = -1;

		try {
			off = userSID.saveSID(buf.getBuffer(), buf.getWritePosition());
		}
		catch (SaveException ex) {
			throw new IOException("Failed to save user SID object");
		}

		buf.setWritePosition(off);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Samr.SamrGetAliasMembership, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the open user request

		doDCERequest(pkt);

		// Retrieve the user information from the response

		DCEBuffer rxBuf = getRxBuffer();
		RIDList rids = new RIDList();

		try {

			// Check the response status

			checkStatus(rxBuf.getStatusCode());

			// Get the count of RIDs returned

			int cnt = rxBuf.getInt();

			if ( cnt > 0 && rxBuf.getPointer() != 0) {

				// Skip the count

				rxBuf.skipBytes(4);

				// Read the RIDs

				for (int i = 0; i < cnt; i++) {

					// Get the RID and attributes

					int rid = rxBuf.getInt();

					// Add the RID to the list

					rids.addRID(new RID(rid, RID.TypeAlias, WellKnownRID.getWellKnownAliasName(rid)));
				}
			}
		}
		catch (DCEBufferException ex) {
		}

		// Return the alias RIDs list

		return rids;
	}

	/**
	 * Return information for the specified user
	 * 
	 * @param usrHandle PolicyHandle
	 * @param infoLevel int
	 * @return UserInfo
	 * @throws IOException Socket error
	 * @throws SMBException SMB error
	 */
	public final UserInfo queryUserInformation(PolicyHandle usrHandle, int infoLevel)
		throws IOException, SMBException {

		// Build the query user information request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(usrHandle);
		buf.putShort(infoLevel);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Samr.SamrQueryUserInfo, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the open user request

		doDCERequest(pkt);

		// Retrieve the user information from the response

		DCEBuffer rxBuf = getRxBuffer();
		UserInfo usrInfo = null;

		try {
			checkStatus(rxBuf.getStatusCode());

			// Get the user information pointer and information level

			int ptr = rxBuf.getPointer();
			int lev = rxBuf.getShort(DCEBuffer.ALIGN_INT);
			if ( ptr != 0) {
				usrInfo = new UserInfo(lev);
				usrInfo.readObject(rxBuf);
				usrInfo.readStrings(rxBuf);
			}
		}
		catch (DCEBufferException ex) {
		}

		// Return the user information

		return usrInfo;
	}

	/**
	 * Close the remote SAM service
	 * 
	 * @param handle PolicyHandle
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void closeHandle(PolicyHandle handle)
		throws IOException, SMBException {

		// Build the close handle request

		DCEBuffer buf = getBuffer();
		buf.resetBuffer();

		buf.putHandle(handle);

		// Initialize the DCE request

		DCEPacket pkt = getPacket();
		try {
			pkt.initializeDCERequest(getHandle(), Samr.SamrCloseHandle, buf, getMaximumTransmitSize(), getNextCallId());
		}
		catch (DCEBufferException ex) {
			ex.printStackTrace();
		}

		// Send the close handle request

		getSession().SendTransaction(pkt, pkt);
		if ( pkt.isValidResponse() == false)
			throw new SMBException(SMBStatus.NTErr, pkt.getLongErrorCode());
	}

	/**
	 * Close the pipe
	 * 
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public void ClosePipe()
		throws IOException, SMBException {

		// Close the SAMR service handle

		if ( getSamrHandle() != null) {

			// Close the SAMR service handle

			closeHandle(m_handle);
			m_handle = null;
		}

		// Call the base class

		super.ClosePipe();
	}

	/**
	 * Unpack a RID list that contains ids and types from a DCE buffer
	 * 
	 * @param buf DCEBuffer
	 * @param rscName String
	 * @return RIDList
	 */
	private final RIDList unpackRIDList(DCEBuffer buf, String rscName) {

		RIDList rids = null;

		try {

			// Get the RID list

			rids = new RIDList();

			int cnt = buf.getInt();
			if ( cnt > 0 && buf.getPointer() != 0) {

				// Skip the max count

				buf.skipBytes(4);

				// Load the RIDs

				for (int i = 0; i < cnt; i++) {

					// Create a new RID

					int id = buf.getInt();
					RID rid = new RID(id, -1, rscName);

					rids.addRID(rid);
				}
			}

			// Add the types to the RIDs

			cnt = buf.getInt();
			if ( cnt > 0 && buf.getPointer() != 0) {

				// Skip the max count

				buf.skipBytes(4);

				// Set the RID type

				for (int i = 0; i < cnt; i++) {

					// Get the RID from the list

					RID curRID = rids.getRIDAt(i);

					int typ = buf.getInt();
					curRID.setType(typ);
				}
			}
		}
		catch (DCEBufferException ex) {

			// Clear the RID list

			rids = null;
		}

		// Return the RID list

		return rids;
	}

	/**
	 * Unpack a RID list that contains names and types from a DCE buffer
	 * 
	 * @param buf DCEBuffer
	 * @param ids int[]
	 * @param count int
	 * @return RIDList
	 */
	private final RIDList unpackRIDNameList(DCEBuffer buf, int[] ids, int count) {

		RIDList rids = null;

		try {

			// Get the RID list

			rids = new RIDList();

			int cnt = buf.getInt();
			if ( cnt > 0 && buf.getPointer() != 0) {

				// Skip the max count + 8 bytes per entry

				buf.skipBytes(4 + (8 * cnt));

				// Load the resource names

				for (int i = 0; i < cnt; i++) {

					// Create a new RID

					String rscName = buf.getCharArray(DCEBuffer.ALIGN_INT);
					RID rid = new RID(ids[i], -1, rscName);

					rids.addRID(rid);
				}
			}

			// Add the types to the RIDs

			cnt = buf.getInt();
			if ( cnt > 0 && buf.getPointer() != 0) {

				// Skip the max count

				buf.skipBytes(4);

				// Set the RID type

				for (int i = 0; i < cnt; i++) {

					// Get the RID from the list

					RID curRID = rids.getRIDAt(i);

					int typ = buf.getInt();
					curRID.setType(typ);
				}
			}
		}
		catch (DCEBufferException ex) {

			// Clear the RID list

			rids = null;
		}

		// Return the RID list

		return rids;
	}

	/**
	 * Return the SAMR service handle
	 * 
	 * @return SamrPolicyHandle
	 */
	private final SamrPolicyHandle getSamrHandle() {
		return m_handle;
	}

	/**
	 * Clear the domain SID and policy handle caches. This will close all domain handles.
	 * 
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	public final void clearCaches()
		throws IOException, SMBException {

		// Clear the domain SID cache

		m_domainSIDs.removeAllSIDs();

		// Close all domain handles and clear the cache

		Enumeration enm = m_domainHandles.enumerateHandles();

		while (enm.hasMoreElements()) {

			// Get a domain handle

			PolicyHandle domainHandle = (PolicyHandle) enm.nextElement();
			closeHandle(domainHandle);
		}

		// Clear the domain handle cache

		m_domainHandles.removeAllHandles();
	}

	/**
	 * Populate the domain SID cache
	 * 
	 * @exception IOException Socket error
	 * @exception SMBException SMB error
	 */
	private final void populateDomainSIDCache()
		throws IOException, SMBException {

		// Enumerate the available domains

		StringList domains = enumerateDomains();

		for (int i = 0; i < domains.numberOfStrings(); i++) {

			// Get a domain name

			String name = domains.getStringAt(i);

			// Get the domain SID and add to the cache

			SID domSid = lookupDomain(name);
			m_domainSIDs.addSID(name, domSid);
		}

		// Add the Builtin domain SID

		SID builtin = WellKnownSID.SIDBuiltinDomain;
		m_domainSIDs.addSID(builtin.getName(), builtin);
	}
}
