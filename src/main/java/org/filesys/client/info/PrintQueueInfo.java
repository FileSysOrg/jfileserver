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

/**
 * SMB print queue information class
 *
 * <p>The PrintQueueInfo class contains the details of a remote print queue on
 *  a remote print server.
 *
 * <p>A list of remote print queues is returned by the AdminSession.getPrintList () method.
 * 
 * @author gkspencer
 */
public final class PrintQueueInfo implements Serializable {

	//	Remote print status codes

	public static final int QueueActive 		= 0;
	public static final int QueuePaused 		= 1;
	public static final int QueueError 			= 2;
	public static final int QueueDelPending 	= 3;

	//	Queue name

	private String m_name;

	//	Queue priority

	private int m_priority;

	//	Time that the queue can start sending print jobs to the printer.
	//	The value is seconds since midnight.

	private int m_startTime;

	//	Time that the queue will stop sending print jobs to the printer.
	//	The value is seconds since midnight.

	private int m_stopTime;

	//	Seperator page file name

	private String m_sepPage;

	//	Name of the print pre-processor

	private String m_preprocess;

	//	List of destination printers for this queue

	private String m_prnDest;

	//	Print queue parameters

	private String m_prnParam;

	//	Queue status

	private int m_status;

	//	Number of print jobs currently in this queue

	private int m_jobCount;

	//	Dvice driver for this queue

	private String m_devdriver;

	//	Device driver data

	private String m_devData;

	//	Queue comment

	private String m_comment;
  
	/**
	 * Class constructor
	 * 
	 * @param qname Name of the print queue
	 */
	public PrintQueueInfo(String qname) {
		m_name = qname;
	}

	/**
	 * Return the queue comment.
	 * 
	 * @return java.lang.String
	 */
	public String getComment() {
		return m_comment;
	}

	/**
	 * Return the printer queue device driver name
	 * 
	 * @return Printer device driver name
	 */
	public final String getDeviceDriver() {
		return m_devdriver;
	}

	/**
	 * Return the printer queue device driver data string
	 * 
	 * @return Printer device driver data string
	 */
	public final String getDriverData() {
		return m_devData;
	}

	/**
	 * Return the count of jobs in the print queue
	 * 
	 * @return Count of jobs in the print queue
	 */
	public final int getJobCount() {
		return m_jobCount;
	}

	/**
	 * Return the printer queue parameter string
	 * 
	 * @return Printer queue parameter string
	 */
	public final String getParameterString() {
		return m_prnParam;
	}

	/**
	 * Return the print queue pre-processor. A null string indicates the default pre-processor.
	 * 
	 * @return Print queue pre-processor
	 */
	public final String getPreProcessor() {
		return m_preprocess;
	}

	/**
	 * Return the list of print destinations
	 * 
	 * @return Print destination list
	 */
	public final String getPrinterList() {
		return m_prnDest;
	}

	/**
	 * Return the queue priority. A value of 1 indidcates the highest priority and a value of 9
	 * indicates the lowest priority.
	 * 
	 * @return Queue priority
	 */
	public final int getPriority() {
		return m_priority;
	}

	/**
	 * Return the queue name
	 * 
	 * @return Queue name string
	 */
	public final String getQueueName() {
		return m_name;
	}

	/**
	 * Return the seperator page file name
	 * 
	 * @return Seperator page file name
	 */
	public final String getSeperatorPage() {
		return m_sepPage;
	}

	/**
	 * Return the queue start time, as the number of minutes since midnight.
	 * 
	 * @return Queue start time
	 */
	public final int getStartTime() {
		return m_startTime;
	}

	/**
	 * Return the queue status. The value is one of QueueActive, QueuePaused, QueueError or
	 * QueueDelPending.
	 * 
	 * @return Print queue status
	 */
	public final int getStatus() {
		return m_status;
	}

	/**
	 * Return the queue status as a string
	 * 
	 * @return Queue status string
	 */
	public final String getStatusString() {

		// Determine the queue status

		return getStatusString(m_status);
	}

	/**
	 * Return the queue status as a string
	 *
	 * @param s int
	 * @return Queue status string
	 */
	public final static String getStatusString(int s) {

		// Determine the queue status

		String sts = null;

		switch (s) {
			case 0:
				sts = "Active";
				break;
			case 1:
				sts = "Paused";
				break;
			case 2:
				sts = "Error";
				break;
			case 3:
				sts = "Delete Pending";
				break;
			default:
				sts = "Unknown";
				break;
		}
		return sts;
	}

	/**
	 * Return the queue stop time, as the number of minutes since midnight.
	 * 
	 * @return Queue stop time.
	 */
	public final int getStopTime() {
		return m_stopTime;
	}

	/**
	 * Set the queue comment.
	 * 
	 * @param comment java.lang.String
	 */
	public void setComment(String comment) {
		m_comment = comment;
	}

	/**
	 * Set the pending print job count for the queue
	 * 
	 * @param jobs Number of pending print jobs
	 */
	public final void setJobCount(int jobs) {
		m_jobCount = jobs;
	}

	/**
	 * Set the printer parameters string
	 * 
	 * @param prm Printer parameters string.
	 */
	public final void setParameterString(String prm) {
		m_prnParam = prm;
	}

	/**
	 * Set the queue pre-processor
	 * 
	 * @param pre Queeu pre-processor
	 */
	public final void setPreProcessor(String pre) {
		m_preprocess = pre;
	}

	/**
	 * Set the printer queue destination print device(s)
	 * 
	 * @param prn Destination print device list
	 */
	public final void setPrinterList(String prn) {
		m_prnDest = prn;
	}

	/**
	 * Set the queue priority
	 * 
	 * @param pri Queue priority, 1 is the highest priority and 9 is the lowest priority.
	 */
	public final void setPriority(int pri) {
		m_priority = pri;
	}

	/**
	 * Set the seperator page details
	 * 
	 * @param sep Seperator page details
	 */
	public final void setSeperatorPage(String sep) {
		m_sepPage = sep;
	}

	/**
	 * Set the queue start time, in minutes since midnight.
	 * 
	 * @param startMin int
	 */
	public void setStartTime(int startMin) {
		m_startTime = startMin;
	}

	/**
	 * Set the queue status
	 * 
	 * @param sts Queue status
	 */
	public final void setStatus(int sts) {
		m_status = sts;
	}

	/**
	 * Set the queue stop time, in minutes since midnight.
	 * 
	 * @param stopMin int
	 */
	public void setStopTime(int stopMin) {
		m_stopTime = stopMin;
	}

	/**
	 * Output the printer queue information as a string
	 * 
	 * @return Printer queue information string
	 */
	public final String toString() {
		StringBuffer str = new StringBuffer();
		str.append("[");
		str.append(getQueueName());
		str.append(" : Pri ");
		str.append(getPriority());
		str.append(" - ");
		str.append(getPrinterList());
		str.append(" - Jobs ");
		str.append(getJobCount());
		str.append(" : ");
		str.append(getStatusString());
		str.append("]");
		return str.toString();
	}
}