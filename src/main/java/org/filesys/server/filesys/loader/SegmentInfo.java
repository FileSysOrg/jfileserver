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

import org.filesys.debug.Debug;

import java.io.IOException;
import java.util.EnumSet;

/**
 * Segment Info Class
 *
 * <p>Base class for file data segment information classes.
 *
 * @author gkspencer
 */
public abstract class SegmentInfo {

    //	Segment load/save states
    public enum State {
        Initial,
        LoadWait,
        Loading,
        Available,
        SaveWait,
        Saving,
        Saved,
        Error
    }

    //	Flags
    public enum Flags {
        Updated,        // file data has been updated
        RequestQueued,  // file load/save request has been queued
        DeleteOnSave,   // delete the local copy after storing
        AllData,        // segment contains all the file data
        Streamed,       // file data is being streamed from/to the back-end store
        ReadError,      // error during file data loading
        WriteError,     // error during file data save
        FileClosed,     // file has been closed
        DeleteFromStore,// delete the file from the store
        RenameOnStore   // rename the file after saving to the store
    }

    //	Flags to indicate if this segment has been updated, queued
    private volatile EnumSet<Flags> m_flags = EnumSet.noneOf( Flags.class);

    //	Segment status
    private volatile State m_status = State.Initial;

    //  Amount of valid data in the file, used to allow reads during data loading
    private volatile long m_readable = -1;

    /**
     * Default constructor
     */
    public SegmentInfo() {
    }

    /**
     * Class constructor
     *
     * @param flags EnumSet&lt;Flags&gt;
     */
    public SegmentInfo(EnumSet<Flags> flags) {
        m_flags = flags;
    }

    /**
     * Check if the segment has been updated
     *
     * @return boolean
     */
    public final boolean isUpdated() {
        return m_flags.contains( Flags.Updated);
    }

    /**
     * Check if the segment has a file request queued
     *
     * @return boolean
     */
    public final boolean isQueued() {
        return m_flags.contains( Flags.RequestQueued);
    }

    /**
     * Check if the segment contains all the file data
     *
     * @return boolean
     */
    public final boolean isAllFileData() {
        return m_flags.contains( Flags.AllData);
    }

    /**
     * Check if the file data is being streamed to/from the back-end store
     *
     * @return boolean
     */
    public final boolean isStreamed() { return m_flags.contains( Flags.Streamed); }

    /**
     * Check if the file should be deleted from the store during the background save
     *
     * @return boolean
     */
    public final boolean hasDeleteFromStore() { return m_flags.contains( Flags.DeleteFromStore); }

    /**
     * Check if the file should be renamed on the store after the background save
     *
     * @return boolean
     */
    public final boolean hasRenameOnStore() { return m_flags.contains( Flags.RenameOnStore); }

    /**
     * Check if the file data is available
     *
     * @param fileOff long
     * @param len int
     * @return boolean
     */
    public boolean isDataAvailable( long fileOff, int len) {
        if (hasStatus().ordinal() >= org.filesys.server.filesys.loader.FileSegmentInfo.State.Available.ordinal() &&
                hasStatus().ordinal() < org.filesys.server.filesys.loader.FileSegmentInfo.State.Error.ordinal())
            return true;
        return false;
    }

    /**
     * Check if the associated file data should be deleted once the data store has completed successfully.
     *
     * @return boolean
     */
    public final boolean hasDeleteOnSave() {
        return m_flags.contains( Flags.DeleteOnSave);
    }

    /**
     * Return the segment status
     *
     * @return State
     */
    public final State hasStatus() {
        return m_status;
    }

    /**
     * Check for a file load error
     *
     * @return boolean
     */
    public final boolean hasLoadError() {
        return hasStatus() == State.Error && m_flags.contains( Flags.ReadError);
    }

    /**
     * Check for a file save error
     *
     * @return boolean
     */
    public final boolean hasSaveError() {
        return hasStatus() == State.Error && m_flags.contains( Flags.WriteError);
    }

    /**
     * The file has been closed
     *
     * @return boolean
     */
    public final boolean isClosed() { return m_flags.contains( Flags.FileClosed); }

    /**
     * Return the temporary file length
     *
     * @return long
     * @throws IOException Failed to get the file length
     */
    public abstract long getFileLength()
        throws IOException;

    /**
     * Return the readable file data length
     *
     * @return long
     */
    public final long getReadableLength() {
        return m_readable;
    }

    /**
     * Return the segment flags
     *
     * @return EnumSet&lt;Flags&gt;
     */
    public final EnumSet<Flags> getFlags() { return m_flags; }

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
     * @param sts State
     */
    public synchronized final void setStatus(State sts) {
        m_status = sts;
        notifyAll();
    }

    /**
     * Set/clear the updated segment flag
     *
     * @param sts boolean
     */
    public final void setUpdated(boolean sts) {
        setFlag( Flags.Updated, sts);
    }

    /**
     * Set/clear the request queued flag
     *
     * @param qd boolean
     */
    public final void setQueued(boolean qd) {
        setFlag( Flags.RequestQueued, qd);
    }

    /**
     * Set the delete on store flag so that the temporary file is deleted as soon as the data store has completed
     * successfully.
     */
    public final void setDeleteOnSave() {
        setFlag(Flags.DeleteOnSave, true);
    }

    /**
     * Set the delete from store flag to delete the file during a background save
     *
     */
    public final void setDeleteFromStore() { setFlag(Flags.DeleteFromStore, true); }

    /**
     * Reset the delete from store flag
     */
    public final void resetDeleteFromStore() { setFlag(Flags.DeleteFromStore, false); }

    /**
     * Set the rename after save during a background save
     */
    public final void setRenameOnStore() { setFlag(Flags.RenameOnStore, true); }

    /**
     * Reset the rename on store flag
     */
    public final void resetRenameOnStore() { setFlag(Flags.RenameOnStore, false); }

    /**
     * Set/clear the all data flag
     *
     * @param allData boolean
     */
    public final void setAllFileData( boolean allData) {
        setFlag( Flags.AllData, allData);
    }

    /**
     * Set/clear the streamed file data flag
     *
     * @param streamed boolean
     */
    public final void setStreamed( boolean streamed) { setFlag( Flags.Streamed, streamed); }

    /**
     * Set/clear the read error flag
     *
     * @param readErr boolean
     */
    public final void setReadError( boolean readErr) { setFlag( Flags.ReadError, readErr); }

    /**
     * Set/clear the write error flag
     *
     * @param writeErr boolean
     */
    public final void setWriteError( boolean writeErr) { setFlag( Flags.WriteError, writeErr); }

    /**
     * Set/clear the closed flag
     *
     * @param closed boolean
     */
    public final void setFileClosed( boolean closed) { setFlag( Flags.FileClosed, closed); }

    /**
     * Set/clear the specified flag
     *
     * @param flag Flags
     * @param sts  boolean
     */
    protected final synchronized void setFlag(Flags flag, boolean sts) {
        if ( sts)
            m_flags.add( flag);
        else
            m_flags.remove( flag);
    }

    /**
     * Wait for another thread to load the file data
     *
     * @param tmo long
     * @param fileOff long
     * @param len int
     */
    public final void waitForData(long tmo, long fileOff, int len) {

        //	Check if the file data has been loaded, if not then wait
        if (isDataAvailable( fileOff, len) == false) {
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
        StringBuilder str = new StringBuilder();

        str.append("[");
        str.append(hasStatus().name());
        str.append(",");

        str.append(",flags=");
        str.append( m_flags);

        str.append("]");

        return str.toString();
    }
}
