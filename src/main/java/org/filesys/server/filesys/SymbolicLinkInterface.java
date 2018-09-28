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
 * Symbolic Link Interface
 *
 * <p>Optional interface that a filesystem driver can implement to indicate that symbolic links are supported.
 *
 * @author gkspencer
 */
public interface SymbolicLinkInterface {

    /**
     * Determine if symbolic links are enabled
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return boolean
     */
    public boolean hasSymbolicLinksEnabled(SrvSession sess, TreeConnection tree);

    /**
     * Read the link data for a symbolic link
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param path String
     * @return String
     * @throws AccessDeniedException Access denied
     * @throws FileNotFoundException File not found
     */
    public String readSymbolicLink(SrvSession sess, TreeConnection tree, String path)
            throws AccessDeniedException, FileNotFoundException;
}
