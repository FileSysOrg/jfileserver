/*
 * Copyright (C) 2020 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */
package org.filesys.server.filesys.loader;

import java.io.IOException;

/**
 * Segment Base Class
 *
 * <p>Base class for segment implementations</p>
 *
 * @author gkspencer
 */
public abstract class Segment {

    //	Shared segment details
    private SegmentInfo m_info;

    //	Open file for write access
    private boolean m_writeable;

    /**
     * Class constructor
     *
     * <p>Create a file segment to hold all data for a file.
     *
     * @param info      FileSegmentInfo
     * @param writeable boolean
     */
    public Segment(SegmentInfo info, boolean writeable) {
        m_info = info;
        m_writeable = writeable;
    }

    /**
     * Return the temporary file length, or -1 if the file is not open
     *
     * @return long
     * @throws IOException Failed to get the file length
     */
    public abstract long getFileLength()
        throws IOException;

    /**
     * Return the segment information
     *
     * @return SegmentInfo
     */
    public final SegmentInfo getInfo() {
        return m_info;
    }

    /**
     * Return the readable file data length
     *
     * @return long
     */
    public final long getReadableLength() {
        return m_info.getReadableLength();
    }

    /**
     * Check if the file data is loaded or queued for loading
     *
     * @return boolean
     */
    public final boolean isDataLoading() {
        if (m_info.hasStatus() == FileSegmentInfo.State.Initial &&
                m_info.isQueued() == false)
            return false;
        return true;
    }

    /**
     * Check if the file data is available
     *
     * @return boolean
     */
    public final boolean isDataAvailable() {
        if (m_info.hasStatus().ordinal() >= FileSegmentInfo.State.Available.ordinal() &&
                m_info.hasStatus().ordinal() < FileSegmentInfo.State.Error.ordinal())
            return true;
        return false;
    }

    /**
     * Return the segment status
     *
     * @return State
     */
    public final FileSegmentInfo.State hasStatus() {
        return m_info.hasStatus();
    }

    /**
     * Check if the file load had an error
     *
     * @return boolean
     */
    public final boolean hasLoadError() {
        return m_info.hasStatus() == FileSegmentInfo.State.Error;
    }

    /**
     * Set the readable data length for the file, used during data loading to allow the file to be read before
     * the file load completes.
     *
     * @param readable long
     */
    public final void setReadableLength(long readable) {
        m_info.setReadableLength(readable);
    }

    /**
     * Set the segment load/update status
     *
     * @param sts State
     */
    public final void setStatus(FileSegmentInfo.State sts) {
        m_info.setStatus(sts);
    }

    /**
     * Set the segment load/update status and queued status
     *
     * @param sts    State
     * @param queued boolean
     */
    public final synchronized void setStatus(FileSegmentInfo.State sts, boolean queued) {
        m_info.setStatus(sts);
        m_info.setQueued(queued);
    }

    /**
     * Check if the temporary file is open
     *
     * @return boolean
     */
    public abstract boolean isOpen();

    /**
     * Check if the file segment has been updated
     *
     * @return boolean
     */
    public final boolean isUpdated() {
        return m_info.isUpdated();
    }

    /**
     * Check if the file segment has a file request queued
     *
     * @return boolean
     */
    public final boolean isQueued() {
        return m_info.isQueued();
    }

    /**
     * Check if a save request is queued for this file segment
     *
     * @return boolean
     */
    public final synchronized boolean isSaveQueued() {
        if (m_info.isQueued() && m_info.hasStatus() == FileSegmentInfo.State.SaveWait)
            return true;
        return false;
    }

    /**
     * Check if the file segment is being saved
     *
     * @return boolean
     */
    public final synchronized boolean isSaving() {
        if (m_info.isQueued() && m_info.hasStatus() == FileSegmentInfo.State.Saving)
            return true;
        return false;
    }

    /**
     * Check if the file segment is being loaded
     *
     * @return boolean
     */
    public final synchronized boolean isLoading() {
        if (m_info.isQueued() && m_info.hasStatus() == FileSegmentInfo.State.Loading)
            return true;
        return false;
    }

    /**
     * Check if the file is writeable
     *
     * @return boolean
     */
    public final boolean isWriteable() {
        return m_writeable;
    }

    /**
     * Get the load lock for this file. If successful the current thread will proceed and can load the file, else
     * the thread will wait until the load has been completed by the thread with the lock.
     *
     * @return boolean        true if the current thread has the load lock, else false to indicate that the file
     * should now be loaded.
     * @throws InterruptedException Error during wait
     */
    public final synchronized boolean getLoadLock()
            throws InterruptedException {

        //	Check if the file is currently being loaded
        boolean sts = false;

        if (isLoading() == true) {

            //	Wait until the file has been loaded by another thread
            wait();
        } else {

            //	Set the file status to loading
            setStatus(FileSegmentInfo.State.Loading);
            sts = true;
        }

        //	Return the lock status
        return sts;
    }

    /**
     * Wait for another thread to load the file data
     *
     * @param fileOff long
     * @param len int
     * @param tmo long
     */
    public final void waitForData(long tmo, long fileOff, int len) {
        m_info.waitForData(tmo, fileOff, len);
    }

    /**
     * Signal that the file data is available, any threads using the waitForData() method
     * will return so that the threads can access the file data.
     */
    public final void signalDataAvailable() {
        m_info.signalDataAvailable();
    }

    /**
     * Return the file segment details as a string
     *
     * @return String
     */
    public String toString() {
        return getInfo().toString();
    }
}
