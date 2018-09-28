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

package org.filesys.server.auth;

import java.util.ArrayList;
import java.util.List;

/**
 * User Account List Class
 *
 * @author gkspencer
 */
public class UserAccountList {

    //	User account list
    private List<UserAccount> m_users;

    /**
     * Create a user account list.
     */
    public UserAccountList() {
        m_users = new ArrayList<UserAccount>();
    }

    /**
     * Add a user to the list of accounts.
     *
     * @param user UserAccount
     */
    public final void addUser(UserAccount user) {

        //	Check if the user exists on the list
        removeUser(user);
        m_users.add(user);
    }

    /**
     * Find the required user account details.
     *
     * @param user String
     * @return UserAccount
     */
    public final UserAccount findUser(String user) {

        //	Search for the specified user account
        for (int i = 0; i < m_users.size(); i++) {
            UserAccount acc = m_users.get(i);
            if (acc.getUserName().equalsIgnoreCase(user))
                return acc;
        }

        //	User not found
        return null;
    }

    /**
     * Determine if the specified user account exists in the list.
     *
     * @param user String
     * @return boolean
     */
    public final boolean hasUser(String user) {

        //	Search for the specified user account
        for (int i = 0; i < m_users.size(); i++) {
            UserAccount acc = m_users.get(i);
            if (acc.getUserName().compareTo(user) == 0)
                return true;
        }

        //	User not found
        return false;
    }

    /**
     * Return the specified user account details
     *
     * @param idx int
     * @return UserAccount
     */
    public final UserAccount getUserAt(int idx) {
        if (idx >= m_users.size())
            return null;
        return m_users.get(idx);
    }

    /**
     * Return the number of defined user accounts.
     *
     * @return int
     */
    public final int numberOfUsers() {
        return m_users.size();
    }

    /**
     * Remove all user accounts from the list.
     */
    public final void removeAllUsers() {
        m_users.clear();
    }

    /**
     * Remvoe the specified user account from the list.
     *
     * @param userAcc UserAccount
     */
    public final void removeUser(UserAccount userAcc) {

        //	Search for the specified user account
        for (int i = 0; i < m_users.size(); i++) {
            UserAccount acc = m_users.get(i);
            if (acc.getUserName().compareTo(userAcc.getUserName()) == 0) {
                m_users.remove(i);
                return;
            }
        }
    }

    /**
     * Remvoe the specified user account from the list.
     *
     * @param user String
     */
    public final void removeUser(String user) {

        //	Search for the specified user account
        for (int i = 0; i < m_users.size(); i++) {
            UserAccount acc = m_users.get(i);
            if (acc.getUserName().compareTo(user) == 0) {
                m_users.remove(i);
                return;
            }
        }
    }

    /**
     * Return the user account list as a string.
     *
     * @return String
     */
    public String toString() {
        StringBuffer str = new StringBuffer();

        str.append("[");
        str.append(m_users.size());
        str.append(":");

        for (int i = 0; i < m_users.size(); i++) {
            UserAccount acc = m_users.get(i);
            str.append(acc.getUserName());
            str.append(",");
        }
        str.append("]");

        return str.toString();
    }
}
