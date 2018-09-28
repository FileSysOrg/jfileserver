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

import org.filesys.client.smb.DirectoryWatcher;
import org.filesys.smb.PacketTypeV1;
import org.filesys.util.DataPacker;

/**
 * Notify Change Asynchronous Request Class
 * 
 * <p>
 * Holds the details of a change notification asynchronous request that is used to receive details
 * of changes to files and/or folders on a remote file server.
 * 
 * @author gkspencer
 */
public class NotifyChangeAsynchRequest extends AsynchRequest {

	// Notify action names

	private static String[] _actionName = { "Added", "Removed", "Modified", "Rename New", "Rename Old" };

	// File id of the directory being watched

	private int m_fid;

	// Filter of events to generate, and watch whole directory tree flag

	private int m_filter;
	private boolean m_watchTree;

	// Directory change handler

	private DirectoryWatcher m_handler;

	/**
	 * Class constructor
	 * 
	 * @param mid int
	 * @param fid int
	 * @param filter int
	 * @param watchTree boolean
	 * @param handler DirectoryWatcher
	 */
	protected NotifyChangeAsynchRequest(int mid, int fid, int filter, boolean watchTree, DirectoryWatcher handler) {
		super(mid, "NotifyChange");

		m_fid = fid;
		m_filter = filter;
		m_watchTree = watchTree;

		m_handler = handler;
	}

	/**
	 * Return the file id being watched
	 * 
	 * @return int
	 */
	public final int getFileId() {
		return m_fid;
	}

	/**
	 * Return the watch filter
	 * 
	 * @return int
	 */
	public final int getFilter() {
		return m_filter;
	}

	/**
	 * Return the watch tree flag
	 * 
	 * @return boolean
	 */
	public final boolean hasWatchTree() {
		return m_watchTree;
	}

	/**
	 * Process the notify change response
	 * 
	 * @param sess Session
	 * @param pkt SMBPacket
	 */
	protected final void processResponse(Session sess, SMBPacket pkt) {

		// Make sure the response is an NT transaction command

		if ( pkt.getCommand() != PacketTypeV1.NTTransact)
			return;

		// Get the notify change response details

		NTTransPacket tpkt = new NTTransPacket(pkt.getBuffer());
		int tcnt = tpkt.getParameterBlockCount();

		if ( tcnt > 0) {

			// Unpack the notify data

			tpkt.resetParameterBlockPointer();

			// Process the notification structures

			int nextOff = -1;
			int action = -1;
			int nameLen = -1;

			int startPos = tpkt.getPosition();
			int endPos = startPos + tcnt;

			do {

				// Get the current strucuture details

				nextOff = tpkt.unpackInt();
				action = tpkt.unpackInt();
				nameLen = tpkt.unpackInt();

				String fname = DataPacker.getUnicodeString(tpkt.getBuffer(), tpkt.getPosition(), nameLen / 2);

				// Pass the details to the listener

				try {
					m_handler.directoryChanged(action, fname);
				}
				catch (Exception ex) {
					ex.printStackTrace();
				}

				// Update the buffer to the next structure, if available

				if ( nextOff != 0) {
					startPos += nextOff;
					tpkt.setPosition(startPos);
				}

			} while (nextOff != 0 && tpkt.getPosition() < endPos);
		}
		else {

			// Notification has triggered but there is no data to say what has changed

			try {
				m_handler.directoryChanged(DirectoryWatcher.FileActionUnknown, null);
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	/**
	 * Resubmit the request to the server
	 * 
	 * @param sess Session
	 * @param pkt SMBPacket
	 * @return boolean
	 */
	protected final boolean resubmitRequest(Session sess, SMBPacket pkt) {

		// Make sure the session is a CIFS session

		boolean sts = false;

		if ( sess.isActive() && sess instanceof CIFSDiskSession) {

			// Access the CIFS session

			CIFSDiskSession cifsSess = (CIFSDiskSession) sess;

			// Resubmit the notify request

			try {
				cifsSess.NTNotifyChange(this);
				sts = true;
			}
			catch (Exception ex) {

				// Mark as completed, failed to resubmit

				setCompleted(true);
			}
		}

		// Return the resubmit status

		return sts;
	}

	/**
	 * Return the request as a string
	 * 
	 * @return String
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();

		str.append("[");
		str.append(getId());
		str.append(":");
		str.append(getName());
		str.append(":");
		str.append(hasCompleted() ? "Completed" : "Pending");

		str.append(",FID=");
		str.append(getFileId());
		str.append(",filter=0x");
		str.append(Integer.toHexString(getFilter()));

		if ( hasWatchTree())
			str.append(",Tree");

		if ( hasAutoReset())
			str.append(",Auto");

		str.append("]");

		return str.toString();
	}
}
