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

package org.filesys.server.filesys.loader;

import java.io.File;
import java.io.IOException;

import org.filesys.debug.Debug;


/**
 * File Segment Info Class
 *
 * <p>Contains the details of a file segment that may be shared by many users/sessions.
 *
 * @author gkspencer
 */
public class FileSegmentInfo {

    //	Segment load/save status
    public final static int Initial     = 0;
    public final static int LoadWait    = 1;
    public final static int Loading     = 2;
    public final static int Available   = 3;
    public final static int SaveWait    = 4;
    public final static int Saving      = 5;
    public final static int Saved       = 6;

    public final static int Error       = 7;

    //	Flags
    private static final int Updated        = 0x0001;
    private static final int RequestQueued  = 0x0002;
    private static final int DeleteOnStore  = 0x0004;

    //	Segment status strings
    private static final String[] _statusStr = {"Initial", "LoadWait", "Loading", "Available", "SaveWait", "Saving", "Saved", "Error"};

    //	Temporary file path
    private String m_tempFile;

    //	Flags to indicate if this segment has been updated, queued
    private int m_flags;

    //	Segment status
    private int m_status = Initial;

    //  Amount of valid data in the file, used to allow reads during data loading
    private long m_readable;

    /**
     * Default constructor
     */
    public FileSegmentInfo() {
        m_status = Initial;
    }

    /**
     * Class constructor
     *
     * @param tempFile String
     */
    public FileSegmentInfo(String tempFile) {
        m_status = Initial;
        setTemporaryFile(tempFile);
    }

    /**
     * Return the temporary file path
     *
     * @return String
     */
    public final String getTemporaryFile() {
        return m_tempFile;
    }

    /**
     * Check if the segment has been updated
     *
     * @return boolean
     */
    public final boolean isUpdated() {
        return (m_flags & Updated) != 0 ? true : false;
    }

    /**
     * Check if the segment has a file request queued
     *
     * @return boolean
     */
    public final boolean isQueued() {
        return (m_flags & RequestQueued) != 0 ? true : false;
    }

    /**
     * Check if the file data is available
     *
     * @return boolean
     */
    public final boolean isDataAvailable() {
        if (hasStatus() >= FileSegmentInfo.Available &&
                hasStatus() < FileSegmentInfo.Error)
            return true;
        return false;
    }

    /**
     * Check if the associated temporary file should be deleted once the data store
     * has completed successfully.
     *
     * @return boolean
     */
    public final boolean hasDeleteOnStore() {
        return (m_flags & DeleteOnStore) != 0 ? true : false;
    }

    /**
     * Delete the temporary file used by the file segment
     *
     * @throws IOException Failed to delete the temporary file
     */
    public final void deleteTemporaryFile()
            throws IOException {

        //	Delete the temporary file used by the file segment
        File tempFile = new File(getTemporaryFile());

        if (tempFile.exists() && tempFile.delete() == false) {

            //	DEBUG
            Debug.println("** Failed to delete " + toString() + " **");

            //	Throw an exception, delete failed
            throw new IOException("Failed to delete file " + getTemporaryFile());
        }
    }

    /**
     * Return the segment status
     *
     * @return int
     */
    public final int hasStatus() {
        return m_status;
    }

    /**
     * Return the temporary file length
     *
     * @return long
     * @throws IOException Failed to get the file length
     */
    public final long getFileLength()
            throws IOException {

        //	Get the file length
        File tempFile = new File(getTemporaryFile());
        return tempFile.length();
    }

    /**
     * Return the readable file data length
     *
     * @return long
     */
    public final long getReadableLength() {
        return m_readable;
    }

    /**
     * Set the readable data length for the file, used during data loading to allow the file to be read before
     * the file load completes.
     *
     * @param readable long
     */
    public final void setReadableLength(long readable) {
        m_readable = readable;
    }

    /**
     * Set the segment load/update status
     *
     * @param sts int
     */
    public synchronized final void setStatus(int sts) {
        m_status = sts;
        notifyAll();
    }

    /**
     * Set the temporary file that is used to hold the local copy of the file data
     *
     * @param tempFile String
     */
    public final void setTemporaryFile(String tempFile) {
        m_tempFile = tempFile;
    }

    /**
     * Set/clear the updated segment flag
     *
     * @param sts boolean
     */
    public synchronized final void setUpdated(boolean sts) {
        setFlag(Updated, sts);
    }

    /**
     * Set/clear the request queued flag
     *
     * @param qd boolean
     */
    public synchronized final void setQueued(boolean qd) {
        setFlag(RequestQueued, qd);
    }

    /**
     * Set the delete on store flag so that the temporary file is deleted as soon as the
     * data store has completed successfully.
     */
    public final synchronized void setDeleteOnStore() {
        if (hasDeleteOnStore() == false)
            setFlag(DeleteOnStore, true);
    }

    /**
     * Set/clear the specified flag
     *
     * @param flag int
     * @param sts  boolean
     */
    protected final synchronized void setFlag(int flag, boolean sts) {
        boolean state = (m_flags & flag) != 0 ? true : false;
        if (state && sts == false)
            m_flags -= flag;
        else if (state == false && sts == true)
            m_flags += flag;
    }

    /**
     * Wait for another thread to load the file data
     *
     * @param tmo long
     */
    public final void waitForData(long tmo) {

        //	Check if the file data has been loaded, if not then wait
        if (isDataAvailable() == false) {
            synchronized (this) {
                try {

                    //	Wait for file data
                    wait(tmo);
                }
                catch (InterruptedException ex) {
                }
            }
        }
    }

    /**
     * Signal that the file data is available, any threads using the waitForData() method
     * will return so that the threads can access the file data.
     */
    public final synchronized void signalDataAvailable() {

        //	Notify any waiting threads that the file data ia available
        notifyAll();
    }

    /**
     * Return the file segment details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(getTemporaryFile());
        str.append(":");
        str.append(_statusStr[hasStatus()]);
        str.append(",");

        if (isUpdated())
            str.append(",Updated");
        if (isQueued())
            str.append(",Queued");

        str.append("]");

        return str.toString();
    }
}
