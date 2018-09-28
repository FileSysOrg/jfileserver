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
 * File Processor List Class
 *
 * @author gkspencer
 */
public class FileProcessorList {

    //	File processor list
    private List<FileProcessor> m_list;

    /**
     * Default constructor
     */
    public FileProcessorList() {
        m_list = new ArrayList<FileProcessor>();
    }

    /**
     * Add a file processor to the list
     *
     * @param proc FileProcessor
     */
    public final void addProcessor(FileProcessor proc) {
        m_list.add(proc);
    }

    /**
     * Return the number of file processors in the list
     *
     * @return int
     */
    public final int numberOfProcessors() {
        return m_list.size();
    }

    /**
     * Return the required file processor
     *
     * @param idx int
     * @return FileProcessor
     */
    public final FileProcessor getProcessorAt(int idx) {

        //	Check the index
        if (idx < 0 || idx >= m_list.size())
            return null;

        //	Return the required file processor
        return m_list.get(idx);
    }

    /**
     * Remove a file processor from the list
     *
     * @param idx int
     * @return FileProcessor
     */
    public final FileProcessor removeProcessorAt(int idx) {

        //	Check the index
        if (idx < 0 || idx >= m_list.size())
            return null;

        //	Remove the required file processor
        return m_list.remove(idx);
    }

    /**
     * Remove all file processors from the list
     */
    public final void removeAllProcessors() {
        m_list.clear();
    }
}
