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

package org.filesys.smb.server.disk.original;

import org.filesys.server.filesys.FileAttribute;
import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.FileName;
import org.filesys.server.filesys.SearchContext;
import org.filesys.smb.server.disk.JavaNIODiskDriver;
import org.filesys.util.WildCard;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Java File Search Context Class
 *
 * @author gkspencer
 */
public class JavaFileSearchContext extends SearchContext {

    //	Directory that we are searching
    private File m_root;

    //	List of files
    private String[] m_list;
    private int m_idx;

    //	File attributes
    private int m_attr;

    //	Single file/directory search flag
    private boolean m_single;

    //	Wildcard checker
    private WildCard m_wildcard;

    // Relative path to folder being searched
    private String m_relPath;

    /**
     * Class constructor
     */
    protected JavaFileSearchContext() {
    }

    /**
     * Return the list of file names for a directory.
     *
     * @param file File
     * @return String[]
     */
    protected final String[] getListForDirectory(File file) {

        //  Check if the file is a directory
        if (isDirectory(file) == false)
            return null;

        //  Get the file list for the directory
        String[] fileList = file.list();
        if (fileList == null) {

            //  Try and get the file list another way
            File file2 = new File(file.getPath().substring(0, file.getPath().length() - 1));
            fileList = file2.list();
        }

        //  Return the file list
        return fileList;
    }

    /**
     * Return the resume id for the current file/directory. We return the index of the next file,
     * this is the file/directory that will be returned if the search is restarted.
     *
     * @return int  Resume id.
     */
    public int getResumeId() {
        return m_idx;
    }

    /**
     * Determine if there are more files to return for this search
     *
     * @return boolean
     */
    public boolean hasMoreFiles() {

        //  Determine if there are any more files to be returned
        if (m_single == true && m_idx > 0)
            return false;
        else if (m_list != null && m_idx >= m_list.length)
            return false;
        return true;
    }

    /**
     * Start a directory search.
     *
     * @param path String
     * @param attr int
     * @exception FileNotFoundException File not found
     */
    public final void initSearch(String path, int attr)
            throws FileNotFoundException {

        //  Store the search attributes
        m_attr = attr;

        //  Split the path, check if there is a filename
        String[] pathStr = FileName.splitPath(path, File.separatorChar);

        //  Set the search string for the context
        if (pathStr[1] != null)
            setSearchString(pathStr[1]);

        //  Create the root file
        if (pathStr[1] != null
                && WildCard.containsWildcards(pathStr[1]) == false) {

            //  Indicate that the search is for a single file/directory
            setSingleFileSearch(true);

            //  Path may be a file
            m_root = new File(pathStr[0], pathStr[1]);
            if (m_root.exists() == false) {

                //  Rebuild the path, looks like it is a directory
                m_root = new File(FileName.buildPath(pathStr[0], pathStr[1], null, File.separatorChar));
                if (m_root.exists() == false)
                    throw new java.io.FileNotFoundException(path);
            }
        }
        else {

            //  Wildcard search of a directory
            String root = pathStr[0];
            if (root.endsWith(":"))
                root = root + File.separator;

            m_root = new File(root);

            if (isDirectory(m_root)) {

                //  Check if there is a file spec, if not then the search is for the directory only
                if (pathStr[1] == null) {

                    //  Single file directory search
                    setSingleFileSearch(true);
                }
                else {

                    //  Multi file search, get the file list for the directory
                    m_list = getListForDirectory(m_root);

                    //  If there is not file list, the path does not exist
                    if (m_list == null)
                        throw new java.io.FileNotFoundException(path);

                    //  Indicate a multi-file search
                    setSingleFileSearch(false);

                    //	Create the wildcard checker
                    m_wildcard = new WildCard(pathStr[1], false);
                }
            }
        }

        //  Clear the current file index
        m_idx = 0;
    }

    /**
     * Test if the specified file is a file or directory.
     *
     * @param file java.io.File
     * @return boolean
     */
    protected final boolean isDirectory(File file) {

        //  If the file object says it is a directory then it's a directory !
        if (file.isDirectory())
            return true;

        //  If we can produce a file list then the file is a directory
        if (file.list() != null)
            return true;

        //  Looks like it's a file then !
        return false;
    }

    /**
     * Determine if this is a wildcard or single file/directory type search.
     *
     * @return boolean
     */
    protected final boolean isSingleFileSearch() {
        return m_single;
    }

    /**
     * Determine if the search is valid. The directory may not exist or the file may not exist for a
     * single file search.
     *
     * @return boolean
     */
    public final boolean isValidSearch() {
        return m_root != null ? true : false;
    }

    /**
     * Return the next file information for this search
     *
     * @param info FileInfo
     * @return boolean
     */
    public boolean nextFileInfo(FileInfo info) {

        //  Get the next file information
        boolean infoValid = false;

        if (isSingleFileSearch()) {

            //  Check if we have already returned the root file details
            if (m_idx == 0) {

                //  Update the file index, indicates that we have returned the single file/directory
                //  details.
                m_idx++;

                //  Determine if the search is for a file or directory
                int fattr = 0;
                long flen = 0L;

                if (isDirectory(m_root))
                    fattr = FileAttribute.Directory;
                else
                    flen = m_root.length();

                //	Check if the file/folder is read-only
                if (m_root.canWrite() == false)
                    fattr += FileAttribute.ReadOnly;

                //  Return the file information
                info.setFileName(m_root.getName());
                info.setSize(flen);
                info.setFileAttributes(fattr);
                info.setFileId(m_root.getAbsolutePath().hashCode());

                long modifyDate = m_root.lastModified();
                info.setModifyDateTime(modifyDate);
                info.setChangeDateTime(modifyDate);

                long dummyCreate = JavaNIODiskDriver.getGlobalCreateDateTime();

                if (dummyCreate > modifyDate)
                    dummyCreate = modifyDate;
                info.setCreationDateTime(dummyCreate);

                //  Indicate that the file information is valid
                infoValid = true;
            }
        }
        else if (m_list != null && m_idx < m_list.length) {

            //  Find a file/directory that matches the search attributes
            boolean foundMatch = false;
            File curFile = new File(m_root, m_list[m_idx++]);

            while (foundMatch == false && curFile != null) {

                //	Check if the file name matches the search pattern
                if (m_wildcard.matchesPattern(curFile.getName()) == true) {

                    //  Check if the file matches the search attributes
                    if (FileAttribute.hasAttribute(m_attr, FileAttribute.Directory) &&
                            isDirectory(curFile)) {

                        //  Found a match
                        foundMatch = true;
                    }
                    else if (curFile.isFile()) {
                        // && SMBFileAttribute.hasAttribute(m_attr,SMBFileAttribute.System) == false) {

                        //  Found a match
                        foundMatch = true;
                    }
                }

                //	Check if we found a match

                if (foundMatch == false) {

                    //  Get the next file from the list
                    if (m_idx < m_list.length)
                        curFile = new File(m_root, m_list[m_idx++]);
                    else
                        curFile = null;
                }
            }

            //  Check if there is a file to return
            if (curFile != null) {

                //  Create a file information object for the file
                int fattr = 0;
                long flen = 0L;

                String fname = curFile.getName();

                if (isDirectory(curFile)) {

                    // Set the directory attribute
                    fattr = FileAttribute.Directory;

                    // Check if the diretory should be hidden
                    if (fname.startsWith("."))
                        fattr += FileAttribute.Hidden;
                }
                else {

                    //	Set the file length
                    flen = curFile.length();

                    //	Check if the file/folder is read-only
                    if (curFile.canWrite() == false)
                        fattr += FileAttribute.ReadOnly;

                    //	Check for common hidden files
                    if (fname.equalsIgnoreCase("Desktop.ini") ||
                            fname.equalsIgnoreCase("Thumbs.db") ||
                            fname.startsWith("."))
                        fattr += FileAttribute.Hidden;
                }

                //  Create the file information object
                info.setFileName(curFile.getName());
                info.setSize(flen);
                info.setFileAttributes(fattr);

                // Build the share relative file path to generate the file id
                StringBuffer relPath = new StringBuffer();
                relPath.append(m_relPath);
                relPath.append(curFile.getName());

                info.setFileId(relPath.toString().hashCode());

                // Set the file timestamps
                long modifyDate = m_root.lastModified();
                info.setModifyDateTime(modifyDate);
                info.setChangeDateTime(modifyDate);

                long dummyCreate = JavaNIODiskDriver.getGlobalCreateDateTime();

                if (dummyCreate > modifyDate)
                    dummyCreate = modifyDate;
                info.setCreationDateTime(dummyCreate);

                //  Indicate that the file information is valid
                infoValid = true;
            }
        }

        //  Return the file information valid state
        return infoValid;
    }

    /**
     * Return the next file name for this search
     *
     * @return String
     */
    public String nextFileName() {

        //  Get the next file name
        if (isDirectory(m_root) == false) {

            //  Check if we have already returned the root file name
            if (m_idx == 0) {

                //  Return the root file name
                m_idx++;
                return m_root.getName();
            }
            else
                return null;
        }

        //  Return the next file name from the list
        else if (m_list != null && m_idx < m_list.length) {

            //	Find the next matching file name
            while (m_idx < m_list.length) {

                //	Check if the current file name matches the search pattern
                String fname = m_list[m_idx++];

                if (m_wildcard.matchesPattern(fname))
                    return fname;
            }
        }

        //  No more file names
        return null;
    }

    /**
     * Restart the search at the specified resume point.
     *
     * @param resumeId Resume point.
     * @return true if the search can be restarted, else false.
     */
    public boolean restartAt(int resumeId) {

        //  Check if the resume point is valid
        if (m_list == null || resumeId >= m_list.length)
            return false;

        //  Reset the current search point
        m_idx = resumeId;
        return true;
    }

    /**
     * Restart the file search at the specified file
     *
     * @param info FileInfo
     * @return boolean
     */
    public boolean restartAt(FileInfo info) {

        //  Check if the file list is valid
        boolean restartOK = false;
        m_idx--;

        if (m_list != null) {

            //  Step backwards through the file list until we find the required restart file
            while (m_idx > 0 && restartOK == false) {

                //  Check if we found the restart file
                if (m_list[m_idx].compareTo(info.getFileName()) == 0)
                    restartOK = true;
                else
                    m_idx--;
            }
        }

        //  Return the restart status
        return restartOK;
    }

    /**
     * Set the wildcard/single file search flag.
     *
     * @param single boolean
     */
    protected final void setSingleFileSearch(boolean single) {
        m_single = single;
    }

    /**
     * Return the total number of file entries for this search if known, else return -1
     *
     * @return int
     */
    public int numberOfEntries() {

        //	Return the count of file entries to be returned by this search
        if (isSingleFileSearch())
            return 1;
        else if (m_list != null)
            return m_list.length;
        else
            return -1;
    }

    /**
     * Set the share relative path to the search folder
     *
     * @param relPath String
     */
    public final void setRelativePath(String relPath) {
        m_relPath = relPath;

        if (m_relPath != null && m_relPath.endsWith(FileName.DOS_SEPERATOR_STR) == false)
            m_relPath = m_relPath + FileName.DOS_SEPERATOR_STR;
    }
}
