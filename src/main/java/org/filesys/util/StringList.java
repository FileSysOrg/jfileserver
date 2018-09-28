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

package org.filesys.util;


import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * String List Class
 *
 * @author gkspencer
 */
public class StringList {

    //	List of strings
    private List<String> m_list;

    /**
     * Default constructor
     */
    public StringList() {
        m_list = new ArrayList<>();
    }

    /**
     * Class constructor
     *
     * @param list Vector
     */
    public StringList(Vector list) {

        //	Allocate the string list
        m_list = new ArrayList<String>();

        //	Copy values to the string list
        for (int i = 0; i < list.size(); i++) {
            Object obj = list.get(i);
            if (obj instanceof String)
                addString((String) obj);
            else
                addString(obj.toString());
        }
    }

    /**
     * Return the number of strings in the list
     *
     * @return int
     */
    public final int numberOfStrings() {
        return m_list.size();
    }

    /**
     * Add a string to the list
     *
     * @param str String
     */
    public final void addString(String str) {
        m_list.add(str);
    }

    /**
     * Return the string at the specified index
     *
     * @param idx int
     * @return String
     */
    public final String getStringAt(int idx) {
        if (idx < 0 || idx >= m_list.size())
            return null;
        return m_list.get(idx);
    }

    /**
     * Check if the list contains the specified string
     *
     * @param str String
     * @return boolean
     */
    public final boolean containsString(String str) {
        return m_list.contains(str);
    }

    /**
     * Return the index of the specified string, or -1 if not in the list
     *
     * @param str String
     * @return int
     */
    public final int findString(String str) {
        return m_list.indexOf(str);
    }

    /**
     * Remove the specified string from the list
     *
     * @param str String
     * @return boolean
     */
    public final boolean removeString(String str) {
        return m_list.remove(str);
    }

    /**
     * Remove the string at the specified index within the list
     *
     * @param idx int
     * @return String
     */
    public final String removeStringAt(int idx) {
        if (idx < 0 || idx >= m_list.size())
            return null;
        return m_list.remove(idx);
    }

    /**
     * Clear the strings from the list
     */
    public final void remoteAllStrings() {
        m_list.clear();
    }

    /**
     * Return the string list as a string
     *
     * @return String
     */
    public String toString() {

        //	Check if the list is empty
        if (numberOfStrings() == 0)
            return "";

        //	Build the string
        StringBuffer str = new StringBuffer();

        for (int i = 0; i < m_list.size(); i++) {
            str.append(getStringAt(i));
            str.append(",");
        }

        //	Remove the trailing comma
        if (str.length() > 0)
            str.setLength(str.length() - 1);

        //	Return the string
        return str.toString();
    }
}
