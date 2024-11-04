/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 * Copyright (C) 2018 GK Spencer
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

package org.filesys.smb.server.disk;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.Iterator;

import org.filesys.server.filesys.FileAttribute;
import org.filesys.server.filesys.FileInfo;
import org.filesys.server.filesys.FileName;
import org.filesys.server.filesys.SearchContext;
import org.filesys.util.WildCard;

/**
 * Java File Search Context Class
 *
 * @author gkspencer
 */
public class JavaNIOSearchContext extends SearchContext {

    //	Directory that we are searching
    private Path m_root;

    //	Directory stream for scanning a folder
    private DirectoryStream<Path> m_stream;
    private Iterator<Path> m_pathIter;
    private int m_idx;

    // Help speed up a common restartAt(FileInfo info) call pattern
    private FileInfo m_cachedFileInfo;
    private boolean m_restartFromCache;

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
    protected JavaNIOSearchContext() {
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

    private void resetIndex() {
        m_idx = 0;
        m_cachedFileInfo = null;
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
        else if (m_stream == null || m_pathIter == null)
            return false;
        else if ( m_pathIter != null)
            return m_pathIter.hasNext() || m_restartFromCache && m_cachedFileInfo != null;
        return true;
    }

    /**
     * Initialize a single file/folder search
     *
     * @param singlePath Path
     */
    public final void initSinglePathSearch( Path singlePath) {

        // Save the search path
        m_root = singlePath;
        setSearchString( m_root.toString());

        // Indicate this is a single file/folder search
        setSingleFileSearch( true);
        resetIndex();

        m_stream = null;
        m_pathIter = null;
        m_wildcard = null;
        m_attr = 0;
    }

    /**
     * Initialize a wildcard folder search
     *
     * @param folderPath Path
     * @param attr int
     * @param wildCard WildCard
     * @exception IOException I/O error
     */
    public final void initWildcardSearch( Path folderPath, int attr, WildCard wildCard)
        throws IOException {

        // Save the search path, attributes and wild card filter
        m_root = folderPath;
        setSearchString( m_root.toString());
        m_attr = attr;
        m_wildcard = wildCard;

        // Indicate a multi-file search
        setSingleFileSearch( false);
        resetIndex();

        // Start the folder search
        m_stream = Files.newDirectoryStream(m_root);
        m_pathIter = m_stream.iterator();
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

        try {

            if (isSingleFileSearch()) {

                //  Check if we have already returned the root file details
                if (m_idx == 0) {

                    //  Update the file index, indicates that we have returned the single file/directory
                    //  details.
                    m_idx++;

                    //  Determine if the search is for a file or directory
                    int fattr = 0;
                    long flen = 0L;

                    if (Files.isDirectory(m_root, LinkOption.NOFOLLOW_LINKS))
                        fattr = FileAttribute.Directory;
                    else
                        flen = Files.size(m_root);

                    //	Check if the file/folder is read-only
                    if ( Files.isWritable(m_root) == false)
                        fattr += FileAttribute.ReadOnly;

                    // Check if the file/folder is hidden
                    if ( Files.isHidden(m_root))
                        fattr += FileAttribute.Hidden;

                    // If no attributes are set mark as a normal file
                    if  ( fattr == 0)
                        fattr = FileAttribute.NTNormal;

                    //  Return the file information
                    info.setFileName(m_root.getFileName().toString());
                    info.setSize(flen);
                    info.setFileAttributes(fattr);
                    info.setFileId(m_root.toString().hashCode());

                    FileTime modifyDate = Files.getLastModifiedTime(m_root, LinkOption.NOFOLLOW_LINKS);
                    long modifyDateMs = modifyDate.toMillis();

                    info.setModifyDateTime(modifyDateMs);
                    info.setChangeDateTime(modifyDateMs);
                    info.setAccessDateTime(modifyDateMs);

                    long dummyCreate = JavaNIODiskDriver.getGlobalCreateDateTime();

                    if (dummyCreate > modifyDateMs)
                        dummyCreate = modifyDateMs;
                    info.setCreationDateTime(dummyCreate);

                    //  Indicate that the file information is valid
                    infoValid = true;
                }
            }
            else if (m_restartFromCache == true && m_cachedFileInfo != null) {
                m_restartFromCache = false;
                info.copyFrom(m_cachedFileInfo);
                infoValid = true;
            }
            else if (m_pathIter != null && m_pathIter.hasNext()) {

                // Find the next file/folder that matches the search attributes
                boolean foundMatch = false;
                Path curPath = m_pathIter.next();
                m_idx++;

                while( foundMatch == false && curPath != null) {

                    //	Check if the file name matches the search pattern
                    if (m_wildcard.matchesPattern(curPath.getFileName().toString())) {

                        //  Check if the file matches the search attributes
                        if (FileAttribute.hasAttribute(m_attr, FileAttribute.Directory) &&
                                Files.isDirectory( curPath, LinkOption.NOFOLLOW_LINKS)) {

                            //  Found a match
                            foundMatch = true;
                        }
                        else {

                            //  Found a match
                            foundMatch = true;
                        }
                    }

                    //	Check if we found a match
                    if (foundMatch == false) {

                        //  Get the next path, if available
                        if ( m_pathIter.hasNext()) {
                            curPath = m_pathIter.next();
                            m_idx++;
                        }
                        else
                            curPath = null;
                    }
                }

                //  Check if there is a path to return information for
                if (curPath != null) {

                    //  Create a file information object for the file
                    int fattr = 0;
                    long flen = 0L;
                    long falloc = 0L;

                    String fname = curPath.getFileName().toString();

                    if ( Files.isDirectory(curPath, LinkOption.NOFOLLOW_LINKS)) {

                        // Set the directory attribute
                        fattr = FileAttribute.Directory;

                        // Check if the diretory should be hidden
                        if ( Files.isHidden( curPath))
                            fattr += FileAttribute.Hidden;
                    }
                    else {

                        //	Set the file length
                        flen = Files.size( curPath);
                        falloc = (flen + 512L) & 0xFFFFFFFFFFFFFE00L;

                        //	Check if the file/folder is read-only
                        if (Files.isWritable( curPath) == false)
                            fattr += FileAttribute.ReadOnly;

                        //	Check for common hidden files
                        if ( Files.isHidden( curPath))
                            fattr += FileAttribute.Hidden;
                        else if (fname.equalsIgnoreCase("Desktop.ini") ||
                                fname.equalsIgnoreCase("Thumbs.db") ||
                                fname.startsWith("."))
                            fattr += FileAttribute.Hidden;
                    }

                    //  Create the file information object
                    info.setFileName(curPath.getFileName().toString());
                    info.setSize(flen);
                    info.setAllocationSize( falloc);
                    info.setFileAttributes(fattr);

                    // Build the share relative file path to generate the file id
                    StringBuffer relPath = new StringBuffer();
                    relPath.append(m_relPath);
                    relPath.append(curPath.getFileName().toString());

                    info.setFileId(relPath.toString().hashCode());

                    // Set the file timestamps
                    FileTime modifyDate = Files.getLastModifiedTime( curPath, LinkOption.NOFOLLOW_LINKS);
                    long modifyDateMs = modifyDate.toMillis();

                    info.setModifyDateTime(modifyDateMs);
                    info.setChangeDateTime(modifyDateMs);
                    info.setAccessDateTime(modifyDateMs);

                    long dummyCreate = JavaNIODiskDriver.getGlobalCreateDateTime();

                    if (dummyCreate > modifyDateMs)
                        dummyCreate = modifyDateMs;
                    info.setCreationDateTime(dummyCreate);

                    //  Indicate that the file information is valid
                    infoValid = true;

                    m_cachedFileInfo = info;
                }
            }
        }
        catch ( IOException ex) {
            infoValid = false;
            m_cachedFileInfo = null;
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

        // Check for a single path search
        if ( isSingleFileSearch()) {

            if ( m_idx++ == 0)
                return m_root.getFileName().toString();
            else
                return null;
        }
        else if (m_restartFromCache == true && m_cachedFileInfo != null) {
            m_restartFromCache = false;
            return m_cachedFileInfo.getFileName();
        }
        else if ( m_pathIter != null && m_pathIter.hasNext()) {

            // Find the next matching file name
            Path curPath = m_pathIter.next();

            while ( curPath != null) {

                String fName = curPath.getFileName().toString();
                if ( m_wildcard.matchesPattern( fName)) {
                    m_idx++;
                    return fName;
                }
                else
                    curPath = m_pathIter.next();
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
        if (m_stream == null || resumeId > m_idx)
            return false;

        // Restart the iteration and skip to the required position
        try {
            m_stream.close();
            m_stream = Files.newDirectoryStream(m_root);
            m_pathIter = m_stream.iterator();
        }
        catch ( IOException ex) {
            return false;
        }

        resetIndex();

        while ( m_pathIter.hasNext() && m_idx != resumeId) {
            m_pathIter.next();
            m_idx++;
        }

        // Return the resume validity
        return m_pathIter.hasNext();
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

        if (m_stream != null) {
            if (m_cachedFileInfo != null && m_cachedFileInfo == info) {
                // Avoid expensively reiterating the whole directory if we're asked to merely go
                // back to the immediately previously retrieved entry.
                m_restartFromCache = true;
                return true;
            }

            // Restart the stream iterator and find the required restart path
            resetIndex();

            try {
                m_stream.close();
                m_stream = Files.newDirectoryStream(m_root);
                m_pathIter = m_stream.iterator();
            }
            catch ( IOException ex) {
                return false;
            }

            Path curPath = m_pathIter.next();
            m_idx++;

            while ( curPath != null && restartOK == false) {

                if ( curPath.getFileName().toString().equalsIgnoreCase(info.getFileName())) {
                    // Rewind by one, so the next call to nextFileInfo will return the correct item
                    restartOK = restartAt(m_idx - 1);
                }
                else {
                    curPath = m_pathIter.next();
                    m_idx++;
                }
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

    /**
     * Close the search.
     */
    public void closeSearch() {
        if (m_stream != null) {
            try {
                m_stream.close();
            }
            catch (IOException unused) {
            }
        }
        super.closeSearch();
    }

}
