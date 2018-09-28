/*
 * Copyright (C) 2006-2011 Alfresco Software Limited.
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

/**
 * Pseudo file information class
 *
 * @author gkspencer
 */
public class PseudoFileInfo extends FileInfo {

    /**
     * Construct an SMB file information object.
     *
     * @param fname File name string.
     * @param fsize File size, in bytes.
     * @param fattr File attributes.
     */
    public PseudoFileInfo(String fname, long fsize, int fattr) {
        super(fname, fsize, fattr);

        setPseudoFile(true);
    }
}
