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

package org.filesys.client.info;

import java.io.Serializable;
import java.util.Date;

/**
 *  SMB print queue entry class
 *
 *  <p>The PrintJob class contains the details of a remote print server job.
 *
 *  <p>A list of print jobs is returned by the AdminSession.getPrintJobs () method.
 *  The returned print jobs can then be used to pause/continue or delete print jobs using
 *  the AdminSession PausePrintJob(), ContinuePrintJob() and DeletePrintJob() methods.
 *  
 *  @author gkspencer
 */
public final class PrintJob implements Serializable {

	//	Print job status codes

	public static final int Queued 			= 0x00;
	public static final int Paused 			= 0x01;
	public static final int Spooling 		= 0x02;

	//	If the print job is in a printing state the remaining bits indicate extra
	//	detail about the job status.

	public static final int Printing 		= 0x03;
	public static final int StatusMask 		= 0x03;

	public static final int Complete 		= 0x0004;

	//	Print job requires user intervention, comment may contain extra information

	public static final int Intervention 	= 0x0008;
	public static final int Error 			= 0x0010;
	public static final int DestOffline 	= 0x0020;
	public static final int DestPaused 		= 0x0040;
	public static final int Notify 			= 0x0080;
	public static final int DestNoPaper 	= 0x0100;
	public static final int DestFormChg 	= 0x0200;
	public static final int DestCartChg 	= 0x0400;
	public static final int DestPenChg 		= 0x0800;
	public static final int PrintDel 		= 0x8000;

	//	Print job id

	private int m_jobid;

	//	Print job priority

	private int m_priority;

	//	Name of the user that submitted the print job

	private String m_user;

	//	Print job position within the print queue, a value of 1 indicates that
	//	the job will print next.

	private int m_pos;

	//	Print job status bits

	private int m_status;

	//	Date/time that the job was submitted

	private Date m_queueTime;

	//	Spool file size, in bytes

	private int m_spoolsize;

	//	Comment about the print job

	private String m_comment;

	//	Name of the document being printed

	private String m_document;
  
	/**
	 * Class constructor.
	 * 
	 * @param id Job id
	 */
	public PrintJob(int id) {
		m_jobid = id;
	}

	/**
	 * Return the print job comment
	 * 
	 * @return Print job comment string
	 */
	public final String getComment() {
		return m_comment;
	}

	/**
	 * Return the print server assigned file number.
	 * 
	 * @return Print server assigned file number.
	 */
	public final int getJobNumber() {
		return m_jobid;
	}

	/**
	 * Return the print jobs queue position
	 * 
	 * @return Print job queue position
	 */
	public final int getPrintPosition() {
		return m_pos;
	}

	/**
	 * Return the extra status bits that are set when a job is in a printing state
	 * 
	 * @return Extra status bits if the job is in a printing state, else zero
	 */
	public final int getPrintStatus() {
		return m_status;
	}

	/**
	 * Return theprint job priority, where 1 is the lowest priority and 99 is the highest priority.
	 * 
	 * @return Print job priority
	 */
	public final int getPriority() {
		return m_priority;
	}

	/**
	 * Return the date/time that the print job was submitted
	 * 
	 * @return Date/time that the print job was submitted
	 */
	public final Date getQueuedDateTime() {
		return m_queueTime;
	}

	/**
	 * Return the spool document name.
	 * 
	 * @return Spool document name string.
	 */
	public final String getSpoolDocument() {
		return m_document;
	}

	/**
	 * Return the spool file size, in bytes.
	 * 
	 * @return Spool file size in bytes.
	 */
	public final int getSpoolFileSize() {
		return m_spoolsize;
	}

	/**
	 * Return the print job status string
	 * 
	 * @return Print job status string
	 */
	public final String getStatusString() {
		String sts = null;
		switch (m_status & StatusMask) {
			case Queued:
				sts = "Queued";
				break;
			case Paused:
				sts = "Paused";
				break;
			case Spooling:
				sts = "Spooling";
				break;
			case Printing: {
				StringBuffer str = new StringBuffer("Printing");
				if ( (m_status & Complete) != 0)
					str.append(" - Complete");
				if ( (m_status & DestCartChg) != 0)
					str.append(" - DestCartChange");
				if ( (m_status & DestFormChg) != 0)
					str.append(" - DestFormChange");
				if ( (m_status & DestNoPaper) != 0)
					str.append(" - NoPaper");
				if ( (m_status & DestOffline) != 0)
					str.append(" - Offline");
				if ( (m_status & DestPaused) != 0)
					str.append(" - Paused");
				if ( (m_status & DestPenChg) != 0)
					str.append(" - DestPenChange");
				if ( (m_status & Error) != 0)
					str.append(" - Error");
				if ( (m_status & Intervention) != 0)
					str.append(" - Intervention");
				if ( (m_status & Notify) != 0)
					str.append(" - Notify");
				if ( (m_status & PrintDel) != 0)
					str.append(" - PrintDel");

				sts = str.toString();
			}
				break;
		}
		return sts;
	}

	/**
	 * Return the name of the user who submitted the print request.
	 * 
	 * @return User name string
	 */
	public final String getUserName() {
		return m_user;
	}

	/**
	 * Determine if the print job is in a paused state
	 * 
	 * @return true if the print job is in a paused state, else false
	 */
	public final boolean isPaused() {
		return (m_status & StatusMask) == Paused ? true : false;
	}

	/**
	 * Determine if the print job is in a printing state
	 * 
	 * @return true is the print job is in a printing state, else false
	 */
	public final boolean isPrinting() {
		return (m_status & StatusMask) == Printing ? true : false;
	}

	/**
	 * Determine if the print job is in a queued state
	 * 
	 * @return true if the print job is in a queued state, else false
	 */
	public final boolean isQueued() {
		return (m_status & StatusMask) == Queued ? true : false;
	}

	/**
	 * Determine if the print job is in a spooling state
	 * 
	 * @return true is the print job is in a spooling state, else false
	 */
	public final boolean isSpooling() {
		return (m_status & StatusMask) == Spooling ? true : false;
	}

	/**
	 * Set the print jobs comment
	 * 
	 * @param comm Print job comment string
	 */
	public final void setComment(String comm) {
		m_comment = comm;
	}

	/**
	 * Set the print job document name
	 * 
	 * @param doc Document name
	 */
	public final void setDocument(String doc) {
		m_document = doc;
	}

	/**
	 * Set the print job id
	 * 
	 * @param id Print job id
	 */
	public final void setJobNumber(int id) {
		m_jobid = id;
	}

	/**
	 * Set the jobs print position
	 * 
	 * @param pos Print position
	 */
	public final void setPrintPosition(int pos) {
		m_pos = pos;
	}

	/**
	 * Set the print job priority
	 * 
	 * @param pri Print job priority
	 */
	public final void setPriority(int pri) {
		m_priority = pri;
	}

	/**
	 * Set the submitted print job date/time
	 * 
	 * @param dattim Date/time that the job was submitted
	 */
	public final void setQueuedDateTime(Date dattim) {
		m_queueTime = dattim;
	}

	/**
	 * Set the print job file size
	 * 
	 * @param siz Spool file size
	 */
	public final void setSpoolFileSize(int siz) {
		m_spoolsize = siz;
	}

	/**
	 * Set the print job status bits
	 * 
	 * @param sts Print job status bits
	 */
	public final void setStatus(int sts) {
		m_status = sts;
	}

	/**
	 * Set the job user name
	 * 
	 * @param usr Job owner user name
	 */
	public final void setUserName(String usr) {
		m_user = usr;
	}

	/**
	 * Return the print job information as a string
	 * 
	 * @return Print job information string
	 */
	public final String toString() {
		StringBuffer str = new StringBuffer();
		str.append("[");
		str.append(this.getJobNumber());
		str.append(" - ");
		str.append(this.getUserName());
		str.append(" - ");
		str.append(this.getStatusString());
		str.append(" - ");
		str.append(this.getQueuedDateTime());
		str.append(" - ");
		str.append(this.getSpoolDocument());
		str.append(" ");
		str.append(this.getSpoolFileSize());
		str.append("]");

		return str.toString();
	}
}