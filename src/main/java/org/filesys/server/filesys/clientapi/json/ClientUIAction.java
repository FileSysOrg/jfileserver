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
 * Client UI Action Class
 *
 * <p>Defines a UI action that the client side will perform after a context menu action is selected to pre-process
 * the selected files/folders, confirm the action or some other user interaction before the server action is called</p>
 *
 * @author gkspencer
 */
public class ClientUIAction {

    /**
     * UI Actions Enum Class
     */
    public enum UIAction {
        MessageDialog,      // Message dialog with severity icon
        YesNoDialog,        // display a Yes/No message box dialog
        OkCancelDialog,     // display an Ok/Cancel message box dialog
        CheckInDialog       // display a check in files dialog
    }

    // UI action and parameters
    @SerializedName( value = "action")
    private UIAction m_action;

    @SerializedName( value = "title")
    private String m_title;

    @SerializedName( value = "text")
    private String m_text;

    @SerializedName( value = "message_level")
    private ServerAction.MessageLevel m_msgLevel = ServerAction.MessageLevel.Info;

    /**
     * Class constructor
     *
     * @param action UIAction
     */
    public ClientUIAction( UIAction action) {
        m_action = action;
    }

    /**
     * Class constructor
     *
     * @param action UIAction
     * @param title String
     * @param text String
     */
    public ClientUIAction( UIAction action, String title, String text) {
        m_action = action;
        m_title = title;
        m_text = text;
    }

    /**
     * Class constructor
     *
     * @param action UIAction
     * @param title String
     * @param text String
     * @param msgLevel ServerAction.MessageLevel
     */
    public ClientUIAction( UIAction action, String title, String text, ServerAction.MessageLevel msgLevel) {
        m_action = action;
        m_title = title;
        m_text = text;
        m_msgLevel = msgLevel;
    }

    /**
     * Return the UI action
     *
     * @return UIAction
     */
    public final UIAction getAction() { return m_action; }

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
}
