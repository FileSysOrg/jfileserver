/*
 * Copyright (C) 2006-2014 Alfresco Software Limited.
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

import java.util.ArrayList;
import java.util.List;

/**
 * Pseudo File List Class
 *
 * <p>
 * Contains a list of pseudo file list entries for a folder.
 *
 * @author gkspencer
 */
public class PseudoFileList {

    // List of pseudo files
    private List<PseudoFile> m_list;

    /**
     * Default constructor
     */
    public PseudoFileList() {
        m_list = new ArrayList<PseudoFile>();
    }

    /**
     * Add a pseudo file to the list
     *
     * @param pfile PseudoFile
     */
    public final void addFile(PseudoFile pfile) {
        m_list.add(pfile);
    }

    /**
     * Return the count of files in the list
     *
     * @return int
     */
    public final int numberOfFiles() {
        return m_list.size();
    }

    /**
     * Returns true if the list is empty
     *
     * @return boolean
     */
    public final boolean isEmpty() {
        return m_list.isEmpty();
    }

    /**
     * Return the file at the specified index in the list
     *
     * @param idx int
     * @return PseudoFile
     */
    public final PseudoFile getFileAt(int idx) {
        if (idx < m_list.size())
            return m_list.get(idx);
        return null;
    }

    /**
     * Search for the specified pseudo file name
     *
     * @param fname         String
     * @param caseSensitive boolean
     * @return PseudoFile or null if it does not exist.
     */
    public final PseudoFile findFile(String fname, boolean caseSensitive) {

        // Check if there are any entries in the list
        if (m_list == null || m_list.size() == 0)
            return null;

        // Search for the name match
        for (PseudoFile pfile : m_list) {
            if (caseSensitive && pfile.getFileName().equals(fname))
                return pfile;
            else if (pfile.getFileName().equalsIgnoreCase(fname))
                return pfile;
        }

        // File not found
        return null;
    }

    /**
     * Remove the specified pseudo file from the list
     *
     * @param fname         String
     * @param caseSensitive boolean
     * @return PseudoFile
     */
    public final PseudoFile removeFile(String fname, boolean caseSensitive) {

        // Check if there are any entries in the list
        if (m_list == null || m_list.size() == 0)
            return null;

        // Search for the name match
        for (int idx = 0; idx < m_list.size(); idx++) {

            // Get the current pseudo file
            PseudoFile pfile = m_list.get(idx);
            boolean match = false;

            if (caseSensitive && pfile.getFileName().equals(fname))
                match = true;
            else if (pfile.getFileName().equalsIgnoreCase(fname))
                match = true;

            // If we found the matching pseudo file remove it from the list
            if (match) {
                m_list.remove(idx);
                return pfile;
            }
        }

        // File not found
        return null;
    }
}
