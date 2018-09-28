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

package org.filesys.server.filesys;

import java.io.FileNotFoundException;

import org.filesys.server.SrvSession;


/**
 * File Id Interface
 *
 * <p>Optional interface that a DiskInterface driver can implement to provide file id to path conversion.
 *
 * @author gkspencer
 */
public interface FileIdInterface {

    /**
     * Convert a file id to a share relative path
     *
     * @param sess   SrvSession
     * @param tree   TreeConnection
     * @param dirid  int
     * @param fileid int
     * @return String
     * @throws FileNotFoundException File not found
     */
    public String buildPathForFileId(SrvSession sess, TreeConnection tree, int dirid, int fileid)
            throws FileNotFoundException;
}
