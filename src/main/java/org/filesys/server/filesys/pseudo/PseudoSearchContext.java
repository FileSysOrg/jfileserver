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

import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.SearchContext;

/**
 * Pseudo File Search Context Class
 *
 * <p>
 * Search context implementation that blends a list of pseudo files/folders into a folder search.
 *
 * @author gkspencer
 */
public abstract class PseudoSearchContext extends SearchContext {

    // Index of current file being returned
    protected int m_index;

    // Pseudo file list blended into a wildcard folder search
    private PseudoFileList m_pseudoList;

    private boolean m_donePseudoFiles = false;

    // Resume id
    private int m_resumeId;

    // Relative path being searched
    private String m_relPath;

    /**
     * Class constructor
     *
     * @param relPath String
     */
    public PseudoSearchContext(String relPath) {
        m_relPath = relPath;
    }

    /**
     * Check if there are pseudo files to be blended into the search results
     *
     * @return boolean
     */
    public final boolean hasPseudoFiles() {
        if (m_pseudoList != null && m_pseudoList.numberOfFiles() > 0)
            return true;
        return false;
    }

    /**
     * Set the pseudo file list for the search
     *
     * @param pseudoList PseudoFileList
     */
    public final void setPseudoFileList(PseudoFileList pseudoList) {
        m_pseudoList = pseudoList;
    }

    /**
     * Return the resume id for the current file/directory in the search.
     *
     * @return int
     */
    public int getResumeId() {
        return m_resumeId;
    }

    /**
     * Determine if there are more files for the active search.
     *
     * @return boolean
     */
    public boolean hasMorePseudoFiles() {

        // Pseudo files are returned first
        if (m_donePseudoFiles == false && m_pseudoList != null && m_index < (m_pseudoList.numberOfFiles() - 1))
            return true;
        return false;
    }

    /**
     * Return file information for the next pseudo file in the active search. Returns false if there are no
     * more pseudo files/folders to be returned
     *
     * @param info FileInfo to return the file information.
     * @return true if the file information is valid, else false
     */
    public boolean nextPseudoFileInfo(FileInfo info) {

        // Check if there is anything else to return
        if (hasMorePseudoFiles() == false)
            return false;

        // Increment the index and resume id
        m_index++;
        m_resumeId++;

        // If the pseudo file list is valid return the pseudo files first
        if (m_donePseudoFiles == false && m_pseudoList != null) {

            if (m_index < m_pseudoList.numberOfFiles()) {

                PseudoFile pfile = m_pseudoList.getFileAt(m_index);
                if (pfile != null) {

                    // Get the file information for the pseudo file
                    FileInfo pinfo = pfile.getFileInfo();

                    // Copy the file information to the callers file info
                    info.copyFrom(pinfo);

                    // Generate a file id for the current file
                    if (info != null && info.getFileId() == -1) {
                        StringBuilder pathStr = new StringBuilder(m_relPath);
                        pathStr.append(info.getFileName());

                        info.setFileId(pathStr.toString().hashCode());
                    }

                    // Check if we have finished with the pseudo file list, switch to the normal file list
                    if (m_index == (m_pseudoList.numberOfFiles() - 1)) {

                        // Switch to the main file list
                        m_donePseudoFiles = true;
                        m_index = -1;
                    }

                    // Indicate that the file information is valid
                    return true;
                }
            }
        }

        // File information is not valid, end of pseudo file list
        return false;
    }

    /**
     * Return the file name of the next pseudo file in the active search. Returns null is there are no
     * more pseudo file names to return
     *
     * @return String
     */
    public String nextPseudoFileName() {

        // Check if there is anything else to return
        if (hasMorePseudoFiles() == false)
            return null;

        // Increment the index and resume id
        m_index++;
        m_resumeId++;

        // If the pseudo file list is valid return the pseudo files first
        if (m_donePseudoFiles == false && m_pseudoList != null) {

            if (m_index < m_pseudoList.numberOfFiles()) {

                PseudoFile pfile = m_pseudoList.getFileAt(m_index);
                if (pfile != null) {

                    // Get the file information for the pseudo file
                    FileInfo pinfo = pfile.getFileInfo();

                    // Copy the file information to the callers file info
                    return pinfo.getFileName();
                }
            } else {

                // Switch to the main file list
                m_donePseudoFiles = true;
                m_index = -1;
            }
        }

        // No more pseudo files
        return null;
    }

    /**
     * Restart a search at the specified pseudo file resume point.
     *
     * @param info FileInfo
     * @return true if the search can be restarted, else false.
     */
    public boolean restartAtPseudoFile(FileInfo info) {

        // Check if the resume point is in the pseudo file list
        int resId = 0;

        if (m_pseudoList != null) {

            while (resId < m_pseudoList.numberOfFiles()) {

                // Check if the current pseudo file matches the resume file name
                PseudoFile pfile = m_pseudoList.getFileAt(resId);
                if (pfile.getFileName().equals(info.getFileName())) {

                    // Found the restart point
                    m_donePseudoFiles = false;
                    m_index = resId - 1;

                    return true;
                } else
                    resId++;
            }
        }

        // Failed to find resume file
        return false;
    }

    /**
     * Restart the current search at the specified pseudo file.
     *
     * @param resumeId int
     * @return true if the search can be restarted, else false.
     */
    public boolean restartAtPseudoFile(int resumeId) {

        // Check if the resume point is in the pseudo file list
        if (m_pseudoList != null) {

            if (resumeId < m_pseudoList.numberOfFiles()) {

                // Resume at a pseudo file
                m_index = resumeId;
                m_donePseudoFiles = false;

                return true;
            }
        }

        // Invalid pseudo file resume point
        return false;
    }
}
