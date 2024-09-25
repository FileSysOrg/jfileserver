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
import org.filesys.server.filesys.clientapi.ApiResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Run Action Response Class
 *
 * @author gkspencer
 */
public class RunActionResponse extends ClientAPIResponse {

    @SerializedName(value = "status")
    private ServerAction.Status m_status;
    
    @SerializedName(value = "client_action")
    private ServerAction.ClientAction m_clientAction;
    
    @SerializedName(value = "parameters")
    private List<String> m_parameters;

    @SerializedName(value = "refresh_original")
    private boolean m_refreshOriginal;

    @SerializedName(value = "ui_action")
    private ClientUIAction m_uiAction;

    @SerializedName(value = "notify_action")
    private NotificationAction m_notification;

    /**
     * Default constructor
     *
     * <p>Return a success status with no action required on the client</p>
     */
    public RunActionResponse() {
        super( ApiResponse.ActionResult);
        
        m_status = ServerAction.Status.Ok;
        m_clientAction = ServerAction.ClientAction.NoAction;
    }

    /**
     * Set a success run script status
     */
    public final void setSuccess() { m_status = ServerAction.Status.Ok; }

    /**
     * Set an error run script status and the error message to be displayed on the client
     *
     * @param errMsg String
     */
    public final void setError( String errMsg) {
        m_status = ServerAction.Status.Error;

        m_clientAction = ServerAction.ClientAction.NoAction;
        m_parameters = new ArrayList<>( 1);

        m_parameters.add( errMsg );
    }

    /**
     * Refresh the original path details on the client
     *
     * @param refresh boolean
     */
    public final void setRefreshOriginal( boolean refresh) { m_refreshOriginal = refresh; }

    /**
     * Set the client action and parameters
     *
     * @param action ScriptAction.ClientAction
     * @param params List&lt;String&gt;
     */
    public final void setClientAction(ServerAction.ClientAction action, List<String> params) {
        m_clientAction = action;
        m_parameters = params;
    }

    /**
     * Set the client action and single parameter
     *
     * @param action ScriptAction.ClientAction
     * @param param String
     */
    public final void setClientAction(ServerAction.ClientAction action, String param) {
        m_clientAction = action;
        m_parameters = new ArrayList<>( 1);

        m_parameters.add( param);
    }

    /**
     * Set the URL for the client application to open
     *
     * @param url String
     */
    public final void setOpenURL( String url) {
        m_clientAction = ServerAction.ClientAction.OpenURL;
        m_parameters = new ArrayList<>( 1);

        m_parameters.add( url);
    }

    /**
     * Set the message for the client application to display
     *
     * @param msg String
     */
    public final void setMessage( String msg) {
        m_clientAction = ServerAction.ClientAction.UIAction;
        m_uiAction = new ClientUIAction( ClientUIAction.UIAction.MessageDialog, "", msg, ServerAction.MessageLevel.Info);
    }

    /**
     * Set the message for the client application to display
     *
     * @param title String
     * @param msg String
     */
    public final void setMessage( String title, String msg) {
        m_clientAction = ServerAction.ClientAction.UIAction;
        m_uiAction = new ClientUIAction( ClientUIAction.UIAction.MessageDialog, title, msg, ServerAction.MessageLevel.Info);
    }

    /**
     * Set the message for the client application to display
     *
     * @param title String
     * @param msg String
     * @param msgLevel String
     */
    public final void setMessage( String title, String msg, ServerAction.MessageLevel msgLevel) {
        m_clientAction = ServerAction.ClientAction.UIAction;
        m_uiAction = new ClientUIAction( ClientUIAction.UIAction.MessageDialog, title, msg, msgLevel);
    }

    /**
     * Set the warning message for the client application to display
     *
     * @param msg String
     */
    public final void setWarningMessage( String msg) {
        m_clientAction = ServerAction.ClientAction.UIAction;
        m_uiAction = new ClientUIAction( ClientUIAction.UIAction.MessageDialog, "", msg, ServerAction.MessageLevel.Warn);
    }

    /**
     * Set the warning message for the client application to display
     *
     * @param title String
     * @param msg String
     */
    public final void setWarningMessage( String title, String msg) {
        m_clientAction = ServerAction.ClientAction.UIAction;
        m_uiAction = new ClientUIAction( ClientUIAction.UIAction.MessageDialog, title, msg, ServerAction.MessageLevel.Warn);
    }

    /**
     * Set the error message for the client application to display
     *
     * @param msg String
     */
    public final void setErrorMessage( String msg) {
        m_clientAction = ServerAction.ClientAction.UIAction;
        m_uiAction = new ClientUIAction( ClientUIAction.UIAction.MessageDialog, "", msg, ServerAction.MessageLevel.Error);
    }

    /**
     * Set the warning message for the client application to display
     *
     * @param title String
     * @param msg String
     */
    public final void setErrorMessage( String title, String msg) {
        m_clientAction = ServerAction.ClientAction.UIAction;
        m_uiAction = new ClientUIAction( ClientUIAction.UIAction.MessageDialog, title, msg, ServerAction.MessageLevel.Error);
    }

    /**
     * Set the application for the client application to execute
     *
     * @param operation String
     * @param app String
     */
    public final void setExecute( String operation, String app) {
        m_clientAction = ServerAction.ClientAction.OpenApplication;
        m_parameters = new ArrayList<>( 2);

        m_parameters.add( operation);
        m_parameters.add( app);
    }

    /**
     * Set the application for the client application to execute
     *
     * @param operation String
     * @param app String
     * @param param String
     */
    public final void setExecute( String operation, String app, String param) {
        m_clientAction = ServerAction.ClientAction.OpenApplication;
        m_parameters = new ArrayList<>( 3);

        m_parameters.add( operation);
        m_parameters.add( app);
        m_parameters.add( param);
    }

    /**
     * Return a list of updated paths to the client
     *
     * @param paths List&lt;String&gt;
     */
    public final void setUpdatedPaths( List<String> paths) {
        m_clientAction = ServerAction.ClientAction.UpdatedPaths;
        m_parameters = paths;
    }

    /**
     * Return a list of new paths to the client
     *
     * @param paths List&lt;String&gt;
     */
    public final void setCreatedPaths( List<String> paths) {
        m_clientAction = ServerAction.ClientAction.NewPaths;
        m_parameters = paths;
    }

    /**
     * Return a list of deleted paths to the client
     *
     * @param paths List&lt;String&gt;
     */
    public final void setRemovedPaths( List<String> paths) {
        m_clientAction = ServerAction.ClientAction.DeletedPaths;
        m_parameters = paths;
    }

    /**
     * Set the client notification
     *
     * @param notification NotificationAction
     */
    public final void setNotification( NotificationAction notification) {
        m_notification = notification;
    }

    /**
     * Set the client notification
     *
     * @param text String
     */
    public final void setNotification( String text) {
        m_notification = new NotificationAction( text);
    }

    /**
     * Set the client notification
     *
     * @param title String
     * @param text String
     */
    public final void setNotification( String title, String text) {
        m_notification = new NotificationAction( title, text);
    }

    /**
     * Parse a message level string into an enum value
     *
     * @param levStr String
     * @return ServerAction.MessageLevel
     */
    private ServerAction.MessageLevel valueOfMessageLevel( String levStr) {
        ServerAction.MessageLevel msgLevel = ServerAction.MessageLevel.Info;

        try {
            msgLevel = ServerAction.MessageLevel.valueOf(levStr);
        }
        catch ( IllegalArgumentException ex) {
        }

        return msgLevel;
    }

    /**
     * Create an error action response
     *
     * @param errMsg String
     */
    public static RunActionResponse createErrorResponse( String errMsg) {
        RunActionResponse response = new RunActionResponse();
        response.setError( errMsg);
        return response;
    }

    /**
     * Return the run action response details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Response: sts=");
        str.append( m_status);
        str.append(", client_action=");
        str.append( m_clientAction);
        str.append(",params=");
        str.append( m_parameters);
        str.append(",refresh_original=");
        str.append( m_refreshOriginal);
        str.append(",ui_action=");
        str.append( m_uiAction);
        str.append(",notify_action=");
        str.append( m_notification);
        str.append("]");

        return str.toString();
    }
}
