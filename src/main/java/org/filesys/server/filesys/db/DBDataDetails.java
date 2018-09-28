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

package org.filesys.server.filesys.db;

/**
 * Database Data Details Class
 *
 * <p>Contains the details of the data records that hold the data for a file or stream.
 *
 * @author gkspencer
 */
public class DBDataDetails {

    //	File and stream id
    private int m_fid;
    private int m_stid;

    //	Jar id if the file is stored as part of a Jar file
    private int m_jarId = -1;

    //	Number of file data records and data segment size in bytes
    private int m_numFragments;
    private int m_sizeFragment;

    /**
     * Class constructor
     *
     * @param fileId   int
     * @param streamId int
     */
    public DBDataDetails(int fileId, int streamId) {
        m_fid = fileId;
        m_stid = streamId;
    }

    /**
     * Class constructor
     *
     * @param fileId       int
     * @param streamId     int
     * @param jarId        int
     * @param numFragments int
     * @param sizeFragment int
     */
    public DBDataDetails(int fileId, int streamId, int jarId, int numFragments, int sizeFragment) {
        m_fid = fileId;
        m_stid = streamId;

        m_jarId = jarId;

        m_numFragments = numFragments;
        m_sizeFragment = sizeFragment;
    }

    /**
     * Return the file id
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
     * Determine if the file is stored in a Jar
     *
     * @return boolean
     */
    public final boolean isStoredInJar() {
        return m_jarId != -1 ? true : false;
    }

    /**
     * Return the Jar id
     *
     * @return int
     */
    public final int getJarId() {
        return m_jarId;
    }

    /**
     * Return the number of data fragments for the file data
     *
     * @return int
     */
    public final int numberOfDataFragments() {
        return m_numFragments;
    }

    /**
     * Return the data fragment size
     *
     * @return int
     */
    public final int getDataFragmentSize() {
        return m_sizeFragment;
    }

    /**
     * Set the Jar id, or use -1 if the file is not stored as part of a Jar file
     *
     * @param jarId int
     */
    public final void setJarId(int jarId) {
        m_jarId = jarId;
    }

    /**
     * Set the number of data fragments and fragment size in bytes
     *
     * @param numFrags int
     * @param sizeFrag int
     */
    public final void setDataFragments(int numFrags, int sizeFrag) {
        m_numFragments = numFrags;
        m_sizeFragment = sizeFrag;
    }

    /**
     * Return the data details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer(64);

        str.append("[FID=");
        str.append(getFileId());
        str.append(",STID=");
        str.append(getStreamId());
        str.append(",JarId");
        str.append(getJarId());
        str.append(",Fragments=");
        str.append(numberOfDataFragments());
        str.append(",FragSize=");
        str.append(getDataFragmentSize());
        str.append("]");

        return str.toString();
    }
}
