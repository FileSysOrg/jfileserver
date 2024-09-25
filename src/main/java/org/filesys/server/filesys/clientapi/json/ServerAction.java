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
import org.filesys.server.filesys.clientapi.ClientAPINetworkFile;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Server Action Class
 *
 * <p>Contains the details of a server side action</p>
 *
 * @author gkspencer
 */
public abstract class ServerAction {

    /**
     * Server Action Flags Enum Class
     *
     * Note: These flags must match the client side action flags
     */
    public enum Flags {
        Files,          // action works on files
        Folders,        // action works on folders
        MultiSelect     // action works on multiple files/folders
    }

    /**
     * Server Action Status Codes Enum Class
     */
    public enum Status {
        Ok,             // success
        Warn,           // warning
        Error           // errors occurred running script
    }

    /**
     * Client Action Enum Class
     *
     * <p>Returned to the client in the server action response</p>
     */
    public enum ClientAction {
        NoAction,       // do nothing, action complete
        OpenURL,        // open a URL, parameters contain the URL
        OpenApplication,// open an application on the client, parameters contain the command line values
        UIAction,       // display a dialog or other UI component
        UpdatedPaths,   // parameters contain a list of paths that were updated by the action
        NewPaths,       // parameters contain a list of paths that were created by the action
        DeletedPaths    // parameters contain a list of paths that were removed by the action
    }

    /**
     * Message Level Enum Class
     *
     * <p>Used when the client action is 'Message' to determine the type of dialog to show</p>
     */
    public enum MessageLevel {
        Info,
        Warn,
        Error,
    }

    // Scripted action details
    @SerializedName( value = "name")
    private String m_name;

    @SerializedName( value = "description")
    private String m_description;

    @SerializedName( value = "flags")
    private EnumSet<ServerAction.Flags> m_flags;

    @SerializedName( value = "ui_action")
    private ClientUIAction m_uiAction;

    @SerializedName( value = "icon")
    private ActionIcon m_icon;

    // Flag to indicate if the server action is enabled
    private transient boolean m_enabled = true;

    /**
     * Class constructor
     *
     * @param flags EnumSet&lt;Flags&gt;
     */
    protected ServerAction(EnumSet<ServerAction.Flags> flags) {
        m_flags = flags;
    }

    /**
     * Class constructor
     *
     * @param name String
     * @param desc String
     * @param flags EnumSet&lt;Flags&gt;
     */
    protected ServerAction(String name, String desc, EnumSet<ServerAction.Flags> flags) {
        m_name = name;
        m_description = desc;
        m_flags = flags;
    }

    /**
     * Class constructor
     *
     * @param name String
     * @param desc String
     * @param flags EnumSet&lt;Flags&gt;
     * @param uiAction ClientUIAction
     */
    protected ServerAction(String name, String desc, EnumSet<ServerAction.Flags> flags, ClientUIAction uiAction) {
        m_name = name;
        m_description = desc;
        m_flags = flags;

        m_uiAction = uiAction;
    }

    /**
     * Class constructor
     *
     * @param name String
     * @param desc String
     * @param flags EnumSet&lt;Flags&gt;
     * @param uiAction ClientUIAction
     * @param icon ActionIcon
     */
    protected ServerAction(String name, String desc, EnumSet<ServerAction.Flags> flags, ClientUIAction uiAction, ActionIcon icon) {
        m_name = name;
        m_description = desc;
        m_flags = flags;

        m_uiAction = uiAction;
        m_icon = icon;
    }

    /**
     * Check if the server action is enabled
     *
     * @return boolean
     */
    public final boolean isEnabled() { return m_enabled; }

    /**
     * Return the action name
     *
     * @return String
     */
    public final String getName() { return m_name; }

    /**
     * Return the action description
     *
     * @return String
     */
    public final String getDescription() { return m_description; }

    /**
     * Return the action flags
     *
     * @return EnumSet&lt;Flags&gt;
     */
    public final EnumSet<ServerAction.Flags> getFlags() { return m_flags; }

    /**
     * Check if there is an optional client UI action
     *
     * @return boolean
     */
    public final boolean hasClientUIAction() { return m_uiAction != null; }

    /**
     * Return the optional client UI action
     *
     * @return ClientUIAction
     */
    public final ClientUIAction getClientUIAction() { return m_uiAction; }

    /**
     * Check if there is an optional action icon
     *
     * @return boolean
     */
    public final boolean hasIcon() { return m_icon != null; }

    /**
     * Return the action icon
     *
     * @return ActionIcon
     */
    public final ActionIcon getIcon() { return m_icon; }

    /**
     * Run the server action
     *
     * @param req RunActionRequest
     * @param netFile ClientAPINetworkFile
     * @return ClientAPIResponse
     * @exception ClientAPIException If an error occurs
     */
    public abstract ClientAPIResponse runAction(RunActionRequest req, ClientAPINetworkFile netFile)
        throws ClientAPIException;

    /**
     * Set or clear the server action enabled flag
     *
     * @param ena boolean
     */
    public final void setEnabled(boolean ena) { m_enabled = ena; }

    /**
     * Set the server action name
     *
     * @param name String
     */
    public final void setName(String name) { m_name = name; }

    /**
     * Set the server action description
     *
     * @param desc String
     */
    public final void setDescription(String desc) { m_description = desc; }

    /**
     * Set the client UI action to run before the server action is called by the client
     *
     * @param uiAction ClientUIAction
     */
    public final void setUIAction( ClientUIAction uiAction) { m_uiAction = uiAction; }

    /**
     * Set the action icon
     *
     * @param icon ActionIcon
     */
    public final void setIcon(ActionIcon icon) { m_icon = icon; }

    /**
     * Return the scripted action details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Name=");
        str.append( getName());
        str.append(",Description=");
        str.append( getDescription());
        str.append(",Flags=");
        str.append( getFlags());

        if ( hasClientUIAction()) {
            str.append(",UIAction=");
            str.append( getClientUIAction());
        }

        if ( hasIcon()) {
            str.append(",Icon=");
            str.append( getIcon());
        }

        if ( !isEnabled())
            str.append(",Disabled");

        str.append("]");

        return str.toString();
    }
}
