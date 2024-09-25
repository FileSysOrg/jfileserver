/*
 * Copyright (C) 2023 GK Spencer
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

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.filesys.server.filesys.clientapi.ApiRequest;

/**
 * Client API Request Class
 *
 * <p>Base class for client API requests</p>
 *
 * @author gkspencer
 */
public abstract class ClientAPIRequest {

    // Request id
    @SerializedName(value = "type")
    private String m_type;

    // Associated client API
    private transient JSONClientAPI m_api;

    // process the request with debug output enabled
    private transient boolean m_debug;

    /**
     * Class constructor
     */
    public ClientAPIRequest() {
    }

    /**
     * Return the message type
     *
     * @return MsgType
     */
    public abstract ApiRequest isType();

    /**
     * Check if debug output is enabled
     *
     * @return boolean
     */
    public final boolean hasDebug() { return m_debug; }

    /**
     * Enable/disable debug output
     *
     * @param ena boolean
     */
    public final void setDebug( boolean ena) { m_debug = ena; }

    /**
     * Check if the request has an associated client API
     *
     * @return boolean
     */
    public final boolean hasClientAPI() { return m_api != null; }

    /**
     * Return the associated client API
     *
     * @return JSONClientAPI
     */
    public final JSONClientAPI getClientAPI() { return m_api; }

    /**
     * Set the associated client API
     *
     * @param clientAPI JSONClientAPI
     */
    public final void setClientAPI( JSONClientAPI clientAPI) { m_api = clientAPI; }

    /**
     * Set the sync message details from the JSON object values
     *
     * @param jsonObj JsonObject
     * @exception JsonParseException Error parsing the JSON object
     */
    public abstract void fromJSON(JsonObject jsonObj)
        throws JsonParseException;
}
