/*
 * Copyright (C) 2024 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */
package org.filesys.server.filesys.clientapi.json;

import com.google.gson.annotations.SerializedName;

/**
 * Notification Action Class
 *
 * <p>Client notification action class</p>
 *
 * @author gkspencer
 */
public class NotificationAction {

    @SerializedName( value = "title")
    private String m_title;

    @SerializedName( value = "text")
    private String m_text;

    /**
     * Class constructor
     *
     * @param text String
     */
    public NotificationAction(String text) {
        m_title = "";
        m_text = text;
    }

    /**
     * Class constructor
     *
     * @param title String
     * @param text String
     */
    public NotificationAction(String title, String text) {
        m_title = title;
        m_text = text;
    }

    /**
     * Check if there is an optional UI title
     *
     * @return boolean
     */
    public final boolean hasTitle() { return m_title != null; }

    /**
     * Return the UI title
     *
     * @return String
     */
    public final String getTitle() { return m_title; }

    /**
     * Check if there is optional UI text
     *
     * @return boolean
     */
    public final boolean hasText() { return m_text != null; }

    /**
     * Return the UI text
     *
     * @return String
     */
    public final String getText() { return m_text; }

    /**
     * Return the notify action details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Notify title=");
        str.append(m_title);
        str.append(", text=");
        str.append(m_text);
        str.append("]");

        return str.toString();
    }
}
