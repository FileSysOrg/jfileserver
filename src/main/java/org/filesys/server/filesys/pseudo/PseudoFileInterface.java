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

package org.filesys.server.filesys.pseudo;

import org.filesys.server.SrvSession;
import org.filesys.server.filesys.TreeConnection;

/**
 * Pseudo File Interface
 *
 * <p>
 * Provides the ability to add files into the file listing of a folder.
 *
 * @author gkspencer
 */
public interface PseudoFileInterface {

    /**
     * Check if the specified path refers to a pseudo file
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param path String
     * @return boolean
     */
    public boolean isPseudoFile(SrvSession sess, TreeConnection tree, String path);

    /**
     * Return the pseudo file for the specified path, or null if the path is not a pseudo file
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param path String
     * @return PseudoFile
     */
    public PseudoFile getPseudoFile(SrvSession sess, TreeConnection tree, String path);

    /**
     * Add pseudo files to a folder so that they appear in a folder search
     * <p>
     * How to access the pseudo files is implementation specific.
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param path String
     * @return int the number of pseudo files
     */
    public int addPseudoFilesToFolder(SrvSession sess, TreeConnection tree, String path);

    /**
     * Delete a pseudo file
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @param path String
     */
    public void deletePseudoFile(SrvSession sess, TreeConnection tree, String path);
}
