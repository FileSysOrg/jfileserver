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

import org.filesys.server.filesys.db.BackgroundLoadSave;

/**
 * Background File Loader Interface
 *
 * <p>Provides methods called by the multi-threaded background loader to load/save file data to
 * a temporary file.
 *
 * @author gkspencer
 */
public interface BackgroundFileLoader <T extends FileRequest> {

    /**
     * Load a file
     *
     * @param loadReq FileRequest
     * @return BackgroundLoadSave.Status
     * @throws Exception Error loading file data
     */
    public BackgroundLoadSave.Status loadFile(T loadReq)
            throws Exception;

    /**
     * Store a file
     *
     * @param saveReq FileRequest
     * @return BackgroundLoadSave.Status
     * @throws Exception Error saving file data
     */
    public BackgroundLoadSave.Status storeFile(T saveReq)
            throws Exception;
}
