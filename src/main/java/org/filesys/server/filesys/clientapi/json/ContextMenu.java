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

import java.util.List;

/**
 * Context Menu Details Class
 *
 * <p>Contains details of the client side context menu to be displayed by the client side application that
 * will trigger server side actions</p>
 *
 * @author gkspencer
 */
public class ContextMenu {

    // Context menu title, description and icon
    private String m_title;
    private String m_description;

    private ActionIcon m_icon;

    // List of sub-menu actions, in the order they will be displayed on the menu
    private List<ServerAction> m_actions;

    /**
     * Class constructor
     *
     * @param title String
     * @param desc String
     * @param icon ActionIcon
     */
    public ContextMenu(String title, String desc, ActionIcon icon) {
        m_title = title;
        m_description = desc;
        m_icon = icon;
    }

    /**
     * Return the menu title
     *
     * @return String
     */
    public final String getTitle() { return m_title; }

    /**
     * Return the menu description
     *
     * @return String
     */
    public final String getDescription() { return m_description; }

    /**
     * Return the menu icon details
     *
     * @return ActionIcon
     */
    public final ActionIcon getIcon() { return m_icon; }

    /**
     * Return the list of server actions
     *
     * @return List&lt;ServerAction&gt;
     */
    public final List<ServerAction> getActions() { return m_actions; }

    /**
     * Set the list of server actions
     *
     * @param actions List&lt;ServerAction&gt;
     */
    public final void setActions(List<ServerAction> actions) { m_actions = actions; }

    /**
     * Return the context menu details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("[CtxMenu title=");
        sb.append( getTitle());
        sb.append(", description=");
        sb.append( getDescription());
        sb.append(", icon=");
        sb.append( getIcon());
        sb.append(", actions=");
        sb.append( getActions());
        sb.append("]");

        return sb.toString();
    }
}
