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

import java.util.ArrayList;
import java.util.List;

/**
 * Multiple File Request Class
 *
 * <p>Contains the details of a transaction of multiple file requests.
 *
 * @author gkspencer
 */
public class MultipleFileRequest extends FileRequest {

    //	List of cached files that are part of this transaction
    private List<CachedFileInfo> m_files;

    /**
     * Class constructor
     *
     * @param typ    int
     * @param tranId int
     */
    public MultipleFileRequest(int typ, int tranId) {
        super(typ);
        setTransactionId(tranId);

        //	Allocate the file list
        m_files = new ArrayList<CachedFileInfo>();
    }

    /**
     * Return the number of files in this request
     *
     * @return int
     */
    public final int getNumberOfFiles() {
        return m_files.size();
    }

    /**
     * Get file details for the specified file
     *
     * @param idx int
     * @return CachedFileInfo
     */
    public final CachedFileInfo getFileInfo(int idx) {
        if (idx > m_files.size())
            return null;
        return m_files.get(idx);
    }

    /**
     * Add a file to this request
     *
     * @param finfo CachedFileInfo
     */
    public final void addFileInfo(CachedFileInfo finfo) {
        m_files.add(finfo);
    }

    /**
     * Return the file request as a string
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        if (isTransaction()) {
            str.append("[Tran=");
            str.append(getTransactionId());
        }

        str.append(",Files=");
        str.append(getNumberOfFiles());

        if (hasAttributes()) {
            str.append(",Attr=");
            str.append(getAttributes());
        }

        str.append("]");

        return str.toString();
    }
}
