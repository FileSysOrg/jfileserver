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

/**
 * File Stream Information Class
 * 
 * <p>
 * Contains the details of a file stream.
 * 
 * @author gkspencer
 */
public class StreamInfo {

	// Constants

	public static final String StreamSeparator = ":";

	// File path and stream name

	private String m_path;
	private String m_name;

	// Parent file id and stream id

	private int m_fid;
	private int m_stid;

	// Stream size/allocation size

	private long m_size;
	private long m_allocSize;

	/**
	 * Default constructor
	 */
	public StreamInfo() {
	}

	/**
	 * Constructor
	 * 
	 * @param path String
	 */
	public StreamInfo(String path) {

		// Parse the path to split into path and stream components

		parsePath(path);
	}

	/**
	 * Constructor
	 * 
	 * @param name String
	 * @param fid int
	 * @param stid int
	 */
	public StreamInfo(String name, int fid, int stid) {
		m_name = name;
		m_fid = fid;
		m_stid = stid;
	}

	/**
	 * Constructor
	 * 
	 * @param name String
	 * @param fid int
	 * @param stid int
	 * @param size long
	 * @param alloc long
	 */
	public StreamInfo(String name, int fid, int stid, long size, long alloc) {
		m_name = name;
		m_fid = fid;
		m_stid = stid;
		m_size = size;
		m_allocSize = alloc;
	}

	/**
	 * Return the file path
	 * 
	 * @return String
	 */
	public final String getPath() {
		return m_path;
	}

	/**
	 * Return the stream name
	 * 
	 * @return String
	 */
	public final String getName() {
		return m_name;
	}

	/**
	 * Return the stream file id
	 * 
	 * @return int
	 */
	public final int getFileId() {
		return m_fid;
	}

	/**
	 * Return the stream id
	 * 
	 * @return int
	 */
	public final int getStreamId() {
		return m_stid;
	}

	/**
	 * Return the stream size
	 * 
	 * @return long
	 */
	public final long getSize() {
		return m_size;
	}

	/**
	 * Return the stream allocation size
	 * 
	 * @return long
	 */
	public final long getAllocationSize() {
		return m_allocSize;
	}

	/**
	 * Set the path, if it contains the stream name the path will be split into file name and stream
	 * name components.
	 * 
	 * @param path String
	 */
	public final void setPath(String path) {
		parsePath(path);
	}

	/**
	 * Set the stream name
	 * 
	 * @param name String
	 */
	public final void setName(String name) {
		m_name = name;
	}

	/**
	 * Set the file id
	 * 
	 * @param id int
	 */
	public final void setFileId(int id) {
		m_fid = id;
	}

	/**
	 * Set the stream id
	 * 
	 * @param id int
	 */
	public final void setStreamId(int id) {
		m_stid = id;
	}

	/**
	 * Set the stream size
	 * 
	 * @param size long
	 */
	public final void setSize(long size) {
		m_size = size;
	}

	/**
	 * Set the stream allocation size
	 * 
	 * @param alloc long
	 */
	public final void setAllocationSize(long alloc) {
		m_allocSize = alloc;
	}

	/**
	 * Parse a path to split into file name and stream name components
	 * 
	 * @param path String
	 */
	protected final void parsePath(String path) {

		// Check if the file name contains a stream name

		int pos = path.indexOf(StreamSeparator);
		if ( pos == -1) {
			m_path = path;
			return;
		}

		// Split the main file name and stream name

		m_path = path.substring(0, pos);
		m_name = path.substring(pos + 1);
	}

	/**
	 * Return the stream information as a string
	 * 
	 * @return String
	 */
	public String toString() {
		StringBuffer str = new StringBuffer();

		str.append("[");
		str.append(getName());
		str.append(",");
		str.append(getFileId());
		str.append(":");
		str.append(getStreamId());
		str.append(",");
		str.append(getSize());
		str.append("/");
		str.append(getAllocationSize());
		str.append("]");

		return str.toString();
	}
}
