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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.filesys.server.filesys.clientapi.ApiRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Run Action Request Class
 *
 * @author gkspencer
 */
public class RunActionRequest extends ClientAPIRequest {

    @SerializedName(value = "action")
    private String m_action;

    @SerializedName(value = "relative_paths")
    private List<String> m_relPaths;

    @SerializedName(value = "parameters")
    private List<String> m_params;

    /**
     * Default constructor
     */
    public RunActionRequest() {
    }

    @Override
    public ApiRequest isType() {
        return ApiRequest.RunAction;
    }

    /**
     * Return the action name
     *
     * @return String
     */
    public final String getAction() { return m_action; }

    /**
     * Return the relative path list
     *
     * @return List&lt;String&gt;
     */
    public final List<String> getRelativePaths() {
        return m_relPaths;
    }

    /**
     * Check if the request has optional parameters
     *
     * @return boolean
     */
    public final boolean hasParameters() { return m_params != null && !m_params.isEmpty(); }

    /**
     * Return the optional parameters list
     *
     * @return List&lt;String&gt;
     */
    public final List<String> getParameters() {
        return m_params;
    }

    @Override
    public void fromJSON(JsonObject jsonObj) throws JsonParseException {

        // Load the action name
        m_action = jsonObj.get("action").getAsString();

        // Load the relative paths list
        JsonArray elems = jsonObj.get("relative_paths").getAsJsonArray();

        // Convert the paths to a list
        m_relPaths = new ArrayList<>(elems.size());

        for (JsonElement curElem : elems) {
            m_relPaths.add( curElem.getAsString());
        }

        // Load the request optional parameters
        m_params = null;

        if ( jsonObj.has("parameters")) {
            JsonArray params = jsonObj.get("parameters").getAsJsonArray();

            // Convert the parameters to a list
            // Note: Do not use isEmpty() call on JsonArray, not available in earlier versions of GSON
            if (params != null && params.size() > 0) {
                m_params = new ArrayList<>(params.size());

                for (JsonElement curElem : params) {
                    m_params.add(curElem.getAsString());
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Request=");
        str.append(isType());
        str.append(", action=");
        str.append(getAction());
        str.append(",rel_paths=");
        str.append(getRelativePaths());
        str.append(",params=");
        if ( hasParameters())
            str.append(getParameters());
        else
            str.append("None");
        str.append("]");

        return str.toString();
    }
}
