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
public class FileSegmentInfo extends SegmentInfo {

    //	Temporary file path
    private String m_tempFile;

    /**
     * Default constructor
     */
    public FileSegmentInfo() {
        super();
    }

    /**
     * Class constructor
     *
     * @param tempFile String
     */
    public FileSegmentInfo(String tempFile) {
        super();

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
     * Return the temporary file length
     *
     * @return long
     * @throws IOException Failed to get the file length
     */
    public long getFileLength()
            throws IOException {

        //	Get the file length
        File tempFile = new File(getTemporaryFile());
        return tempFile.length();
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
     * Return the file segment details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[File:");
        str.append(getTemporaryFile());
        str.append(":");
        str.append(hasStatus().name());
        str.append(",");

        if (isUpdated())
            str.append(",Updated");
        if (isQueued())
            str.append(",Queued");

        str.append("]");

        return str.toString();
    }
}
