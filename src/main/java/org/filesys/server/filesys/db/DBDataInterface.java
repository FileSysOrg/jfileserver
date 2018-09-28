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

import java.io.IOException;

import org.filesys.server.filesys.loader.FileSegment;


/**
 * Database Data Interface
 *
 * <p>The database data interface provides methods for loading/saving file data to database fields.
 *
 * @author gkspencer
 */
public interface DBDataInterface {

    /**
     * Return the file data details for the specified file or stream.
     *
     * @param fileId   int
     * @param streamId int
     * @return DBDataDetails
     * @exception DBException Database error
     */
    public DBDataDetails getFileDataDetails(int fileId, int streamId)
            throws DBException;

    /**
     * Return the maximum data fragment size supported
     *
     * @return long
     */
    public long getMaximumFragmentSize();

    /**
     * Load file data from the database into a temporary/local file
     *
     * @param fileId   int
     * @param streamId int
     * @param fileSeg  FileSegment
     * @exception DBException Database error
     * @exception IOException I/O error
     */
    public void loadFileData(int fileId, int streamId, FileSegment fileSeg)
            throws DBException, IOException;

    /**
     * Load Jar file data from the database into a temporary file
     *
     * @param jarId  int
     * @param jarSeg FileSegment
     * @exception DBException Database error
     * @exception IOException I/O error
     */
    public void loadJarData(int jarId, FileSegment jarSeg)
            throws DBException, IOException;

    /**
     * Save the file data from the temporary/local file to the database
     *
     * @param fileId   int
     * @param streamId int
     * @param fileSeg  FileSegment
     * @return int
     * @exception DBException Database error
     * @exception IOException I/O error
     */
    public int saveFileData(int fileId, int streamId, FileSegment fileSeg)
            throws DBException, IOException;

    /**
     * Save the file data from a Jar file to the database
     *
     * @param jarFile  String
     * @param fileList DBDataDetailsList
     * @return int
     * @exception DBException Database error
     * @exception IOException I/O error
     */
    public int saveJarData(String jarFile, DBDataDetailsList fileList)
            throws DBException, IOException;

    /**
     * Delete the file data for the specified file/stream
     *
     * @param fileId   int
     * @param streamId int
     * @exception DBException Database error
     * @exception IOException I/O error
     */
    public void deleteFileData(int fileId, int streamId)
            throws DBException, IOException;

    /**
     * Delete the file data for the specified Jar file
     *
     * @param jarId int
     * @exception DBException Database error
     * @exception IOException I/O error
     */
    public void deleteJarData(int jarId)
            throws DBException, IOException;
}
