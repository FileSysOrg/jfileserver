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

package org.filesys.server.auth.acl;

import java.util.ArrayList;
import java.util.List;

/**
 * Access Control List Class
 *
 * <p>Contains a list of access controls for a shared filesystem.
 *
 * @author gkspencer
 */
public class AccessControlList {

    //	Access control list
    private List<AccessControl> m_list;

    //	Default access level applied when rules return a default status
    private int m_defaultAccess = AccessControl.ReadWrite;

    /**
     * Create an access control list.
     */
    public AccessControlList() {
        m_list = new ArrayList<AccessControl>();
    }

    /**
     * Get the default access level
     *
     * @return int
     */
    public final int getDefaultAccessLevel() {
        return m_defaultAccess;
    }

    /**
     * Set the default access level
     *
     * @param level int
     * @throws InvalidACLTypeException If the access level is invalid
     */
    public final void setDefaultAccessLevel(int level)
            throws InvalidACLTypeException {

        //	Check the default access level
        if (level < AccessControl.NoAccess || level > AccessControl.MaxLevel)
            throw new InvalidACLTypeException();

        //	Set the default access level for the access control list
        m_defaultAccess = level;
    }

    /**
     * Add an access control to the list
     *
     * @param accCtrl AccessControl
     */
    public final void addControl(AccessControl accCtrl) {

        //	Add the access control to the list
        m_list.add(accCtrl);
    }

    /**
     * Return the specified access control
     *
     * @param idx int
     * @return AccessControl
     */
    public final AccessControl getControlAt(int idx) {
        if (idx < 0 || idx >= m_list.size())
            return null;
        return m_list.get(idx);
    }

    /**
     * Return the number of access controls in the list
     *
     * @return int
     */
    public final int numberOfControls() {
        return m_list.size();
    }

    /**
     * Remove all access controls from the list
     */
    public final void removeAllControls() {
        m_list.clear();
    }

    /**
     * Remove the specified access control from the list.
     *
     * @param idx int
     * @return AccessControl
     */
    public final AccessControl removeControl(int idx) {
        if (idx < 0 || idx >= m_list.size())
            return null;
        return m_list.remove(idx);
    }

    /**
     * Return the access control list as a string.
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(m_list.size());
        str.append(":");

        str.append(":");
        str.append(AccessControl.asAccessString(getDefaultAccessLevel()));
        str.append(":");

        for (int i = 0; i < m_list.size(); i++) {
            AccessControl ctrl = m_list.get(i);
            str.append(ctrl.toString());
            str.append(",");
        }
        str.append("]");

        return str.toString();
    }
}
