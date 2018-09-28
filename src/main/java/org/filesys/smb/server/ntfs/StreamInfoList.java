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

package org.filesys.smb.server.ntfs;


import java.util.ArrayList;
import java.util.List;

/**
 * Stream Information List Class
 *
 * @author gkspencer
 */
public class StreamInfoList {

    //	List of stream information objects
    private List<StreamInfo> m_list;

    /**
     * Default constructor
     */
    public StreamInfoList() {
        m_list = new ArrayList<StreamInfo>();
    }

    /**
     * Copy constructor
     *
     * @param sList StreamInfoList
     */
    public StreamInfoList(StreamInfoList sList) {
        m_list = new ArrayList<StreamInfo>(sList.numberOfStreams());

        // Make a shallow copy of the stream information
        for (int idx = 0; idx < sList.numberOfStreams(); idx++)
            m_list.add(sList.getStreamAt(idx));
    }

    /**
     * Add an item to the list
     *
     * @param info StreamInfo
     */
    public final void addStream(StreamInfo info) {
        m_list.add(info);
    }

    /**
     * Return the stream details at the specified index
     *
     * @param idx int
     * @return StreamInfo
     */
    public final StreamInfo getStreamAt(int idx) {

        //	Range check the index
        if (idx < 0 || idx >= m_list.size())
            return null;

        //	Return the required stream information
        return m_list.get(idx);
    }

    /**
     * Find a stream by name
     *
     * @param name String
     * @return StreamInfo
     */
    public final StreamInfo findStream(String name) {

        //	Search for the required stream
        for (int i = 0; i < m_list.size(); i++) {

            //	Get the current stream information
            StreamInfo sinfo = m_list.get(i);

            //	Check if the stream name matches
            if (sinfo.getName().equals(name))
                return sinfo;
        }

        //	Stream not found
        return null;
    }

    /**
     * Return the count of streams in the list
     *
     * @return int
     */
    public final int numberOfStreams() {
        return m_list.size();
    }

    /**
     * Remove the specified stream from the list
     *
     * @param idx int
     * @return StreamInfo
     */
    public final StreamInfo removeStream(int idx) {

        //	Range check the index
        if (idx < 0 || idx >= m_list.size())
            return null;

        //	Remove the required stream
        StreamInfo info = m_list.get(idx);
        m_list.remove(idx);
        return info;
    }

    /**
     * Remove the specified stream from the list
     *
     * @param name String
     * @return StreamInfo
     */
    public final StreamInfo removeStream(String name) {

        //	Search for the required stream
        for (int i = 0; i < m_list.size(); i++) {

            //	Get the current stream information
            StreamInfo sinfo = m_list.get(i);

            //	Check if the stream name matches
            if (sinfo.getName().equals(name)) {

                //	Remove the stream from the list
                m_list.remove(i);
                return sinfo;
            }
        }

        //	Stream not found
        return null;
    }

    /**
     * Remove all streams from the list
     */
    public final void removeAllStreams() {
        m_list.clear();
    }
}
