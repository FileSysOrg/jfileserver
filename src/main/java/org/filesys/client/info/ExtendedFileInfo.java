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
 * Extended File Information Class
 * 
 * <p>Adds extra file information available under NT to the standard file information, such as compression
 * details and extended attributes.
 * 
 * @author gkspencer
 */
public class ExtendedFileInfo extends FileInfo {

	//	Extended attributes size and raw data
	
	private int m_eaSize = -1;
	private byte[] m_eadata;
	
	//	Compression details
	
	private long m_compSize = -1L;
	private int m_compFormat = -1;
	
	//	NTFS Stream list
	
	private StreamInfoList m_streams;

	//	Various flags
	
	private boolean m_deletePending;
	private int m_links;

	/**
	 * Default constructor
	 */
	public ExtendedFileInfo() {
		super();
	}

	/**
	 * Class constructor
	 * 
	 * @param name String
	 * @param size long
	 * @param attr int
	 */
	public ExtendedFileInfo(String name, long size, int attr) {
		super(name, size, attr);
	}

	/**
	 * Construct an SMB file information object.
	 * 
	 * @param fname File name string.
	 * @param fsize File size, in bytes.
	 * @param fattr File attributes.
	 * @param fdate SMB encoded file date.
	 * @param ftime SMB encoded file time.
	 */
	public ExtendedFileInfo(String fname, long fsize, int fattr, int fdate, int ftime) {
		super(fname, fsize, fattr, fdate, ftime);
	}

	/**
	 * Return the extended attributes size
	 * 
	 * @return int
	 */
	public final int getExtendedAttributesSize() {
		return m_eaSize;
	}

	/**
	 * Check if the extended attribute raw data is available
	 * 
	 * @return boolean
	 */
	public final boolean hasExtendedAttributeData() {
		return m_eadata != null ? true : false;
	}

	/**
	 * Return the extended attribute raw data block
	 * 
	 * @return byte[]
	 */
	public final byte[] getExtendedAttrbuteData() {
		return m_eadata;
	}

	/**
	 * Return the compressed file size
	 * 
	 * @return long
	 */
	public final long getCompressedSize() {
		return m_compSize;
	}

	/**
	 * Return the compression format
	 * 
	 * @return int
	 */
	public final int getCompressionFormat() {
		return m_compFormat;
	}

	/**
	 * Check if the streams list is valid
	 * 
	 * @return boolean
	 */
	public final boolean hasNTFSStreams() {
		return m_streams != null ? true : false;
	}

	/**
	 * Return the NTFS stream count
	 * 
	 * @return int
	 */
	public final int numberOfNTFSStreams() {
		return m_streams != null ? m_streams.numberOfStreams() : 0;
	}

	/**
	 * Return the NTFS streams list
	 * 
	 * @return StreamInfoList
	 */
	public final StreamInfoList getNTFSStreams() {
		return m_streams;
	}

	/**
	 * Check if a file delete is pending for this file
	 * 
	 * @return boolean
	 */
	public final boolean hasDeletePending() {
		return m_deletePending;
	}

	/**
	 * Return the link count
	 * 
	 * @return int
	 */
	public final int getLinkCount() {
		return m_links;
	}

	/**
	 * Set the extended attribute size
	 * 
	 * @param siz int
	 */
	public final void setExtendedAttributesSize(int siz) {
		m_eaSize = siz;
	}

	/**
	 * Set the extended attribute raw data
	 * 
	 * @param eadata byte[]
	 */
	public final void setExtendedAttributeData(byte[] eadata) {
		m_eadata = eadata;
	}

	/**
	 * Set the compressed file size and compression format
	 * 
	 * @param siz long
	 * @param fmt int
	 */
	public final void setCompressedSizeFormat(long siz, int fmt) {
		m_compSize = siz;
		m_compFormat = fmt;
	}

	/**
	 * Add the details of an NTFS stream to the streams list for this file
	 * 
	 * @param stream StreamInfo
	 */
	public final void addNTFSStreamInfo(StreamInfo stream) {

		// Check if the stream list is valid

		if ( m_streams == null)
			m_streams = new StreamInfoList();

		// Add the stream

		m_streams.addStream(stream);
	}

	/**
	 * Set the delete pending flag
	 * 
	 * @param del boolean
	 */
	public final void setDeletePending(boolean del) {
		m_deletePending = del;
	}

	/**
	 * Set the link count
	 * 
	 * @param cnt int
	 */
	public final void setLinkCount(int cnt) {
		m_links = cnt;
	}
}
